package server.eventloop;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 비동기 작업 큐
 * EventLoop와 연계하여 논블로킹 작업 스케줄링
 *
 * 핵심 역할:
 * - EventLoop 스레드와 외부 스레드 간의 작업 전달 중계
 * - 지연 실행, 주기적 실행, 타임아웃 등 시간 기반 작업 스케줄링
 * - Promise 패턴과 체이닝 지원으로 비동기 프로그래밍 편의성 제공
 * - 배치 처리와 재시도 로직 등 고급 비동기 패턴 지원
 */
public class EventQueue {

    // Logger 인스턴스 - EventQueue 동작 상황 추적
    private static final Logger logger = LoggerFactory.getLogger(EventQueue.class);

    // EventLoop 인스턴스 - 실제 작업을 실행할 이벤트 루프
    private final EventLoop eventLoop;

    // 스케줄러 - 시간 기반 작업 처리용 별도 스레드 풀
    // ScheduledExecutorService: 지연 실행과 주기적 실행을 지원하는 스레드 풀
    private final ScheduledExecutorService scheduler;

    // 작업 ID 생성기 - 각 작업에 고유 ID 부여 (디버깅과 추적용)
    // AtomicLong: 스레드 안전한 long 값, 동시성 환경에서 중복 없는 ID 생성
    private final AtomicLong taskIdGenerator;

