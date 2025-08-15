# Phase 3-3: Hybrid Server 구현 완료

## 📋 개요

**목표**: AsyncContext 기반 컨텍스트 스위칭 서버 구현  
**기간**: 3일차  
**완료일**: 2025년 8월 15일

## 🎯 핵심 목표 달성

### 1. AsyncContext 기반 비동기 처리 ✅
- **CompletableFuture 체이닝**: 요청 처리를 비동기 파이프라인으로 구성
- **논블로킹 I/O 시뮬레이션**: I/O 대기 시 스레드 해제 후 완료 시 다른 스레드에서 재개
- **타임아웃 관리**: 각 비동기 작업에 타임아웃 설정으로 무한 대기 방지

### 2. 컨텍스트 스위칭 및 스레드 재활용 ✅
- **이중 스레드풀 구조**:
    - **I/O 스레드풀**: CachedThreadPool로 I/O 집약적 작업 처리
    - **CPU 스레드풀**: FixedThreadPool(CPU 코어 수)로 CPU 집약적 작업 처리
- **컨텍스트 스위칭**: 작업 타입에 따라 적절한 스레드풀로 자동 전환
- **스레드 재활용**: I/O 대기 중인 스레드를 다른 요청 처리에 활용

## 🏗️ 구현 아키텍처

### 핵심 컴포넌트

#### 1. HybridServer.java
```java
public class HybridServer {
    // 비동기 컨텍스트 관리자 - Hybrid 서버의 핵심
    private AsyncContextManager asyncContextManager;
    
    // Traditional 서버 컴포넌트 재사용
    private final Router router;               // 라우팅 시스템
    private final Semaphore connectionSemaphore; // 연결 수 제한
    private final HybridServerMetrics metrics;   // 성능 메트릭
}
```

**주요 기능**:
- **비동기 요청 처리**: `handleClientConnectionAsync()` 메서드로 논블로킹 처리
- **컨텍스트 스위칭**: I/O와 CPU 작업을 적절한 스레드풀로 분배
- **그레이스풀 셧다운**: 활성 연결 완료 대기 후 안전한 종료

#### 2. AsyncContextManager (내부 클래스)
```java
public static class AsyncContextManager {
    private final ExecutorService ioExecutor;    // I/O 작업용 스레드풀
    private final ExecutorService cpuExecutor;   // CPU 작업용 스레드풀
    private final ScheduledExecutorService timeoutExecutor; // 타임아웃 관리
}
```

**핵심 메서드**:
- `processRequestAsync()`: 요청을 비동기로 처리 시작
- `executeCPUTask()`: CPU 집약적 작업 실행
- `executeIOTask()`: I/O 집약적 작업 실행
- `withTimeout()`: 타임아웃 적용

#### 3. HybridServerLauncher.java
- **설정 파일 기반 구성**: `hybrid-server.properties`에서 서버 설정 로드
- **대화형 관리 모드**: 실행 중 서버 상태 확인 및 제어
- **데모 API 제공**: RESTful API, 비동기 처리, Long Polling 등 예시

## 🔄 비동기 처리 플로우

### 요청 처리 과정
```
1. 클라이언트 연결 수락 (메인 스레드)
   ↓
2. HTTP 요청 파싱 (I/O 스레드풀)
   ↓
3. AsyncContextManager.processRequestAsync() 호출
   ↓ (컨텍스트 스위칭)
4. 라우팅 및 핸들러 실행 (적절한 스레드풀)
   ↓
5. HTTP 응답 전송 (I/O 스레드풀)
   ↓
6. 연결 정리 및 메트릭 업데이트
```

### CompletableFuture 체이닝
```java
asyncContextManager.processRequestAsync(request)  // 비동기 시작
    .thenCompose(this::routeRequest)              // 라우팅
    .thenAccept(response -> sendResponse(...))    // 응답 전송
    .exceptionally(throwable -> handleError(...)) // 예외 처리
    .join();                                      // 완료 대기
```

## 🔧 Traditional 서버와의 코드 재사용

### 재사용 컴포넌트
- **HTTP 처리**: `HttpRequestParser`, `HttpResponseBuilder`
- **라우팅 시스템**: `Router`, `RouteHandler`, `Middleware`
- **기본 클래스**: `HttpRequest`, `HttpResponse`, `HttpHeaders`

### 재사용의 장점
1. **코드 중복 방지**: 검증된 HTTP 처리 로직 재사용
2. **일관성 보장**: 모든 서버 타입에서 동일한 API 제공
3. **유지보수 효율성**: 공통 로직 수정 시 모든 서버에 자동 반영

## 📊 성능 특성

### Traditional vs Hybrid 비교

| 특성 | Traditional | Hybrid |
|------|-------------|---------|
| **동시 연결 수** | 스레드 수 제한 | 더 높은 동시성 |
| **메모리 사용량** | 스레드당 고정 | 효율적 재활용 |
| **응답 시간** | 일정함 | I/O 집약적 작업에서 개선 |
| **복잡도** | 단순함 | 중간 수준 |
| **적용 분야** | 일반적 웹 서비스 | I/O 집약적 애플리케이션 |

### 메트릭 수집
- **기본 통계**: 요청 수, 처리 시간, 에러율, 활성 연결
- **비동기 메트릭**: 컨텍스트 스위칭 횟수, I/O vs CPU 작업 비율
- **실시간 모니터링**: `/admin/metrics/realtime` 엔드포인트

## 🛠️ 구현 중 해결한 기술적 문제

### 1. buildAndSend 메서드 누락
**문제**: `HttpResponseBuilder.buildAndSend()` 메서드가 존재하지 않음  
**해결**: Traditional과 Common 패키지의 HttpResponseBuilder에 메서드 추가
```java
public static void buildAndSend(OutputStream outputStream, HttpResponse response) throws IOException {
    byte[] responseBytes = buildResponse(response);
    outputStream.write(responseBytes);
    outputStream.flush();
}
```

