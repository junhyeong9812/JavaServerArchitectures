# 개발 프로세스 (PROCESS.md)

## 🎯 프로젝트 전체 개요

**프로젝트명**: JavaServerArchitectures  
**목표**: 순수 자바로 3가지 HTTP 서버 아키텍처 구현 및 비교  
**개발 기간**: 9주 (2-3개월)  
**구현 방식**: 챕터별 단계적 구현  

### 핵심 설계 철학
1. **무거운 서블릿 API 버리고 HTTP 처리 본질에 집중**
2. **3가지 아키텍처의 차이점을 명확히 드러내는 설계**
3. **현대적 Java 기능 활용** (CompletableFuture, 람다, 스트림)
4. **실무 연관성 극대화** (Spring, Netty, Node.js 원리 이해)

### 3가지 아키텍처 비교 목표
- **Threaded**: Thread-per-Request, 블로킹 I/O
- **Hybrid**: AsyncContext + 컨텍스트 스위칭
- **EventLoop**: 단일 스레드 + 논블로킹 I/O

---

## 📋 Chapter 1: HTTP 코어 모듈 구현 (2주)

**목표**: 3가지 서버가 공통으로 사용할 HTTP 처리 핵심 모듈 구현  
**위치**: `src/main/java/server/core/`  
**핵심**: 간단하고 현대적인 HTTP API 설계

### 1.1 HTTP 기본 클래스 구현 (3일)

#### HttpMethod.java
```java
package server.core.http;

public enum HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE;
    
    public static HttpMethod fromString(String method) {
        try {
            return valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
    }
}
```

#### HttpStatus.java
```java
package server.core.http;

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
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    
    // 5xx Server Error
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    NOT_IMPLEMENTED(501, "Not Implemented"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable");
    
    private final int code;
    private final String reasonPhrase;
    
    HttpStatus(int code, String reasonPhrase) {
        this.code = code;
        this.reasonPhrase = reasonPhrase;
    }
    
    public int getCode() { return code; }
    public String getReasonPhrase() { return reasonPhrase; }
    
    public static HttpStatus fromCode(int code) {
        for (HttpStatus status : values()) {
            if (status.code == code) return status;
        }
        throw new IllegalArgumentException("Unknown status code: " + code);
    }
}
```

#### HttpHeaders.java
```java
package server.core.http;

import java.util.*;

public class HttpHeaders {
    private final Map<String, List<String>> headers = new LinkedHashMap<>();
    
    public void add(String name, String value) {
        headers.computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>()).add(value);
    }
    
    public void set(String name, String value) {
        List<String> values = new ArrayList<>();
        values.add(value);
        headers.put(name.toLowerCase(), values);
    }
    
    public String getFirst(String name) {
        List<String> values = headers.get(name.toLowerCase());
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }
    
    public List<String> get(String name) {
        return headers.getOrDefault(name.toLowerCase(), Collections.emptyList());
    }
    
    public Set<String> getNames() {
        return headers.keySet();
    }
    
    public int size() {
        return headers.size();
    }
    
    // 자주 사용되는 헤더들을 위한 편의 메소드
    public String getContentType() { return getFirst("content-type"); }
    public void setContentType(String contentType) { set("content-type", contentType); }
    
    public String getContentLength() { return getFirst("content-length"); }
    public void setContentLength(long length) { set("content-length", String.valueOf(length)); }
    
    public String getHost() { return getFirst("host"); }
    public String getUserAgent() { return getFirst("user-agent"); }
}
```

