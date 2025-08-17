package server.core.routing;

// HTTP 관련 클래스들
import server.core.http.*;
// 컬렉션 및 동시성 처리
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 현대적 라우팅 시스템
 * 패턴 매칭 + RESTful 지원
 *
 * 역할:
 * - HTTP 요청을 URL 패턴과 메서드에 따라 적절한 핸들러로 라우팅
 * - RESTful API 라우트 자동 생성
 * - 미들웨어 체인 지원
 * - 우선순위 기반 라우트 매칭
 * - 404, 405 에러 자동 처리
 *
 * 특징:
 * - 정규표현식 기반 패턴 매칭
 * - 경로 파라미터 추출 ({id}, {name:\\w+} 등)
 * - 와일드카드 지원 (*)
 * - 비동기 처리 (CompletableFuture 기반)
 * - 메서드 체이닝으로 직관적인 API
 */
public class Router {

    // 등록된 모든 라우트들을 저장하는 리스트
    // 우선순위 순서로 정렬되어 저장됨 (높은 우선순위가 먼저)
    private final List<Route> routes;

    // 404 Not Found 처리를 위한 기본 핸들러
    private final RouteHandler notFoundHandler;

    // 405 Method Not Allowed 처리를 위한 기본 핸들러
    private final RouteHandler methodNotAllowedHandler;

    // 경로별 허용 메서드 추적 맵
    // Key: 경로 패턴, Value: 해당 경로에서 지원하는 HTTP 메서드들
    // 405 에러 시 Allow 헤더 생성에 사용
    private final Map<String, Set<HttpMethod>> pathMethods; // 경로별 허용 메서드 추적

    /**
     * 라우터 생성자
     * 기본 에러 핸들러들과 자료구조들을 초기화
     */
    public Router() {
        // 라우트 목록 초기화
        this.routes = new ArrayList<>();

        // 경로별 메서드 추적 맵 초기화
        this.pathMethods = new HashMap<>();

        // 404 Not Found 기본 핸들러
        // 매치되는 라우트가 없을 때 사용
        this.notFoundHandler = request ->
                CompletableFuture.completedFuture(HttpResponse.notFound());

        // 405 Method Not Allowed 기본 핸들러
        // 같은 경로에 다른 메서드가 있을 때 사용
        this.methodNotAllowedHandler = request -> {
            String path = request.getPath();

            // 해당 경로에서 지원하는 메서드들 확인
            Set<HttpMethod> allowedMethods = pathMethods.get(path);
            if (allowedMethods != null && !allowedMethods.isEmpty()) {
                // 지원하는 메서드들을 문자열로 변환
                // stream().map(): 각 HttpMethod를 문자열로 변환
                // toArray(): 배열로 변환
                // String.join(): 쉼표로 구분하여 합치기
                String methods = String.join(", ",
                        allowedMethods.stream().map(HttpMethod::toString).toArray(String[]::new));

                // Allow 헤더와 함께 405 응답 반환
                return CompletableFuture.completedFuture(
                        HttpResponse.methodNotAllowed(methods));
            }

            // 허용 메서드가 없으면 404로 처리
            return CompletableFuture.completedFuture(HttpResponse.notFound());
        };
    }

    // === 라우트 등록 메서드 ===
    // 각 HTTP 메서드별로 편의 메서드 제공

    /**
     * GET 라우트 등록
     * 리소스 조회, 페이지 표시 등에 사용
     *
     * @param pattern URL 패턴 (예: "/users/{id}")
     * @param handler 요청을 처리할 핸들러
     * @return 메서드 체이닝을 위한 this 객체
     */
    public Router get(String pattern, RouteHandler handler) {
        return addRoute(HttpMethod.GET, pattern, handler);
    }

    /**
     * POST 라우트 등록
     * 새로운 리소스 생성, 폼 제출 등에 사용
     */
    public Router post(String pattern, RouteHandler handler) {
        return addRoute(HttpMethod.POST, pattern, handler);
    }

    /**
     * PUT 라우트 등록
     * 리소스 전체 수정 또는 생성에 사용
     */
    public Router put(String pattern, RouteHandler handler) {
        return addRoute(HttpMethod.PUT, pattern, handler);
    }

    /**
     * DELETE 라우트 등록
     * 리소스 삭제에 사용
     */
    public Router delete(String pattern, RouteHandler handler) {
        return addRoute(HttpMethod.DELETE, pattern, handler);
    }

