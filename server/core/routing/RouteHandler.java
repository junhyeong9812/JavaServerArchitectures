package server.core.routing;

import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * 라우트 핸들러 인터페이스
 * CompletableFuture 기반 비동기 처리 지원
 */
@FunctionalInterface
public interface RouteHandler {
    /**
     * HTTP 요청을 처리하고 응답을 반환
     * @param request HTTP 요청
     * @return 비동기 응답
     */
    CompletableFuture<HttpResponse> handle(HttpRequest request);

    /**
     * 동기 핸들러를 비동기로 래핑하는 편의 메서드
     */
    static RouteHandler sync(SyncRouteHandler handler) {
        return request -> CompletableFuture.completedFuture(handler.handle(request));
    }

    /**
     * 동기 핸들러 인터페이스
     */
    @FunctionalInterface
    interface SyncRouteHandler {
        HttpResponse handle(HttpRequest request);
    }
}