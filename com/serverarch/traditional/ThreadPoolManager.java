package com.serverarch.traditional;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * ThreadedServer를 위한 고급 스레드 풀 관리자
 *
 * 이 클래스는 전통적인 Thread-per-Request 서버에서 사용되는 스레드 풀을
 * 효율적으로 관리하고 모니터링하는 기능을 제공합니다.
 *
 * 주요 기능:
 * - 동적 스레드 풀 크기 조정
 * - 스레드 풀 상태 모니터링
 * - 백프레셔(Backpressure) 정책 적용
 * - 스레드 생명주기 관리
 * - 성능 메트릭 수집
 */
public class ThreadPoolManager {

    // 로거 인스턴스
    private static final Logger logger = Logger.getLogger(ThreadPoolManager.class.getName());

    // 기본 설정 상수들
    private static final int DEFAULT_CORE_POOL_SIZE = 10;          // 기본 코어 스레드 수
    private static final int DEFAULT_MAXIMUM_POOL_SIZE = 200;      // 기본 최대 스레드 수
    private static final long DEFAULT_KEEP_ALIVE_TIME = 60L;       // 기본 유지 시간 (초)
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;        // 기본 큐 용량
    private static final int MONITOR_INTERVAL_SECONDS = 30;        // 모니터링 간격 (초)

    // 스레드 풀 실행기
    private final ThreadPoolExecutor threadPoolExecutor;

    // 모니터링을 위한 스케줄러
    private final ScheduledExecutorService monitoringScheduler;

    // 백프레셔 정책
    private final RejectedExecutionHandler rejectedExecutionHandler;

    // 스레드 팩토리
    private final ThreadFactory threadFactory;

    // 통계 수집을 위한 카운터들
    private final AtomicLong totalTasksSubmitted = new AtomicLong(0);      // 제출된 총 작업 수
    private final AtomicLong totalTasksCompleted = new AtomicLong(0);      // 완료된 총 작업 수
    private final AtomicLong totalTasksRejected = new AtomicLong(0);       // 거부된 총 작업 수
    private final AtomicLong totalExecutionTime = new AtomicLong(0);       // 총 실행 시간 (나노초)

    // 모니터링 상태
    private volatile boolean monitoringEnabled = true;

    /**
     * 기본 설정으로 ThreadPoolManager를 생성합니다.
     */
    public ThreadPoolManager() {
        this(DEFAULT_CORE_POOL_SIZE, DEFAULT_MAXIMUM_POOL_SIZE, DEFAULT_KEEP_ALIVE_TIME,
                TimeUnit.SECONDS, DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * 사용자 정의 설정으로 ThreadPoolManager를 생성합니다.
     *
     * @param corePoolSize 코어 스레드 수
     * @param maximumPoolSize 최대 스레드 수
     * @param keepAliveTime 유지 시간
     * @param unit 시간 단위
     * @param queueCapacity 큐 용량
     */
    public ThreadPoolManager(int corePoolSize,
                             int maximumPoolSize,
                             long keepAliveTime,
                             TimeUnit unit,
                             int queueCapacity) {

        // 입력 검증
        validatePoolParameters(corePoolSize, maximumPoolSize, keepAliveTime, queueCapacity);

        // 커스텀 스레드 팩토리 생성
        this.threadFactory = new CustomThreadFactory("ThreadedServer-Worker");

        // 백프레셔 정책 생성 (CallerRuns + 통계 수집)
        this.rejectedExecutionHandler = new CustomRejectedExecutionHandler();

        // 작업 큐 생성 (용량 제한이 있는 LinkedBlockingQueue)
        BlockingQueue<Runnable> workQueue = queueCapacity > 0
                ? new LinkedBlockingQueue<>(queueCapacity)
                : new LinkedBlockingQueue<>(); // 무제한 큐

        // ThreadPoolExecutor 생성
        this.threadPoolExecutor = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                threadFactory,
                rejectedExecutionHandler
        ) {
            // 작업 완료 시 통계 업데이트를 위한 오버라이드
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                totalTasksCompleted.incrementAndGet();

                // 작업 실행 시간 계산 (TaskWrapper를 사용한 경우)
                if (r instanceof TaskWrapper) {
                    TaskWrapper wrapper = (TaskWrapper) r;
                    long executionTime = System.nanoTime() - wrapper.getSubmissionTime();
                    totalExecutionTime.addAndGet(executionTime);
                }

                // 오류 발생 시 로깅
                if (t != null) {
                    logger.log(Level.WARNING, "스레드 풀 작업 실행 중 오류 발생", t);
                }
            }
        };

        // 코어 스레드들이 유휴 상태에서도 살아있도록 설정
        this.threadPoolExecutor.allowCoreThreadTimeOut(false);

        // 모니터링 스케줄러 생성
        this.monitoringScheduler = Executors.newSingleThreadScheduledExecutor(
                new CustomThreadFactory("ThreadPoolMonitor")
        );

        // 주기적 모니터링 시작
        startMonitoring();

        logger.info(String.format(
                "ThreadPoolManager 생성됨 - 코어: %d, 최대: %d, 유지시간: %d%s, 큐용량: %s",
                corePoolSize, maximumPoolSize, keepAliveTime, unit.toString().toLowerCase(),
                queueCapacity > 0 ? String.valueOf(queueCapacity) : "무제한"
        ));
    }

