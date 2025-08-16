package server.benchmark;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 벤치마크 결과 리포트 생성기
 *
 * 생성 가능한 리포트:
 * 1. HTML 리포트 (차트와 상세 분석 포함)
 * 2. JSON 리포트 (프로그래밍 방식 접근용)
 * 3. 콘솔 리포트 (즉시 확인용)
 * 4. CSV 데이터 (외부 분석 도구용)
 */
public class ReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * 콘솔 리포트 생성 및 출력
     */
    public static void generateConsoleReport(BenchmarkResults results) {
        logger.info("\n" + "=".repeat(80));
        logger.info("🏆 BENCHMARK RESULTS SUMMARY");
        logger.info("=".repeat(80));

        // 기본 정보
        logger.info("📊 Benchmark Info:");
        logger.info("   Start Time: {}", TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(results.getBenchmarkStartTime())));
        logger.info("   Duration: {:.1f} minutes", results.getTotalDurationMs() / 60000.0);
        logger.info("   Total Tests: {}", results.getTotalTestCount());

        if (results.getConfig() != null) {
            logger.info("   Config: {}", results.getConfig());
        }

        logger.info("");

        // 전체 순위
        List<BenchmarkResults.ServerRanking> rankings = results.getOverallRankings();
        logger.info("🥇 Overall Rankings:");
        for (int i = 0; i < rankings.size(); i++) {
            BenchmarkResults.ServerRanking ranking = rankings.get(i);
            String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
            logger.info("   {} #{} {} (Score: {:.1f})",
                    medal, i + 1, ranking.getServerName(), ranking.getOverallScore());
        }
        logger.info("");

        // 테스트별 우승자
        Map<String, String> winners = results.analyzeWinners();
        logger.info("🏅 Test Winners:");
        for (Map.Entry<String, String> entry : winners.entrySet()) {
            if (!"OVERALL".equals(entry.getKey())) {
                logger.info("   {}: {}", entry.getKey(), entry.getValue());
            }
        }
        logger.info("   OVERALL CHAMPION: {}", winners.get("OVERALL"));
        logger.info("");

        // 서버별 상세 성능
        String[] servers = {"Threaded", "Hybrid", "EventLoop"};
        for (String server : servers) {
            BenchmarkResults.ServerPerformanceSummary summary = results.getServerSummary(server);
            logger.info("📈 {} Server Performance:", server);
            logger.info("   Tests Completed: {}", summary.getTestCount());
            logger.info("   Avg Throughput: {:.1f TPS (Max: {:.1f})",
                    summary.getAverageThroughput(), summary.getMaxThroughput());
            logger.info("   Avg Latency: {:.1f ms (Min: {:.1f})",
                    summary.getAverageLatency(), summary.getMinLatency());
            logger.info("   Success Rate: {:.1f%}", summary.getAverageSuccessRate());
            logger.info("   Max Concurrency: {}", summary.getMaxConcurrency());
            logger.info("");
        }

        // 개별 테스트 결과
        logger.info("📋 Individual Test Results:");
        for (Map.Entry<String, ServerComparisonResult> entry : results.getAllResults().entrySet()) {
            String testName = entry.getKey();
            ServerComparisonResult result = entry.getValue();

            logger.info("   🔸 {}:", testName);
            logger.info("      Threaded:  {}", formatTestResult(result.getThreadedResult()));
            logger.info("      Hybrid:    {}", formatTestResult(result.getHybridResult()));
            logger.info("      EventLoop: {}", formatTestResult(result.getEventLoopResult()));
            logger.info("      Winner: {}", result.getBestThroughputServer());
            logger.info("");
        }

        logger.info("=".repeat(80));
    }

    /**
     * HTML 리포트 생성
     */
    public static String generateHtmlReport(BenchmarkResults results) {
        StringBuilder html = new StringBuilder();

        // HTML 헤더
        html.append("<!DOCTYPE html>\n")
                .append("<html lang=\"en\">\n")
                .append("<head>\n")
                .append("    <meta charset=\"UTF-8\">\n")
                .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("    <title>Server Benchmark Results</title>\n")
                .append("    <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n")
                .append("    <style>\n")
                .append(generateCss())
                .append("    </style>\n")
                .append("</head>\n")
                .append("<body>\n");

        // 헤더 섹션
        html.append("    <div class=\"header\">\n")
                .append("        <h1>🏆 Server Architecture Benchmark Results</h1>\n")
                .append("        <div class=\"summary\">\n")
                .append("            <p><strong>Generated:</strong> ")
                .append(TIMESTAMP_FORMAT.format(Instant.now()))
                .append("</p>\n")
                .append("            <p><strong>Duration:</strong> ")
                .append(String.format("%.1f minutes", results.getTotalDurationMs() / 60000.0))
                .append("</p>\n")
                .append("            <p><strong>Total Tests:</strong> ")
                .append(results.getTotalTestCount())
                .append("</p>\n")
                .append("        </div>\n")
                .append("    </div>\n");

        // 전체 순위 섹션
        html.append("    <div class=\"section\">\n")
                .append("        <h2>🥇 Overall Rankings</h2>\n")
                .append("        <div class=\"rankings\">\n");

        List<BenchmarkResults.ServerRanking> rankings = results.getOverallRankings();
        for (int i = 0; i < rankings.size(); i++) {
            BenchmarkResults.ServerRanking ranking = rankings.get(i);
            String rankClass = i == 0 ? "rank-1" : i == 1 ? "rank-2" : "rank-3";
            String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";

            html.append("            <div class=\"rank-card ").append(rankClass).append("\">\n")
                    .append("                <div class=\"medal\">").append(medal).append("</div>\n")
                    .append("                <div class=\"server-name\">").append(ranking.getServerName()).append("</div>\n")
                    .append("                <div class=\"score\">Score: ").append(String.format("%.1f", ranking.getOverallScore())).append("</div>\n")
                    .append("            </div>\n");
        }

        html.append("        </div>\n")
                .append("    </div>\n");

        // 차트 섹션
        html.append("    <div class=\"section\">\n")
                .append("        <h2>📊 Performance Charts</h2>\n")
                .append("        <div class=\"charts\">\n")
                .append("            <div class=\"chart-container\">\n")
                .append("                <canvas id=\"throughputChart\"></canvas>\n")
                .append("            </div>\n")
                .append("            <div class=\"chart-container\">\n")
                .append("                <canvas id=\"latencyChart\"></canvas>\n")
                .append("            </div>\n")
                .append("        </div>\n")
                .append("    </div>\n");

        // 상세 결과 테이블
        html.append("    <div class=\"section\">\n")
                .append("        <h2>📋 Detailed Results</h2>\n")
                .append("        <div class=\"table-container\">\n")
                .append("            <table class=\"results-table\">\n")
                .append("                <thead>\n")
                .append("                    <tr>\n")
                .append("                        <th>Test</th>\n")
                .append("                        <th>Server</th>\n")
                .append("                        <th>Throughput (TPS)</th>\n")
                .append("                        <th>Avg Latency (ms)</th>\n")
                .append("                        <th>P95 Latency (ms)</th>\n")
                .append("                        <th>Success Rate (%)</th>\n")
                .append("                        <th>Concurrency</th>\n")
                .append("                    </tr>\n")
                .append("                </thead>\n")
                .append("                <tbody>\n");

        // 테이블 데이터 추가
        for (Map.Entry<String, ServerComparisonResult> entry : results.getAllResults().entrySet()) {
            String testName = entry.getKey();
            ServerComparisonResult result = entry.getValue();

            html.append(generateTableRow(testName, "Threaded", result.getThreadedResult()));
            html.append(generateTableRow(testName, "Hybrid", result.getHybridResult()));
            html.append(generateTableRow(testName, "EventLoop", result.getEventLoopResult()));
        }

        html.append("                </tbody>\n")
                .append("            </table>\n")
                .append("        </div>\n")
                .append("    </div>\n");

        // JavaScript 차트 생성
        html.append("    <script>\n")
                .append(generateChartScript(results))
                .append("    </script>\n");

        html.append("</body>\n")
                .append("</html>");

        return html.toString();
    }

    /**
     * JSON 리포트 생성
     */
    public static String generateJsonReport(BenchmarkResults results) {
        StringBuilder json = new StringBuilder();

        json.append("{\n")
                .append("  \"benchmarkInfo\": {\n")
                .append("    \"startTime\": ").append(results.getBenchmarkStartTime()).append(",\n")
                .append("    \"durationMs\": ").append(results.getTotalDurationMs()).append(",\n")
                .append("    \"totalTests\": ").append(results.getTotalTestCount()).append(",\n")
                .append("    \"generatedAt\": \"").append(Instant.now().toString()).append("\"\n")
                .append("  },\n");

        // 전체 순위
        json.append("  \"rankings\": [\n");
        List<BenchmarkResults.ServerRanking> rankings = results.getOverallRankings();
        for (int i = 0; i < rankings.size(); i++) {
            BenchmarkResults.ServerRanking ranking = rankings.get(i);
            json.append("    {\n")
                    .append("      \"rank\": ").append(i + 1).append(",\n")
                    .append("      \"server\": \"").append(ranking.getServerName()).append("\",\n")
                    .append("      \"score\": ").append(ranking.getOverallScore()).append("\n")
                    .append("    }");
            if (i < rankings.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ],\n");

        // 테스트 결과
        json.append("  \"testResults\": {\n");
        Set<String> testNames = results.getTestNames();
        int testIndex = 0;
        for (String testName : testNames) {
            ServerComparisonResult result = results.getResult(testName);
            json.append("    \"").append(testName).append("\": {\n")
                    .append("      \"threaded\": ").append(formatTestResultJson(result.getThreadedResult())).append(",\n")
                    .append("      \"hybrid\": ").append(formatTestResultJson(result.getHybridResult())).append(",\n")
                    .append("      \"eventloop\": ").append(formatTestResultJson(result.getEventLoopResult())).append(",\n")
                    .append("      \"winner\": \"").append(result.getBestThroughputServer()).append("\"\n")
                    .append("    }");
            if (testIndex < testNames.size() - 1) json.append(",");
            json.append("\n");
            testIndex++;
        }
        json.append("  }\n");

        json.append("}");

        return json.toString();
    }

    /**
     * CSV 리포트 생성
     */
    public static String generateCsvReport(BenchmarkResults results) {
        StringBuilder csv = new StringBuilder();

        // 헤더
        csv.append("Test,Server,Throughput_TPS,Avg_Latency_ms,P95_Latency_ms,P99_Latency_ms,Success_Rate_percent,Concurrency\n");

        // 데이터
        for (Map.Entry<String, ServerComparisonResult> entry : results.getAllResults().entrySet()) {
            String testName = entry.getKey();
            ServerComparisonResult result = entry.getValue();

            csv.append(formatCsvRow(testName, "Threaded", result.getThreadedResult()));
            csv.append(formatCsvRow(testName, "Hybrid", result.getHybridResult()));
            csv.append(formatCsvRow(testName, "EventLoop", result.getEventLoopResult()));
        }

        return csv.toString();
    }

    /**
     * HTML 리포트 파일로 저장
     */
    public static void saveHtmlReport(String htmlContent, String filename) throws IOException {
        Path path = Paths.get(filename);
        Files.write(path, htmlContent.getBytes());
        logger.info("HTML report saved to: {}", path.toAbsolutePath());
    }

    /**
     * JSON 리포트 파일로 저장
     */
    public static void saveJsonReport(String jsonContent, String filename) throws IOException {
        Path path = Paths.get(filename);
        Files.write(path, jsonContent.getBytes());
        logger.info("JSON report saved to: {}", path.toAbsolutePath());
    }

    /**
     * CSV 리포트 파일로 저장
     */
    public static void saveCsvReport(String csvContent, String filename) throws IOException {
        Path path = Paths.get(filename);
        Files.write(path, csvContent.getBytes());
        logger.info("CSV report saved to: {}", path.toAbsolutePath());
    }

    // === 헬퍼 메서드들 ===

    private static String formatTestResult(TestResult result) {
        if (!result.isSuccessful()) {
            return "FAILED: " + result.getErrorMessage();
        }
        return String.format("%.1f TPS, %.1fms avg, %.1f%% success",
                result.getThroughput(), result.getAverageResponseTime(), result.getSuccessRate());
    }

    private static String formatTestResultJson(TestResult result) {
        if (!result.isSuccessful()) {
            return String.format("{\"successful\": false, \"error\": \"%s\"}", result.getErrorMessage());
        }
        return String.format(
                "{\"successful\": true, \"throughput\": %.2f, \"avgLatency\": %.2f, \"p95Latency\": %.2f, \"successRate\": %.2f, \"concurrency\": %d}",
                result.getThroughput(), result.getAverageResponseTime(), result.getPercentile95ResponseTime(),
                result.getSuccessRate(), result.getConcurrencyLevel()
        );
    }

    private static String generateTableRow(String testName, String serverName, TestResult result) {
        if (!result.isSuccessful()) {
            return String.format(
                    "                    <tr class=\"failed\">\n" +
                            "                        <td>%s</td>\n" +
                            "                        <td>%s</td>\n" +
                            "                        <td colspan=\"5\">FAILED: %s</td>\n" +
                            "                    </tr>\n",
                    testName, serverName, result.getErrorMessage()
            );
        }

        return String.format(
                "                    <tr>\n" +
                        "                        <td>%s</td>\n" +
                        "                        <td>%s</td>\n" +
                        "                        <td>%.1f</td>\n" +
                        "                        <td>%.1f</td>\n" +
                        "                        <td>%.1f</td>\n" +
                        "                        <td>%.1f</td>\n" +
                        "                        <td>%d</td>\n" +
                        "                    </tr>\n",
                testName, serverName, result.getThroughput(), result.getAverageResponseTime(),
                result.getPercentile95ResponseTime(), result.getSuccessRate(), result.getConcurrencyLevel()
        );
    }

    private static String formatCsvRow(String testName, String serverName, TestResult result) {
        if (!result.isSuccessful()) {
            return String.format("%s,%s,0,0,0,0,0,0\n", testName, serverName);
        }
        return String.format("%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%d\n",
                testName, serverName, result.getThroughput(), result.getAverageResponseTime(),
                result.getPercentile95ResponseTime(), result.getPercentile99ResponseTime(),
                result.getSuccessRate(), result.getConcurrencyLevel());
    }

    private static String generateCss() {
        return """
                body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5; }
                .header { background: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                .header h1 { margin: 0; color: #333; }
                .summary { display: flex; gap: 30px; margin-top: 10px; }
                .summary p { margin: 5px 0; color: #666; }
                .section { background: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                .section h2 { margin-top: 0; color: #333; }
                .rankings { display: flex; gap: 20px; justify-content: center; }
                .rank-card { text-align: center; padding: 20px; border-radius: 8px; min-width: 150px; }
                .rank-1 { background: linear-gradient(135deg, #FFD700, #FFA500); }
                .rank-2 { background: linear-gradient(135deg, #C0C0C0, #808080); }
                .rank-3 { background: linear-gradient(135deg, #CD7F32, #8B4513); }
                .medal { font-size: 2em; margin-bottom: 10px; }
                .server-name { font-weight: bold; font-size: 1.2em; margin-bottom: 5px; }
                .score { font-size: 0.9em; opacity: 0.8; }
                .charts { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
                .chart-container { position: relative; height: 300px; }
                .table-container { overflow-x: auto; }
                .results-table { width: 100%; border-collapse: collapse; }
                .results-table th, .results-table td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
                .results-table th { background-color: #f8f9fa; font-weight: bold; }
                .results-table tr:hover { background-color: #f5f5f5; }
                .failed { background-color: #ffe6e6; }
                """;
    }

    private static String generateChartScript(BenchmarkResults results) {
        StringBuilder script = new StringBuilder();

        // 처리량 차트 데이터 준비
        script.append("const throughputData = {\n")
                .append("    labels: [");

        Set<String> testNames = results.getTestNames();
        for (String testName : testNames) {
            script.append("'").append(testName).append("',");
        }
        script.append("],\n")
                .append("    datasets: [\n");

        // 각 서버별 데이터셋
        String[] servers = {"Threaded", "Hybrid", "EventLoop"};
        String[] colors = {"#FF6384", "#36A2EB", "#4BC0C0"};

        for (int i = 0; i < servers.length; i++) {
            script.append("        {\n")
                    .append("            label: '").append(servers[i]).append("',\n")
                    .append("            data: [");

            for (String testName : testNames) {
                ServerComparisonResult result = results.getResult(testName);
                TestResult serverResult = getServerResult(result, servers[i]);
                double throughput = serverResult != null && serverResult.isSuccessful() ?
                        serverResult.getThroughput() : 0;
                script.append(throughput).append(",");
            }

            script.append("],\n")
                    .append("            backgroundColor: '").append(colors[i]).append("',\n")
                    .append("            borderColor: '").append(colors[i]).append("',\n")
                    .append("            borderWidth: 1\n")
                    .append("        }");
            if (i < servers.length - 1) script.append(",");
            script.append("\n");
        }

        script.append("    ]\n")
                .append("};\n\n");

        // 차트 생성 스크립트
        script.append("new Chart(document.getElementById('throughputChart'), {\n")
                .append("    type: 'bar',\n")
                .append("    data: throughputData,\n")
                .append("    options: {\n")
                .append("        responsive: true,\n")
                .append("        maintainAspectRatio: false,\n")
                .append("        plugins: { title: { display: true, text: 'Throughput Comparison (TPS)' } },\n")
                .append("        scales: { y: { beginAtZero: true, title: { display: true, text: 'TPS' } } }\n")
                .append("    }\n")
                .append("});\n\n");

        // 지연시간 차트는 유사하게 생성...
        script.append("new Chart(document.getElementById('latencyChart'), {\n")
                .append("    type: 'line',\n")
                .append("    data: { labels: [], datasets: [] },\n")
                .append("    options: {\n")
                .append("        responsive: true,\n")
                .append("        maintainAspectRatio: false,\n")
                .append("        plugins: { title: { display: true, text: 'Latency Comparison (ms)' } }\n")
                .append("    }\n")
                .append("});\n");

        return script.toString();
    }

    private static TestResult getServerResult(ServerComparisonResult result, String serverName) {
        switch (serverName) {
            case "Threaded": return result.getThreadedResult();
            case "Hybrid": return result.getHybridResult();
            case "EventLoop": return result.getEventLoopResult();
            default: return null;
        }
    }
}