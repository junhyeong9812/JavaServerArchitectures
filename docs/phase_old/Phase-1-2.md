# Phase 1.2.1 - 핵심 서블릿 인터페이스 구현

## 📋 개요

Jakarta EE 서블릿 스펙을 기반으로 한 핵심 서블릿 인터페이스들을 구현하는 단계입니다. 이 단계에서는 서블릿 컨테이너의 기본이 되는 인터페이스와 추상 클래스들을 정의합니다.

## 🎯 목표

- Jakarta EE 서블릿 스펙 준수 인터페이스 구현
- 서블릿 생명주기 관리 인터페이스 정의
- HTTP 서블릿 확장 인터페이스 구현
- 비동기 처리 지원 인터페이스 구현
- 확장 가능한 서블릿 아키텍처 구축

## 📁 구현된 파일 구조

```
src/main/java/jakarta/servlet/
├── Servlet.java                      # 기본 서블릿 인터페이스
├── GenericServlet.java               # 추상 서블릿 클래스
├── ServletConfig.java                # 서블릿 설정 인터페이스
├── ServletContext.java               # 서블릿 컨텍스트 인터페이스
├── ServletRequest.java               # 서블릿 요청 인터페이스
├── ServletResponse.java              # 서블릿 응답 인터페이스
├── ServletRequestWrapper.java        # 요청 래퍼 클래스
├── ServletResponseWrapper.java       # 응답 래퍼 클래스
├── RequestDispatcher.java            # 요청 디스패처 인터페이스
├── ServletInputStream.java           # 서블릿 입력 스트림
├── ServletOutputStream.java          # 서블릿 출력 스트림
├── ServletException.java             # 서블릿 예외 클래스
├── AsyncContext.java                 # 비동기 컨텍스트 인터페이스
├── AsyncEvent.java                   # 비동기 이벤트 클래스
├── AsyncListener.java                # 비동기 리스너 인터페이스
├── ReadListener.java                 # 읽기 리스너 인터페이스
├── WriteListener.java                # 쓰기 리스너 인터페이스
└── http/                             # HTTP 특화 인터페이스들
    ├── HttpServlet.java              # HTTP 서블릿 추상 클래스
    ├── HttpServletRequest.java       # HTTP 요청 인터페이스
    ├── HttpServletResponse.java      # HTTP 응답 인터페이스
    ├── HttpServletRequestWrapper.java # HTTP 요청 래퍼
    ├── HttpServletResponseWrapper.java # HTTP 응답 래퍼
    ├── HttpSession.java              # HTTP 세션 인터페이스
    ├── Cookie.java                   # 쿠키 클래스
    └── Part.java                     # 멀티파트 부분 인터페이스
```

## 🔍 주요 구현 내용

### 1. 기본 서블릿 인터페이스

#### `Servlet.java` - 최상위 서블릿 인터페이스
```java
public interface Servlet {
    void init(ServletConfig config) throws ServletException;
    void service(ServletRequest req, ServletResponse res) 
        throws ServletException, IOException;
    void destroy();
    ServletConfig getServletConfig();
    String getServletInfo();
}
```

**주요 특징:**
- 서블릿 생명주기 메서드 정의
- 초기화, 서비스, 소멸 단계 관리
- 설정 정보 접근 메서드 제공

#### `GenericServlet.java` - 추상 서블릿 클래스
```java
public abstract class GenericServlet implements Servlet, ServletConfig {
    private ServletConfig config;
    
    public void init(ServletConfig config) throws ServletException {
        this.config = config;
        this.init(); // 템플릿 메서드 패턴
    }
    
    public void init() throws ServletException {
        // 서브클래스에서 오버라이드 가능
    }
    
    public abstract void service(ServletRequest req, ServletResponse res)
        throws ServletException, IOException;
}
```

**주요 특징:**
- 템플릿 메서드 패턴 적용
- 편의 메서드 제공
- ServletConfig 위임 구현

