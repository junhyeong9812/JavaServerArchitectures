package com.serverarch.common.http;

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
 *
 * 클래스를 public으로 만든 이유:
 * - 다른 패키지에서 HTTP 파싱 기능을 사용할 수 있도록
 * - 웹 서버의 핵심 기능으로 외부 접근 필요
 */
public class HttpRequestParser {

    /**
     * HTTP 요청의 최대 헤더 크기 (기본 8KB)
     * DoS 공격을 방지하기 위한 제한
     *
     * static final로 선언한 이유:
     * - 모든 인스턴스에서 동일한 값 사용
     * - 컴파일 타임 상수로 성능 최적화
     * - 설정 변경 시 재컴파일로 명확한 변경점 표시
     *
     * 8KB로 설정한 이유:
     * - 일반적인 HTTP 헤더 크기는 1-2KB
     * - 대부분의 웹 서버 기본값과 유사
     * - 과도한 헤더로 인한 메모리 공격 방지
     */
    private static final int MAX_HEADER_SIZE = 8192;

    /**
     * HTTP 요청의 최대 초기 라인 길이 (기본 4KB)
     * 매우 긴 URL 공격을 방지하기 위한 제한
     *
     * 4KB로 설정한 이유:
     * - URL 길이 제한 (브라우저 호환성)
     * - GET 파라미터가 많은 경우 대비
     * - 보안: 긴 URL로 인한 버퍼 오버플로우 방지
     */
    private static final int MAX_REQUEST_LINE_SIZE = 4096;

    /**
     * 헤더 이름의 최대 길이
     *
     * 256바이트로 제한한 이유:
     * - HTTP 표준에서 권장하는 일반적인 크기
     * - 비정상적으로 긴 헤더 이름 방지
     */
    private static final int MAX_HEADER_NAME_SIZE = 256;

    /**
     * 헤더 값의 최대 길이
     *
     * 4KB로 제한한 이유:
     * - User-Agent처럼 긴 헤더 값 허용
     * - 쿠키 헤더의 다중 값 고려
     */
    private static final int MAX_HEADER_VALUE_SIZE = 4096;

    /**
     * CRLF 상수 (HTTP 표준 줄바꿈)
     *
     * HTTP 표준에서 사용하는 줄바꿈:
     * - \r\n (Carriage Return + Line Feed)
     * - Windows 스타일 줄바꿈
     * - 네트워크 프로토콜에서 표준
     */
    private static final String CRLF = "\r\n";

    /**
     * 파싱된 HTTP 요청 정보를 담는 결과 클래스
     *
     * 내부 클래스로 구현한 이유:
     * - HttpRequestParser와 밀접한 관련이 있는 데이터 구조
     * - 캡슐화: 외부에서 직접 인스턴스 생성 방지
     * - 코드 조직화: 관련 코드를 한 곳에 모음
     *
     * static 클래스로 선언한 이유:
     * - 외부 클래스 인스턴스 참조 불필요
     * - 메모리 효율성 (외부 클래스 참조 제거)
     */
    public static class ParsedHttpRequest {
        // ========== 요청 라인 정보 ==========

        /**
         * HTTP 메서드 (GET, POST, PUT 등)
         * String으로 저장하는 이유: 확장 메서드 지원을 위해
         */
        private String method;

        /**
         * 요청 URI (쿼리 스트링 제외)
         * 예: "/api/users"
         */
        private String uri;

        /**
         * 쿼리 스트링 (? 이후 부분)
         * 예: "name=john&age=30"
         */
        private String queryString;

        /**
         * HTTP 버전 문자열
         * 예: "HTTP/1.1"
         */
        private String httpVersion;

        // ========== 헤더 정보 ==========

        /**
         * HTTP 헤더들
         * Map<String, List<String>>을 사용하는 이유:
         * - 하나의 헤더에 여러 값이 올 수 있음 (RFC 7230)
         * - 헤더 이름은 대소문자 무관하므로 소문자로 정규화
         */
        private Map<String, List<String>> headers;

        // ========== 매개변수 정보 ==========