    /**
     * 작업을 스레드 풀에 제출합니다.
     *
     * @param task 실행할 작업
     * @return Future 객체
     */
    public Future<?> submit(Runnable task) {
        // null 체크
        if (task == null) {
            throw new IllegalArgumentException("작업이 null입니다");
        }

        // 제출 통계 업데이트
        totalTasksSubmitted.incrementAndGet();

        // 작업을 TaskWrapper로 감싸서 실행 시간 측정
        TaskWrapper wrappedTask = new TaskWrapper(task);

        try {
            // 스레드 풀에 작업 제출
            return threadPoolExecutor.submit(wrappedTask);
        } catch (RejectedExecutionException e) {
            // 작업 거부 시 통계 업데이트
            totalTasksRejected.incrementAndGet();
            logger.log(Level.WARNING, "작업이 거부되었습니다", e);
            throw e;
        }
    }

    /**
     * Callable 작업을 스레드 풀에 제출합니다.
     *
     * @param task 실행할 Callable 작업
     * @return Future 객체
     */
    public <T> Future<T> submit(Callable<T> task) {
        // null 체크
        if (task == null) {
            throw new IllegalArgumentException("작업이 null입니다");
        }

        // 제출 통계 업데이트
        totalTasksSubmitted.incrementAndGet();

        try {
            // 스레드 풀에 작업 제출
            return threadPoolExecutor.submit(task);
        } catch (RejectedExecutionException e) {
            // 작업 거부 시 통계 업데이트
            totalTasksRejected.incrementAndGet();
            logger.log(Level.WARNING, "Callable 작업이 거부되었습니다", e);
            throw e;
        }
    }

    /**
     * 스레드 풀을 우아하게 종료합니다.
     *
     * @param timeoutSeconds 종료 대기 시간 (초)
     * @return 정상 종료되면 true, 타임아웃되면 false
     */
    public boolean shutdown(long timeoutSeconds) {
        logger.info("ThreadPoolManager 종료를 시작합니다...");

        // 모니터링 중지
        stopMonitoring();

        // 스레드 풀 종료 시작
        threadPoolExecutor.shutdown();

        try {
            // 지정된 시간 동안 종료 대기
            if (threadPoolExecutor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                logger.info("ThreadPoolManager가 정상적으로 종료되었습니다");
                return true;
            } else {
                // 시간 초과 시 강제 종료
                logger.warning("종료 시간이 초과되어 강제 종료를 진행합니다");
                threadPoolExecutor.shutdownNow();

                // 강제 종료 후 추가 대기
                if (threadPoolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.info("ThreadPoolManager가 강제 종료되었습니다");
                } else {
                    logger.severe("ThreadPoolManager를 완전히 종료할 수 없습니다");
                }
                return false;
            }
        } catch (InterruptedException e) {
            // 인터럽트 발생 시 강제 종료
            threadPoolExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            logger.info("ThreadPoolManager 종료가 중단되었습니다");
            return false;
        }
    }

    /**
     * 스레드 풀을 즉시 강제 종료합니다.
     */
    public void shutdownNow() {
        logger.warning("ThreadPoolManager를 즉시 강제 종료합니다");

        // 모니터링 중지
        stopMonitoring();

        // 스레드 풀 즉시 종료
        threadPoolExecutor.shutdownNow();
    }

