package server.core.mini;

// HTTP 관련 클래스들
import server.core.http.*;
// 컬렉션 관련 클래스들
import java.util.*;
// 멀티스레드 안전한 HashMap
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP 요청 래퍼
 * HttpRequest를 서블릿 친화적 인터페이스로 래핑
 *
 * 역할:
 * - HttpRequest의 기능을 서블릿 API 스타일로 제공
 * - 경로 파라미터, 요청 속성 등 추가 기능 제공
 * - 서블릿 컨텍스트와의 연동
 * - 편의 메서드들 제공
 */
public class MiniRequest {

    // 원본 HTTP 요청 객체
    // final: 래퍼 생성 후 변경 불가능
    private final HttpRequest httpRequest;

    // 서블릿 컨텍스트 참조
    // 애플리케이션 수준의 정보에 접근하기 위함
    private final MiniContext context;

    // 요청별 속성들을 저장하는 맵
    // 요청 처리 중에 임시로 데이터를 저장하는 용도
    // ConcurrentHashMap: 멀티스레드 안전한 Map 구현
    private final Map<String, Object> attributes;

    // 생성자
    public MiniRequest(HttpRequest httpRequest, MiniContext context) {
        // Objects.requireNonNull(): null 체크 후 예외 발생
        // NullPointerException을 방지하기 위한 방어적 프로그래밍
        this.httpRequest = Objects.requireNonNull(httpRequest);
        this.context = Objects.requireNonNull(context);

        // 요청별 속성 저장소 초기화
        this.attributes = new ConcurrentHashMap<>();
    }

    // === HTTP 요청 정보 위임 ===
    // HttpRequest의 기능들을 그대로 위임하여 제공

    /**
     * HTTP 메서드 반환 (GET, POST, PUT, DELETE 등)
     */
    public HttpMethod getMethod() {
        // 원본 HttpRequest의 메서드를 그대로 반환
        return httpRequest.getMethod();
    }

    /**
     * 요청 URI 반환 (경로 + 쿼리스트링)
     * 예: "/users/123?name=john"
     */
    public String getRequestURI() {
        return httpRequest.getUri();
    }

    /**
     * 완전한 요청 URL 반환
     * 예: "http://localhost:8080/users/123?name=john"
     */
    public String getRequestURL() {
        // 간단한 구현: 하드코딩된 호스트 + 원본 URI
        // 실제 구현에서는 Host 헤더나 서버 설정을 참조해야 함
        return "http://localhost:8080" + httpRequest.getUri();
    }

    /**
     * 서블릿 경로 반환 (컨텍스트 경로 제외)
     * 컨텍스트 경로가 "/app"이고 URI가 "/app/users/123"이면 "/users/123" 반환
     */
    public String getServletPath() {
        String contextPath = context.getContextPath();
        String uri = httpRequest.getUri();

        // 컨텍스트 경로로 시작하는지 확인
        // startsWith(): 문자열이 특정 접두사로 시작하는지 확인
        if (uri.startsWith(contextPath)) {
            // substring(): 지정된 인덱스부터 끝까지의 부분 문자열 반환
            return uri.substring(contextPath.length());
        }

        // 컨텍스트 경로와 매치되지 않으면 원본 URI 반환
        return uri;
    }

    /**
     * 경로 정보 반환 (현재는 단순히 경로 반환)
     */
    public String getPathInfo() {
        return httpRequest.getPath();
    }

    /**
     * 쿼리 스트링 반환
     * 예: "name=john&age=25"
     */
    public String getQueryString() {
        return httpRequest.getQueryString();
    }

    // === 파라미터 접근 ===
    // 쿼리 파라미터와 폼 파라미터를 통합하여 제공

