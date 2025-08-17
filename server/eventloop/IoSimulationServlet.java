package server.eventloop;

import server.core.http.HttpRequest;
import server.core.http.HttpResponse;
import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Random;

/**
 * EventLoop 서버용 I/O 시뮬레이션 서블릿
 * ThreadedServer의 IoSimulationServlet과 동일한 I/O 작업을 비동기로 처리
 *
 * 🔧 핵심: ThreadedServer와 정확히 동일한 Thread.sleep(100) 수행
 */
public class IoSimulationServlet {

    private static final Logger logger = LoggerFactory.getLogger(IoSimulationServlet.class);

    // 🔧 I/O 작업용 별도 스레드 풀 (EventLoop 메인 스레드 블로킹 방지)
    private static final ScheduledExecutorService ioExecutor = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors() * 2, // I/O 작업은 CPU보다 많은 스레드 사용
            r -> {
                Thread t = new Thread(r, "EventLoop-IO-Worker");
                t.setDaemon(true);
                return t;
            }
    );

    private static final Random random = new Random();

    /**
     * 🔧 핵심: ThreadedServer와 정확히 동일한 I/O 시뮬레이션
     */
    public CompletableFuture<HttpResponse> handleRequest(HttpRequest request) {
        long startTime = System.currentTimeMillis();

        logger.debug("Starting I/O simulation on EventLoop thread: {}",
                Thread.currentThread().getName());

        // 🔧 ThreadedServer의 IoSimulationServlet과 정확히 동일한 작업 수행
        return CompletableFuture.supplyAsync(() -> {
            try {
                // ✅ 핵심: ThreadedServer와 동일한 Thread.sleep(100) 수행
                Thread.sleep(100);

                // 추가적인 I/O 시뮬레이션 (파일 읽기/DB 쿼리 흉내)
                StringBuilder simulatedData = new StringBuilder();
                for (int i = 0; i < 1000; i++) {
                    simulatedData.append("data-line-").append(i).append("\n");
                }

                long duration = System.currentTimeMillis() - startTime;

                logger.debug("I/O simulation completed in {}ms on worker thread: {}",
                        duration, Thread.currentThread().getName());

                // ThreadedServer와 유사한 형태의 JSON 응답
                return HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io\":\"completed\",\"duration\":%d,\"dataSize\":%d,\"thread\":\"%s\",\"timestamp\":%d}",
                        duration,
                        simulatedData.length(),
                        Thread.currentThread().getName(),
                        System.currentTimeMillis()
                ));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("I/O simulation interrupted", e);

                return HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io\":\"interrupted\",\"error\":\"%s\",\"thread\":\"%s\"}",
                        e.getMessage(),
                        Thread.currentThread().getName()
                ));
            }
        }, ioExecutor);
    }

    /**
     * 가변 지연시간 I/O 시뮬레이션
     */
    public CompletableFuture<HttpResponse> handleVariableDelayRequest(HttpRequest request) {
        // delay 파라미터로 지연시간 조정 (기본값 100ms)
        String delayParam = request.getQueryParameter("delay");
        int delayMs = 100;

        try {
            if (delayParam != null) {
                delayMs = Integer.parseInt(delayParam);
                delayMs = Math.min(Math.max(delayMs, 10), 5000); // 10ms~5초 제한
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid delay parameter: {}, using default 100ms", delayParam);
        }

        final int finalDelay = delayMs;

        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(finalDelay);

                return HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io\":\"completed\",\"delay\":%d,\"thread\":\"%s\"}",
                        finalDelay,
                        Thread.currentThread().getName()
                ));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io\":\"interrupted\",\"thread\":\"%s\"}",
                        Thread.currentThread().getName()
                ));
            }
        }, ioExecutor);
    }

    /**
     * 복합 I/O 시뮬레이션 (DB + 파일 + 네트워크)
     */
    public CompletableFuture<HttpResponse> handleComplexIoRequest(HttpRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // 1. 데이터베이스 쿼리 시뮬레이션
                Thread.sleep(50);
                String dbResult = "SELECT * FROM users WHERE active = 1; -- " + random.nextInt(1000) + " rows";

                // 2. 파일 읽기 시뮬레이션
                Thread.sleep(30);
                String fileData = "File content with " + random.nextInt(10000) + " bytes";

                // 3. 외부 API 호출 시뮬레이션
                Thread.sleep(20);
                String apiResponse = "{'status': 'success', 'data': " + random.nextInt(100) + "}";

                long totalDuration = System.currentTimeMillis() - startTime;

                return HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io\":\"complex_completed\",\"operations\":{\"database\":\"%s\",\"file\":\"%s\",\"api\":\"%s\"},\"total_duration\":%d,\"thread\":\"%s\"}",
                        dbResult,
                        fileData,
                        apiResponse,
                        totalDuration,
                        Thread.currentThread().getName()
                ));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io\":\"complex_interrupted\",\"thread\":\"%s\"}",
                        Thread.currentThread().getName()
                ));
            }
        }, ioExecutor);
    }

    /**
     * 비동기 체인 I/O 시뮬레이션 (CompletableFuture 체인)
     */
    public CompletableFuture<HttpResponse> handleAsyncChainRequest(HttpRequest request) {
        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        Thread.sleep(50); // 첫 번째 I/O
                        return "step1_data";
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }, ioExecutor)
                .thenComposeAsync(step1Data ->
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                Thread.sleep(30); // 두 번째 I/O
                                return step1Data + "_step2_data";
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        }, ioExecutor)
                )
                .thenComposeAsync(step2Data ->
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                Thread.sleep(20); // 세 번째 I/O
                                return step2Data + "_step3_data";
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        }, ioExecutor)
                )
                .thenApply(finalData ->
                        HttpResponse.json(String.format(
                                "{\"server\":\"eventloop\",\"io\":\"async_chain_completed\",\"result\":\"%s\",\"thread\":\"%s\"}",
                                finalData,
                                Thread.currentThread().getName()
                        ))
                )
                .exceptionally(throwable ->
                        HttpResponse.json(String.format(
                                "{\"server\":\"eventloop\",\"io\":\"async_chain_failed\",\"error\":\"%s\",\"thread\":\"%s\"}",
                                throwable.getMessage(),
                                Thread.currentThread().getName()
                        ))
                );
    }

    /**
     * I/O Executor 상태 정보
     */
    public CompletableFuture<HttpResponse> getExecutorStats() {
        return CompletableFuture.completedFuture(
                HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io_executor\":{\"shutdown\":%s,\"pool_size\":%d},\"thread\":\"%s\"}",
                        ioExecutor.isShutdown(),
                        Runtime.getRuntime().availableProcessors() * 2,
                        Thread.currentThread().getName()
                ))
        );
    }

    /**
     * 스케줄된 지연 실행 (EventLoop 스타일)
     */
    public CompletableFuture<HttpResponse> handleScheduledRequest(HttpRequest request) {
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();

        // 100ms 후에 작업 수행 (Thread.sleep 대신 스케줄러 사용)
        ioExecutor.schedule(() -> {
            try {
                // 실제 I/O 작업은 여전히 수행
                StringBuilder data = new StringBuilder();
                for (int i = 0; i < 500; i++) {
                    data.append("scheduled-data-").append(i).append("\n");
                }

                future.complete(HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"io\":\"scheduled_completed\",\"dataSize\":%d,\"thread\":\"%s\"}",
                        data.length(),
                        Thread.currentThread().getName()
                )));

            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, 100, TimeUnit.MILLISECONDS);

        return future;
    }

    /**
     * 리소스 정리
     */
    public static void shutdown() {
        logger.info("Shutting down I/O executor for EventLoop server");
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}