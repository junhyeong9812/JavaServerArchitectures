package server.threaded;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 톰캣 스타일 ThreadPoolManager
 * 즉시 스레드 생성 전략 (Tomcat 방식)
 *
 * 동작 방식:
 * 1. Core threads 사용 중 → 즉시 새 스레드 생성 (max까지)
 * 2. Max 도달 후 → Queue 사용
 * 3. Queue 가득참 → Rejection
 */
public class ThreadPoolManager {

    private final TomcatStyleThreadPoolExecutor threadPool;
    private final ScheduledExecutorService monitor;
    private final AtomicInteger activeConnections;
    private final ThreadPoolConfig config;

    // 통계 정보
    private final AtomicLong totalRequestsProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicLong rejectedTasks = new AtomicLong(0);
    private volatile int peakActiveThreads = 0;

    public ThreadPoolManager(ThreadPoolConfig config) {
        this.config = config;
        this.activeConnections = new AtomicInteger(0);

        // 톰캣 스타일 ThreadPoolExecutor 생성
        TomcatStyleTaskQueue taskQueue = new TomcatStyleTaskQueue(config.getQueueCapacity());
        this.threadPool = new TomcatStyleThreadPoolExecutor(
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getKeepAliveTime(),
                TimeUnit.SECONDS,
                taskQueue,
                new ServerThreadFactory(),
                new TomcatStyleRejectedExecutionHandler()
        );

        // 🔧 중요: TaskQueue에 Executor 설정
        taskQueue.setExecutor(threadPool);

        // 모든 코어 스레드 미리 생성
        threadPool.prestartAllCoreThreads();

        // 모니터링 스케줄러
        this.monitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ThreadPool-Monitor");
            t.setDaemon(true);
            return t;
        });

        startMonitoring();

        System.out.println("[ThreadPoolManager] Tomcat-style initialized - Core: " +
                config.getCorePoolSize() + ", Max: " + config.getMaxPoolSize());
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
                totalRequestsProcessed.incrementAndGet();
                totalProcessingTime.addAndGet(processingTime);

                int currentActive = threadPool.getActiveCount();
                if (currentActive > peakActiveThreads) {
                    peakActiveThreads = currentActive;
                }
            }
        });
    }

    /**
     * 간단한 모니터링
     */
    private void startMonitoring() {
        if (config.isDebugMode()) {
            monitor.scheduleAtFixedRate(this::printStatistics,
                    config.getMonitorInterval(),
                    config.getMonitorInterval(),
                    TimeUnit.SECONDS);
        }
    }

    /**
     * 통계 출력
     */
    private void printStatistics() {
        System.out.println("\n=== Tomcat-Style ThreadPool Statistics ===");
        System.out.println("Core Pool Size: " + threadPool.getCorePoolSize());
        System.out.println("Current Pool Size: " + threadPool.getPoolSize());
        System.out.println("Max Pool Size: " + threadPool.getMaximumPoolSize());
        System.out.println("Active Threads: " + threadPool.getActiveCount());
        System.out.println("Queue Size: " + threadPool.getQueue().size());
        System.out.println("Active Connections: " + activeConnections.get());
        System.out.println("Total Requests: " + totalRequestsProcessed.get());
        System.out.println("Rejected Tasks: " + rejectedTasks.get());
        System.out.println("Peak Active: " + peakActiveThreads);

        long totalRequests = totalRequestsProcessed.get();
        if (totalRequests > 0) {
            double avgProcessingTime = (double) totalProcessingTime.get() / totalRequests;
            System.out.println("Avg Processing Time: " + String.format("%.2f", avgProcessingTime) + "ms");

            double rejectionRate = (double) rejectedTasks.get() / totalRequests * 100;
            System.out.println("Rejection Rate: " + String.format("%.2f", rejectionRate) + "%");
        }

        System.out.println("Completed Tasks: " + threadPool.getCompletedTaskCount());
        System.out.println("===========================================\n");
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
                totalRequestsProcessed.get(),
                peakActiveThreads,
                totalRequestsProcessed.get() > 0 ?
                        (double) totalProcessingTime.get() / totalRequestsProcessed.get() : 0,
                rejectedTasks.get()
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
     * 톰캣 스타일 ThreadPoolExecutor
     * 핵심: 즉시 스레드 생성 전략
     */
    private class TomcatStyleThreadPoolExecutor extends ThreadPoolExecutor {

        public TomcatStyleThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                                             long keepAliveTime, TimeUnit unit,
                                             BlockingQueue<Runnable> workQueue,
                                             ThreadFactory threadFactory,
                                             RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit,
                    workQueue, threadFactory, handler);
        }

        @Override
        public void execute(Runnable command) {
            // 톰캣 스타일 로직: 가능하면 즉시 새 스레드 생성

            int currentThreads = getPoolSize();
            int activeThreads = getActiveCount();
            int maxThreads = getMaximumPoolSize();

            // 1. 활성 스레드가 코어 사이즈보다 적으면 기본 처리
            if (activeThreads < getCorePoolSize()) {
                super.execute(command);
                return;
            }

            // 2. 최대 스레드 수에 도달하지 않았으면 즉시 새 스레드 생성
            if (currentThreads < maxThreads) {
                // 코어 사이즈를 일시적으로 늘려서 즉시 스레드 생성 유도
                int newCoreSize = Math.min(currentThreads + 1, maxThreads);
                setCorePoolSize(newCoreSize);

                if (config.isDebugMode()) {
                    System.out.println("[TomcatStyle] Immediate thread creation - " +
                            "Threads: " + currentThreads + " -> " + newCoreSize +
                            ", Active: " + activeThreads);
                }
            }

            // 3. 일반 실행
            super.execute(command);
        }
    }

    /**
     * 톰캣 스타일 작업 큐
     * offer() 메서드를 오버라이드하여 스레드 생성 우선
     */
    private class TomcatStyleTaskQueue extends LinkedBlockingQueue<Runnable> {

        private TomcatStyleThreadPoolExecutor executor;

        public TomcatStyleTaskQueue(int capacity) {
            super(capacity);
        }

        public void setExecutor(TomcatStyleThreadPoolExecutor executor) {
            this.executor = executor;
        }

        @Override
        public boolean offer(Runnable o) {
            // 톰캣 로직: 새 스레드를 만들 수 있으면 큐에 넣지 않음
            if (executor != null) {
                int currentThreads = executor.getPoolSize();
                int maxThreads = executor.getMaximumPoolSize();

                // 새 스레드를 만들 수 있으면 false 반환 (큐에 넣지 않음)
                if (currentThreads < maxThreads) {
                    return false;
                }
            }

            // 최대 스레드에 도달했으면 큐에 저장
            return super.offer(o);
        }
    }

    /**
     * 서버 스레드 팩토리
     */
    private static class ServerThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "TomcatStyle-Thread-" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    /**
     * 톰캣 스타일 거부 정책
     */
    private class TomcatStyleRejectedExecutionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            rejectedTasks.incrementAndGet();

            if (!executor.isShutdown()) {
                System.err.println("[ThreadPool] Task rejected - all threads busy, queue full");

                // 톰캣 방식: CallerRunsPolicy 사용 (호출자 스레드에서 실행)
                try {
                    r.run();
                    System.out.println("[ThreadPool] Task executed in caller thread: " +
                            Thread.currentThread().getName());
                } catch (Exception e) {
                    System.err.println("[ThreadPool] Error executing rejected task: " + e.getMessage());
                }
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
        private final long rejectedTasks;

        public ThreadPoolStatus(int corePoolSize, int currentPoolSize, int activeThreads,
                                int queueSize, int activeConnections, long totalRequests,
                                int peakActiveThreads, double avgProcessingTime, long rejectedTasks) {
            this.corePoolSize = corePoolSize;
            this.currentPoolSize = currentPoolSize;
            this.activeThreads = activeThreads;
            this.queueSize = queueSize;
            this.activeConnections = activeConnections;
            this.totalRequests = totalRequests;
            this.peakActiveThreads = peakActiveThreads;
            this.avgProcessingTime = avgProcessingTime;
            this.rejectedTasks = rejectedTasks;
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
        public long getRejectedTasks() { return rejectedTasks; }

        @Override
        public String toString() {
            return String.format(
                    "TomcatStyle-ThreadPoolStatus{core=%d, current=%d, active=%d, queue=%d, " +
                            "connections=%d, requests=%d, peak=%d, avgTime=%.2fms, rejected=%d}",
                    corePoolSize, currentPoolSize, activeThreads, queueSize,
                    activeConnections, totalRequests, peakActiveThreads, avgProcessingTime, rejectedTasks
            );
        }
    }
}