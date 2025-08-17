# EventLoopServer - 단일 스레드 이벤트 루프 HTTP 서버

## 개요

EventLoopServer는 **단일 스레드 + NIO Selector** 기반의 완전한 비차단(Non-blocking) HTTP 서버입니다. JavaScript의 Node.js나 Python의 asyncio와 유사한 패턴을 Java로 구현한 것으로, 높은 동시성과 메모리 효율성을 제공합니다.

## 핵심 아키텍처

### 이벤트 루프 패턴

```
Single EventLoop Thread
    ↓
NIO Selector (I/O Multiplexing)
    ↓
Event Queue (Task Scheduling)
    ↓  
Non-blocking Handlers
    ↓
Router & Business Logic
```

### 주요 특징

- **Single Thread Event Loop**: 모든 I/O를 하나의 스레드에서 논블로킹 처리
- **NIO Selector**: 수만 개의 동시 연결을 효율적으로 관리
- **Zero Context Switching**: 스레드 전환 오버헤드 제거
- **Memory Efficient**: 연결당 메모리 사용량 최소화
- **High Throughput**: I/O 집약적 작업에 최적화

## 요청 처리 프로세스

### 1. 연결 수락 (Accept Phase)
```java
// ServerSocketChannel에서 새 연결 감지
SocketChannel clientChannel = serverChannel.accept();
clientChannel.configureBlocking(false);

// SelectorManager에 클라이언트 채널 등록
selectorManager.registerClientSocket(clientChannel, nonBlockingHandler);
```

### 2. 이벤트 루프 처리 (Event Loop Phase)
```java
// 단일 스레드에서 모든 I/O 이벤트 처리
while (running.get()) {
    int readyChannels = selector.select(1000);
    
    for (SelectionKey key : selector.selectedKeys()) {
        if (key.isAcceptable()) handleAccept(key);
        else if (key.isReadable()) handleRead(key);
        else if (key.isWritable()) handleWrite(key);
    }
    
    // 큐에 쌓인 비동기 작업들 처리
    processTasks();
}
```

### 3. 논블로킹 요청 파싱 (Non-blocking Parsing)
```java
// HTTP 요청을 점진적으로 파싱
private void tryParseRequest(SocketChannel channel, ConnectionState state) {
    ByteBuffer combinedBuffer = state.getCombinedBuffer();
    
    // 헤더 끝 찾기 (\r\n\r\n)
    int headerEndIndex = findHeaderEnd(combinedBuffer);
    if (headerEndIndex == -1) {
        return; // 아직 완성되지 않음, 더 기다림
    }
    
    // HTTP 요청 파싱 및 라우터로 전달
    HttpRequest request = parseHttpRequestFromBuffer(combinedBuffer, headerEndIndex);
    processRequestAsync(channel, state, request);
}
```

### 4. 비동기 비즈니스 로직 처리 (Async Processing)
```java
// 라우터를 통한 비동기 처리
CompletableFuture<HttpResponse> responseFuture = router.routeWithMiddlewares(request);

responseFuture.whenComplete((response, error) -> {
    eventQueue.execute(() -> {
        if (error != null) {
            sendErrorResponse(channel, state, HttpStatus.INTERNAL_SERVER_ERROR);
        } else {
            sendResponse(channel, state, response);
        }
    });
});
```

### 5. 논블로킹 응답 전송 (Non-blocking Write)
```java
// 응답을 부분적으로 전송
private boolean writeResponse(SocketChannel channel, ConnectionState state) {
    byte[] responseData = state.getResponseData();
    ByteBuffer buffer = ByteBuffer.wrap(responseData, writeOffset, bufferSize);
    
    int bytesWritten = channel.write(buffer);
    state.addWriteOffset(bytesWritten);
    
    return state.getWriteOffset() >= responseData.length;
}
```

## 핵심 컴포넌트

### 이벤트 루프 레이어

#### 1. Core Event Loop
- `EventLoop.java`: 메인 이벤트 루프 엔진
- `EventQueue.java`: 비동기 작업 큐 및 스케줄링
- `SelectorManager.java`: NIO Selector 관리 및 채널 등록

#### 2. Network Layer
- `NonBlockingHandler.java`: 논블로킹 HTTP 요청/응답 처리
- `ServerSocketEventHandler.java`: 새 연결 수락 처리
- `ClientSocketEventHandler.java`: 클라이언트 I/O 이벤트 처리

#### 3. Application Layer
- `EventLoopServer.java`: 메인 서버 클래스
- `EventLoopProcessor.java`: 이벤트 처리 파이프라인 관리
- `EventLoopMiniServletContainer.java`: 서블릿 컨테이너 (선택사항)

