# Hybrid Server 구현 완전 정리

## 파일 구조 및 역할

```
src/main/java/server/hybrid/
├── HybridServer.java                    # 메인 서버 클래스
├── HybridProcessor.java                 # 요청 처리 엔진
├── HybridMiniServletContainer.java      # 서블릿 컨테이너
├── AdaptiveThreadPool.java              # 적응형 스레드풀
├── AsyncContextManager.java             # 비동기 컨텍스트 관리
├── ContextSwitchingHandler.java         # 컨텍스트 스위칭 핸들러
├── ChannelContext.java                  # NIO 채널 컨텍스트
├── MiniAsyncServlet.java                # 비동기 서블릿 추상 클래스
├── HelloWorldAsyncServlet.java          # Hello World 서블릿
├── UserApiAsyncServlet.java             # User API 서블릿
├── StaticFileAsyncServlet.java          # 정적 파일 서블릿
├── FileUploadAsyncServlet.java          # 파일 업로드 서블릿
├── LoadTestAsyncServlet.java            # 로드 테스트 서블릿
└── HybridServerTest.java                # 테스트 클래스
```

---

## 핵심 아키텍처 구성요소

### 1. HybridServer.java - 메인 서버 클래스
**역할**: NIO Selector + Thread Pool 조합의 하이브리드 서버

**핵심 특징**:
- NIO Selector로 I/O 이벤트 감지 (Non-blocking)
- Thread Pool에서 CPU 집약적 작업 처리
- AsyncContext로 I/O 대기시 스레드 해제
- Keep-Alive 연결 지원

**주요 메서드**:
```java
public void start()                    // 서버 시작
private void runSelectorLoop()         // NIO 이벤트 루프
private void handleAccept()            // 새 연결 수락
private void handleRead()              // 데이터 읽기
private void handleWrite()             // 응답 전송
```

**포트**: 8081 (ThreadedServer: 8080, EventLoopServer: 8082)

---

### 2. HybridProcessor.java - 요청 처리 엔진
**역할**: I/O 대기와 CPU 작업 분리, 비동기 컨텍스트 관리

**처리 전략**:
- **SYNC**: 동기 처리 (단순한 요청용)
- **ASYNC**: 비동기 처리 (I/O 집약적 요청용)
- **ADAPTIVE**: 적응형 처리 (부하에 따른 자동 선택)

**핵심 로직**:
```java
public CompletableFuture<HttpResponse> processRequest(HttpRequest request, RouteHandler handler)
private ProcessingStrategy selectStrategy(HttpRequest request, int concurrency)
private CompletableFuture<HttpResponse> processSynchronously()
private CompletableFuture<HttpResponse> processAsynchronously()
```

**자동 선택 기준**:
- 정적 파일 요청 → SYNC
- 높은 동시성 (80% 이상) → ASYNC
- DB/API 요청 → ASYNC
- 기본 → SYNC

---

### 3. AdaptiveThreadPool.java - 적응형 스레드풀
**역할**: 부하에 따른 동적 스레드 수 조정, 성능 최적화

**기능**:
- 우선순위 큐: 중요한 요청 우선 처리
- 동적 크기 조정: CPU 사용률에 따른 스레드 증감
- 백프레셔 처리: 과부하시 요청 제어
- 성능 모니터링: 처리량, 지연시간 추적

**핵심 기능**:
```java
public Future<?> submit(Runnable task, int priority)  // 우선순위 작업 제출
private void adjustPoolSize()                         // 스레드 수 동적 조정
public ThreadPoolStats getStats()                     // 성능 통계
```

**조정 기준**:
- 스레드 사용률 > 80% + 큐 대기 → 스레드 증가
- 스레드 사용률 < 30% + 큐 없음 → 스레드 감소
- 5초마다 모니터링 및 조정

---

### 4. AsyncContextManager.java - 비동기 컨텍스트 관리
**역할**: HTTP 요청의 비동기 처리 컨텍스트 관리, 메모리 누수 방지