    /**
     * EventQueue 생성자
     *
     * @param eventLoop 작업을 실행할 EventLoop 인스턴스
     */
    public EventQueue(EventLoop eventLoop) {
        this.eventLoop = eventLoop;

        // newSingleThreadScheduledExecutor(): 단일 스레드 스케줄러 생성
        // 시간 기반 작업들을 하나의 스레드에서 순차적으로 처리
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            // ThreadFactory로 스레드 생성 방식 커스터마이징
            Thread t = new Thread(r, "EventQueue-Scheduler");

            // setDaemon(true): 데몬 스레드로 설정
            // 메인 프로그램 종료시 이 스레드도 자동 종료
            t.setDaemon(true);
            return t;
        });

        // 작업 ID 생성기 초기화 (0부터 시작)
        this.taskIdGenerator = new AtomicLong(0);
    }

    /**
     * 즉시 실행할 작업 추가
     *
     * EventLoop의 핵심 원칙을 지키면서 작업 실행:
     * - 이미 EventLoop 스레드에서 실행 중이면 즉시 실행
     * - 다른 스레드에서 호출되면 EventLoop에 스케줄링
     *
     * @param task 실행할 작업 (Runnable)
     */
    public void execute(Runnable task) {
        // inEventLoop(): 현재 스레드가 EventLoop 스레드인지 확인
        if (eventLoop.inEventLoop()) {
            // 이미 이벤트루프 스레드에서 실행 중이면 바로 실행
            // 불필요한 큐 오버헤드 제거
            task.run();
        } else {
            // 다른 스레드에서 호출되면 이벤트루프에 스케줄링
            // eventLoop.execute(): EventLoop의 작업 큐에 추가
            eventLoop.execute(task);
        }
    }

    /**
     * 비동기 작업 실행 (CompletableFuture 반환)
     *
     * 작업의 결과를 비동기적으로 받을 수 있는 인터페이스
     * JavaScript의 Promise와 유사한 패턴
     *
     * @param task 실행할 작업 (Supplier<T> - 값을 반환하는 함수)
     * @param <T> 작업 결과 타입
     * @return 작업 결과를 담은 CompletableFuture
     */
    public <T> CompletableFuture<T> executeAsync(Supplier<T> task) {
        // CompletableFuture: Java의 비동기 프로그래밍 도구
        // 작업의 완료 상태와 결과를 나타내는 객체
        CompletableFuture<T> future = new CompletableFuture<>();

        // 실제 작업을 EventLoop에서 실행
        execute(() -> {
            try {
                // task.get(): Supplier에서 값 추출
                T result = task.get();

                // complete(): 성공적인 결과로 Future 완료
                future.complete(result);
            } catch (Exception e) {
                // completeExceptionally(): 예외로 Future 완료
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * 지연 실행 작업 스케줄링
     *
     * 지정된 시간 후에 작업을 실행하도록 예약
     *
     * @param task 실행할 작업
     * @param delay 지연 시간
     * @param unit 시간 단위 (SECONDS, MILLISECONDS 등)
     * @return 스케줄된 작업을 제어할 수 있는 ScheduledFuture
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        // scheduler.schedule(): 지정된 시간 후에 작업 실행
        // () -> eventLoop.execute(task): 람다로 EventLoop 실행 래핑
        // 스케줄러 스레드에서 시간을 관리하고, 실제 실행은 EventLoop에서
        return scheduler.schedule(() -> eventLoop.execute(task), delay, unit);
    }

    /**
     * 지연 실행 비동기 작업
     *
     * 지연 실행과 비동기 결과 처리를 결합
     *
     * @param task 실행할 작업 (Supplier<T>)
     * @param delay 지연 시간
     * @param unit 시간 단위
     * @param <T> 작업 결과 타입
     * @return 작업 결과를 담은 CompletableFuture
     */
    public <T> CompletableFuture<T> scheduleAsync(Supplier<T> task, long delay, TimeUnit unit) {
        CompletableFuture<T> future = new CompletableFuture<>();

        // 지연 실행 스케줄링
        scheduler.schedule(() -> {
            // 실제 작업은 EventLoop에서 실행
            eventLoop.execute(() -> {
                try {
                    T result = task.get();
                    future.complete(result);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
        }, delay, unit);

        return future;
    }

    /**
     * 주기적 실행 작업 스케줄링
     *
     * 고정된 간격으로 작업을 반복 실행
     * 예: 헬스체크, 정리 작업, 모니터링 등
     *
     * @param task 실행할 작업
     * @param initialDelay 최초 실행까지의 지연 시간
     * @param period 반복 간격
     * @param unit 시간 단위
     * @return 스케줄된 작업을 제어할 수 있는 ScheduledFuture
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task,
                                                  long initialDelay,
                                                  long period,
                                                  TimeUnit unit) {
        // scheduleAtFixedRate(): 고정 간격으로 작업 반복
        // 이전 작업의 완료 시간과 관계없이 일정한 간격 유지
        return scheduler.scheduleAtFixedRate(
                () -> eventLoop.execute(task),  // EventLoop에서 실행
                initialDelay,                   // 초기 지연
                period,                         // 반복 간격
                unit                           // 시간 단위
        );
    }

    /**
     * 타임아웃이 있는 비동기 작업
     *
     * 작업이 지정된 시간 내에 완료되지 않으면 TimeoutException으로 실패
     * 네트워크 요청이나 외부 API 호출 등에서 유용
     *
     * @param task 실행할 작업
     * @param timeout 타임아웃 시간
     * @param unit 시간 단위
     * @param <T> 작업 결과 타입
     * @return 작업 결과를 담은 CompletableFuture
     */
    public <T> CompletableFuture<T> executeWithTimeout(Supplier<T> task,
                                                       long timeout,
                                                       TimeUnit unit) {
        CompletableFuture<T> future = new CompletableFuture<>();

        // 실제 작업 실행
        execute(() -> {
            try {
                T result = task.get();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        // 타임아웃 설정
        scheduler.schedule(() -> {
            // isDone(): Future가 완료되었는지 확인
            if (!future.isDone()) {
                // 아직 완료되지 않았으면 타임아웃 예외로 완료
                future.completeExceptionally(
                        new TimeoutException("작업이 " + timeout + " " + unit + " 후에 타임아웃됨")
                );
            }
        }, timeout, unit);

        return future;
    }

    /**
     * Promise 패턴 지원
     *
     * JavaScript의 Promise와 유사한 패턴을 Java에서 구현
     * 비동기 작업의 결과를 명시적으로 제어할 수 있음
     *
     * @param <T> Promise가 처리할 값의 타입
     */
    public static class Promise<T> {
        // CompletableFuture를 내부적으로 사용하여 Promise 기능 구현
        private final CompletableFuture<T> future = new CompletableFuture<>();

        /**
         * Promise를 성공 값으로 해결
         *
         * @param value 해결할 값
         */
        public void resolve(T value) {
            // complete(): 성공적인 값으로 Future 완료
            future.complete(value);
        }

        /**
         * Promise를 에러로 거부
         *
         * @param error 거부할 에러
         */
        public void reject(Throwable error) {
            // completeExceptionally(): 예외로 Future 완료
            future.completeExceptionally(error);
        }

        /**
         * 내부 CompletableFuture 반환
         *
         * @return CompletableFuture 인스턴스
         */
        public CompletableFuture<T> getFuture() {
            return future;
        }

        /**
         * Promise가 성공적으로 해결되었는지 확인
         *
         * @return 성공적으로 완료되면 true
         */
        public boolean isResolved() {
            // isDone(): 완료 여부, isCompletedExceptionally(): 예외로 완료 여부
            return future.isDone() && !future.isCompletedExceptionally();
        }

        /**
         * Promise가 에러로 거부되었는지 확인
         *
         * @return 예외로 완료되면 true
         */
        public boolean isRejected() {
            return future.isCompletedExceptionally();
        }
    }

    /**
     * Promise 생성
     *
     * 새로운 Promise 인스턴스를 생성하여 반환
     *
     * @param <T> Promise 값 타입
     * @return 새로운 Promise 인스턴스
     */
    public <T> Promise<T> createPromise() {
        return new Promise<>();
    }

    /**
     * 여러 작업을 병렬로 실행 (이벤트루프에서 순차 처리)
     *
     * 여러 작업을 하나의 단위로 묶어서 처리
     * EventLoop에서는 순차적으로 실행되지만, 외부에서는 하나의 비동기 작업으로 보임
     *
     * @param tasks 실행할 작업들 (가변 인수)
     * @return 모든 작업 완료를 나타내는 CompletableFuture<Void>
     */
    public CompletableFuture<Void> executeAll(Runnable... tasks) {
        // 작업이 없으면 즉시 완료된 Future 반환
        if (tasks.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        execute(() -> {
            try {
                // 모든 작업을 순차적으로 실행
                for (Runnable task : tasks) {
                    task.run();
                }
                // 모든 작업 완료 후 Future 완료
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * 작업 체인 실행
     *
     * CompletableFuture를 반환하는 작업을 체이닝하여 실행
     * 복잡한 비동기 흐름을 구성할 때 유용
     *
     * @param taskChain CompletableFuture를 반환하는 작업 체인
     * @param <T> 최종 결과 타입
     * @return 체인 실행 결과를 담은 CompletableFuture
     */
    public <T> CompletableFuture<T> chain(Supplier<CompletableFuture<T>> taskChain) {
        if (eventLoop.inEventLoop()) {
            // 이미 EventLoop 스레드에서 실행 중이면 직접 실행
            try {
                return taskChain.get();
            } catch (Exception e) {
                // failedFuture(): 실패한 Future 생성
                return CompletableFuture.failedFuture(e);
            }
        } else {
            // 다른 스레드에서 호출되면 EventLoop에서 실행
            CompletableFuture<T> future = new CompletableFuture<>();

            eventLoop.execute(() -> {
                try {
                    // 체인 실행 후 결과를 Future에 전달
                    taskChain.get().whenComplete((result, error) -> {
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else {
                            future.complete(result);
                        }
                    });
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });

            return future;
        }
    }

    /**
     * 조건부 실행
     *
     * 조건이 참일 때만 작업을 실행
     *
     * @param condition 실행 조건을 판단하는 Supplier
     * @param task 조건이 참일 때 실행할 작업
     * @return 조건부 실행 결과를 담은 CompletableFuture<Void>
     */
    public CompletableFuture<Void> executeIf(Supplier<Boolean> condition, Runnable task) {
        return executeAsync(() -> {
            // condition.get(): 조건 평가
            if (condition.get()) {
                task.run();
            }
            return null; // Void 타입이므로 null 반환
        });
    }

    /**
     * 재시도 로직
     *
     * 작업이 실패하면 지정된 횟수만큼 재시도
     * 네트워크 요청이나 외부 서비스 호출에서 일시적 장애 대응용
     *
     * @param task 재시도할 작업
     * @param maxRetries 최대 재시도 횟수
     * @param retryDelay 재시도 간격
     * @param unit 시간 단위
     * @param <T> 작업 결과 타입
     * @return 재시도 작업 결과를 담은 CompletableFuture
     */
    public <T> CompletableFuture<T> retry(Supplier<T> task, int maxRetries, long retryDelay, TimeUnit unit) {
        // 내부 재귀 메서드 호출 (시도 횟수 0부터 시작)
        return retryInternal(task, maxRetries, 0, retryDelay, unit);
    }

    /**
     * 재시도 로직 내부 구현 (재귀)
     *
     * @param task 재시도할 작업
     * @param maxRetries 최대 재시도 횟수
     * @param currentAttempt 현재 시도 횟수
     * @param retryDelay 재시도 간격
     * @param unit 시간 단위
     * @param <T> 작업 결과 타입
     * @return 재시도 작업 결과를 담은 CompletableFuture
     */
    private <T> CompletableFuture<T> retryInternal(Supplier<T> task, int maxRetries, int currentAttempt,
                                                   long retryDelay, TimeUnit unit) {
        // exceptionallyCompose(): 예외 발생시 다른 CompletableFuture로 대체
        return executeAsync(task).exceptionallyCompose(error -> {
            // 최대 재시도 횟수 초과시 실패로 처리
            if (currentAttempt >= maxRetries) {
                return CompletableFuture.failedFuture(error);
            }

            // 재시도 로그
            logger.debug("작업 실패 (시도 {}/{}), {} {} 후 재시도: {}",
                    currentAttempt + 1, maxRetries + 1, retryDelay, unit, error.getMessage());

            // 지연 후 재시도
            return scheduleAsync(() -> null, retryDelay, unit)
                    // thenCompose(): Future 체이닝
                    .thenCompose(v -> retryInternal(task, maxRetries, currentAttempt + 1, retryDelay, unit));
        });
    }

    /**
     * 배치 처리
     *
     * 대량의 아이템을 작은 배치로 나누어 순차 처리
     * EventLoop 블로킹을 방지하면서 대량 데이터 처리
     *
     * @param items 처리할 아이템 리스트
     * @param processor 각 아이템을 처리하는 함수
     * @param batchSize 한 번에 처리할 배치 크기
     * @param <T> 아이템 타입
     * @return 배치 처리 완료를 나타내는 CompletableFuture<Void>
     */
    public <T> CompletableFuture<Void> processBatch(java.util.List<T> items,
                                                    java.util.function.Consumer<T> processor,
                                                    int batchSize) {
        // 빈 리스트면 즉시 완료
        if (items.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        // 재귀적 배치 처리를 위한 Runnable 구현
        execute(new Runnable() {
            private int index = 0; // 현재 처리 위치

            @Override
            public void run() {
                try {
                    int processed = 0;

                    // 배치 크기만큼 또는 남은 아이템만큼 처리
                    while (index < items.size() && processed < batchSize) {
                        // processor.accept(): Consumer 함수로 아이템 처리
                        processor.accept(items.get(index));
                        index++;
                        processed++;
                    }

                    if (index < items.size()) {
                        // 처리할 아이템이 더 있으면 다음 배치를 스케줄링
                        // EventLoop에 자기 자신을 다시 스케줄링 (재귀)
                        eventLoop.execute(this);
                    } else {
                        // 모든 항목 처리 완료
                        future.complete(null);
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });

        return future;
    }

    /**
     * 스케줄러 종료
     *
     * EventQueue가 사용하는 스케줄러 스레드를 안전하게 종료
     * 애플리케이션 종료시 리소스 정리
     */
    public void shutdown() {
        logger.info("EventQueue 스케줄러 종료 중...");

        // shutdown(): 새로운 작업 제출을 거부하고 기존 작업은 완료 대기
        scheduler.shutdown();

        try {
            // awaitTermination(): 지정된 시간 동안 종료 대기
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                // 5초 내에 종료되지 않으면 강제 종료
                // shutdownNow(): 실행 중인 작업 중단하고 즉시 종료
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            // 인터럽트 발생시 즉시 강제 종료
            scheduler.shutdownNow();
            // interrupt(): 현재 스레드의 인터럽트 상태 복원
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 통계 정보
     *
     * EventQueue의 현재 상태와 성능 지표 제공
     *
     * @return QueueStats 객체
     */
    public QueueStats getStats() {
        return new QueueStats(
                taskIdGenerator.get(),              // 총 생성된 작업 수
                eventLoop.getQueuedTaskCount(),     // 대기 중인 작업 수
                scheduler.isShutdown()              // 스케줄러 종료 여부
        );
    }

    /**
     * 큐 통계 정보 클래스
     *
     * EventQueue의 상태를 나타내는 불변 객체
     */
    public static class QueueStats {
        // final: 생성 후 변경 불가능한 필드들
        private final long totalTasks;      // 총 처리된 작업 수
        private final int queuedTasks;      // 현재 대기 중인 작업 수
        private final boolean shutdown;     // 종료 상태

        /**
         * 생성자 - 모든 통계 값을 한번에 초기화
         *
         * @param totalTasks 총 작업 수
         * @param queuedTasks 대기 중인 작업 수
         * @param shutdown 종료 상태
         */
        public QueueStats(long totalTasks, int queuedTasks, boolean shutdown) {
            this.totalTasks = totalTasks;
            this.queuedTasks = queuedTasks;
            this.shutdown = shutdown;
        }

        // === Getter 메서드들 ===

        /**
         * 총 작업 수 반환
         */
        public long getTotalTasks() {
            return totalTasks;
        }

        /**
         * 대기 중인 작업 수 반환
         */
        public int getQueuedTasks() {
            return queuedTasks;
        }

        /**
         * 종료 상태 반환
         */
        public boolean isShutdown() {
            return shutdown;
        }

        /**
         * 통계 정보의 문자열 표현
         *
         * 디버깅과 모니터링에 유용한 요약 정보
         *
         * @return 포맷된 통계 문자열
         */
        @Override
        public String toString() {
            // String.format(): printf 스타일의 문자열 포맷팅
            return String.format("QueueStats{total=%d, queued=%d, shutdown=%s}",
                    totalTasks, queuedTasks, shutdown);
        }
    }
}