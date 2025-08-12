package com.serverarch.common.routing;

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
 *
 * 클래스를 public으로 선언한 이유:
 * - 웹 서버의 핵심 라우팅 기능
 * - 다른 패키지에서 접근 필요
 * - 서블릿 컨테이너의 표준 컴포넌트
 */
public class ServletMapping {

    /**
     * URL 패턴의 타입을 정의하는 열거형입니다.
     *
     * enum을 사용한 이유:
     * - 타입 안전성 보장
     * - switch문에서 효율적 처리
     * - 코드 가독성 향상
     * - 새로운 패턴 타입 추가 용이
     */
    public enum PatternType {
        /**
         * 정확 매칭: "/hello", "/admin/users"
         *
         * 특징:
         * - URL 경로가 완전히 일치해야 함
         * - 가장 높은 우선순위
         * - 성능이 가장 빠름 (해시맵 조회)
         */
        EXACT,

        /**
         * 경로 매칭: "/admin/*", "/api/v1/*"
         *
         * 특징:
         * - 지정된 경로 아래의 모든 하위 경로 매칭
         * - 와일드카드(*)는 마지막에만 사용
         * - 경로가 길수록 높은 우선순위
         */
        PATH,

        /**
         * 확장자 매칭: "*.jsp", "*.html", "*.json"
         *
         * 특징:
         * - 파일 확장자 기반 매칭
         * - 정적 리소스 처리에 주로 사용
         * - 와일드카드(*)는 시작에만 사용
         */
        EXTENSION,

        /**
         * 기본 서블릿: "/"
         *
         * 특징:
         * - 다른 패턴에 매칭되지 않는 모든 요청 처리
         * - 가장 낮은 우선순위
         * - 정적 파일 서비스 등에 사용
         */
        DEFAULT
    }

    /**
     * 매핑 정보를 담는 클래스입니다.
     *
     * static 내부 클래스로 구현한 이유:
     * - ServletMapping과 밀접한 관련
     * - 외부 클래스 참조 불필요 (메모리 효율성)
     * - 네임스페이스 정리
     */
    public static class MappingInfo {
        // ========== 필드들 ==========

        /**
         * 원본 URL 패턴
         * 예: "/admin/*", "*.jsp", "/hello"
         */
        private final String pattern;

        /**
         * 패턴의 타입 (EXACT, PATH, EXTENSION, DEFAULT)
         */
        private final PatternType type;

        /**
         * 매핑될 서블릿 이름
         */
        private final String servletName;

        /**
         * 매칭 우선순위 (높은 숫자 = 높은 우선순위)
         */
        private final int priority;

        /**
         * 패턴 매칭을 위한 정규식
         * Pattern.compile()로 생성된 컴파일된 정규식
         */
        private final Pattern regexPattern;

        /**
         * MappingInfo 생성자
         *
         * @param pattern URL 패턴
         * @param servletName 서블릿 이름
         */
        public MappingInfo(String pattern, String servletName) {
            this.pattern = pattern;
            this.servletName = servletName;

            // 패턴 분석하여 타입 결정
            this.type = determinePatternType(pattern);

            // 타입과 패턴에 따른 우선순위 계산
            this.priority = calculatePriority(pattern, type);

            // 정규식 패턴 생성 (성능 최적화를 위해 미리 컴파일)
            this.regexPattern = createRegexPattern(pattern, type);
        }

        // ========== Getter 메서드들 ==========

        /**
         * 원본 패턴 반환
         * @return URL 패턴 문자열
         */
        public String getPattern() { return pattern; }

        /**
         * 패턴 타입 반환
         * @return PatternType enum 값
         */
        public PatternType getType() { return type; }

        /**
         * 서블릿 이름 반환
         * @return 서블릿 이름
         */
        public String getServletName() { return servletName; }

        /**
         * 우선순위 반환
         * @return 우선순위 숫자 (높을수록 우선)
         */
        public int getPriority() { return priority; }

        /**
         * 정규식 패턴 반환
         * @return 컴파일된 Pattern 객체
         */
        public Pattern getRegexPattern() { return regexPattern; }

