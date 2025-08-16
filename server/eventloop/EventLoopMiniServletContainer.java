package server.eventloop;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.mini.*;
import server.core.http.*;
import server.core.routing.RouteHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 이벤트 기반 서블릿 컨테이너
 * EventLoop 아키텍처와 미니 서블릿 API를 연결
 */
public class EventLoopMiniServletContainer {

    private static final Logger logger = LoggerFactory.getLogger(EventLoopMiniServletContainer.class);

    private final EventLoopServer server;
    private final MiniContext context;
    private final Map<String, MiniServlet> servlets;
    private final EventQueue eventQueue;

    public EventLoopMiniServletContainer() throws Exception {
        this(new EventLoopServer());
    }

    public EventLoopMiniServletContainer(EventLoopServer server) throws Exception {
        this.server = server;
        this.context = new MiniContext("/");
        this.servlets = new ConcurrentHashMap<>();
        this.eventQueue = server.getProcessor().getEventQueue();

        setupContainer();
    }

    /**
     * 컨테이너 초기 설정
     */
    private void setupContainer() {
        // 컨테이너 정보를 컨텍스트에 설정
        context.setAttribute("container.type", "EventLoop");
        context.setAttribute("container.architecture", "Single Thread + NIO Selector");
        context.setAttribute("container.async", true);

        logger.info("EventLoop MiniServlet Container initialized");
    }

    /**
     * 서블릿 등록
     */
    public EventLoopMiniServletContainer addServlet(String pattern, MiniServlet servlet) {
        try {
            // 서블릿 초기화
            servlet.init(context);
            servlets.put(pattern, servlet);

            // 서블릿 타입에 따른 라우트 핸들러 생성
            RouteHandler handler = createServletHandler(servlet);

            // 라우터에 등록 (모든 HTTP 메서드 지원)
            server.getRouter().all(pattern, handler);

            logger.info("Registered servlet: {} -> {}", pattern, servlet.getClass().getSimpleName());

        } catch (Exception e) {
            logger.error("Failed to register servlet: {}", pattern, e);
            throw new RuntimeException("Failed to register servlet", e);
        }

        return this;
    }

    /**
     * 서블릿 핸들러 생성
     */
    private RouteHandler createServletHandler(MiniServlet servlet) {
        if (servlet instanceof MiniAsyncServlet) {
            // 비동기 서블릿 처리
            return createAsyncServletHandler((MiniAsyncServlet) servlet);
        } else {
            // 동기 서블릿 처리 (EventLoop에서 비동기로 래핑)
            return createSyncServletHandler(servlet);
        }
    }

    /**
     * 비동기 서블릿 핸들러 생성
     */
    private RouteHandler createAsyncServletHandler(MiniAsyncServlet asyncServlet) {
        return request -> {
            // EventLoop 스레드에서 실행되므로 직접 처리
            return eventQueue.executeAsync(() -> {
                try {
                    MiniRequest miniRequest = new MiniRequest(request, context);
                    MiniResponse miniResponse = new MiniResponse();

                    // 비동기 서블릿 처리
                    return asyncServlet.serviceAsync(miniRequest, miniResponse)
                            .exceptionally(error -> {
                                logger.error("Error in async servlet processing", error);
                                return HttpResponse.internalServerError("Async servlet error: " + error.getMessage());
                            });

                } catch (Exception e) {
                    logger.error("Error creating async servlet response", e);
                    return CompletableFuture.completedFuture(
                            HttpResponse.internalServerError("Servlet initialization error"));
                }
            }).thenCompose(future -> future); // CompletableFuture<CompletableFuture<T>>를 CompletableFuture<T>로 평면화
        };
    }

    /**
     * 동기 서블릿 핸들러 생성 (비동기로 래핑)
     */
    private RouteHandler createSyncServletHandler(MiniServlet servlet) {
        return request -> {
            // EventLoop에서 비동기적으로 처리
            return eventQueue.executeAsync(() -> {
                try {
                    MiniRequest miniRequest = new MiniRequest(request, context);
                    MiniResponse miniResponse = new MiniResponse();

                    // 동기 서블릿 처리
                    HttpResponse response = servlet.service(miniRequest, miniResponse);
                    return response;

                } catch (Exception e) {
                    logger.error("Error in sync servlet processing", e);
                    return HttpResponse.internalServerError("Servlet error: " + e.getMessage());
                }
            });
        };
    }

