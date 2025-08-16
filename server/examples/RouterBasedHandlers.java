package server.examples;

import server.core.routing.RouteHandler;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * 라우터 기반 핸들러 예시
 */
public class RouterBasedHandlers {

    /**
     * Hello World 라우트 핸들러
     */
    public static RouteHandler helloHandler() {
        return RouteHandler.sync(request -> {
            String name = request.getQueryParameter("name");
            if (name == null) name = "World";

            return HttpResponse.html(
                    "<h1>Hello, " + name + "!</h1>" +
                            "<p>Powered by Router-based handler</p>"
            );
        });
    }

    /**
     * JSON API 핸들러
     */
    public static RouteHandler jsonApiHandler() {
        return request -> CompletableFuture.supplyAsync(() -> {
            // 비동기적으로 처리
            try {
                Thread.sleep(100); // 가상의 비동기 작업
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return HttpResponse.json(
                    "{ \"message\": \"Hello from async handler\", " +
                            "\"timestamp\": " + System.currentTimeMillis() + " }"
            );
        });
    }

    /**
     * 파일 업로드 핸들러
     */
    public static RouteHandler uploadHandler() {
        return RouteHandler.sync(request -> {
            if (request.getMethod() == HttpMethod.GET) {
                // 업로드 폼 표시
                return HttpResponse.html(
                        "<html><body>" +
                                "<h2>File Upload</h2>" +
                                "<form method='post' enctype='multipart/form-data'>" +
                                "<input type='file' name='file'><br><br>" +
                                "<input type='submit' value='Upload'>" +
                                "</form>" +
                                "</body></html>"
                );
            } else {
                // 파일 처리 (간단한 구현)
                byte[] body = request.getBody();
                return HttpResponse.json(
                        "{ \"status\": \"uploaded\", \"size\": " + body.length + " }"
                );
            }
        });
    }

    /**
     * 에러 핸들러
     */
    public static RouteHandler errorHandler() {
        return RouteHandler.sync(request -> {
            String type = request.getQueryParameter("type");

            switch (type != null ? type : "500") {
                case "400":
                    return HttpResponse.badRequest("This is a test 400 error");
                case "401":
                    return HttpResponse.unauthorized("This is a test 401 error");
                case "403":
                    return HttpResponse.forbidden("This is a test 403 error");
                case "404":
                    return HttpResponse.notFound("This is a test 404 error");
                default:
                    return HttpResponse.internalServerError("This is a test 500 error");
            }
        });
    }
}