package server.eventloop;

import server.core.http.HttpRequest;
import server.core.http.HttpResponse;
import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Random;

/**
 * EventLoop 서버용 I/O 시뮬레이션 서블릿
 * ThreadedServer의 IoSimulationServlet과 동일한 I/O 작업을 비동기로 처리
 *
 * 핵심: ThreadedServer와 정확히 동일한 Thread.sleep(100) 수행
 *
 * EventLoop 아키텍처에서 I/O 처리 원칙:
 * - 메인 EventLoop 스레드를 절대 블로킹하지 않음
 * - 모든 블로킹 I/O 작업은 별도 스레드 풀에서 수행
 * - CompletableFuture로 비동기 결과 처리
 * - 논블로킹 방식으로 높은 동시성 달성
 *
 * 시뮬레이션 대상:
 * - 데이터베이스 쿼리 (JDBC 블로킹 호출)
 * - 파일 시스템 I/O (디스크 읽기/쓰기)
 * - 외부 API 호출 (HTTP 클라이언트)
 * - 네트워크 소켓 통신
 */
public class IoSimulationServlet {

    // Logger 인스턴스 - I/O 시뮬레이션 동작 추적
    private static final Logger logger = LoggerFactory.getLogger(IoSimulationServlet.class);

    // I/O 작업용 별도 스레드 풀 (EventLoop 메인 스레드 블로킹 방지)
    // ScheduledExecutorService: 지연 실행과 스케줄링 기능이 있는 스레드 풀
    // static final: 모든 인스턴스가 하나의 스레드 풀 공유 (리소스 효율성)
    private static final ScheduledExecutorService ioExecutor = Executors.newScheduledThreadPool(
            // I/O 작업은 CPU보다 많은 스레드 사용
            // CPU 집약적 작업: CPU 코어 수와 동일
            // I/O 집약적 작업: CPU 코어 수 × 2 (I/O 대기 시간 동안 다른 작업 처리)
            Runtime.getRuntime().availableProcessors() * 2,

            // ThreadFactory: 새 스레드 생성 방식 커스터마이징
            r -> {
                // 스레드 이름을 명확하게 설정하여 디버깅 용이성 향상
                Thread t = new Thread(r, "EventLoop-IO-Worker");

                // setDaemon(true): 데몬 스레드로 설정
                // 메인 프로그램 종료시 이 스레드들도 자동 종료
                t.setDaemon(true);
                return t;
            }
    );

    // Random 인스턴스 - 시뮬레이션에서 가변 데이터 생성용
    // static: 모든 인스턴스가 공유 (Random은 스레드 안전)
    private static final Random random = new Random();

