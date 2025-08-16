package server.threaded;

/**
 * 서버 설정 클래스
 */
public class ServerConfig {
    private String bindAddress = "0.0.0.0";
    private int backlogSize = 50;
    private int receiveBufferSize = 8192;
    private int sendBufferSize = 8192;
    private boolean tcpNoDelay = true;
    private boolean keepAlive = true;
    private boolean debugMode = false;
    private String contextPath = "/";
    private boolean enableStatistics = true;
    private int statisticsInterval = 30; // seconds

    private ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig();
    private RequestHandlerConfig requestHandlerConfig = new RequestHandlerConfig();

    public ServerConfig() {}

    // Getters and Setters with fluent interface
    public String getBindAddress() {
        return bindAddress;
    }

    public ServerConfig setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
        return this;
    }

    public int getBacklogSize() {
        return backlogSize;
    }

    public ServerConfig setBacklogSize(int backlogSize) {
        this.backlogSize = backlogSize;
        return this;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public ServerConfig setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
        return this;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public ServerConfig setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
        return this;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public ServerConfig setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        return this;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public ServerConfig setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
        return this;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public ServerConfig setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        return this;
    }

    public String getContextPath() {
        return contextPath;
    }

    public ServerConfig setContextPath(String contextPath) {
        this.contextPath = contextPath;
        return this;
    }

    public boolean isEnableStatistics() {
        return enableStatistics;
    }

    public ServerConfig setEnableStatistics(boolean enableStatistics) {
        this.enableStatistics = enableStatistics;
        return this;
    }

    public int getStatisticsInterval() {
        return statisticsInterval;
    }

    public ServerConfig setStatisticsInterval(int statisticsInterval) {
        this.statisticsInterval = statisticsInterval;
        return this;
    }

    public ThreadPoolConfig getThreadPoolConfig() {
        return threadPoolConfig;
    }

    public ServerConfig setThreadPoolConfig(ThreadPoolConfig threadPoolConfig) {
        this.threadPoolConfig = threadPoolConfig;
        return this;
    }

    public RequestHandlerConfig getRequestHandlerConfig() {
        return requestHandlerConfig;
    }

    public ServerConfig setRequestHandlerConfig(RequestHandlerConfig requestHandlerConfig) {
        this.requestHandlerConfig = requestHandlerConfig;
        return this;
    }

    /**
     * 설정 유효성 검증
     */
    public void validate() {
        if (bindAddress == null || bindAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Bind address cannot be null or empty");
        }
        if (backlogSize < 0) {
            throw new IllegalArgumentException("Backlog size must be non-negative");
        }
        if (receiveBufferSize <= 0) {
            throw new IllegalArgumentException("Receive buffer size must be positive");
        }
        if (sendBufferSize <= 0) {
            throw new IllegalArgumentException("Send buffer size must be positive");
        }
        if (contextPath == null) {
            throw new IllegalArgumentException("Context path cannot be null");
        }
        if (statisticsInterval <= 0) {
            throw new IllegalArgumentException("Statistics interval must be positive");
        }

        // 하위 설정 검증
        if (threadPoolConfig != null) {
            threadPoolConfig.validate();
        }
        if (requestHandlerConfig != null) {
            requestHandlerConfig.validate();
        }
    }

    /**
     * 기본 설정 생성
     */
    public static ServerConfig defaultConfig() {
        return new ServerConfig();
    }

    /**
     * 고성능 설정 생성
     */
    public static ServerConfig highPerformanceConfig() {
        return new ServerConfig()
                .setBacklogSize(100)
                .setReceiveBufferSize(16384)
                .setSendBufferSize(16384)
                .setStatisticsInterval(60)
                .setThreadPoolConfig(ThreadPoolConfig.highPerformanceConfig())
                .setRequestHandlerConfig(RequestHandlerConfig.highPerformanceConfig());
    }

    /**
     * 개발용 설정 생성
     */
    public static ServerConfig developmentConfig() {
        return new ServerConfig()
                .setDebugMode(true)
                .setBacklogSize(10)
                .setStatisticsInterval(10)
                .setThreadPoolConfig(ThreadPoolConfig.developmentConfig())
                .setRequestHandlerConfig(RequestHandlerConfig.developmentConfig());
    }

    /**
     * 프로덕션 설정 생성
     */
    public static ServerConfig productionConfig() {
        return new ServerConfig()
                .setDebugMode(false)
                .setBacklogSize(200)
                .setReceiveBufferSize(32768)
                .setSendBufferSize(32768)
                .setStatisticsInterval(300) // 5분
                .setThreadPoolConfig(ThreadPoolConfig.highPerformanceConfig())
                .setRequestHandlerConfig(RequestHandlerConfig.secureConfig());
    }

    /**
     * 로컬 테스트용 설정 생성
     */
    public static ServerConfig localTestConfig() {
        return new ServerConfig()
                .setBindAddress("127.0.0.1")
                .setDebugMode(true)
                .setBacklogSize(5)
                .setStatisticsInterval(5)
                .setThreadPoolConfig(ThreadPoolConfig.developmentConfig())
                .setRequestHandlerConfig(RequestHandlerConfig.developmentConfig());
    }

    @Override
    public String toString() {
        return String.format(
                "ServerConfig{bind=%s, backlog=%d, rcvBuf=%d, sndBuf=%d, " +
                        "tcpNoDelay=%s, keepAlive=%s, debug=%s, context='%s', stats=%s}",
                bindAddress, backlogSize, receiveBufferSize, sendBufferSize,
                tcpNoDelay, keepAlive, debugMode, contextPath, enableStatistics
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ServerConfig)) return false;

        ServerConfig other = (ServerConfig) obj;
        return backlogSize == other.backlogSize &&
                receiveBufferSize == other.receiveBufferSize &&
                sendBufferSize == other.sendBufferSize &&
                tcpNoDelay == other.tcpNoDelay &&
                keepAlive == other.keepAlive &&
                debugMode == other.debugMode &&
                enableStatistics == other.enableStatistics &&
                statisticsInterval == other.statisticsInterval &&
                bindAddress.equals(other.bindAddress) &&
                contextPath.equals(other.contextPath) &&
                threadPoolConfig.equals(other.threadPoolConfig) &&
                requestHandlerConfig.equals(other.requestHandlerConfig);
    }

    @Override
    public int hashCode() {
        int result = bindAddress.hashCode();
        result = 31 * result + backlogSize;
        result = 31 * result + receiveBufferSize;
        result = 31 * result + sendBufferSize;
        result = 31 * result + (tcpNoDelay ? 1 : 0);
        result = 31 * result + (keepAlive ? 1 : 0);
        result = 31 * result + (debugMode ? 1 : 0);
        result = 31 * result + contextPath.hashCode();
        result = 31 * result + (enableStatistics ? 1 : 0);
        result = 31 * result + statisticsInterval;
        result = 31 * result + threadPoolConfig.hashCode();
        result = 31 * result + requestHandlerConfig.hashCode();
        return result;
    }
}