package com.serverarch.eventloop.routing;

import com.serverarch.eventloop.http.HttpRequest;
import com.serverarch.eventloop.http.HttpResponse;
import com.serverarch.common.http.HttpStatus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * EventLoop용 간단한 Router 구현체
 *
 * 비동기 요청 처리를 위한 라우터로, CompletableFuture를 사용하여
 * 논블로킹 방식으로 요청을 처리한다.
 */
public class SimpleEventLoopRouter implements Router {

    private static final Logger logger = Logger.getLogger(SimpleEventLoopRouter.class.getName());

    // 라우트 저장소: "METHOD:PATH" -> AsyncRouteHandler
    private final ConcurrentHashMap<String, AsyncRouteHandler> routes = new ConcurrentHashMap<>();

    // 와일드카드 핸들러 (모든 경로에 대응)
    private AsyncRouteHandler defaultHandler;

    public SimpleEventLoopRouter() {
        // 기본 404 핸들러 설정
        this.defaultHandler = request ->
                CompletableFuture.completedFuture(
                        HttpResponse.notFound("요청한 경로를 찾을 수 없습니다: " + request.getPath())
                );
    }

    @Override
    public CompletableFuture<HttpResponse> route(HttpRequest request) {
        if (request == null) {
            logger.warning("null 요청이 전달됨");
            return CompletableFuture.completedFuture(
                    HttpResponse.badRequest("잘못된 요청입니다")
            );
        }

        try {
            String method = request.getMethod();
            String path = request.getPath();

            logger.fine(String.format("라우팅 시작: %s %s", method, path));

            // 정확한 매치 시도
            String routeKey = method + ":" + path;
            AsyncRouteHandler handler = routes.get(routeKey);

            if (handler != null) {
                logger.fine(String.format("정확한 라우트 매치: %s", routeKey));
                return handler.handle(request);
            }

            // 패턴 매치 시도 (간단한 와일드카드만 지원)
            handler = findPatternMatch(method, path);
            if (handler != null) {
                logger.fine(String.format("패턴 매치 성공: %s %s", method, path));
                return handler.handle(request);
            }

            // 매치되는 라우트가 없으면 기본 핸들러 사용
            logger.fine(String.format("매치되는 라우트 없음, 기본 핸들러 사용: %s %s", method, path));
            return defaultHandler.handle(request);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "라우팅 중 오류 발생", e);
            return CompletableFuture.completedFuture(
                    HttpResponse.serverError("라우팅 처리 중 오류가 발생했습니다: " + e.getMessage())
            );
        }
    }

    @Override
    public Router get(String path, AsyncRouteHandler handler) {
        return route("GET", path, handler);
    }

    @Override
    public Router post(String path, AsyncRouteHandler handler) {
        return route("POST", path, handler);
    }

    @Override
    public Router put(String path, AsyncRouteHandler handler) {
        return route("PUT", path, handler);
    }

    @Override
    public Router delete(String path, AsyncRouteHandler handler) {
        return route("DELETE", path, handler);
    }

    @Override
    public Router head(String path, AsyncRouteHandler handler) {
        return route("HEAD", path, handler);
    }

    @Override
    public Router options(String path, AsyncRouteHandler handler) {
        return route("OPTIONS", path, handler);
    }

    @Override
    public Router all(String path, AsyncRouteHandler handler) {
        // 주요 HTTP 메서드들에 대해 동일한 핸들러 등록
        route("GET", path, handler);
        route("POST", path, handler);
        route("PUT", path, handler);
        route("DELETE", path, handler);
        route("HEAD", path, handler);
        route("OPTIONS", path, handler);
        return this;
    }

    @Override
    public Router route(String method, String path, AsyncRouteHandler handler) {
        if (method == null || path == null || handler == null) {
            throw new IllegalArgumentException("메서드, 경로, 핸들러는 null일 수 없습니다");
        }

        String routeKey = method.toUpperCase() + ":" + normalizePath(path);
        routes.put(routeKey, handler);

        logger.info(String.format("라우트 등록: %s", routeKey));
        return this;
    }

    /**
     * 기본 핸들러 설정 (404 처리용)
     * @param handler 기본 핸들러
     * @return 현재 Router 인스턴스
     */
    @Override
    public Router setDefaultHandler(AsyncRouteHandler handler) {
        this.defaultHandler = handler != null ? handler : this.defaultHandler;
        return this;
    }

    /**
     * 패턴 매칭을 통한 라우트 검색
     * 간단한 와일드카드 패턴만 지원 (*, **)
     */
    private AsyncRouteHandler findPatternMatch(String method, String path) {
        String normalizedPath = normalizePath(path);

        for (String routeKey : routes.keySet()) {
            if (routeKey.startsWith(method.toUpperCase() + ":")) {
                String routePath = routeKey.substring(method.length() + 1);

                if (isPatternMatch(routePath, normalizedPath)) {
                    return routes.get(routeKey);
                }
            }
        }

        return null;
    }

    /**
     * 간단한 패턴 매칭
     * * : 단일 세그먼트 매치
     * ** : 모든 하위 경로 매치
     */
    private boolean isPatternMatch(String pattern, String path) {
        // 정확한 매치
        if (pattern.equals(path)) {
            return true;
        }

        // 와일드카드 패턴 매치
        if (pattern.contains("*")) {
            return matchWildcard(pattern, path);
        }

        return false;
    }

    /**
     * 와일드카드 패턴 매칭 로직
     */
    private boolean matchWildcard(String pattern, String path) {
        // 간단한 구현: ** 패턴만 지원
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        }

        // 단일 * 패턴은 추후 구현 필요
        return false;
    }

    /**
     * 경로 정규화 (슬래시 정리)
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }

        // 시작 슬래시 추가
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        // 끝 슬래시 제거 (루트 경로 제외)
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }

    /**
     * 등록된 라우트 수 반환
     */
    @Override
    public int getRouteCount() {
        return routes.size();
    }

    /**
     * 라우터 상태 정보 반환
     */
    @Override
    public String getStatus() {
        return String.format("SimpleEventLoopRouter{routes=%d}", routes.size());
    }

    @Override
    public String toString() {
        return getStatus();
    }
}