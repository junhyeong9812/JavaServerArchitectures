# Phase 1.1 - 프로젝트 구조 설정

## 📋 개요

Java Server Architectures 프로젝트의 기본 디렉터리 구조와 설정 파일들을 구성하는 단계입니다. 이 단계에서는 전체 프로젝트의 뼈대를 구축하여 향후 개발 작업의 기반을 마련합니다.

## 🎯 목표

- 표준 Java 프로젝트 구조 생성
- 개발 환경 설정 파일 구성
- 빌드 및 배포 스크립트 준비
- 문서화 디렉터리 구성
- 모니터링 및 벤치마크 환경 준비

## 📁 생성된 디렉터리 구조

```
JavaServerArchitectures/
├── src/                              # 소스 코드 루트
│   └── main/java/                    # 메인 Java 소스
│       ├── com/com.serverarch/           # 메인 패키지
│       │   ├── traditional/          # 전통적 서버 구현
│       │   ├── eventloop/            # 이벤트 루프 서버 구현
│       │   ├── hybrid/               # 하이브리드 서버 구현
│       │   ├── common/               # 공통 컴포넌트
│       │   │   ├── http/             # HTTP 프로토콜 처리
│       │   │   ├── io/               # I/O 유틸리티
│       │   │   ├── routing/          # 라우팅 시스템
│       │   │   ├── security/         # 보안 컴포넌트
│       │   │   ├── session/          # 세션 관리
│       │   │   └── utils/            # 공통 유틸리티
│       │   ├── container/            # 서블릿 컨테이너
│       │   ├── benchmark/            # 성능 측정 도구
│       │   ├── monitoring/           # 모니터링 시스템
│       │   └── demo/                 # 데모 애플리케이션
│       └── jakarta/servlet/          # 서블릿 API 구현
├── test/java/                        # 테스트 코드
│   └── com/com.serverarch/              # 테스트 패키지 구조
│       ├── common/                   # 공통 컴포넌트 테스트
│       ├── traditional/              # 전통적 서버 테스트
│       ├── eventloop/                # 이벤트 루프 서버 테스트
│       ├── hybrid/                   # 하이브리드 서버 테스트
│       ├── integration/              # 통합 테스트
│       └── performance/              # 성능 테스트
├── config/                           # 설정 파일들
│   ├── traditional-server.properties # 전통적 서버 설정
│   ├── eventloop-server.properties  # 이벤트 루프 서버 설정
│   ├── hybrid-server.properties     # 하이브리드 서버 설정
│   ├── logging.properties           # 로깅 설정
│   └── benchmark.properties         # 벤치마크 설정
├── scripts/                         # 실행 스크립트들
│   ├── build.sh                     # 빌드 스크립트
│   ├── clean.sh                     # 정리 스크립트
│   ├── run-traditional.sh           # 전통적 서버 실행
│   ├── run-eventloop.sh             # 이벤트 루프 서버 실행
│   ├── run-hybrid.sh                # 하이브리드 서버 실행
│   └── run-benchmark.sh             # 벤치마크 실행
├── docs/                            # 문서화
│   ├── architecture/                # 아키텍처 문서
│   ├── api/                         # API 문서
│   ├── performance/                 # 성능 분석 문서
│   └── common/                      # 공통 문서
├── benchmarks/                      # 벤치마크 관련
│   ├── results/                     # 벤치마크 결과
│   ├── reports/                     # 성능 리포트
│   └── charts/                      # 성능 차트
├── build/                           # 빌드 출력
│   ├── classes/                     # 컴파일된 클래스
│   ├── test-classes/                # 테스트 클래스
│   └── reports/                     # 빌드 리포트
├── lib/                             # 외부 라이브러리
├── logs/                            # 로그 파일들
├── README.md                        # 프로젝트 개요
├── PROCESS.md                       # 개발 프로세스 문서
└── .gitignore                       # Git 무시 파일 목록
```

## 🔧 설정 파일들

### 1. 서버 설정 파일들

