# HybridServer - NIO + Thread Pool 하이브리드 HTTP 서버

## 개요

HybridServer는 Java NIO(Non-blocking I/O)와 Thread Pool을 결합한 하이브리드 아키텍처의 HTTP 서버입니다. I/O 작업은 NIO Selector로 효율적으로 처리하고, CPU 집약적 작업만 Thread Pool에서 처리하여 최적의 성능과 자원 활용률을 달성합니다.

## 핵심 아키텍처

### 하이브리드 처리 방식

```
클라이언트 요청 → NIO Selector(I/O 감지) → Thread Pool(CPU 처리) → NIO Selector(응답 전송)
                     ↓                        ↑
              Context Switch Out        Context Switch In
```

### 주요 특징

- **NIO Selector**: I/O 이벤트 멀티플렉싱으로 수천 개의 동시 연결 처리
- **Adaptive Thread Pool**: 동적 크기 조정으로 CPU 자원 최적화
- **Context Switching**: I/O 대기 시 스레드 해제, 완료 시 재할당
- **Async Servlet**: 비동기 서블릿으로 높은 처리량 달성
- **Request Context**: 스레드와 분리된 요청 상태 관리

## 요청 처리 프로세스

### 1. 연결 수락 (Accept Phase)
```java
// ServerSocketChannel에서 새 연결 감지
SocketChannel clientChannel = serverChannel.accept();
clientChannel.configureBlocking(false);

// Selector에 READ 이벤트 등록
SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);

// 채널별 컨텍스트 생성
ChannelContext context = new ChannelContext(connectionId, clientChannel);
```

### 2. 데이터 읽기 (Read Phase)
```java
// HTTP 요청 데이터를 점진적으로 읽기
ByteBuffer buffer = ByteBuffer.allocate(8192);
int bytesRead = channel.read(buffer);

// 요청 완성도 확인 (Request Line → Headers → Body)
context.appendData(buffer);
if (context.isRequestComplete()) {
    processCompleteRequest(context, key);
}
```

### 3. 컨텍스트 스위치 아웃 (Context Switch Out)
```java
// READ 이벤트 제거하여 NIO 루프에서 분리
key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);

// Thread Pool에 작업 제출
threadPool.submit(() -> {
    // HTTP 파싱 및 라우팅
    HttpRequest request = HttpParser.parseRequest(requestData);
    CompletableFuture<HttpResponse> responseFuture = router.route(request);
});
```

### 4. 비즈니스 로직 처리 (Processing Phase)
```java
// 라우터를 통한 요청 라우팅
CompletableFuture<HttpResponse> responseFuture = router.route(request);

// 서블릿 컨테이너에서 처리
if (servlet instanceof MiniAsyncServlet) {
    return handleAsyncServlet(request, servletInfo);
} else {
    return handleSyncServlet(request, servletInfo);
}
```

### 5. 컨텍스트 스위치 인 (Context Switch In)
```java
responseFuture.whenComplete((response, throwable) -> {
    // 응답을 채널 컨텍스트에 설정
    context.setResponse(response);
    
    // Selector 깨우기 및 WRITE 이벤트 등록
    selector.wakeup();
    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
});
```

### 6. 응답 전송 (Write Phase)
```java
// NIO Selector에서 WRITE 이벤트 감지
HttpResponse response = context.getResponse();
byte[] responseBytes = response.toByteArray();
ByteBuffer buffer = ByteBuffer.wrap(responseBytes);

// 비차단 방식으로 응답 전송
int bytesWritten = channel.write(buffer);
```

## 핵심 컴포넌트

### 서버 아키텍처 레이어

#### 1. Network Layer (NIO)
- `HybridServer.java`: 메인 서버 클래스, NIO Selector 루프 관리
- `ChannelContext.java`: 채널별 HTTP 요청/응답 상태 관리

#### 2. Processing Layer (Thread Pool)
- `AdaptiveThreadPool.java`: 동적 크기 조정 스레드풀
- `HybridProcessor.java`: 요청 처리 전략 결정 및 실행
- `ContextSwitchingHandler.java`: 컨텍스트 스위칭 관리

