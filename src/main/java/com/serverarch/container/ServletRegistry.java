package src.main.java.com.serverarch.container;

import src.main.java.jakarta.servlet.Servlet;
import src.main.java.jakarta.servlet.ServletConfig;
import src.main.java.jakarta.servlet.ServletContext;
import src.main.java.jakarta.servlet.ServletException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 서블릿의 등록, 관리, 조회를 담당하는 레지스트리 클래스입니다.
 *
 * ServletRegistry는 웹 애플리케이션에 등록된 모든 서블릿들을 관리하며,
 * 서블릿의 생명주기(등록, 초기화, 조회, 제거)를 제어합니다.
 *
 * 주요 기능:
 * - 서블릿 등록 및 해제
 * - URL 패턴과 서블릿 매핑
 * - 서블릿 인스턴스 생명주기 관리
 * - 스레드 안전한 서블릿 접근
 * - 동적 서블릿 등록/해제
 */
public class ServletRegistry {

    /**
     * 서블릿 정보를 담는 내부 클래스입니다.
     */
    public static class ServletInfo {
        private final String servletName;
        private final Class<? extends Servlet> servletClass;
        private final Servlet servletInstance;
        private final ServletConfig servletConfig;
        private final Map<String, String> initParameters;
        private final Set<String> urlPatterns;
        private final int loadOnStartup;
        private volatile boolean initialized;
        private volatile boolean destroyed;
        private final Object initLock = new Object();

        public ServletInfo(String servletName,
                           Class<? extends Servlet> servletClass,
                           Servlet servletInstance,
                           ServletConfig servletConfig,
                           Map<String, String> initParameters,
                           Set<String> urlPatterns,
                           int loadOnStartup) {
            this.servletName = servletName;
            this.servletClass = servletClass;
            this.servletInstance = servletInstance;
            this.servletConfig = servletConfig;
            this.initParameters = new HashMap<>(initParameters);
            this.urlPatterns = new HashSet<>(urlPatterns);
            this.loadOnStartup = loadOnStartup;
            this.initialized = false;
            this.destroyed = false;
        }

        // Getters
        public String getServletName() { return servletName; }
        public Class<? extends Servlet> getServletClass() { return servletClass; }
        public Servlet getServletInstance() { return servletInstance; }
        public ServletConfig getServletConfig() { return servletConfig; }
        public Map<String, String> getInitParameters() { return Collections.unmodifiableMap(initParameters); }
        public Set<String> getUrlPatterns() { return Collections.unmodifiableSet(urlPatterns); }
        public int getLoadOnStartup() { return loadOnStartup; }
        public boolean isInitialized() { return initialized; }
        public boolean isDestroyed() { return destroyed; }
        public Object getInitLock() { return initLock; }

        // 상태 변경 메서드들
        void setInitialized(boolean initialized) { this.initialized = initialized; }
        void setDestroyed(boolean destroyed) { this.destroyed = destroyed; }
    }

    /**
     * 서블릿 이름으로 ServletInfo를 관리하는 맵
     * 스레드 안전을 위해 ConcurrentHashMap 사용
     */
    private final Map<String, ServletInfo> servletsByName;

    /**
     * URL 패턴으로 ServletInfo를 관리하는 맵
     * 여러 패턴이 같은 서블릿을 가리킬 수 있음
     */
    private final Map<String, ServletInfo> servletsByPattern;

    /**
     * 로드 순서(load-on-startup)로 정렬된 서블릿 목록
     */
    private final NavigableMap<Integer, List<ServletInfo>> servletsByLoadOrder;

    /**
     * 레지스트리 전체 읽기/쓰기 락
     * 등록/해제는 쓰기 락, 조회는 읽기 락 사용
     */
    private final ReentrantReadWriteLock registryLock;

    /**
     * 서블릿 컨텍스트 참조
     */
    private final ServletContext servletContext;

    /**
     * 레지스트리 생성자
     *
     * @param servletContext 웹 애플리케이션의 ServletContext
     */
    public ServletRegistry(ServletContext servletContext) {
        this.servletContext = servletContext;
        this.servletsByName = new ConcurrentHashMap<>();
        this.servletsByPattern = new ConcurrentHashMap<>();
        this.servletsByLoadOrder = new TreeMap<>();
        this.registryLock = new ReentrantReadWriteLock();
    }

