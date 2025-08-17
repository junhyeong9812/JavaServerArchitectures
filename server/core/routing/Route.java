package server.core.routing;

// HTTP 관련 클래스들
import server.core.http.*;
// 컬렉션 및 유틸리티
import java.util.*;
// 정규표현식 관련
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 개별 라우트 정의
 *
 * 역할:
 * - 특정 HTTP 메서드와 URL 패턴을 핸들러와 연결
 * - 경로 파라미터 추출 ({id}, {name} 등)
 * - 정규표현식 기반 패턴 매칭
 * - 와일드카드 지원 (*)
 * - 라우트 우선순위 관리
 *
 * 지원하는 패턴:
 * - 정적 경로: "/users", "/api/v1/status"
 * - 파라미터: "/users/{id}", "/api/{version}/users/{id}"
 * - 정규식 파라미터: "/users/{id:\\d+}", "/files/{name:\\w+\\.txt}"
 * - 와일드카드: "/static/*", "\\api\\*\\users"
 */
public class Route {

    // 라우트의 기본 정보들 (생성 후 변경 불가)
    private final HttpMethod method;         // HTTP 메서드 (GET, POST 등)
    private final String pattern;           // 원본 패턴 문자열 ("/users/{id}")
    private final Pattern compiledPattern;  // 컴파일된 정규표현식 패턴
    private final List<String> parameterNames; // 추출할 파라미터 이름들
    private final RouteHandler handler;     // 요청을 처리할 핸들러
    private final int priority;             // 라우트 우선순위 (높을수록 먼저 매칭)

    /**
     * 기본 생성자 (우선순위 0)
     *
     * @param method HTTP 메서드
     * @param pattern URL 패턴
     * @param handler 요청 처리 핸들러
     */
    public Route(HttpMethod method, String pattern, RouteHandler handler) {
        // 기본 우선순위 0으로 다른 생성자 호출
        this(method, pattern, handler, 0);
    }

    /**
     * 완전한 생성자 (우선순위 지정)
     *
     * @param method HTTP 메서드
     * @param pattern URL 패턴
     * @param handler 요청 처리 핸들러
     * @param priority 라우트 우선순위 (높을수록 먼저 검사)
     */
    public Route(HttpMethod method, String pattern, RouteHandler handler, int priority) {
        // Objects.requireNonNull(): null 체크 후 예외 발생
        this.method = Objects.requireNonNull(method);
        this.pattern = Objects.requireNonNull(pattern);
        this.handler = Objects.requireNonNull(handler);
        this.priority = priority;

        // 패턴 컴파일 및 파라미터 추출
        // 패턴을 정규표현식으로 변환하고 파라미터 이름들을 추출
        PatternResult result = compilePattern(pattern);
        this.compiledPattern = result.pattern;
        this.parameterNames = result.parameterNames;
    }

    /**
     * 경로 패턴을 정규식으로 컴파일
     *
     * 변환 예시:
     * - "/users/{id}" -> "/users/([^/]+)"
     * - "/users/{id:\\d+}" -> "/users/(\\d+)"
     * - "\\api\\*\\users" -> "\\api\\.*\\users"
     *
     * @param pattern 원본 URL 패턴
     * @return 컴파일된 패턴과 파라미터 이름 목록
     */
    private PatternResult compilePattern(String pattern) {
        // 파라미터 이름들을 저장할 리스트
        List<String> paramNames = new ArrayList<>();

        // 정규표현식을 구성할 StringBuilder
        StringBuilder regex = new StringBuilder();

        // 패턴 문자열을 한 문자씩 처리
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);

