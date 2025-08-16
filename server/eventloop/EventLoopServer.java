package server.eventloop;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.routing.Router;
import server.core.routing.RouteHandler;
import server.core.http.HttpResponse;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 단일 스레드 + Selector 기반 EventLoop 서버
 *
 * 특징:
 * - 단일 스레드에서 모든 I/O 이벤트 처리
 * - NIO Selector를 사용한 논블로킹 I/O
 * - 높은 동시 연결 처리 능력 (10,000+ 연결)
 * - 메모리 효율적 (스레드 풀 없음)
 * - CPU 효율적 (컨텍스트 스위칭 최소화)
 */
public class EventLoopServer {

    private static final Logger logger = LoggerFactory.getLogger(EventLoopServer.class);

    private final Router router;
    private final EventLoopProcessor processor;
    private final AtomicBoolean started;
    private final CountDownLatch shutdownLatch;

    // 서버 설정
    private String host = "localhost";
    private int port = 8082;
    private boolean autoShutdownHook = true;

    public EventLoopServer() throws IOException {
        this(new Router());
    }

    public EventLoopServer(Router router) throws IOException {
        this.router = router;
        this.processor = new EventLoopProcessor(router);
        this.started = new AtomicBoolean(false);
        this.shutdownLatch = new CountDownLatch(1);
    }

    public EventLoopServer(Router router, EventLoopProcessor.ProcessorConfig config) throws IOException {
        this.router = router;
        this.processor = new EventLoopProcessor(router, config);
        this.started = new AtomicBoolean(false);
        this.shutdownLatch = new CountDownLatch(1);
    }

    // === 설정 메서드 ===

    /**
     * 서버 호스트 설정
     */
    public EventLoopServer host(String host) {
        if (started.get()) {
            throw new IllegalStateException("Cannot change host after server started");
        }
        this.host = host;
        return this;
    }

    /**
     * 서버 포트 설정
     */
    public EventLoopServer port(int port) {
        if (started.get()) {
            throw new IllegalStateException("Cannot change port after server started");
        }
        this.port = port;
        return this;
    }

    /**
     * 자동 종료 훅 설정
     */
    public EventLoopServer autoShutdownHook(boolean enable) {
        this.autoShutdownHook = enable;
        return this;
    }

    // === 라우트 등록 편의 메서드 ===

    /**
     * GET 라우트 등록
     */
    public EventLoopServer get(String pattern, RouteHandler handler) {
        router.get(pattern, handler);
        return this;
    }

    /**
     * POST 라우트 등록
     */
    public EventLoopServer post(String pattern, RouteHandler handler) {
        router.post(pattern, handler);
        return this;
    }

    /**
     * PUT 라우트 등록
     */
    public EventLoopServer put(String pattern, RouteHandler handler) {
        router.put(pattern, handler);
        return this;
    }

    /**
     * DELETE 라우트 등록
     */
    public EventLoopServer delete(String pattern, RouteHandler handler) {
        router.delete(pattern, handler);
        return this;
    }

    /**
     * 정적 파일 핸들러 (간단한 구현)
     */
    public EventLoopServer staticFiles(String path, String directory) {
        router.get(path + "/*", RouteHandler.sync(request -> {
            // 간단한 정적 파일 서빙 (실제로는 더 복잡한 구현 필요)
            String filePath = request.getPath().substring(path.length());
            return HttpResponse.text("Static file: " + filePath + " from " + directory);
        }));
        return this;
    }

    // === 서버 생명주기 ===

    /**
     * 서버 시작
     */
    public void start() {
        start(port);
    }

    /**
     * 서버 시작 (포트 지정)
     */
    public void start(int port) {
        start(host, port);
    }

    /**
     * 서버 시작 (호스트, 포트 지정)
     */
    public void start(String host, int port) {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("EventLoopServer already started");
        }

