package com.serverarch.container;

// Jakarta Servlet API 임포트
// jakarta.servlet: Jakarta EE의 서블릿 API 패키지
// Filter: HTTP 요청/응답을 가로채서 처리하는 필터 인터페이스
// FilterChain: 여러 필터를 연결하는 체인 인터페이스
// FilterConfig: 필터 초기화 정보를 담는 인터페이스
// ServletContext: 웹 애플리케이션 전체 컨텍스트 정보 인터페이스
// ServletException: 서블릿 처리 중 발생하는 예외 클래스
import jakarta.servlet.*;

// Java 컬렉션 프레임워크와 기타 유틸리티
// util.*: 컬렉션, 유틸리티 클래스들의 패키지
// ArrayList: 동적 배열 구현 클래스
// Collection: 컬렉션 최상위 인터페이스
// Collections: 컬렉션 유틸리티 메서드 제공 클래스
// Comparator: 객체 비교를 위한 함수형 인터페이스
// Enumeration: 열거형 인터페이스 (Iterator의 구버전)
// HashMap: 해시테이블 기반 맵 구현 클래스
// List: 순서가 있는 컬렉션 인터페이스
// Map: 키-값 쌍을 저장하는 컬렉션 인터페이스
import java.util.*;

// 동시성 관련 컬렉션
// concurrent.ConcurrentHashMap: 스레드 안전한 해시맵 구현 클래스
import java.util.concurrent.ConcurrentHashMap;

/**
 * 애플리케이션의 모든 필터를 관리하고 적절한 필터 체인을 생성하는 매니저 클래스입니다.
 *
 * FilterManager는 다음과 같은 책임을 가집니다:
 * - 필터 등록 및 해제
 * - URL 패턴별 필터 매핑 관리
 * - 요청에 맞는 필터 체인 동적 생성
 * - 필터 생명주기 관리
 * - 필터 초기화 및 정리
 *
 * 필터 매칭 규칙:
 * 1. 정확 매칭: "/admin/login"
 * 2. 경로 매칭: "/admin/*", "/api/*"
 * 3. 확장자 매칭: "*.jsp", "*.html"
 * 4. 전역 매칭: "/*"
 */
public class FilterManager {

    /**
     * 필터 정보를 담는 내부 클래스입니다.
     * 필터 인스턴스와 관련 메타데이터를 포함합니다.
     *
     * static: 외부 클래스의 인스턴스 없이도 생성 가능한 정적 중첩 클래스
     */
    public static class FilterInfo {
        // 필터 이름을 저장하는 필드
        // final: 생성 후 변경 불가능한 불변 필드
        // String: 자바의 문자열 클래스
        private final String filterName;

        // 필터 인스턴스를 저장하는 필드
        private final Filter filter;

        // 필터 설정 정보를 저장하는 필드
        private final FilterConfig filterConfig;

        // URL 패턴 목록을 저장하는 필드
        // List<String>: 문자열을 요소로 하는 리스트 제네릭 타입
        private final List<String> urlPatterns;

        // 필터 실행 순서를 저장하는 필드
        // int: 32비트 정수 기본 타입
        private final int order;

        // 초기화 상태를 저장하는 필드
        // volatile: 멀티스레드 환경에서 변수의 가시성을 보장하는 키워드
        // boolean: 불린 기본 타입 (true/false)
        private volatile boolean initialized;

        // 파괴 상태를 저장하는 필드
        private volatile boolean destroyed;

