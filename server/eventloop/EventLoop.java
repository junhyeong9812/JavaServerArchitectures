package server.eventloop;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Iterator;
import java.util.Set;
import java.io.IOException;

/**
 * 핵심 이벤트루프 엔진
 * 단일 스레드에서 모든 I/O 이벤트를 처리
 */
public class EventLoop {

    private static final Logger logger = LoggerFactory.getLogger(EventLoop.class);

    private final Selector selector;
    private final Thread eventLoopThread;
    private final AtomicBoolean running;
    private final ConcurrentLinkedQueue<Runnable> taskQueue;
    private final AtomicLong taskCount;

    // 성능 통계
    private volatile long lastLoopTime;
    private volatile long totalLoops;
    private volatile long totalTasksExecuted;

    public EventLoop() throws IOException {
        this.selector = Selector.open();
        this.running = new AtomicBoolean(false);
        this.taskQueue = new ConcurrentLinkedQueue<>();
        this.taskCount = new AtomicLong(0);
        this.eventLoopThread = new Thread(this::runEventLoop, "EventLoop-Main");
        this.eventLoopThread.setDaemon(false);
    }

    /**
     * 이벤트루프 시작
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting EventLoop...");
            eventLoopThread.start();
        }
    }

    /**
     * 이벤트루프 종료
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            logger.info("Shutting down EventLoop...");
            selector.wakeup();

            try {
                eventLoopThread.join(5000);
                selector.close();
            } catch (Exception e) {
                logger.error("Error during EventLoop shutdown", e);
            }
        }
    }

    /**
     * Selector 반환 (SelectorManager에서 사용)
     */
    public Selector getSelector() {
        return selector;
    }

    /**
     * 서버 소켓 채널 등록
     */
    public void registerServerSocket(ServerSocketChannel serverChannel,
                                     ServerSocketEventHandler handler) {
        execute(() -> {
            try {
                serverChannel.configureBlocking(false);
                SelectionKey key = serverChannel.register(selector, SelectionKey.OP_ACCEPT);
                key.attach(handler);
                logger.info("Registered server socket on {}",
                        serverChannel.getLocalAddress());
            } catch (IOException e) {
                logger.error("Failed to register server socket", e);
            }
        });
    }

    /**
     * 클라이언트 소켓 채널 등록
     */
    public void registerClientSocket(SocketChannel clientChannel,
                                     ClientSocketEventHandler handler) {
        execute(() -> {
            try {
                clientChannel.configureBlocking(false);
                SelectionKey key = clientChannel.register(selector,
                        SelectionKey.OP_READ);
                key.attach(handler);
                logger.debug("Registered client socket {}",
                        clientChannel.getRemoteAddress());
            } catch (IOException e) {
                logger.error("Failed to register client socket", e);
                closeChannel(clientChannel);
            }
        });
    }

    /**
     * 비동기 작업을 이벤트루프에 스케줄링
     */
    public void execute(Runnable task) {
        if (task == null) return;

        taskQueue.offer(task);
        taskCount.incrementAndGet();
        selector.wakeup(); // 블로킹된 select() 호출을 깨움
    }

    /**
     * 현재 스레드가 이벤트루프 스레드인지 확인
     */
    public boolean inEventLoop() {
        return Thread.currentThread() == eventLoopThread;
    }

    /**
     * 메인 이벤트루프 실행
     */
    private void runEventLoop() {
        logger.info("EventLoop started on thread: {}",
                Thread.currentThread().getName());

        while (running.get()) {
            try {
                long loopStart = System.nanoTime();

                // 1. I/O 이벤트 처리
                processIOEvents();

                // 2. 큐에 쌓인 작업들 처리
                processTasks();

                // 3. 통계 업데이트
                updateStatistics(loopStart);

            } catch (OutOfMemoryError | StackOverflowError fatal) {
                // 치명적 오류 - 즉시 종료
                logger.error("Fatal error in event loop: {}", fatal.getClass().getSimpleName(), fatal);
                break;
            } catch (Exception e) {
                // 일반적인 예외 - 로그 남기고 계속 실행
                logger.error("Error in event loop", e);

                // 연속적인 오류 발생시 잠시 대기
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Throwable throwable) {
                // 기타 심각한 오류
                logger.error("Serious error in event loop", throwable);
                break;
            }
        }

        logger.info("EventLoop stopped. Total loops: {}, Total tasks: {}",
                totalLoops, totalTasksExecuted);
    }