        /**
         * 패턴 타입을 결정합니다.
         *
         * private 메서드로 구현한 이유:
         * - 내부적으로만 사용되는 로직
         * - 생성자에서만 호출
         * - 캡슐화 원칙 준수
         *
         * @param pattern 분석할 URL 패턴
         * @return 결정된 패턴 타입
         */
        private PatternType determinePatternType(String pattern) {
            // "/".equals(pattern): 기본 서블릿 패턴 확인
            // String.equals() 사용으로 null 안전성 보장
            if ("/".equals(pattern)) {
                return PatternType.DEFAULT;
            }
            // startsWith("*."): 확장자 패턴 확인 (예: "*.jsp")
            else if (pattern.startsWith("*.")) {
                return PatternType.EXTENSION;
            }
            // endsWith("/*"): 경로 패턴 확인 (예: "/admin/*")
            else if (pattern.endsWith("/*")) {
                return PatternType.PATH;
            }
            // 위 조건에 해당하지 않으면 정확 매칭
            else {
                return PatternType.EXACT;
            }
        }

        /**
         * 패턴의 우선순위를 계산합니다.
         * 높은 숫자가 높은 우선순위를 의미합니다.
         *
         * 우선순위 체계:
         * - EXACT: 1000 + 패턴 길이 (가장 높음)
         * - PATH: 500 + 경로 길이
         * - EXTENSION: 100 (고정)
         * - DEFAULT: 1 (가장 낮음)
         *
         * @param pattern URL 패턴
         * @param type 패턴 타입
         * @return 계산된 우선순위
         */
        private int calculatePriority(String pattern, PatternType type) {
            // switch 문으로 타입별 우선순위 계산
            switch (type) {
                case EXACT:
                    // 정확 매칭이 최우선, 패턴이 길수록 더 구체적이므로 높은 우선순위
                    // 1000: 기본 우선순위, pattern.length(): 길이 보너스
                    return 1000 + pattern.length();

                case PATH:
                    // 경로가 길수록 우선순위 높음 (더 구체적인 경로)
                    // substring(0, pattern.length() - 2): "/*" 제거
                    String pathPrefix = pattern.substring(0, pattern.length() - 2);
                    return 500 + pathPrefix.length();

                case EXTENSION:
                    // 확장자 매칭은 고정 우선순위
                    return 100;

                case DEFAULT:
                    // 기본 서블릿이 최하위 우선순위
                    return 1;

                default:
                    // 예상치 못한 타입의 경우 최하위
                    return 0;
            }
        }

        /**
         * 정규식 패턴을 생성합니다.
         *
         * 정규식을 사용하는 이유:
         * - 복잡한 패턴 매칭을 효율적으로 처리
         * - 한 번 컴파일하면 재사용 가능
         * - Java의 강력한 정규식 엔진 활용
         *
         * @param pattern URL 패턴
         * @param type 패턴 타입
         * @return 컴파일된 정규식 Pattern
         */
        private Pattern createRegexPattern(String pattern, PatternType type) {
            switch (type) {
                case EXACT:
                case DEFAULT:
                    // Pattern.quote(): 특수 문자를 이스케이프하여 리터럴 문자열로 처리
                    // 정확 매칭과 기본 서블릿은 문자 그대로 매칭
                    return Pattern.compile(Pattern.quote(pattern));

                case PATH:
                    // 경로 매칭: "/admin/*" -> "/admin" + ".*"
                    // substring(0, pattern.length() - 2): "/*" 제거
                    String pathPrefix = pattern.substring(0, pattern.length() - 2);
                    // Pattern.quote(pathPrefix): 경로 부분을 리터럴로 처리
                    // ".*": 임의의 문자열 매칭 (0개 이상의 모든 문자)
                    return Pattern.compile(Pattern.quote(pathPrefix) + ".*");

                case EXTENSION:
                    // 확장자 매칭: "*.jsp" -> ".*\.jsp$"
                    // substring(1): "*" 제거하여 확장자만 추출
                    String extension = pattern.substring(1);
                    // ".*": 파일명 부분 (임의의 문자)
                    // "\\": 정규식에서 점(.)을 리터럴로 처리하기 위한 이스케이프
                    // "$": 문자열 끝 앵커 (확장자로 끝나야 함)
                    return Pattern.compile(".*\\" + extension + "$");

                default:
                    // 기본적으로 리터럴 매칭
                    return Pattern.compile(Pattern.quote(pattern));
            }
        }

