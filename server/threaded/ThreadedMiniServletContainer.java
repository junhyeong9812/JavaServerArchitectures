package server.threaded;

import server.core.mini.*;
import server.core.http.*;
import server.core.routing.*;
import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.*;

/**
 * 스레드 기반 미니 서블릿 컨테이너
 * 서블릿 생명주기 관리 및 요청 라우팅을 담당
 *
 * 주요 기능:
 * - 서블릿 등록 및 생명주기 관리 (init, service, destroy)
 * - URL 패턴 매칭을 통한 요청 라우팅
 * - 동기/비동기 서블릿 모두 지원
 * - 스레드 안전한 서블릿 관리
 * - Router와의 연동으로 유연한 요청 처리
 */
public class ThreadedMiniServletContainer {

    // === 핵심 구성 요소들 ===

    // MiniContext: 서블릿 컨텍스트 (웹 애플리케이션 정보 관리)
    private final MiniContext context;

    // ConcurrentHashMap: 스레드 안전한 HashMap
    // 여러 스레드가 동시에 서블릿을 등록/조회해도 안전
    // Key: URL 패턴 (예: "/api/*", "/hello")
    // Value: ServletRegistration (서블릿과 메타데이터를 포함)
    private final Map<String, ServletRegistration> servlets;

    // Router: 서블릿으로 처리되지 않는 요청들의 fallback 처리기
    private final Router fallbackRouter;

    // volatile: 멀티스레드 환경에서 변수 값의 가시성 보장
    // 한 스레드에서 변경하면 다른 스레드에서 즉시 볼 수 있음
    private volatile boolean initialized = false;

    // Logger 인스턴스 생성
    // 이 클래스 전용 로거로 로그 출처를 명확히 식별
    private static final Logger logger = LoggerFactory.getLogger(ThreadedMiniServletContainer.class);

    /**
     * 생성자
     *
     * @param contextPath 서블릿 컨텍스트 경로 (예: "/", "/app")
     */
    public ThreadedMiniServletContainer(String contextPath) {
        // MiniContext 생성: 서블릿들이 공유하는 컨텍스트 환경
        this.context = new MiniContext(contextPath);

        // ConcurrentHashMap 초기화: 스레드 안전한 서블릿 저장소
        this.servlets = new ConcurrentHashMap<>();

        // Router 초기화: 서블릿이 없을 때의 대안 라우터
        this.fallbackRouter = new Router();

        // 기본 설정 적용
        setupDefaultConfiguration();
    }

    /**
     * 기본 설정
     * 컨테이너의 기본 초기화 매개변수들을 설정
     */
    private void setupDefaultConfiguration() {
        // setInitParameter(): 초기화 매개변수 설정
        // 웹.xml의 <context-param>과 유사한 역할
        context.setInitParameter("container.type", "threaded");
        context.setInitParameter("servlet.version", "mini-1.0");

        // context.log(): 컨텍스트 로거를 통한 로그 출력
        context.log("ThreadedMiniServletContainer initialized");
    }

    /**
     * 서블릿 등록 (기본 버전)
     * 초기화 매개변수 없이 서블릿만 등록
     *
     * @param pattern URL 패턴 (예: "/hello", "/api/*")
     * @param servlet 등록할 MiniServlet 인스턴스
     */
    public void registerServlet(String pattern, MiniServlet servlet) {
        // 빈 HashMap을 초기화 매개변수로 전달
        // new HashMap<>(): Diamond operator - 제네릭 타입 추론
        registerServlet(pattern, servlet, new HashMap<>());
    }

