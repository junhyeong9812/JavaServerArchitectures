# EventLoop Server Architecture (순수 이벤트 루프)

## 📖 개요

EventLoop Server는 **단일 스레드 이벤트 루프 기반**의 HTTP 서버로, Node.js, Netty, Vert.x와 같은 현대적 고성능 서버의 핵심 아키텍처를 구현합니다. **완전한 논블로킹 I/O**와 **이벤트 기반 처리**로 최대 성능과 확장성을 제공합니다.

## 🏗️ 아키텍처 설계

### 핵심 원리
```
단일 이벤트 루프 → NIO Selector → 이벤트 감지 → 논블로킹 처리 → 다음 이벤트
```

### 구성 요소

#### 1. EventLoopServer (메인 서버)
```java
public class EventLoopServer {
    private ServerSocketChannel serverChannel;      // 논블로킹 서버 소켓
    private Selector masterSelector;                // 이벤트 멀티플렉싱
    private EventLoop eventLoop;                    // 핵심 이벤트 루프
    private EventQueue taskQueue;                   // 비동기 작업 큐
    
    // 단일 스레드에서 모든 I/O 이벤트 처리
}
```

#### 2. EventLoop (핵심 이벤트 루프)
```java
public class EventLoop implements Runnable {
    private final Selector selector;
    private final EventQueue eventQueue;
    private volatile boolean running;
    
    @Override
    public void run() {
        while (running) {
            // 1. I/O 이벤트 처리
            selector.select(1); // 1ms 타임아웃
            processSelectedKeys();
            
            // 2. 큐에 있는 태스크 처리
            processEventQueue();
            
            // 3. 타이머 이벤트 처리
            processTimers();
        }
    }
}
```

#### 3. EventQueue (비동기 작업 큐)
```java
public class EventQueue {
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final Queue<DelayedTask> timerTasks = new PriorityQueue<>();
    
    // 외부 스레드에서 이벤트 루프로 작업 전달
    public void execute(Runnable task) {
        tasks.offer(task);
        selector.wakeup(); // 이벤트 루프 깨우기
    }
    
    public void schedule(Runnable task, long delay) {
        timerTasks.offer(new DelayedTask(task, System.currentTimeMillis() + delay));
    }
}
```

#### 4. NonBlockingHandler (논블로킹 핸들러)
```java
public class NonBlockingHandler implements RouteHandler {
    @Override
    public CompletableFuture<HttpResponse> handle(HttpRequest request) {
        // 모든 작업이 논블로킹으로 처리됨
        return performAsyncDatabaseQuery(request)           // 논블로킹 DB
            .thenCompose(data -> processAsyncData(data))    // 논블로킹 처리
            .thenApply(result -> HttpResponse.ok(result))   // 즉시 반환
            .exceptionally(this::handleError);              // 에러 처리
    }
}
```

#### 5. SelectorManager (Selector 관리)
```java
public class SelectorManager {
    private final Selector selector;
    private final Map<SocketChannel, ChannelContext> channels;
    
    public void registerChannel(SocketChannel channel, int ops) {
        channel.configureBlocking(false);
        SelectionKey key = channel.register(selector, ops);
        key.attach(new ChannelContext(channel));
    }
    
    public void processEvents() {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        for (SelectionKey key : selectedKeys) {
            if (key.isAcceptable()) {
                handleAccept(key);
            } else if (key.isReadable()) {
                handleRead(key);
            } else if (key.isWritable()) {
                handleWrite(key);
            }
        }
        selectedKeys.clear();
    }
}
```

## 🔄 요청 처리 흐름

### 상세 처리 단계

1. **이벤트 루프 시작**
   ```java
   public void start() {
       // 단일 스레드에서 무한 루프
       while (running) {
           // I/O 이벤트 대기 (논블로킹)
           int events = selector.select(1); // 1ms 타임아웃
           
           if (events > 0) {
               processNetworkEvents();
           }
           
           // 큐에 쌓인 작업들 처리
           processTaskQueue();
           
           // 타이머 작업 처리
           processTimers();
       }
   }
   ```