    /**
     * 현재 스레드 풀 상태를 반환합니다.
     *
     * @return 스레드 풀 상태 객체
     */
    public ThreadPoolStatus getStatus() {
        return new ThreadPoolStatus(
                threadPoolExecutor.getCorePoolSize(),              // 코어 스레드 수
                threadPoolExecutor.getMaximumPoolSize(),           // 최대 스레드 수
                threadPoolExecutor.getPoolSize(),                  // 현재 스레드 수
                threadPoolExecutor.getActiveCount(),               // 활성 스레드 수
                threadPoolExecutor.getQueue().size(),              // 큐에 대기 중인 작업 수
                threadPoolExecutor.getQueue().remainingCapacity(), // 큐 잔여 용량
                threadPoolExecutor.getCompletedTaskCount(),        // 완료된 작업 수
                threadPoolExecutor.getTaskCount(),                 // 총 작업 수
                totalTasksSubmitted.get(),                         // 제출된 작업 수
                totalTasksRejected.get(),                          // 거부된 작업 수
                threadPoolExecutor.isShutdown(),                   // 종료 상태
                threadPoolExecutor.isTerminated()                  // 완전 종료 상태
        );
    }

    /**
     * 성능 메트릭을 반환합니다.
     *
     * @return 성능 메트릭 객체
     */
    public PerformanceMetrics getPerformanceMetrics() {
        long completedTasks = totalTasksCompleted.get();
        long totalTime = totalExecutionTime.get();

        double averageExecutionTime = completedTasks > 0
                ? (double) totalTime / completedTasks / 1_000_000.0  // 나노초를 밀리초로 변환
                : 0.0;

        double rejectionRate = totalTasksSubmitted.get() > 0
                ? (double) totalTasksRejected.get() / totalTasksSubmitted.get() * 100.0
                : 0.0;

        return new PerformanceMetrics(
                totalTasksSubmitted.get(),
                completedTasks,
                totalTasksRejected.get(),
                averageExecutionTime,
                rejectionRate,
                calculateThroughput()
        );
    }

    /**
     * 스레드 풀 크기를 동적으로 조정합니다.
     *
     * @param corePoolSize 새로운 코어 스레드 수
     * @param maximumPoolSize 새로운 최대 스레드 수
     */
    public void adjustPoolSize(int corePoolSize, int maximumPoolSize) {
        // 입력 검증
        if (corePoolSize < 0) {
            throw new IllegalArgumentException("코어 스레드 수는 0 이상이어야 합니다: " + corePoolSize);
        }
        if (maximumPoolSize < corePoolSize) {
            throw new IllegalArgumentException("최대 스레드 수는 코어 스레드 수 이상이어야 합니다");
        }

        int oldCoreSize = threadPoolExecutor.getCorePoolSize();
        int oldMaxSize = threadPoolExecutor.getMaximumPoolSize();

        // 스레드 풀 크기 조정
        if (maximumPoolSize > oldMaxSize) {
            // 최대 크기를 먼저 증가
            threadPoolExecutor.setMaximumPoolSize(maximumPoolSize);
            threadPoolExecutor.setCorePoolSize(corePoolSize);
        } else {
            // 코어 크기를 먼저 감소
            threadPoolExecutor.setCorePoolSize(corePoolSize);
            threadPoolExecutor.setMaximumPoolSize(maximumPoolSize);
        }

        logger.info(String.format(
                "스레드 풀 크기 조정: 코어 %d->%d, 최대 %d->%d",
                oldCoreSize, corePoolSize, oldMaxSize, maximumPoolSize
        ));
    }

    /**
     * 모니터링을 시작합니다.
     */
    private void startMonitoring() {
        if (monitoringEnabled) {
            monitoringScheduler.scheduleAtFixedRate(
                    this::logThreadPoolStatus,
                    MONITOR_INTERVAL_SECONDS,
                    MONITOR_INTERVAL_SECONDS,
                    TimeUnit.SECONDS
            );
        }
    }

