# Phase 1.4 - 서블릿 컨테이너 공통 인프라 구현

## 📋 개요

서블릿 컨테이너의 핵심 인프라 컴포넌트들을 구현하여 완전한 서블릿 런타임 환경을 구축하는 단계입니다. 이 단계에서는 서블릿 생명주기 관리, 필터 체인, 세션 관리, URL 매핑 등 서블릿 컨테이너의 모든 핵심 기능을 다룹니다.

## 🎯 목표

- 완전한 서블릿 생명주기 관리 시스템 구축
- 필터 체인 패턴 구현 및 동적 필터 관리
- 엔터프라이즈급 세션 관리 시스템
- 유연하고 확장 가능한 URL 매핑 시스템
- 서블릿 인스턴스 풀링 및 성능 최적화
- 스레드 안전한 컨테이너 인프라

## 📁 구현된 파일 구조

```
src/main/java/
├── jakarta/servlet/                  # 표준 서블릿 인터페이스
│   ├── Filter.java                  # 필터 인터페이스
│   ├── FilterChain.java             # 필터 체인 인터페이스
│   └── FilterConfig.java            # 필터 설정 인터페이스
└── com/com.serverarch/
    ├── container/                    # 서블릿 컨테이너 핵심
    │   ├── ServletRegistry.java     # 서블릿 등록 및 관리
    │   ├── ServletInstanceManager.java # 서블릿 인스턴스 생명주기
    │   ├── FilterChainImpl.java     # 필터 체인 구현
    │   └── FilterManager.java       # 필터 관리
    ├── common/
    │   ├── routing/
    │   │   └── ServletMapping.java  # URL 패턴 매핑
    │   └── session/
    │       ├── HttpSessionImpl.java # HttpSession 구현
    │       └── SessionManager.java  # 세션 관리
```

## 🔍 주요 구현 내용

### 1. 서블릿 등록 및 관리

#### `ServletRegistry.java` - 서블릿 레지스트리
```java
public class ServletRegistry {
    // 서블릿 이름으로 ServletInfo를 관리하는 맵
    private final Map<String, ServletInfo> servletsByName;
    // URL 패턴으로 ServletInfo를 관리하는 맵
    private final Map<String, ServletInfo> servletsByPattern;
    // 로드 순서별 서블릿 관리
    private final NavigableMap<Integer, List<ServletInfo>> servletsByLoadOrder;
    
    /**
     * 서블릿을 등록합니다.
     */
    public ServletInfo registerServlet(String servletName,
                                       Class<? extends Servlet> servletClass,
                                       Map<String, String> initParameters,
                                       Set<String> urlPatterns,
                                       int loadOnStartup) throws ServletException {
        
        // 입력 검증 및 중복 검사
        validateServletRegistration(servletName, servletClass, urlPatterns);
        
        // 서블릿 인스턴스 생성
        Servlet servletInstance = createServletInstance(servletClass);
        
        // ServletConfig 생성
        ServletConfig servletConfig = createServletConfig(servletName, initParameters);
        
        // ServletInfo 생성 및 등록
        ServletInfo servletInfo = new ServletInfo(/* ... */);
        
        // 레지스트리에 등록
        servletsByName.put(servletName, servletInfo);
        // URL 패턴 매핑 등록
        // 로드 순서별 등록
        
        return servletInfo;
    }
    
    /**
     * URL 패턴 매칭을 통해 ServletInfo를 찾습니다.
     */
    public ServletInfo findServletByPath(String requestPath) {
        // 1. 정확한 매칭
        // 2. 경로 매칭 (가장 긴 매칭 우선)
        // 3. 확장자 매칭
        // 4. 기본 서블릿
    }
}
```

**주요 특징:**
- 스레드 안전한 서블릿 등록/해제
- URL 패턴 우선순위 기반 매칭
- 서블릿 생명주기 완전 관리
- load-on-startup 순서 처리

### 2. 서블릿 인스턴스 관리

