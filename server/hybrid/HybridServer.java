package server.hybrid;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.http.*;
import server.core.routing.*;
import server.core.mini.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * 하이브리드 HTTP 서버 - NIO + Thread Pool 조합
 *
 * 핵심 개념:
 * 1. NIO Selector로 I/O 이벤트 감지 (Non-blocking)
 * 2. Thread Pool에서 CPU 집약적 작업 처리
 * 3. AsyncContext로 I/O 대기시 스레드 해제
 * 4. 컨텍스트 스위칭으로 효율적 자원 활용
 *
 * Threaded vs Hybrid 차이점:
 * - Threaded: 요청당 스레드 할당, I/O 대기시에도 스레드 점유
 * - Hybrid: I/O는 NIO로 감지, CPU 작업만 스레드 사용
 */
public class HybridServer {

    // === 로깅 시스템 ===
    private static final Logger logger = LoggerFactory.getLogger(HybridServer.class);

    // === 서버 설정 ===
    private final int port;                              // 서버 포트 번호
    private final ServerSocketChannel serverChannel;    // NIO 서버 소켓 채널
    private final Selector selector;                     // NIO Selector - I/O 이벤트 멀티플렉싱

    // === 실행 상태 관리 ===
    private final AtomicBoolean running = new AtomicBoolean(false);     // 서버 실행 상태
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false); // 종료 요청 플래그
    private Thread selectorThread;                       // Selector 루프 실행 스레드

    // === 하이브리드 처리 컴포넌트 ===
    private final HybridProcessor processor;             // 하이브리드 요청 처리기
    private final AsyncContextManager contextManager;    // 비동기 컨텍스트 관리자
    private final AdaptiveThreadPool threadPool;         // 적응형 스레드풀
    private final ContextSwitchingHandler switchingHandler; // 컨텍스트 스위칭 핸들러

    // === 라우팅 및 서블릿 ===
    private final Router router;                         // HTTP 라우팅 시스템
    private final HybridMiniServletContainer servletContainer; // 하이브리드 서블릿 컨테이너

    // === 연결 관리 ===
    private final Map<SocketChannel, ChannelContext> channelContexts; // 채널별 컨텍스트 맵
    private final AtomicLong connectionCounter = new AtomicLong(0);    // 연결 카운터

    // === 성능 메트릭 ===
    private final AtomicLong totalRequests = new AtomicLong(0);        // 총 요청 수
    private final AtomicLong activeConnections = new AtomicLong(0);     // 활성 연결 수
    private final AtomicLong contextSwitches = new AtomicLong(0);       // 컨텍스트 스위치 횟수

    /**
     * HybridServer 생성자
     *
     * @param port 서버 포트 번호
     * @throws IOException NIO 초기화 실패시
     */
    public HybridServer(int port) throws IOException {
        this.port = port;

        // NIO 컴포넌트 초기화
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverChannel.bind(new InetSocketAddress(port));
        this.selector = Selector.open();
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // 하이브리드 처리 컴포넌트 초기화
        this.threadPool = new AdaptiveThreadPool("Hybrid-Worker", 4, 64, 60L);
        this.contextManager = new AsyncContextManager();
        this.switchingHandler = new ContextSwitchingHandler(threadPool, contextManager);
        this.processor = new HybridProcessor(threadPool, contextManager);

        // 라우팅 시스템 초기화
        this.router = new Router();
        this.servletContainer = new HybridMiniServletContainer(router, processor);

        // 동시성 안전한 컬렉션으로 채널 컨텍스트 관리
        this.channelContexts = new ConcurrentHashMap<>();

        logger.info("HybridServer 초기화 완료 - 포트: {}, NIO + Thread Pool 모드", port);
    }

    /**
     * 서버 시작
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            logger.warn("서버가 이미 실행 중입니다");
            return;
        }

        logger.info("HybridServer 시작 중... 포트: {}", port);

        selectorThread = new Thread(this::runSelectorLoop, "Hybrid-Selector");
        selectorThread.setDaemon(false);
        selectorThread.start();

        logger.info("HybridServer 시작 완료 - NIO Selector 루프 실행 중");
    }

    /**
     * NIO Selector 이벤트 루프 - 하이브리드 서버의 핵심!
     */
    private void runSelectorLoop() {
        logger.info("NIO Selector 루프 시작");

        try {
            while (running.get() && !shutdownRequested.get()) {
                int readyChannels = selector.select(1000);

                if (readyChannels == 0) {
                    continue;
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
                    } catch (IOException e) {
                        logger.warn("채널 처리 중 오류 발생: {}", e.getMessage());
                        closeChannel(key);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Selector 루프 오류", e);
        } finally {
            cleanup();
            logger.info("NIO Selector 루프 종료");
        }
    }

    /**
     * 새로운 클라이언트 연결 수락
     */
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);

            long connectionId = connectionCounter.incrementAndGet();
            ChannelContext context = new ChannelContext(connectionId, clientChannel);

            clientKey.attach(context);
            channelContexts.put(clientChannel, context);
            activeConnections.incrementAndGet();

            logger.debug("새 연결 수락 - ID: {}, 활성 연결: {}", connectionId, activeConnections.get());
        }
    }

    /**
     * 클라이언트로부터 데이터 읽기
     */
    private void handleRead(SelectionKey key) throws IOException {
        ChannelContext context = (ChannelContext) key.attachment();

        if (context == null) {
            logger.warn("컨텍스트가 없는 READ 이벤트");
            closeChannel(key);
            return;
        }

        SocketChannel channel = context.getChannel();
        ByteBuffer buffer = ByteBuffer.allocate(8192);

        try {
            int bytesRead = channel.read(buffer);

            if (bytesRead == -1) {
                logger.debug("클라이언트 연결 종료 - ID: {}", context.getConnectionId());
                closeChannel(key);
                return;
            }

            if (bytesRead == 0) {
                return;
            }

            buffer.flip();
            context.appendData(buffer);

            if (context.isRequestComplete()) {
                processCompleteRequest(context, key);
            }

        } catch (IOException e) {
            logger.warn("데이터 읽기 실패 - 연결 ID: {}, 오류: {}", context.getConnectionId(), e.getMessage());
            closeChannel(key);
        }
    }

    /**
     * 완전한 HTTP 요청을 Thread Pool에서 처리
     */
    private void processCompleteRequest(ChannelContext context, SelectionKey key) {
        totalRequests.incrementAndGet();
        key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);

        threadPool.submit(() -> {
            try {
                contextSwitches.incrementAndGet();

                logger.debug("Thread Pool에서 요청 처리 시작 - 연결 ID: {}, 스레드: {}",
                        context.getConnectionId(), Thread.currentThread().getName());

                // HTTP 요청 파싱 - Core의 HttpParser 사용
                String requestData = context.getRequestData();
                ByteArrayInputStream inputStream = new ByteArrayInputStream(
                        requestData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                HttpRequest request = HttpParser.parseRequest(inputStream);

                // 라우터를 통한 요청 처리
                CompletableFuture<HttpResponse> responseFuture = router.route(request);

                responseFuture.whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        logger.error("요청 처리 중 오류", throwable);
                        response = HttpResponse.internalServerError("Internal Server Error");
                    }

                    context.setResponse(response);
                    selector.wakeup();
                    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);

                    logger.debug("응답 준비 완료 - 연결 ID: {}", context.getConnectionId());
                });

            } catch (Exception e) {
                logger.error("요청 처리 실패 - 연결 ID: {}", context.getConnectionId(), e);
                context.setResponse(HttpResponse.internalServerError("Internal Server Error"));
                selector.wakeup();
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            }
        });
    }

    /**
     * 클라이언트에게 응답 데이터 쓰기
     */
    private void handleWrite(SelectionKey key) throws IOException {
        ChannelContext context = (ChannelContext) key.attachment();

        if (context == null || !context.hasResponse()) {
            return;
        }

        SocketChannel channel = context.getChannel();
        HttpResponse response = context.getResponse();

        byte[] responseBytes = response.toByteArray();
        ByteBuffer buffer = ByteBuffer.wrap(responseBytes);

        try {
            int bytesWritten = channel.write(buffer);

            logger.debug("응답 전송 완료 - 연결 ID: {}, 바이트: {}",
                    context.getConnectionId(), bytesWritten);

            closeChannel(key);

        } catch (IOException e) {
            logger.warn("응답 쓰기 실패 - 연결 ID: {}", context.getConnectionId(), e);
            closeChannel(key);
        }
    }

    /**
     * 채널 정리 및 종료
     */
    private void closeChannel(SelectionKey key) {
        try {
            ChannelContext context = (ChannelContext) key.attachment();

            if (context != null) {
                SocketChannel channel = context.getChannel();
                channelContexts.remove(channel);
                activeConnections.decrementAndGet();

                logger.debug("연결 종료 - ID: {}, 활성 연결: {}",
                        context.getConnectionId(), activeConnections.get());
            }

            key.cancel();
            if (key.channel().isOpen()) {
                key.channel().close();
            }

        } catch (IOException e) {
            logger.warn("채널 종료 중 오류", e);
        }
    }

    /**
     * 서버 우아한 종료
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            logger.warn("서버가 이미 중지되었습니다");
            return;
        }

        logger.info("HybridServer 종료 시작...");
        shutdownRequested.set(true);
        selector.wakeup();

        try {
            if (selectorThread != null) {
                selectorThread.join(5000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("서버 종료 대기 중 인터럽트 발생");
        }

        logger.info("HybridServer 종료 완료");
    }

    /**
     * 리소스 정리
     */
    private void cleanup() {
        try {
            for (ChannelContext context : channelContexts.values()) {
                try {
                    context.getChannel().close();
                } catch (IOException e) {
                    logger.warn("채널 정리 중 오류", e);
                }
            }
            channelContexts.clear();

            threadPool.shutdown();

            if (selector.isOpen()) {
                selector.close();
            }

            if (serverChannel.isOpen()) {
                serverChannel.close();
            }

        } catch (IOException e) {
            logger.error("리소스 정리 중 오류", e);
        }
    }

    // === Getters ===

    public Router getRouter() {
        return router;
    }

    public HybridMiniServletContainer getServletContainer() {
        return servletContainer;
    }

    public ContextSwitchingHandler getSwitchingHandler() {
        return switchingHandler;
    }

    public ServerStatus getStatus() {
        return new ServerStatus(
                running.get(),
                activeConnections.get(),
                totalRequests.get(),
                contextSwitches.get(),
                threadPool.getActiveCount(),
                threadPool.getPoolSize()
        );
    }

    /**
     * 서버 상태 정보 클래스
     */
    public static class ServerStatus {
        private final boolean running;
        private final long activeConnections;
        private final long totalRequests;
        private final long contextSwitches;
        private final int activeThreads;
        private final int totalThreads;

        public ServerStatus(boolean running, long activeConnections, long totalRequests,
                            long contextSwitches, int activeThreads, int totalThreads) {
            this.running = running;
            this.activeConnections = activeConnections;
            this.totalRequests = totalRequests;
            this.contextSwitches = contextSwitches;
            this.activeThreads = activeThreads;
            this.totalThreads = totalThreads;
        }

        // Getters
        public boolean isRunning() { return running; }
        public long getActiveConnections() { return activeConnections; }
        public long getTotalRequests() { return totalRequests; }
        public long getContextSwitches() { return contextSwitches; }
        public int getActiveThreads() { return activeThreads; }
        public int getTotalThreads() { return totalThreads; }

        @Override
        public String toString() {
            return String.format(
                    "HybridServer{running=%s, connections=%d, requests=%d, " +
                            "contextSwitches=%d, threads=%d/%d}",
                    running, activeConnections, totalRequests,
                    contextSwitches, activeThreads, totalThreads
            );
        }
    }
}