#### 3. Application Layer (Servlet)
- `HybridMiniServletContainer.java`: 서블릿 생명주기 관리
- `MiniAsyncServlet.java`: 비동기 서블릿 베이스 클래스

#### 4. Context Management
- `AsyncContextManager.java`: 비동기 컨텍스트 생명주기 관리

## 파일별 상세 설명

### 🌟 핵심 서버 파일

#### `HybridServer.java`
**역할**: 하이브리드 HTTP 서버의 메인 클래스
- NIO ServerSocketChannel과 Selector 관리
- 클라이언트 연결 수락 및 I/O 이벤트 처리
- Context Switch Out/In 조정
- 채널별 컨텍스트 생명주기 관리

**핵심 메서드**:
- `runSelectorLoop()`: NIO 이벤트 루프 (Accept/Read/Write)
- `handleAccept()`: 새 클라이언트 연결 수락
- `handleRead()`: 요청 데이터 읽기
- `handleWrite()`: 응답 데이터 전송
- `processCompleteRequest()`: 완전한 요청을 Thread Pool로 전달

#### `AdaptiveThreadPool.java`
**역할**: 동적 크기 조정이 가능한 스레드풀
- 큐 크기와 스레드 사용률 기반 자동 조정
- 우선순위 기반 작업 스케줄링 (PriorityBlockingQueue)
- 실시간 성능 모니터링 및 통계 수집
- 백프레셔 제어를 통한 시스템 보호

**핵심 기능**:
- `submit(task, priority)`: 우선순위 기반 작업 제출
- `monitorAndAdjust()`: 주기적 성능 모니터링 및 크기 조정
- `adjustPoolSize()`: 동적 스레드 수 조정
- Custom `PriorityTask`: 우선순위와 생성시간 기반 정렬

#### `HybridProcessor.java`
**역할**: 요청 처리 전략 결정 및 실행
- SYNC/ASYNC/ADAPTIVE 전략 선택
- 정적 파일, DB/API 요청 분류
- 배치 요청 처리 및 우선순위 처리
- 처리 성능 통계 수집

**처리 전략**:
- **SYNC**: 간단한 요청, 정적 파일
- **ASYNC**: DB/API 요청, 높은 동시성
- **ADAPTIVE**: 스레드 사용률 기반 동적 선택

#### `ContextSwitchingHandler.java`
**역할**: 컨텍스트 스위칭 관리 및 비동기 작업 체인
- Switch Out: I/O 대기 시 스레드 해제
- Switch In: I/O 완료 시 스레드 재할당
- DB 작업, API 호출, 파일 I/O 전용 메서드
- 타임아웃 및 백프레셔 제어

**핵심 메서드**:
- `switchAndExecute()`: 범용 컨텍스트 스위칭
- `executeDbOperation()`: DB 작업 전용
- `executeApiCall()`: API 호출 전용
- `executeMultiple()`: 복합 비동기 작업

### 🔄 컨텍스트 관리 파일

#### `AsyncContextManager.java`
**역할**: 비동기 컨텍스트 생명주기 관리
- 스레드와 분리된 요청 상태 보관
- 컨텍스트 타임아웃 및 자동 정리
- 상태별 컨텍스트 조회 및 속성 관리
- 메모리 누수 방지를 위한 주기적 정리

**컨텍스트 상태**:
- `CREATED`: 생성됨
- `PROCESSING`: 처리 중
- `WAITING`: I/O 대기 중
- `COMPLETED`: 처리 완료
- `ERROR`: 오류 발생
- `TIMEOUT`: 타임아웃

#### `ChannelContext.java`
**역할**: NIO 채널별 HTTP 요청/응답 상태 관리
- HTTP 프로토콜 점진적 파싱 (Request Line → Headers → Body)
- Keep-Alive 연결 관리
- 부분적 데이터 버퍼링
- 연결별 메타데이터 및 속성 저장

**파싱 상태**:
- `REQUEST_LINE`: 첫 번째 줄 파싱
- `HEADERS`: 헤더 파싱
- `BODY`: 바디 파싱
- `COMPLETE`: 파싱 완료

