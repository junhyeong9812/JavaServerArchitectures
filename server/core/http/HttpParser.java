package server.core.http;

// I/O 관련 클래스들 import
import java.io.*;
// UTF-8 등의 문자 인코딩 처리를 위한 import
import java.nio.charset.StandardCharsets;
// 정규표현식 패턴 매칭을 위한 import
import java.util.regex.Pattern;

/**
 * 고성능 HTTP 요청 파서
 * RFC 7230, 7231 준수하는 HTTP/1.1 파싱
 */
public class HttpParser {

    // HTTP 요청 라인의 최대 길이 제한 (8KB)
    // 너무 긴 요청 라인으로 인한 메모리 공격 방지
    private static final int MAX_REQUEST_LINE_LENGTH = 8192;  // 8KB

    // HTTP 헤더 전체 크기 제한 (64KB)
    // 헤더 크기 공격 방지
    private static final int MAX_HEADER_SIZE = 65536;        // 64KB

    // 최대 헤더 개수 제한
    // 너무 많은 헤더로 인한 서비스 거부 공격 방지
    private static final int MAX_HEADERS_COUNT = 100;

    // 요청 라인 형식을 검증하는 정규표현식 패턴
    // "METHOD URI HTTP/1.x" 형식인지 확인
    // ^: 문자열 시작, $: 문자열 끝
    // [A-Z]+: 하나 이상의 대문자 (HTTP 메서드)
    // \\s+: 하나 이상의 공백 문자
    // [^\\s]+: 공백이 아닌 문자들 (URI)
    // HTTP/1\\.[01]: HTTP/1.0 또는 HTTP/1.1
    private static final Pattern REQUEST_LINE_PATTERN =
            Pattern.compile("^([A-Z]+)\\s+([^\\s]+)\\s+(HTTP/1\\.[01])$");

