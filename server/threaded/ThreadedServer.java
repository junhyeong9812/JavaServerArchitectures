package server.threaded;

import server.core.routing.Router;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 스레드 기반 HTTP 서버
 * ServerSocket + Thread Pool을 사용한 전통적인 서버 아키텍처
 */
public class ThreadedServer {

    private final int port;
    private final Router router;
    private final ThreadedProcessor processor;
    private final ThreadedMiniServletContainer servletContainer;
    private final ServerConfig config;

    private ServerSocket serverSocket;
    private Thread acceptorThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // 통계 정보
    private volatile long startTime;
    private volatile long totalAcceptedConnections = 0;
    private volatile long totalFailedConnections = 0;

    public ThreadedServer(int port) {
        this(port, new Router(), new ServerConfig());
    }

    public ThreadedServer(int port, Router router) {
        this(port, router, new ServerConfig());
    }

    public ThreadedServer(int port, Router router, ServerConfig config) {
        this.port = port;
        this.router = router;
        this.config = config;

        // ThreadedProcessor 초기화
        this.processor = new ThreadedProcessor(
                router,
                config.getThreadPoolConfig(),
                config.getRequestHandlerConfig()
        );

        // 서블릿 컨테이너 초기화
        this.servletContainer = new ThreadedMiniServletContainer(config.getContextPath());

        System.out.println("[ThreadedServer] Initialized on port " + port);
    }

    /**
     * 서버 초기화
     */
    public void initialize() throws Exception {
        if (initialized.get()) {
            return;
        }

        System.out.println("[ThreadedServer] Initializing server...");

        try {
            // ServerSocket 생성 및 설정
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.setSoTimeout(1000); // Accept timeout for graceful shutdown

            // 소켓 옵션 설정
            if (config.getReceiveBufferSize() > 0) {
                serverSocket.setReceiveBufferSize(config.getReceiveBufferSize());
            }

            // 포트 바인딩
            InetSocketAddress address = new InetSocketAddress(config.getBindAddress(), port);
            serverSocket.bind(address, config.getBacklogSize());

            // 서블릿 컨테이너 초기화
            servletContainer.initialize();

            initialized.set(true);
            System.out.println("[ThreadedServer] Server initialized successfully");
            System.out.println("  - Listening on: " + address);
            System.out.println("  - Backlog size: " + config.getBacklogSize());
            System.out.println("  - Thread pool: " + config.getThreadPoolConfig().getCorePoolSize() +
                    "-" + config.getThreadPoolConfig().getMaxPoolSize());

        } catch (Exception e) {
            System.err.println("[ThreadedServer] Initialization failed: " + e.getMessage());
            cleanup();
            throw e;
        }
    }

    /**
     * 서버 시작
     */
    public void start() throws Exception {
        if (running.get()) {
            throw new IllegalStateException("Server is already running");
        }

        if (!initialized.get()) {
            initialize();
        }

        running.set(true);
        startTime = System.currentTimeMillis();

        // Accept 스레드 시작
        acceptorThread = new Thread(this::acceptLoop, "ServerAcceptor-" + port);
        acceptorThread.start();

        System.out.println("[ThreadedServer] Server started on port " + port);

        // 통계 출력 스케줄러 (디버그 모드)
        if (config.isDebugMode()) {
            startStatisticsReporter();
        }
    }

    /**
     * 연결 수락 루프
     */
    private void acceptLoop() {
        System.out.println("[ThreadedServer] Accept loop started");

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // 클라이언트 연결 수락
                Socket clientSocket = serverSocket.accept();
                totalAcceptedConnections++;

                // 소켓 옵션 설정
                configureClientSocket(clientSocket);

                // 연결을 ThreadedProcessor에 위임
                processor.processConnection(clientSocket);

            } catch (SocketTimeoutException e) {
                // 정상적인 타임아웃 - 셧다운 체크를 위함
                continue;

            } catch (IOException e) {
                if (running.get()) {
                    totalFailedConnections++;
                    System.err.println("[ThreadedServer] Accept failed: " + e.getMessage());

                    // 연속적인 실패시 잠시 대기
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                totalFailedConnections++;
                System.err.println("[ThreadedServer] Unexpected error in accept loop: " + e.getMessage());
            }
        }

