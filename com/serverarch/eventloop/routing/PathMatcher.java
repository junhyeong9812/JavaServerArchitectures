package com.serverarch.eventloop.routing;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 경로 패턴 매칭 및 파라미터 추출 유틸리티
 *
 * 경로 패턴에서 {paramName} 형태의 경로 파라미터를 추출하고
 * 실제 요청 경로와 매칭하는 기능을 제공
 */
public class PathMatcher {

    private final String pattern;
    private final Pattern regex;
    private final List<String> parameterNames;
    private final boolean hasParameters;

    /**
     * PathMatcher 생성자
     *
     * @param pattern 경로 패턴 (예: "/users/{id}/posts/{postId}")
     */
    public PathMatcher(String pattern) {
        this.pattern = pattern != null ? pattern : "/";
        this.parameterNames = new ArrayList<>();
        this.regex = compilePattern(this.pattern);
        this.hasParameters = !parameterNames.isEmpty();
    }

    /**
     * 패턴 문자열을 정규식으로 컴파일
     * {paramName} -> 캡처 그룹으로 변환
     */
    private Pattern compilePattern(String pattern) {
        // 파라미터 이름 추출 및 정규식 생성
        StringBuilder regexBuilder = new StringBuilder();

        // 특수 문자 이스케이프를 위한 임시 변환
        String escapedPattern = pattern
                .replace(".", "\\.")
                .replace("+", "\\+")
                .replace("*", "\\*")
                .replace("?", "\\?")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("^", "\\^")
                .replace("$", "\\$")
                .replace("|", "\\|");

        // {paramName} 패턴 찾기
        Pattern paramPattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher matcher = paramPattern.matcher(escapedPattern);

        int lastEnd = 0;
        while (matcher.find()) {
            // 파라미터 이전 부분 추가
            regexBuilder.append(Pattern.quote(escapedPattern.substring(lastEnd, matcher.start())));

            // 파라미터 이름 저장
            String paramName = matcher.group(1);
            parameterNames.add(paramName);

            // 파라미터를 정규식 캡처 그룹으로 변환
            regexBuilder.append("([^/]+)"); // 슬래시를 제외한 모든 문자 매칭

            lastEnd = matcher.end();
        }

        // 남은 부분 추가
        if (lastEnd < escapedPattern.length()) {
            regexBuilder.append(Pattern.quote(escapedPattern.substring(lastEnd)));
        }

        // 정확한 매칭을 위해 시작과 끝 앵커 추가
        return Pattern.compile("^" + regexBuilder.toString() + "$");
    }

    /**
     * 주어진 경로가 이 패턴과 매칭되는지 확인
     *
     * @param path 확인할 경로
     * @return 매칭되면 true, 아니면 false
     */
    public boolean matches(String path) {
        if (path == null) {
            return false;
        }
        return regex.matcher(path).matches();
    }

    /**
     * 경로에서 파라미터 값들을 추출
     *
     * @param path 요청 경로
     * @return 파라미터 맵 (key: 파라미터 이름, value: 추출된 값)
     */
    public Map<String, String> extractParameters(String path) {
        if (path == null || !hasParameters) {
            return Collections.emptyMap();
        }

        Matcher matcher = regex.matcher(path);
        if (!matcher.matches()) {
            return Collections.emptyMap();
        }

        Map<String, String> parameters = new HashMap<>();
        for (int i = 0; i < parameterNames.size(); i++) {
            String paramName = parameterNames.get(i);
            String paramValue = matcher.group(i + 1); // 그룹 1부터 시작
            parameters.put(paramName, paramValue);
        }

        return parameters;
    }

    /**
     * 매칭 결과 정보를 담는 클래스
     */
    public static class MatchResult {
        private final boolean matched;
        private final Map<String, String> parameters;

        public MatchResult(boolean matched, Map<String, String> parameters) {
            this.matched = matched;
            this.parameters = parameters != null ? parameters : Collections.emptyMap();
        }

        public boolean isMatched() {
            return matched;
        }

        public Map<String, String> getParameters() {
            return Collections.unmodifiableMap(parameters);
        }

