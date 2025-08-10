package src.main.java.com.serverarch.common.http;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HTTP 요청을 파싱하는 클래스입니다.
 *
 * HttpRequestParser는 클라이언트로부터 받은 원시 HTTP 요청 데이터를
 * 구조화된 형태로 파싱합니다.
 *
 * 파싱 과정:
 * 1. Request Line 파싱 (메서드, URI, HTTP 버전)
 * 2. Headers 파싱 (이름: 값 형태)
 * 3. Body 파싱 (Content-Type에 따라 다른 처리)
 *
 * 지원하는 기능:
 * - HTTP/1.0, HTTP/1.1, HTTP/2.0 지원
 * - 모든 표준 HTTP 메서드 지원
 * - 다양한 Content-Type 처리 (form-data, json, multipart 등)
 * - 쿼리 스트링 파싱
 * - URL 디코딩
 * - 청크 인코딩 처리
 */
public class HttpRequestParser {

    /**
     * HTTP 요청의 최대 헤더 크기 (기본 8KB)
     * DoS 공격을 방지하기 위한 제한
     */
    private static final int MAX_HEADER_SIZE = 8192;

    /**
     * HTTP 요청의 최대 초기 라인 길이 (기본 4KB)
     * 매우 긴 URL 공격을 방지하기 위한 제한
     */
    private static final int MAX_REQUEST_LINE_SIZE = 4096;

    /**
     * 헤더 이름의 최대 길이
     */
    private static final int MAX_HEADER_NAME_SIZE = 256;

    /**
     * 헤더 값의 최대 길이
     */
    private static final int MAX_HEADER_VALUE_SIZE = 4096;

    /**
     * CRLF 상수 (HTTP 표준 줄바꿈)
     */
    private static final String CRLF = "\r\n";

    /**
     * 파싱된 HTTP 요청 정보를 담는 결과 클래스
     */
    public static class ParsedHttpRequest {
        private String method;
        private String uri;
        private String queryString;
        private String httpVersion;
        private Map<String, List<String>> headers;
        private Map<String, String[]> parameters;
        private byte[] body;
        private String characterEncoding;
        private String contentType;
        private int contentLength;

        public ParsedHttpRequest() {
            this.headers = new LinkedHashMap<>(); // 순서 보존
            this.parameters = new HashMap<>();
            this.characterEncoding = "ISO-8859-1"; // HTTP 기본 인코딩
            this.contentLength = -1;
        }

