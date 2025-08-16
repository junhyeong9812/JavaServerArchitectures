package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * 정적 파일 비동기 서블릿
 * CSS, JS, HTML 등 정적 파일을 비동기로 서빙
 */
public class StaticFileAsyncServlet extends MiniAsyncServlet {

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                String path = request.getPathInfo();

                // 비동기 파일 읽기 시뮬레이션
                Thread.sleep(50);

                if (path.endsWith(".css")) {
                    response.setContentType("text/css");
                    response.writeBody("/* Async CSS content for " + path + " */\n" +
                            "body { color: green; background: #f0f0f0; }\n" +
                            ".async { animation: blink 1s infinite; }\n" +
                            "/* Served by: " + Thread.currentThread().getName() + " */");

                } else if (path.endsWith(".js")) {
                    response.setContentType("application/javascript");
                    response.writeBody("// Async JavaScript content for " + path + "\n" +
                            "console.log('Hello from async " + path + "');\n" +
                            "console.log('Thread: " + Thread.currentThread().getName() + "');\n" +
                            "console.log('Processing: Non-blocking async');");

                } else if (path.endsWith(".html")) {
                    response.setContentType("text/html");
                    response.writeBody("<!DOCTYPE html>\n<html><body>\n" +
                            "<h1>Async Static HTML: " + path + "</h1>\n" +
                            "<p>Served by: " + Thread.currentThread().getName() + "</p>\n" +
                            "<p>Server: HybridServer AsyncServlet</p>\n" +
                            "<p>Processing: Non-blocking async</p>\n" +
                            "</body></html>");

                } else {
                    response.setContentType("text/plain");
                    response.writeBody("Async Static file: " + path + "\n" +
                            "Served by: " + Thread.currentThread().getName() + "\n" +
                            "Server: HybridServer\n" +
                            "Processing: Non-blocking async\n" +
                            "Timestamp: " + System.currentTimeMillis());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "File serving interrupted");
            }
        });
    }
}