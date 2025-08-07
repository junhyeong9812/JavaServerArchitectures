# 개발 프로세스 (PROCESS.md)

## 🎯 전체 개발 단계 개요

이 프로젝트는 **완전한 서블릿 컨테이너**를 세 가지 다른 아키텍처로 구현하는 대규모 학습 프로젝트입니다. 각 아키텍처별로 서블릿 구현 방식의 차이점을 깊이 이해하고, 실제 서버의 동작 원리를 파악하는 것이 목표입니다.

**예상 개발 기간**: **2-3개월** (풀타임 기준)
**학습 깊이**: 서버 아키텍처의 모든 측면을 다루는 심화 과정

---

## 📋 Phase 1: 서블릿 API 및 기초 인프라 구축 (4-5주)

### 1.1 프로젝트 구조 설정 (1일)
- [ ] 디렉토리 구조 생성
- [ ] 패키지 구조 정의 (javax.servlet 패키지 구조 모방)
- [ ] 빌드 시스템 구축 (Maven/Gradle 없이 순수 자바)

### 1.2 서블릿 API 설계 및 구현 (2주)

#### 1.2.1 핵심 서블릿 인터페이스
- [ ] `Servlet` 인터페이스 (init, service, destroy)
- [ ] `GenericServlet` 추상 클래스
- [ ] `HttpServlet` 추상 클래스 (doGet, doPost, doPut, doDelete)
- [ ] `ServletConfig` 인터페이스 (서블릿 설정)
- [ ] `ServletContext` 인터페이스 (애플리케이션 컨텍스트)

#### 1.2.2 HTTP 요청/응답 API
- [ ] `ServletRequest` / `HttpServletRequest` 인터페이스
    - 헤더, 파라미터, 바디 처리
    - 세션, 쿠키 접근
    - 파일 업로드 지원
- [ ] `ServletResponse` / `HttpServletResponse` 인터페이스
    - 상태코드, 헤더 설정
    - 출력 스트림 관리
    - 쿠키 설정

#### 1.2.3 비동기 처리 API (Hybrid, EventLoop용)
- [ ] `AsyncContext` 인터페이스
- [ ] `AsyncListener` 인터페이스
- [ ] `AsyncEvent` 클래스

### 1.3 HTTP 프로토콜 완전 구현 (1.5주)

#### 1.3.1 HTTP 파서 구현
- [ ] `HttpRequestParser` 클래스
    - Request Line 파싱 (Method, URI, Version)
    - Header 파싱 및 저장
    - Body 파싱 (Form, JSON, Multipart)
- [ ] `HttpResponseBuilder` 클래스
    - 응답 헤더 생성
    - 바디 인코딩 처리
    - Keep-alive 연결 관리

#### 1.3.2 고급 HTTP 기능
- [ ] 청크 인코딩 (Chunked Encoding) 지원
- [ ] 압축 처리 (gzip, deflate)
- [ ] Range 요청 처리 (파일 다운로드용)
- [ ] WebSocket 업그레이드 핸드셰이크

### 1.4 서블릿 컨테이너 공통 인프라 (1주)

#### 1.4.1 서블릿 라이프사이클 관리
- [ ] `ServletRegistry` 클래스 (서블릿 등록 및 관리)
- [ ] `ServletInstanceManager` 클래스 (인스턴스 생성/소멸)
- [ ] `ServletLifecycleListener` 인터페이스

#### 1.4.2 세션 관리
- [ ] `HttpSession` 인터페이스 구현
- [ ] `SessionManager` 클래스 (세션 생성/만료/정리)
- [ ] `SessionStorage` 인터페이스 (메모리/파일 저장소)

#### 1.4.3 라우팅 시스템
- [ ] `ServletMapping` 클래스 (URL 패턴 매칭)
- [ ] `FilterChain` 구현 (필터 체인 처리)
- [ ] `RequestDispatcher` 구현 (forward/include)

---

## 📋 Phase 2: Traditional Server 구현 (2-3주)

**목표**: 표준 톰캣 스타일의 Thread-per-Request 서버 구현

### 2.1 Traditional 서블릿 컨테이너 (1주)

#### 2.1.1 핵심 컨테이너 구현
- [ ] `TraditionalServletContainer` 클래스
- [ ] `TraditionalHttpServletRequest` 구현
- [ ] `TraditionalHttpServletResponse` 구현
- [ ] 동기식 서블릿 라이프사이클 관리

#### 2.1.2 스레드 관리
- [ ] `ThreadPoolExecutor` 기반 스레드풀
- [ ] 요청당 스레드 할당 및 관리
- [ ] 스레드 로컬 스토리지 활용 (요청 컨텍스트)

### 2.2 블로킹 I/O 처리 (1주)
- [ ] `ServerSocket` 기반 연결 수락
- [ ] 동기식 소켓 읽기/쓰기
- [ ] 연결 타임아웃 관리
- [ ] 에러 핸들링 및 복구

### 2.3 고급 기능 구현 (1주)
- [ ] Keep-alive 연결 처리
- [ ] 파일 서빙 (정적 리소스)
- [ ] 에러 페이지 처리
- [ ] 로깅 시스템 통합