**관리 대상**:
- 컨텍스트 ID 생성: nodeId-sequence 형태의 고유 식별자
- 상태 추적: CREATED → PROCESSING → WAITING → COMPLETED
- 타임아웃 처리: 기본 30초, 장시간 대기 요청 정리
- 속성 관리: 요청별 메타데이터 저장

**주요 기능**:
```java
public String createContext(HttpRequest request)       // 컨텍스트 생성
public AsyncContext getContext(String contextId)       // 컨텍스트 조회
public void updateContextState(String contextId, State state)  // 상태 업데이트
private void cleanupExpiredContexts()                  // 만료된 컨텍스트 정리
```

**상태 전환**:
```
CREATED (요청 접수) 
   ↓
PROCESSING (처리 시작)
   ↓
WAITING (I/O 대기) ← Context Switch Out
   ↓
COMPLETED (처리 완료) ← Context Switch In
```

---

### 5. ContextSwitchingHandler.java - 컨텍스트 스위칭 핸들러
**역할**: I/O 대기시 스레드 해제, I/O 완료시 스레드 재할당

**핵심 개념**:
- **Switch Out**: I/O 대기시 현재 스레드에서 컨텍스트 분리
- **Switch In**: I/O 완료시 새로운 스레드에서 컨텍스트 복원
- **컨텍스트 스위칭**: 스레드와 요청 컨텍스트 분리 관리

**활용 예시**:
```java
public CompletableFuture<String> executeDbOperation()    // DB 작업 with 컨텍스트 스위칭
public CompletableFuture<String> executeApiCall()        // API 호출 with 컨텍스트 스위칭
public CompletableFuture<byte[]> executeFileOperation()  // 파일 I/O with 컨텍스트 스위칭
```

**처리 흐름**:
```
Thread-A: 요청 처리 시작
    ↓
Switch Out: 컨텍스트 저장, Thread-A 해제
    ↓
I/O 작업: DB, API, File 등 (스레드 사용 안함)
    ↓
Switch In: Thread-B에서 컨텍스트 복원, 처리 계속
```

---

### 6. ChannelContext.java - NIO 채널 컨텍스트
**역할**: 채널별 HTTP 요청/응답 상태 관리, 부분 데이터 버퍼링

**관리 데이터**:
- 요청 파싱 상태: REQUEST_LINE → HEADERS → BODY → COMPLETE
- HTTP 메타데이터: Method, URI, Version, Headers
- 연결 정보: Keep-Alive, 요청 카운트, 활동 시간
- 속성 저장소: 채널별 메타데이터

**파싱 상태 관리**:
```java
public enum ParsingState {
    REQUEST_LINE,    // GET /path HTTP/1.1
    HEADERS,         // Host: localhost...
    BODY,            // POST 데이터
    COMPLETE         // 파싱 완료
}
```

---

## 서블릿 시스템

### 7. MiniAsyncServlet.java - 비동기 서블릿 추상 클래스
**역할**: Core의 MiniServlet을 상속받아 비동기 처리 기능 추가

**설계 원칙**:
- Core 시스템과 완전 호환
- 기존 service() 메서드는 final이므로 건드리지 않음
- 별도의 processAsync() 메서드 제공

**메서드 구조**:
```java
public CompletableFuture<Void> processAsync()      // 비동기 처리 진입점
protected CompletableFuture<Void> doGetAsync()     // HTTP GET 비동기 처리
protected CompletableFuture<Void> doPostAsync()    // HTTP POST 비동기 처리
// ... 기타 HTTP 메서드들
```

**Core 호환성**:
- MiniServlet을 extends
- service() 메서드는 그대로 유지
- processAsync()는 하이브리드 전용 추가 메서드

---

### 8. HybridMiniServletContainer.java - 서블릿 컨테이너
**역할**: 하이브리드 방식의 서블릿 생명주기 관리