        public String getParameter(String name) {
            return parameters.get(name);
        }

        public boolean hasParameter(String name) {
            return parameters.containsKey(name);
        }
    }

    /**
     * 경로 매칭과 파라미터 추출을 한 번에 수행
     *
     * @param path 요청 경로
     * @return MatchResult 객체
     */
    public MatchResult match(String path) {
        if (path == null) {
            return new MatchResult(false, null);
        }

        Matcher matcher = regex.matcher(path);
        if (!matcher.matches()) {
            return new MatchResult(false, null);
        }

        if (!hasParameters) {
            return new MatchResult(true, Collections.emptyMap());
        }

        Map<String, String> parameters = new HashMap<>();
        for (int i = 0; i < parameterNames.size(); i++) {
            String paramName = parameterNames.get(i);
            String paramValue = matcher.group(i + 1);
            parameters.put(paramName, paramValue);
        }

        return new MatchResult(true, parameters);
    }

    // ========== Getter 메서드들 ==========

    /**
     * 원본 패턴 문자열 반환
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * 파라미터 이름 목록 반환
     */
    public List<String> getParameterNames() {
        return Collections.unmodifiableList(parameterNames);
    }

    /**
     * 파라미터가 있는지 확인
     */
    public boolean hasParameters() {
        return hasParameters;
    }

    /**
     * 파라미터 개수 반환
     */
    public int getParameterCount() {
        return parameterNames.size();
    }

    // ========== 유틸리티 메서드들 ==========

    /**
     * 두 패턴의 우선순위 비교
     * 구체적인 패턴이 더 높은 우선순위를 가짐
     *
     * @param other 비교할 다른 PathMatcher
     * @return 이 패턴이 더 구체적이면 음수, 같으면 0, 덜 구체적이면 양수
     */
    public int comparePriority(PathMatcher other) {
        if (other == null) {
            return -1;
        }

        // 파라미터가 적을수록 더 구체적
        int paramDiff = this.parameterNames.size() - other.parameterNames.size();
        if (paramDiff != 0) {
            return paramDiff;
        }

        // 패턴 길이가 길수록 더 구체적
        int lengthDiff = other.pattern.length() - this.pattern.length();
        if (lengthDiff != 0) {
            return lengthDiff;
        }

        // 사전순 정렬
        return this.pattern.compareTo(other.pattern);
    }

    /**
     * 정적 팩토리 메서드 - 패턴 검증과 함께 생성
     */
    public static PathMatcher of(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("패턴은 null이거나 빈 문자열일 수 없습니다");
        }

        // 기본적인 패턴 검증
        if (!pattern.startsWith("/")) {
            throw new IllegalArgumentException("패턴은 '/'로 시작해야 합니다: " + pattern);
        }

        return new PathMatcher(pattern);
    }

    /**
     * 간단한 경로 매칭 (파라미터 없는 경우)
     */
    public static boolean simpleMatch(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }

        if (pattern.contains("{")) {
            // 파라미터가 있는 경우 PathMatcher 사용
            return new PathMatcher(pattern).matches(path);
        } else {
            // 단순 문자열 비교
            return pattern.equals(path);
        }
    }

    // ========== Object 메서드 오버라이드 ==========

    @Override
    public String toString() {
        return String.format("PathMatcher{pattern='%s', parameters=%s}",
                pattern, parameterNames);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        PathMatcher that = (PathMatcher) obj;
        return Objects.equals(pattern, that.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern);
    }

    // ========== 테스트용 메서드 ==========

    /**
     * 패턴 매칭 테스트 (개발/디버깅용)
     */
    public void testPattern(String... testPaths) {
        System.out.println("Testing pattern: " + pattern);
        System.out.println("Parameters: " + parameterNames);
        System.out.println("Regex: " + regex.pattern());
        System.out.println();

        for (String testPath : testPaths) {
            MatchResult result = match(testPath);
            System.out.printf("Path: %-20s -> Matched: %-5s, Params: %s%n",
                    testPath, result.isMatched(), result.getParameters());
        }
        System.out.println();
    }
}