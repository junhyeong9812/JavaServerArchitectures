# Hybrid Server Architecture (비동기 하이브리드)

## 📖 개요

Hybrid Server는 **Thread Pool + AsyncContext 기반의 하이브리드 아키텍처**를 구현한 HTTP 서버입니다. 전통적인 스레드 모델의 장점을 유지하면서 **컨텍스트 스위칭**을 통해 I/O 대기 시간 동안 스레드를 효율적으로 재활용하는 현대적 접근 방식입니다.

## 🏗️ 아키텍처 설계

### 핵심 원리
```
요청 수신 → 초기 스레드 할당 → I/O 대기시 스레드 해제 → 다른 스레드에서 재개 → 응답 전송
```

### 구성 요소

#### 1. HybridServer (메인 서버)
```java
public class HybridServer {
    private ServerSocketChannel serverChannel;      // NIO 기반 서버 소켓
    private Selector acceptSelector;                // 연결 수락용 Selector
    private AdaptiveThreadPool workerPool;          // 적응형 스레드풀
    private AsyncContextManager contextManager;     // 비동기 컨텍스트 관리
    
    // 하이브리드 처리: NIO 수락 + 스레드풀 처리
}
```

#### 2. AsyncContextManager (비동기 컨텍스트 관리)
```java
public class AsyncContextManager {
    private Map<String, AsyncContext> activeContexts;
    private ScheduledExecutorService timeoutExecutor;
    
    // 컨텍스트 생성 → 스레드 해제 → 다른 스레드에서 재개
    public AsyncContext createContext(HttpRequest request, HttpResponse response);
    public void resumeContext(String contextId, Object result);
}
```

#### 3. ContextSwitchingHandler (컨텍스트 스위칭 핸들러)
```java
public class ContextSwitchingHandler implements RouteHandler {
    @Override
    public CompletableFuture<HttpResponse> handle(HttpRequest request) {
        // I/O 작업을 별도 스레드로 위임
        return CompletableFuture
            .supplyAsync(() -> performDatabaseQuery())    // I/O 스레드
            .thenApplyAsync(this::processData)            // CPU 스레드
            .thenApply(result -> HttpResponse.ok(result));
    }
}
```

#### 4. AdaptiveThreadPool (적응형 스레드풀)
```java
public class AdaptiveThreadPool {
    private ThreadPoolExecutor ioPool;      // I/O 전용 스레드풀
    private ThreadPoolExecutor cpuPool;     // CPU 전용 스레드풀
    private ThreadPoolExecutor fastPool;    // 빠른 응답용 스레드풀
    
    // 작업 종류에 따라 적절한 스레드풀 선택
    public <T> CompletableFuture<T> submitIoTask(Supplier<T> task);
    public <T> CompletableFuture<T> submitCpuTask(Supplier<T> task);
}
```

## 🔄 요청 처리 흐름

### 상세 처리 단계

1. **연결 수락** (NIO 기반)
   ```java
   // 메인 스레드에서 논블로킹 수락
   while (serverChannel.isOpen()) {
       selector.select(); // 논블로킹
       Set<SelectionKey> keys = selector.selectedKeys();
       for (SelectionKey key : keys) {
           if (key.isAcceptable()) {
               SocketChannel clientChannel = serverChannel.accept();
               // 워커 스레드로 위임
               workerPool.submitFastTask(() -> handleConnection(clientChannel));
           }
       }
   }
   ```

2. **초기 요청 처리** (워커 스레드)
   ```java
   public void handleConnection(SocketChannel channel) {
       HttpRequest request = parseRequest(channel);  // 빠른 파싱
       AsyncContext context = contextManager.createContext(request, response);
       
       // 비즈니스 로직을 비동기로 처리
       processRequestAsync(context);
   }
   ```

3. **비동기 처리 체인**
   ```java
   public void processRequestAsync(AsyncContext context) {
       CompletableFuture
           .supplyAsync(() -> {
               // I/O 작업: 별도 I/O 스레드에서 수행
               return database.query("SELECT * FROM users");
           }, ioPool)
           .thenApplyAsync(data -> {
               // CPU 작업: CPU 전용 스레드에서 수행
               return processData(data);
           }, cpuPool)
           .thenAcceptAsync(result -> {
               // 응답 전송: 빠른 응답 스레드에서 수행
               context.getResponse().write(result);
               context.complete();
           }, fastPool)
           .exceptionally(throwable -> {
               // 에러 처리
               context.getResponse().setStatus(500);
               context.complete();
               return null;
           });
   }
   ```