2. **연결 수락** (논블로킹)
   ```java
   private void handleAccept(SelectionKey key) {
       ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
       
       // 논블로킹 수락
       SocketChannel clientChannel;
       while ((clientChannel = serverChannel.accept()) != null) {
           clientChannel.configureBlocking(false);
           
           // READ 이벤트 등록
           clientChannel.register(selector, SelectionKey.OP_READ);
       }
   }
   ```

3. **요청 읽기** (논블로킹)
   ```java
   private void handleRead(SelectionKey key) {
       SocketChannel channel = (SocketChannel) key.channel();
       ChannelContext context = (ChannelContext) key.attachment();
       
       try {
           // 논블로킹 읽기
           int bytesRead = channel.read(context.getReadBuffer());
           
           if (bytesRead > 0) {
               context.appendData();
               
               // HTTP 요청 완성 확인
               if (context.isRequestComplete()) {
                   processRequest(context);
               }
           } else if (bytesRead == -1) {
               // 연결 종료
               closeChannel(channel);
           }
       } catch (IOException e) {
           closeChannel(channel);
       }
   }
   ```

4. **비즈니스 로직 처리** (논블로킹)
   ```java
   private void processRequest(ChannelContext context) {
       HttpRequest request = context.parseRequest();
       
       // 라우터에서 핸들러 찾기
       RouteHandler handler = router.findHandler(request);
       
       // 논블로킹 처리
       handler.handle(request)
           .thenAccept(response -> {
               // 응답을 이벤트 루프에 다시 큐잉
               eventQueue.execute(() -> {
                   context.setResponse(response);
                   // WRITE 이벤트 등록
                   context.getChannel().register(selector, SelectionKey.OP_WRITE);
               });
           })
           .exceptionally(throwable -> {
               // 에러 처리도 이벤트 루프에서
               eventQueue.execute(() -> {
                   context.setResponse(HttpResponse.error(500));
                   context.getChannel().register(selector, SelectionKey.OP_WRITE);
               });
               return null;
           });
   }
   ```

5. **응답 전송** (논블로킹)
   ```java
   private void handleWrite(SelectionKey key) {
       SocketChannel channel = (SocketChannel) key.channel();
       ChannelContext context = (ChannelContext) key.attachment();
       
       try {
           // 논블로킹 쓰기
           ByteBuffer writeBuffer = context.getWriteBuffer();
           int bytesWritten = channel.write(writeBuffer);
           
           if (!writeBuffer.hasRemaining()) {
               // 전송 완료
               if (context.isKeepAlive()) {
                   // Keep-Alive: READ 모드로 전환
                   key.interestOps(SelectionKey.OP_READ);
                   context.reset();
               } else {
                   // 연결 종료
                   closeChannel(channel);
               }
           }
       } catch (IOException e) {
           closeChannel(channel);
       }
   }
   ```

## ⚙️ 설정 및 튜닝

### EventLoop 설정
```properties
# eventloop-server.properties
server.port=8082

# 이벤트 루프 설정
server.eventloop.select-timeout=1
server.eventloop.max-events=1024
server.eventloop.buffer-size=8192

# 채널 설정
server.channel.receive-buffer=65536
server.channel.send-buffer=65536
server.channel.tcp-nodelay=true
server.channel.so-reuseaddr=true

# 연결 관리
server.max-connections=50000
server.keep-alive.timeout=60000
server.keep-alive.max-requests=1000

# 타이머 설정
server.timer.resolution=10
server.timer.max-timers=10000

# 백프레셰 설정
server.backpressure.read-buffer-high-water=65536
server.backpressure.write-buffer-high-water=65536
```

### JVM 최적화
```bash
# EventLoop 전용 JVM 옵션
-Xms2g -Xmx2g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=10
-XX:+UseStringDeduplication
-Dio.netty.allocator.type=pooled
-Dio.netty.ioRatio=80
```

## 📊 성능 특성

### 장점 ✅

1. **극한의 확장성**
   - 단일 스레드로 수만 개 동시 연결 처리
   - C10K, C100K 문제 완전 해결
   - 메모리 사용량 최소화 (연결당 수 KB)

2. **최적의 성능**
   - 컨텍스트 스위칭 오버헤드 제거
   - CPU 캐시 친화적 동작
   - 예측 가능한 응답 시간

