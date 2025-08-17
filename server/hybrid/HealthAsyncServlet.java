package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * Health Check 비동기 서블릿
 * 서버 상태 확인을 위한 헬스체크 엔드포인트
 */
public class HealthAsyncServlet extends MiniAsyncServlet {

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            // 간단한 헬스체크 응답
            String healthJson = String.format(
                    "{ \"status\": \"healthy\", \"server\": \"HybridServer\", " +
                            "\"thread\": \"%s\", \"timestamp\": %d, \"processing\": \"async\" }",
                    Thread.currentThread().getName(),
                    System.currentTimeMillis()
            );

            response.sendJson(healthJson);
        });
    }

    @Override
    protected CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            // POST 요청으로도 헬스체크 가능
            response.sendJson(
                    "{ \"status\": \"healthy\", \"method\": \"POST\", \"server\": \"HybridServer\" }"
            );
        });
    }
}