    /**
     * 서블릿 등록 (초기화 매개변수 포함)
     * 서블릿을 URL 패턴에 등록하고 초기화 매개변수 설정
     *
     * @param pattern URL 패턴
     * @param servlet 등록할 서블릿
     * @param initParams 초기화 매개변수들 (web.xml의 <init-param>과 유사)
     */
    public void registerServlet(String pattern, MiniServlet servlet, Map<String, String> initParams) {
        // 컨테이너가 이미 초기화된 후에는 서블릿 등록 불가
        if (initialized) {
            // IllegalStateException: 잘못된 상태에서 메서드 호출 시 발생
            throw new IllegalStateException("Cannot register servlet after container initialization");
        }

        // ServletRegistration 객체 생성
        // 서블릿과 관련된 모든 메타데이터를 포함하는 래퍼 클래스
        ServletRegistration registration = new ServletRegistration(servlet, pattern, initParams);

        // ConcurrentHashMap.put(): 스레드 안전한 등록
        servlets.put(pattern, registration);

        // servlet.getClass().getSimpleName(): 클래스명만 추출 (패키지명 제외)
        // 예: com.example.HelloServlet -> HelloServlet
        context.log("Servlet registered: " + servlet.getClass().getSimpleName() + " -> " + pattern);
    }

    /**
     * 비동기 서블릿 등록 (기본 버전)
     *
     * @param pattern URL 패턴
     * @param servlet 등록할 MiniAsyncServlet 인스턴스
     */
    public void registerAsyncServlet(String pattern, MiniAsyncServlet servlet) {
        registerAsyncServlet(pattern, servlet, new HashMap<>());
    }

    /**
     * 비동기 서블릿 등록 (초기화 매개변수 포함)
     * CompletableFuture를 사용한 비동기 처리가 가능한 서블릿 등록
     *
     * @param pattern URL 패턴
     * @param servlet 등록할 비동기 서블릿
     * @param initParams 초기화 매개변수들
     */
    public void registerAsyncServlet(String pattern, MiniAsyncServlet servlet, Map<String, String> initParams) {
        if (initialized) {
            throw new IllegalStateException("Cannot register servlet after container initialization");
        }

        // ServletRegistration 생성 시 async=true 플래그 설정
        ServletRegistration registration = new ServletRegistration(servlet, pattern, initParams, true);
        servlets.put(pattern, registration);

        context.log("Async Servlet registered: " + servlet.getClass().getSimpleName() + " -> " + pattern);
    }

    /**
     * Router 기반 핸들러 등록
     * 서블릿이 아닌 Router의 RouteHandler를 등록
     * 서블릿보다 가벼운 요청 처리기로 사용
     *
     * @param pattern URL 패턴
     * @param handler 등록할 RouteHandler
     */
    public void registerHandler(String pattern, RouteHandler handler) {
        // Router.all(): 모든 HTTP 메서드에 대해 핸들러 등록
        // GET, POST, PUT, DELETE 등 모든 메서드를 처리
        fallbackRouter.all(pattern, handler);

        context.log("Handler registered: " + pattern);
    }

    /**
     * 컨테이너 초기화
     * 등록된 모든 서블릿들의 init() 메서드를 호출하여 초기화
     *
     * @throws Exception 서블릿 초기화 중 발생할 수 있는 예외
     */
    public void initialize() throws Exception {
        // 중복 초기화 방지
        if (initialized) {
            return;
        }

        context.log("Initializing servlet container...");

        // Map.values(): Map의 모든 값들을 Collection으로 반환
        // enhanced for loop: for (Type item : collection) 형태
        for (ServletRegistration registration : servlets.values()) {
            try {
                // === 서블릿별 컨텍스트 생성 ===

                // 각 서블릿마다 고유한 컨텍스트 환경 제공
                MiniContext servletContext = createServletContext(registration);

                // === 서블릿 초기화 ===

                // servlet.init(): 서블릿의 초기화 메서드 호출
                // 서블릿 생명주기의 첫 번째 단계
                // 한 번만 호출되며, 서블릿이 첫 요청을 처리하기 전에 실행
                registration.servlet.init(servletContext);

                // 초기화 완료 표시
                registration.initialized = true;

                context.log("Servlet initialized: " + registration.servlet.getClass().getSimpleName());

            } catch (Exception e) {
                // 초기화 실패 시 오류 로그 출력
                context.log("Failed to initialize servlet: " + registration.servlet.getClass().getSimpleName(), e);

                // 예외를 다시 던져서 초기화 실패를 알림
                throw e;
            }
        }

        // 초기화 완료 표시
        initialized = true;

        // servlets.size(): Map에 등록된 서블릿 개수
        context.log("Servlet container initialization completed - " + servlets.size() + " servlets loaded");
    }

