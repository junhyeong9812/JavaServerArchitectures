package server.benchmark;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 전체 벤치마크 결과를 담는 클래스
 * 3가지 서버의 모든 테스트 결과와 분석 데이터 포함
 */
public class BenchmarkResults {

    private final Map<String, ServerComparisonResult> testResults;
    private final long benchmarkStartTime;
    private final long benchmarkEndTime;
    private final BenchmarkConfig config;
    private final Map<String, Object> metadata;

    public BenchmarkResults() {
        this.testResults = new LinkedHashMap<>();
        this.benchmarkStartTime = System.currentTimeMillis();
        this.benchmarkEndTime = 0;
        this.config = null;
        this.metadata = new HashMap<>();
    }

    public BenchmarkResults(BenchmarkConfig config) {
        this.testResults = new LinkedHashMap<>();
        this.benchmarkStartTime = System.currentTimeMillis();
        this.benchmarkEndTime = 0;
        this.config = config;
        this.metadata = new HashMap<>();
    }

    /**
     * 테스트 결과 추가
     */
    public void addResult(String testName, ServerComparisonResult result) {
        testResults.put(testName, result);
    }

    /**
     * 특정 테스트 결과 조회
     */
    public ServerComparisonResult getResult(String testName) {
        return testResults.get(testName);
    }

    /**
     * 모든 테스트 결과 조회
     */
    public Map<String, ServerComparisonResult> getAllResults() {
        return new LinkedHashMap<>(testResults);
    }

    /**
     * 테스트 이름 목록
     */
    public Set<String> getTestNames() {
        return new LinkedHashSet<>(testResults.keySet());
    }

    /**
     * 서버별 종합 우승 분석
     */
    public Map<String, String> analyzeWinners() {
        Map<String, String> winners = new HashMap<>();

        // 각 테스트별 우승자 분석
        for (Map.Entry<String, ServerComparisonResult> entry : testResults.entrySet()) {
            String testName = entry.getKey();
            ServerComparisonResult result = entry.getValue();

            String winner = findWinnerByThroughput(result);
            winners.put(testName, winner);
        }

        // 종합 우승자 결정
        Map<String, Integer> winCounts = new HashMap<>();
        for (String winner : winners.values()) {
            winCounts.put(winner, winCounts.getOrDefault(winner, 0) + 1);
        }

        String overallWinner = winCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("No clear winner");

        winners.put("OVERALL", overallWinner);

        return winners;
    }

    /**
     * 처리량 기준 우승자 찾기
     */
    private String findWinnerByThroughput(ServerComparisonResult result) {
        double threadedTps = result.getThreadedResult().getThroughput();
        double hybridTps = result.getHybridResult().getThroughput();
        double eventLoopTps = result.getEventLoopResult().getThroughput();

        if (eventLoopTps >= hybridTps && eventLoopTps >= threadedTps) {
            return "EventLoop";
        } else if (hybridTps >= threadedTps) {
            return "Hybrid";
        } else {
            return "Threaded";
        }
    }

    /**
     * 서버별 성능 요약
     */
    public ServerPerformanceSummary getServerSummary(String serverName) {
        List<TestResult> serverResults = new ArrayList<>();

        for (ServerComparisonResult result : testResults.values()) {
            TestResult serverResult = getServerResult(result, serverName);
            if (serverResult != null && serverResult.isSuccessful()) {
                serverResults.add(serverResult);
            }
        }

        if (serverResults.isEmpty()) {
            return ServerPerformanceSummary.empty(serverName);
        }

        // 통계 계산
        double avgThroughput = serverResults.stream()
                .mapToDouble(TestResult::getThroughput)
                .average().orElse(0.0);

        double avgLatency = serverResults.stream()
                .mapToDouble(TestResult::getAverageResponseTime)
                .average().orElse(0.0);

        double avgSuccessRate = serverResults.stream()
                .mapToDouble(TestResult::getSuccessRate)
                .average().orElse(0.0);

        int maxConcurrency = serverResults.stream()
                .mapToInt(TestResult::getConcurrencyLevel)
                .max().orElse(0);

        double maxThroughput = serverResults.stream()
                .mapToDouble(TestResult::getThroughput)
                .max().orElse(0.0);

        double minLatency = serverResults.stream()
                .mapToDouble(TestResult::getAverageResponseTime)
                .min().orElse(0.0);

        return new ServerPerformanceSummary(
                serverName,
                serverResults.size(),
                avgThroughput,
                maxThroughput,
                avgLatency,
                minLatency,
                avgSuccessRate,
                maxConcurrency
        );
    }