### 2. Router 메서드명 불일치
**문제**: `router.addMiddleware()` 메서드가 존재하지 않음  
**해결**: `router.use()` 메서드로 변경 (실제 Router 클래스의 메서드명)

### 3. CorsMiddleware 기본 생성자 부재
**문제**: `new CorsMiddleware()` 호출 시 컴파일 오류  
**해결**: 기본 생성자 추가
```java
public CorsMiddleware() {
    this("*", "GET, POST, PUT, DELETE, OPTIONS", "Content-Type, Authorization");
}
```

## 🎨 데모 API 구현

### 1. RESTful 사용자 관리 API
- `GET /api/users` - 모든 사용자 조회
- `POST /api/users` - 새 사용자 생성
- `GET /api/users/{id}` - 특정 사용자 조회
- `DELETE /api/users/{id}` - 사용자 삭제

### 2. 비동기 처리 데모
- `GET /api/async-demo?delay=2000` - 비동기 작업 시뮬레이션
- 지정된 시간만큼 지연 후 응답 (최대 10초)
- 처리 스레드 정보와 함께 응답

### 3. Long Polling 시뮬레이션
- `GET /api/long-poll?timeout=30` - 실시간 이벤트 대기
- 랜덤 시간 후 알림 이벤트 발생
- WebSocket 대안으로 실시간 통신 구현

### 4. 파일 업로드 처리
- `POST /api/upload` - 파일 업로드 시뮬레이션
- multipart/form-data와 일반 바이너리 데이터 모두 지원

## 🔍 모니터링 및 관리 기능

### 1. 헬스 체크 엔드포인트
- `GET /health` - 서버 생존 여부 확인
- `GET /metrics` - 기본 성능 지표
- `GET /info` - 서버 구성 정보
- `GET /async-status` - 비동기 컨텍스트 상태

### 2. 관리자 엔드포인트
- `GET /admin/status` - 상세 서버 상태
- `GET /admin/performance` - 성능 메트릭
- `GET /admin/metrics/realtime` - 실시간 메트릭

### 3. 대화형 관리 모드
```bash
java HybridServerLauncher -i  # 대화형 모드 실행
```
**지원 명령어**: `status`, `metrics`, `memory`, `async`, `gc`, `loglevel`, `stop`

## 📁 파일 구조

```
com/serverarch/hybrid/
├── HybridServer.java           # 메인 서버 클래스
└── HybridServerLauncher.java   # 서버 실행기 및 데모

config/
└── hybrid-server.properties    # 서버 설정 파일

# 재사용 컴포넌트 (Traditional 패키지)
com/serverarch/traditional/
├── HttpRequest.java
├── HttpResponse.java  
├── HttpRequestParser.java
├── HttpResponseBuilder.java
└── routing/
    ├── Router.java
    ├── RouteHandler.java
    ├── Middleware.java
    ├── LoggingMiddleware.java
    └── CorsMiddleware.java
```

## 🚀 실행 방법

### 1. 기본 실행
```bash
java com.serverarch.hybrid.HybridServerLauncher
```

### 2. 설정 옵션과 함께 실행
```bash
java com.serverarch.hybrid.HybridServerLauncher -p 8081 -i
```

### 3. 서버 접속 URL
- **기본 서버**: http://localhost:8081
- **헬스 체크**: http://localhost:8081/health
- **사용자 API**: http://localhost:8081/api/users
- **비동기 데모**: http://localhost:8081/api/async-demo?delay=2000

## 📈 성과 및 학습 포인트

### 1. 아키텍처 설계 원칙
- **코드 재사용성**: Traditional 서버의 검증된 컴포넌트 활용
- **관심사의 분리**: HTTP 처리와 비동기 처리의 명확한 분리
- **확장성**: AsyncContextManager를 통한 비동기 작업 확장 가능

### 2. 비동기 프로그래밍 패턴
- **CompletableFuture 체이닝**: 복잡한 비동기 플로우를 선언적으로 구성
- **스레드풀 전략**: 작업 특성에 맞는 스레드풀 분리로 효율성 향상
- **타임아웃 처리**: 시스템 안정성을 위한 적절한 타임아웃 설정

### 3. 모니터링과 운영
- **메트릭 수집**: 성능 분석을 위한 포괄적인 지표 수집
- **그레이스풀 셧다운**: 운영 환경에서의 안전한 서버 종료
- **대화형 관리**: 실시간 서버 상태 확인 및 제어

## 🔮 다음 단계 (EventLoop 서버 준비)

1. **완전한 논블로킹 I/O**: Java NIO의 Selector와 Channel 활용
2. **이벤트 기반 아키텍처**: 모든 I/O 작업을 이벤트로 처리
3. **단일 스레드 이벤트 루프**: Node.js 스타일의 이벤트 루프 구현
4. **최대 동시성**: 수천 개의 동시 연결 처리 능력

## ✅ 완료 체크리스트

- [x] AsyncContext 기반 비동기 처리 구현
- [x] 컨텍스트 스위칭 및 스레드 재활용 구현
- [x] 이중 스레드풀 구조 (I/O + CPU) 구현
- [x] Traditional 서버 컴포넌트 재사용
- [x] 포괄적인 메트릭 수집 시스템
- [x] 데모 API 및 테스트 엔드포인트 구현
- [x] 대화형 관리 모드 구현
- [x] 그레이스풀 셧다운 구현
- [x] 컴파일 오류 수정 및 안정성 확보

**Phase 3-3 완료**: Hybrid Server 구현 성공적으로 완료! 🎉