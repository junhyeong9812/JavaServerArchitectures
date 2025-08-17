package server.core.http;

// I/O 관련 클래스들
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
// UTF-8 등의 표준 문자 인코딩
import java.nio.charset.StandardCharsets;
// 시간대 처리를 위한 클래스들
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
// 유틸리티 클래스
import java.util.Objects;

/**
 * HTTP 응답 객체
 * HTTP/1.1 응답 생성 및 Keep-alive 지원
 */
public class HttpResponse {

    // HTTP 버전 상수 - 모든 응답에서 사용
    private static final String HTTP_VERSION = "HTTP/1.1";

    // HTTP Date 헤더 형식 (RFC 7231)
    // "EEE, dd MMM yyyy HH:mm:ss zzz" -> "Mon, 01 Jan 2024 12:00:00 GMT"
    // DateTimeFormatter: 날짜/시간을 특정 형식으로 포맷팅하는 클래스
    private static final DateTimeFormatter HTTP_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz");

    // HTTP 응답의 기본 구성 요소들 (불변)
    private final HttpStatus status;   // HTTP 상태 코드 (200, 404 등)
    private final HttpHeaders headers; // HTTP 헤더들
    private final byte[] body;         // 응답 본문 (바이트 배열)

    // 기본 생성자
    public HttpResponse(HttpStatus status, HttpHeaders headers, byte[] body) {
        // Objects.requireNonNull(): null 체크 후 예외 발생
        this.status = Objects.requireNonNull(status, "Status cannot be null");

        // headers가 null이면 빈 HttpHeaders 객체 생성
        // 삼항 연산자: 조건 ? 참일때값 : 거짓일때값
        this.headers = headers != null ? headers : new HttpHeaders();

        // body가 null이면 빈 배열, 아니면 복사본 생성
        // clone(): 배열의 얕은 복사 (외부 수정 방지)
        this.body = body != null ? body.clone() : new byte[0];

        // 기본 헤더들을 설정 (Date, Server 등)
        setDefaultHeaders();
    }

    // === 정적 팩토리 메서드 ===

    /**
     * 200 OK 응답 생성
     */
    public static HttpResponse ok() {
        // 빈 본문을 가진 200 OK 응답 생성
        return new HttpResponse(HttpStatus.OK, new HttpHeaders(), new byte[0]);
    }

    // 문자열 본문을 가진 200 OK 응답
    public static HttpResponse ok(String body) {
        // 문자열을 UTF-8 바이트 배열로 변환
        // getBytes(charset): 지정된 문자 인코딩으로 바이트 배열 생성
        return ok(body.getBytes(StandardCharsets.UTF_8));
    }

    // 바이트 배열 본문을 가진 200 OK 응답
    public static HttpResponse ok(byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        // Content-Length 헤더 자동 설정
        headers.setContentLength(body.length);
        return new HttpResponse(HttpStatus.OK, headers, body);
    }

    // Content-Type을 지정한 200 OK 응답
    public static HttpResponse ok(String body, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);

        // 문자열을 바이트로 변환하여 길이 계산
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        headers.setContentLength(bodyBytes.length);

