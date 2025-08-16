package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * 로드 테스트 비동기 서블릿
 * CPU 집약적 작업과 I/O 작업을 비동기로 처리하여 성능 측정
 * Threaded 서버와의 성능 비교용
 */
public class LoadTestAsyncServlet extends MiniAsyncServlet {

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                long start = System.currentTimeMillis();

                // CPU 집약적 작업 시뮬레이션 (비동기로 처리)
                double result = 0;
                for (int i = 0; i < 100000; i++) {
                    result += Math.sqrt(i) * Math.sin(i);
                }

                // 추가적인 비동기 I/O 시뮬레이션
                Thread.sleep(50);

                long duration = System.currentTimeMillis() - start;

                String resultJson = String.format(
                        "{ \"computation\": %.2f, \"duration\": %d, " +
                                "\"thread\": \"%s\", \"server\": \"HybridServer\", " +
                                "\"processing\": \"async\", \"iterations\": 100000 }",
                        result, duration, Thread.currentThread().getName()
                );

                response.sendJson(resultJson);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "Load test interrupted");
            }
        });
    }

    @Override
    protected CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                String intensity = request.getParameter("intensity");
                int iterations = intensity != null ? Integer.parseInt(intensity) : 50000;

                long start = System.currentTimeMillis();

                // 가변적인 부하 테스트
                double result = 0;
                for (int i = 0; i < iterations; i++) {
                    result += Math.sqrt(i) * Math.cos(i) * Math.tan(i % 100 + 1);
                }

                // 비동기 I/O 시뮬레이션
                Thread.sleep(100);

                long duration = System.currentTimeMillis() - start;

                String resultJson = String.format(
                        "{ \"computation\": %.2f, \"duration\": %d, " +
                                "\"thread\": \"%s\", \"server\": \"HybridServer\", " +
                                "\"processing\": \"async\", \"iterations\": %d, \"intensity\": \"%s\" }",
                        result, duration, Thread.currentThread().getName(), iterations, intensity
                );

                response.sendJson(resultJson);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "Load test interrupted");
            } catch (NumberFormatException e) {
                response.sendError(HttpStatus.BAD_REQUEST, "Invalid intensity parameter");
            }
        });
    }
}