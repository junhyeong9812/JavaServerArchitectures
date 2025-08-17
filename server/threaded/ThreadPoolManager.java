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

    /*
     * 톰캣 스타일 ThreadPoolExecutor 인스턴스
     *
     * final 키워드 사용 이유:
     * - 객체 생성 후 참조 변경 불가 (불변성 보장)
     * - 스레드 안전성 향상
     * - 의도치 않은 참조 변경 방지
     *
     * TomcatStyleThreadPoolExecutor는 내부 클래스로 정의되어
     * 표준 ThreadPoolExecutor와 다른 스레드 생성 전략을 구현합니다.
     */
    private final TomcatStyleThreadPoolExecutor threadPool;

    /*
     * 모니터링을 위한 스케줄된 실행자 서비스
     *
     * ScheduledExecutorService:
     * - ExecutorService를 확장한 인터페이스
     * - 지연 실행 및 주기적 실행 기능 제공
     * - scheduleAtFixedRate(), scheduleWithFixedDelay() 등의 메서드 포함
     *
     * 용도:
     * - 주기적인 스레드풀 상태 모니터링
     * - 통계 정보 수집 및 출력
     * - 성능 지표 추적
     */
    private final ScheduledExecutorService monitor;

    /*
     * 활성 연결 수를 추적하는 원자적 정수
     *
     * AtomicInteger 사용 이유:
     * - 멀티스레드 환경에서 안전한 정수 연산
     * - Compare-And-Swap (CAS) 연산 사용
     * - 락 없이 원자적 연산 수행 (높은 성능)
     * - volatile 특성 내포 (메모리 가시성 보장)
     *
     * 주요 메서드:
     * - incrementAndGet(): 1 증가 후 값 반환
     * - decrementAndGet(): 1 감소 후 값 반환
     * - get(): 현재 값 읽기
     * - compareAndSet(expect, update): 조건부 업데이트
     */
    private final AtomicInteger activeConnections;

    /*
     * 스레드풀 설정 정보를 담은 객체
     * ThreadPoolConfig 클래스의 인스턴스로 모든 설정 파라미터를 포함
     */
    private final ThreadPoolConfig config;

    // 통계 정보
    /*
     * 총 처리된 요청 수를 추적하는 원자적 긴 정수
     *
     * AtomicLong 사용 이유:
     * - long 타입의 원자적 연산 지원 (64비트)
     * - 큰 수의 요청을 안전하게 카운트
     * - int 오버플로우 방지 (최대 9,223,372,036,854,775,807)
     *
     * 용도:
     * - 서버 가동 이후 총 처리 요청 수 추적
     * - 평균 처리 시간 계산의 분모로 사용
     * - 처리량 (throughput) 계산
     */
    private final AtomicLong totalRequestsProcessed = new AtomicLong(0);

    /*
     * 총 처리 시간을 누적하는 원자적 긴 정수 (밀리초 단위)
     *
     * 용도:
     * - 모든 요청의 처리 시간 합계
     * - 평균 처리 시간 계산: totalProcessingTime / totalRequestsProcessed
     * - 성능 분석 및 최적화 지표
     */
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    /*
     * 거부된 작업 수를 추적하는 원자적 긴 정수
     *
     * 거부 발생 조건:
     * - 모든 스레드가 사용 중
     * - 작업 큐가 가득 참
     * - RejectedExecutionHandler 호출됨
     *
     * 용도:
     * - 시스템 부하 상태 모니터링
     * - 거부율 계산: rejectedTasks / totalRequests
     * - 스레드풀 크기 조정의 기준
     */
    private final AtomicLong rejectedTasks = new AtomicLong(0);

    /*
     * 최대 동시 활성 스레드 수를 추적하는 변수
     *
     * volatile 키워드 사용 이유:
     * - 모든 스레드에서 일관된 값 보기 보장
     * - CPU 캐시가 아닌 메인 메모리에서 직접 읽기/쓰기
     * - 메모리 가시성 문제 해결
     * - 단순 읽기/쓰기만 원자적 (복합 연산은 비원자적)
     *
     * 용도:
     * - 스레드풀의 최대 사용률 추적
     * - 피크 시간대 부하 분석
     * - 스레드풀 크기 최적화 기준
     */
    private volatile int peakActiveThreads = 0;

    /*
     * ThreadPoolManager 생성자
     *
     * @param config ThreadPoolConfig 객체 - 스레드풀의 모든 설정 파라미터 포함
     *
     * 생성자에서 수행하는 작업:
     * 1. 설정 객체 저장
     * 2. 활성 연결 카운터 초기화
     * 3. 톰캣 스타일 스레드풀 생성
     * 4. 모니터링 스케줄러 설정
     * 5. 모든 코어 스레드 미리 생성
     */
    public ThreadPoolManager(ThreadPoolConfig config) {
        /*
         * this.config = config;
         *
         * 전달받은 설정 객체를 인스턴스 변수에 저장
         * 이후 스레드풀 동작 중 설정값 참조에 사용
         */
        this.config = config;

        /*
         * this.activeConnections = new AtomicInteger(0);
         *
         * AtomicInteger 생성자:
         * - new AtomicInteger(0): 초기값 0으로 생성
         * - 활성 연결 수를 0부터 카운트 시작
         *
         * 초기값을 0으로 설정하는 이유:
         * - 서버 시작 시점에는 활성 연결이 없음
         * - 연결이 들어올 때마다 incrementAndGet()으로 증가
         * - 연결이 종료될 때마다 decrementAndGet()으로 감소
         */
        this.activeConnections = new AtomicInteger(0);

        // 톰캣 스타일 ThreadPoolExecutor 생성
        /*
         * TomcatStyleTaskQueue taskQueue = new TomcatStyleTaskQueue(config.getQueueCapacity());
         *
         * 톰캣 스타일 작업 큐 생성:
         * - TomcatStyleTaskQueue: 내부 클래스로 정의된 특별한 큐
         * - LinkedBlockingQueue를 상속하여 offer() 메서드 오버라이드
         * - config.getQueueCapacity(): 설정에서 큐 용량 가져오기
         *
         * 큐 용량의 의미:
         * - 최대 대기 가능한 작업 수
         * - 큐가 가득 차면 RejectedExecutionHandler 호출
         * - 메모리 사용량과 직결 (큐 크기 × 작업 객체 크기)
         */
        TomcatStyleTaskQueue taskQueue = new TomcatStyleTaskQueue(config.getQueueCapacity());

        /*
         * this.threadPool = new TomcatStyleThreadPoolExecutor(...);
         *
         * 톰캣 스타일 ThreadPoolExecutor 생성
         * 매개변수 설명:
         *
         * 1. config.getCorePoolSize(): 코어 풀 크기
         *    - 항상 유지되는 기본 스레드 수
         *    - 작업이 없어도 살아있는 스레드들
         *
         * 2. config.getMaxPoolSize(): 최대 풀 크기
         *    - 생성 가능한 최대 스레드 수
         *    - 부하 급증 시 확장 한계
         *
         * 3. config.getKeepAliveTime(): Keep-Alive 시간
         *    - 코어 풀 초과 스레드의 유휴 대기 시간
         *    - 이 시간 후 초과 스레드 종료
         *
         * 4. TimeUnit.SECONDS: 시간 단위
         *    - Keep-Alive 시간의 단위를 초로 지정
         *    - TimeUnit enum의 SECONDS 상수
         *
         * 5. taskQueue: 작업 큐
         *    - 위에서 생성한 톰캣 스타일 큐
         *    - 스레드가 모두 사용 중일 때 작업 저장
         *
         * 6. new ServerThreadFactory(): 스레드 팩토리
         *    - 새 스레드 생성 방식 정의
         *    - 스레드 이름, 우선순위, 데몬 여부 설정
         *
         * 7. new TomcatStyleRejectedExecutionHandler(): 거부 정책
         *    - 스레드풀 포화 시 거부된 작업 처리 방식
         *    - 톰캣 방식: CallerRunsPolicy (호출자 스레드에서 실행)
         */
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
        /*
         * taskQueue.setExecutor(threadPool);
         *
         * 순환 참조 문제 해결:
         *
         * 문제 상황:
         * - TomcatStyleTaskQueue는 스레드 생성 여부 판단을 위해 Executor 참조 필요
         * - TomcatStyleThreadPoolExecutor는 생성자에서 TaskQueue를 받음
         * - 생성자에서는 아직 threadPool 객체가 완성되지 않아 참조 불가
         *
         * 해결 방법:
         * - ThreadPoolExecutor 생성 완료 후
         * - setExecutor() 메서드로 참조 설정
         * - 이제 TaskQueue에서 threadPool.getPoolSize() 등 호출 가능
         *
         * 이 설정이 중요한 이유:
         * - TaskQueue의 offer() 메서드에서 스레드 생성 가능 여부 판단
         * - 톰캣 스타일 로직의 핵심 구현
         */
        taskQueue.setExecutor(threadPool);

        // 모든 코어 스레드 미리 생성
        /*
         * threadPool.prestartAllCoreThreads();
         *
         * prestartAllCoreThreads() 메서드:
         * - ThreadPoolExecutor의 메서드
         * - 모든 코어 스레드를 즉시 생성하여 대기 상태로 만듦
         * - 반환값: 실제로 시작된 스레드 수 (int)
         *
         * 미리 생성하는 이유:
         * 1. 초기 요청 지연 제거
         *    - 첫 번째 요청도 즉시 처리 가능
         *    - 스레드 생성 시간 제거 (보통 1-10ms)
         *
         * 2. 예측 가능한 성능
         *    - 모든 요청이 일관된 처리 시간
         *    - 스레드 생성으로 인한 지터(jitter) 제거
         *
         * 3. 안정적인 동작
         *    - 부하 테스트 시 일관된 결과
         *    - 운영 환경에서 예측 가능한 응답 시간
         *
         * 단점:
         * - 초기 메모리 사용량 증가 (코어 스레드 수 × 스택 크기)
         * - 유휴 시간에도 스레드 유지 (리소스 사용)
         */
        threadPool.prestartAllCoreThreads();

        // 모니터링 스케줄러
        /*
         * this.monitor = Executors.newSingleThreadScheduledExecutor(r -> {
         *     Thread t = new Thread(r, "ThreadPool-Monitor");
         *     t.setDaemon(true);
         *     return t;
         * });
         *
         * Executors.newSingleThreadScheduledExecutor() 메서드:
         * - 단일 스레드로 구성된 ScheduledExecutorService 생성
         * - 주기적인 작업 실행에 최적화
         * - 순차적 실행 보장 (동시 실행 없음)
         *
         * 람다 표현식 (ThreadFactory):
         * - r -> { ... }: Runnable을 받아 Thread를 반환하는 함수
         * - ThreadFactory 인터페이스의 newThread() 메서드 구현
         *
         * Thread 생성 과정:
         * 1. new Thread(r, "ThreadPool-Monitor"):
         *    - r: 실행할 Runnable 객체
         *    - "ThreadPool-Monitor": 스레드 이름 (디버깅 시 식별 용이)
         *
         * 2. t.setDaemon(true):
         *    - 데몬 스레드로 설정
         *    - JVM 종료 시 이 스레드가 종료를 방해하지 않음
         *    - 메인 스레드 종료 시 자동으로 함께 종료
         *
         * 3. return t: 설정된 Thread 객체 반환
         *
         * 데몬 스레드의 중요성:
         * - 백그라운드 작업용 스레드
         * - 모든 비데몬 스레드 종료 시 JVM이 자동 종료
         * - 모니터링 스레드가 JVM 종료를 막지 않음
         */
        this.monitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ThreadPool-Monitor");
            t.setDaemon(true);
            return t;
        });

        /*
         * startMonitoring();
         *
         * 모니터링 시작 메서드 호출
         * - 아래에 정의된 private 메서드
         * - 설정에 따라 주기적 통계 출력 시작
         * - 디버그 모드일 때만 실제 모니터링 수행
         */
        startMonitoring();

        /*
         * System.out.println(...);
         *
         * 초기화 완료 메시지 출력
         * - 서버 시작 시 스레드풀 설정 확인 가능
         * - 코어 스레드 수와 최대 스레드 수 표시
         * - 운영자가 설정 적용 상태 확인 가능
         *
         * 문자열 연결:
         * - "Core: " + config.getCorePoolSize(): 코어 스레드 수
         * - "Max: " + config.getMaxPoolSize(): 최대 스레드 수
         * - + 연산자로 문자열 연결 (StringBuilder로 내부 최적화)
         */
        System.out.println("[ThreadPoolManager] Tomcat-style initialized - Core: " +
                config.getCorePoolSize() + ", Max: " + config.getMaxPoolSize());
    }

    /**
     * 작업 제출
     */
    /*
     * public Future<?> submit(Runnable task)
     *
     * 메서드 시그니처 설명:
     * - public: 외부에서 호출 가능한 공개 메서드
     * - Future<?>: 제네릭 와일드카드 사용한 Future 반환
     * - submit: 작업을 스레드풀에 제출하는 메서드명
     * - Runnable task: 실행할 작업 객체
     *
     * Future<?> 반환의 의미:
     * - Future: 비동기 작업의 결과를 나타내는 인터페이스
     * - ?: 와일드카드, 반환 타입 없음을 의미
     * - 작업 완료 여부 확인, 취소, 대기 등 가능
     *
     * 이 메서드의 역할:
     * - 클라이언트 연결 처리 작업을 스레드풀에 제출
     * - 통계 수집 기능 추가
     * - 성능 측정 래퍼 제공
     */
    public Future<?> submit(Runnable task) {
        /*
         * activeConnections.incrementAndGet();
         *
         * AtomicInteger의 incrementAndGet() 메서드:
         * - 현재 값을 1 증가시킨 후 증가된 값을 반환
         * - 원자적 연산: 멀티스레드 환경에서 안전
         * - Compare-And-Swap (CAS) 알고리즘 사용
         *
         * 이 시점에 증가시키는 이유:
         * - 작업이 스레드풀에 제출되는 순간부터 활성 연결로 간주
         * - 큐에서 대기 중인 시간도 활성 상태로 포함
         * - 정확한 동시 연결 수 추적
         */
        activeConnections.incrementAndGet();

        /*
         * long startTime = System.currentTimeMillis();
         *
         * System.currentTimeMillis() 메서드:
         * - 현재 시간을 밀리초로 반환 (long 타입)
         * - Unix timestamp: 1970년 1월 1일 00:00:00 UTC 기준
         * - 시간 측정의 시작점으로 사용
         *
         * 성능 측정 목적:
         * - 작업 제출 시점부터 완료까지 전체 시간 측정
         * - 큐 대기 시간 + 실제 실행 시간 포함
         * - 평균 응답 시간 계산에 사용
         */
        long startTime = System.currentTimeMillis();

        /*
         * return threadPool.submit(() -> { ... });
         *
         * threadPool.submit() 메서드:
         * - ThreadPoolExecutor의 submit() 메서드 호출
         * - Runnable을 Future로 래핑하여 반환
         * - 작업을 큐에 추가하거나 즉시 실행
         *
         * 람다 표현식 () -> { ... }:
         * - Runnable 인터페이스의 run() 메서드 구현
         * - 원본 task를 래핑하여 통계 수집 기능 추가
         * - try-finally 구조로 리소스 정리 보장
         */
        return threadPool.submit(() -> {
            /*
             * try { task.run(); }
             *
             * try 블록:
             * - 예외가 발생할 수 있는 코드 영역
             * - task.run(): 전달받은 작업의 실제 실행
             *
             * task.run() 메서드:
             * - Runnable 인터페이스의 유일한 메서드
             * - 실제 비즈니스 로직이 이 안에서 실행됨
             * - HTTP 요청 처리, 응답 생성 등의 작업
             */
            try {
                task.run();
            } finally {
                /*
                 * finally 블록:
                 * - try 블록의 실행 결과와 관계없이 항상 실행
                 * - 예외 발생 여부와 무관하게 리소스 정리 수행
                 * - return 문이 있어도 실행됨
                 *
                 * 통계 수집과 정리 작업:
                 * 1. 활성 연결 수 감소
                 * 2. 처리 시간 계산 및 누적
                 * 3. 처리된 요청 수 증가
                 * 4. 최대 동시 스레드 수 업데이트
                 */

                /*
                 * activeConnections.decrementAndGet();
                 *
                 * AtomicInteger의 decrementAndGet() 메서드:
                 * - 현재 값을 1 감소시킨 후 감소된 값을 반환
                 * - incrementAndGet()의 반대 동작
                 * - 원자적 연산으로 스레드 안전성 보장
                 *
                 * 감소 시키는 이유:
                 * - 작업 완료 시점에 활성 연결에서 제외
                 * - 예외 발생해도 반드시 감소 (finally 블록)
                 * - 정확한 동시 연결 수 유지
                 */
                activeConnections.decrementAndGet();

                // 통계 업데이트
                /*
                 * long processingTime = System.currentTimeMillis() - startTime;
                 *
                 * 처리 시간 계산:
                 * - 현재 시간에서 시작 시간을 뺀 차이
                 * - 밀리초 단위의 총 처리 시간
                 * - 큐 대기 시간 + 실제 실행 시간 포함
                 *
                 * 이 시간에 포함되는 요소:
                 * 1. 스레드풀 큐에서 대기한 시간
                 * 2. 스레드 스케줄링 대기 시간
                 * 3. 실제 작업 실행 시간
                 * 4. 컨텍스트 스위칭 오버헤드
                 */
                long processingTime = System.currentTimeMillis() - startTime;

                /*
                 * totalRequestsProcessed.incrementAndGet();
                 *
                 * AtomicLong의 incrementAndGet() 메서드:
                 * - long 타입 값을 1 증가시킨 후 반환
                 * - 64비트 정수의 원자적 연산
                 * - 매우 큰 수까지 안전하게 카운트 가능
                 *
                 * 처리된 요청 수 증가:
                 * - 작업 완료 시마다 호출
                 * - 성공/실패 여부와 무관하게 증가
                 * - 전체 처리량 통계의 기준
                 */
                totalRequestsProcessed.incrementAndGet();

                /*
                 * totalProcessingTime.addAndGet(processingTime);
                 *
                 * AtomicLong의 addAndGet() 메서드:
                 * - 현재 값에 지정된 값을 더한 후 결과 반환
                 * - 매개변수: 추가할 값 (processingTime)
                 * - 원자적 연산으로 동시성 문제 해결
                 *
                 * 총 처리 시간 누적:
                 * - 모든 요청의 처리 시간 합계
                 * - 평균 처리 시간 계산에 사용
                 * - 성능 트렌드 분석 데이터
                 */
                totalProcessingTime.addAndGet(processingTime);

                /*
                 * int currentActive = threadPool.getActiveCount();
                 *
                 * ThreadPoolExecutor의 getActiveCount() 메서드:
                 * - 현재 활발히 작업을 실행 중인 스레드 수 반환
                 * - 대기 중이거나 유휴 상태인 스레드는 제외
                 * - 실시간으로 변하는 값 (근사치)
                 *
                 * 활성 스레드의 정의:
                 * - run() 메서드를 실행 중인 스레드
                 * - 작업을 할당받아 처리 중인 상태
                 * - CPU나 I/O 작업을 수행 중
                 */
                int currentActive = threadPool.getActiveCount();

                /*
                 * if (currentActive > peakActiveThreads) {
                 *     peakActiveThreads = currentActive;
                 * }
                 *
                 * 최대 동시 활성 스레드 수 업데이트:
                 *
                 * 조건문 (currentActive > peakActiveThreads):
                 * - 현재 활성 스레드 수가 기록된 최대값보다 큰 경우
                 * - 새로운 최대값 발견 시에만 업데이트
                 *
                 * peakActiveThreads = currentActive:
                 * - volatile 변수에 새로운 최대값 저장
                 * - 모든 스레드에서 즉시 볼 수 있도록 메모리에 직접 기록
                 *
                 * 경쟁 조건 (Race Condition) 이슈:
                 * - 여러 스레드가 동시에 이 코드 실행 가능
                 * - volatile은 가시성만 보장, 원자성은 보장 안 함
                 * - 정확하지 않을 수 있지만 통계 목적으로는 충분
                 * - 성능상 synchronized나 AtomicInteger 사용하지 않음
                 */
                if (currentActive > peakActiveThreads) {
                    peakActiveThreads = currentActive;
                }
            }
        });
    }

    /**
     * 간단한 모니터링
     */
    /*
     * private void startMonitoring()
     *
     * 접근 제한자 private:
     * - 클래스 내부에서만 호출 가능
     * - 외부에서 직접 호출 방지
     * - 캡슐화 원칙 준수
     *
     * 반환 타입 void:
     * - 반환값 없음
     * - 모니터링 시작만 담당
     *
     * 메서드 역할:
     * - 설정에 따른 조건부 모니터링 시작
     * - 주기적 통계 출력 스케줄링
     * - 디버그 모드에서만 동작
     */
    private void startMonitoring() {
        /*
         * if (config.isDebugMode()) { ... }
         *
         * 조건문으로 디버그 모드 체크:
         * - config.isDebugMode(): 설정에서 디버그 모드 여부 반환
         * - true일 때만 모니터링 활성화
         * - false일 때는 아무 작업 안 함 (성능 최적화)
         *
         * 디버그 모드에서만 실행하는 이유:
         * 1. 성능 오버헤드 최소화
         *    - 모니터링 자체도 CPU와 메모리 사용
         *    - 프로덕션에서 불필요한 로그 출력 방지
         *
         * 2. 로그 노이즈 감소
         *    - 운영 환경에서 중요한 로그만 출력
         *    - 디버깅 시에만 상세 정보 제공
         *
         * 3. 자원 절약
         *    - 스케줄러 스레드 리소스 절약
         *    - I/O 부하 감소 (콘솔 출력)
         */
        if (config.isDebugMode()) {
            /*
             * monitor.scheduleAtFixedRate(this::printStatistics,
             *                           config.getMonitorInterval(),
             *                           config.getMonitorInterval(),
             *                           TimeUnit.SECONDS);
             *
             * ScheduledExecutorService의 scheduleAtFixedRate() 메서드:
             *
             * 매개변수 설명:
             * 1. this::printStatistics: 실행할 메서드 참조
             *    - 메서드 참조 문법 (Java 8+)
             *    - () -> this.printStatistics()와 동일
             *    - Runnable 인터페이스 구현
             *
             * 2. config.getMonitorInterval(): 초기 지연 시간
             *    - 최초 실행까지 대기할 시간
             *    - 설정에서 모니터 간격 값 사용
             *    - 서버 시작 후 첫 번째 통계 출력까지 시간
             *
             * 3. config.getMonitorInterval(): 주기 간격
             *    - 반복 실행 간격
             *    - 두 번째 실행부터 적용되는 주기
             *    - 고정된 주기로 실행 (Fixed Rate)
             *
             * 4. TimeUnit.SECONDS: 시간 단위
             *    - 위의 시간값들이 초 단위임을 명시
             *    - TimeUnit enum의 SECONDS 상수
             *
             * Fixed Rate vs Fixed Delay:
             * - Fixed Rate: 시작 시점 기준으로 고정 주기
             * - Fixed Delay: 완료 시점 기준으로 고정 대기
             * - 여기서는 Fixed Rate 사용 (일정한 주기 보장)
             */
            monitor.scheduleAtFixedRate(this::printStatistics,
                    config.getMonitorInterval(),
                    config.getMonitorInterval(),
                    TimeUnit.SECONDS);
        }
    }

    /**
     * 통계 출력
     */
    /*
     * private void printStatistics()
     *
     * 접근 제한자 private:
     * - 내부에서만 호출되는 유틸리티 메서드
     * - 스케줄러에서 주기적으로 호출
     *
     * 반환 타입 void:
     * - 콘솔 출력만 담당
     * - 반환값 불필요
     *
     * 메서드 역할:
     * - 스레드풀의 현재 상태 정보 수집
     * - 포맷된 통계 정보를 콘솔에 출력
     * - 성능 지표 실시간 모니터링 제공
     */
    private void printStatistics() {
        /*
         * System.out.println("\n=== Tomcat-Style ThreadPool Statistics ===");
         *
         * 통계 출력 시작 헤더:
         * - \n: 줄바꿈 문자로 이전 출력과 구분
         * - ===: 시각적 구분선
         * - "Tomcat-Style ThreadPool Statistics": 통계 종류 명시
         *
         * 가독성 향상:
         * - 로그에서 통계 구간 쉽게 식별
         * - 다른 로그 메시지와 구분
         * - 일관된 형식으로 출력
         */
        System.out.println("\n=== Tomcat-Style ThreadPool Statistics ===");

        /*
         * System.out.println("Core Pool Size: " + threadPool.getCorePoolSize());
         *
         * ThreadPoolExecutor의 getCorePoolSize() 메서드:
         * - 현재 설정된 코어 풀 크기 반환
         * - 항상 유지되는 기본 스레드 수
         * - 런타임에 변경 가능 (setCorePoolSize())
         *
         * 출력 목적:
         * - 현재 코어 스레드 설정 확인
         * - 동적 변경 시 변경된 값 확인
         * - 설정 적용 상태 검증
         */
        System.out.println("Core Pool Size: " + threadPool.getCorePoolSize());

        /*
         * System.out.println("Current Pool Size: " + threadPool.getPoolSize());
         *
         * ThreadPoolExecutor의 getPoolSize() 메서드:
         * - 현재 실제로 생성된 스레드 수 반환
         * - 코어 스레드 + 추가 생성된 스레드
         * - 부하에 따라 동적으로 변화
         *
         * 코어 풀 크기와의 차이:
         * - 코어 풀 크기: 설정값 (목표값)
         * - 현재 풀 크기: 실제 생성된 스레드 수
         * - 부하가 적으면 현재 크기 < 코어 크기 가능
         */
        System.out.println("Current Pool Size: " + threadPool.getPoolSize());

        /*
         * System.out.println("Max Pool Size: " + threadPool.getMaximumPoolSize());
         *
         * ThreadPoolExecutor의 getMaximumPoolSize() 메서드:
         * - 설정된 최대 스레드 수 반환
         * - 절대 초과할 수 없는 상한선
         * - 시스템 리소스 보호를 위한 제한
         *
         * 모니터링 목적:
         * - 스레드풀의 확장 한계 확인
         * - 현재 사용률 계산 기준
         * - 최대 도달 시 거부 발생 예측
         */
        System.out.println("Max Pool Size: " + threadPool.getMaximumPoolSize());

        /*
         * System.out.println("Active Threads: " + threadPool.getActiveCount());
         *
         * ThreadPoolExecutor의 getActiveCount() 메서드:
         * - 현재 작업을 실행 중인 스레드 수
         * - 유휴 상태나 대기 중인 스레드 제외
         * - 실시간 부하 상태 표시
         *
         * 활성 스레드 수의 의미:
         * - 높음: 높은 부하, 많은 동시 작업
         * - 낮음: 낮은 부하, 여유 있는 상태
         * - 최대값 근접: 병목 가능성, 확장 필요
         */
        System.out.println("Active Threads: " + threadPool.getActiveCount());

        /*
         * System.out.println("Queue Size: " + threadPool.getQueue().size());
         *
         * 큐 크기 조회 과정:
         * 1. threadPool.getQueue(): 작업 큐 객체 반환 (BlockingQueue)
         * 2. .size(): 큐에 대기 중인 작업 수 반환
         *
         * 큐 크기의 의미:
         * - 0: 모든 작업이 즉시 처리됨 (이상적)
         * - 높음: 스레드 부족, 대기 작업 누적
         * - 최대값 근접: 거부 발생 임박
         *
         * 톰캣 스타일에서 큐 사용:
         * - 스레드 생성 우선이므로 큐 사용 빈도 낮음
         * - 최대 스레드 도달 후에만 큐 사용
         * - 큐 크기 증가 = 시스템 한계 도달 신호
         */
        System.out.println("Queue Size: " + threadPool.getQueue().size());

        /*
         * System.out.println("Active Connections: " + activeConnections.get());
         *
         * AtomicInteger의 get() 메서드:
         * - 현재 값을 원자적으로 읽기
         * - volatile 읽기와 동일한 메모리 가시성
         * - 다른 스레드의 변경사항 즉시 반영
         *
         * 활성 연결 수와 활성 스레드 수 차이:
         * - 활성 연결: 처리 중인 총 연결 수 (큐 대기 포함)
         * - 활성 스레드: 실제 작업 중인 스레드 수
         * - 활성 연결 >= 활성 스레드 (대기 중인 연결 때문)
         */
        System.out.println("Active Connections: " + activeConnections.get());

        /*
         * System.out.println("Total Requests: " + totalRequestsProcessed.get());
         *
         * AtomicLong의 get() 메서드:
         * - 서버 시작 이후 처리된 총 요청 수
         * - 성공/실패 모두 포함
         * - 누적 통계 (재시작 시 초기화)
         *
         * 활용 목적:
         * - 서버 처리량 확인
         * - 평균 계산의 분모
         * - 부하 추세 분석
         */
        System.out.println("Total Requests: " + totalRequestsProcessed.get());

        /*
         * System.out.println("Rejected Tasks: " + rejectedTasks.get());
         *
         * 거부된 작업 수:
         * - 스레드풀 포화로 처리하지 못한 작업
         * - RejectedExecutionHandler가 호출된 횟수
         * - 시스템 한계 지표
         *
         * 거부 발생 원인:
         * - 모든 스레드 사용 중
         * - 작업 큐 가득 참
         * - 시스템 리소스 부족
         */
        System.out.println("Rejected Tasks: " + rejectedTasks.get());

        /*
         * System.out.println("Peak Active: " + peakActiveThreads);
         *
         * 최대 동시 활성 스레드 수:
         * - 서버 시작 이후 기록된 최대값
         * - volatile 변수 직접 읽기
         * - 피크 시간대 부하 수준 파악
         *
         * 분석 용도:
         * - 스레드풀 크기 적정성 평가
         * - 최대 부하 시 시스템 상태
         * - 용량 계획 수립 기준
         */
        System.out.println("Peak Active: " + peakActiveThreads);

        /*
         * long totalRequests = totalRequestsProcessed.get();
         *
         * 지역 변수에 총 요청 수 저장:
         * - 계산 중 값 변경 방지
         * - 일관된 계산 기준 제공
         * - 여러 번 get() 호출 최적화
         */
        long totalRequests = totalRequestsProcessed.get();

        /*
         * if (totalRequests > 0) { ... }
         *
         * 0으로 나누기 오류 방지:
         * - 총 요청이 0이면 평균 계산 불가
         * - 서버 시작 직후나 무부하 상태
         * - 의미 있는 통계가 있을 때만 출력
         */
        if (totalRequests > 0) {
            /*
             * double avgProcessingTime = (double) totalProcessingTime.get() / totalRequests;
             *
             * 평균 처리 시간 계산:
             * 1. totalProcessingTime.get(): 총 처리 시간 (long)
             * 2. (double): long을 double로 형변환
             * 3. / totalRequests: 총 요청 수로 나누기
             *
             * 형변환이 필요한 이유:
             * - long / long = long (정수 나눗셈, 소수점 버림)
             * - double / long = double (실수 나눗셈, 정확한 평균)
             *
             * 평균 처리 시간의 의미:
             * - 개별 요청의 평균 소요 시간
             * - 성능 지표의 핵심 메트릭
             * - 시스템 최적화 기준
             */
            double avgProcessingTime = (double) totalProcessingTime.get() / totalRequests;

            /*
             * System.out.println("Avg Processing Time: " + String.format("%.2f", avgProcessingTime) + "ms");
             *
             * String.format("%.2f", avgProcessingTime):
             * - 포맷 문자열 "%.2f": 소수점 둘째자리까지 표시
             * - avgProcessingTime: 형식화할 double 값
             * - 결과: "123.45" 형태의 문자열
             *
             * 포맷 지정자 "%.2f" 설명:
             * - %: 포맷 지정자 시작
             * - .2: 소수점 이하 2자리까지
             * - f: 부동소수점 수 (floating point)
             *
             * "ms" 단위 추가:
             * - 밀리초 단위임을 명시
             * - 가독성 향상
             * - 단위 혼동 방지
             */
            System.out.println("Avg Processing Time: " + String.format("%.2f", avgProcessingTime) + "ms");

            /*
             * double rejectionRate = (double) rejectedTasks.get() / totalRequests * 100;
             *
             * 거부율 계산:
             * 1. rejectedTasks.get(): 거부된 작업 수
             * 2. (double): long을 double로 형변환
             * 3. / totalRequests: 총 요청 수로 나누기
             * 4. * 100: 백분율로 변환
             *
             * 거부율의 의미:
             * - 전체 요청 중 처리하지 못한 비율
             * - 시스템 포화도 지표
             * - 0%에 가까울수록 이상적
             * - 높은 값은 용량 부족 신호
             */
            double rejectionRate = (double) rejectedTasks.get() / totalRequests * 100;

            /*
             * System.out.println("Rejection Rate: " + String.format("%.2f", rejectionRate) + "%");
             *
             * 거부율 출력:
             * - String.format("%.2f", rejectionRate): 소수점 둘째자리
             * - "%": 백분율 단위 표시
             *
             * 거부율 해석:
             * - 0.00%: 모든 요청 처리 성공
             * - 1-5%: 약간의 부하, 주의 필요
             * - 10%+: 심각한 용량 부족
             */
            System.out.println("Rejection Rate: " + String.format("%.2f", rejectionRate) + "%");
        }

        /*
         * System.out.println("Completed Tasks: " + threadPool.getCompletedTaskCount());
         *
         * ThreadPoolExecutor의 getCompletedTaskCount() 메서드:
         * - 완료된 작업의 총 개수 반환 (long)
         * - 성공적으로 끝난 작업만 카운트
         * - 예외로 종료된 작업도 포함
         *
         * 완료된 작업 수의 특징:
         * - 누적 통계 (재시작 시 초기화)
         * - 진행 중인 작업은 제외
         * - 스레드풀 내부에서 자동 관리
         *
         * totalRequestsProcessed와의 차이:
         * - totalRequestsProcessed: 직접 카운트한 요청 수
         * - getCompletedTaskCount(): ThreadPoolExecutor 내부 카운트
         * - 값이 다를 수 있음 (구현 차이)
         */
        System.out.println("Completed Tasks: " + threadPool.getCompletedTaskCount());

        /*
         * System.out.println("===========================================\n");
         *
         * 통계 출력 마무리:
         * - ===: 통계 구간 종료 표시
         * - \n: 다음 출력과 구분을 위한 줄바꿈
         *
         * 일관된 형식:
         * - 시작과 끝을 동일한 구분선으로 표시
         * - 로그 파싱 시 구간 식별 용이
         * - 가독성 향상
         */
        System.out.println("===========================================\n");
    }

    /**
     * 현재 활성 연결 수
     */
    /*
     * public int getActiveConnections()
     *
     * 외부 접근을 위한 getter 메서드:
     * - public: 다른 클래스에서 호출 가능
     * - int: 활성 연결 수 반환 (정수)
     * - 캡슐화 원칙: private 필드에 간접 접근
     *
     * 용도:
     * - 외부 모니터링 시스템에서 사용
     * - 다른 컴포넌트에서 부하 상태 확인
     * - 로드 밸런싱 의사결정 기준
     */
    public int getActiveConnections() {
        /*
         * return activeConnections.get();
         *
         * AtomicInteger의 get() 메서드:
         * - 현재 값을 원자적으로 읽기
         * - volatile 읽기와 동일한 효과
         * - 최신 값 보장 (메모리 가시성)
         *
         * 직접 반환하는 이유:
         * - 읽기 전용 접근 제공
         * - 외부에서 수정 불가
         * - 스레드 안전한 값 제공
         */
        return activeConnections.get();
    }

    /**
     * 스레드풀 상태 정보
     */
    /*
     * public ThreadPoolStatus getStatus()
     *
     * 종합 상태 정보 반환 메서드:
     * - public: 외부 접근 허용
     * - ThreadPoolStatus: 하단에 정의된 내부 클래스
     * - 모든 통계를 하나의 객체로 패키징
     *
     * 용도:
     * - 외부 모니터링 시스템 연동
     * - REST API로 상태 정보 제공
     * - 관리 대시보드 데이터 소스
     */
    public ThreadPoolStatus getStatus() {
        /*
         * return new ThreadPoolStatus(...)
         *
         * ThreadPoolStatus 객체 생성:
         * - 현재 시점의 모든 통계 정보 스냅샷
         * - 생성자에 모든 파라미터 전달
         * - 불변 객체로 설계 (값 변경 불가)
         */
        return new ThreadPoolStatus(
                /*
                 * threadPool.getCorePoolSize()
                 * 설정된 코어 풀 크기
                 */
                threadPool.getCorePoolSize(),

                /*
                 * threadPool.getPoolSize()
                 * 현재 실제 생성된 스레드 수
                 */
                threadPool.getPoolSize(),

                /*
                 * threadPool.getActiveCount()
                 * 현재 작업 중인 스레드 수
                 */
                threadPool.getActiveCount(),

                /*
                 * threadPool.getQueue().size()
                 * 대기 중인 작업 수
                 */
                threadPool.getQueue().size(),

                /*
                 * activeConnections.get()
                 * 활성 연결 수
                 */
                activeConnections.get(),

                /*
                 * totalRequestsProcessed.get()
                 * 총 처리된 요청 수
                 */
                totalRequestsProcessed.get(),

                /*
                 * peakActiveThreads
                 * 최대 동시 활성 스레드 수 (volatile 읽기)
                 */
                peakActiveThreads,

                /*
                 * 평균 처리 시간 계산:
                 * totalRequestsProcessed.get() > 0 ?
                 *   (double) totalProcessingTime.get() / totalRequestsProcessed.get() : 0
                 *
                 * 삼항 연산자 (condition ? true_value : false_value):
                 * - 조건: totalRequestsProcessed.get() > 0
                 * - 참일 때: 평균 처리 시간 계산
                 * - 거짓일 때: 0 반환 (0으로 나누기 방지)
                 */
                totalRequestsProcessed.get() > 0 ?
                        (double) totalProcessingTime.get() / totalRequestsProcessed.get() : 0,

                /*
                 * rejectedTasks.get()
                 * 거부된 작업 수
                 */
                rejectedTasks.get()
        );
    }

    /**
     * 셧다운
     */
    /*
     * public void shutdown()
     *
     * 스레드풀 종료 메서드:
     * - public: 외부에서 호출 가능 (서버 종료 시)
     * - void: 반환값 없음
     * - 우아한 종료 (Graceful Shutdown) 수행
     *
     * 종료 순서:
     * 1. 모니터링 중단
     * 2. 스레드풀 종료 시작
     * 3. 작업 완료 대기
     * 4. 강제 종료 (필요시)
     */
    public void shutdown() {
        /*
         * System.out.println("[ThreadPool] Shutting down...");
         *
         * 종료 시작 로그:
         * - 관리자에게 종료 과정 알림
         * - 디버깅 시 종료 시점 확인
         * - 로그 분석 시 종료 구간 식별
         */
        System.out.println("[ThreadPool] Shutting down...");

        /*
         * monitor.shutdown();
         *
         * ScheduledExecutorService의 shutdown() 메서드:
         * - 새로운 작업 제출 거부
         * - 기존에 제출된 작업은 완료까지 대기
         * - 스케줄된 모니터링 작업 중단
         *
         * 모니터를 먼저 종료하는 이유:
         * - 더 이상 통계 출력 불필요
         * - 종료 과정 중 예외 방지
         * - 깔끔한 종료 과정
         */
        monitor.shutdown();

        /*
         * threadPool.shutdown();
         *
         * ThreadPoolExecutor의 shutdown() 메서드:
         * - 새로운 작업 제출 거부
         * - 현재 실행 중인 작업은 완료까지 대기
         * - 큐에 대기 중인 작업도 처리 완료
         * - interrupt()는 보내지 않음 (우아한 종료)
         *
         * shutdownNow()와의 차이:
         * - shutdown(): 진행 중인 작업 완료 대기
         * - shutdownNow(): 즉시 interrupt() 전송
         */
        threadPool.shutdown();

        /*
         * try { ... } catch (InterruptedException e) { ... }
         *
         * 예외 처리가 필요한 이유:
         * - awaitTermination()이 InterruptedException 발생 가능
         * - 대기 중인 스레드가 interrupt될 수 있음
         * - 안전한 종료 보장
         */
        try {
            /*
             * if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) { ... }
             *
             * ThreadPoolExecutor의 awaitTermination() 메서드:
             * - 매개변수: 대기 시간 (30초), 시간 단위 (초)
             * - 반환값: 지정 시간 내 종료 완료 여부 (boolean)
             * - true: 시간 내 모든 작업 완료
             * - false: 시간 초과, 아직 작업 진행 중
             *
             * 30초 대기하는 이유:
             * - 일반적인 요청 처리 시간 충분히 고려
             * - 너무 짧으면: 진행 중인 작업 강제 중단
             * - 너무 길면: 서버 종료 지연
             *
             * 논리 부정 (!):
             * - false 반환 시 (시간 초과) if 블록 실행
             * - true 반환 시 (정상 종료) if 블록 건너뜀
             */
            if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                /*
                 * System.out.println("[ThreadPool] Force shutdown...");
                 *
                 * 강제 종료 알림:
                 * - 30초 내 종료되지 않아 강제 조치 필요
                 * - 관리자에게 비정상 종료 상황 알림
                 * - 로그 분석 시 문제 상황 식별
                 */
                System.out.println("[ThreadPool] Force shutdown...");

                /*
                 * threadPool.shutdownNow();
                 *
                 * ThreadPoolExecutor의 shutdownNow() 메서드:
                 * - 실행 중인 모든 스레드에 interrupt() 전송
                 * - 대기 중인 작업 목록 반환 (처리되지 않은 작업들)
                 * - 즉시 종료 시도 (강제 종료)
                 *
                 * interrupt() 전송의 의미:
                 * - 스레드에 종료 신호 전달
                 * - InterruptedException 발생 가능
                 * - 협력적 종료 메커니즘
                 *
                 * 주의사항:
                 * - 진행 중인 작업이 중단될 수 있음
                 * - 데이터 손실 가능성
                 * - 최후의 수단으로만 사용
                 */
                threadPool.shutdownNow();
            }

            /*
             * System.out.println("[ThreadPool] Shutdown completed");
             *
             * 종료 완료 로그:
             * - 정상적으로 스레드풀 종료됨을 알림
             * - 관리자에게 종료 성공 확인
             * - 시스템 재시작 시 이전 종료 상태 확인 가능
             */
            System.out.println("[ThreadPool] Shutdown completed");

        } catch (InterruptedException e) {
            /*
             * InterruptedException 처리:
             *
             * 발생 상황:
             * - awaitTermination() 대기 중 현재 스레드가 interrupt됨
             * - 다른 스레드가 Thread.interrupt() 호출
             * - 시스템 종료 과정에서 발생 가능
             *
             * 처리 방법:
             * 1. 즉시 강제 종료 수행
             * 2. 인터럽트 상태 복원
             */

            /*
             * threadPool.shutdownNow();
             *
             * 예외 상황에서의 강제 종료:
             * - 정상적인 대기가 불가능한 상황
             * - 즉시 모든 스레드에 interrupt() 전송
             * - 빠른 종료를 위한 비상 조치
             */
            threadPool.shutdownNow();

            /*
             * Thread.currentThread().interrupt();
             *
             * 인터럽트 상태 복원:
             * - InterruptedException catch 시 인터럽트 상태가 클리어됨
             * - 상위 호출자나 다른 컴포넌트가 인터럽트 상태를 알 수 있도록 복원
             * - Java의 인터럽트 처리 베스트 프랙티스
             *
             * 인터럽트 전파의 중요성:
             * - 협력적 종료 메커니즘 유지
             * - 다른 컴포넌트의 정상적인 종료 보장
             * - 시스템 전체의 일관된 종료 처리
             */
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 톰캣 스타일 ThreadPoolExecutor
     * 핵심: 즉시 스레드 생성 전략
     */
    /*
     * private class TomcatStyleThreadPoolExecutor extends ThreadPoolExecutor
     *
     * 내부 클래스 (Inner Class) 정의:
     * - private: 외부에서 접근 불가, 캡슐화
     * - class: 클래스 정의 키워드
     * - extends ThreadPoolExecutor: 표준 ThreadPoolExecutor 상속
     *
     * 내부 클래스 사용 이유:
     * 1. 캡슐화: 외부에서 직접 생성/접근 불가
     * 2. 응집성: 관련 기능을 하나의 클래스 내부에 묶음
     * 3. 네임스페이스: 외부 클래스 범위 내에서만 의미 있음
     * 4. 외부 클래스 멤버 접근: config 등 직접 접근 가능
     *
     * ThreadPoolExecutor 상속 목적:
     * - execute() 메서드 오버라이드
     * - 톰캣의 스레드 생성 전략 구현
     * - 기존 기능은 그대로 유지하면서 핵심 로직만 변경
     */
    private class TomcatStyleThreadPoolExecutor extends ThreadPoolExecutor {

        /*
         * public TomcatStyleThreadPoolExecutor(...)
         *
         * 생성자 정의:
         * - 부모 클래스 ThreadPoolExecutor의 생성자와 동일한 시그니처
         * - 모든 매개변수를 부모 생성자에 그대로 전달
         * - 추가적인 초기화 작업 없음
         *
         * 매개변수 설명:
         * - int corePoolSize: 코어 풀 크기
         * - int maximumPoolSize: 최대 풀 크기
         * - long keepAliveTime: Keep-Alive 시간
         * - TimeUnit unit: 시간 단위
         * - BlockingQueue<Runnable> workQueue: 작업 큐
         * - ThreadFactory threadFactory: 스레드 생성 팩토리
         * - RejectedExecutionHandler handler: 거부 정책 핸들러
         */
        public TomcatStyleThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                                             long keepAliveTime, TimeUnit unit,
                                             BlockingQueue<Runnable> workQueue,
                                             ThreadFactory threadFactory,
                                             RejectedExecutionHandler handler) {
            /*
             * super(corePoolSize, maximumPoolSize, keepAliveTime, unit,
             *       workQueue, threadFactory, handler);
             *
             * 부모 클래스 생성자 호출:
             * - super 키워드: 부모 클래스 생성자 호출
             * - 모든 매개변수를 부모에게 그대로 전달
             * - ThreadPoolExecutor의 기본 초기화 수행
             *
             * 부모 생성자에서 수행되는 작업:
             * 1. 스레드풀 크기 설정 검증
             * 2. 내부 자료구조 초기화
             * 3. 작업 큐와 스레드 팩토리 설정
             * 4. 거부 정책 핸들러 설정
             * 5. 내부 통계 카운터 초기화
             */
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit,
                    workQueue, threadFactory, handler);
        }

        /*
         * @Override
         * public void execute(Runnable command)
         *
         * 메서드 오버라이드:
         * - @Override: 어노테이션으로 오버라이드 명시
         * - 부모 클래스의 execute() 메서드를 재정의
         * - 톰캣 스타일 스레드 생성 로직 구현
         *
         * execute() 메서드의 역할:
         * - 새로운 작업을 스레드풀에 제출
         * - 스레드 생성/작업 할당/큐 저장 결정
         * - 스레드풀의 핵심 동작 로직
         *
         * 표준 vs 톰캣 스타일 차이:
         * 표준: Core 가득참 → Queue 저장 → Queue 가득참 → 새 스레드 생성
         * 톰캣: Core 가득참 → 새 스레드 생성 → Max 도달 → Queue 저장
         */
        @Override
        public void execute(Runnable command) {
            // 톰캣 스타일 로직: 가능하면 즉시 새 스레드 생성

            /*
             * int currentThreads = getPoolSize();
             *
             * ThreadPoolExecutor의 getPoolSize() 메서드:
             * - 현재 스레드풀에 생성된 총 스레드 수 반환
             * - 활성 스레드 + 유휴 스레드 포함
             * - 코어 스레드 + 추가 생성된 스레드
             *
             * 현재 스레드 수의 용도:
             * - 새 스레드 생성 가능 여부 판단
             * - 최대 스레드 수와 비교 기준
             * - 스레드풀 확장 결정
             */
            int currentThreads = getPoolSize();

            /*
             * int activeThreads = getActiveCount();
             *
             * ThreadPoolExecutor의 getActiveCount() 메서드:
             * - 현재 작업을 실행 중인 스레드 수 반환
             * - 대기 중이거나 유휴 상태인 스레드 제외
             * - 실제 작업 부하 수준 나타냄
             *
             * 활성 스레드 수의 의미:
             * - 현재 처리 중인 작업 수와 직결
             * - 추가 스레드 필요 여부 판단 기준
             * - 시스템 부하 상태 지표
             */
            int activeThreads = getActiveCount();

            /*
             * int maxThreads = getMaximumPoolSize();
             *
             * ThreadPoolExecutor의 getMaximumPoolSize() 메서드:
             * - 설정된 최대 스레드 수 반환
             * - 절대 초과할 수 없는 상한선
             * - 시스템 리소스 보호를 위한 제한
             *
             * 최대 스레드 수의 역할:
             * - 스레드 생성 가능 여부 판단
             * - 시스템 안정성 보장
             * - 메모리 사용량 제한
             */
            int maxThreads = getMaximumPoolSize();

            // 1. 활성 스레드가 코어 사이즈보다 적으면 기본 처리
            /*
             * if (activeThreads < getCorePoolSize()) {
             *     super.execute(command);
             *     return;
             * }
             *
             * 첫 번째 조건 분기: 코어 풀에 여유가 있는 경우
             *
             * getCorePoolSize() 메서드:
             * - 설정된 코어 풀 크기 반환
             * - 항상 유지해야 하는 기본 스레드 수
             *
             * 조건 (activeThreads < getCorePoolSize()):
             * - 활성 스레드 수가 코어 풀 크기보다 작음
             * - 아직 코어 풀이 가득 차지 않은 상태
             * - 표준 ThreadPoolExecutor와 동일한 동작
             *
             * super.execute(command):
             * - 부모 클래스의 execute() 메서드 호출
             * - 표준 ThreadPoolExecutor의 기본 로직 수행
             * - 코어 풀 범위 내에서는 표준 동작 유지
             *
             * return 문:
             * - 메서드 즉시 종료
             * - 이후 톰캣 스타일 로직 실행 안 함
             * - 표준 동작으로 충분한 상황
             */
            if (activeThreads < getCorePoolSize()) {
                super.execute(command);
                return;
            }

            // 2. 최대 스레드 수에 도달하지 않았으면 즉시 새 스레드 생성
            /*
             * if (currentThreads < maxThreads) { ... }
             *
             * 두 번째 조건 분기: 톰캣 스타일 핵심 로직
             *
             * 조건 (currentThreads < maxThreads):
             * - 현재 스레드 수가 최대 스레드 수보다 작음
             * - 아직 새 스레드를 생성할 여지가 있음
             * - 표준 ThreadPoolExecutor와 다른 분기점
             *
             * 이 조건의 의미:
             * - 코어 풀은 가득 찼지만 최대 한계는 도달 안 함
             * - 표준: 큐에 저장하려 시도
             * - 톰캣: 즉시 새 스레드 생성 시도
             */
            if (currentThreads < maxThreads) {
                // 코어 사이즈를 일시적으로 늘려서 즉시 스레드 생성 유도
                /*
                 * int newCoreSize = Math.min(currentThreads + 1, maxThreads);
                 *
                 * 새로운 코어 크기 계산:
                 *
                 * Math.min(a, b) 메서드:
                 * - 두 값 중 더 작은 값 반환
                 * - a: currentThreads + 1 (현재 + 1개)
                 * - b: maxThreads (최대 한계)
                 *
                 * currentThreads + 1:
                 * - 현재 스레드 수에서 1개 추가
                 * - 점진적 확장 (한 번에 1개씩)
                 * - 급격한 스레드 생성 방지
                 *
                 * Math.min()을 사용하는 이유:
                 * - 최대 스레드 수 초과 방지
                 * - currentThreads + 1이 maxThreads보다 클 수 있음
                 * - 안전한 경계 검사
                 *
                 * 예시:
                 * - currentThreads = 8, maxThreads = 10
                 * - newCoreSize = Math.min(9, 10) = 9
                 * - currentThreads = 9, maxThreads = 10
                 * - newCoreSize = Math.min(10, 10) = 10
                 */
                int newCoreSize = Math.min(currentThreads + 1, maxThreads);

                /*
                 * setCorePoolSize(newCoreSize);
                 *
                 * ThreadPoolExecutor의 setCorePoolSize() 메서드:
                 * - 코어 풀 크기를 동적으로 변경
                 * - 새로운 크기가 현재보다 크면 즉시 스레드 생성
                 * - 새로운 크기가 현재보다 작으면 초과 스레드 제거 시도
                 *
                 * 톰캣 스타일의 핵심 트릭:
                 * 1. 코어 크기를 1 증가시킴
                 * 2. ThreadPoolExecutor는 코어 크기까지 즉시 스레드 생성
                 * 3. 결과적으로 새 스레드가 즉시 생성됨
                 * 4. 큐 대기 없이 바로 작업 할당
                 *
                 * 이 방법의 장점:
                 * - ThreadPoolExecutor의 내부 로직 활용
                 * - 복잡한 스레드 생성 로직 재구현 불필요
                 * - 기존 예외 처리 및 동기화 메커니즘 그대로 사용
                 * - 안전하고 검증된 스레드 생성 과정
                 */
                setCorePoolSize(newCoreSize);

                /*
                 * if (config.isDebugMode()) { ... }
                 *
                 * 디버그 모드에서만 로그 출력:
                 * - config.isDebugMode(): 설정에서 디버그 모드 여부 확인
                 * - 성능 최적화: 디버그 모드가 아니면 문자열 생성 안 함
                 * - 프로덕션 환경에서 로그 오버헤드 제거
                 */
                if (config.isDebugMode()) {
                    /*
                     * System.out.println("[TomcatStyle] Immediate thread creation - " + ...);
                     *
                     * 즉시 스레드 생성 로그:
                     * - [TomcatStyle]: 톰캣 스타일 로직임을 명시
                     * - "Immediate thread creation": 즉시 스레드 생성됨을 알림
                     *
                     * 로그에 포함되는 정보:
                     * - "Threads: " + currentThreads + " -> " + newCoreSize:
                     *   현재 스레드 수에서 새로운 코어 크기로 변경됨
                     * - ", Active: " + activeThreads:
                     *   현재 활성 스레드 수 (작업 부하 수준)
                     *
                     * 디버깅 목적:
                     * - 톰캣 스타일 로직이 언제 작동하는지 확인
                     * - 스레드 생성 패턴 분석
                     * - 성능 최적화 효과 검증
                     */
                    System.out.println("[TomcatStyle] Immediate thread creation - " +
                            "Threads: " + currentThreads + " -> " + newCoreSize +
                            ", Active: " + activeThreads);
                }
            }

            // 3. 일반 실행
            /*
             * super.execute(command);
             *
             * 부모 클래스의 execute() 메서드 최종 호출:
             *
             * 이 시점에서의 상황:
             * 1. 코어 풀 크기를 조정했거나 (위에서 setCorePoolSize() 호출)
             * 2. 이미 최대 스레드에 도달한 상태
             *
             * 부모 execute()의 동작:
             * - 코어 크기 조정된 경우: 새 스레드 생성하여 작업 즉시 실행
             * - 최대 스레드 도달한 경우: 작업을 큐에 저장 시도
             * - 큐도 가득 찬 경우: RejectedExecutionHandler 호출
             *
             * 톰캣 스타일의 결과:
             * 1. 코어 풀 여유 → 즉시 처리 (표준과 동일)
             * 2. 코어 풀 가득, 최대 미달 → 새 스레드 생성하여 즉시 처리 (톰캣 특화)
             * 3. 최대 스레드 도달 → 큐에 대기 (표준과 동일)
             * 4. 큐도 가득참 → 거부 처리 (표준과 동일)
             *
             * 핵심 차이점:
             * - 표준: 2번 상황에서 큐에 저장 시도
             * - 톰캣: 2번 상황에서 즉시 스레드 생성
             * - 결과: 더 낮은 대기 시간, 빠른 응답성
             */
            super.execute(command);
        }
    }

    /**
     * 톰캣 스타일 작업 큐
     * offer() 메서드를 오버라이드하여 스레드 생성 우선
     */
    /*
     * private class TomcatStyleTaskQueue extends LinkedBlockingQueue<Runnable>
     *
     * 내부 클래스로 특별한 작업 큐 구현:
     * - private: 외부 접근 불가, 캡슐화
     * - extends LinkedBlockingQueue<Runnable>: 표준 블로킹 큐 상속
     *
     * LinkedBlockingQueue<Runnable> 특징:
     * - FIFO (First In, First Out) 순서
     * - 용량 제한 가능 (생성자에서 설정)
     * - 스레드 안전 (내부적으로 동기화)
     * - 블로킹 연산 (put/take)과 비블로킹 연산 (offer/poll) 지원
     *
     * 제네릭 <Runnable>:
     * - 큐에 저장되는 요소 타입이 Runnable
     * - 실행 가능한 작업 객체들을 저장
     * - 타입 안전성 보장
     *
     * 상속하는 이유:
     * - offer() 메서드의 동작만 변경하고 싶음
     * - 나머지 큐 기능은 그대로 사용
     * - 톰캣 스타일 로직에 맞게 큐 동작 커스터마이징
     */
    private class TomcatStyleTaskQueue extends LinkedBlockingQueue<Runnable> {

        /*
         * private TomcatStyleThreadPoolExecutor executor;
         *
         * Executor 참조 저장:
         * - TomcatStyleThreadPoolExecutor 타입으로 명시
         * - 스레드 생성 가능 여부 판단을 위해 필요
         * - offer() 메서드에서 getPoolSize(), getMaximumPoolSize() 호출
         *
         * 순환 참조 관계:
         * - ThreadPoolExecutor는 TaskQueue를 참조
         * - TaskQueue는 ThreadPoolExecutor를 참조
         * - 생성자에서 설정할 수 없어 별도 setter 메서드 필요
         */
        private TomcatStyleThreadPoolExecutor executor;

        /*
         * public TomcatStyleTaskQueue(int capacity)
         *
         * 생성자:
         * - public: 외부 클래스에서 생성할 수 있도록
         * - int capacity: 큐의 최대 용량
         *
         * super(capacity) 호출:
         * - 부모 클래스 LinkedBlockingQueue의 생성자 호출
         * - capacity 매개변수 전달
         * - 내부 배열과 동기화 메커니즘 초기화
         */
        public TomcatStyleTaskQueue(int capacity) {
            /*
             * super(capacity);
             *
             * LinkedBlockingQueue 생성자 호출:
             * - capacity: 큐의 최대 용량 설정
             * - 내부적으로 Node 기반 연결 리스트 구조 사용
             * - ReentrantLock 기반 동기화 메커니즘 초기화
             * - Condition 객체들 생성 (notEmpty, notFull)
             *
             * 용량 제한의 효과:
             * - 큐가 가득 차면 offer()는 false 반환
             * - put() 호출 시 공간이 생길 때까지 블로킹
             * - 메모리 사용량 제한 (OutOfMemoryError 방지)
             */
            super(capacity);
        }

        /*
         * public void setExecutor(TomcatStyleThreadPoolExecutor executor)
         *
         * Executor 참조 설정 메서드:
         * - 순환 참조 문제 해결을 위한 setter
         * - ThreadPoolExecutor 생성 완료 후 호출
         * - offer() 메서드에서 사용할 참조 설정
         */
        public void setExecutor(TomcatStyleThreadPoolExecutor executor) {
            /*
             * this.executor = executor;
             *
             * 인스턴스 변수에 참조 저장:
             * - 이제 offer() 메서드에서 executor의 메서드들 호출 가능
             * - getPoolSize(), getMaximumPoolSize() 등 사용 가능
             * - 스레드 생성 가능 여부 판단 준비 완료
             */
            this.executor = executor;
        }

        /*
         * @Override
         * public boolean offer(Runnable o)
         *
         * 큐 저장 메서드 오버라이드:
         * - LinkedBlockingQueue의 offer() 메서드 재정의
         * - 톰캣 스타일 로직의 핵심 구현
         *
         * offer() 메서드의 역할:
         * - 큐에 요소를 추가하려 시도
         * - 성공 시 true, 실패 시 false 반환
         * - 비블로킹: 큐가 가득 찬 경우 즉시 false 반환
         *
         * 표준 ThreadPoolExecutor에서 offer() 호출 시점:
         * 1. 코어 풀이 가득 참
         * 2. 새 작업이 제출됨
         * 3. 큐에 저장 시도 (offer() 호출)
         * 4. true 반환 시: 큐에 저장 성공
         * 5. false 반환 시: 새 스레드 생성 시도
         *
         * 톰캣 스타일의 전략:
         * - 새 스레드 생성 가능하면 큐에 저장하지 않음 (false 반환)
         * - 큐 저장보다 스레드 생성을 우선시
         */
        @Override
        public boolean offer(Runnable o) {
            // 톰캣 로직: 새 스레드를 만들 수 있으면 큐에 넣지 않음
            /*
             * if (executor != null) { ... }
             *
             * Executor 참조 null 체크:
             * - setExecutor() 호출 전에는 null 상태
             * - null인 경우 표준 동작 수행
             * - 안전한 참조 접근 보장
             */
            if (executor != null) {
                /*
                 * int currentThreads = executor.getPoolSize();
                 *
                 * 현재 스레드 수 조회:
                 * - executor.getPoolSize(): 현재 생성된 총 스레드 수
                 * - 활성 + 유휴 스레드 모두 포함
                 * - 스레드 생성 가능 여부 판단 기준
                 */
                int currentThreads = executor.getPoolSize();

                /*
                 * int maxThreads = executor.getMaximumPoolSize();
                 *
                 * 최대 스레드 수 조회:
                 * - executor.getMaximumPoolSize(): 설정된 최대 스레드 수
                 * - 절대 초과할 수 없는 상한선
                 * - 스레드 생성 가능 여부 판단 기준
                 */
                int maxThreads = executor.getMaximumPoolSize();

                // 새 스레드를 만들 수 있으면 false 반환 (큐에 넣지 않음)
                /*
                 * if (currentThreads < maxThreads) {
                 *     return false;
                 * }
                 *
                 * 톰캣 스타일 핵심 로직:
                 *
                 * 조건 (currentThreads < maxThreads):
                 * - 현재 스레드 수가 최대 스레드 수보다 작음
                 * - 아직 새 스레드를 생성할 여지가 있음
                 *
                 * return false의 의미:
                 * - 큐에 작업을 저장하지 않겠다는 신호
                 * - ThreadPoolExecutor는 false 반환 시 새 스레드 생성 시도
                 * - 작업이 큐에서 대기하지 않고 즉시 처리됨
                 *
                 * 톰캣 vs 표준 비교:
                 * - 표준: 큐에 공간 있으면 true 반환 (큐에 저장)
                 * - 톰캣: 스레드 생성 가능하면 false 반환 (즉시 스레드 생성)
                 *
                 * 결과:
                 * - 더 낮은 대기 시간
                 * - 빠른 응답성
                 * - 높은 동시 처리 능력
                 */
                if (currentThreads < maxThreads) {
                    return false;
                }
            }

            // 최대 스레드에 도달했으면 큐에 저장
            /*
             * return super.offer(o);
             *
             * 부모 클래스의 offer() 메서드 호출:
             *
             * 이 지점에 도달하는 경우:
             * 1. executor가 null인 경우 (초기화 미완료)
             * 2. currentThreads >= maxThreads인 경우 (최대 스레드 도달)
             *
             * super.offer(o)의 동작:
             * - LinkedBlockingQueue의 표준 offer() 로직
             * - 큐에 공간이 있으면 작업 저장 후 true 반환
             * - 큐가 가득 찬 경우 false 반환
             *
             * 최대 스레드 도달 후 큐 사용:
             * - 더 이상 스레드 생성 불가
             * - 큐에서 대기하는 것이 유일한 선택
             * - 표준 ThreadPoolExecutor와 동일한 동작
             *
             * 큐 가득참 시:
             * - super.offer(o)가 false 반환
             * - ThreadPoolExecutor는 RejectedExecutionHandler 호출
             * - 거부 정책에 따라 작업 처리 (예: CallerRunsPolicy)
             */
            return super.offer(o);
        }
    }

    /**
     * 서버 스레드 팩토리
     */
    /*
     * private static class ServerThreadFactory implements ThreadFactory
     *
     * 스레드 생성 팩토리 구현:
     * - private: 외부 접근 불가
     * - static: 외부 클래스 인스턴스와 독립적
     * - implements ThreadFactory: 스레드 생성 인터페이스 구현
     *
     * ThreadFactory 인터페이스:
     * - java.util.concurrent 패키지의 함수형 인터페이스
     * - newThread(Runnable r) 메서드 하나만 정의
     * - ThreadPoolExecutor가 새 스레드 생성 시 사용
     *
     * 커스텀 ThreadFactory 사용 이유:
     * 1. 스레드 이름 지정 (디버깅 용이)
     * 2. 데몬 스레드 여부 설정
     * 3. 우선순위 설정
     * 4. UncaughtExceptionHandler 설정
     * 5. 스레드 그룹 지정
     */
    private static class ServerThreadFactory implements ThreadFactory {
        /*
         * private final AtomicInteger threadNumber = new AtomicInteger(1);
         *
         * 스레드 번호 카운터:
         * - AtomicInteger: 멀티스레드 환경에서 안전한 정수 연산
         * - final: 참조 변경 불가 (객체 자체는 변경 가능)
         * - 초기값 1: 스레드 번호를 1부터 시작
         *
         * 스레드 번호의 용도:
         * - 각 스레드에 고유한 번호 부여
         * - 스레드 이름에 포함하여 식별 용이
         * - 디버깅 시 스레드 구분
         *
         * AtomicInteger 사용 이유:
         * - 여러 스레드가 동시에 newThread() 호출 가능
         * - 중복되지 않는 고유 번호 보장
         * - 락 없이 원자적 증가 연산
         */
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        /*
         * @Override
         * public Thread newThread(Runnable r)
         *
         * ThreadFactory 인터페이스 구현:
         * - 새로운 Thread 객체 생성 및 설정
         * - Runnable r: 스레드에서 실행할 작업
         * - 반환값: 설정이 완료된 Thread 객체
         */
        @Override
        public Thread newThread(Runnable r) {
            /*
             * Thread t = new Thread(r, "TomcatStyle-Thread-" + threadNumber.getAndIncrement());
             *
             * Thread 생성자 호출:
             * - Thread(Runnable target, String name): 이름을 지정한 스레드 생성
             * - r: 실행할 Runnable 객체
             * - "TomcatStyle-Thread-" + threadNumber.getAndIncrement(): 스레드 이름
             *
             * threadNumber.getAndIncrement():
             * - 현재 값을 반환한 후 1 증가
             * - 원자적 연산으로 중복 방지
             * - 1, 2, 3, ... 순서로 증가
             *
             * 스레드 이름 형식:
             * - "TomcatStyle-Thread-1"
             * - "TomcatStyle-Thread-2"
             * - "TomcatStyle-Thread-3"
             * - ...
             *
             * 이름 지정의 장점:
             * - 디버거에서 스레드 식별 용이
             * - 스레드 덤프 분석 시 구분 가능
             * - 로그 메시지에서 출처 확인
             * - 모니터링 도구에서 추적 가능
             */
            Thread t = new Thread(r, "TomcatStyle-Thread-" + threadNumber.getAndIncrement());

            /*
             * t.setDaemon(false);
             *
             * 데몬 스레드 설정:
             * - setDaemon(false): 비데몬 스레드로 설정
             * - 데몬 스레드: JVM 종료를 방해하지 않는 백그라운드 스레드
             * - 비데몬 스레드: JVM 종료를 방해하는 메인 작업 스레드
             *
             * false로 설정하는 이유:
             * - 클라이언트 요청 처리 중인 스레드는 중요한 작업
             * - JVM이 종료되더라도 진행 중인 요청은 완료해야 함
             * - 데이터 무결성 보장
             * - 클라이언트에게 적절한 응답 전송
             *
             * 데몬 vs 비데몬:
             * - 데몬: 모든 비데몬 스레드 종료 시 자동 종료
             * - 비데몬: 명시적으로 종료해야 JVM 종료 가능
             */
            t.setDaemon(false);

            /*
             * t.setPriority(Thread.NORM_PRIORITY);
             *
             * 스레드 우선순위 설정:
             * - Thread.NORM_PRIORITY: 기본 우선순위 (값: 5)
             * - 우선순위 범위: 1 (MIN_PRIORITY) ~ 10 (MAX_PRIORITY)
             * - 기본값으로 설정하여 공정한 스케줄링
             *
             * Thread.NORM_PRIORITY 사용 이유:
             * - 모든 요청이 동등한 우선순위로 처리
             * - 특정 작업이 다른 작업을 아사(starvation)시키지 않음
             * - 운영체제 스케줄러의 기본 정책 활용
             *
             * 우선순위 주의사항:
             * - OS마다 구현이 다름 (Windows vs Linux)
             * - 실제 효과는 제한적일 수 있음
             * - 과도한 우선순위 조작은 시스템 불안정 야기
             */
            t.setPriority(Thread.NORM_PRIORITY);

            /*
             * return t;
             *
             * 설정이 완료된 Thread 객체 반환:
             * - ThreadPoolExecutor가 이 스레드를 풀에 추가
             * - 스레드는 생성된 상태 (NEW)
             * - start() 호출 시 실행 시작 (RUNNABLE)
             */
            return t;
        }
    }

    /**
     * 톰캣 스타일 거부 정책
     */
    /*
     * private class TomcatStyleRejectedExecutionHandler implements RejectedExecutionHandler
     *
     * 작업 거부 처리기 구현:
     * - private: 외부 접근 불가
     * - implements RejectedExecutionHandler: 거부 정책 인터페이스 구현
     *
     * RejectedExecutionHandler 인터페이스:
     * - java.util.concurrent 패키지의 인터페이스
     * - rejectedExecution(Runnable r, ThreadPoolExecutor executor) 메서드 정의
     * - 스레드풀이 포화되어 작업을 거부할 때 호출
     *
     * 거부 발생 조건:
     * 1. 모든 스레드가 사용 중
     * 2. 작업 큐가 가득 참
     * 3. 스레드풀이 shutdown 상태
     *
     * 표준 거부 정책들:
     * - AbortPolicy: RejectedExecutionException 발생 (기본값)
     * - CallerRunsPolicy: 호출자 스레드에서 실행
     * - DiscardPolicy: 조용히 무시
     * - DiscardOldestPolicy: 큐의 가장 오래된 작업 제거 후 재시도
     */
    private class TomcatStyleRejectedExecutionHandler implements RejectedExecutionHandler {
        /*
         * @Override
         * public void rejectedExecution(Runnable r, ThreadPoolExecutor executor)
         *
         * 거부된 작업 처리 메서드:
         * - Runnable r: 거부된 작업 객체
         * - ThreadPoolExecutor executor: 거부를 발생시킨 스레드풀
         */
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            /*
             * rejectedTasks.incrementAndGet();
             *
             * 거부된 작업 수 증가:
             * - AtomicLong의 incrementAndGet(): 원자적으로 1 증가
             * - 통계 수집 목적
             * - 시스템 부하 상태 모니터링 지표
             */
            rejectedTasks.incrementAndGet();

            /*
             * if (!executor.isShutdown()) { ... }
             *
             * 스레드풀 상태 확인:
             * - executor.isShutdown(): 종료 과정 중인지 확인
             * - false: 정상 운영 중 (부하로 인한 거부)
             * - true: 종료 중 (새 작업 수락 불가)
             *
             * 종료 중이 아닌 경우에만 처리하는 이유:
             * - 종료 중에는 새 작업 처리 의미 없음
             * - 리소스 정리 과정 방해 방지
             * - 깔끔한 종료 과정 보장
             */
            if (!executor.isShutdown()) {
                /*
                 * System.err.println("[ThreadPool] Task rejected - all threads busy, queue full");
                 *
                 * 거부 상황 에러 로그:
                 * - System.err: 표준 에러 스트림 (긴급한 메시지용)
                 * - 시스템 관리자에게 알림
                 * - 부하 상태 심각성 표시
                 *
                 * 로그 메시지 내용:
                 * - "Task rejected": 작업이 거부됨
                 * - "all threads busy": 모든 스레드 사용 중
                 * - "queue full": 대기 큐도 가득 참
                 */
                System.err.println("[ThreadPool] Task rejected - all threads busy, queue full");

                // 톰캣 방식: CallerRunsPolicy 사용 (호출자 스레드에서 실행)
                /*
                 * try { r.run(); } catch (Exception e) { ... }
                 *
                 * CallerRunsPolicy 구현:
                 * - r.run(): 거부된 작업을 현재 스레드에서 직접 실행
                 * - 호출자 스레드: 작업을 제출한 스레드 (보통 Acceptor 스레드)
                 *
                 * CallerRunsPolicy의 장점:
                 * 1. 작업 손실 방지: 모든 작업이 처리됨
                 * 2. 백프레셔 효과: 호출자가 느려져서 부하 자동 조절
                 * 3. 시스템 안정성: OutOfMemoryError 방지
                 *
                 * 백프레셔 메커니즘:
                 * - 호출자 스레드가 작업 실행에 시간 소모
                 * - 새로운 작업 제출 속도 자동 감소
                 * - 스레드풀 부하 자연스럽게 완화
                 *
                 * try-catch 이유:
                 * - 거부 처리 중 예외 발생 가능
                 * - 예외로 인한 호출자 스레드 중단 방지
                 * - 안정적인 거부 처리 보장
                 */
                try {
                    r.run();

                    /*
                     * System.out.println("[ThreadPool] Task executed in caller thread: " +
                     *                   Thread.currentThread().getName());
                     *
                     * 호출자 스레드 실행 로그:
                     * - 거부된 작업이 호출자 스레드에서 실행됨을 알림
                     * - Thread.currentThread().getName(): 현재 스레드 이름
                     * - 디버깅 목적 (어떤 스레드에서 실행되었는지 확인)
                     *
                     * 일반적인 호출자 스레드:
                     * - ServerAcceptor-8080: 연결 수락 스레드
                     * - main: 메인 스레드
                     * - 기타 작업 제출 스레드
                     */
                    System.out.println("[ThreadPool] Task executed in caller thread: " +
                            Thread.currentThread().getName());

                } catch (Exception e) {
                    /*
                     * System.err.println("[ThreadPool] Error executing rejected task: " + e.getMessage());
                     *
                     * 거부 작업 실행 중 예외 처리:
                     * - e.getMessage(): 예외 메시지 추출
                     * - 에러 로그로 문제 상황 기록
                     * - 시스템 관리자에게 알림
                     *
                     * 예외 발생 가능한 상황:
                     * - 작업 로직 자체의 오류
                     * - 리소스 부족 (메모리, 네트워크 등)
                     * - 외부 시스템 연동 실패
                     * - 데이터 처리 오류
                     *
                     * 예외를 다시 던지지 않는 이유:
                     * - 거부 처리기는 최후의 보루
                     * - 예외 전파 시 시스템 전체 불안정
                     * - 로그만 남기고 계속 진행
                     */
                    System.err.println("[ThreadPool] Error executing rejected task: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 스레드풀 상태 정보 클래스
     */
    /*
     * public static class ThreadPoolStatus
     *
     * 상태 정보 캡슐화 클래스:
     * - public: 외부에서 접근 가능
     * - static: 외부 클래스 인스턴스와 독립적
     * - 불변 객체 (Immutable Object) 설계
     *
     * 불변 객체의 장점:
     * 1. 스레드 안전성: 생성 후 변경 불가
     * 2. 캐싱 가능: 동일한 상태면 재사용
     * 3. 부작용 없음: 메서드 호출 시 상태 변경 걱정 없음
     * 4. 간단한 설계: setter 메서드 불필요
     *
     * 용도:
     * - 외부 모니터링 시스템 연동
     * - REST API 응답 데이터
     * - 관리 대시보드 표시
     * - 로그 분석 데이터
     */
    public static class ThreadPoolStatus {
        /*
         * private final int corePoolSize;
         *
         * 코어 풀 크기:
         * - final: 생성 후 변경 불가
         * - 설정된 기본 스레드 수
         * - 항상 유지되는 스레드 수
         */
        private final int corePoolSize;

        /*
         * private final int currentPoolSize;
         *
         * 현재 풀 크기:
         * - 실제로 생성된 스레드 수
         * - 코어 + 추가 생성된 스레드
         * - 부하에 따라 동적 변화
         */
        private final int currentPoolSize;

        /*
         * private final int activeThreads;
         *
         * 활성 스레드 수:
         * - 현재 작업을 실행 중인 스레드
         * - 유휴 상태 스레드 제외
         * - 실시간 부하 지표
         */
        private final int activeThreads;

        /*
         * private final int queueSize;
         *
         * 큐 크기:
         * - 대기 중인 작업 수
         * - 스레드풀 포화도 지표
         * - 병목 상황 확인 기준
         */
        private final int queueSize;

        /*
         * private final int activeConnections;
         *
         * 활성 연결 수:
         * - 처리 중인 총 연결 수
         * - 큐 대기 + 실행 중 모두 포함
         * - 전체 부하 수준 지표
         */
        private final int activeConnections;

        /*
         * private final long totalRequests;
         *
         * 총 요청 수:
         * - 서버 시작 이후 처리된 총 요청
         * - 누적 통계 데이터
         * - 처리량 계산 기준
         */
        private final long totalRequests;

        /*
         * private final int peakActiveThreads;
         *
         * 최대 활성 스레드 수:
         * - 기록된 최대 동시 활성 스레드
         * - 피크 시간대 부하 수준
         * - 용량 계획 기준 데이터
         */
        private final int peakActiveThreads;

        /*
         * private final double avgProcessingTime;
         *
         * 평균 처리 시간:
         * - 요청당 평균 소요 시간 (밀리초)
         * - 성능 지표의 핵심 메트릭
         * - 시스템 최적화 기준
         */
        private final double avgProcessingTime;

        /*
         * private final long rejectedTasks;
         *
         * 거부된 작업 수:
         * - 처리하지 못한 작업 수
         * - 시스템 포화도 지표
         * - 용량 부족 신호
         */
        private final long rejectedTasks;

        /*
         * public ThreadPoolStatus(...)
         *
         * 생성자: 모든 필드를 매개변수로 받아 초기화
         * - 불변 객체이므로 생성 시점에 모든 값 설정
         * - 이후 값 변경 불가능
         */
        public ThreadPoolStatus(int corePoolSize, int currentPoolSize, int activeThreads,
                                int queueSize, int activeConnections, long totalRequests,
                                int peakActiveThreads, double avgProcessingTime, long rejectedTasks) {
            /*
             * 각 매개변수를 대응하는 final 필드에 할당
             * 생성자에서 한 번만 설정되고 이후 변경 불가
             */
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
        /*
         * 모든 필드에 대한 getter 메서드들
         * - public: 외부에서 값 읽기 가능
         * - setter 없음: 불변 객체이므로 수정 불가
         * - 단순히 필드 값 반환만 수행
         */
        public int getCorePoolSize() { return corePoolSize; }
        public int getCurrentPoolSize() { return currentPoolSize; }
        public int getActiveThreads() { return activeThreads; }
        public int getQueueSize() { return queueSize; }
        public int getActiveConnections() { return activeConnections; }
        public long getTotalRequests() { return totalRequests; }
        public int getPeakActiveThreads() { return peakActiveThreads; }
        public double getAvgProcessingTime() { return avgProcessingTime; }
        public long getRejectedTasks() { return rejectedTasks; }

        /*
         * @Override
         * public String toString()
         *
         * 문자열 표현 메서드:
         * - Object 클래스의 toString() 오버라이드
         * - 객체의 상태를 읽기 쉬운 문자열로 변환
         * - 디버깅, 로깅, 모니터링에 사용
         */
        @Override
        public String toString() {
            /*
             * String.format(...)
             *
             * 포맷 문자열을 사용한 문자열 생성:
             * - 일관된 형식으로 정보 표시
             * - 가독성 좋은 출력 제공
             * - 파싱 가능한 구조화된 형태
             *
             * 포맷 지정자:
             * - %d: 정수 (int, long)
             * - %.2f: 소수점 둘째자리까지 실수
             * - %s: 문자열
             *
             * 출력 정보:
             * - core: 코어 풀 크기
             * - current: 현재 풀 크기
             * - active: 활성 스레드 수
             * - queue: 큐 크기
             * - connections: 활성 연결 수
             * - requests: 총 요청 수
             * - peak: 최대 활성 스레드
             * - avgTime: 평균 처리 시간 (밀리초)
             * - rejected: 거부된 작업 수
             */
            return String.format(
                    "TomcatStyle-ThreadPoolStatus{core=%d, current=%d, active=%d, queue=%d, " +
                            "connections=%d, requests=%d, peak=%d, avgTime=%.2fms, rejected=%d}",
                    corePoolSize, currentPoolSize, activeThreads, queueSize,
                    activeConnections, totalRequests, peakActiveThreads, avgProcessingTime, rejectedTasks
            );
        }
    }
}