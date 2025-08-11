package com.serverarch.common.http;

public enum HttpStatus {
    // 2xx Success
    OK(200, "OK"),
    CREATED(201, "Created"),
    NO_CONTENT(204, "No Content"),

    // 3xx Redirection
    MOVED_PERMANENTLY(301, "Moved Permanently"),
    FOUND(302, "Found"),

    // 4xx Client Error
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),

    // 5xx Server Error
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    NOT_IMPLEMENTED(501, "Not Implemented"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable");

    // HTTP 상태 코드 숫자 값
    private final int code;
    // HTTP 상태 코드에 대한 설명 문구
    private final String reasonPhrase;

    /**
     * HttpStatus 열거형 생성자
     * @param code HTTP 상태 코드 숫자
     * @param reasonPhrase 상태 코드 설명 문구
     */
    HttpStatus(int code, String reasonPhrase) {
        this.code = code;
        this.reasonPhrase = reasonPhrase;
    }

    /**
     * HTTP 상태 코드 숫자 반환
     * @return 상태 코드 (예: 200, 404, 500)
     */
    public int getCode() {
        return code;
    }

    /**
     * HTTP 상태 코드 설명 문구 반환
     * @return 설명 문구 (예: "OK", "Not Found", "Internal Server Error")
     */
    public String getReasonPhrase() {
        return reasonPhrase;
    }

    /**
     * 상태 코드 숫자로부터 HttpStatus 열거형 찾기
     * @param code HTTP 상태 코드 숫자
     * @return 해당하는 HttpStatus 또는 기본값 INTERNAL_SERVER_ERROR
     */
    public static HttpStatus fromCode(int code) {
        // 모든 HttpStatus 값들을 순회하며 일치하는 코드 찾기
        for (HttpStatus status : values()) {
            if (status.code == code) return status;
        }
        // 일치하는 코드가 없으면 서버 오류로 처리 (기존 로직 유지)
        return INTERNAL_SERVER_ERROR;
    }

    // ========== 추가된 유틸리티 메서드들 ==========

    /**
     * 상태 코드가 성공 범위(2xx)인지 확인
     * @param code 확인할 상태 코드
     * @return 2xx 범위이면 true
     */
    public static boolean isSuccess(int code) {
        // 200-299 범위가 성공 응답
        return code >= 200 && code < 300;
    }

    /**
     * 상태 코드가 리다이렉션 범위(3xx)인지 확인
     * @param code 확인할 상태 코드
     * @return 3xx 범위이면 true
     */
    public static boolean isRedirection(int code) {
        // 300-399 범위가 리다이렉션 응답
        return code >= 300 && code < 400;
    }

    /**
     * 상태 코드가 클라이언트 오류 범위(4xx)인지 확인
     * @param code 확인할 상태 코드
     * @return 4xx 범위이면 true
     */
    public static boolean isClientError(int code) {
        // 400-499 범위가 클라이언트 오류
        return code >= 400 && code < 500;
    }

    /**
     * 상태 코드가 서버 오류 범위(5xx)인지 확인
     * @param code 확인할 상태 코드
     * @return 5xx 범위이면 true
     */
    public static boolean isServerError(int code) {
        // 500-599 범위가 서버 오류
        return code >= 500 && code < 600;
    }

    /**
     * 현재 HttpStatus 인스턴스가 성공 상태인지 확인
     * @return 성공 상태이면 true
     */
    public boolean isSuccess() {
        // 인스턴스의 코드로 성공 여부 확인
        return isSuccess(this.code);
    }

    /**
     * 현재 HttpStatus 인스턴스가 리다이렉션 상태인지 확인
     * @return 리다이렉션 상태이면 true
     */
    public boolean isRedirection() {
        // 인스턴스의 코드로 리다이렉션 여부 확인
        return isRedirection(this.code);
    }

    /**
     * 현재 HttpStatus 인스턴스가 클라이언트 오류 상태인지 확인
     * @return 클라이언트 오류 상태이면 true
     */
    public boolean isClientError() {
        // 인스턴스의 코드로 클라이언트 오류 여부 확인
        return isClientError(this.code);
    }

    /**
     * 현재 HttpStatus 인스턴스가 서버 오류 상태인지 확인
     * @return 서버 오류 상태이면 true
     */
    public boolean isServerError() {
        // 인스턴스의 코드로 서버 오류 여부 확인
        return isServerError(this.code);
    }
}