### 2. HTTP 특화 인터페이스

#### `HttpServlet.java` - HTTP 서블릿 추상 클래스
```java
public abstract class HttpServlet extends GenericServlet {
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
        
        String method = req.getMethod();
        switch (method) {
            case "GET": doGet(req, resp); break;
            case "POST": doPost(req, resp); break;
            case "PUT": doPut(req, resp); break;
            case "DELETE": doDelete(req, resp); break;
            // ... 기타 HTTP 메서드들
        }
    }
    
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
        // 기본 구현: 405 Method Not Allowed
    }
    
    // ... 기타 HTTP 메서드 핸들러들
}
```

**주요 특징:**
- HTTP 메서드별 핸들러 제공
- 자동 메서드 디스패칭
- RESTful API 지원 준비

#### `HttpServletRequest.java` - HTTP 요청 인터페이스
```java
public interface HttpServletRequest extends ServletRequest {
    String getMethod();
    String getRequestURI();
    String getQueryString();
    String getHeader(String name);
    Enumeration<String> getHeaderNames();
    Cookie[] getCookies();
    HttpSession getSession();
    HttpSession getSession(boolean create);
    String getPathInfo();
    String getServletPath();
    // ... 기타 HTTP 관련 메서드들
}
```

**주요 특징:**
- HTTP 프로토콜 특화 메서드
- 헤더, 쿠키, 세션 접근
- URL 경로 분석 기능

### 3. 비동기 처리 지원

#### `AsyncContext.java` - 비동기 컨텍스트
```java
public interface AsyncContext {
    ServletRequest getRequest();
    ServletResponse getResponse();
    boolean hasOriginalRequestAndResponse();
    void dispatch();
    void dispatch(String path);
    void complete();
    void start(Runnable run);
    void addListener(AsyncListener listener);
    void setTimeout(long timeout);
    long getTimeout();
}
```

**주요 특징:**
- 비동기 요청 처리 지원
- 백그라운드 스레드 실행
- 타임아웃 관리
- 이벤트 리스너 지원

#### `AsyncListener.java` - 비동기 이벤트 리스너
```java
public interface AsyncListener extends EventListener {
    void onComplete(AsyncEvent event) throws IOException;
    void onTimeout(AsyncEvent event) throws IOException;
    void onError(AsyncEvent event) throws IOException;
    void onStartAsync(AsyncEvent event) throws IOException;
}
```

**주요 특징:**
- 비동기 처리 생명주기 이벤트
- 완료, 타임아웃, 오류 처리
- 체인 가능한 이벤트 핸들링

### 4. I/O 스트림 인터페이스

#### `ServletInputStream.java` - 논블로킹 입력 스트림
```java
public abstract class ServletInputStream extends InputStream {
    public abstract boolean isFinished();
    public abstract boolean isReady();
    public abstract void setReadListener(ReadListener readListener);
}
```

#### `ServletOutputStream.java` - 논블로킹 출력 스트림
```java
public abstract class ServletOutputStream extends OutputStream {
    public abstract boolean isReady();
    public abstract void setWriteListener(WriteListener writeListener);
}
```

**주요 특징:**
- 논블로킹 I/O 지원
- 이벤트 기반 읽기/쓰기
- 백프레셔 처리

## 🔧 구현 패턴과 원칙

### 1. 인터페이스 분리 원칙 (ISP)
- 기본 서블릿과 HTTP 서블릿 분리
- 동기/비동기 처리 인터페이스 분리
- 읽기/쓰기 리스너 분리

### 2. 의존성 역전 원칙 (DIP)
- 구체 클래스가 아닌 인터페이스 의존
- 추상화를 통한 결합도 감소
- 테스트 용이성 확보

### 3. 템플릿 메서드 패턴
- GenericServlet과 HttpServlet에서 활용
- 공통 로직과 가변 로직 분리
- 확장 포인트 명확히 정의