### 2.4 테스트 및 검증 (2-3일)
- [ ] 단위 테스트 (각 컴포넌트별)
- [ ] 통합 테스트 (전체 요청 처리 플로우)
- [ ] 부하 테스트 (100-500 동시 연결)
- [ ] 메모리 누수 검증

---

## 📋 Phase 3: 성능 측정 및 벤치마킹 도구 (1-2주)

**목표**: 정밀한 성능 비교를 위한 측정 도구 및 테스트 시나리오

### 3.1 벤치마킹 클라이언트 (1주)
- [ ] `HttpBenchmarkClient` 클래스
    - 다중 스레드 클라이언트 풀
    - 다양한 요청 패턴 지원 (단순, I/O 집약적, CPU 집약적)
    - 연결 재사용 및 Keep-alive 지원

### 3.2 성능 메트릭 수집기 (3-4일)
- [ ] `PerformanceProfiler` 클래스
    - JVM 메트릭 수집 (힙, GC, 스레드)
    - OS 레벨 메트릭 (CPU, 메모리, 네트워크)
    - 애플리케이션 메트릭 (응답시간, 처리량, 에러율)

### 3.3 테스트 시나리오 및 리포팅 (2-3일)
- [ ] 다양한 워크로드 시나리오
    - 단순 GET 요청 (CPU 바운드)
    - JSON API 요청 (I/O 바운드)
    - 파일 업로드/다운로드
    - 데이터베이스 조회 시뮬레이션 (대기 시간)
- [ ] 실시간 모니터링 대시보드
- [ ] 상세 성능 리포트 생성

---

## 📋 Phase 4: Hybrid Server 구현 (3-4주)

**목표**: 톰캣 + 이벤트루프 하이브리드 아키텍처 구현

### 4.1 비동기 서블릿 컨테이너 (2주)

#### 4.1.1 하이브리드 컨테이너 설계
- [ ] `HybridServletContainer` 클래스
- [ ] `AsyncHttpServletRequest` 구현 (비동기 확장)
- [ ] `AsyncHttpServletResponse` 구현
- [ ] `AsyncContext` 완전 구현

#### 4.1.2 컨텍스트 스위칭 매니저
- [ ] `ContextSwitchManager` 클래스
    - I/O 대기 감지 및 스레드 해제
    - 비동기 작업 큐 관리
    - 작업 완료 시 콜백 처리
- [ ] `AsyncTaskExecutor` 클래스
    - 백그라운드 작업 실행
    - 타임아웃 관리 (10초 제한)
    - 결과 콜백 처리

### 4.2 스마트 스레드 관리 (1주)
- [ ] `AdaptiveThreadPool` 클래스
    - 동적 스레드 풀 크기 조정
    - 작업 부하에 따른 스레드 증감
- [ ] `ThreadContextManager` 클래스
    - 스레드별 컨텍스트 저장/복원
    - 세션 및 요청 상태 관리

### 4.3 백프레셔 및 플로우 제어 (1주)
- [ ] `BackpressureController` 클래스
    - 과부하 상황 감지 및 대응
    - 큐 크기 제한 및 거부 전략
- [ ] `QualityOfService` 매니저
    - 요청 우선순위 관리
    - SLA 기반 처리 보장

---

## 📋 Phase 5: Event Loop Server 구현 (3-4주)

**목표**: 단일 스레드 이벤트루프 기반 완전 비동기 서버

### 5.1 이벤트루프 아키텍처 (2주)

#### 5.1.1 핵심 이벤트루프
- [ ] `EventLoopServletContainer` 클래스
- [ ] `EventLoop` 클래스 (NIO Selector 기반)
- [ ] `EventQueue` 및 `EventDispatcher`
- [ ] 논블로킹 소켓 처리

#### 5.1.2 비동기 서블릿 API
- [ ] `AsyncHttpServletRequest` (CompletableFuture 기반)
- [ ] `AsyncHttpServletResponse` (스트리밍 지원)
- [ ] `EventDrivenServlet` 추상 클래스

### 5.2 고급 비동기 패턴 (1-2주)
- [ ] `CompletableFuture` 체인 관리
- [ ] 비동기 파일 I/O 처리
- [ ] 비동기 데이터베이스 커넥션 풀
- [ ] 웹소켓 지원 (업그레이드 프로토콜)

### 5.3 에러 핸들링 및 복구 (3-4일)
- [ ] 비동기 예외 처리 메커니즘
- [ ] 데드락 감지 및 복구
- [ ] 서킷 브레이커 패턴 구현

---

## 📋 Phase 6: 고급 기능 및 최적화 (2-3주)

### 6.1 성능 최적화
- [ ] 제로 카피 I/O (FileChannel.transferTo)
- [ ] 객체 풀링 (요청/응답 객체 재사용)
- [ ] JIT 컴파일러 최적화 고려
- [ ] GC 튜닝 및 메모리 최적화

### 6.2 모니터링 및 관리
- [ ] JMX 기반 관리 인터페이스
- [ ] 실시간 메트릭 수집 및 알림
- [ ] 헬스체크 및 프로브 엔드포인트
- [ ] 설정 동적 변경 지원