        try {
            logger.info("🚀 Starting EventLoop Server...");
            logger.info("   Host: {}", host);
            logger.info("   Port: {}", port);
            logger.info("   Routes: {}", router.getRouteCount());

            // 기본 라우트 설정 (없는 경우)
            setupDefaultRoutes();

            // 프로세서 시작
            processor.start(host, port);

            // JVM 종료 훅 등록
            if (autoShutdownHook) {
                setupShutdownHook();
            }

            logger.info("✅ EventLoop Server started successfully!");
            logger.info("   Server running at: http://{}:{}", host, port);
            logger.info("   Architecture: Single Thread + NIO Selector");
            logger.info("   Expected concurrent connections: 10,000+");

            // 라우트 정보 출력
            if (logger.isDebugEnabled()) {
                router.printRoutes();
            }

        } catch (IOException e) {
            started.set(false);
            logger.error("❌ Failed to start EventLoop Server", e);
            throw new RuntimeException("Failed to start server", e);
        }
    }

    /**
     * 서버 종료
     */
    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return; // 이미 종료됨
        }

        logger.info("🛑 Stopping EventLoop Server...");

        try {
            processor.shutdown();
            shutdownLatch.countDown();

            logger.info("✅ EventLoop Server stopped successfully");

        } catch (Exception e) {
            logger.error("❌ Error stopping server", e);
        }
    }

    /**
     * 서버가 종료될 때까지 대기
     */
    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }

    /**
     * 타임아웃과 함께 서버 종료 대기
     */
    public boolean awaitShutdown(long timeout, TimeUnit unit) throws InterruptedException {
        return shutdownLatch.await(timeout, unit);
    }

    // === 내부 유틸리티 ===

    /**
     * 기본 라우트 설정
     */
    private void setupDefaultRoutes() {
        // 기본 헬스체크 라우트
        if (router.getRouteCount() == 0) {
            router.get("/", RouteHandler.sync(request ->
                    HttpResponse.html(generateWelcomePage())
            ));
        }

        // 서버 정보 라우트
        router.get("/server/info", RouteHandler.sync(request ->
                HttpResponse.json(generateServerInfo())
        ));

        // 서버 통계 라우트
        router.get("/server/stats", RouteHandler.sync(request ->
                HttpResponse.json(generateServerStats())
        ));

        // 헬스체크 라우트
        router.get("/health", RouteHandler.sync(request ->
                HttpResponse.text("OK")
        ));
    }

    /**
     * 환영 페이지 HTML 생성
     */
    private String generateWelcomePage() {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <title>EventLoop Server</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 50px; background: #f5f5f5; }
                    .container { max-width: 800px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    .header { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 20px; }
                    .feature { background: #ecf0f1; padding: 15px; margin: 10px 0; border-radius: 5px; }
                    .stats { display: flex; gap: 20px; }
                    .stat { background: #3498db; color: white; padding: 15px; border-radius: 5px; text-align: center; flex: 1; }
                    .routes { background: #2ecc71; color: white; padding: 10px; border-radius: 5px; }
                    pre { background: #34495e; color: #ecf0f1; padding: 15px; border-radius: 5px; overflow-x: auto; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🚀 EventLoop Server</h1>
                        <p><strong>Single Thread + NIO Selector Architecture</strong></p>
                    </div>
                    
                    <div class="feature">
                        <h3>⚡ Server Features</h3>
                        <ul>
                            <li><strong>Single Thread:</strong> 모든 I/O를 하나의 스레드에서 처리</li>
                            <li><strong>NIO Selector:</strong> 논블로킹 I/O로 높은 성능</li>
                            <li><strong>High Concurrency:</strong> 10,000+ 동시 연결 지원</li>
                            <li><strong>Memory Efficient:</strong> 스레드 풀 없이 메모리 절약</li>
                            <li><strong>CPU Efficient:</strong> 컨텍스트 스위칭 최소화</li>
                        </ul>
                    </div>
                    
                    <div class="stats">
                        <div class="stat">
                            <h4>Server Port</h4>
                            <div>%d</div>
                        </div>
                        <div class="stat">
                            <h4>Routes</h4>
                            <div>%d</div>
                        </div>
                        <div class="stat">
                            <h4>Architecture</h4>
                            <div>EventLoop</div>
                        </div>
                    </div>
                    
                    <div class="feature">
                        <h3>🔗 API Endpoints</h3>
                        <div class="routes">
                            <a href="/server/info" style="color: white;">GET /server/info</a> - 서버 정보<br>
                            <a href="/server/stats" style="color: white;">GET /server/stats</a> - 실시간 통계<br>
                            <a href="/health" style="color: white;">GET /health</a> - 헬스체크
                        </div>
                    </div>
                    
                    <div class="feature">
                        <h3>💡 Example Usage</h3>
                        <pre>// EventLoop 서버 특징 테스트
curl http://localhost:%d/server/stats

// 동시 연결 테스트 (EventLoop 서버는 이런 테스트에 최적화됨)
for i in {1..1000}; do
  curl http://localhost:%d/health &
done</pre>
                    </div>
                </div>
            </body>
            </html>
            """, getPort(), router.getRouteCount(), getPort(), getPort());
    }

    /**
     * 서버 정보 JSON 생성
     */
    private String generateServerInfo() {
        return String.format("""
            {
                "server": "EventLoop Server",
                "architecture": "Single Thread + NIO Selector",
                "version": "1.0",
                "host": "%s",
                "port": %d,
                "routes": %d,
                "features": [
                    "Single Thread Event Loop",
                    "NIO Selector",
                    "Non-blocking I/O",
                    "High Concurrency (10,000+ connections)",
                    "Memory Efficient",
                    "CPU Efficient"
                ],
                "startTime": %d,
                "uptime": %d
            }""",
                host, getPort(), router.getRouteCount(),
                System.currentTimeMillis(), System.currentTimeMillis());
    }

    /**
     * 서버 통계 JSON 생성
     */
    private String generateServerStats() {
        EventLoopProcessor.ProcessorStats stats = processor.getStats();
        return String.format("""
            {
                "running": %s,
                "eventLoops": %d,
                "tasksExecuted": %d,
                "activeConnections": %d,
                "totalConnections": %d,
                "bytesRead": %d,
                "bytesWritten": %d,
                "architecture": "EventLoop",
                "memoryUsage": {
                    "total": %d,
                    "free": %d,
                    "used": %d
                }
            }""",
                stats.isRunning(),
                stats.getTotalLoops(),
                stats.getTotalTasks(),
                stats.getHandlerStats().getActiveConnections(),
                stats.getSelectorStats().getTotalConnections(),
                stats.getSelectorStats().getBytesRead(),
                stats.getSelectorStats().getBytesWritten(),
                Runtime.getRuntime().totalMemory(),
                Runtime.getRuntime().freeMemory(),
                Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
    }

    /**
     * JVM 종료 훅 설정
     */
    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Received shutdown signal");
            stop();
        }, "EventLoopServer-ShutdownHook"));
    }

    // === 상태 확인 ===

    /**
     * 서버 실행 중인지 확인
     */
    public boolean isRunning() {
        return started.get() && processor.isRunning();
    }

    /**
     * 서버 포트 반환
     */
    public int getPort() {
        return processor.getPort();
    }

    /**
     * 라우터 반환
     */
    public Router getRouter() {
        return router;
    }

    /**
     * 프로세서 반환
     */
    public EventLoopProcessor getProcessor() {
        return processor;
    }

    /**
     * 통계 정보 반환
     */
    public EventLoopProcessor.ProcessorStats getStats() {
        return processor.getStats();
    }

    @Override
    public String toString() {
        return String.format("EventLoopServer{host='%s', port=%d, running=%s, routes=%d}",
                host, port, isRunning(), router.getRouteCount());
    }
}