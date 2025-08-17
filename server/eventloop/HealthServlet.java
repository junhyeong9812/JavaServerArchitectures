package server.eventloop;

import server.core.http.HttpResponse;
import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * EventLoop 서버용 헬스체크 서블릿
 * ThreadedServer의 HealthServlet과 동일한 기능 제공
 */
public class HealthServlet {

    private static final Logger logger = LoggerFactory.getLogger(HealthServlet.class);

    /**
     * 헬스체크 요청 처리
     */
    public CompletableFuture<HttpResponse> handleRequest() {
        return CompletableFuture.completedFuture(
                HttpResponse.json("{\"status\":\"healthy\",\"server\":\"eventloop\"}")
        );
    }

    /**
     * 서버 상태 정보 포함한 상세 헬스체크
     */
    public CompletableFuture<HttpResponse> handleDetailedHealth() {
        return CompletableFuture.completedFuture(
                HttpResponse.json(String.format(
                        "{\"status\":\"healthy\",\"server\":\"eventloop\",\"timestamp\":%d,\"thread\":\"%s\"}",
                        System.currentTimeMillis(),
                        Thread.currentThread().getName()
                ))
        );
    }
}