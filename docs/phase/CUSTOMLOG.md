# 커스텀 Logger 시스템 설계 문서

## 🎯 구현 배경 및 목적

### 왜 기존 Logging Framework 대신 커스텀 구현했는가?

#### 1. **Zero Dependency 정책**
- **외부 라이브러리 없음**: SLF4J, Logback, Log4j 등의 의존성 제거
- **순수 Java 구현**: JDK 표준 라이브러리만 사용
- **설정 복잡도 최소화**: XML/properties 파일 불필요

#### 2. **교육적 목적**
- **HTTP 서버 아키텍처 학습에 집중**: 로깅 프레임워크 설정으로 본질 흐림 방지
- **내부 동작 원리 이해**: 비동기 처리, 큐 관리, 스레드 모델 학습
- **설계 패턴 적용**: Factory 패턴, Strategy 패턴 실습

#### 3. **성능 vs 단순성 균형**
- **AsyncLogger**: 고성능 비동기 처리
- **SimpleLogger**: 단순한 동기 처리
- **상황별 선택**: 개발/프로덕션 환경에 맞는 유연성

#### 4. **면접 대응력**
- **"왜 Logger 안 썼나?"** → "커스텀 비동기 Logger 구현"
- **"성능은?"** → "SLF4J와 유사한 비동기 큐 기반"
- **"확장성은?"** → "인터페이스 기반으로 교체 가능"

## 📐 시스템 아키텍처

### 전체 구조
```
server/core/logging/
├── LogLevel.java        # 로그 레벨 정의 (DEBUG, INFO, WARN, ERROR)
├── Logger.java          # SLF4J 스타일 인터페이스
├── AsyncLogger.java     # 비동기 구현체 (별도 스레드 + println)
├── SimpleLogger.java    # 동기 구현체 (즉시 println)
└── LoggerFactory.java   # 팩토리 + 타입 선택 + 전역 설정
```

### 클래스 다이어그램
```
<<interface>>
    Logger
      ↑
   ┌──┴──┐
   │     │
AsyncLogger  SimpleLogger
   │     │
   └──┬──┘
      │
LoggerFactory (생성 및 관리)
```

## 🔧 핵심 컴포넌트 상세

### 1. LogLevel.java
```java
public enum LogLevel {
    DEBUG(0), INFO(1), WARN(2), ERROR(3)
}
```
**역할:**
- 로그 레벨 우선순위 정의
- 레벨별 필터링 지원
- 표준 로깅 레벨 준수

### 2. Logger.java (인터페이스)
```java
public interface Logger {
    void info(String message);
    void info(String format, Object... args);  // SLF4J 스타일
    void error(String message, Throwable throwable);
    boolean isDebugEnabled();  // 성능 최적화
}
```
**특징:**
- **SLF4J 호환 API**: 기존 로깅 경험 활용
- **포맷팅 지원**: `logger.info("User {}, Count {}", name, count)`
- **레벨 체크**: 불필요한 문자열 생성 방지

### 3. AsyncLogger.java (비동기 구현체)

#### 동작 원리
```java
public class AsyncLogger implements Logger {
    private final BlockingQueue<LogMessage> messageQueue;
    private final Thread loggerThread;
    
    // 1. 로그 호출 (메인 스레드)
    public void info(String message) {
        messageQueue.offer(new LogMessage(INFO, message));  // 큐에 추가만
    }
    
    // 2. 별도 스레드에서 처리
    private void processMessages() {
        while (running) {
            LogMessage msg = messageQueue.take();
            System.out.println(formatMessage(msg));  // 실제 println!
        }
    }
}
```

#### 핵심 특징
- **논블로킹**: 호출 스레드는 큐에 추가만 하고 즉시 반환
- **순서 보장**: BlockingQueue를 통한 FIFO 처리
- **백프레셔 처리**: 큐 가득시 오래된 메시지 제거
- **우아한 종료**: 큐 비울 때까지 대기 후 스레드 종료

#### 성능 최적화
```java
// 레벨 체크로 불필요한 문자열 생성 방지
public void debug(String format, Object... args) {
    if (isDebugEnabled()) {  // 먼저 체크
        log(DEBUG, formatMessage(format, args), null);
    }
}
```