    /**
     * 모니터링을 중지합니다.
     */
    private void stopMonitoring() {
        monitoringEnabled = false;
        monitoringScheduler.shutdown();
        try {
            if (!monitoringScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                monitoringScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitoringScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 스레드 풀 상태를 로깅합니다.
     */
    private void logThreadPoolStatus() {
        if (!monitoringEnabled) {
            return;
        }

        try {
            ThreadPoolStatus status = getStatus();
            PerformanceMetrics metrics = getPerformanceMetrics();

            logger.info(String.format(
                    "스레드풀 상태 - 활성: %d/%d, 큐: %d, 완료: %d, 거부: %d, 평균실행시간: %.2fms, 처리량: %.2f작업/초",
                    status.getActiveCount(),
                    status.getCurrentPoolSize(),
                    status.getQueueSize(),
                    metrics.getCompletedTasks(),
                    metrics.getRejectedTasks(),
                    metrics.getAverageExecutionTime(),
                    metrics.getThroughput()
            ));

            // 경고 조건 체크
            checkAndLogWarnings(status, metrics);

        } catch (Exception e) {
            logger.log(Level.WARNING, "스레드 풀 상태 모니터링 중 오류", e);
        }
    }

    /**
     * 경고 조건을 체크하고 로깅합니다.
     */
    private void checkAndLogWarnings(ThreadPoolStatus status, PerformanceMetrics metrics) {
        // 큐 포화 경고
        if (status.getQueueRemainingCapacity() == 0 && status.getQueueSize() > 0) {
            logger.warning("작업 큐가 포화 상태입니다");
        }

        // 높은 거부율 경고
        if (metrics.getRejectionRate() > 5.0) { // 5% 이상
            logger.warning(String.format("높은 작업 거부율: %.2f%%", metrics.getRejectionRate()));
        }

        // 느린 처리 시간 경고
        if (metrics.getAverageExecutionTime() > 5000.0) { // 5초 이상
            logger.warning(String.format("느린 평균 처리 시간: %.2fms", metrics.getAverageExecutionTime()));
        }

        // 모든 스레드가 활성 상태인 경우 경고
        if (status.getActiveCount() == status.getMaximumPoolSize() && status.getQueueSize() > 0) {
            logger.warning("모든 스레드가 활성 상태이며 작업이 큐에 대기 중입니다");
        }
    }

    /**
     * 처리량을 계산합니다 (작업/초).
     */
    private double calculateThroughput() {
        // 간단한 처리량 계산 (최근 완료된 작업 기준)
        // 실제 구현에서는 더 정교한 시간 윈도우 기반 계산을 사용할 수 있습니다.
        long completedTasks = totalTasksCompleted.get();
        return completedTasks / 60.0; // 분당 작업 수를 초당으로 변환
    }

    /**
     * 풀 매개변수의 유효성을 검증합니다.
     */
    private void validatePoolParameters(int corePoolSize, int maximumPoolSize,
                                        long keepAliveTime, int queueCapacity) {
        if (corePoolSize < 0) {
            throw new IllegalArgumentException("코어 스레드 수는 0 이상이어야 합니다: " + corePoolSize);
        }
        if (maximumPoolSize < 1) {
            throw new IllegalArgumentException("최대 스레드 수는 1 이상이어야 합니다: " + maximumPoolSize);
        }
        if (maximumPoolSize < corePoolSize) {
            throw new IllegalArgumentException("최대 스레드 수는 코어 스레드 수 이상이어야 합니다");
        }
        if (keepAliveTime < 0) {
            throw new IllegalArgumentException("유지 시간은 0 이상이어야 합니다: " + keepAliveTime);
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("큐 용량은 0 이상이어야 합니다 (0은 무제한): " + queueCapacity);
        }
    }

    /**
     * 커스텀 스레드 팩토리 클래스
     */
    private static class CustomThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public CustomThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            // 의미있는 스레드 이름 생성
            Thread thread = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());

            // 데몬 스레드가 아닌 일반 스레드로 설정
            thread.setDaemon(false);

            // 기본 우선순위 설정
            thread.setPriority(Thread.NORM_PRIORITY);

            return thread;
        }
    }

    /**
     * 커스텀 거부 실행 핸들러 클래스
     */
    private class CustomRejectedExecutionHandler implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            // 거부 통계 업데이트
            totalTasksRejected.incrementAndGet();

            // 로깅
            logger.warning(String.format(
                    "작업이 거부되었습니다 - 활성 스레드: %d, 큐 크기: %d, 작업: %s",
                    executor.getActiveCount(),
                    executor.getQueue().size(),
                    r.getClass().getSimpleName()
            ));

