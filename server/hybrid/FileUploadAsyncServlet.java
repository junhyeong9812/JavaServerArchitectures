package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * 파일 업로드 비동기 서블릿
 * 파일 업로드를 비동기로 처리하여 스레드 블로킹 방지
 */
public class FileUploadAsyncServlet extends MiniAsyncServlet {

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            // 업로드 폼 표시
            response.sendHtml(
                    "<html><body>" +
                            "<h2>Async File Upload Test</h2>" +
                            "<form method='post' enctype='multipart/form-data'>" +
                            "<p>File: <input type='file' name='file'></p>" +
                            "<p>Description: <input type='text' name='description'></p>" +
                            "<p><input type='submit' value='Upload Async'></p>" +
                            "</form>" +
                            "<p>Thread: " + Thread.currentThread().getName() + "</p>" +
                            "<p>Server: HybridServer AsyncServlet</p>" +
                            "<p>Processing: Non-blocking async upload</p>" +
                            "</body></html>"
            );
        });
    }

    @Override
    protected CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 비동기 파일 업로드 처리 시뮬레이션
                Thread.sleep(300);

                String description = request.getParameter("description");
                byte[] body = request.getBodyBytes();

                String resultJson = String.format(
                        "{ \"status\": \"uploaded_async\", \"description\": \"%s\", " +
                                "\"bodySize\": %d, \"thread\": \"%s\", \"server\": \"HybridServer\", " +
                                "\"processing\": \"async\", \"processingTime\": \"300ms\" }",
                        description != null ? description : "No description",
                        body.length,
                        Thread.currentThread().getName()
                );

                response.sendJson(resultJson);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "Upload processing interrupted");
            }
        });
    }
}