#### 4. Utility Components
- `ByteBufferInputStream.java`: ByteBuffer를 InputStream으로 변환

## 파일별 상세 설명

### 🌟 핵심 이벤트 루프 파일

#### `EventLoop.java`
**역할**: 단일 스레드 이벤트 루프의 핵심 엔진
- NIO Selector를 통한 I/O 이벤트 멀티플렉싱
- 비동기 작업 큐 관리 및 실행
- 이벤트 루프 생명주기 관리
- 성능 통계 수집 및 모니터링

**핵심 메서드**:
- `runEventLoop()`: 메인 이벤트 루프 실행
- `processIOEvents()`: I/O 이벤트 처리 (Accept/Read/Write)
- `processTasks()`: 큐에 쌓인 비동기 작업 처리
- `execute()`: 비동기 작업을 이벤트 루프에 스케줄링

#### `EventQueue.java`
**역할**: 비동기 작업 큐 및 스케줄링 관리
- 즉시 실행, 지연 실행, 주기적 실행 지원
- CompletableFuture 기반 비동기 작업 체인
- 타임아웃이 있는 작업 실행
- Promise 패턴 지원

**핵심 기능**:
- `executeAsync()`: 비동기 작업 실행 (CompletableFuture 반환)
- `scheduleAsync()`: 지연 실행 비동기 작업
- `chain()`: 작업 체인 실행
- `retry()`: 재시도 로직 지원

#### `SelectorManager.java`
**역할**: NIO Selector와 채널 관리
- 서버/클라이언트 소켓 채널 등록
- 채널별 읽기/쓰기 이벤트 관리
- 연결 타임아웃 및 정리
- 채널 통계 및 모니터링

**핵심 기능**:
- `registerServerSocket()`: 서버 소켓 등록
- `registerClientSocket()`: 클라이언트 소켓 등록
- `enableWrite()` / `disableWrite()`: 쓰기 이벤트 제어
- `cleanupTimeoutConnections()`: 타임아웃 연결 정리

### 🔄 네트워크 처리 파일

#### `NonBlockingHandler.java`
**역할**: 논블로킹 HTTP 요청/응답 처리의 핵심
- HTTP 프로토콜 점진적 파싱
- 논블로킹 방식의 요청 읽기/응답 쓰기
- 연결 상태 관리 (Reading → Processing → Writing)
- Keep-Alive 연결 지원

**핵심 기능**:
- `tryParseRequest()`: 논블로킹 HTTP 요청 파싱
- `processRequestAsync()`: 비동기 요청 처리
- `writeResponse()`: 논블로킹 응답 전송
- `ConnectionState`: 연결별 상태 관리 클래스

#### `ServerSocketEventHandler.java` / `ClientSocketEventHandler.java`
**역할**: 이벤트 기반 네트워크 처리 인터페이스
- 새 연결 수락 이벤트 처리
- 클라이언트 읽기/쓰기/연결 해제 이벤트 처리
- 이벤트 기반 아키텍처의 핵심 추상화

### 🎯 서버 관리 파일

#### `EventLoopServer.java`
**역할**: EventLoop 서버의 메인 클래스
- 서버 생명주기 관리 (시작/종료)
- 라우트 등록 편의 메서드 제공
- 기본 엔드포인트 및 정적 파일 서빙
- JVM 종료 훅 및 우아한 종료

**핵심 기능**:
- `start()`: 서버 시작 및 초기화
- `get()`, `post()`: HTTP 라우트 등록
- `generateWelcomePage()`: 환영 페이지 생성
- `awaitShutdown()`: 서버 종료 대기

#### `EventLoopProcessor.java`
**역할**: 이벤트 처리 파이프라인 관리
- EventLoop, SelectorManager, EventQueue 조합
- 리소스 정리 및 주기적 작업 스케줄링
- 서버 통계 수집 및 모니터링
- 설정 관리 (타임아웃, 버퍼 크기 등)

### 🚀 비즈니스 로직 서블릿

#### CPU 집약적 작업 처리

**`CpuIntensiveServlet.java`**
- **특징**: EventLoop 메인 스레드 블로킹 방지
- **구현**: 별도 CPU 워커 스레드풀 사용
- **최적화**: ThreadedServer와 동일한 계산을 비동기로 처리

```java
// CPU 작업을 별도 스레드풀로 위임
return CompletableFuture.supplyAsync(() -> {
    double result = 0.0;
    for (int i = 0; i < 100000; i++) {
        result += Math.sqrt(i) * Math.sin(i);
    }
    return HttpResponse.json(...);
}, cpuExecutor);
```