        /**
         * 매핑 정보를 문자열로 표현
         *
         * toString() 오버라이드 이유:
         * - 디버깅 시 의미 있는 정보 제공
         * - 로깅에서 읽기 쉬운 형태로 출력
         *
         * @return 포맷된 매핑 정보 문자열
         */
        @Override
        public String toString() {
            // String.format(): 형식화된 문자열 생성
            // %s: 문자열 포맷, %d: 정수 포맷
            return String.format("MappingInfo{pattern='%s', type=%s, servlet='%s', priority=%d}",
                    pattern, type, servletName, priority);
        }
    }

    /**
     * 매칭 결과를 담는 클래스입니다.
     *
     * 별도 클래스로 분리한 이유:
     * - 매칭 결과의 복잡한 정보를 구조화
     * - 향후 확장 가능성 (경로 파라미터 등)
     * - 타입 안전성 제공
     */
    public static class MatchResult {
        /**
         * 매칭된 매핑 정보
         */
        private final MappingInfo mappingInfo;

        /**
         * 실제 매칭된 경로 부분
         * 예: "/admin/users"에서 "/admin/*" 매칭 시 "/admin"
         */
        private final String matchedPath;

        /**
         * 추가 경로 정보 (PathInfo)
         * 예: "/admin/users"에서 "/admin/*" 매칭 시 "/users"
         */
        private final String pathInfo;

        /**
         * 경로 파라미터 (향후 확장용)
         * 예: "/user/{id}" 패턴에서 {id} 값 저장
         */
        private final Map<String, String> pathParameters;

        /**
         * MatchResult 생성자
         *
         * @param mappingInfo 매칭된 매핑 정보
         * @param matchedPath 매칭된 경로
         * @param pathInfo 추가 경로 정보
         * @param pathParameters 경로 파라미터
         */
        public MatchResult(MappingInfo mappingInfo, String matchedPath,
                           String pathInfo, Map<String, String> pathParameters) {
            this.mappingInfo = mappingInfo;
            this.matchedPath = matchedPath;
            this.pathInfo = pathInfo;

            // 방어적 복사: 외부에서 전달받은 Map을 복사하여 저장
            // pathParameters != null: null 체크
            // new HashMap<>(pathParameters): 새로운 HashMap으로 복사
            // new HashMap<>(): pathParameters가 null이면 빈 맵 생성
            this.pathParameters = pathParameters != null ?
                    new HashMap<>(pathParameters) : new HashMap<>();
        }

        // ========== Getter 메서드들 ==========

        /**
         * 매핑 정보 반환
         * @return MappingInfo 객체
         */
        public MappingInfo getMappingInfo() { return mappingInfo; }

        /**
         * 매칭된 경로 반환
         * @return 매칭된 경로 문자열
         */
        public String getMatchedPath() { return matchedPath; }

        /**
         * 경로 정보 반환
         * @return PathInfo 문자열
         */
        public String getPathInfo() { return pathInfo; }

        /**
         * 경로 파라미터 반환 (읽기 전용)
         *
         * Collections.unmodifiableMap() 사용 이유:
         * - 외부에서 수정하지 못하도록 보호
         * - 내부 상태의 무결성 보장
         * - 방어적 프로그래밍 패턴
         *
         * @return 읽기 전용 경로 파라미터 맵
         */
        public Map<String, String> getPathParameters() {
            return Collections.unmodifiableMap(pathParameters);
        }

        // ========== 편의 메서드들 ==========

        /**
         * 서블릿 이름 반환 (편의 메서드)
         * @return 매핑된 서블릿 이름
         */
        public String getServletName() { return mappingInfo.getServletName(); }

