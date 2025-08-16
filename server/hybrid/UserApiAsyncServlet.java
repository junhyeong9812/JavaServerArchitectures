package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * User API 비동기 서블릿
 * RESTful API를 비동기로 처리하여 높은 처리량 달성
 */
public class UserApiAsyncServlet extends MiniAsyncServlet {

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 비동기 DB 조회 시뮬레이션
                Thread.sleep(100);

                String userId = request.getParameter("id");
                if (userId != null) {
                    // 특정 사용자 조회
                    String userJson = String.format(
                            "{ \"id\": \"%s\", \"name\": \"User %s\", \"email\": \"user%s@example.com\", " +
                                    "\"thread\": \"%s\", \"server\": \"HybridServer\", \"processing\": \"async\" }",
                            userId, userId, userId, Thread.currentThread().getName()
                    );
                    response.sendJson(userJson);
                } else {
                    // 전체 사용자 목록
                    String usersJson = String.format(
                            "{ \"users\": [" +
                                    "  { \"id\": \"1\", \"name\": \"Alice\" }," +
                                    "  { \"id\": \"2\", \"name\": \"Bob\" }," +
                                    "  { \"id\": \"3\", \"name\": \"Charlie\" }" +
                                    "], \"thread\": \"%s\", \"server\": \"HybridServer\", \"processing\": \"async\" }",
                            Thread.currentThread().getName()
                    );
                    response.sendJson(usersJson);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "Async operation interrupted");
            }
        });
    }

    @Override
    protected CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 비동기 사용자 생성 시뮬레이션
                Thread.sleep(200);

                String requestBody = request.getBody();
                String resultJson = String.format(
                        "{ \"status\": \"created\", \"data\": %s, " +
                                "\"thread\": \"%s\", \"server\": \"HybridServer\", " +
                                "\"processing\": \"async\", \"timestamp\": %d }",
                        requestBody != null ? requestBody : "{}",
                        Thread.currentThread().getName(),
                        System.currentTimeMillis()
                );

                response.setStatus(HttpStatus.CREATED);
                response.sendJson(resultJson);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "User creation failed");
            }
        });
    }
}