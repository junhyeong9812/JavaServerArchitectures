package com.serverarch.traditional.routing; // 패키지 선언 - 라우팅 시스템 패키지

// import 선언부
import com.serverarch.traditional.*; // HttpRequest, HttpResponse 클래스들 import
import java.util.*; // Collections, Map, List 등 유틸리티 클래스들
import java.util.regex.*; // Pattern, Matcher 정규식 클래스들
import java.util.function.*; // Predicate 등 함수형 인터페이스들
import java.util.concurrent.*; // CopyOnWriteArrayList 동시성 컬렉션

/**
 * 개별 라우트를 나타내는 클래스
 *
 * 기능:
 * 1. URL 패턴 매칭 (정적, 동적, 와일드카드)
 * 2. 경로 파라미터 추출 (/users/{id} -> {id: "123"})
 * 3. 조건부 매칭 (헤더, 쿼리 파라미터 기반)
 * 4. 라우트별 미들웨어 지원
 * 5. 우선순위 계산 (구체적인 패턴일수록 높은 우선순위)
 */
public class Route { // public 클래스 선언

    // 기본 라우트 정보
    private final String method; // HTTP 메서드 (GET, POST 등)
    private final String pattern; // URL 패턴 ("/users/{id}")
    private final RouteHandler handler; // 요청 처리 핸들러
    private final Pattern compiledPattern; // 컴파일된 정규식 패턴
    private final List<String> parameterNames; // 경로 파라미터 이름들 ([id, name])
    private final int priority; // 라우트 우선순위 (높을수록 먼저 매칭)

    // 고급 기능들
    private final List<Middleware> middlewares; // 라우트별 미들웨어
    private final List<Predicate<HttpRequest>> conditions; // 조건부 매칭 조건들
    private final Map<String, Object> metadata; // 라우트 메타데이터

    /**
     * 기본 생성자
     * @param method HTTP 메서드
     * @param pattern URL 패턴
     * @param handler 요청 처리 핸들러
     */
    public Route(String method, String pattern, RouteHandler handler) {
        // 필수 매개변수 검증
        if (method == null || pattern == null || handler == null) {
            throw new IllegalArgumentException("메서드, 패턴, 핸들러는 모두 필수입니다");
        }

        this.method = method.toUpperCase(); // HTTP 메서드를 대문자로 정규화
        this.pattern = normalizePattern(pattern); // 패턴 정규화 (/users/ -> /users)
        this.handler = handler;

        // 경로 파라미터 추출 및 정규식 컴파일
        this.parameterNames = extractParameterNames(this.pattern); // {id}, {name} 등 추출
        this.compiledPattern = compilePattern(this.pattern); // 정규식으로 변환 및 컴파일
        this.priority = calculatePriority(this.pattern); // 우선순위 계산

        // 컬렉션 초기화 - 동시성 안전한 컬렉션 사용
        this.middlewares = new CopyOnWriteArrayList<>(); // 읽기가 많은 리스트에 최적화
        this.conditions = new CopyOnWriteArrayList<>(); // 조건 목록
        this.metadata = new ConcurrentHashMap<>(); // 메타데이터 맵
    }

    /**
     * 요청 경로가 이 라우트와 매칭되는지 확인
     * @param path 요청 경로
     * @param request HTTP 요청 (조건부 매칭용)
     * @return 매칭 결과 (null이면 매칭 실패)
     */
    public RouteMatchResult match(String path, HttpRequest request) {
        // 1. 기본 패턴 매칭 확인
        Matcher matcher = compiledPattern.matcher(path); // 정규식 매칭 시도
        if (!matcher.matches()) {
            return null; // 패턴이 매칭되지 않으면 실패
        }

        // 2. 조건부 매칭 확인 (모든 조건이 참이어야 함)
        // Stream API를 사용한 함수형 스타일 조건 검사
        boolean allConditionsMet = conditions.stream() // Stream 생성
                .allMatch(condition -> condition.test(request)); // allMatch() - 모든 조건이 true인지 확인, 람다로 각 조건 테스트

        if (!allConditionsMet) {
            return null; // 조건을 만족하지 않으면 실패
        }

        // 3. 경로 파라미터 추출
        Map<String, String> pathParams = extractPathParameters(matcher); // 매칭된 그룹에서 파라미터 추출

        // 4. 매칭 결과 생성
        return new RouteMatchResult(this, pathParams); // 성공적인 매칭 결과 반환
    }