### 🎯 서블릿 컨테이너 파일

#### `HybridMiniServletContainer.java`
**역할**: 하이브리드 방식의 서블릿 생명주기 관리
- 비동기/동기 서블릿 구분 처리
- 서블릿 풀링 및 재사용 최적화
- URL 패턴 매칭 및 라우팅
- 서블릿 통계 및 모니터링

**핵심 기능**:
- `handleAsyncServlet()`: 비동기 서블릿 처리
- `handleSyncServlet()`: 동기 서블릿 하이브리드 처리
- 서블릿 풀 관리 (borrowServletFromPool/returnServletToPool)
- URL 패턴 매칭 (정확 매칭 → 와일드카드 매칭)

#### `MiniAsyncServlet.java`
**역할**: 비동기 서블릿 베이스 클래스
- Core의 MiniServlet 상속하여 비동기 기능 추가
- HTTP 메서드별 비동기 처리 메서드 제공
- CompletableFuture 기반 비동기 처리
- 기존 동기 메서드와의 호환성 유지

### 🚀 실제 서블릿 구현 파일

#### `HelloWorldAsyncServlet.java`
**역할**: 기본 Hello World 비동기 서블릿
- 간단한 GET 요청 처리
- 쿼리 파라미터 지원
- 스레드 정보 포함 응답

#### `UserApiAsyncServlet.java`
**역할**: RESTful User API 비동기 서블릿
- GET: 사용자 목록/개별 조회 (100ms DB 시뮬레이션)
- POST: 사용자 생성 (200ms 처리 시뮬레이션)
- JSON 응답 및 HTTP 상태 코드 관리

#### `CpuIntensiveAsyncServlet.java`
**역할**: CPU 집약적 작업 비동기 처리
- 복잡한 수학 연산 (Math.sqrt, sin, cos, tan)
- 가변적 반복 횟수 지원 (최대 100만)
- 처리 시간 측정 및 성능 통계

#### `IoSimulationAsyncServlet.java`
**역할**: I/O 작업 시뮬레이션 비동기 서블릿
- Database, File, API, Cache 작업 시뮬레이션
- 가변적 지연 시간 설정
- 다단계 I/O 작업 체인 처리

#### `FileUploadAsyncServlet.java`
**역할**: 파일 업로드 비동기 처리
- Multipart/form-data 처리 시뮬레이션
- 비차단 파일 업로드
- 업로드 폼 제공 및 결과 JSON 응답

#### `StaticFileAsyncServlet.java`
**역할**: 정적 파일 비동기 서빙
- CSS, JS, HTML 파일 처리
- MIME 타입 자동 설정
- 비차단 파일 읽기 시뮬레이션

#### `LoadTestAsyncServlet.java`
**역할**: 로드 테스트용 비동기 서블릿
- CPU + I/O 복합 작업 시뮬레이션
- 가변적 부하 강도 설정
- 성능 측정 및 벤치마킹

#### `HealthAsyncServlet.java`
**역할**: 헬스체크 비동기 엔드포인트
- 서버 상태 확인
- 실시간 스레드 정보
- JSON 상태 응답

### 🧪 테스트 파일

#### `HybridServerTest.java`
**역할**: 하이브리드 서버 테스트 및 런처
- 통합 테스트 실행
- 실제 서버 실행 모드
- 성능 테스트 및 벤치마킹
- 컨텍스트 스위칭 기능 검증

## 성능 특성

### 동시성 처리

```java
// NIO Selector로 수천 개 연결 처리
while (running.get()) {
    int readyChannels = selector.select(1000);
    Set<SelectionKey> selectedKeys = selector.selectedKeys();
    
    // 각 이벤트를 효율적으로 처리
    for (SelectionKey key : selectedKeys) {
        if (key.isAcceptable()) handleAccept(key);
        else if (key.isReadable()) handleRead(key);
        else if (key.isWritable()) handleWrite(key);
    }
}
```

### 메모리 효율성

