package com.serverarch.traditional;

import com.serverarch.common.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 간단하고 현대적인 HTTP 요청 클래스
 *
 * 설계 목표:
 * 1. 무거운 서블릿 API 대신 HTTP 처리 본질에 집중
 * 2. 3가지 서버 아키텍처가 공통으로 사용할 수 있는 단순한 구조
 * 3. 현대적 Java 기능 활용 (불변성, 방어적 복사)
 * 4. 메모리 효율성과 스레드 안전성 고려
 */
public class HttpRequest {

    // HTTP 메서드 (GET, POST, PUT, DELETE 등)
    // final로 선언하여 불변성 보장 - 요청이 생성된 후 변경되면 안 되기 때문
    // String 타입 사용 - 기존 HttpMethod 클래스의 상수와 호환성 유지
    private final String method;

    // 요청 경로 (/users/123 같은 URI path)
    // final로 선언하여 불변성 보장 - 라우팅의 기준이 되므로 변경되면 안 됨
    private final String path;

    // 쿼리 스트링 (?name=value&age=30 부분)
    // null일 수 있으므로 nullable로 설계 - 모든 요청이 쿼리 스트링을 갖지는 않음
    private final String queryString;

    // HTTP 헤더들 (Host, Content-Type, User-Agent 등)
    // final로 선언하여 불변성 보장 - 헤더는 요청의 메타데이터이므로 변경되면 안 됨
    private final HttpHeaders headers;

    // 요청 바디 (POST/PUT 요청의 데이터)
    // byte[]로 설계한 이유: 텍스트뿐만 아니라 바이너리 데이터도 처리해야 하기 때문
    private final byte[] body;

    // 경로 파라미터 (/users/{id}에서 {id} 부분)
    // mutable Map으로 설계한 이유: 라우팅 과정에서 동적으로 추출되어 설정되기 때문
    private final Map<String, String> pathParameters = new HashMap<>();

    // 요청 속성 (필터나 인터셉터에서 설정하는 메타데이터)
    // mutable Map으로 설계한 이유: 요청 처리 과정에서 동적으로 추가되는 정보이기 때문
    private final Map<String, Object> attributes = new HashMap<>();

    /**
     * HttpRequest 생성자
     *
     * @param method HTTP 메서드 (null이면 GET으로 기본값 설정)
     * @param path 요청 경로 (null이면 "/"로 기본값 설정)
     * @param queryString 쿼리 스트링 (nullable)
     * @param headers HTTP 헤더들 (null이면 빈 헤더로 기본값 설정)
     * @param body 요청 바디 (null이면 빈 배열로 기본값 설정)
     */
    public HttpRequest(String method, String path, String queryString,
                       HttpHeaders headers, byte[] body) {
        // null 체크하여 기본값 설정 - 방어적 프로그래밍으로 NPE 방지
        this.method = method != null ? method : HttpMethod.GET;

        // path가 null이면 루트 경로로 설정 - 모든 HTTP 요청은 경로를 가져야 하기 때문
        this.path = path != null ? path : "/";

        // queryString은 null 허용 - 모든 요청이 쿼리 스트링을 갖지는 않기 때문
        this.queryString = queryString;

        // headers가 null이면 빈 헤더 객체 생성 - 헤더 접근 시 NPE 방지
        this.headers = headers != null ? headers : new HttpHeaders();

        // body가 null이면 빈 배열로 설정 - 바디 접근 시 NPE 방지 및 일관성 보장
        this.body = body != null ? body : new byte[0];
    }

    // ========== 기본 Getter 메서드들 ==========

    /**
     * HTTP 메서드 반환
     * @return HTTP 메서드 (GET, POST, PUT, DELETE 등)
     */
    public String getMethod() {
        // 단순 반환 - final 필드이므로 불변성 보장됨
        return method;
    }

