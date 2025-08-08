# JavaServerArchitectures

**순수 자바로 구현하는 3가지 HTTP 서버 아키텍처 비교 프로젝트**

## 📖 프로젝트 개요

이 프로젝트는 **순수 자바 코드만을 사용하여 3가지 서로 다른 HTTP 서버 아키텍처를 구현하고 비교**하는 학습 중심 프로젝트입니다. 무거운 서블릿 API 대신 **핵심 HTTP 처리 모델**에 집중하여 현대적인 서버 아키텍처의 본질을 이해합니다.

**개발 기간**: 2-3개월 (파트타임 기준)  
**학습 레벨**: 시스템 아키텍처 중급-고급  
**구현 범위**: 핵심 HTTP 서버 + 미니멀 서블릿 API

## 🎯 3가지 서버 아키텍처

### 1. **Threaded Server (전통적 Thread-per-Request)**
```java
// 요청당 스레드 할당, 블로킹 I/O
public class ThreadedHandler implements RouteHandler {
    @Override
    public CompletableFuture<HttpResponse> handle(HttpRequest request) {
        // 스레드가 전체 처리 완료까지 블로킹
        String data = performDatabaseQuery();    // 블로킹 I/O
        String result = processData(data);       // CPU 작업
        return CompletableFuture.completedFuture(
            HttpResponse.ok(result)
        );
    }
}
```

### 2. **Hybrid Server (비동기 하이브리드)**
```java
// AsyncContext + 컨텍스트 스위칭으로 스레드 재활용
public class HybridHandler implements RouteHandler {
    @Override
    public CompletableFuture<HttpResponse> handle(HttpRequest request) {
        // I/O 대기 시 스레드 해제, 완료 시 다른 스레드에서 재개
        return CompletableFuture
            .supplyAsync(() -> performDatabaseQuery())  // 별도 스레드
            .thenApply(this::processData)               // CPU 작업
            .thenApply(result -> HttpResponse.ok(result));
    }
}
```

### 3. **EventLoop Server (순수 이벤트 루프)**
```java
// 단일 스레드 이벤트루프, 완전 논블로킹
public class EventLoopHandler implements RouteHandler {
    @Override
    public CompletableFuture<HttpResponse> handle(HttpRequest request) {
        // 모든 I/O가 논블로킹으로 이벤트루프에서 처리
        return performAsyncDatabaseQuery()           // 논블로킹 I/O
            .thenCompose(data -> processAsyncData(data))  // 비동기 처리
            .thenApply(result -> HttpResponse.ok(result));
    }
}
```

## 🏗️ 프로젝트 구조

