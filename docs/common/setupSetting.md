# JavaServerArchitectures - 설정 및 셋업 완전 가이드

이 문서는 JavaServerArchitectures 프로젝트의 모든 설정 파일과 스크립트에 대한 상세한 설명을 제공합니다. 각 줄의 의미와 목적을 완전히 이해할 수 있도록 작성되었습니다.

## 🏗️ 프로젝트 개요

JavaServerArchitectures는 세 가지 서버 아키텍처를 비교 분석하는 프로젝트입니다:
- **Traditional Server (8080)**: 전통적인 스레드 풀 기반 서버
- **Hybrid Server (8081)**: 하이브리드 비동기/동기 서버
- **Event Loop Server (8082)**: 단일 스레드 이벤트 루프 서버

## 📁 프로젝트 구조

```
JavaServerArchitectures/
├── config/                 # 설정 파일들
│   ├── benchmark.properties
│   ├── eventloop-server.properties
│   ├── hybrid-server.properties
│   ├── logging.properties
│   └── traditional-server.properties
├── scripts/                # 실행 스크립트들
│   ├── build.sh
│   ├── clean.sh
│   ├── run-benchmark.sh
│   ├── run-eventloop.sh
│   ├── run-hybrid.sh
│   └── run-traditional.sh
├── src/main/java/          # 소스 코드
├── test/java/              # 테스트 코드
├── build/                  # 빌드 결과물
├── lib/                    # 외부 라이브러리
├── logs/                   # 로그 파일
└── benchmarks/             # 벤치마크 결과
```

---

## ⚙️ 설정 파일 상세 분석

### 1. benchmark.properties - 벤치마크 설정

```properties
# Benchmark Configuration
benchmark.duration=300                    # 벤치마크 실행 시간 (초) - 5분간 테스트
benchmark.warmup=30                       # 워밍업 시간 (초) - JVM 최적화를 위한 준비 시간
benchmark.connections=100,500,1000,2000   # 동시 접속자 수 시나리오 - 4단계로 부하 증가
benchmark.request.types=simple,json,upload,websocket  # 테스트할 요청 유형들
                                          # simple: 단순 GET 요청
                                          # json: JSON 데이터 POST 요청  
                                          # upload: 파일 업로드 요청
                                          # websocket: 웹소켓 연결 테스트
benchmark.output.format=json,html         # 결과 출력 형식 - JSON과 HTML 보고서 생성
benchmark.report.detailed=true            # 상세 리포트 생성 여부 - 모든 메트릭 포함
```

**설정 의미:**
- `duration=300`: 각 테스트 시나리오를 5분간 실행하여 충분한 데이터 수집
- `warmup=30`: JVM의 JIT 컴파일러 최적화와 메모리 할당을 안정화
- `connections`: 100→500→1000→2000 순으로 부하를 증가시켜 서버 한계점 파악
- `request.types`: 다양한 워크로드로 서버 성능의 다면적 평가
- `output.format`: 개발자용 JSON과 관리자용 HTML 리포트 동시 제공

---

### 2. traditional-server.properties - 전통적 서버 설정

```properties
# Traditional Server Configuration
server.port=8080                         # 서버 포트 - HTTP 기본 포트 사용
server.thread.pool.core=10               # 코어 스레드 수 - 최소 유지할 스레드 개수
server.thread.pool.max=200               # 최대 스레드 수 - 동시 처리 가능한 최대 요청 수
server.thread.pool.keepalive=60000       # 스레드 유지 시간 (ms) - 유휴 스레드 생존 시간 1분
server.connection.timeout=30000          # 연결 타임아웃 (ms) - 클라이언트 연결 대기 시간 30초
server.socket.backlog=128                # 소켓 백로그 - 대기 중인 연결 요청 큐 크기
server.enable.keepalive=true             # HTTP Keep-Alive 활성화 - 연결 재사용으로 성능 향상
server.keepalive.timeout=15000           # Keep-Alive 타임아웃 (ms) - 연결 유지 시간 15초
```

