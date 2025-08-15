package server.core.http;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 고성능 HTTP 요청 파서
 * RFC 7230, 7231 준수하는 HTTP/1.1 파싱
 */
public class HttpParser {

    private static final int MAX_REQUEST_LINE_LENGTH = 8192;  // 8KB
    private static final int MAX_HEADER_SIZE = 65536;        // 64KB
    private static final int MAX_HEADERS_COUNT = 100;

    private static final Pattern REQUEST_LINE_PATTERN =
            Pattern.compile("^([A-Z]+)\\s+([^\\s]+)\\s+(HTTP/1\\.[01])$");

    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DOUBLE_CRLF = "\r\n\r\n".getBytes(StandardCharsets.UTF_8);

    /**
     * InputStream에서 HTTP 요청 파싱
     */
    public static HttpRequest parseRequest(InputStream inputStream) throws IOException {
        BufferedInputStream bufferedInput = new BufferedInputStream(inputStream, 8192);

        // 1. Request Line 파싱
        String requestLine = readRequestLine(bufferedInput);
        RequestLineComponents components = parseRequestLine(requestLine);

        // 2. Headers 파싱
        HttpHeaders headers = parseHeaders(bufferedInput);

        // 3. Body 파싱 (필요한 경우)
        byte[] body = parseBody(bufferedInput, headers, components.method);

        return new HttpRequest(components.method, components.uri, components.version,
                headers, body);
    }

    /**
     * Request Line 읽기
     */
    private static String readRequestLine(BufferedInputStream input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        int length = 0;
        boolean foundCR = false;

        while ((b = input.read()) != -1) {
            length++;
            if (length > MAX_REQUEST_LINE_LENGTH) {
                throw new IOException("Request line too long");
            }

            if (b == '\r') {
                foundCR = true;
            } else if (b == '\n' && foundCR) {
                break;
            } else {
                if (foundCR) {
                    baos.write('\r');
                    foundCR = false;
                }
                baos.write(b);
            }
        }

        if (b == -1) {
            throw new IOException("Unexpected end of stream while reading request line");
        }

        return baos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Request Line 파싱
     */
    private static RequestLineComponents parseRequestLine(String requestLine) {
        if (requestLine == null || requestLine.trim().isEmpty()) {
            throw new IllegalArgumentException("Request line cannot be empty");
        }

        String[] parts = requestLine.split("\\s+", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid request line format: " + requestLine);
        }

        // Method 파싱
        HttpMethod method;
        try {
            method = HttpMethod.fromString(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid HTTP method: " + parts[0]);
        }

        // URI 파싱
        String uri = parts[1];
        if (!uri.startsWith("/")) {
            throw new IllegalArgumentException("URI must start with '/': " + uri);
        }

        // HTTP Version 파싱
        String version = parts[2];
        if (!"HTTP/1.0".equals(version) && !"HTTP/1.1".equals(version)) {
            throw new IllegalArgumentException("Unsupported HTTP version: " + version);
        }

        return new RequestLineComponents(method, uri, version);
    }

    /**
     * HTTP 헤더들 파싱
     */
    private static HttpHeaders parseHeaders(BufferedInputStream input) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        String line;
        int headerCount = 0;
        int totalHeaderSize = 0;

        while ((line = readHeaderLine(input)) != null) {
            if (line.isEmpty()) {
                break; // 빈 줄이면 헤더 끝
            }

            headerCount++;
            totalHeaderSize += line.length();

            if (headerCount > MAX_HEADERS_COUNT) {
                throw new IOException("Too many headers");
            }

            if (totalHeaderSize > MAX_HEADER_SIZE) {
                throw new IOException("Headers too large");
            }

            parseHeaderLine(line, headers);
        }

        return headers;
    }

    /**
     * 헤더 라인 읽기
     */
    private static String readHeaderLine(BufferedInputStream input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        boolean foundCR = false;

        while ((b = input.read()) != -1) {
            if (b == '\r') {
                foundCR = true;
            } else if (b == '\n') {
                if (foundCR) {
                    break; // CRLF 발견
                } else {
                    baos.write(b); // LF만 있는 경우는 허용
                }
            } else {
                if (foundCR) {
                    baos.write('\r');
                    foundCR = false;
                }
                baos.write(b);
            }
        }

        if (b == -1 && baos.size() == 0) {
            return null; // EOF
        }

        return baos.toString(StandardCharsets.UTF_8);
    }

    /**
     * 개별 헤더 라인 파싱
     */
    private static void parseHeaderLine(String line, HttpHeaders headers) {
        int colonIndex = line.indexOf(':');
        if (colonIndex == -1) {
            throw new IllegalArgumentException("Invalid header line: " + line);
        }

        String name = line.substring(0, colonIndex).trim();
        String value = line.substring(colonIndex + 1).trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Header name cannot be empty");
        }

        // 헤더 folding 처리 (RFC 7230에서는 deprecated이지만 호환성을 위해)
        headers.add(name, value);
    }