    /**
     * 요청 경로 반환
     * @return 요청 경로 (예: "/users/123")
     */
    public String getPath() {
        // 단순 반환 - String은 불변 객체이므로 안전
        return path;
    }

    /**
     * 요청 URI 반환 (getPath()와 동일)
     * @return 요청 URI - 호환성을 위한 별칭 메서드
     */
    public String getUri() {
        // path와 동일한 값 반환 - 서블릿 API 호환성을 위한 별칭
        return path;
    }

    /**
     * 쿼리 스트링 반환
     * @return 쿼리 스트링 (예: "name=john&age=30") 또는 null
     */
    public String getQueryString() {
        // null일 수 있음을 명시적으로 허용 - 모든 요청이 쿼리 스트링을 갖지는 않음
        return queryString;
    }

    /**
     * HTTP 헤더들 반환
     * @return HTTP 헤더 객체 (never null)
     */
    public HttpHeaders getHeaders() {
        // 항상 non-null 반환 - 생성자에서 null 체크했으므로 안전
        return headers;
    }

    /**
     * 요청 바디를 바이트 배열로 반환
     * @return 요청 바디의 복사본 (방어적 복사)
     */
    public byte[] getBody() {
        // clone()으로 방어적 복사 - 외부에서 배열을 수정해도 원본이 변경되지 않도록 보호
        return body.clone();
    }

    /**
     * 요청 바디를 문자열로 반환
     * @return 요청 바디를 UTF-8로 디코딩한 문자열
     */
    public String getBodyAsString() {
        // UTF-8로 디코딩 - 웹에서 가장 널리 사용되는 인코딩이므로 기본값으로 사용
        return new String(body, StandardCharsets.UTF_8);
    }

    // ========== 헤더 편의 메서드들 ==========

    /**
     * 특정 헤더의 첫 번째 값 반환
     * @param name 헤더 이름
     * @return 헤더 값 또는 null
     */
    public String getHeader(String name) {
        // HttpHeaders에 위임 - 헤더 처리 로직을 중앙화하여 일관성 보장
        return headers.getFirst(name);
    }

    /**
     * Host 헤더 반환
     * @return Host 헤더 값 (예: "www.example.com:8080")
     */
    public String getHost() {
        // 자주 사용되는 헤더를 위한 편의 메서드 - 코드 가독성 향상
        return headers.getHost();
    }

    /**
     * User-Agent 헤더 반환
     * @return User-Agent 헤더 값 (클라이언트 정보)
     */
    public String getUserAgent() {
        // 로깅이나 분석에 자주 사용되는 헤더 - 편의성 제공
        return headers.getUserAgent();
    }

    /**
     * Content-Type 헤더 반환
     * @return Content-Type 헤더 값 (예: "application/json")
     */
    public String getContentType() {
        // 요청 바디 해석에 필수적인 헤더 - 자주 접근하므로 편의 메서드 제공
        return headers.getContentType();
    }

    /**
     * Content-Length 반환
     * @return Content-Length 값 또는 실제 바디 크기
     */
    public int getContentLength() {
        // Content-Length 헤더를 우선 확인
        String length = headers.getContentLength();

        // 헤더가 없으면 실제 바디 크기 반환 - 실용적인 폴백 전략
        return length != null ? Integer.parseInt(length) : body.length;
    }

    // ========== 경로 파라미터 (라우팅에서 설정) ==========

    /**
     * 경로 파라미터 설정
     * 라우팅 시스템에서 호출됨 (예: /users/{id}에서 id 값 설정)
     *
     * @param name 파라미터 이름
     * @param value 파라미터 값
     */
    public void setPathParameter(String name, String value) {
        // mutable Map에 직접 저장 - 라우팅 과정에서만 호출되므로 안전
        pathParameters.put(name, value);
    }

    /**
     * 특정 경로 파라미터 값 반환
     *
     * @param name 파라미터 이름
     * @return 파라미터 값 또는 null
     */
    public String getPathParameter(String name) {
        // Map에서 직접 조회 - 단순하고 효율적
        return pathParameters.get(name);
    }

