package server.threaded;

import server.core.routing.Router;
import java.net.Socket;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 스레드 기반 요청 처리기
 * 각 연결을 개별 스레드에서 처리
 */
public class ThreadedProcessor {

    private final ThreadPoolManager threadPoolManager;
    private final Router router;
    private final RequestHandlerConfig handlerConfig;

    // 통계 정보
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong rejectedConnections = new AtomicLong(0);
    private volatile long startTime;

    public ThreadedProcessor(Router router, ThreadPoolConfig threadPoolConfig,
                             RequestHandlerConfig handlerConfig) {
        this.router = router;
        this.handlerConfig = handlerConfig;
        this.threadPoolManager = new ThreadPoolManager(threadPoolConfig);
        this.startTime = System.currentTimeMillis();

        System.out.println("[ThreadedProcessor] Initialized with config: " + handlerConfig);
    }

    /**
     * 클라이언트 연결 처리
     */
    public Future<?> processConnection(Socket clientSocket) {
        long connectionId = totalConnections.incrementAndGet();
        activeConnections.incrementAndGet();

        if (handlerConfig.isDebugMode()) {
            System.out.println("[ThreadedProcessor] New connection #" + connectionId +
                    " from " + clientSocket.getRemoteSocketAddress());
        }

        try {
            // 스레드풀에 작업 제출
            return threadPoolManager.submit(new ConnectionTask(clientSocket, connectionId));

        } catch (Exception e) {
            // 스레드풀이 포화된 경우
            rejectedConnections.incrementAndGet();
            activeConnections.decrementAndGet();

            System.err.println("[ThreadedProcessor] Connection #" + connectionId +
                    " rejected - thread pool saturated");

            // 연결 즉시 종료
            try {
                clientSocket.close();
            } catch (Exception closeException) {
                // 무시
            }

            throw new RuntimeException("Thread pool saturated", e);
        }
    }

    /**
     * 연결 처리 작업 래퍼
     */
    private class ConnectionTask implements Runnable {
        private final Socket clientSocket;
        private final long connectionId;

        public ConnectionTask(Socket clientSocket, long connectionId) {
            this.clientSocket = clientSocket;
            this.connectionId = connectionId;
        }

        @Override
        public void run() {
            try {
                // 실제 요청 처리
                BlockingRequestHandler handler = new BlockingRequestHandler(
                        clientSocket, router, handlerConfig
                );
                handler.run();

            } catch (Exception e) {
                System.err.println("[ThreadedProcessor] Error in connection #" + connectionId +
                        ": " + e.getMessage());
            } finally {
                activeConnections.decrementAndGet();

                if (handlerConfig.isDebugMode()) {
                    System.out.println("[ThreadedProcessor] Connection #" + connectionId +
                            " finished - active: " + activeConnections.get());
                }
            }
        }
    }

    /**
     * 프로세서 상태 정보
     */
    public ProcessorStatus getStatus() {
        ThreadPoolManager.ThreadPoolStatus poolStatus = threadPoolManager.getStatus();

        return new ProcessorStatus(
                totalConnections.get(),
                activeConnections.get(),
                rejectedConnections.get(),
                System.currentTimeMillis() - startTime,
                poolStatus
        );
    }

    /**
     * 통계 정보 출력
     */
    public void printStatistics() {
        ProcessorStatus status = getStatus();

        System.out.println("\n=== ThreadedProcessor Statistics ===");
        System.out.println("Total Connections: " + status.getTotalConnections());
        System.out.println("Active Connections: " + status.getActiveConnections());
        System.out.println("Rejected Connections: " + status.getRejectedConnections());
        System.out.println("Uptime: " + (status.getUptime() / 1000) + " seconds");

        if (status.getTotalConnections() > 0) {
            double rejectionRate = (double) status.getRejectedConnections() / status.getTotalConnections() * 100;
            System.out.println("Rejection Rate: " + String.format("%.2f", rejectionRate) + "%");
        }

        System.out.println("\nThread Pool Status:");
        System.out.println(status.getThreadPoolStatus());
        System.out.println("=====================================\n");
    }

    /**
     * 프로세서 종료
     */
    public void shutdown() {
        System.out.println("[ThreadedProcessor] Shutting down...");

        // 통계 출력
        printStatistics();

        // 스레드풀 종료
        threadPoolManager.shutdown();

        System.out.println("[ThreadedProcessor] Shutdown completed");
    }

    /**
     * 활성 연결 수 확인
     */
    public long getActiveConnections() {
        return activeConnections.get();
    }

    /**
     * 총 연결 수 확인
     */
    public long getTotalConnections() {
        return totalConnections.get();
    }

    /**
     * 거부된 연결 수 확인
     */
    public long getRejectedConnections() {
        return rejectedConnections.get();
    }

    /**
     * 프로세서 상태 클래스
     */
    public static class ProcessorStatus {
        private final long totalConnections;
        private final long activeConnections;
        private final long rejectedConnections;
        private final long uptime;
        private final ThreadPoolManager.ThreadPoolStatus threadPoolStatus;

        public ProcessorStatus(long totalConnections, long activeConnections,
                               long rejectedConnections, long uptime,
                               ThreadPoolManager.ThreadPoolStatus threadPoolStatus) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.rejectedConnections = rejectedConnections;
            this.uptime = uptime;
            this.threadPoolStatus = threadPoolStatus;
        }

        public long getTotalConnections() { return totalConnections; }
        public long getActiveConnections() { return activeConnections; }
        public long getRejectedConnections() { return rejectedConnections; }
        public long getUptime() { return uptime; }
        public ThreadPoolManager.ThreadPoolStatus getThreadPoolStatus() { return threadPoolStatus; }

        @Override
        public String toString() {
            return String.format(
                    "ProcessorStatus{total=%d, active=%d, rejected=%d, uptime=%ds}",
                    totalConnections, activeConnections, rejectedConnections, uptime / 1000
            );
        }
    }
}