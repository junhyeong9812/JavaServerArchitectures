package server.hybrid;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.http.HttpRequest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * 비동기 컨텍스트 관리자
 *
 * 역할:
 * 1. HTTP 요청의 비동기 처리 컨텍스트 관리
 * 2. 스레드간 요청 상태 공유
 * 3. 컨텍스트 생명주기 관리
 * 4. 메모리 누수 방지를 위한 자동 정리
 */
public class AsyncContextManager {

    private static final Logger logger = LoggerFactory.getLogger(AsyncContextManager.class);

    // === 컨텍스트 저장소 ===
    private final ConcurrentMap<String, AsyncContext> contexts = new ConcurrentHashMap<>();

    // === 컨텍스트 ID 생성 ===
    private final AtomicLong contextIdGenerator = new AtomicLong(0);
    private final String nodeId;

    // === 타임아웃 관리 ===
    private final ScheduledExecutorService cleanupExecutor;
    private final long defaultTimeoutMs;
    private final AtomicInteger activeContexts = new AtomicInteger(0);

    // === 통계 ===
    private final AtomicLong createdContexts = new AtomicLong(0);
    private final AtomicLong expiredContexts = new AtomicLong(0);
    private final AtomicLong cleanedContexts = new AtomicLong(0);

    /**
     * AsyncContextManager 생성자
     */
    public AsyncContextManager() {
        this(30000); // 기본 30초 타임아웃
    }

    /**
     * AsyncContextManager 생성자 (타임아웃 지정)
     */
    public AsyncContextManager(long timeoutMs) {
        this.defaultTimeoutMs = timeoutMs;

        this.nodeId = UUID.randomUUID().toString().substring(0, 8);

        this.cleanupExecutor = Executors.newScheduledThreadPool(1,
                r -> new Thread(r, "AsyncContext-Cleanup"));

        this.cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpiredContexts,
                10,
                10,
                TimeUnit.SECONDS
        );

