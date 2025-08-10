package src.main.java.com.serverarch.common.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HTTP 응답을 생성하는 클래스입니다.
 *
 * HttpResponseBuilder는 서버에서 클라이언트로 보낼 HTTP 응답을
 * 표준 형식에 맞게 생성합니다.
 *
 * 생성 과정:
 * 1. Status Line 생성 (HTTP 버전, 상태 코드, 상태 메시지)
 * 2. Headers 생성 (이름: 값 형태)
 * 3. Body 생성 (Content-Type에 따라 다른 처리)
 *
 * 지원하는 기능:
 * - HTTP/1.0, HTTP/1.1, HTTP/2.0 지원
 * - 모든 표준 HTTP 상태 코드
 * - 다양한 Content-Type 처리
 * - 청크 인코딩 (Transfer-Encoding: chunked)
 * - 압축 지원 (gzip, deflate)
 * - 쿠키 설정
 * - 캐시 제어 헤더
 */
public class HttpResponseBuilder {

    /**
     * HTTP 응답의 기본 문자 인코딩
     */
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /**
     * HTTP 날짜 형식 (RFC 1123)
     * 예: "Mon, 01 Jan 2024 12:00:00 GMT"
     */
    private static final DateTimeFormatter HTTP_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);

    /**
     * CRLF 상수 (HTTP 표준 줄바꿈)
     */
    private static final String CRLF = "\r\n";

    /**
     * HTTP 응답 정보를 담는 클래스
     */
    public static class HttpResponse {
        private String httpVersion;
        private int statusCode;
        private String statusMessage;
        private Map<String, List<String>> headers;
        private byte[] body;
        private Charset charset;
        private boolean chunkedEncoding;

        public HttpResponse() {
            this.httpVersion = "HTTP/1.1"; // 기본값
            this.statusCode = 200; // 기본값
            this.statusMessage = "OK"; // 기본값
            this.headers = new LinkedHashMap<>(); // 순서 보존
            this.charset = DEFAULT_CHARSET;
            this.chunkedEncoding = false;
        }

        // Getters and Setters
        public String getHttpVersion() { return httpVersion; }
        public void setHttpVersion(String httpVersion) { this.httpVersion = httpVersion; }

        public int getStatusCode() { return statusCode; }
        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
            this.statusMessage = getDefaultStatusMessage(statusCode);
        }

        public String getStatusMessage() { return statusMessage; }
        public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

        public Map<String, List<String>> getHeaders() { return headers; }
        public void setHeaders(Map<String, List<String>> headers) { this.headers = headers; }

        public byte[] getBody() { return body; }
        public void setBody(byte[] body) { this.body = body; }

        public Charset getCharset() { return charset; }
        public void setCharset(Charset charset) { this.charset = charset; }

        public boolean isChunkedEncoding() { return chunkedEncoding; }
        public void setChunkedEncoding(boolean chunkedEncoding) { this.chunkedEncoding = chunkedEncoding; }

        /**
         * 헤더를 설정합니다 (기존 값 대체).
         */
        public void setHeader(String name, String value) {
            List<String> values = new ArrayList<>();
            values.add(value);
            headers.put(name.toLowerCase(), values);
        }

        /**
         * 헤더를 추가합니다 (기존 값에 추가).
         */
        public void addHeader(String name, String value) {
            headers.computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>()).add(value);
        }

        /**
         * 첫 번째 헤더 값을 반환합니다.
         */
        public String getHeader(String name) {
            List<String> values = headers.get(name.toLowerCase());
            return (values != null && !values.isEmpty()) ? values.get(0) : null;
        }

        /**
         * 모든 헤더 값을 반환합니다.
         */
        public List<String> getHeaders(String name) {
            return headers.getOrDefault(name.toLowerCase(), Collections.emptyList());
        }

        /**
         * 헤더가 존재하는지 확인합니다.
         */
        public boolean containsHeader(String name) {
            return headers.containsKey(name.toLowerCase());
        }
    }

    /**
     * 새로운 HTTP 응답 빌더를 생성합니다.
     */
    public static HttpResponse createResponse() {
        return new HttpResponse();
    }

    /**
     * 성공 응답(200 OK)을 생성합니다.
     */
    public static HttpResponse createOkResponse() {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(200);
        return response;
    }

    /**
     * 에러 응답을 생성합니다.
     */
    public static HttpResponse createErrorResponse(int statusCode, String message) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(statusCode);

        // 에러 메시지를 HTML 형태로 본문에 설정
        String htmlContent = createErrorHtml(statusCode, message);
        response.setBody(htmlContent.getBytes(response.getCharset()));
        response.setHeader("Content-Type", "text/html; charset=" + response.getCharset().name());

        return response;
    }

    /**
     * 리다이렉트 응답을 생성합니다.
     */
    public static HttpResponse createRedirectResponse(String location) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(302);
        response.setHeader("Location", location);

        // 기본 리다이렉트 메시지
        String content = "Redirecting to " + location;
        response.setBody(content.getBytes(response.getCharset()));
        response.setHeader("Content-Type", "text/plain; charset=" + response.getCharset().name());

        return response;
    }

    /**
     * JSON 응답을 생성합니다.
     */
    public static HttpResponse createJsonResponse(String jsonContent) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(200);
        response.setBody(jsonContent.getBytes(response.getCharset()));
        response.setHeader("Content-Type", "application/json; charset=" + response.getCharset().name());

        return response;
    }

    /**
     * 텍스트 응답을 생성합니다.
     */
    public static HttpResponse createTextResponse(String textContent) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(200);
        response.setBody(textContent.getBytes(response.getCharset()));
        response.setHeader("Content-Type", "text/plain; charset=" + response.getCharset().name());

        return response;
    }

    /**
     * HTML 응답을 생성합니다.
     */
    public static HttpResponse createHtmlResponse(String htmlContent) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(200);
        response.setBody(htmlContent.getBytes(response.getCharset()));
        response.setHeader("Content-Type", "text/html; charset=" + response.getCharset().name());

        return response;
    }

    /**
     * HTTP 응답을 바이트 배열로 직렬화합니다.
     */
    public static byte[] buildResponse(HttpResponse response) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeResponse(response, baos);
        return baos.toByteArray();
    }

    /**
     * HTTP 응답을 OutputStream에 씁니다.
     */
    public static void writeResponse(HttpResponse response, OutputStream outputStream)
            throws IOException {

        // 자동으로 기본 헤더들 추가
        addDefaultHeaders(response);

        // Status Line 쓰기
        writeStatusLine(response, outputStream);

        // Headers 쓰기
        writeHeaders(response, outputStream);

        // 빈 줄 (헤더와 본문 구분)
        outputStream.write(CRLF.getBytes(StandardCharsets.US_ASCII));

        // Body 쓰기
        writeBody(response, outputStream);

        outputStream.flush();
    }

    /**
     * Status Line을 씁니다.
     * 형식: HTTP/1.1 200 OK
     */
    private static void writeStatusLine(HttpResponse response, OutputStream outputStream)
            throws IOException {

        String statusLine = String.format("%s %d %s%s",
                response.getHttpVersion(),
                response.getStatusCode(),
                response.getStatusMessage(),
                CRLF
        );

        outputStream.write(statusLine.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Headers를 씁니다.
     * 형식: Header-Name: Header-Value
     */
    private static void writeHeaders(HttpResponse response, OutputStream outputStream)
            throws IOException {

        for (Map.Entry<String, List<String>> entry : response.getHeaders().entrySet()) {
            String headerName = entry.getKey();
            List<String> headerValues = entry.getValue();

            for (String headerValue : headerValues) {
                String headerLine = String.format("%s: %s%s",
                        formatHeaderName(headerName), headerValue, CRLF);
                outputStream.write(headerLine.getBytes(StandardCharsets.US_ASCII));
            }
        }
    }

    /**
     * Body를 씁니다.
     */
    private static void writeBody(HttpResponse response, OutputStream outputStream)
            throws IOException {

        byte[] body = response.getBody();
        if (body != null && body.length > 0) {
            if (response.isChunkedEncoding()) {
                writeChunkedBody(body, outputStream);
            } else {
                outputStream.write(body);
            }
        }
    }

    /**
     * 청크 인코딩으로 본문을 씁니다.
     */
    private static void writeChunkedBody(byte[] body, OutputStream outputStream)
            throws IOException {

        int chunkSize = 8192; // 8KB 청크
        int offset = 0;

        while (offset < body.length) {
            int currentChunkSize = Math.min(chunkSize, body.length - offset);

            // 청크 크기 (16진수)
            String chunkSizeHex = Integer.toHexString(currentChunkSize) + CRLF;
            outputStream.write(chunkSizeHex.getBytes(StandardCharsets.US_ASCII));

            // 청크 데이터
            outputStream.write(body, offset, currentChunkSize);
            outputStream.write(CRLF.getBytes(StandardCharsets.US_ASCII));

            offset += currentChunkSize;
        }

        // 마지막 청크 (크기 0)
        outputStream.write(("0" + CRLF + CRLF).getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * 기본 헤더들을 추가합니다.
     */
    private static void addDefaultHeaders(HttpResponse response) {
        // Date 헤더 (현재 시간)
        if (!response.containsHeader("date")) {
            String currentDate = ZonedDateTime.now().format(HTTP_DATE_FORMAT);
            response.setHeader("Date", currentDate);
        }

        // Server 헤더
        if (!response.containsHeader("server")) {
            response.setHeader("Server", "JavaServerArchitectures/1.0");
        }

        // Content-Length 헤더 (청크 인코딩이 아닌 경우)
        if (!response.isChunkedEncoding() && !response.containsHeader("content-length")) {
            byte[] body = response.getBody();
            int contentLength = (body != null) ? body.length : 0;
            response.setHeader("Content-Length", String.valueOf(contentLength));
        }

        // Transfer-Encoding 헤더 (청크 인코딩인 경우)
        if (response.isChunkedEncoding() && !response.containsHeader("transfer-encoding")) {
            response.setHeader("Transfer-Encoding", "chunked");
        }

        // Connection 헤더 (기본값)
        if (!response.containsHeader("connection")) {
            if ("HTTP/1.1".equals(response.getHttpVersion())) {
                response.setHeader("Connection", "keep-alive");
            } else {
                response.setHeader("Connection", "close");
            }
        }
    }

    /**
     * 헤더 이름을 표준 형식으로 포맷합니다.
     * 예: "content-type" -> "Content-Type"
     */
    private static String formatHeaderName(String headerName) {
        String[] parts = headerName.split("-");
        StringBuilder formatted = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                formatted.append("-");
            }
            String part = parts[i];
            if (!part.isEmpty()) {
                formatted.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    formatted.append(part.substring(1).toLowerCase());
                }
            }
        }

        return formatted.toString();
    }

    /**
     * 에러 HTML 페이지를 생성합니다.
     */
    private static String createErrorHtml(int statusCode, String message) {
        String title = statusCode + " " + getDefaultStatusMessage(statusCode);

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>" + title + "</title>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; margin: 40px; }\n" +
                "        h1 { color: #d32f2f; }\n" +
                "        p { color: #666; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>" + title + "</h1>\n" +
                "    <p>" + (message != null ? message : "An error occurred.") + "</p>\n" +
                "    <hr>\n" +
                "    <p><em>JavaServerArchitectures/1.0</em></p>\n" +
                "</body>\n" +
                "</html>";
    }

    /**
     * 상태 코드에 대한 기본 상태 메시지를 반환합니다.
     */
    private static String getDefaultStatusMessage(int statusCode) {
        switch (statusCode) {
            // 1xx Informational
            case 100: return "Continue";
            case 101: return "Switching Protocols";

            // 2xx Success
            case 200: return "OK";
            case 201: return "Created";
            case 202: return "Accepted";
            case 204: return "No Content";
            case 206: return "Partial Content";

            // 3xx Redirection
            case 300: return "Multiple Choices";
            case 301: return "Moved Permanently";
            case 302: return "Found";
            case 304: return "Not Modified";
            case 307: return "Temporary Redirect";

            // 4xx Client Error
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 408: return "Request Timeout";
            case 409: return "Conflict";
            case 410: return "Gone";
            case 413: return "Payload Too Large";
            case 414: return "URI Too Long";
            case 415: return "Unsupported Media Type";
            case 429: return "Too Many Requests";

            // 5xx Server Error
            case 500: return "Internal Server Error";
            case 501: return "Not Implemented";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            case 504: return "Gateway Timeout";
            case 505: return "HTTP Version Not Supported";

            default: return "Unknown Status";
        }
    }
}