        /**
         * FilterInfo 생성자
         *
         * @param filterName 필터 이름
         * @param filter 필터 인스턴스
         * @param filterConfig 필터 설정
         * @param urlPatterns URL 패턴 목록
         * @param order 실행 순서
         */
        public FilterInfo(String filterName, Filter filter, FilterConfig filterConfig,
                          List<String> urlPatterns, int order) {
            // this: 현재 객체 참조 키워드
            this.filterName = filterName;
            this.filter = filter;
            this.filterConfig = filterConfig;

            // new ArrayList<>(): ArrayList 생성자 호출
            // urlPatterns != null: null 체크
            // ? : : 삼항 연산자
            // Collections.emptyList(): 빈 리스트 반환하는 정적 메서드
            this.urlPatterns = new ArrayList<>(urlPatterns != null ? urlPatterns : Collections.emptyList());
            this.order = order;

            // 초기화 상태는 false로 시작
            this.initialized = false;
            this.destroyed = false;
        }

        // Getter 메서드들 - 필드 값을 반환하는 메서드들
        // public: 모든 곳에서 접근 가능한 접근 제어자
        public String getFilterName() {
            return filterName;
        }

        public Filter getFilter() {
            return filter;
        }

        public FilterConfig getFilterConfig() {
            return filterConfig;
        }

        public List<String> getUrlPatterns() {
            // Collections.unmodifiableList(): 수정 불가능한 읽기 전용 리스트 반환
            return Collections.unmodifiableList(urlPatterns);
        }

        public int getOrder() {
            return order;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public boolean isDestroyed() {
            return destroyed;
        }

        // 상태 변경 메서드들
        // 패키지 프라이빗 (default) 접근 제어 - 같은 패키지에서만 접근 가능
        void setInitialized(boolean initialized) {
            this.initialized = initialized;
        }

        void setDestroyed(boolean destroyed) {
            this.destroyed = destroyed;
        }

        /**
         * 객체의 문자열 표현을 반환하는 메서드
         * Object 클래스의 toString() 메서드를 오버라이드
         */
        @Override
        public String toString() {
            // String.format(): 형식화된 문자열을 생성하는 정적 메서드
            // %s: 문자열 포맷 지정자
            // %d: 정수 포맷 지정자
            return String.format("FilterInfo{name='%s', order=%d, patterns=%s, initialized=%s}",
                    filterName, order, urlPatterns, initialized);
        }
    }

    /**
     * 등록된 필터들 (필터 이름 -> FilterInfo)
     * 스레드 안전한 맵 사용
     *
     * Map<String, FilterInfo>: 키는 String, 값은 FilterInfo인 맵 제네릭 타입
     */
    private final Map<String, FilterInfo> registeredFilters;

    /**
     * URL 패턴별 필터 매핑 (패턴 -> 필터 이름 목록)
     * 각 패턴에 여러 필터가 매핑될 수 있음
     *
     * Map<String, List<String>>: 키는 String, 값은 String 리스트인 맵
     */
    private final Map<String, List<String>> filterMappings;

    /**
     * 서블릿 컨텍스트 참조
     * 필터 초기화 시 FilterConfig 생성에 사용
     */
    private final ServletContext servletContext;

    /**
     * FilterManager 생성자
     *
     * @param servletContext 서블릿 컨텍스트
     */
    public FilterManager(ServletContext servletContext) {
        this.servletContext = servletContext;

        // new ConcurrentHashMap<>(): 스레드 안전한 해시맵 생성
        // ConcurrentHashMap: 동시성을 지원하는 해시맵 구현 클래스
        this.registeredFilters = new ConcurrentHashMap<>();
        this.filterMappings = new ConcurrentHashMap<>();
    }

