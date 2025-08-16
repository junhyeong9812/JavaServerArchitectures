package server.hybrid;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * 적응형 스레드풀 - 하이브리드 서버의 핵심 컴포넌트
 *
 * 기능:
 * 1. 부하에 따른 동적 스레드 수 조정
 * 2. 작업 우선순위 지원
 * 3. 성능 모니터링 및 튜닝
 * 4. 스레드 이름 관리
 * 5. 백프레셔(Backpressure) 처리
 *
 * 일반 ThreadPoolExecutor와의 차이점:
 * - 실시간 부하 모니터링
 * - 큐 길이 기반 스레드 수 조정
 * - 작업 처리 시간 추적
 * - 자동 튜닝 기능
 */
public class AdaptiveThreadPool extends ThreadPoolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveThreadPool.class);

    // === 스레드풀 설정 ===
    private final String poolName;                       // 스레드풀 이름
    private final int minPoolSize;                       // 최소 스레드 수
    private final int maxPoolSize;                       // 최대 스레드 수
    private final long keepAliveTimeSeconds;             // 유휴 스레드 생존 시간

    // === 적응형 조정 변수 ===
    private volatile boolean adaptiveEnabled = true;     // 적응형 조정 활성화
    private volatile int targetQueueSize = 10;           // 목표 큐 크기
    private volatile double adjustmentFactor = 0.1;      // 조정 계수 (0.1 = 10%)

    // === 모니터링 ===
    private final AtomicLong submittedTasks = new AtomicLong(0);      // 제출된 작업 수
    private final AtomicLong completedTasks = new AtomicLong(0);      // 완료된 작업 수
    private final AtomicLong rejectedTasks = new AtomicLong(0);       // 거부된 작업 수
    private final AtomicLong totalExecutionTime = new AtomicLong(0);  // 총 실행 시간 (나노초)

    // === 스케줄링 ===
    private final ScheduledExecutorService monitorExecutor;          // 모니터링 스케줄러
    private final AtomicReference<ThreadPoolStats> lastStats = new AtomicReference<>();

    // === 우선순위 큐 ===
    private final PriorityBlockingQueue<Runnable> priorityQueue;     // 우선순위 작업 큐

    /**
     * 적응형 스레드풀 생성자
     *
     * @param poolName 스레드풀 이름
     * @param minPoolSize 최소 스레드 수
     * @param maxPoolSize 최대 스레드 수
     * @param keepAliveTimeSeconds 유휴 스레드 생존 시간(초)
     */
    public AdaptiveThreadPool(String poolName, int minPoolSize, int maxPoolSize, long keepAliveTimeSeconds) {
        // 부모 클래스(ThreadPoolExecutor) 생성자 호출
        super(
                minPoolSize,                                    // 코어 스레드 수
                maxPoolSize,                                    // 최대 스레드 수
                keepAliveTimeSeconds,                           // 유휴 시간
                TimeUnit.SECONDS,                               // 시간 단위
                new PriorityBlockingQueue<>(1000),              // 우선순위 큐 (초기 용량 1000)
                new AdaptiveThreadFactory(poolName),            // 커스텀 스레드 팩토리
                new AdaptiveRejectedExecutionHandler()          // 커스텀 거부 핸들러
        );

        this.poolName = poolName;
        this.minPoolSize = minPoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTimeSeconds = keepAliveTimeSeconds;

        // 큐 참조 저장 (우선순위 기능 활용)
        this.priorityQueue = (PriorityBlockingQueue<Runnable>) getQueue();

        // allowCoreThreadTimeOut(true) - 코어 스레드도 유휴시 종료 허용
        allowCoreThreadTimeOut(true);

        // 모니터링 스케줄러 초기화
        this.monitorExecutor = Executors.newScheduledThreadPool(1,
                r -> new Thread(r, poolName + "-Monitor"));

        // 5초마다 스레드풀 상태 모니터링 및 조정
        this.monitorExecutor.scheduleAtFixedRate(
                this::monitorAndAdjust,  // 실행할 메서드
                5,                       // 초기 지연 (초)
                5,                       // 실행 간격 (초)
                TimeUnit.SECONDS         // 시간 단위
        );

        logger.info("AdaptiveThreadPool 생성 완료 - 이름: {}, 크기: {}-{}, 유휴시간: {}초",
                poolName, minPoolSize, maxPoolSize, keepAliveTimeSeconds);
    }

    /**
     * 작업 제출 (우선순위 지원)
     *
     * @param task 실행할 작업
     * @param priority 우선순위 (높을수록 먼저 실행)
     * @return Future 객체
     */
    public Future<?> submit(Runnable task, int priority) {
        submittedTasks.incrementAndGet();

        // PriorityTask로 래핑하여 우선순위 적용
        PriorityTask priorityTask = new PriorityTask(task, priority);

        try {
            return super.submit(priorityTask);
        } catch (RejectedExecutionException e) {
            rejectedTasks.incrementAndGet();
            logger.warn("작업 거부 - 스레드풀: {}, 우선순위: {}", poolName, priority);
            throw e;
        }
    }

    /**
     * 일반 작업 제출 (기본 우선순위)
     */
    @Override
    public Future<?> submit(Runnable task) {
        return submit(task, 0); // 기본 우선순위 0
    }

    /**
     * Callable 작업 제출 (우선순위 지원)
     */
    public <T> Future<T> submit(Callable<T> task, int priority) {
        submittedTasks.incrementAndGet();

        // Callable을 Runnable로 변환 후 우선순위 적용
        FutureTask<T> futureTask = new FutureTask<>(task);
        PriorityTask priorityTask = new PriorityTask(futureTask, priority);

        try {
            execute(priorityTask);
            return futureTask;
        } catch (RejectedExecutionException e) {
            rejectedTasks.incrementAndGet();
            throw e;
        }
    }

    /**
     * 스레드풀 모니터링 및 적응형 조정
     * 스케줄러에 의해 주기적으로 실행됨
     */
    private void monitorAndAdjust() {
        try {
            // 현재 스레드풀 상태 수집
            ThreadPoolStats currentStats = collectStats();
            lastStats.set(currentStats);

            logger.debug("스레드풀 상태 - {}", currentStats);

            // 적응형 조정이 활성화된 경우에만 실행
            if (adaptiveEnabled) {
                adjustPoolSize(currentStats);
            }

        } catch (Exception e) {
            logger.error("스레드풀 모니터링 중 오류", e);
        }
    }

    /**
     * 스레드풀 크기 적응형 조정
     *
     * @param stats 현재 통계
     */
    private void adjustPoolSize(ThreadPoolStats stats) {
        int currentPoolSize = getPoolSize();
        int currentQueueSize = getQueue().size();
        double utilization = (double) getActiveCount() / currentPoolSize;

        // 조정 필요 여부 판단
        boolean needIncrease = shouldIncreasePoolSize(stats, currentQueueSize, utilization);
        boolean needDecrease = shouldDecreasePoolSize(stats, currentQueueSize, utilization);

        if (needIncrease && currentPoolSize < maxPoolSize) {
            // 스레드 수 증가
            int newSize = Math.min(maxPoolSize,
                    currentPoolSize + (int) Math.max(1, currentPoolSize * adjustmentFactor));

            setCorePoolSize(newSize);
            setMaximumPoolSize(Math.max(newSize, getMaximumPoolSize()));

            logger.info("스레드풀 크기 증가 - {}: {} -> {}, 큐크기: {}, 사용률: {:.2f}%",
                    poolName, currentPoolSize, newSize, currentQueueSize, utilization * 100);

        } else if (needDecrease && currentPoolSize > minPoolSize) {
            // 스레드 수 감소
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
        // 1. 큐가 목표 크기를 초과한 경우
        if (queueSize > targetQueueSize) {
            return true;
        }

        // 2. 스레드 사용률이 80% 이상인 경우
        if (utilization > 0.8) {
            return true;
        }

        // 3. 평균 작업 대기 시간이 증가하는 경우
        if (stats.getAverageWaitTime() > 100) { // 100ms 초과
            return true;
        }

        return false;
    }

    /**
     * 스레드풀 크기 감소 필요 여부 판단
     */
    private boolean shouldDecreasePoolSize(ThreadPoolStats stats, int queueSize, double utilization) {
        // 1. 큐가 비어있고 사용률이 낮은 경우
        if (queueSize == 0 && utilization < 0.3) {
            return true;
        }

        // 2. 평균 작업 대기 시간이 매우 낮은 경우
        if (stats.getAverageWaitTime() < 10) { // 10ms 미만
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

        return totalExecutionTime.get() / completed / 1_000_000; // 나노초 -> 밀리초
    }

    /**
     * 평균 대기 시간 계산 (밀리초)
     * 큐 크기와 처리 속도 기반 추정
     */
    private long calculateAverageWaitTime() {
        int queueSize = getQueue().size();
        if (queueSize == 0) return 0;

        long avgExecutionTime = calculateAverageExecutionTime();
        if (avgExecutionTime == 0) return 0;

        // 단순 추정: 큐 크기 * 평균 실행 시간 / 활성 스레드 수
        int activeThreads = Math.max(1, getActiveCount());
        return (queueSize * avgExecutionTime) / activeThreads;
    }

    /**
     * 처리량 계산 (작업/초)
     */
    private double calculateThroughput() {
        long completed = completedTasks.get();
        if (completed == 0) return 0.0;

        // 스레드풀 생성 후 경과 시간 기반 계산 (단순화)
        // 실제로는 더 정교한 시간 추적이 필요
        return completed / 60.0; // 임시로 분당 처리량을 초당으로 변환
    }

    /**
     * 작업 실행 전 후처리
     */
    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);

        // PriorityTask인 경우 원본 작업 추출
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
            // 실행 시간 추적
            if (r instanceof PriorityTask) {
                PriorityTask priorityTask = (PriorityTask) r;
                long executionTime = System.nanoTime() - priorityTask.getStartTime();
                totalExecutionTime.addAndGet(executionTime);
            }

            // 완료된 작업 수 증가
            completedTasks.incrementAndGet();

        } catch (Exception e) {
            logger.warn("작업 후처리 중 오류", e);
        }

        // 예외 발생시 로깅
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

        // 모니터링 스케줄러 종료
        monitorExecutor.shutdown();

        // 부모 클래스 종료 메서드 호출
        super.shutdown();

        try {
            // 종료 대기 (최대 30초)
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
     * 우선순위 작업 래퍼 클래스
     * Comparable 인터페이스 구현으로 우선순위 큐에서 정렬
     */
    private static class PriorityTask implements Runnable, Comparable<PriorityTask> {
        private final Runnable task;            // 원본 작업
        private final int priority;             // 우선순위
        private final long createdTime;         // 생성 시간
        private volatile long startTime;        // 실행 시작 시간

        public PriorityTask(Runnable task, int priority) {
            this.task = task;
            this.priority = priority;
            this.createdTime = System.nanoTime();
        }

        @Override
        public void run() {
            task.run();
        }

        @Override
        public int compareTo(PriorityTask other) {
            // 우선순위가 높을수록 먼저 실행 (내림차순)
            int result = Integer.compare(other.priority, this.priority);

            // 우선순위가 같으면 생성 시간이 빠른 것을 먼저 (FIFO)
            if (result == 0) {
                result = Long.compare(this.createdTime, other.createdTime);
            }

            return result;
        }

        // Getters
        public int getPriority() { return priority; }
        public long getCreatedTime() { return createdTime; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
    }

    /**
     * 커스텀 스레드 팩토리
     * 스레드 이름과 속성을 적절히 설정
     */
    private static class AdaptiveThreadFactory implements ThreadFactory {
        private final String poolName;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final ThreadGroup group;

        AdaptiveThreadFactory(String poolName) {
            this.poolName = poolName;
            SecurityManager s = System.getSecurityManager();
            this.group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, poolName + "-" + threadNumber.getAndIncrement(), 0);

            // 데몬 스레드로 설정 (JVM 종료를 방해하지 않음)
            if (t.isDaemon()) {
                t.setDaemon(false);
            }

            // 일반 우선순위로 설정
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }

            return t;
        }
    }

    /**
     * 커스텀 거부 핸들러
     * 작업이 거부될 때의 처리 방식 정의
     */
    private static class AdaptiveRejectedExecutionHandler implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            // 백프레셔 처리: 큐가 가득 찬 경우 제출 스레드에서 직접 실행
            if (!executor.isShutdown()) {
                try {
                    // CallerRunsPolicy와 유사하지만 로깅 추가
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