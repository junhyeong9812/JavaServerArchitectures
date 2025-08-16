package server.hybrid;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.http.*;
import server.core.routing.*;
import server.core.mini.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * 하이브리드 미니 서블릿 컨테이너
 *
 * 기능:
 * 1. 하이브리드 방식의 서블릿 생명주기 관리
 * 2. 비동기 서블릿 처리 지원
 * 3. 라우팅 시스템과 서블릿 통합
 * 4. 컨텍스트 스위칭 최적화
 * 5. 서블릿 풀링 및 재사용
 */
public class HybridMiniServletContainer {

    private static final Logger logger = LoggerFactory.getLogger(HybridMiniServletContainer.class);

    // === 핵심 컴포넌트 ===
    private final Router router;                        // HTTP 라우팅 시스템
    private final HybridProcessor processor;            // 하이브리드 요청 처리기

    // === 서블릿 관리 ===
    private final ConcurrentMap<String, ServletInfo> servlets;     // 등록된 서블릿들
    private final ConcurrentMap<String, String> pathToServlet;     // 경로 -> 서블릿 매핑
    private final AtomicLong servletRequestCounter = new AtomicLong(0);

    // === 컨텍스트 관리 ===
    private final MiniContext globalContext;           // 글로벌 컨텍스트
    private final ConcurrentMap<String, MiniContext> contexts;     // 컨텍스트별 설정

    // === 서블릿 풀링 (성능 최적화) ===
    private final ConcurrentMap<String, Queue<MiniServlet>> servletPools;
    private final int maxServletPoolSize = 10;         // 풀 최대 크기

    // === 통계 및 모니터링 ===
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong asyncRequests = new AtomicLong(0);
    private final AtomicLong completedRequests = new AtomicLong(0);
    private final AtomicLong errorRequests = new AtomicLong(0);

    /**
     * HybridMiniServletContainer 생성자
     */
    public HybridMiniServletContainer(Router router, HybridProcessor processor) {
        this.router = router;
        this.processor = processor;

        // 컬렉션 초기화
        this.servlets = new ConcurrentHashMap<>();
        this.pathToServlet = new ConcurrentHashMap<>();
        this.contexts = new ConcurrentHashMap<>();
        this.servletPools = new ConcurrentHashMap<>();

        // 글로벌 컨텍스트 초기화
        this.globalContext = new MiniContext("/");

        // 기본 라우트 핸들러 등록
        registerDefaultHandler();

        logger.info("HybridMiniServletContainer 초기화 완료");
    }

    /**
     * 기본 라우트 핸들러 등록
     */
    private void registerDefaultHandler() {
        router.addRoute("*", "*", this::handleServletRequest);
        logger.debug("기본 서블릿 핸들러 등록 완료");
    }

    /**
     * 서블릿 등록
     */
    public void registerServlet(String name, MiniServlet servlet, String... patterns) {
        try {
            // 서블릿 정보 생성
            ServletInfo servletInfo = new ServletInfo(name, servlet, patterns);

            // 서블릿 초기화
            servlet.init(globalContext);

            // 서블릿 등록
            servlets.put(name, servletInfo);

            // 패턴별 매핑 등록
            for (String pattern : patterns) {
                pathToServlet.put(pattern, name);
                logger.debug("서블릿 패턴 매핑 등록: {} -> {}", pattern, name);
            }

            // 서블릿 풀 초기화
            initializeServletPool(name, servlet);

            logger.info("서블릿 등록 완료: {} (패턴: {})", name, Arrays.toString(patterns));

        } catch (Exception e) {
            logger.error("서블릿 등록 실패: {}", name, e);
            throw new RuntimeException("서블릿 등록 실패: " + name, e);
        }
    }

    /**
     * 서블릿 풀 초기화
     */
    private void initializeServletPool(String servletName, MiniServlet prototype) {
        Queue<MiniServlet> pool = new ConcurrentLinkedQueue<>();

        try {
            // 프로토타입을 기반으로 추가 인스턴스 생성
            for (int i = 0; i < 3; i++) {
                MiniServlet instance = createServletInstance(prototype);
                instance.init(globalContext);
                pool.offer(instance);
            }

            servletPools.put(servletName, pool);
            logger.debug("서블릿 풀 초기화 완료: {} (초기 크기: {})", servletName, pool.size());

        } catch (Exception e) {
            logger.warn("서블릿 풀 초기화 실패: {}", servletName, e);
        }
    }

