package com.serverarch.container;

// Jakarta Servlet API 임포트
// jakarta.servlet.Servlet: HTTP 요청을 처리하는 서블릿 인터페이스
// jakarta.servlet.ServletConfig: 서블릿 초기화 정보를 담는 인터페이스
// jakarta.servlet.ServletContext: 웹 애플리케이션 전체 컨텍스트 인터페이스
// jakarta.servlet.ServletException: 서블릿 처리 중 발생하는 예외 클래스
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

// Java 컬렉션 프레임워크
// java.util.*: 컬렉션 관련 모든 클래스와 인터페이스
// ArrayList: 동적 배열 구현 클래스
// Collection: 컬렉션의 최상위 인터페이스
// Collections: 컬렉션 유틸리티 메서드 제공 클래스
// Enumeration: 열거 인터페이스 (Iterator의 구버전)
// HashMap: 해시테이블 기반 맵 구현 클래스
// HashSet: 해시테이블 기반 집합 구현 클래스
// List: 순서가 있는 컬렉션 인터페이스
// Map: 키-값 쌍을 저장하는 컬렉션 인터페이스
// NavigableMap: 정렬된 맵 인터페이스
// Set: 중복을 허용하지 않는 컬렉션 인터페이스
// TreeMap: 이진 탐색 트리 기반 정렬된 맵 구현 클래스
import java.util.*;