        /**
         * 패턴 반환 (편의 메서드)
         * @return URL 패턴
         */
        public String getPattern() { return mappingInfo.getPattern(); }

        /**
         * 패턴 타입 반환 (편의 메서드)
         * @return 패턴 타입
         */
        public PatternType getPatternType() { return mappingInfo.getType(); }
    }

    // ========== ServletMapping 인스턴스 필드들 ==========

    /**
     * URL 패턴별 매핑 정보 저장소
     * 우선순위별로 정렬된 리스트 사용
     *
     * List를 사용하는 이유:
     * - 우선순위 순서 유지 필요
     * - 순차 검색으로 첫 번째 매칭 찾기
     * - 동적 추가/삭제 지원
     */
    private final List<MappingInfo> mappings;

    /**
     * 빠른 정확 매칭을 위한 캐시
     *
     * Map을 사용하는 이유:
     * - 정확 매칭은 O(1) 성능으로 처리 가능
     * - 가장 자주 사용되는 패턴 타입
     * - 성능 최적화 효과가 큼
     */
    private final Map<String, MappingInfo> exactMappings;

    /**
     * ServletMapping 생성자
     *
     * 기본 생성자로 구현한 이유:
     * - 설정 없이 바로 사용 가능
     * - 필요에 따라 매핑 추가
     * - 간단한 초기화
     */
    public ServletMapping() {
        // ArrayList: 동적 크기 조정, 빠른 순차 접근
        this.mappings = new ArrayList<>();

        // HashMap: 빠른 키-값 검색 (O(1))
        this.exactMappings = new HashMap<>();
    }

    /**
     * URL 패턴과 서블릿을 매핑합니다.
     *
     * synchronized로 동기화한 이유:
     * - 멀티스레드 환경에서 안전한 매핑 추가
     * - 리스트와 맵의 일관성 보장
     * - 정렬 작업 중 다른 스레드의 접근 방지
     *
     * @param pattern URL 패턴
     * @param servletName 서블릿 이름
     * @throws IllegalArgumentException 잘못된 패턴인 경우
     */
    public synchronized void addMapping(String pattern, String servletName) {
        // ========== 입력 검증 ==========

        // null이나 빈 패턴 체크
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("URL 패턴이 null이거나 비어있습니다");
        }

        // null이나 빈 서블릿 이름 체크
        if (servletName == null || servletName.trim().isEmpty()) {
            throw new IllegalArgumentException("서블릿 이름이 null이거나 비어있습니다");
        }

        // 패턴 유효성 검증 (세부 규칙 확인)
        validatePattern(pattern);

        // ========== 매핑 정보 생성 ==========

        // trim(): 앞뒤 공백 제거하여 정규화
        MappingInfo mappingInfo = new MappingInfo(pattern.trim(), servletName.trim());

        // ========== 기존 매핑 처리 ==========

        // 기존 매핑 제거 (같은 패턴이 있다면 덮어쓰기)
        removeMapping(pattern);

        // ========== 새 매핑 추가 ==========

        // 리스트에 추가
        mappings.add(mappingInfo);

        // 우선순위별 정렬 (높은 우선순위가 앞으로)
        // (m1, m2) -> Integer.compare(m2.getPriority(), m1.getPriority())
        // 람다 표현식으로 내림차순 정렬 (m2 vs m1 순서)
        mappings.sort((m1, m2) -> Integer.compare(m2.getPriority(), m1.getPriority()));

        // ========== 캐시 업데이트 ==========

        // 정확 매칭 캐시 업데이트
        if (mappingInfo.getType() == PatternType.EXACT) {
            // put(): 맵에 키-값 쌍 추가
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

        // removeIf(): 조건에 맞는 요소들을 제거하는 메서드
        // mapping -> pattern.equals(mapping.getPattern()): 람다 표현식
        // 패턴이 일치하는 매핑을 찾아서 제거
        boolean removed = mappings.removeIf(mapping -> pattern.equals(mapping.getPattern()));

        // 정확 매칭 캐시에서도 제거
        // remove(): 맵에서 키에 해당하는 엔트리 제거
        exactMappings.remove(pattern);

        return removed;
    }

