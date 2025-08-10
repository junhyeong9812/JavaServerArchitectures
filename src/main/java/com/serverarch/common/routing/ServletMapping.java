package src.main.java.com.serverarch.common.routing;

import java.util.*;
import java.util.regex.Pattern;

/**
 * URL 패턴과 서블릿 매핑을 처리하는 클래스입니다.
 *
 * ServletMapping은 HTTP 요청 URL을 서블릿 패턴과 매칭하여
 * 올바른 서블릿으로 라우팅하는 역할을 담당합니다.
 *
 * 지원하는 URL 패턴:
 * 1. 정확 매칭: "/hello", "/admin/users"
 * 2. 경로 매칭: "/admin/*", "/api/*"
 * 3. 확장자 매칭: "*.jsp", "*.html"
 * 4. 기본 서블릿: "/"
 *
 * 매칭 우선순위:
 * 1. 정확 매칭 (highest priority)
 * 2. 가장 긴 경로 매칭
 * 3. 확장자 매칭
 * 4. 기본 서블릿 (lowest priority)
 */
public class ServletMapping {

    /**
     * URL 패턴의 타입을 정의하는 열거형입니다.
     */
    public enum PatternType {
        /**
         * 정확 매칭: "/hello", "/admin/users"
         */
        EXACT,

        /**
         * 경로 매칭: "/admin/*", "/api/v1/*"
         */
        PATH,

        /**
         * 확장자 매칭: "*.jsp", "*.html", "*.json"
         */
        EXTENSION,

        /**
         * 기본 서블릿: "/"
         */
        DEFAULT
    }

    /**
     * 매핑 정보를 담는 클래스입니다.
     */
    public static class MappingInfo {
        private final String pattern;
        private final PatternType type;
        private final String servletName;
        private final int priority;
        private final Pattern regexPattern;

        public MappingInfo(String pattern, String servletName) {
            this.pattern = pattern;
            this.servletName = servletName;
            this.type = determinePatternType(pattern);
            this.priority = calculatePriority(pattern, type);
            this.regexPattern = createRegexPattern(pattern, type);
        }

        // Getters
        public String getPattern() { return pattern; }
        public PatternType getType() { return type; }
        public String getServletName() { return servletName; }
        public int getPriority() { return priority; }
        public Pattern getRegexPattern() { return regexPattern; }

        /**
         * 패턴 타입을 결정합니다.
         */
        private PatternType determinePatternType(String pattern) {
            if ("/".equals(pattern)) {
                return PatternType.DEFAULT;
            } else if (pattern.startsWith("*.")) {
                return PatternType.EXTENSION;
            } else if (pattern.endsWith("/*")) {
                return PatternType.PATH;
            } else {
                return PatternType.EXACT;
            }
        }

        /**
         * 패턴의 우선순위를 계산합니다.
         * 높은 숫자가 높은 우선순위를 의미합니다.
         */
        private int calculatePriority(String pattern, PatternType type) {
            switch (type) {
                case EXACT:
                    return 1000 + pattern.length(); // 정확 매칭이 최우선
                case PATH:
                    // 경로가 길수록 우선순위 높음
                    String pathPrefix = pattern.substring(0, pattern.length() - 2);
                    return 500 + pathPrefix.length();
                case EXTENSION:
                    return 100; // 확장자 매칭
                case DEFAULT:
                    return 1; // 기본 서블릿이 최하위
                default:
                    return 0;
            }
        }

        /**
         * 정규식 패턴을 생성합니다.
         */
        private Pattern createRegexPattern(String pattern, PatternType type) {
            switch (type) {
                case EXACT:
                case DEFAULT:
                    return Pattern.compile(Pattern.quote(pattern));

                case PATH:
                    String pathPrefix = pattern.substring(0, pattern.length() - 2);
                    return Pattern.compile(Pattern.quote(pathPrefix) + ".*");

                case EXTENSION:
                    String extension = pattern.substring(1); // remove *
                    return Pattern.compile(".*\\" + extension + "$");

                default:
                    return Pattern.compile(Pattern.quote(pattern));
            }
        }

        @Override
        public String toString() {
            return String.format("MappingInfo{pattern='%s', type=%s, servlet='%s', priority=%d}",
                    pattern, type, servletName, priority);
        }
    }

    /**
     * 매핑 결과를 담는 클래스입니다.
     */
    public static class MatchResult {
        private final MappingInfo mappingInfo;
        private final String matchedPath;
        private final String pathInfo;
        private final Map<String, String> pathParameters;