### 4. SimpleLogger.java (동기 구현체)

#### 동작 원리
```java
public class SimpleLogger implements Logger {
    public void info(String message) {
        String formatted = formatMessage(INFO, message);
        System.out.println(formatted);  // 즉시 출력
    }
}
```

#### 특징
- **즉시 출력**: 호출 즉시 System.out.println
- **순서 완벽 보장**: 멀티스레드 환경에서도 호출 순서 유지
- **디버깅 친화적**: 스택 트레이스와 로그 순서 일치
- **최소 메모리**: 큐나 별도 스레드 불필요

### 5. LoggerFactory.java (팩토리)

#### 생성 및 캐싱
```java
public class LoggerFactory {
    private static final ConcurrentMap<String, Logger> loggerCache = new ConcurrentHashMap<>();
    
    public static Logger getLogger(Class<?> clazz) {
        return loggerCache.computeIfAbsent(clazz.getName(), name -> 
            new AsyncLogger(name, defaultLevel)  // 기본은 Async
        );
    }
    
    public static Logger getSimpleLogger(Class<?> clazz) {
        return getLogger(clazz, LoggerType.SIMPLE);  // 명시적 Simple
    }
}
```

#### 전역 설정 관리
```java
// 개발용 설정
public static void configureForDevelopment() {
    LoggerConfig config = new LoggerConfig()
        .setDefaultLevel(LogLevel.DEBUG)
        .setDefaultLoggerType(LoggerType.SIMPLE)  // 즉시 출력
        .setEnableColors(true)
        .setEnableThreadName(true);
    configure(config);
}

// 프로덕션용 설정
public static void configureForProduction() {
    LoggerConfig config = new LoggerConfig()
        .setDefaultLevel(LogLevel.INFO)
        .setDefaultLoggerType(LoggerType.ASYNC)   // 고성능
        .setEnableColors(false)
        .setMaxQueueSize(10000);
    configure(config);
}
```

## ⚡ 성능 특성 비교

### AsyncLogger vs SimpleLogger

| 측면 | AsyncLogger | SimpleLogger |
|------|-------------|--------------|
| **처리량** | 높음 (논블로킹) | 보통 (블로킹) |
| **지연시간** | 낮음 (큐 지연) | 즉시 |
| **메모리** | 큐 사용 | 최소 |
| **CPU** | 멀티스레드 활용 | 단일스레드 |
| **순서 보장** | 큐 순서 | 완벽 보장 |
| **디버깅** | 복잡 | 간단 |

### 성능 벤치마크 (가상)
```
10,000 로그 메시지 출력 시:

AsyncLogger:
- 메인 스레드 시간: 15ms (큐에 추가만)
- 총 처리 시간: 200ms (백그라운드)
- 메모리: 512KB (큐 버퍼)

SimpleLogger:
- 메인 스레드 시간: 180ms (즉시 출력)
- 총 처리 시간: 180ms
- 메모리: 64KB (최소)
```

## 🎨 메시지 포맷팅

### 출력 형식
```
[2025-08-16 15:30:45.123] INFO  server.ThreadedServer    - Server started on port 8080
[timestamp]               level logger-name             message
```

### 색상 지원 (터미널)
```java
private String getColorCode(LogLevel level) {
    switch (level) {
        case DEBUG: return "\u001B[36m"; // Cyan
        case INFO:  return "\u001B[32m"; // Green  
        case WARN:  return "\u001B[33m"; // Yellow
        case ERROR: return "\u001B[31m"; // Red
    }
}
```

### Logger 이름 단축
```java
// 전체 이름이 길 경우 자동 단축
"server.threaded.ThreadedServer" → "s.t.ThreadedServer"
```

## 🔄 생명주기 관리

### AsyncLogger 시작
```java
public AsyncLogger(String name, LogLevel level) {
    this.messageQueue = new LinkedBlockingQueue<>(maxQueueSize);
    this.loggerThread = new Thread(this::processMessages, "AsyncLogger-" + name);
    this.loggerThread.setDaemon(true);  // JVM 종료 방해 안함
    this.loggerThread.start();
}
```

