package server.core.routing;

import server.core.http.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 개별 라우트 정의
 */
public class Route {
    private final HttpMethod method;
    private final String pattern;
    private final Pattern compiledPattern;
    private final List<String> parameterNames;
    private final RouteHandler handler;
    private final int priority;

    public Route(HttpMethod method, String pattern, RouteHandler handler) {
        this(method, pattern, handler, 0);
    }

    public Route(HttpMethod method, String pattern, RouteHandler handler, int priority) {
        this.method = Objects.requireNonNull(method);
        this.pattern = Objects.requireNonNull(pattern);
        this.handler = Objects.requireNonNull(handler);
        this.priority = priority;

        // 패턴 컴파일 및 파라미터 추출
        PatternResult result = compilePattern(pattern);
        this.compiledPattern = result.pattern;
        this.parameterNames = result.parameterNames;
    }

    /**
     * 경로 패턴을 정규식으로 컴파일
     * /users/{id} -> /users/([^/]+)
     * /users/{id:\\d+} -> /users/(\\d+)
     */
    private PatternResult compilePattern(String pattern) {
        List<String> paramNames = new ArrayList<>();
        StringBuilder regex = new StringBuilder();

        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);

            if (c == '{') {
                // 파라미터 시작
                int end = pattern.indexOf('}', i);
                if (end == -1) {
                    throw new IllegalArgumentException("Unclosed parameter in pattern: " + pattern);
                }

                String param = pattern.substring(i + 1, end);
                String paramName;
                String paramRegex = "[^/]+"; // 기본값

                // 커스텀 정규식 확인: {id:\\d+}
                int colonIndex = param.indexOf(':');
                if (colonIndex != -1) {
                    paramName = param.substring(0, colonIndex);
                    paramRegex = param.substring(colonIndex + 1);
                } else {
                    paramName = param;
                }

                paramNames.add(paramName);
                regex.append('(').append(paramRegex).append(')');
                i = end + 1;
            } else if (c == '*') {
                // 와일드카드
                regex.append(".*");
                i++;
            } else {
                // 일반 문자 (정규식 특수문자 이스케이프)
                if ("[](){}*+?.^$|\\".indexOf(c) != -1) {
                    regex.append('\\');
                }
                regex.append(c);
                i++;
            }
        }

        return new PatternResult(Pattern.compile("^" + regex.toString() + "$"), paramNames);
    }

    /**
     * 요청이 이 라우트와 매치되는지 확인
     */
    public RouteMatchResult match(HttpRequest request) {
        if (method != request.getMethod()) {
            return null;
        }

        String path = request.getPath();
        Matcher matcher = compiledPattern.matcher(path);

        if (!matcher.matches()) {
            return null;
        }

        // 경로 파라미터 추출
        Map<String, String> pathParams = new HashMap<>();
        for (int i = 0; i < parameterNames.size() && i < matcher.groupCount(); i++) {
            pathParams.put(parameterNames.get(i), matcher.group(i + 1));
        }

        return new RouteMatchResult(this, pathParams);
    }

    public HttpMethod getMethod() { return method; }
    public String getPattern() { return pattern; }
    public RouteHandler getHandler() { return handler; }
    public int getPriority() { return priority; }

    @Override
    public String toString() {
        return String.format("Route{%s %s}", method, pattern);
    }

    private static class PatternResult {
        final Pattern pattern;
        final List<String> parameterNames;

        PatternResult(Pattern pattern, List<String> parameterNames) {
            this.pattern = pattern;
            this.parameterNames = parameterNames;
        }
    }
}