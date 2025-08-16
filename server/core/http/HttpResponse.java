package server.core.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * HTTP 응답 객체
 * HTTP/1.1 응답 생성 및 Keep-alive 지원
 */
public class HttpResponse {

    private static final String HTTP_VERSION = "HTTP/1.1";
    private static final DateTimeFormatter HTTP_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz");

    private final HttpStatus status;
    private final HttpHeaders headers;
    private final byte[] body;

    // 기본 생성자
    public HttpResponse(HttpStatus status, HttpHeaders headers, byte[] body) {
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.headers = headers != null ? headers : new HttpHeaders();
        this.body = body != null ? body.clone() : new byte[0];

        // 기본 헤더 설정
        setDefaultHeaders();
    }

    // === 정적 팩토리 메서드 ===

    /**
     * 200 OK 응답 생성
     */
    public static HttpResponse ok() {
        return new HttpResponse(HttpStatus.OK, new HttpHeaders(), new byte[0]);
    }

    public static HttpResponse ok(String body) {
        return ok(body.getBytes(StandardCharsets.UTF_8));
    }

    public static HttpResponse ok(byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(body.length);
        return new HttpResponse(HttpStatus.OK, headers, body);
    }

    public static HttpResponse ok(String body, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentLength(body.getBytes(StandardCharsets.UTF_8).length);
        return new HttpResponse(HttpStatus.OK, headers, body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * JSON 응답 생성
     */
    public static HttpResponse json(String jsonBody) {
        return ok(jsonBody, "application/json; charset=utf-8");
    }

    /**
     * HTML 응답 생성
     */
    public static HttpResponse html(String htmlBody) {
        return ok(htmlBody, "text/html; charset=utf-8");
    }

    /**
     * 텍스트 응답 생성
     */
    public static HttpResponse text(String textBody) {
        return ok(textBody, "text/plain; charset=utf-8");
    }

    /**
     * 201 Created 응답
     */
    public static HttpResponse created() {
        return new HttpResponse(HttpStatus.CREATED, new HttpHeaders(), new byte[0]);
    }

    public static HttpResponse created(String body) {
        return created(body.getBytes(StandardCharsets.UTF_8));
    }

    public static HttpResponse created(byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(body.length);
        return new HttpResponse(HttpStatus.CREATED, headers, body);
    }

    /**
     * 204 No Content 응답
     */
    public static HttpResponse noContent() {
        return new HttpResponse(HttpStatus.NO_CONTENT, new HttpHeaders(), new byte[0]);
    }

    /**
     * 301 Moved Permanently 리다이렉트
     */
    public static HttpResponse movedPermanently(String location) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Location", location);
        return new HttpResponse(HttpStatus.MOVED_PERMANENTLY, headers, new byte[0]);
    }

    /**
     * 302 Found 리다이렉트
     */
    public static HttpResponse found(String location) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Location", location);
        return new HttpResponse(HttpStatus.FOUND, headers, new byte[0]);
    }

    /**
     * 400 Bad Request 에러
     */
    public static HttpResponse badRequest() {
        return badRequest("Bad Request");
    }