**아키텍처 특징:**
- **요청당 스레드 모델**: 각 클라이언트 요청마다 별도 스레드 할당
- **스레드 풀 관리**: 10개 코어 스레드로 시작, 필요시 최대 200개까지 확장
- **메모리 사용**: 스레드당 약 1MB 스택 메모리 사용 (최대 200MB)
- **적합한 워크로드**: CPU 집약적 작업, 동시 접속자 수가 적은 환경

---

### 3. hybrid-server.properties - 하이브리드 서버 설정

```properties
# Hybrid Server Configuration
server.port=8081                         # 서버 포트 - Traditional과 구분을 위한 포트
server.thread.pool.core=20               # 코어 스레드 수 - Traditional보다 많은 기본 스레드
server.thread.pool.max=100               # 최대 스레드 수 - 제한적 스레드 사용으로 리소스 절약
server.async.timeout=10000               # 비동기 타임아웃 (ms) - 비동기 작업 완료 대기 시간
server.context.switch.threshold=1000     # 컨텍스트 스위치 임계값 - 동기/비동기 전환 기준점
server.backpressure.enabled=true         # 백프레셔 활성화 - 과부하 상황에서 요청 제어
server.backpressure.threshold=1000       # 백프레셔 임계값 - 대기 중인 요청 수 한계점
```

**아키텍처 특징:**
- **적응형 처리**: 요청 특성에 따라 동기/비동기 처리 방식 선택
- **백프레셔 메커니즘**: 시스템 과부하 시 새로운 요청을 거부하여 안정성 확보
- **컨텍스트 스위치 최적화**: 임계값 기반으로 처리 방식 동적 결정
- **적합한 워크로드**: 혼합된 I/O와 CPU 작업, 중간 규모 동시 접속

---

### 4. eventloop-server.properties - 이벤트 루프 서버 설정

```properties
# Event Loop Server Configuration
server.port=8082                         # 서버 포트 - 다른 서버들과 구분
server.eventloop.threads=1               # 이벤트 루프 스레드 수 - 단일 스레드 모델
server.worker.threads=4                  # 워커 스레드 수 - CPU 집약적 작업용 별도 스레드
server.selector.timeout=1000             # 셀렉터 타임아웃 (ms) - I/O 이벤트 대기 시간
server.buffer.size=8192                  # 버퍼 크기 (bytes) - I/O 작업용 메모리 버퍼 (8KB)
server.websocket.enabled=true            # 웹소켓 지원 - 실시간 양방향 통신 활성화
```

**아키텍처 특징:**
- **단일 스레드 이벤트 루프**: 모든 I/O 이벤트를 하나의 스레드에서 처리
- **논블로킹 I/O**: 블로킹 없이 많은 동시 연결 처리 가능
- **워커 스레드 분리**: CPU 집약적 작업은 별도 스레드 풀에서 처리
- **적합한 워크로드**: 대량의 동시 접속, I/O 집약적 작업, 실시간 애플리케이션

---

### 5. logging.properties - 로깅 설정

```properties
# Logging Configuration
.level=INFO                               # 전역 로그 레벨 - INFO 이상만 출력
com.serverarch.level=DEBUG               # 프로젝트 패키지 로그 레벨 - 디버그 정보 포함

# Console Handler - 콘솔 출력 설정
handlers=java.util.logging.ConsoleHandler,java.util.logging.FileHandler
                                          # 로그 출력 대상 - 콘솔과 파일 동시 사용

# Console - 콘솔 로그 설정
java.util.logging.ConsoleHandler.level=INFO      # 콘솔 출력 레벨 - 운영 정보만
java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
                                          # 콘솔 포맷터 - 단순한 텍스트 형식

# File - 파일 로그 설정  
java.util.logging.FileHandler.level=DEBUG        # 파일 출력 레벨 - 모든 디버그 정보
java.util.logging.FileHandler.pattern=logs/server-%u.log
                                          # 로그 파일 패턴 - logs 폴더, 고유 번호 포함
java.util.logging.FileHandler.limit=10485760     # 파일 크기 제한 - 10MB (10 * 1024 * 1024)
java.util.logging.FileHandler.count=5            # 로그 파일 개수 - 순환식 5개 파일 유지
java.util.logging.FileHandler.formatter=java.util.logging.SimpleFormatter
                                          # 파일 포맷터 - 단순한 텍스트 형식
```

