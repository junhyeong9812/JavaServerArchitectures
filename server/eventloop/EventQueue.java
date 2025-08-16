package server.eventloop;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 비동기 작업 큐
 * EventLoop와 연계하여 논블로킹 작업 스케줄링
 */
public class EventQueue {

    private static final Logger logger = LoggerFactory.getLogger(EventQueue.class);

    private final EventLoop eventLoop;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong taskIdGenerator;

    public EventQueue(EventLoop eventLoop) {
        this.eventLoop = eventLoop;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EventQueue-Scheduler");
            t.setDaemon(true);
            return t;
        });
        this.taskIdGenerator = new AtomicLong(0);
    }

    /**
     * 즉시 실행할 작업 추가
     */
    public void execute(Runnable task) {
        if (eventLoop.inEventLoop()) {
            // 이미 이벤트루프 스레드에서 실행 중이면 바로 실행
            task.run();
        } else {
            // 다른 스레드에서 호출되면 이벤트루프에 스케줄링
            eventLoop.execute(task);
        }
    }

    /**
     * 비동기 작업 실행 (CompletableFuture 반환)
     */
    public <T> CompletableFuture<T> executeAsync(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();

        execute(() -> {
            try {
                T result = task.get();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * 지연 실행 작업 스케줄링
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return scheduler.schedule(() -> eventLoop.execute(task), delay, unit);
    }

    /**
     * 지연 실행 비동기 작업
     */
    public <T> CompletableFuture<T> scheduleAsync(Supplier<T> task, long delay, TimeUnit unit) {
        CompletableFuture<T> future = new CompletableFuture<>();

        scheduler.schedule(() -> {
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
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task,
                                                  long initialDelay,
                                                  long period,
                                                  TimeUnit unit) {
        return scheduler.scheduleAtFixedRate(
                () -> eventLoop.execute(task),
                initialDelay,
                period,
                unit
        );
    }

    /**
     * 타임아웃이 있는 비동기 작업
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
            if (!future.isDone()) {
                future.completeExceptionally(
                        new TimeoutException("Task timed out after " + timeout + " " + unit)
                );
            }
        }, timeout, unit);

        return future;
    }

    /**
     * Promise 패턴 지원
     */
    public static class Promise<T> {
        private final CompletableFuture<T> future = new CompletableFuture<>();

        public void resolve(T value) {
            future.complete(value);
        }

        public void reject(Throwable error) {
            future.completeExceptionally(error);
        }

        public CompletableFuture<T> getFuture() {
            return future;
        }

        public boolean isResolved() {
            return future.isDone() && !future.isCompletedExceptionally();
        }

        public boolean isRejected() {
            return future.isCompletedExceptionally();
        }
    }

    /**
     * Promise 생성
     */
    public <T> Promise<T> createPromise() {
        return new Promise<>();
    }

    /**
     * 여러 작업을 병렬로 실행 (이벤트루프에서 순차 처리)
     */
    public CompletableFuture<Void> executeAll(Runnable... tasks) {
        if (tasks.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        execute(() -> {
            try {
                for (Runnable task : tasks) {
                    task.run();
                }
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * 작업 체인 실행
     */
    public <T> CompletableFuture<T> chain(Supplier<CompletableFuture<T>> taskChain) {
        if (eventLoop.inEventLoop()) {
            try {
                return taskChain.get();
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        } else {
            CompletableFuture<T> future = new CompletableFuture<>();

            eventLoop.execute(() -> {
                try {
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
     */
    public CompletableFuture<Void> executeIf(Supplier<Boolean> condition, Runnable task) {
        return executeAsync(() -> {
            if (condition.get()) {
                task.run();
            }
            return null;
        });
    }

    /**
     * 재시도 로직
     */
    public <T> CompletableFuture<T> retry(Supplier<T> task, int maxRetries, long retryDelay, TimeUnit unit) {
        return retryInternal(task, maxRetries, 0, retryDelay, unit);
    }

    private <T> CompletableFuture<T> retryInternal(Supplier<T> task, int maxRetries, int currentAttempt,
                                                   long retryDelay, TimeUnit unit) {
        return executeAsync(task).exceptionallyCompose(error -> {
            if (currentAttempt >= maxRetries) {
                return CompletableFuture.failedFuture(error);
            }

            logger.debug("Task failed (attempt {}/{}), retrying in {} {}: {}",
                    currentAttempt + 1, maxRetries + 1, retryDelay, unit, error.getMessage());

            return scheduleAsync(() -> null, retryDelay, unit)
                    .thenCompose(v -> retryInternal(task, maxRetries, currentAttempt + 1, retryDelay, unit));
        });
    }

    /**
     * 배치 처리
     */
    public <T> CompletableFuture<Void> processBatch(java.util.List<T> items,
                                                    java.util.function.Consumer<T> processor,
                                                    int batchSize) {
        if (items.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        execute(new Runnable() {
            private int index = 0;

            @Override
            public void run() {
                try {
                    int processed = 0;
                    while (index < items.size() && processed < batchSize) {
                        processor.accept(items.get(index));
                        index++;
                        processed++;
                    }

                    if (index < items.size()) {
                        // 다음 배치를 스케줄링
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
     */
    public void shutdown() {
        logger.info("Shutting down EventQueue scheduler...");

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 통계 정보
     */
    public QueueStats getStats() {
        return new QueueStats(
                taskIdGenerator.get(),
                eventLoop.getQueuedTaskCount(),
                scheduler.isShutdown()
        );
    }

    public static class QueueStats {
        private final long totalTasks;
        private final int queuedTasks;
        private final boolean shutdown;

        public QueueStats(long totalTasks, int queuedTasks, boolean shutdown) {
            this.totalTasks = totalTasks;
            this.queuedTasks = queuedTasks;
            this.shutdown = shutdown;
        }

        public long getTotalTasks() { return totalTasks; }
        public int getQueuedTasks() { return queuedTasks; }
        public boolean isShutdown() { return shutdown; }

        @Override
        public String toString() {
            return String.format("QueueStats{total=%d, queued=%d, shutdown=%s}",
                    totalTasks, queuedTasks, shutdown);
        }
    }
}