    // HTTP 프로토콜에서 사용하는 줄바꿈 문자 (CRLF)
    // \r\n을 바이트 배열로 미리 생성해서 성능 최적화
    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.UTF_8);

    // 헤더 끝을 나타내는 더블 CRLF
    // 헤더와 본문을 구분하는 빈 줄
    private static final byte[] DOUBLE_CRLF = "\r\n\r\n".getBytes(StandardCharsets.UTF_8);

    /**
     * InputStream에서 HTTP 요청 파싱
     */
    public static HttpRequest parseRequest(InputStream inputStream) throws IOException {
        // BufferedInputStream: 입력 스트림을 버퍼링하여 성능 향상
        // 8192: 8KB 버퍼 크기 설정
        // 작은 단위로 여러 번 읽는 것보다 큰 단위로 읽어서 버퍼에 저장 후 사용
        BufferedInputStream bufferedInput = new BufferedInputStream(inputStream, 8192);

        // 1. Request Line 파싱
        // "GET /path HTTP/1.1" 형태의 첫 번째 줄 읽기
        String requestLine = readRequestLine(bufferedInput);
        // 요청 라인을 메서드, URI, 버전으로 분리
        RequestLineComponents components = parseRequestLine(requestLine);

        // 2. Headers 파싱
        // "Header-Name: Header-Value" 형태의 헤더들 읽기
        HttpHeaders headers = parseHeaders(bufferedInput);

        // 3. Body 파싱 (필요한 경우)
        // Content-Length나 Transfer-Encoding에 따라 본문 읽기
        byte[] body = parseBody(bufferedInput, headers, components.method);

        // 파싱된 정보로 HttpRequest 객체 생성하여 반환
        return new HttpRequest(components.method, components.uri, components.version,
                headers, body);
    }

    /**
     * Request Line 읽기
     */
    private static String readRequestLine(BufferedInputStream input) throws IOException {
        // 바이트를 저장할 동적 배열
        // ByteArrayOutputStream: 메모리에 바이트 데이터를 쓸 수 있는 스트림
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int b;                // 읽은 바이트를 저장할 변수
        int length = 0;       // 현재까지 읽은 길이
        boolean foundCR = false;  // \r(Carriage Return)을 발견했는지 여부

        // 입력 스트림에서 한 바이트씩 읽기
        // read(): 다음 바이트를 읽어서 반환, 스트림 끝이면 -1 반환
        while ((b = input.read()) != -1) {
            length++;  // 읽은 길이 증가

            // 요청 라인이 너무 길면 예외 발생 (보안)
            if (length > MAX_REQUEST_LINE_LENGTH) {
                throw new IOException("Request line too long");
            }

            // 읽은 바이트가 \r(13)인 경우
            if (b == '\r') {
                foundCR = true;  // CR 발견 표시
            }
            // 읽은 바이트가 \n(10)이고 이전에 \r을 발견한 경우
            else if (b == '\n' && foundCR) {
                break;  // CRLF 완성으로 요청 라인 끝
            }
            else {
                // \r 다음에 \n이 아닌 경우, 이전 \r을 일반 문자로 처리
                if (foundCR) {
                    baos.write('\r');    // 이전 \r을 출력 스트림에 쓰기
                    foundCR = false;     // CR 발견 상태 초기화
                }
                // 현재 바이트를 출력 스트림에 쓰기
                baos.write(b);
            }
        }

        // 스트림 끝에 도달했는데 요청 라인이 완성되지 않은 경우
        if (b == -1) {
            throw new IOException("Unexpected end of stream while reading request line");
        }

        // ByteArrayOutputStream의 내용을 UTF-8 문자열로 변환
        // toString(charset): 지정된 문자 인코딩으로 바이트를 문자열로 변환
        return baos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Request Line 파싱
     */
    private static RequestLineComponents parseRequestLine(String requestLine) {
        // 요청 라인이 null이거나 빈 문자열인지 확인
        if (requestLine == null || requestLine.trim().isEmpty()) {
            throw new IllegalArgumentException("Request line cannot be empty");
        }

        // 공백으로 분리하되 최대 3개 부분으로만 나누기
        // split(regex, limit): 정규표현식으로 문자열을 분리, limit은 최대 분리 개수
        // \\s+: 하나 이상의 공백 문자
        String[] parts = requestLine.split("\\s+", 3);

        // 정확히 3개 부분(메서드, URI, 버전)이 있어야 함
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid request line format: " + requestLine);
        }

        // Method 파싱
        HttpMethod method;
        try {
            // 첫 번째 부분을 HTTP 메서드로 변환
            method = HttpMethod.fromString(parts[0]);
        } catch (IllegalArgumentException e) {
            // 지원하지 않는 HTTP 메서드인 경우 예외 발생
            throw new IllegalArgumentException("Invalid HTTP method: " + parts[0]);
        }

        // URI 파싱
        String uri = parts[1];
        // URI는 반드시 '/'로 시작해야 함 (절대 경로)
        if (!uri.startsWith("/")) {
            throw new IllegalArgumentException("URI must start with '/': " + uri);
        }

        // HTTP Version 파싱
        String version = parts[2];
        // HTTP/1.0 또는 HTTP/1.1만 지원
        if (!"HTTP/1.0".equals(version) && !"HTTP/1.1".equals(version)) {
            throw new IllegalArgumentException("Unsupported HTTP version: " + version);
        }

        // 파싱된 구성 요소들을 담은 객체 반환
        return new RequestLineComponents(method, uri, version);
    }

    /**
     * HTTP 헤더들 파싱
     */
    private static HttpHeaders parseHeaders(BufferedInputStream input) throws IOException {
        // 헤더들을 저장할 HttpHeaders 객체 생성
        HttpHeaders headers = new HttpHeaders();

        String line;         // 읽은 헤더 라인
        int headerCount = 0; // 현재까지 읽은 헤더 개수
        int totalHeaderSize = 0; // 전체 헤더 크기

        // 헤더 라인들을 하나씩 읽기
        // readHeaderLine()이 null을 반환하면 스트림 끝
        while ((line = readHeaderLine(input)) != null) {
            // 빈 줄이면 헤더 섹션 끝 (본문 시작)
            if (line.isEmpty()) {
                break; // 빈 줄이면 헤더 끝
            }

            headerCount++;              // 헤더 개수 증가
            totalHeaderSize += line.length(); // 헤더 크기 누적

            // 헤더 개수 제한 확인 (DoS 공격 방지)
            if (headerCount > MAX_HEADERS_COUNT) {
                throw new IOException("Too many headers");
            }

            // 헤더 전체 크기 제한 확인 (메모리 공격 방지)
            if (totalHeaderSize > MAX_HEADER_SIZE) {
                throw new IOException("Headers too large");
            }

            // 개별 헤더 라인 파싱하여 HttpHeaders 객체에 추가
            parseHeaderLine(line, headers);
        }

        return headers;
    }

    /**
     * 헤더 라인 읽기
     */
    private static String readHeaderLine(BufferedInputStream input) throws IOException {
        // 바이트들을 저장할 동적 배열
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int b;                    // 읽은 바이트
        boolean foundCR = false;  // \r을 발견했는지 여부

        // 한 바이트씩 읽으면서 줄 끝을 찾기
        while ((b = input.read()) != -1) {
            if (b == '\r') {
                foundCR = true;   // CR 발견 표시
            } else if (b == '\n') {
                if (foundCR) {
                    break;        // CRLF 발견으로 라인 끝
                } else {
                    baos.write(b); // LF만 있는 경우는 허용 (관대한 파싱)
                }
            } else {
                // CR 다음에 LF가 아닌 경우
                if (foundCR) {
                    baos.write('\r'); // 이전 CR을 일반 문자로 처리
                    foundCR = false;
                }
                baos.write(b);        // 현재 바이트 추가
            }
        }

        // 스트림 끝에 도달했고 아무것도 읽지 못한 경우
        if (b == -1 && baos.size() == 0) {
            return null; // EOF
        }

        // 읽은 바이트들을 UTF-8 문자열로 변환
        return baos.toString(StandardCharsets.UTF_8);
    }

    /**
     * 개별 헤더 라인 파싱
     */
    private static void parseHeaderLine(String line, HttpHeaders headers) {
        // ':'를 찾아서 헤더명과 값을 분리
        // indexOf(): 문자열에서 특정 문자의 첫 번째 위치 반환, 없으면 -1
        int colonIndex = line.indexOf(':');

        // 콜론이 없으면 잘못된 헤더 형식
        if (colonIndex == -1) {
            throw new IllegalArgumentException("Invalid header line: " + line);
        }

        // 헤더명: 콜론 이전 부분, 앞뒤 공백 제거
        // substring(start, end): start부터 end 직전까지의 부분 문자열
        // trim(): 앞뒤 공백 제거
        String name = line.substring(0, colonIndex).trim();

        // 헤더값: 콜론 이후 부분, 앞뒤 공백 제거
        String value = line.substring(colonIndex + 1).trim();

        // 헤더명이 비어있으면 에러
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Header name cannot be empty");
        }

        // 헤더 folding 처리 (RFC 7230에서는 deprecated이지만 호환성을 위해)
        // HTTP/1.1에서는 헤더가 여러 줄에 걸쳐 올 수 있음 (현재는 단순하게 처리)
        // add() 메서드로 헤더 추가 (같은 이름의 헤더가 있으면 값 추가)
        headers.add(name, value);
    }

    /**
     * Request Body 파싱
     */
    private static byte[] parseBody(BufferedInputStream input, HttpHeaders headers,
                                    HttpMethod method) throws IOException {

        // GET, HEAD, DELETE는 보통 body가 없음
        // canHaveBody(): 해당 메서드가 본문을 가질 수 있는지 확인
        if (!method.canHaveBody()) {
            return new byte[0];  // 빈 바이트 배열 반환
        }

        // Content-Length 헤더에서 본문 크기 가져오기
        long contentLength = headers.getContentLength();

        // Content-Length가 0이면 본문 없음
        if (contentLength == 0) {
            return new byte[0];
        }

        // Content-Length가 명시되어 있으면 고정 길이로 읽기
        if (contentLength > 0) {
            return readFixedLengthBody(input, contentLength);
        }

        // Transfer-Encoding: chunked 확인
        String transferEncoding = headers.get("Transfer-Encoding");

        // chunked 인코딩인 경우 청크 단위로 읽기
        // equalsIgnoreCase(): 대소문자 구분 없이 문자열 비교
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
        // 본문 크기가 int 최대값보다 크면 처리 불가
        // Integer.MAX_VALUE: int 타입의 최대값 (약 2GB)
        if (contentLength > Integer.MAX_VALUE) {
            throw new IOException("Request body too large");
        }

        // long을 int로 변환 (위에서 크기 확인했으므로 안전)
        int length = (int) contentLength;

        // 본문을 저장할 바이트 배열 생성
        byte[] body = new byte[length];

        int totalRead = 0;  // 현재까지 읽은 바이트 수

        // 지정된 길이만큼 모두 읽을 때까지 반복
        while (totalRead < length) {
            // read(buffer, offset, len): 버퍼의 offset부터 len만큼 읽기
            // 실제로 읽은 바이트 수 반환 (요청한 만큼 못 읽을 수 있음)
            int read = input.read(body, totalRead, length - totalRead);

            // -1이면 스트림 끝에 도달 (예상보다 일찍 끝남)
            if (read == -1) {
                throw new IOException("Unexpected end of stream while reading body");
            }

            totalRead += read;  // 읽은 바이트 수 누적
        }

        return body;
    }

    /**
     * Chunked body 읽기 (간단한 구현)
     */
    private static byte[] readChunkedBody(BufferedInputStream input) throws IOException {
        // 청크들을 모아서 저장할 동적 배열
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // 청크를 하나씩 읽는 무한 루프
        while (true) {
            // 청크 크기 읽기 (16진수로 표현됨)
            String chunkSizeLine = readHeaderLine(input);

            if (chunkSizeLine == null) {
                throw new IOException("Unexpected end of chunked body");
            }

            // 청크 크기 파싱 (16진수)
            int chunkSize;
            try {
                // 청크 크기는 "size;extension" 형태일 수 있음
                // split(";"): 세미콜론으로 분리하여 크기 부분만 가져오기
                String sizeStr = chunkSizeLine.split(";")[0].trim(); // 확장 무시

                // parseInt(string, radix): 문자열을 지정된 진법(radix)의 정수로 변환
                // 16: 16진법으로 파싱
                chunkSize = Integer.parseInt(sizeStr, 16);
            } catch (NumberFormatException e) {
                // 16진수가 아닌 잘못된 형식
                throw new IOException("Invalid chunk size: " + chunkSizeLine);
            }

            // 청크 크기가 0이면 마지막 청크 (본문 끝)
            if (chunkSize == 0) {
                // 트레일러 헤더들 읽기 (무시)
                // 마지막 청크 다음에 올 수 있는 추가 헤더들
                String line;
                while ((line = readHeaderLine(input)) != null && !line.isEmpty()) {
                    // 트레일러 헤더 무시
                }
                break;  // 청크 읽기 완료
            }

            // 청크 데이터 읽기 (고정 길이)
            byte[] chunkData = readFixedLengthBody(input, chunkSize);

            // 읽은 청크 데이터를 전체 본문에 추가
            // write(byte[]): 바이트 배열을 출력 스트림에 쓰기
            baos.write(chunkData);

            // 청크 끝의 CRLF 읽기 (청크 데이터 다음에 오는 줄바꿈)
            readHeaderLine(input);
        }

        // 모든 청크를 합친 전체 본문을 바이트 배열로 반환
        return baos.toByteArray();
    }

    /**
     * Request Line 컴포넌트 내부 클래스
     * 파싱된 요청 라인의 구성 요소들을 담는 불변 클래스
     */
    private static class RequestLineComponents {
        final HttpMethod method;  // HTTP 메서드 (GET, POST 등)
        final String uri;         // 요청 URI (/path/to/resource)
        final String version;     // HTTP 버전 (HTTP/1.1)

        // 생성자
        RequestLineComponents(HttpMethod method, String uri, String version) {
            this.method = method;
            this.uri = uri;
            this.version = version;
        }
    }

    /**
     * 파싱 예외 클래스
     * HTTP 파싱 중 발생하는 전용 예외
     */
    public static class HttpParseException extends IOException {
        // 메시지만 있는 생성자
        public HttpParseException(String message) {
            super(message);  // 부모 클래스(IOException) 생성자 호출
        }

        // 메시지와 원인이 되는 예외를 받는 생성자
        public HttpParseException(String message, Throwable cause) {
            super(message, cause);  // 부모 클래스 생성자 호출
        }
    }
}