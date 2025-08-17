package server.core.routing;

// HTTP 관련 클래스들
import server.core.http.*;
// 비동기 처리를 위한 CompletableFuture
import java.util.concurrent.CompletableFuture;

/**
 * 라우트 핸들러 인터페이스
 * CompletableFuture 기반 비동기 처리 지원
 *
 * 역할:
 * - HTTP 요청을 처리하고 응답을 생성하는 함수형 인터페이스
 * - 비동기 처리를 기본으로 하여 고성능 웹 애플리케이션 지원
 * - 동기 핸들러와 비동기 핸들러 모두 지원
 * - 람다 표현식과 메서드 레퍼런스 사용 가능
 */
// @FunctionalInterface: 함수형 인터페이스임을 명시
// 정확히 하나의 추상 메서드만 가져야 함 (default, static 메서드는 여러 개 가능)
@FunctionalInterface
public interface RouteHandler {

    /**
     * HTTP 요청을 처리하고 응답을 반환
     *
     * 이 메서드는 비동기적으로 실행되며, CompletableFuture를 반환하여
     * 호출자가 결과를 비동기적으로 처리할 수 있도록 함
     *
     * @param request HTTP 요청 객체 (헤더, 본문, 파라미터 등 포함)
     * @return 비동기 HTTP 응답 Future 객체
     *
     * 사용 예시:
     * RouteHandler handler = request -> {
     *     // 비동기 데이터베이스 조회
     *     return database.findUserAsync(request.getParameter("id"))
     *         .thenApply(user -> HttpResponse.json(user.toJson()));
     * };
     */
    CompletableFuture<HttpResponse> handle(HttpRequest request);

    /**
     * 동기 핸들러를 비동기로 래핑하는 편의 메서드
     *
     * 기존의 동기식 처리 로직을 비동기 핸들러로 쉽게 변환할 수 있도록 제공
     * 내부적으로 CompletableFuture.completedFuture()를 사용하여
     * 이미 완료된 상태의 Future를 반환
     *
     * @param handler 동기 처리를 수행하는 핸들러
     * @return 동기 핸들러를 래핑한 비동기 핸들러
     *
     * 사용 예시:
     * RouteHandler asyncHandler = RouteHandler.sync(request -> {
     *     // 동기 처리 로직
     *     User user = database.findUser(request.getParameter("id"));
     *     return HttpResponse.json(user.toJson());
     * });
     */
    static RouteHandler sync(SyncRouteHandler handler) {
        // 람다 표현식: request -> CompletableFuture.completedFuture(handler.handle(request))
        // 동기 핸들러의 결과를 즉시 완료된 Future로 래핑
        return request -> CompletableFuture.completedFuture(handler.handle(request));
    }

    /**
     * 동기 핸들러 인터페이스
     *
     * 기존의 동기식 웹 애플리케이션 로직을 위한 인터페이스
     * CompletableFuture를 사용하지 않고 직접 HttpResponse를 반환
     *
     * 특징:
     * - 간단한 로직에 적합
     * - 블로킹 I/O 작업 시 스레드가 대기
     * - 이해하기 쉽고 디버깅이 용이
     * - 기존 서블릿 방식과 유사
     */
    @FunctionalInterface
    interface SyncRouteHandler {

        /**
         * HTTP 요청을 동기적으로 처리하고 응답을 반환
         *
         * @param request HTTP 요청 객체
         * @return HTTP 응답 객체 (즉시 반환)
         *
         * 사용 예시:
         * SyncRouteHandler handler = request -> {
         *     String name = request.getParameter("name");
         *     return HttpResponse.text("Hello, " + name + "!");
         * };
         */
        HttpResponse handle(HttpRequest request);
    }
}

/*
 * 사용 패턴 예시:
 *
 * 1. 람다 표현식을 사용한 간단한 핸들러:
 * router.get("/hello", request ->
 *     CompletableFuture.completedFuture(
 *         HttpResponse.text("Hello World!")));
 *
 * 2. 비동기 데이터베이스 조회:
 * router.get("/users/{id}", request -> {
 *     String userId = request.getAttribute("path.id", String.class);
 *     return userService.findByIdAsync(userId)
 *         .thenApply(user -> user != null
 *             ? HttpResponse.json(user.toJson())
 *             : HttpResponse.notFound());
 * });
 *
 * 3. 동기 핸들러를 비동기로 래핑:
 * router.post("/users", RouteHandler.sync(request -> {
 *     User user = User.fromJson(request.getBodyAsString());
 *     User saved = userService.save(user);
 *     return HttpResponse.json(saved.toJson());
 * }));
 *
 * 4. 복잡한 비동기 체이닝:
 * router.put("/users/{id}", request -> {
 *     String userId = request.getAttribute("path.id", String.class);
 *     User updateData = User.fromJson(request.getBodyAsString());
 *
 *     return userService.findByIdAsync(userId)
 *         .thenCompose(existingUser -> {
 *             if (existingUser == null) {
 *                 return CompletableFuture.completedFuture(HttpResponse.notFound());
 *             }
 *             return userService.updateAsync(userId, updateData);
 *         })
 *         .thenApply(updatedUser -> HttpResponse.json(updatedUser.toJson()))
 *         .exceptionally(throwable ->
 *             HttpResponse.internalServerError("Update failed: " + throwable.getMessage()));
 * });
 *
 * 5. 여러 비동기 작업 조합:
 * router.get("/dashboard", request -> {
 *     CompletableFuture<User> userFuture = getCurrentUserAsync(request);
 *     CompletableFuture<List<Order>> ordersFuture = getRecentOrdersAsync(request);
 *     CompletableFuture<Analytics> analyticsFuture = getAnalyticsAsync(request);
 *
 *     return CompletableFuture.allOf(userFuture, ordersFuture, analyticsFuture)
 *         .thenApply(v -> {
 *             Dashboard dashboard = new Dashboard(
 *                 userFuture.join(),
 *                 ordersFuture.join(),
 *                 analyticsFuture.join()
 *             );
 *             return HttpResponse.json(dashboard.toJson());
 *         });
 * });
 */