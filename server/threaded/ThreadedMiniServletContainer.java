package server.threaded;

import server.core.mini.*;
import server.core.http.*;
import server.core.routing.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.*;

/**
 * 스레드 기반 미니 서블릿 컨테이너
 * 서블릿 생명주기 관리 및 요청 라우팅
 */
public class ThreadedMiniServletContainer {

    private final MiniContext context;
    private final Map<String, ServletRegistration> servlets;
    private final Router fallbackRouter;
    private volatile boolean initialized = false;

    public ThreadedMiniServletContainer(String contextPath) {
        this.context = new MiniContext(contextPath);
        this.servlets = new ConcurrentHashMap<>();
        this.fallbackRouter = new Router();

        // 기본 설정
        setupDefaultConfiguration();
    }

    /**
     * 기본 설정
     */
    private void setupDefaultConfiguration() {
        context.setInitParameter("container.type", "threaded");
        context.setInitParameter("servlet.version", "mini-1.0");
        context.log("ThreadedMiniServletContainer initialized");
    }

    /**
     * 서블릿 등록
     */
    public void registerServlet(String pattern, MiniServlet servlet) {
        registerServlet(pattern, servlet, new HashMap<>());
    }

    /**
     * 서블릿 등록 (초기화 파라미터 포함)
     */
    public void registerServlet(String pattern, MiniServlet servlet, Map<String, String> initParams) {
        if (initialized) {
            throw new IllegalStateException("Cannot register servlet after container initialization");
        }

        ServletRegistration registration = new ServletRegistration(servlet, pattern, initParams);
        servlets.put(pattern, registration);

        context.log("Servlet registered: " + servlet.getClass().getSimpleName() + " -> " + pattern);
    }

    /**
     * 비동기 서블릿 등록
     */
    public void registerAsyncServlet(String pattern, MiniAsyncServlet servlet) {
        registerAsyncServlet(pattern, servlet, new HashMap<>());
    }

    /**
     * 비동기 서블릿 등록 (초기화 파라미터 포함)
     */
    public void registerAsyncServlet(String pattern, MiniAsyncServlet servlet, Map<String, String> initParams) {
        if (initialized) {
            throw new IllegalStateException("Cannot register servlet after container initialization");
        }

        ServletRegistration registration = new ServletRegistration(servlet, pattern, initParams, true);
        servlets.put(pattern, registration);

        context.log("Async Servlet registered: " + servlet.getClass().getSimpleName() + " -> " + pattern);
    }

    /**
     * Router 기반 핸들러 등록
     */
    public void registerHandler(String pattern, RouteHandler handler) {
        fallbackRouter.all(pattern, handler);
        context.log("Handler registered: " + pattern);
    }

    /**
     * 컨테이너 초기화
     */
    public void initialize() throws Exception {
        if (initialized) {
            return;
        }

        context.log("Initializing servlet container...");

        // 등록된 모든 서블릿 초기화
        for (ServletRegistration registration : servlets.values()) {
            try {
                // 서블릿별 컨텍스트 설정
                MiniContext servletContext = createServletContext(registration);
                registration.servlet.init(servletContext);
                registration.initialized = true;

                context.log("Servlet initialized: " + registration.servlet.getClass().getSimpleName());

            } catch (Exception e) {
                context.log("Failed to initialize servlet: " + registration.servlet.getClass().getSimpleName(), e);
                throw e;
            }
        }

        initialized = true;
        context.log("Servlet container initialization completed - " + servlets.size() + " servlets loaded");
    }

    /**
     * 서블릿별 컨텍스트 생성
     */
    private MiniContext createServletContext(ServletRegistration registration) {
        MiniContext servletContext = new MiniContext(context.getContextPath());

        // 글로벌 초기화 파라미터 복사
        for (String paramName : context.getInitParameterNames()) {
            servletContext.setInitParameter(paramName, context.getInitParameter(paramName));
        }

        // 서블릿별 초기화 파라미터 추가
        for (Map.Entry<String, String> entry : registration.initParams.entrySet()) {
            servletContext.setInitParameter(entry.getKey(), entry.getValue());
        }

        // 서블릿 정보 설정
        servletContext.setAttribute("servlet.pattern", registration.pattern);
        servletContext.setAttribute("servlet.async", registration.async);

        return servletContext;
    }

    /**
     * HTTP 요청 처리 (동기)
     */
    public CompletableFuture<HttpResponse> handleRequest(HttpRequest httpRequest) {
        if (!initialized) {
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Container not initialized"));
        }

        // 매칭되는 서블릿 찾기
        ServletRegistration registration = findMatchingServlet(httpRequest.getPath());

        if (registration != null) {
            return handleServletRequest(registration, httpRequest);
        }

        // 서블릿이 없으면 fallback 라우터 사용
        return fallbackRouter.routeWithMiddlewares(httpRequest);
    }

