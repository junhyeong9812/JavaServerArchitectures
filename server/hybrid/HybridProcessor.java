package server.hybrid;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.http.*;
import server.core.routing.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;

/**
 * 하이브리드 요청 처리기
 *
 * 역할:
 * 1. I/O 대기와 CPU 작업 분리
 * 2. 비동기 컨텍스트 관리
 * 3. 스레드 풀 효율적 활용
 * 4. 요청 분산 및 로드 밸런싱
 */
public class HybridProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HybridProcessor.class);

    // === 처리 컴포넌트 ===
    private final AdaptiveThreadPool threadPool;
    private final AsyncContextManager contextManager;

    // === 성능 메트릭 ===
    private final AtomicLong processedRequests = new AtomicLong(0);
    private final AtomicLong asyncOperations = new AtomicLong(0);
    private final AtomicLong averageProcessingTime = new AtomicLong(0);

    // === 처리 전략 ===
    private volatile ProcessingStrategy strategy = ProcessingStrategy.ADAPTIVE;
    private final AtomicInteger concurrentRequests = new AtomicInteger(0);

    /**
     * 처리 전략 열거형
     */
    public enum ProcessingStrategy {
        SYNC,       // 동기 처리
        ASYNC,      // 비동기 처리
        ADAPTIVE    // 적응형 처리
    }

    /**
     * HybridProcessor 생성자
     */
    public HybridProcessor(AdaptiveThreadPool threadPool, AsyncContextManager contextManager) {
        this.threadPool = threadPool;
        this.contextManager = contextManager;

        logger.info("HybridProcessor 초기화 완료 - 전략: {}", strategy);
    }

    /**
     * HTTP 요청 처리 - 하이브리드 서버의 핵심 로직
     */
    public CompletableFuture<HttpResponse> processRequest(HttpRequest request, RouteHandler routeHandler) {
        long startTime = System.nanoTime();
        int currentConcurrency = concurrentRequests.incrementAndGet();

        logger.debug("요청 처리 시작 - URI: {}, 동시 요청: {}",
                request.getPath(), currentConcurrency);

        try {
            ProcessingStrategy selectedStrategy = selectStrategy(request, currentConcurrency);

            CompletableFuture<HttpResponse> responseFuture = switch (selectedStrategy) {
                case SYNC -> processSynchronously(request, routeHandler);
                case ASYNC -> processAsynchronously(request, routeHandler);
                case ADAPTIVE -> processAdaptively(request, routeHandler, currentConcurrency);
            };

            return responseFuture.whenComplete((response, throwable) -> {
                concurrentRequests.decrementAndGet();

                long processingTime = (System.nanoTime() - startTime) / 1_000_000;
                updateProcessingTime(processingTime);

                processedRequests.incrementAndGet();

                if (throwable != null) {
                    logger.warn("요청 처리 실패 - URI: {}, 처리시간: {}ms",
                            request.getPath(), processingTime, throwable);
                } else {
                    logger.debug("요청 처리 완료 - URI: {}, 처리시간: {}ms, 상태: {}",
                            request.getPath(), processingTime, response.getStatusCode());
                }
            });

        } catch (Exception e) {
            concurrentRequests.decrementAndGet();

            logger.error("요청 처리 중 예외 발생", e);
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Internal Server Error")
            );
        }
    }

    /**
     * 처리 전략 선택 로직
     */
    private ProcessingStrategy selectStrategy(HttpRequest request, int concurrency) {
        if (strategy != ProcessingStrategy.ADAPTIVE) {
            return strategy;
        }

        if (isStaticFileRequest(request)) {
            logger.debug("정적 파일 요청 감지 - 동기 처리 선택");
            return ProcessingStrategy.SYNC;
        }

        if (concurrency > threadPool.getMaximumPoolSize() * 0.8) {
            logger.debug("높은 동시성 감지 - 비동기 처리 선택: {}", concurrency);
            return ProcessingStrategy.ASYNC;
        }

        if (isDatabaseOrApiRequest(request)) {
            logger.debug("DB/API 요청 감지 - 비동기 처리 선택");
            return ProcessingStrategy.ASYNC;
        }

        return ProcessingStrategy.SYNC;
    }

    /**
     * 동기 요청 처리
     */
    private CompletableFuture<HttpResponse> processSynchronously(HttpRequest request, RouteHandler handler) {
        logger.debug("동기 처리 시작 - 스레드: {}", Thread.currentThread().getName());

        try {
            CompletableFuture<HttpResponse> result = handler.handle(request);

            if (result.isDone()) {
                return result;
            }

            HttpResponse response = result.get(30, TimeUnit.SECONDS);
            return CompletableFuture.completedFuture(response);

        } catch (TimeoutException e) {
            logger.warn("동기 처리 타임아웃 - URI: {}", request.getPath());
            return CompletableFuture.completedFuture(
                    HttpResponse.builder(HttpStatus.REQUEST_TIMEOUT)
                            .body("Request Timeout")
                            .build()
            );
        } catch (Exception e) {
            logger.error("동기 처리 오류", e);
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Processing Error")
            );
        }
    }

    /**
     * 비동기 요청 처리
     */
    private CompletableFuture<HttpResponse> processAsynchronously(HttpRequest request, RouteHandler handler) {
        logger.debug("비동기 처리 시작 - 스레드: {}", Thread.currentThread().getName());

        asyncOperations.incrementAndGet();

        String contextId = contextManager.createContext(request);

        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        logger.debug("비동기 핸들러 실행 - 컨텍스트: {}, 스레드: {}",
                                contextId, Thread.currentThread().getName());

                        return handler.handle(request);

                    } catch (Exception e) {
                        logger.error("비동기 핸들러 오류", e);
                        return CompletableFuture.completedFuture(
                                HttpResponse.internalServerError("Handler Error")
                        );
                    }
                }, threadPool)
                .thenCompose(future -> future)
                .whenComplete((response, throwable) -> {
                    contextManager.removeContext(contextId);

                    if (throwable != null) {
                        logger.warn("비동기 처리 실패 - 컨텍스트: {}", contextId, throwable);
                    }
                });
    }

    /**
     * 적응형 요청 처리
     */
    private CompletableFuture<HttpResponse> processAdaptively(HttpRequest request, RouteHandler handler, int concurrency) {
        int activeThreads = threadPool.getActiveCount();
        int maxThreads = threadPool.getMaximumPoolSize();
        double utilization = (double) activeThreads / maxThreads;

        logger.debug("적응형 처리 - 스레드 사용률: {:.2f}%, 동시요청: {}",
                utilization * 100, concurrency);

        if (utilization > 0.7) {
            logger.debug("높은 스레드 사용률 - 비동기 처리 선택");
            return processAsynchronously(request, handler);
        } else {
            logger.debug("낮은 스레드 사용률 - 동기 처리 선택");
            return processSynchronously(request, handler);
        }
    }

    /**
     * 배치 요청 처리
     */
    public CompletableFuture<HttpResponse[]> processBatch(HttpRequest[] requests, RouteHandler[] handlers) {
        if (requests.length != handlers.length) {
            throw new IllegalArgumentException("요청과 핸들러 수가 일치하지 않습니다");
        }

        logger.info("배치 처리 시작 - 요청 수: {}", requests.length);

        CompletableFuture<HttpResponse>[] futures = new CompletableFuture[requests.length];

        for (int i = 0; i < requests.length; i++) {
            futures[i] = processRequest(requests[i], handlers[i]);
        }

        return CompletableFuture.allOf(futures)
                .thenApply(void_ -> {
                    HttpResponse[] responses = new HttpResponse[futures.length];
                    for (int i = 0; i < futures.length; i++) {
                        try {
                            responses[i] = futures[i].get();
                        } catch (Exception e) {
                            logger.error("배치 처리 중 오류", e);
                            responses[i] = HttpResponse.internalServerError("Batch Processing Error");
                        }
                    }
                    return responses;
                });
    }

    /**
     * 우선순위 기반 요청 처리
     */
    public CompletableFuture<HttpResponse> processWithPriority(HttpRequest request, RouteHandler handler, int priority) {
        if (priority > 5) {
            logger.debug("고우선순위 요청 처리 - 우선순위: {}", priority);
            return processSynchronously(request, handler);
        } else {
            return processRequest(request, handler);
        }
    }

    /**
     * 정적 파일 요청 여부 확인
     */
    private boolean isStaticFileRequest(HttpRequest request) {
        String path = request.getPath().toLowerCase();
        return path.endsWith(".css") || path.endsWith(".js") ||
                path.endsWith(".png") || path.endsWith(".jpg") ||
                path.endsWith(".gif") || path.endsWith(".ico") ||
                path.startsWith("/static/") || path.startsWith("/assets/");
    }

    /**
     * 데이터베이스/API 요청 여부 확인
     */
    private boolean isDatabaseOrApiRequest(HttpRequest request) {
        String path = request.getPath().toLowerCase();
        HttpMethod method = request.getMethod();

        boolean isApiPath = path.startsWith("/api/") ||
                path.startsWith("/rest/") ||
                path.contains("/data/");

        boolean isDataMethod = method == HttpMethod.POST ||
                method == HttpMethod.PUT ||
                method == HttpMethod.DELETE;

        return isApiPath || isDataMethod;
    }

    /**
     * 처리 시간 업데이트 (이동 평균)
     */
    private void updateProcessingTime(long processingTime) {
        long currentAverage = averageProcessingTime.get();
        long newAverage = (long) (currentAverage * 0.9 + processingTime * 0.1);
        averageProcessingTime.set(newAverage);
    }

    /**
     * 처리 전략 변경
     */
    public void setProcessingStrategy(ProcessingStrategy strategy) {
        ProcessingStrategy oldStrategy = this.strategy;
        this.strategy = strategy;

        logger.info("처리 전략 변경: {} -> {}", oldStrategy, strategy);
    }

    /**
     * 프로세서 통계 조회
     */
    public ProcessorStats getStats() {
        return new ProcessorStats(
                processedRequests.get(),
                asyncOperations.get(),
                concurrentRequests.get(),
                averageProcessingTime.get(),
                strategy,
                threadPool.getActiveCount(),
                threadPool.getPoolSize()
        );
    }

    /**
     * 프로세서 종료
     */
    public void shutdown() {
        logger.info("HybridProcessor 종료 중...");
        contextManager.shutdown();
        logger.info("HybridProcessor 종료 완료");
    }

    /**
     * 프로세서 통계 클래스
     */
    public static class ProcessorStats {
        private final long processedRequests;
        private final long asyncOperations;
        private final int concurrentRequests;
        private final long averageProcessingTime;
        private final ProcessingStrategy strategy;
        private final int activeThreads;
        private final int totalThreads;

        public ProcessorStats(long processedRequests, long asyncOperations,
                              int concurrentRequests, long averageProcessingTime,
                              ProcessingStrategy strategy, int activeThreads, int totalThreads) {
            this.processedRequests = processedRequests;
            this.asyncOperations = asyncOperations;
            this.concurrentRequests = concurrentRequests;
            this.averageProcessingTime = averageProcessingTime;
            this.strategy = strategy;
            this.activeThreads = activeThreads;
            this.totalThreads = totalThreads;
        }

        // Getters
        public long getProcessedRequests() { return processedRequests; }
        public long getAsyncOperations() { return asyncOperations; }
        public int getConcurrentRequests() { return concurrentRequests; }
        public long getAverageProcessingTime() { return averageProcessingTime; }
        public ProcessingStrategy getStrategy() { return strategy; }
        public int getActiveThreads() { return activeThreads; }
        public int getTotalThreads() { return totalThreads; }

        @Override
        public String toString() {
            return String.format(
                    "ProcessorStats{requests=%d, async=%d, concurrent=%d, " +
                            "avgTime=%dms, strategy=%s, threads=%d/%d}",
                    processedRequests, asyncOperations, concurrentRequests,
                    averageProcessingTime, strategy, activeThreads, totalThreads
            );
        }
    }
}