    /**
     * 서블릿별 컨텍스트 생성
     * 각 서블릿마다 고유한 실행 환경을 제공
     *
     * @param registration 서블릿 등록 정보
     * @return 서블릿 전용 MiniContext
     */
    private MiniContext createServletContext(ServletRegistration registration) {
        // 새로운 컨텍스트 생성 (기존 컨텍스트 경로 상속)
        MiniContext servletContext = new MiniContext(context.getContextPath());

        // === 글로벌 초기화 매개변수 복사 ===

        // context.getInitParameterNames(): 모든 초기화 매개변수 이름들
        // Enumeration: 레거시 반복자 인터페이스 (Iterator의 이전 버전)
        for (String paramName : context.getInitParameterNames()) {
            // 기존 컨텍스트의 매개변수를 새 컨텍스트로 복사
            servletContext.setInitParameter(paramName, context.getInitParameter(paramName));
        }

        // === 서블릿별 초기화 매개변수 추가 ===

        // Map.Entry: Map의 키-값 쌍을 나타내는 인터페이스
        // entrySet(): Map의 모든 키-값 쌍들을 Set으로 반환
        for (Map.Entry<String, String> entry : registration.initParams.entrySet()) {
            // entry.getKey(): 키 반환
            // entry.getValue(): 값 반환
            servletContext.setInitParameter(entry.getKey(), entry.getValue());
        }

        // === 서블릿 메타데이터 설정 ===

        // setAttribute(): 컨텍스트에 객체 속성 설정
        // 서블릿에서 request.getAttribute()로 접근 가능
        servletContext.setAttribute("servlet.pattern", registration.pattern);
        servletContext.setAttribute("servlet.async", registration.async);

        return servletContext;
    }

    /**
     * HTTP 요청 처리 (동기)
     * 클라이언트의 HTTP 요청을 적절한 서블릿이나 핸들러로 라우팅
     *
     * @param httpRequest 처리할 HTTP 요청
     * @return CompletableFuture로 래핑된 HTTP 응답
     */
    public CompletableFuture<HttpResponse> handleRequest(HttpRequest httpRequest) {
        // 컨테이너가 초기화되지 않았으면 오류 응답
        if (!initialized) {
            // CompletableFuture.completedFuture(): 이미 완료된 Future 생성
            // 비동기 처리 없이 즉시 결과 반환
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Container not initialized"));
        }

        // === 매칭되는 서블릿 찾기 ===

        // httpRequest.getPath(): 요청 URL의 경로 부분 (/api/users 등)
        ServletRegistration registration = findMatchingServlet(httpRequest.getPath());

        if (registration != null) {
            // 서블릿이 있으면 서블릿으로 처리
            return handleServletRequest(registration, httpRequest);
        }

        // === 서블릿이 없으면 fallback 라우터 사용 ===

        // Router.routeWithMiddlewares(): 미들웨어를 포함한 라우팅 처리
        // 서블릿보다 가벼운 핸들러들로 요청 처리
        return fallbackRouter.routeWithMiddlewares(httpRequest);
    }

