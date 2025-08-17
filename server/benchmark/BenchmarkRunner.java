package server.benchmark;

import server.core.http.HttpResponse;
import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.routing.Router;
import server.examples.HelloWorldServlet;
import server.hybrid.HybridMiniServletContainer;
import server.threaded.*;
import server.hybrid.HybridServer;
import server.eventloop.EventLoopServer;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    // 결과 파일 경로
    private static final String RESULT_FILE_PATH = "result.txt";

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

        logger.info("Starting Server Architecture Benchmark");
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

            // 5. 결과를 최종 result.txt 파일에 저장
            saveResultsToFile(results);

            // 6. 부분 결과 파일들 정리 (선택사항)
            cleanupPartialFiles();

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

        // Threaded Server 시작 (서블릿만 사용, 라우트 핸들러 제거)
//        threadedServer = new ThreadedServer(THREADED_PORT);
//        registerThreadedServlets(threadedServer);
//        threadedServer.start();
        // ✅ Threaded Server 시작 (성능 최적화 설정)
        // ✅ Threaded Server 시작 (톰캣 스타일 최적화 설정)
        ServerConfig optimizedConfig = new ServerConfig()
                .setDebugMode(false)  // 디버그 모드 비활성화
                .setRequestHandlerConfig(
                        new RequestHandlerConfig()
                                .setDebugMode(false)  // 핸들러 디버그 모드 비활성화
                                .setSocketTimeout(15000)  // 타임아웃 단축
                )
                .setThreadPoolConfig(
                        new ThreadPoolConfig()
                                .setCorePoolSize(20)           // 🔧 코어 스레드 증가 (10 → 20)
                                .setMaxPoolSize(200)           // 🔧 최대 스레드 대폭 증가 (100 → 200)
                                .setQueueCapacity(50)          // 🔧 큐 사이즈 감소 (200 → 50)
                                .setKeepAliveTime(30)          // 🔧 스레드 유지 시간 단축
                                .setDebugMode(false)           // 디버그 모드 비활성화
                                .setMonitorInterval(60)        // 🔧 모니터링 간격 늘림
                );

        threadedServer = new ThreadedServer(THREADED_PORT, new Router(), optimizedConfig);
        registerThreadedServlets(threadedServer);
        threadedServer.start();
        logger.info("Threaded Server started on port {} (Tomcat-style optimized)", THREADED_PORT);

        // Hybrid Server 시작
        hybridServer = new HybridServer(HYBRID_PORT);
        registerHybridServlets(hybridServer.getServletContainer());
        setupHybridRoutes(hybridServer);
        hybridServer.start();
        logger.info("Hybrid Server started on port {}", HYBRID_PORT);

        // EventLoop Server 시작 (수정됨)
        eventLoopServer = new EventLoopServer();
        registerEventLoopServlets(eventLoopServer); // 서블릿 등록
        eventLoopServer.start(EVENTLOOP_PORT);
        logger.info("EventLoop Server started on port {}", EVENTLOOP_PORT);
    }

    /**
     * ThreadedServer 서블릿 등록 (성능 최적화: 서블릿만 사용)
     */
    private void registerThreadedServlets(ThreadedServer server) {
        // 벤치마크용 서블릿만 등록 (라우트 핸들러는 사용하지 않음)
        server.registerServlet("/health", new HealthServlet());
        server.registerServlet("/hello", new HelloWorldServlet());
        server.registerServlet("/cpu-intensive", new CpuIntensiveServlet());
        server.registerServlet("/io-simulation", new IoSimulationServlet());

        logger.info("ThreadedServer: 4 servlets registered (optimized for benchmark)");
    }

    // ✅ setupThreadedRoutes() 메서드 제거됨
    // ThreadedServer는 서블릿만 사용하여 성능 최적화

    /**
     * Hybrid Server용 서블릿 등록
     */
    private void registerHybridServlets(HybridMiniServletContainer container) {
        try {
            container.registerServlet("Health",
                    new server.hybrid.HealthAsyncServlet(), "/health");

            container.registerServlet("HelloWorld",
                    new server.hybrid.HelloWorldAsyncServlet(), "/hello");

            container.registerServlet("CpuIntensive",
                    new server.hybrid.CpuIntensiveAsyncServlet(), "/cpu-intensive");

            container.registerServlet("IoSimulation",
                    new server.hybrid.IoSimulationAsyncServlet(), "/io-simulation");

            logger.info("Hybrid Server servlets registered successfully - 4 servlets");

        } catch (Exception e) {
            logger.error("Failed to register Hybrid Server servlets", e);
            throw new RuntimeException("Hybrid servlet registration failed", e);
        }
    }

    /**
     * Hybrid 서버 라우트 설정
     */
    private void setupHybridRoutes(HybridServer server) {
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
     * 새로운 EventLoop 서버용 서블릿 등록 메서드
     */
    private void registerEventLoopServlets(EventLoopServer server) {
        try {
            // EventLoop 서블릿 인스턴스 생성
            server.eventloop.HealthServlet healthServlet = new server.eventloop.HealthServlet();
            server.eventloop.HelloWorldServlet helloServlet = new server.eventloop.HelloWorldServlet();
            server.eventloop.CpuIntensiveServlet cpuServlet = new server.eventloop.CpuIntensiveServlet();
            server.eventloop.IoSimulationServlet ioServlet = new server.eventloop.IoSimulationServlet();

            // 🔧 핵심: ThreadedServer와 동일한 URL 패턴으로 라우트 등록

            // 1. Health check
            server.get("/health", request -> healthServlet.handleRequest());

            // 2. Hello World
            server.get("/hello", request -> helloServlet.handleRequest(request));

            // 3. CPU Intensive 작업
            server.get("/cpu-intensive", request -> cpuServlet.handleRequest(request));

            // 4. I/O Simulation (🔧 가장 중요한 수정)
            server.get("/io-simulation", request -> ioServlet.handleRequest(request));

            // 추가 엔드포인트들
            server.get("/hello-json", request -> helloServlet.handleJsonRequest(request));
            server.get("/cpu-heavy", request -> cpuServlet.handleHeavyRequest(request));
            server.get("/cpu-param", request -> cpuServlet.handleParameterizedRequest(request));
            server.get("/io-variable", request -> ioServlet.handleVariableDelayRequest(request));
            server.get("/io-complex", request -> ioServlet.handleComplexIoRequest(request));
            server.get("/io-chain", request -> ioServlet.handleAsyncChainRequest(request));
            server.get("/io-scheduled", request -> ioServlet.handleScheduledRequest(request));

            // 상태 정보 엔드포인트
            server.get("/cpu-stats", request -> cpuServlet.getExecutorStats());
            server.get("/io-stats", request -> ioServlet.getExecutorStats());

            logger.info("EventLoop Server servlets registered successfully - 4 core servlets + 8 additional endpoints");

        } catch (Exception e) {
            logger.error("Failed to register EventLoop Server servlets", e);
            throw new RuntimeException("EventLoop servlet registration failed", e);
        }
    }

    /**
     * EventLoop 서버 라우트 설정
     */
    private void setupEventLoopRoutes(EventLoopServer server) {
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

        //수정 방안: CompletableFuture.delayedExecutor 사용
        server.get("/io-simulation", request -> {
            CompletableFuture<HttpResponse> delayed = new CompletableFuture<>();

            // 별도 스케줄러로 100ms 후 완료
            CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)
                    .execute(() -> delayed.complete(
                            HttpResponse.json("{\"server\":\"eventloop\",\"io\":\"completed\"}")
                    ));

            return delayed;
        });

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

        logger.info("All servers are ready for benchmarking");
    }

    /**
     * 벤치마크 스위트 실행
     */
    private BenchmarkResults executeBenchmarkSuites() throws Exception {
        BenchmarkResults results = new BenchmarkResults();

        logger.info("Starting benchmark suites...");

        // 1. 기본 응답성 테스트
        logger.info("1. Basic Responsiveness Test");
        ServerComparisonResult basicResult = runBasicTest();
        results.addResult("basic", basicResult);
        savePartialResults(results, "1-basic");

        // 2. 동시성 테스트 (점진적 부하 증가)
        logger.info("2. Concurrency Test");
        ServerComparisonResult concurrencyResult = runConcurrencyTest();
        results.addResult("concurrency", concurrencyResult);
        savePartialResults(results, "2-concurrency");

        // 3. CPU 집약적 작업 테스트
        logger.info("3. CPU Intensive Test");
        ServerComparisonResult cpuResult = runCpuIntensiveTest();
        results.addResult("cpu_intensive", cpuResult);
        savePartialResults(results, "3-cpu-intensive");

        // 4. I/O 집약적 작업 테스트
        logger.info("4. I/O Intensive Test");
        ServerComparisonResult ioResult = runIoIntensiveTest();
        results.addResult("io_intensive", ioResult);
        savePartialResults(results, "4-io-intensive");

        // 5. 메모리 압박 테스트
        logger.info("5. Memory Pressure Test");
        ServerComparisonResult memoryResult = runMemoryPressureTest();
        results.addResult("memory_pressure", memoryResult);
        savePartialResults(results, "5-memory-pressure");

        // 6. 지속성 테스트 (장시간 실행)
        logger.info("6. Endurance Test");
        ServerComparisonResult enduranceResult = runEnduranceTest();
        results.addResult("endurance", enduranceResult);
        savePartialResults(results, "6-endurance");

        return results;
    }

    /**
     * 기본 응답성 테스트
     */
    private ServerComparisonResult runBasicTest() throws Exception {
        logger.info("Running basic responsiveness test...");

        ConcurrencyTester tester = new ConcurrencyTester();

        TestResult threadedResult = tester.runTest(
                "localhost", THREADED_PORT, "/hello",
                10, 100, 30
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

            Thread.sleep(1000);
        }

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
                20, 200, 120
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
                100, 500, 180
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

        memoryProfiler.startMonitoring();

        TestResult threadedResult = tester.runTest(
                "localhost", THREADED_PORT, "/hello",
                2000, 5000, 300
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
                50, 600
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
        logger.info("Analyzing benchmark results...");

        ReportGenerator.generateConsoleReport(results);

        try {
            String htmlReport = ReportGenerator.generateHtmlReport(results);
            ReportGenerator.saveHtmlReport(htmlReport, "benchmark_results.html");
            logger.info("HTML report saved: benchmark_results.html");
        } catch (Exception e) {
            logger.error("Failed to generate HTML report", e);
        }

        try {
            String jsonResults = ReportGenerator.generateJsonReport(results);
            ReportGenerator.saveJsonReport(jsonResults, "benchmark_results.json");
            logger.info("JSON results saved: benchmark_results.json");
        } catch (Exception e) {
            logger.error("Failed to save JSON results", e);
        }
    }

    /**
     * 부분 결과를 중간 파일에 저장 (각 테스트 완료 후)
     */
    private void savePartialResults(BenchmarkResults results, String testPhase) {
        String partialFileName = "result_" + testPhase + ".txt";

        try (PrintWriter writer = new PrintWriter(new FileWriter(partialFileName))) {
            // 헤더 정보
            writer.println("=".repeat(80));
            writer.println("                    PARTIAL BENCHMARK RESULTS - " + testPhase.toUpperCase());
            writer.println("=".repeat(80));
            writer.println("Benchmark Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.println("Test Phase: " + testPhase);
            writer.println("Configuration: " + config.toString());
            writer.println();

            // 완료된 테스트 결과들 출력
            Map<String, ServerComparisonResult> allResults = results.getAllResults();

            for (Map.Entry<String, ServerComparisonResult> entry : allResults.entrySet()) {
                String testName = entry.getKey();
                ServerComparisonResult result = entry.getValue();

                writer.println("-".repeat(60));
                writer.println("TEST: " + testName.toUpperCase());
                writer.println("-".repeat(60));

                // 각 서버 결과
                writeServerResult(writer, "THREADED SERVER", result.getThreadedResult());
                writeServerResult(writer, "HYBRID SERVER", result.getHybridResult());
                writeServerResult(writer, "EVENTLOOP SERVER", result.getEventLoopResult());

                // 승자 결정
                TestResult winner = determineWinner(result);
                writer.println("WINNER: " + getServerName(result, winner));
                writer.println();
            }

            // 현재까지의 요약
            writer.println("=".repeat(80));
            writer.println("                        CURRENT SUMMARY");
            writer.println("=".repeat(80));

            Map<String, String> winners = results.analyzeWinners();
            for (Map.Entry<String, String> entry : winners.entrySet()) {
                writer.println(String.format("%-25s: %s", entry.getKey(), entry.getValue()));
            }

            // 진행 상황
            writer.println();
            writer.println("-".repeat(60));
            writer.println("PROGRESS");
            writer.println("-".repeat(60));
            writer.println("Tests Completed: " + allResults.size() + "/6");

            String[] testOrder = {"basic", "concurrency", "cpu_intensive", "io_intensive", "memory_pressure", "endurance"};
            for (int i = 0; i < testOrder.length; i++) {
                String status = allResults.containsKey(testOrder[i]) ? "COMPLETED" : "PENDING";
                writer.println(String.format("%d. %-15s: %s", i+1, testOrder[i].toUpperCase(), status));
            }

            logger.info("Partial results saved to: {}", partialFileName);

        } catch (IOException e) {
            logger.error("Failed to save partial results to file: {}", partialFileName, e);
        }
    }

    /**
     * 벤치마크 결과를 최종 result.txt 파일에 저장
     */
    private void saveResultsToFile(BenchmarkResults results) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(RESULT_FILE_PATH))) {
            // 헤더 정보
            writer.println("=".repeat(80));
            writer.println("                    SERVER ARCHITECTURE BENCHMARK RESULTS");
            writer.println("=".repeat(80));
            writer.println("Benchmark Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.println("Configuration: " + config.toString());
            writer.println();

            // 각 테스트 결과 출력
            Map<String, ServerComparisonResult> allResults = results.getAllResults();

            for (Map.Entry<String, ServerComparisonResult> entry : allResults.entrySet()) {
                String testName = entry.getKey();
                ServerComparisonResult result = entry.getValue();

                writer.println("-".repeat(60));
                writer.println("TEST: " + testName.toUpperCase());
                writer.println("-".repeat(60));

                // 각 서버 결과
                writeServerResult(writer, "THREADED SERVER", result.getThreadedResult());
                writeServerResult(writer, "HYBRID SERVER", result.getHybridResult());
                writeServerResult(writer, "EVENTLOOP SERVER", result.getEventLoopResult());

                // 승자 결정
                TestResult winner = determineWinner(result);
                writer.println("WINNER: " + getServerName(result, winner));
                writer.println();
            }

            // 전체 요약
            writer.println("=".repeat(80));
            writer.println("                            OVERALL SUMMARY");
            writer.println("=".repeat(80));

            Map<String, String> winners = results.analyzeWinners();
            for (Map.Entry<String, String> entry : winners.entrySet()) {
                writer.println(String.format("%-25s: %s", entry.getKey(), entry.getValue()));
            }

            // 추천 사항
            writer.println();
            writer.println("-".repeat(60));
            writer.println("RECOMMENDATIONS");
            writer.println("-".repeat(60));
            generateRecommendations(writer, results);

            logger.info("Benchmark results saved to: {}", RESULT_FILE_PATH);

        } catch (IOException e) {
            logger.error("Failed to save results to file: {}", RESULT_FILE_PATH, e);
        }
    }

    /**
     * 개별 서버 결과를 파일에 작성 (TestResult 클래스 기반)
     */
    private void writeServerResult(PrintWriter writer, String serverName, TestResult result) {
        writer.println(String.format("%s:", serverName));
        writer.println(String.format("   Throughput:       %,.2f TPS", result.getThroughput()));
        writer.println(String.format("   Avg Latency:      %,.2f ms", result.getAverageResponseTime()));
        writer.println(String.format("   Median Latency:   %,.2f ms", result.getMedianResponseTime()));
        writer.println(String.format("   P95 Latency:      %,.2f ms", result.getPercentile95ResponseTime()));
        writer.println(String.format("   P99 Latency:      %,.2f ms", result.getPercentile99ResponseTime()));
        writer.println(String.format("   Min Latency:      %,.2f ms", result.getMinResponseTime()));
        writer.println(String.format("   Max Latency:      %,.2f ms", result.getMaxResponseTime()));
        writer.println(String.format("   Error Rate:       %.2f%%", result.getErrorRate()));
        writer.println(String.format("   Success Rate:     %.2f%%", result.getSuccessRate()));
        writer.println(String.format("   Concurrent Conn:  %d", result.getConcurrencyLevel()));
        writer.println(String.format("   Total Requests:   %,d", result.getTotalRequests()));
        writer.println(String.format("   Success Requests: %,d", result.getSuccessfulRequests()));
        writer.println(String.format("   Duration:         %,.2f seconds", result.getDurationMs() / 1000.0));
        writer.println(String.format("   Overall Score:    %.1f/100", result.getOverallPerformanceScore()));
        writer.println(String.format("   Stability Score:  %.1f/100", result.getStabilityScore()));

        if (result.getMemoryIncrease() > 0) {
            writer.println(String.format("   Memory Increase:  %,.2f MB", result.getMemoryIncrease() / (1024.0 * 1024.0)));
            writer.println(String.format("   Memory/Request:   %,.2f KB", result.getMemoryEfficiencyPerRequest() / 1024.0));
        }

        writer.println();
    }

    /**
     * 테스트 승자 결정
     */
    private TestResult determineWinner(ServerComparisonResult result) {
        TestResult threaded = result.getThreadedResult();
        TestResult hybrid = result.getHybridResult();
        TestResult eventLoop = result.getEventLoopResult();

        List<TestResult> candidates = Arrays.asList(threaded, hybrid, eventLoop);

        return candidates.stream()
                .filter(r -> r.isSuccessful() && r.getErrorRate() < 5.0)
                .max(Comparator.comparing(TestResult::getOverallPerformanceScore))
                .orElse(candidates.stream()
                        .filter(TestResult::isSuccessful)
                        .max(Comparator.comparing(TestResult::getThroughput))
                        .orElse(threaded));
    }

    /**
     * 서버 이름 반환
     */
    private String getServerName(ServerComparisonResult result, TestResult target) {
        if (target == result.getThreadedResult()) return "Threaded Server";
        if (target == result.getHybridResult()) return "Hybrid Server";
        if (target == result.getEventLoopResult()) return "EventLoop Server";
        return "Unknown";
    }

    /**
     * 추천 사항 생성
     */
    private void generateRecommendations(PrintWriter writer, BenchmarkResults results) {
        Map<String, String> winners = results.analyzeWinners();

        writer.println("Based on the benchmark results:");
        writer.println();

        if (winners.containsKey("basic")) {
            writer.println("- For basic web applications: " + winners.get("basic"));
        }

        if (winners.containsKey("concurrency")) {
            writer.println("- For high concurrency scenarios: " + winners.get("concurrency"));
        }

        if (winners.containsKey("cpu_intensive")) {
            writer.println("- For CPU-intensive tasks: " + winners.get("cpu_intensive"));
        }

        if (winners.containsKey("io_intensive")) {
            writer.println("- For I/O-intensive applications: " + winners.get("io_intensive"));
        }

        if (winners.containsKey("memory_pressure")) {
            writer.println("- For memory-constrained environments: " + winners.get("memory_pressure"));
        }

        if (winners.containsKey("endurance")) {
            writer.println("- For long-running services: " + winners.get("endurance"));
        }

        writer.println();
        writer.println("General Guidelines:");
        writer.println("   - Consider your specific use case and traffic patterns");
        writer.println("   - Test with your actual workload characteristics");
        writer.println("   - Monitor resource usage in production environments");
        writer.println("   - Evaluate based on your performance requirements and constraints");
    }

    /**
     * 부분 결과 파일들 정리 (선택사항)
     */
    private void cleanupPartialFiles() {
        String[] partialFiles = {
                "result_1-basic.txt",
                "result_2-concurrency.txt",
                "result_3-cpu-intensive.txt",
                "result_4-io-intensive.txt",
                "result_5-memory-pressure.txt",
                "result_6-endurance.txt"
        };

        // 부분 파일들을 삭제하거나 backup 폴더로 이동할 수 있음
        // 현재는 로그만 출력
        logger.info("Partial result files created during benchmark:");
        for (String fileName : partialFiles) {
            java.io.File file = new java.io.File(fileName);
            if (file.exists()) {
                logger.info("  - {}", fileName);
            }
        }
        logger.info("You can delete these files or keep them for reference");
    }

    /**
     * 모든 서버 종료
     */
    private void stopServers() {
        logger.info("Stopping all servers...");

        if (threadedServer != null) {
            try {
                threadedServer.stop();
                logger.info("Threaded Server stopped");
            } catch (Exception e) {
                logger.error("Error stopping Threaded Server", e);
            }
        }

        if (hybridServer != null) {
            try {
                hybridServer.stop();
                logger.info("Hybrid Server stopped");
            } catch (Exception e) {
                logger.error("Error stopping Hybrid Server", e);
            }
        }

        if (eventLoopServer != null) {
            try {
                // EventLoop 서블릿 리소스 정리
                server.eventloop.CpuIntensiveServlet.shutdown();
                server.eventloop.IoSimulationServlet.shutdown();

                eventLoopServer.stop();
                logger.info("EventLoop Server stopped");
            } catch (Exception e) {
                logger.error("Error stopping EventLoop Server", e);
            }
        }
    }
    /**
     * 🔧 추가: EventLoop 서버 상태 확인을 위한 헬퍼 메서드
     */
    private boolean isEventLoopServerHealthy() {
        try {
            return loadTestClient.healthCheck("localhost", EVENTLOOP_PORT);
        } catch (Exception e) {
            logger.error("EventLoop server health check failed", e);
            return false;
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
        logger.info("Benchmark Complete!");
        logger.info("Results saved to: {}", RESULT_FILE_PATH);
        logger.info("Winner Summary:");

        Map<String, String> winners = results.analyzeWinners();
        for (Map.Entry<String, String> entry : winners.entrySet()) {
            logger.info("  {}: {}", entry.getKey(), entry.getValue());
        }

        System.exit(0);
    }
}