    /**
     * JSP 스타일 서블릿 등록 (간단한 구현)
     */
    public EventLoopMiniServletContainer addJsp(String pattern, String jspContent) {
        MiniServlet jspServlet = new MiniServlet() {
            @Override
            protected void doGet(MiniRequest request, MiniResponse response) throws Exception {
                // 간단한 JSP 스타일 처리 (실제로는 더 복잡한 JSP 엔진 필요)
                String processedContent = processJspContent(jspContent, request);
                response.sendHtml(processedContent);
            }
        };

        return addServlet(pattern, jspServlet);
    }

    /**
     * 간단한 JSP 컨텐츠 처리
     */
    private String processJspContent(String jspContent, MiniRequest request) {
        // 매우 간단한 JSP 스타일 변수 치환
        String processed = jspContent;

        // ${param.name} 형태의 파라미터 치환
        java.util.regex.Pattern paramPattern = java.util.regex.Pattern.compile("\\$\\{param\\.([^}]+)\\}");
        java.util.regex.Matcher matcher = paramPattern.matcher(processed);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String paramName = matcher.group(1);
            String value = request.getParameter(paramName);
            matcher.appendReplacement(sb, value != null ? value : "");
        }
        matcher.appendTail(sb);
        processed = sb.toString();

        // ${request.uri} 같은 요청 정보 치환
        processed = processed.replace("${request.uri}", request.getRequestURI());
        processed = processed.replace("${request.method}", request.getMethod().toString());

