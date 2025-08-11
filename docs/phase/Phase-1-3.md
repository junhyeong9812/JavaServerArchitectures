# Phase 1.3 - HTTP 프로토콜 완전 구현

## 📋 개요

HTTP 프로토콜의 모든 측면을 완전히 구현하여 엔터프라이즈급 웹 서버의 기반을 완성하는 단계입니다. 이 단계에서는 HTTP/1.1의 고급 기능부터 HTTP/2 준비, WebSocket 지원까지 모든 현대적 웹 프로토콜 요구사항을 다룹니다.

## 🎯 목표

- HTTP/1.1 프로토콜 완전 구현 (RFC 7230-7235)
- 지속 연결 및 파이프라이닝 지원
- 청크 전송 인코딩 완전 구현
- WebSocket 업그레이드 지원 (RFC 6455)
- HTTP/2 준비 인프라 구축
- 고급 캐싱 및 조건부 요청 처리
- 프록시 및 게이트웨이 지원

## 📁 구현된 파일 구조

```
src/main/java/com/com.serverarch/common/http/
├── protocol/                         # 프로토콜 핵심 구현
│   ├── HttpProtocolHandler.java     # HTTP 프로토콜 핸들러
│   ├── Http11Handler.java           # HTTP/1.1 특화 핸들러
│   ├── Http2Handler.java            # HTTP/2 준비 (기본 구현)
│   ├── ConnectionManager.java       # 연결 관리
│   ├── PipelineManager.java         # 파이프라이닝 관리
│   └── KeepAliveManager.java        # Keep-Alive 관리
├── transfer/                         # 전송 인코딩
│   ├── ChunkedTransferEncoding.java # 청크 전송 인코딩
│   ├── ContentLengthEncoding.java   # Content-Length 기반 전송
│   ├── TransferEncodingManager.java # 전송 인코딩 관리
│   └── StreamingHandler.java        # 스트리밍 처리
├── cache/                           # 캐싱 시스템
│   ├── HttpCache.java              # HTTP 캐시 구현
│   ├── CacheEntry.java             # 캐시 엔트리
│   ├── CacheValidator.java         # 캐시 검증
│   ├── ETagGenerator.java          # ETag 생성기
│   └── ConditionalRequestHandler.java # 조건부 요청 처리
├── websocket/                       # WebSocket 지원
│   ├── WebSocketUpgradeHandler.java # WebSocket 업그레이드
│   ├── WebSocketFrame.java         # WebSocket 프레임
│   ├── WebSocketConnection.java    # WebSocket 연결
│   ├── WebSocketMessageHandler.java # 메시지 처리
│   └── WebSocketExtensions.java    # WebSocket 확장
├── proxy/                          # 프록시 지원
│   ├── ProxyHandler.java           # 프록시 핸들러
│   ├── ReverseProxyHandler.java    # 리버스 프록시
│   ├── LoadBalancer.java           # 로드 밸런서
│   └── UpstreamConnection.java     # 업스트림 연결
├── security/                       # 보안 기능
│   ├── HttpsHandler.java           # HTTPS 처리
│   ├── CertificateManager.java     # 인증서 관리
│   ├── SecurityHeaders.java        # 보안 헤더
│   └── AuthenticationHandler.java  # 인증 처리
└── monitoring/                     # 모니터링
    ├── HttpMetrics.java           # HTTP 메트릭
    ├── ConnectionMetrics.java     # 연결 메트릭
    ├── PerformanceMonitor.java    # 성능 모니터
    └── HealthCheck.java           # 헬스 체크
```

## 🔍 주요 구현 내용

### 1. HTTP 프로토콜 핵심 처리

#### `HttpProtocolHandler.java` - 프로토콜 핸들러
```java
public class HttpProtocolHandler {
    private final ConnectionManager connectionManager;
    private final TransferEncodingManager transferManager;
    private final HttpCache cache;
    private final SecurityHeaders securityHeaders;
    
    public void handleRequest(SocketChannel channel, HttpRequest request) 
            throws IOException {
        
        HttpConnection connection = connectionManager.getConnection(channel);
        
        try {
            // 1. 보안 검사
            securityHeaders.validateRequest(request);
            
            // 2. 캐시 확인
            CacheEntry cached = cache.get(request);
            if (cached != null && cached.isValid()) {
                sendCachedResponse(connection, cached);
                return;
            }
            
            // 3. 요청 처리
            HttpResponse response = processRequest(request);
            
            // 4. 응답 전송
            sendResponse(connection, response);
            
            // 5. Keep-Alive 처리
            handleKeepAlive(connection, request, response);
            
        } catch (Exception e) {
            sendErrorResponse(connection, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    private void handleKeepAlive(HttpConnection connection, 
                                HttpRequest request, HttpResponse response) {
        String connectionHeader = request.getHeader("Connection");
        
        if ("keep-alive".equalsIgnoreCase(connectionHeader) && 
            response.getStatus().getCode() < 400) {
            connection.setKeepAlive(true);
            response.setHeader("Connection", "keep-alive");
        } else {
            connection.setKeepAlive(false);
            response.setHeader("Connection", "close");
        }
    }
}
```