    /**
     * 모든 경로 파라미터 반환
     *
     * @return 경로 파라미터의 복사본
     */
    public Map<String, String> getPathParameters() {
        // 방어적 복사로 반환 - 외부에서 수정해도 원본에 영향 없음
        return new HashMap<>(pathParameters);
    }

    // ========== 쿼리 파라미터 파싱 ==========

    /**
     * 쿼리 스트링을 파싱하여 키-값 쌍으로 반환
     *
     * @return 쿼리 파라미터 Map (never null)
     */
    public Map<String, String> getQueryParameters() {
        // 결과를 저장할 Map 생성
        Map<String, String> params = new HashMap<>();

        // 쿼리 스트링이 없으면 빈 Map 반환 - null 체크로 NPE 방지
        if (queryString != null && !queryString.isEmpty()) {
            // '&'로 파라미터들을 분리 - HTTP 표준에 따른 구분자
            String[] pairs = queryString.split("&");

            // 각 파라미터 쌍을 처리
            for (String pair : pairs) {
                // '='로 키와 값을 분리, 최대 2개로 제한 - 값에 '='가 포함될 수 있음
                String[] keyValue = pair.split("=", 2);

                if (keyValue.length == 2) {
                    // 키와 값이 모두 있는 경우 - 정상적인 파라미터
                    params.put(keyValue[0], keyValue[1]);
                } else if (keyValue.length == 1) {
                    // 값이 없는 경우 빈 문자열로 설정 - 플래그 형태의 파라미터 지원
                    params.put(keyValue[0], "");
                }
                // 길이가 0인 경우는 무시 - 빈 파라미터는 의미 없음
            }
        }

        // 항상 non-null Map 반환 - 호출자가 null 체크할 필요 없음
        return params;
    }

    /**
     * 특정 쿼리 파라미터 값 반환
     *
     * @param name 파라미터 이름
     * @return 파라미터 값 또는 null
     */
    public String getParameter(String name) {
        // getQueryParameters()에 위임 - 파싱 로직 중복 방지
        return getQueryParameters().get(name);
    }

    // ========== 속성 관리 (필터 체인에서 사용) ==========

    /**
     * 요청 속성 설정
     * 필터나 인터셉터에서 요청 처리 과정에서 메타데이터를 저장할 때 사용
     *
     * @param name 속성 이름
     * @param value 속성 값 (null이면 속성 제거)
     */
    public void setAttribute(String name, Object value) {
        if (value == null) {
            // null 값이면 속성 제거 - 메모리 절약 및 명확한 의미 전달
            attributes.remove(name);
        } else {
            // 속성 저장 - Object 타입으로 다양한 데이터 지원
            attributes.put(name, value);
        }
    }

    /**
     * 요청 속성 조회
     *
     * @param name 속성 이름
     * @return 속성 값 또는 null
     */
    public Object getAttribute(String name) {
        // Map에서 직접 조회 - 단순하고 효율적
        return attributes.get(name);
    }

    /**
     * 특정 타입으로 속성 조회 (타입 안전성 제공)
     *
     * @param name 속성 이름
     * @param type 기대하는 타입
     * @return 지정된 타입의 속성 값 또는 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name, Class<T> type) {
        // 속성 조회
        Object value = attributes.get(name);

        // 타입 확인 후 캐스팅 - ClassCastException 방지
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }

        // 타입이 맞지 않거나 null이면 null 반환
        return null;
    }

    /**
     * 모든 속성 이름 반환
     *
     * @return 속성 이름들의 복사본
     */
    public Set<String> getAttributeNames() {
        // 방어적 복사로 반환 - 외부에서 수정해도 원본에 영향 없음
        return new HashSet<>(attributes.keySet());
    }

    // ========== 편의 메서드들 ==========

