# Phase 2.1: Thread-per-Request HTTP 서버 구현 완료

## 📋 구현 개요

Phase 2.1에서는 **전통적인 Thread-per-Request 방식의 HTTP 서버**를 완성했습니다. 이는 JavaServerArchitectures 프로젝트의 첫 번째 서버 아키텍처로, 각 클라이언트 요청을 별도의 스레드에서 처리하는 가장 직관적이고 구현하기 쉬운 방식입니다.

## 🎯 핵심 설계 원칙

### 1. **Thread-per-Request 모델**
- 각 클라이언트 연결마다 별도 스레드 할당
- 요청 처리 완료까지 해당 스레드가 전담 처리
- 블로킹 I/O로 구현 단순성 확보

### 2. **스레드풀 기반 최적화**
- 스레드 생성/소멸 비용을 줄이기 위한 고정 크기 스레드풀 사용
- 동시 처리 가능한 요청 수를 스레드풀 크기로 제한
- 백프레셔(Backpressure) 정책으로 과부하 상황 처리

### 3. **완전한 HTTP/1.1 지원**
- HTTP 요청 파싱 (헤더, 바디, 쿼리 파라미터)
- HTTP 응답 생성 (상태 코드, 헤더, 바디)
- 다양한 Content-Type 지원 (HTML, JSON, 텍스트)

## 📁 구현된 파일 구조

```
src/main/java/
├── com/serverarch/common/http/          # 🔥 HTTP 공통 모듈
│   ├── HttpStatus.java                  # HTTP 상태 코드 정의 + 유틸리티
│   ├── HttpMethod.java                  # HTTP 메서드 상수 + 검증 로직
│   └── HttpHeaders.java                 # HTTP 헤더 관리 클래스
├── com/serverarch/traditional/          # 🔥 Thread-per-Request 서버
│   ├── HttpRequest.java                 # HTTP 요청 모델
│   ├── HttpResponse.java                # HTTP 응답 모델
│   ├── ThreadedRequestProcessor.java    # HTTP 요청/응답 처리 로직
│   ├── ThreadedServer.java              # 메인 서버 클래스
│   ├── ThreadedServerLauncher.java      # 서버 실행 런처
│   └── ThreadPoolManager.java           # 고급 스레드풀 관리 (확장)
```

## 🔧 핵심 구현 컴포넌트

### 1. **ThreadedServer.java** - 메인 서버 엔진
```java
// 핵심 기능들
- 포트 바인딩 및 클라이언트 연결 수락
- 스레드풀 관리 (생성, 작업 할당, 종료)
- 요청당 별도 스레드 할당 (Thread-per-Request)
- 소켓 설정 및 타임아웃 관리
- 서버 통계 수집 (처리된 요청 수, 실패 수 등)
- 우아한 종료 처리 (Graceful Shutdown)
```

**지원하는 생성자:**
- `ThreadedServer(int port)` - 기본 설정
- `ThreadedServer(int port, int threadPoolSize)` - 포트 + 스레드풀 크기
- `ThreadedServer(int port, int threadPoolSize, int backlog)` - 전체 설정

### 2. **ThreadedRequestProcessor.java** - HTTP 처리 엔진
```java
// 핵심 기능들
- HTTP 요청 파싱 (Request Line, Headers, Body)
- 간단한 라우팅 시스템 ("/", "/hello", "/echo", "/stats")
- HTTP 응답 생성 및 직렬화
- 에러 처리 및 HTML 에러 페이지 생성
- Content-Type 자동 설정
- XSS 방지를 위한 HTML 이스케이프
```

**지원하는 엔드포인트:**
- `GET /` - 홈페이지 (사용 가능한 API 목록)
- `GET /hello?name=이름` - 인사말 페이지
- `POST /echo` - 요청 바디를 그대로 반환
- `GET /stats` - 서버 통계 및 시스템 정보

### 3. **HttpRequest.java** - HTTP 요청 모델
```java
// 핵심 기능들
- 불변성 보장 (final 필드들)
- 쿼리 파라미터 파싱 (?name=value&age=30)
- 경로 파라미터 지원 (/users/{id})
- 요청 속성 관리 (필터 체인용)
- 편의 메서드들 (isAjax(), isJson(), hasBody() 등)
- 자주 사용되는 헤더 접근 메서드들
```

### 4. **HttpResponse.java** - HTTP 응답 모델
```java
// 핵심 기능들
- 정적 팩토리 메서드 (ok(), json(), html(), notFound() 등)
- 자동 Content-Length 설정
- 캐시 제어 메서드 (setMaxAge(), disableCache())
- CORS 헤더 지원 (setCorsHeaders())
- 쿠키 설정 지원 (addCookie())
- HTML 에러 페이지 자동 생성
```

### 5. **ThreadedServerLauncher.java** - 서버 실행 관리
```java
// 핵심 기능들
- 명령줄 인수 파싱 및 검증
- 로깅 시스템 설정 (콘솔 + 파일)
- 우아한 종료 훅 등록 (Ctrl+C 처리)
- 최종 통계 출력
- 개발/테스트용 편의 메서드들
- 시스템 정보 출력 기능
```

## 🚀 실행 방법

### 1. **기본 실행**
```bash
# 기본 포트 8080, 스레드풀 100개로 실행
java com.serverarch.traditional.ThreadedServerLauncher

# 또는 단축 명령 (IDE에서)
java ThreadedServerLauncher
```

### 2. **커스텀 설정 실행**
```bash
# 포트 9090, 스레드풀 200개로 실행
java ThreadedServerLauncher 9090 200

# 포트만 변경 (스레드풀은 기본값 100)
java ThreadedServerLauncher 8081
```

### 3. **도움말 확인**
```bash
java ThreadedServerLauncher --help
# 또는
java ThreadedServerLauncher -h
```

