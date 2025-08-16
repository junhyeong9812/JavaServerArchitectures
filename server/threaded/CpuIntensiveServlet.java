package server.threaded;

import server.core.mini.*;
import server.core.http.*;

/**
 * CPU 집약적 작업 서블릿
 */
public class CpuIntensiveServlet extends MiniServlet {
    @Override
    protected void doGet(MiniRequest request, MiniResponse response) throws Exception {
        // CPU 집약적 작업 시뮬레이션
        double result = 0;
        for (int i = 0; i < 100000; i++) {
            result += Math.sqrt(i) * Math.sin(i);
        }

        response.sendJson(String.format(
                "{ \"server\": \"threaded\", \"result\": %.2f, \"thread\": \"%s\" }",
                result, Thread.currentThread().getName()
        ));
    }
}