**로깅 전략:**
- **계층적 로그 레벨**: 전역 INFO, 프로젝트 DEBUG로 적절한 정보량 조절
- **이중 출력**: 콘솔은 운영 모니터링용, 파일은 디버깅용
- **로그 로테이션**: 10MB씩 5개 파일로 순환하여 디스크 공간 관리
- **패키지별 제어**: com.serverarch 패키지만 상세 로깅으로 노이즈 감소

---

## 🔧 실행 스크립트 상세 분석

### 1. build.sh - 빌드 스크립트

```bash
#!/bin/bash
# 스크립트 해석기 지정 - Bash 셸로 실행

echo "Building JavaServerArchitectures..."
# 빌드 시작 메시지 출력

# 로그 디렉토리 생성
mkdir -p logs
# -p 옵션: 상위 디렉토리가 없어도 생성, 이미 존재해도 오류 없음

# 기존 클래스 파일 정리
rm -rf build/classes/*
rm -rf build/test-classes/*
# -rf 옵션: 강제(-f) 재귀적(-r) 삭제로 모든 하위 파일/폴더 제거

echo "Compiling main sources..."
# 메인 소스 컴파일 시작 알림

# 메인 소스 컴파일
find src/main/java -name "*.java" > sources.list
# find: src/main/java 디렉토리에서 .java 확장자 파일 검색
# > sources.list: 검색 결과를 sources.list 파일에 저장

if [ -s sources.list ]; then
# -s: 파일이 존재하고 크기가 0보다 큰지 확인 (소스 파일이 있는지 검사)
    javac -d build/classes -cp "lib/*" @sources.list
    # javac: 자바 컴파일러 실행
    # -d build/classes: 컴파일된 클래스 파일 출력 디렉토리
    # -cp "lib/*": 클래스패스에 lib 폴더의 모든 JAR 파일 포함
    # @sources.list: 파일에서 컴파일할 소스 파일 목록 읽기
    if [ $? -eq 0 ]; then
    # $?: 직전 명령어의 종료 상태 코드 (0=성공, 0이외=실패)
        echo "Main compilation successful!"
    else
        echo "Main compilation failed!"
        exit 1
        # exit 1: 오류 상태로 스크립트 종료
    fi
else
    echo "No main source files found."
fi

echo "Compiling test sources..."
# 테스트 소스 컴파일 시작 알림

# 테스트 소스 컴파일
find test/java -name "*.java" > test-sources.list 2>/dev/null
# 2>/dev/null: 에러 메시지를 /dev/null로 리다이렉트 (에러 무시)
if [ -s test-sources.list ]; then
    javac -d build/test-classes -cp "build/classes:lib/*" @test-sources.list
    # -cp "build/classes:lib/*": 메인 클래스와 라이브러리를 클래스패스에 포함
    if [ $? -eq 0 ]; then
        echo "Test compilation successful!"
    else
        echo "Test compilation failed!"
        exit 1
    fi
else
    echo "No test source files found."
fi

# 임시 파일 정리
rm -f sources.list test-sources.list
# -f: 파일이 없어도 오류 없이 삭제

echo "Build completed successfully!"
```

**빌드 프로세스:**
1. **환경 준비**: 로그 디렉토리 생성, 기존 빌드 파일 정리
2. **소스 수집**: find 명령으로 모든 Java 소스 파일 목록 생성
3. **순차 컴파일**: 메인 소스 → 테스트 소스 순으로 의존성 고려
4. **오류 처리**: 각 단계별 성공/실패 확인 및 조기 종료
5. **정리**: 임시 파일 삭제로 깔끔한 환경 유지

---

### 2. clean.sh - 정리 스크립트