            // CallerRuns 정책 적용 (호출한 스레드에서 직접 실행)
            if (!executor.isShutdown()) {
                logger.fine("호출 스레드에서 작업을 직접 실행합니다");
                r.run();
            }
        }
    }

    /**
     * 작업 실행 시간 측정을 위한 래퍼 클래스
     */
    private static class TaskWrapper implements Runnable {
        private final Runnable originalTask;
        private final long submissionTime;

        public TaskWrapper(Runnable originalTask) {
            this.originalTask = originalTask;
            this.submissionTime = System.nanoTime();
        }

        @Override
        public void run() {
            originalTask.run();
        }

        public long getSubmissionTime() {
            return submissionTime;
        }
    }

    /**
     * 스레드 풀 상태 정보를 담는 불변 클래스
     */
    public static class ThreadPoolStatus {
        private final int corePoolSize;
        private final int maximumPoolSize;
        private final int currentPoolSize;
        private final int activeCount;
        private final int queueSize;
        private final int queueRemainingCapacity;
        private final long completedTaskCount;
        private final long totalTaskCount;
        private final long submittedTaskCount;
        private final long rejectedTaskCount;
        private final boolean isShutdown;
        private final boolean isTerminated;

        public ThreadPoolStatus(int corePoolSize, int maximumPoolSize, int currentPoolSize,
                                int activeCount, int queueSize, int queueRemainingCapacity,
                                long completedTaskCount, long totalTaskCount, long submittedTaskCount,
                                long rejectedTaskCount, boolean isShutdown, boolean isTerminated) {
            this.corePoolSize = corePoolSize;
            this.maximumPoolSize = maximumPoolSize;
            this.currentPoolSize = currentPoolSize;
            this.activeCount = activeCount;
            this.queueSize = queueSize;
            this.queueRemainingCapacity = queueRemainingCapacity;
            this.completedTaskCount = completedTaskCount;
            this.totalTaskCount = totalTaskCount;
            this.submittedTaskCount = submittedTaskCount;
            this.rejectedTaskCount = rejectedTaskCount;
            this.isShutdown = isShutdown;
            this.isTerminated = isTerminated;
        }

        // Getter 메서드들
        public int getCorePoolSize() { return corePoolSize; }
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public int getCurrentPoolSize() { return currentPoolSize; }
        public int getActiveCount() { return activeCount; }
        public int getQueueSize() { return queueSize; }
        public int getQueueRemainingCapacity() { return queueRemainingCapacity; }
        public long getCompletedTaskCount() { return completedTaskCount; }
        public long getTotalTaskCount() { return totalTaskCount; }
        public long getSubmittedTaskCount() { return submittedTaskCount; }
        public long getRejectedTaskCount() { return rejectedTaskCount; }
        public boolean isShutdown() { return isShutdown; }
        public boolean isTerminated() { return isTerminated; }

        @Override
        public String toString() {
            return String.format(
                    "ThreadPoolStatus{코어=%d, 최대=%d, 현재=%d, 활성=%d, 큐=%d, 완료=%d, 제출=%d, 거부=%d}",
                    corePoolSize, maximumPoolSize, currentPoolSize, activeCount, queueSize,
                    completedTaskCount, submittedTaskCount, rejectedTaskCount
            );
        }
    }

    /**
     * 성능 메트릭 정보를 담는 불변 클래스
     */
    public static class PerformanceMetrics {
        private final long submittedTasks;
        private final long completedTasks;
        private final long rejectedTasks;
        private final double averageExecutionTime;
        private final double rejectionRate;
        private final double throughput;

        public PerformanceMetrics(long submittedTasks, long completedTasks, long rejectedTasks,
                                  double averageExecutionTime, double rejectionRate, double throughput) {
            this.submittedTasks = submittedTasks;
            this.completedTasks = completedTasks;
            this.rejectedTasks = rejectedTasks;
            this.averageExecutionTime = averageExecutionTime;
            this.rejectionRate = rejectionRate;
            this.throughput = throughput;
        }

        // Getter 메서드들
        public long getSubmittedTasks() { return submittedTasks; }
        public long getCompletedTasks() { return completedTasks; }
        public long getRejectedTasks() { return rejectedTasks; }
        public double getAverageExecutionTime() { return averageExecutionTime; }
        public double getRejectionRate() { return rejectionRate; }
        public double getThroughput() { return throughput; }

        @Override
        public String toString() {
            return String.format(
                    "PerformanceMetrics{제출=%d, 완료=%d, 거부=%d, 평균실행시간=%.2fms, 거부율=%.2f%%, 처리량=%.2f작업/초}",
                    submittedTasks, completedTasks, rejectedTasks, averageExecutionTime, rejectionRate, throughput
            );
        }
    }
}