    /**
     * 요청이 AJAX 요청인지 확인
     *
     * @return AJAX 요청이면 true
     */
    public boolean isAjax() {
        // X-Requested-With 헤더로 AJAX 요청 판별 - jQuery 등에서 자동 설정
        String requestedWith = getHeader("X-Requested-With");
        return "XMLHttpRequest".equalsIgnoreCase(requestedWith);
    }

    /**
     * 요청 바디가 JSON 형태인지 확인
     *
     * @return JSON 요청이면 true
     */
    public boolean isJson() {
        // Content-Type 헤더로 JSON 판별 - API 서버에서 자주 사용
        String contentType = getContentType();
        return contentType != null && contentType.toLowerCase().contains("application/json");
    }

    /**
     * 요청 바디가 폼 데이터인지 확인
     *
     * @return 폼 데이터 요청이면 true
     */
    public boolean isFormData() {
        // Content-Type 헤더로 폼 데이터 판별 - HTML 폼 제출에서 사용
        String contentType = getContentType();
        return contentType != null &&
                contentType.toLowerCase().contains("application/x-www-form-urlencoded");
    }

    /**
     * HTTP 메서드가 안전한(Safe) 메서드인지 확인
     *
     * @return 안전한 메서드이면 true
     */
    public boolean isSafeMethod() {
        // HttpMethod 클래스의 유틸리티 메서드에 위임 - 메서드별 특성 판별 로직 중앙화
        return HttpMethod.isSafe(method);
    }

    /**
     * 요청 바디가 있는지 확인
     *
     * @return 바디가 있으면 true
     */
    public boolean hasBody() {
        // 바디 길이로 판별 - 단순하고 직관적
        return body.length > 0;
    }

    /**
     * 쿼리 스트링이 있는지 확인
     *
     * @return 쿼리 스트링이 있으면 true
     */
    public boolean hasQueryString() {
        // null 체크와 빈 문자열 체크 - 의미 있는 쿼리 스트링만 true
        return queryString != null && !queryString.trim().isEmpty();
    }

    // ========== Object 메서드 오버라이드 ==========

    /**
     * 요청 정보를 문자열로 표현
     * 디버깅과 로깅에 유용
     */
    @Override
    public String toString() {
        // StringBuilder 사용으로 효율적인 문자열 조합
        StringBuilder sb = new StringBuilder();
        sb.append("HttpRequest{");
        sb.append("method=").append(method);
        sb.append(", path='").append(path).append('\'');

        if (queryString != null) {
            // 쿼리 스트링이 있을 때만 포함 - 간결성을 위해
            sb.append(", queryString='").append(queryString).append('\'');
        }

        sb.append(", contentLength=").append(getContentLength());
        sb.append(", headerCount=").append(headers.size());
        sb.append('}');

        return sb.toString();
    }

    /**
     * 두 요청 객체가 같은지 비교
     * 테스트와 캐싱에서 사용
     */
    @Override
    public boolean equals(Object o) {
        // 동일 객체 참조 확인 - 가장 빠른 비교
        if (this == o) return true;

        // null 체크와 클래스 타입 확인 - 안전한 비교를 위해
        if (o == null || getClass() != o.getClass()) return false;

        HttpRequest that = (HttpRequest) o;

        // 모든 주요 필드 비교 - 완전한 동등성 확인
        return Objects.equals(method, that.method) &&
                Objects.equals(path, that.path) &&
                Objects.equals(queryString, that.queryString) &&
                Objects.equals(headers, that.headers) &&
                Arrays.equals(body, that.body);  // 배열은 Arrays.equals 사용
    }

    /**
     * 해시 코드 생성
     * HashMap, HashSet 등에서 사용
     */
    @Override
    public int hashCode() {
        // Objects.hash로 여러 필드 조합 - 표준적인 방법
        int result = Objects.hash(method, path, queryString, headers);

        // 배열은 별도로 해시 코드 계산 후 조합
        result = 31 * result + Arrays.hashCode(body);

        return result;
    }
}