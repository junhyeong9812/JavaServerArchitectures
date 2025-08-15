package server.core.routing;

import server.core.http.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 현대적 라우팅 시스템
 * 패턴 매칭 + RESTful 지원
 */
public class Router {

    private final List<Route> routes;
    private final RouteHandler notFoundHandler;
    private final RouteHandler methodNotAllowedHandler;
    private final Map<String, Set<HttpMethod>> pathMethods; // 경로별 허용 메서드 추적

    public Router() {
        this.routes = new ArrayList<>();
        this.pathMethods = new HashMap<>();
        this.notFoundHandler = request ->
                CompletableFuture.completedFuture(HttpResponse.notFound());
        this.methodNotAllowedHandler = request -> {
            String path = request.getPath();
            Set<HttpMethod> allowedMethods = pathMethods.get(path);
            if (allowedMethods != null && !allowedMethods.isEmpty()) {
                String methods = String.join(", ",
                        allowedMethods.stream().map(HttpMethod::toString).toArray(String[]::new));
                return CompletableFuture.completedFuture(
                        HttpResponse.methodNotAllowed(methods));
            }
            return CompletableFuture.completedFuture(HttpResponse.notFound());
        };
    }

    // === 라우트 등록 메서드 ===

    /**
     * GET 라우트 등록
     */
    public Router get(String pattern, RouteHandler handler) {
        return addRoute(HttpMethod.GET, pattern, handler);
    }

    /**
     * POST 라우트 등록
     */
    public Router post(String pattern, RouteHandler handler) {
        return addRoute(HttpMethod.POST, pattern, handler);
    }

    /**
     * PUT 라우트 등록
     */
    public Router put(String pattern, RouteHandler handler) {
        return addRoute(HttpMethod.PUT, pattern, handler);
    }

    /**
     * DELETE 라우트 등록
     */
    public Router delete(String pattern, RouteHandler handler) {
        return addRoute(HttpMethod.DELETE, pattern, handler);
    }

    /**
     * PATCH 라우트 등록
     */
    public Router patch(String pattern, RouteHandler handler) {
        return addRoute(HttpMethod.PATCH, pattern, handler);
    }

    /**
     * OPTIONS 라우트 등록
     */
    public Router options(String pattern, RouteHandler handler) {
        return addRoute(HttpMethod.OPTIONS, pattern, handler);
    }

    /**
     * HEAD 라우트 등록
     */
    public Router head(String pattern, RouteHandler handler) {
        return addRoute(HttpMethod.HEAD, pattern, handler);
    }

    /**
     * 모든 HTTP 메서드에 대한 라우트 등록
     */
    public Router all(String pattern, RouteHandler handler) {
        for (HttpMethod method : HttpMethod.values()) {
            addRoute(method, pattern, handler);
        }
        return this;
    }

    /**
     * 일반적인 라우트 등록
     */
    public Router addRoute(HttpMethod method, String pattern, RouteHandler handler) {
        return addRoute(method, pattern, handler, 0);
    }