    /**
     * 단일 파라미터 값 가져오기
     * 쿼리 파라미터를 우선으로 하고, 없으면 폼 파라미터에서 찾음
     */
    public String getParameter(String name) {
        // 1. 쿼리 파라미터에서 먼저 찾기
        String value = httpRequest.getQueryParameter(name);

        // 2. 쿼리 파라미터에 없으면 폼 파라미터에서 찾기
        // 삼항 연산자: value가 null이 아니면 그대로, null이면 폼에서 찾기
        return value != null ? value : httpRequest.getFormParameter(name);
    }

    /**
     * 파라미터의 모든 값 가져오기 (다중 값 지원)
     * 예: checkbox에서 여러 값이 선택된 경우
     */
    public String[] getParameterValues(String name) {
        // 쿼리 파라미터의 모든 값들
        List<String> queryValues = httpRequest.getQueryParameterValues(name);
        // 폼 파라미터의 모든 값들
        List<String> formValues = httpRequest.getFormParameterValues(name);

        // 두 리스트를 합치기
        // ArrayList 생성자: 기존 Collection을 복사하여 새 리스트 생성
        List<String> allValues = new ArrayList<>(queryValues);
        // addAll(): 다른 Collection의 모든 요소를 추가
        allValues.addAll(formValues);

        // 값이 있으면 배열로 변환, 없으면 null 반환
        // isEmpty(): 리스트가 비어있는지 확인
        // toArray(): 리스트를 배열로 변환
        return allValues.isEmpty() ? null : allValues.toArray(new String[0]);
    }

    /**
     * 모든 파라미터를 맵으로 반환
     * Key: 파라미터명, Value: 파라미터 값들의 배열
     */
    public Map<String, String[]> getParameterMap() {
        // 결과를 저장할 맵
        Map<String, String[]> paramMap = new HashMap<>();

        // 1. 쿼리 파라미터들 추가
        // forEach(): Map의 각 엔트리에 대해 람다 함수 실행
        // (key, values) -> ... : 람다 표현식
        httpRequest.getQueryParameters().forEach((key, values) ->
                // List를 String 배열로 변환하여 맵에 저장
                paramMap.put(key, values.toArray(new String[0])));

        // 2. 폼 파라미터들 추가 (중복시 쿼리 파라미터가 우선)
        // putIfAbsent(): 키가 없을 때만 값을 저장 (기존 값 유지)
        httpRequest.getFormParameters().forEach((key, values) ->
                paramMap.putIfAbsent(key, values.toArray(new String[0])));

        return paramMap;
    }

    /**
     * 모든 파라미터명 가져오기
     */
    public Set<String> getParameterNames() {
        // HashSet: 중복을 허용하지 않는 집합
        Set<String> names = new HashSet<>();

        // 쿼리 파라미터 이름들 추가
        // addAll(): 다른 Collection의 모든 요소를 Set에 추가
        names.addAll(httpRequest.getQueryParameters().keySet());

        // 폼 파라미터 이름들 추가 (중복은 자동으로 제거됨)
        names.addAll(httpRequest.getFormParameters().keySet());

        return names;
    }

    // === 헤더 접근 ===
    // HTTP 헤더 정보에 대한 편리한 접근 제공

    /**
     * 특정 헤더의 첫 번째 값 가져오기
     */
    public String getHeader(String name) {
        return httpRequest.getHeader(name);
    }

    /**
     * 특정 헤더의 모든 값 가져오기 (다중 값 헤더 지원)
     */
    public List<String> getHeaders(String name) {
        return httpRequest.getHeaders().getAll(name);
    }

    /**
     * 모든 헤더명 가져오기
     */
    public Set<String> getHeaderNames() {
        return httpRequest.getHeaderNames();
    }

    /**
     * Content-Type 헤더 가져오기
     */
    public String getContentType() {
        return httpRequest.getContentType();
    }

    /**
     * Content-Length 헤더 가져오기
     */
    public int getContentLength() {
        // long을 int로 형변환 (일반적인 요청 크기 범위 내에서 안전)
        return (int) httpRequest.getContentLength();
    }

    // === Body 접근 ===
    // 요청 본문 데이터에 대한 접근 제공

