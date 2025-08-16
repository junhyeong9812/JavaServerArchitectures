package server.threaded;

import server.core.mini.*;

/**
 * 파일 업로드 서블릿
 */
public class FileUploadServlet extends MiniServlet {

    @Override
    protected void doGet(MiniRequest request, MiniResponse response) throws Exception {
        // 업로드 폼 표시
        response.sendHtml(
                "<html><body>" +
                        "<h2>File Upload Test</h2>" +
                        "<form method='post' enctype='multipart/form-data'>" +
                        "<p>File: <input type='file' name='file'></p>" +
                        "<p>Description: <input type='text' name='description'></p>" +
                        "<p><input type='submit' value='Upload'></p>" +
                        "</form>" +
                        "<p>Thread: " + Thread.currentThread().getName() + "</p>" +
                        "</body></html>"
        );
    }

    @Override
    protected void doPost(MiniRequest request, MiniResponse response) throws Exception {
        // 간단한 업로드 처리 (실제 파일 파싱 없이)
        String description = request.getParameter("description");
        byte[] body = request.getBodyBytes();

        response.sendJson(String.format(
                "{ \"status\": \"uploaded\", \"description\": \"%s\", " +
                        "\"bodySize\": %d, \"thread\": \"%s\" }",
                description != null ? description : "No description",
                body.length,
                Thread.currentThread().getName()
        ));
    }
}