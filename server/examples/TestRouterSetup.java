package server.examples;

import server.core.routing.*;
import server.core.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * 테스트 라우터 설정
 */
public class TestRouterSetup {

    public static Router createTestRouter() {
        Router router = new Router();

        // 기본 라우트들
        router.get("/", RouterBasedHandlers.helloHandler());
        router.get("/hello", RouterBasedHandlers.helloHandler());
        router.get("/api/test", RouterBasedHandlers.jsonApiHandler());
        router.all("/upload", RouterBasedHandlers.uploadHandler());
        router.get("/error", RouterBasedHandlers.errorHandler());

        // 파라미터가 있는 라우트
        router.get("/users/{id}", request -> {
            String userId = request.getAttribute("path.id", String.class);
            return CompletableFuture.completedFuture(
                    HttpResponse.json("{ \"userId\": \"" + userId + "\" }")
            );
        });

        // 정규식 파라미터
        router.get("/numbers/{id:\\d+}", request -> {
            String numberId = request.getAttribute("path.id", String.class);
            return CompletableFuture.completedFuture(
                    HttpResponse.json("{ \"number\": " + numberId + " }")
            );
        });

        // RESTful 리소스
        router.resource("/api/users", new ResourceHandler()
                .index(request -> CompletableFuture.completedFuture(
                        HttpResponse.json("{ \"users\": [] }")
                ))
                .show(request -> {
                    String id = request.getAttribute("path.id", String.class);
                    return CompletableFuture.completedFuture(
                            HttpResponse.json("{ \"user\": { \"id\": \"" + id + "\" } }")
                    );
                })
                .create(request -> CompletableFuture.completedFuture(
                        HttpResponse.created("{ \"created\": true }")
                ))
        );

        // 미들웨어 추가
        router.use((request, next) -> {
            System.out.println("Request: " + request.getMethod() + " " + request.getUri());
            long start = System.currentTimeMillis();

            return next.handle(request).thenApply(response -> {
                long duration = System.currentTimeMillis() - start;
                System.out.println("Response: " + response.getStatus() + " (" + duration + "ms)");
                return response;
            });
        });

        return router;
    }
}