    /**
     * 핵심: ThreadedServer와 정확히 동일한 I/O 시뮬레이션
     *
     * ThreadedServer에서는 Thread.sleep(100)을 메인 스레드에서 실행하여
     * 스레드를 블로킹하지만, EventLoop에서는 별도 스레드에서 수행
     *
     * 처리 흐름:
     * 1. EventLoop 메인 스레드에서 요청 수신
     * 2. I/O 작업을 별도 스레드 풀로 위임
     * 3. 메인 스레드는 즉시 다른 요청 처리 가능
     * 4. I/O 작업 완료시 비동기적으로 응답 전송
     *
     * @param request HTTP 요청 객체
     * @return I/O 작업 결과를 담은 CompletableFuture<HttpResponse>
     */
    public CompletableFuture<HttpResponse> handleRequest(HttpRequest request) {
        // 작업 시작 시간 기록 (성능 측정용)
        long startTime = System.currentTimeMillis();

        // EventLoop 메인 스레드에서 I/O 시뮬레이션 시작 로그
        logger.debug("EventLoop 스레드에서 I/O 시뮬레이션 시작: {}",
                Thread.currentThread().getName());

        // 핵심: ThreadedServer의 IoSimulationServlet과 정확히 동일한 작업을 별도 스레드에서 수행
        // CompletableFuture.supplyAsync(): 별도 스레드에서 비동기로 값을 계산
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 핵심: ThreadedServer와 동일한 Thread.sleep(100) 수행
                // 실제 I/O 작업 (DB 쿼리, 파일 읽기, API 호출 등) 시뮬레이션
                Thread.sleep(100);

                // 추가적인 I/O 시뮬레이션 (파일 읽기/DB 쿼리 흉내)
                // StringBuilder: 효율적인 문자열 조합
                StringBuilder simulatedData = new StringBuilder();

                // 1000줄의 데이터를 읽어오는 것을 시뮬레이션
                for (int i = 0; i < 1000; i++) {
                    simulatedData.append("data-line-").append(i).append("\n");
                }

                // 총 소요 시간 계산
                long duration = System.currentTimeMillis() - startTime;

                // I/O 작업 완료 로그 (워커 스레드에서 출력)
                logger.debug("I/O 시뮬레이션이 {}ms에 완료됨, 워커 스레드: {}",
                        duration, Thread.currentThread().getName());

                // ThreadedServer와 유사한 형태의 JSON 응답
                // 서버 타입, 작업 결과, 소요 시간, 처리 스레드 등 정보 포함
                return HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io\":\"completed\",\"duration\":%d,\"dataSize\":%d,\"thread\":\"%s\",\"timestamp\":%d}",
                        duration,                              // 총 소요 시간 (밀리초)
                        simulatedData.length(),                // 시뮬레이션된 데이터 크기
                        Thread.currentThread().getName(),      // 실제 I/O 작업을 수행한 워커 스레드
                        System.currentTimeMillis()            // 응답 생성 시각
                ));

            } catch (InterruptedException e) {
                // 인터럽트 발생시 처리
                // interrupt(): 현재 스레드의 인터럽트 상태 복원
                Thread.currentThread().interrupt();

                logger.error("I/O 시뮬레이션이 인터럽트됨", e);

                // 인터럽트 에러 응답
                return HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io\":\"interrupted\",\"error\":\"%s\",\"thread\":\"%s\"}",
                        e.getMessage(),
                        Thread.currentThread().getName()
                ));
            }
        }, ioExecutor); // 두 번째 매개변수: 작업을 실행할 Executor 지정
    }

    /**
     * 가변 지연시간 I/O 시뮬레이션
     *
     * 클라이언트가 지연 시간을 조정할 수 있는 테스트용 엔드포인트
     * 다양한 I/O 성능 시나리오 테스트 가능
     *
     * URL 예시:
     * - /io-variable -> 100ms 지연 (기본값)
     * - /io-variable?delay=500 -> 500ms 지연
     * - /io-variable?delay=50 -> 50ms 지연
     *
     * @param request HTTP 요청 객체 (delay 쿼리 파라미터 포함)
     * @return 가변 지연 I/O 결과를 담은 CompletableFuture<HttpResponse>
     */
    public CompletableFuture<HttpResponse> handleVariableDelayRequest(HttpRequest request) {
        // delay 파라미터로 지연시간 조정 (기본값 100ms)
        String delayParam = request.getQueryParameter("delay");
        int delayMs = 100; // 기본 지연 시간

        try {
            if (delayParam != null) {
                // Integer.parseInt(): 문자열을 정수로 변환
                delayMs = Integer.parseInt(delayParam);

                // Math.min(), Math.max(): 값의 범위 제한
                // 10ms ~ 5초 사이로 제한하여 서버 안정성 보장
                delayMs = Math.min(Math.max(delayMs, 10), 5000);
            }
        } catch (NumberFormatException e) {
            // 잘못된 숫자 형식의 파라미터 처리
            logger.warn("잘못된 delay 파라미터: {}, 기본값 100ms 사용", delayParam);
        }

        // final 변수: 람다 표현식 내부에서 사용하기 위해 final로 선언
        final int finalDelay = delayMs;

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 사용자가 지정한 시간만큼 I/O 지연 시뮬레이션
                Thread.sleep(finalDelay);

                // 성공 응답
                return HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io\":\"completed\",\"delay\":%d,\"thread\":\"%s\"}",
                        finalDelay,                           // 실제 사용된 지연 시간
                        Thread.currentThread().getName()     // 워커 스레드 이름
                ));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io\":\"interrupted\",\"thread\":\"%s\"}",
                        Thread.currentThread().getName()
                ));
            }
        }, ioExecutor);
    }

    /**
     * 복합 I/O 시뮬레이션 (DB + 파일 + 네트워크)
     *
     * 실제 웹 애플리케이션에서 발생하는 복합적인 I/O 작업 시뮬레이션
     * 여러 종류의 I/O 작업이 순차적으로 실행되는 시나리오
     *
     * 시뮬레이션 작업:
     * 1. 데이터베이스 쿼리 (50ms)
     * 2. 파일 시스템 읽기 (30ms)
     * 3. 외부 API 호출 (20ms)
     *
     * @param request HTTP 요청 객체
     * @return 복합 I/O 작업 결과를 담은 CompletableFuture<HttpResponse>
     */
    public CompletableFuture<HttpResponse> handleComplexIoRequest(HttpRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // 1. 데이터베이스 쿼리 시뮬레이션
                Thread.sleep(50);
                // random.nextInt(1000): 0~999 사이의 랜덤 정수
                String dbResult = "SELECT * FROM users WHERE active = 1; -- " + random.nextInt(1000) + " rows";

                // 2. 파일 읽기 시뮬레이션
                Thread.sleep(30);
                String fileData = "File content with " + random.nextInt(10000) + " bytes";

                // 3. 외부 API 호출 시뮬레이션
                Thread.sleep(20);
                String apiResponse = "{'status': 'success', 'data': " + random.nextInt(100) + "}";

                // 총 소요 시간 계산
                long totalDuration = System.currentTimeMillis() - startTime;

                // 복합 I/O 작업 결과를 포함한 JSON 응답
                return HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io\":\"complex_completed\",\"operations\":{\"database\":\"%s\",\"file\":\"%s\",\"api\":\"%s\"},\"total_duration\":%d,\"thread\":\"%s\"}",
                        dbResult,                            // DB 쿼리 결과
                        fileData,                            // 파일 읽기 결과
                        apiResponse,                         // API 호출 결과
                        totalDuration,                       // 총 소요 시간
                        Thread.currentThread().getName()    // 워커 스레드 이름
                ));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io\":\"complex_interrupted\",\"thread\":\"%s\"}",
                        Thread.currentThread().getName()
                ));
            }
        }, ioExecutor);
    }

    /**
     * 비동기 체인 I/O 시뮬레이션 (CompletableFuture 체인)
     *
     * 복잡한 비동기 워크플로우 시뮬레이션
     * 이전 단계의 결과가 다음 단계의 입력이 되는 파이프라인 처리
     *
     * 처리 단계:
     * 1. 첫 번째 I/O 작업 (50ms) -> "step1_data"
     * 2. 두 번째 I/O 작업 (30ms) -> "step1_data_step2_data"
     * 3. 세 번째 I/O 작업 (20ms) -> "step1_data_step2_data_step3_data"
     *
     * CompletableFuture 체이닝의 장점:
     * - 각 단계가 독립적으로 비동기 실행
     * - 이전 단계 완료 후 자동으로 다음 단계 시작
     * - 에러 전파와 예외 처리 자동화
     *
     * @param request HTTP 요청 객체
     * @return 비동기 체인 처리 결과를 담은 CompletableFuture<HttpResponse>
     */
    public CompletableFuture<HttpResponse> handleAsyncChainRequest(HttpRequest request) {
        return CompletableFuture
                // 첫 번째 비동기 작업
                .supplyAsync(() -> {
                    try {
                        Thread.sleep(50); // 첫 번째 I/O
                        return "step1_data";
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }, ioExecutor)

                // thenComposeAsync(): 이전 결과를 받아서 새로운 CompletableFuture 반환
                // 체이닝의 핵심: 이전 단계의 결과가 다음 단계의 입력이 됨
                .thenComposeAsync(step1Data ->
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                Thread.sleep(30); // 두 번째 I/O
                                return step1Data + "_step2_data";
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        }, ioExecutor)
                )

                // 세 번째 체인 단계
                .thenComposeAsync(step2Data ->
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                Thread.sleep(20); // 세 번째 I/O
                                return step2Data + "_step3_data";
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        }, ioExecutor)
                )

                // thenApply(): 최종 결과를 HTTP 응답으로 변환
                // 체인의 마지막 단계에서 실제 응답 생성
                .thenApply(finalData ->
                        HttpResponse.json(String.format(
                                "{\"server\":\"eventloop\",\"io\":\"async_chain_completed\",\"result\":\"%s\",\"thread\":\"%s\"}",
                                finalData,                          // 체인 전체의 최종 결과
                                Thread.currentThread().getName()   // 최종 처리 스레드
                        ))
                )

                // exceptionally(): 체인 중 어느 단계에서든 예외 발생시 처리
                // 체인의 장점: 하나의 에러 핸들러로 모든 단계의 예외 처리
                .exceptionally(throwable ->
                        HttpResponse.json(String.format(
                                "{\"server\":\"eventloop\",\"io\":\"async_chain_failed\",\"error\":\"%s\",\"thread\":\"%s\"}",
                                throwable.getMessage(),
                                Thread.currentThread().getName()
                        ))
                );
    }

    /**
     * I/O Executor 상태 정보
     *
     * 현재 I/O 스레드 풀의 상태와 설정 정보 제공
     * 모니터링과 디버깅에 유용
     *
     * @return I/O 스레드 풀 상태를 담은 CompletableFuture<HttpResponse>
     */
    public CompletableFuture<HttpResponse> getExecutorStats() {
        // completedFuture(): 이미 완료된 Future 반환
        // 즉시 응답 가능한 정보이므로 별도 비동기 처리 불필요
        return CompletableFuture.completedFuture(
                HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io_executor\":{\"shutdown\":%s,\"pool_size\":%d},\"thread\":\"%s\"}",
                        ioExecutor.isShutdown(),                        // 스레드 풀 종료 여부
                        Runtime.getRuntime().availableProcessors() * 2, // 설정된 스레드 풀 크기
                        Thread.currentThread().getName()               // 현재 응답 처리 스레드
                ))
        );
    }

    /**
     * 스케줄된 지연 실행 (EventLoop 스타일)
     *
     * Thread.sleep 대신 스케줄러를 사용한 비동기 지연 처리
     * EventLoop의 비동기 철학에 더 부합하는 방식
     *
     * 차이점:
     * - Thread.sleep: 스레드를 블로킹 (동기 방식)
     * - Scheduler: 지정된 시간 후 콜백 실행 (비동기 방식)
     *
     * @param request HTTP 요청 객체
     * @return 스케줄된 작업 결과를 담은 CompletableFuture<HttpResponse>
     */
    public CompletableFuture<HttpResponse> handleScheduledRequest(HttpRequest request) {
        // 결과를 수동으로 제어할 수 있는 CompletableFuture 생성
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();

        // 100ms 후에 작업 수행 (Thread.sleep 대신 스케줄러 사용)
        // schedule(): 지정된 시간 후에 작업 실행
        ioExecutor.schedule(() -> {
            try {
                // 실제 I/O 작업은 여전히 수행 (데이터 생성 등)
                StringBuilder data = new StringBuilder();

                // 500줄의 스케줄된 데이터 생성
                for (int i = 0; i < 500; i++) {
                    data.append("scheduled-data-").append(i).append("\n");
                }

                // 성공적인 결과로 Future 완료
                future.complete(HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io\":\"scheduled_completed\",\"dataSize\":%d,\"thread\":\"%s\"}",
                        data.length(),                      // 생성된 데이터 크기
                        Thread.currentThread().getName()   // 스케줄된 작업을 실행한 스레드
                )));

            } catch (Exception e) {
                // 예외 발생시 Future를 예외로 완료
                future.completeExceptionally(e);
            }
        }, 100, TimeUnit.MILLISECONDS); // 100밀리초 후 실행

        return future;
    }

    /**
     * 리소스 정리
     *
     * 애플리케이션 종료시 I/O 스레드 풀을 안전하게 종료
     * 메모리 누수와 좀비 스레드 방지
     *
     * static 메서드: 인스턴스 생성 없이 호출 가능
     * 애플리케이션 종료 훅에서 호출됨
     */
    public static void shutdown() {
        logger.info("EventLoop 서버용 I/O executor 종료 중");

        // shutdown(): 새로운 작업 제출을 거부하고 기존 작업은 완료 대기
        // 현재 실행 중인 작업들이 정상적으로 끝날 때까지 기다림
        ioExecutor.shutdown();

        try {
            // awaitTermination(): 지정된 시간 동안 종료 대기
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                // 5초 내에 정상 종료되지 않으면 강제 종료
                // shutdownNow(): 실행 중인 작업을 인터럽트하고 즉시 종료
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            // 대기 중 인터럽트 발생시 즉시 강제 종료
            ioExecutor.shutdownNow();

            // interrupt(): 현재 스레드의 인터럽트 상태 복원
            Thread.currentThread().interrupt();
        }
    }
}