    /**
     * 라우트별 미들웨어 추가
     * @param middleware 추가할 미들웨어
     * @return 현재 Route 객체 (메서드 체이닝용)
     */
    public Route middleware(Middleware middleware) {
        if (middleware != null) {
            this.middlewares.add(middleware); // 미들웨어 리스트에 추가
        }
        return this; // 메서드 체이닝을 위해 자기 자신 반환
    }

    /**
     * 여러 미들웨어를 한 번에 추가
     * @param middlewares 추가할 미들웨어들
     * @return 현재 Route 객체
     */
    public Route middlewares(Middleware... middlewares) { // 가변 인수로 여러 미들웨어 받기
        if (middlewares != null) {
            // Arrays.stream()을 사용한 함수형 스타일 처리
            Arrays.stream(middlewares) // 배열을 스트림으로 변환
                    .filter(Objects::nonNull) // null이 아닌 요소만 필터링, 메서드 참조 사용
                    .forEach(this.middlewares::add); // 각 미들웨어를 리스트에 추가, 메서드 참조 사용
        }
        return this; // 메서드 체이닝
    }

    /**
     * 조건부 매칭 조건 추가
     * @param condition 매칭 조건 (헤더, 쿼리 파라미터 등)
     * @return 현재 Route 객체
     */
    public Route when(Predicate<HttpRequest> condition) {
        if (condition != null) {
            this.conditions.add(condition); // 조건 리스트에 추가
        }
        return this; // 메서드 체이닝
    }

    /**
     * 특정 헤더 값이 있을 때만 매칭되도록 조건 추가
     * @param headerName 헤더 이름
     * @param expectedValue 기대하는 헤더 값
     * @return 현재 Route 객체
     */
    public Route whenHeader(String headerName, String expectedValue) {
        // 람다 표현식으로 헤더 조건 생성
        return when(request -> expectedValue.equals(request.getHeader(headerName))); // when() 메서드 호출로 조건 등록
    }

    /**
     * 특정 쿼리 파라미터가 있을 때만 매칭되도록 조건 추가
     * @param paramName 파라미터 이름
     * @param expectedValue 기대하는 파라미터 값
     * @return 현재 Route 객체
     */
    public Route whenParam(String paramName, String expectedValue) {
        // 쿼리 파라미터 조건 생성
        return when(request -> expectedValue.equals(request.getParameter(paramName))); // getParameter()로 쿼리 파라미터 확인
    }

    /**
     * AJAX 요청일 때만 매칭되도록 조건 추가
     * @return 현재 Route 객체
     */
    public Route whenAjax() {
        return when(HttpRequest::isAjax); // 메서드 참조로 AJAX 조건 등록
    }

    /**
     * JSON 요청일 때만 매칭되도록 조건 추가
     * @return 현재 Route 객체
     */
    public Route whenJson() {
        return when(HttpRequest::isJson); // 메서드 참조로 JSON 조건 등록
    }

    /**
     * 라우트 메타데이터 설정
     * @param key 메타데이터 키
     * @param value 메타데이터 값
     * @return 현재 Route 객체
     */
    public Route meta(String key, Object value) {
        if (key != null) {
            this.metadata.put(key, value); // 메타데이터 맵에 저장
        }
        return this; // 메서드 체이닝
    }

    /**
     * 라우트 이름 설정 (편의 메서드)
     * @param name 라우트 이름
     * @return 현재 Route 객체
     */
    public Route name(String name) {
        return meta("name", name); // "name" 키로 메타데이터 저장
    }