    /**
     * 서블릿을 등록합니다.
     *
     * @param servletName 서블릿 이름 (고유해야 함)
     * @param servletClass 서블릿 클래스
     * @param initParameters 초기화 매개변수
     * @param urlPatterns URL 패턴들
     * @param loadOnStartup 로드 순서 (-1이면 지연 로딩)
     * @return 등록된 ServletInfo 객체
     * @throws ServletException 등록 실패 시
     */
    public ServletInfo registerServlet(String servletName,
                                       Class<? extends Servlet> servletClass,
                                       Map<String, String> initParameters,
                                       Set<String> urlPatterns,
                                       int loadOnStartup) throws ServletException {

        // 입력 검증
        validateServletRegistration(servletName, servletClass, urlPatterns);

        registryLock.writeLock().lock();
        try {
            // 중복 이름 검사
            if (servletsByName.containsKey(servletName)) {
                throw new ServletException("서블릿 이름이 이미 등록되어 있습니다: " + servletName);
            }

            // URL 패턴 중복 검사
            for (String pattern : urlPatterns) {
                if (servletsByPattern.containsKey(pattern)) {
                    throw new ServletException("URL 패턴이 이미 등록되어 있습니다: " + pattern);
                }
            }

            // 서블릿 인스턴스 생성
            Servlet servletInstance = createServletInstance(servletClass);

            // ServletConfig 생성
            ServletConfig servletConfig = createServletConfig(servletName, initParameters);

            // ServletInfo 생성
            ServletInfo servletInfo = new ServletInfo(
                    servletName, servletClass, servletInstance, servletConfig,
                    initParameters, urlPatterns, loadOnStartup
            );

            // 레지스트리에 등록
            servletsByName.put(servletName, servletInfo);

            // URL 패턴 매핑 등록
            for (String pattern : urlPatterns) {
                servletsByPattern.put(pattern, servletInfo);
            }

            // 로드 순서별 등록 (load-on-startup >= 0인 경우만)
            if (loadOnStartup >= 0) {
                servletsByLoadOrder
                        .computeIfAbsent(loadOnStartup, k -> new ArrayList<>())
                        .add(servletInfo);
            }

            return servletInfo;

        } catch (Exception e) {
            throw new ServletException("서블릿 등록 실패: " + servletName, e);
        } finally {
            registryLock.writeLock().unlock();
        }
    }

    /**
     * 서블릿 인스턴스로 직접 등록합니다.
     */
    public ServletInfo registerServlet(String servletName,
                                       Servlet servletInstance,
                                       Map<String, String> initParameters,
                                       Set<String> urlPatterns,
                                       int loadOnStartup) throws ServletException {

        // 입력 검증
        if (servletInstance == null) {
            throw new IllegalArgumentException("서블릿 인스턴스가 null입니다");
        }

        validateServletRegistration(servletName, servletInstance.getClass(), urlPatterns);

        registryLock.writeLock().lock();
        try {
            // 중복 검사
            if (servletsByName.containsKey(servletName)) {
                throw new ServletException("서블릿 이름이 이미 등록되어 있습니다: " + servletName);
            }

            for (String pattern : urlPatterns) {
                if (servletsByPattern.containsKey(pattern)) {
                    throw new ServletException("URL 패턴이 이미 등록되어 있습니다: " + pattern);
                }
            }

            // ServletConfig 생성
            ServletConfig servletConfig = createServletConfig(servletName, initParameters);

            // ServletInfo 생성
            ServletInfo servletInfo = new ServletInfo(
                    servletName, servletInstance.getClass(), servletInstance, servletConfig,
                    initParameters, urlPatterns, loadOnStartup
            );

            // 레지스트리에 등록
            servletsByName.put(servletName, servletInfo);

            for (String pattern : urlPatterns) {
                servletsByPattern.put(pattern, servletInfo);
            }

            if (loadOnStartup >= 0) {
                servletsByLoadOrder
                        .computeIfAbsent(loadOnStartup, k -> new ArrayList<>())
                        .add(servletInfo);
            }

            return servletInfo;

        } finally {
            registryLock.writeLock().unlock();
        }
    }