    /**
     * 요청 경로에 매칭되는 서블릿을 찾습니다.
     *
     * 매칭 알고리즘:
     * 1. 경로 정규화
     * 2. 정확 매칭 확인 (캐시 활용)
     * 3. 우선순위 순서로 패턴 매칭
     *
     * @param requestPath 요청 경로
     * @return 매칭 결과, 매칭되지 않으면 null
     */
    public MatchResult findMapping(String requestPath) {
        if (requestPath == null) {
            return null;
        }

        // ========== 1. URL 디코딩 및 정규화 ==========
        String normalizedPath = normalizePath(requestPath);

        // ========== 2. 정확 매칭 확인 (빠른 경로) ==========
        // get(): 맵에서 키에 해당하는 값 조회
        MappingInfo exactMatch = exactMappings.get(normalizedPath);
        if (exactMatch != null) {
            // 정확 매칭이 있으면 즉시 반환 (최고 우선순위)
            return new MatchResult(exactMatch, normalizedPath, null, null);
        }

        // ========== 3. 우선순위별로 패턴 매칭 ==========
        // for-each 루프로 우선순위 순서대로 확인
        for (MappingInfo mapping : mappings) {
            // 각 매핑에 대해 매칭 시도
            MatchResult result = tryMatch(mapping, normalizedPath);
            if (result != null) {
                return result; // 첫 번째 매칭된 결과 반환
            }
        }

        return null; // 매칭되는 패턴 없음
    }