```
JavaServerArchitectures/
├── README.md                           # 프로젝트 개요
├── PROCESS.md                          # 상세 구현 가이드 (챕터별)
├── docs/                               # 문서 및 분석 자료
│   ├── architecture/                   # 아키텍처 설계 문서
│   │   ├── threaded-architecture.md    # Thread-per-Request 설계
│   │   ├── hybrid-architecture.md      # 하이브리드 아키텍처 설계
│   │   ├── eventloop-architecture.md   # 이벤트루프 아키텍처 설계
│   │   └── performance-comparison.md   # 성능 비교 분석
│   ├── implementation/                 # 구현 가이드
│   │   ├── http-core-guide.md          # HTTP 코어 구현 가이드
│   │   ├── mini-servlet-guide.md       # 미니 서블릿 API 가이드
│   │   └── testing-guide.md            # 테스트 방법론
│   └── benchmarks/                     # 벤치마크 결과
│       ├── load-test-results.md        # 부하 테스트 결과
│       └── performance-analysis.md     # 상세 성능 분석
├── src/                               # 소스코드 디렉토리
│   └── main/
│       └── java/
│           ├── server/                 # 서버 공통 패키지
│           │   └── core/              # 🔥 HTTP 코어 모듈
│           │       ├── http/          # HTTP 프로토콜 처리
│           │       │   ├── HttpRequest.java
│           │       │   ├── HttpResponse.java
│           │       │   ├── HttpMethod.java
│           │       │   ├── HttpStatus.java
│           │       │   ├── HttpHeaders.java
│           │       │   ├── HttpParser.java
│           │       │   └── HttpBuilder.java
│           │       ├── routing/       # 라우팅 시스템
│           │       │   ├── Router.java
│           │       │   ├── Route.java
│           │       │   ├── RouteHandler.java
│           │       │   └── RouteMatchResult.java
│           │       ├── filter/        # 필터 체인
│           │       │   ├── FilterChain.java
│           │       │   ├── Filter.java
│           │       │   └── FilterContext.java
│           │       ├── mini/          # 🔥 미니 서블릿 API
│           │       │   ├── MiniServlet.java
│           │       │   ├── MiniAsyncServlet.java
│           │       │   ├── MiniContext.java
│           │       │   ├── MiniRequest.java
│           │       │   ├── MiniResponse.java
│           │       │   └── MiniSession.java
│           │       └── utils/         # 공통 유틸리티
│           │           ├── StringUtils.java
│           │           ├── IOUtils.java
│           │           └── ThreadUtils.java
│           ├── threaded/              # 🔥 Thread-per-Request 서버
│           │   ├── ThreadedServer.java
│           │   ├── ThreadedProcessor.java
│           │   ├── ThreadPoolManager.java
│           │   ├── BlockingRequestHandler.java
│           │   └── ThreadedMiniServletContainer.java
│           ├── hybrid/                # 🔥 하이브리드 서버
│           │   ├── HybridServer.java
│           │   ├── HybridProcessor.java
│           │   ├── AsyncContextManager.java
│           │   ├── ContextSwitchingHandler.java
│           │   ├── AdaptiveThreadPool.java
│           │   └── HybridMiniServletContainer.java
│           ├── eventloop/             # 🔥 이벤트루프 서버
│           │   ├── EventLoopServer.java
│           │   ├── EventLoopProcessor.java
│           │   ├── EventLoop.java
│           │   ├── EventQueue.java
│           │   ├── NonBlockingHandler.java
│           │   ├── SelectorManager.java
│           │   └── EventLoopMiniServletContainer.java
│           ├── examples/              # 실용적 예시 애플리케이션
│           │   ├── hello/             # Hello World 예시
│           │   │   ├── HelloWorldHandler.java
│           │   │   └── HelloWorldServlet.java
│           │   ├── api/               # REST API 예시
│           │   │   ├── UserApiHandler.java
│           │   │   ├── JsonUtils.java
│           │   │   └── ApiResponse.java
│           │   ├── upload/            # 파일 업로드 예시
│           │   │   ├── FileUploadHandler.java
│           │   │   ├── MultipartParser.java
│           │   │   └── FileUploadServlet.java
│           │   ├── websocket/         # 웹소켓 예시 (EventLoop만)
│           │   │   ├── WebSocketHandler.java
│           │   │   ├── WebSocketFrame.java
│           │   │   └── ChatServer.java
│           │   └── static/            # 정적 파일 서빙
│           │       ├── StaticFileHandler.java
│           │       └── MimeTypeResolver.java
│           └── benchmark/             # 🔥 성능 측정 도구
│               ├── BenchmarkRunner.java
│               ├── LoadTestClient.java
│               ├── PerformanceCollector.java
│               ├── ConcurrencyTester.java
│               ├── ThroughputAnalyzer.java
│               └── LatencyProfiler.java
├── test/                              # 테스트 코드
│   └── java/
│       └── server/
│           ├── core/                  # 코어 모듈 테스트
│           ├── threaded/              # Threaded 서버 테스트
│           ├── hybrid/                # Hybrid 서버 테스트
│           ├── eventloop/             # EventLoop 서버 테스트
│           ├── integration/           # 통합 테스트
│           └── benchmark/             # 성능 테스트
├── config/                           # 설정 파일
│   ├── threaded-server.conf          # Threaded 서버 설정
│   ├── hybrid-server.conf            # Hybrid 서버 설정
│   ├── eventloop-server.conf         # EventLoop 서버 설정
│   └── benchmark.conf                # 벤치마크 설정
├── scripts/                          # 실행 및 빌드 스크립트
│   ├── build.sh                     # 컴파일 스크립트
│   ├── run-threaded.sh              # Threaded 서버 실행
│   ├── run-hybrid.sh                # Hybrid 서버 실행
│   ├── run-eventloop.sh             # EventLoop 서버 실행
│   ├── run-benchmark.sh             # 벤치마크 실행
│   ├── load-test.sh                 # 부하 테스트 실행
│   └── clean.sh                     # 정리 스크립트
├── benchmarks/                       # 벤치마크 결과 및 분석
│   ├── results/                     # 측정 결과 파일
│   ├── charts/                      # 성능 비교 차트
│   ├── reports/                     # 상세 분석 리포트
│   └── load-tests/                  # 부하 테스트 로그
├── build/                           # 컴파일된 클래스 파일
└── lib/                             # 외부 의존성 (최소한)
    └── junit-5.jar                  # 테스트용만 사용
```

