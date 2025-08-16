package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * 하이브리드 서버용 비동기 서블릿 추상 클래스
 *
 * Core의 MiniServlet을 상속받아 비동기 처리 기능만 추가
 * 기존 동기 메서드들은 그대로 유지하고, 비동기 버전만 새로 제공
 */
public abstract class MiniAsyncServlet extends MiniServlet {

    /**
     * 비동기 요청 처리 메서드 - 하이브리드 서버 전용
     *
     * @param request MiniRequest 래퍼
     * @param response MiniResponse 래퍼
     * @return CompletableFuture<Void> 비동기 처리 완료 시그널
     */
    public CompletableFuture<Void> processAsync(MiniRequest request, MiniResponse response) {
        HttpMethod method = request.getMethod();

        switch (method) {
            case GET:
                return doGetAsync(request, response);
            case POST:
                return doPostAsync(request, response);
            case PUT:
                return doPutAsync(request, response);
            case DELETE:
                return doDeleteAsync(request, response);
            case HEAD:
                return doHeadAsync(request, response);
            case OPTIONS:
                return doOptionsAsync(request, response);
            case PATCH:
                return doPatchAsync(request, response);
            default:
                return CompletableFuture.runAsync(() -> {
                    response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
                    response.setHeader("Allow", "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH");
                });
        }
    }

    /**
     * HTTP 메서드별 비동기 처리 메서드들
     * 서브클래스에서 필요한 메서드만 오버라이드
     */
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                doGet(request, response);
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    protected CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                doPost(request, response);
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    protected CompletableFuture<Void> doPutAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                doPut(request, response);
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    protected CompletableFuture<Void> doDeleteAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                doDelete(request, response);
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    protected CompletableFuture<Void> doHeadAsync(MiniRequest request, MiniResponse response) {
        return doGetAsync(request, response)
                .thenRun(() -> response.clearBody()); // HEAD는 body가 없음
    }

    protected CompletableFuture<Void> doOptionsAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                doOptions(request, response);
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    protected CompletableFuture<Void> doPatchAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                doPatch(request, response);
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }
}