    /**
     * Request Body 파싱
     */
    private static byte[] parseBody(BufferedInputStream input, HttpHeaders headers,
                                    HttpMethod method) throws IOException {

        // GET, HEAD, DELETE는 보통 body가 없음
        if (!method.canHaveBody()) {
            return new byte[0];
        }

        long contentLength = headers.getContentLength();
        if (contentLength == 0) {
            return new byte[0];
        }

        if (contentLength > 0) {
            // Content-Length 기반 읽기
            return readFixedLengthBody(input, contentLength);
        }

        // Transfer-Encoding: chunked 확인
        String transferEncoding = headers.get("Transfer-Encoding");
        if ("chunked".equalsIgnoreCase(transferEncoding)) {
            return readChunkedBody(input);
        }

        // Content-Length도 없고 chunked도 아니면 body 없음
        return new byte[0];
    }

    /**
     * 고정 길이 body 읽기
     */
    private static byte[] readFixedLengthBody(BufferedInputStream input,
                                              long contentLength) throws IOException {
        if (contentLength > Integer.MAX_VALUE) {
            throw new IOException("Request body too large");
        }

        int length = (int) contentLength;
        byte[] body = new byte[length];
        int totalRead = 0;

        while (totalRead < length) {
            int read = input.read(body, totalRead, length - totalRead);
            if (read == -1) {
                throw new IOException("Unexpected end of stream while reading body");
            }
            totalRead += read;
        }

        return body;
    }

    /**
     * Chunked body 읽기 (간단한 구현)
     */
    private static byte[] readChunkedBody(BufferedInputStream input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        while (true) {
            // 청크 크기 읽기
            String chunkSizeLine = readHeaderLine(input);
            if (chunkSizeLine == null) {
                throw new IOException("Unexpected end of chunked body");
            }

            // 청크 크기 파싱 (16진수)
            int chunkSize;
            try {
                String sizeStr = chunkSizeLine.split(";")[0].trim(); // 확장 무시
                chunkSize = Integer.parseInt(sizeStr, 16);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid chunk size: " + chunkSizeLine);
            }

            if (chunkSize == 0) {
                // 마지막 청크
                // 트레일러 헤더들 읽기 (무시)
                String line;
                while ((line = readHeaderLine(input)) != null && !line.isEmpty()) {
                    // 트레일러 헤더 무시
                }
                break;
            }

            // 청크 데이터 읽기
            byte[] chunkData = readFixedLengthBody(input, chunkSize);
            baos.write(chunkData);

            // 청크 끝의 CRLF 읽기
            readHeaderLine(input);
        }

        return baos.toByteArray();
    }

    /**
     * Request Line 컴포넌트 내부 클래스
     */
    private static class RequestLineComponents {
        final HttpMethod method;
        final String uri;
        final String version;

        RequestLineComponents(HttpMethod method, String uri, String version) {
            this.method = method;
            this.uri = uri;
            this.version = version;
        }
    }

    /**
     * 파싱 예외 클래스
     */
    public static class HttpParseException extends IOException {
        public HttpParseException(String message) {
            super(message);
        }

        public HttpParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}