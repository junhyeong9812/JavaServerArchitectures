# 벤치마크 문제 해결 과정 정리

## 🚨 발견된 문제들

### 1. LoadTestClient HTTP 헤더 문제
**문제**: Java HttpClient에서 `Connection` 헤더 직접 설정 제한
```
DEBUG s.b.LoadTestClient - Health check error: restricted header name: "Connection"
```

**해결**: Connection 헤더 제거
```java
// 문제 코드
.header("Connection", "close")

// 해결 코드
// Connection 헤더 완전 제거 (HttpClient가 자동 관리)
```

### 2. ThreadedServer 서블릿 등록 문제
**문제**: Handler는 등록되지만 Servlet은 0개 로드됨
```
[Handler registered: /hello, /health, /cpu-intensive, /io-simulation]
[Servlet container initialization completed - 0 servlets loaded]
```

**원인**:
- BenchmarkRunner에서 `registerHandler()` 사용
- ThreadedServerTest에서는 `registerServlet()` 사용
- ServletContainer와 fallbackRouter 혼용으로 복잡성 증가

### 3. HybridServer 크래시
**문제**: PriorityBlockingQueue에서 ClassCastException
```
Exception: class java.util.concurrent.FutureTask cannot be cast to class java.lang.Comparable
```

### 4. EventLoopServer I/O 오류
**문제**: HTTP 요청 파싱 실패
```
ERROR s.e.EventLoop - Error processing I/O event for key: channel=...
DEBUG s.b.LoadTestClient - Request failed: HTTP/1.1 header parser received no bytes
```

### 5. ThreadedServer mark/reset 에러 ⭐ 새로 추가
**문제**: HTTP 요청 파싱 시 mark/reset 지원되지 않음
```
[ServerThread-1] ServletContainer integration enabled
Request parsing failed: mark/reset not supported
[ServerThread-1] I/O error: mark/reset not supported
[ServerThread-1] Connection closed - total time: 1ms, requests: 0
```

**원인**: `Socket.getInputStream()`은 mark/reset을 지원하지 않음
```java
// 문제 코드 (BlockingRequestHandler.parseRequest)
private HttpRequest parseRequest(InputStream inputStream) throws IOException {
    inputStream.mark(1);           // ← Socket InputStream은 mark/reset 미지원!
    int firstByte = inputStream.read();
    if (firstByte == -1) {
        return null;
    }
    inputStream.reset();           // ← 여기서 에러 발생
    return HttpParser.parseRequest(inputStream);
}
```

**해결**: BufferedInputStream으로 감싸기
```java
// BlockingRequestHandler.run() 수정
InputStream inputStream = new BufferedInputStream(clientSocket.getInputStream(), 8192);
OutputStream outputStream = new BufferedOutputStream(clientSocket.getOutputStream(), 8192);
```

## 🔍 ThreadedServerTest 구동 과정에서 생긴 문제들

### 1. 벤치마크 제어권 상실 문제
**문제**: `ThreadedServerTest.main()` 호출 시 무한 루프 진입
```java
// ThreadedServerTest.main()에서
Thread.currentThread().join();  // ← 여기서 무한 대기
```

**결과**:
- 벤치마크의 나머지 코드 실행 안됨
- HybridServer, EventLoopServer 시작 안됨
- 벤치마크 종료 로직 실행 안됨

### 2. 서블릿 접근 제한자 문제
**문제**: package-private 서블릿들을 외부 패키지에서 접근 불가
```java
// server.threaded 패키지 내부
class IoSimulationServlet extends MiniServlet {  // ← package-private
class CpuIntensiveServlet extends MiniServlet { // ← package-private
```

**에러**:
```
'server.threaded.IoSimulationServlet' is not public in 'server.threaded'. 
Cannot be accessed from outside package
```

**해결**: public으로 변경
```java
public class IoSimulationServlet extends MiniServlet {  // ← public 추가
public class CpuIntensiveServlet extends MiniServlet { // ← public 추가
```

