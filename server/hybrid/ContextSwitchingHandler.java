package server.hybrid;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.http.*;
import server.core.routing.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 컨텍스트 스위칭 핸들러 - 하이브리드 서버의 핵심 컴포넌트
 *
 * 역할:
 * 1. I/O 대기시 스레드 해제 (Context Switch Out)
 * 2. I/O 완료시 스레드 재할당 (Context Switch In)
 * 3. 스레드와 요청 컨텍스트 분리 관리
 * 4. 백프레셔(Backpressure) 제어
 * 5. 비동기 작업 체인 관리
 */
public class ContextSwitchingHandler {

    private static final Logger logger = LoggerFactory.getLogger(ContextSwitchingHandler.class);

    // === 핵심 컴포넌트 ===
    private final AdaptiveThreadPool threadPool;
    private final AsyncContextManager contextManager;

    // === 컨텍스트 스위칭 통계 ===
    private final AtomicLong totalSwitchOuts = new AtomicLong(0);
    private final AtomicLong totalSwitchIns = new AtomicLong(0);
    private final AtomicLong activeSwitchedContexts = new AtomicLong(0);

    // === 성능 모니터링 ===
    private final AtomicLong totalSwitchTime = new AtomicLong(0);
    private final AtomicLong switchTimeouts = new AtomicLong(0);

    // === 설정 ===
    private volatile long defaultSwitchTimeoutMs = 30000;
    private volatile int maxConcurrentSwitches = 1000;

    /**
     * ContextSwitchingHandler 생성자
     */
    public ContextSwitchingHandler(AdaptiveThreadPool threadPool, AsyncContextManager contextManager) {
        this.threadPool = threadPool;
        this.contextManager = contextManager;

        logger.info("ContextSwitchingHandler 초기화 - 최대 스위치: {}, 타임아웃: {}ms",
                maxConcurrentSwitches, defaultSwitchTimeoutMs);
    }

    /**
     * 비동기 처리 with 컨텍스트 스위칭
     */
    public <T> CompletableFuture<T> switchAndExecute(HttpRequest request,
                                                     Supplier<CompletableFuture<T>> asyncOperation) {
        SwitchContext switchContext = switchOut(request);

        try {
            CompletableFuture<T> operationFuture = asyncOperation.get();

            return operationFuture.whenCompleteAsync((result, throwable) -> {
                switchIn(switchContext, result, throwable);
            }, threadPool);

        } catch (Exception e) {
            switchInWithError(switchContext, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 데이터베이스 작업 with 컨텍스트 스위칭
     */
    public CompletableFuture<String> executeDbOperation(HttpRequest request,
                                                        Function<HttpRequest, String> dbOperation) {
        return switchAndExecute(request, () ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        logger.debug("DB 작업 시작 - URI: {}, 스레드: {}",
                                request.getPath(), Thread.currentThread().getName());

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
     */
    public CompletableFuture<String> executeApiCall(HttpRequest request,
                                                    Function<HttpRequest, String> apiCall) {
        return switchAndExecute(request, () ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        logger.debug("API 호출 시작 - URI: {}, 스레드: {}",
                                request.getPath(), Thread.currentThread().getName());

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
     */
    public CompletableFuture<byte[]> executeFileOperation(HttpRequest request,
                                                          Function<HttpRequest, byte[]> fileOperation) {
        return switchAndExecute(request, () ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        logger.debug("파일 작업 시작 - URI: {}, 스레드: {}",
                                request.getPath(), Thread.currentThread().getName());

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
     */
    private SwitchContext switchOut(HttpRequest request) {
        long currentSwitches = activeSwitchedContexts.get();
        if (currentSwitches >= maxConcurrentSwitches) {
            logger.warn("최대 동시 스위치 수 초과: {}", currentSwitches);
            throw new RuntimeException("Too many concurrent context switches");
        }

        long switchId = totalSwitchOuts.incrementAndGet();
        String contextId = contextManager.createContext(request);

        SwitchContext switchContext = new SwitchContext(
                switchId,
                contextId,
                request,
                Thread.currentThread().getName(),
                System.nanoTime()
        );

        activeSwitchedContexts.incrementAndGet();

        logger.debug("Context Switch Out - ID: {}, 요청: {}, 스레드: {}",
                switchId, request.getPath(), Thread.currentThread().getName());

        return switchContext;
    }

    /**
     * Context Switch In - 새로운 스레드에서 컨텍스트 복원
     */
    private <T> void switchIn(SwitchContext switchContext, T result, Throwable throwable) {
        try {
            long switchTimeNanos = System.nanoTime() - switchContext.getSwitchOutTime();
            totalSwitchTime.addAndGet(switchTimeNanos);

            totalSwitchIns.incrementAndGet();
            activeSwitchedContexts.decrementAndGet();

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
     */
    private void switchInWithError(SwitchContext switchContext, Throwable error) {
        switchIn(switchContext, null, error);
    }

    /**
     * 복합 비동기 작업 처리
     */
    @SafeVarargs
    public final CompletableFuture<Object[]> executeMultiple(HttpRequest request,
                                                             Supplier<CompletableFuture<?>>... operations) {
        CompletableFuture<?>[] futures = new CompletableFuture[operations.length];

        for (int i = 0; i < operations.length; i++) {
            final int index = i;
            // 각 operation을 개별적으로 처리
            futures[i] = processMultipleOperation(request, operations[index]);
        }

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
     * executeMultiple용 개별 작업 처리
     */
    private CompletableFuture<?> processMultipleOperation(HttpRequest request,
                                                          Supplier<CompletableFuture<?>> operation) {
        SwitchContext switchContext = switchOut(request);

        try {
            CompletableFuture<?> operationFuture = operation.get();

            return operationFuture.whenCompleteAsync((result, throwable) -> {
                switchIn(switchContext, result, throwable);
            }, threadPool);

        } catch (Exception e) {
            switchInWithError(switchContext, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 타임아웃과 함께 컨텍스트 스위칭
     */
    public <T> CompletableFuture<T> switchAndExecuteWithTimeout(HttpRequest request,
                                                                Supplier<CompletableFuture<T>> asyncOperation,
                                                                long timeoutMs) {
        SwitchContext switchContext = switchOut(request);

        try {
            CompletableFuture<T> operationFuture = asyncOperation.get();

            CompletableFuture<T> timeoutFuture = new CompletableFuture<>();

            ScheduledFuture<?> timeoutTask = Executors.newSingleThreadScheduledExecutor()
                    .schedule(() -> {
                        switchTimeouts.incrementAndGet();
                        timeoutFuture.completeExceptionally(
                                new TimeoutException("Context switch timeout after " + timeoutMs + "ms")
                        );
                    }, timeoutMs, TimeUnit.MILLISECONDS);

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

        return totalSwitchTime.get() / totalSwitches / 1_000_000;
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