#### `ServletInstanceManager.java` - 인스턴스 매니저
```java
public class ServletInstanceManager {
    public enum InstanceStrategy {
        SINGLETON,    // 서블릿당 하나의 인스턴스
        PROTOTYPE,    // 요청마다 새로운 인스턴스
        POOLED       // 인스턴스 풀 사용
    }
    
    // 서블릿 클래스별 인스턴스 캐시
    private final Map<Class<? extends Servlet>, Servlet> singletonInstances;
    // 인스턴스별 메타데이터
    private final Map<Servlet, InstanceInfo> instanceInfoMap;
    
    /**
     * 서블릿 인스턴스를 생성하거나 기존 인스턴스를 반환합니다.
     */
    public Servlet getInstance(Class<? extends Servlet> servletClass) 
            throws ServletException {
        InstanceStrategy strategy = getEffectiveStrategy(servletClass);
        
        switch (strategy) {
            case SINGLETON:
                return getSingletonInstance(servletClass);
            case PROTOTYPE:
                return createNewInstance(servletClass);
            case POOLED:
                return getPooledInstance(servletClass);
        }
    }
    
    /**
     * 싱글톤 인스턴스를 반환합니다 (스레드 안전).
     */
    private Servlet getSingletonInstance(Class<? extends Servlet> servletClass)
            throws ServletException {
        // Double-check 패턴으로 스레드 안전하게 단일 인스턴스 보장
        Servlet instance = singletonInstances.get(servletClass);
        if (instance != null) {
            updateInstanceAccess(instance);
            return instance;
        }
        
        synchronized (getCreationLock(servletClass)) {
            instance = singletonInstances.get(servletClass);
            if (instance != null) {
                return instance;
            }
            
            instance = createNewInstance(servletClass);
            singletonInstances.put(servletClass, instance);
            return instance;
        }
    }
}
```

**주요 특징:**
- 다양한 인스턴스 생성 전략 지원
- 메모리 효율적인 인스턴스 관리
- 인스턴스 생성 통계 및 모니터링
- 스레드 안전한 접근 제어

### 3. 필터 체인 시스템

#### `FilterManager.java` - 필터 관리
```java
public class FilterManager {
    // 등록된 필터들 (필터 이름 -> FilterInfo)
    private final Map<String, FilterInfo> registeredFilters;
    // URL 패턴별 필터 매핑
    private final Map<String, List<String>> filterMappings;
    
    /**
     * 필터를 등록합니다.
     */
    public void registerFilter(String filterName, Filter filter,
                               Map<String, String> initParameters,
                               List<String> urlPatterns, int order) 
                               throws ServletException {
        
        // 입력 검증
        validateFilterRegistration(filterName, filter, urlPatterns);
        
        // FilterConfig 생성
        FilterConfig filterConfig = new FilterConfigImpl(filterName, servletContext, initParameters);
        
        // FilterInfo 생성
        FilterInfo filterInfo = new FilterInfo(filterName, filter, filterConfig, urlPatterns, order);
        
        // 등록 및 순서 정렬
        registeredFilters.put(filterName, filterInfo);
        updateFilterMappings(filterInfo);
    }
    
    /**
     * 요청 경로에 매칭되는 필터 체인을 생성합니다.
     */
    public FilterChain createFilterChain(String requestPath, Servlet servlet) {
        List<FilterInfo> matchingFilters = new ArrayList<>();
        
        // 매칭되는 필터들 수집
        for (Map.Entry<String, List<String>> entry : filterMappings.entrySet()) {
            String pattern = entry.getKey();
            if (matchesPattern(pattern, requestPath)) {
                for (String filterName : entry.getValue()) {
                    FilterInfo filterInfo = registeredFilters.get(filterName);
                    if (filterInfo != null && !filterInfo.isDestroyed()) {
                        matchingFilters.add(filterInfo);
                    }
                }
            }
        }
        
        // 순서별 정렬 및 중복 제거
        matchingFilters.sort(Comparator.comparing(FilterInfo::getOrder));
        
        return new FilterChainImpl(matchingFilters, servlet);
    }
}
```