### 3. 벤치마크 API 불일치 문제
**문제**: 각 서버마다 다른 API 스타일 사용
- ThreadedServer: ServletContainer 방식 (`registerServlet()`)
- HybridServer: Router 방식 (`getRouter().get()`)
- EventLoopServer: Router 방식 (`server.get()`)

**시도했던 잘못된 접근들**:
```java
// ❌ 시도 1: ThreadedServer도 Handler 방식으로 통일
server.registerHandler("/hello", handler);  // ServletContainer 무시됨

// ❌ 시도 2: 복잡한 통합 핸들러 구현
setupThreadedRoutes() 메서드로 Handler 등록

// ❌ 시도 3: 서블릿을 직접 구현해서 등록
new MiniServlet() { ... } 방식으로 인라인 생성
```

## 🔍 분석 과정

### 문제 해결 시행착오 과정
1. **첫 번째 시도**: BenchmarkRunner에서 ThreadedServer 직접 제어
    - 결과: 서블릿 0개 로드, Handler/ServletContainer 충돌

2. **두 번째 시도**: ThreadedServerTest.main() 호출
    - 결과: 무한 루프 진입, 벤치마크 중단

3. **세 번째 시도**: ThreadedServer를 별도 스레드에서 실행
    - 결과: 복잡성 증가, mark/reset 에러 발견

4. **네 번째 시도**: mark/reset 문제 해결
    - 결과: **성공!** HTTP 요청 정상 처리

### 아키텍처 설계 관점에서의 고민
1. **Router vs ServletContainer**: 어느 것이 메인 처리기가 되어야 하는가?
2. **코드 중복**: BenchmarkRunner와 ThreadedServerTest에서 동일한 로직 구현
3. **책임 분리**: 누가 서블릿 등록을 담당해야 하는가?

### 핵심 깨달음
- **기존 검증된 코드의 소중함**: ThreadedServerTest는 이미 완벽함
- **Stream 처리의 미묘함**: Socket InputStream vs BufferedInputStream
- **API 일관성의 중요성**: 서버마다 다른 등록 방식의 혼란

## ✅ 최종 해결 방안

### mark/reset 문제 해결 (핵심 솔루션)
```java
// BlockingRequestHandler.run() 수정
@Override
public void run() {
    try {
        // ⭐ 핵심: BufferedInputStream으로 감싸기
        InputStream inputStream = new BufferedInputStream(clientSocket.getInputStream(), 8192);
        OutputStream outputStream = new BufferedOutputStream(clientSocket.getOutputStream(), 8192);
        
        // 나머지 로직은 동일...
        while (keepAlive && requestCount < config.getMaxRequestsPerConnection()) {
            HttpRequest request = parseRequest(inputStream);  // 이제 정상 작동
            // ...
        }
    }
}
```

### 서블릿 접근성 해결
```java
// server.threaded 패키지에서
public class IoSimulationServlet extends MiniServlet {     // public 추가
public class CpuIntensiveServlet extends MiniServlet {    // public 추가
public class HealthServlet extends MiniServlet {          // public 확인
```

### LoadTestClient 수정
```java
// 수정된 헬스체크 메서드
private boolean tryHealthCheckPath(String host, int port, String path) {
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(5))
            .header("User-Agent", "LoadTestClient/1.0")
            // Connection 헤더 제거 (HttpClient가 자동 관리)
            .GET()
            .build();
    
    // 200-499 범위 모두 OK (서버 응답 확인)
    boolean healthy = response.statusCode() >= 200 && response.statusCode() < 500;
}
```

### 벤치마크 서블릿 등록 방식
```java
// BenchmarkRunner에서 - 기존 검증된 방식 활용
private void registerThreadedServlets(ThreadedServer server) {
    server.registerServlet("/health", new server.examples.HealthServlet());
    server.registerServlet("/hello", new server.examples.HelloWorldServlet());
    server.registerServlet("/cpu-intensive", new server.examples.CpuIntensiveServlet());
    server.registerServlet("/io-simulation", new server.examples.IoSimulationServlet());
}
```

## 🎯 핵심 교훈