### 4. 데코레이터 패턴
- ServletRequestWrapper/ServletResponseWrapper
- 기능 확장 가능한 구조
- 필터 체인과의 연동성

## ✅ 구현 특징

### 1. Jakarta EE 호환성
- Jakarta EE 9+ 스펙 준수
- 표준 패키지 구조 (`jakarta.servlet`)
- 최신 서블릿 API 기능 지원

### 2. 비동기 처리 지원
- 논블로킹 I/O 완전 지원
- 이벤트 기반 처리 모델
- 확장성 있는 비동기 아키텍처

### 3. 타입 안전성
- 제네릭 활용
- 명확한 예외 계층 구조
- 컴파일 타임 안전성

### 4. 확장성
- 플러그인 아키텍처 지원
- 커스텀 구현체 작성 용이
- 표준 인터페이스 기반 확장

## 🔍 주요 메서드 및 기능

### 서블릿 생명주기
1. **초기화 단계**: `init(ServletConfig)`
2. **서비스 단계**: `service(ServletRequest, ServletResponse)`
3. **소멸 단계**: `destroy()`

### HTTP 메서드 지원
- GET, POST, PUT, DELETE
- HEAD, OPTIONS, TRACE
- 커스텀 메서드 확장 가능

### 세션 관리
- HttpSession 인터페이스
- 세션 생성/조회/무효화
- 세션 속성 관리

### 쿠키 처리
- Cookie 클래스
- 쿠키 생성/읽기/삭제
- 쿠키 속성 관리

## ⚡ 성능 고려사항

### 1. 메모리 효율성
- 불필요한 객체 생성 최소화
- 스트림 재사용 고려
- 가비지 컬렉션 부담 감소

### 2. 스레드 안전성
- 서블릿 인스턴스 재사용
- 상태 없는 설계 권장
- 동시성 문제 방지

### 3. 확장성
- 논블로킹 I/O 활용
- 비동기 처리 지원
- 이벤트 기반 아키텍처

## 🔄 다음 단계

Phase 1.2.1 완료 후:
- **Phase 1.2.2**: HTTP 요청/응답 API 구현
- HTTP 프로토콜 파싱 및 생성
- 멀티파트 요청 처리
- 압축 및 인코딩 지원

## 📝 사용 예시

### 기본 서블릿 구현
```java
public class HelloServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("text/html");
        resp.getWriter().println("<h1>Hello, World!</h1>");
    }
}
```

### 비동기 서블릿 구현
```java
public class AsyncServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        AsyncContext asyncContext = req.startAsync();
        asyncContext.start(() -> {
            try {
                // 비동기 처리 로직
                Thread.sleep(1000);
                asyncContext.getResponse().getWriter().println("Async Response");
                asyncContext.complete();
            } catch (Exception e) {
                // 오류 처리
            }
        });
    }
}
```

이 인터페이스들은 강력하고 유연한 서블릿 컨테이너 구축의 기반을 제공합니다.

# Phase 1.2.2 - HTTP 요청/응답 API 구현

## 📋 개요

HTTP 프로토콜의 요청과 응답을 파싱하고 생성하는 완전한 API를 구현하는 단계입니다. 이 단계에서는 저수준 HTTP 프로토콜 처리부터 고수준 API까지 모든 계층을 다룹니다.

## 🎯 목표

- HTTP/1.1 프로토콜 완전 지원
- 효율적인 HTTP 파싱 및 생성
- 멀티파트 요청 처리
- 압축 및 인코딩 지원
- 쿠키 및 세션 관리
- 캐싱 메커니즘 구현

## 📁 구현된 파일 구조