#### HttpRequest.java
```java
package server.core.http;

import java.util.Map;
import java.util.HashMap;

public class HttpRequest {
    private final HttpMethod method;
    private final String path;
    private final String queryString;
    private final HttpHeaders headers;
    private final byte[] body;
    private final Map<String, String> pathParameters = new HashMap<>();
    private final Map<String, Object> attributes = new HashMap<>();
    
    public HttpRequest(HttpMethod method, String path, String queryString, 
                      HttpHeaders headers, byte[] body) {
        this.method = method;
        this.path = path;
        this.queryString = queryString;
        this.headers = headers;
        this.body = body != null ? body : new byte[0];
    }
    
    // Getter 메소드들
    public HttpMethod getMethod() { return method; }
    public String getPath() { return path; }
    public String getQueryString() { return queryString; }
    public HttpHeaders getHeaders() { return headers; }
    public byte[] getBody() { return body; }
    public String getBodyAsString() { return new String(body); }
    
    // 경로 파라미터 (라우팅에서 설정)
    public void setPathParameter(String name, String value) {
        pathParameters.put(name, value);
    }
    
    public String getPathParameter(String name) {
        return pathParameters.get(name);
    }
    
    // 쿼리 파라미터 파싱
    public Map<String, String> getQueryParameters() {
        Map<String, String> params = new HashMap<>();
        if (queryString != null && !queryString.isEmpty()) {
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return params;
    }
    
    // 속성 관리 (필터 체인에서 사용)
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }
    
    public Object getAttribute(String name) {
        return attributes.get(name);
    }
}
```

#### HttpResponse.java
```java
package server.core.http;

public class HttpResponse {
    private HttpStatus status = HttpStatus.OK;
    private final HttpHeaders headers = new HttpHeaders();
    private byte[] body = new byte[0];
    
    // 생성자
    public HttpResponse() {}
    
    public HttpResponse(HttpStatus status) {
        this.status = status;
    }
    
    // 편의 생성 메소드들
    public static HttpResponse ok() {
        return new HttpResponse(HttpStatus.OK);
    }
    
    public static HttpResponse ok(String body) {
        HttpResponse response = new HttpResponse(HttpStatus.OK);
        response.setBody(body);
        response.headers.setContentType("text/plain; charset=UTF-8");
        return response;
    }
    
    public static HttpResponse json(String json) {
        HttpResponse response = new HttpResponse(HttpStatus.OK);
        response.setBody(json);
        response.headers.setContentType("application/json; charset=UTF-8");
        return response;
    }
    
    public static HttpResponse notFound() {
        return new HttpResponse(HttpStatus.NOT_FOUND);
    }
    
    public static HttpResponse serverError() {
        return new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    // Getter/Setter 메소드들
    public HttpStatus getStatus() { return status; }
    public void setStatus(HttpStatus status) { this.status = status; }
    
    public HttpHeaders getHeaders() { return headers; }
    
    public byte[] getBody() { return body; }
    public void setBody(byte[] body) { 
        this.body = body;
        headers.setContentLength(body.length);
    }
    public void setBody(String body) { 
        setBody(body.getBytes()); 
    }
    
    public String getBodyAsString() { return new String(body); }
}
```

### 1.2 HTTP 파서 구현 (2일)

#### HttpParser.java
```java
package server.core.http;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class HttpParser {
    
    public static HttpRequest parseRequest(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream, StandardCharsets.UTF_8)
        );
        
        // 요청 라인 파싱 (GET /path?query HTTP/1.1)
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.trim().isEmpty()) {
            throw new IOException("Invalid HTTP request: empty request line");
        }
        
        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            throw new IOException("Invalid HTTP request line: " + requestLine);
        }
        
        HttpMethod method = HttpMethod.fromString(parts[0]);
        String fullPath = parts[1];
        String httpVersion = parts[2];
        
        // 경로와 쿼리스트링 분리
        String path;
        String queryString = null;
        int queryIndex = fullPath.indexOf('?');
        if (queryIndex != -1) {
            path = fullPath.substring(0, queryIndex);
            queryString = fullPath.substring(queryIndex + 1);
        } else {
            path = fullPath;
        }
        
        // 헤더 파싱
        HttpHeaders headers = new HttpHeaders();
        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.trim().isEmpty()) {
            int colonIndex = headerLine.indexOf(':');
            if (colonIndex != -1) {
                String name = headerLine.substring(0, colonIndex).trim();
                String value = headerLine.substring(colonIndex + 1).trim();
                headers.add(name, value);
            }
        }
        
        // 바디 읽기
        byte[] body = readBody(reader, headers);
        
        return new HttpRequest(method, path, queryString, headers, body);
    }
    
    private static byte[] readBody(BufferedReader reader, HttpHeaders headers) throws IOException {
        String contentLengthStr = headers.getContentLength();
        if (contentLengthStr == null) {
            return new byte[0];
        }
        
        try {
            int contentLength = Integer.parseInt(contentLengthStr);
            if (contentLength <= 0) {
                return new byte[0];
            }
            
            char[] buffer = new char[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = reader.read(buffer, totalRead, contentLength - totalRead);
                if (read == -1) break;
                totalRead += read;
            }
            
            return new String(buffer, 0, totalRead).getBytes(StandardCharsets.UTF_8);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid Content-Length header: " + contentLengthStr);
        }
    }
}
```