    /**
     * 특정 서버의 결과 추출
     */
    private TestResult getServerResult(ServerComparisonResult result, String serverName) {
        switch (serverName.toLowerCase()) {
            case "threaded": return result.getThreadedResult();
            case "hybrid": return result.getHybridResult();
            case "eventloop": return result.getEventLoopResult();
            default: return null;
        }
    }

    /**
     * 전체 성능 순위
     */
    public List<ServerRanking> getOverallRankings() {
        List<ServerRanking> rankings = new ArrayList<>();

        String[] serverNames = {"Threaded", "Hybrid", "EventLoop"};

        for (String serverName : serverNames) {
            ServerPerformanceSummary summary = getServerSummary(serverName);
            double overallScore = calculateOverallScore(summary);
            rankings.add(new ServerRanking(serverName, overallScore, summary));
        }

        rankings.sort((a, b) -> Double.compare(b.getOverallScore(), a.getOverallScore()));

        return rankings;
    }

    /**
     * 종합 점수 계산
     */
    private double calculateOverallScore(ServerPerformanceSummary summary) {
        if (summary.getTestCount() == 0) return 0.0;

        // 정규화된 점수 (0-100)
        double throughputScore = Math.min(100, summary.getAverageThroughput() / 10);
        double latencyScore = Math.max(0, 100 - summary.getAverageLatency() / 10);
        double reliabilityScore = summary.getAverageSuccessRate();

        return (throughputScore * 0.4 + latencyScore * 0.3 + reliabilityScore * 0.3);
    }

    /**
     * 성능 비교 매트릭스 생성
     */
    public PerformanceMatrix generateComparisonMatrix() {
        Map<String, Map<String, Double>> throughputMatrix = new HashMap<>();
        Map<String, Map<String, Double>> latencyMatrix = new HashMap<>();
        Map<String, Map<String, Double>> successRateMatrix = new HashMap<>();

        String[] servers = {"Threaded", "Hybrid", "EventLoop"};

        for (String server : servers) {
            throughputMatrix.put(server, new HashMap<>());
            latencyMatrix.put(server, new HashMap<>());
            successRateMatrix.put(server, new HashMap<>());
        }

        // 각 테스트별로 매트릭스 채우기
        for (ServerComparisonResult result : testResults.values()) {
            TestResult[] results = {
                    result.getThreadedResult(),
                    result.getHybridResult(),
                    result.getEventLoopResult()
            };

            for (int i = 0; i < servers.length; i++) {
                for (int j = 0; j < servers.length; j++) {
                    if (i != j && results[i].isSuccessful() && results[j].isSuccessful()) {
                        String from = servers[i];
                        String to = servers[j];

                        // 처리량 비교 (상대방 대비 개선율)
                        double throughputImprovement =
                                (results[i].getThroughput() - results[j].getThroughput()) /
                                        results[j].getThroughput() * 100;

                        // 지연시간 비교 (상대방 대비 개선율)
                        double latencyImprovement =
                                (results[j].getAverageResponseTime() - results[i].getAverageResponseTime()) /
                                        results[j].getAverageResponseTime() * 100;

                        // 성공률 비교
                        double successRateImprovement =
                                results[i].getSuccessRate() - results[j].getSuccessRate();

                        throughputMatrix.get(from).put(to, throughputImprovement);
                        latencyMatrix.get(from).put(to, latencyImprovement);
                        successRateMatrix.get(from).put(to, successRateImprovement);
                    }
                }
            }
        }

        return new PerformanceMatrix(throughputMatrix, latencyMatrix, successRateMatrix);
    }