    /**
     * 서블릿을 해제합니다.
     *
     * @param servletName 해제할 서블릿 이름
     * @return 해제된 ServletInfo, 존재하지 않으면 null
     */
    public ServletInfo unregisterServlet(String servletName) {
        if (servletName == null || servletName.trim().isEmpty()) {
            return null;
        }

        registryLock.writeLock().lock();
        try {
            ServletInfo servletInfo = servletsByName.remove(servletName);
            if (servletInfo == null) {
                return null;
            }

            // URL 패턴 매핑 제거
            for (String pattern : servletInfo.getUrlPatterns()) {
                servletsByPattern.remove(pattern);
            }

            // 로드 순서에서 제거
            if (servletInfo.getLoadOnStartup() >= 0) {
                List<ServletInfo> servlets = servletsByLoadOrder.get(servletInfo.getLoadOnStartup());
                if (servlets != null) {
                    servlets.remove(servletInfo);
                    if (servlets.isEmpty()) {
                        servletsByLoadOrder.remove(servletInfo.getLoadOnStartup());
                    }
                }
            }

            // 서블릿 정리
            destroyServlet(servletInfo);

            return servletInfo;

        } finally {
            registryLock.writeLock().unlock();
        }
    }

    /**
     * 서블릿 이름으로 ServletInfo를 조회합니다.
     *
     * @param servletName 서블릿 이름
     * @return ServletInfo 객체, 없으면 null
     */
    public ServletInfo getServletByName(String servletName) {
        if (servletName == null) {
            return null;
        }

        registryLock.readLock().lock();
        try {
            return servletsByName.get(servletName);
        } finally {
            registryLock.readLock().unlock();
        }
    }

    /**
     * URL 패턴으로 ServletInfo를 조회합니다.
     *
     * @param urlPattern URL 패턴
     * @return ServletInfo 객체, 없으면 null
     */
    public ServletInfo getServletByPattern(String urlPattern) {
        if (urlPattern == null) {
            return null;
        }

        registryLock.readLock().lock();
        try {
            return servletsByPattern.get(urlPattern);
        } finally {
            registryLock.readLock().unlock();
        }
    }

    /**
     * URL 패턴 매칭을 통해 ServletInfo를 찾습니다.
     *
     * 패턴 매칭 우선순위:
     * 1. 정확한 매칭
     * 2. 경로 매칭 (/* 패턴)
     * 3. 확장자 매칭 (*.jsp 패턴)
     * 4. 기본 서블릿 (/ 패턴)
     *
     * @param requestPath 요청 경로
     * @return 매칭되는 ServletInfo, 없으면 null
     */
    public ServletInfo findServletByPath(String requestPath) {
        if (requestPath == null) {
            return null;
        }

        registryLock.readLock().lock();
        try {
            // 1. 정확한 매칭
            ServletInfo exactMatch = servletsByPattern.get(requestPath);
            if (exactMatch != null) {
                return exactMatch;
            }

            // 2. 경로 매칭 (가장 긴 매칭 우선)
            String bestPathMatch = null;
            ServletInfo bestPathServlet = null;

            for (Map.Entry<String, ServletInfo> entry : servletsByPattern.entrySet()) {
                String pattern = entry.getKey();

                if (pattern.endsWith("/*")) {
                    String pathPrefix = pattern.substring(0, pattern.length() - 2);
                    if (requestPath.startsWith(pathPrefix) &&
                            (bestPathMatch == null || pathPrefix.length() > bestPathMatch.length())) {
                        bestPathMatch = pathPrefix;
                        bestPathServlet = entry.getValue();
                    }
                }
            }

            if (bestPathServlet != null) {
                return bestPathServlet;
            }

            // 3. 확장자 매칭
            int lastDotIndex = requestPath.lastIndexOf('.');
            if (lastDotIndex != -1) {
                String extension = requestPath.substring(lastDotIndex);
                String extensionPattern = "*" + extension;
                ServletInfo extensionMatch = servletsByPattern.get(extensionPattern);
                if (extensionMatch != null) {
                    return extensionMatch;
                }
            }

            // 4. 기본 서블릿
            return servletsByPattern.get("/");

        } finally {
            registryLock.readLock().unlock();
        }
    }

    /**
     * 등록된 모든 서블릿 이름을 반환합니다.
     */
    public Set<String> getServletNames() {
        registryLock.readLock().lock();
        try {
            return new HashSet<>(servletsByName.keySet());
        } finally {
            registryLock.readLock().unlock();
        }
    }