#### HttpBuilder.java
```java
package server.core.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpBuilder {
    
    public static byte[] buildResponse(HttpResponse response) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // 상태 라인 작성
            String statusLine = String.format("HTTP/1.1 %d %s\r\n", 
                response.getStatus().getCode(), 
                response.getStatus().getReasonPhrase());
            baos.write(statusLine.getBytes(StandardCharsets.UTF_8));
            
            // 헤더 작성
            HttpHeaders headers = response.getHeaders();
            for (String name : headers.getNames()) {
                for (String value : headers.get(name)) {
                    String headerLine = name + ": " + value + "\r\n";
                    baos.write(headerLine.getBytes(StandardCharsets.UTF_8));
                }
            }
            
            // 헤더와 바디 구분자
            baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
            
            // 바디 작성
            if (response.getBody().length > 0) {
                baos.write(response.getBody());
            }
            
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to build HTTP response", e);
        }
    }
}
```

### 1.3 라우팅 시스템 구현 (2일)

#### RouteHandler.java
```java
package server.core.routing;

import server.core.http.HttpRequest;
import server.core.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface RouteHandler {
    CompletableFuture<HttpResponse> handle(HttpRequest request);
    
    // 편의 메소드: 동기식 핸들러를 비동기로 래핑
    static RouteHandler sync(SyncRouteHandler syncHandler) {
        return request -> CompletableFuture.completedFuture(syncHandler.handle(request));
    }
}

@FunctionalInterface
interface SyncRouteHandler {
    HttpResponse handle(HttpRequest request);
}
```

#### Route.java
```java
package server.core.routing;

import server.core.http.HttpMethod;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map;
import java.util.HashMap;

public class Route {
    private final HttpMethod method;
    private final String pattern;
    private final Pattern compiledPattern;
    private final RouteHandler handler;
    private final String[] parameterNames;
    
    public Route(HttpMethod method, String pattern, RouteHandler handler) {
        this.method = method;
        this.pattern = pattern;
        this.handler = handler;
        this.parameterNames = extractParameterNames(pattern);
        this.compiledPattern = compilePattern(pattern);
    }
    
    public RouteMatchResult match(HttpMethod requestMethod, String path) {
        if (!method.equals(requestMethod)) {
            return null;
        }
        
        Matcher matcher = compiledPattern.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        
        Map<String, String> pathParams = new HashMap<>();
        for (int i = 0; i < parameterNames.length; i++) {
            pathParams.put(parameterNames[i], matcher.group(i + 1));
        }
        
        return new RouteMatchResult(this, pathParams);
    }
    
    private String[] extractParameterNames(String pattern) {
        // 패턴에서 {id}, {name} 같은 파라미터 추출
        return pattern.replaceAll("\\{([^}]+)\\}", "$1").split("/");
    }
    
    private Pattern compilePattern(String pattern) {
        // {id} -> ([^/]+) 형태로 정규식 변환
        String regex = pattern.replaceAll("\\{[^}]+\\}", "([^/]+)");
        return Pattern.compile("^" + regex + "$");
    }
    
    public RouteHandler getHandler() { return handler; }
    public HttpMethod getMethod() { return method; }
    public String getPattern() { return pattern; }
}
```

#### RouteMatchResult.java
```java
package server.core.routing;

import java.util.Map;

public class RouteMatchResult {
    private final Route route;
    private final Map<String, String> pathParameters;
    
    public RouteMatchResult(Route route, Map<String, String> pathParameters) {
        this.route = route;
        this.pathParameters = pathParameters;
    }
    
    public Route getRoute() { return route; }
    public Map<String, String> getPathParameters() { return pathParameters; }
}
```

