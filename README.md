# JavaServerArchitectures

**순수 자바로 구현하는 완전한 서블릿 컨테이너 - 3가지 서버 아키텍처 비교 프로젝트**

## 📖 프로젝트 소개

이 프로젝트는 **순수 자바 코드만을 사용하여 완전한 서블릿 컨테이너를 세 가지 다른 아키텍처로 구현**하는 대규모 학습 프로젝트입니다. 외부 프레임워크나 라이브러리 없이 직접 구현함으로써 각 서버 아키텍처의 동작 원리와 서블릿 구현 방식의 차이점을 깊이 이해할 수 있습니다.

**개발 기간**: 5-6개월 (파트타임 기준)  
**학습 레벨**: 서버 아키텍처 심화 과정  
**구현 범위**: 프로덕션급 서블릿 컨테이너

## 🎯 구현 목표

### 1. **Traditional Thread-per-Request Server (전통적 톰캣 스타일)**
- 요청당 스레드를 할당하는 전통적인 동기식 방식
- 완전한 서블릿 라이프사이클 (init → service → destroy)
- 블로킹 I/O와 동기식 처리
- 스레드풀 기반 요청 관리

### 2. **Hybrid Event-driven Server (하이브리드 아키텍처)**
- 톰캣의 스레드풀 + 이벤트루프 개념 결합
- AsyncContext를 활용한 비동기 서블릿 처리
- I/O 대기 시 컨텍스트 스위칭으로 스레드 재활용
- 10초 초과 작업은 별도 스레드 풀로 위임

### 3. **Pure Event Loop Server (FastAPI/Node.js 스타일)**
- 단일 스레드 기반 완전한 비동기 이벤트 루프
- CompletableFuture 체인 기반 논블로킹 처리
- NIO Selector를 활용한 고성능 I/O 처리
- 웹소켓 및 실시간 통신 지원

## 🏗️ 프로젝트 구조