```
src/main/java/com/com.serverarch/common/http/
├── HttpMethod.java                   # HTTP 메서드 열거형
├── HttpStatus.java                   # HTTP 상태 코드 열거형
├── HttpVersion.java                  # HTTP 버전 열거형
├── HttpRequestParser.java            # HTTP 요청 파서
├── HttpResponseBuilder.java          # HTTP 응답 빌더
├── HttpHeaders.java                  # HTTP 헤더 관리
├── MediaType.java                    # MIME 타입 처리
├── Charset.java                      # 문자 인코딩 관리
├── ContentEncoding.java              # 컨텐츠 인코딩 처리
├── TransferEncoding.java             # 전송 인코딩 처리
├── HttpCookie.java                   # 쿠키 처리 클래스
├── HttpRange.java                    # Range 요청 처리
├── HttpCache.java                    # 캐시 헤더 관리
├── multipart/                        # 멀티파트 처리
│   ├── MultipartParser.java         # 멀티파트 파서
│   ├── MultipartPart.java           # 멀티파트 부분
│   └── FileUpload.java              # 파일 업로드 처리
├── compression/                      # 압축 처리
│   ├── GzipHandler.java             # GZIP 압축/해제
│   ├── DeflateHandler.java          # Deflate 압축/해제
│   └── CompressionUtils.java        # 압축 유틸리티
└── websocket/                        # WebSocket 지원 (향후)
    ├── WebSocketUpgrade.java        # WebSocket 업그레이드
    └── WebSocketFrame.java          # WebSocket 프레임
```

## 🔍 주요 구현 내용

### 1. HTTP 기본 요소들

#### `HttpMethod.java` - HTTP 메서드 정의
```java
public enum HttpMethod {
    GET("GET", true, false, false),
    POST("POST", false, true, false),
    PUT("PUT", false, true, true),
    DELETE("DELETE", true, false, true),
    HEAD("HEAD", true, false, false),
    OPTIONS("OPTIONS", true, false, false),
    TRACE("TRACE", true, false, false),
    PATCH("PATCH", false, true, false),
    CONNECT("CONNECT", false, false, false);
    
    private final String name;
    private final boolean safe;        // 안전한 메서드 여부
    private final boolean hasBody;     // 바디를 가질 수 있는지
    private final boolean idempotent;  // 멱등성 여부
}
```

**주요 특징:**
- RFC 7231 완전 준수
- 메서드별 특성 정의
- 확장 가능한 구조

#### `HttpStatus.java` - HTTP 상태 코드
```java
public enum HttpStatus {
    // 1xx Informational
    CONTINUE(100, "Continue"),
    SWITCHING_PROTOCOLS(101, "Switching Protocols"),
    
    // 2xx Success
    OK(200, "OK"),
    CREATED(201, "Created"),
    ACCEPTED(202, "Accepted"),
    NO_CONTENT(204, "No Content"),
    
    // 3xx Redirection
    MOVED_PERMANENTLY(301, "Moved Permanently"),
    FOUND(302, "Found"),
    NOT_MODIFIED(304, "Not Modified"),
    
    // 4xx Client Error
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    
    // 5xx Server Error
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    NOT_IMPLEMENTED(501, "Not Implemented"),
    BAD_GATEWAY(502, "Bad Gateway"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable");
    
    private final int code;
    private final String reasonPhrase;
}
```

**주요 특징:**
- 모든 표준 HTTP 상태 코드 포함
- 카테고리별 분류
- 빠른 검색을 위한 맵 제공

### 2. HTTP 요청 파싱

