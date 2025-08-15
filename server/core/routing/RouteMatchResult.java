package server.core.routing;

import server.core.http.HttpRequest;
import java.util.*;

/**
 * 라우트 매칭 결과
 */
public class RouteMatchResult {
    private final Route route;
    private final Map<String, String> pathParameters;

    public RouteMatchResult(Route route, Map<String, String> pathParameters) {
        this.route = Objects.requireNonNull(route);
        this.pathParameters = Collections.unmodifiableMap(
                new HashMap<>(Objects.requireNonNull(pathParameters))
        );
    }

    public Route getRoute() {
        return route;
    }

    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    public String getPathParameter(String name) {
        return pathParameters.get(name);
    }

    /**
     * 요청에 경로 파라미터 설정
     */
    public void setPathParametersToRequest(HttpRequest request) {
        for (Map.Entry<String, String> entry : pathParameters.entrySet()) {
            request.setAttribute("path." + entry.getKey(), entry.getValue());
        }
        request.setAttribute("path.parameters", pathParameters);
    }

    @Override
    public String toString() {
        return String.format("RouteMatch{route=%s, params=%s}", route, pathParameters);
    }
}