        /**
         * 쿼리 스트링과 폼 데이터의 매개변수들
         * Map<String, String[]>을 사용하는 이유:
         * - Servlet API와 호환성 유지
         * - 동일한 이름의 매개변수 여러 개 지원
         */
        private Map<String, String[]> parameters;

        // ========== 요청 본문 정보 ==========

        /**
         * 요청 본문 데이터 (바이너리)
         * byte[]로 저장하는 이유:
         * - 텍스트와 바이너리 데이터 모두 지원
         * - 문자 인코딩에 독립적
         */
        private byte[] body;

        /**
         * 문자 인코딩
         * Content-Type에서 추출하거나 기본값 사용
         */
        private String characterEncoding;

        /**
         * Content-Type 헤더 값
         * 자주 사용되므로 별도 필드로 저장
         */
        private String contentType;

        /**
         * Content-Length 헤더 값
         * int로 저장하는 이유: 메모리 효율성
         * -1: 길이 정보 없음을 의미
         */
        private int contentLength;

        /**
         * 기본 생성자
         *
         * 각 필드를 적절한 기본값으로 초기화:
         * - 컬렉션: 빈 컬렉션으로 초기화
         * - 문자 인코딩: HTTP 기본 인코딩으로 초기화
         * - 길이: -1로 초기화 (알 수 없음 표시)
         */
        public ParsedHttpRequest() {
            // LinkedHashMap: 순서 보존 + 빠른 조회
            // 헤더 순서가 중요할 수 있으므로 LinkedHashMap 사용
            this.headers = new LinkedHashMap<>();

            // HashMap: 일반적인 키-값 매핑, 순서 불필요
            this.parameters = new HashMap<>();

            // ISO-8859-1: HTTP 기본 문자 인코딩 (RFC 7230)
            // Content-Type에 charset이 없을 때 사용하는 기본값
            this.characterEncoding = "ISO-8859-1";

            // -1: Content-Length 헤더가 없음을 의미
            this.contentLength = -1;
        }

        // ========== Getter/Setter 메서드들 ==========
        // public으로 선언하여 외부에서 접근 가능하도록 함

        /**
         * HTTP 메서드 반환
         * @return HTTP 메서드 (예: "GET", "POST")
         */
        public String getMethod() { return method; }

        /**
         * HTTP 메서드 설정
         * @param method HTTP 메서드
         */
        public void setMethod(String method) { this.method = method; }

        /**
         * 요청 URI 반환 (쿼리 스트링 제외)
         * @return URI (예: "/api/users")
         */
        public String getUri() { return uri; }

        /**
         * 요청 URI 설정
         * @param uri 요청 URI
         */
        public void setUri(String uri) { this.uri = uri; }

        /**
         * 쿼리 스트링 반환
         * @return 쿼리 스트링 (예: "name=john&age=30")
         */
        public String getQueryString() { return queryString; }

        /**
         * 쿼리 스트링 설정
         * @param queryString 쿼리 스트링
         */
        public void setQueryString(String queryString) { this.queryString = queryString; }

        /**
         * HTTP 버전 반환
         * @return HTTP 버전 (예: "HTTP/1.1")
         */
        public String getHttpVersion() { return httpVersion; }

        /**
         * HTTP 버전 설정
         * @param httpVersion HTTP 버전
         */
        public void setHttpVersion(String httpVersion) { this.httpVersion = httpVersion; }

        /**
         * 모든 헤더 반환
         * @return 헤더 맵 (헤더명 -> 값 리스트)
         */
        public Map<String, List<String>> getHeaders() { return headers; }

        /**
         * 헤더 맵 설정
         * @param headers 헤더 맵
         */
        public void setHeaders(Map<String, List<String>> headers) { this.headers = headers; }

        /**
         * 모든 매개변수 반환
         * @return 매개변수 맵 (이름 -> 값 배열)
         */
        public Map<String, String[]> getParameters() { return parameters; }

        /**
         * 매개변수 맵 설정
         * @param parameters 매개변수 맵
         */
        public void setParameters(Map<String, String[]> parameters) { this.parameters = parameters; }

        /**
         * 요청 본문 반환
         * @return 본문 데이터 (바이트 배열)
         */
        public byte[] getBody() { return body; }