    /**
     * 특정 패턴과 경로를 매칭해봅니다.
     *
     * private 메서드로 구현한 이유:
     * - 내부적으로만 사용되는 매칭 로직
     * - 타입별로 다른 매칭 방식 처리
     * - 코드 모듈화
     *
     * @param mapping 매핑 정보
     * @param path 정규화된 요청 경로
     * @return 매칭 결과 또는 null
     */
    private MatchResult tryMatch(MappingInfo mapping, String path) {
        // 패턴 타입에 따라 다른 매칭 메서드 호출
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
     *
     * @param mapping 매핑 정보
     * @param path 요청 경로
     * @return 매칭 결과 또는 null
     */
    private MatchResult tryExactMatch(MappingInfo mapping, String path) {
        // equals(): 문자열 정확 비교
        if (mapping.getPattern().equals(path)) {
            // pathInfo는 null (정확 매칭은 추가 경로 없음)
            return new MatchResult(mapping, path, null, null);
        }
        return null;
    }

    /**
     * 경로 매칭을 시도합니다.
     *
     * 경로 매칭 로직:
     * 1. 패턴에서 "/*" 제거하여 접두사 추출
     * 2. 요청 경로가 접두사로 시작하는지 확인
     * 3. 남은 부분을 pathInfo로 설정
     *
     * @param mapping 매핑 정보
     * @param path 요청 경로
     * @return 매칭 결과 또는 null
     */
    private MatchResult tryPathMatch(MappingInfo mapping, String path) {
        String pattern = mapping.getPattern();

        // "/*" 제거하여 경로 접두사 추출
        // substring(0, pattern.length() - 2): 마지막 2글자("/*") 제거
        String pathPrefix = pattern.substring(0, pattern.length() - 2);

        // startsWith(): 문자열이 특정 접두사로 시작하는지 확인
        if (path.startsWith(pathPrefix)) {
            String pathInfo = null;

            // 경로가 접두사보다 긴 경우 pathInfo 설정
            if (path.length() > pathPrefix.length()) {
                // 접두사 이후 부분을 pathInfo로 설정
                pathInfo = path.substring(pathPrefix.length());

                // pathInfo가 "/"로 시작하지 않으면 추가
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
     *
     * 확장자 매칭 로직:
     * 1. 패턴에서 "*" 제거하여 확장자 추출
     * 2. 요청 경로가 해당 확장자로 끝나는지 확인
     *
     * @param mapping 매핑 정보
     * @param path 요청 경로
     * @return 매칭 결과 또는 null
     */
    private MatchResult tryExtensionMatch(MappingInfo mapping, String path) {
        String pattern = mapping.getPattern();

        // "*" 제거하여 확장자 추출
        // substring(1): 첫 번째 글자("*") 제거
        String extension = pattern.substring(1);

        // endsWith(): 문자열이 특정 접미사로 끝나는지 확인
        if (path.endsWith(extension)) {
            // pathInfo는 null (확장자 매칭은 추가 경로 없음)
            return new MatchResult(mapping, path, null, null);
        }

        return null;
    }

    /**
     * 기본 서블릿 매칭을 시도합니다.
     *
     * 기본 서블릿 특징:
     * - 모든 경로에 매칭됨 (마지막 보루)
     * - 전체 경로를 pathInfo로 설정
     * - 정적 파일 서비스 등에 사용
     *
     * @param mapping 매핑 정보
     * @param path 요청 경로
     * @return 매칭 결과
     */
    private MatchResult tryDefaultMatch(MappingInfo mapping, String path) {
        // 기본 서블릿은 모든 경로에 매칭됨
        // matchedPath는 "/", pathInfo는 전체 경로 (첫 번째 "/" 제거)
        // substring(1): 첫 번째 "/"를 제거하여 pathInfo 생성
        return new MatchResult(mapping, "/", path.substring(1), null);
    }

    /**
     * 경로를 정규화합니다.
     *
     * 정규화 과정:
     * 1. null/빈 문자열 처리
     * 2. 시작 슬래시 추가
     * 3. 연속 슬래시 제거
     * 4. URL 디코딩
     *
     * @param path 원본 경로
     * @return 정규화된 경로
     */
    private String normalizePath(String path) {
        // null이나 빈 문자열은 루트 경로로 처리
        if (path == null || path.isEmpty()) {
            return "/";
        }

        // 시작 슬래시 추가 (없는 경우)
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        // 연속 슬래시 제거
        // replaceAll("/+", "/"): 정규식으로 연속된 슬래시를 하나로 치환
        // "/+": 하나 이상의 슬래시 매칭
        path = path.replaceAll("/+", "/");

        // URL 디코딩 (간단한 구현)
        path = decodeUrl(path);

        return path;
    }

    /**
     * 간단한 URL 디코딩을 수행합니다.
     *
     * @param url 디코딩할 URL
     * @return 디코딩된 URL
     */
    private String decodeUrl(String url) {
        try {
            // URLDecoder.decode(): URL 인코딩된 문자열을 디코딩
            // "UTF-8": 디코딩에 사용할 문자 인코딩
            return java.net.URLDecoder.decode(url, "UTF-8");
        } catch (Exception e) {
            // 디코딩 실패 시 원본 반환 (안전한 폴백)
            return url;
        }
    }

    /**
     * URL 패턴의 유효성을 검증합니다.
     *
     * 검증 규칙:
     * 1. 빈 패턴 금지
     * 2. 잘못된 와일드카드 사용 금지
     * 3. 여러 와일드카드 금지
     * 4. 경로 패턴 형식 검증
     *
     * @param pattern 검증할 패턴
     * @throws IllegalArgumentException 유효하지 않은 패턴
     */
    private void validatePattern(String pattern) {
        // ========== 1. 빈 패턴 검사 ==========
        if (pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("빈 URL 패턴은 허용되지 않습니다");
        }

        // ========== 2. 잘못된 와일드카드 사용 검사 ==========
        // contains("*"): 별표 포함 여부 확인
        // &&: 논리적 AND 연산
        // !pattern.equals("/"): 기본 서블릿이 아님
        // !pattern.startsWith("*."): 확장자 패턴이 아님
        // !pattern.endsWith("/*"): 경로 패턴이 아님
        if (pattern.contains("*") && !pattern.equals("/") &&
                !pattern.startsWith("*.") && !pattern.endsWith("/*")) {
            throw new IllegalArgumentException("잘못된 와일드카드 사용: " + pattern);
        }

        // ========== 3. 여러 와일드카드 검사 ==========
        // 문자열에서 "*"를 모두 제거한 후 길이 차이로 개수 계산
        // pattern.replace("*", ""): 모든 "*" 제거
        int asteriskCount = pattern.length() - pattern.replace("*", "").length();
        if (asteriskCount > 1) {
            throw new IllegalArgumentException("여러 개의 와일드카드는 허용되지 않습니다: " + pattern);
        }

        // ========== 4. 경로 매칭 패턴 검증 ==========
        // endsWith("/*"): 경로 패턴인지 확인
        // pattern.length() > 2: "/*"보다 긴지 확인 (접두사가 있는지)
        if (pattern.endsWith("/*") && pattern.length() > 2) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            // 경로 패턴의 접두사는 "/"로 시작해야 함
            if (!prefix.startsWith("/")) {
                throw new IllegalArgumentException("경로 매칭 패턴은 '/'로 시작해야 합니다: " + pattern);
            }
        }
    }

    /**
     * 등록된 모든 매핑을 반환합니다.
     *
     * synchronized로 동기화한 이유:
     * - 읽기 작업도 일관된 스냅샷 보장
     * - 정렬 중인 리스트 접근 방지
     *
     * @return 모든 매핑 정보의 복사본
     */
    public synchronized List<MappingInfo> getAllMappings() {
        // new ArrayList<>(mappings): 방어적 복사
        // 외부에서 수정해도 원본에 영향 없음
        return new ArrayList<>(mappings);
    }

    /**
     * 매핑 정보를 반환합니다.
     *
     * 디버깅과 모니터링 목적의 정보 제공
     *
     * @return 포맷된 매핑 정보 문자열
     */
    public synchronized String getMappingInfo() {
        // StringBuilder: 효율적인 문자열 연결
        StringBuilder info = new StringBuilder();

        // append(): 문자열 추가
        // mappings.size(): 리스트 크기
        info.append("Servlet Mappings (").append(mappings.size()).append(" total):\n");

        // for-each 루프로 모든 매핑 정보 출력
        for (MappingInfo mapping : mappings) {
            // String.format(): 형식화된 문자열 생성
            // %-20s: 왼쪽 정렬, 20자리 문자열
            // %-15s: 왼쪽 정렬, 15자리 문자열
            info.append(String.format("  Pattern: %-20s -> Servlet: %-15s (Type: %s, Priority: %d)\n",
                    mapping.getPattern(), mapping.getServletName(),
                    mapping.getType(), mapping.getPriority()));
        }

        // toString(): StringBuilder를 String으로 변환
        return info.toString();
    }

    /**
     * 매핑 통계를 반환합니다.
     *
     * 통계 정보:
     * - 전체 매핑 수
     * - 타입별 매핑 수
     *
     * @return 포맷된 통계 정보 문자열
     */
    public synchronized String getStatistics() {
        // 타입별 개수를 세기 위한 맵
        Map<PatternType, Integer> typeCount = new HashMap<>();

        // 모든 매핑을 순회하며 타입별 개수 계산
        for (MappingInfo mapping : mappings) {
            // merge(): 키가 있으면 값을 조합, 없으면 새로 추가
            // mapping.getType(): 현재 매핑의 타입
            // 1: 추가할 값
            // Integer::sum: 기존 값과 새 값을 더하는 메서드 참조
            typeCount.merge(mapping.getType(), 1, Integer::sum);
        }

        // 통계 정보 포맷팅
        return String.format(
                "Mapping Statistics:\n" +
                        "  Total Mappings: %d\n" +
                        "  Exact Mappings: %d\n" +
                        "  Path Mappings: %d\n" +
                        "  Extension Mappings: %d\n" +
                        "  Default Mappings: %d",
                mappings.size(),
                // getOrDefault(): 키가 있으면 값 반환, 없으면 기본값 반환
                typeCount.getOrDefault(PatternType.EXACT, 0),
                typeCount.getOrDefault(PatternType.PATH, 0),
                typeCount.getOrDefault(PatternType.EXTENSION, 0),
                typeCount.getOrDefault(PatternType.DEFAULT, 0)
        );
    }
}