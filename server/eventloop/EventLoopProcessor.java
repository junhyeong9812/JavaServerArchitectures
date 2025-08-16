package server.eventloop;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.routing.Router;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 이벤트 처리 루프
 * EventLoop, SelectorManager, NonBlockingHandler를 조합하여 완전한 처리 파이프라인 구성
 */
public class EventLoopProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EventLoopProcessor.class);

    private final EventLoop eventLoop;
    private final SelectorManager selectorManager;
    private final EventQueue eventQueue;
    private final NonBlockingHandler handler;
    private final Router router;

    private final AtomicBoolean started;
    private ServerSocketChannel serverChannel;
    private ScheduledFuture<?> cleanupTask;

    // 설정
    private final ProcessorConfig config;

    public EventLoopProcessor(Router router) throws IOException {
        this(router, new ProcessorConfig());
    }

    public EventLoopProcessor(Router router, ProcessorConfig config) throws IOException {
        this.router = router;
        this.config = config;
        this.started = new AtomicBoolean(false);

        // 컴포넌트 초기화
        this.eventLoop = new EventLoop();
        this.selectorManager = new SelectorManager(eventLoop, eventLoop.getSelector());
        this.eventQueue = new EventQueue(eventLoop);
        this.handler = new NonBlockingHandler(router, selectorManager, eventQueue);
    }

    /**
     * 서버 시작
     */
    public void start(int port) throws IOException {
        start("localhost", port);
    }

    /**
     * 서버 시작 (호스트 지정)
     */
    public void start(String host, int port) throws IOException {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("EventLoopProcessor already started");
        }

        logger.info("Starting EventLoopProcessor on {}:{}", host, port);

        try {
            // 서버 소켓 설정
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(host, port));

            // EventLoop 시작
            eventLoop.start();

            // 서버 소켓을 SelectorManager에 등록
            selectorManager.registerServerSocket(serverChannel, handler);

            // 정리 작업 스케줄링
            scheduleCleanupTasks();

            logger.info("EventLoopProcessor started successfully on {}:{}", host, port);

        } catch (IOException e) {
            started.set(false);
            cleanup();
            throw e;
        }
    }

    /**
     * 서버 종료
     */
    public void shutdown() {
        if (!started.compareAndSet(true, false)) {
            return; // 이미 종료됨
        }

        logger.info("Shutting down EventLoopProcessor...");

        try {
            // 정리 작업 취소
            if (cleanupTask != null) {
                cleanupTask.cancel(false);
            }

            // 모든 연결 종료
            selectorManager.closeAllConnections();

            // 서버 소켓 종료
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }

            // EventQueue 종료
            eventQueue.shutdown();

            // EventLoop 종료
            eventLoop.shutdown();

            logger.info("EventLoopProcessor shutdown completed");

        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        } finally {
            cleanup();
        }
    }

    /**
     * 주기적 정리 작업 스케줄링
     */
    private void scheduleCleanupTasks() {
        // 타임아웃 연결 정리 (30초마다)
        cleanupTask = eventQueue.scheduleAtFixedRate(
                handler::cleanupTimeoutConnections,
                config.getCleanupInterval(),
                config.getCleanupInterval(),
                TimeUnit.SECONDS
        );

        // 통계 출력 (1분마다)
        eventQueue.scheduleAtFixedRate(
                this::logStatistics,
                60,
                60,
                TimeUnit.SECONDS
        );
    }

    /**
     * 통계 정보 로그 출력
     */
    private void logStatistics() {
        try {
            SelectorManager.SelectorStats selectorStats = selectorManager.getStats();
            EventQueue.QueueStats queueStats = eventQueue.getStats();
            NonBlockingHandler.HandlerStats handlerStats = handler.getStats();

            logger.info("EventLoop Statistics:");
            logger.info("  EventLoop: {}", eventLoop);
            logger.info("  Selector: {}", selectorStats);
            logger.info("  Queue: {}", queueStats);
            logger.info("  Handler: {}", handlerStats);

        } catch (Exception e) {
            logger.error("Error logging statistics", e);
        }
    }

    /**
     * 리소스 정리
     */
    private void cleanup() {
        // 추가 정리 작업이 필요한 경우 여기에 추가
    }

    /**
     * 비동기 작업 실행
     */
    public void execute(Runnable task) {
        eventQueue.execute(task);
    }

    /**
     * 비동기 작업 실행 (CompletableFuture 반환)
     */
    public <T> java.util.concurrent.CompletableFuture<T> executeAsync(java.util.function.Supplier<T> task) {
        return eventQueue.executeAsync(task);
    }

    /**
     * 지연 실행
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return eventQueue.schedule(task, delay, unit);
    }

    /**
     * 현재 실행 중인지 확인
     */
    public boolean isRunning() {
        return started.get() && eventLoop.isRunning();
    }

    /**
     * 서버 포트 정보
     */
    public int getPort() {
        try {
            if (serverChannel != null && serverChannel.isOpen()) {
                return ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
            }
        } catch (IOException e) {
            logger.error("Error getting server port", e);
        }
        return -1;
    }

    /**
     * EventQueue 반환
     */
    public EventQueue getEventQueue() {
        return eventQueue;
    }

    /**
     * 라우터 반환
     */
    public Router getRouter() {
        return router;
    }

    /**
     * 통계 정보 반환
     */
    public ProcessorStats getStats() {
        return new ProcessorStats(
                isRunning(),
                eventLoop.getTotalLoops(),
                eventLoop.getTotalTasksExecuted(),
                selectorManager.getStats(),
                eventQueue.getStats(),
                handler.getStats()
        );
    }

    /**
     * EventLoopProcessor 설정 클래스
     */
    public static class ProcessorConfig {
        private int cleanupInterval = 30; // 초
        private int connectionTimeout = 30000; // 밀리초
        private int maxRequestSize = 1024 * 1024; // 1MB
        private int responseBufferSize = 8192; // 8KB

        public int getCleanupInterval() { return cleanupInterval; }
        public ProcessorConfig setCleanupInterval(int cleanupInterval) {
            this.cleanupInterval = cleanupInterval;
            return this;
        }

        public int getConnectionTimeout() { return connectionTimeout; }
        public ProcessorConfig setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public int getMaxRequestSize() { return maxRequestSize; }
        public ProcessorConfig setMaxRequestSize(int maxRequestSize) {
            this.maxRequestSize = maxRequestSize;
            return this;
        }

        public int getResponseBufferSize() { return responseBufferSize; }
        public ProcessorConfig setResponseBufferSize(int responseBufferSize) {
            this.responseBufferSize = responseBufferSize;
            return this;
        }
    }

    /**
     * EventLoopProcessor 통계 정보
     */
    public static class ProcessorStats {
        private final boolean running;
        private final long totalLoops;
        private final long totalTasks;
        private final SelectorManager.SelectorStats selectorStats;
        private final EventQueue.QueueStats queueStats;
        private final NonBlockingHandler.HandlerStats handlerStats;

        public ProcessorStats(boolean running, long totalLoops, long totalTasks,
                              SelectorManager.SelectorStats selectorStats,
                              EventQueue.QueueStats queueStats,
                              NonBlockingHandler.HandlerStats handlerStats) {
            this.running = running;
            this.totalLoops = totalLoops;
            this.totalTasks = totalTasks;
            this.selectorStats = selectorStats;
            this.queueStats = queueStats;
            this.handlerStats = handlerStats;
        }

        public boolean isRunning() { return running; }
        public long getTotalLoops() { return totalLoops; }
        public long getTotalTasks() { return totalTasks; }
        public SelectorManager.SelectorStats getSelectorStats() { return selectorStats; }
        public EventQueue.QueueStats getQueueStats() { return queueStats; }
        public NonBlockingHandler.HandlerStats getHandlerStats() { return handlerStats; }

        @Override
        public String toString() {
            return String.format("ProcessorStats{running=%s, loops=%d, tasks=%d, %s, %s, %s}",
                    running, totalLoops, totalTasks, selectorStats, queueStats, handlerStats);
        }
    }
}