```
JavaServerArchitectures/
├── README.md                           # 프로젝트 개요
├── PROCESS.md                          # 개발 프로세스 및 일정
├── docs/                               # 문서 및 설계 자료
│   ├── architecture/                   # 아키텍처 설계 문서
│   │   ├── traditional-design.md       # 전통적 서버 설계
│   │   ├── hybrid-design.md            # 하이브리드 서버 설계
│   │   └── eventloop-design.md         # 이벤트루프 서버 설계
│   ├── api/                           # API 문서
│   │   ├── servlet-api.md             # 서블릿 API 명세
│   │   └── container-api.md           # 컨테이너 API 명세
│   └── performance/                    # 성능 분석 결과
│       ├── benchmark-results.md        # 벤치마크 결과
│       └── analysis-report.md          # 상세 분석 리포트
├── src/                               # 소스코드 디렉토리
│   └── main/
│       └── java/
│           ├── jakarta/               # Jakarta EE 서블릿 API (현재 표준)
│           │   └── servlet/
│           │       ├── Servlet.java
│           │       ├── GenericServlet.java
│           │       ├── ServletRequest.java
│           │       ├── ServletResponse.java
│           │       ├── ServletConfig.java
│           │       ├── ServletContext.java
│           │       ├── ServletException.java
│           │       ├── AsyncContext.java
│           │       ├── AsyncListener.java
│           │       ├── Filter.java
│           │       ├── FilterChain.java
│           │       ├── FilterConfig.java
│           │       ├── RequestDispatcher.java
│           │       └── http/
│           │           ├── HttpServlet.java
│           │           ├── HttpServletRequest.java
│           │           ├── HttpServletResponse.java
│           │           ├── HttpSession.java
│           │           ├── Cookie.java
│           │           └── Part.java
│           ├── com/serverarch/         # 프로젝트 메인 패키지
│           │   ├── common/             # 공통 컴포넌트
│           │   │   ├── http/          # HTTP 프로토콜 구현
│           │   │   │   ├── HttpRequestParser.java
│           │   │   │   ├── HttpResponseBuilder.java
│           │   │   │   ├── HttpStatus.java
│           │   │   │   ├── HttpMethod.java
│           │   │   │   └── HttpVersion.java
│           │   │   ├── io/            # I/O 유틸리티
│           │   │   │   ├── SocketUtils.java
│           │   │   │   ├── BufferManager.java
│           │   │   │   └── ConnectionManager.java
│           │   │   ├── session/       # 세션 관리
│           │   │   │   ├── SessionManager.java
│           │   │   │   ├── SessionStorage.java
│           │   │   │   └── HttpSessionImpl.java
│           │   │   ├── routing/       # 라우팅 시스템
│           │   │   │   ├── Router.java
│           │   │   │   ├── Route.java
│           │   │   │   ├── ServletMapping.java
│           │   │   │   └── RequestDispatcher.java
│           │   │   ├── security/      # 보안 기능
│           │   │   │   ├── SecurityManager.java
│           │   │   │   ├── AuthenticationFilter.java
│           │   │   │   └── SSLContextManager.java
│           │   │   └── utils/         # 공통 유틸리티
│           │   │       ├── StringUtils.java
│           │   │       ├── DateUtils.java
│           │   │       └── LoggerFactory.java
│           │   ├── container/         # 서블릿 컨테이너 공통 부분
│           │   │   ├── ServletContainer.java
│           │   │   ├── ServletRegistry.java
│           │   │   ├── ServletInstanceManager.java
│           │   │   ├── FilterChain.java
│           │   │   └── WebApplicationContext.java
│           │   ├── traditional/       # 전통적 서버 구현
│           │   │   ├── TraditionalServer.java
│           │   │   ├── TraditionalServletContainer.java
│           │   │   ├── TraditionalHttpServletRequest.java
│           │   │   ├── TraditionalHttpServletResponse.java
│           │   │   ├── ThreadPoolManager.java
│           │   │   ├── RequestProcessor.java
│           │   │   └── ConnectionHandler.java
│           │   ├── hybrid/            # 하이브리드 서버 구현
│           │   │   ├── HybridServer.java
│           │   │   ├── HybridServletContainer.java
│           │   │   ├── AsyncHttpServletRequest.java
│           │   │   ├── AsyncHttpServletResponse.java
│           │   │   ├── AsyncContextImpl.java
│           │   │   ├── ContextSwitchManager.java
│           │   │   ├── AsyncTaskExecutor.java
│           │   │   ├── AdaptiveThreadPool.java
│           │   │   ├── BackpressureController.java
│           │   │   └── ThreadContextManager.java
│           │   ├── eventloop/         # 이벤트루프 서버 구현
│           │   │   ├── EventLoopServer.java
│           │   │   ├── EventLoopServletContainer.java
│           │   │   ├── EventLoop.java
│           │   │   ├── EventQueue.java
│           │   │   ├── EventDispatcher.java
│           │   │   ├── AsyncServlet.java
│           │   │   ├── CompletableFutureUtils.java
│           │   │   ├── NonBlockingSocketChannel.java
│           │   │   ├── WebSocketHandler.java
│           │   │   └── CircuitBreaker.java
│           │   ├── monitoring/        # 모니터링 및 메트릭
│           │   │   ├── PerformanceProfiler.java
│           │   │   ├── MetricsCollector.java
│           │   │   ├── JVMMetrics.java
│           │   │   ├── ApplicationMetrics.java
│           │   │   ├── HealthCheckManager.java
│           │   │   └── JMXReporter.java
│           │   ├── benchmark/         # 성능 측정 도구
│           │   │   ├── BenchmarkRunner.java
│           │   │   ├── HttpBenchmarkClient.java
│           │   │   ├── WorkloadGenerator.java
│           │   │   ├── TestScenario.java
│           │   │   ├── PerformanceReport.java
│           │   │   └── ResultAnalyzer.java
│           │   └── demo/              # 데모 애플리케이션
│           │       ├── SimpleServlet.java
│           │       ├── AsyncDatabaseServlet.java
│           │       ├── FileUploadServlet.java
│           │       ├── WebSocketEchoServlet.java
│           │       ├── JSONApiServlet.java
│           │       └── StaticFileServlet.java
├── test/                              # 테스트 코드
│   └── java/
│       └── com/serverarch/
│           ├── common/                # 공통 컴포넌트 테스트
│           ├── traditional/           # Traditional 서버 테스트
│           ├── hybrid/                # Hybrid 서버 테스트
│           ├── eventloop/             # EventLoop 서버 테스트
│           ├── integration/           # 통합 테스트
│           └── performance/           # 성능 테스트
├── config/                           # 설정 파일
│   ├── traditional-server.properties  # Traditional 서버 설정
│   ├── hybrid-server.properties      # Hybrid 서버 설정
│   ├── eventloop-server.properties   # EventLoop 서버 설정
│   ├── benchmark.properties          # 벤치마크 설정
│   └── logging.properties           # 로깅 설정
├── scripts/                          # 실행 및 빌드 스크립트
│   ├── build.sh                     # 컴파일 스크립트
│   ├── run-traditional.sh           # Traditional 서버 실행
│   ├── run-hybrid.sh                # Hybrid 서버 실행
│   ├── run-eventloop.sh             # EventLoop 서버 실행
│   ├── run-benchmark.sh             # 벤치마크 실행
│   └── clean.sh                     # 정리 스크립트
├── benchmarks/                       # 성능 테스트 결과
│   ├── results/                     # 벤치마크 결과 파일
│   ├── charts/                      # 성능 비교 차트
│   └── reports/                     # 상세 분석 리포트
├── lib/                             # 외부 의존성 (최소한)
│   └── junit-4.13.jar              # 테스트용 JUnit만 사용
└── build/                           # 컴파일된 클래스 파일
    ├── classes/                     # 메인 클래스들
    ├── test-classes/                # 테스트 클래스들
    └── reports/                     # 테스트 결과 리포트
```

