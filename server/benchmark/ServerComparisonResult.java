package server.benchmark;

/**
 * 서버간 비교 결과 클래스
 * 3개 서버의 동일한 테스트에 대한 결과를 담고 비교 분석 제공
 */
public class ServerComparisonResult {

    private final String testName;
    private final TestResult threadedResult;
    private final TestResult hybridResult;
    private final TestResult eventLoopResult;

    public ServerComparisonResult(String testName, TestResult threadedResult,
                                  TestResult hybridResult, TestResult eventLoopResult) {
        this.testName = testName;
        this.threadedResult = threadedResult;
        this.hybridResult = hybridResult;
        this.eventLoopResult = eventLoopResult;
    }

    /**
     * 가장 높은 처리량을 가진 서버 찾기
     */
    public String getBestThroughputServer() {
        double threadedTps = threadedResult.isSuccessful() ? threadedResult.getThroughput() : 0;
        double hybridTps = hybridResult.isSuccessful() ? hybridResult.getThroughput() : 0;
        double eventLoopTps = eventLoopResult.isSuccessful() ? eventLoopResult.getThroughput() : 0;

        if (eventLoopTps >= hybridTps && eventLoopTps >= threadedTps) {
            return "EventLoop";
        } else if (hybridTps >= threadedTps) {
            return "Hybrid";
        } else {
            return "Threaded";
        }
    }

    /**
     * 가장 낮은 지연시간을 가진 서버 찾기
     */
    public String getBestLatencyServer() {
        double threadedLatency = threadedResult.isSuccessful() ?
                threadedResult.getAverageResponseTime() : Double.MAX_VALUE;
        double hybridLatency = hybridResult.isSuccessful() ?
                hybridResult.getAverageResponseTime() : Double.MAX_VALUE;
        double eventLoopLatency = eventLoopResult.isSuccessful() ?
                eventLoopResult.getAverageResponseTime() : Double.MAX_VALUE;

        if (eventLoopLatency <= hybridLatency && eventLoopLatency <= threadedLatency) {
            return "EventLoop";
        } else if (hybridLatency <= threadedLatency) {
            return "Hybrid";
        } else {
            return "Threaded";
        }
    }

    /**
     * 가장 높은 안정성을 가진 서버 찾기 (성공률 기준)
     */
    public String getBestReliabilityServer() {
        double threadedReliability = threadedResult.isSuccessful() ?
                threadedResult.getSuccessRate() : 0;
        double hybridReliability = hybridResult.isSuccessful() ?
                hybridResult.getSuccessRate() : 0;
        double eventLoopReliability = eventLoopResult.isSuccessful() ?
                eventLoopResult.getSuccessRate() : 0;

        if (eventLoopReliability >= hybridReliability && eventLoopReliability >= threadedReliability) {
            return "EventLoop";
        } else if (hybridReliability >= threadedReliability) {
            return "Hybrid";
        } else {
            return "Threaded";
        }
    }

    /**
     * 처리량 비교 분석
     */
    public ComparisonAnalysis analyzeThroughput() {
        if (!threadedResult.isSuccessful() || !hybridResult.isSuccessful() ||
                !eventLoopResult.isSuccessful()) {
            return new ComparisonAnalysis("throughput", "Some tests failed", null);
        }

        double threadedTps = threadedResult.getThroughput();
        double hybridTps = hybridResult.getThroughput();
        double eventLoopTps = eventLoopResult.getThroughput();

        double maxTps = Math.max(Math.max(threadedTps, hybridTps), eventLoopTps);
        String winner = getBestThroughputServer();

        StringBuilder analysis = new StringBuilder();
        analysis.append(String.format("Winner: %s (%.1f TPS). ", winner, maxTps));

        // 각 서버 간 성능 비교
        if ("EventLoop".equals(winner)) {
            double hybridGap = ((eventLoopTps - hybridTps) / hybridTps) * 100;
            double threadedGap = ((eventLoopTps - threadedTps) / threadedTps) * 100;
            analysis.append(String.format("EventLoop is %.1f%% faster than Hybrid, %.1f%% faster than Threaded.",
                    hybridGap, threadedGap));
        } else if ("Hybrid".equals(winner)) {
            double eventLoopGap = ((hybridTps - eventLoopTps) / eventLoopTps) * 100;
            double threadedGap = ((hybridTps - threadedTps) / threadedTps) * 100;
            analysis.append(String.format("Hybrid is %.1f%% faster than EventLoop, %.1f%% faster than Threaded.",
                    eventLoopGap, threadedGap));
        }

        return new ComparisonAnalysis("throughput", analysis.toString(), winner);
    }

