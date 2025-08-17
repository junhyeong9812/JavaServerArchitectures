package server.eventloop;

import server.core.http.HttpRequest;
import server.core.http.HttpResponse;
import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * EventLoop 서버용 HelloWorld 서블릿
 * ThreadedServer의 HelloWorldServlet과 동일한 기능 제공
 */
public class HelloWorldServlet {

    private static final Logger logger = LoggerFactory.getLogger(HelloWorldServlet.class);

    /**
     * Hello World 요청 처리
     */
    public CompletableFuture<HttpResponse> handleRequest(HttpRequest request) {
        // 쿼리 파라미터에서 name 추출
        String name = request.getQueryParameter("name");
        if (name == null || name.trim().isEmpty()) {
            name = "World";
        }

        String responseText = String.format("Hello, %s! (EventLoop Server)", name);

        logger.debug("Processing hello request for name: {} on thread: {}",
                name, Thread.currentThread().getName());

        return CompletableFuture.completedFuture(
                HttpResponse.text(responseText)
        );
    }

    /**
     * JSON 형태의 Hello 응답
     */
    public CompletableFuture<HttpResponse> handleJsonRequest(HttpRequest request) {
        String name = request.getQueryParameter("name");
        if (name == null || name.trim().isEmpty()) {
            name = "World";
        }

        return CompletableFuture.completedFuture(
                HttpResponse.json(String.format(
                        "{\"message\":\"Hello, %s!\",\"server\":\"eventloop\",\"thread\":\"%s\",\"timestamp\":%d}",
                        name,
                        Thread.currentThread().getName(),
                        System.currentTimeMillis()
                ))
        );
    }
}