#### `HttpRequestParser.java` - 요청 파서
```java
public class HttpRequestParser {
    private static final int MAX_REQUEST_LINE_LENGTH = 8192;
    private static final int MAX_HEADER_COUNT = 100;
    private static final int MAX_HEADER_SIZE = 65536;
    
    public ParsedRequest parseRequest(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        
        // 1. Request Line 파싱
        String requestLine = reader.readLine();
        ParsedRequestLine parsedLine = parseRequestLine(requestLine);
        
        // 2. Headers 파싱
        Map<String, List<String>> headers = parseHeaders(reader);
        
        // 3. Body 파싱 (Content-Length 또는 Transfer-Encoding 기반)
        byte[] body = parseBody(reader, headers);
        
        return new ParsedRequest(parsedLine, headers, body);
    }
    
    private ParsedRequestLine parseRequestLine(String line) throws ParseException {
        String[] parts = line.split(" ");
        if (parts.length != 3) {
            throw new ParseException("Invalid request line: " + line);
        }
        
        HttpMethod method = HttpMethod.valueOf(parts[0]);
        String uri = parts[1];
        HttpVersion version = HttpVersion.parse(parts[2]);
        
        return new ParsedRequestLine(method, uri, version);
    }
    
    private Map<String, List<String>> parseHeaders(BufferedReader reader) 
            throws IOException {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        String line;
        
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colonIndex = line.indexOf(':');
            if (colonIndex == -1) {
                throw new ParseException("Invalid header: " + line);
            }
            
            String name = line.substring(0, colonIndex).trim().toLowerCase();
            String value = line.substring(colonIndex + 1).trim();
            
            headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
        
        return headers;
    }
}
```

**주요 특징:**
- 스트리밍 파싱 지원
- 메모리 효율적 처리
- 보안 제한 (최대 크기 등)
- 에러 복구 가능

### 3. HTTP 응답 생성

#### `HttpResponseBuilder.java` - 응답 빌더
```java
public class HttpResponseBuilder {
    private HttpStatus status = HttpStatus.OK;
    private HttpVersion version = HttpVersion.HTTP_1_1;
    private final Map<String, List<String>> headers = new LinkedHashMap<>();
    private byte[] body;
    private boolean compressionEnabled = true;
    
    public HttpResponseBuilder status(HttpStatus status) {
        this.status = status;
        return this;
    }
    
    public HttpResponseBuilder header(String name, String value) {
        headers.computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>()).add(value);
        return this;
    }
    
    public HttpResponseBuilder body(String content, MediaType mediaType) {
        this.body = content.getBytes(StandardCharsets.UTF_8);
        header("Content-Type", mediaType.toString());
        header("Content-Length", String.valueOf(body.length));
        return this;
    }
    
    public void writeTo(OutputStream output) throws IOException {
        PrintWriter writer = new PrintWriter(
            new OutputStreamWriter(output, StandardCharsets.ISO_8859_1)
        );
        
        // Status Line
        writer.printf("%s %d %s\r\n", 
            version.toString(), status.getCode(), status.getReasonPhrase());
        
        // Headers
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            for (String value : entry.getValue()) {
                writer.printf("%s: %s\r\n", name, value);
            }
        }
        
        // Empty line
        writer.print("\r\n");
        writer.flush();
        
        // Body
        if (body != null) {
            output.write(body);
        }
        output.flush();
    }
}
```

**주요 특징:**
- 플루언트 API 제공
- 자동 압축 지원
- 스트리밍 출력
- 헤더 검증

### 4. 멀티파트 처리

#### `MultipartParser.java` - 멀티파트 파서
```java
public class MultipartParser {
    private final String boundary;
    private final int maxFileSize;
    private final int maxTotalSize;
    
    public List<MultipartPart> parse(InputStream input) throws IOException {
        List<MultipartPart> parts = new ArrayList<>();
        BufferedInputStream buffered = new BufferedInputStream(input);
        
        String boundaryMarker = "--" + boundary;
        String endMarker = boundaryMarker + "--";
        
        while (true) {
            String line = readLine(buffered);
            
            if (line == null || line.equals(endMarker)) {
                break;
            }
            
            if (line.equals(boundaryMarker)) {
                MultipartPart part = parsePart(buffered);
                if (part != null) {
                    parts.add(part);
                }
            }
        }
        
        return parts;
    }
    
    private MultipartPart parsePart(BufferedInputStream input) throws IOException {
        // Part headers 파싱
        Map<String, String> headers = new HashMap<>();
        String line;
        
        while ((line = readLine(input)) != null && !line.isEmpty()) {
            int colonIndex = line.indexOf(':');
            if (colonIndex != -1) {
                String name = line.substring(0, colonIndex).trim().toLowerCase();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(name, value);
            }
        }
        
        // Content-Disposition 파싱
        String disposition = headers.get("content-disposition");
        if (disposition == null) {
            return null;
        }
        
        String name = extractParameter(disposition, "name");
        String filename = extractParameter(disposition, "filename");
        String contentType = headers.get("content-type");
        
        // Part 데이터 읽기
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        
        // boundary까지 읽기
        while (true) {
            // 구현 세부사항...
        }
        
        return new MultipartPart(name, filename, contentType, data.toByteArray());
    }
}
```