    /**
     * 지연시간 비교 분석
     */
    public ComparisonAnalysis analyzeLatency() {
        if (!threadedResult.isSuccessful() || !hybridResult.isSuccessful() ||
                !eventLoopResult.isSuccessful()) {
            return new ComparisonAnalysis("latency", "Some tests failed", null);
        }

        double threadedLatency = threadedResult.getAverageResponseTime();
        double hybridLatency = hybridResult.getAverageResponseTime();
        double eventLoopLatency = eventLoopResult.getAverageResponseTime();

        double minLatency = Math.min(Math.min(threadedLatency, hybridLatency), eventLoopLatency);
        String winner = getBestLatencyServer();

        StringBuilder analysis = new StringBuilder();
        analysis.append(String.format("Winner: %s (%.1fms avg). ", winner, minLatency));

        // P95 지연시간도 비교
        double threadedP95 = threadedResult.getPercentile95ResponseTime();
        double hybridP95 = hybridResult.getPercentile95ResponseTime();
        double eventLoopP95 = eventLoopResult.getPercentile95ResponseTime();

        analysis.append(String.format("P95 latencies - Threaded: %.1fms, Hybrid: %.1fms, EventLoop: %.1fms.",
                threadedP95, hybridP95, eventLoopP95));

        return new ComparisonAnalysis("latency", analysis.toString(), winner);
    }

    /**
     * 동시성 처리 능력 분석
     */
    public ComparisonAnalysis analyzeConcurrency() {
        int threadedConcurrency = threadedResult.getConcurrencyLevel();
        int hybridConcurrency = hybridResult.getConcurrencyLevel();
        int eventLoopConcurrency = eventLoopResult.getConcurrencyLevel();

        // 동시성 레벨이 같은 경우, 처리량으로 비교
        if (threadedConcurrency == hybridConcurrency && hybridConcurrency == eventLoopConcurrency) {
            return analyzeThroughput();
        }

        int maxConcurrency = Math.max(Math.max(threadedConcurrency, hybridConcurrency), eventLoopConcurrency);
        String analysis = String.format("Max concurrency achieved: %d. Threaded: %d, Hybrid: %d, EventLoop: %d.",
                maxConcurrency, threadedConcurrency, hybridConcurrency, eventLoopConcurrency);

        String winner = maxConcurrency == eventLoopConcurrency ? "EventLoop" :
                maxConcurrency == hybridConcurrency ? "Hybrid" : "Threaded";

        return new ComparisonAnalysis("concurrency", analysis, winner);
    }

    /**
     * 종합 성능 점수 비교
     */
    public ComparisonAnalysis analyzeOverallPerformance() {
        double threadedScore = threadedResult.isSuccessful() ?
                threadedResult.getOverallPerformanceScore() : 0;
        double hybridScore = hybridResult.isSuccessful() ?
                hybridResult.getOverallPerformanceScore() : 0;
        double eventLoopScore = eventLoopResult.isSuccessful() ?
                eventLoopResult.getOverallPerformanceScore() : 0;

        double maxScore = Math.max(Math.max(threadedScore, hybridScore), eventLoopScore);
        String winner = maxScore == eventLoopScore ? "EventLoop" :
                maxScore == hybridScore ? "Hybrid" : "Threaded";

        String analysis = String.format("Overall scores - Threaded: %.1f, Hybrid: %.1f, EventLoop: %.1f. Winner: %s.",
                threadedScore, hybridScore, eventLoopScore, winner);

        return new ComparisonAnalysis("overall", analysis, winner);
    }

    /**
     * 메모리 효율성 비교 (메모리 데이터가 있는 경우)
     */
    public ComparisonAnalysis analyzeMemoryEfficiency() {
        if (threadedResult.getMemoryIncrease() == 0 && hybridResult.getMemoryIncrease() == 0 &&
                eventLoopResult.getMemoryIncrease() == 0) {
            return new ComparisonAnalysis("memory", "No memory data available", null);
        }

        double threadedMemoryPerReq = threadedResult.getMemoryEfficiencyPerRequest();
        double hybridMemoryPerReq = hybridResult.getMemoryEfficiencyPerRequest();
        double eventLoopMemoryPerReq = eventLoopResult.getMemoryEfficiencyPerRequest();

        double minMemory = Math.min(Math.min(threadedMemoryPerReq, hybridMemoryPerReq), eventLoopMemoryPerReq);
        String winner = minMemory == eventLoopMemoryPerReq ? "EventLoop" :
                minMemory == hybridMemoryPerReq ? "Hybrid" : "Threaded";

        String analysis = String.format("Memory per request - Threaded: %.1f KB, Hybrid: %.1f KB, EventLoop: %.1f KB. Winner: %s.",
                threadedMemoryPerReq / 1024, hybridMemoryPerReq / 1024, eventLoopMemoryPerReq / 1024, winner);

        return new ComparisonAnalysis("memory", analysis, winner);
    }

    /**
     * 상세 비교 요약 생성
     */
    public DetailedComparison generateDetailedComparison() {
        return new DetailedComparison(
                testName,
                analyzeThroughput(),
                analyzeLatency(),
                analyzeConcurrency(),
                analyzeMemoryEfficiency(),
                analyzeOverallPerformance()
        );
    }

