package server.hybrid;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.http.*;
import server.core.routing.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

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
    private final AdaptiveThreadPool threadPool;         // 적응형 스레드풀
    private final AsyncContextManager contextManager;    // 비동기 컨텍스트 관리자

    // === 성능 메트릭 ===
    private final AtomicLong processedRequests = new AtomicLong(0);      // 처리된 요청 수
    private final AtomicLong asyncOperations = new AtomicLong(0);        // 비동기 작업 수
    private final AtomicLong averageProcessingTime = new AtomicLong(0);  // 평균 처리 시간

    // === 처리 전략 ===
    private volatile ProcessingStrategy strategy = ProcessingStrategy.ADAPTIVE; // 처리 전략
    private final AtomicInteger concurrentRequests = new AtomicInteger(0);       // 동시 요청 수

    /**
     * 처리 전략 열거형
     */
    public enum ProcessingStrategy {
        SYNC,       // 동기 처리 - 단순한 요청에 적합
        ASYNC,      // 비동기 처리 - I/O 집약적 요청에 적합
        ADAPTIVE    // 적응형 처리 - 요청 특성에 따라 자동 선택
    }

    /**
     * HybridProcessor 생성자
     *
     * @param threadPool 스레드풀
     * @param contextManager 컨텍스트 관리자
     */
    public HybridProcessor(AdaptiveThreadPool threadPool, AsyncContextManager contextManager) {
        this.threadPool = threadPool;
        this.contextManager = contextManager;

        logger.info("HybridProcessor 초기화 완료 - 전략: {}", strategy);
    }

    /**
     * HTTP 요청 처리 - 하이브리드 서버의 핵심 로직
     *
     * @param request HTTP 요청
     * @param routeHandler 라우트 핸들러
     * @return CompletableFuture<HttpResponse> 비동기 응답
     */
    public CompletableFuture<HttpResponse> processRequest(HttpRequest request, RouteHandler routeHandler) {
        // 처리 시작 시간 기록
        long startTime = System.nanoTime();

        // 동시 요청 수 증가
        int currentConcurrency = concurrentRequests.incrementAndGet();

        logger.debug("요청 처리 시작 - URI: {}, 동시 요청: {}",
                request.getPath(), currentConcurrency);

        try {
            // 처리 전략 결정
            ProcessingStrategy selectedStrategy = selectStrategy(request, currentConcurrency);

            // 전략별 처리 분기
            CompletableFuture<HttpResponse> responseFuture = switch (selectedStrategy) {
                case SYNC -> processSynchronously(request, routeHandler);
                case ASYNC -> processAsynchronously(request, routeHandler);
                case ADAPTIVE -> processAdaptively(request, routeHandler, currentConcurrency);
            };

            // 처리 완료 후 후처리
            return responseFuture.whenComplete((response, throwable) -> {
                // 동시 요청 수 감소
                concurrentRequests.decrementAndGet();

                // 처리 시간 계산 및 기록
                long processingTime = (System.nanoTime() - startTime) / 1_000_000; // 밀리초 변환
                updateProcessingTime(processingTime);

                // 처리된 요청 수 증가
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
            // 예외 발생시 동시 요청 수 즉시 감소
            concurrentRequests.decrementAndGet();

            logger.error("요청 처리 중 예외 발생", e);
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Internal Server Error")
            );
        }
    }

    /**
     * 처리 전략 선택 로직
     *
     * @param request HTTP 요청
     * @param concurrency 현재 동시 요청 수
     * @return 선택된 처리 전략
     */
    private ProcessingStrategy selectStrategy(HttpRequest request, int concurrency) {
        // 명시적 전략이 설정된 경우
        if (strategy != ProcessingStrategy.ADAPTIVE) {
            return strategy;
        }

        // 적응형 전략 - 요청 특성 분석

        // 1. 정적 파일 요청 - 동기 처리가 효율적
        if (isStaticFileRequest(request)) {
            logger.debug("정적 파일 요청 감지 - 동기 처리 선택");
            return ProcessingStrategy.SYNC;
        }

        // 2. 높은 동시성 - 비동기 처리로 스레드 절약
        if (concurrency > threadPool.getMaximumPoolSize() * 0.8) {
            logger.debug("높은 동시성 감지 - 비동기 처리 선택: {}", concurrency);
            return ProcessingStrategy.ASYNC;
        }

        // 3. 데이터베이스/API 요청 추정 - 비동기 처리
        if (isDatabaseOrApiRequest(request)) {
            logger.debug("DB/API 요청 감지 - 비동기 처리 선택");
            return ProcessingStrategy.ASYNC;
        }

        // 4. 기본값 - 동기 처리
        return ProcessingStrategy.SYNC;
    }

    /**
     * 동기 요청 처리
     * 현재 스레드에서 직접 처리 (Thread Pool 워커 스레드)
     *
     * @param request HTTP 요청
     * @param handler 라우트 핸들러
     * @return 동기 완료된 CompletableFuture
     */
    private CompletableFuture<HttpResponse> processSynchronously(HttpRequest request, RouteHandler handler) {
        logger.debug("동기 처리 시작 - 스레드: {}", Thread.currentThread().getName());

        try {
            // 핸들러 직접 호출 - 현재 스레드에서 블로킹
            CompletableFuture<HttpResponse> result = handler.handle(request);

            // 이미 완료된 Future인지 확인
            if (result.isDone()) {
                return result;
            }

            // 아직 완료되지 않은 경우 동기적으로 대기
            // get() - 블로킹 호출, 결과 대기
            HttpResponse response = result.get(30, TimeUnit.SECONDS); // 30초 타임아웃
            return CompletableFuture.completedFuture(response);

        } catch (TimeoutException e) {
            logger.warn("동기 처리 타임아웃 - URI: {}", request.getPath());
            return CompletableFuture.completedFuture(
                    HttpResponse.requestTimeout("Request Timeout")
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
     * AsyncContext를 통해 스레드 해제 후 별도 처리
     *
     * @param request HTTP 요청
     * @param handler 라우트 핸들러
     * @return 비동기 CompletableFuture
     */
    private CompletableFuture<HttpResponse> processAsynchronously(HttpRequest request, RouteHandler handler) {
        logger.debug("비동기 처리 시작 - 스레드: {}", Thread.currentThread().getName());

        // 비동기 작업 수 증가
        asyncOperations.incrementAndGet();

        // AsyncContext 생성 및 등록
        String contextId = contextManager.createContext(request);

        // CompletableFuture 체인으로 비동기 처리
        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        logger.debug("비동기 핸들러 실행 - 컨텍스트: {}, 스레드: {}",
                                contextId, Thread.currentThread().getName());

                        // 핸들러 호출
                        return handler.handle(request);

                    } catch (Exception e) {
                        logger.error("비동기 핸들러 오류", e);
                        return CompletableFuture.completedFuture(
                                HttpResponse.internalServerError("Handler Error")
                        );
                    }
                }, threadPool) // 스레드풀에서 실행
                .thenCompose(future -> future) // CompletableFuture<CompletableFuture<T>> -> CompletableFuture<T>
                .whenComplete((response, throwable) -> {
                    // 컨텍스트 정리
                    contextManager.removeContext(contextId);

                    if (throwable != null) {
                        logger.warn("비동기 처리 실패 - 컨텍스트: {}", contextId, throwable);
                    }
                });
    }

    /**
     * 적응형 요청 처리
     * 시스템 상태에 따라 동적으로 처리 방식 결정
     *
     * @param request HTTP 요청
     * @param handler 라우트 핸들러
     * @param concurrency 현재 동시 요청 수
     * @return CompletableFuture
     */
    private CompletableFuture<HttpResponse> processAdaptively(HttpRequest request, RouteHandler handler, int concurrency) {
        // 스레드풀 상태 확인
        int activeThreads = threadPool.getActiveCount();
        int maxThreads = threadPool.getMaximumPoolSize();
        double utilization = (double) activeThreads / maxThreads;

        logger.debug("적응형 처리 - 스레드 사용률: {:.2f}%, 동시요청: {}",
                utilization * 100, concurrency);

        // 스레드풀 사용률이 높으면 비동기, 낮으면 동기
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
     * 여러 요청을 한 번에 처리하여 효율성 향상
     *
     * @param requests 요청 배치
     * @param handlers 핸들러 배치
     * @return 응답 배치
     */
    public CompletableFuture<HttpResponse[]> processBatch(HttpRequest[] requests, RouteHandler[] handlers) {
        if (requests.length != handlers.length) {
            throw new IllegalArgumentException("요청과 핸들러 수가 일치하지 않습니다");
        }

        logger.info("배치 처리 시작 - 요청 수: {}", requests.length);

        // 모든 요청을 병렬로 처리
        CompletableFuture<HttpResponse>[] futures = new CompletableFuture[requests.length];

        for (int i = 0; i < requests.length; i++) {
            futures[i] = processRequest(requests[i], handlers[i]);
        }

        // 모든 처리가 완료될 때까지 대기
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
     * 중요한 요청을 먼저 처리
     *
     * @param request HTTP 요청
     * @param handler 라우트 핸들러
     * @param priority 우선순위 (높을수록 우선)
     * @return CompletableFuture
     */
    public CompletableFuture<HttpResponse> processWithPriority(HttpRequest request, RouteHandler handler, int priority) {
        // 우선순위 기반 스레드풀 사용
        if (priority > 5) {
            // 높은 우선순위 - 즉시 처리
            logger.debug("고우선순위 요청 처리 - 우선순위: {}", priority);
            return processSynchronously(request, handler);
        } else {
            // 일반 우선순위 - 일반 처리
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

        // API 경로 패턴
        boolean isApiPath = path.startsWith("/api/") ||
                path.startsWith("/rest/") ||
                path.contains("/data/");

        // 데이터 변경 메서드
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

        // 간단한 이동 평균 계산 (가중치 0.1)
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

        // 컨텍스트 관리자 종료
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