package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * 하이브리드 서버용 비동기 서블릿 인터페이스
 *
 * Core의 MiniServlet을 확장하여 비동기 처리 지원
 * 기존 Core 시스템과 호환성 유지
 */
public interface MiniAsyncServlet extends MiniServlet {

    /**
     * 비동기 요청 처리 메서드
     * 하이브리드 서버에서 호출됨
     *
     * @param request MiniRequest 래퍼
     * @param response MiniResponse 래퍼
     * @return CompletableFuture<Void> 비동기 처리 결과
     */
    CompletableFuture<Void> serviceAsync(MiniRequest request, MiniResponse response);

    /**
     * 기본 service 메서드 오버라이드
     * 동기 호출시 비동기 메서드를 동기적으로 처리
     */
    @Override
    default HttpResponse service(MiniRequest request, MiniResponse response) throws Exception {
        try {
            // 비동기 메서드를 동기적으로 실행
            CompletableFuture<Void> future = serviceAsync(request, response);
            future.get(); // 블로킹 대기
            return response.build();
        } catch (Exception e) {
            throw new RuntimeException("Async servlet execution failed", e);
        }
    }

    /**
     * 기본 HTTP 메서드들을 비동기로 처리
     */
    default CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                doGet(request, response);
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    default CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                doPost(request, response);
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    default CompletableFuture<Void> doPutAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                doPut(request, response);
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    default CompletableFuture<Void> doDeleteAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                doDelete(request, response);
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    default CompletableFuture<Void> doHeadAsync(MiniRequest request, MiniResponse response) {
        return doGetAsync(request, response)
                .thenRun(() -> response.clearBody()); // HEAD는 body가 없음
    }

    default CompletableFuture<Void> doOptionsAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            response.setStatus(HttpStatus.OK);
            response.setHeader("Allow", "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH");
        });
    }

    default CompletableFuture<Void> doPatchAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                doPatch(request, response);
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    /**
     * 기본 serviceAsync 구현 - HTTP 메서드별 라우팅
     */
    default CompletableFuture<Void> serviceAsync(MiniRequest request, MiniResponse response) {
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
}