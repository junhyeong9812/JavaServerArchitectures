package server.eventloop;

import server.core.http.HttpRequest;
import server.core.http.HttpResponse;
import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * EventLoop 서버용 CPU 집약적 작업 서블릿
 * ThreadedServer의 CpuIntensiveServlet과 동일한 작업을 비동기로 처리
 *
 * EventLoop의 핵심 원칙:
 * - 메인 EventLoop 스레드를 절대 블로킹하지 않음
 * - CPU 집약적 작업은 별도 스레드 풀에서 수행
 * - CompletableFuture로 비동기 결과 처리
 */
public class CpuIntensiveServlet {

    // LoggerFactory.getLogger(): 클래스별 Logger 인스턴스 생성
    // static final: 클래스 로딩시 한번만 초기화, 변경 불가
    private static final Logger logger = LoggerFactory.getLogger(CpuIntensiveServlet.class);

    // CPU 작업용 별도 스레드 풀 (EventLoop 메인 스레드 블로킹 방지)
    // ScheduledExecutorService: 스케줄링 기능이 있는 스레드 풀
    // static final: 모든 인스턴스가 하나의 스레드 풀 공유
    private static final ScheduledExecutorService cpuExecutor = Executors.newScheduledThreadPool(
            // Runtime.getRuntime(): 현재 JVM 런타임 인스턴스
            // availableProcessors(): 사용 가능한 CPU 코어 수 반환
            // CPU 집약적 작업이므로 코어 수만큼 스레드 생성이 적절
            Runtime.getRuntime().availableProcessors(),

            // ThreadFactory: 새 스레드 생성 방식을 커스터마이징
            // 람다 표현식으로 ThreadFactory 인터페이스 구현
            r -> {
                // Thread 생성자: Runnable과 스레드 이름 지정
                Thread t = new Thread(r, "EventLoop-CPU-Worker");

                // setDaemon(true): 데몬 스레드로 설정
                // 메인 프로그램 종료시 이 스레드들도 자동 종료됨
                t.setDaemon(true);

                // 생성된 스레드 반환
                return t;
            }
    );

    /**
     * CPU 집약적 작업 처리 - ThreadedServer와 동일한 계산 수행
     *
     * CompletableFuture<T>: 비동기 작업의 결과를 나타내는 클래스
     * - EventLoop 스레드에서 즉시 반환됨 (논블로킹)
     * - 실제 계산은 별도 스레드에서 수행
     * - 완료되면 결과를 HttpResponse로 제공
     *
     * @param request HTTP 요청 객체 (현재는 사용하지 않지만 확장성을 위해 보존)
     * @return CompletableFuture<HttpResponse> - 비동기 HTTP 응답
     */
    public CompletableFuture<HttpResponse> handleRequest(HttpRequest request) {
        // System.currentTimeMillis(): 현재 시간을 밀리초로 반환
        // 작업 수행 시간 측정을 위한 시작 시간 기록
        long startTime = System.currentTimeMillis();

        // Thread.currentThread().getName(): 현재 스레드의 이름 반환
        // EventLoop 메인 스레드에서 이 메서드가 호출됨을 확인하는 로그
        logger.debug("EventLoop 스레드에서 CPU 집약적 작업 시작: {}",
                Thread.currentThread().getName());

        // 핵심: ThreadedServer와 정확히 동일한 CPU 작업을 별도 스레드에서 수행
        // CompletableFuture.supplyAsync(): 별도 스레드에서 비동기로 값을 계산
        return CompletableFuture.supplyAsync(() -> {

            // ThreadedServer의 CpuIntensiveServlet과 동일한 계산
            // 복잡한 수학 연산으로 CPU 사용량 증가시킴
            double result = 0.0;

            // for 루프: 100,000번 반복하여 CPU 집약적 작업 시뮬레이션
            for (int i = 0; i < 100000; i++) {
                // Math.sqrt(i): i의 제곱근 계산
                // Math.sin(i): i의 사인 값 계산 (라디안 단위)
                // 두 값을 곱해서 result에 누적
                result += Math.sqrt(i) * Math.sin(i);
            }

            // 작업 완료 시간 계산
            long duration = System.currentTimeMillis() - startTime;

            // 작업 완료 로그 - 워커 스레드에서 출력됨
            logger.debug("CPU 집약적 작업이 {}ms에 완료됨, 워커 스레드: {}",
                    duration, Thread.currentThread().getName());

            // ThreadedServer와 유사한 형태의 JSON 응답
            // String.format(): printf 스타일의 문자열 포맷팅
            // HttpResponse.json(): JSON 응답 생성 헬퍼 메서드
            return HttpResponse.json(String.format(
                    "{\"server\":\"eventloop\",\"result\":%.2f,\"duration\":%d,\"thread\":\"%s\",\"timestamp\":%d}",
                    result,                                    // 계산 결과 (소수점 2자리)
                    duration,                                  // 소요 시간 (밀리초)
                    Thread.currentThread().getName(),         // 작업한 스레드 이름
                    System.currentTimeMillis()                // 응답 생성 시간
            ));

        }, cpuExecutor); // 두 번째 매개변수: 작업을 실행할 Executor 지정
    }