## 🔧 핵심 구현 기능

### HTTP 코어 모듈
- **경량 HTTP/1.1 처리**: 헤더 파싱, 바디 처리, Keep-alive
- **현대적 라우팅**: 패턴 매칭, RESTful 경로 지원
- **필터 체인**: 인증, 로깅, CORS 등 횡단 관심사 처리
- **세션 관리**: 메모리 기반 세션 저장소

### 미니 서블릿 API
- **MiniServlet**: 핵심 생명주기만 (init, service, destroy)
- **MiniAsyncServlet**: 비동기 처리 지원 (CompletableFuture 기반)
- **MiniContext**: 애플리케이션 설정 및 전역 데이터
- **간단한 어노테이션**: @Route, @Filter, @Async

### 3가지 서버 아키텍처
- **Threaded**: 전통적 스레드풀, 블로킹 I/O
- **Hybrid**: AsyncContext 기반 컨텍스트 스위칭
- **EventLoop**: NIO Selector 기반 단일 스레드 루프

## 📊 성능 비교 목표

| 측정 항목 | Threaded | Hybrid | EventLoop |
|-----------|----------|--------|-----------|
| **동시 연결** | ~500 | ~2,000 | ~10,000+ |
| **메모리 효율** | 낮음 | 중간 | 높음 |
| **CPU 효율** | 낮음 | 높음 | 매우 높음 |
| **응답 지연** | 일정 | I/O시 우수 | 가장 우수 |
| **구현 복잡도** | 단순 | 복잡 | 매우 복잡 |
| **디버깅 용이성** | 쉬움 | 보통 | 어려움 |

## 🚀 실행 방법

### 빌드 및 컴파일
```bash
# 전체 프로젝트 컴파일
./scripts/build.sh

# 또는 수동 컴파일
find src -name "*.java" | xargs javac -d build/classes
```

### 각 서버 실행
```bash
# Threaded Server (포트 8080)
./scripts/run-threaded.sh

# Hybrid Server (포트 8081)
./scripts/run-hybrid.sh

# EventLoop Server (포트 8082)
./scripts/run-eventloop.sh
```

### 성능 테스트
```bash
# 전체 서버 비교 벤치마크
./scripts/run-benchmark.sh

# 부하 테스트 (wrk 기반)
./scripts/load-test.sh --concurrent=1000 --duration=60s

# 특정 서버만 테스트
java -cp build/classes server.benchmark.BenchmarkRunner \
    --server=threaded \
    --scenario=high-concurrency \
    --duration=300
```