    /**
     * 매칭되는 서블릿 찾기
     */
    private ServletRegistration findMatchingServlet(String path) {
        // 정확한 매칭 우선
        ServletRegistration exact = servlets.get(path);
        if (exact != null) {
            return exact;
        }

        // 패턴 매칭 (간단한 구현)
        for (ServletRegistration registration : servlets.values()) {
            if (matchesPattern(path, registration.pattern)) {
                return registration;
            }
        }

        return null;
    }

    /**
     * 간단한 패턴 매칭
     */
    private boolean matchesPattern(String path, String pattern) {
        if (pattern.equals("/*")) {
            return true;
        }

        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return path.startsWith(prefix);
        }

        return path.equals(pattern);
    }

    /**
     * 서블릿 요청 처리
     */
    private CompletableFuture<HttpResponse> handleServletRequest(ServletRegistration registration,
                                                                 HttpRequest httpRequest) {
        try {
            MiniRequest miniRequest = new MiniRequest(httpRequest, context);
            MiniResponse miniResponse = new MiniResponse();

            // 패턴 정보 설정
            miniRequest.setAttribute("servlet.pattern", registration.pattern);

            if (registration.async && registration.servlet instanceof MiniAsyncServlet) {
                // 비동기 서블릿 처리
                MiniAsyncServlet asyncServlet = (MiniAsyncServlet) registration.servlet;
                return asyncServlet.serviceAsync(miniRequest, miniResponse);

            } else {
                // 동기 서블릿 처리 (별도 스레드에서)
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return registration.servlet.service(miniRequest, miniResponse);
                    } catch (Exception e) {
                        context.log("Servlet processing error", e);
                        return HttpResponse.internalServerError("Servlet error: " + e.getMessage());
                    }
                });
            }

        } catch (Exception e) {
            context.log("Request handling error", e);
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Request handling error: " + e.getMessage()));
        }
    }

    /**
     * 컨테이너 종료
     */
    public void destroy() {
        context.log("Destroying servlet container...");

        // 모든 서블릿 종료
        for (ServletRegistration registration : servlets.values()) {
            try {
                if (registration.initialized) {
                    registration.servlet.destroy();
                    context.log("Servlet destroyed: " + registration.servlet.getClass().getSimpleName());
                }
            } catch (Exception e) {
                context.log("Error destroying servlet", e);
            }
        }

        servlets.clear();
        initialized = false;

        context.log("Servlet container destroyed");
    }

    /**
     * 컨테이너 상태 정보
     */
    public ContainerStatus getStatus() {
        int initializedServlets = 0;
        int asyncServlets = 0;

        for (ServletRegistration registration : servlets.values()) {
            if (registration.initialized) {
                initializedServlets++;
            }
            if (registration.async) {
                asyncServlets++;
            }
        }

        return new ContainerStatus(
                initialized,
                servlets.size(),
                initializedServlets,
                asyncServlets,
                context.getUpTime()
        );
    }

    /**
     * 등록된 서블릿 목록
     */
    public Map<String, String> getServletMappings() {
        Map<String, String> mappings = new HashMap<>();
        for (Map.Entry<String, ServletRegistration> entry : servlets.entrySet()) {
            mappings.put(entry.getKey(), entry.getValue().servlet.getClass().getSimpleName());
        }
        return mappings;
    }

    /**
     * 컨텍스트 반환
     */
    public MiniContext getContext() {
        return context;
    }

    /**
     * 서블릿 등록 정보
     */
    private static class ServletRegistration {
        final MiniServlet servlet;
        final String pattern;
        final Map<String, String> initParams;
        final boolean async;
        volatile boolean initialized = false;

        ServletRegistration(MiniServlet servlet, String pattern, Map<String, String> initParams) {
            this(servlet, pattern, initParams, false);
        }

        ServletRegistration(MiniServlet servlet, String pattern, Map<String, String> initParams, boolean async) {
            this.servlet = servlet;
            this.pattern = pattern;
            this.initParams = new HashMap<>(initParams);
            this.async = async;
        }
    }

    /**
     * 컨테이너 상태 정보
     */
    public static class ContainerStatus {
        private final boolean initialized;
        private final int totalServlets;
        private final int initializedServlets;
        private final int asyncServlets;
        private final long uptime;

        public ContainerStatus(boolean initialized, int totalServlets,
                               int initializedServlets, int asyncServlets, long uptime) {
            this.initialized = initialized;
            this.totalServlets = totalServlets;
            this.initializedServlets = initializedServlets;
            this.asyncServlets = asyncServlets;
            this.uptime = uptime;
        }

        public boolean isInitialized() { return initialized; }
        public int getTotalServlets() { return totalServlets; }
        public int getInitializedServlets() { return initializedServlets; }
        public int getAsyncServlets() { return asyncServlets; }
        public long getUptime() { return uptime; }

        @Override
        public String toString() {
            return String.format(
                    "ContainerStatus{initialized=%s, servlets=%d/%d, async=%d, uptime=%ds}",
                    initialized, initializedServlets, totalServlets, asyncServlets, uptime / 1000
            );
        }
    }
}