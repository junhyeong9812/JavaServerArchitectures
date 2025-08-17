# ThreadedServer 구현 가이드

## 아키텍처 개요

ThreadedServer는 **Tomcat 스타일의 스레드 풀 전략**을 구현한 멀티스레드 HTTP 서버입니다. 기존의 표준 ThreadPoolExecutor와 달리 **즉시 스레드 생성**을 우선시하여 더 빠른 응답성을 제공합니다.

### 핵심 특징
- **Tomcat 스타일 스레드 생성**: 큐 대기보다 스레드 생성 우선
- **하이브리드 요청 처리**: Router + ServletContainer 통합 지원
- **HTTP Keep-Alive**: 단일 연결로 다중 요청 처리
- **실시간 모니터링**: 상세한 성능 통계 제공

## 파일 구조 및 역할

### 1. 핵심 서버 클래스
```
ThreadedServer.java          # 메인 서버 클래스 (시작점)
├── ServerConfig.java        # 서버 전체 설정 관리
└── ThreadedProcessor.java   # 연결 처리 중간 계층
```

### 2. 스레드 풀 관리
```
ThreadPoolManager.java       # Tomcat 스타일 스레드 풀 관리자
├── ThreadPoolConfig.java    # 스레드 풀 설정
└── RequestHandlerConfig.java # 요청 처리 설정
```

### 3. 요청 처리
```
BlockingRequestHandler.java  # 개별 연결의 실제 HTTP 처리
└── ThreadedMiniServletContainer.java # 서블릿 컨테이너
```

### 4. 예제 서블릿들
```
HealthServlet.java           # 헬스체크 엔드포인트
CpuIntensiveServlet.java     # CPU 집약적 작업 시뮬레이션
IoSimulationServlet.java     # I/O 집약적 작업 시뮬레이션
FileUploadServlet.java       # 파일 업로드 처리
StaticFileServlet.java       # 정적 파일 서빙
```

### 5. 테스트 및 실행
```
ThreadedServerTest.java      # 서버 구동 및 테스트
```

## 코드 흐름 다이어그램

```
ThreadedServerTest
    ↓
ThreadedServer
    ├── ServerConfig
    └── ThreadedProcessor
            ├── ThreadPoolManager
            │   └── TomcatStyleThreadPoolExecutor
            │       └── BlockingRequestHandler
            └── ThreadedMiniServletContainer
                └── Servlets
```

## 코드 읽기 순서 가이드

### Phase 1: 기본 이해 (설정 및 구조)
1. **`ServerConfig.java`** - 서버의 전체 설정 체계 이해
2. **`ThreadPoolConfig.java`** - 스레드 풀 설정 옵션들
3. **`RequestHandlerConfig.java`** - HTTP 요청 처리 설정

### Phase 2: 메인 플로우 (서버 시작부터 요청 처리까지)
4. **`ThreadedServerTest.java`** - 실제 서버 사용 예제
5. **`ThreadedServer.java`** - 서버의 생명주기와 Accept 루프
6. **`ThreadedProcessor.java`** - 연결 수락과 작업 분배 로직

### Phase 3: 핵심 구현 (Tomcat 스타일 스레드 풀)
7. **`ThreadPoolManager.java`** **[가장 중요]** - Tomcat 스타일 구현의 핵심
    - `TomcatStyleThreadPoolExecutor` 클래스
    - `TomcatStyleTaskQueue` 클래스
    - 즉시 스레드 생성 로직

### Phase 4: 요청 처리 (HTTP 프로토콜 처리)
8. **`BlockingRequestHandler.java`** - HTTP Keep-Alive와 실제 요청 처리
9. **`ThreadedMiniServletContainer.java`** - 서블릿 생명주기 관리

### Phase 5: 실제 서블릿들 (비즈니스 로직)
10. **`HealthServlet.java`** - 간단한 서블릿 예제
11. **`CpuIntensiveServlet.java`** - CPU 부하 테스트용
12. **`IoSimulationServlet.java`** - I/O 부하 테스트용

## 실행 흐름

### 1. 서버 시작 과정
```java
// 1. 설정 생성
ServerConfig config = new ServerConfig()
    .setDebugMode(true)
    .setThreadPoolConfig(new ThreadPoolConfig().setCorePoolSize(5));

// 2. 서버 생성 및 서블릿 등록
ThreadedServer server = new ThreadedServer(8080, router, config);
server.registerServlet("/health", new HealthServlet());

// 3. 서버 시작
server.start(); // → initialize() → Accept 루프 시작
```

