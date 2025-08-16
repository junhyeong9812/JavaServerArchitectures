package server.threaded;

import server.core.mini.*;
import server.core.http.*;

/**
 * 헬스체크 서블릿
 * 서버 상태 및 기본 정보 제공
 */
public class HealthServlet extends MiniServlet {

    @Override
    protected void doGet(MiniRequest request, MiniResponse response) throws Exception {
        // 간단한 헬스체크 응답
        response.sendJson(String.format(
                "{ \"status\": \"healthy\", " +
                        "\"server\": \"threaded\", " +
                        "\"timestamp\": %d, " +
                        "\"thread\": \"%s\" }",
                System.currentTimeMillis(),
                Thread.currentThread().getName()
        ));
    }

    @Override
    protected void doPost(MiniRequest request, MiniResponse response) throws Exception {
        // POST도 같은 응답 (일부 헬스체크 도구가 POST 사용)
        doGet(request, response);
    }

    @Override
    protected void doHead(MiniRequest request, MiniResponse response) throws Exception {
        // HEAD 요청 - 헤더만 반환 (바디 없음)
        response.setStatus(HttpStatus.OK);
        response.setContentType("application/json");
    }
}