    /**
     * 요청 본문을 문자열로 가져오기
     */
    public String getBody() {
        return httpRequest.getBodyAsString();
    }

    /**
     * 요청 본문을 바이트 배열로 가져오기
     */
    public byte[] getBodyBytes() {
        return httpRequest.getBody();
    }

    // === 속성 관리 ===
    // 요청 처리 중에 임시 데이터를 저장하고 공유하는 기능

    /**
     * 요청 속성 설정
     * 요청 처리 중에 필터, 서블릿 등이 데이터를 공유할 때 사용
     */
    public void setAttribute(String name, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("Attribute name cannot be null");
        }

        if (value == null) {
            // 값이 null이면 속성 제거
            attributes.remove(name);
        } else {
            // 값이 있으면 속성 저장
            attributes.put(name, value);
        }

        // HttpRequest에도 동일하게 설정 (일관성 유지)
        httpRequest.setAttribute(name, value);
    }

    /**
     * 요청 속성 가져오기
     * MiniRequest의 속성을 우선으로 하고, 없으면 HttpRequest에서 찾기
     */
    public Object getAttribute(String name) {
        // 1. MiniRequest의 속성에서 먼저 찾기
        Object value = attributes.get(name);

        // 2. 없으면 원본 HttpRequest에서 찾기
        return value != null ? value : httpRequest.getAttribute(name);
    }

    /**
     * 요청 속성 제거
     */
    public void removeAttribute(String name) {
        // 양쪽 모두에서 제거
        attributes.remove(name);
        httpRequest.removeAttribute(name);
    }

    /**
     * 모든 속성명 가져오기
     */
    public Set<String> getAttributeNames() {
        // 두 소스의 속성명들을 합치기
        Set<String> names = new HashSet<>(attributes.keySet());
        names.addAll(httpRequest.getAttributeNames());
        return names;
    }

    // === 경로 파라미터 (라우팅에서 설정됨) ===
    // RESTful API에서 사용하는 경로 파라미터 지원
    // 예: /users/{id} -> /users/123에서 id=123

    /**
     * 경로 파라미터 가져오기
     * 라우터에서 "path." 접두사로 설정한 속성을 찾음
     */
    public String getPathParameter(String name) {
        // 경로 파라미터는 "path." 접두사를 사용하여 저장됨
        return getAttribute("path." + name, String.class);
    }

    /**
     * 모든 경로 파라미터를 맵으로 가져오기
     */
    @SuppressWarnings("unchecked")  // 제네릭 형변환 경고 억제
    public Map<String, String> getPathParameters() {
        // 라우터에서 설정한 전체 경로 파라미터 맵
        return getAttribute("path.parameters", Map.class);
    }

    // === 유틸리티 메서드 ===
    // 편의성을 위한 추가 메서드들

    /**
     * 타입 안전한 속성 가져오기
     * 제네릭을 사용하여 형변환 없이 원하는 타입으로 반환
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name, Class<T> type) {
        Object value = getAttribute(name);

        // 값이 있고 요청한 타입의 인스턴스인지 확인
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * JSON 요청인지 확인
     * Content-Type이 application/json인지 체크
     */
    public boolean isJsonRequest() {
        return httpRequest.isJsonRequest();
    }

    /**
     * AJAX 요청인지 확인
     * X-Requested-With 헤더가 XMLHttpRequest인지 체크
     */
    public boolean isAjaxRequest() {
        return httpRequest.isAjaxRequest();
    }

    /**
     * 서블릿 컨텍스트 반환
     */
    public MiniContext getContext() {
        return context;
    }

    /**
     * 원본 HttpRequest 객체 반환
     * 고급 사용자가 직접 HttpRequest 기능에 접근할 때 사용
     */
    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    /**
     * 요청 정보의 문자열 표현
     * 로깅과 디버깅에 유용
     */
    @Override
    public String toString() {
        // "GET /users/123" 형태로 메서드와 URI 표시
        return String.format("MiniRequest{%s %s}", getMethod(), getRequestURI());
    }
}