    /**
     * I/O 이벤트 처리
     */
    private void processIOEvents() throws IOException {
        int readyChannels = selector.select(1000); // 1초 타임아웃

        if (readyChannels == 0) {
            return; // 타임아웃 또는 wakeup() 호출
        }

        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        while (keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();
            keyIterator.remove();

            if (!key.isValid()) {
                continue;
            }

            try {
                if (key.isAcceptable()) {
                    handleAccept(key);
                } else if (key.isReadable()) {
                    handleRead(key);
                } else if (key.isWritable()) {
                    handleWrite(key);
                }
            } catch (Exception e) {
                logger.error("Error processing I/O event for key: {}", key, e);
                closeKey(key);
            }
        }
    }

    /**
     * 새로운 연결 수락
     */
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        ServerSocketEventHandler handler = (ServerSocketEventHandler) key.attachment();

        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel != null) {
            logger.debug("Accepted new connection from {}",
                    clientChannel.getRemoteAddress());
            handler.onAccept(this, clientChannel);
        }
    }

    /**
     * 데이터 읽기
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientSocketEventHandler handler = (ClientSocketEventHandler) key.attachment();

        ByteBuffer buffer = ByteBuffer.allocate(8192);
        int bytesRead = channel.read(buffer);

        if (bytesRead > 0) {
            buffer.flip();
            handler.onRead(this, channel, buffer);
        } else if (bytesRead == -1) {
            // 클라이언트가 연결을 닫음
            logger.debug("Client closed connection: {}", channel.getRemoteAddress());
            handler.onDisconnect(this, channel);
            closeKey(key);
        }
    }

    /**
     * 데이터 쓰기
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientSocketEventHandler handler = (ClientSocketEventHandler) key.attachment();

        handler.onWrite(this, channel);

        // 쓰기 완료 후 WRITE interest 제거
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
    }

    /**
     * 큐에 쌓인 작업들 처리
     */
    private void processTasks() {
        int processedTasks = 0;
        Runnable task;

        // 한 번에 너무 많은 작업을 처리하지 않도록 제한
        while (processedTasks < 10000 && (task = taskQueue.poll()) != null) {
            try {
                task.run();
                processedTasks++;
                totalTasksExecuted++;
            } catch (Exception e) {
                logger.error("Error executing task", e);
            }
        }

        if (processedTasks > 0) {
            logger.debug("Processed {} tasks", processedTasks);
        }
    }

    /**
     * 통계 정보 업데이트
     */
    private void updateStatistics(long loopStart) {
        totalLoops++;
        lastLoopTime = System.nanoTime() - loopStart;

        // 주기적으로 통계 로그 출력
        if (totalLoops % 10000 == 0) {
            logger.debug("EventLoop stats - Loops: {}, Tasks: {}, Last loop: {}μs",
                    totalLoops, totalTasksExecuted, lastLoopTime / 1000);
        }
    }

    /**
     * SelectionKey 안전 종료
     */
    private void closeKey(SelectionKey key) {
        try {
            key.cancel();
            Channel channel = key.channel();
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            logger.error("Error closing key/channel", e);
        }
    }

    /**
     * 채널 안전 종료
     */
    private void closeChannel(Channel channel) {
        try {
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            logger.error("Error closing channel", e);
        }
    }

    /**
     * 쓰기 준비 상태로 변경
     */
    public void enableWrite(SocketChannel channel) {
        execute(() -> {
            SelectionKey key = channel.keyFor(selector);
            if (key != null && key.isValid()) {
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            }
        });
    }

    // === 성능 통계 ===

    public long getTotalLoops() { return totalLoops; }
    public long getTotalTasksExecuted() { return totalTasksExecuted; }
    public long getLastLoopTimeNanos() { return lastLoopTime; }
    public int getQueuedTaskCount() { return taskQueue.size(); }
    public boolean isRunning() { return running.get(); }

    @Override
    public String toString() {
        return String.format("EventLoop{running=%s, loops=%d, tasks=%d, queued=%d}",
                running.get(), totalLoops, totalTasksExecuted, taskQueue.size());
    }
}