### 우아한 종료
```java
public void shutdown() {
    running.set(false);           // 새 메시지 수락 중지
    loggerThread.interrupt();     // 스레드 깨우기
    
    try {
        loggerThread.join(1000);  // 1초 대기
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}

// JVM 종료시 자동 정리
static {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        LoggerFactory.shutdown();
    }));
}
```

## 🚀 사용 예시

### 기본 사용법
```java
public class ThreadedServer {
    private static final Logger logger = LoggerFactory.getLogger(ThreadedServer.class);
    
    public void start() {
        logger.info("Starting server on port {}", port);
        
        try {
            // 서버 로직
            logger.debug("Server configuration: {}", config);
        } catch (Exception e) {
            logger.error("Server start failed", e);
        }
        
        logger.info("Server started successfully");
    }
}
```

### 조건부 로깅 (성능 최적화)
```java
// 비효율적 - 항상 문자열 생성
logger.debug("Heavy computation result: " + expensiveOperation());

// 효율적 - 레벨 체크 후 실행
if (logger.isDebugEnabled()) {
    logger.debug("Heavy computation result: {}", expensiveOperation());
}
```

### 설정별 사용
```java
// 개발 환경 - 즉시 출력으로 디버깅
LoggerFactory.configureForDevelopment();
Logger devLogger = LoggerFactory.getLogger(MyClass.class);

// 프로덕션 환경 - 고성능 비동기
LoggerFactory.configureForProduction();  
Logger prodLogger = LoggerFactory.getLogger(MyClass.class);
```

## 🎯 설계 결정사항

### 1. 왜 println을 내부에서 사용했는가?
- **의존성 제거**: 외부 라이브러리 없이 순수 Java
- **단순성**: 복잡한 출력 스트림 관리 불필요
- **호환성**: 모든 환경에서 동작 보장

### 2. 왜 두 가지 구현체를 만들었는가?
- **상황별 최적화**: 개발 vs 프로덕션
- **트레이드오프 선택**: 성능 vs 단순성
- **학습 목적**: 동기/비동기 처리 방식 비교

### 3. 왜 SLF4J 스타일 API를 채택했는가?
- **개발자 친숙도**: 업계 표준 API
- **마이그레이션 용이**: 향후 SLF4J 교체 가능
- **기능 완성도**: 레벨 체크, 포맷팅 등 필수 기능

### 4. 메모리 관리 전략
```java
// 큐 오버플로우 처리
if (!messageQueue.offer(logMessage)) {
    messageQueue.poll();  // 오래된 메시지 제거
    messageQueue.offer(logMessage);  // 새 메시지 추가
}
```

## 💡 확장 가능성

### 향후 개선 방향
1. **파일 출력**: FileAppender 구현
2. **네트워크 전송**: TCP/UDP Appender
3. **구조화 로깅**: JSON 포맷 지원
4. **메트릭 수집**: 로그 통계 정보
5. **필터링**: 패키지별 세밀한 제어

### 인터페이스 확장
```java
// 새로운 구현체 추가 가능
public class FileLogger implements Logger {
    public void info(String message) {
        writeToFile(formatMessage(message));
    }
}

// 팩토리에서 선택
LoggerFactory.getLogger(clazz, LoggerType.FILE);
```

## 📋 결론

### 구현 성과
- ✅ **Zero Dependency** 달성
- ✅ **고성능 비동기 처리** 구현
- ✅ **표준 API 호환성** 확보
- ✅ **유연한 설정 시스템** 제공
- ✅ **우아한 생명주기 관리** 구현

### 기술적 가치
- **아키텍처 설계**: 인터페이스 기반 확장성
- **동시성 처리**: 스레드 안전한 큐 관리
- **성능 최적화**: 레벨 체크, 백프레셔 처리
- **메모리 관리**: 큐 오버플로우 방지

### 면접 대응력
이 구현을 통해 다음 질문들에 완벽하게 답변할 수 있습니다:
- "로깅 시스템을 직접 구현한 이유는?"
- "비동기 로깅의 장단점은?"
- "성능과 단순성 사이의 트레이드오프는?"
- "확장성을 고려한 설계 방법은?"

**결과적으로, 교육적 목적과 실용성을 모두 만족하는 완전한 로깅 시스템을 구현했습니다.** 🎯