        public MatchResult(MappingInfo mappingInfo, String matchedPath,
                           String pathInfo, Map<String, String> pathParameters) {
            this.mappingInfo = mappingInfo;
            this.matchedPath = matchedPath;
            this.pathInfo = pathInfo;
            this.pathParameters = pathParameters != null ?
                    new HashMap<>(pathParameters) : new HashMap<>();
        }

        // Getters
        public MappingInfo getMappingInfo() { return mappingInfo; }
        public String getMatchedPath() { return matchedPath; }
        public String getPathInfo() { return pathInfo; }
        public Map<String, String> getPathParameters() { return Collections.unmodifiableMap(pathParameters); }

        public String getServletName() { return mappingInfo.getServletName(); }
        public String getPattern() { return mappingInfo.getPattern(); }
        public PatternType getPatternType() { return mappingInfo.getType(); }
    }

    /**
     * URL 패턴별 매핑 정보 저장소
     * 우선순위별로 정렬된 리스트 사용
     */
    private final List<MappingInfo> mappings;

    /**
     * 빠른 정확 매칭을 위한 캐시
     */
    private final Map<String, MappingInfo> exactMappings;

    /**
     * ServletMapping 생성자
     */
    public ServletMapping() {
        this.mappings = new ArrayList<>();
        this.exactMappings = new HashMap<>();
    }

    /**
     * URL 패턴과 서블릿을 매핑합니다.
     *
     * @param pattern URL 패턴
     * @param servletName 서블릿 이름
     * @throws IllegalArgumentException 잘못된 패턴인 경우
     */
    public synchronized void addMapping(String pattern, String servletName) {
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("URL 패턴이 null이거나 비어있습니다");
        }

        if (servletName == null || servletName.trim().isEmpty()) {
            throw new IllegalArgumentException("서블릿 이름이 null이거나 비어있습니다");
        }

        // 패턴 검증
        validatePattern(pattern);

        MappingInfo mappingInfo = new MappingInfo(pattern.trim(), servletName.trim());

        // 기존 매핑 제거 (같은 패턴이 있다면)
        removeMapping(pattern);

        // 새 매핑 추가
        mappings.add(mappingInfo);

        // 우선순위별 정렬
        mappings.sort((m1, m2) -> Integer.compare(m2.getPriority(), m1.getPriority()));

