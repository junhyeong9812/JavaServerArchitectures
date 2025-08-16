package server.benchmark;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;
import java.net.*;
import java.io.*;

/**
 * 고성능 HTTP 로드 테스트 클라이언트 (수정된 버전)
 *
 * 특징:
 * 1. 비동기 HTTP 클라이언트 사용
 * 2. 연결 풀링 및 재사용
 * 3. 정확한 응답 시간 측정
 * 4. 에러 처리 및 재시도
 * 5. 실시간 통계 수집
 * 6. ⭐ 개선된 헬스체크 (여러 경로 시도, 관대한 상태 코드)
 */
public class LoadTestClient {

    private static final Logger logger = LoggerFactory.getLogger(LoadTestClient.class);

    private final HttpClient httpClient;
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final AtomicLong successCounter = new AtomicLong(0);
    private final AtomicLong errorCounter = new AtomicLong(0);

    public LoadTestClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * ⭐ 헬스체크 - 서버가 응답하는지 확인 (수정된 버전)
     * 여러 경로를 시도하고 관대한 상태 코드 체크
     */
    public boolean healthCheck(String host, int port) {
        logger.debug("Health check starting for {}:{}", host, port);

        // 1. /health 경로 시도
        if (tryHealthCheckPath(host, port, "/health")) {
            return true;
        }

        // 2. /hello 경로 시도 (벤치마크에서 등록한 경로)
        if (tryHealthCheckPath(host, port, "/hello")) {
            return true;
        }

        // 3. 루트 경로 시도
        if (tryHealthCheckPath(host, port, "/")) {
            return true;
        }

        // 4. 마지막으로 TCP 연결만 확인
        return checkTcpConnection(host, port);
    }

    /**
     * ⭐ 특정 경로로 헬스체크 시도
     */
    private boolean tryHealthCheckPath(String host, int port, String path) {
        try {
            String url = String.format("http://%s:%d%s", host, port, path);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "LoadTestClient/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            // ⭐ 200-499 범위는 모두 OK (서버가 응답하고 있다는 의미)
            // 404도 괜찮음 - 서버는 살아있고 요청을 처리했음
            boolean healthy = response.statusCode() >= 200 && response.statusCode() < 500;

            if (healthy) {
                logger.debug("Health check OK: {}:{}{} (status: {})",
                        host, port, path, response.statusCode());
                return true;
            } else {
                logger.debug("Health check failed: {}:{}{} (status: {})",
                        host, port, path, response.statusCode());
            }

        } catch (Exception e) {
            logger.debug("Health check error for {}:{}{}: {}",
                    host, port, path, e.getMessage());
        }

        return false;
    }

