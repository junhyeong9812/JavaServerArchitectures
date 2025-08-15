# Phase 2.1 - Traditional Thread-per-Request Server 구현

## 📋 개요

Phase 2.1에서는 **전통적인 Thread-per-Request 방식의 HTTP 서버**를 완전히 구현했습니다. 이는 각 클라이언트 연결마다 별도의 스레드를 할당하여 처리하는 가장 직관적이고 이해하기 쉬운 서버 아키텍처입니다.

## 🎯 구현 목표

- ✅ **전통적 Thread-per-Request 모델** 완전 구현
- ✅ **블로킹 I/O 기반** 동기식 처리
- ✅ **스레드 풀 관리** 및 최적화
- ✅ **완전한 서블릿 API 지원**
- ✅ **실시간 모니터링** 및 통계
- ✅ **우아한 종료 처리**
- ✅ **크로스 플랫폼 실행 스크립트**

## 🏗️ 아키텍처 개요

```
Client Request → ServerSocket → ThreadPool → RequestHandler Thread
                                                ↓
                                        HTTP Request Parsing
                                                ↓
                                         ServletContainer
                                                ↓
                                           Filter Chain
                                                ↓
                                            Servlet
                                                ↓
                                        HTTP Response
                                                ↓
                                         Client Response
```

### 핵심 특징

1. **Thread-per-Request**: 각 요청을 별도 스레드에서 처리
2. **동기식 블로킹**: 모든 I/O 작업이 블로킹 방식으로 수행
3. **스레드 풀 기반**: 미리 생성된 스레드 풀에서 요청 처리
4. **완전한 격리**: 각 요청이 독립적인 스레드 컨텍스트에서 실행

## 📁 구현된 파일 구조

```
src/main/java/com/com.serverarch/traditional/
├── ThreadedServer.java                    # 메인 HTTP 서버
├── ThreadedRequestProcessor.java          # 요청 처리 로직
├── ThreadPoolManager.java                 # 고급 스레드 풀 관리
├── ThreadedServletContainer.java          # 서블릿 컨테이너
└── ThreadedServerLauncher.java            # 통합 실행기

scripts/
├── run-threaded-server.sh                 # Linux/macOS 실행 스크립트
└── run-threaded-server.bat                # Windows 실행 스크립트
```

## 🔍 주요 구현 내용

### 1. ThreadedServer.java - 메인 HTTP 서버

**전통적인 Thread-per-Request 아키텍처의 핵심 구현**

```java
public class ThreadedServer {
    // 각 클라이언트 연결을 별도 스레드에서 처리
    private void runServerLoop() {
        while (running.get()) {
            Socket clientSocket = serverSocket.accept();        // 연결 수락
            threadPool.submit(new RequestHandler(clientSocket)); // 스레드 할당
        }
    }
    
    // RequestHandler: 개별 요청 처리 담당
    private class RequestHandler implements Runnable {
        public void run() {
            // 1. HTTP 요청 파싱 (블로킹 I/O)
            HttpRequest request = parseHttpRequest(inputStream);
            
            // 2. 서블릿 처리 (블로킹)
            HttpResponse response = processWithServlet(request, servletInfo);
            
            // 3. HTTP 응답 전송 (블로킹 I/O)
            sendHttpResponse(outputStream, response);
        }
    }
}
```

**주요 특징:**
- **완전한 서버 생명주기 관리** (시작/중지)
- **우아한 종료 처리** (기존 연결 완료 대기)
- **상세한 통계 수집** (요청 수, 처리 시간, 오류율)
- **스레드 안전한 구현**
- **설정 가능한 스레드 풀 크기**

### 2. ThreadedRequestProcessor.java - 요청 처리기

**동기식 HTTP 요청 처리 로직의 완전한 구현**