3. **리소스 효율성**
   - 최소한의 스레드 사용 (1-2개)
   - 낮은 메모리 풋프린트
   - 높은 CPU 활용률

4. **실시간 지원**
   - WebSocket, Server-Sent Events 최적화
   - 지속 연결 관리 효율성
   - 스트리밍 데이터 처리

### 단점 ❌

1. **구현 복잡도**
   - 논블로킹 프로그래밍 학습 곡선
   - 콜백 체인 복잡성
   - 에러 처리 및 디버깅 어려움

2. **CPU 집약적 작업 제약**
   - 단일 스레드에서 블로킹 작업 금지
   - CPU 집약적 작업 시 전체 루프 블로킹
   - 별도 워커 스레드 필요

3. **에코시스템 제약**
   - 논블로킹 라이브러리 필수
   - 기존 동기 코드 사용 불가
   - 학습 리소스 부족

## 📈 성능 지표 (예상 수치)

| 지표 | 수치 | 설명 |
|------|------|------|
| **최대 동시 연결** | 50,000+개 | 메모리가 허용하는 한 확장 |
| **평균 응답 시간** | 1-10ms | 논블로킹으로 지연 최소화 |
| **처리량 (TPS)** | 50,000-200,000+ | 단일 스레드 최대 성능 |
| **메모리 사용량** | 연결당 2-8KB | 최소한의 상태만 유지 |
| **CPU 사용률** | 매우 높음 | 단일 코어 100% 활용 |

## 🛠️ 구현 세부사항

### 1. ChannelContext (연결 상태 관리)
```java
public class ChannelContext {
    private final SocketChannel channel;
    private final ByteBuffer readBuffer;
    private final ByteBuffer writeBuffer;
    private HttpRequest currentRequest;
    private HttpResponse pendingResponse;
    private boolean keepAlive;
    private long lastActivityTime;
    
    public boolean isRequestComplete() {
        // HTTP 요청 완성도 확인
        String data = new String(readBuffer.array(), 0, readBuffer.position());
        return data.contains("\r\n\r\n");
    }
    
    public void reset() {
        // Keep-Alive를 위한 상태 리셋
        readBuffer.clear();
        writeBuffer.clear();
        currentRequest = null;
        pendingResponse = null;
        lastActivityTime = System.currentTimeMillis();
    }
}
```

### 2. 논블로킹 HTTP 파서
```java
public class NonBlockingHttpParser {
    private enum State {
        READING_REQUEST_LINE,
        READING_HEADERS,
        READING_BODY,
        COMPLETE
    }
    
    private State state = State.READING_REQUEST_LINE;
    private StringBuilder requestBuilder = new StringBuilder();
    
    public ParseResult parse(ByteBuffer buffer) {
        while (buffer.hasRemaining() && state != State.COMPLETE) {
            char c = (char) buffer.get();
            
            switch (state) {
                case READING_REQUEST_LINE:
                    if (c == '\n') {
                        parseRequestLine();
                        state = State.READING_HEADERS;
                    } else {
                        requestBuilder.append(c);
                    }
                    break;
                // ... 다른 상태들 처리
            }
        }
        
        return state == State.COMPLETE ? ParseResult.complete() : ParseResult.needMoreData();
    }
}
```

### 3. 백프레셰 제어
```java
public class BackpressureController {
    private static final int HIGH_WATER_MARK = 65536;
    private static final int LOW_WATER_MARK = 8192;
    
    public void checkReadBackpressure(ChannelContext context) {
        if (context.getReadBuffer().position() > HIGH_WATER_MARK) {
            // 읽기 중단
            context.getChannel().register(selector, 0); // 이벤트 해제
        }
    }
    
    public void checkWriteBackpressure(ChannelContext context) {
        if (context.getWriteBuffer().remaining() < LOW_WATER_MARK) {
            // 쓰기 재개
            context.getChannel().register(selector, SelectionKey.OP_WRITE);
        }
    }
}
```

