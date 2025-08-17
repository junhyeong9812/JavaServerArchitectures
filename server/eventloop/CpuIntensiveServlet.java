package server.eventloop;

import server.core.http.HttpRequest;
import server.core.http.HttpResponse;
import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * EventLoop 서버용 CPU 집약적 작업 서블릿
 * ThreadedServer의 CpuIntensiveServlet과 동일한 작업을 비동기로 처리
 */
public class CpuIntensiveServlet {

    private static final Logger logger = LoggerFactory.getLogger(CpuIntensiveServlet.class);

    // 🔧 CPU 작업용 별도 스레드 풀 (EventLoop 메인 스레드 블로킹 방지)
    private static final ScheduledExecutorService cpuExecutor = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "EventLoop-CPU-Worker");
                t.setDaemon(true);
                return t;
            }
    );

    /**
     * CPU 집약적 작업 처리 - ThreadedServer와 동일한 계산 수행
     */
    public CompletableFuture<HttpResponse> handleRequest(HttpRequest request) {
        long startTime = System.currentTimeMillis();

        logger.debug("Starting CPU intensive task on EventLoop thread: {}",
                Thread.currentThread().getName());

        // 🔧 핵심: ThreadedServer와 정확히 동일한 CPU 작업을 별도 스레드에서 수행
        return CompletableFuture.supplyAsync(() -> {

            // ThreadedServer의 CpuIntensiveServlet과 동일한 계산
            double result = 0.0;
            for (int i = 0; i < 100000; i++) {
                result += Math.sqrt(i) * Math.sin(i);
            }

            long duration = System.currentTimeMillis() - startTime;

            logger.debug("CPU intensive task completed in {}ms on worker thread: {}",
                    duration, Thread.currentThread().getName());

            // ThreadedServer와 유사한 형태의 JSON 응답
            return HttpResponse.json(String.format(
                    "{\"server\":\"eventloop\",\"result\":%.2f,\"duration\":%d,\"thread\":\"%s\",\"timestamp\":%d}",
                    result,
                    duration,
                    Thread.currentThread().getName(),
                    System.currentTimeMillis()
            ));

        }, cpuExecutor);
    }

    /**
     * 더 무거운 CPU 작업 (스트레스 테스트용)
     */
    public CompletableFuture<HttpResponse> handleHeavyRequest(HttpRequest request) {
        return CompletableFuture.supplyAsync(() -> {

            // 더 무거운 계산 작업
            double result = 0.0;
            for (int i = 0; i < 500000; i++) {
                result += Math.sqrt(i) * Math.sin(i) * Math.cos(i);
            }

            return HttpResponse.json(String.format(
                    "{\"server\":\"eventloop\",\"heavy_result\":%.2f,\"thread\":\"%s\"}",
                    result,
                    Thread.currentThread().getName()
            ));

        }, cpuExecutor);
    }

    /**
     * 파라미터 기반 동적 CPU 작업
     */
    public CompletableFuture<HttpResponse> handleParameterizedRequest(HttpRequest request) {
        // iterations 파라미터로 계산 횟수 조정
        String iterationsParam = request.getQueryParameter("iterations");
        int iterations = 100000; // 기본값

        try {
            if (iterationsParam != null) {
                iterations = Integer.parseInt(iterationsParam);
                iterations = Math.min(Math.max(iterations, 1000), 1000000); // 1K~1M 제한
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid iterations parameter: {}, using default", iterationsParam);
        }

        final int finalIterations = iterations;

        return CompletableFuture.supplyAsync(() -> {

            double result = 0.0;
            for (int i = 0; i < finalIterations; i++) {
                result += Math.sqrt(i) * Math.sin(i);
            }

            return HttpResponse.json(String.format(
                    "{\"server\":\"eventloop\",\"result\":%.2f,\"iterations\":%d,\"thread\":\"%s\"}",
                    result,
                    finalIterations,
                    Thread.currentThread().getName()
            ));

        }, cpuExecutor);
    }

    /**
     * CPU Executor 상태 정보
     */
    public CompletableFuture<HttpResponse> getExecutorStats() {
        return CompletableFuture.completedFuture(
                HttpResponse.json(String.format(
                        "{\"server\":\"eventloop\",\"cpu_executor\":{\"shutdown\":%s,\"available_processors\":%d},\"thread\":\"%s\"}",
                        cpuExecutor.isShutdown(),
                        Runtime.getRuntime().availableProcessors(),
                        Thread.currentThread().getName()
                ))
        );
    }

    /**
     * 리소스 정리
     */
    public static void shutdown() {
        logger.info("Shutting down CPU executor for EventLoop server");
        cpuExecutor.shutdown();
    }
}