    /**
     * PATCH 라우트 등록
     * 리소스 부분 수정에 사용
     */
    public Router patch(String pattern, RouteHandler handler) {
        return addRoute(HttpMethod.PATCH, pattern, handler);
    }

    /**
     * OPTIONS 라우트 등록
     * CORS preflight 요청, 지원 메서드 확인 등에 사용
     */
    public Router options(String pattern, RouteHandler handler) {
        return addRoute(HttpMethod.OPTIONS, pattern, handler);
    }

    /**
     * HEAD 라우트 등록
     * GET과 동일하지만 응답 본문 없이 헤더만 반환
     */
    public Router head(String pattern, RouteHandler handler) {
        return addRoute(HttpMethod.HEAD, pattern, handler);
    }

    /**
     * 모든 HTTP 메서드에 대한 라우트 등록
     * 모든 메서드에 동일한 핸들러를 적용할 때 사용
     *
     * @param pattern URL 패턴
     * @param handler 모든 메서드에 적용할 핸들러
     * @return 메서드 체이닝을 위한 this 객체
     */
    public Router all(String pattern, RouteHandler handler) {
        // HttpMethod.values(): 모든 HTTP 메서드 열거형 값들을 배열로 반환
        for (HttpMethod method : HttpMethod.values()) {
            addRoute(method, pattern, handler);
        }
        return this;
    }

    /**
     * 일반적인 라우트 등록
     * 우선순위 0으로 기본 등록
     *
     * @param method HTTP 메서드
     * @param pattern URL 패턴
     * @param handler 요청 처리 핸들러
     * @return 메서드 체이닝을 위한 this 객체
     */
    public Router addRoute(HttpMethod method, String pattern, RouteHandler handler) {
        return addRoute(method, pattern, handler, 0);
    }

