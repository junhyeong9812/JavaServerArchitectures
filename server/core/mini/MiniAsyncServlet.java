package server.core.mini;

import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * 비동기 서블릿 지원
 */
public abstract class MiniAsyncServlet extends MiniServlet {

    /**
     * 비동기 요청 처리
     */
    public final CompletableFuture<HttpResponse> serviceAsync(MiniRequest request, MiniResponse response) {
        if (!isInitialized()) {
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Servlet not initialized"));
        }

        try {
            // HTTP 메서드별 라우팅
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
                    response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
                    response.setHeader("Allow", "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH");
                    return CompletableFuture.completedFuture(response.build());
            }
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Servlet error: " + e.getMessage()));
        }
    }

    /**
     * 비동기 GET 처리
     */
    protected CompletableFuture<HttpResponse> doGetAsync(MiniRequest request, MiniResponse response) {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        return CompletableFuture.completedFuture(response.build());
    }

    /**
     * 비동기 POST 처리
     */
    protected CompletableFuture<HttpResponse> doPostAsync(MiniRequest request, MiniResponse response) {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        return CompletableFuture.completedFuture(response.build());
    }

    /**
     * 비동기 PUT 처리
     */
    protected CompletableFuture<HttpResponse> doPutAsync(MiniRequest request, MiniResponse response) {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        return CompletableFuture.completedFuture(response.build());
    }

    /**
     * 비동기 DELETE 처리
     */
    protected CompletableFuture<HttpResponse> doDeleteAsync(MiniRequest request, MiniResponse response) {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        return CompletableFuture.completedFuture(response.build());
    }

    /**
     * 비동기 HEAD 처리
     */
    protected CompletableFuture<HttpResponse> doHeadAsync(MiniRequest request, MiniResponse response) {
        return doGetAsync(request, response)
                .thenApply(httpResponse -> {
                    // HEAD는 body가 없음
                    return HttpResponse.builder(httpResponse.getStatus())
                            .header("Content-Length", String.valueOf(httpResponse.getBodyLength()))
                            .build();
                });
    }

    /**
     * 비동기 OPTIONS 처리
     */
    protected CompletableFuture<HttpResponse> doOptionsAsync(MiniRequest request, MiniResponse response) {
        response.setStatus(HttpStatus.OK);
        response.setHeader("Allow", "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH");
        return CompletableFuture.completedFuture(response.build());
    }

    /**
     * 비동기 PATCH 처리
     */
    protected CompletableFuture<HttpResponse> doPatchAsync(MiniRequest request, MiniResponse response) {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        return CompletableFuture.completedFuture(response.build());
    }
}