```bash
#!/bin/bash
echo "Cleaning build artifacts..."
# 정리 작업 시작 알림

rm -rf build/classes/*        # 컴파일된 메인 클래스 파일 삭제
rm -rf build/test-classes/*   # 컴파일된 테스트 클래스 파일 삭제  
rm -rf build/reports/*        # 생성된 리포트 파일 삭제
rm -rf logs/*.log             # 모든 로그 파일 삭제 (.log 확장자만)
rm -rf benchmarks/results/*   # 벤치마크 결과 파일 삭제

echo "Clean completed!"
```

**정리 대상:**
- **빌드 산출물**: 컴파일된 클래스 파일들
- **리포트**: 테스트 및 분석 결과 파일들
- **로그**: 실행 중 생성된 로그 파일들
- **벤치마크**: 성능 테스트 결과물들

---

### 3. run-traditional.sh - 전통적 서버 실행

```bash
#!/bin/bash
# Bash 셸 스크립트로 실행 지정

echo "Starting Traditional Server..."
# 사용자에게 Traditional Server 시작을 알리는 메시지 출력

java -cp build/classes:lib/* \
# java: JVM으로 Java 애플리케이션 실행
# -cp build/classes:lib/*: 클래스패스(CLASSPATH) 설정
#   - build/classes: 컴파일된 프로젝트 클래스 파일들이 위치한 디렉토리
#   - lib/*: 외부 라이브러리 JAR 파일들 (와일드카드로 모든 JAR 포함)
#   - 콜론(:)으로 구분하여 여러 경로 지정 (Windows에서는 세미콜론)
# \: 백슬래시는 줄 연속 문자 - 긴 명령어를 여러 줄로 나누어 가독성 향상
     -Djava.util.logging.config.file=config/logging.properties \
     # -D: JVM 시스템 프로퍼티 설정 옵션
     # java.util.logging.config.file: Java 기본 로깅 프레임워크 설정 파일 지정
     # config/logging.properties: 로깅 레벨, 핸들러, 포맷터 등이 정의된 파일
     -Dserver.config=config/traditional-server.properties \
     # server.config: 애플리케이션에서 사용할 커스텀 시스템 프로퍼티
     # config/traditional-server.properties: Traditional 서버 전용 설정 파일
     # 포트, 스레드 풀, 타임아웃 등의 서버 설정이 포함
     com.serverarch.traditional.TraditionalServer
     # 실행할 메인 클래스의 완전한 패키지명 (Fully Qualified Class Name)
     # 이 클래스의 main() 메서드가 프로그램 진입점이 됨
```

**Traditional Server 실행 특징:**
- **스레드 기반 아키텍처**: 요청당 스레드 할당 방식으로 동작
- **포트 8080**: HTTP 기본 포트 사용으로 웹 브라우저에서 직접 접근 가능
- **설정 파일 분리**: 서버별 독립적인 설정으로 유연한 구성 관리
- **로깅 통합**: 모든 서버가 동일한 로깅 정책 사용

---

### 4. run-hybrid.sh - 하이브리드 서버 실행

```bash
#!/bin/bash
echo "Starting Hybrid Server..."
# Hybrid Server 시작 알림

java -cp build/classes:lib/* \
# 동일한 클래스패스 설정 (컴파일된 클래스 + 라이브러리)
     -Djava.util.logging.config.file=config/logging.properties \
     # 공통 로깅 설정 사용 - 모든 서버에서 일관된 로그 형식
     -Dserver.config=config/hybrid-server.properties \
     # Hybrid 서버 전용 설정 파일 지정
     # 백프레셔, 비동기 타임아웃, 컨텍스트 스위치 임계값 등 포함
     com.serverarch.hybrid.HybridServer
     # Hybrid 서버 메인 클래스 - 적응형 동기/비동기 처리 구현
```

**Hybrid Server 실행 특징:**
- **적응형 처리**: 요청 특성에 따라 동기/비동기 처리 방식 자동 선택
- **포트 8081**: Traditional과 구분되는 독립적인 포트 사용
- **백프레셔 지원**: 과부하 상황에서 시스템 안정성 확보
- **성능 최적화**: 컨텍스트 스위치 최소화로 효율성 향상

---

### 5. run-eventloop.sh - 이벤트 루프 서버 실행