### 2. 요청 처리 흐름
```
클라이언트 연결
    ↓
ServerSocket.accept() (ThreadedServer)
    ↓
ThreadedProcessor.processConnection()
    ↓
ThreadPoolManager.submit() 
    ↓
TomcatStyleThreadPoolExecutor.execute() ← 핵심 로직
    ↓
BlockingRequestHandler.run()
    ↓
ServletContainer 또는 Router로 요청 라우팅
    ↓
HTTP 응답 전송
```

## Tomcat 스타일 핵심 로직

### 표준 vs Tomcat 방식 비교

**표준 ThreadPoolExecutor:**
```
Core Pool 가득참 → Queue 저장 → Queue 가득참 → 새 스레드 생성
```

**Tomcat 스타일:**
```
Core Pool 가득참 → 즉시 새 스레드 생성 → Max 도달 → Queue 저장
```

### 핵심 구현 포인트

1. **`TomcatStyleThreadPoolExecutor.execute()`**
```java
if (activeThreads < getCorePoolSize()) {
    super.execute(command);  // 표준 동작
    return;
}

if (currentThreads < maxThreads) {
    // 핵심: 코어 풀 크기를 1 증가시켜 즉시 스레드 생성 유도
    setCorePoolSize(Math.min(currentThreads + 1, maxThreads));
}
super.execute(command);
```

2. **`TomcatStyleTaskQueue.offer()`**
```java
if (currentThreads < maxThreads) {
    return false;  // 핵심: 큐 저장 거부하여 스레드 생성 유도
}
return super.offer(o);  // 최대 스레드 도달 후에만 큐 사용
```

## 모니터링 및 통계

### 실시간 통계 확인
```java
ThreadPoolManager.ThreadPoolStatus status = threadPool.getStatus();
System.out.println("Active Threads: " + status.getActiveThreads());
System.out.println("Queue Size: " + status.getQueueSize());
System.out.println("Avg Processing Time: " + status.getAvgProcessingTime() + "ms");
```

### 주요 지표들
- **Active Threads**: 현재 작업 중인 스레드 수
- **Queue Size**: 대기 중인 작업 수 (Tomcat 스타일에서는 낮게 유지)
- **Rejection Rate**: 거부된 작업 비율 (시스템 포화도 지표)
- **Peak Active Threads**: 최대 동시 활성 스레드 수

## 테스트 시나리오

### 1. 부하 테스트
```bash
# CPU 집약적 작업
curl http://localhost:8080/cpu-intensive

# I/O 시뮬레이션
curl http://localhost:8080/io-simulation

# 동시 요청 테스트
for i in {1..10}; do curl http://localhost:8080/load-test & done
```

### 2. Keep-Alive 테스트
```bash
# 단일 연결로 다중 요청
curl -H "Connection: keep-alive" http://localhost:8080/hello
```

## 설정 최적화 가이드

### CPU 집약적 작업
```java
new ThreadPoolConfig()
    .setCorePoolSize(Runtime.getRuntime().availableProcessors())
    .setMaxPoolSize(Runtime.getRuntime().availableProcessors() + 1)
    .setQueueCapacity(50);
```

### I/O 집약적 작업
```java
new ThreadPoolConfig()
    .setCorePoolSize(Runtime.getRuntime().availableProcessors() * 2)
    .setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 4)
    .setQueueCapacity(200);
```

## 학습 포인트

1. **Tomcat 스타일의 우수성**: 왜 큐 대기보다 스레드 생성이 더 빠른 응답성을 제공하는가?
2. **동시성 제어**: AtomicInteger, volatile, synchronized의 적절한 사용
3. **HTTP Keep-Alive**: 단일 TCP 연결의 재사용으로 성능 향상
4. **리소스 관리**: 스레드 생성/소멸 최적화와 메모리 사용량 균형
5. **모니터링**: 실시간 성능 지표 수집과 병목 지점 식별

## 주의사항

- **메모리 사용량**: 스레드 증가 = 메모리 사용량 증가 (스레드당 ~1MB)
- **컨텍스트 스위칭**: 과도한 스레드는 오히려 성능 저하 가능
- **우아한 종료**: 진행 중인 요청 완료 후 종료하는 Graceful Shutdown 구현

## 성능 비교

### 표준 ThreadPoolExecutor vs Tomcat 스타일

| 지표 | 표준 방식 | Tomcat 스타일 |
|------|-----------|---------------|
| 응답 시간 | 큐 대기 시간 포함 | 즉시 스레드 생성으로 단축 |
| 처리량 | 큐 크기에 제한됨 | 최대 스레드까지 확장 |
| 메모리 사용 | 큐 버퍼링으로 안정적 | 스레드 증가로 더 많이 사용 |
| 복잡도 | 단순함 | 동적 크기 조정으로 복잡 |

이 가이드를 통해 ThreadedServer의 구현을 체계적으로 이해하고, 고성능 멀티스레드 서버 개발의 핵심 개념들을 학습할 수 있습니다.