```java
public class ThreadedRequestProcessor {
    // 동기식 요청 처리 메인 로직
    public void processRequest(InputStream inputStream, OutputStream outputStream) {
        // 1. 요청 파싱 (타임아웃 처리 포함)
        HttpRequest httpRequest = parseHttpRequest(inputStream);
        
        // 2. 요청 처리 (서블릿 컨테이너 위임)
        HttpResponse httpResponse = processHttpRequest(httpRequest);
        
        // 3. 응답 전송
        sendHttpResponse(outputStream, httpResponse);
    }
    
    // 서블릿을 통한 요청 처리
    private HttpResponse processWithServlet(HttpRequest request, ServletInfo servletInfo) {
        // 서블릿 초기화 확인
        ensureServletInitialized(servletInfo);
        
        // 필터 체인 생성 및 실행
        FilterChain filterChain = filterManager.createFilterChain(request.getPath(), servlet);
        filterChain.doFilter(servletRequest, servletResponse);
        
        return convertToHttpResponse(servletResponse);
    }
}
```

**주요 특징:**
- **완전한 서블릿 API 지원**
- **타임아웃 처리 및 오류 복구**
- **성능 메트릭 수집**
- **스레드 안전한 처리**
- **비동기 작업 지원** (필요시)

### 3. ThreadPoolManager.java - 고급 스레드 풀 관리자

**엔터프라이즈급 스레드 풀 관리 및 모니터링**

```java
public class ThreadPoolManager {
    // 동적 스레드 풀 생성
    private final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
        corePoolSize, maximumPoolSize, keepAliveTime, unit,
        workQueue, threadFactory, rejectedExecutionHandler
    );
    
    // 실시간 모니터링
    private void logThreadPoolStatus() {
        ThreadPoolStatus status = getStatus();
        logger.info(String.format(
            "스레드풀 - 활성: %d/%d, 큐: %d, 완료: %d, 거부: %d",
            status.getActiveCount(), status.getCurrentPoolSize(),
            status.getQueueSize(), metrics.getCompletedTasks(), metrics.getRejectedTasks()
        ));
    }
    
    // 백프레셔 정책
    private class CustomRejectedExecutionHandler implements RejectedExecutionHandler {
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            // CallerRuns 정책 + 통계 수집
            if (!executor.isShutdown()) {
                r.run(); // 호출 스레드에서 직접 실행
            }
        }
    }
}
```

**주요 특징:**
- **동적 스레드 풀 크기 조정**
- **실시간 성능 모니터링**
- **백프레셔(Backpressure) 정책**
- **작업 실행 시간 측정**
- **우아한 종료 처리**
- **경고 및 알림 시스템**

### 4. ThreadedServletContainer.java - 서블릿 컨테이너

**완전한 서블릿 런타임 환경 제공**

```java
public class ThreadedServletContainer {
    // HTTP 요청을 서블릿으로 처리
    public HttpResponse processRequest(HttpRequest httpRequest) {
        // 서블릿 조회
        ServletInfo servletInfo = servletRegistry.findServletByPath(httpRequest.getPath());
        
        // 서블릿 처리
        return processWithServlet(httpRequest, servletInfo);
    }
    
    // 세션 처리
    private void handleSessionProcessing(HttpServletRequestImpl servletRequest, 
                                       HttpRequest httpRequest) {
        // 세션 ID 추출
        String sessionId = extractSessionIdFromRequest(httpRequest);
        
        // 기존 세션 조회 또는 새 세션 생성 준비
        if (sessionId != null) {
            HttpSession session = sessionManager.getSession(sessionId);
            servletRequest.setSession(session);
        }
    }
}
```

**주요 특징:**
- **완전한 서블릿 생명주기 관리**
- **세션 관리 및 쿠키 처리**
- **필터 체인 실행**
- **동기식 요청/응답 처리**
- **컨테이너 통계 수집**

### 5. ThreadedServerLauncher.java - 통합 실행기

**완전한 실행 환경과 데모 애플리케이션 제공**

```java
public class ThreadedServerLauncher {
    // 데모 서블릿 등록
    private void registerDemoServlets() throws ServletException {
        container.registerServlet("HelloServlet", HelloServlet.class, "/hello", "/");
        container.registerServlet("TimeServlet", TimeServlet.class, "/time");
        container.registerServlet("EchoServlet", EchoServlet.class, "/echo");
        container.registerServlet("StatsServlet", StatsServlet.class, "/stats");
        container.registerServlet("SessionServlet", SessionServlet.class, "/session");
    }
    
    // 데모 필터 등록
    private void registerDemoFilters() throws ServletException {
        container.registerFilter("LoggingFilter", new LoggingFilter(), "/*");
        container.registerFilter("TimingFilter", new TimingFilter(), "/*");
    }
    
    // Hello World 서블릿 구현
    public static class HelloServlet extends HttpServlet {
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
            resp.setContentType("text/html; charset=UTF-8");
            resp.getWriter().println("<h1>안녕하세요! ThreadedServer입니다</h1>");
            resp.getWriter().println("<p>현재 시간: " + new Date() + "</p>");
        }
    }
}
```