        System.out.println("[ThreadedServer] Accept loop terminated");
    }

    /**
     * 클라이언트 소켓 설정
     */
    private void configureClientSocket(Socket clientSocket) throws IOException {
        clientSocket.setTcpNoDelay(config.isTcpNoDelay());
        clientSocket.setKeepAlive(config.isKeepAlive());

        if (config.getSendBufferSize() > 0) {
            clientSocket.setSendBufferSize(config.getSendBufferSize());
        }
        if (config.getReceiveBufferSize() > 0) {
            clientSocket.setReceiveBufferSize(config.getReceiveBufferSize());
        }
    }

    /**
     * 통계 리포터 시작
     */
    private void startStatisticsReporter() {
        Thread statsThread = new Thread(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(30000); // 30초마다 출력
                    if (running.get()) {
                        printDetailedStatistics();
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "StatisticsReporter-" + port);

        statsThread.setDaemon(true);
        statsThread.start();
    }

    /**
     * 서블릿 등록
     */
    public ThreadedServer registerServlet(String pattern, server.core.mini.MiniServlet servlet) {
        servletContainer.registerServlet(pattern, servlet);
        return this;
    }

    /**
     * 비동기 서블릿 등록
     */
    public ThreadedServer registerAsyncServlet(String pattern, server.core.mini.MiniAsyncServlet servlet) {
        servletContainer.registerAsyncServlet(pattern, servlet);
        return this;
    }

    /**
     * 라우터 핸들러 등록
     */
    public ThreadedServer registerHandler(String pattern, server.core.routing.RouteHandler handler) {
        servletContainer.registerHandler(pattern, handler);
        return this;
    }

    /**
     * 서버 중지
     */
    public void stop() {
        if (!running.get()) {
            return;
        }

        System.out.println("[ThreadedServer] Stopping server...");
        running.set(false);

        // Accept 스레드 중지
        if (acceptorThread != null) {
            acceptorThread.interrupt();
            try {
                acceptorThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // ServerSocket 닫기
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[ThreadedServer] Error closing server socket: " + e.getMessage());
        }

        // ThreadedProcessor 종료
        processor.shutdown();

        // 서블릿 컨테이너 종료
        servletContainer.destroy();

        // 최종 통계 출력
        printDetailedStatistics();

        System.out.println("[ThreadedServer] Server stopped");
    }

    /**
     * 정리 작업
     */
    private void cleanup() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // 무시
        }
        initialized.set(false);
    }

    /**
     * 서버 상태 정보
     */
    public ServerStatus getStatus() {
        ThreadedProcessor.ProcessorStatus processorStatus = processor.getStatus();
        ThreadedMiniServletContainer.ContainerStatus containerStatus = servletContainer.getStatus();

        return new ServerStatus(
                running.get(),
                initialized.get(),
                port,
                System.currentTimeMillis() - startTime,
                totalAcceptedConnections,
                totalFailedConnections,
                processorStatus,
                containerStatus
        );
    }

    /**
     * 상세 통계 출력
     */
    public void printDetailedStatistics() {
        ServerStatus status = getStatus();

        System.out.println("\n" + "=".repeat(50));
        System.out.println("ThreadedServer Statistics (Port " + port + ")");
        System.out.println("=".repeat(50));
        System.out.println("Status: " + (status.isRunning() ? "RUNNING" : "STOPPED"));
        System.out.println("Uptime: " + (status.getUptime() / 1000) + " seconds");
        System.out.println("Total Accepted: " + status.getTotalAcceptedConnections());
        System.out.println("Total Failed: " + status.getTotalFailedConnections());

        if (status.getTotalAcceptedConnections() > 0) {
            double failureRate = (double) status.getTotalFailedConnections() /
                    (status.getTotalAcceptedConnections() + status.getTotalFailedConnections()) * 100;
            System.out.println("Failure Rate: " + String.format("%.2f", failureRate) + "%");
        }

        System.out.println("\nProcessor Status:");
        System.out.println(status.getProcessorStatus());

        System.out.println("\nContainer Status:");
        System.out.println(status.getContainerStatus());

        System.out.println("=".repeat(50) + "\n");
    }

    /**
     * Graceful shutdown hook 등록
     */
    public void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[ThreadedServer] Shutdown hook triggered");
            stop();
        }, "ShutdownHook-" + port));
    }

    // Getters
    public int getPort() { return port; }
    public boolean isRunning() { return running.get(); }
    public boolean isInitialized() { return initialized.get(); }
    public long getTotalAcceptedConnections() { return totalAcceptedConnections; }
    public long getTotalFailedConnections() { return totalFailedConnections; }

    /**
     * 서버 상태 클래스
     */
    public static class ServerStatus {
        private final boolean running;
        private final boolean initialized;
        private final int port;
        private final long uptime;
        private final long totalAcceptedConnections;
        private final long totalFailedConnections;
        private final ThreadedProcessor.ProcessorStatus processorStatus;
        private final ThreadedMiniServletContainer.ContainerStatus containerStatus;

        public ServerStatus(boolean running, boolean initialized, int port, long uptime,
                            long totalAcceptedConnections, long totalFailedConnections,
                            ThreadedProcessor.ProcessorStatus processorStatus,
                            ThreadedMiniServletContainer.ContainerStatus containerStatus) {
            this.running = running;
            this.initialized = initialized;
            this.port = port;
            this.uptime = uptime;
            this.totalAcceptedConnections = totalAcceptedConnections;
            this.totalFailedConnections = totalFailedConnections;
            this.processorStatus = processorStatus;
            this.containerStatus = containerStatus;
        }

        // Getters
        public boolean isRunning() { return running; }
        public boolean isInitialized() { return initialized; }
        public int getPort() { return port; }
        public long getUptime() { return uptime; }
        public long getTotalAcceptedConnections() { return totalAcceptedConnections; }
        public long getTotalFailedConnections() { return totalFailedConnections; }
        public ThreadedProcessor.ProcessorStatus getProcessorStatus() { return processorStatus; }
        public ThreadedMiniServletContainer.ContainerStatus getContainerStatus() { return containerStatus; }

        @Override
        public String toString() {
            return String.format("ServerStatus{running=%s, port=%d, uptime=%ds, connections=%d/%d}",
                    running, port, uptime / 1000,
                    totalAcceptedConnections, totalFailedConnections);
        }
    }
}