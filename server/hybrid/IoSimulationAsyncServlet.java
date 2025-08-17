package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;
import java.util.Random;

/**
 * I/O 시뮬레이션 비동기 서블릿
 * 데이터베이스 조회, 파일 읽기, API 호출 등 I/O 대기를 시뮬레이션
 * Threaded 서버의 IoSimulationServlet과 동일한 기능을 비동기로 처리
 */
public class IoSimulationAsyncServlet extends MiniAsyncServlet {

    private static final Random random = new Random();

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // I/O 지연 시간 설정 (기본 100ms)
                int delayMs = 100;
                String delayParam = request.getParameter("delay");
                if (delayParam != null) {
                    try {
                        delayMs = Integer.parseInt(delayParam);
                        delayMs = Math.min(delayMs, 5000); // 최대 5초
                    } catch (NumberFormatException e) {
                        // 기본값 사용
                    }
                }

                // I/O 유형 결정
                String ioType = request.getParameter("type");
                if (ioType == null) {
                    String[] types = {"database", "file", "api", "cache"};
                    ioType = types[random.nextInt(types.length)];
                }

                // I/O 작업 시뮬레이션
                Thread.sleep(delayMs);

                // 작업별 추가 처리
                String ioResult = simulateIoOperation(ioType, delayMs);

                long duration = System.currentTimeMillis() - startTime;

                String resultJson = String.format(
                        "{ \"ioType\": \"%s\", \"delay\": %d, \"duration\": %d, " +
                                "\"result\": \"%s\", \"thread\": \"%s\", \"server\": \"HybridServer\", " +
                                "\"processing\": \"async\", \"timestamp\": %d }",
                        ioType, delayMs, duration, ioResult,
                        Thread.currentThread().getName(), System.currentTimeMillis()
                );

                response.sendJson(resultJson);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "I/O simulation interrupted");
            }
        });
    }

    @Override
    protected CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // POST 요청은 더 복잡한 I/O 시뮬레이션
                String body = request.getBody();

                // 여러 단계의 I/O 작업 시뮬레이션
                simulateMultiStepIo();

                long duration = System.currentTimeMillis() - startTime;

                String resultJson = String.format(
                        "{ \"operation\": \"multi-step-io\", \"duration\": %d, " +
                                "\"steps\": [\"validate\", \"database\", \"cache\", \"response\"], " +
                                "\"bodySize\": %d, \"thread\": \"%s\", \"server\": \"HybridServer\", " +
                                "\"processing\": \"async\", \"method\": \"POST\" }",
                        duration, body != null ? body.length() : 0,
                        Thread.currentThread().getName()
                );

                response.sendJson(resultJson);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "Multi-step I/O failed");
            }
        });
    }

    /**
     * I/O 작업 유형별 시뮬레이션
     */
    private String simulateIoOperation(String ioType, int baseDelay) throws InterruptedException {
        switch (ioType.toLowerCase()) {
            case "database":
                // 데이터베이스 조회 시뮬레이션
                Thread.sleep(baseDelay + random.nextInt(50)); // 추가 지연
                return "Database query completed - 150 rows affected";

            case "file":
                // 파일 읽기 시뮬레이션
                Thread.sleep(baseDelay + random.nextInt(30));
                return "File read completed - 2.5MB processed";

            case "api":
                // 외부 API 호출 시뮬레이션
                Thread.sleep(baseDelay + random.nextInt(100)); // API는 더 가변적
                return "External API call completed - 200 OK";

            case "cache":
                // 캐시 조회 시뮬레이션 (더 빠름)
                Thread.sleep(Math.max(5, baseDelay / 10));
                return "Cache lookup completed - Hit ratio: 85%";

            default:
                Thread.sleep(baseDelay);
                return "Generic I/O operation completed";
        }
    }

    /**
     * 다단계 I/O 작업 시뮬레이션
     */
    private void simulateMultiStepIo() throws InterruptedException {
        // 1. 입력 검증 (빠름)
        Thread.sleep(10);

        // 2. 데이터베이스 조회 (중간)
        Thread.sleep(80 + random.nextInt(40));

        // 3. 캐시 업데이트 (빠름)
        Thread.sleep(15);

        // 4. 로그 기록 (중간)
        Thread.sleep(25 + random.nextInt(15));

        // 5. 응답 준비 (빠름)
        Thread.sleep(5);
    }
}