    /**
     * 필터를 등록합니다.
     *
     * 필터는 등록 시점에 초기화되지 않으며, 실제 요청 처리 시 지연 초기화됩니다.
     * 이는 성능 최적화와 순환 의존성 방지를 위함입니다.
     *
     * @param filterName 필터 이름 (고유해야 함)
     * @param filter 필터 인스턴스
     * @param initParameters 초기화 매개변수
     * @param urlPatterns URL 패턴 목록
     * @param order 필터 실행 순서 (낮을수록 먼저 실행)
     * @throws ServletException 등록 실패 시
     */
    public synchronized void registerFilter(String filterName, Filter filter,
                                            Map<String, String> initParameters,
                                            List<String> urlPatterns, int order) throws ServletException {
        // synchronized: 메서드 전체를 동기화하는 키워드 (스레드 안전성 보장)

        // 입력 검증
        validateFilterRegistration(filterName, filter, urlPatterns);

        // 중복 이름 검사
        // registeredFilters.containsKey(): Map 인터페이스의 키 존재 여부 확인 메서드
        if (registeredFilters.containsKey(filterName)) {
            // new ServletException(): 서블릿 예외 생성자 호출
            throw new ServletException("필터 이름이 이미 등록되어 있습니다: " + filterName);
        }

        // FilterConfig 생성
        // new FilterConfigImpl(): 내부 클래스의 생성자 호출
        // initParameters != null: null 체크
        // new HashMap<>(): 해시맵 생성자 호출
        FilterConfig filterConfig = new FilterConfigImpl(
                filterName, servletContext,
                initParameters != null ? initParameters : new HashMap<>()
        );

        // FilterInfo 생성
        FilterInfo filterInfo = new FilterInfo(filterName, filter, filterConfig, urlPatterns, order);

        // 등록
        // registeredFilters.put(): Map 인터페이스의 키-값 저장 메서드
        registeredFilters.put(filterName, filterInfo);

        // URL 패턴 매핑 등록
        if (urlPatterns != null) {
            // for-each 루프: 컬렉션의 모든 요소를 순회
            for (String pattern : urlPatterns) {
                // filterMappings.computeIfAbsent(): 키가 없으면 새 값을 계산해서 추가
                // k -> new ArrayList<>(): 람다 표현식으로 새 ArrayList 생성
                // .add(): List 인터페이스의 요소 추가 메서드
                filterMappings.computeIfAbsent(pattern, k -> new ArrayList<>()).add(filterName);

                // 필터 순서대로 정렬
                // filterMappings.get(): Map의 값 조회 메서드
                // .sort(): List의 정렬 메서드
                // (name1, name2) -> {}: 람다 표현식으로 Comparator 구현
                filterMappings.get(pattern).sort((name1, name2) -> {
                    FilterInfo info1 = registeredFilters.get(name1);
                    FilterInfo info2 = registeredFilters.get(name2);
                    // Integer.compare(): Integer 클래스의 정수 비교 정적 메서드
                    return Integer.compare(info1.getOrder(), info2.getOrder());
                });
            }
        }
    }

    /**
     * 필터를 해제합니다.
     *
     * @param filterName 해제할 필터 이름
     * @return 해제 성공 여부
     */
    public synchronized boolean unregisterFilter(String filterName) {
        if (filterName == null) {
            return false;
        }

        // registeredFilters.remove(): Map의 키-값 제거 메서드
        FilterInfo filterInfo = registeredFilters.remove(filterName);
        if (filterInfo == null) {
            return false;
        }

        // URL 패턴 매핑에서 제거
        // filterInfo.getUrlPatterns(): FilterInfo의 URL 패턴 목록 반환
        for (String pattern : filterInfo.getUrlPatterns()) {
            List<String> filterNames = filterMappings.get(pattern);
            if (filterNames != null) {
                // filterNames.remove(): List의 요소 제거 메서드
                filterNames.remove(filterName);
                // filterNames.isEmpty(): List가 비어있는지 확인하는 메서드
                if (filterNames.isEmpty()) {
                    filterMappings.remove(pattern);
                }
            }
        }

        // 필터 정리
        destroyFilter(filterInfo);

        return true;
    }