4. **컨텍스트 재개 및 응답**
   ```java
   public void complete(AsyncContext context) {
       try {
           // 최종 응답 전송
           sendResponse(context.getChannel(), context.getResponse());
       } finally {
           // 리소스 정리
           contextManager.removeContext(context.getId());
           context.getChannel().close();
       }
   }
   ```

## ⚙️ 설정 및 튜닝

### 스레드풀 설정
```properties
# hybrid-server.properties
server.port=8081

# I/O 스레드풀 (I/O 집약적 작업)
server.pool.io.core-size=100
server.pool.io.max-size=500
server.pool.io.keep-alive=300000
server.pool.io.queue-capacity=2000

# CPU 스레드풀 (CPU 집약적 작업)
server.pool.cpu.core-size=8
server.pool.cpu.max-size=16
server.pool.cpu.keep-alive=60000
server.pool.cpu.queue-capacity=100

# 빠른 응답 스레드풀 (네트워크 I/O)
server.pool.fast.core-size=20
server.pool.fast.max-size=50
server.pool.fast.keep-alive=30000
server.pool.fast.queue-capacity=500

# AsyncContext 설정
server.async.timeout=30000
server.async.max-contexts=10000
```

### NIO 설정
```properties
# Selector 설정
server.selector.timeout=100
server.selector.buffer-size=8192

# 채널 설정
server.channel.so-rcvbuf=65536
server.channel.so-sndbuf=65536
server.channel.tcp-nodelay=true
```

## 📊 성능 특성

### 장점 ✅

1. **효율적인 스레드 활용**
   - I/O 대기 시간 동안 스레드 해제
   - 스레드당 처리 가능한 요청 수 증가
   - 컨텍스트 스위칭으로 리소스 효율성 극대화

2. **확장성 향상**
   - 동시 연결 수 대폭 증가 (500 → 2,000+)
   - 메모리 사용량 최적화
   - C10K 문제 부분적 해결

3. **작업 유형별 최적화**
   - I/O/CPU/Network 작업을 전용 스레드풀로 분리
   - 각 작업 유형에 맞는 스레드풀 튜닝 가능
   - 우선순위 기반 작업 스케줄링

4. **부분적 호환성**
   - 기존 동기 코드와 혼용 가능
   - 점진적 마이그레이션 지원

### 단점 ❌

1. **구현 복잡도 증가**
   - 비동기 프로그래밍 학습 곡선
   - 디버깅과 에러 추적의 어려움
   - 콜백 지옥 (Callback Hell) 가능성

2. **컨텍스트 관리 오버헤드**
   - AsyncContext 생성/관리 비용
   - 메모리에서 컨텍스트 상태 유지
   - 타임아웃 처리 복잡성

3. **스레드풀 튜닝 복잡성**
   - 3개 스레드풀의 밸런싱 필요
   - 작업 부하에 따른 동적 조정
   - 데드락 가능성 증가

## 📈 성능 지표 (예상 수치)

| 지표 | 수치 | 설명 |
|------|------|------|
| **최대 동시 연결** | ~2,000개 | 컨텍스트 스위칭으로 확장성 향상 |
| **평균 응답 시간** | 30-80ms | I/O 대기 시간 최적화 |
| **처리량 (TPS)** | 5,000-15,000 | 스레드 재활용으로 처리량 증가 |
| **메모리 사용량** | 연결당 50-100KB | 컨텍스트 상태만 메모리에 유지 |
| **CPU 사용률** | 높음 | 효율적인 스레드 활용 |

## 🛠️ 구현 세부사항

### 1. AsyncContext 구현
```java
public class AsyncContext {
    private final String id;
    private final HttpRequest request;
    private final HttpResponse response;
    private final SocketChannel channel;
    private final long startTime;
    private final long timeout;
    private volatile boolean completed;
    
    public void complete() {
        if (!completed) {
            completed = true;
            // 컨텍스트 정리 및 응답 전송
        }
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() - startTime > timeout;
    }
}
```

### 2. 스레드풀 선택 로직
```java
public class TaskRouter {
    public <T> CompletableFuture<T> submit(Callable<T> task, TaskType type) {
        switch (type) {
            case IO_INTENSIVE:
                return ioPool.submit(task);
            case CPU_INTENSIVE:
                return cpuPool.submit(task);
            case FAST_RESPONSE:
                return fastPool.submit(task);
            default:
                return defaultPool.submit(task);
        }
    }
}
```