### 1. Stream 처리의 복잡성
- **Socket InputStream**: mark/reset 미지원
- **BufferedInputStream**: mark/reset 지원
- **미묘한 차이가 큰 영향**: 단 한 줄의 차이로 전체 시스템 동작 불가

### 2. 문제 해결 접근법의 진화
```
1차: "벤치마크에서 모든 걸 제어하자" → 복잡성 증가
2차: "기존 코드를 그대로 쓰자" → 제어권 상실
3차: "하이브리드 방식으로 가자" → 더 복잡해짐
4차: "핵심 문제만 해결하자" → 성공! ✅
```

### 3. 디버깅의 중요성
- **로그 분석**: `mark/reset not supported` 메시지가 핵심 단서
- **단계별 접근**: 복잡한 문제를 작은 단위로 분해
- **검증된 방법**: curl 테스트로 서버 동작 확인

### 4. 아키텍처 설계 원칙
- **단일 책임 원칙**: ServletContainer가 요청 처리 담당
- **코드 재사용**: 동일한 로직을 여러 곳에서 구현하지 않기
- **검증된 패턴**: 이미 작동하는 구조 그대로 활용

### 5. 기존 검증된 코드 활용의 중요성
- ThreadedServerTest는 이미 완벽하게 작동함
- 새로 구현하는 대신 기존 코드 재활용
- 코드 중복 제거 및 유지보수성 향상

## 📋 최종 성공 로그

### 문제 해결 전
```
Request parsing failed: mark/reset not supported
[ServerThread-1] I/O error: mark/reset not supported
[ServerThread-1] Connection closed - total time: 1ms, requests: 0
```

### 문제 해결 후
```
[ServerThread-1] ServletContainer integration enabled
Request handled by ServletContainer: /hello
[ServerThread-1] Request 1 processed in 41ms - GET /hello
Request handled by ServletContainer: /favicon.ico
[ServerThread-1] Request 2 processed in 1ms - GET /favicon.ico
```

## 🚀 최종 결과

### 변경 전
```
❌ mark/reset not supported 에러
❌ HTTP 요청 파싱 실패
❌ 모든 연결이 0ms에 종료
❌ ServletContainer 요청 처리 안됨
❌ 헬스체크 실패 (Connection 헤더 문제)
❌ 서블릿 0개 로드 문제
```

### 변경 후
```
✅ BufferedInputStream으로 mark/reset 지원
✅ HTTP 요청 정상 파싱
✅ ServletContainer에서 요청 정상 처리
✅ 41ms, 1ms 등 정상적인 처리 시간
✅ /hello, /favicon.ico 모두 성공
✅ 헬스체크 통과
✅ 모든 서블릿 정상 로드
```

## 📋 제거할 코드들

```java
// BenchmarkRunner에서 제거
❌ setupThreadedRoutes() 메서드 전체
❌ 복잡한 Handler 등록 로직
❌ ServletContainer와 Router 혼용 로직
❌ ThreadedServerTest.main() 직접 호출
```

## 💡 향후 개선 방향

1. **다른 서버들도 동일한 패턴 적용**
    - HybridServer: ChannelContext에서 BufferedInputStream 사용 여부 확인
    - EventLoopServer: NonBlockingHandler HTTP 파싱 로직 점검

2. **공통 HTTP 파싱 유틸리티 개발**
    - mark/reset 문제를 방지하는 안전한 파싱 래퍼
    - 모든 서버에서 공통 사용 가능한 파싱 로직

3. **통합 테스트 강화**
    - BufferedInputStream 사용 여부 자동 검증
    - HTTP 파싱 성공률 모니터링

4. **HybridServer**: PriorityBlockingQueue → 일반 BlockingQueue로 변경

5. **EventLoopServer**: NonBlockingHandler HTTP 파싱 로직 수정

---

**핵심 메시지**: 복잡한 아키텍처 문제로 보였던 것이 실제로는 **단순한 Stream 처리 문제**였다. 때로는 가장 기본적인 부분에서 가장 큰 문제가 발생한다! 그리고 새로운 코드를 작성하는 것보다 **기존의 검증된 코드를 그대로 활용**하는 것이 최고의 해결책일 수 있다! 🎯