    /**
     * 매칭되는 서블릿 찾기
     * URL 패턴 매칭을 통해 요청을 처리할 서블릿 검색
     *
     * @param path 요청 경로
     * @return 매칭되는 ServletRegistration 또는 null
     */
    private ServletRegistration findMatchingServlet(String path) {
        // === 1. 정확한 매칭 우선 ===

        // Map.get(): 키에 해당하는 값 반환 (없으면 null)
        ServletRegistration exact = servlets.get(path);
        if (exact != null) {
            return exact;  // 정확히 일치하는 패턴이 있으면 즉시 반환
        }

        // === 2. 패턴 매칭 (와일드카드 등) ===

        // Map.values(): 모든 등록된 서블릿들을 순회
        for (ServletRegistration registration : servlets.values()) {
            // 각 서블릿의 패턴과 요청 경로를 비교
            if (matchesPattern(path, registration.pattern)) {
                return registration;  // 첫 번째로 매칭되는 패턴 반환
            }
        }

        return null;  // 매칭되는 서블릿이 없음
    }

    /**
     * 간단한 패턴 매칭
     * URL 패턴과 실제 요청 경로를 비교하여 매칭 여부 결정
     *
     * @param path 요청 경로 (예: "/api/users/123")
     * @param pattern URL 패턴 (예: "/api/*", "/*", "/hello")
     * @return 매칭 여부
     */
    private boolean matchesPattern(String path, String pattern) {
        // === 1. 전체 매칭 패턴 ("/*") ===

        // String.equals(): 문자열 정확한 일치 비교
        if (pattern.equals("/*")) {
            return true;  // 모든 경로와 매칭
        }

        // === 2. 접두사 매칭 패턴 ("/api/*") ===

        // String.endsWith(): 문자열이 특정 접미사로 끝나는지 확인
        if (pattern.endsWith("/*")) {
            // substring(): 문자열의 일부분 추출
            // pattern.length() - 2: "/*"를 제외한 접두사 부분만 추출
            String prefix = pattern.substring(0, pattern.length() - 2);

            // String.startsWith(): 문자열이 특정 접두사로 시작하는지 확인
            return path.startsWith(prefix);
        }

        // === 3. 정확한 매칭 ===

        return path.equals(pattern);
    }