### 3. 타임아웃 관리
```java
public class TimeoutManager {
    private final ScheduledExecutorService scheduler;
    
    public void scheduleTimeout(AsyncContext context) {
        scheduler.schedule(() -> {
            if (!context.isCompleted()) {
                context.getResponse().setStatus(408); // Request Timeout
                context.complete();
            }
        }, context.getTimeout(), TimeUnit.MILLISECONDS);
    }
}
```

### 4. 백프레셰 제어
```java
public class BackpressureController {
    private final AtomicInteger activeContexts = new AtomicInteger(0);
    private final int maxContexts;
    
    public boolean tryAcquire() {
        int current = activeContexts.get();
        if (current >= maxContexts) {
            return false; // 백프레셰 발동
        }
        return activeContexts.compareAndSet(current, current + 1);
    }
    
    public void release() {
        activeContexts.decrementAndGet();
    }
}
```

## 🎯 적합한 사용 사례

### ✅ 적합한 경우
- **중간 규모 웹 애플리케이션** (동시 사용자 500-2,000명)
- **I/O 집약적 서비스** (데이터베이스, 외부 API 호출 많음)
- **마이크로서비스 아키텍처** (서비스 간 통신 많음)
- **레거시 시스템 현대화** (기존 코드 점진적 개선)

### ❌ 부적합한 경우
- **초고성능 요구** (EventLoop이 더 적합)
- **단순한 CRUD 애플리케이션** (Threaded가 더 단순)
- **실시간 스트리밍** (지속 연결에는 부적합)
- **임베디드 시스템** (메모리/CPU 제약)

## 🔍 모니터링 포인트

### 핵심 메트릭
1. **스레드풀 상태**
   - I/O Pool Utilization
   - CPU Pool Utilization  
   - Fast Pool Utilization
   - Queue Depths

2. **AsyncContext 상태**
   - Active Contexts
   - Context Creation Rate
   - Context Timeout Rate
   - Average Context Lifetime

3. **성능 지표**
   - Response Time Distribution
   - Throughput per Thread Pool
   - Context Switching Overhead

### 경고 임계값
```properties
# 모니터링 임계값
async.context.active.warning=80%
async.context.timeout.rate.warning=5%
thread.pool.queue.warning=70%
response.time.p95.warning=200ms
```

## 🚀 최적화 팁

### 1. 스레드풀 크기 계산
```java
// I/O 집약적 작업 (I/O 대기 시간이 긴 경우)
int ioPoolSize = availableProcessors * (1 + waitTime / serviceTime);

// CPU 집약적 작업
int cpuPoolSize = availableProcessors + 1;

// 빠른 응답 (네트워크 I/O)
int fastPoolSize = availableProcessors * 2;
```

### 2. 컨텍스트 풀링
```java
// AsyncContext 객체 재사용으로 GC 압박 감소
public class ContextPool {
    private final Queue<AsyncContext> pool = new ConcurrentLinkedQueue<>();
    
    public AsyncContext acquire() {
        AsyncContext context = pool.poll();
        return context != null ? context.reset() : new AsyncContext();
    }
    
    public void release(AsyncContext context) {
        if (pool.size() < MAX_POOL_SIZE) {
            pool.offer(context);
        }
    }
}
```

### 3. 배치 처리 최적화
```java
// 유사한 작업들을 배치로 처리하여 효율성 증대
public class BatchProcessor {
    private final List<Task> batch = new ArrayList<>();
    private final int batchSize = 100;
    
    public void addTask(Task task) {
        synchronized (batch) {
            batch.add(task);
            if (batch.size() >= batchSize) {
                processBatch(new ArrayList<>(batch));
                batch.clear();
            }
        }
    }
}
```

## 🔚 결론

Hybrid Server는 **전통적인 스레드 모델의 단순함**과 **비동기 처리의 효율성**을 절충한 현실적인 해결책입니다. 완전한 EventLoop보다는 복잡하지만, 기존 동기 코드와의 호환성을 유지하면서 **상당한 성능 향상**을 제공합니다.

**주요 포인트**:
- 스레드 효율성 2-4배 향상
- 동시 연결 수 4배 증가  
- 구현 복잡도 중간 수준
- 점진적 마이그레이션 가능

**다음 단계**: EventLoop 서버에서 완전한 비동기 처리가 어떻게 더 높은 성능을 달성하는지 학습해보세요!
