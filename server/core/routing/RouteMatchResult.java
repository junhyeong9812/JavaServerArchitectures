package server.core.routing;

// HTTP 요청 클래스
import server.core.http.HttpRequest;
// 컬렉션 관련 클래스들
import java.util.*;

/**
 * 라우트 매칭 결과
 *
 * 역할:
 * - 요청 URL이 특정 라우트 패턴과 매치되었을 때의 결과 정보 저장
 * - 매치된 라우트 정보와 추출된 경로 파라미터 관리
 * - 경로 파라미터를 요청 객체에 설정하는 기능 제공
 * - 불변 객체로 설계되어 스레드 안전성 보장
 *
 * 예시:
 * - 라우트 패턴: "/users/{id}/orders/{orderId}"
 * - 요청 URL: "/users/123/orders/456"
 * - 추출된 파라미터: {id: "123", orderId: "456"}
 */
public class RouteMatchResult {

    // 매치된 라우트 객체 (불변)
    // Route 객체는 HTTP 메서드, 패턴, 핸들러 정보를 포함
    private final Route route;

    // 추출된 경로 파라미터들 (불변 맵)
    // Key: 파라미터명 (예: "id", "orderId")
    // Value: 파라미터값 (예: "123", "456")
    private final Map<String, String> pathParameters;

    /**
     * 라우트 매칭 결과 생성자
     *
     * @param route 매치된 라우트 객체
     * @param pathParameters 추출된 경로 파라미터 맵
     */
    public RouteMatchResult(Route route, Map<String, String> pathParameters) {
        // Objects.requireNonNull(): null 체크 후 예외 발생
        // null이면 NullPointerException 발생, 아니면 그대로 반환
        this.route = Objects.requireNonNull(route);

        // 경로 파라미터 맵을 불변으로 만들기
        // 1. Objects.requireNonNull(): null 체크
        // 2. new HashMap<>(): 새로운 HashMap으로 복사 (원본 보호)
        // 3. Collections.unmodifiableMap(): 수정 불가능한 맵으로 래핑
        this.pathParameters = Collections.unmodifiableMap(
                new HashMap<>(Objects.requireNonNull(pathParameters))
        );
    }

    /**
     * 매치된 라우트 반환
     *
     * @return 매치된 Route 객체
     */
    public Route getRoute() {
        return route;
    }

    /**
     * 모든 경로 파라미터 반환
     *
     * 불변 맵을 반환하므로 외부에서 수정할 수 없음
     *
     * @return 경로 파라미터 맵 (불변)
     *
     * 사용 예시:
     * Map<String, String> params = matchResult.getPathParameters();
     * for (Map.Entry<String, String> entry : params.entrySet()) {
     *     System.out.println(entry.getKey() + " = " + entry.getValue());
     * }
     */
    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    /**
     * 특정 경로 파라미터 값 반환
     *
     * @param name 파라미터명
     * @return 파라미터값 (없으면 null)
     *
     * 사용 예시:
     * String userId = matchResult.getPathParameter("id");
     * String orderId = matchResult.getPathParameter("orderId");
     */
    public String getPathParameter(String name) {
        // Map.get(): 키에 해당하는 값 반환, 없으면 null
        return pathParameters.get(name);
    }

    /**
     * 요청에 경로 파라미터 설정
     *
     * 추출된 경로 파라미터들을 HttpRequest의 속성으로 설정하여
     * 핸들러에서 쉽게 접근할 수 있도록 함
     *
     * 설정되는 속성들:
     * 1. 개별 파라미터: "path.{파라미터명}" 형태로 각각 설정
     * 2. 전체 파라미터 맵: "path.parameters" 키로 전체 맵 설정
     *
     * @param request 경로 파라미터를 설정할 HTTP 요청 객체
     *
     * 사용 예시:
     * // 라우터에서 매치 결과를 요청에 설정
     * matchResult.setPathParametersToRequest(request);
     *
     * // 핸들러에서 파라미터 접근
     * String userId = request.getAttribute("path.id", String.class);
     * Map<String, String> allParams = request.getAttribute("path.parameters", Map.class);
     */
    public void setPathParametersToRequest(HttpRequest request) {
        // 각 경로 파라미터를 개별 속성으로 설정
        // Map.Entry: 맵의 키-값 쌍을 나타내는 인터페이스
        for (Map.Entry<String, String> entry : pathParameters.entrySet()) {
            // "path." 접두사를 붙여서 다른 속성과 구분
            // 예: {id: "123"} -> request.setAttribute("path.id", "123")
            request.setAttribute("path." + entry.getKey(), entry.getValue());
        }

        // 전체 파라미터 맵을 하나의 속성으로도 설정
        // 핸들러에서 전체 파라미터에 한번에 접근할 수 있도록 함
        request.setAttribute("path.parameters", pathParameters);
    }

    /**
     * 매칭 결과의 문자열 표현
     * 디버깅과 로깅에 유용
     *
     * @return 매칭 결과를 나타내는 문자열
     *
     * 출력 예시:
     * "RouteMatch{route=Route{GET /users/{id}}, params={id=123}}"
     */
    @Override
    public String toString() {
        // String.format(): printf 스타일의 문자열 포맷팅
        // %s: 문자열 형식 지정자
        return String.format("RouteMatch{route=%s, params=%s}", route, pathParameters);
    }
}

/*
 * 사용 시나리오 예시:
 *
 * 1. 기본 경로 파라미터 사용:
 * - 라우트: "/users/{id}"
 * - 요청: "/users/123"
 * - 결과: {id: "123"}
 *
 * RouteMatchResult result = route.match(request);
 * if (result != null) {
 *     result.setPathParametersToRequest(request);
 *     String userId = result.getPathParameter("id"); // "123"
 * }
 *
 * 2. 다중 경로 파라미터:
 * - 라우트: "/users/{userId}/orders/{orderId}"
 * - 요청: "/users/123/orders/456"
 * - 결과: {userId: "123", orderId: "456"}
 *
 * Map<String, String> params = result.getPathParameters();
 * String userId = params.get("userId");     // "123"
 * String orderId = params.get("orderId");   // "456"
 *
 * 3. 정규식 파라미터:
 * - 라우트: "/api/v{version:\\d+}/users/{id:\\d+}"
 * - 요청: "/api/v1/users/123"
 * - 결과: {version: "1", id: "123"}
 *
 * String version = result.getPathParameter("version"); // "1"
 * String id = result.getPathParameter("id");           // "123"
 *
 * 4. 핸들러에서 파라미터 사용:
 * RouteHandler handler = request -> {
 *     // 개별 파라미터 접근
 *     String id = request.getAttribute("path.id", String.class);
 *
 *     // 전체 파라미터 맵 접근
 *     Map<String, String> allParams = request.getAttribute("path.parameters", Map.class);
 *
 *     // 비즈니스 로직 처리
 *     return userService.findByIdAsync(id)
 *         .thenApply(user -> HttpResponse.json(user.toJson()));
 * };
 */