**핵심 기능**:
- 서블릿 타입 구분: instanceof MiniAsyncServlet로 비동기/동기 구분
- 서블릿 풀링: 인스턴스 재사용으로 성능 최적화
- 라우팅 통합: Router와 서블릿 시스템 연동
- 비동기 처리: processAsync() 호출로 비동기 서블릿 처리

**처리 흐름**:
```java
1. handleServletRequest() - 요청 접수
2. findServletForPath() - URL 패턴 매칭
3. instanceof 체크 - 서블릿 타입 구분
4. handleAsyncServlet() 또는 handleSyncServlet() 호출
5. 서블릿 풀에서 인스턴스 가져오기
6. processAsync() 또는 service() 호출
7. 결과 반환 및 인스턴스 풀에 반납
```

**서블릿 풀링**:
- 초기 3개 인스턴스 생성
- 최대 10개까지 풀 크기 확장
- 사용 후 풀로 반환, 풀 초과시 destroy()

---

## 서블릿 구현체들

### 9-13. 실제 서블릿 구현체들

| 서블릿 | 역할 | Threaded 대응 |
|--------|------|---------------|
| **HelloWorldAsyncServlet** | 기본 Hello World | HelloWorldServlet |
| **UserApiAsyncServlet** | REST API (GET/POST) | (Threaded에 없음) |
| **StaticFileAsyncServlet** | 정적 파일 서빙 | StaticFileServlet |
| **FileUploadAsyncServlet** | 파일 업로드 | FileUploadServlet |
| **LoadTestAsyncServlet** | 성능 테스트 | (Threaded에 없음) |

**공통 특징**:
- CompletableFuture.runAsync() 사용
- Thread.sleep()으로 I/O 대기 시뮬레이션
- 스레드 이름 출력으로 비동기 처리 확인
- "processing": "async" 표시로 Threaded와 구분

**구현 패턴**:
```java
@Override
protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
    return CompletableFuture.runAsync(() -> {
        try {
            // 비동기 I/O 시뮬레이션
            Thread.sleep(100);
            
            // 응답 생성
            response.sendJson("{ \"result\": \"async processing\" }");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted");
        }
    });
}
```

---

## 핵심 동작 원리

### Hybrid Server의 요청 처리 흐름

```
1. 클라이언트 요청
   ↓
2. NIO Selector 이벤트 감지
   ↓
3. SocketChannel.read() - Non-blocking
   ↓
4. ChannelContext에 데이터 축적
   ↓
5. HTTP 요청 완전한가? 
   No → 3번으로 돌아가서 더 읽기
   Yes → 6번으로 진행
   ↓
6. ThreadPool.submit() - CPU 작업을 스레드풀로
   ↓
7. HybridProcessor.processRequest()
   ↓
8. 처리 전략 선택 (SYNC/ASYNC/ADAPTIVE)
   ↓
9-A. SYNC: 현재 스레드에서 완료까지 처리
9-B. ASYNC: CompletableFuture로 비동기 처리
   ↓
10. (ASYNC의 경우) ContextSwitchingHandler
   ↓
11. Switch Out - 스레드 해제
   ↓
12. I/O 작업 (DB, API, File)
   ↓
13. Switch In - 새 스레드에서 재개
   ↓
14. 응답 준비
   ↓
15. NIO Selector로 WRITE 이벤트 등록
   ↓
16. SocketChannel.write() - Non-blocking
   ↓
17. 클라이언트 응답 완료
```

---

## 핵심 차별점

### Threaded vs Hybrid 비교

| 구분 | Threaded Server | Hybrid Server |
|------|-----------------|---------------|
| **I/O 모델** | Blocking I/O | Non-blocking I/O (NIO) |
| **스레드 사용** | 요청당 스레드 할당 | I/O 이벤트 감지 + 작업 스레드 분리 |
| **I/O 대기시** | 스레드 점유 유지 | 스레드 해제 (Context Switching) |
| **메모리 사용** | 높음 (스레드당 스택) | 낮음 (스레드 재사용) |
| **동시 연결** | ~500 | ~10,000+ |
| **CPU 효율** | 낮음 | 높음 |
| **구현 복잡도** | 단순 | 복잡 |