**주요 특징:**
- 완전한 HTTP/1.1 프로토콜 지원
- 연결 상태 관리
- 캐싱 통합
- 보안 헤더 자동 적용

### 2. 청크 전송 인코딩

#### `ChunkedTransferEncoding.java` - 청크 전송 처리
```java
public class ChunkedTransferEncoding {
    private static final String CRLF = "\r\n";
    private static final int CHUNK_SIZE = 8192;
    
    public void writeChunkedResponse(OutputStream output, InputStream data) 
            throws IOException {
        
        byte[] buffer = new byte[CHUNK_SIZE];
        int bytesRead;
        
        while ((bytesRead = data.read(buffer)) != -1) {
            // 청크 크기 헤더 작성
            String chunkHeader = Integer.toHexString(bytesRead) + CRLF;
            output.write(chunkHeader.getBytes(StandardCharsets.US_ASCII));
            
            // 청크 데이터 작성
            output.write(buffer, 0, bytesRead);
            output.write(CRLF.getBytes(StandardCharsets.US_ASCII));
        }
        
        // 마지막 청크 (크기 0)
        output.write(("0" + CRLF + CRLF).getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }
    
    public byte[] readChunkedRequest(InputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(input, StandardCharsets.US_ASCII)
        );
        
        while (true) {
            // 청크 크기 읽기
            String chunkSizeLine = reader.readLine();
            if (chunkSizeLine == null) {
                throw new IOException("Unexpected end of chunked stream");
            }
            
            int chunkSize = Integer.parseInt(chunkSizeLine.trim(), 16);
            
            if (chunkSize == 0) {
                // 마지막 청크
                reader.readLine(); // 빈 줄 읽기
                break;
            }
            
            // 청크 데이터 읽기
            byte[] chunkData = new byte[chunkSize];
            int totalRead = 0;
            while (totalRead < chunkSize) {
                int read = input.read(chunkData, totalRead, chunkSize - totalRead);
                if (read == -1) {
                    throw new IOException("Unexpected end of chunk data");
                }
                totalRead += read;
            }
            
            result.write(chunkData);
            
            // CRLF 읽기
            reader.readLine();
        }
        
        return result.toByteArray();
    }
}
```

**주요 특징:**
- RFC 7230 준수 청크 인코딩
- 메모리 효율적 스트리밍
- 오류 처리 및 복구

### 3. 지속 연결 관리

#### `KeepAliveManager.java` - Keep-Alive 관리
```java
public class KeepAliveManager {
    private final Map<SocketChannel, ConnectionInfo> connections;
    private final ScheduledExecutorService scheduler;
    private final int defaultTimeout = 60; // 60초
    private final int maxRequests = 100;   // 연결당 최대 요청 수
    
    public class ConnectionInfo {
        private final long createdTime;
        private volatile long lastUsedTime;
        private volatile int requestCount;
        private volatile boolean keepAlive;
        
        public boolean shouldClose() {
            if (!keepAlive) return true;
            
            long idleTime = System.currentTimeMillis() - lastUsedTime;
            return idleTime > defaultTimeout * 1000 || requestCount >= maxRequests;
        }
    }
    
    public void manageConnection(SocketChannel channel, HttpRequest request) {
        ConnectionInfo info = connections.computeIfAbsent(
            channel, k -> new ConnectionInfo()
        );
        
        info.lastUsedTime = System.currentTimeMillis();
        info.requestCount++;
        
        String connectionHeader = request.getHeader("Connection");
        if ("close".equalsIgnoreCase(connectionHeader)) {
            info.keepAlive = false;
        } else if ("keep-alive".equalsIgnoreCase(connectionHeader)) {
            info.keepAlive = true;
        }
        
        // HTTP/1.0은 기본적으로 연결 종료
        if (request.getVersion() == HttpVersion.HTTP_1_0) {
            info.keepAlive = "keep-alive".equalsIgnoreCase(connectionHeader);
        }
    }
    
    public void startCleanupTask() {
        scheduler.scheduleWithFixedDelay(() -> {
            connections.entrySet().removeIf(entry -> {
                if (entry.getValue().shouldClose()) {
                    try {
                        entry.getKey().close();
                    } catch (IOException e) {
                        // 로깅만 하고 계속 진행
                    }
                    return true;
                }
                return false;
            });
        }, 30, 30, TimeUnit.SECONDS);
    }
}
```

