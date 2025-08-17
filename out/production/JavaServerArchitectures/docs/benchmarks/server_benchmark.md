# 서버 벤치마크 성능 분석 및 최적화 가이드

## 📊 벤치마크 결과 분석

### 초기 성능 문제점
- **Threaded Server**: 12.72 TPS (심각한 성능 저하)
- **EventLoop Server**: I/O 테스트에서 89.4% 에러율
- **Hybrid Server**: 상대적으로 안정적인 성능

### 테스트별 승자 (수정 전)
1. **Basic Test**: EventLoop Server (1,370 TPS)
2. **Concurrency Test**: EventLoop Server (3,425 TPS)
3. **CPU Intensive**: Hybrid Server (784 TPS)
4. **I/O Intensive**: Hybrid Server (104 TPS) - EventLoop 에러로 인해
5. **Memory Pressure**: Hybrid Server (1,005 TPS)

## 🔍 성능 문제 원인 분석

### 1. EventLoop I/O 에러 문제
**원인**: EventLoop에서 `Thread.sleep(100)` 사용으로 단일 스레드 블로킹
```java
// ❌ 문제 코드
server.get("/io-simulation", request ->
    server.getProcessor().executeAsync(() -> {
        Thread.sleep(100); // 단일 스레드 블로킹으로 다른 요청 처리 불가
        return HttpResponse.json("{\"server\":\"eventloop\",\"io\":\"completed\"}");
    })
);

// ✅ 수정 방안
server.get("/io-simulation", request -> {
    return CompletableFuture
            .delayedExecutor(100, TimeUnit.MILLISECONDS)
            .thenApply(v -> HttpResponse.json(
                "{\"server\":\"eventloop\",\"io\":\"completed\"}"
            ));
});
```

### 2. Threaded Server 성능 저하 원인
**주요 원인들**:
1. **이중 처리 구조**: ServletContainer와 Router 중복 등록
2. **디버그 오버헤드**: 매 요청마다 로깅 및 통계 계산
3. **ThreadPool 오버헤드**: 과도한 모니터링 및 스케일링
4. **복잡한 fallback 로직**: 실패 시 이중 처리 시도

## 🛠️ 성능 최적화 방안

### 1. BenchmarkRunner 중복 라우트 제거
```java
// ❌ 기존: 서블릿과 핸들러 중복 등록
private void registerThreadedServlets(ThreadedServer server) {
    server.registerServlet("/hello", new HelloWorldServlet());     // 서블릿 등록
}

private void setupThreadedRoutes(ThreadedServer server) {
    server.registerHandler("/hello", request -> ...);             // 핸들러도 등록
}

// ✅ 수정: setupThreadedRoutes() 메서드 완전 제거
private void startServers() throws Exception {
    threadedServer = new ThreadedServer(THREADED_PORT);
    registerThreadedServlets(threadedServer); // 서블릿만 사용
    threadedServer.start();
}
```

### 2. BlockingRequestHandler 최적화
**주요 개선사항**:
- 디버그 관련 변수 미리 계산
- 불필요한 시간 계산 제거
- 처리 로직 단순화
- String 연산 최적화

```java
// 성능 최적화: 반복 호출되는 값들 미리 계산
private final boolean debugMode = config.isDebugMode();
private final String threadName = debugMode ? Thread.currentThread().getName() : null;

// 최적화된 요청 처리 (fallback 로직 단순화)
private HttpResponse processRequestOptimized(HttpRequest request) {
    try {
        if (servletContainer != null) {
            CompletableFuture<HttpResponse> future = servletContainer.handleRequest(request);
            if (future != null) {
                HttpResponse response = future.get();
                if (response != null) {
                    return response;
                }
            }
        }
        return router.routeWithMiddlewares(request).get();
    } catch (Exception e) {
        return HttpResponse.internalServerError("Request processing failed");
    }
}
```

### 3. 디버그 모드 비활성화
```java
// BenchmarkRunner에서 최적화된 서버 설정
ServerConfig optimizedConfig = new ServerConfig()
        .setDebugMode(false)  // 디버그 모드 비활성화
        .setRequestHandlerConfig(
            new RequestHandlerConfig()
                .setDebugMode(false)  // 핸들러 디버그 모드 비활성화
        )
        .setThreadPoolConfig(
            new ThreadPoolConfig()
                .setDebugMode(false)  // 스레드풀 디버그 모드 비활성화
        );

threadedServer = new ThreadedServer(THREADED_PORT, new Router(), optimizedConfig);
```

## 📈 예상 성능 개선 효과

### Threaded Server
- **디버그 오버헤드 제거**: 20-30% 성능 향상
- **처리 로직 단순화**: 15-25% 성능 향상
- **불필요한 계산 제거**: 10-15% 성능 향상
- **전체 예상**: **12.72 TPS → 500-1000+ TPS** (40-80배 개선)

### EventLoop Server
- **I/O 시뮬레이션 수정**: 89.4% 에러율 → 0% 에러율
- **논블로킹 처리**: 단일 스레드 효율성 극대화

## 🎯 최종 권장사항

### 우선순위별 수정사항
1. **최우선**: EventLoop I/O 시뮬레이션을 논블로킹 방식으로 변경
2. **높음**: BenchmarkRunner에서 ThreadedServer 중복 라우트 제거
3. **높음**: 모든 서버에서 디버그 모드 비활성화
4. **중간**: BlockingRequestHandler 최적화 적용

### 벤치마크 결과 파일 개선
- **부분 결과 저장**: 각 테스트 완료 후 즉시 `result_N-testname.txt` 생성
- **최종 결과 저장**: 모든 테스트 완료 후 `result.txt` 생성
- **상세한 메트릭**: TestResult 클래스의 모든 성능 지표 활용

### 서버별 최적 사용 시나리오 (예상)
- **EventLoop Server**: I/O 집약적 작업, 높은 동시 연결
- **Hybrid Server**: CPU 집약적 작업, 메모리 압박 상황
- **Threaded Server**: 안정적인 처리량, 전통적인 웹 애플리케이션

## 🔧 구현 체크리스트

### EventLoop 수정
- [ ] I/O 시뮬레이션을 `CompletableFuture.delayedExecutor()` 사용으로 변경
- [ ] Thread.sleep() 사용 완전 제거

### Threaded Server 수정
- [ ] BenchmarkRunner에서 setupThreadedRoutes() 메서드 제거
- [ ] 디버그 모드 비활성화된 ServerConfig 적용
- [ ] BlockingRequestHandler 최적화 버전 적용

### 벤치마크 개선
- [ ] 부분 결과 저장 기능 테스트
- [ ] 최종 result.txt 파일 형식 확인
- [ ] 모든 TestResult 메트릭이 올바르게 출력되는지 검증

이러한 최적화를 통해 공정하고 정확한 서버 아키텍처 성능 비교가 가능할 것으로 예상됩니다.