#### Router.java
```java
package server.core.routing;

import server.core.http.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class Router {
    private final List<Route> routes = new ArrayList<>();
    
    public void addRoute(HttpMethod method, String pattern, RouteHandler handler) {
        routes.add(new Route(method, pattern, handler));
    }
    
    // 편의 메소드들
    public void get(String pattern, RouteHandler handler) {
        addRoute(HttpMethod.GET, pattern, handler);
    }
    
    public void post(String pattern, RouteHandler handler) {
        addRoute(HttpMethod.POST, pattern, handler);
    }
    
    public void put(String pattern, RouteHandler handler) {
        addRoute(HttpMethod.PUT, pattern, handler);
    }
    
    public void delete(String pattern, RouteHandler handler) {
        addRoute(HttpMethod.DELETE, pattern, handler);
    }
    
    public CompletableFuture<HttpResponse> route(HttpRequest request) {
        for (Route route : routes) {
            RouteMatchResult match = route.match(request.getMethod(), request.getPath());
            if (match != null) {
                // 경로 파라미터를 요청에 설정
                for (Map.Entry<String, String> entry : match.getPathParameters().entrySet()) {
                    request.setPathParameter(entry.getKey(), entry.getValue());
                }
                
                return route.getHandler().handle(request);
            }
        }
        
        // 일치하는 라우트가 없으면 404
        return CompletableFuture.completedFuture(HttpResponse.notFound());
    }
}
```

### 1.4 필터 체인 구현 (1일)

#### Filter.java
```java
package server.core.filter;

import server.core.http.HttpRequest;
import server.core.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface Filter {
    CompletableFuture<HttpResponse> doFilter(HttpRequest request, FilterChain chain);
}
```

#### FilterChain.java
```java
package server.core.filter;

import server.core.http.HttpRequest;
import server.core.http.HttpResponse;
import server.core.routing.RouteHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FilterChain {
    private final List<Filter> filters;
    private final RouteHandler finalHandler;
    private int currentIndex = 0;
    
    public FilterChain(List<Filter> filters, RouteHandler finalHandler) {
        this.filters = filters;
        this.finalHandler = finalHandler;
    }
    
    public CompletableFuture<HttpResponse> doFilter(HttpRequest request) {
        if (currentIndex < filters.size()) {
            Filter filter = filters.get(currentIndex++);
            return filter.doFilter(request, this);
        } else {
            // 모든 필터를 통과했으면 최종 핸들러 실행
            return finalHandler.handle(request);
        }
    }
}
```

### 1.5 미니 서블릿 API 구현 (2일)

#### MiniServlet.java
```java
package server.core.mini;

import server.core.http.HttpRequest;
import server.core.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public interface MiniServlet {
    
    default void init(MiniContext context) throws Exception {
        // 기본 구현은 아무것도 하지 않음
    }
    
    CompletableFuture<HttpResponse> service(HttpRequest request);
    
    default void destroy() {
        // 기본 구현은 아무것도 하지 않음
    }
}
```

#### MiniAsyncServlet.java
```java
package server.core.mini;

import server.core.http.HttpRequest;
import server.core.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public abstract class MiniAsyncServlet implements MiniServlet {
    
    @Override
    public CompletableFuture<HttpResponse> service(HttpRequest request) {
        switch (request.getMethod()) {
            case GET:
                return doGet(request);
            case POST:
                return doPost(request);
            case PUT:
                return doPut(request);
            case DELETE:
                return doDelete(request);
            default:
                return CompletableFuture.completedFuture(
                    new HttpResponse(HttpStatus.METHOD_NOT_ALLOWED)
                );
        }
    }
    
    protected CompletableFuture<HttpResponse> doGet(HttpRequest request) {
        return methodNotAllowed();
    }
    
    protected CompletableFuture<HttpResponse> doPost(HttpRequest request) {
        return methodNotAllowed();
    }
    
    protected CompletableFuture<HttpResponse> doPut(HttpRequest request) {
        return methodNotAllowed();
    }
    
    protected CompletableFuture<HttpResponse> doDelete(HttpRequest request) {
        return methodNotAllowed();
    }
    
    private CompletableFuture<HttpResponse> methodNotAllowed() {
        return CompletableFuture.completedFuture(
            new HttpResponse(HttpStatus.METHOD_NOT_ALLOWED)
        );
    }
}
```