    /**
     * 우선순위가 있는 라우트 등록
     *
     * @param method HTTP 메서드
     * @param pattern URL 패턴
     * @param handler 요청 처리 핸들러
     * @param priority 우선순위 (높을수록 먼저 매칭)
     * @return 메서드 체이닝을 위한 this 객체
     */
    public Router addRoute(HttpMethod method, String pattern, RouteHandler handler, int priority) {
        // 새 라우트 생성
        Route route = new Route(method, pattern, handler, priority);

        // 라우트 목록에 추가
        routes.add(route);

        // 우선순위 순으로 정렬 (높은 우선순위가 먼저)
        // Collections.sort(): 리스트를 정렬
        // Comparator.compare(): 두 값을 비교하여 정렬 순서 결정
        // r2.getPriority() - r1.getPriority(): 내림차순 정렬 (높은 값이 먼저)
        routes.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));

        // 경로별 메서드 추적 정보 업데이트
        updatePathMethods(pattern, method);

        return this;
    }

    /**
     * RESTful 리소스 라우트 등록
     * 하나의 ResourceHandler로 전체 CRUD API를 자동 생성
     *
     * @param basePath 기본 경로 (예: "/users")
     * @param resourceHandler RESTful 핸들러들을 담은 객체
     * @return 메서드 체이닝을 위한 this 객체
     *
     * 생성되는 라우트들:
     * GET    /basePath        -> resourceHandler.index
     * GET    /basePath/{id}   -> resourceHandler.show
     * POST   /basePath        -> resourceHandler.create
     * PUT    /basePath/{id}   -> resourceHandler.update
     * DELETE /basePath/{id}   -> resourceHandler.delete
     */
    public Router resource(String basePath, ResourceHandler resourceHandler) {
        // 경로 정규화: 앞에 /가 없으면 추가, 뒤에 /가 있으면 제거
        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        if (basePath.endsWith("/")) {
            // substring(): 문자열의 일부분 추출
            basePath = basePath.substring(0, basePath.length() - 1);
        }

        // GET /resources - index (목록 조회)
        if (resourceHandler.getIndexHandler() != null) {
            get(basePath, resourceHandler.getIndexHandler());
        }

        // GET /resources/{id} - show (개별 조회)
        if (resourceHandler.getShowHandler() != null) {
            get(basePath + "/{id}", resourceHandler.getShowHandler());
        }

        // POST /resources - create (새 리소스 생성)
        if (resourceHandler.getCreateHandler() != null) {
            post(basePath, resourceHandler.getCreateHandler());
        }

        // PUT /resources/{id} - update (전체 수정)
        if (resourceHandler.getUpdateHandler() != null) {
            put(basePath + "/{id}", resourceHandler.getUpdateHandler());
        }

        // DELETE /resources/{id} - delete (삭제)
        if (resourceHandler.getDeleteHandler() != null) {
            delete(basePath + "/{id}", resourceHandler.getDeleteHandler());
        }

        return this;
    }

    // === 라우트 매칭 및 처리 ===

    /**
     * 요청을 처리하고 응답 반환
     * 라우터의 핵심 메서드
     *
     * @param request HTTP 요청 객체
     * @return 비동기 HTTP 응답
     */
    public CompletableFuture<HttpResponse> route(HttpRequest request) {
        // 1. 매칭되는 라우트 찾기
        RouteMatchResult matchResult = findMatchingRoute(request);

        if (matchResult == null) {
            // 매칭되는 라우트가 없으면 404 또는 405 처리
            return handleNotFound(request);
        }

        // 2. 경로 파라미터를 요청에 설정
        // 핸들러에서 경로 파라미터에 접근할 수 있도록 함
        matchResult.setPathParametersToRequest(request);

        try {
            // 3. 매칭된 핸들러 실행
            return matchResult.getRoute().getHandler().handle(request)
                    // exceptionally(): 예외 발생 시 대체 결과 제공
                    .exceptionally(throwable -> {
                        // 핸들러에서 예외 발생시 500 에러 반환
                        return HttpResponse.internalServerError("Handler error: " + throwable.getMessage());
                    });
        } catch (Exception e) {
            // 핸들러 호출 자체에서 예외 발생 시
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Route processing error: " + e.getMessage()));
        }
    }

    /**
     * 매칭되는 라우트 찾기
     * 등록된 모든 라우트를 우선순위 순으로 검사
     *
     * @param request HTTP 요청 객체
     * @return 매칭 결과 (없으면 null)
     */
    private RouteMatchResult findMatchingRoute(HttpRequest request) {
        // 우선순위 순으로 정렬된 라우트들을 순회
        for (Route route : routes) {
            // 각 라우트와 매칭 시도
            RouteMatchResult result = route.match(request);
            if (result != null) {
                // 첫 번째로 매칭되는 라우트 반환 (우선순위가 높은 것부터 검사)
                return result;
            }
        }
        // 매칭되는 라우트가 없으면 null 반환
        return null;
    }

    /**
     * 404 Not Found 처리
     * 매칭되는 라우트가 없을 때 호출됨
     *
     * @param request HTTP 요청 객체
     * @return 404 또는 405 응답
     */
    private CompletableFuture<HttpResponse> handleNotFound(HttpRequest request) {
        // 같은 경로에 다른 메서드가 있는지 확인
        String path = request.getPath();

        // 모든 라우트를 검사하여 같은 경로에 다른 메서드가 있는지 확인
        for (Route route : routes) {
            // 임시 GET 요청을 만들어서 경로만 확인
            // 실제 메서드는 다르지만 경로 패턴이 같은지 검사
            HttpRequest tempRequest = new HttpRequest(HttpMethod.GET, path, "HTTP/1.1",
                    new HttpHeaders(), new byte[0]);

            if (route.match(tempRequest) != null) {
                // 같은 경로에 다른 메서드가 있으면 405 Method Not Allowed
                return methodNotAllowedHandler.handle(request);
            }
        }

        // 같은 경로에 다른 메서드도 없으면 404 Not Found
        return notFoundHandler.handle(request);
    }

    /**
     * 경로별 허용 메서드 업데이트
     * 405 에러 시 Allow 헤더 생성을 위한 정보 관리
     *
     * @param pattern URL 패턴
     * @param method HTTP 메서드
     */
    private void updatePathMethods(String pattern, HttpMethod method) {
        // 단순한 패턴만 추적 (파라미터나 와일드카드가 없는 경우)
        // 복잡한 패턴은 성능상 이유로 추적하지 않음
        if (!pattern.contains("{") && !pattern.contains("*")) {
            // computeIfAbsent(): 키가 없으면 새 Set 생성, 있으면 기존 Set 반환
            pathMethods.computeIfAbsent(pattern, k -> new HashSet<>()).add(method);
        }
    }

    // === 미들웨어 지원 ===

    /**
     * 미들웨어 인터페이스
     * 요청 처리 전후에 실행되는 로직을 정의
     *
     * 사용 예시:
     * - 인증/인가 처리
     * - 로깅
     * - 요청/응답 변환
     * - 캐싱
     * - CORS 처리
     */
    @FunctionalInterface
    public interface Middleware {
        /**
         * 미들웨어 처리 메서드
         *
         * @param request HTTP 요청
         * @param next 다음 처리 단계 (다음 미들웨어 또는 최종 핸들러)
         * @return 비동기 HTTP 응답
         */
        CompletableFuture<HttpResponse> handle(HttpRequest request, NextHandler next);
    }

    /**
     * 다음 처리 단계를 나타내는 인터페이스
     * 미들웨어 체인에서 다음 단계로 진행할 때 사용
     */
    @FunctionalInterface
    public interface NextHandler {
        /**
         * 다음 처리 단계 실행
         *
         * @param request HTTP 요청
         * @return 비동기 HTTP 응답
         */
        CompletableFuture<HttpResponse> handle(HttpRequest request);
    }

    /**
     * 글로벌 미들웨어 체인
     * 모든 요청에 적용되는 미들웨어들의 순서 목록
     */
    private final List<Middleware> middlewares = new ArrayList<>();

    /**
     * 미들웨어 추가
     * 등록된 순서대로 실행됨
     *
     * @param middleware 추가할 미들웨어
     * @return 메서드 체이닝을 위한 this 객체
     *
     * 사용 예시:
     * router.use((request, next) -> {
     *     // 요청 전 처리
     *     System.out.println("Before: " + request.getPath());
     *
     *     return next.handle(request).thenApply(response -> {
     *         // 응답 후 처리
     *         System.out.println("After: " + response.getStatus());
     *         return response;
     *     });
     * });
     */
    public Router use(Middleware middleware) {
        middlewares.add(middleware);
        return this;
    }

    /**
     * 미들웨어를 적용한 라우팅
     * 미들웨어 체인을 거쳐서 최종적으로 라우팅 수행
     *
     * @param request HTTP 요청
     * @return 비동기 HTTP 응답
     */
    public CompletableFuture<HttpResponse> routeWithMiddlewares(HttpRequest request) {
        // 미들웨어 체인 실행 (0번 인덱스부터 시작)
        // 최종 핸들러는 this::route (일반 라우팅 메서드)
        return executeMiddlewares(request, 0, this::route);
    }

    /**
     * 미들웨어 체인 실행
     * 재귀적으로 미들웨어들을 순서대로 실행
     *
     * @param request HTTP 요청
     * @param index 현재 실행할 미들웨어 인덱스
     * @param finalHandler 모든 미들웨어 실행 후 최종 핸들러
     * @return 비동기 HTTP 응답
     */
    private CompletableFuture<HttpResponse> executeMiddlewares(HttpRequest request,
                                                               int index,
                                                               NextHandler finalHandler) {
        // 모든 미들웨어를 실행했으면 최종 핸들러 실행
        if (index >= middlewares.size()) {
            return finalHandler.handle(request);
        }

        // 현재 미들웨어 가져오기
        Middleware middleware = middlewares.get(index);

        // 다음 단계를 위한 NextHandler 생성
        // 람다 표현식: req -> executeMiddlewares(req, index + 1, finalHandler)
        // 다음 미들웨어 또는 최종 핸들러를 호출하는 함수
        NextHandler next = req -> executeMiddlewares(req, index + 1, finalHandler);

        // 현재 미들웨어 실행
        return middleware.handle(request, next);
    }

    // === 정보 조회 ===

    /**
     * 등록된 모든 라우트 반환
     * 디버깅이나 관리 목적으로 사용
     *
     * @return 라우트 목록의 복사본 (원본 수정 방지)
     */
    public List<Route> getRoutes() {
        // ArrayList 생성자: 기존 리스트를 복사하여 새 리스트 생성
        return new ArrayList<>(routes);
    }

    /**
     * 라우트 개수 반환
     *
     * @return 등록된 라우트의 총 개수
     */
    public int getRouteCount() {
        return routes.size();
    }

    /**
     * 라우트 정보 출력
     * 디버깅 목적으로 등록된 모든 라우트를 콘솔에 출력
     */
    public void printRoutes() {
        System.out.println("Registered Routes:");

        // forEach(): 리스트의 각 요소에 대해 람다 함수 실행
        // route -> System.out.println("  " + route): 각 라우트를 들여쓰기와 함께 출력
        routes.forEach(route -> System.out.println("  " + route));
    }

    /**
     * 라우터 정보의 문자열 표현
     * 디버깅과 로깅에 유용
     */
    @Override
    public String toString() {
        return String.format("Router{%d routes, %d middlewares}",
                routes.size(), middlewares.size());
    }
}
//