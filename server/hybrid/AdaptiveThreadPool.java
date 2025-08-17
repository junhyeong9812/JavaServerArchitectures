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
    // ThreadPoolExecutor를 상속받아 기본 스레드풀 기능을 확장
    // 상속을 통해 기존의 검증된 스레드풀 로직을 재사용하면서 필요한 부분만 커스터마이징

    // SLF4J 로거 인스턴스 생성 - 로그 출력을 위한 표준 로깅 프레임워크
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveThreadPool.class);
    // static final로 선언하여 클래스 레벨에서 하나의 로거만 사용, 메모리 효율성 향상

    // === 스레드풀 설정 ===
    // 스레드풀의 기본 설정값들을 저장하는 불변 필드들
    private final String poolName; // 스레드풀 이름 - 로깅과 모니터링에서 구분용
    private final int minPoolSize; // 최소 스레드 수 - 항상 유지할 기본 스레드 개수
    private final int maxPoolSize; // 최대 스레드 수 - 부하 증가시 확장 가능한 최대 한계
    private final long keepAliveTimeSeconds; // 스레드 유지 시간(초) - 유휴 스레드가 종료되기 전 대기 시간
    // final로 선언하여 생성 후 변경 불가능하도록 보장, 설정 안정성 확보

    // === 적응형 조정 변수 ===
    // 런타임에 동적으로 스레드풀 크기를 조정하기 위한 변수들
    private volatile boolean adaptiveEnabled = true; // 적응형 조정 활성화 여부
    // volatile 키워드로 멀티스레드 환경에서 변수 변경의 가시성 보장
    private volatile int targetQueueSize = 10; // 목표 큐 크기 - 이 값을 기준으로 스레드 수 조정
    private volatile double adjustmentFactor = 0.1; // 조정 계수 - 한 번에 변경할 스레드 수의 비율
    // volatile 사용으로 다른 스레드에서 설정 변경시 즉시 반영

    // === 모니터링 ===
    // 스레드풀 성능과 상태를 추적하기 위한 원자적 카운터들
    private final AtomicLong submittedTasks = new AtomicLong(0); // 제출된 총 작업 수
    private final AtomicLong completedTasks = new AtomicLong(0); // 완료된 총 작업 수
    private final AtomicLong rejectedTasks = new AtomicLong(0); // 거부된 총 작업 수
    private final AtomicLong totalExecutionTime = new AtomicLong(0); // 총 실행 시간(나노초)
    // AtomicLong 사용으로 멀티스레드 환경에서 동시 접근시에도 데이터 무결성 보장
    // 락 없이도 원자적 연산 가능하여 성능상 이점

    // === 스케줄링 ===
    // 주기적인 모니터링과 조정 작업을 위한 스케줄러
    private final ScheduledExecutorService monitorExecutor; // 모니터링 전용 스케줄러
    private final AtomicReference<ThreadPoolStats> lastStats = new AtomicReference<>();
    // AtomicReference로 ThreadPoolStats 객체를 원자적으로 교체 가능
    // 통계 정보 읽기와 쓰기가 동시에 발생해도 일관성 유지

    // === 우선순위 큐 ===
    private final PriorityBlockingQueue<Runnable> priorityQueue;
    // 작업의 우선순위에 따라 실행 순서를 결정하는 큐
    // BlockingQueue 인터페이스로 스레드 안전성과 블로킹 기능 제공

    /**
     * 적응형 스레드풀 생성자
     */
    public AdaptiveThreadPool(String poolName, int minPoolSize, int maxPoolSize, long keepAliveTimeSeconds) {
        // 부모 클래스 ThreadPoolExecutor의 생성자 호출
        super(
                minPoolSize, // corePoolSize - 기본 유지할 스레드 수
                maxPoolSize, // maximumPoolSize - 최대 스레드 수
                keepAliveTimeSeconds, // keepAliveTime - 유휴 스레드 유지 시간
                TimeUnit.SECONDS, // 시간 단위를 초로 지정
                new PriorityBlockingQueue<>(1000), // 작업 큐 - 우선순위 기반 블로킹 큐, 초기 용량 1000
                new AdaptiveThreadFactory(poolName), // 스레드 생성 팩토리 - 커스텀 네이밍 적용
                new AdaptiveRejectedExecutionHandler() // 거부 정책 - 큐가 가득 찰 때의 처리 방식
        );
        // ThreadPoolExecutor 생성자에 모든 필수 파라미터 전달하여 기본 스레드풀 설정

        // 생성자 파라미터를 인스턴스 변수에 저장
        this.poolName = poolName; // 풀 이름 저장 - 로깅과 모니터링에 사용
        this.minPoolSize = minPoolSize; // 최소 크기 저장 - 동적 조정시 하한선으로 사용
        this.maxPoolSize = maxPoolSize; // 최대 크기 저장 - 동적 조정시 상한선으로 사용
        this.keepAliveTimeSeconds = keepAliveTimeSeconds; // 유지 시간 저장

        // 부모 클래스에서 생성된 큐를 PriorityBlockingQueue로 캐스팅하여 참조 저장
        this.priorityQueue = (PriorityBlockingQueue<Runnable>) getQueue();
        // getQueue()는 ThreadPoolExecutor의 메서드로 작업 큐 반환
        // 우선순위 기능 사용을 위해 구체적인 타입으로 캐스팅

        // 코어 스레드도 유휴 시간이 지나면 종료되도록 설정
        allowCoreThreadTimeOut(true);
        // 기본적으로 코어 스레드는 계속 유지되지만, 리소스 효율성을 위해 타임아웃 허용
        // 부하가 적을 때 불필요한 스레드 자원 해제 가능

        // 모니터링 전용 스케줄러 생성 - 단일 스레드로 충분
        this.monitorExecutor = Executors.newScheduledThreadPool(1,
                r -> new Thread(r, poolName + "-Monitor")); // 람다식으로 스레드 이름 지정
        // newScheduledThreadPool 사용으로 주기적 작업 실행 가능
        // 별도 스레드에서 모니터링하여 메인 스레드풀에 영향 없음

        // 5초마다 모니터링 및 조정 작업 실행
        this.monitorExecutor.scheduleAtFixedRate(
                this::monitorAndAdjust, // 실행할 메서드 참조
                5, // 초기 지연 시간(초)
                5, // 실행 간격(초)
                TimeUnit.SECONDS // 시간 단위
        );
        // scheduleAtFixedRate로 정확한 간격으로 반복 실행
        // 메서드 참조(::) 사용으로 간결한 코드 작성

        // 생성 완료 로그 출력 - 설정 정보와 함께 기록
        logger.info("적응형 스레드풀 생성 완료 - 이름: {}, 크기: {}-{}, 유휴시간: {}초",
                poolName, minPoolSize, maxPoolSize, keepAliveTimeSeconds);
        // SLF4J의 파라미터화된 메시지 사용으로 성능 향상과 가독성 확보
    }

    /**
     * 작업 제출 (우선순위 지원) - 수정된 버전
     */
    public Future<?> submit(Runnable task, int priority) {
        // 제출된 작업 수 원자적 증가 - 통계 목적
        submittedTasks.incrementAndGet();
        // incrementAndGet()으로 값 증가 후 새로운 값 반환, 원자적 연산으로 스레드 안전

        // 모든 작업을 PriorityTask로 래핑하여 우선순위 정보 포함
        PriorityTask priorityTask = new PriorityTask(task, priority);
        // 일반 Runnable을 우선순위 정보와 함께 래핑
        // 이를 통해 PriorityBlockingQueue에서 우선순위 기반 정렬 가능

        try {
            // execute()를 직접 호출하여 PriorityTask가 큐에 들어가도록 함
            execute(priorityTask);
            // submit() 대신 execute() 사용으로 우선순위 래핑 구조 유지
            // execute()는 ThreadPoolExecutor의 핵심 메서드로 작업을 큐에 추가

            // PriorityTask 자체를 Future로 반환 (Runnable과 Future 인터페이스 구현)
            return priorityTask.getFuture();
            // PriorityTask 내부의 FutureTask 반환으로 비동기 결과 추적 가능

        } catch (RejectedExecutionException e) {
            // 작업 거부시 통계 업데이트
            rejectedTasks.incrementAndGet();
            // 거부된 작업 수 증가로 모니터링 정보 제공

            // 거부 상황 로그 기록
            logger.warn("작업 거부 - 스레드풀: {}, 우선순위: {}", poolName, priority);
            // 디버깅과 모니터링을 위한 상세 정보 로깅

            // 예외를 다시 던져서 호출자가 처리하도록 함
            throw e; // 호출자에게 거부 상황 알림
        }
    }

    /**
     * 일반 작업 제출 (기본 우선순위) - 수정된 버전
     */
    @Override
    public Future<?> submit(Runnable task) {
        // 기본 우선순위 0으로 우선순위 지원 submit 메서드 호출
        return submit(task, 0);
        // 메서드 오버로딩으로 기존 ThreadPoolExecutor API 호환성 유지
        // 우선순위 미지정시 중간값인 0 사용
    }

    /**
     * Callable 작업 제출 (우선순위 지원) - 수정된 버전
     */
    public <T> Future<T> submit(Callable<T> task, int priority) {
        // 제출된 작업 수 증가
        submittedTasks.incrementAndGet();

        // Callable을 Runnable로 래핑하여 PriorityTask 생성
        FutureTask<T> futureTask = new FutureTask<>(task);
        // FutureTask는 Callable을 Runnable로 변환하면서 Future 기능 제공
        // 제네릭 타입 T로 반환값 타입 안전성 보장

        PriorityTask priorityTask = new PriorityTask(futureTask, priority);
        // FutureTask를 다시 PriorityTask로 래핑하여 우선순위 기능 추가

        try {
            // 우선순위 작업으로 실행
            execute(priorityTask);
            // execute로 우선순위 큐에 작업 추가

            return futureTask; // 원래 FutureTask 반환
            // 호출자는 원본 FutureTask를 통해 결과값 T 획득 가능

        } catch (RejectedExecutionException e) {
            // 거부 통계 업데이트 및 예외 재전파
            rejectedTasks.incrementAndGet();
            throw e;
        }
    }

    /**
     * Callable 작업 제출 (기본 우선순위) - 새로 추가
     */
    @Override
    public <T> Future<T> submit(Callable<T> task) {
        // 기본 우선순위로 Callable 제출
        return submit(task, 0);
        // ThreadPoolExecutor의 원본 API와 호환성 유지
    }

    /**
     * 스레드풀 모니터링 및 적응형 조정
     */
    private void monitorAndAdjust() {
        try {
            // 현재 스레드풀 통계 수집
            ThreadPoolStats currentStats = collectStats();
            // 모든 성능 지표를 종합하여 현재 상태 스냅샷 생성

            // 최신 통계를 원자적으로 업데이트
            lastStats.set(currentStats);
            // AtomicReference.set()으로 스레드 안전하게 통계 정보 갱신

            // 디버그 레벨로 상세 상태 로깅
            logger.debug("스레드풀 상태 - {}", currentStats);
            // toString() 메서드로 자동 변환되어 모든 통계 정보 출력

            // 적응형 조정이 활성화된 경우에만 크기 조정 실행
            if (adaptiveEnabled) {
                adjustPoolSize(currentStats);
                // 수집된 통계를 바탕으로 스레드풀 크기 동적 조정
            }

        } catch (Exception e) {
            // 모니터링 중 예외 발생시 로그 기록하고 계속 진행
            logger.error("스레드풀 모니터링 중 오류", e);
            // 모니터링 실패가 전체 시스템을 중단시키지 않도록 예외 처리
        }
    }

    /**
     * 스레드풀 크기 적응형 조정
     */
    private void adjustPoolSize(ThreadPoolStats stats) {
        // 현재 스레드풀 상태 정보 수집
        int currentPoolSize = getPoolSize(); // 현재 활성 스레드 수
        int currentQueueSize = getQueue().size(); // 대기 중인 작업 수
        double utilization = (double) getActiveCount() / currentPoolSize; // 스레드 사용률 계산
        // 사용률 = 작업 중인 스레드 수 / 전체 스레드 수

        // 스레드 증가 필요성 판단
        boolean needIncrease = shouldIncreasePoolSize(stats, currentQueueSize, utilization);
        // 큐 크기, 사용률, 대기 시간 등을 종합적으로 판단

        boolean needDecrease = shouldDecreasePoolSize(stats, currentQueueSize, utilization);
        // 리소스 낭비 방지를 위한 감소 필요성 판단

        // 스레드 증가 조건 확인 및 실행
        if (needIncrease && currentPoolSize < maxPoolSize) {
            // 증가가 필요하고 최대 크기 미만인 경우

            // 새로운 스레드 수 계산 - 조정 계수에 따라 점진적 증가
            int newSize = Math.min(maxPoolSize,
                    currentPoolSize + (int) Math.max(1, currentPoolSize * adjustmentFactor));
            // Math.max(1, ...)로 최소 1개는 증가하도록 보장
            // Math.min으로 최대 크기 초과 방지

            // 코어 스레드 수와 최대 스레드 수 동시 조정
            setCorePoolSize(newSize); // 코어 스레드 수 변경
            setMaximumPoolSize(Math.max(newSize, getMaximumPoolSize())); // 최대 스레드 수 조정
            // 코어 크기가 최대 크기를 초과하지 않도록 보장

            // 조정 결과 로그 출력
            logger.info("스레드풀 크기 증가 - {}: {} -> {}, 큐크기: {}, 사용률: {:.2f}%",
                    poolName, currentPoolSize, newSize, currentQueueSize, utilization * 100);
            // 퍼센트 표시를 위해 100 곱셈, 소수점 둘째 자리까지 표시

        } else if (needDecrease && currentPoolSize > minPoolSize) {
            // 감소가 필요하고 최소 크기 초과인 경우

            // 새로운 스레드 수 계산 - 점진적 감소
            int newSize = Math.max(minPoolSize,
                    currentPoolSize - (int) Math.max(1, currentPoolSize * adjustmentFactor));
            // Math.max로 최소 크기 미만으로 감소 방지

            // 코어 스레드 수만 조정 (최대 크기는 유지)
            setCorePoolSize(newSize);
            // 감소시에는 코어 크기만 줄이고 최대 크기는 유지하여 급격한 부하 증가에 대비

            // 조정 결과 로그 출력
            logger.info("스레드풀 크기 감소 - {}: {} -> {}, 큐크기: {}, 사용률: {:.2f}%",
                    poolName, currentPoolSize, newSize, currentQueueSize, utilization * 100);
        }
    }

    /**
     * 스레드풀 크기 증가 필요 여부 판단
     */
    private boolean shouldIncreasePoolSize(ThreadPoolStats stats, int queueSize, double utilization) {
        // 큐 크기가 목표치 초과시 증가 필요
        if (queueSize > targetQueueSize) {
            return true; // 대기 작업이 많으면 처리 능력 부족 상태
        }

        // 스레드 사용률이 80% 초과시 증가 필요
        if (utilization > 0.8) {
            return true; // 높은 사용률은 스레드 부족 신호
        }

        // 평균 대기 시간이 100ms 초과시 증가 필요
        if (stats.getAverageWaitTime() > 100) {
            return true; // 긴 대기 시간은 처리 지연 의미
        }

        return false; // 모든 조건이 양호하면 증가 불필요
    }

    /**
     * 스레드풀 크기 감소 필요 여부 판단
     */
    private boolean shouldDecreasePoolSize(ThreadPoolStats stats, int queueSize, double utilization) {
        // 큐가 비어있고 사용률이 30% 미만시 감소 가능
        if (queueSize == 0 && utilization < 0.3) {
            return true; // 리소스 낭비 상태
        }

        // 평균 대기 시간이 10ms 미만시 감소 가능
        if (stats.getAverageWaitTime() < 10) {
            return true; // 빠른 처리는 여유 있는 상태
        }

        return false; // 안정성을 위해 보수적 감소 정책
    }

    /**
     * 현재 스레드풀 통계 수집
     */
    private ThreadPoolStats collectStats() {
        // ThreadPoolStats 객체 생성하여 모든 통계 정보 수집
        return new ThreadPoolStats(
                poolName, // 풀 이름
                getPoolSize(), // 현재 스레드 수
                getActiveCount(), // 작업 중인 스레드 수
                getQueue().size(), // 큐에 대기 중인 작업 수
                getCompletedTaskCount(), // ThreadPoolExecutor의 완료 작업 수
                submittedTasks.get(), // 직접 추적하는 제출 작업 수
                rejectedTasks.get(), // 직접 추적하는 거부 작업 수
                calculateAverageExecutionTime(), // 계산된 평균 실행 시간
                calculateAverageWaitTime(), // 계산된 평균 대기 시간
                calculateThroughput() // 계산된 처리량
        );
        // 모든 성능 지표를 하나의 객체로 집약하여 일관성 있는 스냅샷 제공
    }

    /**
     * 평균 실행 시간 계산 (밀리초)
     */
    private long calculateAverageExecutionTime() {
        long completed = completedTasks.get(); // 완료된 작업 수 조회
        if (completed == 0) return 0; // 0으로 나눗셈 방지

        // 총 실행 시간을 완료 작업 수로 나누어 평균 계산
        return totalExecutionTime.get() / completed / 1_000_000;
        // 나노초를 밀리초로 변환 (1,000,000 나노초 = 1 밀리초)
    }

    /**
     * 평균 대기 시간 계산 (밀리초)
     */
    private long calculateAverageWaitTime() {
        int queueSize = getQueue().size(); // 현재 큐에 대기 중인 작업 수
        if (queueSize == 0) return 0; // 대기 작업 없으면 0

        long avgExecutionTime = calculateAverageExecutionTime(); // 평균 실행 시간 사용
        if (avgExecutionTime == 0) return 0; // 실행 시간 정보 없으면 계산 불가

        int activeThreads = Math.max(1, getActiveCount()); // 활성 스레드 수 (최소 1)
        // Math.max로 0으로 나눗셈 방지

        // 큐 크기 * 평균 실행 시간 / 활성 스레드 수로 대기 시간 추정
        return (queueSize * avgExecutionTime) / activeThreads;
        // 간단한 큐잉 이론 기반 대기 시간 추정
    }

    /**
     * 처리량 계산 (작업/초)
     */
    private double calculateThroughput() {
        long completed = completedTasks.get(); // 완료된 작업 수
        if (completed == 0) return 0.0; // 완료 작업 없으면 0

        // 완료 작업 수를 60초로 나누어 분당 처리량을 초당으로 변환
        return completed / 60.0;
        // 모니터링 주기가 5초이므로 누적 데이터를 시간 단위로 정규화
    }

    /**
     * 작업 실행 전 후처리
     */
    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        // 부모 클래스의 beforeExecute 호출하여 기본 동작 유지
        super.beforeExecute(t, r);
        // ThreadPoolExecutor의 기본 전처리 로직 실행

        // PriorityTask인 경우 시작 시간 기록
        if (r instanceof PriorityTask) {
            PriorityTask priorityTask = (PriorityTask) r;
            priorityTask.setStartTime(System.nanoTime()); // 나노초 정밀도로 시작 시간 기록
            // 나노초 사용으로 마이크로초 단위까지 정확한 실행 시간 측정 가능
        }
    }

    /**
     * 작업 실행 후 후처리
     */
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        // 부모 클래스의 afterExecute 호출
        super.afterExecute(r, t);

        try {
            // PriorityTask인 경우 실행 시간 계산 및 통계 업데이트
            if (r instanceof PriorityTask) {
                PriorityTask priorityTask = (PriorityTask) r;
                long executionTime = System.nanoTime() - priorityTask.getStartTime();
                // 현재 시간에서 시작 시간을 빼서 실행 시간 계산
                totalExecutionTime.addAndGet(executionTime);
                // 원자적으로 총 실행 시간에 추가하여 평균 계산에 사용
            }

            // 완료된 작업 수 증가
            completedTasks.incrementAndGet();
            // 통계 추적을 위한 완료 카운트 업데이트

        } catch (Exception e) {
            // 후처리 중 예외 발생해도 시스템 안정성을 위해 경고만 로그
            logger.warn("작업 후처리 중 오류", e);
            // 후처리 실패가 전체 시스템에 영향주지 않도록 예외 처리
        }

        // 작업 실행 중 예외 발생한 경우 로그 기록
        if (t != null) {
            logger.error("작업 실행 중 예외 발생", t);
            // 작업 실행 실패에 대한 상세 로그 기록으로 디버깅 지원
        }
    }

    /**
     * 우선순위 설정
     */
    public void setTargetQueueSize(int targetQueueSize) {
        this.targetQueueSize = targetQueueSize; // 목표 큐 크기 변경
        logger.info("목표 큐 크기 변경: {}", targetQueueSize);
        // 설정 변경을 로그로 기록하여 운영 중 추적 가능
    }

    /**
     * 조정 계수 설정
     */
    public void setAdjustmentFactor(double adjustmentFactor) {
        // 조정 계수를 0.01~0.5 범위로 제한하여 안정성 확보
        this.adjustmentFactor = Math.max(0.01, Math.min(0.5, adjustmentFactor));
        // Math.max와 Math.min으로 범위 제한하여 급격한 변화 방지
        logger.info("조정 계수 변경: {}", this.adjustmentFactor);
    }

    /**
     * 적응형 조정 활성화/비활성화
     */
    public void setAdaptiveEnabled(boolean enabled) {
        this.adaptiveEnabled = enabled; // volatile 변수로 즉시 반영
        logger.info("적응형 조정: {}", enabled ? "활성화" : "비활성화");
        // 삼항 연산자로 사용자 친화적 메시지 출력
    }

    /**
     * 스레드풀 통계 조회
     */
    public ThreadPoolStats getCurrentStats() {
        ThreadPoolStats stats = lastStats.get(); // 최근 수집된 통계 조회
        // 통계가 없으면 즉시 수집하여 반환
        return stats != null ? stats : collectStats();
        // null 체크 후 삼항 연산자로 안전한 반환
    }

    /**
     * 스레드풀 상태 정보 출력
     */
    public void printStatus() {
        ThreadPoolStats stats = getCurrentStats(); // 현재 통계 정보 획득

        // 상세한 상태 정보를 단계별로 로그 출력
        logger.info("=== {} 상태 ===", poolName);
        logger.info("스레드: {}/{} (활성: {})", getPoolSize(), getMaximumPoolSize(), getActiveCount());
        // 현재/최대 스레드 수와 활성 스레드 수 표시
        logger.info("큐: {} (목표: {})", getQueue().size(), targetQueueSize);
        // 현재 큐 크기와 목표 크기 비교
        logger.info("작업: 제출={}, 완료={}, 거부={}", submittedTasks.get(), completedTasks.get(), rejectedTasks.get());
        // 작업 처리 현황 요약
        logger.info("성능: 평균실행={}ms, 평균대기={}ms, 처리량={:.2f}/s",
                stats.getAverageExecutionTime(), stats.getAverageWaitTime(), stats.getThroughput());
        // 성능 지표 요약 - 소수점 둘째 자리까지 표시
    }

    /**
     * 스레드풀 종료
     */
    @Override
    public void shutdown() {
        logger.info("{} 종료 시작...", poolName);

        // 모니터링 스케줄러 먼저 종료
        monitorExecutor.shutdown();
        // 모니터링 중단하여 추가 조정 작업 방지

        // 부모 클래스의 shutdown 호출하여 스레드풀 종료
        super.shutdown();

        try {
            // 30초 동안 정상 종료 대기
            if (!awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("{} 강제 종료", poolName);
                shutdownNow(); // 정상 종료 실패시 강제 종료
                // shutdownNow()는 실행 중인 작업을 인터럽트하여 즉시 종료
            }
        } catch (InterruptedException e) {
            // 인터럽트 발생시 현재 스레드 인터럽트 상태 복원
            Thread.currentThread().interrupt();
            shutdownNow(); // 강제 종료로 빠른 정리
        }

        logger.info("{} 종료 완료", poolName);
    }

    /**
     * 우선순위 작업 래퍼 클래스 - 수정된 버전
     * FutureTask와 호환되도록 개선
     */
    private static class PriorityTask implements Runnable, Comparable<PriorityTask> {
        // Runnable 구현으로 ThreadPoolExecutor에서 실행 가능
        // Comparable 구현으로 PriorityBlockingQueue에서 정렬 가능

        private final Runnable task; // 실제 실행할 작업
        private final int priority; // 우선순위 (높을수록 먼저 실행)
        private final long createdTime; // 생성 시간 (FIFO 보장용)
        private volatile long startTime; // 실행 시작 시간 (성능 측정용)
        private final FutureTask<Void> future; // Future 기능 제공

        public PriorityTask(Runnable task, int priority) {
            this.task = task; // 원본 작업 저장
            this.priority = priority; // 우선순위 저장
            this.createdTime = System.nanoTime(); // 나노초 정밀도로 생성 시간 기록
            // 동일한 우선순위에서 FIFO 순서 보장을 위해 생성 시간 기록

            // Runnable을 Callable로 래핑하여 Future 생성
            this.future = new FutureTask<>(() -> {
                task.run(); // 원본 작업 실행
                return null; // Void 타입이므로 null 반환
            });
            // FutureTask로 래핑하여 비동기 결과 추적과 취소 기능 제공
        }

        @Override
        public void run() {
            future.run(); // FutureTask 실행
            // FutureTask.run()이 실제로 Callable을 실행하고 결과 저장
        }

        @Override
        public int compareTo(PriorityTask other) {
            // 높은 우선순위가 먼저 실행되도록 (역순 정렬)
            int result = Integer.compare(other.priority, this.priority);
            // other.priority를 먼저 써서 높은 값이 앞에 오도록 정렬

            if (result == 0) {
                // 동일한 우선순위일 경우 FIFO (먼저 생성된 것부터)
                result = Long.compare(this.createdTime, other.createdTime);
                // 생성 시간 오름차순으로 FIFO 순서 보장
            }

            return result; // PriorityQueue는 자연 순서(오름차순) 사용
        }

        // Future 객체 반환 메서드
        public Future<Void> getFuture() {
            return future;
        }

        // 접근자 메서드들
        public int getPriority() { return priority; }
        public long getCreatedTime() { return createdTime; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
    }

    /**
     * 커스텀 스레드 팩토리 (Java 17+ 호환)
     */
    private static class AdaptiveThreadFactory implements ThreadFactory {
        // ThreadFactory 인터페이스 구현으로 스레드 생성 방식 커스터마이징

        private final String poolName; // 스레드 이름 접두사
        private final AtomicInteger threadNumber = new AtomicInteger(1); // 스레드 번호 생성기
        private final ThreadGroup group; // 스레드 그룹

        AdaptiveThreadFactory(String poolName) {
            this.poolName = poolName; // 풀 이름을 스레드 이름에 포함

            // SecurityManager 사용하지 않고 현재 스레드 그룹 사용
            this.group = Thread.currentThread().getThreadGroup();
            // Java 17+에서 SecurityManager 제거됨에 따라 단순화된 방식 사용
        }

        @Override
        public Thread newThread(Runnable r) {
            // 새로운 스레드 생성 - 그룹, 작업, 이름, 스택 크기 지정
            Thread t = new Thread(group, r, poolName + "-" + threadNumber.getAndIncrement(), 0);
            // threadNumber.getAndIncrement()로 고유한 스레드 번호 생성
            // 스택 크기 0은 플랫폼 기본값 사용 의미

            // 데몬 스레드가 아니도록 설정
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            // 데몬 스레드는 JVM 종료를 막지 않지만, 작업 스레드는 일반 스레드로 설정

            // 우선순위를 표준 우선순위로 설정
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            // 모든 스레드를 동일한 우선순위로 설정하여 공정한 스케줄링

            return t; // 설정 완료된 스레드 반환
        }
    }

    /**
     * 커스텀 거부 핸들러
     */
    private static class AdaptiveRejectedExecutionHandler implements RejectedExecutionHandler {
        // RejectedExecutionHandler 인터페이스 구현으로 작업 거부 정책 정의

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            // 스레드풀이 종료되지 않은 상태에서만 처리
            if (!executor.isShutdown()) {
                try {
                    Logger logger = LoggerFactory.getLogger(AdaptiveRejectedExecutionHandler.class);
                    // 로거 인스턴스 생성하여 거부 상황 로깅

                    logger.warn("스레드풀 포화 - 호출 스레드에서 직접 실행: {}",
                            Thread.currentThread().getName());
                    // Caller-Runs 정책 - 호출한 스레드에서 직접 작업 실행

                    r.run(); // 거부된 작업을 호출 스레드에서 직접 실행
                    // 백프레셔 효과로 호출자의 속도 자동 조절

                } catch (Exception e) {
                    Logger logger = LoggerFactory.getLogger(AdaptiveRejectedExecutionHandler.class);
                    logger.error("거부된 작업 실행 중 오류", e);
                    // 직접 실행 중 오류 발생해도 시스템 안정성 유지
                }
            }
            // 스레드풀 종료 중이면 작업 무시 (정상적인 종료 과정)
        }
    }

    /**
     * 스레드풀 통계 클래스
     */
    public static class ThreadPoolStats {
        // 스레드풀의 모든 성능 지표를 담는 불변 데이터 클래스

        private final String poolName; // 풀 이름
        private final int poolSize; // 현재 스레드 수
        private final int activeCount; // 작업 중인 스레드 수
        private final int queueSize; // 대기 중인 작업 수
        private final long completedTaskCount; // 완료된 작업 수
        private final long submittedTasks; // 제출된 작업 수
        private final long rejectedTasks; // 거부된 작업 수
        private final long averageExecutionTime; // 평균 실행 시간(ms)
        private final long averageWaitTime; // 평균 대기 시간(ms)
        private final double throughput; // 처리량(작업/초)
        // 모든 필드를 final로 선언하여 불변성 보장

        public ThreadPoolStats(String poolName, int poolSize, int activeCount, int queueSize,
                               long completedTaskCount, long submittedTasks, long rejectedTasks,
                               long averageExecutionTime, long averageWaitTime, double throughput) {
            // 생성자에서 모든 통계 값을 초기화
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

        // 접근자 메서드들 - 불변 객체이므로 getter만 제공
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
            // 모든 통계 정보를 읽기 쉬운 형태로 포맷팅
            return String.format(
                    "%s{pool=%d, active=%d, queue=%d, completed=%d, " +
                            "submitted=%d, rejected=%d, avgExec=%dms, avgWait=%dms, throughput=%.2f/s}",
                    poolName, poolSize, activeCount, queueSize, completedTaskCount,
                    submittedTasks, rejectedTasks, averageExecutionTime, averageWaitTime, throughput
            );
            // String.format으로 일관된 형식의 문자열 생성
        }
    }
}