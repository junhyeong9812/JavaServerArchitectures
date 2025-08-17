package server.hybrid;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * 적응형 스레드풀 - 하이브리드 서버의 핵심 컴포넌트 (수정된 버전)
 *
 * 수정사항:
 * 1. PriorityBlockingQueue 사용 시 모든 작업을 PriorityTask로 래핑
 * 2. submit() 메서드에서 올바른 우선순위 처리
 * 3. FutureTask ClassCastException 해결
 */
public class AdaptiveThreadPool extends ThreadPoolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveThreadPool.class);

    // === 스레드풀 설정 ===
    private final String poolName;
    private final int minPoolSize;
    private final int maxPoolSize;
    private final long keepAliveTimeSeconds;

    // === 적응형 조정 변수 ===
    private volatile boolean adaptiveEnabled = true;
    private volatile int targetQueueSize = 10;
    private volatile double adjustmentFactor = 0.1;

    // === 모니터링 ===
    private final AtomicLong submittedTasks = new AtomicLong(0);
    private final AtomicLong completedTasks = new AtomicLong(0);
    private final AtomicLong rejectedTasks = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);

    // === 스케줄링 ===
    private final ScheduledExecutorService monitorExecutor;
    private final AtomicReference<ThreadPoolStats> lastStats = new AtomicReference<>();

    // === 우선순위 큐 ===
    private final PriorityBlockingQueue<Runnable> priorityQueue;

    /**
     * 적응형 스레드풀 생성자
     */
    public AdaptiveThreadPool(String poolName, int minPoolSize, int maxPoolSize, long keepAliveTimeSeconds) {
        super(
                minPoolSize,
                maxPoolSize,
                keepAliveTimeSeconds,
                TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(1000),
                new AdaptiveThreadFactory(poolName),
                new AdaptiveRejectedExecutionHandler()
        );

        this.poolName = poolName;
        this.minPoolSize = minPoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTimeSeconds = keepAliveTimeSeconds;

        this.priorityQueue = (PriorityBlockingQueue<Runnable>) getQueue();

        allowCoreThreadTimeOut(true);

        this.monitorExecutor = Executors.newScheduledThreadPool(1,
                r -> new Thread(r, poolName + "-Monitor"));

        this.monitorExecutor.scheduleAtFixedRate(
                this::monitorAndAdjust,
                5,
                5,
                TimeUnit.SECONDS
        );

        logger.info("AdaptiveThreadPool 생성 완료 - 이름: {}, 크기: {}-{}, 유휴시간: {}초",
                poolName, minPoolSize, maxPoolSize, keepAliveTimeSeconds);
    }

    /**
     * 작업 제출 (우선순위 지원) - 수정된 버전
     */
    public Future<?> submit(Runnable task, int priority) {
        submittedTasks.incrementAndGet();

        // ⭐ 모든 작업을 PriorityTask로 래핑
        PriorityTask priorityTask = new PriorityTask(task, priority);

        try {
            // ⭐ execute()를 직접 호출하여 PriorityTask가 큐에 들어가도록 함
            execute(priorityTask);

            // ⭐ PriorityTask 자체를 Future로 반환 (Runnable과 Future 인터페이스 구현)
            return priorityTask.getFuture();

        } catch (RejectedExecutionException e) {
            rejectedTasks.incrementAndGet();
            logger.warn("작업 거부 - 스레드풀: {}, 우선순위: {}", poolName, priority);
            throw e;
        }
    }

    /**
     * 일반 작업 제출 (기본 우선순위) - 수정된 버전
     */
    @Override
    public Future<?> submit(Runnable task) {
        return submit(task, 0);  // 기본 우선순위 0으로 처리
    }

    /**
     * Callable 작업 제출 (우선순위 지원) - 수정된 버전
     */
    public <T> Future<T> submit(Callable<T> task, int priority) {
        submittedTasks.incrementAndGet();

        // ⭐ Callable을 Runnable로 래핑하여 PriorityTask 생성
        FutureTask<T> futureTask = new FutureTask<>(task);
        PriorityTask priorityTask = new PriorityTask(futureTask, priority);

        try {
            execute(priorityTask);
            return futureTask;  // 원래 FutureTask 반환

        } catch (RejectedExecutionException e) {
            rejectedTasks.incrementAndGet();
            throw e;
        }
    }

    /**
     * ⭐ Callable 작업 제출 (기본 우선순위) - 새로 추가
     */
    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return submit(task, 0);
    }

    /**
     * 스레드풀 모니터링 및 적응형 조정
     */
    private void monitorAndAdjust() {
        try {
            ThreadPoolStats currentStats = collectStats();
            lastStats.set(currentStats);

            logger.debug("스레드풀 상태 - {}", currentStats);

            if (adaptiveEnabled) {
                adjustPoolSize(currentStats);
            }

        } catch (Exception e) {
            logger.error("스레드풀 모니터링 중 오류", e);
        }
    }

    /**
     * 스레드풀 크기 적응형 조정
     */
    private void adjustPoolSize(ThreadPoolStats stats) {
        int currentPoolSize = getPoolSize();
        int currentQueueSize = getQueue().size();
        double utilization = (double) getActiveCount() / currentPoolSize;

        boolean needIncrease = shouldIncreasePoolSize(stats, currentQueueSize, utilization);
        boolean needDecrease = shouldDecreasePoolSize(stats, currentQueueSize, utilization);

        if (needIncrease && currentPoolSize < maxPoolSize) {
            int newSize = Math.min(maxPoolSize,
                    currentPoolSize + (int) Math.max(1, currentPoolSize * adjustmentFactor));

            setCorePoolSize(newSize);
            setMaximumPoolSize(Math.max(newSize, getMaximumPoolSize()));

            logger.info("스레드풀 크기 증가 - {}: {} -> {}, 큐크기: {}, 사용률: {:.2f}%",
                    poolName, currentPoolSize, newSize, currentQueueSize, utilization * 100);

        } else if (needDecrease && currentPoolSize > minPoolSize) {
            int newSize = Math.max(minPoolSize,
                    currentPoolSize - (int) Math.max(1, currentPoolSize * adjustmentFactor));

            setCorePoolSize(newSize);

            logger.info("스레드풀 크기 감소 - {}: {} -> {}, 큐크기: {}, 사용률: {:.2f}%",
                    poolName, currentPoolSize, newSize, currentQueueSize, utilization * 100);
        }
    }

    /**
     * 스레드풀 크기 증가 필요 여부 판단
     */
    private boolean shouldIncreasePoolSize(ThreadPoolStats stats, int queueSize, double utilization) {
        if (queueSize > targetQueueSize) {
            return true;
        }

        if (utilization > 0.8) {
            return true;
        }

        if (stats.getAverageWaitTime() > 100) {
            return true;
        }

        return false;
    }

    /**
     * 스레드풀 크기 감소 필요 여부 판단
     */
    private boolean shouldDecreasePoolSize(ThreadPoolStats stats, int queueSize, double utilization) {
        if (queueSize == 0 && utilization < 0.3) {
            return true;
        }

        if (stats.getAverageWaitTime() < 10) {
            return true;
        }

        return false;
    }

    /**
     * 현재 스레드풀 통계 수집
     */
    private ThreadPoolStats collectStats() {
        return new ThreadPoolStats(
                poolName,
                getPoolSize(),
                getActiveCount(),
                getQueue().size(),
                getCompletedTaskCount(),
                submittedTasks.get(),
                rejectedTasks.get(),
                calculateAverageExecutionTime(),
                calculateAverageWaitTime(),
                calculateThroughput()
        );
    }

    /**
     * 평균 실행 시간 계산 (밀리초)
     */
    private long calculateAverageExecutionTime() {
        long completed = completedTasks.get();
        if (completed == 0) return 0;

        return totalExecutionTime.get() / completed / 1_000_000;
    }

    /**
     * 평균 대기 시간 계산 (밀리초)
     */
    private long calculateAverageWaitTime() {
        int queueSize = getQueue().size();
        if (queueSize == 0) return 0;

        long avgExecutionTime = calculateAverageExecutionTime();
        if (avgExecutionTime == 0) return 0;

        int activeThreads = Math.max(1, getActiveCount());
        return (queueSize * avgExecutionTime) / activeThreads;
    }

    /**
     * 처리량 계산 (작업/초)
     */
    private double calculateThroughput() {
        long completed = completedTasks.get();
        if (completed == 0) return 0.0;

        return completed / 60.0;
    }

    /**
     * 작업 실행 전 후처리
     */
    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);

        if (r instanceof PriorityTask) {
            PriorityTask priorityTask = (PriorityTask) r;
            priorityTask.setStartTime(System.nanoTime());
        }
    }

    /**
     * 작업 실행 후 후처리
     */
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);

        try {
            if (r instanceof PriorityTask) {
                PriorityTask priorityTask = (PriorityTask) r;
                long executionTime = System.nanoTime() - priorityTask.getStartTime();
                totalExecutionTime.addAndGet(executionTime);
            }

            completedTasks.incrementAndGet();

        } catch (Exception e) {
            logger.warn("작업 후처리 중 오류", e);
        }

        if (t != null) {
            logger.error("작업 실행 중 예외 발생", t);
        }
    }

    /**
     * 우선순위 설정
     */
    public void setTargetQueueSize(int targetQueueSize) {
        this.targetQueueSize = targetQueueSize;
        logger.info("목표 큐 크기 변경: {}", targetQueueSize);
    }

    /**
     * 조정 계수 설정
     */
    public void setAdjustmentFactor(double adjustmentFactor) {
        this.adjustmentFactor = Math.max(0.01, Math.min(0.5, adjustmentFactor));
        logger.info("조정 계수 변경: {}", this.adjustmentFactor);
    }

    /**
     * 적응형 조정 활성화/비활성화
     */
    public void setAdaptiveEnabled(boolean enabled) {
        this.adaptiveEnabled = enabled;
        logger.info("적응형 조정: {}", enabled ? "활성화" : "비활성화");
    }

    /**
     * 스레드풀 통계 조회
     */
    public ThreadPoolStats getCurrentStats() {
        ThreadPoolStats stats = lastStats.get();
        return stats != null ? stats : collectStats();
    }

    /**
     * 스레드풀 상태 정보 출력
     */
    public void printStatus() {
        ThreadPoolStats stats = getCurrentStats();
        logger.info("=== {} 상태 ===", poolName);
        logger.info("스레드: {}/{} (활성: {})", getPoolSize(), getMaximumPoolSize(), getActiveCount());
        logger.info("큐: {} (목표: {})", getQueue().size(), targetQueueSize);
        logger.info("작업: 제출={}, 완료={}, 거부={}", submittedTasks.get(), completedTasks.get(), rejectedTasks.get());
        logger.info("성능: 평균실행={}ms, 평균대기={}ms, 처리량={:.2f}/s",
                stats.getAverageExecutionTime(), stats.getAverageWaitTime(), stats.getThroughput());
    }

    /**
     * 스레드풀 종료
     */
    @Override
    public void shutdown() {
        logger.info("{} 종료 시작...", poolName);

        monitorExecutor.shutdown();
        super.shutdown();

        try {
            if (!awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("{} 강제 종료", poolName);
                shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdownNow();
        }

        logger.info("{} 종료 완료", poolName);
    }

    /**
     * ⭐ 우선순위 작업 래퍼 클래스 - 수정된 버전
     * FutureTask와 호환되도록 개선
     */
    private static class PriorityTask implements Runnable, Comparable<PriorityTask> {
        private final Runnable task;
        private final int priority;
        private final long createdTime;
        private volatile long startTime;
        private final FutureTask<Void> future;  // ⭐ Future 기능 제공

        public PriorityTask(Runnable task, int priority) {
            this.task = task;
            this.priority = priority;
            this.createdTime = System.nanoTime();
            // ⭐ Runnable을 Callable로 래핑하여 Future 생성
            this.future = new FutureTask<>(() -> {
                task.run();
                return null;
            });
        }

        @Override
        public void run() {
            future.run();  // ⭐ FutureTask 실행
        }

        @Override
        public int compareTo(PriorityTask other) {
            // 높은 우선순위가 먼저 실행되도록 (역순 정렬)
            int result = Integer.compare(other.priority, this.priority);

            if (result == 0) {
                // 동일한 우선순위일 경우 FIFO (먼저 생성된 것부터)
                result = Long.compare(this.createdTime, other.createdTime);
            }

            return result;
        }

        public Future<Void> getFuture() {
            return future;
        }

        public int getPriority() { return priority; }
        public long getCreatedTime() { return createdTime; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
    }

    /**
     * 커스텀 스레드 팩토리 (Java 17+ 호환)
     */
    private static class AdaptiveThreadFactory implements ThreadFactory {
        private final String poolName;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final ThreadGroup group;

        AdaptiveThreadFactory(String poolName) {
            this.poolName = poolName;
            // ⭐ SecurityManager 사용하지 않고 현재 스레드 그룹 사용
            this.group = Thread.currentThread().getThreadGroup();
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, poolName + "-" + threadNumber.getAndIncrement(), 0);

            if (t.isDaemon()) {
                t.setDaemon(false);
            }

            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }

            return t;
        }
    }

    /**
     * 커스텀 거부 핸들러
     */
    private static class AdaptiveRejectedExecutionHandler implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (!executor.isShutdown()) {
                try {
                    Logger logger = LoggerFactory.getLogger(AdaptiveRejectedExecutionHandler.class);
                    logger.warn("스레드풀 포화 - 호출 스레드에서 직접 실행: {}",
                            Thread.currentThread().getName());

                    r.run();
                } catch (Exception e) {
                    Logger logger = LoggerFactory.getLogger(AdaptiveRejectedExecutionHandler.class);
                    logger.error("거부된 작업 실행 중 오류", e);
                }
            }
        }
    }

    /**
     * 스레드풀 통계 클래스
     */
    public static class ThreadPoolStats {
        private final String poolName;
        private final int poolSize;
        private final int activeCount;
        private final int queueSize;
        private final long completedTaskCount;
        private final long submittedTasks;
        private final long rejectedTasks;
        private final long averageExecutionTime;
        private final long averageWaitTime;
        private final double throughput;

        public ThreadPoolStats(String poolName, int poolSize, int activeCount, int queueSize,
                               long completedTaskCount, long submittedTasks, long rejectedTasks,
                               long averageExecutionTime, long averageWaitTime, double throughput) {
            this.poolName = poolName;
            this.poolSize = poolSize;
            this.activeCount = activeCount;
            this.queueSize = queueSize;
            this.completedTaskCount = completedTaskCount;
            this.submittedTasks = submittedTasks;
            this.rejectedTasks = rejectedTasks;
            this.averageExecutionTime = averageExecutionTime;
            this.averageWaitTime = averageWaitTime;
            this.throughput = throughput;
        }

        // Getters
        public String getPoolName() { return poolName; }
        public int getPoolSize() { return poolSize; }
        public int getActiveCount() { return activeCount; }
        public int getQueueSize() { return queueSize; }
        public long getCompletedTaskCount() { return completedTaskCount; }
        public long getSubmittedTasks() { return submittedTasks; }
        public long getRejectedTasks() { return rejectedTasks; }
        public long getAverageExecutionTime() { return averageExecutionTime; }
        public long getAverageWaitTime() { return averageWaitTime; }
        public double getThroughput() { return throughput; }

        @Override
        public String toString() {
            return String.format(
                    "%s{pool=%d, active=%d, queue=%d, completed=%d, " +
                            "submitted=%d, rejected=%d, avgExec=%dms, avgWait=%dms, throughput=%.2f/s}",
                    poolName, poolSize, activeCount, queueSize, completedTaskCount,
                    submittedTasks, rejectedTasks, averageExecutionTime, averageWaitTime, throughput
            );
        }
    }
}