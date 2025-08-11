package com.serverarch.common.http;

import java.util.*;

/**
 * HTTP 헤더들을 관리하는 클래스
 *
 * 설계 목표:
 * 1. 대소문자 무관한 헤더 이름 처리 (HTTP 표준 준수)
 * 2. 멀티값 헤더 지원 (예: Accept-Encoding: gzip, deflate)
 * 3. 자주 사용되는 헤더들을 위한 편의 메서드 제공
 * 4. 기존 HttpRequest/HttpResponse 코드와 완벽 호환
 *
 * HTTP 헤더 특성:
 * - 헤더 이름은 대소문자 무관 (RFC 7230)
 * - 하나의 헤더에 여러 값이 올 수 있음
 * - 일부 헤더는 특별한 의미를 가짐 (Content-Type, Content-Length 등)
 */
public class HttpHeaders {

    // 헤더 저장소 - 헤더명을 소문자로 정규화하여 저장
    // LinkedHashMap 사용 이유: 헤더 순서 보존 + 빠른 조회 (O(1))
    private final Map<String, List<String>> headers = new LinkedHashMap<>();

    /**
     * 기본 생성자
     * 빈 헤더 컬렉션으로 초기화
     */
    public HttpHeaders() {
        // 명시적으로 빈 상태로 초기화
        // 필요시 기본 헤더들을 추가할 수 있도록 준비
    }