#### I/O 시뮬레이션 처리

**`IoSimulationServlet.java`**
- **특징**: 논블로킹 I/O 시뮬레이션
- **구현**: 별도 I/O 워커 스레드풀 사용
- **호환성**: ThreadedServer와 동일한 Thread.sleep(100) 수행

```java
// I/O 작업을 별도 스레드풀로 위임
return CompletableFuture.supplyAsync(() -> {
    Thread.sleep(100); // ThreadedServer와 동일한 지연
    return HttpResponse.json(...);
}, ioExecutor);
```

#### 기본 서블릿들

**`HelloWorldServlet.java`**
- 간단한 텍스트/JSON 응답
- 쿼리 파라미터 처리
- 즉시 완료되는 CompletableFuture 반환

**`HealthServlet.java`**
- 서버 상태 확인
- 헬스체크 엔드포인트
- 실시간 스레드 정보 포함

### 🧪 테스트 및 유틸리티

#### `EventLoopServerTest.java`
**역할**: EventLoop 서버 테스트 및 벤치마크
- ThreadedServer와 동일한 엔드포인트 제공
- 성능 비교를 위한 벤치마크 라우트
- 이벤트 루프 특화 테스트 케이스

#### `ByteBufferInputStream.java`
**역할**: ByteBuffer와 InputStream 브릿지
- 기존 HttpParser와의 호환성 제공
- ByteBuffer 데이터를 스트림으로 변환
- mark/reset 지원으로 파싱 효율성 향상

### 📦 서블릿 컨테이너 (선택사항)

#### `EventLoopMiniServletContainer.java`
**역할**: 이벤트 기반 서블릿 컨테이너
- EventLoop 아키텍처와 미니 서블릿 API 연결
- 비동기/동기 서블릿 모두 지원
- JSP 스타일 서블릿, 필터 체인, 세션 관리 지원

## 성능 특성

### 동시성 모델

```java
// 단일 스레드에서 수만 개 연결 처리
while (running.get()) {
    // 1. I/O 이벤트 감지 (논블로킹)
    int readyChannels = selector.select(1000);
    
    // 2. 준비된 채널들 처리
    for (SelectionKey key : selector.selectedKeys()) {
        // 각 이벤트를 즉시 처리 (컨텍스트 스위칭 없음)
        handleEvent(key);
    }
    
    // 3. 비동기 작업들 처리
    processTasks();
}
```

### 메모리 효율성

```java
// 연결당 매우 작은 메모리 사용
class ConnectionState {
    private final List<ByteBuffer> bufferChain;    // 필요한 만큼만
    private State state;                           // 단순 enum
    private HttpRequest request;                   // 파싱 완료시에만
    private byte[] responseData;                   // 응답시에만
}
```

### 비동기 작업 처리

```java
// CPU 집약적 작업은 별도 스레드풀로 위임
CompletableFuture.supplyAsync(() -> {
    // 무거운 계산 작업
    return heavyComputation();
}, cpuExecutor).thenApply(result -> {
    // 결과를 이벤트 루프에서 처리
    return HttpResponse.json(result);
});
```

## 사용 방법

### 서버 시작

```bash
# 기본 실행
java server.eventloop.EventLoopServerTest

# 포트 8082에서 시작됨
```

### 기본 라우트 등록

```java
EventLoopServer server = new EventLoopServer();

// GET 라우트
server.get("/hello", RouteHandler.sync(request -> 
    HttpResponse.text("Hello from EventLoop!")
));

// 비동기 라우트
server.get("/async", request -> 
    CompletableFuture.supplyAsync(() -> {
        // 비동기 작업
        return HttpResponse.json("{\"message\":\"async response\"}");
    })
);

server.start(8082);
```

### 엔드포인트 테스트

```bash
# 기본 테스트
curl http://localhost:8082/hello

# 서버 정보
curl http://localhost:8082/server/info

# 실시간 통계
curl http://localhost:8082/server/stats

# 동시성 테스트 (EventLoop의 강점)
for i in {1..1000}; do 
  curl http://localhost:8082/concurrent & 
done
```

## ThreadedServer vs HybridServer vs EventLoopServer 비교

| 특성 | ThreadedServer | HybridServer | EventLoopServer |
|------|----------------|--------------|-----------------|
| **동시성 모델** | Thread per Request | NIO + Thread Pool | Single Thread Event Loop |
| **메모리 사용** | 요청당 ~8MB | 연결당 ~1KB | 연결당 ~100B |
| **동시 연결** | ~수백 개 | ~수만 개 | ~수만 개 |
| **I/O 처리** | Blocking I/O | Non-blocking I/O | Non-blocking I/O |
| **CPU 처리** | 동일 스레드 | Thread Pool | 별도 스레드풀 위임 |
| **컨텍스트 스위칭** | 빈번함 | 제한적 | 없음 |
| **확장성** | 제한적 | 높음 | 매우 높음 |
| **적합한 용도** | 간단한 앱 | 복합 워크로드 | I/O 집약적 앱 |