    /**
     * 등록된 모든 ServletInfo를 반환합니다.
     */
    public Collection<ServletInfo> getAllServlets() {
        registryLock.readLock().lock();
        try {
            return new ArrayList<>(servletsByName.values());
        } finally {
            registryLock.readLock().unlock();
        }
    }

    /**
     * load-on-startup 순서에 따라 서블릿들을 초기화합니다.
     */
    public void initializeStartupServlets() throws ServletException {
        registryLock.readLock().lock();
        try {
            // 로드 순서대로 정렬된 서블릿들 초기화
            for (Map.Entry<Integer, List<ServletInfo>> entry : servletsByLoadOrder.entrySet()) {
                for (ServletInfo servletInfo : entry.getValue()) {
                    initializeServlet(servletInfo);
                }
            }
        } finally {
            registryLock.readLock().unlock();
        }
    }

    /**
     * 모든 서블릿을 정리합니다.
     */
    public void destroyAllServlets() {
        registryLock.writeLock().lock();
        try {
            // 모든 서블릿 정리
            for (ServletInfo servletInfo : servletsByName.values()) {
                destroyServlet(servletInfo);
            }

            // 레지스트리 정리
            servletsByName.clear();
            servletsByPattern.clear();
            servletsByLoadOrder.clear();

        } finally {
            registryLock.writeLock().unlock();
        }
    }

    /**
     * 서블릿을 초기화합니다.
     */
    public void initializeServlet(ServletInfo servletInfo) throws ServletException {
        if (servletInfo.isDestroyed()) {
            throw new ServletException("이미 제거된 서블릿입니다: " + servletInfo.getServletName());
        }

        if (servletInfo.isInitialized()) {
            return; // 이미 초기화됨
        }

        synchronized (servletInfo.getInitLock()) {
            // Double-check
            if (servletInfo.isInitialized()) {
                return;
            }

            try {
                servletInfo.getServletInstance().init(servletInfo.getServletConfig());
                servletInfo.setInitialized(true);
            } catch (Exception e) {
                throw new ServletException("서블릿 초기화 실패: " + servletInfo.getServletName(), e);
            }
        }
    }

    /**
     * 서블릿을 정리합니다.
     */
    private void destroyServlet(ServletInfo servletInfo) {
        if (servletInfo.isDestroyed()) {
            return;
        }

        synchronized (servletInfo.getInitLock()) {
            if (servletInfo.isDestroyed()) {
                return;
            }

            try {
                if (servletInfo.isInitialized()) {
                    servletInfo.getServletInstance().destroy();
                }
            } catch (Exception e) {
                // 로깅만 하고 계속 진행
                System.err.println("서블릿 정리 중 오류 발생: " + servletInfo.getServletName() + " - " + e.getMessage());
            } finally {
                servletInfo.setDestroyed(true);
            }
        }
    }

    /**
     * 서블릿 등록 정보를 검증합니다.
     */
    private void validateServletRegistration(String servletName,
                                             Class<? extends Servlet> servletClass,
                                             Set<String> urlPatterns) {
        if (servletName == null || servletName.trim().isEmpty()) {
            throw new IllegalArgumentException("서블릿 이름이 null이거나 비어있습니다");
        }

        if (servletClass == null) {
            throw new IllegalArgumentException("서블릿 클래스가 null입니다");
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
     * 서블릿 인스턴스를 생성합니다.
     */
    private Servlet createServletInstance(Class<? extends Servlet> servletClass)
            throws ServletException {
        try {
            return servletClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ServletException("서블릿 인스턴스 생성 실패: " + servletClass.getName(), e);
        }
    }

    /**
     * ServletConfig를 생성합니다.
     */
    private ServletConfig createServletConfig(String servletName,
                                              Map<String, String> initParameters) {
        return new ServletConfigImpl(servletName, servletContext, initParameters);
    }

    /**
     * ServletConfig 구현 클래스
     */
    private static class ServletConfigImpl implements ServletConfig {
        private final String servletName;
        private final ServletContext servletContext;
        private final Map<String, String> initParameters;

        public ServletConfigImpl(String servletName, ServletContext servletContext,
                                 Map<String, String> initParameters) {
            this.servletName = servletName;
            this.servletContext = servletContext;
            this.initParameters = new HashMap<>(initParameters != null ? initParameters : Collections.emptyMap());
        }

        @Override
        public String getServletName() {
            return servletName;
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