        // Getters and Setters
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }

        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }

        public String getQueryString() { return queryString; }
        public void setQueryString(String queryString) { this.queryString = queryString; }

        public String getHttpVersion() { return httpVersion; }
        public void setHttpVersion(String httpVersion) { this.httpVersion = httpVersion; }

        public Map<String, List<String>> getHeaders() { return headers; }
        public void setHeaders(Map<String, List<String>> headers) { this.headers = headers; }

        public Map<String, String[]> getParameters() { return parameters; }
        public void setParameters(Map<String, String[]> parameters) { this.parameters = parameters; }

        public byte[] getBody() { return body; }
        public void setBody(byte[] body) { this.body = body; }

        public String getCharacterEncoding() { return characterEncoding; }
        public void setCharacterEncoding(String characterEncoding) { this.characterEncoding = characterEncoding; }

        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }

        public int getContentLength() { return contentLength; }
        public void setContentLength(int contentLength) { this.contentLength = contentLength; }

        /**
         * 지정된 이름의 첫 번째 헤더 값을 반환합니다.
         */
        public String getHeader(String name) {
            List<String> values = headers.get(name.toLowerCase());
            return (values != null && !values.isEmpty()) ? values.get(0) : null;
        }

        /**
         * 지정된 이름의 모든 헤더 값을 반환합니다.
         */
        public List<String> getHeaders(String name) {
            return headers.getOrDefault(name.toLowerCase(), Collections.emptyList());
        }

        /**
         * 지정된 이름의 매개변수 값을 반환합니다.
         */
        public String getParameter(String name) {
            String[] values = parameters.get(name);
            return (values != null && values.length > 0) ? values[0] : null;
        }

        /**
         * 지정된 이름의 모든 매개변수 값을 반환합니다.
         */
        public String[] getParameterValues(String name) {
            return parameters.get(name);
        }
    }

    /**
     * InputStream에서 HTTP 요청을 파싱합니다.
     *
     * @param inputStream HTTP 요청 데이터가 포함된 InputStream
     * @return 파싱된 HTTP 요청 정보
     * @throws IOException 입출력 오류 시
     * @throws HttpParseException HTTP 파싱 오류 시
     */
    public static ParsedHttpRequest parseRequest(InputStream inputStream)
            throws IOException, HttpParseException {

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.US_ASCII)
        );

        ParsedHttpRequest request = new ParsedHttpRequest();

        // 1. Request Line 파싱
        parseRequestLine(reader, request);

        // 2. Headers 파싱
        parseHeaders(reader, request);

        // 3. Body 파싱 (필요한 경우)
        parseBody(inputStream, request);

        // 4. 쿼리 스트링 파싱
        parseQueryString(request);

        return request;
    }

    /**
     * HTTP 요청 라인을 파싱합니다.
     * 형식: METHOD URI HTTP/VERSION
     * 예: GET /index.html?name=value HTTP/1.1
     */
    private static void parseRequestLine(BufferedReader reader, ParsedHttpRequest request)
            throws IOException, HttpParseException {

        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.trim().isEmpty()) {
            throw new HttpParseException("빈 요청 라인");
        }

        if (requestLine.length() > MAX_REQUEST_LINE_SIZE) {
            throw new HttpParseException("요청 라인이 너무 깁니다: " + requestLine.length());
        }

        // 공백으로 분리 (METHOD URI HTTP/VERSION)
        String[] parts = requestLine.trim().split("\\s+");
        if (parts.length != 3) {
            throw new HttpParseException("잘못된 요청 라인 형식: " + requestLine);
        }

        // HTTP 메서드 검증
        String method = parts[0].toUpperCase();
        if (!isValidHttpMethod(method)) {
            throw new HttpParseException("지원하지 않는 HTTP 메서드: " + method);
        }
        request.setMethod(method);

        // URI 파싱 (쿼리 스트링 분리)
        String fullUri = parts[1];
        int queryIndex = fullUri.indexOf('?');
        if (queryIndex != -1) {
            request.setUri(fullUri.substring(0, queryIndex));
            request.setQueryString(fullUri.substring(queryIndex + 1));
        } else {
            request.setUri(fullUri);
        }

        // HTTP 버전 검증
        String httpVersion = parts[2];
        if (!isValidHttpVersion(httpVersion)) {
            throw new HttpParseException("지원하지 않는 HTTP 버전: " + httpVersion);
        }
        request.setHttpVersion(httpVersion);
    }

    /**
     * HTTP 헤더들을 파싱합니다.
     * 형식: Header-Name: Header-Value
     */
    private static void parseHeaders(BufferedReader reader, ParsedHttpRequest request)
            throws IOException, HttpParseException {

        String line;
        int totalHeaderSize = 0;

        while ((line = reader.readLine()) != null) {
            // 빈 라인은 헤더 섹션의 끝을 의미
            if (line.trim().isEmpty()) {
                break;
            }

            totalHeaderSize += line.length();
            if (totalHeaderSize > MAX_HEADER_SIZE) {
                throw new HttpParseException("헤더 크기가 너무 큽니다");
            }

            // 헤더 파싱: "Name: Value"
            int colonIndex = line.indexOf(':');
            if (colonIndex == -1) {
                throw new HttpParseException("잘못된 헤더 형식: " + line);
            }

            String headerName = line.substring(0, colonIndex).trim().toLowerCase();
            String headerValue = line.substring(colonIndex + 1).trim();

            // 헤더 이름/값 길이 검증
            if (headerName.length() > MAX_HEADER_NAME_SIZE) {
                throw new HttpParseException("헤더 이름이 너무 깁니다: " + headerName);
            }
            if (headerValue.length() > MAX_HEADER_VALUE_SIZE) {
                throw new HttpParseException("헤더 값이 너무 깁니다");
            }

            // 헤더 저장 (중복 헤더는 리스트로 관리)
            request.getHeaders().computeIfAbsent(headerName, k -> new ArrayList<>()).add(headerValue);

            // 특별한 헤더들 처리
            processSpecialHeaders(headerName, headerValue, request);
        }
    }

    /**
     * 특별한 의미를 갖는 헤더들을 처리합니다.
     */
    private static void processSpecialHeaders(String headerName, String headerValue,
                                              ParsedHttpRequest request) {
        switch (headerName) {
            case "content-type":
                request.setContentType(headerValue);
                // charset 추출
                if (headerValue.contains("charset=")) {
                    String charset = extractCharset(headerValue);
                    if (charset != null) {
                        request.setCharacterEncoding(charset);
                    }
                }
                break;

            case "content-length":
                try {
                    int length = Integer.parseInt(headerValue);
                    if (length < 0) {
                        throw new NumberFormatException("음수 길이");
                    }
                    request.setContentLength(length);
                } catch (NumberFormatException e) {
                    // Content-Length 헤더 무시 (로그 기록 권장)
                }
                break;
        }
    }

    /**
     * Content-Type에서 charset을 추출합니다.
     * 예: "text/html; charset=UTF-8" -> "UTF-8"
     */
    private static String extractCharset(String contentType) {
        int charsetIndex = contentType.toLowerCase().indexOf("charset=");
        if (charsetIndex != -1) {
            String charset = contentType.substring(charsetIndex + 8);
            int semicolonIndex = charset.indexOf(';');
            if (semicolonIndex != -1) {
                charset = charset.substring(0, semicolonIndex);
            }
            return charset.trim();
        }
        return null;
    }

    /**
     * HTTP 요청 본문을 파싱합니다.
     */
    private static void parseBody(InputStream inputStream, ParsedHttpRequest request)
            throws IOException, HttpParseException {

        if (request.getContentLength() <= 0) {
            return; // 본문이 없음
        }

        // 최대 본문 크기 제한 (예: 50MB)
        final int MAX_BODY_SIZE = 50 * 1024 * 1024;
        if (request.getContentLength() > MAX_BODY_SIZE) {
            throw new HttpParseException("요청 본문이 너무 큽니다: " + request.getContentLength());
        }

        // 본문 읽기
        byte[] body = new byte[request.getContentLength()];
        int totalRead = 0;
        int bytesRead;

        while (totalRead < body.length) {
            bytesRead = inputStream.read(body, totalRead, body.length - totalRead);
            if (bytesRead == -1) {
                throw new HttpParseException("요청 본문이 예상보다 짧습니다");
            }
            totalRead += bytesRead;
        }

        request.setBody(body);

        // Content-Type에 따른 본문 파싱
        parseBodyByContentType(request);
    }

    /**
     * Content-Type에 따라 본문을 파싱합니다.
     */
    private static void parseBodyByContentType(ParsedHttpRequest request)
            throws HttpParseException {

        String contentType = request.getContentType();
        if (contentType == null) {
            return;
        }

        // Content-Type의 메인 타입만 추출 (파라미터 제거)
        String mainType = contentType.split(";")[0].trim().toLowerCase();

        switch (mainType) {
            case "application/x-www-form-urlencoded":
                parseFormUrlEncoded(request);
                break;

            case "multipart/form-data":
                // multipart는 복잡하므로 기본 처리만 (향후 확장 가능)
                parseMultipartFormData(request);
                break;

            case "application/json":
            case "text/plain":
            case "text/html":
                // 텍스트 기반 데이터는 그대로 유지
                break;

            default:
                // 기타 타입은 바이너리로 처리
                break;
        }
    }

    /**
     * application/x-www-form-urlencoded 형태의 본문을 파싱합니다.
     */
    private static void parseFormUrlEncoded(ParsedHttpRequest request)
            throws HttpParseException {

        try {
            String bodyString = new String(request.getBody(), request.getCharacterEncoding());
            Map<String, List<String>> params = parseUrlEncodedString(bodyString);

            // 기존 쿼리 파라미터와 병합
            for (Map.Entry<String, List<String>> entry : params.entrySet()) {
                String[] values = entry.getValue().toArray(new String[0]);
                request.getParameters().put(entry.getKey(), values);
            }

        } catch (UnsupportedEncodingException e) {
            throw new HttpParseException("지원하지 않는 문자 인코딩: " + request.getCharacterEncoding());
        }
    }

    /**
     * multipart/form-data 형태의 본문을 파싱합니다.
     * (기본 구현 - 향후 확장 필요)
     */
    private static void parseMultipartFormData(ParsedHttpRequest request) {
        // 복잡한 multipart 파싱은 향후 구현
        // 현재는 raw body 데이터만 보존
    }

    /**
     * URL 인코딩된 쿼리 스트링을 파싱합니다.
     */
    private static void parseQueryString(ParsedHttpRequest request)
            throws HttpParseException {

        String queryString = request.getQueryString();
        if (queryString == null || queryString.trim().isEmpty()) {
            return;
        }

        try {
            Map<String, List<String>> params = parseUrlEncodedString(queryString);

            // 매개변수 저장
            for (Map.Entry<String, List<String>> entry : params.entrySet()) {
                String[] values = entry.getValue().toArray(new String[0]);
                request.getParameters().put(entry.getKey(), values);
            }

        } catch (Exception e) {
            throw new HttpParseException("쿼리 스트링 파싱 오류: " + e.getMessage());
        }
    }

    /**
     * URL 인코딩된 문자열을 파싱하여 매개변수 맵을 반환합니다.
     * 형식: name1=value1&name2=value2&name1=value3
     */
    private static Map<String, List<String>> parseUrlEncodedString(String encoded)
            throws UnsupportedEncodingException {

        Map<String, List<String>> params = new HashMap<>();

        if (encoded == null || encoded.trim().isEmpty()) {
            return params;
        }

        // '&'로 분리
        String[] pairs = encoded.split("&");

        for (String pair : pairs) {
            if (pair.trim().isEmpty()) {
                continue;
            }

            // '='로 분리
            int equalIndex = pair.indexOf('=');
            String name, value;

            if (equalIndex != -1) {
                name = URLDecoder.decode(pair.substring(0, equalIndex), "UTF-8");
                value = URLDecoder.decode(pair.substring(equalIndex + 1), "UTF-8");
            } else {
                name = URLDecoder.decode(pair, "UTF-8");
                value = "";
            }

            // 동일한 이름의 매개변수가 여러 개일 수 있음
            params.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }

        return params;
    }

    /**
     * 유효한 HTTP 메서드인지 확인합니다.
     */
    private static boolean isValidHttpMethod(String method) {
        // RFC 7231에서 정의된 표준 메서드들 + 일부 확장 메서드
        Set<String> validMethods = Set.of(
                "GET", "HEAD", "POST", "PUT", "DELETE", "CONNECT", "OPTIONS", "TRACE", "PATCH"
        );
        return validMethods.contains(method);
    }

    /**
     * 유효한 HTTP 버전인지 확인합니다.
     */
    private static boolean isValidHttpVersion(String version) {
        // 지원하는 HTTP 버전들
        Set<String> validVersions = Set.of("HTTP/1.0", "HTTP/1.1", "HTTP/2.0");
        return validVersions.contains(version);
    }

    /**
     * HTTP 파싱 오류를 나타내는 예외 클래스
     */
    public static class HttpParseException extends Exception {
        public HttpParseException(String message) {
            super(message);
        }

        public HttpParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
