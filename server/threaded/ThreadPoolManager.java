package server.threaded;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 동적 스레드풀 관리자
 * 요청 부하에 따라 스레드풀 크기를 자동 조정
 */
public class ThreadPoolManager {

    private final ThreadPoolExecutor threadPool;
    private final ScheduledExecutorService monitor;
    private final AtomicInteger activeConnections;
    private final ThreadPoolConfig config;

    // 통계 정보
    private volatile long totalRequestsProcessed = 0;
    private volatile long totalProcessingTime = 0;
    private volatile int peakActiveThreads = 0;

    public ThreadPoolManager(ThreadPoolConfig config) {
        this.config = config;
        this.activeConnections = new AtomicInteger(0);

        // 커스텀 ThreadPoolExecutor 생성
        this.threadPool = new ThreadPoolExecutor(
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getKeepAliveTime(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getQueueCapacity()),
                new ServerThreadFactory(),
                new ServerRejectedExecutionHandler()
        );

        // 스레드풀 모니터링 스케줄러
        this.monitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ThreadPool-Monitor");
            t.setDaemon(true);
            return t;
        });

        startMonitoring();
    }

    /**
     * 작업 제출
     */
    public Future<?> submit(Runnable task) {
        activeConnections.incrementAndGet();
        long startTime = System.currentTimeMillis();

        return threadPool.submit(() -> {
            try {
                task.run();
            } finally {
                activeConnections.decrementAndGet();

                // 통계 업데이트
                long processingTime = System.currentTimeMillis() - startTime;
                totalRequestsProcessed++;
                totalProcessingTime += processingTime;

                int currentActive = threadPool.getActiveCount();
                if (currentActive > peakActiveThreads) {
                    peakActiveThreads = currentActive;
                }
            }
        });
    }

    /**
     * 스레드풀 모니터링 시작
     */
    private void startMonitoring() {
        monitor.scheduleAtFixedRate(this::adjustPoolSize,
                config.getMonitorInterval(),
                config.getMonitorInterval(),
                TimeUnit.SECONDS);

        // 통계 출력 (디버그용)
        if (config.isDebugMode()) {
            monitor.scheduleAtFixedRate(this::printStatistics, 30, 30, TimeUnit.SECONDS);
        }
    }

    /**
     * 스레드풀 크기 동적 조정
     */
    private void adjustPoolSize() {
        try {
            int currentActive = threadPool.getActiveCount();
            int currentPool = threadPool.getPoolSize();
            int queueSize = threadPool.getQueue().size();

            // 부하가 높은 경우 스레드 증가
            if (currentActive >= currentPool * 0.8 && queueSize > 0) {
                if (currentPool < config.getMaxPoolSize()) {
                    threadPool.setCorePoolSize(Math.min(currentPool + config.getScaleStep(),
                            config.getMaxPoolSize()));
                    System.out.println("[ThreadPool] Scaled UP: " + threadPool.getCorePoolSize() +
                            " (active: " + currentActive + ", queue: " + queueSize + ")");
                }
            }
            // 부하가 낮은 경우 스레드 감소
            else if (currentActive <= currentPool * 0.3 && queueSize == 0) {
                if (currentPool > config.getCorePoolSize()) {
                    threadPool.setCorePoolSize(Math.max(currentPool - config.getScaleStep(),
                            config.getCorePoolSize()));
                    System.out.println("[ThreadPool] Scaled DOWN: " + threadPool.getCorePoolSize() +
                            " (active: " + currentActive + ", queue: " + queueSize + ")");
                }
            }

        } catch (Exception e) {
            System.err.println("[ThreadPool] Error during adjustment: " + e.getMessage());
        }
    }

    /**
     * 통계 정보 출력
     */
    private void printStatistics() {
        System.out.println("\n=== ThreadPool Statistics ===");
        System.out.println("Core Pool Size: " + threadPool.getCorePoolSize());
        System.out.println("Current Pool Size: " + threadPool.getPoolSize());
        System.out.println("Active Threads: " + threadPool.getActiveCount());
        System.out.println("Queue Size: " + threadPool.getQueue().size());
        System.out.println("Active Connections: " + activeConnections.get());
        System.out.println("Total Requests Processed: " + totalRequestsProcessed);
        System.out.println("Peak Active Threads: " + peakActiveThreads);

        if (totalRequestsProcessed > 0) {
            double avgProcessingTime = (double) totalProcessingTime / totalRequestsProcessed;
            System.out.println("Average Processing Time: " + String.format("%.2f", avgProcessingTime) + "ms");
        }

        System.out.println("Completed Tasks: " + threadPool.getCompletedTaskCount());
        System.out.println("==============================\n");
    }

    /**
     * 현재 활성 연결 수
     */
    public int getActiveConnections() {
        return activeConnections.get();
    }

    /**
     * 스레드풀 상태 정보
     */
    public ThreadPoolStatus getStatus() {
        return new ThreadPoolStatus(
                threadPool.getCorePoolSize(),
                threadPool.getPoolSize(),
                threadPool.getActiveCount(),
                threadPool.getQueue().size(),
                activeConnections.get(),
                totalRequestsProcessed,
                peakActiveThreads,
                totalRequestsProcessed > 0 ? (double) totalProcessingTime / totalRequestsProcessed : 0
        );
    }

    /**
     * 셧다운
     */
    public void shutdown() {
        System.out.println("[ThreadPool] Shutting down...");

        monitor.shutdown();
        threadPool.shutdown();

        try {
            if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                System.out.println("[ThreadPool] Force shutdown...");
                threadPool.shutdownNow();
            }
            System.out.println("[ThreadPool] Shutdown completed");
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 커스텀 스레드 팩토리
     */
    private static class ServerThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ServerThread-" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    /**
     * 커스텀 거부 정책
     */
    private static class ServerRejectedExecutionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            System.err.println("[ThreadPool] Task rejected - pool is saturated!");
            // 간단한 백프레셔: 잠시 대기 후 재시도
            try {
                Thread.sleep(100);
                if (!executor.isShutdown()) {
                    executor.getQueue().offer(r, 1, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 스레드풀 상태 정보 클래스
     */
    public static class ThreadPoolStatus {
        private final int corePoolSize;
        private final int currentPoolSize;
        private final int activeThreads;
        private final int queueSize;
        private final int activeConnections;
        private final long totalRequests;
        private final int peakActiveThreads;
        private final double avgProcessingTime;

        public ThreadPoolStatus(int corePoolSize, int currentPoolSize, int activeThreads,
                                int queueSize, int activeConnections, long totalRequests,
                                int peakActiveThreads, double avgProcessingTime) {
            this.corePoolSize = corePoolSize;
            this.currentPoolSize = currentPoolSize;
            this.activeThreads = activeThreads;
            this.queueSize = queueSize;
            this.activeConnections = activeConnections;
            this.totalRequests = totalRequests;
            this.peakActiveThreads = peakActiveThreads;
            this.avgProcessingTime = avgProcessingTime;
        }

        // Getters
        public int getCorePoolSize() { return corePoolSize; }
        public int getCurrentPoolSize() { return currentPoolSize; }
        public int getActiveThreads() { return activeThreads; }
        public int getQueueSize() { return queueSize; }
        public int getActiveConnections() { return activeConnections; }
        public long getTotalRequests() { return totalRequests; }
        public int getPeakActiveThreads() { return peakActiveThreads; }
        public double getAvgProcessingTime() { return avgProcessingTime; }

        @Override
        public String toString() {
            return String.format(
                    "ThreadPoolStatus{core=%d, current=%d, active=%d, queue=%d, " +
                            "connections=%d, requests=%d, peak=%d, avgTime=%.2fms}",
                    corePoolSize, currentPoolSize, activeThreads, queueSize,
                    activeConnections, totalRequests, peakActiveThreads, avgProcessingTime
            );
        }
    }
}