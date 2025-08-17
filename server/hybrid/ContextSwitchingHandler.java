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

    // SLF4J 로거 인스턴스 생성 - 컨텍스트 스위칭 관련 로깅용
    private static final Logger logger = LoggerFactory.getLogger(ContextSwitchingHandler.class);
    // static final로 클래스당 하나의 로거 인스턴스 공유

    // === 핵심 컴포넌트 ===
    // 비동기 작업 실행을 위한 적응형 스레드풀
    private final AdaptiveThreadPool threadPool;
    // 커스텀 스레드풀로 우선순위 기반 작업 스케줄링 지원

    // 비동기 컨텍스트 생명주기 관리를 위한 매니저
    private final AsyncContextManager contextManager;
    // 스레드와 분리된 요청 상태 정보 보관 및 관리

    // === 컨텍스트 스위칭 통계 ===
    // 성능 모니터링을 위한 원자적 카운터들
    private final AtomicLong totalSwitchOuts = new AtomicLong(0); // 총 Switch Out 횟수
    private final AtomicLong totalSwitchIns = new AtomicLong(0); // 총 Switch In 횟수
    private final AtomicLong activeSwitchedContexts = new AtomicLong(0); // 현재 스위치된 컨텍스트 수
    // AtomicLong 사용으로 멀티스레드 환경에서 정확한 통계 유지

    // === 성능 모니터링 ===
    private final AtomicLong totalSwitchTime = new AtomicLong(0); // 총 스위치 시간 (나노초)
    private final AtomicLong switchTimeouts = new AtomicLong(0); // 스위치 타임아웃 횟수
    // 스위치 성능과 안정성 모니터링을 위한 메트릭

    // === 설정 ===
    // 런타임에 조정 가능한 설정값들
    private volatile long defaultSwitchTimeoutMs = 30000; // 기본 스위치 타임아웃 30초
    private volatile int maxConcurrentSwitches = 1000; // 최대 동시 스위치 수 1000개
    // volatile 키워드로 설정 변경의 즉시 반영 보장

    /**
     * ContextSwitchingHandler 생성자
     */
    public ContextSwitchingHandler(AdaptiveThreadPool threadPool, AsyncContextManager contextManager) {
        // 의존성 주입으로 필요한 컴포넌트들 초기화
        this.threadPool = threadPool;
        this.contextManager = contextManager;

        // 초기화 완료 로그 출력
        logger.info("컨텍스트 스위칭 핸들러 초기화 - 최대 스위치: {}, 타임아웃: {}ms",
                maxConcurrentSwitches, defaultSwitchTimeoutMs);
        // 운영 환경에서 초기 설정 확인용 로그
    }

    /**
     * 비동기 처리 with 컨텍스트 스위칭
     */
    public <T> CompletableFuture<T> switchAndExecute(HttpRequest request,
                                                     Supplier<CompletableFuture<T>> asyncOperation) {
        // 제네릭 메서드로 다양한 반환 타입 지원
        // Supplier<CompletableFuture<T>>로 지연 실행과 비동기 결과 조합

        // Context Switch Out 수행 - 현재 스레드에서 컨텍스트 분리
        SwitchContext switchContext = switchOut(request);
        // 요청 정보를 보존하면서 스레드 자원 해제 준비

        try {
            // 비동기 작업 실행 - Supplier를 통한 지연 실행
            CompletableFuture<T> operationFuture = asyncOperation.get();
            // get() 호출로 실제 비동기 작업 시작

            // 작업 완료시 Context Switch In 수행
            return operationFuture.whenCompleteAsync((result, throwable) -> {
                switchIn(switchContext, result, throwable);
                // 비동기 작업 완료 후 컨텍스트 복원 및 스레드 재할당
            }, threadPool);
            // threadPool 지정으로 스위치 인 작업을 별도 스레드에서 실행

        } catch (Exception e) {
            // 비동기 작업 시작 실패시 즉시 스위치 인 (오류와 함께)
            switchInWithError(switchContext, e);
            return CompletableFuture.failedFuture(e);
            // 실패한 CompletableFuture 반환으로 호출자에게 오류 전파
        }
    }

    /**
     * 데이터베이스 작업 with 컨텍스트 스위칭
     */
    public CompletableFuture<String> executeDbOperation(HttpRequest request,
                                                        Function<HttpRequest, String> dbOperation) {
        // DB 작업 특화 메서드 - 반환 타입을 String으로 고정
        // Function<HttpRequest, String>으로 요청 기반 DB 작업 정의

        return switchAndExecute(request, () ->
                        // 람다식으로 비동기 작업 정의
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                // DB 작업 시작 로그
                                logger.debug("DB 작업 시작 - URI: {}, 스레드: {}",
                                        request.getPath(), Thread.currentThread().getName());

                                // 실제 DB 작업 실행
                                String result = dbOperation.apply(request);
                                // Function.apply()로 요청을 인자로 DB 작업 수행

                                // DB 작업 완료 로그
                                logger.debug("DB 작업 완료 - 결과 크기: {}", result.length());
                                return result; // 결과 반환

                            } catch (Exception e) {
                                // DB 작업 중 예외 발생시 로그 기록 후 런타임 예외로 래핑
                                logger.error("DB 작업 실패", e);
                                throw new RuntimeException("Database operation failed", e);
                                // 체크 예외를 언체크 예외로 변환하여 CompletableFuture 호환
                            }
                        }, threadPool)
                // supplyAsync()로 별도 스레드에서 DB 작업 실행
        );
    }

    /**
     * HTTP API 호출 with 컨텍스트 스위칭
     */
    public CompletableFuture<String> executeApiCall(HttpRequest request,
                                                    Function<HttpRequest, String> apiCall) {
        // API 호출 특화 메서드 - DB 작업과 유사한 패턴

        return switchAndExecute(request, () ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        // API 호출 시작 로그
                        logger.debug("API 호출 시작 - URI: {}, 스레드: {}",
                                request.getPath(), Thread.currentThread().getName());

                        // 실제 API 호출 실행
                        String result = apiCall.apply(request);

                        // API 호출 완료 로그
                        logger.debug("API 호출 완료 - 응답 크기: {}", result.length());
                        return result;

                    } catch (Exception e) {
                        // API 호출 실패시 오류 처리
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
        // 파일 작업 특화 메서드 - 바이너리 데이터 반환 (byte[])

        return switchAndExecute(request, () ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        // 파일 작업 시작 로그
                        logger.debug("파일 작업 시작 - URI: {}, 스레드: {}",
                                request.getPath(), Thread.currentThread().getName());

                        // 실제 파일 작업 실행
                        byte[] result = fileOperation.apply(request);

                        // 파일 작업 완료 로그
                        logger.debug("파일 작업 완료 - 크기: {} bytes", result.length);
                        return result;

                    } catch (Exception e) {
                        // 파일 작업 실패시 오류 처리
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
        // 현재 활성 스위치 수 확인 - 과부하 방지
        long currentSwitches = activeSwitchedContexts.get();
        if (currentSwitches >= maxConcurrentSwitches) {
            // 최대 동시 스위치 수 초과시 예외 발생
            logger.warn("최대 동시 스위치 수 초과: {}", currentSwitches);
            throw new RuntimeException("Too many concurrent context switches");
            // 백프레셔 메커니즘으로 시스템 보호
        }

        // 고유한 스위치 ID 생성
        long switchId = totalSwitchOuts.incrementAndGet();
        // 원자적 증가로 고유한 스위치 ID 보장

        // 비동기 컨텍스트 생성 - 요청 상태 보존
        String contextId = contextManager.createContext(request);
        // 컨텍스트 매니저를 통해 요청 정보를 스레드와 분리하여 저장

        // 스위치 컨텍스트 객체 생성 - 스위칭 메타데이터 보관
        SwitchContext switchContext = new SwitchContext(
                switchId,
                contextId,
                request,
                Thread.currentThread().getName(), // 현재 스레드 이름 기록
                System.nanoTime() // 스위치 아웃 시간을 나노초 정밀도로 기록
        );

        // 활성 스위치 수 증가
        activeSwitchedContexts.incrementAndGet();

        // 스위치 아웃 로그 기록
        logger.debug("Context Switch Out - ID: {}, 요청: {}, 스레드: {}",
                switchId, request.getPath(), Thread.currentThread().getName());

        return switchContext; // 스위치 컨텍스트 반환
    }

    /**
     * Context Switch In - 새로운 스레드에서 컨텍스트 복원
     */
    private <T> void switchIn(SwitchContext switchContext, T result, Throwable throwable) {
        try {
            // 스위치 소요 시간 계산 (나노초)
            long switchTimeNanos = System.nanoTime() - switchContext.getSwitchOutTime();
            // 스위치 아웃부터 스위치 인까지의 정확한 시간 측정
            totalSwitchTime.addAndGet(switchTimeNanos);
            // 누적 스위치 시간에 원자적 추가

            // 스위치 인 통계 업데이트
            totalSwitchIns.incrementAndGet();
            activeSwitchedContexts.decrementAndGet(); // 활성 스위치 수 감소

            // 비동기 컨텍스트 정리 - 더 이상 필요 없는 컨텍스트 제거
            contextManager.removeContext(switchContext.getContextId());

            // 스위치 인 로그 기록
            logger.debug("Context Switch In - ID: {}, 시간: {}ms, 스레드: {} -> {}",
                    switchContext.getSwitchId(),
                    switchTimeNanos / 1_000_000, // 나노초를 밀리초로 변환
                    switchContext.getOriginalThread(),
                    Thread.currentThread().getName());

            // 작업 실행 중 예외 발생시 로그 기록
            if (throwable != null) {
                logger.warn("Context Switch In with error - ID: {}",
                        switchContext.getSwitchId(), throwable);
            }

        } catch (Exception e) {
            // 스위치 인 과정에서 예외 발생시 오류 로그
            logger.error("Context Switch In 실패", e);
            // 스위치 인 실패가 전체 시스템을 중단시키지 않도록 예외 처리
        }
    }

    /**
     * 오류와 함께 Context Switch In
     */
    private void switchInWithError(SwitchContext switchContext, Throwable error) {
        // 일반 스위치 인과 동일하게 처리 (결과는 null, 예외 정보 전달)
        switchIn(switchContext, null, error);
    }

    /**
     * 복합 비동기 작업 처리
     */
    @SafeVarargs // 가변 인수의 타입 안전성 보장 어노테이션
    public final CompletableFuture<Object[]> executeMultiple(HttpRequest request,
                                                             Supplier<CompletableFuture<?>>... operations) {
        // 여러 비동기 작업을 병렬로 실행하는 메서드
        // 가변 인수로 임의 개수의 작업 지원

        // 각 작업을 위한 CompletableFuture 배열 생성
        CompletableFuture<?>[] futures = new CompletableFuture[operations.length];

        // 모든 작업을 병렬로 시작
        for (int i = 0; i < operations.length; i++) {
            final int index = i; // final 변수로 람다식에서 사용 가능하도록 함
            // 각 operation을 개별적으로 처리
            futures[i] = processMultipleOperation(request, operations[index]);
        }

        // 모든 작업이 완료될 때까지 대기 후 결과 배열 생성
        return CompletableFuture.allOf(futures)
                .thenApply(void_ -> {
                    // allOf()는 모든 작업 완료시 Void 반환하므로 무시
                    Object[] results = new Object[futures.length];
                    for (int i = 0; i < futures.length; i++) {
                        try {
                            results[i] = futures[i].get(); // 각 작업의 결과 수집
                        } catch (Exception e) {
                            results[i] = e; // 예외 발생시 Exception 객체 저장
                            // 일부 작업 실패가 전체 결과를 무효화하지 않도록 함
                        }
                    }
                    return results; // 모든 결과(성공/실패 포함)를 배열로 반환
                });
    }

    /**
     * executeMultiple용 개별 작업 처리
     */
    private CompletableFuture<?> processMultipleOperation(HttpRequest request,
                                                          Supplier<CompletableFuture<?>> operation) {
        // 각 개별 작업에 대해 컨텍스트 스위칭 적용
        SwitchContext switchContext = switchOut(request);
        // 개별 작업마다 독립적인 컨텍스트 스위치 수행

        try {
            // 비동기 작업 실행
            CompletableFuture<?> operationFuture = operation.get();

            // 작업 완료시 스위치 인 수행
            return operationFuture.whenCompleteAsync((result, throwable) -> {
                switchIn(switchContext, result, throwable);
            }, threadPool);

        } catch (Exception e) {
            // 작업 시작 실패시 즉시 스위치 인
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
        // 타임아웃 기능이 추가된 컨텍스트 스위칭 메서드
        SwitchContext switchContext = switchOut(request);

        try {
            // 비동기 작업 실행
            CompletableFuture<T> operationFuture = asyncOperation.get();

            // 타임아웃 처리를 위한 별도 CompletableFuture 생성
            CompletableFuture<T> timeoutFuture = new CompletableFuture<>();

            // 지정된 시간 후 타임아웃 발생시키는 스케줄된 작업
            ScheduledFuture<?> timeoutTask = Executors.newSingleThreadScheduledExecutor()
                    .schedule(() -> {
                        switchTimeouts.incrementAndGet(); // 타임아웃 통계 업데이트
                        timeoutFuture.completeExceptionally(
                                new TimeoutException("Context switch timeout after " + timeoutMs + "ms")
                        );
                        // TimeoutException으로 타임아웃 CompletableFuture 완료
                    }, timeoutMs, TimeUnit.MILLISECONDS);

            // 원본 작업과 타임아웃 중 먼저 완료되는 것 선택
            return CompletableFuture.anyOf(operationFuture, timeoutFuture)
                    .thenCompose(result -> {
                        timeoutTask.cancel(false); // 완료되면 타임아웃 작업 취소
                        // cancel(false)로 이미 시작된 작업은 인터럽트하지 않음

                        if (result instanceof Throwable) {
                            // 예외 결과인 경우 실패한 CompletableFuture 반환
                            return CompletableFuture.failedFuture((Throwable) result);
                        } else {
                            // 정상 결과인 경우 성공한 CompletableFuture 반환
                            @SuppressWarnings("unchecked")
                            T typedResult = (T) result;
                            // 타입 안전성을 위한 캐스팅 (경고 억제)
                            return CompletableFuture.completedFuture(typedResult);
                        }
                    })
                    .whenCompleteAsync((result, throwable) -> {
                        // 최종적으로 스위치 인 수행
                        switchIn(switchContext, result, throwable);
                    }, threadPool);

        } catch (Exception e) {
            // 작업 시작 실패시 즉시 스위치 인
            switchInWithError(switchContext, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 컨텍스트 스위칭 통계 조회
     */
    public SwitchingStats getStats() {
        // 현재 상태의 스위칭 통계 객체 생성하여 반환
        return new SwitchingStats(
                totalSwitchOuts.get(), // 총 스위치 아웃 횟수
                totalSwitchIns.get(), // 총 스위치 인 횟수
                activeSwitchedContexts.get(), // 현재 활성 스위치 수
                switchTimeouts.get(), // 타임아웃 발생 횟수
                calculateAverageSwitchTime(), // 계산된 평균 스위치 시간
                maxConcurrentSwitches, // 최대 동시 스위치 설정값
                defaultSwitchTimeoutMs // 기본 타임아웃 설정값
        );
    }

    /**
     * 평균 스위치 시간 계산 (밀리초)
     */
    private long calculateAverageSwitchTime() {
        long totalSwitches = totalSwitchIns.get(); // 완료된 스위치 수
        if (totalSwitches == 0) return 0; // 0으로 나눗셈 방지

        // 총 스위치 시간을 완료된 스위치 수로 나누어 평균 계산
        return totalSwitchTime.get() / totalSwitches / 1_000_000;
        // 나노초를 밀리초로 변환 (1,000,000 나노초 = 1 밀리초)
    }

    /**
     * 스위치 타임아웃 설정
     */
    public void setSwitchTimeout(long timeoutMs) {
        this.defaultSwitchTimeoutMs = timeoutMs; // volatile 변수로 즉시 반영
        logger.info("컨텍스트 스위치 타임아웃 변경: {}ms", timeoutMs);
    }

    /**
     * 최대 동시 스위치 수 설정
     */
    public void setMaxConcurrentSwitches(int maxSwitches) {
        this.maxConcurrentSwitches = maxSwitches; // volatile 변수로 즉시 반영
        logger.info("최대 동시 스위치 수 변경: {}", maxSwitches);
    }

    /**
     * 스위치 컨텍스트 클래스
     */
    public static class SwitchContext {
        // 컨텍스트 스위칭 과정에서 필요한 메타데이터를 보관하는 클래스

        private final long switchId; // 스위치 고유 식별자
        private final String contextId; // 비동기 컨텍스트 ID
        private final HttpRequest request; // 원본 HTTP 요청
        private final String originalThread; // 원래 처리 스레드 이름
        private final long switchOutTime; // 스위치 아웃 시간 (나노초)
        // 모든 필드를 final로 선언하여 불변성 보장

        public SwitchContext(long switchId, String contextId, HttpRequest request,
                             String originalThread, long switchOutTime) {
            // 생성자에서 모든 필드 초기화
            this.switchId = switchId;
            this.contextId = contextId;
            this.request = request;
            this.originalThread = originalThread;
            this.switchOutTime = switchOutTime;
        }

        // 접근자 메서드들 - 불변 객체이므로 getter만 제공
        public long getSwitchId() { return switchId; }
        public String getContextId() { return contextId; }
        public HttpRequest getRequest() { return request; }
        public String getOriginalThread() { return originalThread; }
        public long getSwitchOutTime() { return switchOutTime; }

        @Override
        public String toString() {
            // 스위치 컨텍스트의 주요 정보를 읽기 쉬운 형태로 포맷팅
            return String.format("SwitchContext{id=%d, contextId='%s', thread='%s', uri='%s'}",
                    switchId, contextId, originalThread, request.getPath());
        }
    }

    /**
     * 스위칭 통계 클래스
     */
    public static class SwitchingStats {
        // 컨텍스트 스위칭 성능 통계를 담는 불변 데이터 클래스

        private final long totalSwitchOuts; // 총 스위치 아웃 횟수
        private final long totalSwitchIns; // 총 스위치 인 횟수
        private final long activeSwitches; // 현재 활성 스위치 수
        private final long timeouts; // 타임아웃 발생 횟수
        private final long averageSwitchTimeMs; // 평균 스위치 시간(밀리초)
        private final int maxConcurrentSwitches; // 최대 동시 스위치 설정값
        private final long defaultTimeoutMs; // 기본 타임아웃 설정값

        public SwitchingStats(long totalSwitchOuts, long totalSwitchIns, long activeSwitches,
                              long timeouts, long averageSwitchTimeMs, int maxConcurrentSwitches,
                              long defaultTimeoutMs) {
            // 생성자에서 모든 통계 값 초기화
            this.totalSwitchOuts = totalSwitchOuts;
            this.totalSwitchIns = totalSwitchIns;
            this.activeSwitches = activeSwitches;
            this.timeouts = timeouts;
            this.averageSwitchTimeMs = averageSwitchTimeMs;
            this.maxConcurrentSwitches = maxConcurrentSwitches;
            this.defaultTimeoutMs = defaultTimeoutMs;
        }

        // 접근자 메서드들
        public long getTotalSwitchOuts() { return totalSwitchOuts; }
        public long getTotalSwitchIns() { return totalSwitchIns; }
        public long getActiveSwitches() { return activeSwitches; }
        public long getTimeouts() { return timeouts; }
        public long getAverageSwitchTimeMs() { return averageSwitchTimeMs; }
        public int getMaxConcurrentSwitches() { return maxConcurrentSwitches; }
        public long getDefaultTimeoutMs() { return defaultTimeoutMs; }

        // 타임아웃 비율 계산 메서드
        public double getTimeoutRate() {
            // 전체 스위치 아웃 대비 타임아웃 비율을 백분율로 계산
            return totalSwitchOuts > 0 ? (double) timeouts / totalSwitchOuts * 100 : 0.0;
            // 0으로 나눗셈 방지 및 백분율 변환
        }

        @Override
        public String toString() {
            // 모든 통계 정보를 읽기 쉬운 형태로 포맷팅
            return String.format(
                    "SwitchingStats{switchOuts=%d, switchIns=%d, active=%d, " +
                            "timeouts=%d (%.1f%%), avgTime=%dms, maxConcurrent=%d}",
                    totalSwitchOuts, totalSwitchIns, activeSwitches,
                    timeouts, getTimeoutRate(), averageSwitchTimeMs, maxConcurrentSwitches
            );
            // 타임아웃 비율을 포함한 포괄적인 통계 정보 표시
        }
    }
}