    /**
     * 복사 생성자
     * 기존 헤더들을 복사하여 새로운 인스턴스 생성
     *
     * @param other 복사할 헤더 객체
     */
    public HttpHeaders(HttpHeaders other) {
        if (other != null) {
            // 깊은 복사 수행 - 리스트까지 복사하여 독립성 보장
            for (Map.Entry<String, List<String>> entry : other.headers.entrySet()) {
                this.headers.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
    }

    // ========== 헤더 추가/설정 메서드들 ==========

    /**
     * 헤더 값 추가 (기존 값에 추가)
     * 동일한 헤더에 여러 값이 있을 수 있는 경우 사용
     *
     * @param name 헤더 이름 (대소문자 무관)
     * @param value 헤더 값
     */
    public void add(String name, String value) {
        // null 체크 - 잘못된 입력 방지
        if (name == null || value == null) {
            return;
        }

        // 헤더 이름을 소문자로 정규화 - HTTP 표준에 따라 대소문자 무관 처리
        String normalizedName = name.toLowerCase().trim();

        // 빈 헤더 이름 방지
        if (normalizedName.isEmpty()) {
            return;
        }

        // 기존 값 리스트에 추가 - computeIfAbsent로 리스트가 없으면 생성
        headers.computeIfAbsent(normalizedName, k -> new ArrayList<>()).add(value.trim());
    }

    /**
     * 헤더 값 설정 (기존 값 대체)
     * 하나의 헤더에 하나의 값만 있어야 하는 경우 사용
     *
     * @param name 헤더 이름 (대소문자 무관)
     * @param value 헤더 값
     */
    public void set(String name, String value) {
        // null 체크
        if (name == null) {
            return;
        }

        // 헤더 이름 정규화
        String normalizedName = name.toLowerCase().trim();

        // 빈 헤더 이름 방지
        if (normalizedName.isEmpty()) {
            return;
        }

        if (value == null) {
            // 값이 null이면 헤더 제거
            headers.remove(normalizedName);
        } else {
            // 새 리스트 생성하여 기존 값들 모두 대체
            List<String> values = new ArrayList<>();
            values.add(value.trim());
            headers.put(normalizedName, values);
        }
    }

    /**
     * 헤더 제거
     *
     * @param name 제거할 헤더 이름 (대소문자 무관)
     */
    public void remove(String name) {
        if (name != null) {
            // 헤더 이름 정규화 후 제거
            headers.remove(name.toLowerCase().trim());
        }
    }

    // ========== 헤더 조회 메서드들 ==========

    /**
     * 지정된 헤더의 첫 번째 값 반환
     * 가장 많이 사용되는 조회 메서드
     *
     * @param name 헤더 이름 (대소문자 무관)
     * @return 첫 번째 헤더 값 또는 null (헤더가 없는 경우)
     */
    public String getFirst(String name) {
        if (name == null) {
            return null;
        }

        // 헤더 값 리스트 조회
        List<String> values = headers.get(name.toLowerCase().trim());

        // 리스트가 존재하고 비어있지 않으면 첫 번째 값 반환
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    /**
     * 지정된 헤더의 모든 값 반환
     * 멀티값 헤더 처리용
     *
     * @param name 헤더 이름 (대소문자 무관)
     * @return 헤더 값들의 복사본 (변경해도 원본에 영향 없음)
     */
    public List<String> get(String name) {
        if (name == null) {
            return Collections.emptyList();
        }

        // 헤더 값 리스트 조회
        List<String> values = headers.get(name.toLowerCase().trim());

        // 방어적 복사로 반환 - 외부에서 수정해도 원본에 영향 없음
        return values != null ? new ArrayList<>(values) : Collections.emptyList();
    }

    /**
     * 모든 헤더 이름 반환
     *
     * @return 헤더 이름들의 복사본 (소문자로 정규화된 상태)
     */
    public Set<String> getNames() {
        // 방어적 복사로 반환
        return new HashSet<>(headers.keySet());
    }

    /**
     * 지정된 헤더가 존재하는지 확인
     *
     * @param name 확인할 헤더 이름 (대소문자 무관)
     * @return 헤더가 존재하면 true
     */
    public boolean contains(String name) {
        return name != null && headers.containsKey(name.toLowerCase().trim());
    }

    /**
     * 전체 헤더 수 반환
     *
     * @return 등록된 헤더의 개수
     */
    public int size() {
        return headers.size();
    }

    /**
     * 헤더가 비어있는지 확인
     *
     * @return 헤더가 없으면 true
     */
    public boolean isEmpty() {
        return headers.isEmpty();
    }

    // ========== 자주 사용되는 헤더들을 위한 편의 메서드들 ==========

    /**
     * Content-Type 헤더 반환
     * HTTP 요청/응답에서 가장 중요한 헤더 중 하나
     *
     * @return Content-Type 값 (예: "text/html; charset=UTF-8")
     */
    public String getContentType() {
        return getFirst("content-type");
    }

    /**
     * Content-Type 헤더 설정
     *
     * @param contentType Content-Type 값
     */
    public void setContentType(String contentType) {
        set("Content-Type", contentType);
    }

    /**
     * Content-Length 헤더 반환
     * 요청/응답 바디의 크기를 나타냄
     *
     * @return Content-Length 값 (문자열)
     */
    public String getContentLength() {
        return getFirst("content-length");
    }

    /**
     * Content-Length 헤더 설정 (정수값)
     *
     * @param length 바디 크기
     */
    public void setContentLength(long length) {
        set("Content-Length", String.valueOf(length));
    }

    /**
     * Host 헤더 반환
     * HTTP/1.1에서 필수 헤더
     *
     * @return Host 값 (예: "www.example.com:8080")
     */
    public String getHost() {
        return getFirst("host");
    }

    /**
     * Host 헤더 설정
     *
     * @param host Host 값
     */
    public void setHost(String host) {
        set("Host", host);
    }

    /**
     * User-Agent 헤더 반환
     * 클라이언트 정보를 나타냄
     *
     * @return User-Agent 값
     */
    public String getUserAgent() {
        return getFirst("user-agent");
    }

    /**
     * User-Agent 헤더 설정
     *
     * @param userAgent User-Agent 값
     */
    public void setUserAgent(String userAgent) {
        set("User-Agent", userAgent);
    }

    /**
     * Accept 헤더 반환
     * 클라이언트가 수용 가능한 미디어 타입들
     *
     * @return Accept 값 (예: "text/html,application/json")
     */
    public String getAccept() {
        return getFirst("accept");
    }

    /**
     * Accept 헤더 설정
     *
     * @param accept Accept 값
     */
    public void setAccept(String accept) {
        set("Accept", accept);
    }

    /**
     * Authorization 헤더 반환
     * 인증 정보를 담음
     *
     * @return Authorization 값 (예: "Bearer token123")
     */
    public String getAuthorization() {
        return getFirst("authorization");
    }

    /**
     * Authorization 헤더 설정
     *
     * @param authorization Authorization 값
     */
    public void setAuthorization(String authorization) {
        set("Authorization", authorization);
    }

    /**
     * Cache-Control 헤더 반환
     * 캐싱 동작을 제어
     *
     * @return Cache-Control 값 (예: "no-cache, no-store")
     */
    public String getCacheControl() {
        return getFirst("cache-control");
    }

    /**
     * Cache-Control 헤더 설정
     *
     * @param cacheControl Cache-Control 값
     */
    public void setCacheControl(String cacheControl) {
        set("Cache-Control", cacheControl);
    }

    /**
     * Connection 헤더 반환
     * 연결 관리 옵션
     *
     * @return Connection 값 (예: "keep-alive", "close")
     */
    public String getConnection() {
        return getFirst("connection");
    }

    /**
     * Connection 헤더 설정
     *
     * @param connection Connection 값
     */
    public void setConnection(String connection) {
        set("Connection", connection);
    }

    // ========== 헤더 조작 편의 메서드들 ==========

    /**
     * 모든 헤더 삭제
     * 헤더 컬렉션을 완전히 비움
     */
    public void clear() {
        headers.clear();
    }

    /**
     * 다른 HttpHeaders의 모든 헤더를 현재 객체에 추가
     * 기존 헤더와 병합됨
     *
     * @param other 추가할 헤더들
     */
    public void addAll(HttpHeaders other) {
        if (other == null) {
            return;
        }

        // 다른 객체의 모든 헤더를 순회하며 추가
        for (Map.Entry<String, List<String>> entry : other.headers.entrySet()) {
            String headerName = entry.getKey();

            // 각 헤더의 모든 값들을 추가
            for (String value : entry.getValue()) {
                add(headerName, value); // add 메서드 사용으로 기존 값에 추가
            }
        }
    }

    /**
     * 헤더 값들을 쉼표로 구분된 단일 문자열로 반환
     * Accept, Accept-Encoding 같은 멀티값 헤더에 유용
     *
     * @param name 헤더 이름
     * @return 쉼표로 구분된 헤더 값들 또는 null
     */
    public String getCommaSeparated(String name) {
        List<String> values = get(name);

        if (values.isEmpty()) {
            return null;
        }

        // 쉼표와 공백으로 구분하여 조합
        return String.join(", ", values);
    }

    /**
     * 쉼표로 구분된 문자열을 파싱하여 멀티값 헤더로 설정
     * "gzip, deflate, br" -> ["gzip", "deflate", "br"]
     *
     * @param name 헤더 이름
     * @param commaSeparatedValues 쉼표로 구분된 값들
     */
    public void setCommaSeparated(String name, String commaSeparatedValues) {
        if (name == null || commaSeparatedValues == null) {
            return;
        }

        // 기존 값들 제거
        remove(name);

        // 쉼표로 분리하여 각각 추가
        String[] values = commaSeparatedValues.split(",");
        for (String value : values) {
            String trimmedValue = value.trim();
            if (!trimmedValue.isEmpty()) {
                add(name, trimmedValue);
            }
        }
    }

    // ========== Object 메서드 오버라이드 ==========

    /**
     * 헤더 정보를 문자열로 표현
     * 디버깅과 로깅에 유용
     */
    @Override
    public String toString() {
        if (headers.isEmpty()) {
            return "HttpHeaders{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("HttpHeaders{");

        boolean first = true;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!first) {
                sb.append(", ");
            }

            // 헤더명=값들 형태로 표현
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * 두 HttpHeaders 객체가 같은지 비교
     * 테스트에서 주로 사용
     */
    @Override
    public boolean equals(Object o) {
        // 동일 객체 참조 확인
        if (this == o) return true;

        // null 체크와 클래스 타입 확인
        if (o == null || getClass() != o.getClass()) return false;

        HttpHeaders that = (HttpHeaders) o;

        // 헤더 맵 전체 비교
        return Objects.equals(headers, that.headers);
    }

    /**
     * 해시 코드 생성
     * HashMap, HashSet 등에서 사용
     */
    @Override
    public int hashCode() {
        return Objects.hash(headers);
    }

    // ========== 내부 유틸리티 메서드들 ==========

    /**
     * 헤더 이름이 유효한지 검증
     * HTTP 표준에 따른 토큰 문자만 허용
     *
     * @param name 검증할 헤더 이름
     * @return 유효하면 true
     */
    private boolean isValidHeaderName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        // HTTP 토큰 문자만 허용 (RFC 7230)
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!isTokenChar(c)) {
                return false;
            }
        }

        return true;
    }

    /**
     * HTTP 토큰에서 사용할 수 있는 문자인지 확인
     * RFC 7230에 정의된 tchar 규칙
     */
    private boolean isTokenChar(char c) {
        return (c >= 'A' && c <= 'Z') ||
                (c >= 'a' && c <= 'z') ||
                (c >= '0' && c <= '9') ||
                c == '!' || c == '#' || c == '$' || c == '%' || c == '&' ||
                c == '\'' || c == '*' || c == '+' || c == '-' || c == '.' ||
                c == '^' || c == '_' || c == '`' || c == '|' || c == '~';
    }
}