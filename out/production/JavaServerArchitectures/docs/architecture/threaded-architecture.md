# Threaded Server Architecture (Thread-per-Request)

## 📖 개요

Threaded Server는 **전통적인 Thread-per-Request 모델**을 구현한 HTTP 서버입니다. 각 클라이언트 요청마다 전용 스레드를 할당하여 처리하는 가장 직관적이고 이해하기 쉬운 아키텍처입니다.

## 🏗️ 아키텍처 설계

### 핵심 원리
```
클라이언트 요청 → ServerSocket.accept() → 새 스레드 생성 → 요청 처리 → 응답 전송 → 스레드 종료
```

### 구성 요소

#### 1. ThreadedServer (메인 서버)
```java
public class ThreadedServer {
    private ServerSocket serverSocket;
    private ThreadPoolManager threadPool;
    private boolean running;
    
    // 메인 루프: 요청 수락 + 스레드 할당
    while (running) {
        Socket clientSocket = serverSocket.accept();
        threadPool.execute(new ThreadedProcessor(clientSocket));
    }
}
```

#### 2. ThreadedProcessor (요청 처리기)
```java
public class ThreadedProcessor implements Runnable {
    private Socket clientSocket;
    
    @Override
    public void run() {
        // 전체 처리 과정이 단일 스레드에서 블로킹으로 진행
        HttpRequest request = parseRequest(clientSocket);
        HttpResponse response = processRequest(request);
        sendResponse(clientSocket, response);
    }
}
```

#### 3. ThreadPoolManager (스레드풀 관리)
```java
public class ThreadPoolManager {
    private ThreadPoolExecutor executor;
    
    // 설정 가능한 스레드풀 파라미터
    - Core Pool Size: 기본 스레드 수
    - Maximum Pool Size: 최대 스레드 수  
    - Keep Alive Time: 유휴 스레드 생존 시간
    - Queue Capacity: 대기 큐 크기
}
```

## 🔄 요청 처리 흐름

### 상세 처리 단계

1. **요청 수신**
   ```java
   Socket clientSocket = serverSocket.accept(); // 메인 스레드에서 블로킹
   ```

2. **스레드 할당**
   ```java
   threadPool.execute(() -> {
       // 새 스레드에서 모든 처리 진행
   });
   ```

3. **HTTP 파싱** (블로킹)
   ```java
   BufferedReader reader = new BufferedReader(
       new InputStreamReader(clientSocket.getInputStream())
   );
   String requestLine = reader.readLine(); // 블로킹 I/O
   ```

4. **비즈니스 로직 실행** (블로킹)
   ```java
   // 데이터베이스 쿼리, 파일 읽기 등 모든 I/O가 블로킹
   String data = database.query("SELECT * FROM users"); // 블로킹
   String result = processData(data); // CPU 작업
   ```

5. **응답 전송** (블로킹)
   ```java
   PrintWriter writer = new PrintWriter(clientSocket.getOutputStream());
   writer.write("HTTP/1.1 200 OK\r\n");
   writer.write(responseBody); // 블로킹 I/O
   writer.flush();
   ```

6. **연결 종료**
   ```java
   clientSocket.close();
   // 스레드 종료 또는 스레드풀로 반환
   ```

## ⚙️ 설정 및 튜닝

### 스레드풀 설정
```properties
# traditional-server.properties
server.port=8080
server.thread.core-pool-size=50
server.thread.max-pool-size=200
server.thread.keep-alive-time=60000
server.thread.queue-capacity=1000
server.socket.so-timeout=30000
```

### 메모리 설정
```bash
# JVM 옵션
-Xms1g -Xmx2g
-XX:NewRatio=3
-XX:+UseG1GC
```

## 📊 성능 특성

### 장점 ✅

1. **구현 단순성**
   - 가장 직관적인 프로그래밍 모델
   - 디버깅과 로깅이 쉬움
   - 에러 처리가 명확함

2. **개발 생산성**
   - 동기적 코드 작성 (콜백 지옥 없음)
   - 기존 블로킹 라이브러리 그대로 사용 가능
   - 스레드 로컬 변수 활용 가능

3. **안정성**
   - 하나의 요청 실패가 다른 요청에 영향 없음
   - 스레드 간 격리로 메모리 오염 방지

### 단점 ❌

1. **메모리 소비**
   - 스레드당 1-2MB 스택 메모리 할당
   - 1000개 동시 연결 = 1-2GB 메모리 소모

