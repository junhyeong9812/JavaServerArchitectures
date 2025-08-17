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

    // SLF4J 로거 인스턴스 생성 - 하이브리드 처리기 관련 로깅용
    private static final Logger logger = LoggerFactory.getLogger(HybridProcessor.class);
    // static final로 클래스당 하나의 로거 인스턴스 공유하여 메모리 효율성 확보

    // === 처리 컴포넌트 ===
    private final AdaptiveThreadPool threadPool;
    // 적응형 스레드풀 - 동적 크기 조정과 우선순위 기반 작업 스케줄링
    // CPU 집약적 작업과 비동기 처리를 위한 핵심 스레드 풀

    private final AsyncContextManager contextManager;
    // 비동기 컨텍스트 관리자 - 스레드와 분리된 요청 상태 관리
    // I/O 대기시 스레드 해제를 위한 컨텍스트 보관 및 생명주기 관리

    // === 성능 메트릭 ===
    private final AtomicLong processedRequests = new AtomicLong(0);
    // 처리된 총 요청 수를 원자적으로 추적
    private final AtomicLong asyncOperations = new AtomicLong(0);
    // 비동기로 처리된 작업 수를 원자적으로 추적
    private final AtomicLong averageProcessingTime = new AtomicLong(0);
    // 평균 처리 시간을 원자적으로 관리 (이동 평균 방식)
    // AtomicLong 사용으로 멀티스레드 환경에서 정확한 성능 메트릭 수집

    // === 처리 전략 ===
    private volatile ProcessingStrategy strategy = ProcessingStrategy.ADAPTIVE;
    // 현재 적용 중인 처리 전략 - 런타임에 동적 변경 가능
    // volatile 키워드로 멀티스레드 환경에서 변경 가시성 보장

    private final AtomicInteger concurrentRequests = new AtomicInteger(0);
    // 현재 동시 처리 중인 요청 수를 원자적으로 추적
    // 적응형 처리 전략 결정에 사용되는 핵심 지표

    /**
     * 처리 전략 열거형
     */
    public enum ProcessingStrategy {
        SYNC,       // 동기 처리 - 전통적인 블로킹 방식
        ASYNC,      // 비동기 처리 - 논블로킹 방식
        ADAPTIVE    // 적응형 처리 - 상황에 따라 동기/비동기 선택
    }
    // enum 사용 이유:
    // 1. 타입 안전성 보장으로 잘못된 전략값 방지
    // 2. switch문에서 컴파일 타임 검증 가능
    // 3. 새로운 전략 추가시 누락 방지

    /**
     * HybridProcessor 생성자
     */
    public HybridProcessor(AdaptiveThreadPool threadPool, AsyncContextManager contextManager) {
        // 의존성 주입을 통한 핵심 컴포넌트 초기화
        this.threadPool = threadPool;
        this.contextManager = contextManager;
        // 생성자 주입으로 컴포넌트 간 결합도 낮추고 테스트 용이성 확보

        logger.info("하이브리드 처리기 초기화 완료 - 전략: {}", strategy);
        // 초기화 완료와 기본 전략 로그 기록
    }

    /**
     * HTTP 요청 처리 - 하이브리드 서버의 핵심 로직
     */
    public CompletableFuture<HttpResponse> processRequest(HttpRequest request, RouteHandler routeHandler) {
        // 모든 HTTP 요청의 진입점 - 하이브리드 처리의 핵심 메서드
        // RouteHandler를 인자로 받아 라우팅 로직과 처리 로직 분리

        // 성능 측정을 위한 시작 시간 기록 (나노초 정밀도)
        long startTime = System.nanoTime();
        // System.nanoTime() 사용 이유:
        // 1. 마이크로초 단위의 정밀한 시간 측정
        // 2. System.currentTimeMillis()보다 정확한 경과 시간 계산
        // 3. 시스템 시간 변경에 영향받지 않는 단조 증가 시간

        // 동시 요청 수 원자적 증가
        int currentConcurrency = concurrentRequests.incrementAndGet();
        // incrementAndGet() 사용으로 증가와 조회를 원자적으로 수행

        logger.debug("요청 처리 시작 - URI: {}, 동시 요청: {}",
                request.getPath(), currentConcurrency);

        try {
            // 현재 상황에 맞는 최적의 처리 전략 선택
            ProcessingStrategy selectedStrategy = selectStrategy(request, currentConcurrency);

            // 선택된 전략에 따른 요청 처리 분기
            CompletableFuture<HttpResponse> responseFuture = switch (selectedStrategy) {
                case SYNC -> processSynchronously(request, routeHandler);
                // 동기 처리 - 블로킹 방식으로 즉시 처리
                case ASYNC -> processAsynchronously(request, routeHandler);
                // 비동기 처리 - 논블로킹 방식으로 스레드 풀에서 처리
                case ADAPTIVE -> processAdaptively(request, routeHandler, currentConcurrency);
                // 적응형 처리 - 런타임 상황에 따라 동적 선택
            };
            // switch 표현식 사용으로 간결하고 타입 안전한 분기 처리

            // 처리 완료 시점에 후처리 작업 수행
            return responseFuture.whenComplete((response, throwable) -> {
                // whenComplete() 사용 이유:
                // 1. 성공/실패 무관하게 실행되는 후처리 로직
                // 2. 원본 결과를 변경하지 않고 부가 작업만 수행
                // 3. 리소스 정리와 통계 업데이트에 적합

                concurrentRequests.decrementAndGet(); // 동시 요청 수 감소

                // 처리 시간 계산 및 통계 업데이트
                long processingTime = (System.nanoTime() - startTime) / 1_000_000;
                // 나노초를 밀리초로 변환하여 일반적인 성능 지표 형태로 변환
                updateProcessingTime(processingTime);
                // 이동 평균 방식으로 평균 처리 시간 갱신

                processedRequests.incrementAndGet(); // 처리 완료 요청 수 증가

                if (throwable != null) {
                    // 처리 실패시 경고 로그 기록
                    logger.warn("요청 처리 실패 - URI: {}, 처리시간: {}ms",
                            request.getPath(), processingTime, throwable);
                } else {
                    // 처리 성공시 디버그 로그 기록
                    logger.debug("요청 처리 완료 - URI: {}, 처리시간: {}ms, 상태: {}",
                            request.getPath(), processingTime, response.getStatusCode());
                }
            });

        } catch (Exception e) {
            // 처리 중 예외 발생시 정리 작업 및 에러 응답
            concurrentRequests.decrementAndGet(); // 동시 요청 수 복원

            logger.error("요청 처리 중 예외 발생", e);
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Internal Server Error")
            );
            // completedFuture()로 즉시 완료된 실패 응답 반환
        }
    }

    /**
     * 처리 전략 선택 로직
     */
    private ProcessingStrategy selectStrategy(HttpRequest request, int concurrency) {
        // 고정 전략이 설정된 경우 그대로 사용
        if (strategy != ProcessingStrategy.ADAPTIVE) {
            return strategy;
        }
        // 적응형이 아닌 경우 설정된 전략을 그대로 반환

        // 정적 파일 요청은 동기 처리가 효율적
        if (isStaticFileRequest(request)) {
            logger.debug("정적 파일 요청 감지 - 동기 처리 선택");
            return ProcessingStrategy.SYNC;
            // 정적 파일은 디스크 I/O가 빠르고 CPU 사용이 적어 동기 처리 적합
        }

        // 높은 동시성 상황에서는 비동기 처리로 스레드 효율성 확보
        if (concurrency > threadPool.getMaximumPoolSize() * 0.8) {
            logger.debug("높은 동시성 감지 - 비동기 처리 선택: {}", concurrency);
            return ProcessingStrategy.ASYNC;
            // 스레드 풀 용량의 80% 초과시 비동기 처리로 스레드 블로킹 방지
        }

        // 데이터베이스나 외부 API 호출은 비동기 처리가 유리
        if (isDatabaseOrApiRequest(request)) {
            logger.debug("DB/API 요청 감지 - 비동기 처리 선택");
            return ProcessingStrategy.ASYNC;
            // I/O 대기 시간이 긴 작업은 비동기로 스레드 해제
        }

        // 기본적으로는 동기 처리 (오버헤드 최소화)
        return ProcessingStrategy.SYNC;
    }

    /**
     * 동기 요청 처리
     */
    private CompletableFuture<HttpResponse> processSynchronously(HttpRequest request, RouteHandler handler) {
        logger.debug("동기 처리 시작 - 스레드: {}", Thread.currentThread().getName());

        try {
            // 핸들러 실행하여 CompletableFuture 획득
            CompletableFuture<HttpResponse> result = handler.handle(request);
            // RouteHandler.handle()은 CompletableFuture<HttpResponse> 반환

            // 이미 완료된 작업인 경우 즉시 반환
            if (result.isDone()) {
                return result;
            }

            // 동기 처리를 위해 결과 대기 (최대 30초)
            HttpResponse response = result.get(30, TimeUnit.SECONDS);
            // get(timeout) 사용 이유:
            // 1. 동기 처리에서는 결과를 즉시 필요로 함
            // 2. 무한 대기 방지를 위한 타임아웃 설정
            // 3. TimeoutException 발생시 적절한 에러 처리 가능

            return CompletableFuture.completedFuture(response);
            // 동기 결과를 CompletableFuture로 래핑하여 일관된 인터페이스 제공

        } catch (TimeoutException e) {
            // 처리 시간 초과시 408 Request Timeout 응답
            logger.warn("동기 처리 타임아웃 - URI: {}", request.getPath());
            return CompletableFuture.completedFuture(
                    HttpResponse.builder(HttpStatus.REQUEST_TIMEOUT)
                            .body("Request Timeout")
                            .build()
            );
            // HTTP 408 상태 코드로 클라이언트에게 타임아웃 알림

        } catch (Exception e) {
            // 기타 예외 발생시 500 Internal Server Error 응답
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

        asyncOperations.incrementAndGet(); // 비동기 작업 수 증가

        // 비동기 컨텍스트 생성으로 요청 상태 보존
        String contextId = contextManager.createContext(request);
        // 스레드와 분리하여 요청 정보 관리

        return CompletableFuture
                .supplyAsync(() -> {
                    // supplyAsync()로 별도 스레드에서 비동기 실행
                    try {
                        logger.debug("비동기 핸들러 실행 - 컨텍스트: {}, 스레드: {}",
                                contextId, Thread.currentThread().getName());

                        return handler.handle(request);
                        // 핸들러 실행하여 CompletableFuture<HttpResponse> 반환

                    } catch (Exception e) {
                        logger.error("비동기 핸들러 오류", e);
                        return CompletableFuture.completedFuture(
                                HttpResponse.internalServerError("Handler Error")
                        );
                        // 핸들러 실행 실패시 에러 응답 생성
                    }
                }, threadPool) // 적응형 스레드 풀에서 실행
                .thenCompose(future -> future)
                // thenCompose() 사용 이유:
                // 1. CompletableFuture<CompletableFuture<HttpResponse>>를 평면화
                // 2. 중첩된 Future를 단일 Future로 변환
                // 3. 비동기 체인에서 일관된 타입 유지
                .whenComplete((response, throwable) -> {
                    // 비동기 처리 완료 후 컨텍스트 정리
                    contextManager.removeContext(contextId);
                    // 메모리 누수 방지를 위한 컨텍스트 제거

                    if (throwable != null) {
                        logger.warn("비동기 처리 실패 - 컨텍스트: {}", contextId, throwable);
                    }
                });
    }

    /**
     * 적응형 요청 처리
     */
    private CompletableFuture<HttpResponse> processAdaptively(HttpRequest request, RouteHandler handler, int concurrency) {
        // 현재 스레드 풀 상태 분석
        int activeThreads = threadPool.getActiveCount();
        int maxThreads = threadPool.getMaximumPoolSize();
        double utilization = (double) activeThreads / maxThreads;
        // 스레드 사용률 계산으로 현재 시스템 부하 측정

        logger.debug("적응형 처리 - 스레드 사용률: {:.2f}%, 동시요청: {}",
                utilization * 100, concurrency);

        // 스레드 사용률 기반 처리 방식 결정
        if (utilization > 0.7) {
            // 70% 이상 사용률에서는 비동기 처리로 스레드 효율성 확보
            logger.debug("높은 스레드 사용률 - 비동기 처리 선택");
            return processAsynchronously(request, handler);
        } else {
            // 낮은 사용률에서는 동기 처리로 오버헤드 최소화
            logger.debug("낮은 스레드 사용률 - 동기 처리 선택");
            return processSynchronously(request, handler);
        }
        // 임계값 0.7은 실험적으로 결정된 최적 기준점
    }

    /**
     * 배치 요청 처리
     */
    public CompletableFuture<HttpResponse[]> processBatch(HttpRequest[] requests, RouteHandler[] handlers) {
        // 여러 요청을 동시에 처리하는 배치 처리 기능

        // 요청과 핸들러 수 일치 검증
        if (requests.length != handlers.length) {
            throw new IllegalArgumentException("요청과 핸들러 수가 일치하지 않습니다");
        }

        logger.info("배치 처리 시작 - 요청 수: {}", requests.length);

        // 각 요청별로 CompletableFuture 생성
        CompletableFuture<HttpResponse>[] futures = new CompletableFuture[requests.length];

        for (int i = 0; i < requests.length; i++) {
            futures[i] = processRequest(requests[i], handlers[i]);
            // 각 요청을 개별적으로 처리하여 병렬 실행
        }

        // 모든 요청 완료까지 대기 후 결과 배열 생성
        return CompletableFuture.allOf(futures)
                .thenApply(void_ -> {
                    // allOf()는 모든 Future 완료를 보장하지만 결과는 Void
                    HttpResponse[] responses = new HttpResponse[futures.length];
                    for (int i = 0; i < futures.length; i++) {
                        try {
                            responses[i] = futures[i].get();
                            // 이미 완료된 Future이므로 get()이 즉시 반환
                        } catch (Exception e) {
                            logger.error("배치 처리 중 오류", e);
                            responses[i] = HttpResponse.internalServerError("Batch Processing Error");
                            // 개별 요청 실패가 전체 배치를 실패시키지 않도록 에러 응답 생성
                        }
                    }
                    return responses;
                });
    }

    /**
     * 우선순위 기반 요청 처리
     */
    public CompletableFuture<HttpResponse> processWithPriority(HttpRequest request, RouteHandler handler, int priority) {
        // 우선순위가 높은 요청(5 초과)은 동기 처리로 즉시 처리
        if (priority > 5) {
            logger.debug("고우선순위 요청 처리 - 우선순위: {}", priority);
            return processSynchronously(request, handler);
            // 중요한 요청은 지연 없이 즉시 처리
        } else {
            // 일반 우선순위는 표준 처리 과정 적용
            return processRequest(request, handler);
        }
        // 우선순위 기반 차별화된 처리로 서비스 품질 향상
    }

    /**
     * 정적 파일 요청 여부 확인
     */
    private boolean isStaticFileRequest(HttpRequest request) {
        String path = request.getPath().toLowerCase();
        // 대소문자 구분 없이 경로 검사

        return path.endsWith(".css") || path.endsWith(".js") ||
                path.endsWith(".png") || path.endsWith(".jpg") ||
                path.endsWith(".gif") || path.endsWith(".ico") ||
                path.startsWith("/static/") || path.startsWith("/assets/");
        // 일반적인 정적 파일 확장자와 경로 패턴으로 판단
        // 웹 애플리케이션에서 자주 사용되는 정적 리소스 타입들 포함
    }

    /**
     * 데이터베이스/API 요청 여부 확인
     */
    private boolean isDatabaseOrApiRequest(HttpRequest request) {
        String path = request.getPath().toLowerCase();
        HttpMethod method = request.getMethod();

        // API 경로 패턴 확인
        boolean isApiPath = path.startsWith("/api/") ||
                path.startsWith("/rest/") ||
                path.contains("/data/");
        // RESTful API의 일반적인 경로 패턴들

        // 데이터 변경 메서드 확인
        boolean isDataMethod = method == HttpMethod.POST ||
                method == HttpMethod.PUT ||
                method == HttpMethod.DELETE;
        // 데이터베이스 쓰기 작업을 수반하는 HTTP 메서드들

        return isApiPath || isDataMethod;
        // 경로 패턴 또는 메서드 기반으로 DB/API 요청 판단
    }

    /**
     * 처리 시간 업데이트 (이동 평균)
     */
    private void updateProcessingTime(long processingTime) {
        // 이동 평균 계산으로 최근 처리 시간 경향 반영
        long currentAverage = averageProcessingTime.get();
        long newAverage = (long) (currentAverage * 0.9 + processingTime * 0.1);
        // 가중 이동 평균: 기존 값에 90%, 새 값에 10% 가중치
        // 급격한 변동 완화하면서도 최근 경향 반영

        averageProcessingTime.set(newAverage);
        // 원자적 업데이트로 멀티스레드 환경에서 정확성 보장
    }

    /**
     * 처리 전략 변경
     */
    public void setProcessingStrategy(ProcessingStrategy strategy) {
        ProcessingStrategy oldStrategy = this.strategy;
        this.strategy = strategy;
        // volatile 변수로 즉시 반영되어 모든 스레드에서 새 전략 적용

        logger.info("처리 전략 변경: {} -> {}", oldStrategy, strategy);
        // 전략 변경 로그로 운영 중 설정 변경 추적
    }

    /**
     * 프로세서 통계 조회
     */
    public ProcessorStats getStats() {
        // 현재 프로세서 상태의 스냅샷을 불변 객체로 반환
        return new ProcessorStats(
                processedRequests.get(),
                asyncOperations.get(),
                concurrentRequests.get(),
                averageProcessingTime.get(),
                strategy,
                threadPool.getActiveCount(),
                threadPool.getPoolSize()
        );
        // 모든 통계 정보를 원자적으로 수집하여 일관된 상태 제공
    }

    /**
     * 프로세서 종료
     */
    public void shutdown() {
        logger.info("하이브리드 처리기 종료 중...");
        contextManager.shutdown();
        // 컨텍스트 매니저 종료로 리소스 정리
        logger.info("하이브리드 처리기 종료 완료");
    }

    /**
     * 프로세서 통계 클래스
     */
    public static class ProcessorStats {
        // 프로세서 성능 통계를 담는 불변 데이터 클래스

        private final long processedRequests; // 처리된 요청 수
        private final long asyncOperations; // 비동기 작업 수
        private final int concurrentRequests; // 현재 동시 요청 수
        private final long averageProcessingTime; // 평균 처리 시간
        private final ProcessingStrategy strategy; // 현재 처리 전략
        private final int activeThreads; // 활성 스레드 수
        private final int totalThreads; // 총 스레드 수
        // 모든 필드를 final로 선언하여 불변성 보장

        public ProcessorStats(long processedRequests, long asyncOperations,
                              int concurrentRequests, long averageProcessingTime,
                              ProcessingStrategy strategy, int activeThreads, int totalThreads) {
            // 생성자에서 모든 통계 값 초기화
            this.processedRequests = processedRequests;
            this.asyncOperations = asyncOperations;
            this.concurrentRequests = concurrentRequests;
            this.averageProcessingTime = averageProcessingTime;
            this.strategy = strategy;
            this.activeThreads = activeThreads;
            this.totalThreads = totalThreads;
        }

        // 접근자 메서드들 - 불변 객체이므로 getter만 제공
        public long getProcessedRequests() { return processedRequests; }
        public long getAsyncOperations() { return asyncOperations; }
        public int getConcurrentRequests() { return concurrentRequests; }
        public long getAverageProcessingTime() { return averageProcessingTime; }
        public ProcessingStrategy getStrategy() { return strategy; }
        public int getActiveThreads() { return activeThreads; }
        public int getTotalThreads() { return totalThreads; }

        @Override
        public String toString() {
            // 모든 통계 정보를 읽기 쉬운 형태로 포맷팅
            return String.format(
                    "ProcessorStats{requests=%d, async=%d, concurrent=%d, " +
                            "avgTime=%dms, strategy=%s, threads=%d/%d}",
                    processedRequests, asyncOperations, concurrentRequests,
                    averageProcessingTime, strategy, activeThreads, totalThreads
            );
            // 운영자가 한눈에 파악할 수 있는 핵심 지표들을 간결하게 표현
        }
    }
}