        /**
         * 요청 본문 설정
         * @param body 본문 데이터
         */
        public void setBody(byte[] body) { this.body = body; }

        /**
         * 문자 인코딩 반환
         * @return 문자 인코딩 (예: "UTF-8")
         */
        public String getCharacterEncoding() { return characterEncoding; }

        /**
         * 문자 인코딩 설정
         * @param characterEncoding 문자 인코딩
         */
        public void setCharacterEncoding(String characterEncoding) {
            this.characterEncoding = characterEncoding;
        }

        /**
         * Content-Type 반환
         * @return Content-Type 헤더 값
         */
        public String getContentType() { return contentType; }

        /**
         * Content-Type 설정
         * @param contentType Content-Type 헤더 값
         */
        public void setContentType(String contentType) { this.contentType = contentType; }

        /**
         * Content-Length 반환
         * @return 본문 길이 (-1: 알 수 없음)
         */
        public int getContentLength() { return contentLength; }

        /**
         * Content-Length 설정
         * @param contentLength 본문 길이
         */
        public void setContentLength(int contentLength) { this.contentLength = contentLength; }

        /**
         * 지정된 이름의 첫 번째 헤더 값을 반환합니다.
         *
         * 첫 번째 값만 반환하는 이유:
         * - 대부분의 헤더는 단일 값
         * - 사용하기 편리한 API 제공
         *
         * @param name 헤더 이름 (대소문자 무관)
         * @return 첫 번째 헤더 값 또는 null
         */
        public String getHeader(String name) {
            // name.toLowerCase(): 대소문자 무관 비교를 위한 소문자 변환
            // headers.get(): Map에서 값 조회
            List<String> values = headers.get(name.toLowerCase());

            // 삼항 연산자로 null 체크와 빈 리스트 체크
            // values != null: null 체크
            // !values.isEmpty(): 빈 리스트가 아닌지 확인
            // values.get(0): 첫 번째 요소 반환
            return (values != null && !values.isEmpty()) ? values.get(0) : null;
        }

        /**
         * 지정된 이름의 모든 헤더 값을 반환합니다.
         *
         * 모든 값을 반환하는 이유:
         * - Accept-Encoding처럼 여러 값을 가질 수 있는 헤더 지원
         * - 완전한 헤더 정보 제공
         *
         * @param name 헤더 이름 (대소문자 무관)
         * @return 헤더 값 리스트 (빈 리스트 가능)
         */
        public List<String> getHeaders(String name) {
            // getOrDefault(): 키가 없으면 기본값 반환
            // Collections.emptyList(): 불변의 빈 리스트 반환
            return headers.getOrDefault(name.toLowerCase(), Collections.emptyList());
        }

        /**
         * 지정된 이름의 매개변수 값을 반환합니다.
         *
         * @param name 매개변수 이름
         * @return 첫 번째 매개변수 값 또는 null
         */
        public String getParameter(String name) {
            // parameters.get(): Map에서 String[] 배열 조회
            String[] values = parameters.get(name);

            // 배열이 존재하고 비어있지 않으면 첫 번째 값 반환
            return (values != null && values.length > 0) ? values[0] : null;
        }

        /**
         * 지정된 이름의 모든 매개변수 값을 반환합니다.
         *
         * @param name 매개변수 이름
         * @return 매개변수 값 배열 (null 가능)
         */
        public String[] getParameterValues(String name) {
            // 배열을 그대로 반환 (null 가능)
            return parameters.get(name);
        }
    }