    /**
     * 요청 경로에 매칭되는 필터 체인을 생성합니다.
     *
     * 매칭되는 모든 필터를 찾아서 순서대로 체인을 구성합니다.
     * 필터는 지연 초기화되므로 체인 생성 시점에 초기화됩니다.
     *
     * @param requestPath 요청 경로
     * @param servlet 최종 실행할 서블릿
     * @return 필터 체인 (매칭되는 필터가 없으면 서블릿만 포함)
     */
    public FilterChain createFilterChain(String requestPath, Servlet servlet) {
        List<FilterInfo> matchingFilters = new ArrayList<>();

        // 매칭되는 필터들 수집
        // filterMappings.entrySet(): Map의 모든 키-값 쌍을 Set<Entry>로 반환
        for (Map.Entry<String, List<String>> entry : filterMappings.entrySet()) {
            // entry.getKey(): Map.Entry의 키 반환 메서드
            String pattern = entry.getKey();
            if (matchesPattern(pattern, requestPath)) {
                // entry.getValue(): Map.Entry의 값 반환 메서드
                for (String filterName : entry.getValue()) {
                    FilterInfo filterInfo = registeredFilters.get(filterName);
                    // filterInfo != null: null 체크
                    // !filterInfo.isDestroyed(): 논리 부정 연산자로 파괴되지 않았음을 확인
                    if (filterInfo != null && !filterInfo.isDestroyed()) {
                        // matchingFilters.add(): List의 요소 추가 메서드
                        matchingFilters.add(filterInfo);
                    }
                }
            }
        }

        // 순서별 정렬 (order가 같으면 이름순)
        // matchingFilters.sort(): List의 정렬 메서드
        // (f1, f2) -> {}: 람다 표현식으로 Comparator 구현
        matchingFilters.sort((f1, f2) -> {
            int orderCompare = Integer.compare(f1.getOrder(), f2.getOrder());
            if (orderCompare == 0) {
                // f1.getFilterName().compareTo(): String의 사전식 비교 메서드
                return f1.getFilterName().compareTo(f2.getFilterName());
            }
            return orderCompare;
        });

        // 중복 제거 (같은 필터가 여러 패턴에 매칭될 수 있음)
        // new LinkedHashMap<>(): 삽입 순서를 유지하는 해시맵 생성
        Map<String, FilterInfo> uniqueFilters = new LinkedHashMap<>();
        for (FilterInfo filterInfo : matchingFilters) {
            // uniqueFilters.put(): Map의 키-값 저장 메서드 (중복 키는 덮어씀)
            uniqueFilters.put(filterInfo.getFilterName(), filterInfo);
        }

        // 필터 체인 생성
        // new FilterChainImpl(): 필터 체인 구현 클래스 생성자 호출
        // uniqueFilters.values(): Map의 모든 값을 Collection으로 반환
        // new ArrayList<>(): 컬렉션을 ArrayList로 변환
        return new FilterChainImpl(new ArrayList<>(uniqueFilters.values()), servlet);
    }

    /**
     * 특정 필터를 초기화합니다.
     *
     * 지연 초기화 패턴을 사용하여 실제 사용 시점에 초기화합니다.
     * 스레드 안전하게 중복 초기화를 방지합니다.
     *
     * @param filterInfo 초기화할 필터 정보
     * @throws ServletException 초기화 실패 시
     */
    public void initializeFilter(FilterInfo filterInfo) throws ServletException {
        if (filterInfo.isDestroyed()) {
            throw new ServletException("이미 파괴된 필터입니다: " + filterInfo.getFilterName());
        }

        if (filterInfo.isInitialized()) {
            return; // 이미 초기화됨
        }

        // synchronized 블록: 특정 객체에 대한 동기화
        synchronized (filterInfo) {
            // Double-check 패턴 - 동기화 블록 내에서 다시 확인
            if (filterInfo.isInitialized()) {
                return;
            }

            try {
                // filterInfo.getFilter().init(): Filter의 초기화 메서드 호출
                filterInfo.getFilter().init(filterInfo.getFilterConfig());
                filterInfo.setInitialized(true);
            } catch (Exception e) {
                throw new ServletException("필터 초기화 실패: " + filterInfo.getFilterName(), e);
            }
        }
    }