#### MiniContext.java
```java
package server.core.mini;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MiniContext {
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Map<String, String> initParameters = new ConcurrentHashMap<>();
    
    // 속성 관리
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }
    
    public Object getAttribute(String name) {
        return attributes.get(name);
    }
    
    public void removeAttribute(String name) {
        attributes.remove(name);
    }
    
    // 초기화 파라미터 관리
    public void setInitParameter(String name, String value) {
        initParameters.put(name, value);
    }
    
    public String getInitParameter(String name) {
        return initParameters.get(name);
    }
    
    public Map<String, String> getInitParameters() {
        return new HashMap<>(initParameters);
    }
}
```

### Chapter 1 완료 기준
- [ ] HTTP 기본 클래스들 완전 구현 및 테스트
- [ ] HTTP 파서로 실제 요청 파싱 가능
- [ ] 라우팅 시스템으로 RESTful API 매핑 가능
- [ ] 필터 체인으로 횡단 관심사 처리 가능
- [ ] 미니 서블릿으로 간단한 웹 애플리케이션 작성 가능

---

## 📋 Chapter 2: Threaded 서버 구현 (1주)

**목표**: 전통적인 Thread-per-Request 방식의 HTTP 서버 구현  
**위치**: `src/main/java/threaded/`  
**핵심**: 스레드풀 + 블로킹 I/O 방식

### 2.1 기본 서버 구조 (2일)

#### ThreadedServer.java
```java
package threaded;

import server.core.routing.Router;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadedServer {
    private final int port;
    private final Router router;
    private final ExecutorService threadPool;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    
    public ThreadedServer(int port, Router router, int threadPoolSize) {
        this.port = port;
        this.router = router;
        this.threadPool = Executors.newFixedThreadPool(threadPoolSize);
    }
    
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        
        System.out.println("Threaded Server started on port " + port);
        
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                // 요청당 스레드 할당하여 처리
                threadPool.submit(new ThreadedRequestHandler(clientSocket, router));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }
    
    public void stop() throws IOException {
        running = false;
        if (serverSocket != null) {
            serverSocket.close();
        }
        threadPool.shutdown();
    }
}
```

#### ThreadedRequestHandler.java
```java
package threaded;

import server.core.http.*;
import server.core.routing.Router;
import java.net.Socket;
import java.io.*;

public class ThreadedRequestHandler implements Runnable {
    private final Socket clientSocket;
    private final Router router;
    
    public ThreadedRequestHandler(Socket clientSocket, Router router) {
        this.clientSocket = clientSocket;
        this.router = router;
    }
    
    @Override
    public void run() {
        try (Socket socket = clientSocket;
             InputStream input = socket.getInputStream();
             OutputStream output = socket.getOutputStream()) {
            
            // HTTP 요청 파싱 (블로킹)
            HttpRequest request = HttpParser.parseRequest(input);
            
            // 라우팅 및 처리 (블로킹)
            HttpResponse response = router.route(request).get(); // .get()으로 블로킹
            
            // HTTP 응답 전송 (블로킹)
            byte[] responseBytes = HttpBuilder.buildResponse(response);
            output.write(responseBytes);
            output.flush();
            
        } catch (Exception e) {
            System.err.println("Error handling request: " + e.getMessage());
        }
    }
}
```

### 2.2 스레드풀 관리 (1일)

#### ThreadPoolManager.java
```java
package threaded;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolManager {
    private final ExecutorService threadPool;
    private final ThreadPoolMonitor monitor;
    
    public ThreadPoolManager(int corePoolSize, int maximumPoolSize, 
                           long keepAliveTime, TimeUnit unit) {
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            keepAliveTime,
            unit,
            new LinkedBlockingQueue<>(),
            new CustomThreadFactory("threaded-server"),
            new ThreadPoolExecutor.CallerRunsPolicy() // 백프레셔 정책
        );
        
        this.threadPool = executor;
        this.monitor = new ThreadPoolMonitor(executor);
    }
    
    public Future<?> submit(Runnable task) {
        return threadPool.submit(task);
    }
    
    public void shutdown() {
        monitor.shutdown();
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    public ThreadPoolStats getStats() {
        return monitor.getStats();
    }
    
    private static class CustomThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        
        CustomThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            return t;
        }
    }
}
```