        return processed;
    }

    /**
     * 필터 체인 지원 (간단한 구현)
     */
    public EventLoopMiniServletContainer addFilter(String pattern, ServletFilter filter) {
        // 기존 라우트를 필터로 래핑
        server.getRouter().use((request, next) -> {
            if (request.getPath().matches(pattern.replace("*", ".*"))) {
                return eventQueue.executeAsync(() -> {
                    try {
                        MiniRequest miniRequest = new MiniRequest(request, context);
                        MiniResponse miniResponse = new MiniResponse();

                        // 필터 실행
                        if (filter.doFilter(miniRequest, miniResponse)) {
                            // 필터가 통과하면 다음 핸들러 실행
                            return next.handle(request);
                        } else {
                            // 필터가 차단하면 필터의 응답 반환
                            return CompletableFuture.completedFuture(miniResponse.build());
                        }
                    } catch (Exception e) {
                        logger.error("Filter error", e);
                        return CompletableFuture.completedFuture(
                                HttpResponse.internalServerError("Filter error"));
                    }
                }).thenCompose(future -> future);
            } else {
                return next.handle(request);
            }
        });

        return this;
    }

    /**
     * 서블릿 컨텍스트 리스너 등록
     */
    public EventLoopMiniServletContainer addContextListener(ContextListener listener) {
        try {
            listener.contextInitialized(context);
            logger.info("Context listener registered: {}", listener.getClass().getSimpleName());
        } catch (Exception e) {
            logger.error("Error initializing context listener", e);
        }

        return this;
    }

    /**
     * 세션 관리 활성화 (간단한 메모리 기반)
     */
    public EventLoopMiniServletContainer enableSessions() {
        // 간단한 세션 관리자 구현
        SessionManager sessionManager = new InMemorySessionManager();
        context.setAttribute("session.manager", sessionManager);

        // 세션 쿠키 처리 미들웨어 추가
        server.getRouter().use((request, next) -> {
            return eventQueue.executeAsync(() -> {
                // 세션 처리 로직
                String sessionId = extractSessionId(request);
                final String finalSessionId;
                if (sessionId == null) {
                    finalSessionId = sessionManager.createSession();
                } else {
                    finalSessionId = sessionId;
                }

                request.setAttribute("session.id", finalSessionId);
                request.setAttribute("session", sessionManager.getSession(finalSessionId));

                return next.handle(request).thenApply(response -> {
                    // 응답에 세션 쿠키 추가
                    response.setHeader("Set-Cookie", "JSESSIONID=" + finalSessionId + "; Path=/");
                    return response;
                });
            }).thenCompose(future -> future);
        });

        logger.info("Session management enabled");
        return this;
    }

    /**
     * 요청에서 세션 ID 추출
     */
    private String extractSessionId(HttpRequest request) {
        String cookieHeader = request.getHeader("Cookie");
        if (cookieHeader != null) {
            String[] cookies = cookieHeader.split(";");
            for (String cookie : cookies) {
                String[] parts = cookie.trim().split("=", 2);
                if ("JSESSIONID".equals(parts[0]) && parts.length == 2) {
                    return parts[1];
                }
            }
        }
        return null;
    }

    /**
     * 서버 시작
     */
    public void start() {
        start(8082);
    }

    /**
     * 서버 시작 (포트 지정)
     */
    public void start(int port) {
        logger.info("🚀 Starting EventLoop Servlet Container on port {}", port);
        logger.info("   Servlets: {}", servlets.size());
        logger.info("   Architecture: EventLoop + MiniServlet");

        server.start(port);

        logger.info("✅ EventLoop Servlet Container started successfully!");
    }

    /**
     * 서버 종료
     */
    public void stop() {
        logger.info("🛑 Stopping EventLoop Servlet Container...");

        // 모든 서블릿 종료
        for (MiniServlet servlet : servlets.values()) {
            try {
                servlet.destroy();
            } catch (Exception e) {
                logger.error("Error destroying servlet", e);
            }
        }
        servlets.clear();

        server.stop();

        logger.info("✅ EventLoop Servlet Container stopped");
    }

    /**
     * 서버 실행 중인지 확인
     */
    public boolean isRunning() {
        return server.isRunning();
    }

    /**
     * 서블릿 정보 반환
     */
    public Map<String, String> getServletInfo() {
        Map<String, String> info = new ConcurrentHashMap<>();
        for (Map.Entry<String, MiniServlet> entry : servlets.entrySet()) {
            info.put(entry.getKey(), entry.getValue().getClass().getSimpleName());
        }
        return info;
    }

    /**
     * 컨테이너 통계
     */
    public ContainerStats getStats() {
        return new ContainerStats(
                servlets.size(),
                server.getStats(),
                isRunning()
        );
    }

    // === 인터페이스 정의 ===

    /**
     * 서블릿 필터 인터페이스
     */
    @FunctionalInterface
    public interface ServletFilter {
        boolean doFilter(MiniRequest request, MiniResponse response) throws Exception;
    }

    /**
     * 컨텍스트 리스너 인터페이스
     */
    @FunctionalInterface
    public interface ContextListener {
        void contextInitialized(MiniContext context) throws Exception;
    }

    /**
     * 간단한 세션 관리자 인터페이스
     */
    public interface SessionManager {
        String createSession();
        Map<String, Object> getSession(String sessionId);
        void destroySession(String sessionId);
    }

    /**
     * 메모리 기반 세션 관리자
     */
    private static class InMemorySessionManager implements SessionManager {
        private final Map<String, Map<String, Object>> sessions = new ConcurrentHashMap<>();
        private final java.util.concurrent.atomic.AtomicLong sessionIdGenerator =
                new java.util.concurrent.atomic.AtomicLong(0);

        @Override
        public String createSession() {
            String sessionId = "SESSION_" + sessionIdGenerator.incrementAndGet() + "_" + System.currentTimeMillis();
            sessions.put(sessionId, new ConcurrentHashMap<>());
            return sessionId;
        }

        @Override
        public Map<String, Object> getSession(String sessionId) {
            return sessions.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
        }

        @Override
        public void destroySession(String sessionId) {
            sessions.remove(sessionId);
        }
    }

    /**
     * 컨테이너 통계 정보
     */
    public static class ContainerStats {
        private final int servletCount;
        private final EventLoopProcessor.ProcessorStats processorStats;
        private final boolean running;

        public ContainerStats(int servletCount, EventLoopProcessor.ProcessorStats processorStats, boolean running) {
            this.servletCount = servletCount;
            this.processorStats = processorStats;
            this.running = running;
        }

        public int getServletCount() { return servletCount; }
        public EventLoopProcessor.ProcessorStats getProcessorStats() { return processorStats; }
        public boolean isRunning() { return running; }

        @Override
        public String toString() {
            return String.format("ContainerStats{servlets=%d, running=%s, %s}",
                    servletCount, running, processorStats);
        }
    }
}