    /**
     * 모든 필터를 정리합니다.
     *
     * 애플리케이션 종료 시 호출되어 모든 필터의 리소스를 정리합니다.
     */
    public synchronized void destroyAllFilters() {
        // 모든 필터 정리
        // registeredFilters.values(): Map의 모든 값을 Collection으로 반환
        for (FilterInfo filterInfo : registeredFilters.values()) {
            destroyFilter(filterInfo);
        }

        // 저장소 정리
        // .clear(): 컬렉션의 모든 요소 제거 메서드
        registeredFilters.clear();
        filterMappings.clear();
    }

    /**
     * 등록된 모든 필터 정보를 반환합니다.
     */
    public synchronized Collection<FilterInfo> getAllFilters() {
        // new ArrayList<>(): 새로운 ArrayList 생성으로 방어적 복사
        return new ArrayList<>(registeredFilters.values());
    }

    /**
     * 필터 이름으로 필터 정보를 조회합니다.
     */
    public FilterInfo getFilterInfo(String filterName) {
        // filterName != null: null 체크
        // registeredFilters.get(): Map의 값 조회 메서드
        return filterName != null ? registeredFilters.get(filterName) : null;
    }

    /**
     * 등록된 필터 수를 반환합니다.
     */
    public int getFilterCount() {
        // registeredFilters.size(): Map의 크기 반환 메서드
        return registeredFilters.size();
    }

    /**
     * 매니저 상태 정보를 반환합니다.
     */
    public synchronized String getStatusInfo() {
        // new StringBuilder(): 문자열 빌더 생성자 호출
        // StringBuilder: 가변 문자열을 효율적으로 조작하는 클래스
        StringBuilder info = new StringBuilder();

        // info.append(): StringBuilder의 문자열 추가 메서드
        info.append("FilterManager Status:\n");
        info.append("  Total Filters: ").append(registeredFilters.size()).append("\n");
        info.append("  URL Patterns: ").append(filterMappings.size()).append("\n");

        // !registeredFilters.isEmpty(): Map이 비어있지 않은지 확인
        if (!registeredFilters.isEmpty()) {
            info.append("  Registered Filters:\n");

            // registeredFilters.values().stream(): 컬렉션을 스트림으로 변환
            // .stream(): Collection 인터페이스의 스트림 생성 메서드
            // .sorted(): 스트림의 정렬 메서드
            // Comparator.comparing(): 비교자 생성 정적 메서드
            // FilterInfo::getOrder: 메서드 참조 (람다 표현식의 축약형)
            // .forEach(): 스트림의 각 요소에 대해 액션 수행
            registeredFilters.values().stream()
                    .sorted(Comparator.comparing(FilterInfo::getOrder))
                    .forEach(filterInfo -> {
                        info.append("    ").append(filterInfo.toString()).append("\n");
                    });
        }

        // info.toString(): StringBuilder를 String으로 변환
        return info.toString();
    }

    // ========== 내부 메서드들 ==========

    /**
     * URL 패턴 매칭을 수행합니다.
     *
     * 지원하는 패턴 타입:
     * - 정확 매칭: "/admin/login"
     * - 경로 매칭: "/admin/*", "/api/*"
     * - 확장자 매칭: "*.jsp", "*.html"
     * - 전역 매칭: "/*"
     */
    private boolean matchesPattern(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }

        // 전역 매칭
        // pattern.equals(): String의 동등성 비교 메서드
        if ("/*".equals(pattern)) {
            return true;
        }
        // 정확 매칭
        else if (pattern.equals(path)) {
            return true;
        }
        // 확장자 매칭
        // pattern.startsWith(): String의 접두사 확인 메서드
        else if (pattern.startsWith("*.")) {
            // pattern.substring(): String의 부분 문자열 추출 메서드
            String extension = pattern.substring(1);
            // path.endsWith(): String의 접미사 확인 메서드
            return path.endsWith(extension);
        }
        // 경로 매칭
        // pattern.endsWith(): String의 접미사 확인 메서드
        else if (pattern.endsWith("/*")) {
            // pattern.length(): String의 길이 반환 메서드
            String prefix = pattern.substring(0, pattern.length() - 2);
            // path.startsWith(): String의 접두사 확인 메서드
            return path.startsWith(prefix);
        }