### 6.3 보안 기능
- [ ] HTTPS/TLS 지원
- [ ] 요청 크기 제한 및 DoS 방어
- [ ] 인증 및 인가 프레임워크
- [ ] 보안 헤더 자동 추가

---

## 📋 Phase 7: 종합 테스트 및 분석 (1-2주)

### 7.1 통합 성능 벤치마크
- [ ] 동일 조건 하 세 서버 비교
- [ ] 다양한 워크로드 시나리오 테스트
- [ ] 장기간 안정성 테스트 (24시간 이상)
- [ ] 메모리 누수 및 리소스 누수 검증

### 7.2 결과 분석 및 문서화
- [ ] 상세 성능 비교 리포트
- [ ] 각 아키텍처의 장단점 분석
- [ ] 사용 케이스별 권장사항
- [ ] 학습한 내용 정리 및 공유

---

## 🎯 각 아키텍처별 서블릿 구현 차이점

### 1. Traditional Server
```java
public class TraditionalServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        // 동기식 처리 - 스레드가 완료까지 블로킹
        String result = performDatabaseQuery(); // 블로킹 호출
        resp.getWriter().write(result);
        // 스레드가 전체 처리 완료까지 점유
    }
}
```

### 2. Hybrid Server
```java
public class HybridServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        AsyncContext asyncContext = req.startAsync();
        
        // I/O 작업을 별도 스레드로 위임, 현재 스레드는 해제
        CompletableFuture.supplyAsync(() -> performDatabaseQuery())
            .thenAccept(result -> {
                try {
                    resp.getWriter().write(result);
                    asyncContext.complete(); // 비동기 완료
                } catch (IOException e) {
                    asyncContext.complete();
                }
            });
        // doGet 메서드는 즉시 리턴, 스레드 해제
    }
}
```

### 3. Event Loop Server
```java
public class EventDrivenServlet extends AsyncServlet {
    @Override
    protected CompletableFuture<Void> doGetAsync(
            AsyncHttpServletRequest req, 
            AsyncHttpServletResponse resp) {
        
        // 완전 비동기 체인으로 처리
        return performAsyncDatabaseQuery()
            .thenCompose(result -> resp.writeAsync(result))
            .exceptionally(throwable -> {
                resp.setStatus(500);
                return null;
            });
        // 모든 I/O가 이벤트루프에서 논블로킹으로 처리
    }
}
```

---

## 📅 상세 개발 일정

| Phase | 기간 | 주요 마일스톤 | 누적 |
|-------|------|---------------|------|
| Phase 1 | 4-5주 | 서블릿 API 및 HTTP 완전 구현 | 5주 |
| Phase 2 | 2-3주 | Traditional Server 완성 | 8주 |
| Phase 3 | 1-2주 | 벤치마킹 도구 완성 | 10주 |
| Phase 4 | 3-4주 | Hybrid Server 완성 | 14주 |
| Phase 5 | 3-4주 | Event Loop Server 완성 | 18주 |
| Phase 6 | 2-3주 | 고급 기능 및 최적화 | 21주 |
| Phase 7 | 1-2주 | 최종 분석 및 문서화 | 23주 |

**총 예상 기간**: **약 5-6개월** (파트타임 기준)

---

## 🔧 개발 원칙 및 가이드라인

### 코드 품질
- **테스트 주도 개발**: 각 컴포넌트마다 단위 테스트 필수
- **문서화**: 복잡한 로직은 상세한 주석과 설계 문서
- **성능 측정**: 모든 주요 기능에 성능 메트릭 수집 포인트

### 학습 목표 달성
- **깊이 있는 이해**: 표면적 구현이 아닌 원리 파악
- **실제 적용**: 각 패턴이 실제 어떤 상황에서 유용한지 체험
- **비교 분석**: 정량적 데이터를 통한 객관적 비교

### 확장성 고려
- **모듈러 설계**: 컴포넌트 간 느슨한 결합
- **설정 가능**: 다양한 시나리오 테스트를 위한 설정 분리
- **플러그인 아키텍처**: 새로운 기능 추가가 쉬운 구조

---

## 🚀 기대 효과

이 프로젝트를 완성하면 다음과 같은 심화 지식을 얻을 수 있습니다:

### 기술적 역량
- **서버 아키텍처 설계** 능력
- **멀티스레딩 및 동시성** 전문 지식
- **네트워크 프로그래밍** 실무 경험
- **성능 최적화** 노하우

### 시스템 이해도
- **톰캣, Netty 같은 실제 서버**의 동작 원리
- **스프링 MVC, FastAPI** 같은 프레임워크의 내부 구조
- **마이크로서비스 아키텍처**에서의 서버 역할

정말 **방대하고 도전적인 프로젝트**가 되겠지만, 그만큼 **깊이 있는 학습**과 **실무에 바로 적용 가능한 지식**을 얻을 수 있을 것입니다! 🔥

어디서부터 시작해볼까요? Phase 1의 서블릿 API 설계부터 차근차근 해보시겠어요?