```bash
#!/bin/bash
echo "Starting Event Loop Server..."
# Event Loop Server 시작 알림

java -cp build/classes:lib/* \
# 동일한 클래스패스 구성
     -Djava.util.logging.config.file=config/logging.properties \
     # 통합 로깅 설정 적용
     -Dserver.config=config/eventloop-server.properties \
     # Event Loop 서버 전용 설정 파일
     # 이벤트 루프 스레드 수, 워커 스레드, 셀렉터 타임아웃 등 설정
     com.serverarch.eventloop.EventLoopServer
     # Event Loop 서버 메인 클래스 - 단일 스레드 이벤트 기반 처리
```

**Event Loop Server 실행 특징:**
- **단일 스레드 이벤트 루프**: 하나의 스레드로 모든 I/O 이벤트 처리
- **포트 8082**: 독립적인 포트로 다른 서버들과 동시 실행 가능
- **논블로킹 I/O**: 대량의 동시 연결을 효율적으로 처리
- **웹소켓 지원**: 실시간 양방향 통신 기능 제공

---

**서버 실행 스크립트 공통 분석:**

#### JVM 옵션 상세 설명
```bash
java -cp build/classes:lib/* \
```
- **java**: JVM(Java Virtual Machine) 실행 명령어
- **-cp (또는 -classpath)**: 클래스 로딩 경로 지정
    - `build/classes`: 소스 코드가 컴파일된 .class 파일들의 위치
    - `lib/*`: 외부 의존성 라이브러리들 (JAR 파일)
    - 콜론(:) 구분자로 여러 경로를 하나의 클래스패스로 결합

#### 시스템 프로퍼티 설정
```bash
-Djava.util.logging.config.file=config/logging.properties
```
- **-D**: JVM 시스템 프로퍼티 정의 옵션
- **java.util.logging.config.file**: JUL(Java Util Logging) 설정 파일 경로
- **목적**: 애플리케이션 전체의 로깅 동작 제어

```bash
-Dserver.config=config/[server-type]-server.properties
```
- **server.config**: 커스텀 시스템 프로퍼티 (애플리케이션에서 정의)
- **목적**: 각 서버 타입별 고유 설정을 런타임에 주입
- **장점**: 하드코딩 없이 외부 설정 파일로 서버 동작 제어

#### 메인 클래스 실행
```bash
com.serverarch.[type].TypeServer
```
- **패키지 구조**: com.serverarch 루트 패키지 하위에 서버 타입별 분리
- **클래스 명명**: [Type]Server 패턴으로 일관성 있는 구조
- **main() 메서드**: 각 클래스의 static main 메서드가 프로그램 진입점

#### 스크립트 실행 순서와 주의사항

1. **빌드 선행**: 반드시 `build.sh` 실행 후 서버 스크립트 실행
   ```bash
   ./scripts/build.sh  # 컴파일 완료 확인
   ```

2. **포트 충돌 방지**: 각 서버는 다른 포트 사용으로 동시 실행 가능
    - Traditional: 8080
    - Hybrid: 8081
    - Event Loop: 8082

3. **터미널 분리**: 각 서버를 별도 터미널에서 실행 권장
   ```bash
   # 터미널 1
   ./scripts/run-traditional.sh
   
   # 터미널 2  
   ./scripts/run-hybrid.sh
   
   # 터미널 3
   ./scripts/run-eventloop.sh
   ```

4. **종료 방법**: Ctrl+C로 각 서버 개별 종료 또는 pkill 사용
   ```bash
   # 모든 서버 일괄 종료
   pkill -f "com.serverarch"
   ```

#### 환경 변수와 JVM 튜닝

실제 운영 환경에서는 추가 JVM 옵션을 고려할 수 있습니다:

```bash
# 메모리 설정 예시
java -Xms512m -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -cp build/classes:lib/* \
     -Djava.util.logging.config.file=config/logging.properties \
     -Dserver.config=config/traditional-server.properties \
     com.serverarch.traditional.TraditionalServer
```

