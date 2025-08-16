# Phase 2: ThreadedServer 구현 가이드

## 📋 개요

Phase 2에서는 전통적인 **Thread-per-Connection** 모델의 HTTP 서버를 구현합니다. 각 클라이언트 연결마다 별도의 스레드를 할당하여 요청을 처리하는 블로킹 I/O 방식입니다.

## 🏗️ 아키텍처 설계

### 핵심 컴포넌트

```
ThreadedServer
├── ThreadPoolManager      # 동적 스레드풀 관리
├── ThreadedProcessor      # 연결 처리 및 통계
├── BlockingRequestHandler # 개별 연결 처리
├── ThreadedMiniServletContainer # 서블릿 컨테이너
└── Configuration Classes   # 설정 관리
```

### 처리 흐름

```
1. ServerSocket Accept
2. ThreadedProcessor로 연결 전달
3. ThreadPoolManager에서 스레드 할당
4. BlockingRequestHandler에서 HTTP 처리
5. MiniServletContainer 또는 Router로 라우팅
6. 응답 전송 및 연결 정리
```

## 📁 파일 구조

### PowerShell 명령어로 파일 생성

```powershell
# 폴더 생성
New-Item -ItemType Directory -Path "server\threaded" -Force

# 파일 생성
New-Item -ItemType File -Path "server\threaded\ThreadPoolManager.java" -Force
New-Item -ItemType File -Path "server\threaded\ThreadPoolConfig.java" -Force
New-Item -ItemType File -Path "server\threaded\ThreadedProcessor.java" -Force
New-Item -ItemType File -Path "server\threaded\BlockingRequestHandler.java" -Force
New-Item -ItemType File -Path "server\threaded\ThreadedServer.java" -Force
New-Item -ItemType File -Path "server\threaded\ThreadedMiniServletContainer.java" -Force
New-Item -ItemType File -Path "server\threaded\RequestHandlerConfig.java" -Force
New-Item -ItemType File -Path "server\threaded\ServerConfig.java" -Force
New-Item -ItemType File -Path "server\threaded\ThreadedServerTest.java" -Force
New-Item -ItemType File -Path "server\threaded\StaticFileServlet.java" -Force
New-Item -ItemType File -Path "server\threaded\FileUploadServlet.java" -Force
```

### 최종 폴더 구조

```
server/threaded/
├── ThreadPoolManager.java          # 스레드풀 관리자
├── ThreadPoolConfig.java           # 스레드풀 설정
├── ThreadedProcessor.java          # 연결 처리기
├── BlockingRequestHandler.java     # 블로킹 요청 핸들러
├── ThreadedServer.java             # 메인 서버
├── ThreadedMiniServletContainer.java # 서블릿 컨테이너
├── RequestHandlerConfig.java       # 요청 핸들러 설정
├── ServerConfig.java               # 서버 설정
├── ThreadedServerTest.java         # 테스트 클래스
├── StaticFileServlet.java          # 정적 파일 서블릿
└── FileUploadServlet.java          # 파일 업로드 서블릿
```

## 🔧 핵심 구현 상세

### 1. ThreadPoolManager

**역할**: 동적 스레드풀 관리 및 부하 기반 자동 스케일링

**주요 기능**:
- 요청 부하에 따른 스레드풀 크기 자동 조정
- 실시간 성능 모니터링 및 통계 수집
- 커스텀 거부 정책으로 백프레셔 처리

```java
// 사용 예시
ThreadPoolConfig config = new ThreadPoolConfig()
    .setCorePoolSize(10)
    .setMaxPoolSize(100)
    .setDebugMode(true);

ThreadPoolManager manager = new ThreadPoolManager(config);
Future<?> task = manager.submit(() -> handleRequest());
```

### 2. BlockingRequestHandler

**역할**: 개별 클라이언트 연결의 HTTP 요청/응답 처리

**주요 기능**:
- HTTP/1.1 Keep-Alive 연결 지원
- 요청당 최대 처리 수 제한
- 소켓 타임아웃 및 예외 처리

```java
// 연결당 다중 요청 처리
while (keepAlive && requestCount < maxRequestsPerConnection) {
    HttpRequest request = parseRequest(inputStream);
    HttpResponse response = processRequest(request);
    sendResponse(response, outputStream);
}
```

### 3. ThreadedProcessor

**역할**: 연결 통계 관리 및 ThreadPoolManager 연동

**주요 기능**:
- 연결 수명주기 추적
- 성능 통계 수집 (처리량, 지연시간, 거부율)
- 스레드풀 포화 상태 처리

### 4. ThreadedMiniServletContainer

**역할**: 서블릿 생명주기 관리 및 요청 라우팅

**주요 기능**:
- 동기/비동기 서블릿 지원
- 패턴 기반 URL 매핑
- 서블릿 초기화 및 종료 관리

```java
// 서블릿 등록 예시
container.registerServlet("/api/*", new ApiServlet());
container.registerAsyncServlet("/async/*", new AsyncServlet());
```

## ⚙️ 설정 시스템

### ThreadPoolConfig
- **corePoolSize**: 기본 스레드 수 (기본값: 10)
- **maxPoolSize**: 최대 스레드 수 (기본값: 100)
- **queueCapacity**: 큐 용량 (기본값: 200)
- **scaleStep**: 스케일링 단위 (기본값: 5)

### RequestHandlerConfig
- **socketTimeout**: 소켓 타임아웃 (기본값: 30초)
- **maxRequestsPerConnection**: 연결당 최대 요청 수 (기본값: 100)
- **enableKeepAlive**: Keep-Alive 활성화 (기본값: true)