        return new HttpResponse(HttpStatus.OK, headers, bodyBytes);
    }

    /**
     * JSON 응답 생성
     */
    public static HttpResponse json(String jsonBody) {
        // application/json Content-Type으로 OK 응답 생성
        // charset=utf-8: 문자 인코딩 명시
        return ok(jsonBody, "application/json; charset=utf-8");
    }

    /**
     * HTML 응답 생성
     */
    public static HttpResponse html(String htmlBody) {
        // text/html Content-Type으로 OK 응답 생성
        return ok(htmlBody, "text/html; charset=utf-8");
    }

    /**
     * 텍스트 응답 생성
     */
    public static HttpResponse text(String textBody) {
        // text/plain Content-Type으로 OK 응답 생성
        return ok(textBody, "text/plain; charset=utf-8");
    }

    /**
     * 201 Created 응답
     */
    public static HttpResponse created() {
        // 본문 없는 201 Created 응답
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
        // 본문이 없는 204 응답 (성공하지만 반환할 내용 없음)
        return new HttpResponse(HttpStatus.NO_CONTENT, new HttpHeaders(), new byte[0]);
    }

    /**
     * 301 Moved Permanently 리다이렉트
     */
    public static HttpResponse movedPermanently(String location) {
        HttpHeaders headers = new HttpHeaders();
        // Location 헤더: 리다이렉트할 URL 지정
        headers.set("Location", location);
        return new HttpResponse(HttpStatus.MOVED_PERMANENTLY, headers, new byte[0]);
    }

    /**
     * 302 Found 리다이렉트
     */
    public static HttpResponse found(String location) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Location", location);
        // 302: 임시 리다이렉트 (원본 URL이 유효함)
        return new HttpResponse(HttpStatus.FOUND, headers, new byte[0]);
    }

    /**
     * 400 Bad Request 에러
     */
    public static HttpResponse badRequest() {
        return badRequest("Bad Request");
    }

    public static HttpResponse badRequest(String message) {
        // 클라이언트 요청에 문제가 있을 때 사용
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
        // WWW-Authenticate 헤더: 인증 방법 지정
        // Basic realm="Server": 기본 인증(ID/PW) 요구
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
        // 서버가 요청을 이해했지만 권한이 없어서 거부
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
        // 요청한 리소스를 찾을 수 없음
        return new HttpResponse(HttpStatus.NOT_FOUND, new HttpHeaders(),
                message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 405 Method Not Allowed 에러
     */
    public static HttpResponse methodNotAllowed(String allowedMethods) {
        HttpHeaders headers = new HttpHeaders();
        // Allow 헤더: 해당 리소스에서 허용되는 HTTP 메서드들
        // 예: "GET, POST, PUT"
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
        // 서버 내부 오류 발생 시 사용
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
        // 서버가 일시적으로 서비스를 제공할 수 없을 때 사용
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

    // 빌더 패턴: 복잡한 객체를 단계별로 생성하는 디자인 패턴
    // HttpResponse.builder(HttpStatus.OK).contentType("text/html").body("content").build()
    public static class Builder {
        private final HttpStatus status;    // 상태 코드 (필수)
        private final HttpHeaders headers;  // 헤더들 (빌더에서 설정)
        private byte[] body;               // 본문 (선택사항)

        public Builder(HttpStatus status) {
            this.status = status;
            this.headers = new HttpHeaders();
            this.body = new byte[0];  // 기본값: 빈 본문
        }

        // 헤더 설정 메서드 (메서드 체이닝 지원)
        public Builder header(String name, String value) {
            headers.set(name, value);
            return this;  // 자기 자신을 반환하여 체이닝 가능
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

        // 본문 설정 메서드
        public Builder body(String body) {
            return body(body.getBytes(StandardCharsets.UTF_8));
        }

        public Builder body(byte[] body) {
            this.body = body != null ? body.clone() : new byte[0];
            // 본문 설정 시 Content-Length 자동 업데이트
            headers.setContentLength(this.body.length);
            return this;
        }

        // 쿠키 설정 메서드 (기본)
        public Builder cookie(String name, String value) {
            // Set-Cookie 헤더 추가 (여러 쿠키 지원을 위해 add 사용)
            headers.add("Set-Cookie", name + "=" + value);
            return this;
        }

        // 만료 시간이 있는 쿠키 설정
        public Builder cookie(String name, String value, int maxAge) {
            // Max-Age: 쿠키 유효 시간(초)
            headers.add("Set-Cookie", name + "=" + value + "; Max-Age=" + maxAge);
            return this;
        }

        // 최종 HttpResponse 객체 생성
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

    // 본문의 복사본 반환 (원본 보호)
    public byte[] getBody() {
        return body.clone();
    }

    public String getBodyAsString() {
        // 본문을 UTF-8 문자열로 변환
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
        return this;  // 메서드 체이닝 지원
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
            // 메모리에 바이트를 쓸 수 있는 스트림
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // HTTP 응답을 스트림에 쓰기
            writeTo(baos);
            // 스트림의 내용을 바이트 배열로 반환
            return baos.toByteArray();
        } catch (IOException e) {
            // ByteArrayOutputStream은 메모리 내 작업이므로 IOException이 거의 발생하지 않음
            // 하지만 인터페이스 제약으로 예외 처리 필요
            throw new RuntimeException("Error creating response bytes", e);
        }
    }

    /**
     * OutputStream에 HTTP 응답 작성
     */
    public void writeTo(OutputStream output) throws IOException {
        // 1. Status Line 작성
        // "HTTP/1.1 200 OK\r\n" 형태
        String statusLine = HTTP_VERSION + " " + status.getCode() + " " + status.getReasonPhrase() + "\r\n";
        output.write(statusLine.getBytes(StandardCharsets.UTF_8));

        // 2. Headers 작성
        // "Header-Name: Header-Value\r\n" 형태들
        output.write(headers.toHeaderString().getBytes(StandardCharsets.UTF_8));

        // 3. 헤더와 본문을 구분하는 빈 줄
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));

        // 4. Body 작성 (있는 경우)
        if (body.length > 0) {
            output.write(body);
        }

        // 출력 스트림 플러시 (버퍼에 있는 데이터를 즉시 전송)
        // flush(): 버퍼링된 데이터를 강제로 출력
        output.flush();
    }

    /**
     * 기본 헤더 설정
     */
    private void setDefaultHeaders() {
        // Date 헤더 - HTTP 응답이 생성된 시간
        if (!headers.contains("Date")) {
            // 현재 시간을 GMT 시간대로 변환하여 HTTP 표준 형식으로 포맷
            // ZonedDateTime.now(): 현재 날짜/시간
            // ZoneId.of("GMT"): GMT 시간대
            // format(): 지정된 형식으로 날짜/시간 포맷팅
            String dateString = ZonedDateTime.now(ZoneId.of("GMT"))
                    .format(HTTP_DATE_FORMAT);
            headers.set("Date", dateString);
        }

        // Server 헤더 - 서버 소프트웨어 정보
        if (!headers.contains("Server")) {
            headers.set("Server", "JavaServerArchitectures/1.0");
        }

        // Content-Length 헤더 (body가 있는 경우)
        if (body.length > 0 && !headers.contains("Content-Length")) {
            headers.setContentLength(body.length);
        }

        // Connection 헤더 기본값 - Keep-Alive 연결 유지
        if (!headers.contains("Connection")) {
            headers.set("Connection", "keep-alive");
        }
    }

    /**
     * 객체의 문자열 표현
     * 디버깅과 로깅에 유용
     */
    @Override
    public String toString() {
        // "HttpResponse{status=200 OK, bodyLength=1024}" 형태
        return String.format("HttpResponse{status=%s, bodyLength=%d}",
                status, body.length);
    }

    /**
     * 객체 동등성 비교
     */
    @Override
    public boolean equals(Object obj) {
        // 같은 객체 참조면 true
        if (this == obj) return true;

        // HttpResponse 타입이 아니면 false
        if (!(obj instanceof HttpResponse)) return false;

        HttpResponse other = (HttpResponse) obj;

        // 모든 필드가 동일한지 확인
        // enum은 == 사용, 객체는 equals() 사용, 배열은 Arrays.equals() 사용
        return status == other.status &&
                headers.equals(other.headers) &&
                java.util.Arrays.equals(body, other.body);
    }

    /**
     * 해시코드 계산
     * equals()와 함께 오버라이드해야 함
     */
    @Override
    public int hashCode() {
        // Objects.hash(): 여러 값의 해시코드를 결합
        // Arrays.hashCode(): 배열의 해시코드 계산
        return Objects.hash(status, headers, java.util.Arrays.hashCode(body));
    }
}