- **-Xms512m**: 초기 힙 메모리 512MB
- **-Xmx2g**: 최대 힙 메모리 2GB
- **-XX:+UseG1GC**: G1 가비지 컬렉터 사용
- **-XX:MaxGCPauseMillis=200**: GC 중단 시간 최대 200ms

---

### 6. run-benchmark.sh - 벤치마크 실행

```bash
#!/bin/bash
echo "Running benchmarks on all servers..."

# 모든 서버가 실행 중인지 확인
echo "Checking server availability..."

# Traditional Server 체크
curl -f http://localhost:8080/health >/dev/null 2>&1
# curl -f: HTTP 오류 시 종료 코드 0이 아닌 값 반환
# >/dev/null 2>&1: 표준 출력과 표준 에러를 모두 무시
if [ $? -ne 0 ]; then
# -ne: not equal (같지 않음)
    echo "Warning: Traditional Server (8080) not responding"
fi

# Hybrid Server 체크
curl -f http://localhost:8081/health >/dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "Warning: Hybrid Server (8081) not responding"
fi

# Event Loop Server 체크  
curl -f http://localhost:8082/health >/dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "Warning: Event Loop Server (8082) not responding"
fi

# 벤치마크 실행
java -cp build/classes:lib/* \
     -Dbenchmark.config=config/benchmark.properties \
     # 벤치마크 설정 파일 지정
     com.serverarch.benchmark.BenchmarkRunner
     # 벤치마크 실행 메인 클래스

echo "Benchmark completed! Check benchmarks/reports/ for results."
```

**벤치마크 프로세스:**
1. **서버 상태 확인**: 모든 서버가 실행 중인지 health 엔드포인트로 확인
2. **경고 출력**: 응답하지 않는 서버에 대한 경고 메시지
3. **벤치마크 실행**: 설정에 따라 모든 서버에 대해 성능 테스트 수행
4. **결과 안내**: 테스트 완료 후 결과 파일 위치 안내

---

## 🚀 실행 가이드

### 1. 프로젝트 빌드
```bash
./scripts/build.sh
```

### 2. 서버 실행 (각각 다른 터미널에서)
```bash
# Traditional Server (포트 8080)
./scripts/run-traditional.sh

# Hybrid Server (포트 8081)  
./scripts/run-hybrid.sh

# Event Loop Server (포트 8082)
./scripts/run-eventloop.sh
```

### 3. 벤치마크 실행
```bash
./scripts/run-benchmark.sh
```

### 4. 정리
```bash
./scripts/clean.sh
```

---

## 📊 성능 비교 분석

### Traditional Server
- **장점**: 구현 단순, 디버깅 용이, CPU 집약적 작업에 적합
- **단점**: 메모리 사용량 높음, 컨텍스트 스위치 오버헤드
- **적합한 시나리오**: 적은 동시 접속자, 복잡한 비즈니스 로직

### Hybrid Server
- **장점**: 적응형 처리, 백프레셔로 안정성 확보
- **단점**: 복잡한 구현, 임계값 튜닝 필요
- **적합한 시나리오**: 혼합 워크로드, 중간 규모 서비스

### Event Loop Server
- **장점**: 높은 동시성, 낮은 메모리 사용량, 확장성 우수
- **단점**: CPU 집약적 작업에 부적합, 복잡한 디버깅
- **적합한 시나리오**: 대량 동시 접속, I/O 집약적 작업

---

## 🔍 모니터링 및 트러블슈팅

### 로그 확인
```bash
# 실시간 로그 모니터링
tail -f logs/server-0.log

# 에러 로그 검색
grep "ERROR" logs/server-*.log
```

### 포트 사용 확인
```bash
# 포트 사용 상태 확인
netstat -tulpn | grep :808[0-2]

# 프로세스 종료
pkill -f "com.serverarch"
```

### 성능 모니터링
```bash
# JVM 메모리 사용량
jstat -gc [PID]

# 스레드 덤프
jstack [PID]
```

이 가이드를 통해 JavaServerArchitectures 프로젝트의 모든 설정과 스크립트를 완전히 이해하고 효과적으로 활용할 수 있습니다.