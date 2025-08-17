package server.hybrid;

// 미니 서블릿 컨테이너 관련 클래스들을 임포트 - 경량화된 서블릿 구현
import server.core.mini.*;
// HTTP 관련 핵심 클래스들을 임포트 - HTTP 요청/응답, 상태 코드 등을 처리
import server.core.http.*;
// 비동기 처리를 위한 CompletableFuture 임포트 - Java 8의 비동기 프로그래밍 지원
import java.util.concurrent.CompletableFuture;
// 난수 생성을 위한 Random 클래스 임포트 - I/O 작업의 가변적인 지연 시간 시뮬레이션
import java.util.Random;

/**
 * I/O 시뮬레이션 비동기 서블릿
 * 데이터베이스 조회, 파일 읽기, API 호출 등 I/O 대기를 시뮬레이션
 * Threaded 서버의 IoSimulationServlet과 동일한 기능을 비동기로 처리
 */
// MiniAsyncServlet을 상속받아 비동기 서블릿 구현
// 상속을 통해 doGetAsync, doPostAsync 등의 메서드를 오버라이드할 수 있음
public class IoSimulationAsyncServlet extends MiniAsyncServlet {

    // static final로 선언하여 클래스 레벨에서 공유되는 불변 Random 인스턴스
    // 모든 메서드에서 동일한 Random 객체를 사용하여 메모리 효율성 확보
    private static final Random random = new Random();