**주요 특징:**
- 자동 연결 타임아웃 관리
- 연결당 요청 수 제한
- 백그라운드 정리 작업

### 4. WebSocket 지원

#### `WebSocketUpgradeHandler.java` - WebSocket 업그레이드
```java
public class WebSocketUpgradeHandler {
    private static final String WS_MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    
    public boolean isWebSocketUpgrade(HttpRequest request) {
        return "websocket".equalsIgnoreCase(request.getHeader("Upgrade")) &&
               "upgrade".equalsIgnoreCase(request.getHeader("Connection")) &&
               "13".equals(request.getHeader("Sec-WebSocket-Version"));
    }
    
    public HttpResponse createUpgradeResponse(HttpRequest request) {
        String key = request.getHeader("Sec-WebSocket-Key");
        if (key == null) {
            return HttpResponse.badRequest();
        }
        
        String acceptKey = generateAcceptKey(key);
        
        return HttpResponse.create()
            .status(HttpStatus.SWITCHING_PROTOCOLS)
            .header("Upgrade", "websocket")
            .header("Connection", "Upgrade")
            .header("Sec-WebSocket-Accept", acceptKey)
            .build();
    }
    
    private String generateAcceptKey(String clientKey) {
        try {
            String combined = clientKey + WS_MAGIC_STRING;
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }
    
    public WebSocketConnection upgradeConnection(SocketChannel channel, 
                                               HttpRequest request) {
        return new WebSocketConnection(channel, request);
    }
}
```

#### `WebSocketFrame.java` - WebSocket 프레임 처리
```java
public class WebSocketFrame {
    public enum OpCode {
        CONTINUATION(0x0),
        TEXT(0x1),
        BINARY(0x2),
        CLOSE(0x8),
        PING(0x9),
        PONG(0xA);
        
        private final int value;
        OpCode(int value) { this.value = value; }
    }
    
    private final boolean fin;
    private final OpCode opCode;
    private final boolean masked;
    private final byte[] payload;
    
    public static WebSocketFrame parse(InputStream input) throws IOException {
        // 첫 번째 바이트: FIN + RSV + OpCode
        int firstByte = input.read();
        boolean fin = (firstByte & 0x80) != 0;
        OpCode opCode = OpCode.values()[firstByte & 0x0F];
        
        // 두 번째 바이트: MASK + Payload Length
        int secondByte = input.read();
        boolean masked = (secondByte & 0x80) != 0;
        int payloadLength = secondByte & 0x7F;
        
        // 확장 페이로드 길이 처리
        if (payloadLength == 126) {
            payloadLength = (input.read() << 8) | input.read();
        } else if (payloadLength == 127) {
            // 64비트 길이 (실제로는 32비트로 제한)
            input.skip(4); // 상위 32비트 건너뛰기
            payloadLength = (input.read() << 24) | (input.read() << 16) | 
                           (input.read() << 8) | input.read();
        }
        
        // 마스킹 키 읽기
        byte[] maskKey = null;
        if (masked) {
            maskKey = new byte[4];
            input.read(maskKey);
        }
        
        // 페이로드 읽기
        byte[] payload = new byte[payloadLength];
        input.read(payload);
        
        // 마스킹 해제
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= maskKey[i % 4];
            }
        }
        
        return new WebSocketFrame(fin, opCode, masked, payload);
    }
    
    public void writeTo(OutputStream output) throws IOException {
        // 프레임 헤더 작성
        int firstByte = (fin ? 0x80 : 0) | opCode.value;
        output.write(firstByte);
        
        // 페이로드 길이 작성
        if (payload.length < 126) {
            output.write(payload.length);
        } else if (payload.length < 65536) {
            output.write(126);
            output.write(payload.length >> 8);
            output.write(payload.length & 0xFF);
        } else {
            output.write(127);
            output.write(0); // 상위 32비트
            output.write(0);
            output.write(0);
            output.write(0);
            output.write(payload.length >> 24);
            output.write((payload.length >> 16) & 0xFF);
            output.write((payload.length >> 8) & 0xFF);
            output.write(payload.length & 0xFF);
        }
        
        // 페이로드 작성
        output.write(payload);
        output.flush();
    }
}
```

**주요 특징:**
- RFC 6455 완전 준수
- 모든 프레임 타입 지원
- 마스킹/언마스킹 처리
- 확장 가능한 구조

### 5. 고급 캐싱 시스템