### ServerConfig
- **bindAddress**: 바인딩 주소 (기본값: "0.0.0.0")
- **backlogSize**: 백로그 크기 (기본값: 50)
- **tcpNoDelay**: Nagle 알고리즘 비활성화 (기본값: true)

## 🚀 컴파일 및 실행

### 1. 컴파일

```bash
# 모든 Java 파일 컴파일
javac server\threaded\*.java server\core\**\*.java server\examples\*.java
```

### 2. 실행

```bash
# ThreadedServer 테스트 실행
java server.threaded.ThreadedServerTest
```

### 3. 예상 출력

```
=== ThreadedServer Test ===

[ThreadedServer] Initialized on port 8080
[ThreadedProcessor] Initialized with config: RequestHandlerConfig{...}
[ThreadedServer] Initializing server...
[ThreadedServer] Server initialized successfully
  - Listening on: /0.0.0.0:8080
  - Backlog size: 50
  - Thread pool: 5-20
[ThreadedServer] Accept loop started
ThreadedServer is running on http://localhost:8080

Available endpoints:
  GET  http://localhost:8080/hello
  GET  http://localhost:8080/servlet/hello
  GET  http://localhost:8080/api/users
  POST http://localhost:8080/api/users
  GET  http://localhost:8080/status
  GET  http://localhost:8080/load-test
```

## 🧪 테스트 엔드포인트

### 기본 테스트
```bash
# Hello World (Router)
curl http://localhost:8080/hello?name=ThreadedServer

# Hello World (Servlet)  
curl http://localhost:8080/servlet/hello

# 비동기 JSON API
curl http://localhost:8080/api/test
```

### 부하 테스트
```bash
# 동시 요청 테스트
for i in {1..20}; do curl http://localhost:8080/load-test & done

# 서버 상태 확인
curl http://localhost:8080/status
```

### 에러 테스트
```bash
# 400 에러
curl http://localhost:8080/error/400

# 404 에러
curl http://localhost:8080/error/404

# 500 에러
curl http://localhost:8080/error/exception
```

## 📊 성능 특성

### 장점
- **직관적인 프로그래밍 모델**: 요청당 스레드로 간단한 동기 코딩
- **Keep-Alive 지원**: HTTP/1.1 연결 재사용으로 성능 향상
- **동적 스케일링**: 부하에 따른 자동 스레드풀 조정
- **완전한 격리**: 각 요청이 독립적인 스레드에서 처리

### 제한사항
- **메모리 사용량**: 스레드당 1MB 스택 메모리 사용
- **컨텍스트 스위칭**: 많은 동시 연결시 오버헤드 증가
- **스케일링 한계**: 수천 개의 동시 연결 처리에 한계

### 최적 사용 케이스
- **중간 규모 애플리케이션** (동시 연결 < 1000)
- **I/O 대기가 적은 CPU 집약적 작업**
- **기존 서블릿 API와 호환성이 필요한 경우**

## 🔍 모니터링 및 디버깅

### 실시간 통계 확인
```java
// 프로세서 상태
ThreadedProcessor.ProcessorStatus status = processor.getStatus();
System.out.println("Active Connections: " + status.getActiveConnections());
System.out.println("Total Requests: " + status.getTotalConnections());

// 스레드풀 상태
ThreadPoolManager.ThreadPoolStatus poolStatus = manager.getStatus();
System.out.println("Active Threads: " + poolStatus.getActiveThreads());
System.out.println("Queue Size: " + poolStatus.getQueueSize());
```

### 디버그 모드 활성화
```java
ServerConfig config = new ServerConfig()
    .setDebugMode(true)
    .setThreadPoolConfig(
        new ThreadPoolConfig().setDebugMode(true)
    )
    .setRequestHandlerConfig(
        new RequestHandlerConfig().setDebugMode(true)
    );
```

## 🔧 설정 최적화

### 개발 환경
```java
ServerConfig.developmentConfig()  // 소규모, 디버그 활성화
ThreadPoolConfig.developmentConfig()  // 2-10 스레드
RequestHandlerConfig.developmentConfig()  // 긴 타임아웃
```

### 프로덕션 환경
```java
ServerConfig.productionConfig()  // 대용량, 최적화된 설정
ThreadPoolConfig.highPerformanceConfig()  // 20-200 스레드
RequestHandlerConfig.secureConfig()  // 보안 강화 설정
```

### 고성능 환경
```java
ServerConfig.highPerformanceConfig()
    .setBacklogSize(200)
    .setReceiveBufferSize(32768)
    .setSendBufferSize(32768);
```

## 🔄 다음 단계

Phase 2 완료 후 다음을 진행할 수 있습니다:

1. **Phase 3: NIO Server** - 논블로킹 I/O 기반 서버
2. **Phase 4: Hybrid Server** - ThreadedServer + NIO 조합
3. **Phase 5: Reactive Server** - 완전 비동기 리액티브 서버

## 🏆 완성 체크리스트

- [ ] 모든 파일 생성 및 컴파일 성공
- [ ] ThreadedServerTest 실행 성공
- [ ] 기본 엔드포인트 동작 확인
- [ ] 부하 테스트로 스레드풀 스케일링 확인
- [ ] 통계 정보 정상 출력 확인
- [ ] Graceful shutdown 동작 확인

---

**🎯 Phase 2 목표 달성**: 전통적이지만 안정적인 Thread-per-Connection 서버 아키텍처 완성!