2. **컨텍스트 스위칭 오버헤드**
   - 스레드 수 증가시 CPU 컨텍스트 스위칭 비용 증가
   - I/O 대기 중에도 스레드가 블로킹됨

3. **확장성 제한**
   - C10K 문제: 10,000개 동시 연결 처리 어려움
   - 스레드 생성 비용 (1-2ms per thread)

## 📈 성능 지표 (예상 수치)

| 지표 | 수치 | 설명 |
|------|------|------|
| **최대 동시 연결** | ~500개 | 메모리 제한으로 제약 |
| **평균 응답 시간** | 50-100ms | I/O 대기 시간 포함 |
| **처리량 (TPS)** | 1,000-5,000 | 요청 복잡도에 따라 차이 |
| **메모리 사용량** | 스레드당 1-2MB | 스택 + 힙 메모리 |
| **CPU 사용률** | 중간 | 컨텍스트 스위칭 오버헤드 |

## 🛠️ 구현 세부사항

### 1. 연결 관리
```java
public class ConnectionManager {
    private int maxConnections = 500;
    private AtomicInteger activeConnections = new AtomicInteger(0);
    
    public boolean acceptConnection() {
        if (activeConnections.get() >= maxConnections) {
            return false; // 연결 거부
        }
        activeConnections.incrementAndGet();
        return true;
    }
}
```

### 2. 리소스 정리
```java
public class ThreadedProcessor implements Runnable {
    @Override
    public void run() {
        try {
            processRequest();
        } finally {
            // 반드시 리소스 정리
            closeSocket();
            connectionManager.releaseConnection();
        }
    }
}
```

### 3. 타임아웃 처리
```java
public void configureSocket(Socket socket) throws SocketException {
    socket.setSoTimeout(30000); // 30초 타임아웃
    socket.setKeepAlive(true);
    socket.setTcpNoDelay(true);
}
```

## 🎯 적합한 사용 사례

### ✅ 적합한 경우
- **소규모 애플리케이션** (동시 사용자 < 500명)
- **내부 시스템** (관리자 도구, 백오피스)
- **프로토타입 개발** (빠른 개발이 필요한 경우)
- **레거시 시스템 통합** (기존 블로킹 라이브러리 사용)

### ❌ 부적합한 경우
- **고트래픽 웹사이트** (동시 사용자 > 1000명)
- **실시간 채팅** (WebSocket 등 지속 연결)
- **IoT 게이트웨이** (수만 개의 센서 연결)
- **마이크로서비스** (높은 동시성 요구)

## 🔍 모니터링 포인트

### 핵심 메트릭
1. **스레드풀 상태**
   - Active Threads
   - Queue Size
   - Rejected Tasks

2. **메모리 사용량**
   - Heap Memory
   - Non-Heap Memory
   - Thread Stack Memory

3. **응답 시간**
   - Average Response Time
   - 95th Percentile
   - Max Response Time

### 경고 임계값
```properties
# 모니터링 임계값
thread.pool.active.warning=80%
thread.pool.queue.warning=70%
memory.heap.warning=85%
response.time.warning=500ms
```

## 🚀 최적화 팁

### 1. 스레드풀 튜닝
```java
// I/O 집약적 작업: 스레드 수를 늘림
int coreSize = Runtime.getRuntime().availableProcessors() * 2;
int maxSize = coreSize * 4;

// CPU 집약적 작업: CPU 코어 수에 맞춤
int coreSize = Runtime.getRuntime().availableProcessors();
int maxSize = coreSize + 1;
```

### 2. Keep-Alive 활용
```java
// HTTP Keep-Alive로 연결 재사용
response.setHeader("Connection", "keep-alive");
response.setHeader("Keep-Alive", "timeout=30, max=100");
```

### 3. 버퍼 크기 최적화
```java
// I/O 버퍼 크기 조정
BufferedInputStream bis = new BufferedInputStream(
    socket.getInputStream(), 8192
);
BufferedOutputStream bos = new BufferedOutputStream(
    socket.getOutputStream(), 8192
);
```

## 🔚 결론

Threaded Server는 **구현이 단순하고 이해하기 쉬운** 아키텍처로, **소규모에서 중간 규모의 애플리케이션**에 적합합니다. 하지만 **높은 동시성이 요구되는 환경**에서는 메모리와 컨텍스트 스위칭 오버헤드로 인해 성능 한계가 명확합니다.

**다음 단계**: Hybrid 서버에서 어떻게 이런 한계를 극복하는지 학습해보세요!
