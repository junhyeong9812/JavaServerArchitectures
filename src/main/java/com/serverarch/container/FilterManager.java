package src.main.java.com.serverarch.container;

import src.main.java.jakarta.servlet.*;
import java.util.*;
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
     */
    public static class FilterInfo {
        private final String filterName;           // 필터 이름
        private final Filter filter;               // 필터 인스턴스
        private final FilterConfig filterConfig;   // 필터 설정
        private final List<String> urlPatterns;    // URL 패턴 목록
        private final int order;                   // 실행 순서 (낮을수록 먼저 실행)
        private volatile boolean initialized;       // 초기화 상태
        private volatile boolean destroyed;         // 파괴 상태

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
            this.filterName = filterName;
            this.filter = filter;
            this.filterConfig = filterConfig;
            this.urlPatterns = new ArrayList<>(urlPatterns != null ? urlPatterns : Collections.emptyList());
            this.order = order;
            this.initialized = false;
            this.destroyed = false;
        }

        // Getter 메서드들
        public String getFilterName() { return filterName; }
        public Filter getFilter() { return filter; }
        public FilterConfig getFilterConfig() { return filterConfig; }
        public List<String> getUrlPatterns() { return Collections.unmodifiableList(urlPatterns); }
        public int getOrder() { return order; }
        public boolean isInitialized() { return initialized; }
        public boolean isDestroyed() { return destroyed; }

        // 상태 변경 메서드들
        void setInitialized(boolean initialized) { this.initialized = initialized; }
        void setDestroyed(boolean destroyed) { this.destroyed = destroyed; }

        @Override
        public String toString() {
            return String.format("FilterInfo{name='%s', order=%d, patterns=%s, initialized=%s}",
                    filterName, order, urlPatterns, initialized);
        }
    }

    /**
     * 등록된 필터들 (필터 이름 -> FilterInfo)
     * 스레드 안전한 맵 사용
     */
    private final Map<String, FilterInfo> registeredFilters;

    /**
     * URL 패턴별 필터 매핑 (패턴 -> 필터 이름 목록)
     * 각 패턴에 여러 필터가 매핑될 수 있음
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

        // 입력 검증
        validateFilterRegistration(filterName, filter, urlPatterns);

        // 중복 이름 검사
        if (registeredFilters.containsKey(filterName)) {
            throw new ServletException("필터 이름이 이미 등록되어 있습니다: " + filterName);
        }

        // FilterConfig 생성
        FilterConfig filterConfig = new FilterConfigImpl(
                filterName, servletContext,
                initParameters != null ? initParameters : new HashMap<>()
        );

        // FilterInfo 생성
        FilterInfo filterInfo = new FilterInfo(filterName, filter, filterConfig, urlPatterns, order);

        // 등록
        registeredFilters.put(filterName, filterInfo);

        // URL 패턴 매핑 등록
        if (urlPatterns != null) {
            for (String pattern : urlPatterns) {
                filterMappings.computeIfAbsent(pattern, k -> new ArrayList<>()).add(filterName);

                // 필터 순서대로 정렬
                filterMappings.get(pattern).sort((name1, name2) -> {
                    FilterInfo info1 = registeredFilters.get(name1);
                    FilterInfo info2 = registeredFilters.get(name2);
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

        FilterInfo filterInfo = registeredFilters.remove(filterName);
        if (filterInfo == null) {
            return false;
        }

        // URL 패턴 매핑에서 제거
        for (String pattern : filterInfo.getUrlPatterns()) {
            List<String> filterNames = filterMappings.get(pattern);
            if (filterNames != null) {
                filterNames.remove(filterName);
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

        // 순서별 정렬 (order가 같으면 이름순)
        matchingFilters.sort((f1, f2) -> {
            int orderCompare = Integer.compare(f1.getOrder(), f2.getOrder());
            if (orderCompare == 0) {
                return f1.getFilterName().compareTo(f2.getFilterName());
            }
            return orderCompare;
        });

        // 중복 제거 (같은 필터가 여러 패턴에 매칭될 수 있음)
        Map<String, FilterInfo> uniqueFilters = new LinkedHashMap<>();
        for (FilterInfo filterInfo : matchingFilters) {
            uniqueFilters.put(filterInfo.getFilterName(), filterInfo);
        }

        // 필터 체인 생성
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

        synchronized (filterInfo) {
            // Double-check 패턴
            if (filterInfo.isInitialized()) {
                return;
            }

            try {
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
        for (FilterInfo filterInfo : registeredFilters.values()) {
            destroyFilter(filterInfo);
        }

        // 저장소 정리
        registeredFilters.clear();
        filterMappings.clear();
    }

    /**
     * 등록된 모든 필터 정보를 반환합니다.
     */
    public synchronized Collection<FilterInfo> getAllFilters() {
        return new ArrayList<>(registeredFilters.values());
    }

    /**
     * 필터 이름으로 필터 정보를 조회합니다.
     */
    public FilterInfo getFilterInfo(String filterName) {
        return filterName != null ? registeredFilters.get(filterName) : null;
    }

    /**
     * 등록된 필터 수를 반환합니다.
     */
    public int getFilterCount() {
        return registeredFilters.size();
    }

    /**
     * 매니저 상태 정보를 반환합니다.
     */
    public synchronized String getStatusInfo() {
        StringBuilder info = new StringBuilder();
        info.append("FilterManager Status:\n");
        info.append("  Total Filters: ").append(registeredFilters.size()).append("\n");
        info.append("  URL Patterns: ").append(filterMappings.size()).append("\n");

        if (!registeredFilters.isEmpty()) {
            info.append("  Registered Filters:\n");
            registeredFilters.values().stream()
                    .sorted(Comparator.comparing(FilterInfo::getOrder))
                    .forEach(filterInfo -> {
                        info.append("    ").append(filterInfo.toString()).append("\n");
                    });
        }

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
        if ("/*".equals(pattern)) {
            return true;
        }
        // 정확 매칭
        else if (pattern.equals(path)) {
            return true;
        }
        // 확장자 매칭
        else if (pattern.startsWith("*.")) {
            String extension = pattern.substring(1);
            return path.endsWith(extension);
        }
        // 경로 매칭
        else if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return path.startsWith(prefix);
        }

        return false;
    }

    /**
     * 필터 등록 정보를 검증합니다.
     */
    private void validateFilterRegistration(String filterName, Filter filter, List<String> urlPatterns) {
        if (filterName == null || filterName.trim().isEmpty()) {
            throw new IllegalArgumentException("필터 이름이 null이거나 비어있습니다");
        }

        if (filter == null) {
            throw new IllegalArgumentException("필터 인스턴스가 null입니다");
        }

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
                    filterInfo.getFilter().destroy();
                }
            } catch (Exception e) {
                // 로깅만 하고 계속 진행
                System.err.println("필터 정리 중 오류 발생: " + filterInfo.getFilterName() + " - " + e.getMessage());
            } finally {
                filterInfo.setDestroyed(true);
            }
        }
    }

    /**
     * FilterConfig 구현 클래스
     */
    private static class FilterConfigImpl implements FilterConfig {
        private final String filterName;
        private final ServletContext servletContext;
        private final Map<String, String> initParameters;

        public FilterConfigImpl(String filterName, ServletContext servletContext,
                                Map<String, String> initParameters) {
            this.filterName = filterName;
            this.servletContext = servletContext;
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
            return initParameters.get(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.enumeration(initParameters.keySet());
        }
    }
}