### 예시 애플리케이션 테스트
```bash
# Hello World 테스트
curl http://localhost:8080/hello
curl http://localhost:8081/hello
curl http://localhost:8082/hello

# REST API 테스트
curl -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"name": "John", "email": "john@example.com"}'

# 파일 업로드 테스트
curl -X POST http://localhost:8082/upload \
     -F "file=@test.txt"

# 웹소켓 테스트 (EventLoop 서버만)
wscat -c ws://localhost:8082/chat
```

## 🎓 학습 목표 및 기대 효과

### 깊이 있는 기술 지식
- **서버 아키텍처 설계 원리** 완전 이해
- **멀티스레딩 vs 이벤트루프** 본질적 차이 체득
- **HTTP 프로토콜** 저수준 구현 경험
- **동시성 프로그래밍** 패턴 습득
- **성능 최적화** 실전 노하우

### 실무 적용 역량
- **Spring MVC vs WebFlux** 선택 기준 이해
- **Netty, Vert.x** 같은 비동기 프레임워크 원리 파악
- **Node.js, FastAPI** 이벤트루프 방식 이해
- **마이크로서비스** 아키텍처에서 서버 선택 기준
- **성능 튜닝** 및 **병목 지점 분석** 능력

### 시스템 설계 통찰
- **언제 어떤 아키텍처를 선택해야 하는가?**
- **확장성과 복잡도의 트레이드오프**
- **메모리 vs CPU vs 응답성** 균형점
- **디버깅과 모니터링** 전략 차이
- **실제 프로덕션 환경** 적용 고려사항

## 📋 개발 로드맵

### Chapter 1: HTTP 코어 구현 (2주)
- HTTP 파서, 라우터, 필터 체인 구현
- 미니 서블릿 API 설계 및 구현

### Chapter 2: Threaded 서버 (1주)
- 전통적 Thread-per-Request 구현
- 스레드풀 관리 및 블로킹 I/O 처리

### Chapter 3: Hybrid 서버 (2주)
- AsyncContext 기반 비동기 처리
- 컨텍스트 스위칭 및 스레드 재활용

### Chapter 4: EventLoop 서버 (2주)
- NIO Selector 기반 이벤트루프
- 완전 논블로킹 I/O 처리

### Chapter 5: 예시 및 벤치마크 (1주)
- 실용적 예시 애플리케이션 구현
- 성능 측정 도구 및 비교 분석

### Chapter 6: 최적화 및 분석 (1주)
- 성능 최적화 및 튜닝
- 상세 분석 리포트 작성

**총 예상 기간**: **9주 (2-3개월)**

## 💡 프로젝트의 차별점

### 1. **핵심에 집중**
- 무거운 서블릿 API 대신 **HTTP 처리 본질**에 집중
- **3가지 아키텍처의 핵심 차이점** 명확히 드러냄

### 2. **현대적 설계**
- CompletableFuture 기반 **비동기 처리**
- **함수형 인터페이스** 활용한 간결한 API
- **람다와 스트림** 적극 활용

### 3. **실무 연관성**
- **실제 프레임워크들의 선택 이유** 이해
- **성능 vs 복잡도** 트레이드오프 체험
- **실무에서 바로 적용 가능한** 아키텍처 지식

### 4. **완전한 비교 분석**
- **정량적 성능 측정**
- **정성적 개발 경험** 비교
- **실제 사용 시나리오별** 권장사항

## 🔥 시작하기

```bash
git clone <repository-url>
cd JavaServerArchitectures
chmod +x scripts/*.sh
./scripts/build.sh

# 3개 서버 모두 실행 (각각 다른 포트)
./scripts/run-threaded.sh &
./scripts/run-hybrid.sh &
./scripts/run-eventloop.sh &

# 성능 비교 테스트
./scripts/run-benchmark.sh

echo "3가지 HTTP 서버 아키텍처로 떠나는 여행을 시작합니다! 🚀"
```

---

**이 프로젝트를 완성하면 Spring, Netty, Node.js 같은 모든 서버 기술의 본질을 이해하게 될 것입니다!** 🔥