    /**
     * 서블릿 인스턴스 생성 (클론)
     */
    private MiniServlet createServletInstance(MiniServlet prototype) throws Exception {
        Class<?> servletClass = prototype.getClass();
        return (MiniServlet) servletClass.getDeclaredConstructor().newInstance();
    }

    /**
     * 서블릿 요청 처리 - 하이브리드 컨테이너의 핵심 로직
     */
    private CompletableFuture<HttpResponse> handleServletRequest(HttpRequest request) {
        totalRequests.incrementAndGet();

        try {
            // 1. 요청 경로에 맞는 서블릿 찾기
            String servletName = findServletForPath(request.getPath());

            if (servletName == null) {
                logger.debug("서블릿을 찾을 수 없음 - 경로: {}", request.getPath());
                return CompletableFuture.completedFuture(
                        HttpResponse.notFound("No servlet found for path: " + request.getPath())
                );
            }

            ServletInfo servletInfo = servlets.get(servletName);
            if (servletInfo == null) {
                logger.warn("등록되지 않은 서블릿: {}", servletName);
                return CompletableFuture.completedFuture(
                        HttpResponse.internalServerError("Servlet not found: " + servletName)
                );
            }

            // 2. 서블릿 타입에 따른 처리 분기
            if (servletInfo.getServlet() instanceof MiniAsyncServlet) {
                return handleAsyncServlet(request, servletInfo);
            } else {
                return handleSyncServlet(request, servletInfo);
            }

        } catch (Exception e) {
            logger.error("서블릿 요청 처리 중 오류", e);
            errorRequests.incrementAndGet();
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Servlet processing error")
            );
        }
    }

    /**
     * 비동기 서블릿 처리
     */
    private CompletableFuture<HttpResponse> handleAsyncServlet(HttpRequest request, ServletInfo servletInfo) {
        asyncRequests.incrementAndGet();

        logger.debug("비동기 서블릿 처리 시작 - 서블릿: {}, URI: {}",
                servletInfo.getName(), request.getPath());

        MiniAsyncServlet asyncServlet = (MiniAsyncServlet) borrowServletFromPool(servletInfo.getName());

        if (asyncServlet == null) {
            asyncServlet = (MiniAsyncServlet) servletInfo.getServlet();
        }

        try {
            MiniRequest miniRequest = new MiniRequest(request, globalContext);
            MiniResponse miniResponse = new MiniResponse();

            CompletableFuture<Void> servletFuture = asyncServlet.serviceAsync(miniRequest, miniResponse);

            final MiniAsyncServlet finalServlet = asyncServlet;

            return servletFuture
                    .thenApply(unused -> {
                        completedRequests.incrementAndGet();
                        logger.debug("비동기 서블릿 처리 완료 - 서블릿: {}", servletInfo.getName());
                        return miniResponse.build();
                    })
                    .whenComplete((response, throwable) -> {
                        returnServletToPool(servletInfo.getName(), finalServlet);

                        if (throwable != null) {
                            logger.error("비동기 서블릿 처리 실패", throwable);
                            errorRequests.incrementAndGet();
                        }
                    })
                    .exceptionally(throwable -> {
                        logger.error("비동기 서블릿 예외", throwable);
                        errorRequests.incrementAndGet();
                        return HttpResponse.internalServerError("Async servlet error");
                    });

        } catch (Exception e) {
            returnServletToPool(servletInfo.getName(), asyncServlet);
            logger.error("비동기 서블릿 호출 실패", e);
            errorRequests.incrementAndGet();
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Async servlet call failed")
            );
        }
    }

    /**
     * 동기 서블릿 처리 (하이브리드 방식)
     */
    private CompletableFuture<HttpResponse> handleSyncServlet(HttpRequest request, ServletInfo servletInfo) {
        logger.debug("동기 서블릿 하이브리드 처리 시작 - 서블릿: {}, URI: {}",
                servletInfo.getName(), request.getPath());

        RouteHandler servletHandler = (req) -> {
            MiniServlet servlet = borrowServletFromPool(servletInfo.getName());

            if (servlet == null) {
                servlet = servletInfo.getServlet();
            }

            try {
                MiniRequest miniRequest = new MiniRequest(req, globalContext);
                MiniResponse miniResponse = new MiniResponse();

                HttpResponse response = servlet.service(miniRequest, miniResponse);

                completedRequests.incrementAndGet();
                logger.debug("동기 서블릿 처리 완료 - 서블릿: {}", servletInfo.getName());

                return CompletableFuture.completedFuture(response);

            } catch (Exception e) {
                logger.error("동기 서블릿 처리 실패", e);
                errorRequests.incrementAndGet();
                return CompletableFuture.completedFuture(
                        HttpResponse.internalServerError("Sync servlet error")
                );
            } finally {
                returnServletToPool(servletInfo.getName(), servlet);
            }
        };

        return processor.processRequest(request, servletHandler);
    }

    /**
     * 요청 경로에 맞는 서블릿 찾기
     */
    private String findServletForPath(String path) {
        // 1. 정확한 매칭 우선
        String servletName = pathToServlet.get(path);
        if (servletName != null) {
            return servletName;
        }

        // 2. 패턴 매칭
        for (Map.Entry<String, String> entry : pathToServlet.entrySet()) {
            String pattern = entry.getKey();
            String name = entry.getValue();

            if (matchesPattern(path, pattern)) {
                return name;
            }
        }

        return null;
    }

    /**
     * URL 패턴 매칭
     */
    private boolean matchesPattern(String path, String pattern) {
        if ("*".equals(pattern)) {
            return true;
        }

        if (pattern.equals(path)) {
            return true;
        }

        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return path.startsWith(prefix);
        }

        if (pattern.startsWith("*.")) {
            String extension = pattern.substring(1);
            return path.endsWith(extension);
        }

        return false;
    }

    /**
     * 서블릿 풀에서 인스턴스 가져오기
     */
    private MiniServlet borrowServletFromPool(String servletName) {
        Queue<MiniServlet> pool = servletPools.get(servletName);

        if (pool != null) {
            MiniServlet servlet = pool.poll();
            if (servlet != null) {
                logger.debug("서블릿 풀에서 인스턴스 가져옴: {} (풀 크기: {})",
                        servletName, pool.size());
                return servlet;
            }
        }

        return null;
    }

    /**
     * 서블릿을 풀로 반환
     */
    private void returnServletToPool(String servletName, MiniServlet servlet) {
        if (servlet == null) return;

        Queue<MiniServlet> pool = servletPools.get(servletName);

        if (pool != null && pool.size() < maxServletPoolSize) {
            pool.offer(servlet);
            logger.debug("서블릿 풀로 반환: {} (풀 크기: {})", servletName, pool.size());
        } else {
            try {
                servlet.destroy();
                logger.debug("서블릿 인스턴스 파괴: {}", servletName);
            } catch (Exception e) {
                logger.warn("서블릿 파괴 중 오류", e);
            }
        }
    }

    /**
     * 컨텍스트 등록
     */
    public void registerContext(String contextPath, MiniContext context) {
        contexts.put(contextPath, context);
        logger.info("컨텍스트 등록: {}", contextPath);
    }

    /**
     * 서블릿 해제
     */
    public void unregisterServlet(String servletName) {
        try {
            ServletInfo servletInfo = servlets.remove(servletName);

            if (servletInfo != null) {
                for (String pattern : servletInfo.getPatterns()) {
                    pathToServlet.remove(pattern);
                }

                servletInfo.getServlet().destroy();
                destroyServletPool(servletName);

                logger.info("서블릿 해제 완료: {}", servletName);
            }

        } catch (Exception e) {
            logger.error("서블릿 해제 실패: {}", servletName, e);
        }
    }

    /**
     * 서블릿 풀 파괴
     */
    private void destroyServletPool(String servletName) {
        Queue<MiniServlet> pool = servletPools.remove(servletName);

        if (pool != null) {
            int poolSize = pool.size();

            MiniServlet servlet;
            while ((servlet = pool.poll()) != null) {
                try {
                    servlet.destroy();
                } catch (Exception e) {
                    logger.warn("풀 서블릿 파괴 중 오류", e);
                }
            }

            logger.debug("서블릿 풀 파괴 완료: {} (파괴된 인스턴스: {})", servletName, poolSize);
        }
    }

    // === Getters ===

    public Set<String> getServletNames() {
        return new HashSet<>(servlets.keySet());
    }

    public ServletInfo getServletInfo(String servletName) {
        return servlets.get(servletName);
    }

    public ContainerStats getStats() {
        return new ContainerStats(
                servlets.size(),
                totalRequests.get(),
                asyncRequests.get(),
                completedRequests.get(),
                errorRequests.get(),
                servletRequestCounter.get(),
                calculateAveragePoolSize()
        );
    }

    private double calculateAveragePoolSize() {
        if (servletPools.isEmpty()) return 0.0;

        int totalSize = servletPools.values().stream()
                .mapToInt(Queue::size)
                .sum();

        return (double) totalSize / servletPools.size();
    }

    /**
     * 컨테이너 종료
     */
    public void shutdown() {
        logger.info("HybridMiniServletContainer 종료 시작...");

        try {
            Set<String> servletNames = new HashSet<>(servlets.keySet());
            for (String servletName : servletNames) {
                unregisterServlet(servletName);
            }

            contexts.clear();
            logger.info("HybridMiniServletContainer 종료 완료");

        } catch (Exception e) {
            logger.error("컨테이너 종료 중 오류", e);
        }
    }

    // === 내부 클래스들 ===

    public static class ServletInfo {
        private final String name;
        private final MiniServlet servlet;
        private final String[] patterns;
        private final long registeredTime;

        public ServletInfo(String name, MiniServlet servlet, String[] patterns) {
            this.name = name;
            this.servlet = servlet;
            this.patterns = patterns.clone();
            this.registeredTime = System.currentTimeMillis();
        }

        public String getName() { return name; }
        public MiniServlet getServlet() { return servlet; }
        public String[] getPatterns() { return patterns.clone(); }
        public long getRegisteredTime() { return registeredTime; }

        @Override
        public String toString() {
            return String.format("ServletInfo{name='%s', patterns=%s, class=%s}",
                    name, Arrays.toString(patterns), servlet.getClass().getSimpleName());
        }
    }

    public static class ContainerStats {
        private final int servletCount;
        private final long totalRequests;
        private final long asyncRequests;
        private final long completedRequests;
        private final long errorRequests;
        private final long servletRequests;
        private final double averagePoolSize;

        public ContainerStats(int servletCount, long totalRequests, long asyncRequests,
                              long completedRequests, long errorRequests, long servletRequests,
                              double averagePoolSize) {
            this.servletCount = servletCount;
            this.totalRequests = totalRequests;
            this.asyncRequests = asyncRequests;
            this.completedRequests = completedRequests;
            this.errorRequests = errorRequests;
            this.servletRequests = servletRequests;
            this.averagePoolSize = averagePoolSize;
        }

        // Getters
        public int getServletCount() { return servletCount; }
        public long getTotalRequests() { return totalRequests; }
        public long getAsyncRequests() { return asyncRequests; }
        public long getCompletedRequests() { return completedRequests; }
        public long getErrorRequests() { return errorRequests; }
        public long getServletRequests() { return servletRequests; }
        public double getAveragePoolSize() { return averagePoolSize; }

        public double getErrorRate() {
            return totalRequests > 0 ? (double) errorRequests / totalRequests * 100 : 0.0;
        }

        public double getAsyncRate() {
            return totalRequests > 0 ? (double) asyncRequests / totalRequests * 100 : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                    "ContainerStats{servlets=%d, requests=%d, async=%d (%.1f%%), " +
                            "completed=%d, errors=%d (%.1f%%), avgPoolSize=%.1f}",
                    servletCount, totalRequests, asyncRequests, getAsyncRate(),
                    completedRequests, errorRequests, getErrorRate(), averagePoolSize
            );
        }
    }
}