    // === Getters ===

    public String getTestName() {
        return testName;
    }

    public TestResult getThreadedResult() {
        return threadedResult;
    }

    public TestResult getHybridResult() {
        return hybridResult;
    }

    public TestResult getEventLoopResult() {
        return eventLoopResult;
    }

    /**
     * 특정 서버의 결과 반환
     */
    public TestResult getServerResult(String serverName) {
        switch (serverName.toLowerCase()) {
            case "threaded": return threadedResult;
            case "hybrid": return hybridResult;
            case "eventloop": return eventLoopResult;
            default: throw new IllegalArgumentException("Unknown server: " + serverName);
        }
    }

    /**
     * 모든 서버 결과를 맵으로 반환
     */
    public java.util.Map<String, TestResult> getAllServerResults() {
        java.util.Map<String, TestResult> results = new java.util.LinkedHashMap<>();
        results.put("Threaded", threadedResult);
        results.put("Hybrid", hybridResult);
        results.put("EventLoop", eventLoopResult);
        return results;
    }

    @Override
    public String toString() {
        return String.format("%s: Threaded(%.1f TPS), Hybrid(%.1f TPS), EventLoop(%.1f TPS)",
                testName,
                threadedResult.isSuccessful() ? threadedResult.getThroughput() : 0,
                hybridResult.isSuccessful() ? hybridResult.getThroughput() : 0,
                eventLoopResult.isSuccessful() ? eventLoopResult.getThroughput() : 0);
    }

    // === 내부 클래스들 ===

    /**
     * 비교 분석 결과
     */
    public static class ComparisonAnalysis {
        private final String metric;
        private final String analysis;
        private final String winner;

        public ComparisonAnalysis(String metric, String analysis, String winner) {
            this.metric = metric;
            this.analysis = analysis;
            this.winner = winner;
        }

        public String getMetric() { return metric; }
        public String getAnalysis() { return analysis; }
        public String getWinner() { return winner; }

        @Override
        public String toString() {
            return String.format("[%s] %s", metric.toUpperCase(), analysis);
        }
    }

    /**
     * 상세 비교 결과
     */
    public static class DetailedComparison {
        private final String testName;
        private final ComparisonAnalysis throughputAnalysis;
        private final ComparisonAnalysis latencyAnalysis;
        private final ComparisonAnalysis concurrencyAnalysis;
        private final ComparisonAnalysis memoryAnalysis;
        private final ComparisonAnalysis overallAnalysis;

        public DetailedComparison(String testName, ComparisonAnalysis throughputAnalysis,
                                  ComparisonAnalysis latencyAnalysis, ComparisonAnalysis concurrencyAnalysis,
                                  ComparisonAnalysis memoryAnalysis, ComparisonAnalysis overallAnalysis) {
            this.testName = testName;
            this.throughputAnalysis = throughputAnalysis;
            this.latencyAnalysis = latencyAnalysis;
            this.concurrencyAnalysis = concurrencyAnalysis;
            this.memoryAnalysis = memoryAnalysis;
            this.overallAnalysis = overallAnalysis;
        }

        public String getTestName() { return testName; }
        public ComparisonAnalysis getThroughputAnalysis() { return throughputAnalysis; }
        public ComparisonAnalysis getLatencyAnalysis() { return latencyAnalysis; }
        public ComparisonAnalysis getConcurrencyAnalysis() { return concurrencyAnalysis; }
        public ComparisonAnalysis getMemoryAnalysis() { return memoryAnalysis; }
        public ComparisonAnalysis getOverallAnalysis() { return overallAnalysis; }

        /**
         * 가장 많이 이긴 서버 반환
         */
        public String getOverallWinner() {
            java.util.Map<String, Integer> winCount = new java.util.HashMap<>();

            String[] winners = {
                    throughputAnalysis.getWinner(),
                    latencyAnalysis.getWinner(),
                    concurrencyAnalysis.getWinner(),
                    overallAnalysis.getWinner()
            };

            for (String winner : winners) {
                if (winner != null) {
                    winCount.put(winner, winCount.getOrDefault(winner, 0) + 1);
                }
            }

            return winCount.entrySet().stream()
                    .max(java.util.Map.Entry.comparingByValue())
                    .map(java.util.Map.Entry::getKey)
                    .orElse("No clear winner");
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== ").append(testName).append(" Detailed Comparison ===\n");
            sb.append(throughputAnalysis).append("\n");
            sb.append(latencyAnalysis).append("\n");
            sb.append(concurrencyAnalysis).append("\n");
            if (memoryAnalysis.getWinner() != null) {
                sb.append(memoryAnalysis).append("\n");
            }
            sb.append(overallAnalysis).append("\n");
            sb.append("Overall Winner: ").append(getOverallWinner());
            return sb.toString();
        }
    }
}