## 🔧 주요 구현 기능

### 서블릿 API 완전 구현
- **표준 서블릿 라이프사이클**: init() → service() → destroy()
- **HTTP 서블릿**: doGet, doPost, doPut, doDelete, doOptions
- **비동기 처리**: AsyncContext, AsyncListener (Servlet 3.0+)
- **필터 체인**: ServletFilter, FilterChain 구현
- **요청 디스패처**: forward, include 지원

### HTTP/1.1 완전 지원
- **모든 HTTP 메소드**: GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD
- **헤더 처리**: 완전한 HTTP 헤더 파싱 및 생성
- **바디 처리**: Form, JSON, Multipart, Binary 데이터
- **연결 관리**: Keep-alive, Connection pooling
- **압축**: gzip, deflate 자동 압축/해제
- **청크 인코딩**: 대용량 데이터 스트리밍

### 세션 및 보안
- **세션 관리**: HttpSession 완전 구현
- **쿠키 처리**: 보안 쿠키, SameSite 속성
- **HTTPS 지원**: SSL/TLS 1.3 지원
- **보안 헤더**: HSTS, CSP, X-Frame-Options 자동 추가
- **인증/인가**: 기본 인증 프레임워크

### 고급 기능
- **WebSocket**: HTTP 업그레이드 및 실시간 통신
- **파일 업로드**: Multipart 파일 업로드 완전 지원
- **정적 파일**: 효율적인 정적 리소스 서빙
- **에러 처리**: 커스텀 에러 페이지 및 예외 처리
- **로깅**: 구조화된 로깅 시스템

## 📊 각 서버별 핵심 특징

### Traditional Server 특징
```java
// 전통적 동기식 서블릿 처리
public class TraditionalServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        // 스레드가 전체 처리 완료까지 블로킹
        String data = performDatabaseQuery();    // 블로킹 I/O
        String result = processData(data);       // CPU 작업
        resp.getWriter().write(result);          // 블로킹 출력
        // 하나의 스레드가 전체 요청 생명주기 담당
    }
}
```

### Hybrid Server 특징
```java
// AsyncContext를 활용한 하이브리드 처리
public class HybridServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        AsyncContext asyncContext = req.startAsync();
        
        // I/O 작업 시 스레드 해제, 다른 작업에 재사용
        CompletableFuture
            .supplyAsync(() -> performDatabaseQuery())  // 별도 스레드
            .thenApply(this::processData)               // CPU 작업
            .thenAccept(result -> {
                try {
                    resp.getWriter().write(result);
                    asyncContext.complete();            // 비동기 완료
                } catch (IOException e) {
                    asyncContext.complete();
                }
            });
        // doGet은 즉시 리턴, 스레드는 다른 요청 처리 가능
    }
}
```

### Event Loop Server 특징
```java
// 완전한 비동기 이벤트 기반 처리
public class EventLoopServlet extends AsyncServlet {
    @Override
    protected CompletableFuture<Void> doGetAsync(
            AsyncHttpServletRequest req, 
            AsyncHttpServletResponse resp) {
        
        // 모든 I/O가 논블로킹으로 이벤트루프에서 처리
        return performAsyncDatabaseQuery()           // 논블로킹 I/O
            .thenCompose(data -> processAsyncData(data))  // 비동기 처리
            .thenCompose(result -> resp.writeAsync(result)) // 논블로킹 출력
            .exceptionally(throwable -> {
                resp.setStatus(500);
                return null;
            });
        // 단일 이벤트루프 스레드에서 모든 요청 처리
    }
}
```

## 📈 성능 비교 예상 결과

| 항목 | Traditional | Hybrid | Event Loop |
|------|-------------|--------|------------|
| **동시 연결 처리** | ~200 | ~2,000 | ~10,000+ |
| **메모리 사용량** | 높음 (스레드당 1MB) | 중간 | 낮음 |
| **CPU 효율성** | 낮음 (컨텍스트 스위칭) | 높음 | 매우 높음 |
| **응답 시간** | 일정 | I/O 집약적 시 빠름 | 가장 빠름 |
| **구현 복잡도** | 단순 | 복잡 | 매우 복잡 |
| **디버깅 용이성** | 쉬움 | 보통 | 어려움 |

