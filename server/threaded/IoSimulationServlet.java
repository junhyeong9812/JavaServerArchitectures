package server.threaded;

import server.core.mini.*;
import server.core.http.*;

/**
 * I/O 시뮬레이션 서블릿
 */
public class IoSimulationServlet extends MiniServlet {
    @Override
    protected void doGet(MiniRequest request, MiniResponse response) throws Exception {
        try {
            // I/O 작업 시뮬레이션 (파일 읽기, DB 조회 등)
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        response.sendJson(String.format(
                "{ \"server\": \"threaded\", \"io\": \"completed\", \"thread\": \"%s\" }",
                Thread.currentThread().getName()
        ));
    }
}
