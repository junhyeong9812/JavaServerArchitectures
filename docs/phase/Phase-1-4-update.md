# Phase 1.4 - 서블릿 컨테이너 공통 인프라 수정 사항 요약

## 🔧 현재 상태 분석

### ✅ 이미 완성된 파일들
- `ServletRegistry.java` - 완전 구현됨
- `ServletInstanceManager.java` - 완전 구현됨
- `ServletMapping.java` - 완전 구현됨
- `SessionManager.java` - 완전 구현됨
- `HttpSessionImpl.java` - 완전 구현됨 (필드/생성자 포함)

### ❌ 수정이 필요한 파일들
- `FilterChainImpl.java` - 인터페이스 분리 필요
- 누락된 인터페이스 파일들 생성 필요

## 📝 수정 작업 리스트

### 1. 새로 생성해야 할 인터페이스 파일들

#### A. `src/main/java/jakarta/servlet/Filter.java`
```java
// 위치: src/main/java/jakarta/servlet/Filter.java
// 내용: 아티팩트 "filter_interface" 참조
```

#### B. `src/main/java/jakarta/servlet/FilterChain.java`
```java
// 위치: src/main/java/jakarta/servlet/FilterChain.java  
// 내용: 아티팩트 "filter_chain_interface" 참조
```

#### C. `src/main/java/jakarta/servlet/FilterConfig.java`
```java
// 위치: src/main/java/jakarta/servlet/FilterConfig.java
// 내용: 아티팩트 "filter_config_interface" 참조
```

### 2. 새로 생성해야 할 클래스 파일

#### D. `src/main/java/com/serverarch/container/FilterManager.java`
```java
// 위치: src/main/java/com/serverarch/container/FilterManager.java
// 내용: 아티팩트 "filter_manager" 참조
```

### 3. 수정해야 할 기존 파일

#### E. `src/main/java/com/serverarch/container/FilterChainImpl.java`
**현재 문제점:**
- 파일 내부에 Filter, FilterConfig, FilterManager 인터페이스/클래스가 모두 포함됨
- 단일 책임 원칙 위반
- 인터페이스와 구현이 분리되지 않음

**수정 방법:**
```java
// 기존 파일에서 제거해야 할 부분들:
// 1. interface Filter { ... }
// 2. interface FilterConfig { ... }  
// 3. class FilterConfigImpl implements FilterConfig { ... }
// 4. class FilterManager { ... }

// 새로 교체할 내용:
// 아티팩트 "filter_chain_impl_updated" 전체 내용으로 교체
```

## 🚀 구체적인 수정 단계

### Step 1: 디렉터리 준비
```bash
# Jakarta 서블릿 인터페이스용 디렉터리 생성
mkdir -p src/main/java/jakarta/servlet
```

### Step 2: 인터페이스 파일들 생성
1. `Filter.java` 생성 - 아티팩트 내용 복사
2. `FilterChain.java` 생성 - 아티팩트 내용 복사
3. `FilterConfig.java` 생성 - 아티팩트 내용 복사

### Step 3: FilterManager 클래스 생성
```bash
# FilterManager.java 파일 생성
# 아티팩트 "filter_manager" 내용 전체 복사
```

### Step 4: FilterChainImpl.java 수정
```java
// 현재 파일 내용을 아티팩트 "filter_chain_impl_updated" 내용으로 완전 교체
```

## 📋 수정 후 파일 구조

```
src/main/java/
├── jakarta/servlet/
│   ├── Filter.java                    # 새로 생성
│   ├── FilterChain.java               # 새로 생성  
│   └── FilterConfig.java              # 새로 생성
└── com/serverarch/
    ├── container/
    │   ├── FilterChainImpl.java       # 수정 필요
    │   ├── FilterManager.java         # 새로 생성
    │   ├── ServletInstanceManager.java # 완성됨
    │   └── ServletRegistry.java       # 완성됨
    ├── common/
    │   ├── routing/
    │   │   └── ServletMapping.java    # 완성됨
    │   └── session/
    │       ├── HttpSessionImpl.java   # 완성됨
    │       └── SessionManager.java    # 완성됨
```

## ⚡ 주요 개선사항

### 1. 아키텍처 개선
- **인터페이스 분리**: Jakarta EE 표준 구조 준수
- **단일 책임**: 각 클래스가 하나의 책임만 담당
- **의존성 명확화**: 인터페이스와 구현체 분리

### 2. 코드 품질 향상
- **표준 준수**: Jakarta EE 서블릿 스펙 구조
- **재사용성**: 독립적인 인터페이스로 재사용 가능
- **테스트 용이성**: Mock 객체 생성 용이

### 3. 성능 최적화
- **지연 초기화**: 필터는 실제 사용 시점에 초기화
- **효율적인 매칭**: URL 패턴 매칭 최적화
- **메모리 관리**: 적절한 리소스 해제

## 🔍 검증 방법

### 1. 컴파일 검증
```bash
# 모든 Java 파일이 정상 컴파일되는지 확인
javac -cp . src/main/java/com/serverarch/**/*.java src/main/java/jakarta/servlet/*.java
```

### 2. 의존성 검증
- FilterChainImpl이 FilterManager.FilterInfo 사용
- 모든 인터페이스가 올바르게 임포트됨
- 순환 의존성 없음

### 3. 기능 검증
- 필터 체인 생성 및 실행
- URL 패턴 매칭
- 세션 관리 기능

## ⚠️ 주의사항

1. **패키지 구조**: Jakarta 인터페이스는 표준 패키지에 위치
2. **임포트 문**: 기존 임포트 문들 확인 및 수정
3. **컴파일 순서**: 인터페이스 먼저 컴파일 후 구현체 컴파일
4. **테스트 코드**: 기존 테스트 코드가 있다면 수정 필요

## 🎯 완료 후 상태

Phase 1.4 완료 시 다음이 달성됩니다:
- ✅ 완전한 필터 체인 시스템
- ✅ 표준 준수 인터페이스 구조
- ✅ 확장 가능한 필터 관리 시스템
- ✅ 스레드 안전한 세션 관리
- ✅ 유연한 URL 매핑 시스템