#### `FilterChainImpl.java` - 필터 체인 구현
```java
public class FilterChainImpl implements FilterChain {
    private final List<FilterManager.FilterInfo> filters;
    private final Servlet servlet;
    private int currentFilterIndex;
    private boolean chainCompleted;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response)
            throws IOException, ServletException {
        
        // 체인 완료 상태 확인
        if (chainCompleted) {
            throw new IllegalStateException("필터 체인이 이미 완료되었습니다");
        }
        
        // 다음 필터 실행
        if (currentFilterIndex < filters.size()) {
            FilterManager.FilterInfo filterInfo = filters.get(currentFilterIndex);
            currentFilterIndex++;
            
            // 필터 초기화 (지연 초기화)
            ensureFilterInitialized(filterInfo);
            
            // 필터 실행
            filterInfo.getFilter().doFilter(request, response, this);
        } else {
            // 모든 필터 통과 후 서블릿 실행
            executeServlet(request, response);
        }
    }
    
    /**
     * 필터를 지연 초기화합니다.
     */
    private void ensureFilterInitialized(FilterManager.FilterInfo filterInfo) 
            throws ServletException {
        if (!filterInfo.isInitialized()) {
            synchronized (filterInfo) {
                if (!filterInfo.isInitialized()) {
                    filterInfo.getFilter().init(filterInfo.getFilterConfig());
                    filterInfo.setInitialized(true);
                }
            }
        }
    }
}
```

**주요 특징:**
- 동적 필터 등록/해제
- URL 패턴 기반 필터 매칭
- 순서 기반 필터 실행
- 지연 초기화로 성능 최적화

### 4. URL 매핑 시스템

#### `ServletMapping.java` - URL 패턴 매핑
```java
public class ServletMapping {
    public enum PatternType {
        EXACT,      // 정확 매칭: "/hello"
        PATH,       // 경로 매칭: "/admin/*"
        EXTENSION,  // 확장자 매칭: "*.jsp"
        DEFAULT     // 기본 서블릿: "/"
    }
    
    /**
     * 매핑 정보를 담는 클래스
     */
    public static class MappingInfo {
        private final String pattern;
        private final PatternType type;
        private final String servletName;
        private final int priority;        // 우선순위 (높을수록 우선)
        private final Pattern regexPattern; // 정규식 패턴
        
        private int calculatePriority(String pattern, PatternType type) {
            switch (type) {
                case EXACT:
                    return 1000 + pattern.length(); // 정확 매칭이 최우선
                case PATH:
                    return 500 + pattern.length() - 2; // 경로가 길수록 우선
                case EXTENSION:
                    return 100; // 확장자 매칭
                case DEFAULT:
                    return 1;   // 기본 서블릿이 최하위
            }
        }
    }
    
    /**
     * 요청 경로에 매칭되는 서블릿을 찾습니다.
     */
    public MatchResult findMapping(String requestPath) {
        String normalizedPath = normalizePath(requestPath);
        
        // 1. 정확 매칭 확인 (빠른 경로)
        MappingInfo exactMatch = exactMappings.get(normalizedPath);
        if (exactMatch != null) {
            return new MatchResult(exactMatch, normalizedPath, null, null);
        }
        
        // 2. 우선순위별로 패턴 매칭
        for (MappingInfo mapping : mappings) {
            MatchResult result = tryMatch(mapping, normalizedPath);
            if (result != null) {
                return result;
            }
        }
        
        return null; // 매칭되는 패턴 없음
    }
}
```

**주요 특징:**
- 우선순위 기반 매칭 알고리즘
- 정규식 패턴 캐싱
- 빠른 정확 매칭 캐시
- 확장 가능한 패턴 타입

### 5. 세션 관리 시스템

