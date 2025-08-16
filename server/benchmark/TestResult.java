package server.benchmark;

import java.util.*;

/**
 * 개별 테스트 결과 클래스
 * 각 서버의 개별 벤치마크 테스트 결과를 저장
 */
public class TestResult {

    // 기본 정보
    private final boolean successful;
    private final String errorMessage;

    // 요청 통계
    private final int totalRequests;
    private final int successfulRequests;
    private final double errorRate;

    // 시간 통계
    private final long durationMs;
    private final double throughput; // requests per second

    // 응답 시간 통계 (밀리초)
    private final double averageResponseTime;
    private final double minResponseTime;
    private final double maxResponseTime;
    private final double medianResponseTime;
    private final double percentile95ResponseTime;
    private final double percentile99ResponseTime;

    // 동시성 정보
    private int concurrencyLevel;

    // 상태 코드 분포
    private final Map<Integer, Long> statusCodeDistribution;

    // 메모리 정보
    private long memoryIncrease;

    // 추가 메타데이터
    private final Map<String, Object> metadata;

    public TestResult(boolean successful, String errorMessage, int totalRequests, int successfulRequests,
                      double errorRate, long durationMs, double throughput, double averageResponseTime,
                      double minResponseTime, double maxResponseTime, double medianResponseTime,
                      double percentile95ResponseTime, double percentile99ResponseTime,
                      int concurrencyLevel, Map<Integer, Long> statusCodeDistribution, long memoryIncrease) {
        this.successful = successful;
        this.errorMessage = errorMessage;
        this.totalRequests = totalRequests;
        this.successfulRequests = successfulRequests;
        this.errorRate = errorRate;
        this.durationMs = durationMs;
        this.throughput = throughput;
        this.averageResponseTime = averageResponseTime;
        this.minResponseTime = minResponseTime;
        this.maxResponseTime = maxResponseTime;
        this.medianResponseTime = medianResponseTime;
        this.percentile95ResponseTime = percentile95ResponseTime;
        this.percentile99ResponseTime = percentile99ResponseTime;
        this.concurrencyLevel = concurrencyLevel;
        this.statusCodeDistribution = new HashMap<>(statusCodeDistribution);
        this.memoryIncrease = memoryIncrease;
        this.metadata = new HashMap<>();
    }

    /**
     * 실패한 테스트 결과 생성
     */
    public static TestResult failed(String errorMessage) {
        return new TestResult(
                false, errorMessage, 0, 0, 100.0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0, new HashMap<>(), 0
        );
    }

    /**
     * 빈 테스트 결과 생성
     */
    public static TestResult empty() {
        return new TestResult(
                true, null, 0, 0, 0.0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0, new HashMap<>(), 0
        );
    }

    // === 성능 지표 계산 메서드 ===

    /**
     * 성공률 계산
     */
    public double getSuccessRate() {
        return totalRequests > 0 ? (double) successfulRequests / totalRequests * 100 : 0.0;
    }

    /**
     * 초당 처리량 (TPS)
     */
    public double getTransactionsPerSecond() {
        return throughput;
    }

    /**
     * 평균 동시 처리 능력
     */
    public double getAverageConcurrentThroughput() {
        return concurrencyLevel > 0 ? throughput / concurrencyLevel : 0.0;
    }

    /**
     * 응답시간 효율성 (낮을수록 좋음)
     */
    public double getLatencyEfficiency() {
        return averageResponseTime > 0 ? throughput / averageResponseTime : 0.0;
    }

    /**
     * 메모리 효율성 (요청당 메모리 사용량)
     */
    public double getMemoryEfficiencyPerRequest() {
        return totalRequests > 0 ? (double) memoryIncrease / totalRequests : 0.0;
    }

    /**
     * 안정성 점수 (0-100, 높을수록 좋음)
     */
    public double getStabilityScore() {
        if (!successful) return 0.0;

        double successScore = getSuccessRate();
        double latencyStability = Math.max(0, 100 - (percentile99ResponseTime - averageResponseTime) / averageResponseTime * 100);

        return (successScore * 0.7 + latencyStability * 0.3);
    }

    /**
     * 종합 성능 점수 (0-100, 높을수록 좋음)
     */
    public double getOverallPerformanceScore() {
        if (!successful) return 0.0;

        // 정규화된 점수들
        double throughputScore = Math.min(100, throughput / 10); // 1000 TPS를 100점으로
        double latencyScore = Math.max(0, 100 - averageResponseTime / 10); // 1000ms를 0점으로
        double stabilityScore = getStabilityScore();

        return (throughputScore * 0.4 + latencyScore * 0.3 + stabilityScore * 0.3);
    }

    // === 비교 메서드 ===

    /**
     * 다른 결과와 처리량 비교
     */
    public ComparisonResult compareThroughput(TestResult other) {
        if (!this.successful || !other.successful) {
            return new ComparisonResult("throughput", 0.0, "One or both tests failed");
        }

        double improvement = ((this.throughput - other.throughput) / other.throughput) * 100;
        String description = String.format("%.1f%% %s than baseline (%.1f vs %.1f TPS)",
                Math.abs(improvement), improvement >= 0 ? "better" : "worse",
                this.throughput, other.throughput);

        return new ComparisonResult("throughput", improvement, description);
    }

    /**
     * 다른 결과와 응답시간 비교
     */
    public ComparisonResult compareLatency(TestResult other) {
        if (!this.successful || !other.successful) {
            return new ComparisonResult("latency", 0.0, "One or both tests failed");
        }

        double improvement = ((other.averageResponseTime - this.averageResponseTime) / other.averageResponseTime) * 100;
        String description = String.format("%.1f%% %s than baseline (%.1fms vs %.1fms)",
                Math.abs(improvement), improvement >= 0 ? "better" : "worse",
                this.averageResponseTime, other.averageResponseTime);

        return new ComparisonResult("latency", improvement, description);
    }