    /**
     * 서블릿 요청 처리
     * 매칭된 서블릿을 사용하여 실제 HTTP 요청 처리
     *
     * @param registration 매칭된 서블릿 등록 정보
     * @param httpRequest 처리할 HTTP 요청
     * @return CompletableFuture로 래핑된 HTTP 응답
     */
    private CompletableFuture<HttpResponse> handleServletRequest(ServletRegistration registration,
                                                                 HttpRequest httpRequest) {
        try {
            // === 서블릿 요청/응답 객체 생성 ===

            // MiniRequest: HttpRequest를 서블릿 API 형태로 래핑
            MiniRequest miniRequest = new MiniRequest(httpRequest, context);

            // MiniResponse: 서블릿이 응답을 작성할 수 있는 객체
            MiniResponse miniResponse = new MiniResponse();

            // === 요청에 서블릿 정보 추가 ===

            // setAttribute(): 요청 객체에 속성 설정
            // 서블릿 내에서 어떤 패턴으로 매칭되었는지 확인 가능
            miniRequest.setAttribute("servlet.pattern", registration.pattern);

            // === 동기/비동기 서블릿 구분 처리 ===

            if (registration.async && registration.servlet instanceof MiniAsyncServlet) {
                // === 비동기 서블릿 처리 ===

                // instanceof: 객체가 특정 클래스의 인스턴스인지 확인
                MiniAsyncServlet asyncServlet = (MiniAsyncServlet) registration.servlet;

                // serviceAsync(): 비동기 서블릿의 요청 처리 메서드
                // CompletableFuture를 직접 반환하여 비동기 처리
                return asyncServlet.serviceAsync(miniRequest, miniResponse);

            } else {
                // === 동기 서블릿 처리 (별도 스레드에서) ===

                // CompletableFuture.supplyAsync(): 별도 스레드에서 비동기 실행
                // Supplier 인터페이스를 람다로 구현
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        // service(): 동기 서블릿의 요청 처리 메서드
                        // HttpResponse를 직접 반환
                        return registration.servlet.service(miniRequest, miniResponse);
                    } catch (Exception e) {
                        // 서블릿 처리 중 예외 발생 시 로그 출력
                        context.log("Servlet processing error", e);

                        // 500 Internal Server Error 응답 반환
                        return HttpResponse.internalServerError("Servlet error: " + e.getMessage());
                    }
                });
            }

        } catch (Exception e) {
            // 요청 처리 설정 중 예외 발생 시
            context.log("Request handling error", e);

            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Request handling error: " + e.getMessage()));
        }
    }

    /**
     * 컨테이너 종료
     * 모든 서블릿의 destroy() 메서드를 호출하여 안전하게 정리
     */
    public void destroy() {
        context.log("Destroying servlet container...");

        // === 모든 서블릿 종료 ===

        for (ServletRegistration registration : servlets.values()) {
            try {
                // 초기화된 서블릿만 종료 처리
                if (registration.initialized) {
                    // destroy(): 서블릿 생명주기의 마지막 단계
                    // 서블릿이 메모리에서 제거되기 전에 정리 작업 수행
                    registration.servlet.destroy();

                    context.log("Servlet destroyed: " + registration.servlet.getClass().getSimpleName());
                }
            } catch (Exception e) {
                // 개별 서블릿 종료 중 오류가 발생해도 다른 서블릿들은 계속 처리
                context.log("Error destroying servlet", e);
            }
        }

        // === 컨테이너 정리 ===

        // Map.clear(): 모든 엔트리 제거
        servlets.clear();
        initialized = false;

        context.log("Servlet container destroyed");
    }

    /**
     * 컨테이너 상태 정보
     * 현재 컨테이너의 전반적인 상태를 수집하여 반환
     *
     * @return 컨테이너 상태 정보
     */
    public ContainerStatus getStatus() {
        int initializedServlets = 0;  // 초기화된 서블릿 수
        int asyncServlets = 0;        // 비동기 서블릿 수

        // 모든 등록된 서블릿들을 순회하며 상태 집계
        for (ServletRegistration registration : servlets.values()) {
            if (registration.initialized) {
                initializedServlets++;
            }
            if (registration.async) {
                asyncServlets++;
            }
        }

        // ContainerStatus 객체 생성 및 반환
        return new ContainerStatus(
                initialized,                    // 컨테이너 초기화 여부
                servlets.size(),               // 전체 등록된 서블릿 수
                initializedServlets,           // 초기화 완료된 서블릿 수
                asyncServlets,                 // 비동기 서블릿 수
                context.getUpTime()            // 컨테이너 업타임
        );
    }

    /**
     * 등록된 서블릿 목록
     * 모든 등록된 서블릿의 매핑 정보를 반환
     *
     * @return URL 패턴 -> 서블릿 클래스명의 매핑
     */
    public Map<String, String> getServletMappings() {
        // HashMap: 결과를 담을 새로운 Map
        Map<String, String> mappings = new HashMap<>();

        // Map.entrySet(): Map의 모든 키-값 쌍들
        for (Map.Entry<String, ServletRegistration> entry : servlets.entrySet()) {
            // entry.getKey(): URL 패턴
            // entry.getValue().servlet.getClass().getSimpleName(): 서블릿 클래스명
            mappings.put(entry.getKey(), entry.getValue().servlet.getClass().getSimpleName());
        }

        return mappings;
    }

    /**
     * 컨텍스트 반환
     * 외부에서 컨테이너의 컨텍스트에 접근할 수 있게 함
     *
     * @return MiniContext 인스턴스
     */
    public MiniContext getContext() {
        return context;
    }

    /**
     * 서블릿 등록 정보
     * 서블릿과 관련된 모든 메타데이터를 포함하는 내부 클래스
     *
     * static: 외부 클래스의 인스턴스 없이 생성 가능
     * private: 컨테이너 내부에서만 사용
     */
    private static class ServletRegistration {
        // final: 생성 후 변경 불가능한 필드들
        final MiniServlet servlet;                  // 서블릿 인스턴스
        final String pattern;                       // URL 매칭 패턴
        final Map<String, String> initParams;       // 초기화 매개변수들
        final boolean async;                        // 비동기 서블릿 여부

        // volatile: 멀티스레드에서 안전한 상태 플래그
        volatile boolean initialized = false;       // 초기화 완료 여부

        /**
         * 동기 서블릿 등록 정보 생성
         */
        ServletRegistration(MiniServlet servlet, String pattern, Map<String, String> initParams) {
            // this(): 같은 클래스의 다른 생성자 호출
            this(servlet, pattern, initParams, false);  // async=false
        }

        /**
         * 서블릿 등록 정보 생성 (비동기 여부 지정)
         */
        ServletRegistration(MiniServlet servlet, String pattern, Map<String, String> initParams, boolean async) {
            this.servlet = servlet;
            this.pattern = pattern;

            // new HashMap<>(initParams): 매개변수 Map의 복사본 생성
            // 원본 Map의 변경이 이 객체에 영향을 주지 않도록 방어적 복사
            this.initParams = new HashMap<>(initParams);
            this.async = async;
        }
    }

    /**
     * 컨테이너 상태 정보
     * 컨테이너의 현재 상태를 나타내는 불변 데이터 객체
     *
     * static: 외부 클래스 인스턴스 없이 생성 가능
     * 불변 객체 패턴으로 스레드 안전성 보장
     */
    public static class ContainerStatus {
        // final: 생성 후 변경 불가능한 필드들
        private final boolean initialized;          // 컨테이너 초기화 여부
        private final int totalServlets;           // 전체 등록된 서블릿 수
        private final int initializedServlets;     // 초기화 완료된 서블릿 수
        private final int asyncServlets;           // 비동기 서블릿 수
        private final long uptime;                 // 컨테이너 업타임 (밀리초)

        /**
         * ContainerStatus 생성자
         * 모든 상태 정보를 한번에 초기화하는 불변 객체 패턴
         */
        public ContainerStatus(boolean initialized, int totalServlets,
                               int initializedServlets, int asyncServlets, long uptime) {
            this.initialized = initialized;
            this.totalServlets = totalServlets;
            this.initializedServlets = initializedServlets;
            this.asyncServlets = asyncServlets;
            this.uptime = uptime;
        }

        // === Getter 메서드들 ===
        // 모든 필드에 대한 읽기 전용 접근자

        /**
         * 컨테이너 초기화 여부 반환
         */
        public boolean isInitialized() {
            return initialized;
        }

        /**
         * 전체 등록된 서블릿 수 반환
         */
        public int getTotalServlets() {
            return totalServlets;
        }

        /**
         * 초기화 완료된 서블릿 수 반환
         */
        public int getInitializedServlets() {
            return initializedServlets;
        }

        /**
         * 비동기 서블릿 수 반환
         */
        public int getAsyncServlets() {
            return asyncServlets;
        }

        /**
         * 컨테이너 업타임 반환 (밀리초)
         */
        public long getUptime() {
            return uptime;
        }

        /**
         * 객체의 문자열 표현
         * 주요 상태 정보를 간략하게 요약하여 반환
         *
         * @return 컨테이너 상태의 문자열 표현
         */
        @Override
        public String toString() {
            // String.format(): printf 스타일 포맷팅
            // %s: 문자열, %d: 정수 형식 지정자
            return String.format(
                    "ContainerStatus{initialized=%s, servlets=%d/%d, async=%d, uptime=%ds}",
                    initialized,              // 초기화 여부
                    initializedServlets,      // 초기화된 서블릿 수
                    totalServlets,           // 전체 서블릿 수
                    asyncServlets,           // 비동기 서블릿 수
                    uptime / 1000           // 업타임 (초 단위로 변환)
            );
        }
    }
}