## 🚀 실행 방법

### 빌드
```bash
# 전체 프로젝트 컴파일
./scripts/build.sh

# 또는 수동 컴파일
find src -name "*.java" | xargs javac -d build/classes -cp "lib/*"
```

### 서버 실행
```bash
# Traditional Server (포트 8080)
./scripts/run-traditional.sh

# Hybrid Server (포트 8081)  
./scripts/run-hybrid.sh

# Event Loop Server (포트 8082)
./scripts/run-eventloop.sh
```

### 성능 테스트
```bash
# 전체 서버 벤치마크 실행
./scripts/run-benchmark.sh

# 특정 시나리오 테스트
java -cp build/classes com.serverarch.benchmark.BenchmarkRunner \
    --scenario=high-concurrency \
    --duration=300s \
    --connections=1000
```

### 데모 테스트
```bash
# 간단한 GET 요청
curl http://localhost:8080/hello

# JSON API 테스트
curl -X POST http://localhost:8081/api/users \
     -H "Content-Type: application/json" \
     -d '{"name": "John", "email": "john@example.com"}'

# 파일 업로드 테스트
curl -X POST http://localhost:8082/upload \
     -F "file=@test.txt"

# 웹소켓 연결 (Event Loop 서버만)
wscat -c ws://localhost:8082/websocket
```

## 📋 개발 단계별 학습 목표

### Phase 1: 서블릿 API 구현 (4-5주)
- **학습 목표**: 서블릿 표준 이해, HTTP 프로토콜 깊이 파악
- **핵심 결과물**: 완전한 서블릿 API와 HTTP 파서

### Phase 2: Traditional Server (2-3주)
- **학습 목표**: 전통적 서버 아키텍처와 멀티스레딩 이해
- **핵심 결과물**: 톰캣 스타일 서버 완성

### Phase 3: 성능 측정 (1-2주)
- **학습 목표**: 서버 성능 측정 방법론과 병목 지점 분석
- **핵심 결과물**: 정밀한 벤치마킹 도구

### Phase 4: Hybrid Server (3-4주)
- **학습 목표**: 비동기 프로그래밍과 컨텍스트 스위칭
- **핵심 결과물**: AsyncContext 기반 하이브리드 서버

### Phase 5: Event Loop Server (3-4주)
- **학습 목표**: 완전한 비동기 아키텍처와 이벤트 기반 설계
- **핵심 결과물**: 고성능 이벤트루프 서버

### Phase 6-7: 최적화 및 분석 (2-4주)
- **학습 목표**: 프로덕션급 최적화와 아키텍처 비교 분석
- **핵심 결과물**: 상세 성능 분석 리포트

## 🎓 이 프로젝트를 통해 배우는 것들

### 깊이 있는 기술 지식
- **서버 아키텍처 설계 원리**
- **멀티스레딩과 동시성 프로그래밍**
- **네트워크 프로그래밍과 소켓 관리**
- **HTTP 프로토콜의 모든 측면**
- **비동기 프로그래밍 패턴**
- **성능 최적화 기법**

### 실무 적용 능력
- **대용량 트래픽 처리 방법**
- **서버 모니터링과 디버깅**
- **메모리 관리와 GC 튜닝**
- **보안 취약점과 대응책**

### 시스템 이해도
- **Spring MVC, Netty 같은 프레임워크의 내부 동작**
- **톰캣, Jetty 같은 실제 서버의 구현 원리**
- **Node.js, FastAPI 같은 비동기 서버의 장단점**
- **마이크로서비스 아키텍처에서의 서버 선택 기준**

## 🤝 기여 및 피드백

이 프로젝트는 개인 학습 목적이지만, 다음과 같은 기여를 환영합니다:
- 성능 최적화 아이디어
- 추가 테스트 시나리오 제안
- 코드 리뷰 및 개선사항
- 문서화 개선

## 📄 라이선스

MIT License - 교육 목적으로 자유롭게 사용하세요!

---

**💡 이 프로젝트는 단순한 코딩 연습을 넘어서, 현대 웹 서버의 모든 것을 이해하고 직접 구현해보는 도전적인 여정입니다. 완성하면 서버 아키텍처 전문가 수준의 깊이 있는 지식을 얻을 수 있을 것입니다!** 🚀

## 🔥 시작하기

```bash
git clone <repository-url>
cd JavaServerArchitectures
chmod +x scripts/*.sh
./scripts/build.sh
echo "Let's build the future of web servers! 🚀"
```