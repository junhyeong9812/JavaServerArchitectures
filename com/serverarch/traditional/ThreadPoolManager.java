package com.serverarch.traditional;

// Java 동시성 라이브러리
// java.util.concurrent.*: 동시성 처리를 위한 모든 클래스들
// ExecutorService: 비동기 작업 실행을 위한 인터페이스
// Executors: ExecutorService 생성을 위한 팩토리 클래스
// ThreadPoolExecutor: 스레드풀 실행기의 구체적인 구현 클래스
// ScheduledExecutorService: 지연 및 주기적 작업 실행을 위한 인터페이스
// ThreadFactory: 새로운 스레드 생성을 위한 팩토리 인터페이스
// RejectedExecutionHandler: 작업 거부 정책을 정의하는 인터페이스
// RejectedExecutionException: 작업 거부 시 발생하는 예외 클래스
// Future: 비동기 작업의 결과를 나타내는 인터페이스
// Callable: 결과를 반환하는 작업을 나타내는 인터페이스
// TimeUnit: 시간 단위를 나타내는 열거형
// BlockingQueue: 블로킹 큐 인터페이스
// LinkedBlockingQueue: 링크드 노드 기반 블로킹 큐 구현
import java.util.concurrent.*;

// Java 원자적 연산 라이브러리
// java.util.concurrent.atomic.*: 원자적 연산을 지원하는 클래스들
// AtomicLong: 원자적 long 연산을 지원하는 클래스
// AtomicInteger: 원자적 int 연산을 지원하는 클래스
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

