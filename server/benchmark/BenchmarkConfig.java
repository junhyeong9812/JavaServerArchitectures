package server.benchmark;

/**
 * 벤치마크 설정 클래스
 */
public class BenchmarkConfig {

    // 기본 설정값
    private int warmupRequests = 50;
    private int targetThroughput = 500;
    private int maxConcurrency = 1000;
    private int testDurationSeconds = 60;
    private int timeoutSeconds = 30;

    // 고급 설정
    private boolean enableMemoryProfiling = true;
    private boolean enableGcAnalysis = true;
    private boolean enableDetailedLatency = true;
    private int latencyPercentiles[] = {50, 75, 90, 95, 99};

    // 리포트 설정
    private boolean generateHtmlReport = true;
    private boolean generateJsonReport = true;
    private boolean saveRawData = false;

    public BenchmarkConfig() {}

    // === 기본 설정 팩토리 메서드 ===

    public static BenchmarkConfig defaultConfig() {
        return new BenchmarkConfig();
    }

    public static BenchmarkConfig quickTest() {
        return new BenchmarkConfig()
                .setWarmupRequests(10)
                .setTargetThroughput(100)
                .setMaxConcurrency(50)
                .setTestDurationSeconds(30);
    }

    public static BenchmarkConfig stressTest() {
        return new BenchmarkConfig()
                .setWarmupRequests(200)
                .setTargetThroughput(2000)
                .setMaxConcurrency(5000)
                .setTestDurationSeconds(300); // 5분
    }

    public static BenchmarkConfig enduranceTest() {
        return new BenchmarkConfig()
                .setWarmupRequests(100)
                .setTargetThroughput(500)
                .setMaxConcurrency(1000)
                .setTestDurationSeconds(3600); // 1시간
    }

    // === Fluent Interface Setters ===

    public BenchmarkConfig setWarmupRequests(int warmupRequests) {
        this.warmupRequests = warmupRequests;
        return this;
    }

    public BenchmarkConfig setTargetThroughput(int targetThroughput) {
        this.targetThroughput = targetThroughput;
        return this;
    }

    public BenchmarkConfig setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
        return this;
    }

    public BenchmarkConfig setTestDurationSeconds(int testDurationSeconds) {
        this.testDurationSeconds = testDurationSeconds;
        return this;
    }

    public BenchmarkConfig setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    public BenchmarkConfig setEnableMemoryProfiling(boolean enableMemoryProfiling) {
        this.enableMemoryProfiling = enableMemoryProfiling;
        return this;
    }

    public BenchmarkConfig setEnableGcAnalysis(boolean enableGcAnalysis) {
        this.enableGcAnalysis = enableGcAnalysis;
        return this;
    }

    public BenchmarkConfig setEnableDetailedLatency(boolean enableDetailedLatency) {
        this.enableDetailedLatency = enableDetailedLatency;
        return this;
    }

    public BenchmarkConfig setLatencyPercentiles(int[] latencyPercentiles) {
        this.latencyPercentiles = latencyPercentiles.clone();
        return this;
    }

    public BenchmarkConfig setGenerateHtmlReport(boolean generateHtmlReport) {
        this.generateHtmlReport = generateHtmlReport;
        return this;
    }

    public BenchmarkConfig setGenerateJsonReport(boolean generateJsonReport) {
        this.generateJsonReport = generateJsonReport;
        return this;
    }

    public BenchmarkConfig setSaveRawData(boolean saveRawData) {
        this.saveRawData = saveRawData;
        return this;
    }

    // === Getters ===

    public int getWarmupRequests() { return warmupRequests; }
    public int getTargetThroughput() { return targetThroughput; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public int getTestDurationSeconds() { return testDurationSeconds; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public boolean isEnableMemoryProfiling() { return enableMemoryProfiling; }
    public boolean isEnableGcAnalysis() { return enableGcAnalysis; }
    public boolean isEnableDetailedLatency() { return enableDetailedLatency; }
    public int[] getLatencyPercentiles() { return latencyPercentiles.clone(); }
    public boolean isGenerateHtmlReport() { return generateHtmlReport; }
    public boolean isGenerateJsonReport() { return generateJsonReport; }
    public boolean isSaveRawData() { return saveRawData; }

    @Override
    public String toString() {
        return String.format(
                "BenchmarkConfig{warmup=%d, throughput=%d, concurrency=%d, duration=%ds, timeout=%ds}",
                warmupRequests, targetThroughput, maxConcurrency, testDurationSeconds, timeoutSeconds
        );
    }
}