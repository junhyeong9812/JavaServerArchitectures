# JavaServerArchitectures 완전 구현 계획

## 🎯 구현 우선순위

### Phase 1: 핵심 기반 구조 (1주)
```
src/main/java/server/core/
├── http/           # HTTP 코어 프로토콜 처리
│   ├── HttpRequest.java         ✅ 완전한 HTTP/1.1 요청 파싱
│   ├── HttpResponse.java        ✅ 응답 빌더 + Keep-alive 지원
│   ├── HttpHeaders.java         ✅ 헤더 관리 (Case-insensitive)
│   ├── HttpMethod.java          ✅ GET, POST, PUT, DELETE 등
│   ├── HttpStatus.java          ✅ 모든 HTTP 상태코드
│   └── HttpParser.java          ✅ 고성능 파싱 로직
├── routing/        # 현대적 라우팅 시스템
│   ├── Router.java              ✅ 패턴 매칭 + RESTful 지원
│   ├── Route.java               ✅ 경로 + 핸들러 매핑
│   ├── RouteHandler.java        ✅ CompletableFuture 기반
│   └── RouteMatchResult.java    ✅ 매칭 결과 + 파라미터
└── mini/           # 🔥 미니 서블릿 API
    ├── MiniServlet.java         ✅ 핵심 생명주기 (init/service/destroy)
    ├── MiniAsyncServlet.java    ✅ 비동기 처리 지원
    ├── MiniContext.java         ✅ 애플리케이션 설정
    ├── MiniRequest.java         ✅ 요청 래퍼
    └── MiniResponse.java        ✅ 응답 래퍼
```

### Phase 2: Threaded 서버 완성 (3일)
```
src/main/java/threaded/
├── ThreadedServer.java          ✅ ServerSocket + Thread Pool
├── ThreadedProcessor.java       ✅ 요청별 스레드 처리
├── ThreadPoolManager.java       ✅ 동적 스레드풀 관리
├── BlockingRequestHandler.java  ✅ 블로킹 I/O 핸들러
└── ThreadedMiniServletContainer.java ✅ 서블릿 컨테이너
```

### Phase 3: Hybrid 서버 구현 (4일)
```
src/main/java/hybrid/
├── HybridServer.java            ✅ NIO + 스레드풀 조합
├── HybridProcessor.java         ✅ 요청 분산 로직
├── AsyncContextManager.java    ✅ 비동기 컨텍스트 관리
├── ContextSwitchingHandler.java ✅ I/O 대기시 스레드 해제
├── AdaptiveThreadPool.java      ✅ 동적 스레드풀 크기 조정
└── HybridMiniServletContainer.java ✅ 비동기 서블릿 지원
```

### Phase 4: EventLoop 서버 구현 (4일)
```
src/main/java/eventloop/
├── EventLoopServer.java         ✅ 단일 스레드 + Selector
├── EventLoopProcessor.java      ✅ 이벤트 처리 루프
├── EventLoop.java               ✅ 핵심 이벤트루프 엔진
├── EventQueue.java              ✅ 비동기 작업 큐
├── NonBlockingHandler.java      ✅ 완전 논블로킹 핸들러
├── SelectorManager.java         ✅ NIO Selector 관리
└── EventLoopMiniServletContainer.java ✅ 이벤트 기반 서블릿
```

### Phase 5: 성능 측정 도구 (2일)
```
src/main/java/benchmark/
├── BenchmarkRunner.java         ✅ 3서버 동시 테스트
├── LoadTestClient.java          ✅ 고성능 클라이언트
├── PerformanceCollector.java    ✅ 메트릭 수집
├── ConcurrencyTester.java       ✅ 동시성 테스트
├── ThroughputAnalyzer.java      ✅ 처리량 분석
└── LatencyProfiler.java         ✅ 지연시간 프로파일링
```

### Phase 6: 실용적 예시 (2일)
```
src/main/java/examples/
├── hello/HelloWorldHandler.java     ✅ 기본 Hello World
├── api/UserApiHandler.java          ✅ REST API 예시
├── upload/FileUploadHandler.java    ✅ 파일 업로드
├── websocket/WebSocketHandler.java  ✅ 웹소켓 (EventLoop만)
└── static/StaticFileHandler.java    ✅ 정적 파일 서빙
```