## 🧪 테스트 방법

### 1. **브라우저 테스트**
```
http://localhost:8080/           # 홈페이지
http://localhost:8080/hello      # 기본 인사말
http://localhost:8080/hello?name=김철수  # 개인화된 인사말
http://localhost:8080/stats      # 서버 통계
```

### 2. **curl 명령어 테스트**
```bash
# GET 요청
curl http://localhost:8080/

# 쿼리 파라미터 포함
curl "http://localhost:8080/hello?name=개발자"

# POST 에코 테스트
curl -X POST http://localhost:8080/echo \
     -H "Content-Type: text/plain" \
     -d "Hello, World!"

# JSON 에코 테스트
curl -X POST http://localhost:8080/echo \
     -H "Content-Type: application/json" \
     -d '{"message": "Hello", "from": "Client"}'

# 서버 통계 조회
curl http://localhost:8080/stats
```

### 3. **성능 테스트**
```bash
# Apache Bench로 간단한 부하 테스트
ab -n 1000 -c 10 http://localhost:8080/

# wrk로 더 정교한 부하 테스트
wrk -t12 -c400 -d30s http://localhost:8080/hello
```

## 📊 성능 특성

### **장점 ✅**
- **구현 단순성**: 직관적이고 이해하기 쉬운 코드
- **디버깅 용이성**: 스택 추적이 명확하고 문제 진단이 쉬움
- **블로킹 I/O 호환성**: 기존 라이브러리와 쉽게 연동
- **개발 생산성**: 빠른 개발과 테스트 가능

### **제한사항 ⚠️**
- **메모리 사용량**: 스레드당 약 1MB 스택 메모리 사용
- **동시 연결 제한**: 일반적으로 수백 개 수준 (JVM 스레드 제한)
- **컨텍스트 스위칭**: 스레드 수가 많아질수록 성능 저하
- **확장성**: CPU 코어 수 대비 비효율적

### **권장 사용 시나리오**
- 중소 규모 웹 애플리케이션
- 내부 도구나 관리 시스템
- 프로토타입이나 MVP 개발
- 블로킹 I/O가 많은 레거시 시스템 연동

## 🔧 설정 가능한 옵션

### **서버 설정**
```java
// 기본값들
DEFAULT_THREAD_POOL_SIZE = 200;    // 기본 스레드풀 크기
DEFAULT_BACKLOG = 50;              // 기본 연결 대기 큐 크기
SOCKET_TIMEOUT = 30000;            // 소켓 타임아웃 (30초)
```

### **HTTP 제한사항**
```java
MAX_REQUEST_LINE_LENGTH = 8192;    // 요청 라인 최대 길이 (8KB)
MAX_HEADER_SIZE = 8192;            // 헤더 최대 크기 (8KB)
MAX_BODY_SIZE = 10 * 1024 * 1024;  // 바디 최대 크기 (10MB)
```

## 📈 모니터링 정보

### **실시간 통계**
- 총 요청 수 (totalRequestsReceived)
- 처리 완료 요청 수 (totalRequestsProcessed)
- 실패한 요청 수 (totalRequestsFailed)
- 현재 활성 연결 수 (currentActiveConnections)
- 성공률 / 실패율 계산

### **시스템 정보**
- JVM 메모리 사용량 (총/사용/여유)
- 활성 스레드 수
- CPU 코어 수
- 현재 처리 스레드 정보

### **로그 출력**
```
[2025-01-XX XX:XX:XX] [INFO] ThreadedServer: ThreadedServer가 포트 8080에서 시작되었습니다 (스레드풀: 200)
[2025-01-XX XX:XX:XX] [FINE] ThreadedRequestProcessor: 요청 처리 완료: GET /hello (15ms)
[2025-01-XX XX:XX:XX] [INFO] ThreadedServer: 서버 통계 - 총 요청: 1523, 처리 완료: 1520, 실패: 3, 활성 연결: 5
```

## 🔍 코드 품질 특징

### **설계 패턴 적용**
- **팩토리 패턴**: HttpResponse.ok(), json(), html() 등
- **빌더 패턴**: HttpHeaders, HttpRequest 설정
- **전략 패턴**: 다양한 Content-Type 처리
- **템플릿 메서드**: RequestHandler의 요청 처리 흐름

### **보안 고려사항**
- **XSS 방지**: HTML 출력 시 자동 이스케이프
- **DoS 방지**: 요청 크기 제한 및 타임아웃 설정
- **입력 검증**: HTTP 메서드, 헤더 이름 등 유효성 검사
- **리소스 보호**: try-with-resources로 자동 정리

### **확장성 준비**
- **인터페이스 분리**: 요청 처리 로직을 별도 클래스로 분리
- **설정 주입**: 생성자를 통한 설정값 주입
- **통계 수집**: 성능 모니터링을 위한 메트릭 수집
- **로깅 체계**: 다양한 레벨의 로그 출력

## 🎯 다음 단계 (Phase 2.2)

Phase 2.1에서 구현한 Thread-per-Request 서버를 기반으로, 다음 단계에서는:

1. **고급 스레드풀 관리** (ThreadPoolManager 활용)
2. **더 정교한 라우팅 시스템** (패턴 매칭, RESTful API)
3. **미들웨어/필터 체인** (인증, 로깅, CORS)
4. **정적 파일 서빙** (CSS, JS, 이미지)
5. **WebSocket 지원 준비** (프로토콜 업그레이드)

---

**🎉 Phase 2.1 완료!** 이제 우리는 완전히 작동하는 Thread-per-Request HTTP 서버를 가지게 되었습니다. 이 서버는 실제 운영 환경에서도 중소 규모의 웹 애플리케이션을 충분히 처리할 수 있는 수준입니다.