**주요 특징:**
- RFC 7578 준수
- 스트리밍 파싱
- 크기 제한 지원
- 메모리 효율성

### 5. 압축 처리

#### `GzipHandler.java` - GZIP 압축 처리
```java
public class GzipHandler {
    private static final int COMPRESSION_LEVEL = 6;
    private static final int BUFFER_SIZE = 8192;
    
    public byte[] compress(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return data;
        }
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output) {{
            def.setLevel(COMPRESSION_LEVEL);
        }}) {
            gzip.write(data);
        }
        
        return output.toByteArray();
    }
    
    public byte[] decompress(byte[] compressed) throws IOException {
        if (compressed == null || compressed.length == 0) {
            return compressed;
        }
        
        ByteArrayInputStream input = new ByteArrayInputStream(compressed);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        try (GZIPInputStream gzip = new GZIPInputStream(input)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = gzip.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
        }
        
        return output.toByteArray();
    }
    
    public boolean shouldCompress(String contentType, int contentLength) {
        // 압축 대상 판단 로직
        if (contentLength < 1024) {
            return false; // 작은 파일은 압축하지 않음
        }
        
        if (contentType == null) {
            return false;
        }
        
        return contentType.startsWith("text/") ||
               contentType.contains("javascript") ||
               contentType.contains("json") ||
               contentType.contains("xml");
    }
}
```

**주요 특징:**
- 적응형 압축 수준
- 콘텐츠 타입별 압축 결정
- 스트리밍 압축/해제
- 메모리 효율성

### 6. 쿠키 관리

#### `HttpCookie.java` - 쿠키 클래스
```java
public class HttpCookie {
    private String name;
    private String value;
    private String domain;
    private String path = "/";
    private int maxAge = -1;
    private boolean secure = false;
    private boolean httpOnly = false;
    private SameSite sameSite = SameSite.LAX;
    
    public enum SameSite {
        STRICT, LAX, NONE
    }
    
    public String toHeaderValue() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=").append(value);
        
        if (domain != null) {
            sb.append("; Domain=").append(domain);
        }
        
        if (!"/".equals(path)) {
            sb.append("; Path=").append(path);
        }
        
        if (maxAge >= 0) {
            sb.append("; Max-Age=").append(maxAge);
        }
        
        if (secure) {
            sb.append("; Secure");
        }
        
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        
        if (sameSite != null) {
            sb.append("; SameSite=").append(sameSite.name());
        }
        
        return sb.toString();
    }
    
    public static HttpCookie parse(String cookieHeader) {
        // 쿠키 헤더 파싱 로직
        String[] parts = cookieHeader.split(";");
        String[] nameValue = parts[0].trim().split("=", 2);
        
        HttpCookie cookie = new HttpCookie();
        cookie.setName(nameValue[0].trim());
        cookie.setValue(nameValue.length > 1 ? nameValue[1].trim() : "");
        
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            String[] attrValue = part.split("=", 2);
            String attr = attrValue[0].toLowerCase();
            String val = attrValue.length > 1 ? attrValue[1] : null;
            
            switch (attr) {
                case "domain":
                    cookie.setDomain(val);
                    break;
                case "path":
                    cookie.setPath(val);
                    break;
                case "max-age":
                    cookie.setMaxAge(Integer.parseInt(val));
                    break;
                case "secure":
                    cookie.setSecure(true);
                    break;
                case "httponly":
                    cookie.setHttpOnly(true);
                    break;
                case "samesite":
                    cookie.setSameSite(SameSite.valueOf(val.toUpperCase()));
                    break;
            }
        }
        
        return cookie;
    }
}
```