### 4. 타이머 관리
```java
public class TimerManager {
    private final PriorityQueue<TimerTask> timerQueue = 
        new PriorityQueue<>(Comparator.comparing(TimerTask::getExecuteTime));
    
    public void schedule(Runnable task, long delay) {
        long executeTime = System.currentTimeMillis() + delay;
        timerQueue.offer(new TimerTask(task, executeTime));
    }
    
    public void processTimers() {
        long now = System.currentTimeMillis();
        
        while (!timerQueue.isEmpty() && timerQueue.peek().getExecuteTime() <= now) {
            TimerTask task = timerQueue.poll();
            try {
                task.getRunnable().run();
            } catch (Exception e) {
                // 타이머 태스크 에러 로깅
                logger.error("Timer task failed", e);
            }
        }
    }
}
```

## 🎯 적합한 사용 사례

### ✅ 적합한 경우
- **고성능 웹서버** (대용량 트래픽, 낮은 지연시간)
- **실시간 애플리케이션** (채팅, 게임 서버, 라이브 스트리밍)
- **API 게이트웨이** (마이크로서비스 프록시)
- **IoT 게이트웨이** (수만 개 센서 연결)
- **CDN 엣지 서버** (정적 컨텐츠 서빙)

### ❌ 부적합한 경우
- **CPU 집약적 애플리케이션** (이미지 처리, 암호화)
- **블로킹 라이브러리 의존** (기존 JDBC, 파일 I/O)
- **소규모 단순 애플리케이션** (오버엔지니어링)
- **팀의 비동기 경험 부족** (학습 비용 고려)

## 🔍 모니터링 포인트

### 핵심 메트릭
1. **이벤트 루프 상태**
   - Event Loop Lag (이벤트 루프 지연)
   - Events per Second
   - Task Queue Depth

2. **연결 상태**
   - Active Connections
   - Connection Rate (초당 새 연결)
   - Connection Duration

3. **메모리 및 성능**
   - Memory per Connection
   - GC Pause Time
   - CPU Utilization

### 경고 임계값
```properties
# 모니터링 임계값
eventloop.lag.warning=10ms
eventloop.queue.depth.warning=1000
connections.active.warning=40000
memory.per.connection.warning=10KB
gc.pause.warning=5ms
```

## 🚀 최적화 팁

### 1. 버퍼 크기 튜닝
```java
// 연결 특성에 따른 버퍼 크기 조정
public class BufferSizeCalculator {
    public static int calculateReadBufferSize(ConnectionType type) {
        switch (type) {
            case HTTP_API:      return 4096;   // 작은 JSON 요청
            case FILE_UPLOAD:   return 65536;  // 대용량 파일
            case WEBSOCKET:     return 1024;   // 작은 메시지
            default:            return 8192;
        }
    }
}
```

### 2. 객체 풀링
```java
// ByteBuffer 재사용으로 GC 압박 감소
public class BufferPool {
    private final Queue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();
    private final int bufferSize;
    
    public ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            buffer = ByteBuffer.allocateDirect(bufferSize);
        }
        buffer.clear();
        return buffer;
    }
    
    public void release(ByteBuffer buffer) {
        if (pool.size() < MAX_POOL_SIZE) {
            pool.offer(buffer);
        }
    }
}
```

### 3. CPU 집약적 작업 오프로딩
```java
// 별도 워커 스레드풀로 CPU 작업 위임
public class CpuTaskExecutor {
    private final ExecutorService workerPool = 
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    
    public CompletableFuture<String> processCpuIntensiveTask(String data) {
        return CompletableFuture.supplyAsync(() -> {
            // CPU 집약적 작업을 워커 스레드에서 처리
            return heavyProcessing(data);
        }, workerPool);
    }
}
```

## 🔚 결론

EventLoop Server는 **현대적 고성능 서버의 궁극적 형태**로, **최대 확장성과 성능**을 제공합니다. 하지만 **높은 구현 복잡도와 학습 곡선**을 요구하므로, 팀의 역량과 요구사항을 신중히 고려해야 합니다.

**주요 포인트**:
- 10-100배 확장성 향상
- 마이크로초 단위 응답 시간
- 완전한 논블로킹 생태계 필요
- 실시간 애플리케이션 최적화

**EventLoop는 현대 웹의 핵심 기술**이며, Node.js, Netty, Nginx의 성공 비결입니다! 🚀