**제공되는 데모 서블릿:**
- **HelloServlet** (`/`, `/hello`) - Welcome 페이지
- **TimeServlet** (`/time`) - 현재 시간 JSON 응답
- **EchoServlet** (`/echo`) - POST 요청 에코
- **StatsServlet** (`/stats`) - 서버 통계 및 스레드 정보
- **SessionServlet** (`/session`) - 세션 테스트 및 관리

**제공되는 필터:**
- **LoggingFilter** - 요청/응답 로깅
- **TimingFilter** - 응답 시간 측정 및 헤더 추가

## 🚀 실행 방법

### Linux/macOS 실행

```bash
# 기본 실행 (포트 8080, 스레드 100개)
./scripts/run-threaded-server.sh

# 사용자 정의 설정
./scripts/run-threaded-server.sh -p 9090 -t 200 -m 1g

# 디버그 모드
./scripts/run-threaded-server.sh -d -v

# 정리 후 빌드
./scripts/run-threaded-server.sh --clean

# 도움말
./scripts/run-threaded-server.sh --help
```

### Windows 실행

```cmd
REM 기본 실행
scripts\run-threaded-server.bat

REM 사용자 정의 설정  
scripts\run-threaded-server.bat -p 9090 -t 200 -m 1g

REM 디버그 모드
scripts\run-threaded-server.bat -d -v

REM 도움말
scripts\run-threaded-server.bat --help
```

### 실행 옵션

| 옵션 | 설명 | 기본값 |
|------|------|--------|
| `-p, --port` | 서버 포트 번호 | 8080 |
| `-t, --threads` | 스레드 풀 크기 | 100 |
| `-m, --memory` | JVM 힙 메모리 크기 | 512m |
| `-d, --debug` | 디버그 모드 활성화 | false |
| `-v, --verbose` | 상세 출력 모드 | false |
| `-c, --clean` | 빌드 전 정리 | false |
| `--no-build` | 빌드 없이 실행 | false |
| `-h, --help` | 도움말 표시 | - |

## 🌐 제공되는 엔드포인트

### 1. Welcome 페이지 (`/` 또는 `/hello`)
```http
GET / HTTP/1.1
Host: localhost:8080

→ HTML 페이지로 서버 정보 및 링크 제공
```

### 2. 시간 서비스 (`/time`)
```http
GET /time HTTP/1.1
Host: localhost:8080

→ {"timestamp":1640995200000,"time":"Sat Jan 01 00:00:00 KST 2022"}
```

### 3. 에코 서비스 (`/echo`)
```http
POST /echo HTTP/1.1
Host: localhost:8080
Content-Type: text/plain

Hello World

→ 요청 정보와 바디 내용을 에코
```

### 4. 서버 통계 (`/stats`)
```http
GET /stats HTTP/1.1  
Host: localhost:8080

→ HTML 페이지로 서버 통계, 스레드 정보, 시스템 정보 표시
```

### 5. 세션 테스트 (`/session`)
```http
GET /session HTTP/1.1
Host: localhost:8080

→ 세션 생성/조회, 속성 관리, 세션 정보 표시
```

## 📊 성능 특성

### 장점 ✅

1. **간단하고 직관적**
    - 이해하기 쉬운 구조
    - 디버깅이 용이함
    - 순차적 실행 흐름

2. **안정적인 처리**
    - 각 요청이 독립적
    - 예외 격리가 자연스럽게 이루어짐
    - 예측 가능한 동작

3. **완전한 서블릿 호환성**
    - 기존 서블릿 코드 그대로 사용 가능
    - 표준 API 완전 지원
    - 서드파티 라이브러리 호환