### 2.3 미니 서블릿 컨테이너 (2일)

#### ThreadedMiniServletContainer.java
```java
package threaded;

import server.core.mini.*;
import server.core.http.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ThreadedMiniServletContainer {
    private final Map<String, MiniServlet> servlets = new ConcurrentHashMap<>();
    private final MiniContext context = new MiniContext();
    
    public void addServlet(String path, MiniServlet servlet) {
        try {
            servlet.init(context);
            servlets.put(path, servlet);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize servlet: " + path, e);
        }
    }
    
    public HttpResponse processRequest(HttpRequest request) {
        String path = request.getPath();
        MiniServlet servlet = findServlet(path);
        
        if (servlet == null) {
            return HttpResponse.notFound();
        }
        
        try {
            // 서블릿 처리 (동기식으로 대기)
            return servlet.service(request).get();
        } catch (Exception e) {
            System.err.println("Error processing servlet: " + e.getMessage());
            return HttpResponse.serverError();
        }
    }
    
    private MiniServlet findServlet(String path) {
        // 정확한 매치 우선
        MiniServlet servlet = servlets.get(path);
        if (servlet != null) {
            return servlet;
        }
        
        // 패턴 매칭 (간단한 prefix 매칭)
        for (Map.Entry<String, MiniServlet> entry : servlets.entrySet()) {
            String pattern = entry.getKey();
            if (pattern.endsWith("/*") && 
                path.startsWith(pattern.substring(0, pattern.length() - 2))) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    public void destroy() {
        for (MiniServlet servlet : servlets.values()) {
            try {
                servlet.destroy();
            } catch (Exception e) {
                System.err.println("Error destroying servlet: " + e.getMessage());
            }
        }
        servlets.clear();
    }
}
```

### Chapter 2 완료 기준
- [ ] Thread-per-Request 방식으로 동시 요청 처리
- [ ] 스레드풀 관리 및 모니터링 기능
- [ ] 블로킹 I/O로 요청/응답 처리
- [ ] 미니 서블릿 컨테이너로 웹 애플리케이션 실행
- [ ] 간단한 부하 테스트로 동작 확인

---

## 📋 Chapter 3: Hybrid 서버 구현 (2주)

**목표**: AsyncContext 기반 컨텍스트 스위칭으로 스레드 재활용하는 서버 구현  
**위치**: `src/main/java/hybrid/`  
**핵심**: 스레드풀 + AsyncContext + CompletableFuture

### 3.1 비동기 컨텍스트 관리 (1주)

#### HybridServer.java
```java
package hybrid;

import server.core.routing.Router;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

public class HybridServer {
    private final int port;
    private final Router router;
    private final AsyncContextManager asyncManager;
    private volatile boolean running = false;
    
    public HybridServer(int port, Router router) {
        this.port = port;
        this.router = router;
        this.asyncManager = new AsyncContextManager();
    }
    
    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        running = true;
        
        System.out.println("Hybrid Server started on port " + port);
        
        while (running) {
            Socket clientSocket = serverSocket.accept();
            // 비동기 처리 시작
            handleRequestAsync(clientSocket);
        }
    }
    
    private void handleRequestAsync(Socket clientSocket) {
        CompletableFuture
            .supplyAsync(() -> parseRequest(clientSocket))
            .thenCompose(request -> router.route(request))
            .thenAccept(response -> sendResponse(clientSocket, response))
            .exceptionally(throwable -> {
                handleError(clientSocket, throwable);
                return null;
            });
    }
}
```