    // @Override 어노테이션: 부모 클래스의 메서드를 재정의함을 명시
    // 컴파일러가 메서드 시그니처 검증을 수행하여 오타나 실수 방지
    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        // CompletableFuture.runAsync(): Runnable을 받아서 별도 스레드에서 비동기 실행
        // 반환값이 없는 작업(Void)을 처리할 때 사용
        return CompletableFuture.runAsync(() -> {
            try {
                // System.currentTimeMillis(): 현재 시간을 밀리초로 반환
                // 작업 시작 시점을 기록하여 나중에 소요 시간 계산에 사용
                long startTime = System.currentTimeMillis();

                // I/O 지연 시간 설정 (기본값 100ms)
                int delayMs = 100;
                // request.getParameter(): URL 쿼리 파라미터에서 값을 추출
                // 예: /io-simulation?delay=200 에서 "200"을 추출
                String delayParam = request.getParameter("delay");
                if (delayParam != null) {
                    try {
                        // Integer.parseInt(): 문자열을 정수로 변환
                        // NumberFormatException이 발생할 수 있음
                        delayMs = Integer.parseInt(delayParam);
                        // Math.min(): 두 값 중 작은 값 반환
                        // 최대 지연 시간을 5초로 제한하여 서버 안정성 확보
                        delayMs = Math.min(delayMs, 5000);
                    } catch (NumberFormatException e) {
                        // 파라미터가 숫자가 아닌 경우 기본값(100ms) 사용
                        // 예외를 잡지만 기본값으로 복구하여 계속 진행
                    }
                }

                // I/O 유형 결정 - 사용자가 지정하거나 랜덤으로 선택
                String ioType = request.getParameter("type");
                if (ioType == null) {
                    // 사용자가 유형을 지정하지 않은 경우 랜덤으로 선택
                    String[] types = {"database", "file", "api", "cache"};
                    // random.nextInt(n): 0부터 n-1까지의 랜덤 정수 반환
                    ioType = types[random.nextInt(types.length)];
                }

                // I/O 작업 시뮬레이션 - 실제 I/O 대기를 Thread.sleep()으로 구현
                Thread.sleep(delayMs);

                // 작업별 추가 처리 - 각 I/O 유형에 따른 세부 시뮬레이션
                String ioResult = simulateIoOperation(ioType, delayMs);

                // 작업 완료 후 총 소요 시간 계산
                long duration = System.currentTimeMillis() - startTime;

                // JSON 형태의 응답 데이터 생성
                // String.format(): printf 스타일의 문자열 포맷팅
                // %s: 문자열, %d: 정수 형태로 치환
                String resultJson = String.format(
                        "{ \"ioType\": \"%s\", \"delay\": %d, \"duration\": %d, " +
                                "\"result\": \"%s\", \"thread\": \"%s\", \"server\": \"HybridServer\", " +
                                "\"processing\": \"async\", \"timestamp\": %d }",
                        ioType, delayMs, duration, ioResult,
                        // Thread.currentThread().getName(): 현재 스레드의 이름
                        // 어떤 스레드에서 작업이 처리되었는지 추적 가능
                        Thread.currentThread().getName(), System.currentTimeMillis()
                );

                // response.sendJson(): JSON 응답 전송
                // Content-Type을 application/json으로 자동 설정
                response.sendJson(resultJson);

            } catch (InterruptedException e) {
                // Thread.sleep() 중 인터럽트 발생 시 처리
                // Thread.currentThread().interrupt(): 인터럽트 상태를 다시 설정
                // 인터럽트 신호를 무시하지 않고 상위 호출자에게 전파
                Thread.currentThread().interrupt();
                // response.sendError(): HTTP 오류 응답 전송
                // HttpStatus.INTERNAL_SERVER_ERROR: 500 상태 코드
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "I/O 시뮬레이션이 중단되었습니다");
            }
        });
    }

    @Override
    protected CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        // POST 요청 처리 - GET보다 복잡한 I/O 시뮬레이션
        return CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // POST 요청의 바디 데이터 추출
                // request.getBody(): HTTP 요청 바디를 문자열로 반환
                // POST 요청에서는 바디에 데이터가 포함될 수 있음
                String body = request.getBody();

                // 여러 단계의 I/O 작업 시뮬레이션 - 실제 웹 애플리케이션의 복잡한 처리 과정 모방
                simulateMultiStepIo();

                long duration = System.currentTimeMillis() - startTime;

                // POST 작업 결과를 JSON으로 구성
                String resultJson = String.format(
                        "{ \"operation\": \"multi-step-io\", \"duration\": %d, " +
                                "\"steps\": [\"validate\", \"database\", \"cache\", \"response\"], " +
                                "\"bodySize\": %d, \"thread\": \"%s\", \"server\": \"HybridServer\", " +
                                "\"processing\": \"async\", \"method\": \"POST\" }",
                        duration,
                        // 삼항 연산자 (조건 ? 참일때값 : 거짓일때값)
                        // body가 null이 아니면 길이를, null이면 0을 반환
                        body != null ? body.length() : 0,
                        Thread.currentThread().getName()
                );

                response.sendJson(resultJson);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "다단계 I/O 작업이 실패했습니다");
            }
        });
    }

    /**
     * I/O 작업 유형별 시뮬레이션
     */
    // throws InterruptedException: 메서드 내에서 Thread.sleep() 사용으로 인한 예외 선언
    // 호출자가 이 예외를 처리하도록 강제함
    private String simulateIoOperation(String ioType, int baseDelay) throws InterruptedException {
        // switch 문: 여러 조건을 효율적으로 처리
        // ioType.toLowerCase(): 대소문자 구분 없이 처리하기 위해 소문자로 변환
        switch (ioType.toLowerCase()) {
            case "database":
                // 데이터베이스 조회 시뮬레이션
                // baseDelay + random.nextInt(50): 기본 지연 + 0~49ms의 랜덤 추가 지연
                // 실제 DB 조회의 가변적인 응답 시간을 시뮬레이션
                Thread.sleep(baseDelay + random.nextInt(50));
                return "Database query completed - 150 rows affected";

            case "file":
                // 파일 읽기 시뮬레이션
                Thread.sleep(baseDelay + random.nextInt(30));
                return "File read completed - 2.5MB processed";

            case "api":
                // 외부 API 호출 시뮬레이션
                // API 호출은 네트워크 상태에 따라 더 가변적이므로 더 큰 랜덤 범위 사용
                Thread.sleep(baseDelay + random.nextInt(100));
                return "External API call completed - 200 OK";

            case "cache":
                // 캐시 조회 시뮬레이션 (더 빠름)
                // Math.max(5, baseDelay / 10): 최소 5ms 보장, 기본 지연의 1/10 사용
                // 캐시는 일반적으로 매우 빠르므로 지연 시간을 크게 단축
                Thread.sleep(Math.max(5, baseDelay / 10));
                return "Cache lookup completed - Hit ratio: 85%";

            default:
                // 위의 경우에 해당하지 않는 기본 처리
                Thread.sleep(baseDelay);
                return "Generic I/O operation completed";
        }
    }

    /**
     * 다단계 I/O 작업 시뮬레이션
     */
    private void simulateMultiStepIo() throws InterruptedException {
        // 실제 웹 애플리케이션의 복잡한 처리 과정을 단계별로 시뮬레이션

        // 1. 입력 검증 (빠름) - 사용자 입력 데이터의 유효성 검사
        Thread.sleep(10);

        // 2. 데이터베이스 조회 (중간) - 실제 데이터 조회/수정
        // random.nextInt(40): 0~39ms의 추가 랜덤 지연
        Thread.sleep(80 + random.nextInt(40));

        // 3. 캐시 업데이트 (빠름) - 조회 결과를 캐시에 저장
        Thread.sleep(15);

        // 4. 로그 기록 (중간) - 작업 내역을 로그 파일에 기록
        Thread.sleep(25 + random.nextInt(15));

        // 5. 응답 준비 (빠름) - 최종 응답 데이터 구성
        Thread.sleep(5);
    }
}