    /**
     * 더 무거운 CPU 작업 (스트레스 테스트용)
     *
     * 일반 작업보다 5배 더 많은 계산을 수행하여
     * 시스템의 부하 처리 능력을 테스트
     *
     * @param request HTTP 요청 객체
     * @return CompletableFuture<HttpResponse> - 비동기 HTTP 응답
     */
    public CompletableFuture<HttpResponse> handleHeavyRequest(HttpRequest request) {
        // supplyAsync(): 별도 스레드에서 비동기 계산 수행
        return CompletableFuture.supplyAsync(() -> {

            // 더 무거운 계산 작업 - 500,000번 반복
            double result = 0.0;
            for (int i = 0; i < 500000; i++) {
                // Math.cos(i): i의 코사인 값 계산 추가
                // 이전보다 더 복잡한 수학 연산으로 CPU 부하 증가
                result += Math.sqrt(i) * Math.sin(i) * Math.cos(i);
            }

            // 결과를 JSON 형태로 응답
            return HttpResponse.json(String.format(
                    "{\"server\":\"eventloop\",\"heavy_result\":%.2f,\"thread\":\"%s\"}",
                    result,                                    // 계산 결과
                    Thread.currentThread().getName()          // 작업한 스레드 이름
            ));

        }, cpuExecutor); // CPU 전용 스레드 풀에서 실행
    }

    /**
     * 파라미터 기반 동적 CPU 작업
     *
     * 클라이언트가 iterations 쿼리 파라미터로 계산 횟수를 조정할 수 있음
     * 예: /cpu-param?iterations=50000
     *
     * @param request HTTP 요청 객체 - 쿼리 파라미터 추출에 사용
     * @return CompletableFuture<HttpResponse> - 비동기 HTTP 응답
     */
    public CompletableFuture<HttpResponse> handleParameterizedRequest(HttpRequest request) {
        // iterations 파라미터로 계산 횟수 조정
        // getQueryParameter(): URL의 쿼리 스트링에서 특정 파라미터 값 추출
        String iterationsParam = request.getQueryParameter("iterations");

        // 기본값 설정
        int iterations = 100000;

        // try-catch: 예외 처리 블록
        try {
            // 파라미터가 제공된 경우 정수로 변환
            if (iterationsParam != null) {
                // Integer.parseInt(): 문자열을 정수로 변환
                iterations = Integer.parseInt(iterationsParam);

                // Math.min(), Math.max(): 값의 범위 제한
                // 1,000 ~ 1,000,000 사이로 제한하여 서버 안정성 보장
                iterations = Math.min(Math.max(iterations, 1000), 1000000);
            }
        } catch (NumberFormatException e) {
            // NumberFormatException: 문자열을 숫자로 변환할 수 없을 때 발생
            // 잘못된 파라미터 값에 대한 경고 로그
            logger.warn("잘못된 iterations 파라미터: {}, 기본값 사용", iterationsParam);
        }

        // final 변수: 람다 표현식 내부에서 사용하기 위해 final로 선언
        // 람다는 외부의 지역 변수를 사용할 때 effectively final이어야 함
        final int finalIterations = iterations;

        // 비동기 계산 수행
        return CompletableFuture.supplyAsync(() -> {

            double result = 0.0;
            // 사용자가 지정한 횟수만큼 계산 수행
            for (int i = 0; i < finalIterations; i++) {
                result += Math.sqrt(i) * Math.sin(i);
            }

            // 결과와 함께 사용된 반복 횟수도 응답에 포함
            return HttpResponse.json(String.format(
                    "{\"server\":\"eventloop\",\"result\":%.2f,\"iterations\":%d,\"thread\":\"%s\"}",
                    result,                                    // 계산 결과
                    finalIterations,                           // 실제 사용된 반복 횟수
                    Thread.currentThread().getName()          // 작업한 스레드 이름
            ));

        }, cpuExecutor); // CPU 전용 스레드 풀에서 실행
    }

    /**
     * CPU Executor 상태 정보
     *
     * 스레드 풀의 현재 상태와 시스템 정보를 반환
     * 모니터링과 디버깅에 유용
     *
     * @return CompletableFuture<HttpResponse> - 시스템 상태 정보
     */
    public CompletableFuture<HttpResponse> getExecutorStats() {
        // CompletableFuture.completedFuture(): 이미 완료된 Future 반환
        // 별도 계산이 필요 없는 간단한 정보이므로 즉시 완료된 Future 사용
        return CompletableFuture.completedFuture(
                HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"cpu_executor\":{\"shutdown\":%s,\"available_processors\":%d},\"thread\":\"%s\"}",
                        cpuExecutor.isShutdown(),                      // 스레드 풀 종료 여부
                        Runtime.getRuntime().availableProcessors(),    // 사용 가능한 CPU 코어 수
                        Thread.currentThread().getName()               // 현재 스레드 이름
                ))
        );
    }

    /**
     * 리소스 정리
     *
     * 애플리케이션 종료시 스레드 풀을 안전하게 종료
     * static 메서드: 인스턴스 생성 없이 호출 가능
     *
     * shutdown vs shutdownNow:
     * - shutdown(): 현재 실행 중인 작업은 완료하고 새로운 작업은 거부
     * - shutdownNow(): 모든 작업을 즉시 중단하고 대기 중인 작업 목록 반환
     */
    public static void shutdown() {
        // 종료 시작 로그
        logger.info("EventLoop 서버용 CPU executor 종료 중");

        // cpuExecutor.shutdown(): 스레드 풀을 graceful하게 종료
        // - 새로운 작업 제출을 거부
        // - 이미 제출된 작업들은 완료될 때까지 기다림
        // - 스레드들이 작업 완료 후 자연스럽게 종료됨
        cpuExecutor.shutdown();
    }
}