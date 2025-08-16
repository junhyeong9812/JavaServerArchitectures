package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * Hello World 비동기 서블릿
 * Threaded의 HelloWorldServlet과 동일한 기능을 비동기로 처리
 */
public class HelloWorldAsyncServlet extends MiniAsyncServlet {

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            String name = request.getParameter("name");
            if (name == null) name = "World";

            response.sendHtml(
                    "<h1>Hello, " + name + "!</h1>" +
                            "<p>Handled by HybridServer AsyncServlet</p>" +
                            "<p>Thread: " + Thread.currentThread().getName() + "</p>" +
                            "<p>Timestamp: " + System.currentTimeMillis() + "</p>" +
                            "<p>Type: Non-blocking Async Processing</p>"
            );
        });
    }
}