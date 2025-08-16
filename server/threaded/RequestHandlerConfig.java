package server.threaded;

/**
 * 요청 핸들러 설정 클래스
 */
public class RequestHandlerConfig {
    private int socketTimeout = 30000; // 30초
    private int maxRequestsPerConnection = 100;
    private boolean debugMode = false;
    private int readBufferSize = 8192;
    private int writeBufferSize = 8192;
    private boolean enableKeepAlive = true;
    private int connectionTimeout = 60000; // 60초
    private boolean enableRequestLogging = false;

    public RequestHandlerConfig() {}

    // Getters and Setters with fluent interface
    public int getSocketTimeout() {
        return socketTimeout;
    }

    public RequestHandlerConfig setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
        return this;
    }

    public int getMaxRequestsPerConnection() {
        return maxRequestsPerConnection;
    }

    public RequestHandlerConfig setMaxRequestsPerConnection(int maxRequestsPerConnection) {
        this.maxRequestsPerConnection = maxRequestsPerConnection;
        return this;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public RequestHandlerConfig setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        return this;
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public RequestHandlerConfig setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
        return this;
    }

    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    public RequestHandlerConfig setWriteBufferSize(int writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
        return this;
    }

    public boolean isEnableKeepAlive() {
        return enableKeepAlive;
    }

    public RequestHandlerConfig setEnableKeepAlive(boolean enableKeepAlive) {
        this.enableKeepAlive = enableKeepAlive;
        return this;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public RequestHandlerConfig setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    public boolean isEnableRequestLogging() {
        return enableRequestLogging;
    }

    public RequestHandlerConfig setEnableRequestLogging(boolean enableRequestLogging) {
        this.enableRequestLogging = enableRequestLogging;
        return this;
    }

    /**
     * 설정 유효성 검증
     */
    public void validate() {
        if (socketTimeout < 0) {
            throw new IllegalArgumentException("Socket timeout must be non-negative");
        }
        if (maxRequestsPerConnection <= 0) {
            throw new IllegalArgumentException("Max requests per connection must be positive");
        }
        if (readBufferSize <= 0) {
            throw new IllegalArgumentException("Read buffer size must be positive");
        }
        if (writeBufferSize <= 0) {
            throw new IllegalArgumentException("Write buffer size must be positive");
        }
        if (connectionTimeout < 0) {
            throw new IllegalArgumentException("Connection timeout must be non-negative");
        }
    }

    /**
     * 기본 설정 생성
     */
    public static RequestHandlerConfig defaultConfig() {
        return new RequestHandlerConfig();
    }

    /**
     * 고성능 설정 생성
     */
    public static RequestHandlerConfig highPerformanceConfig() {
        return new RequestHandlerConfig()
                .setSocketTimeout(15000)
                .setMaxRequestsPerConnection(200)
                .setReadBufferSize(16384)
                .setWriteBufferSize(16384)
                .setConnectionTimeout(30000);
    }

    /**
     * 개발용 설정 생성
     */
    public static RequestHandlerConfig developmentConfig() {
        return new RequestHandlerConfig()
                .setDebugMode(true)
                .setEnableRequestLogging(true)
                .setSocketTimeout(60000)
                .setMaxRequestsPerConnection(50);
    }

    /**
     * 보안 강화 설정 생성
     */
    public static RequestHandlerConfig secureConfig() {
        return new RequestHandlerConfig()
                .setSocketTimeout(10000)
                .setMaxRequestsPerConnection(50)
                .setConnectionTimeout(20000)
                .setEnableKeepAlive(false);
    }

    @Override
    public String toString() {
        return String.format(
                "RequestHandlerConfig{timeout=%dms, maxReqs=%d, debug=%s, " +
                        "readBuf=%d, writeBuf=%d, keepAlive=%s, connTimeout=%dms}",
                socketTimeout, maxRequestsPerConnection, debugMode,
                readBufferSize, writeBufferSize, enableKeepAlive, connectionTimeout
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RequestHandlerConfig)) return false;

        RequestHandlerConfig other = (RequestHandlerConfig) obj;
        return socketTimeout == other.socketTimeout &&
                maxRequestsPerConnection == other.maxRequestsPerConnection &&
                debugMode == other.debugMode &&
                readBufferSize == other.readBufferSize &&
                writeBufferSize == other.writeBufferSize &&
                enableKeepAlive == other.enableKeepAlive &&
                connectionTimeout == other.connectionTimeout &&
                enableRequestLogging == other.enableRequestLogging;
    }

    @Override
    public int hashCode() {
        int result = socketTimeout;
        result = 31 * result + maxRequestsPerConnection;
        result = 31 * result + (debugMode ? 1 : 0);
        result = 31 * result + readBufferSize;
        result = 31 * result + writeBufferSize;
        result = 31 * result + (enableKeepAlive ? 1 : 0);
        result = 31 * result + connectionTimeout;
        result = 31 * result + (enableRequestLogging ? 1 : 0);
        return result;
    }
}