#### AsyncContextManager.java
```java
package hybrid;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class AsyncContextManager {
    private final ExecutorService ioThreadPool;
    private final ExecutorService cpuThreadPool;
    private final ScheduledExecutorService timeoutExecutor;
    private final AtomicLong activeContexts = new AtomicLong(0);
    
    public AsyncContextManager() {
        this.ioThreadPool = Executors.newCachedThreadPool();
        this.cpuThreadPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
        );
        this.timeoutExecutor = Executors.newScheduledThreadPool(2);
    }
    
    public <T> CompletableFuture<T> executeIO(Callable<T> ioTask) {
        activeContexts.incrementAndGet();
        
        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    return ioTask.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, ioThreadPool)
            .whenComplete((result, throwable) -> {
                activeContexts.decrementAndGet();
            });
    }
    
    public <T> CompletableFuture<T> executeCPU(Callable<T> cpuTask) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return cpuTask.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, cpuThreadPool);
    }
    
    public <T> CompletableFuture<T> withTimeout(CompletableFuture<T> future, 
                                               long timeout, TimeUnit unit) {
        CompletableFuture<T> timeoutFuture = new CompletableFuture<>();
        
        // 원본 작업 완료 시 결과 전달
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                timeoutFuture.completeExceptionally(throwable);
            } else {
                timeoutFuture.complete(result);
            }
        });
        
        // 타임아웃 스케줄링
        timeoutExecutor.schedule(() -> {
            timeoutFuture.completeExceptionally(
                new TimeoutException("Operation timed out after " + timeout + " " + unit)
            );
        }, timeout, unit);
        
        return timeoutFuture;
    }
}
```

### 3.2 컨텍스트 스위칭 구현 (1주)

#### ContextSwitchingHandler.java
```java
package hybrid;

import server.core.http.*;
import server.core.routing.RouteHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ContextSwitchingHandler implements RouteHandler {
    private final AsyncContextManager asyncManager;
    private final RouteHandler delegateHandler;
    
    public ContextSwitchingHandler(RouteHandler delegateHandler, 
                                 AsyncContextManager asyncManager) {
        this.delegateHandler = delegateHandler;
        this.asyncManager = asyncManager;
    }
    
    @Override
    public CompletableFuture<HttpResponse> handle(HttpRequest request) {
        // 요청 처리를 비동기로 시작
        CompletableFuture<HttpResponse> future = delegateHandler.handle(request);
        
        // 10초 타임아웃 설정
        CompletableFuture<HttpResponse> timeoutFuture = 
            asyncManager.withTimeout(future, 10, TimeUnit.SECONDS);
        
        return timeoutFuture.handle((response, throwable) -> {
            if (throwable instanceof TimeoutException) {
                // 타임아웃 시 별도 스레드풀로 위임
                System.out.println("Request timed out, delegating to background thread");
                return handleLongRunningTask(request);
            } else if (throwable != null) {
                return HttpResponse.serverError();
            } else {
                return response;
            }
        }).thenCompose(response -> {
            if (response instanceof CompletableFuture) {
                return (CompletableFuture<HttpResponse>) response;
            } else {
                return CompletableFuture.completedFuture(response);
            }
        });
    }
    
    private CompletableFuture<HttpResponse> handleLongRunningTask(HttpRequest request) {
        // 장기 실행 작업은 별도 스레드풀에서 처리
        return asyncManager.executeIO(() -> {
            // 실제로는 delegateHandler를 다시 호출하거나
            // 특별한 장기 작업 처리 로직 실행
            Thread.sleep(15000); // 15초 작업 시뮬레이션
            return HttpResponse.ok("Long running task completed");
        });
    }
}
```

### Chapter 3 완료 기준
- [ ] AsyncContext로 요청 처리 중 스레드 해제
- [ ] CompletableFuture 체인으로 비동기 처리
- [ ] 타임아웃 시 별도 스레드풀로 작업 위임
- [ ] 스레드 재활용으로 동시성 향상
- [ ] Threaded 서버 대비 성능 향상 확인

---

## 📋 Chapter 4: EventLoop 서버 구현 (2주)

**목표**: NIO Selector 기반 단일 스레드 이벤트루프 서버 구현  
**위치**: `src/main/java/eventloop/`  
**핵심**: 완전 논블로킹 I/O + 이벤트 기반 처리

### 4.1 이벤트루프 핵심 구현 (1주)

#### EventLoop