    /**
     * 우선순위가 있는 라우트 등록
     */
    public Router addRoute(HttpMethod method, String pattern, RouteHandler handler, int priority) {
        Route route = new Route(method, pattern, handler, priority);
        routes.add(route);

        // 우선순위 순으로 정렬 (높은 우선순위가 먼저)
        routes.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));

        // 경로별 메서드 추적
        updatePathMethods(pattern, method);

        return this;
    }

    /**
     * RESTful 리소스 라우트 등록
     */
    public Router resource(String basePath, ResourceHandler resourceHandler) {
        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        if (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }

        // GET /resources - index
        if (resourceHandler.getIndexHandler() != null) {
            get(basePath, resourceHandler.getIndexHandler());
        }

        // GET /resources/{id} - show
        if (resourceHandler.getShowHandler() != null) {
            get(basePath + "/{id}", resourceHandler.getShowHandler());
        }

        // POST /resources - create
        if (resourceHandler.getCreateHandler() != null) {
            post(basePath, resourceHandler.getCreateHandler());
        }

        // PUT /resources/{id} - update
        if (resourceHandler.getUpdateHandler() != null) {
            put(basePath + "/{id}", resourceHandler.getUpdateHandler());
        }

        // DELETE /resources/{id} - delete
        if (resourceHandler.getDeleteHandler() != null) {
            delete(basePath + "/{id}", resourceHandler.getDeleteHandler());
        }

        return this;
    }

    // === 라우트 매칭 및 처리 ===

    /**
     * 요청을 처리하고 응답 반환
     */
    public CompletableFuture<HttpResponse> route(HttpRequest request) {
        RouteMatchResult matchResult = findMatchingRoute(request);

        if (matchResult == null) {
            // 매칭되는 라우트가 없음
            return handleNotFound(request);
        }

        // 경로 파라미터를 요청에 설정
        matchResult.setPathParametersToRequest(request);

        try {
            return matchResult.getRoute().getHandler().handle(request)
                    .exceptionally(throwable -> {
                        // 핸들러에서 예외 발생시 500 에러 반환
                        return HttpResponse.internalServerError("Handler error: " + throwable.getMessage());
                    });
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Route processing error: " + e.getMessage()));
        }
    }

    /**
     * 매칭되는 라우트 찾기
     */
    private RouteMatchResult findMatchingRoute(HttpRequest request) {
        for (Route route : routes) {
            RouteMatchResult result = route.match(request);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * 404 Not Found 처리
     */
    private CompletableFuture<HttpResponse> handleNotFound(HttpRequest request) {
        // 같은 경로에 다른 메서드가 있는지 확인
        String path = request.getPath();
        for (Route route : routes) {
            if (route.match(new HttpRequest(HttpMethod.GET, path, "HTTP/1.1",
                    new HttpHeaders(), new byte[0])) != null) {
                // 같은 경로에 다른 메서드가 있으면 405 Method Not Allowed
                return methodNotAllowedHandler.handle(request);
            }
        }

        return notFoundHandler.handle(request);
    }

    /**
     * 경로별 허용 메서드 업데이트
     */
    private void updatePathMethods(String pattern, HttpMethod method) {
        // 단순한 패턴만 추적 (파라미터가 없는 경우)
        if (!pattern.contains("{") && !pattern.contains("*")) {
            pathMethods.computeIfAbsent(pattern, k -> new HashSet<>()).add(method);
        }
    }

    // === 미들웨어 지원 ===

    /**
     * 미들웨어 인터페이스
     */
    @FunctionalInterface
    public interface Middleware {
        CompletableFuture<HttpResponse> handle(HttpRequest request, NextHandler next);
    }

    @FunctionalInterface
    public interface NextHandler {
        CompletableFuture<HttpResponse> handle(HttpRequest request);
    }

    /**
     * 글로벌 미들웨어 체인
     */
    private final List<Middleware> middlewares = new ArrayList<>();

    /**
     * 미들웨어 추가
     */
    public Router use(Middleware middleware) {
        middlewares.add(middleware);
        return this;
    }

    /**
     * 미들웨어를 적용한 라우팅
     */
    public CompletableFuture<HttpResponse> routeWithMiddlewares(HttpRequest request) {
        return executeMiddlewares(request, 0, this::route);
    }

    /**
     * 미들웨어 체인 실행
     */
    private CompletableFuture<HttpResponse> executeMiddlewares(HttpRequest request,
                                                               int index,
                                                               NextHandler finalHandler) {
        if (index >= middlewares.size()) {
            return finalHandler.handle(request);
        }

        Middleware middleware = middlewares.get(index);
        NextHandler next = req -> executeMiddlewares(req, index + 1, finalHandler);

        return middleware.handle(request, next);
    }

    // === 정보 조회 ===

    /**
     * 등록된 모든 라우트 반환
     */
    public List<Route> getRoutes() {
        return new ArrayList<>(routes);
    }

    /**
     * 라우트 개수 반환
     */
    public int getRouteCount() {
        return routes.size();
    }

    /**
     * 라우트 정보 출력
     */
    public void printRoutes() {
        System.out.println("Registered Routes:");
        routes.forEach(route -> System.out.println("  " + route));
    }

    @Override
    public String toString() {
        return String.format("Router{%d routes, %d middlewares}",
                routes.size(), middlewares.size());
    }
}