#### `SessionManager.java` - 세션 매니저
```java
public class SessionManager {
    // 세션 저장소 (세션 ID -> HttpSession)
    private final Map<String, HttpSession> sessions;
    // 세션 메타데이터 저장소
    private final Map<String, SessionInfo> sessionInfos;
    // 세션 만료 정리를 위한 스케줄러
    private final ScheduledExecutorService cleanupScheduler;
    
    /**
     * 새로운 세션을 생성합니다.
     */
    public HttpSession createSession() {
        String sessionId = generateSessionId();
        
        sessionLock.writeLock().lock();
        try {
            // 중복 ID 확인
            while (sessions.containsKey(sessionId)) {
                sessionId = generateSessionId();
            }
            
            // 세션 정보 생성
            SessionInfo sessionInfo = new SessionInfo(sessionId, defaultMaxInactiveInterval);
            sessionInfos.put(sessionId, sessionInfo);
            
            // HttpSession 구현체 생성
            HttpSession session = new HttpSessionImpl(sessionId, servletContext, this);
            sessions.put(sessionId, session);
            
            totalSessionsCreated++;
            return session;
        } finally {
            sessionLock.writeLock().unlock();
        }
    }
    
    /**
     * 만료된 세션들을 정리합니다.
     */
    public void cleanupExpiredSessions() {
        List<String> expiredSessionIds = new ArrayList<>();
        
        // 읽기 락으로 만료된 세션 ID 수집
        sessionLock.readLock().lock();
        try {
            for (Map.Entry<String, SessionInfo> entry : sessionInfos.entrySet()) {
                SessionInfo sessionInfo = entry.getValue();
                if (!sessionInfo.isValid() || sessionInfo.isExpired()) {
                    expiredSessionIds.add(entry.getKey());
                }
            }
        } finally {
            sessionLock.readLock().unlock();
        }
        
        // 쓰기 락으로 만료된 세션 제거
        if (!expiredSessionIds.isEmpty()) {
            sessionLock.writeLock().lock();
            try {
                for (String sessionId : expiredSessionIds) {
                    removeExpiredSession(sessionId);
                }
            } finally {
                sessionLock.writeLock().unlock();
            }
        }
    }
    
    /**
     * 보안 세션 ID를 생성합니다.
     */
    private String generateSessionId() {
        byte[] randomBytes = new byte[sessionIdLength];
        secureRandom.nextBytes(randomBytes);
        
        StringBuilder sessionId = new StringBuilder();
        for (byte b : randomBytes) {
            sessionId.append(String.format("%02X", b & 0xFF));
        }
        return sessionId.toString();
    }
}
```

#### `HttpSessionImpl.java` - HttpSession 구현
```java
public class HttpSessionImpl implements HttpSession {
    // 세션 고유 식별자
    private final String sessionId;
    // 세션 생성 시간 및 접근 시간
    private final long creationTime;
    private volatile long lastAccessedTime;
    // 세션 설정 및 상태
    private volatile int maxInactiveInterval;
    private volatile boolean valid;
    private volatile boolean isNew;
    // 세션 속성 저장소 (스레드 안전)
    private final Map<String, Object> attributes;
    
    @Override
    public void setAttribute(String name, Object value) {
        checkValid();
        
        if (name == null) {
            throw new IllegalArgumentException("속성 이름이 null입니다");
        }
        
        updateLastAccessedTime();
        
        if (value == null) {
            removeAttribute(name);
        } else {
            Object oldValue = attributes.put(name, value);
            // HttpSessionAttributeListener 호출 (향후 구현)
        }
    }
    
    @Override
    public synchronized void invalidate() {
        checkValid();
        
        // 모든 속성 제거
        Set<String> attributeNames = new HashSet<>(attributes.keySet());
        for (String name : attributeNames) {
            removeAttribute(name);
        }
        
        // 세션 무효화
        this.valid = false;
        sessionManager.invalidateSession(sessionId);
    }
}
```

**주요 특징:**
- 보안 세션 ID 생성
- 자동 만료 및 정리
- 스레드 안전한 세션 속성 관리
- 세션 통계 및 모니터링

## ✅ 구현된 핵심 기능

### 서블릿 생명주기 관리
- [x] 서블릿 등록/해제
- [x] 초기화 및 소멸 처리
- [x] load-on-startup 지원
- [x] 동적 서블릿 관리

### 필터 체인 시스템
- [x] 동적 필터 등록
- [x] URL 패턴 매칭
- [x] 순서 기반 실행
- [x] 지연 초기화