    /**
     * 다른 결과와 메모리 효율성 비교
     */
    public ComparisonResult compareMemoryEfficiency(TestResult other) {
        if (!this.successful || !other.successful) {
            return new ComparisonResult("memory", 0.0, "One or both tests failed");
        }

        double thisEfficiency = getMemoryEfficiencyPerRequest();
        double otherEfficiency = other.getMemoryEfficiencyPerRequest();

        if (otherEfficiency == 0) {
            return new ComparisonResult("memory", 0.0, "Cannot compare - baseline has no memory data");
        }

        double improvement = ((otherEfficiency - thisEfficiency) / otherEfficiency) * 100;
        String description = String.format("%.1f%% %s memory efficiency than baseline",
                Math.abs(improvement), improvement >= 0 ? "better" : "worse");

        return new ComparisonResult("memory", improvement, description);
    }

    // === Setters ===

    public void setConcurrencyLevel(int concurrencyLevel) {
        this.concurrencyLevel = concurrencyLevel;
    }

    public void setMemoryIncrease(long memoryIncrease) {
        this.memoryIncrease = memoryIncrease;
    }

    public void setMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    // === Getters ===

    public boolean isSuccessful() { return successful; }
    public String getErrorMessage() { return errorMessage; }
    public int getTotalRequests() { return totalRequests; }
    public int getSuccessfulRequests() { return successfulRequests; }
    public double getErrorRate() { return errorRate; }
    public long getDurationMs() { return durationMs; }
    public double getThroughput() { return throughput; }
    public double getAverageResponseTime() { return averageResponseTime; }
    public double getMinResponseTime() { return minResponseTime; }
    public double getMaxResponseTime() { return maxResponseTime; }
    public double getMedianResponseTime() { return medianResponseTime; }
    public double getPercentile95ResponseTime() { return percentile95ResponseTime; }
    public double getPercentile99ResponseTime() { return percentile99ResponseTime; }
    public int getConcurrencyLevel() { return concurrencyLevel; }
    public Map<Integer, Long> getStatusCodeDistribution() { return new HashMap<>(statusCodeDistribution); }
    public long getMemoryIncrease() { return memoryIncrease; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }

    // === 편의 메서드 ===

    /**
     * 요약 정보 반환
     */
    public String getSummary() {
        if (!successful) {
            return String.format("FAILED: %s", errorMessage);
        }

        return String.format(
                "TPS: %.1f, Avg: %.1fms, P95: %.1fms, Success: %.1f%%, Concurrency: %d",
                throughput, averageResponseTime, percentile95ResponseTime, getSuccessRate(), concurrencyLevel
        );
    }

    /**
     * 상세 정보 반환
     */
    public String getDetailedSummary() {
        if (!successful) {
            return String.format("Test Failed: %s", errorMessage);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Test Result Summary ===\n");
        sb.append(String.format("Requests: %d total, %d successful (%.1f%% success rate)\n",
                totalRequests, successfulRequests, getSuccessRate()));
        sb.append(String.format("Duration: %d ms\n", durationMs));
        sb.append(String.format("Throughput: %.1f requests/sec\n", throughput));
        sb.append(String.format("Concurrency: %d\n", concurrencyLevel));
        sb.append("\nResponse Times:\n");
        sb.append(String.format("  Average: %.2f ms\n", averageResponseTime));
        sb.append(String.format("  Median:  %.2f ms\n", medianResponseTime));
        sb.append(String.format("  Min:     %.2f ms\n", minResponseTime));
        sb.append(String.format("  Max:     %.2f ms\n", maxResponseTime));
        sb.append(String.format("  95th%%:   %.2f ms\n", percentile95ResponseTime));
        sb.append(String.format("  99th%%:   %.2f ms\n", percentile99ResponseTime));

        if (memoryIncrease > 0) {
            sb.append(String.format("\nMemory: %.2f MB increase (%.2f KB per request)\n",
                    memoryIncrease / (1024.0 * 1024.0), getMemoryEfficiencyPerRequest() / 1024.0));
        }

        sb.append(String.format("\nScores:\n"));
        sb.append(String.format("  Stability: %.1f/100\n", getStabilityScore()));
        sb.append(String.format("  Overall:   %.1f/100\n", getOverallPerformanceScore()));

        return sb.toString();
    }

    @Override
    public String toString() {
        return getSummary();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        TestResult that = (TestResult) obj;
        return successful == that.successful &&
                totalRequests == that.totalRequests &&
                successfulRequests == that.successfulRequests &&
                Double.compare(that.errorRate, errorRate) == 0 &&
                durationMs == that.durationMs &&
                Double.compare(that.throughput, throughput) == 0 &&
                Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(successful, errorMessage, totalRequests, successfulRequests,
                errorRate, durationMs, throughput);
    }

    /**
     * 비교 결과 클래스
     */
    public static class ComparisonResult {
        private final String metric;
        private final double improvementPercentage;
        private final String description;

        public ComparisonResult(String metric, double improvementPercentage, String description) {
            this.metric = metric;
            this.improvementPercentage = improvementPercentage;
            this.description = description;
        }

        public String getMetric() { return metric; }
        public double getImprovementPercentage() { return improvementPercentage; }
        public String getDescription() { return description; }
        public boolean isBetter() { return improvementPercentage > 0; }
        public boolean isWorse() { return improvementPercentage < 0; }
        public boolean isSimilar() { return Math.abs(improvementPercentage) < 5.0; }

        @Override
        public String toString() {
            return description;
        }
    }
}