4. **선형적 확장**
    - 스레드 수에 비례하는 처리 능력
    - 명확한 성능 한계

### 한계 ⚠️

1. **높은 메모리 사용량**
    - 스레드당 1-2MB 스택 메모리
    - 1000 스레드 = ~2GB 메모리 필요

2. **제한된 동시성**
    - 일반적으로 ~500개 동시 연결 한계
    - OS의 스레드 생성 한계에 의존

3. **컨텍스트 스위칭 오버헤드**
    - 많은 스레드 간 전환 비용
    - CPU 코어 수 대비 과도한 스레드 시 성능 저하

4. **I/O 대기 시 리소스 낭비**
    - 블로킹 I/O 중 스레드 유휴
    - 네트워크 지연 시 효율성 저하

### 성능 벤치마크 예상치

| 메트릭 | 예상 성능 |
|--------|-----------|
| **동시 연결** | ~500개 |
| **처리량** | ~1,000 req/sec |
| **응답 시간** | 10-50ms (CPU 작업) |
| **메모리 사용량** | 높음 (스레드당 1-2MB) |
| **CPU 사용률** | 중간 (컨텍스트 스위칭) |

## 📈 모니터링 및 메트릭

### 서버 통계
- **총 요청 수** / **처리 완료 수** / **실패 수**
- **현재 활성 연결 수**
- **평균 응답 시간**
- **처리량** (req/sec)

### 스레드 풀 통계
- **활성 스레드 수** / **최대 스레드 수**
- **큐 대기 작업 수** / **큐 잔여 용량**
- **완료된 작업 수** / **거부된 작업 수**
- **평균 작업 실행 시간**

### 컨테이너 통계
- **등록된 서블릿 수** / **필터 수**
- **활성 세션 수** / **생성된 세션 수**
- **서블릿별 처리 시간**
- **오류율**

### 실시간 모니터링 로그 예시

```
[INFO] 스레드풀 상태 - 활성: 45/100, 큐: 12, 완료: 15420, 거부: 0, 평균실행시간: 25.3ms, 처리량: 156.7작업/초
[INFO] 요청 처리 완료: GET /hello (23ms)
[INFO] 새 세션 쿠키 설정: A1B2C3D4E5F6
[WARNING] 느린 요청 처리: POST /echo (1250ms)
```

## 🔧 설정 및 튜닝

### JVM 옵션 (자동 설정됨)
```bash
-Xmx512m                    # 최대 힙 크기
-Xms512m                    # 초기 힙 크기  
-server                     # 서버 모드
-XX:+UseG1GC               # G1 가비지 컬렉터
-XX:+UseStringDeduplication # 문자열 중복 제거
-Djava.awt.headless=true   # 헤드리스 모드
```

### 스레드 풀 튜닝 가이드

**CPU 집약적 작업:**
```bash
# CPU 코어 수와 동일하게 설정
./run-threaded-server.sh -t $(nproc)
```

**I/O 집약적 작업:**
```bash  
# CPU 코어 수의 2-4배로 설정
./run-threaded-server.sh -t $(($(nproc) * 3))
```

**메모리 제약 환경:**
```bash
# 스레드 수를 줄이고 메모리 증가
./run-threaded-server.sh -t 50 -m 1g
```

## 🧪 테스트 및 검증

### 기능 테스트

```bash
# 서버 시작 후 다음 명령들로 테스트

# 1. 기본 동작 확인
curl http://localhost:8080/

# 2. JSON 응답 확인  
curl http://localhost:8080/time

# 3. POST 요청 테스트
curl -X POST -d "Hello World" http://localhost:8080/echo

# 4. 세션 테스트
curl -c cookies.txt -b cookies.txt http://localhost:8080/session

# 5. 서버 통계 확인
curl http://localhost:8080/stats
```

### 부하 테스트 (권장 도구)

**Apache Bench (ab):**
```bash
# 100 동시 연결, 1000 요청
ab -n 1000 -c 100 http://localhost:8080/hello
```

**wrk:**
```bash  
# 10 스레드, 100 연결, 30초 동안
wrk -t10 -c100 -d30s http://localhost:8080/hello
```