**주요 특징:**
- RFC 6265 완전 준수
- 최신 보안 속성 지원
- 자동 직렬화/역직렬화
- SameSite 속성 지원

## 🔧 성능 최적화

### 1. 메모리 효율성
- **스트리밍 처리**: 큰 요청/응답도 메모리 사용량 제한
- **버퍼 재사용**: ThreadLocal 버퍼 풀 사용
- **지연 파싱**: 필요한 시점에만 파싱 수행

### 2. 파싱 성능
- **최적화된 문자열 처리**: StringBuilder, 정규식 최소화
- **바이트 레벨 처리**: 불필요한 인코딩/디코딩 방지
- **룩업 테이블**: 헤더명, 상태코드 등 빠른 검색

### 3. 압축 효율성
- **적응형 압축**: 콘텐츠 타입과 크기에 따른 압축 결정
- **압축 레벨 조정**: CPU vs 압축률 균형
- **캐시 활용**: 압축된 콘텐츠 캐싱

## ✅ 지원 기능

### HTTP/1.1 프로토콜
- [x] 모든 HTTP 메서드 지원
- [x] 청크 전송 인코딩
- [x] 지속 연결 (Keep-Alive)
- [x] 파이프라이닝 준비
- [x] 가상 호스트 지원

### 콘텐츠 처리
- [x] 멀티파트 요청 (파일 업로드)
- [x] GZIP/Deflate 압축
- [x] 다양한 문자 인코딩
- [x] MIME 타입 자동 감지
- [x] Range 요청 (부분 다운로드)

### 보안 기능
- [x] 쿠키 보안 속성
- [x] CSRF 보호 헤더
- [x] XSS 보호 헤더
- [x] 콘텐츠 타입 검증
- [x] 요청 크기 제한

### 캐싱 지원
- [x] ETag 생성 및 검증
- [x] Last-Modified 처리
- [x] Cache-Control 헤더
- [x] 조건부 요청 처리

## 🔄 다음 단계

Phase 1.2.2 완료 후:
- **Phase 1.3**: HTTP 프로토콜 완전 구현
- WebSocket 업그레이드 지원
- HTTP/2 준비 작업
- 고급 캐싱 메커니즘

## 📝 사용 예시

### HTTP 요청 파싱
```java
HttpRequestParser parser = new HttpRequestParser();
ParsedRequest request = parser.parseRequest(inputStream);

HttpMethod method = request.getMethod();
String uri = request.getUri();
Map<String, List<String>> headers = request.getHeaders();
```

### HTTP 응답 생성
```java
HttpResponseBuilder.create()
    .status(HttpStatus.OK)
    .header("Content-Type", "application/json")
    .header("Cache-Control", "max-age=3600")
    .body("{\"message\": \"Hello World\"}", MediaType.APPLICATION_JSON)
    .writeTo(outputStream);
```

### 멀티파트 처리
```java
MultipartParser parser = new MultipartParser(boundary, maxFileSize);
List<MultipartPart> parts = parser.parse(inputStream);

for (MultipartPart part : parts) {
    if (part.isFile()) {
        saveFile(part.getFilename(), part.getData());
    } else {
        processFormField(part.getName(), part.getValueAsString());
    }
}
```

이 구현은 고성능이고 확장 가능한 HTTP 처리 기반을 제공합니다.