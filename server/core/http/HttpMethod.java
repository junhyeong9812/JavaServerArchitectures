package server.core.http;

/**
 * HTTP 메서드 열거형
 * RESTful API의 모든 표준 메서드를 지원
 */
public enum HttpMethod {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE"),
    HEAD("HEAD"),
    OPTIONS("OPTIONS"),
    PATCH("PATCH"),
    TRACE("TRACE"),
    CONNECT("CONNECT");

    private final String method;

    HttpMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    /**
     * 문자열로부터 HttpMethod를 파싱
     */
    public static HttpMethod fromString(String method) {
        if (method == null || method.trim().isEmpty()) {
            throw new IllegalArgumentException("HTTP method cannot be null or empty");
        }

        String upperMethod = method.trim().toUpperCase();
        for (HttpMethod httpMethod : values()) {
            if (httpMethod.method.equals(upperMethod)) {
                return httpMethod;
            }
        }

        throw new IllegalArgumentException("Unsupported HTTP method: " + method);
    }

    /**
     * 안전한 메서드인지 확인 (RFC 7231)
     * 안전한 메서드는 서버 상태를 변경하지 않음
     */
    public boolean isSafe() {
        return this == GET || this == HEAD || this == OPTIONS || this == TRACE;
    }

    /**
     * 멱등성을 가진 메서드인지 확인 (RFC 7231)
     * 멱등성 메서드는 여러 번 실행해도 같은 결과
     */
    public boolean isIdempotent() {
        return this == GET || this == HEAD || this == PUT || this == DELETE ||
                this == OPTIONS || this == TRACE;
    }

    /**
     * 요청 본문을 가질 수 있는 메서드인지 확인
     */
    public boolean canHaveBody() {
        return this == POST || this == PUT || this == PATCH;
    }

    @Override
    public String toString() {
        return method;
    }
}