// Java 동시성 라이브러리
// java.util.concurrent.ConcurrentHashMap: 스레드 안전한 해시맵 구현 클래스
// java.util.concurrent.locks.ReentrantReadWriteLock: 읽기/쓰기 락 구현 클래스
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
     *
     * static: 외부 클래스의 인스턴스 없이도 생성 가능한 정적 중첩 클래스
     */
    public static class ServletInfo {
        // 서블릿 이름을 저장하는 필드
        // final: 생성 후 변경 불가능한 불변 필드
        private final String servletName;

        // 서블릿 클래스 정보를 저장하는 필드
        // Class<? extends Servlet>: Servlet을 상속받는 클래스 타입
        private final Class<? extends Servlet> servletClass;

        // 서블릿 인스턴스를 저장하는 필드
        private final Servlet servletInstance;

        // 서블릿 설정 정보를 저장하는 필드
        private final ServletConfig servletConfig;

        // 초기화 매개변수를 저장하는 필드
        // Map<String, String>: 키와 값이 모두 String인 맵
        private final Map<String, String> initParameters;

        // URL 패턴들을 저장하는 필드
        // Set<String>: 중복을 허용하지 않는 String 집합
        private final Set<String> urlPatterns;

        // 로드 순서를 저장하는 필드
        // int: 32비트 정수 기본 타입
        private final int loadOnStartup;

        // 초기화 상태를 저장하는 필드
        // volatile: 멀티스레드 환경에서 변수 가시성 보장
        private volatile boolean initialized;

        // 파괴 상태를 저장하는 필드
        private volatile boolean destroyed;

        // 초기화 동기화를 위한 락 객체
        // Object: 자바의 최상위 클래스
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

            // new HashMap<>(): 새로운 해시맵 생성으로 방어적 복사
            this.initParameters = new HashMap<>(initParameters);

            // new HashSet<>(): 새로운 해시셋 생성으로 방어적 복사
            this.urlPatterns = new HashSet<>(urlPatterns);
            this.loadOnStartup = loadOnStartup;
            this.initialized = false;
            this.destroyed = false;
        }

        // Getter 메서드들 - 필드 값을 반환하는 메서드들
        public String getServletName() {
            return servletName;
        }

        public Class<? extends Servlet> getServletClass() {
            return servletClass;
        }

        public Servlet getServletInstance() {
            return servletInstance;
        }

        public ServletConfig getServletConfig() {
            return servletConfig;
        }

        public Map<String, String> getInitParameters() {
            // Collections.unmodifiableMap(): 수정 불가능한 읽기 전용 맵 반환
            return Collections.unmodifiableMap(initParameters);
        }

        public Set<String> getUrlPatterns() {
            // Collections.unmodifiableSet(): 수정 불가능한 읽기 전용 집합 반환
            return Collections.unmodifiableSet(urlPatterns);
        }

        public int getLoadOnStartup() {
            return loadOnStartup;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public boolean isDestroyed() {
            return destroyed;
        }

        public Object getInitLock() {
            return initLock;
        }

        // 상태 변경 메서드들
        // 패키지 프라이빗 (default) 접근 제어
        void setInitialized(boolean initialized) {
            this.initialized = initialized;
        }

        void setDestroyed(boolean destroyed) {
            this.destroyed = destroyed;
        }
    }

    /**
     * 서블릿 이름으로 ServletInfo를 관리하는 맵
     * 스레드 안전을 위해 ConcurrentHashMap 사용
     *
     * Map<String, ServletInfo>: 키는 String, 값은 ServletInfo인 맵
     */
    private final Map<String, ServletInfo> servletsByName;

    /**
     * URL 패턴으로 ServletInfo를 관리하는 맵
     * 여러 패턴이 같은 서블릿을 가리킬 수 있음
     */
    private final Map<String, ServletInfo> servletsByPattern;

    /**
     * 로드 순서(load-on-startup)로 정렬된 서블릿 목록
     *
     * NavigableMap<Integer, List<ServletInfo>>: 정렬된 맵 인터페이스
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

        // new ConcurrentHashMap<>(): 스레드 안전한 해시맵 생성
        this.servletsByName = new ConcurrentHashMap<>();
        this.servletsByPattern = new ConcurrentHashMap<>();

        // new TreeMap<>(): 이진 탐색 트리 기반 정렬된 맵 생성
        this.servletsByLoadOrder = new TreeMap<>();

        // new ReentrantReadWriteLock(): 읽기/쓰기 락 생성
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

        // registryLock.writeLock(): 쓰기 락 획득
        // .lock(): 락 잠금
        registryLock.writeLock().lock();
        try {
            // 중복 이름 검사
            // servletsByName.containsKey(): Map에서 키 존재 여부 확인
            if (servletsByName.containsKey(servletName)) {
                throw new ServletException("서블릿 이름이 이미 등록되어 있습니다: " + servletName);
            }

            // URL 패턴 중복 검사
            // for-each 루프: 컬렉션의 모든 요소 순회
            for (String pattern : urlPatterns) {
                // servletsByPattern.containsKey(): Map에서 키 존재 여부 확인
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
            // servletsByName.put(): Map에 키-값 저장
            servletsByName.put(servletName, servletInfo);

            // URL 패턴 매핑 등록
            for (String pattern : urlPatterns) {
                servletsByPattern.put(pattern, servletInfo);
            }

            // 로드 순서별 등록 (load-on-startup >= 0인 경우만)
            if (loadOnStartup >= 0) {
                // servletsByLoadOrder.computeIfAbsent(): 키가 없으면 새 값 계산해서 추가
                // k -> new ArrayList<>(): 람다 표현식으로 새 ArrayList 생성
                // .add(): List에 요소 추가
                servletsByLoadOrder
                        .computeIfAbsent(loadOnStartup, k -> new ArrayList<>())
                        .add(servletInfo);
            }

            return servletInfo;

        } catch (Exception e) {
            throw new ServletException("서블릿 등록 실패: " + servletName, e);
        } finally {
            // finally: 예외 발생 여부와 상관없이 실행
            // registryLock.writeLock().unlock(): 쓰기 락 해제
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

        // servletInstance.getClass(): Object의 클래스 정보 반환 메서드
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
        // servletName.trim(): String의 앞뒤 공백 제거 메서드
        // .isEmpty(): String이 빈 문자열인지 확인
        if (servletName == null || servletName.trim().isEmpty()) {
            return null;
        }

        registryLock.writeLock().lock();
        try {
            // servletsByName.remove(): Map에서 키-값 제거
            ServletInfo servletInfo = servletsByName.remove(servletName);
            if (servletInfo == null) {
                return null;
            }

            // URL 패턴 매핑 제거
            // servletInfo.getUrlPatterns(): ServletInfo의 URL 패턴 집합 반환
            for (String pattern : servletInfo.getUrlPatterns()) {
                // servletsByPattern.remove(): Map에서 키-값 제거
                servletsByPattern.remove(pattern);
            }

            // 로드 순서에서 제거
            // servletInfo.getLoadOnStartup(): ServletInfo의 로드 순서 반환
            if (servletInfo.getLoadOnStartup() >= 0) {
                // servletsByLoadOrder.get(): Map의 값 조회
                List<ServletInfo> servlets = servletsByLoadOrder.get(servletInfo.getLoadOnStartup());
                if (servlets != null) {
                    // servlets.remove(): List에서 요소 제거
                    servlets.remove(servletInfo);
                    // servlets.isEmpty(): List가 비어있는지 확인
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

        // registryLock.readLock(): 읽기 락 획득
        registryLock.readLock().lock();
        try {
            // servletsByName.get(): Map의 값 조회
            return servletsByName.get(servletName);
        } finally {
            // registryLock.readLock().unlock(): 읽기 락 해제
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

            // servletsByPattern.entrySet(): Map의 모든 키-값 쌍을 Set<Entry>로 반환
            for (Map.Entry<String, ServletInfo> entry : servletsByPattern.entrySet()) {
                // entry.getKey(): Map.Entry의 키 반환
                String pattern = entry.getKey();

                // pattern.endsWith(): String의 접미사 확인 메서드
                if (pattern.endsWith("/*")) {
                    // pattern.substring(): String의 부분 문자열 추출
                    // pattern.length(): String의 길이 반환
                    String pathPrefix = pattern.substring(0, pattern.length() - 2);

                    // requestPath.startsWith(): String의 접두사 확인 메서드
                    // &&: 논리 AND 연산자
                    // pathPrefix.length(): String의 길이 반환
                    if (requestPath.startsWith(pathPrefix) &&
                            (bestPathMatch == null || pathPrefix.length() > bestPathMatch.length())) {
                        bestPathMatch = pathPrefix;
                        // entry.getValue(): Map.Entry의 값 반환
                        bestPathServlet = entry.getValue();
                    }
                }
            }

            if (bestPathServlet != null) {
                return bestPathServlet;
            }

            // 3. 확장자 매칭
            // requestPath.lastIndexOf(): String에서 문자의 마지막 위치 반환
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
            // new HashSet<>(): 새로운 해시셋 생성으로 방어적 복사
            // servletsByName.keySet(): Map의 모든 키를 Set으로 반환
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
            // new ArrayList<>(): 새로운 ArrayList 생성으로 방어적 복사
            // servletsByName.values(): Map의 모든 값을 Collection으로 반환
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
            // servletsByLoadOrder.entrySet(): NavigableMap의 모든 키-값 쌍 반환
            for (Map.Entry<Integer, List<ServletInfo>> entry : servletsByLoadOrder.entrySet()) {
                // entry.getValue(): Map.Entry의 값(리스트) 반환
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
            // servletsByName.values(): Map의 모든 값을 Collection으로 반환
            for (ServletInfo servletInfo : servletsByName.values()) {
                destroyServlet(servletInfo);
            }

            // 레지스트리 정리
            // .clear(): 컬렉션의 모든 요소 제거
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
        // servletInfo.isDestroyed(): ServletInfo의 파괴 상태 확인
        if (servletInfo.isDestroyed()) {
            // servletInfo.getServletName(): ServletInfo의 서블릿 이름 반환
            throw new ServletException("이미 제거된 서블릿입니다: " + servletInfo.getServletName());
        }

        // servletInfo.isInitialized(): ServletInfo의 초기화 상태 확인
        if (servletInfo.isInitialized()) {
            return; // 이미 초기화됨
        }

        // synchronized: 특정 객체에 대한 동기화 블록
        // servletInfo.getInitLock(): ServletInfo의 초기화 락 객체 반환
        synchronized (servletInfo.getInitLock()) {
            // Double-check 패턴 - 동기화 블록 내에서 다시 확인
            if (servletInfo.isInitialized()) {
                return;
            }

            try {
                // servletInfo.getServletInstance(): ServletInfo의 서블릿 인스턴스 반환
                // .init(): Servlet 인터페이스의 초기화 메서드
                // servletInfo.getServletConfig(): ServletInfo의 서블릿 설정 반환
                servletInfo.getServletInstance().init(servletInfo.getServletConfig());

                // servletInfo.setInitialized(): ServletInfo의 초기화 상태 설정
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
                    // servletInfo.getServletInstance().destroy(): Servlet의 정리 메서드 호출
                    servletInfo.getServletInstance().destroy();
                }
            } catch (Exception e) {
                // 로깅만 하고 계속 진행
                // System.err: 표준 에러 출력 스트림
                // e.getMessage(): Exception의 메시지 반환 메서드
                System.err.println("서블릿 정리 중 오류 발생: " + servletInfo.getServletName() + " - " + e.getMessage());
            } finally {
                // finally: 예외 발생 여부와 상관없이 실행
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
        // servletName.trim(): String의 앞뒤 공백 제거
        // .isEmpty(): String이 빈 문자열인지 확인
        if (servletName == null || servletName.trim().isEmpty()) {
            throw new IllegalArgumentException("서블릿 이름이 null이거나 비어있습니다");
        }

        if (servletClass == null) {
            throw new IllegalArgumentException("서블릿 클래스가 null입니다");
        }

        // urlPatterns.isEmpty(): Set이 비어있는지 확인
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
            // servletClass.getDeclaredConstructor(): 클래스의 선언된 생성자 반환
            // .newInstance(): 생성자를 통해 새 인스턴스 생성
            return servletClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            // servletClass.getName(): Class의 이름 반환 메서드
            throw new ServletException("서블릿 인스턴스 생성 실패: " + servletClass.getName(), e);
        }
    }

    /**
     * ServletConfig를 생성합니다.
     */
    private ServletConfig createServletConfig(String servletName,
                                              Map<String, String> initParameters) {
        // new ServletConfigImpl(): 내부 클래스의 생성자 호출
        return new ServletConfigImpl(servletName, servletContext, initParameters);
    }

    /**
     * ServletConfig 구현 클래스
     *
     * static: 외부 클래스 인스턴스 없이 생성 가능한 정적 중첩 클래스
     */
    private static class ServletConfigImpl implements ServletConfig {
        private final String servletName;
        private final ServletContext servletContext;
        private final Map<String, String> initParameters;

        public ServletConfigImpl(String servletName, ServletContext servletContext,
                                 Map<String, String> initParameters) {
            this.servletName = servletName;
            this.servletContext = servletContext;

            // new HashMap<>(): 새로운 해시맵 생성으로 방어적 복사
            // initParameters != null: null 체크
            // Collections.emptyMap(): 빈 맵 반환하는 정적 메서드
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
            // initParameters.get(): Map의 값 조회 메서드
            return initParameters.get(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            // Collections.enumeration(): Collection을 Enumeration으로 변환하는 정적 메서드
            // initParameters.keySet(): Map의 모든 키를 Set으로 반환
            return Collections.enumeration(initParameters.keySet());
        }
    }
}