package com.serverarch.eventloop.http;

import com.serverarch.common.http.HttpHeaders;
import com.serverarch.common.http.HttpStatus;
import java.nio.charset.StandardCharsets;

/**
 * HttpResponse 인터페이스의 간단한 구현체
 *
 * EventLoop 서버에서 사용하는 기본적인 HTTP 응답 구현
 * 불변 객체로 설계되어 스레드 안전성을 보장
 */
public class SimpleHttpResponse implements HttpResponse {

    // HTTP 상태 (200 OK, 404 Not Found 등)
    private final HttpStatus status;

    // 응답 바디 (바이트 배열로 저장)
    private final byte[] body;

    // HTTP 헤더들
    private final HttpHeaders headers;

    /**
     * SimpleHttpResponse 생성자
     *
     * @param status HTTP 상태 객체
     * @param body 응답 바디 (바이트 배열)
     */
    public SimpleHttpResponse(HttpStatus status, byte[] body) {
        this.status = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        this.body = body != null ? body.clone() : new byte[0]; // 방어적 복사
        this.headers = new HttpHeaders();

        // 기본 헤더 설정
        setupDefaultHeaders();
    }

    /**
     * 문자열 바디를 받는 편의 생성자
     *
     * @param status HTTP 상태 객체
     * @param body 응답 바디 (문자열)
     */
    public SimpleHttpResponse(HttpStatus status, String body) {
        this(status, body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0]);
    }

    /**
     * 기본 헤더들을 설정하는 메서드
     */
    private void setupDefaultHeaders() {
        // Content-Length 헤더 설정
        headers.set("Content-Length", String.valueOf(body.length));

        // 바디가 있는 경우에만 Content-Type 설정
        if (body.length > 0) {
            headers.set("Content-Type", "text/plain; charset=UTF-8");
        }

        // 서버 헤더 설정
        headers.set("Server", "EventLoop-Server/1.0");

        // 연결 헤더 설정 (Keep-Alive 지원)
        headers.set("Connection", "keep-alive");
    }

    @Override
    public int getStatusCode() {
        return status.getCode();
    }

    @Override
    public String getStatusMessage() {
        return status.getReasonPhrase();
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public byte[] getBody() {
        return body.clone(); // 방어적 복사
    }

    @Override
    public String getBodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    // ========== 팩토리 메서드들 ==========

    /**
     * HTTP 상태와 문자열 바디로 응답 생성
     *
     * @param status HTTP 상태
     * @param body 응답 바디 (문자열)
     * @return SimpleHttpResponse 인스턴스
     */
    public static SimpleHttpResponse create(HttpStatus status, String body) {
        return new SimpleHttpResponse(status, body);
    }

    /**
     * HTTP 상태와 바이트 배열 바디로 응답 생성
     *
     * @param status HTTP 상태
     * @param body 응답 바디 (바이트 배열)
     * @return SimpleHttpResponse 인스턴스
     */
    public static SimpleHttpResponse create(HttpStatus status, byte[] body) {
        return new SimpleHttpResponse(status, body);
    }

    /**
     * 빈 바디를 가진 응답 생성
     *
     * @param status HTTP 상태
     * @return SimpleHttpResponse 인스턴스
     */
    public static SimpleHttpResponse empty(HttpStatus status) {
        return new SimpleHttpResponse(status, new byte[0]);
    }

    // ========== 헤더 편의 메서드들 ==========

    /**
     * Content-Type 헤더 설정
     *
     * @param contentType Content-Type 값
     * @return 현재 인스턴스 (메서드 체이닝용)
     */
    public SimpleHttpResponse withContentType(String contentType) {
        headers.set("Content-Type", contentType);
        return this;
    }

    /**
     * 커스텀 헤더 추가
     *
     * @param name 헤더 이름
     * @param value 헤더 값
     * @return 현재 인스턴스 (메서드 체이닝용)
     */
    public SimpleHttpResponse withHeader(String name, String value) {
        headers.set(name, value);
        return this;
    }

    /**
     * JSON Content-Type 설정
     *
     * @return 현재 인스턴스 (메서드 체이닝용)
     */
    public SimpleHttpResponse asJson() {
        return withContentType("application/json; charset=UTF-8");
    }

    /**
     * HTML Content-Type 설정
     *
     * @return 현재 인스턴스 (메서드 체이닝용)
     */
    public SimpleHttpResponse asHtml() {
        return withContentType("text/html; charset=UTF-8");
    }

    /**
     * 캐시 비활성화 헤더 설정
     *
     * @return 현재 인스턴스 (메서드 체이닝용)
     */
    public SimpleHttpResponse noCache() {
        headers.set("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.set("Pragma", "no-cache");
        headers.set("Expires", "0");
        return this;
    }

    // ========== 유틸리티 메서드들 ==========

    /**
     * 응답 크기 반환 (헤더 + 바디)
     *
     * @return 전체 응답 크기 (바이트)
     */
    public int getTotalSize() {
        // 헤더 크기는 대략적으로 계산
        int headerSize = headers.getNames().stream()
                .mapToInt(name -> name.length() + headers.getFirst(name).length() + 4) // ": " + "\r\n"
                .sum();

        // 상태 라인 크기 추가 ("HTTP/1.1 200 OK\r\n")
        int statusLineSize = 15 + status.getReasonPhrase().length();

        return statusLineSize + headerSize + 2 + body.length; // +2 for final "\r\n"
    }

    /**
     * 응답이 성공적인지 확인
     *
     * @return 2xx 상태 코드이면 true
     */
    public boolean isSuccess() {
        return status.isSuccess();
    }

    /**
     * 응답이 에러인지 확인
     *
     * @return 4xx 또는 5xx 상태 코드이면 true
     */
    public boolean isError() {
        return status.isClientError() || status.isServerError();
    }

    // ========== Object 메서드 오버라이드 ==========

    @Override
    public String toString() {
        return String.format("SimpleHttpResponse{status=%d %s, bodyLength=%d, headerCount=%d}",
                status.getCode(), status.getReasonPhrase(), body.length, headers.size());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        SimpleHttpResponse that = (SimpleHttpResponse) obj;
        return status.equals(that.status) &&
                java.util.Arrays.equals(body, that.body) &&
                headers.equals(that.headers);
    }

    @Override
    public int hashCode() {
        int result = status.hashCode();
        result = 31 * result + java.util.Arrays.hashCode(body);
        result = 31 * result + headers.hashCode();
        return result;
    }
}