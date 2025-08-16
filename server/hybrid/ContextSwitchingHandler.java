package server.hybrid;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.http.*;
import server.core.routing.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;

/**
 * 컨텍스트 스위칭 핸들러 - 하이브리드 서버의 핵심 컴포넌트
 *
 * 역할:
 * 1. I/O 대기시 스레드 해제 (Context Switch Out)
 * 2. I/O 완료시 스레드 재할당 (Context Switch In)
 * 3. 스레드와 요청 컨텍스트 분리 관리
 * 4. 백프레셔(Backpressure) 제어
 * 5. 비동기 작업 체인 관리
 *
 * Threaded vs Hybrid의 핵심 차이점:
 * - Threaded: 요청 시작부터 완료까지 스레드 점유
 * - Hybrid: I/O 대기시 스레드 해제, 완료시 다른 스레드에서 재개
 */
public class ContextSwitchingHandler {

    private static final Logger logger = LoggerFactory.getLogger(ContextSwitchingHandler.class);

    // === 핵심 컴포넌트 ===
    private final AdaptiveThreadPool threadPool;         // 워커 스레드풀
    private final AsyncContextManager contextManager;    // 비동기 컨텍스트 관리

    // === 컨텍스트 스위칭 통계 ===
    private final AtomicLong totalSwitchOuts = new AtomicLong(0);   // 총 스위치 아웃 횟수
    private final AtomicLong totalSwitchIns = new AtomicLong(0);    // 총 스위치 인 횟수
    private final AtomicLong activeSwitchedContexts = new AtomicLong(0); // 현재 스위치된 컨텍스트 수

    // === 성능 모니터링 ===
    private final AtomicLong totalSwitchTime = new AtomicLong(0);   // 총 스위치 시간 (나노초)
    private final AtomicLong switchTimeouts = new AtomicLong(0);    // 스위치 타임아웃 횟수

    // === 설정 ===
    private volatile long defaultSwitchTimeoutMs = 30000;          // 기본 스위치 타임아웃 (30초)
    private volatile int maxConcurrentSwitches = 1000;             // 최대 동시 스위치 수

    /**
     * ContextSwitchingHandler 생성자
     *
     * @param threadPool 워커 스레드풀
     * @param contextManager 비동기 컨텍스트 관리자
     */
    public ContextSwitchingHandler(AdaptiveThreadPool threadPool, AsyncContextManager contextManager) {
        this.threadPool = threadPool;
        this.contextManager = contextManager;

        logger.info("ContextSwitchingHandler 초기화 - 최대 스위치: {}, 타임아웃: {}ms",
                maxConcurrentSwitches, defaultSwitchTimeoutMs);
    }

    /**
     * 비동기 처리 with 컨텍스트 스위칭
     * 핵심 메서드: I/O 집약적 작업을 비동기로 처리
     *
     * @param request HTTP 요청
     * @param asyncOperation 비동기 작업 (I/O 작업)
     * @param <T> 작업 결과 타입
     * @return CompletableFuture<T> 비동기 결과
     */
    public <T> CompletableFuture<T> switchAndExecute(HttpRequest request,
                                                     Supplier<CompletableFuture<T>> asyncOperation) {
        // 1. Context Switch Out - 현재 스레드에서 컨텍스트 분리
        SwitchContext switchContext = switchOut(request);

        try {
            // 2. 비동기 작업 실행
            CompletableFuture<T> operationFuture = asyncOperation.get();

            // 3. 작업 완료시 Context Switch In
            return operationFuture.whenCompleteAsync((result, throwable) -> {
                switchIn(switchContext, result, throwable);
            }, threadPool);

        } catch (Exception e) {
            // 예외 발생시 즉시 컨텍스트 복원
            switchInWithError(switchContext, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 데이터베이스 작업 with 컨텍스트 스위칭
     * 실제 사용 예시를 위한 특화 메서드
     *
     * @param request HTTP 요청
     * @param dbOperation 데이터베이스 작업
     * @return CompletableFuture<String> DB 결과
     */
    public CompletableFuture<String> executeDbOperation(HttpRequest request,
                                                        Function<HttpRequest, String> dbOperation) {
        return switchAndExecute(request, () ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        // DB 작업 시뮬레이션 (실제로는 JDBC, JPA 등 사용)
                        logger.debug("DB 작업 시작 - URI: {}, 스레드: {}",
                                request.getPath(), Thread.currentThread().getName());

                        // 실제 DB 호출 (I/O 블로킹)
                        String result = dbOperation.apply(request);

                        logger.debug("DB 작업 완료 - 결과 크기: {}", result.length());
                        return result;

                    } catch (Exception e) {
                        logger.error("DB 작업 실패", e);
                        throw new RuntimeException("Database operation failed", e);
                    }
                }, threadPool)
        );
    }