    public static HttpResponse badRequest(String message) {
        return new HttpResponse(HttpStatus.BAD_REQUEST, new HttpHeaders(),
                message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 401 Unauthorized 에러
     */
    public static HttpResponse unauthorized() {
        return unauthorized("Unauthorized");
    }

    public static HttpResponse unauthorized(String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("WWW-Authenticate", "Basic realm=\"Server\"");
        return new HttpResponse(HttpStatus.UNAUTHORIZED, headers,
                message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 403 Forbidden 에러
     */
    public static HttpResponse forbidden() {
        return forbidden("Forbidden");
    }

    public static HttpResponse forbidden(String message) {
        return new HttpResponse(HttpStatus.FORBIDDEN, new HttpHeaders(),
                message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 404 Not Found 에러
     */
    public static HttpResponse notFound() {
        return notFound("Not Found");
    }

    public static HttpResponse notFound(String message) {
        return new HttpResponse(HttpStatus.NOT_FOUND, new HttpHeaders(),
                message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 405 Method Not Allowed 에러
     */
    public static HttpResponse methodNotAllowed(String allowedMethods) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Allow", allowedMethods);
        return new HttpResponse(HttpStatus.METHOD_NOT_ALLOWED, headers,
                "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 500 Internal Server Error
     */
    public static HttpResponse internalServerError() {
        return internalServerError("Internal Server Error");
    }

    public static HttpResponse internalServerError(String message) {
        return new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR, new HttpHeaders(),
                message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 503 Service Unavailable
     */
    public static HttpResponse serviceUnavailable() {
        return serviceUnavailable("Service Unavailable");
    }

    public static HttpResponse serviceUnavailable(String message) {
        return new HttpResponse(HttpStatus.SERVICE_UNAVAILABLE, new HttpHeaders(),
                message.getBytes(StandardCharsets.UTF_8));
    }

    // === 빌더 패턴 ===

    /**
     * 응답 빌더 생성
     */
    public static Builder builder(HttpStatus status) {
        return new Builder(status);
    }

    public static class Builder {
        private final HttpStatus status;
        private final HttpHeaders headers;
        private byte[] body;

        public Builder(HttpStatus status) {
            this.status = status;
            this.headers = new HttpHeaders();
            this.body = new byte[0];
        }

        public Builder header(String name, String value) {
            headers.set(name, value);
            return this;
        }

        public Builder contentType(String contentType) {
            headers.setContentType(contentType);
            return this;
        }

        public Builder contentLength(long length) {
            headers.setContentLength(length);
            return this;
        }

        public Builder keepAlive(boolean keepAlive) {
            headers.setKeepAlive(keepAlive);
            return this;
        }

        public Builder body(String body) {
            return body(body.getBytes(StandardCharsets.UTF_8));
        }

        public Builder body(byte[] body) {
            this.body = body != null ? body.clone() : new byte[0];
            headers.setContentLength(this.body.length);
            return this;
        }

        public Builder cookie(String name, String value) {
            headers.add("Set-Cookie", name + "=" + value);
            return this;
        }

        public Builder cookie(String name, String value, int maxAge) {
            headers.add("Set-Cookie", name + "=" + value + "; Max-Age=" + maxAge);
            return this;
        }

        public HttpResponse build() {
            return new HttpResponse(status, headers, body);
        }
    }

    // === 접근자 메서드 ===

    public HttpStatus getStatus() {
        return status;
    }

    /**
     * 상태 코드를 정수로 반환 (편의 메서드)
     */
    public int getStatusCode() {
        return status.getCode();
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body.clone();
    }

    public String getBodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public int getBodyLength() {
        return body.length;
    }

    // === 헤더 편의 메서드 ===

    /**
     * 헤더 설정
     */
    public HttpResponse setHeader(String name, String value) {
        headers.set(name, value);
        return this;
    }

    /**
     * Content-Type 설정
     */
    public HttpResponse setContentType(String contentType) {
        headers.setContentType(contentType);
        return this;
    }

    /**
     * Keep-Alive 설정
     */
    public HttpResponse setKeepAlive(boolean keepAlive) {
        headers.setKeepAlive(keepAlive);
        return this;
    }

    /**
     * 쿠키 추가
     */
    public HttpResponse addCookie(String name, String value) {
        headers.add("Set-Cookie", name + "=" + value);
        return this;
    }

    // === 응답 출력 ===

    /**
     * 완전한 HTTP 응답을 바이트 배열로 생성
     */
    public byte[] toByteArray() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writeTo(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error creating response bytes", e);
        }
    }

    /**
     * OutputStream에 HTTP 응답 작성
     */
    public void writeTo(OutputStream output) throws IOException {
        // Status Line
        String statusLine = HTTP_VERSION + " " + status.getCode() + " " + status.getReasonPhrase() + "\r\n";
        output.write(statusLine.getBytes(StandardCharsets.UTF_8));

        // Headers
        output.write(headers.toHeaderString().getBytes(StandardCharsets.UTF_8));

        // Empty line
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));

        // Body
        if (body.length > 0) {
            output.write(body);
        }

        output.flush();
    }

    /**
     * 기본 헤더 설정
     */
    private void setDefaultHeaders() {
        // Date 헤더
        if (!headers.contains("Date")) {
            String dateString = ZonedDateTime.now(ZoneId.of("GMT"))
                    .format(HTTP_DATE_FORMAT);
            headers.set("Date", dateString);
        }

        // Server 헤더
        if (!headers.contains("Server")) {
            headers.set("Server", "JavaServerArchitectures/1.0");
        }

        // Content-Length 헤더 (body가 있는 경우)
        if (body.length > 0 && !headers.contains("Content-Length")) {
            headers.setContentLength(body.length);
        }

        // Connection 헤더 기본값
        if (!headers.contains("Connection")) {
            headers.set("Connection", "keep-alive");
        }
    }

    @Override
    public String toString() {
        return String.format("HttpResponse{status=%s, bodyLength=%d}",
                status, body.length);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof HttpResponse)) return false;

        HttpResponse other = (HttpResponse) obj;
        return status == other.status &&
                headers.equals(other.headers) &&
                java.util.Arrays.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, headers, java.util.Arrays.hashCode(body));
    }
}