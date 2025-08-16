package server.threaded;

/**
 * 스레드풀 설정 클래스
 */
public class ThreadPoolConfig {
    private int corePoolSize = 10;
    private int maxPoolSize = 100;
    private int queueCapacity = 200;
    private long keepAliveTime = 60; // seconds
    private int monitorInterval = 10; // seconds
    private int scaleStep = 5;
    private boolean debugMode = false;

    public ThreadPoolConfig() {}

    // Getters and Setters with fluent interface
    public int getCorePoolSize() {
        return corePoolSize;
    }

    public ThreadPoolConfig setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
        return this;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public ThreadPoolConfig setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
        return this;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public ThreadPoolConfig setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
        return this;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public ThreadPoolConfig setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
        return this;
    }

    public int getMonitorInterval() {
        return monitorInterval;
    }

    public ThreadPoolConfig setMonitorInterval(int monitorInterval) {
        this.monitorInterval = monitorInterval;
        return this;
    }

    public int getScaleStep() {
        return scaleStep;
    }

    public ThreadPoolConfig setScaleStep(int scaleStep) {
        this.scaleStep = scaleStep;
        return this;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public ThreadPoolConfig setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        return this;
    }

    /**
     * 설정 유효성 검증
     */
    public void validate() {
        if (corePoolSize <= 0) {
            throw new IllegalArgumentException("Core pool size must be positive");
        }
        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("Max pool size must be >= core pool size");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("Queue capacity must be non-negative");
        }
        if (keepAliveTime < 0) {
            throw new IllegalArgumentException("Keep alive time must be non-negative");
        }
        if (monitorInterval <= 0) {
            throw new IllegalArgumentException("Monitor interval must be positive");
        }
        if (scaleStep <= 0) {
            throw new IllegalArgumentException("Scale step must be positive");
        }
    }

    /**
     * 기본 설정 생성
     */
    public static ThreadPoolConfig defaultConfig() {
        return new ThreadPoolConfig();
    }

    /**
     * 고성능 설정 생성
     */
    public static ThreadPoolConfig highPerformanceConfig() {
        return new ThreadPoolConfig()
                .setCorePoolSize(20)
                .setMaxPoolSize(200)
                .setQueueCapacity(500)
                .setScaleStep(10)
                .setMonitorInterval(5);
    }

    /**
     * 개발용 설정 생성
     */
    public static ThreadPoolConfig developmentConfig() {
        return new ThreadPoolConfig()
                .setCorePoolSize(2)
                .setMaxPoolSize(10)
                .setQueueCapacity(50)
                .setDebugMode(true)
                .setMonitorInterval(5);
    }

    @Override
    public String toString() {
        return String.format(
                "ThreadPoolConfig{core=%d, max=%d, queue=%d, keepAlive=%ds, " +
                        "monitor=%ds, scaleStep=%d, debug=%s}",
                corePoolSize, maxPoolSize, queueCapacity, keepAliveTime,
                monitorInterval, scaleStep, debugMode
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ThreadPoolConfig)) return false;

        ThreadPoolConfig other = (ThreadPoolConfig) obj;
        return corePoolSize == other.corePoolSize &&
                maxPoolSize == other.maxPoolSize &&
                queueCapacity == other.queueCapacity &&
                keepAliveTime == other.keepAliveTime &&
                monitorInterval == other.monitorInterval &&
                scaleStep == other.scaleStep &&
                debugMode == other.debugMode;
    }

    @Override
    public int hashCode() {
        int result = corePoolSize;
        result = 31 * result + maxPoolSize;
        result = 31 * result + queueCapacity;
        result = 31 * result + (int) (keepAliveTime ^ (keepAliveTime >>> 32));
        result = 31 * result + monitorInterval;
        result = 31 * result + scaleStep;
        result = 31 * result + (debugMode ? 1 : 0);
        return result;
    }
}