    /**
     * 메타데이터 추가
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    // === Getters ===

    public long getBenchmarkStartTime() { return benchmarkStartTime; }
    public long getBenchmarkEndTime() { return benchmarkEndTime; }
    public BenchmarkConfig getConfig() { return config; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }

    public long getTotalDurationMs() {
        return benchmarkEndTime > 0 ? benchmarkEndTime - benchmarkStartTime :
                System.currentTimeMillis() - benchmarkStartTime;
    }

    public int getTotalTestCount() {
        return testResults.size();
    }

    // === 내부 클래스들 ===

    /**
     * 서버 성능 요약
     */
    public static class ServerPerformanceSummary {
        private final String serverName;
        private final int testCount;
        private final double averageThroughput;
        private final double maxThroughput;
        private final double averageLatency;
        private final double minLatency;
        private final double averageSuccessRate;
        private final int maxConcurrency;

        public ServerPerformanceSummary(String serverName, int testCount, double averageThroughput,
                                        double maxThroughput, double averageLatency, double minLatency,
                                        double averageSuccessRate, int maxConcurrency) {
            this.serverName = serverName;
            this.testCount = testCount;
            this.averageThroughput = averageThroughput;
            this.maxThroughput = maxThroughput;
            this.averageLatency = averageLatency;
            this.minLatency = minLatency;
            this.averageSuccessRate = averageSuccessRate;
            this.maxConcurrency = maxConcurrency;
        }

        public static ServerPerformanceSummary empty(String serverName) {
            return new ServerPerformanceSummary(serverName, 0, 0, 0, 0, 0, 0, 0);
        }

        // Getters
        public String getServerName() { return serverName; }
        public int getTestCount() { return testCount; }
        public double getAverageThroughput() { return averageThroughput; }
        public double getMaxThroughput() { return maxThroughput; }
        public double getAverageLatency() { return averageLatency; }
        public double getMinLatency() { return minLatency; }
        public double getAverageSuccessRate() { return averageSuccessRate; }
        public int getMaxConcurrency() { return maxConcurrency; }

        @Override
        public String toString() {
            return String.format("%s: %.1f TPS avg (%.1f max), %.1fms avg latency, %.1f%% success",
                    serverName, averageThroughput, maxThroughput, averageLatency, averageSuccessRate);
        }
    }

    /**
     * 서버 순위
     */
    public static class ServerRanking {
        private final String serverName;
        private final double overallScore;
        private final ServerPerformanceSummary summary;

        public ServerRanking(String serverName, double overallScore, ServerPerformanceSummary summary) {
            this.serverName = serverName;
            this.overallScore = overallScore;
            this.summary = summary;
        }

        public String getServerName() { return serverName; }
        public double getOverallScore() { return overallScore; }
        public ServerPerformanceSummary getSummary() { return summary; }

        @Override
        public String toString() {
            return String.format("%s (Score: %.1f) - %s", serverName, overallScore, summary);
        }
    }

    /**
     * 성능 비교 매트릭스
     */
    public static class PerformanceMatrix {
        private final Map<String, Map<String, Double>> throughputMatrix;
        private final Map<String, Map<String, Double>> latencyMatrix;
        private final Map<String, Map<String, Double>> successRateMatrix;

        public PerformanceMatrix(Map<String, Map<String, Double>> throughputMatrix,
                                 Map<String, Map<String, Double>> latencyMatrix,
                                 Map<String, Map<String, Double>> successRateMatrix) {
            this.throughputMatrix = new HashMap<>(throughputMatrix);
            this.latencyMatrix = new HashMap<>(latencyMatrix);
            this.successRateMatrix = new HashMap<>(successRateMatrix);
        }

        public Map<String, Map<String, Double>> getThroughputMatrix() {
            return new HashMap<>(throughputMatrix);
        }
        public Map<String, Map<String, Double>> getLatencyMatrix() {
            return new HashMap<>(latencyMatrix);
        }
        public Map<String, Map<String, Double>> getSuccessRateMatrix() {
            return new HashMap<>(successRateMatrix);
        }

        /**
         * 특정 서버가 다른 서버보다 얼마나 좋은지 반환
         */
        public double getThroughputAdvantage(String from, String to) {
            return throughputMatrix.getOrDefault(from, new HashMap<>()).getOrDefault(to, 0.0);
        }

        public double getLatencyAdvantage(String from, String to) {
            return latencyMatrix.getOrDefault(from, new HashMap<>()).getOrDefault(to, 0.0);
        }

        public double getSuccessRateAdvantage(String from, String to) {
            return successRateMatrix.getOrDefault(from, new HashMap<>()).getOrDefault(to, 0.0);
        }
    }
}