        logger.info("AsyncContextManager 초기화 완료 - 노드: {}, 타임아웃: {}ms",
                nodeId, timeoutMs);
    }

    /**
     * 새로운 비동기 컨텍스트 생성
     */
    public String createContext(HttpRequest request) {
        long contextNumber = contextIdGenerator.incrementAndGet();
        String contextId = String.format("%s-%d", nodeId, contextNumber);

        long createdTime = System.currentTimeMillis();
        long expireTime = createdTime + defaultTimeoutMs;

        AsyncContext context = new AsyncContext(
                contextId,
                request,
                createdTime,
                expireTime,
                Thread.currentThread().getName()
        );

        contexts.put(contextId, context);

        activeContexts.incrementAndGet();
        createdContexts.incrementAndGet();

        logger.debug("비동기 컨텍스트 생성 - ID: {}, URI: {}, 만료시간: {}",
                contextId, request.getPath(), new Date(expireTime));

        return contextId;
    }

    /**
     * 컨텍스트 조회
     */
    public AsyncContext getContext(String contextId) {
        AsyncContext context = contexts.get(contextId);

        if (context == null) {
            logger.debug("컨텍스트를 찾을 수 없음 - ID: {}", contextId);
            return null;
        }

        if (context.isExpired()) {
            logger.debug("만료된 컨텍스트 - ID: {}", contextId);
            removeContext(contextId);
            return null;
        }

        context.updateLastAccess();
        return context;
    }

    /**
     * 컨텍스트 제거
     */
    public AsyncContext removeContext(String contextId) {
        AsyncContext removed = contexts.remove(contextId);

        if (removed != null) {
            activeContexts.decrementAndGet();

            logger.debug("컨텍스트 제거 완료 - ID: {}, 생존시간: {}ms",
                    contextId, removed.getLifetimeMs());
        }

        return removed;
    }

    /**
     * 컨텍스트 상태 업데이트
     */
    public void updateContextState(String contextId, AsyncContext.State state, Object data) {
        AsyncContext context = contexts.get(contextId);

        if (context != null) {
            context.setState(state);
            context.setStateData(data);
            context.updateLastAccess();

            logger.debug("컨텍스트 상태 업데이트 - ID: {}, 상태: {}", contextId, state);
        }
    }

    /**
     * 컨텍스트에 속성 설정
     */
    public void setContextAttribute(String contextId, String key, Object value) {
        AsyncContext context = contexts.get(contextId);

        if (context != null) {
            context.setAttribute(key, value);
            context.updateLastAccess();

            logger.debug("컨텍스트 속성 설정 - ID: {}, 키: {}", contextId, key);
        }
    }

    /**
     * 컨텍스트 속성 조회
     */
    public Object getContextAttribute(String contextId, String key) {
        AsyncContext context = contexts.get(contextId);

        if (context != null) {
            context.updateLastAccess();
            return context.getAttribute(key);
        }

        return null;
    }

    /**
     * 특정 상태의 컨텍스트들 조회
     */
    public List<AsyncContext> getContextsByState(AsyncContext.State state) {
        List<AsyncContext> result = new ArrayList<>();

        for (AsyncContext context : contexts.values()) {
            if (context.getState() == state && !context.isExpired()) {
                result.add(context);
            }
        }

        return result;
    }

    /**
     * 만료된 컨텍스트 정리
     */
    private void cleanupExpiredContexts() {
        long currentTime = System.currentTimeMillis();
        int cleanedCount = 0;

        Iterator<Map.Entry<String, AsyncContext>> iterator = contexts.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, AsyncContext> entry = iterator.next();
            AsyncContext context = entry.getValue();

            if (context.isExpired(currentTime)) {
                iterator.remove();

                activeContexts.decrementAndGet();
                expiredContexts.incrementAndGet();
                cleanedCount++;

                logger.debug("만료된 컨텍스트 정리 - ID: {}, 생존시간: {}ms",
                        context.getId(), context.getLifetimeMs());
            }
        }

        if (cleanedCount > 0) {
            cleanedContexts.addAndGet(cleanedCount);
            logger.info("컨텍스트 정리 완료 - 정리된 수: {}, 활성 수: {}",
                    cleanedCount, activeContexts.get());
        }
    }

    /**
     * 특정 시간보다 오래된 컨텍스트 정리
     */
    public int cleanupOldContexts(long olderThanMs) {
        long cutoffTime = System.currentTimeMillis() - olderThanMs;
        int cleanedCount = 0;

        Iterator<Map.Entry<String, AsyncContext>> iterator = contexts.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, AsyncContext> entry = iterator.next();
            AsyncContext context = entry.getValue();

            if (context.getCreatedTime() < cutoffTime) {
                iterator.remove();
                activeContexts.decrementAndGet();
                cleanedCount++;

                logger.debug("오래된 컨텍스트 정리 - ID: {}, 나이: {}ms",
                        context.getId(), System.currentTimeMillis() - context.getCreatedTime());
            }
        }

        if (cleanedCount > 0) {
            cleanedContexts.addAndGet(cleanedCount);
            logger.info("오래된 컨텍스트 정리 완료 - 정리된 수: {}", cleanedCount);
        }

        return cleanedCount;
    }

    /**
     * 모든 컨텍스트 강제 정리
     */
    public void clearAllContexts() {
        int clearedCount = contexts.size();
        contexts.clear();
        activeContexts.set(0);

        logger.warn("모든 컨텍스트 강제 정리 - 정리된 수: {}", clearedCount);
    }

    /**
     * 컨텍스트 관리자 통계 조회
     */
    public ContextManagerStats getStats() {
        return new ContextManagerStats(
                activeContexts.get(),
                createdContexts.get(),
                expiredContexts.get(),
                cleanedContexts.get(),
                defaultTimeoutMs,
                nodeId
        );
    }

    /**
     * 현재 활성 컨텍스트 ID 목록 조회
     */
    public Set<String> getActiveContextIds() {
        return new HashSet<>(contexts.keySet());
    }

    /**
     * 컨텍스트 매니저 종료
     */
    public void shutdown() {
        logger.info("AsyncContextManager 종료 시작...");

        try {
            cleanupExecutor.shutdown();

            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("정리 스케줄러 강제 종료");
                cleanupExecutor.shutdownNow();
            }

            int remainingContexts = contexts.size();
            if (remainingContexts > 0) {
                logger.info("남은 컨텍스트 정리 중... 수: {}", remainingContexts);
                clearAllContexts();
            }

            logger.info("AsyncContextManager 종료 완료");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("종료 중 인터럽트 발생");
        }
    }

    /**
     * 컨텍스트 관리자 통계 클래스
     */
    public static class ContextManagerStats {
        private final int activeContexts;
        private final long createdContexts;
        private final long expiredContexts;
        private final long cleanedContexts;
        private final long defaultTimeoutMs;
        private final String nodeId;

        public ContextManagerStats(int activeContexts, long createdContexts,
                                   long expiredContexts, long cleanedContexts,
                                   long defaultTimeoutMs, String nodeId) {
            this.activeContexts = activeContexts;
            this.createdContexts = createdContexts;
            this.expiredContexts = expiredContexts;
            this.cleanedContexts = cleanedContexts;
            this.defaultTimeoutMs = defaultTimeoutMs;
            this.nodeId = nodeId;
        }

        // Getters
        public int getActiveContexts() { return activeContexts; }
        public long getCreatedContexts() { return createdContexts; }
        public long getExpiredContexts() { return expiredContexts; }
        public long getCleanedContexts() { return cleanedContexts; }
        public long getDefaultTimeoutMs() { return defaultTimeoutMs; }
        public String getNodeId() { return nodeId; }

        @Override
        public String toString() {
            return String.format(
                    "ContextManagerStats{active=%d, created=%d, expired=%d, " +
                            "cleaned=%d, timeout=%dms, node=%s}",
                    activeContexts, createdContexts, expiredContexts,
                    cleanedContexts, defaultTimeoutMs, nodeId
            );
        }
    }

    /**
     * 비동기 컨텍스트 클래스
     */
    public static class AsyncContext {

        /**
         * 컨텍스트 상태 열거형
         */
        public enum State {
            CREATED,    // 생성됨
            PROCESSING, // 처리 중
            WAITING,    // I/O 대기 중
            COMPLETED,  // 처리 완료
            ERROR,      // 오류 발생
            TIMEOUT     // 타임아웃
        }

        // === 기본 정보 ===
        private final String id;
        private final HttpRequest request;
        private final long createdTime;
        private final long expireTime;
        private final String createdThread;

        // === 상태 관리 ===
        private volatile State state = State.CREATED;
        private volatile Object stateData;
        private volatile long lastAccessTime;

        // === 속성 저장소 ===
        private final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();

        // === 처리 메타데이터 ===
        private volatile String processingThread;
        private volatile long processingStartTime;
        private volatile Throwable lastError;

        /**
         * AsyncContext 생성자
         */
        public AsyncContext(String id, HttpRequest request, long createdTime,
                            long expireTime, String createdThread) {
            this.id = id;
            this.request = request;
            this.createdTime = createdTime;
            this.expireTime = expireTime;
            this.createdThread = createdThread;
            this.lastAccessTime = createdTime;
        }

        /**
         * 컨텍스트 만료 여부 확인
         */
        public boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        /**
         * 특정 시간 기준 만료 여부 확인
         */
        public boolean isExpired(long currentTime) {
            return currentTime > expireTime;
        }

        /**
         * 마지막 접근 시간 업데이트
         */
        public void updateLastAccess() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        /**
         * 처리 시작 표시
         */
        public void startProcessing() {
            this.state = State.PROCESSING;
            this.processingThread = Thread.currentThread().getName();
            this.processingStartTime = System.currentTimeMillis();
            updateLastAccess();
        }

        /**
         * I/O 대기 상태로 전환
         */
        public void markWaiting(Object waitingFor) {
            this.state = State.WAITING;
            this.stateData = waitingFor;
            updateLastAccess();
        }

        /**
         * 처리 완료 표시
         */
        public void markCompleted(Object result) {
            this.state = State.COMPLETED;
            this.stateData = result;
            updateLastAccess();
        }

        /**
         * 오류 발생 표시
         */
        public void markError(Throwable error) {
            this.state = State.ERROR;
            this.lastError = error;
            this.stateData = error.getMessage();
            updateLastAccess();
        }

        /**
         * 타임아웃 표시
         */
        public void markTimeout() {
            this.state = State.TIMEOUT;
            updateLastAccess();
        }

        /**
         * 속성 설정
         */
        public void setAttribute(String key, Object value) {
            if (value == null) {
                attributes.remove(key);
            } else {
                attributes.put(key, value);
            }
        }

        /**
         * 속성 조회
         */
        public Object getAttribute(String key) {
            return attributes.get(key);
        }

        /**
         * 모든 속성 키 조회
         */
        public Set<String> getAttributeKeys() {
            return new HashSet<>(attributes.keySet());
        }

        /**
         * 생존 시간 계산 (밀리초)
         */
        public long getLifetimeMs() {
            return System.currentTimeMillis() - createdTime;
        }

        /**
         * 처리 시간 계산 (밀리초)
         */
        public long getProcessingTimeMs() {
            if (processingStartTime == 0) {
                return 0;
            }
            return System.currentTimeMillis() - processingStartTime;
        }

        /**
         * 만료까지 남은 시간 (밀리초)
         */
        public long getTimeToExpireMs() {
            return Math.max(0, expireTime - System.currentTimeMillis());
        }

        // === Getters ===

        public String getId() { return id; }
        public HttpRequest getRequest() { return request; }
        public long getCreatedTime() { return createdTime; }
        public long getExpireTime() { return expireTime; }
        public String getCreatedThread() { return createdThread; }
        public State getState() { return state; }
        public Object getStateData() { return stateData; }
        public long getLastAccessTime() { return lastAccessTime; }
        public String getProcessingThread() { return processingThread; }
        public long getProcessingStartTime() { return processingStartTime; }
        public Throwable getLastError() { return lastError; }

        // === Setters ===

        public void setState(State state) {
            this.state = state;
            updateLastAccess();
        }

        public void setStateData(Object stateData) {
            this.stateData = stateData;
            updateLastAccess();
        }

        @Override
        public String toString() {
            return String.format(
                    "AsyncContext{id='%s', state=%s, lifetime=%dms, " +
                            "uri='%s', thread='%s'}",
                    id, state, getLifetimeMs(),
                    request.getPath(), processingThread
            );
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            AsyncContext that = (AsyncContext) obj;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }
}