    /**
     * ⭐ TCP 연결만 확인 (최후의 수단)
     * HTTP가 실패해도 최소한 포트가 열려있는지 확인
     */
    private boolean checkTcpConnection(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000); // 3초 타임아웃
            logger.debug("TCP connection OK: {}:{}", host, port);
            return true;
        } catch (Exception e) {
            logger.warn("TCP connection failed: {}:{} - {}", host, port, e.getMessage());
            return false;
        }
    }

    /**
     * 단일 HTTP 요청 실행 및 응답 시간 측정
     */
    public RequestResult executeRequest(String host, int port, String path) {
        long requestId = requestCounter.incrementAndGet();
        long startTime = System.nanoTime();

        try {
            String url = String.format("http://%s:%d%s", host, port, path);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            long endTime = System.nanoTime();
            long responseTimeNanos = endTime - startTime;

            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;

            if (success) {
                successCounter.incrementAndGet();
            } else {
                errorCounter.incrementAndGet();
            }

            return new RequestResult(
                    requestId,
                    success,
                    response.statusCode(),
                    responseTimeNanos,
                    response.body().length(),
                    null
            );

        } catch (Exception e) {
            long endTime = System.nanoTime();
            long responseTimeNanos = endTime - startTime;

            errorCounter.incrementAndGet();
            logger.debug("Request {} failed: {}", requestId, e.getMessage());

            return new RequestResult(
                    requestId,
                    false,
                    -1,
                    responseTimeNanos,
                    0,
                    e.getMessage()
            );
        }
    }

    /**
     * 비동기 요청 실행
     */
    public CompletableFuture<RequestResult> executeRequestAsync(String host, int port, String path) {
        long requestId = requestCounter.incrementAndGet();
        long startTime = System.nanoTime();

        try {
            String url = String.format("http://%s:%d%s", host, port, path);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        long endTime = System.nanoTime();
                        long responseTimeNanos = endTime - startTime;

                        boolean success = response.statusCode() >= 200 && response.statusCode() < 300;

                        if (success) {
                            successCounter.incrementAndGet();
                        } else {
                            errorCounter.incrementAndGet();
                        }

                        return new RequestResult(
                                requestId,
                                success,
                                response.statusCode(),
                                responseTimeNanos,
                                response.body().length(),
                                null
                        );
                    })
                    .exceptionally(e -> {
                        long endTime = System.nanoTime();
                        long responseTimeNanos = endTime - startTime;

                        errorCounter.incrementAndGet();
                        logger.debug("Async request {} failed: {}", requestId, e.getMessage());

                        return new RequestResult(
                                requestId,
                                false,
                                -1,
                                responseTimeNanos,
                                0,
                                e.getMessage()
                        );
                    });

        } catch (Exception e) {
            return CompletableFuture.completedFuture(new RequestResult(
                    requestId,
                    false,
                    -1,
                    System.nanoTime() - startTime,
                    0,
                    e.getMessage()
            ));
        }
    }

    /**
     * 부하 테스트 실행 - 지정된 동시성과 요청 수로 테스트
     */
    public List<RequestResult> executeLoadTest(String host, int port, String path,
                                               int concurrency, int totalRequests) {
        logger.info("Starting load test: {}:{}{} (concurrency={}, requests={})",
                host, port, path, concurrency, totalRequests);

        List<RequestResult> results = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        long testStartTime = System.currentTimeMillis();

        try {
            for (int i = 0; i < totalRequests; i++) {
                executor.submit(() -> {
                    try {
                        RequestResult result = executeRequest(host, port, path);
                        synchronized (results) {
                            results.add(result);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // 모든 요청 완료 대기
            boolean completed = latch.await(5, TimeUnit.MINUTES);

            if (!completed) {
                logger.warn("Load test timed out - some requests may not have completed");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Load test interrupted", e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }

        long testEndTime = System.currentTimeMillis();
        logger.info("Load test completed in {}ms - results: {}",
                testEndTime - testStartTime, results.size());

        return results;
    }

    /**
     * 지속적 부하 테스트 - 지정된 시간 동안 일정한 부하 유지
     */
    public List<RequestResult> executeContinuousLoadTest(String host, int port, String path,
                                                         int concurrency, int durationSeconds) {
        logger.info("Starting continuous load test: {}:{}{} (concurrency={}, duration={}s)",
                host, port, path, concurrency, durationSeconds);

        List<RequestResult> results = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        AtomicBoolean running = new AtomicBoolean(true);

        // 각 스레드가 지속적으로 요청을 보냄
        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                while (running.get()) {
                    try {
                        RequestResult result = executeRequest(host, port, path);
                        results.add(result);

                        // 간단한 속도 조절 (optional)
                        Thread.sleep(100);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        logger.debug("Error in continuous load test", e);
                    }
                }
            });
        }

        // 지정된 시간 후 테스트 중지
        try {
            Thread.sleep(durationSeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        running.set(false);
        executor.shutdown();

        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }

        logger.info("Continuous load test completed - results: {}", results.size());
        return results;
    }

    /**
     * 웜업 실행 - 서버 준비 상태 확인
     */
    public void warmup(String host, int port, String path, int requests) {
        logger.info("Warming up server {}:{}{} with {} requests", host, port, path, requests);

        List<CompletableFuture<RequestResult>> futures = new ArrayList<>();

        for (int i = 0; i < requests; i++) {
            futures.add(executeRequestAsync(host, port, path));
        }

        // 모든 웜업 요청 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(2, TimeUnit.MINUTES)
                .join();

        logger.info("Warmup completed");
    }

    /**
     * ⭐ 재시도가 포함된 헬스체크 (벤치마크용)
     */
    public boolean healthCheckWithRetry(String host, int port, int maxRetries, long retryDelayMs) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            if (healthCheck(host, port)) {
                logger.debug("Health check passed on attempt {} for {}:{}", attempt, host, port);
                return true;
            }

            if (attempt < maxRetries) {
                logger.debug("Health check failed, retrying in {}ms (attempt {}/{})",
                        retryDelayMs, attempt, maxRetries);
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        logger.warn("Health check failed after {} attempts for {}:{}", maxRetries, host, port);
        return false;
    }

    /**
     * 현재 통계 반환
     */
    public ClientStats getStats() {
        return new ClientStats(
                requestCounter.get(),
                successCounter.get(),
                errorCounter.get()
        );
    }

    /**
     * 통계 리셋
     */
    public void resetStats() {
        requestCounter.set(0);
        successCounter.set(0);
        errorCounter.set(0);
    }

    /**
     * 리소스 정리
     */
    public void close() {
        // HttpClient는 자동으로 리소스 정리됨
        logger.debug("LoadTestClient closed");
    }

    /**
     * 요청 결과 클래스
     */
    public static class RequestResult {
        private final long requestId;
        private final boolean success;
        private final int statusCode;
        private final long responseTimeNanos;
        private final int responseSize;
        private final String errorMessage;
        private final long timestamp;

        public RequestResult(long requestId, boolean success, int statusCode,
                             long responseTimeNanos, int responseSize, String errorMessage) {
            this.requestId = requestId;
            this.success = success;
            this.statusCode = statusCode;
            this.responseTimeNanos = responseTimeNanos;
            this.responseSize = responseSize;
            this.errorMessage = errorMessage;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public long getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public int getStatusCode() { return statusCode; }
        public long getResponseTimeNanos() { return responseTimeNanos; }
        public double getResponseTimeMillis() { return responseTimeNanos / 1_000_000.0; }
        public int getResponseSize() { return responseSize; }
        public String getErrorMessage() { return errorMessage; }
        public long getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("RequestResult{id=%d, success=%s, status=%d, time=%.2fms}",
                    requestId, success, statusCode, getResponseTimeMillis());
        }
    }

    /**
     * 클라이언트 통계 클래스
     */
    public static class ClientStats {
        private final long totalRequests;
        private final long successfulRequests;
        private final long failedRequests;

        public ClientStats(long totalRequests, long successfulRequests, long failedRequests) {
            this.totalRequests = totalRequests;
            this.successfulRequests = successfulRequests;
            this.failedRequests = failedRequests;
        }

        public long getTotalRequests() { return totalRequests; }
        public long getSuccessfulRequests() { return successfulRequests; }
        public long getFailedRequests() { return failedRequests; }
        public double getSuccessRate() {
            return totalRequests > 0 ? (double) successfulRequests / totalRequests * 100 : 0;
        }

        @Override
        public String toString() {
            return String.format("ClientStats{total=%d, success=%d, failed=%d, rate=%.1f%%}",
                    totalRequests, successfulRequests, failedRequests, getSuccessRate());
        }
    }
}