## 장단점 분석

### ✅ EventLoop 서버의 장점

1. **극도로 높은 동시성**
    - 단일 스레드로 수만 개 연결 처리
    - 메모리 사용량 최소화

2. **Zero 컨텍스트 스위칭**
    - CPU 효율성 극대화
    - 예측 가능한 성능

3. **간단한 동시성 모델**
    - 경쟁 조건(Race Condition) 없음
    - 락(Lock) 불필요

4. **뛰어난 I/O 처리 능력**
    - 웹 API, 마이크로서비스에 최적
    - 실시간 애플리케이션에 적합

### ⚠️ EventLoop 서버의 제약사항

1. **CPU 집약적 작업 주의**
    - 메인 스레드 블로킹 방지 필요
    - 별도 스레드풀로 위임 필수

2. **단일 스레드 의존성**
    - 예외 발생시 전체 서버 영향
    - 디버깅 복잡성 증가

3. **학습 곡선**
    - 비동기 프로그래밍 패러다임 이해 필요
    - 콜백 헬(Callback Hell) 가능성

## 모니터링 및 통계

### 실시간 메트릭

```java
// 이벤트 루프 통계
EventLoop eventLoop = server.getProcessor().getEventLoop();
// - 총 루프 횟수
// - 실행된 작업 수
// - 평균 루프 시간
// - 큐에 대기 중인 작업 수

// 셀렉터 통계  
SelectorStats selectorStats = selectorManager.getStats();
// - 총 연결 수
// - 활성 연결 수
// - 읽은/쓴 바이트 수

// 핸들러 통계
HandlerStats handlerStats = handler.getStats();
// - 활성 연결 수
// - 연결별 생존 시간
```

### 성능 튜닝 포인트

```java
// 프로세서 설정
EventLoopProcessor.ProcessorConfig config = new EventLoopProcessor.ProcessorConfig()
    .setCleanupInterval(30)        // 정리 주기 (초)
    .setConnectionTimeout(30000)   // 연결 타임아웃 (ms)
    .setMaxRequestSize(1024*1024)  // 최대 요청 크기
    .setResponseBufferSize(8192);  // 응답 버퍼 크기

EventLoopServer server = new EventLoopServer(router, config);
```

## 확장 포인트

### 커스텀 이벤트 처리

```java
// 커스텀 이벤트 핸들러
public class CustomHandler implements ClientSocketEventHandler {
    @Override
    public void onRead(EventLoop eventLoop, SocketChannel channel, ByteBuffer buffer) {
        // 커스텀 읽기 로직
    }
    
    @Override
    public void onWrite(EventLoop eventLoop, SocketChannel channel) {
        // 커스텀 쓰기 로직
    }
}
```

### 비동기 작업 체인

```java
// 복잡한 비동기 워크플로우
eventQueue.executeAsync(() -> fetchUserData(userId))
    .thenCompose(user -> eventQueue.executeAsync(() -> fetchUserPosts(user)))
    .thenCompose(posts -> eventQueue.executeAsync(() -> enrichPosts(posts)))
    .thenApply(enrichedPosts -> HttpResponse.json(enrichedPosts))
    .exceptionally(error -> HttpResponse.internalServerError("Error: " + error.getMessage()));
```

### 미들웨어 체인

```java
// EventLoop 서버용 미들웨어
router.use((request, next) -> {
    long start = System.nanoTime();
    
    return next.handle(request).thenApply(response -> {
        long duration = (System.nanoTime() - start) / 1_000_000;
        response.setHeader("X-Response-Time", duration + "ms");
        return response;
    });
});
```

## 결론

EventLoopServer는 **I/O 집약적이고 높은 동시성이 필요한 애플리케이션**에 최적화된 아키텍처입니다. 단일 스레드 이벤트 루프를 통해 **메모리 효율성과 높은 처리량**을 달성하며, 특히 **웹 API, 마이크로서비스, 실시간 애플리케이션**에 적합합니다.

CPU 집약적 작업은 별도 스레드풀로 위임하고, 비동기 프로그래밍 패턴을 적절히 활용하면 **ThreadedServer나 HybridServer보다 훨씬 높은 성능**을 달성할 수 있습니다.