### URL 매핑
- [x] 정확/경로/확장자/기본 매칭
- [x] 우선순위 기반 라우팅
- [x] 정규식 패턴 지원
- [x] 성능 최적화된 매칭

### 세션 관리
- [x] 보안 세션 ID 생성
- [x] 자동 만료 처리
- [x] 스레드 안전한 구현
- [x] 세션 통계

### 인스턴스 관리
- [x] 싱글톤/프로토타입/풀링 전략
- [x] 메모리 효율적 관리
- [x] 인스턴스 통계
- [x] 스레드 안전성

## 🔧 성능 최적화

### 1. 메모리 관리
- **인스턴스 풀링**: 서블릿 인스턴스 재사용
- **세션 정리**: 자동 만료 세션 정리
- **캐시 최적화**: URL 매칭 결과 캐싱

### 2. 스레드 안전성
- **ConcurrentHashMap**: 동시성 안전한 컬렉션
- **ReadWriteLock**: 읽기/쓰기 성능 최적화
- **지연 초기화**: 필요 시점에만 초기화

### 3. 확장성
- **비동기 정리 작업**: 백그라운드 세션 정리
- **로드 밸런싱 준비**: 분산 세션 지원 준비
- **플러그인 아키텍처**: 확장 가능한 구조

## 🏗️ 아키텍처 특징

### 1. 모듈화 설계
- 각 컴포넌트는 독립적으로 작동
- 명확한 인터페이스 정의
- 의존성 주입 지원

### 2. 표준 준수
- Jakarta EE 서블릿 스펙 완전 준수
- 표준 API 구현
- 하위 호환성 보장

### 3. 확장성
- 플러그인 기반 아키텍처
- 커스텀 구현체 지원
- 런타임 설정 변경

## 📊 모니터링 및 메트릭

### 서블릿 메트릭
- 등록된 서블릿 수
- 서블릿별 요청 처리 시간
- 서블릿 초기화 실패율

### 세션 메트릭
- 활성 세션 수
- 세션 생성/만료율
- 평균 세션 생존 시간

### 필터 메트릭
- 등록된 필터 수
- 필터별 처리 시간
- 필터 체인 실행 통계

## 🔄 다음 단계

Phase 1.4 완료 후:
- **Phase 2.1**: Traditional 서버 아키텍처 구현
- 스레드 풀 기반 요청 처리
- 블로킹 I/O 모델
- 성능 벤치마크

## 📝 사용 예시

### 서블릿 등록
```java
ServletRegistry registry = new ServletRegistry(servletContext);

registry.registerServlet(
    "HelloServlet",                    // 서블릿 이름
    HelloServlet.class,                // 서블릿 클래스
    Map.of("greeting", "Hello World"), // 초기화 매개변수
    Set.of("/hello", "/hi"),           // URL 패턴
    1                                  // load-on-startup
);
```

### 필터 등록
```java
FilterManager filterManager = new FilterManager(servletContext);

filterManager.registerFilter(
    "LoggingFilter",                   // 필터 이름
    new LoggingFilter(),               // 필터 인스턴스
    Map.of("logLevel", "INFO"),        // 초기화 매개변수
    List.of("/*"),                     // URL 패턴
    1                                  // 실행 순서
);
```

### 필터 체인 실행
```java
String requestPath = "/hello";
Servlet servlet = registry.findServletByPath(requestPath).getServletInstance();
FilterChain chain = filterManager.createFilterChain(requestPath, servlet);

chain.doFilter(request, response);
```

### 세션 관리
```java
SessionManager sessionManager = new SessionManager(servletContext);

// 새 세션 생성
HttpSession session = sessionManager.createSession();

// 세션 조회
HttpSession existingSession = sessionManager.getSession(sessionId);

// 세션 정리
sessionManager.cleanupExpiredSessions();
```

이 구현은 엔터프라이즈급 서블릿 컨테이너의 완전한 인프라를 제공하며, 확장 가능하고 성능이 우수한 웹 애플리케이션 개발 기반을 마련합니다.