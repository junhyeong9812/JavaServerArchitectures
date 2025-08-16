package server.benchmark;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 동시성 테스트 도구
 *
 * 기능:
 * 1. 동시 연결 부하 테스트
 * 2. 처리량 측정 (TPS)
 * 3. 응답 시간 분석
 * 4. 에러율 계산
 * 5. 점진적 부하 증가 테스트
 */
public class ConcurrencyTester {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrencyTester.class);

    private final LoadTestClient client;
    private final LatencyProfiler latencyProfiler;

    public ConcurrencyTester() {
        this.client = new LoadTestClient();
        this.latencyProfiler = new LatencyProfiler();
    }

    /**
     * 기본 동시성 테스트
     */
    public TestResult runTest(String host, int port, String path,
                              int concurrency, int totalRequests, int timeoutSeconds) {
        logger.info("Running concurrency test - {}:{}{} (concurrency={}, requests={})",
                host, port, path, concurrency, totalRequests);

        long startTime = System.currentTimeMillis();

        try {
            // 웜업
            warmup(host, port, path, Math.min(concurrency, 10));

            // 실제 테스트 실행
            List<LoadTestClient.RequestResult> results = client.executeLoadTest(
                    host, port, path, concurrency, totalRequests);

            long endTime = System.currentTimeMillis();
            long durationMs = endTime - startTime;

            return analyzeResults(results, durationMs, concurrency);

        } catch (Exception e) {
            logger.error("Concurrency test failed", e);
            return TestResult.failed(e.getMessage());
        }
    }

    /**
     * 장시간 지속 테스트
     */
    public TestResult runLongTest(String host, int port, String path,
                                  int concurrency, int durationSeconds) {
        logger.info("Running long-duration test - {}:{}{} (concurrency={}, duration={}s)",
                host, port, path, concurrency, durationSeconds);

        long startTime = System.currentTimeMillis();

        try {
            // 웜업
            warmup(host, port, path, Math.min(concurrency, 10));

            // 지속적 부하 테스트
            List<LoadTestClient.RequestResult> results = client.executeContinuousLoadTest(
                    host, port, path, concurrency, durationSeconds);

            long endTime = System.currentTimeMillis();
            long actualDurationMs = endTime - startTime;

            return analyzeResults(results, actualDurationMs, concurrency);

        } catch (Exception e) {
            logger.error("Long-duration test failed", e);
            return TestResult.failed(e.getMessage());
        }
    }

    /**
     * 점진적 부하 증가 테스트
     */
    public List<TestResult> runRampUpTest(String host, int port, String path,
                                          int startConcurrency, int maxConcurrency,
                                          int step, int requestsPerLevel) {
        logger.info("Running ramp-up test - {}:{}{} ({}->{}+{}, {} requests per level)",
                host, port, path, startConcurrency, maxConcurrency, step, requestsPerLevel);

        List<TestResult> results = new ArrayList<>();

        for (int concurrency = startConcurrency; concurrency <= maxConcurrency; concurrency += step) {
            logger.info("Testing concurrency level: {}", concurrency);

            TestResult result = runTest(host, port, path, concurrency, requestsPerLevel, 120);
            result.setConcurrencyLevel(concurrency);
            results.add(result);

            // 서버 회복 시간
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            // 실패율이 너무 높으면 중단
            if (result.getErrorRate() > 50.0) {
                logger.warn("High error rate ({}%) at concurrency {}, stopping ramp-up",
                        result.getErrorRate(), concurrency);
                break;
            }
        }

        return results;
    }

    /**
     * 스트레스 테스트 - 서버 한계점 찾기
     */
    public StressTestResult runStressTest(String host, int port, String path,
                                          int maxConcurrency, int requestsPerLevel) {
        logger.info("Running stress test to find server limits - {}:{}{}",
                host, port, path);

        List<TestResult> results = new ArrayList<>();
        int successfulConcurrency = 0;
        int maxStableConcurrency = 0;

        // 이진 탐색 방식으로 한계점 찾기
        int low = 1;
        int high = maxConcurrency;
        int lastSuccessful = 0;

        while (low <= high) {
            int mid = (low + high) / 2;
            logger.info("Testing stress level: {} concurrent connections", mid);

            TestResult result = runTest(host, port, path, mid, requestsPerLevel, 180);
            result.setConcurrencyLevel(mid);
            results.add(result);

            // 성공 기준: 에러율 < 5%, 평균 응답시간 < 5초
            boolean successful = result.getErrorRate() < 5.0 &&
                    result.getAverageResponseTime() < 5000;

            if (successful) {
                lastSuccessful = mid;
                maxStableConcurrency = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }

            // 서버 회복 시간
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return new StressTestResult(results, maxStableConcurrency, lastSuccessful);
    }

    /**
     * 메모리 압박 테스트
     */
    public TestResult runMemoryPressureTest(String host, int port, String path,
                                            int concurrency, int totalRequests) {
        logger.info("Running memory pressure test - {}:{}{}", host, port, path);

        // GC 강제 실행으로 초기 상태 정리
        System.gc();

        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        TestResult result = runTest(host, port, path, concurrency, totalRequests, 300);

        // 메모리 사용량 측정
        System.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;

        result.setMemoryIncrease(memoryIncrease);

        logger.info("Memory pressure test completed - memory increase: {}MB",
                memoryIncrease / (1024 * 1024));

        return result;
    }

    /**
     * CPU 집약적 작업 테스트
     */
    public TestResult runCpuIntensiveTest(String host, int port, String path,
                                          int concurrency, int totalRequests) {
        logger.info("Running CPU intensive test - {}:{}{}", host, port, path);

        // CPU 집약적 작업은 더 긴 타임아웃 필요
        return runTest(host, port, path, concurrency, totalRequests, 300);
    }

    /**
     * 웜업 실행
     */
    private void warmup(String host, int port, String path, int requests) {
        logger.debug("Warming up server with {} requests", requests);
        client.warmup(host, port, path, requests);
    }

    /**
     * 결과 분석
     */
    private TestResult analyzeResults(List<LoadTestClient.RequestResult> results,
                                      long durationMs, int concurrency) {
        if (results.isEmpty()) {
            return TestResult.failed("No results collected");
        }

        // 기본 통계
        int totalRequests = results.size();
        long successfulRequests = results.stream()
                .mapToLong(r -> r.isSuccess() ? 1 : 0)
                .sum();

        double errorRate = (totalRequests - successfulRequests) * 100.0 / totalRequests;
        double throughput = (double) successfulRequests / (durationMs / 1000.0);

        // 응답 시간 분석 (성공한 요청만)
        List<Double> responseTimes = results.stream()
                .filter(LoadTestClient.RequestResult::isSuccess)
                .map(LoadTestClient.RequestResult::getResponseTimeMillis)
                .sorted()
                .toList();

        LatencyProfiler.LatencyStats latencyStats = latencyProfiler.analyze(responseTimes);

        // 상태 코드 분석
        Map<Integer, Long> statusCodes = results.stream()
                .collect(Collectors.groupingBy(
                        LoadTestClient.RequestResult::getStatusCode,
                        Collectors.counting()
                ));

        return new TestResult(
                true,                                    // successful
                null,                                    // error message
                totalRequests,
                (int) successfulRequests,
                errorRate,
                durationMs,
                throughput,
                latencyStats.getAverage(),
                latencyStats.getMin(),
                latencyStats.getMax(),
                latencyStats.getMedian(),
                latencyStats.getPercentile95(),
                latencyStats.getPercentile99(),
                concurrency,
                statusCodes,
                0 // memory increase (별도 설정)
        );
    }

    /**
     * 스트레스 테스트 결과
     */
    public static class StressTestResult {
        private final List<TestResult> allResults;
        private final int maxStableConcurrency;
        private final int maxTestedConcurrency;

        public StressTestResult(List<TestResult> allResults, int maxStableConcurrency, int maxTestedConcurrency) {
            this.allResults = new ArrayList<>(allResults);
            this.maxStableConcurrency = maxStableConcurrency;
            this.maxTestedConcurrency = maxTestedConcurrency;
        }

        public List<TestResult> getAllResults() { return new ArrayList<>(allResults); }
        public int getMaxStableConcurrency() { return maxStableConcurrency; }
        public int getMaxTestedConcurrency() { return maxTestedConcurrency; }

        public TestResult getBestResult() {
            return allResults.stream()
                    .filter(r -> r.getErrorRate() < 5.0)
                    .max(Comparator.comparing(TestResult::getThroughput))
                    .orElse(allResults.get(0));
        }

        @Override
        public String toString() {
            return String.format("StressTestResult{maxStable=%d, maxTested=%d, totalTests=%d}",
                    maxStableConcurrency, maxTestedConcurrency, allResults.size());
        }
    }

    /**
     * 동시성 테스트를 위한 테스트 결과 확장
     */
    public static class ConcurrencyTestSuite {
        private final String serverName;
        private final List<TestResult> results;
        private final StressTestResult stressResult;

        public ConcurrencyTestSuite(String serverName, List<TestResult> results, StressTestResult stressResult) {
            this.serverName = serverName;
            this.results = new ArrayList<>(results);
            this.stressResult = stressResult;
        }

        public String getServerName() { return serverName; }
        public List<TestResult> getResults() { return new ArrayList<>(results); }
        public StressTestResult getStressResult() { return stressResult; }

        public TestResult getBestThroughputResult() {
            return results.stream()
                    .max(Comparator.comparing(TestResult::getThroughput))
                    .orElse(null);
        }

        public TestResult getBestLatencyResult() {
            return results.stream()
                    .filter(r -> r.getErrorRate() < 1.0) // 에러율 1% 미만만 고려
                    .min(Comparator.comparing(TestResult::getAverageResponseTime))
                    .orElse(null);
        }

        public double getMaxStableThroughput() {
            return stressResult != null ?
                    stressResult.getBestResult().getThroughput() : 0.0;
        }

        @Override
        public String toString() {
            return String.format("ConcurrencyTestSuite{server='%s', tests=%d, maxStable=%d}",
                    serverName, results.size(),
                    stressResult != null ? stressResult.getMaxStableConcurrency() : 0);
        }
    }
}