### Context Switching의 핵심

**기존 방식** (Threaded):
```java
Thread-1: HTTP 요청 받음 → DB 대기 (블로킹) → 응답 반환
Thread-2: HTTP 요청 받음 → DB 대기 (블로킹) → 응답 반환
// 각 스레드가 I/O 대기시에도 계속 점유
```

**Hybrid 방식**:
```java
Selector: HTTP 요청 감지 → Thread-A에 전달
Thread-A: 요청 처리 시작 → DB 작업 필요 → Context Switch Out
         (Thread-A 해제, 컨텍스트는 AsyncContextManager에 저장)
Thread-B: DB 작업 완료 → Context Switch In → 응답 완료
// 스레드는 실제 CPU 작업시에만 사용
```

---

## 성능 최적화 요소

### 1. Adaptive Thread Pool
- 부하에 따른 동적 스레드 수 조정
- 우선순위 큐로 중요 요청 우선 처리
- 백프레셔로 과부하 상황 제어

### 2. Context Switching
- I/O 대기시 스레드 해제로 효율성 극대화
- 컨텍스트 복원으로 요청 연속성 보장

### 3. Servlet Pooling
- 서블릿 인스턴스 재사용으로 GC 압박 감소
- 초기화 비용 최소화

### 4. NIO Event Loop
- 단일 스레드로 수천 개 연결 관리
- OS 레벨 이벤트 알림 활용

---

## 예상 성능 비교

| 측정 항목 | Threaded | Hybrid | 개선율 |
|-----------|----------|--------|--------|
| **동시 연결** | 500 | 10,000+ | **20x** |
| **메모리 사용** | 1GB | 200MB | **5x** |
| **CPU 효율** | 60% | 90%+ | **1.5x** |
| **응답 지연** | 100ms | 50ms | **2x** |
| **처리량** | 1,000 req/s | 5,000 req/s | **5x** |

---

## 학습 효과

### 기술적 이해
1. **NIO vs BIO** 본질적 차이 체득
2. **Event Loop vs Thread Pool** 아키텍처 비교
3. **비동기 프로그래밍** 패턴 완전 습득
4. **Context Switching** 개념과 구현 이해

### 실무 적용
1. **Spring WebFlux** 동작 원리 이해
2. **Netty, Vert.x** 아키텍처 파악
3. **Node.js** 이벤트 루프 이해
4. **마이크로서비스** 환경에서 서버 선택 기준

---

## 실행 방법

### 서버 실행
```bash
# Hybrid 서버 실행 (포트 8081)
./scripts/run-hybrid.sh

# 또는 Java 직접 실행
java -cp . server.hybrid.HybridServerTest
```

### 테스트 엔드포인트
```bash
# Hello World 테스트
curl http://localhost:8081/hello?name=Hybrid

# User API 테스트
curl http://localhost:8081/api/users

# 정적 파일 테스트  
curl http://localhost:8081/static/test.js

# 로드 테스트
curl http://localhost:8081/load-test

# 파일 업로드 테스트
curl -X POST http://localhost:8081/upload \
     -F "file=@test.txt" \
     -F "description=Test file"
```

### 성능 비교 테스트
```bash
# 3개 서버 동시 실행
./scripts/run-threaded.sh  # 포트 8080
./scripts/run-hybrid.sh    # 포트 8081  
./scripts/run-eventloop.sh # 포트 8082

# 부하 테스트
./scripts/run-benchmark.sh --concurrent=1000 --duration=60s
```

이제 **실제 프로덕션 급 서버의 동작 원리**를 완전히 이해하게 되었습니다!