## 🔥 핵심 구현 포인트

### 1. Threaded Server 특징
```java
// 요청당 스레드 할당 - 직관적이지만 메모리 소모 큼
public class ThreadedHandler implements RouteHandler {
    @Override
    public CompletableFuture<HttpResponse> handle(HttpRequest request) {
        // 현재 스레드에서 모든 처리 완료까지 블로킹
        String data = performDatabaseQuery();    // 블로킹 I/O
        String result = processData(data);       // CPU 작업
        return CompletableFuture.completedFuture(
            HttpResponse.ok(result)
        );
    }
}
```

### 2. Hybrid Server 특징
```java
// AsyncContext로 스레드 해제 후 다른 스레드에서 재개
public class HybridHandler implements RouteHandler {
    @Override
    public CompletableFuture<HttpResponse> handle(HttpRequest request) {
        // I/O 대기시 스레드 반납, 완료시 다른 스레드에서 처리
        return CompletableFuture
            .supplyAsync(() -> performDatabaseQuery())  // I/O 스레드
            .thenApplyAsync(this::processData)          // CPU 스레드
            .thenApply(result -> HttpResponse.ok(result));
    }
}
```

### 3. EventLoop Server 특징
```java
// 모든 I/O가 논블로킹, 단일 스레드 이벤트루프
public class EventLoopHandler implements RouteHandler {
    @Override
    public CompletableFuture<HttpResponse> handle(HttpRequest request) {
        // 모든 작업이 이벤트루프에서 논블로킹으로 처리
        return performAsyncDatabaseQuery()           // 논블로킹 I/O
            .thenCompose(data -> processAsyncData(data))  // 비동기 처리
            .thenApply(result -> HttpResponse.ok(result));
    }
}
```

## 📊 성능 비교 목표

| 측정 항목 | Threaded | Hybrid | EventLoop |
|-----------|----------|--------|-----------|
| **동시 연결** | ~500 | ~2,000 | ~10,000+ |
| **메모리 사용** | 높음 | 중간 | 낮음 |
| **CPU 효율** | 낮음 | 높음 | 매우 높음 |
| **응답 지연** | 일정 | I/O시 우수 | 가장 우수 |
| **구현 복잡도** | 단순 | 복잡 | 매우 복잡 |

## 🛠️ 개발 도구 및 스크립트

### 빌드 및 실행 스크립트
```bash
# scripts/build.sh - 전체 컴파일
# scripts/run-threaded.sh - Threaded 서버 실행 (포트 8080)
# scripts/run-hybrid.sh - Hybrid 서버 실행 (포트 8081)
# scripts/run-eventloop.sh - EventLoop 서버 실행 (포트 8082)
# scripts/run-benchmark.sh - 3서버 성능 비교
```

### 테스트 시나리오
```bash
# 기본 기능 테스트
curl http://localhost:8080/hello
curl http://localhost:8081/hello  
curl http://localhost:8082/hello

# REST API 테스트
curl -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"name": "John", "email": "john@example.com"}'

# 동시성 테스트
./scripts/load-test.sh --concurrent=1000 --duration=60s
```

## 🎓 학습 효과

### 기술적 이해
- **멀티스레딩 vs 이벤트루프** 본질적 차이 체득
- **HTTP 프로토콜** 저수준 구현 경험
- **비동기 프로그래밍** 패턴 완전 습득
- **성능 최적화** 실전 노하우

### 실무 적용
- **Spring MVC vs WebFlux** 선택 기준 명확화
- **Netty, Vert.x** 이벤트루프 방식 이해
- **Node.js, FastAPI** 아키텍처 원리 파악
- **마이크로서비스** 환경에서 서버 선택 기준

## 🚀 시작 방법

1. **기존 코드 정리**: 현재 부분 구현된 코드들을 정리
2. **새로운 구조로 재시작**: README 구조에 맞게 처음부터 구현
3. **단계별 구현**: Phase 1부터 순차적으로 진행
4. **지속적 테스트**: 각 Phase 완료시마다 동작 확인

**총 예상 기간: 3주 (파트타임 기준)**

이렇게 완성하면 **실제 프로덕션에서 사용하는 서버 기술들의 본질을 완전히 이해**하게 될 것입니다! 🔥