**curl 스크립트:**
```bash
# 다중 동시 요청 (백그라운드)
for i in {1..50}; do
  curl http://localhost:8080/time &
done
wait
```

## 🐛 트러블슈팅

### 자주 발생하는 문제들

**1. "Address already in use" 오류**
```bash
# 포트 사용 중 확인
netstat -tulpn | grep :8080

# 다른 포트로 실행
./run-threaded-server.sh -p 8081
```

**2. OutOfMemoryError**
```bash
# 힙 메모리 증가
./run-threaded-server.sh -m 2g

# 또는 스레드 수 감소
./run-threaded-server.sh -t 50
```

**3. 컴파일 오류**
```bash
# Java 버전 확인 (11+ 필요)
java -version

# 정리 후 재빌드
./run-threaded-server.sh --clean
```

**4. 느린 응답 시간**
```bash
# 디버그 모드로 실행하여 병목 지점 확인
./run-threaded-server.sh -d -v

# 스레드 풀 크기 조정
./run-threaded-server.sh -t 200
```

### 로그 분석

**성능 문제 식별:**
```
[WARNING] 느린 요청 처리: GET /slow-endpoint (5000ms)
[WARNING] 높은 작업 거부율: 15.5%
[WARNING] 모든 스레드가 활성 상태이며 작업이 큐에 대기 중입니다
```

**메모리 문제 식별:**
```
[WARNING] GC 빈발 - 힙 메모리 부족 가능성
Exception in thread "main" java.lang.OutOfMemoryError: Java heap space
```

## 📚 학습 포인트

### 이해해야 할 핵심 개념

1. **Thread-per-Request 모델**
    - 각 요청 = 하나의 스레드
    - 스레드 생명주기와 요청 생명주기 일치
    - 스레드 로컬 저장소 활용 가능

2. **블로킹 I/O**
    - 동기식 읽기/쓰기 작업
    - I/O 대기 시 스레드 블록
    - 간단한 프로그래밍 모델

3. **스레드 풀 관리**
    - 스레드 생성/소멸 비용 최적화
    - 동시성 제어
    - 백프레셔 처리

4. **서블릿 생명주기**
    - init() → service() → destroy()
    - 스레드 안전성 고려사항
    - 상태 관리

### 실무 적용 시 고려사항

1. **언제 사용하면 좋은가?**
    - CPU 집약적 작업이 주요한 경우
    - 동시 연결 수가 적은 경우 (~500개 이하)
    - 기존 서블릿 코드를 그대로 사용해야 하는 경우
    - 간단하고 이해하기 쉬운 구조가 필요한 경우

2. **언제 피해야 하는가?**
    - 높은 동시성이 필요한 경우 (>1000 연결)
    - I/O 대기 시간이 긴 경우
    - 메모리가 제한적인 환경
    - 마이크로서비스처럼 많은 외부 호출이 있는 경우

## 🔄 다음 단계

Phase 2.1 완료 후 진행할 내용:

### Phase 2.2 - Hybrid Server 구현 예정
- **AsyncContext 기반** 비동기 처리
- **컨텍스트 스위칭**으로 스레드 재활용
- **CompletableFuture** 체인 기반 처리
- **Traditional vs Hybrid** 성능 비교

### 예상 개선 효과
- **동시성**: 500 → 2,000+ 연결
- **메모리 효율성**: 높음 → 매우 높음
- **I/O 처리**: 비효율 → 효율적
- **복잡도**: 낮음 → 중간

## 📝 참고 자료

### 관련 기술
- **Apache Tomcat** - 대표적인 Thread-per-Request 서버
- **Jetty** - 하이브리드 접근 방식 지원
- **Spring MVC** - Traditional 서블릿 모델 기반

### 학습 자료
- [Oracle Java Concurrency Tutorial](https://docs.oracle.com/javase/tutorial/essential/concurrency/)
- [Jakarta Servlet Specification](https://jakarta.ee/specifications/servlet/)
- [ThreadPoolExecutor Documentation](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html)

---

**Phase 2.1 구현으로 전통적인 웹 서버 아키텍처의 핵심을 완전히 이해하고 체득할 수 있습니다! 🚀**