    /**
     * 라우트 설명 설정 (편의 메서드)
     * @param description 라우트 설명
     * @return 현재 Route 객체
     */
    public Route description(String description) {
        return meta("description", description); // "description" 키로 메타데이터 저장
    }

    // ========== Private 유틸리티 메서드들 ==========

    /**
     * URL 패턴 정규화
     * /users/ -> /users, // -> / 등의 정리 작업
     * @param pattern 원본 패턴
     * @return 정규화된 패턴
     */
    private String normalizePattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return "/"; // 빈 패턴은 루트로 처리
        }

        // 시작에 /가 없으면 추가
        if (!pattern.startsWith("/")) {
            pattern = "/" + pattern; // 문자열 연결로 슬래시 추가
        }

        // 마지막 /를 제거 (루트 패턴 "/" 제외)
        if (pattern.length() > 1 && pattern.endsWith("/")) {
            pattern = pattern.substring(0, pattern.length() - 1); // substring()으로 마지막 문자 제거
        }

        // 연속된 슬래시를 하나로 정리
        pattern = pattern.replaceAll("/+", "/"); // 정규식으로 연속 슬래시 정리

        return pattern; // 정규화된 패턴 반환
    }

    /**
     * 패턴에서 경로 파라미터 이름들 추출
     * /users/{id}/posts/{postId} -> [id, postId]
     * @param pattern URL 패턴
     * @return 파라미터 이름 리스트
     */
    private List<String> extractParameterNames(String pattern) {
        List<String> paramNames = new ArrayList<>(); // 파라미터 이름 저장 리스트

        // {parameter} 형태의 패턴을 찾는 정규식
        Pattern paramPattern = Pattern.compile("\\{([^}]+)\\}"); // \{ \}로 중괄호 매칭, 캡처 그룹으로 이름 추출
        Matcher matcher = paramPattern.matcher(pattern); // 패턴 매칭 준비

        // 모든 매칭되는 파라미터 찾기
        while (matcher.find()) { // find() - 다음 매칭 찾기
            paramNames.add(matcher.group(1)); // group(1) - 첫 번째 캡처 그룹 (파라미터 이름)
        }

        return paramNames; // 추출된 파라미터 이름들 반환
    }

    /**
     * URL 패턴을 정규식으로 컴파일
     * /users/{id} -> /users/([^/]+)
     * /static/* -> /static/.*
     * /files/** -> /files/.*
     * @param pattern URL 패턴
     * @return 컴파일된 정규식 Pattern
     */
    private Pattern compilePattern(String pattern) {
        String regex = pattern; // 패턴을 정규식으로 변환할 문자열

        // 1. 경로 파라미터 {id} -> ([^/]+) 변환
        regex = regex.replaceAll("\\{[^}]+\\}", "([^/]+)"); // {파라미터} -> 캡처 그룹으로 변환, [^/]+는 슬래시가 아닌 모든 문자

        // 2. 와일드카드 패턴 처리
        regex = regex.replace("**", "DOUBLE_WILDCARD"); // ** 임시 치환 (단일 *와 구분하기 위해)
        regex = regex.replace("*", "[^/]*"); // * -> [^/]* (슬래시가 아닌 문자들)
        regex = regex.replace("DOUBLE_WILDCARD", ".*"); // ** -> .* (모든 문자, 슬래시 포함)

        // 3. 특수 문자 이스케이프
        regex = regex.replace(".", "\\."); // . -> \. (리터럴 점)
        regex = regex.replace("?", "\\?"); // ? -> \? (리터럴 물음표)

        // 4. 정확한 매칭을 위해 앵커 추가
        regex = "^" + regex + "$"; // ^ 시작, $ 끝 앵커 추가

        return Pattern.compile(regex); // 정규식 컴파일하여 Pattern 객체 반환
    }

    /**
     * 라우트 우선순위 계산
     * 더 구체적인 패턴일수록 높은 우선순위
     * @param pattern URL 패턴
     * @return 우선순위 (높을수록 먼저 매칭)
     */
    private int calculatePriority(String pattern) {
        int priority = 0; // 기본 우선순위

        // 1. 정적 경로 세그먼트마다 +10점
        String[] segments = pattern.split("/"); // 슬래시로 분할
        for (String segment : segments) { // 각 세그먼트 검사
            if (!segment.isEmpty() && !segment.contains("{") && !segment.contains("*")) { // 정적 세그먼트인지 확인
                priority += 10; // 정적 세그먼트마다 10점 추가
            }
        }

        // 2. 파라미터가 적을수록 +5점
        priority += Math.max(0, 5 - parameterNames.size()) * 5; // 파라미터 개수에 반비례

        // 3. 와일드카드에 대한 페널티
        if (pattern.contains("**")) { // 더블 와일드카드 페널티
            priority -= 20; // 큰 페널티
        } else if (pattern.contains("*")) { // 싱글 와일드카드 페널티
            priority -= 10; // 작은 페널티
        }

        // 4. 패턴 길이에 비례 (더 긴 패턴이 더 구체적)
        priority += pattern.length(); // 패턴 길이만큼 점수 추가

        return priority; // 계산된 우선순위 반환
    }

    /**
     * 매칭된 정규식에서 경로 파라미터 값들 추출
     * @param matcher 매칭된 Matcher 객체
     * @return 파라미터 이름-값 맵
     */
    private Map<String, String> extractPathParameters(Matcher matcher) {
        Map<String, String> params = new HashMap<>(); // 파라미터 저장 맵

        // 파라미터 이름과 매칭된 그룹을 매핑
        for (int i = 0; i < parameterNames.size(); i++) { // 파라미터 개수만큼 반복
            String paramName = parameterNames.get(i); // i번째 파라미터 이름
            String paramValue = matcher.group(i + 1); // i+1번째 캡처 그룹 (0번은 전체 매칭)
            params.put(paramName, paramValue); // 맵에 이름-값 쌍 저장
        }

        return params; // 추출된 파라미터 맵 반환
    }

    // ========== Getter 메서드들 ==========

    public String getMethod() { return method; }
    public String getPattern() { return pattern; }
    public RouteHandler getHandler() { return handler; }
    public int getPriority() { return priority; }
    public List<Middleware> getMiddlewares() { return new ArrayList<>(middlewares); } // 방어적 복사
    public List<String> getParameterNames() { return new ArrayList<>(parameterNames); } // 방어적 복사
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); } // 방어적 복사

    /**
     * 특정 메타데이터 값 조회
     * @param key 메타데이터 키
     * @return 메타데이터 값 (없으면 null)
     */
    public Object getMetadata(String key) {
        return metadata.get(key); // 맵에서 값 조회
    }

    /**
     * 라우트 이름 조회 (편의 메서드)
     * @return 라우트 이름
     */
    public String getName() {
        return (String) getMetadata("name"); // "name" 키의 메타데이터 조회
    }

    /**
     * 라우트 설명 조회 (편의 메서드)
     * @return 라우트 설명
     */
    public String getDescription() {
        return (String) getMetadata("description"); // "description" 키의 메타데이터 조회
    }

    // ========== Object 메서드 오버라이드 ==========

    @Override
    public String toString() {
        return String.format("Route{method='%s', pattern='%s', priority=%d, paramCount=%d}",
                method, pattern, priority, parameterNames.size()); // 라우트 정보를 문자열로 표현
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true; // 동일 참조 확인
        if (o == null || getClass() != o.getClass()) return false; // null 체크 및 타입 확인

        Route route = (Route) o; // 타입 캐스팅
        return Objects.equals(method, route.method) && // 메서드 비교
                Objects.equals(pattern, route.pattern); // 패턴 비교
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, pattern); // 메서드와 패턴으로 해시 코드 생성
    }
}