        // 정확 매칭 캐시 업데이트
        if (mappingInfo.getType() == PatternType.EXACT) {
            exactMappings.put(pattern, mappingInfo);
        }
    }

    /**
     * URL 패턴 매핑을 제거합니다.
     *
     * @param pattern 제거할 URL 패턴
     * @return 제거 성공 여부
     */
    public synchronized boolean removeMapping(String pattern) {
        if (pattern == null) {
            return false;
        }

        boolean removed = mappings.removeIf(mapping -> pattern.equals(mapping.getPattern()));

        // 정확 매칭 캐시에서도 제거
        exactMappings.remove(pattern);

        return removed;
    }

    /**
     * 요청 경로에 매칭되는 서블릿을 찾습니다.
     *
     * @param requestPath 요청 경로
     * @return 매칭 결과, 매칭되지 않으면 null
     */
    public MatchResult findMapping(String requestPath) {
        if (requestPath == null) {
            return null;
        }

        // URL 디코딩 및 정규화
        String normalizedPath = normalizePath(requestPath);

        // 1. 정확 매칭 확인 (빠른 경로)
        MappingInfo exactMatch = exactMappings.get(normalizedPath);
        if (exactMatch != null) {
            return new MatchResult(exactMatch, normalizedPath, null, null);
        }

        // 2. 우선순위별로 패턴 매칭
        for (MappingInfo mapping : mappings) {
            MatchResult result = tryMatch(mapping, normalizedPath);
            if (result != null) {
                return result;
            }
        }

        return null; // 매칭되는 패턴 없음
    }

    /**
     * 특정 패턴과 경로를 매칭해봅니다.
     */
    private MatchResult tryMatch(MappingInfo mapping, String path) {
        switch (mapping.getType()) {
            case EXACT:
                return tryExactMatch(mapping, path);
            case PATH:
                return tryPathMatch(mapping, path);
            case EXTENSION:
                return tryExtensionMatch(mapping, path);
            case DEFAULT:
                return tryDefaultMatch(mapping, path);
            default:
                return null;
        }
    }

    /**
     * 정확 매칭을 시도합니다.
     */
    private MatchResult tryExactMatch(MappingInfo mapping, String path) {
        if (mapping.getPattern().equals(path)) {
            return new MatchResult(mapping, path, null, null);
        }
        return null;
    }

    /**
     * 경로 매칭을 시도합니다.
     */
    private MatchResult tryPathMatch(MappingInfo mapping, String path) {
        String pattern = mapping.getPattern();
        String pathPrefix = pattern.substring(0, pattern.length() - 2); // remove /*

        if (path.startsWith(pathPrefix)) {
            String pathInfo = null;
            if (path.length() > pathPrefix.length()) {
                pathInfo = path.substring(pathPrefix.length());
                if (!pathInfo.startsWith("/")) {
                    pathInfo = "/" + pathInfo;
                }
            }

            return new MatchResult(mapping, pathPrefix, pathInfo, null);
        }

        return null;
    }

    /**
     * 확장자 매칭을 시도합니다.
     */
    private MatchResult tryExtensionMatch(MappingInfo mapping, String path) {
        String pattern = mapping.getPattern();
        String extension = pattern.substring(1); // remove *

        if (path.endsWith(extension)) {
            return new MatchResult(mapping, path, null, null);
        }

        return null;
    }

    /**
     * 기본 서블릿 매칭을 시도합니다.
     */
    private MatchResult tryDefaultMatch(MappingInfo mapping, String path) {
        // 기본 서블릿은 모든 경로에 매칭됨
        return new MatchResult(mapping, "/", path.substring(1), null);
    }

    /**
     * 경로를 정규화합니다.
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }

        // 시작 슬래시 추가
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        // 연속 슬래시 제거
        path = path.replaceAll("/+", "/");

        // URL 디코딩 (간단한 구현)
        path = decodeUrl(path);

        return path;
    }

    /**
     * 간단한 URL 디코딩을 수행합니다.
     */
    private String decodeUrl(String url) {
        try {
            return java.net.URLDecoder.decode(url, "UTF-8");
        } catch (Exception e) {
            return url; // 디코딩 실패 시 원본 반환
        }
    }

    /**
     * URL 패턴의 유효성을 검증합니다.
     */
    private void validatePattern(String pattern) {
        // 빈 패턴 검사
        if (pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("빈 URL 패턴은 허용되지 않습니다");
        }

        // 잘못된 와일드카드 사용 검사
        if (pattern.contains("*") && !pattern.equals("/") &&
                !pattern.startsWith("*.") && !pattern.endsWith("/*")) {
            throw new IllegalArgumentException("잘못된 와일드카드 사용: " + pattern);
        }

        // 여러 와일드카드 검사
        int asteriskCount = pattern.length() - pattern.replace("*", "").length();
        if (asteriskCount > 1) {
            throw new IllegalArgumentException("여러 개의 와일드카드는 허용되지 않습니다: " + pattern);
        }

        // 경로 매칭 패턴 검증
        if (pattern.endsWith("/*") && pattern.length() > 2) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            if (!prefix.startsWith("/")) {
                throw new IllegalArgumentException("경로 매칭 패턴은 '/'로 시작해야 합니다: " + pattern);
            }
        }
    }

    /**
     * 등록된 모든 매핑을 반환합니다.
     */
    public synchronized List<MappingInfo> getAllMappings() {
        return new ArrayList<>(mappings);
    }

    /**
     * 매핑 정보를 반환합니다.
     */
    public synchronized String getMappingInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Servlet Mappings (").append(mappings.size()).append(" total):\n");

        for (MappingInfo mapping : mappings) {
            info.append(String.format("  Pattern: %-20s -> Servlet: %-15s (Type: %s, Priority: %d)\n",
                    mapping.getPattern(), mapping.getServletName(),
                    mapping.getType(), mapping.getPriority()));
        }

        return info.toString();
    }

    /**
     * 매핑 통계를 반환합니다.
     */
    public synchronized String getStatistics() {
        Map<PatternType, Integer> typeCount = new HashMap<>();
        for (MappingInfo mapping : mappings) {
            typeCount.merge(mapping.getType(), 1, Integer::sum);
        }

        return String.format(
                "Mapping Statistics:\n" +
                        "  Total Mappings: %d\n" +
                        "  Exact Mappings: %d\n" +
                        "  Path Mappings: %d\n" +
                        "  Extension Mappings: %d\n" +
                        "  Default Mappings: %d",
                mappings.size(),
                typeCount.getOrDefault(PatternType.EXACT, 0),
                typeCount.getOrDefault(PatternType.PATH, 0),
                typeCount.getOrDefault(PatternType.EXTENSION, 0),
                typeCount.getOrDefault(PatternType.DEFAULT, 0)
        );
    }
}