            if (c == '{') {
                // 파라미터 시작 - {id} 또는 {id:\\d+} 형태

                // 닫는 중괄호 찾기
                int end = pattern.indexOf('}', i);
                if (end == -1) {
                    // 닫는 중괄호가 없으면 잘못된 패턴
                    throw new IllegalArgumentException("Unclosed parameter in pattern: " + pattern);
                }

                // 중괄호 안의 내용 추출 (예: "id" 또는 "id:\\d+")
                String param = pattern.substring(i + 1, end);
                String paramName;           // 파라미터 이름
                String paramRegex = "[^/]+"; // 기본 정규식 (슬래시가 아닌 모든 문자)

                // 커스텀 정규식 확인: {id:\\d+} 형태
                int colonIndex = param.indexOf(':');
                if (colonIndex != -1) {
                    // 콜론이 있으면 이름과 정규식을 분리
                    paramName = param.substring(0, colonIndex);
                    paramRegex = param.substring(colonIndex + 1);
                } else {
                    // 콜론이 없으면 파라미터 이름만
                    paramName = param;
                }

                // 파라미터 이름을 목록에 추가
                paramNames.add(paramName);

                // 정규식에 캡처 그룹 추가 (괄호로 감싸서 나중에 추출 가능)
                regex.append('(').append(paramRegex).append(')');

                // 다음 위치로 이동 (닫는 중괄호 다음)
                i = end + 1;

            } else if (c == '*') {
                // 와일드카드 - 임의의 문자들과 매치
                // ".*": 모든 문자(개행 제외)를 0개 이상 매치
                regex.append(".*");
                i++;

            } else {
                // 일반 문자 처리

                // 정규식 특수문자인 경우 이스케이프 처리
                // indexOf(): 문자가 특수문자 목록에 있는지 확인
                if ("[](){}*+?.^$|\\".indexOf(c) != -1) {
                    // 백슬래시를 앞에 붙여서 리터럴 문자로 처리
                    regex.append('\\');
                }
                regex.append(c);
                i++;
            }
        }

        // 완전한 매치를 위해 앵커 추가
        // ^: 문자열 시작, $: 문자열 끝
        // Pattern.compile(): 정규표현식 문자열을 Pattern 객체로 컴파일
        Pattern compiledPattern = Pattern.compile("^" + regex.toString() + "$");

        return new PatternResult(compiledPattern, paramNames);
    }

    /**
     * 요청이 이 라우트와 매치되는지 확인
     *
     * @param request HTTP 요청 객체
     * @return 매치 결과 (매치되지 않으면 null)
     */
    public RouteMatchResult match(HttpRequest request) {
        // 1. HTTP 메서드 확인
        if (method != request.getMethod()) {
            // 메서드가 다르면 매치 실패
            return null;
        }

        // 2. URL 경로 추출 및 패턴 매칭
        String path = request.getPath();

        // Matcher: 정규표현식 패턴을 특정 문자열에 적용하는 객체
        Matcher matcher = compiledPattern.matcher(path);

        // matches(): 전체 문자열이 패턴과 완전히 매치되는지 확인
        if (!matcher.matches()) {
            // 패턴이 매치되지 않으면 실패
            return null;
        }

        // 3. 경로 파라미터 추출
        Map<String, String> pathParams = new HashMap<>();

        // 매치된 그룹들을 순회하며 파라미터 값 추출
        // groupCount(): 캡처 그룹의 개수 반환
        for (int i = 0; i < parameterNames.size() && i < matcher.groupCount(); i++) {
            // group(n): n번째 캡처 그룹의 매치된 문자열 반환 (1부터 시작)
            pathParams.put(parameterNames.get(i), matcher.group(i + 1));
        }

        // 4. 매치 결과 객체 생성하여 반환
        return new RouteMatchResult(this, pathParams);
    }

    // === Getter 메서드들 ===

    /**
     * HTTP 메서드 반환
     */
    public HttpMethod getMethod() {
        return method;
    }

    /**
     * 원본 패턴 문자열 반환
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * 핸들러 반환
     */
    public RouteHandler getHandler() {
        return handler;
    }

    /**
     * 우선순위 반환
     */
    public int getPriority() {
        return priority;
    }

    /**
     * 라우트 정보의 문자열 표현
     * 디버깅과 로깅에 유용
     */
    @Override
    public String toString() {
        return String.format("Route{%s %s}", method, pattern);
    }

    /**
     * 패턴 컴파일 결과를 담는 내부 클래스
     * 컴파일된 정규표현식 패턴과 추출된 파라미터 이름들을 함께 저장
     */
    private static class PatternResult {
        final Pattern pattern;              // 컴파일된 정규표현식 패턴
        final List<String> parameterNames;  // 추출된 파라미터 이름 목록

        PatternResult(Pattern pattern, List<String> parameterNames) {
            this.pattern = pattern;
            this.parameterNames = parameterNames;
        }
    }
}

/*
 * 패턴 매칭 예시:
 *
 * 1. 정적 경로:
 * - 패턴: "/users"
 * - 매치: "/users" ✓
 * - 불매치: "/users/123" ✗, "/user" ✗
 *
 * 2. 단일 파라미터:
 * - 패턴: "/users/{id}"
 * - 정규식: "/users/([^/]+)"
 * - 매치: "/users/123" -> {id: "123"}
 * - 매치: "/users/john" -> {id: "john"}
 * - 불매치: "/users" ✗, "/users/123/orders" ✗
 *
 * 3. 정규식 파라미터:
 * - 패턴: "/users/{id:\\d+}"
 * - 정규식: "/users/(\\d+)"
 * - 매치: "/users/123" -> {id: "123"}
 * - 불매치: "/users/john" ✗ (숫자가 아님)
 *
 * 4. 다중 파라미터:
 * - 패턴: "/users/{userId}/orders/{orderId}"
 * - 정규식: "/users/([^/]+)/orders/([^/]+)"
 * - 매치: "/users/123/orders/456" -> {userId: "123", orderId: "456"}
 *
 * 5. 와일드카드:
 * - 패턴: "/static/*"
 * - 정규식: "/static/.*"
 * - 매치: "/static/css/style.css", "/static/js/app.js"
 *
 * 6. 복합 패턴:
 * - 패턴: "/api/v{version:\\d+}/users/{id:\\d+}/profile"
 * - 정규식: "/api/v(\\d+)/users/(\\d+)/profile"
 * - 매치: "/api/v1/users/123/profile" -> {version: "1", id: "123"}
 */