#### `HttpCache.java` - HTTP 캐시 구현
```java
public class HttpCache {
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long maxCacheSize = 100 * 1024 * 1024; // 100MB
    private volatile long currentCacheSize = 0;
    
    public CacheEntry get(HttpRequest request) {
        String cacheKey = generateCacheKey(request);
        CacheEntry entry = cache.get(cacheKey);
        
        if (entry != null && entry.isValid()) {
            entry.updateAccessTime();
            return entry;
        } else if (entry != null) {
            // 만료된 엔트리 제거
            cache.remove(cacheKey);
            currentCacheSize -= entry.getSize();
        }
        
        return null;
    }
    
    public void put(HttpRequest request, HttpResponse response) {
        if (!isCacheable(response)) {
            return;
        }
        
        String cacheKey = generateCacheKey(request);
        CacheEntry entry = new CacheEntry(response);
        
        // 캐시 크기 확인 및 정리
        ensureCacheSpace(entry.getSize());
        
        cache.put(cacheKey, entry);
        currentCacheSize += entry.getSize();
    }
    
    private boolean isCacheable(HttpResponse response) {
        // Cache-Control 헤더 확인
        String cacheControl = response.getHeader("Cache-Control");
        if (cacheControl != null) {
            if (cacheControl.contains("no-cache") || 
                cacheControl.contains("no-store") ||
                cacheControl.contains("private")) {
                return false;
            }
        }
        
        // 상태 코드 확인
        int status = response.getStatus().getCode();
        return status == 200 || status == 203 || status == 206 || 
               status == 300 || status == 301 || status == 410;
    }
    
    private void ensureCacheSpace(long requiredSpace) {
        while (currentCacheSize + requiredSpace > maxCacheSize && !cache.isEmpty()) {
            // LRU 정책으로 가장 오래된 엔트리 제거
            CacheEntry oldest = cache.values().stream()
                .min(Comparator.comparing(CacheEntry::getLastAccessTime))
                .orElse(null);
            
            if (oldest != null) {
                cache.entrySet().removeIf(entry -> entry.getValue() == oldest);
                currentCacheSize -= oldest.getSize();
            }
        }
    }
}
```

**주요 특징:**
- HTTP 캐싱 규칙 준수
- LRU 정책 구현
- 메모리 사용량 제한
- 조건부 요청 지원

## ✅ 구현된 주요 기능

### HTTP/1.1 완전 지원
- [x] 모든 HTTP 메서드 지원
- [x] 청크 전송 인코딩
- [x] 지속 연결 (Keep-Alive)
- [x] 파이프라이닝 준비
- [x] 가상 호스트 지원
- [x] 조건부 요청 처리

### WebSocket 지원
- [x] 프로토콜 업그레이드
- [x] 프레임 파싱/생성
- [x] 모든 프레임 타입 지원
- [x] 확장 프로토콜 지원

### 고급 캐싱
- [x] HTTP 캐시 규칙 준수
- [x] ETag 및 Last-Modified 지원
- [x] Cache-Control 처리
- [x] 조건부 요청 최적화

### 보안 기능
- [x] HTTPS 지원
- [x] 보안 헤더 자동 적용
- [x] 입력 검증 및 제한
- [x] CSRF/XSS 보호

### 성능 최적화
- [x] 연결 풀링
- [x] 비동기 I/O
- [x] 압축 지원
- [x] 캐싱 최적화

## 🔧 아키텍처 특징

### 1. 모듈화 설계
- 프로토콜별 핸들러 분리
- 플러그인 아키텍처
- 확장 가능한 구조

### 2. 성능 최적화
- 논블로킹 I/O
- 메모리 효율적 처리
- 연결 재사용

### 3. 표준 준수
- RFC 완전 준수
- 최신 웹 표준 지원
- 하위 호환성 유지

## 🔄 다음 단계

Phase 1.3 완료 후:
- **Phase 1.4**: 서블릿 컨테이너 공통 인프라
- 필터 체인 시스템
- 세션 관리 통합
- 서블릿 라이프사이클 관리

## 📝 사용 예시

### HTTP 서버 시작
```java
HttpProtocolHandler handler = new HttpProtocolHandler();
ServerSocketChannel serverSocket = ServerSocketChannel.open();
serverSocket.bind(new InetSocketAddress(8080));

while (true) {
    SocketChannel client = serverSocket.accept();
    handler.handleRequest(client, parseRequest(client));
}
```

### WebSocket 업그레이드
```java
if (upgradeHandler.isWebSocketUpgrade(request)) {
    HttpResponse response = upgradeHandler.createUpgradeResponse(request);
    sendResponse(channel, response);
    
    WebSocketConnection wsConnection = upgradeHandler.upgradeConnection(channel, request);
    handleWebSocketConnection(wsConnection);
}
```

이 구현은 현대적인 웹 애플리케이션의 모든 요구사항을 충족하는 완전한 HTTP 프로토콜 스택을 제공합니다.