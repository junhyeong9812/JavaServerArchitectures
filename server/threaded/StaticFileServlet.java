package server.threaded;

import server.core.mini.*;

/**
 * 정적 파일 서빙 서블릿
 */
public class StaticFileServlet extends MiniServlet {

    @Override
    protected void doGet(MiniRequest request, MiniResponse response) throws Exception {
        String path = request.getPathInfo();

        // 간단한 정적 파일 시뮬레이션
        if (path.endsWith(".css")) {
            response.setContentType("text/css");
            response.writeBody("/* CSS content for " + path + " */\n" +
                    "body { color: blue; }");
        } else if (path.endsWith(".js")) {
            response.setContentType("application/javascript");
            response.writeBody("// JavaScript content for " + path + "\n" +
                    "console.log('Hello from " + path + "');");
        } else {
            response.setContentType("text/plain");
            response.writeBody("Static file: " + path + "\n" +
                    "Served by: " + Thread.currentThread().getName());
        }
    }
}