// Java 로깅 라이브러리
// java.util.logging.Logger: 로그 메시지를 기록하는 클래스
// java.util.logging.Level: 로그 레벨을 정의하는 열거형
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
    // Logger.getLogger(): Logger 클래스의 정적 메서드로 로거 인스턴스 생성
    // ThreadPoolManager.class.getName(): 클래스 이름을 로거 이름으로 사용
    private static final Logger logger = Logger.getLogger(ThreadPoolManager.class.getName());

    // 기본 설정 상수들
    private static final int DEFAULT_CORE_POOL_SIZE = 10;          // 기본 코어 스레드 수
    private static final int DEFAULT_MAXIMUM_POOL_SIZE = 200;      // 기본 최대 스레드 수
    private static final long DEFAULT_KEEP_ALIVE_TIME = 60L;       // 기본 유지 시간 (초)
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;        // 기본 큐 용량
    private static final int MONITOR_INTERVAL_SECONDS = 30;        // 모니터링 간격 (초)

    // 스레드 풀 실행기
    // final: 한 번 초기화 후 변경 불가능
    // ThreadPoolExecutor: 스레드풀의 구체적인 구현 클래스
    private final ThreadPoolExecutor threadPoolExecutor;

    // 모니터링을 위한 스케줄러
    // ScheduledExecutorService: 주기적 작업 실행을 위한 인터페이스
    private final ScheduledExecutorService monitoringScheduler;

    // 백프레셔 정책
    // RejectedExecutionHandler: 작업 거부 시 실행할 정책 인터페이스
    private final RejectedExecutionHandler rejectedExecutionHandler;

    // 스레드 팩토리
    // ThreadFactory: 새로운 스레드 생성을 위한 팩토리 인터페이스
    private final ThreadFactory threadFactory;

    // 통계 수집을 위한 카운터들
    // AtomicLong: 멀티스레드 환경에서 안전한 long 값 관리
    private final AtomicLong totalTasksSubmitted = new AtomicLong(0);      // 제출된 총 작업 수
    private final AtomicLong totalTasksCompleted = new AtomicLong(0);      // 완료된 총 작업 수
    private final AtomicLong totalTasksRejected = new AtomicLong(0);       // 거부된 총 작업 수
    private final AtomicLong totalExecutionTime = new AtomicLong(0);       // 총 실행 시간 (나노초)

    // 모니터링 상태
    // volatile: 멀티스레드 환경에서 변수 가시성 보장
    private volatile boolean monitoringEnabled = true;

    /**
     * 기본 설정으로 ThreadPoolManager를 생성합니다.
     */
    public ThreadPoolManager() {
        // this(): 같은 클래스의 다른 생성자 호출
        // 기본 설정값들을 사용하여 상세 생성자 호출
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
        // new CustomThreadFactory(): 내부 클래스의 생성자 호출
        this.threadFactory = new CustomThreadFactory("ThreadedServer-Worker");

        // 백프레셔 정책 생성 (CallerRuns + 통계 수집)
        this.rejectedExecutionHandler = new CustomRejectedExecutionHandler();

        // 작업 큐 생성 (용량 제한이 있는 LinkedBlockingQueue)
        // BlockingQueue<Runnable>: 작업을 저장하는 블로킹 큐
        // queueCapacity > 0: 용량 제한 여부 확인
        // ? : : 삼항 연산자
        BlockingQueue<Runnable> workQueue = queueCapacity > 0
                ? new LinkedBlockingQueue<>(queueCapacity)  // 용량 제한 큐
                : new LinkedBlockingQueue<>(); // 무제한 큐

        // ThreadPoolExecutor 생성
        // 익명 클래스로 확장하여 afterExecute 메서드 오버라이드
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
                // super.afterExecute(): 부모 클래스의 메서드 호출
                super.afterExecute(r, t);

                // totalTasksCompleted.incrementAndGet(): 원자적 증가 연산
                totalTasksCompleted.incrementAndGet();

                // 작업 실행 시간 계산 (TaskWrapper를 사용한 경우)
                // instanceof: 객체 타입 확인 연산자
                if (r instanceof TaskWrapper) {
                    // 타입 캐스팅
                    TaskWrapper wrapper = (TaskWrapper) r;

                    // System.nanoTime(): 현재 시간을 나노초로 반환
                    // wrapper.getSubmissionTime(): 작업 제출 시간 반환
                    long executionTime = System.nanoTime() - wrapper.getSubmissionTime();

                    // totalExecutionTime.addAndGet(): 원자적 덧셈 연산
                    totalExecutionTime.addAndGet(executionTime);
                }

                // 오류 발생 시 로깅
                if (t != null) {
                    // logger.log(): 로그 출력 메서드
                    // Level.WARNING: 경고 레벨
                    logger.log(Level.WARNING, "스레드 풀 작업 실행 중 오류 발생", t);
                }
            }
        };

        // 코어 스레드들이 유휴 상태에서도 살아있도록 설정
        // threadPoolExecutor.allowCoreThreadTimeOut(): 코어 스레드 타임아웃 설정
        // false: 코어 스레드는 타임아웃되지 않음
        this.threadPoolExecutor.allowCoreThreadTimeOut(false);

        // 모니터링 스케줄러 생성
        // Executors.newSingleThreadScheduledExecutor(): 단일 스레드 스케줄러 생성
        this.monitoringScheduler = Executors.newSingleThreadScheduledExecutor(
                new CustomThreadFactory("ThreadPoolMonitor")
        );

        // 주기적 모니터링 시작
        startMonitoring();

        // 생성 완료 로그
        // String.format(): 형식화된 문자열 생성
        // unit.toString().toLowerCase(): 시간 단위를 소문자 문자열로 변환
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
            // new IllegalArgumentException(): 잘못된 인수 예외 생성
            throw new IllegalArgumentException("작업이 null입니다");
        }

        // 제출 통계 업데이트
        totalTasksSubmitted.incrementAndGet();

        // 작업을 TaskWrapper로 감싸서 실행 시간 측정
        TaskWrapper wrappedTask = new TaskWrapper(task);

        try {
            // 스레드 풀에 작업 제출
            // threadPoolExecutor.submit(): 작업을 스레드풀에 제출하고 Future 반환
            return threadPoolExecutor.submit(wrappedTask);
        } catch (RejectedExecutionException e) {
            // 작업 거부 시 통계 업데이트
            totalTasksRejected.incrementAndGet();
            logger.log(Level.WARNING, "작업이 거부되었습니다", e);

            // throw: 예외를 다시 던짐
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
        // <T>: 제네릭 타입 매개변수
        // Callable<T>: 결과를 반환하는 작업 인터페이스
        // Future<T>: 비동기 작업의 결과를 나타내는 인터페이스

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
        // threadPoolExecutor.shutdown(): 새로운 작업 수락 중지, 기존 작업은 완료 대기
        threadPoolExecutor.shutdown();

        try {
            // 지정된 시간 동안 종료 대기
            // threadPoolExecutor.awaitTermination(): 모든 작업 완료까지 대기
            // TimeUnit.SECONDS: 시간 단위 지정
            if (threadPoolExecutor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                logger.info("ThreadPoolManager가 정상적으로 종료되었습니다");
                return true;
            } else {
                // 시간 초과 시 강제 종료
                logger.warning("종료 시간이 초과되어 강제 종료를 진행합니다");

                // threadPoolExecutor.shutdownNow(): 실행 중인 작업 인터럽트하고 즉시 종료
                threadPoolExecutor.shutdownNow();

                // 강제 종료 후 추가 대기
                if (threadPoolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.info("ThreadPoolManager가 강제 종료되었습니다");
                } else {
                    // Level.SEVERE: 심각한 오류 레벨
                    logger.severe("ThreadPoolManager를 완전히 종료할 수 없습니다");
                }
                return false;
            }
        } catch (InterruptedException e) {
            // 인터럽트 발생 시 강제 종료
            threadPoolExecutor.shutdownNow();

            // Thread.currentThread().interrupt(): 현재 스레드의 인터럽트 상태 복원
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
        // new ThreadPoolStatus(): 내부 클래스의 생성자 호출
        // threadPoolExecutor의 다양한 상태 정보를 수집하여 전달
        return new ThreadPoolStatus(
                // threadPoolExecutor.getCorePoolSize(): 코어 스레드 수 반환
                threadPoolExecutor.getCorePoolSize(),

                // threadPoolExecutor.getMaximumPoolSize(): 최대 스레드 수 반환
                threadPoolExecutor.getMaximumPoolSize(),

                // threadPoolExecutor.getPoolSize(): 현재 스레드 수 반환
                threadPoolExecutor.getPoolSize(),

                // threadPoolExecutor.getActiveCount(): 활성 스레드 수 반환
                threadPoolExecutor.getActiveCount(),

                // threadPoolExecutor.getQueue(): 작업 큐 반환
                // .size(): 큐에 대기 중인 작업 수
                threadPoolExecutor.getQueue().size(),

                // .remainingCapacity(): 큐의 남은 용량
                threadPoolExecutor.getQueue().remainingCapacity(),

                // threadPoolExecutor.getCompletedTaskCount(): 완료된 작업 수 반환
                threadPoolExecutor.getCompletedTaskCount(),

                // threadPoolExecutor.getTaskCount(): 총 작업 수 반환
                threadPoolExecutor.getTaskCount(),

                // totalTasksSubmitted.get(): 원자적 값 읽기
                totalTasksSubmitted.get(),
                totalTasksRejected.get(),

                // threadPoolExecutor.isShutdown(): 종료 시작 여부 반환
                threadPoolExecutor.isShutdown(),

                // threadPoolExecutor.isTerminated(): 완전 종료 여부 반환
                threadPoolExecutor.isTerminated()
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

        // 평균 실행 시간 계산
        // completedTasks > 0: 0으로 나누기 방지
        // (double): 정수를 실수로 형변환
        // / 1_000_000.0: 나노초를 밀리초로 변환 (1_000_000은 숫자 구분자 사용)
        double averageExecutionTime = completedTasks > 0
                ? (double) totalTime / completedTasks / 1_000_000.0
                : 0.0;

        // 거부율 계산
        // * 100.0: 백분율로 변환
        double rejectionRate = totalTasksSubmitted.get() > 0
                ? (double) totalTasksRejected.get() / totalTasksSubmitted.get() * 100.0
                : 0.0;

        // new PerformanceMetrics(): 성능 메트릭 객체 생성
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

        // 현재 크기 저장 (로깅용)
        int oldCoreSize = threadPoolExecutor.getCorePoolSize();
        int oldMaxSize = threadPoolExecutor.getMaximumPoolSize();

        // 스레드 풀 크기 조정
        // 순서가 중요함: 크기를 늘릴 때와 줄일 때 순서가 다름
        if (maximumPoolSize > oldMaxSize) {
            // 최대 크기를 먼저 증가
            // threadPoolExecutor.setMaximumPoolSize(): 최대 스레드 수 설정
            threadPoolExecutor.setMaximumPoolSize(maximumPoolSize);

            // threadPoolExecutor.setCorePoolSize(): 코어 스레드 수 설정
            threadPoolExecutor.setCorePoolSize(corePoolSize);
        } else {
            // 코어 크기를 먼저 감소
            threadPoolExecutor.setCorePoolSize(corePoolSize);
            threadPoolExecutor.setMaximumPoolSize(maximumPoolSize);
        }

        // 변경 로그
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
            // monitoringScheduler.scheduleAtFixedRate(): 고정 간격으로 주기적 실행
            // this::logThreadPoolStatus: 메서드 참조 (람다 표현식의 축약형)
            monitoringScheduler.scheduleAtFixedRate(
                    this::logThreadPoolStatus,      // 실행할 작업
                    MONITOR_INTERVAL_SECONDS,        // 초기 지연 시간
                    MONITOR_INTERVAL_SECONDS,        // 실행 간격
                    TimeUnit.SECONDS                 // 시간 단위
            );
        }
    }

    /**
     * 모니터링을 중지합니다.
     */
    private void stopMonitoring() {
        monitoringEnabled = false;

        // monitoringScheduler.shutdown(): 스케줄러 종료
        monitoringScheduler.shutdown();

        try {
            // monitoringScheduler.awaitTermination(): 종료 대기
            if (!monitoringScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                // 시간 초과 시 강제 종료
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

            // 상세한 상태 정보 로깅
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
        // status.getQueueRemainingCapacity(): 큐의 남은 용량
        // status.getQueueSize(): 큐에 있는 작업 수
        if (status.getQueueRemainingCapacity() == 0 && status.getQueueSize() > 0) {
            logger.warning("작업 큐가 포화 상태입니다");
        }

        // 높은 거부율 경고
        // metrics.getRejectionRate(): 거부율 반환
        if (metrics.getRejectionRate() > 5.0) { // 5% 이상
            // String.format() + %.2f: 소수점 둘째 자리까지 표시
            logger.warning(String.format("높은 작업 거부율: %.2f%%", metrics.getRejectionRate()));
        }

        // 느린 처리 시간 경고
        // metrics.getAverageExecutionTime(): 평균 실행 시간 반환
        if (metrics.getAverageExecutionTime() > 5000.0) { // 5초 이상
            logger.warning(String.format("느린 평균 처리 시간: %.2fms", metrics.getAverageExecutionTime()));
        }

        // 모든 스레드가 활성 상태인 경우 경고
        // status.getActiveCount(): 현재 활성 스레드 수
        // status.getMaximumPoolSize(): 최대 스레드 수
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

        // 분당 작업 수를 초당으로 변환
        // / 60.0: 60초로 나누어 초당 처리량 계산
        return completedTasks / 60.0;
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
     *
     * static: 외부 클래스의 인스턴스 없이도 생성 가능한 정적 중첩 클래스
     */
    private static class CustomThreadFactory implements ThreadFactory {
        // AtomicInteger: 원자적 정수 연산을 지원하는 클래스
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public CustomThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            // 의미있는 스레드 이름 생성
            // threadNumber.getAndIncrement(): 현재 값을 반환하고 1 증가
            Thread thread = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());

            // 데몬 스레드가 아닌 일반 스레드로 설정
            // thread.setDaemon(): 데몬 스레드 여부 설정
            // false: 일반 스레드 (JVM이 이 스레드들의 완료를 기다림)
            thread.setDaemon(false);

            // 기본 우선순위 설정
            // thread.setPriority(): 스레드 우선순위 설정
            // Thread.NORM_PRIORITY: 일반 우선순위 상수 (5)
            thread.setPriority(Thread.NORM_PRIORITY);

            return thread;
        }
    }

    /**
     * 커스텀 거부 실행 핸들러 클래스
     *
     * 내부 클래스로 구현하여 외부 클래스의 인스턴스 변수에 접근 가능
     */
    private class CustomRejectedExecutionHandler implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            // 거부 통계 업데이트
            totalTasksRejected.incrementAndGet();

            // 로깅
            // executor.getActiveCount(): 실행기의 활성 스레드 수
            // executor.getQueue().size(): 실행기 큐의 크기
            // r.getClass().getSimpleName(): 작업 클래스의 단순 이름
            logger.warning(String.format(
                    "작업이 거부되었습니다 - 활성 스레드: %d, 큐 크기: %d, 작업: %s",
                    executor.getActiveCount(),
                    executor.getQueue().size(),
                    r.getClass().getSimpleName()
            ));

            // CallerRuns 정책 적용 (호출한 스레드에서 직접 실행)
            // executor.isShutdown(): 실행기가 종료 중인지 확인
            if (!executor.isShutdown()) {
                // logger.fine(): FINE 레벨 로그 (디버그용)
                logger.fine("호출 스레드에서 작업을 직접 실행합니다");

                // r.run(): 작업을 현재 스레드에서 직접 실행
                r.run();
            }
        }
    }

    /**
     * 작업 실행 시간 측정을 위한 래퍼 클래스
     *
     * static: 외부 클래스의 인스턴스 없이도 생성 가능한 정적 중첩 클래스
     */
    private static class TaskWrapper implements Runnable {
        private final Runnable originalTask;
        private final long submissionTime;

        public TaskWrapper(Runnable originalTask) {
            this.originalTask = originalTask;

            // System.nanoTime(): 현재 시간을 나노초로 반환 (고정밀 시간 측정용)
            this.submissionTime = System.nanoTime();
        }

        @Override
        public void run() {
            // 원본 작업 실행
            originalTask.run();
        }

        public long getSubmissionTime() {
            return submissionTime;
        }
    }

    /**
     * 스레드 풀 상태 정보를 담는 불변 클래스
     *
     * static: 외부 클래스의 인스턴스 없이도 생성 가능한 정적 중첩 클래스
     */
    public static class ThreadPoolStatus {
        // 모든 필드를 final로 선언하여 불변성 보장
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

        // Getter 메서드들 - 불변 객체이므로 필드 값 반환만 수행
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
            // String.format(): 형식화된 문자열 생성
            return String.format(
                    "ThreadPoolStatus{코어=%d, 최대=%d, 현재=%d, 활성=%d, 큐=%d, 완료=%d, 제출=%d, 거부=%d}",
                    corePoolSize, maximumPoolSize, currentPoolSize, activeCount, queueSize,
                    completedTaskCount, submittedTaskCount, rejectedTaskCount
            );
        }
    }

    /**
     * 성능 메트릭 정보를 담는 불변 클래스
     *
     * static: 외부 클래스의 인스턴스 없이도 생성 가능한 정적 중첩 클래스
     */
    public static class PerformanceMetrics {
        // 모든 필드를 final로 선언하여 불변성 보장
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

        // Getter 메서드들 - 불변 객체이므로 필드 값 반환만 수행
        public long getSubmittedTasks() { return submittedTasks; }
        public long getCompletedTasks() { return completedTasks; }
        public long getRejectedTasks() { return rejectedTasks; }
        public double getAverageExecutionTime() { return averageExecutionTime; }
        public double getRejectionRate() { return rejectionRate; }
        public double getThroughput() { return throughput; }

        @Override
        public String toString() {
            // String.format(): 형식화된 문자열 생성
            // %.2f: 소수점 둘째 자리까지 표시
            return String.format(
                    "PerformanceMetrics{제출=%d, 완료=%d, 거부=%d, 평균실행시간=%.2fms, 거부율=%.2f%%, 처리량=%.2f작업/초}",
                    submittedTasks, completedTasks, rejectedTasks, averageExecutionTime, rejectionRate, throughput
            );
        }
    }
}