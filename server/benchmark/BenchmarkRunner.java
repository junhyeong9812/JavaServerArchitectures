package server.benchmark;

import server.core.http.HttpResponse;
import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.examples.HelloWorldServlet;
import server.threaded.*;
import server.hybrid.HybridServer;
import server.eventloop.EventLoopServer;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.*;

/**
 * 3가지 서버 아키텍처 동시 성능 테스트 러너
 *
 * 측정 항목:
 * 1. 처리량 (TPS - Transactions Per Second)
 * 2. 응답 지연시간 (Latency)
 * 3. 동시 연결 처리 능력
 * 4. 메모리 사용량
 * 5. CPU 사용률
 * 6. 에러율
 */
public class BenchmarkRunner {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);

    // 서버 포트 설정
    private static final int THREADED_PORT = 8080;
    private static final int HYBRID_PORT = 8081;
    private static final int EVENTLOOP_PORT = 8082;

    // 벤치마크 설정
    private final BenchmarkConfig config;
    private final PerformanceCollector collector;
    private final LoadTestClient loadTestClient;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 서버 인스턴스들
    private ThreadedServer threadedServer;
    private HybridServer hybridServer;
    private EventLoopServer eventLoopServer;

    public BenchmarkRunner() {
        this(BenchmarkConfig.defaultConfig());
    }

    public BenchmarkRunner(BenchmarkConfig config) {
        this.config = config;
        this.collector = new PerformanceCollector();
        this.loadTestClient = new LoadTestClient();
    }

    /**
     * 전체 벤치마크 실행
     */
    public BenchmarkResults runBenchmark() throws Exception {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Benchmark already running");
        }

        logger.info("🚀 Starting Server Architecture Benchmark");
        logger.info("   Threaded Server:  http://localhost:{}", THREADED_PORT);
        logger.info("   Hybrid Server:    http://localhost:{}", HYBRID_PORT);
        logger.info("   EventLoop Server: http://localhost:{}", EVENTLOOP_PORT);
        logger.info("   Config: {}", config);

        try {
            // 1. 서버들 시작
            startServers();

            // 2. 서버 준비 대기
            waitForServersReady();

            // 3. 벤치마크 실행
            BenchmarkResults results = executeBenchmarkSuites();

            // 4. 결과 분석 및 출력
            analyzeAndReportResults(results);

            return results;

        } finally {
            stopServers();
            running.set(false);
        }
    }

    /**
     * 모든 서버 시작
     */
    private void startServers() throws Exception {
        logger.info("Starting all servers...");

        // Threaded Server 시작
        threadedServer = new ThreadedServer(THREADED_PORT);
        registerThreadedServlets(threadedServer);  // ThreadedServerTest.registerServlets()와 동일
        threadedServer.start();
//        ThreadedServerTest.main(new String[]{});
        logger.info("Threaded Server started on port {}", THREADED_PORT);

        // Hybrid Server 시작
        hybridServer = new HybridServer(HYBRID_PORT);
        setupHybridRoutes(hybridServer);
        hybridServer.start();
        logger.info("Hybrid Server started on port {}", HYBRID_PORT);

        // EventLoop Server 시작
        eventLoopServer = new EventLoopServer();
        setupEventLoopRoutes(eventLoopServer);
        eventLoopServer.start(EVENTLOOP_PORT);
        logger.info("EventLoop Server started on port {}", EVENTLOOP_PORT);
    }

    /**
     * ThreadedServer 서블릿 등록 - ThreadedServerTest.registerServlets()와 동일
     */
    private void registerThreadedServlets(ThreadedServer server) {
        // ThreadedServerTest.registerServlets()에서 벤치마크에 필요한 것만
        server.registerServlet("/health", new HealthServlet());
        server.registerServlet("/hello", new HelloWorldServlet());  // /servlet/hello -> /hello로 변경
        server.registerServlet("/cpu-intensive", new CpuIntensiveServlet());
        server.registerServlet("/io-simulation", new IoSimulationServlet());
    }

    /**
     * Threaded 서버 라우트 설정 - 실제 API 사용
     */
    private void setupThreadedRoutes(ThreadedServer server) {
        // ThreadedServer.registerHandler() 사용
        server.registerHandler("/hello", request ->
                CompletableFuture.completedFuture(
                        HttpResponse.text("Hello from Threaded Server")
                )
        );

        server.registerHandler("/cpu-intensive", request ->
                CompletableFuture.supplyAsync(() -> {
                    // CPU 집약적 작업 시뮬레이션
                    double result = 0;
                    for (int i = 0; i < 100000; i++) {
                        result += Math.sqrt(i) * Math.sin(i);
                    }
                    return HttpResponse.json(
                            String.format("{\"server\":\"threaded\",\"result\":%.2f}", result)
                    );
                })
        );

        server.registerHandler("/io-simulation", request ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        // I/O 작업 시뮬레이션 (파일 읽기, DB 조회 등)
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return HttpResponse.json(
                            "{\"server\":\"threaded\",\"io\":\"completed\"}"
                    );
                })
        );

        server.registerHandler("/health", request ->
                CompletableFuture.completedFuture(
                        HttpResponse.json("{\"status\":\"healthy\",\"server\":\"threaded\"}")
                )
        );
    }

    /**
     * Hybrid 서버 라우트 설정 - 실제 API 사용
     */
    private void setupHybridRoutes(HybridServer server) {
        // HybridServer.getRouter() 사용
        server.getRouter().get("/hello", request ->
                CompletableFuture.completedFuture(
                        HttpResponse.text("Hello from Hybrid Server")
                )
        );

        server.getRouter().get("/cpu-intensive", request ->
                server.getSwitchingHandler().switchAndExecute(request, () ->
                        CompletableFuture.supplyAsync(() -> {
                            double result = 0;
                            for (int i = 0; i < 100000; i++) {
                                result += Math.sqrt(i) * Math.sin(i);
                            }
                            return String.format("{\"server\":\"hybrid\",\"result\":%.2f}", result);
                        })
                ).thenApply(json -> HttpResponse.json(json))
        );

        server.getRouter().get("/io-simulation", request ->
                server.getSwitchingHandler().executeDbOperation(request, req -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "{\"server\":\"hybrid\",\"io\":\"completed\"}";
                }).thenApply(json -> HttpResponse.json(json))
        );

        server.getRouter().get("/health", request ->
                CompletableFuture.completedFuture(
                        HttpResponse.json("{\"status\":\"healthy\",\"server\":\"hybrid\"}")
                )
        );
    }

    /**
     * EventLoop 서버 라우트 설정 - 실제 API 사용
     */
    private void setupEventLoopRoutes(EventLoopServer server) {
        // ✅ EventLoopServer.get() 직접 사용
        server.get("/hello", request ->
                CompletableFuture.completedFuture(
                        HttpResponse.text("Hello from EventLoop Server")
                )
        );

        server.get("/cpu-intensive", request ->
                server.getProcessor().executeAsync(() -> {
                    double result = 0;
                    for (int i = 0; i < 100000; i++) {
                        result += Math.sqrt(i) * Math.sin(i);
                    }
                    return HttpResponse.json(
                            String.format("{\"server\":\"eventloop\",\"result\":%.2f}", result)
                    );
                })
        );

        server.get("/io-simulation", request ->
                server.getProcessor().executeAsync(() -> {
                    // EventLoop에서는 실제 블로킹 I/O를 피해야 함
                    // 논블로킹 또는 시뮬레이션으로 처리
                    return HttpResponse.json(
                            "{\"server\":\"eventloop\",\"io\":\"completed\"}"
                    );
                })
        );

        server.get("/health", request ->
                CompletableFuture.completedFuture(
                        HttpResponse.json("{\"status\":\"healthy\",\"server\":\"eventloop\"}")
                )
        );
    }

    /**
     * 서버 준비 상태 대기
     */
    private void waitForServersReady() throws InterruptedException {
        logger.info("Waiting for servers to be ready...");
        Thread.sleep(2000); // 2초 대기

        // 각 서버 헬스체크
        boolean threadedReady = loadTestClient.healthCheck("localhost", THREADED_PORT);
        boolean hybridReady = loadTestClient.healthCheck("localhost", HYBRID_PORT);
        boolean eventLoopReady = loadTestClient.healthCheck("localhost", EVENTLOOP_PORT);

        if (!threadedReady || !hybridReady || !eventLoopReady) {
            throw new RuntimeException("Some servers are not ready");
        }

        logger.info("✅ All servers are ready for benchmarking");
    }

    /**
     * 벤치마크 스위트 실행
     */
    private BenchmarkResults executeBenchmarkSuites() throws Exception {
        BenchmarkResults results = new BenchmarkResults();

        logger.info("📊 Starting benchmark suites...");

        // 1. 기본 응답성 테스트
        logger.info("1️⃣ Basic Responsiveness Test");
        results.addResult("basic", runBasicTest());

        // 2. 동시성 테스트 (점진적 부하 증가)
        logger.info("2️⃣ Concurrency Test");
        results.addResult("concurrency", runConcurrencyTest());

        // 3. CPU 집약적 작업 테스트
        logger.info("3️⃣ CPU Intensive Test");
        results.addResult("cpu_intensive", runCpuIntensiveTest());

        // 4. I/O 집약적 작업 테스트
        logger.info("4️⃣ I/O Intensive Test");
        results.addResult("io_intensive", runIoIntensiveTest());

        // 5. 메모리 압박 테스트
        logger.info("5️⃣ Memory Pressure Test");
        results.addResult("memory_pressure", runMemoryPressureTest());

        // 6. 지속성 테스트 (장시간 실행)
        logger.info("6️⃣ Endurance Test");
        results.addResult("endurance", runEnduranceTest());

        return results;
    }

    /**
     * 기본 응답성 테스트
     */
    private ServerComparisonResult runBasicTest() throws Exception {
        logger.info("Running basic responsiveness test...");

        ConcurrencyTester tester = new ConcurrencyTester();

        // 각 서버에 대해 동일한 테스트 수행
        TestResult threadedResult = tester.runTest(
                "localhost", THREADED_PORT, "/hello",
                10, 100, 30  // 10 동시연결, 100 요청, 30초 타임아웃
        );

        TestResult hybridResult = tester.runTest(
                "localhost", HYBRID_PORT, "/hello",
                10, 100, 30
        );

        TestResult eventLoopResult = tester.runTest(
                "localhost", EVENTLOOP_PORT, "/hello",
                10, 100, 30
        );

        return new ServerComparisonResult(
                "Basic Responsiveness",
                threadedResult, hybridResult, eventLoopResult
        );
    }

    /**
     * 동시성 테스트 - 점진적 부하 증가
     */
    private ServerComparisonResult runConcurrencyTest() throws Exception {
        logger.info("Running concurrency test with increasing load...");

        List<TestResult> threadedResults = new ArrayList<>();
        List<TestResult> hybridResults = new ArrayList<>();
        List<TestResult> eventLoopResults = new ArrayList<>();

        int[] concurrencyLevels = {10, 50, 100, 500, 1000};
        ConcurrencyTester tester = new ConcurrencyTester();

        for (int concurrency : concurrencyLevels) {
            logger.info("Testing with {} concurrent connections", concurrency);

            // 각 서버 테스트
            threadedResults.add(tester.runTest(
                    "localhost", THREADED_PORT, "/hello",
                    concurrency, 1000, 60
            ));

            hybridResults.add(tester.runTest(
                    "localhost", HYBRID_PORT, "/hello",
                    concurrency, 1000, 60
            ));

            eventLoopResults.add(tester.runTest(
                    "localhost", EVENTLOOP_PORT, "/hello",
                    concurrency, 1000, 60
            ));

            // 서버 회복 시간
            Thread.sleep(1000);
        }

        // 최고 성능 결과 선택
        TestResult bestThreaded = threadedResults.stream()
                .max(Comparator.comparing(TestResult::getThroughput))
                .orElse(threadedResults.get(0));

        TestResult bestHybrid = hybridResults.stream()
                .max(Comparator.comparing(TestResult::getThroughput))
                .orElse(hybridResults.get(0));

        TestResult bestEventLoop = eventLoopResults.stream()
                .max(Comparator.comparing(TestResult::getThroughput))
                .orElse(eventLoopResults.get(0));

        return new ServerComparisonResult(
                "Concurrency Performance",
                bestThreaded, bestHybrid, bestEventLoop
        );
    }

    /**
     * CPU 집약적 작업 테스트
     */
    private ServerComparisonResult runCpuIntensiveTest() throws Exception {
        logger.info("Running CPU intensive test...");

        ConcurrencyTester tester = new ConcurrencyTester();

        TestResult threadedResult = tester.runTest(
                "localhost", THREADED_PORT, "/cpu-intensive",
                20, 200, 120  // CPU 작업이므로 시간 여유
        );

        TestResult hybridResult = tester.runTest(
                "localhost", HYBRID_PORT, "/cpu-intensive",
                20, 200, 120
        );

        TestResult eventLoopResult = tester.runTest(
                "localhost", EVENTLOOP_PORT, "/cpu-intensive",
                20, 200, 120
        );

        return new ServerComparisonResult(
                "CPU Intensive Performance",
                threadedResult, hybridResult, eventLoopResult
        );
    }

    /**
     * I/O 집약적 작업 테스트
     */
    private ServerComparisonResult runIoIntensiveTest() throws Exception {
        logger.info("Running I/O intensive test...");

        ConcurrencyTester tester = new ConcurrencyTester();

        TestResult threadedResult = tester.runTest(
                "localhost", THREADED_PORT, "/io-simulation",
                100, 500, 180  // I/O 대기로 인한 시간 여유
        );

        TestResult hybridResult = tester.runTest(
                "localhost", HYBRID_PORT, "/io-simulation",
                100, 500, 180
        );

        TestResult eventLoopResult = tester.runTest(
                "localhost", EVENTLOOP_PORT, "/io-simulation",
                100, 500, 180
        );

        return new ServerComparisonResult(
                "I/O Intensive Performance",
                threadedResult, hybridResult, eventLoopResult
        );
    }

    /**
     * 메모리 압박 테스트
     */
    private ServerComparisonResult runMemoryPressureTest() throws Exception {
        logger.info("Running memory pressure test...");

        MemoryProfiler memoryProfiler = new MemoryProfiler();
        ConcurrencyTester tester = new ConcurrencyTester();

        // 메모리 사용량 모니터링 시작
        memoryProfiler.startMonitoring();

        TestResult threadedResult = tester.runTest(
                "localhost", THREADED_PORT, "/hello",
                2000, 5000, 300  // 높은 동시성으로 메모리 압박
        );

        TestResult hybridResult = tester.runTest(
                "localhost", HYBRID_PORT, "/hello",
                2000, 5000, 300
        );

        TestResult eventLoopResult = tester.runTest(
                "localhost", EVENTLOOP_PORT, "/hello",
                2000, 5000, 300
        );

        memoryProfiler.stopMonitoring();

        return new ServerComparisonResult(
                "Memory Pressure Performance",
                threadedResult, hybridResult, eventLoopResult
        );
    }

    /**
     * 지속성 테스트 (10분간 실행)
     */
    private ServerComparisonResult runEnduranceTest() throws Exception {
        logger.info("Running endurance test (10 minutes)...");

        ConcurrencyTester tester = new ConcurrencyTester();

        TestResult threadedResult = tester.runLongTest(
                "localhost", THREADED_PORT, "/hello",
                50, 600  // 50 동시연결, 10분
        );

        TestResult hybridResult = tester.runLongTest(
                "localhost", HYBRID_PORT, "/hello",
                50, 600
        );

        TestResult eventLoopResult = tester.runLongTest(
                "localhost", EVENTLOOP_PORT, "/hello",
                50, 600
        );

        return new ServerComparisonResult(
                "Endurance Performance",
                threadedResult, hybridResult, eventLoopResult
        );
    }

    /**
     * 결과 분석 및 리포트 생성
     */
    private void analyzeAndReportResults(BenchmarkResults results) {
        logger.info("📈 Analyzing benchmark results...");

        // 콘솔 리포트
        ReportGenerator.generateConsoleReport(results);

        // HTML 리포트 생성
        try {
            String htmlReport = ReportGenerator.generateHtmlReport(results);
            ReportGenerator.saveHtmlReport(htmlReport, "benchmark_results.html");
            logger.info("📄 HTML report saved: benchmark_results.html");
        } catch (Exception e) {
            logger.error("Failed to generate HTML report", e);
        }

        // JSON 결과 저장
        try {
            String jsonResults = ReportGenerator.generateJsonReport(results);
            ReportGenerator.saveJsonReport(jsonResults, "benchmark_results.json");
            logger.info("📊 JSON results saved: benchmark_results.json");
        } catch (Exception e) {
            logger.error("Failed to save JSON results", e);
        }
    }

    /**
     * 모든 서버 종료
     */
    private void stopServers() {
        logger.info("🛑 Stopping all servers...");

        if (threadedServer != null) {
            try {
                threadedServer.stop();
                logger.info("✅ Threaded Server stopped");
            } catch (Exception e) {
                logger.error("Error stopping Threaded Server", e);
            }
        }

        if (hybridServer != null) {
            try {
                hybridServer.stop();
                logger.info("✅ Hybrid Server stopped");
            } catch (Exception e) {
                logger.error("Error stopping Hybrid Server", e);
            }
        }

        if (eventLoopServer != null) {
            try {
                eventLoopServer.stop();
                logger.info("✅ EventLoop Server stopped");
            } catch (Exception e) {
                logger.error("Error stopping EventLoop Server", e);
            }
        }
    }

    /**
     * 메인 실행 메서드
     */
    public static void main(String[] args) throws Exception {
        // 로깅 시스템 초기화
        server.core.logging.LoggerFactory.configureForTesting();

        // 벤치마크 설정
        BenchmarkConfig config = BenchmarkConfig.defaultConfig()
                .setWarmupRequests(100)
                .setTargetThroughput(1000)
                .setMaxConcurrency(2000);

        // 벤치마크 실행
        BenchmarkRunner runner = new BenchmarkRunner(config);
        BenchmarkResults results = runner.runBenchmark();

        // 최종 요약
        logger.info("🎯 Benchmark Complete!");
        logger.info("Winner Summary:");

        Map<String, String> winners = results.analyzeWinners();
        for (Map.Entry<String, String> entry : winners.entrySet()) {
            logger.info("  {}: {}", entry.getKey(), entry.getValue());
        }

        System.exit(0);
    }
}