        return false;
    }

    /**
     * 필터 등록 정보를 검증합니다.
     */
    private void validateFilterRegistration(String filterName, Filter filter, List<String> urlPatterns) {
        // filterName.trim(): String의 앞뒤 공백 제거 메서드
        // .isEmpty(): String이 빈 문자열인지 확인하는 메서드
        if (filterName == null || filterName.trim().isEmpty()) {
            // new IllegalArgumentException(): 잘못된 인수 예외 생성자
            throw new IllegalArgumentException("필터 이름이 null이거나 비어있습니다");
        }

        if (filter == null) {
            throw new IllegalArgumentException("필터 인스턴스가 null입니다");
        }

        // urlPatterns.isEmpty(): List가 비어있는지 확인하는 메서드
        if (urlPatterns == null || urlPatterns.isEmpty()) {
            throw new IllegalArgumentException("URL 패턴이 null이거나 비어있습니다");
        }

        // URL 패턴 유효성 검사
        for (String pattern : urlPatterns) {
            if (pattern == null || pattern.trim().isEmpty()) {
                throw new IllegalArgumentException("유효하지 않은 URL 패턴: " + pattern);
            }
        }
    }

    /**
     * 필터를 정리합니다.
     */
    private void destroyFilter(FilterInfo filterInfo) {
        if (filterInfo.isDestroyed()) {
            return;
        }

        synchronized (filterInfo) {
            if (filterInfo.isDestroyed()) {
                return;
            }

            try {
                if (filterInfo.isInitialized()) {
                    // filterInfo.getFilter().destroy(): Filter의 정리 메서드 호출
                    filterInfo.getFilter().destroy();
                }
            } catch (Exception e) {
                // 로깅만 하고 계속 진행
                // System.err: 표준 에러 출력 스트림
                // .println(): PrintStream의 한 줄 출력 메서드
                // e.getMessage(): Exception의 메시지 반환 메서드
                System.err.println("필터 정리 중 오류 발생: " + filterInfo.getFilterName() + " - " + e.getMessage());
            } finally {
                // finally: 예외 발생 여부와 관계없이 실행되는 블록
                filterInfo.setDestroyed(true);
            }
        }
    }

    /**
     * FilterConfig 구현 클래스
     *
     * static: 외부 클래스 인스턴스 없이 생성 가능한 정적 중첩 클래스
     */
    private static class FilterConfigImpl implements FilterConfig {
        private final String filterName;
        private final ServletContext servletContext;
        // Map<String, String>: 키와 값이 모두 String인 맵
        private final Map<String, String> initParameters;

        public FilterConfigImpl(String filterName, ServletContext servletContext,
                                Map<String, String> initParameters) {
            this.filterName = filterName;
            this.servletContext = servletContext;
            // new HashMap<>(): 새로운 해시맵 생성으로 방어적 복사
            this.initParameters = new HashMap<>(initParameters);
        }

        @Override
        public String getFilterName() {
            return filterName;
        }

        @Override
        public ServletContext getServletContext() {
            return servletContext;
        }

        @Override
        public String getInitParameter(String name) {
            // initParameters.get(): Map의 값 조회 메서드
            return initParameters.get(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            // Collections.enumeration(): Set을 Enumeration으로 변환하는 정적 메서드
            // initParameters.keySet(): Map의 모든 키를 Set으로 반환
            return Collections.enumeration(initParameters.keySet());
        }
    }
}