```java
// 스레드당 메모리가 아닌 연결당 작은 컨텍스트만 사용
class ChannelContext {
    private final StringBuilder requestBuffer;      // ~1KB
    private final Map<String, Object> attributes;   // 필요시에만
    private volatile boolean requestComplete;       // 상태 플래그들
}
```

### 백프레셔 제어

```java
// 시스템 과부하 방지
if (currentSwitches >= maxConcurrentSwitches) {
    throw new RuntimeException("Too many concurrent context switches");
}

// Thread Pool 적응형 조정
if (queueSize > targetQueueSize || utilization > 0.8) {
    increaseThreadPool();
}
```

## 사용 방법

### 서버 시작

```bash
# 기본 실행
java server.hybrid.HybridServerTest

# 테스트 모드
java server.hybrid.HybridServerTest test
```

### 엔드포인트 테스트

```bash
# 기본 서블릿 테스트
curl http://localhost:8081/hello?name=HybridServer

# JSON API 테스트
curl http://localhost:8081/api/users

# 컨텍스트 스위칭 테스트
curl http://localhost:8081/test/db
curl http://localhost:8081/test/api

# 로드 테스트
for i in {1..100}; do 
  curl http://localhost:8081/load-test & 
done
```

## ThreadedServer vs HybridServer 비교

| 특성 | ThreadedServer | HybridServer |
|------|----------------|--------------|
| **연결 모델** | Thread per Request | NIO + Thread Pool |
| **메모리 사용** | 요청당 ~8MB (스택) | 연결당 ~1KB (컨텍스트) |
| **동시 연결** | ~수백 개 | ~수만 개 |
| **I/O 처리** | Blocking I/O | Non-blocking I/O |
| **CPU 활용** | I/O 대기시 낭비 | CPU 작업만 스레드 사용 |
| **확장성** | 제한적 (메모리/스레드) | 높음 (I/O와 CPU 분리) |

## 모니터링 및 통계

### 실시간 메트릭

```java
// 서버 상태
ServerStatus status = server.getStatus();
// - 활성 연결 수
// - 총 요청 수  
// - 컨텍스트 스위치 횟수
// - 스레드 사용률

// 스레드풀 통계
ThreadPoolStats poolStats = threadPool.getCurrentStats();
// - 평균 실행 시간
// - 평균 대기 시간
// - 처리량 (요청/초)

// 컨텍스트 스위칭 통계  
SwitchingStats switchStats = switchingHandler.getStats();
// - 스위치 아웃/인 횟수
// - 평균 스위치 시간
// - 타임아웃 비율
```

## 확장 포인트

### 커스텀 서블릿 추가

```java
public class MyAsyncServlet extends MiniAsyncServlet {
    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            // 비동기 비즈니스 로직
        });
    }
}

// 서블릿 등록
container.registerServlet("MyServlet", new MyAsyncServlet(), "/my-api/*");
```

### 커스텀 처리 전략

```java
// 처리 전략 변경
processor.setProcessingStrategy(ProcessingStrategy.ASYNC);

// 스레드풀 설정 조정
threadPool.setTargetQueueSize(20);
threadPool.setAdjustmentFactor(0.2);
```

## 주요 장점

### 1. 높은 동시성
- NIO Selector로 수만 개의 동시 연결 처리
- 메모리 사용량 최소화 (연결당 ~1KB)

### 2. 효율적 자원 활용
- I/O 대기시 스레드 해제
- CPU 집약적 작업만 Thread Pool 사용

### 3. 적응형 성능 조정
- 실시간 부하에 따른 스레드 수 자동 조정
- 우선순위 기반 작업 스케줄링

### 4. 확장성
- 컨텍스트 스위칭으로 유연한 처리
- 비동기 서블릿으로 높은 처리량

## 결론

HybridServer는 NIO의 높은 동시성과 Thread Pool의 효율적 CPU 활용을 결합하여, 제한된 자원으로 최대 성능을 달성하는 하이브리드 아키텍처입니다. 컨텍스트 스위칭을 통해 I/O 대기 시간을 효과적으로 활용하며, 비동기 서블릿으로 높은 처리량을 제공합니다.