    /**
     * HTTP API 호출 with 컨텍스트 스위칭
     * 외부 API 호출시 사용
     *
     * @param request HTTP 요청
     * @param apiCall API 호출 함수
     * @return CompletableFuture<String> API 응답
     */
    public CompletableFuture<String> executeApiCall(HttpRequest request,
                                                    Function<HttpRequest, String> apiCall) {
        return switchAndExecute(request, () ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        logger.debug("API 호출 시작 - URI: {}, 스레드: {}",
                                request.getPath(), Thread.currentThread().getName());

                        // 외부 API 호출 (네트워크 I/O)
                        String result = apiCall.apply(request);

                        logger.debug("API 호출 완료 - 응답 크기: {}", result.length());
                        return result;

                    } catch (Exception e) {
                        logger.error("API 호출 실패", e);
                        throw new RuntimeException("API call failed", e);
                    }
                }, threadPool)
        );
    }

    /**
     * 파일 I/O 작업 with 컨텍스트 스위칭
     * 파일 읽기/쓰기시 사용
     *
     * @param request HTTP 요청
     * @param fileOperation 파일 작업
     * @return CompletableFuture<byte[]> 파일 데이터
     */
    public CompletableFuture<byte[]> executeFileOperation(HttpRequest request,
                                                          Function<HttpRequest, byte[]> fileOperation) {
        return switchAndExecute(request, () ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        logger.debug("파일 작업 시작 - URI: {}, 스레드: {}",
                                request.getPath(), Thread.currentThread().getName());

                        // 파일 I/O 작업
                        byte[] result = fileOperation.apply(request);

                        logger.debug("파일 작업 완료 - 크기: {} bytes", result.length);
                        return result;

                    } catch (Exception e) {
                        logger.error("파일 작업 실패", e);
                        throw new RuntimeException("File operation failed", e);
                    }
                }, threadPool)
        );
    }

    /**
     * Context Switch Out - 현재 스레드에서 컨텍스트 분리
     *
     * @param request HTTP 요청
     * @return SwitchContext 스위치 컨텍스트
     */
    private SwitchContext switchOut(HttpRequest request) {
        // 동시 스위치 수 제한 확인
        long currentSwitches = activeSwitchedContexts.get();
        if (currentSwitches >= maxConcurrentSwitches) {
            logger.warn("최대 동시 스위치 수 초과: {}", currentSwitches);
            throw new RuntimeException("Too many concurrent context switches");
        }

        // 스위치 컨텍스트 생성
        long switchId = totalSwitchOuts.incrementAndGet();
        String contextId = contextManager.createContext(request);

        SwitchContext switchContext = new SwitchContext(
                switchId,
                contextId,
                request,
                Thread.currentThread().getName(),
                System.nanoTime()
        );

        // 활성 스위치 수 증가
        activeSwitchedContexts.incrementAndGet();

        logger.debug("Context Switch Out - ID: {}, 요청: {}, 스레드: {}",
                switchId, request.getPath(), Thread.currentThread().getName());

        return switchContext;
    }

    /**
     * Context Switch In - 새로운 스레드에서 컨텍스트 복원
     *
     * @param switchContext 스위치 컨텍스트
     * @param result 작업 결과
     * @param throwable 발생한 예외 (있는 경우)
     */
    private <T> void switchIn(SwitchContext switchContext, T result, Throwable throwable) {
        try {
            // 스위치 시간 계산
            long switchTimeNanos = System.nanoTime() - switchContext.getSwitchOutTime();
            totalSwitchTime.addAndGet(switchTimeNanos);

            // Switch In 통계 업데이트
            totalSwitchIns.incrementAndGet();
            activeSwitchedContexts.decrementAndGet();

            // 컨텍스트 정리
            contextManager.removeContext(switchContext.getContextId());

            logger.debug("Context Switch In - ID: {}, 시간: {}ms, 스레드: {} -> {}",
                    switchContext.getSwitchId(),
                    switchTimeNanos / 1_000_000,
                    switchContext.getOriginalThread(),
                    Thread.currentThread().getName());

            if (throwable != null) {
                logger.warn("Context Switch In with error - ID: {}",
                        switchContext.getSwitchId(), throwable);
            }

        } catch (Exception e) {
            logger.error("Context Switch In 실패", e);
        }
    }

    /**
     * 오류와 함께 Context Switch In
     *
     * @param switchContext 스위치 컨텍스트
     * @param error 발생한 오류
     */
    private void switchInWithError(SwitchContext switchContext, Throwable error) {
        switchIn(switchContext, null, error);
    }

    /**
     * 복합 비동기 작업 처리
     * 여러 I/O 작업을 순차적 또는 병렬로 처리
     *
     * @param request HTTP 요청
     * @param operations 비동기 작업들
     * @return CompletableFuture<결과 리스트>
     */
    @SafeVarargs
    public final CompletableFuture<Object[]> executeMultiple(HttpRequest request,
                                                             Supplier<CompletableFuture<?>>... operations) {
        // 모든 작업을 컨텍스트 스위칭으로 처리
        CompletableFuture<?>[] futures = new CompletableFuture[operations.length];

        for (int i = 0; i < operations.length; i++) {
            final int index = i;
            futures[i] = switchAndExecute(request, operations[index]);
        }

        // 모든 작업 완료 대기
        return CompletableFuture.allOf(futures)
                .thenApply(void_ -> {
                    Object[] results = new Object[futures.length];
                    for (int i = 0; i < futures.length; i++) {
                        try {
                            results[i] = futures[i].get();
                        } catch (Exception e) {
                            results[i] = e;
                        }
                    }
                    return results;
                });
    }

    /**
     * 타임아웃과 함께 컨텍스트 스위칭
     *
     * @param request HTTP 요청
     * @param asyncOperation 비동기 작업
     * @param timeoutMs 타임아웃 (밀리초)
     * @return CompletableFuture<T> 결과 또는 타임아웃
     */
    public <T> CompletableFuture<T> switchAndExecuteWithTimeout(HttpRequest request,
                                                                Supplier<CompletableFuture<T>> asyncOperation,
                                                                long timeoutMs) {
        SwitchContext switchContext = switchOut(request);

        try {
            CompletableFuture<T> operationFuture = asyncOperation.get();

            // 타임아웃 설정
            CompletableFuture<T> timeoutFuture = new CompletableFuture<>();

            // 타임아웃 스케줄링
            ScheduledFuture<?> timeoutTask = Executors.newSingleThreadScheduledExecutor()
                    .schedule(() -> {
                        switchTimeouts.incrementAndGet();
                        timeoutFuture.completeExceptionally(
                                new TimeoutException("Context switch timeout after " + timeoutMs + "ms")
                        );
                    }, timeoutMs, TimeUnit.MILLISECONDS);

            // 먼저 완료되는 것으로 결과 결정
            return CompletableFuture.anyOf(operationFuture, timeoutFuture)
                    .thenCompose(result -> {
                        timeoutTask.cancel(false);

                        if (result instanceof Throwable) {
                            return CompletableFuture.failedFuture((Throwable) result);
                        } else {
                            @SuppressWarnings("unchecked")
                            T typedResult = (T) result;
                            return CompletableFuture.completedFuture(typedResult);
                        }
                    })
                    .whenCompleteAsync((result, throwable) -> {
                        switchIn(switchContext, result, throwable);
                    }, threadPool);

        } catch (Exception e) {
            switchInWithError(switchContext, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 컨텍스트 스위칭 통계 조회
     */
    public SwitchingStats getStats() {
        return new SwitchingStats(
                totalSwitchOuts.get(),
                totalSwitchIns.get(),
                activeSwitchedContexts.get(),
                switchTimeouts.get(),
                calculateAverageSwitchTime(),
                maxConcurrentSwitches,
                defaultSwitchTimeoutMs
        );
    }

    /**
     * 평균 스위치 시간 계산 (밀리초)
     */
    private long calculateAverageSwitchTime() {
        long totalSwitches = totalSwitchIns.get();
        if (totalSwitches == 0) return 0;

        return totalSwitchTime.get() / totalSwitches / 1_000_000; // 나노초 -> 밀리초
    }

    /**
     * 스위치 타임아웃 설정
     */
    public void setSwitchTimeout(long timeoutMs) {
        this.defaultSwitchTimeoutMs = timeoutMs;
        logger.info("컨텍스트 스위치 타임아웃 변경: {}ms", timeoutMs);
    }

    /**
     * 최대 동시 스위치 수 설정
     */
    public void setMaxConcurrentSwitches(int maxSwitches) {
        this.maxConcurrentSwitches = maxSwitches;
        logger.info("최대 동시 스위치 수 변경: {}", maxSwitches);
    }

    /**
     * 스위치 컨텍스트 클래스
     * 개별 컨텍스트 스위치의 상태를 추적
     */
    public static class SwitchContext {
        private final long switchId;
        private final String contextId;
        private final HttpRequest request;
        private final String originalThread;
        private final long switchOutTime;

        public SwitchContext(long switchId, String contextId, HttpRequest request,
                             String originalThread, long switchOutTime) {
            this.switchId = switchId;
            this.contextId = contextId;
            this.request = request;
            this.originalThread = originalThread;
            this.switchOutTime = switchOutTime;
        }

        // Getters
        public long getSwitchId() { return switchId; }
        public String getContextId() { return contextId; }
        public HttpRequest getRequest() { return request; }
        public String getOriginalThread() { return originalThread; }
        public long getSwitchOutTime() { return switchOutTime; }

        @Override
        public String toString() {
            return String.format("SwitchContext{id=%d, contextId='%s', thread='%s', uri='%s'}",
                    switchId, contextId, originalThread, request.getPath());
        }
    }

    /**
     * 스위칭 통계 클래스
     */
    public static class SwitchingStats {
        private final long totalSwitchOuts;
        private final long totalSwitchIns;
        private final long activeSwitches;
        private final long timeouts;
        private final long averageSwitchTimeMs;
        private final int maxConcurrentSwitches;
        private final long defaultTimeoutMs;

        public SwitchingStats(long totalSwitchOuts, long totalSwitchIns, long activeSwitches,
                              long timeouts, long averageSwitchTimeMs, int maxConcurrentSwitches,
                              long defaultTimeoutMs) {
            this.totalSwitchOuts = totalSwitchOuts;
            this.totalSwitchIns = totalSwitchIns;
            this.activeSwitches = activeSwitches;
            this.timeouts = timeouts;
            this.averageSwitchTimeMs = averageSwitchTimeMs;
            this.maxConcurrentSwitches = maxConcurrentSwitches;
            this.defaultTimeoutMs = defaultTimeoutMs;
        }

        // Getters
        public long getTotalSwitchOuts() { return totalSwitchOuts; }
        public long getTotalSwitchIns() { return totalSwitchIns; }
        public long getActiveSwitches() { return activeSwitches; }
        public long getTimeouts() { return timeouts; }
        public long getAverageSwitchTimeMs() { return averageSwitchTimeMs; }
        public int getMaxConcurrentSwitches() { return maxConcurrentSwitches; }
        public long getDefaultTimeoutMs() { return defaultTimeoutMs; }

        public double getTimeoutRate() {
            return totalSwitchOuts > 0 ? (double) timeouts / totalSwitchOuts * 100 : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                    "SwitchingStats{switchOuts=%d, switchIns=%d, active=%d, " +
                            "timeouts=%d (%.1f%%), avgTime=%dms, maxConcurrent=%d}",
                    totalSwitchOuts, totalSwitchIns, activeSwitches,
                    timeouts, getTimeoutRate(), averageSwitchTimeMs, maxConcurrentSwitches
            );
        }
    }
}