#### `config/traditional-server.properties`
```properties
# 전통적 서버 설정
server.port=8080
server.thread.pool.size=200
server.thread.pool.max=500
server.connection.timeout=30000
server.socket.backlog=100
```

#### `config/eventloop-server.properties`
```properties
# 이벤트 루프 서버 설정
server.port=8081
server.eventloop.threads=4
server.selector.timeout=1000
server.buffer.size=8192
server.channel.keep.alive=true
```

#### `config/hybrid-server.properties`
```properties
# 하이브리드 서버 설정
server.port=8082
server.io.threads=4
server.worker.threads=100
server.hybrid.mode=adaptive
server.load.balancing=round_robin
```

### 2. 로깅 설정

#### `config/logging.properties`
```properties
# 로깅 설정
handlers=java.util.logging.ConsoleHandler,java.util.logging.FileHandler
.level=INFO

java.util.logging.ConsoleHandler.level=INFO
java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter

java.util.logging.FileHandler.level=ALL
java.util.logging.FileHandler.pattern=logs/server-%g.log
java.util.logging.FileHandler.limit=10485760
java.util.logging.FileHandler.count=5
java.util.logging.FileHandler.formatter=java.util.logging.SimpleFormatter
```

### 3. 벤치마크 설정

#### `config/benchmark.properties`
```properties
# 벤치마크 설정
benchmark.concurrent.users=100,500,1000,2000
benchmark.duration.seconds=60
benchmark.warmup.seconds=10
benchmark.request.timeout=5000
benchmark.output.format=json,csv
benchmark.charts.enabled=true
```

## 🚀 실행 스크립트들

### 1. 빌드 스크립트 (`scripts/build.sh`)
```bash
#!/bin/bash
# Java 소스 컴파일 및 빌드
echo "Building Java Server Architectures..."
mkdir -p build/classes
find src -name "*.java" | xargs javac -d build/classes -cp lib/*
echo "Build completed."
```

### 2. 서버 실행 스크립트들
각 서버 아키텍처별로 독립적인 실행 스크립트 제공:
- `run-traditional.sh`: 전통적 스레드 기반 서버
- `run-eventloop.sh`: 이벤트 루프 기반 서버
- `run-hybrid.sh`: 하이브리드 서버

### 3. 벤치마크 스크립트 (`scripts/run-benchmark.sh`)
```bash
#!/bin/bash
# 성능 벤치마크 실행
echo "Running performance benchmarks..."
java -cp build/classes com.com.serverarch.benchmark.BenchmarkRunner
echo "Benchmark completed. Results saved to benchmarks/results/"
```

## 📚 문서화 구조

### 1. 아키텍처 문서 (`docs/architecture/`)
- 각 서버 아키텍처의 설계 문서
- UML 다이어그램
- 성능 특성 분석

### 2. API 문서 (`docs/api/`)
- JavaDoc 생성 결과
- REST API 명세 (해당시)
- 사용법 가이드

### 3. 성능 문서 (`docs/performance/`)
- 벤치마크 결과 분석
- 성능 튜닝 가이드
- 모니터링 지표 설명

## ✅ 완료 체크리스트

- [x] 디렉터리 구조 생성
- [x] 설정 파일 템플릿 작성
- [x] 빌드 스크립트 구성
- [x] 실행 스크립트 준비
- [x] 문서화 구조 설정
- [x] Git 저장소 초기화
- [x] .gitignore 파일 구성

## 🔄 다음 단계

Phase 1.1 완료 후 다음 단계로 진행:
- **Phase 1.2.1**: 핵심 서블릿 인터페이스 구현
- **Phase 1.2.2**: HTTP 요청/응답 API 구현

## 📝 참고사항

### 개발 환경 요구사항
- Java 11 이상
- Git
- 텍스트 에디터 또는 IDE
- Linux/macOS/Windows 환경

### 프로젝트 컨벤션
- 패키지명: `com.com.serverarch.*`
- 코딩 스타일: Java 표준 컨벤션
- 문서화: JavaDoc 필수
- 테스트: JUnit 기반 단위 테스트

이 구조는 확장 가능하고 유지보수가 용이한 프로젝트 기반을 제공합니다.