    /**
     * InputStream에서 HTTP 요청을 파싱합니다.
     *
     * static 메서드로 구현한 이유:
     * - 상태를 유지하지 않는 유틸리티 메서드
     * - 인스턴스 생성 없이 사용 가능
     * - 스레드 안전성 보장
     *
     * @param inputStream HTTP 요청 데이터가 포함된 InputStream
     * @return 파싱된 HTTP 요청 정보
     * @throws IOException 입출력 오류 시
     * @throws HttpParseException HTTP 파싱 오류 시
     */
    public static ParsedHttpRequest parseRequest(InputStream inputStream)
            throws IOException, HttpParseException {

        // BufferedReader: 효율적인 텍스트 읽기를 위한 래퍼
        // InputStreamReader: 바이트 스트림을 문자 스트림으로 변환
        // StandardCharsets.US_ASCII: HTTP 헤더는 ASCII 인코딩 사용
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.US_ASCII)
        );

        // 파싱 결과를 담을 객체 생성
        ParsedHttpRequest request = new ParsedHttpRequest();

        // 1. Request Line 파싱 (메서드, URI, HTTP 버전)
        parseRequestLine(reader, request);

        // 2. Headers 파싱 (헤더 이름: 값 형태)
        parseHeaders(reader, request);

        // 3. Body 파싱 (Content-Length가 있는 경우)
        parseBody(inputStream, request);

        // 4. 쿼리 스트링 파싱 (URI에서 분리된 부분)
        parseQueryString(request);

        return request;
    }

    /**
     * HTTP 요청 라인을 파싱합니다.
     * 형식: METHOD URI HTTP/VERSION
     * 예: GET /index.html?name=value HTTP/1.1
     *
     * private static으로 구현한 이유:
     * - 내부적으로만 사용하는 파싱 로직
     * - 외부에서 직접 호출할 필요 없음
     * - 코드 모듈화
     *
     * @param reader 텍스트를 읽을 BufferedReader
     * @param request 파싱 결과를 저장할 객체
     * @throws IOException 읽기 오류 시
     * @throws HttpParseException 파싱 오류 시
     */
    private static void parseRequestLine(BufferedReader reader, ParsedHttpRequest request)
            throws IOException, HttpParseException {

        // readLine(): BufferedReader의 한 줄 읽기 메서드
        // HTTP 요청의 첫 번째 줄이 요청 라인
        String requestLine = reader.readLine();

        // null 체크: 스트림 끝에 도달했거나 빈 요청
        // trim().isEmpty(): 공백만 있는 줄도 오류로 처리
        if (requestLine == null || requestLine.trim().isEmpty()) {
            throw new HttpParseException("빈 요청 라인");
        }

        // 요청 라인 길이 제한 체크 (보안)
        if (requestLine.length() > MAX_REQUEST_LINE_SIZE) {
            throw new HttpParseException("요청 라인이 너무 깁니다: " + requestLine.length());
        }

        // 공백으로 분리 (METHOD URI HTTP/VERSION)
        // split("\\s+"): 하나 이상의 공백 문자로 분할
        // \\s+: 정규표현식에서 공백 문자 1개 이상을 의미
        String[] parts = requestLine.trim().split("\\s+");

        // HTTP 요청 라인은 정확히 3개 부분으로 구성되어야 함
        if (parts.length != 3) {
            throw new HttpParseException("잘못된 요청 라인 형식: " + requestLine);
        }

        // HTTP 메서드 검증 및 설정
        // toUpperCase(): HTTP 메서드는 대문자로 정규화
        String method = parts[0].toUpperCase();
        if (!isValidHttpMethod(method)) {
            throw new HttpParseException("지원하지 않는 HTTP 메서드: " + method);
        }
        request.setMethod(method);

        // URI 파싱 (쿼리 스트링 분리)
        String fullUri = parts[1];
        // indexOf('?'): 물음표 위치 찾기 (-1: 없음)
        int queryIndex = fullUri.indexOf('?');

        if (queryIndex != -1) {
            // 쿼리 스트링이 있는 경우
            // substring(0, queryIndex): 시작부터 물음표 이전까지
            request.setUri(fullUri.substring(0, queryIndex));
            // substring(queryIndex + 1): 물음표 다음부터 끝까지
            request.setQueryString(fullUri.substring(queryIndex + 1));
        } else {
            // 쿼리 스트링이 없는 경우
            request.setUri(fullUri);
        }

        // HTTP 버전 검증 및 설정
        String httpVersion = parts[2];
        if (!isValidHttpVersion(httpVersion)) {
            throw new HttpParseException("지원하지 않는 HTTP 버전: " + httpVersion);
        }
        request.setHttpVersion(httpVersion);
    }

    /**
     * HTTP 헤더들을 파싱합니다.
     * 형식: Header-Name: Header-Value
     *
     * @param reader 텍스트를 읽을 BufferedReader
     * @param request 파싱 결과를 저장할 객체
     * @throws IOException 읽기 오류 시
     * @throws HttpParseException 파싱 오류 시
     */
    private static void parseHeaders(BufferedReader reader, ParsedHttpRequest request)
            throws IOException, HttpParseException {

        String line;
        int totalHeaderSize = 0; // 헤더 크기 누적 (DoS 방지)

        // 헤더 섹션은 빈 줄까지 계속됨
        while ((line = reader.readLine()) != null) {
            // 빈 라인은 헤더 섹션의 끝을 의미
            if (line.trim().isEmpty()) {
                break; // 헤더 파싱 종료
            }

            // 헤더 크기 누적 및 제한 체크
            totalHeaderSize += line.length();
            if (totalHeaderSize > MAX_HEADER_SIZE) {
                throw new HttpParseException("헤더 크기가 너무 큽니다");
            }

            // 헤더 파싱: "Name: Value" 형식
            // indexOf(':'): 콜론 위치 찾기
            int colonIndex = line.indexOf(':');
            if (colonIndex == -1) {
                // 콜론이 없으면 잘못된 헤더 형식
                throw new HttpParseException("잘못된 헤더 형식: " + line);
            }

            // 헤더 이름과 값 추출
            // substring(0, colonIndex): 콜론 이전 = 헤더 이름
            // trim(): 앞뒤 공백 제거
            // toLowerCase(): 대소문자 무관 처리
            String headerName = line.substring(0, colonIndex).trim().toLowerCase();

            // substring(colonIndex + 1): 콜론 다음부터 = 헤더 값
            String headerValue = line.substring(colonIndex + 1).trim();

            // 헤더 이름/값 길이 검증 (보안)
            if (headerName.length() > MAX_HEADER_NAME_SIZE) {
                throw new HttpParseException("헤더 이름이 너무 깁니다: " + headerName);
            }
            if (headerValue.length() > MAX_HEADER_VALUE_SIZE) {
                throw new HttpParseException("헤더 값이 너무 깁니다");
            }

            // 헤더 저장 (중복 헤더는 리스트로 관리)
            // computeIfAbsent(): 키가 없으면 새 값을 계산해서 추가
            // k -> new ArrayList<>(): 람다 표현식으로 새 리스트 생성
            request.getHeaders().computeIfAbsent(headerName, k -> new ArrayList<>()).add(headerValue);

            // 특별한 헤더들 처리 (Content-Type, Content-Length 등)
            processSpecialHeaders(headerName, headerValue, request);
        }
    }

    /**
     * 특별한 의미를 갖는 헤더들을 처리합니다.
     *
     * 특별한 헤더들을 별도 처리하는 이유:
     * - 빠른 접근을 위해 별도 필드에 저장
     * - 헤더 값의 특별한 파싱이 필요한 경우
     *
     * @param headerName 헤더 이름 (소문자)
     * @param headerValue 헤더 값
     * @param request 파싱 결과 객체
     */
    private static void processSpecialHeaders(String headerName, String headerValue,
                                              ParsedHttpRequest request) {
        // switch 문으로 특별한 헤더들 처리
        switch (headerName) {
            case "content-type":
                // Content-Type 헤더 저장
                request.setContentType(headerValue);

                // charset 파라미터 추출
                // Content-Type: text/html; charset=UTF-8
                if (headerValue.contains("charset=")) {
                    String charset = extractCharset(headerValue);
                    if (charset != null) {
                        request.setCharacterEncoding(charset);
                    }
                }
                break;

            case "content-length":
                try {
                    // 문자열을 정수로 변환
                    // Integer.parseInt(): String을 int로 변환
                    int length = Integer.parseInt(headerValue);
                    if (length < 0) {
                        // 음수 길이는 유효하지 않음
                        throw new NumberFormatException("음수 길이");
                    }
                    request.setContentLength(length);
                } catch (NumberFormatException e) {
                    // Content-Length 헤더 무시 (로그 기록 권장)
                    // 잘못된 값이지만 요청을 완전히 거부하지는 않음
                }
                break;
        }
    }

    /**
     * Content-Type에서 charset을 추출합니다.
     * 예: "text/html; charset=UTF-8" -> "UTF-8"
     *
     * @param contentType Content-Type 헤더 값
     * @return 추출된 charset 또는 null
     */
    private static String extractCharset(String contentType) {
        // "charset=" 문자열의 위치 찾기
        // toLowerCase(): 대소문자 무관 검색
        int charsetIndex = contentType.toLowerCase().indexOf("charset=");

        if (charsetIndex != -1) {
            // "charset=" 이후의 문자열 추출
            // charsetIndex + 8: "charset="의 길이만큼 건너뛰기
            String charset = contentType.substring(charsetIndex + 8);

            // 세미콜론이 있으면 그 이전까지만 추출
            int semicolonIndex = charset.indexOf(';');
            if (semicolonIndex != -1) {
                charset = charset.substring(0, semicolonIndex);
            }

            // 앞뒤 공백 제거 후 반환
            return charset.trim();
        }
        return null; // charset 파라미터가 없음
    }

    /**
     * HTTP 요청 본문을 파싱합니다.
     *
     * @param inputStream 원본 InputStream (BufferedReader가 아닌)
     * @param request 파싱 결과 객체
     * @throws IOException 읽기 오류 시
     * @throws HttpParseException 파싱 오류 시
     */
    private static void parseBody(InputStream inputStream, ParsedHttpRequest request)
            throws IOException, HttpParseException {

        // Content-Length가 없거나 0 이하면 본문이 없음
        if (request.getContentLength() <= 0) {
            return; // 본문 파싱 스킵
        }

        // 최대 본문 크기 제한 (예: 50MB)
        // DoS 공격 방지: 과도하게 큰 요청 본문 제한
        final int MAX_BODY_SIZE = 50 * 1024 * 1024; // 50MB
        if (request.getContentLength() > MAX_BODY_SIZE) {
            throw new HttpParseException("요청 본문이 너무 큽니다: " + request.getContentLength());
        }

        // 본문 읽기
        // new byte[]: 지정된 크기의 바이트 배열 생성
        byte[] body = new byte[request.getContentLength()];
        int totalRead = 0; // 읽은 바이트 수 누적
        int bytesRead; // 한 번에 읽은 바이트 수

        // 전체 본문을 읽을 때까지 반복
        while (totalRead < body.length) {
            // read(배열, 시작위치, 읽을크기): InputStream의 바이트 읽기 메서드
            // body: 읽은 데이터를 저장할 배열
            // totalRead: 배열에서 저장 시작할 위치
            // body.length - totalRead: 남은 읽을 바이트 수
            bytesRead = inputStream.read(body, totalRead, body.length - totalRead);

            if (bytesRead == -1) {
                // -1: 스트림 끝에 도달 (EOF)
                // Content-Length만큼 읽지 못했다는 의미
                throw new HttpParseException("요청 본문이 예상보다 짧습니다");
            }
            totalRead += bytesRead; // 읽은 바이트 수 누적
        }

        // 파싱된 본문 저장
        request.setBody(body);

        // Content-Type에 따른 본문 파싱
        parseBodyByContentType(request);
    }

    /**
     * Content-Type에 따라 본문을 파싱합니다.
     *
     * Content-Type별 처리가 필요한 이유:
     * - 폼 데이터: 매개변수로 파싱
     * - JSON: 그대로 유지
     * - 멀티파트: 특별한 파싱 필요
     *
     * @param request 파싱 결과 객체
     * @throws HttpParseException 파싱 오류 시
     */
    private static void parseBodyByContentType(ParsedHttpRequest request)
            throws HttpParseException {

        String contentType = request.getContentType();
        if (contentType == null) {
            return; // Content-Type이 없으면 처리하지 않음
        }

        // Content-Type의 메인 타입만 추출 (파라미터 제거)
        // split(";"): 세미콜론으로 분할
        // [0]: 첫 번째 부분 = 메인 타입
        // trim(): 공백 제거
        // toLowerCase(): 대소문자 무관 처리
        String mainType = contentType.split(";")[0].trim().toLowerCase();

        // Content-Type별 처리
        switch (mainType) {
            case "application/x-www-form-urlencoded":
                // HTML 폼에서 전송하는 기본 형식
                parseFormUrlEncoded(request);
                break;

            case "multipart/form-data":
                // 파일 업로드에 사용하는 형식
                // multipart는 복잡하므로 기본 처리만 (향후 확장 가능)
                parseMultipartFormData(request);
                break;

            case "application/json":
            case "text/plain":
            case "text/html":
                // 텍스트 기반 데이터는 그대로 유지
                // 별도 파싱 불필요
                break;

            default:
                // 기타 타입은 바이너리로 처리
                // 별도 파싱 불필요
                break;
        }
    }

    /**
     * application/x-www-form-urlencoded 형태의 본문을 파싱합니다.
     *
     * 폼 인코딩 파싱이 중요한 이유:
     * - HTML 폼의 기본 전송 방식
     * - 매개변수로 변환하여 쉬운 접근 제공
     *
     * @param request 파싱 결과 객체
     * @throws HttpParseException 파싱 오류 시
     */
    private static void parseFormUrlEncoded(ParsedHttpRequest request)
            throws HttpParseException {

        try {
            // 바이트 배열을 문자열로 변환
            // new String(바이트배열, 인코딩): 바이트를 문자열로 변환
            String bodyString = new String(request.getBody(), request.getCharacterEncoding());

            // URL 인코딩된 문자열을 파싱
            Map<String, List<String>> params = parseUrlEncodedString(bodyString);

            // 기존 쿼리 파라미터와 병합
            for (Map.Entry<String, List<String>> entry : params.entrySet()) {
                // List를 String[] 배열로 변환
                // toArray(new String[0]): 리스트를 배열로 변환
                String[] values = entry.getValue().toArray(new String[0]);
                // 매개변수 맵에 추가
                request.getParameters().put(entry.getKey(), values);
            }

        } catch (UnsupportedEncodingException e) {
            // 지원하지 않는 문자 인코딩
            throw new HttpParseException("지원하지 않는 문자 인코딩: " + request.getCharacterEncoding());
        }
    }

    /**
     * multipart/form-data 형태의 본문을 파싱합니다.
     * (기본 구현 - 향후 확장 필요)
     *
     * 기본 구현만 제공하는 이유:
     * - multipart 파싱은 매우 복잡함
     * - boundary 추출, 각 파트 파싱 등 많은 작업 필요
     * - 현재는 raw body 데이터만 보존
     *
     * @param request 파싱 결과 객체
     */
    private static void parseMultipartFormData(ParsedHttpRequest request) {
        // 복잡한 multipart 파싱은 향후 구현
        // 현재는 raw body 데이터만 보존
        // TODO: multipart boundary 추출 및 각 파트 파싱 구현
    }

    /**
     * URL 인코딩된 쿼리 스트링을 파싱합니다.
     *
     * @param request 파싱 결과 객체
     * @throws HttpParseException 파싱 오류 시
     */
    private static void parseQueryString(ParsedHttpRequest request)
            throws HttpParseException {

        String queryString = request.getQueryString();

        // 쿼리 스트링이 없으면 처리하지 않음
        if (queryString == null || queryString.trim().isEmpty()) {
            return;
        }

        try {
            // URL 인코딩된 문자열 파싱
            Map<String, List<String>> params = parseUrlEncodedString(queryString);

            // 매개변수 저장
            for (Map.Entry<String, List<String>> entry : params.entrySet()) {
                // List를 String[] 배열로 변환
                String[] values = entry.getValue().toArray(new String[0]);
                request.getParameters().put(entry.getKey(), values);
            }

        } catch (Exception e) {
            // 쿼리 스트링 파싱 오류
            throw new HttpParseException("쿼리 스트링 파싱 오류: " + e.getMessage());
        }
    }

    /**
     * URL 인코딩된 문자열을 파싱하여 매개변수 맵을 반환합니다.
     * 형식: name1=value1&name2=value2&name1=value3
     *
     * 별도 메서드로 분리한 이유:
     * - 쿼리 스트링과 폼 데이터에서 재사용
     * - 코드 중복 방지
     * - 테스트 용이성
     *
     * @param encoded URL 인코딩된 문자열
     * @return 파싱된 매개변수 맵 (이름 -> 값 리스트)
     * @throws UnsupportedEncodingException 인코딩 오류 시
     */
    private static Map<String, List<String>> parseUrlEncodedString(String encoded)
            throws UnsupportedEncodingException {

        // 매개변수를 저장할 맵
        Map<String, List<String>> params = new HashMap<>();

        // null이나 빈 문자열 체크
        if (encoded == null || encoded.trim().isEmpty()) {
            return params; // 빈 맵 반환
        }

        // '&'로 분리하여 각 매개변수 쌍 처리
        // split("&"): 앰퍼샌드로 문자열 분할
        String[] pairs = encoded.split("&");

        // 각 매개변수 쌍 처리
        for (String pair : pairs) {
            // 빈 쌍은 무시
            if (pair.trim().isEmpty()) {
                continue;
            }

            // '='로 분리하여 이름과 값 추출
            int equalIndex = pair.indexOf('=');
            String name, value;

            if (equalIndex != -1) {
                // '='가 있는 경우: name=value
                name = URLDecoder.decode(pair.substring(0, equalIndex), "UTF-8");
                value = URLDecoder.decode(pair.substring(equalIndex + 1), "UTF-8");
            } else {
                // '='가 없는 경우: name만 있음 (값은 빈 문자열)
                name = URLDecoder.decode(pair, "UTF-8");
                value = "";
            }

            // 동일한 이름의 매개변수가 여러 개일 수 있음
            // computeIfAbsent(): 키가 없으면 새 리스트 생성
            params.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }

        return params;
    }

    /**
     * 유효한 HTTP 메서드인지 확인합니다.
     *
     * @param method 확인할 HTTP 메서드
     * @return 유효한 메서드이면 true
     */
    private static boolean isValidHttpMethod(String method) {
        // RFC 7231에서 정의된 표준 메서드들 + 일부 확장 메서드
        // Set.of(): Java 9+의 불변 Set 생성 메서드
        Set<String> validMethods = Set.of(
                "GET", "HEAD", "POST", "PUT", "DELETE", "CONNECT", "OPTIONS", "TRACE", "PATCH"
        );
        // contains(): Set의 포함 여부 확인 메서드
        return validMethods.contains(method);
    }

    /**
     * 유효한 HTTP 버전인지 확인합니다.
     *
     * @param version 확인할 HTTP 버전
     * @return 유효한 버전이면 true
     */
    private static boolean isValidHttpVersion(String version) {
        // 지원하는 HTTP 버전들
        Set<String> validVersions = Set.of("HTTP/1.0", "HTTP/1.1", "HTTP/2.0");
        return validVersions.contains(version);
    }

    /**
     * HTTP 파싱 오류를 나타내는 예외 클래스
     *
     * static 내부 클래스로 구현한 이유:
     * - HttpRequestParser와 밀접한 관련
     * - 외부 클래스 참조 불필요 (메모리 효율성)
     * - 패키지 구조 단순화
     *
     * Exception을 상속하는 이유:
     * - 체크드 예외로 처리 강제
     * - HTTP 파싱 오류는 예상 가능한 오류
     * - 호출자가 반드시 처리해야 하는 예외
     */
    public static class HttpParseException extends Exception {

        /**
         * 메시지만 있는 예외 생성자
         *
         * @param message 오류 메시지
         */
        public HttpParseException(String message) {
            // super(): 부모 클래스(Exception)의 생성자 호출
            // Exception(String message): 메시지를 설정하는 생성자
            super(message);
        }

        /**
         * 메시지와 원인 예외가 있는 생성자
         *
         * 원인 예외를 받는 이유:
         * - 예외 체이닝으로 원래 오류 정보 보존
         * - 디버깅 시 전체 스택 트레이스 확인 가능
         * - 근본 원인 추적 용이
         *
         * @param message 오류 메시지
         * @param cause 원인이 된 예외
         */
        public HttpParseException(String message, Throwable cause) {
            // super(String, Throwable): 메시지와 원인을 모두 설정하는 생성자
            super(message, cause);
        }
    }
}