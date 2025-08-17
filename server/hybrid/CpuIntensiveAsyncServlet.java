package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * CPU 집약적 작업 비동기 서블릿
 * 수학 연산을 통한 CPU 부하 테스트용
 * Threaded 서버의 CpuIntensiveServlet과 동일한 기능을 비동기로 처리
 */
public class CpuIntensiveAsyncServlet extends MiniAsyncServlet {

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();

            // CPU 집약적 작업 시뮬레이션
            double result = 0;
            int iterations = 100000; // 기본 반복 횟수

            // 쿼리 파라미터로 반복 횟수 조정 가능
            String iterParam = request.getParameter("iterations");
            if (iterParam != null) {
                try {
                    iterations = Integer.parseInt(iterParam);
                    iterations = Math.min(iterations, 1000000); // 최대 100만번
                } catch (NumberFormatException e) {
                    // 기본값 사용
                }
            }

            // 복잡한 수학 연산
            for (int i = 0; i < iterations; i++) {
                result += Math.sqrt(i) * Math.sin(i) * Math.cos(i % 100);

                // 추가적인 연산
                if (i % 1000 == 0) {
                    result += Math.pow(i, 0.1);
                }
            }

            long duration = System.currentTimeMillis() - startTime;

            String resultJson = String.format(
                    "{ \"computation\": %.4f, \"duration\": %d, " +
                            "\"iterations\": %d, \"thread\": \"%s\", \"server\": \"HybridServer\", " +
                            "\"processing\": \"async\", \"timestamp\": %d }",
                    result, duration, iterations, Thread.currentThread().getName(),
                    System.currentTimeMillis()
            );

            response.sendJson(resultJson);
        });
    }

    @Override
    protected CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // POST 바디에서 설정 읽기
                String body = request.getBody();
                int iterations = 50000; // POST 기본값
                String complexity = "normal";

                if (body != null && !body.trim().isEmpty()) {
                    // 간단한 JSON 파싱 (실제로는 JSON 라이브러리 사용 권장)
                    if (body.contains("iterations")) {
                        try {
                            String[] parts = body.split("iterations");
                            if (parts.length > 1) {
                                String num = parts[1].replaceAll("[^0-9]", "");
                                if (!num.isEmpty()) {
                                    iterations = Math.min(Integer.parseInt(num), 1000000);
                                }
                            }
                        } catch (Exception e) {
                            // 기본값 사용
                        }
                    }

                    if (body.contains("high")) {
                        complexity = "high";
                        iterations *= 2;
                    }
                }

                // 더 복잡한 CPU 작업
                double result = 0;
                for (int i = 0; i < iterations; i++) {
                    if ("high".equals(complexity)) {
                        // 고강도 연산
                        result += Math.sqrt(i) * Math.sin(i) * Math.cos(i) * Math.tan(i % 50 + 1);
                        result += Math.log(i + 1) * Math.exp(i % 10 * 0.01);
                    } else {
                        // 일반 연산
                        result += Math.sqrt(i) * Math.sin(i);
                    }
                }

                long duration = System.currentTimeMillis() - startTime;

                String resultJson = String.format(
                        "{ \"computation\": %.4f, \"duration\": %d, " +
                                "\"iterations\": %d, \"complexity\": \"%s\", " +
                                "\"thread\": \"%s\", \"server\": \"HybridServer\", " +
                                "\"processing\": \"async\", \"method\": \"POST\" }",
                        result, duration, iterations, complexity,
                        Thread.currentThread().getName()
                );

                response.sendJson(resultJson);

            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR,
                        "CPU intensive processing failed: " + e.getMessage());
            }
        });
    }
}