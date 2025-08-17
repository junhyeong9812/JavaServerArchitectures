package server.eventloop;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

/**
 * NIO Selector 관리자
 * 채널 등록, 이벤트 관리, 리소스 정리 담당
 */
public class SelectorManager {

    private static final Logger logger = LoggerFactory.getLogger(SelectorManager.class);

    private final EventLoop eventLoop;
    private final Selector selector;
    private final Map<SocketChannel, ChannelContext> channelContexts;
    private final AtomicLong channelIdGenerator;

    // 성능 통계
    private volatile long totalConnections;
    private volatile long activeConnections;
    private volatile long bytesRead;
    private volatile long bytesWritten;

    public SelectorManager(EventLoop eventLoop, Selector selector) {
        this.eventLoop = eventLoop;
        this.selector = selector;
        this.channelContexts = new ConcurrentHashMap<>();
        this.channelIdGenerator = new AtomicLong(0);
    }

    /**
     * 서버 소켓 등록
     */
    public void registerServerSocket(ServerSocketChannel serverChannel,
                                     ServerSocketEventHandler handler) throws IOException {
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> {
                try {
                    registerServerSocket(serverChannel, handler);
                } catch (IOException e) {
                    logger.error("Failed to register server socket", e);
                }
            });
            return;
        }

        serverChannel.configureBlocking(false);
        SelectionKey key = serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        key.attach(handler);

        logger.info("Registered server socket: {}", serverChannel.getLocalAddress());
    }

    /**
     * 클라이언트 소켓 등록
     */
    public void registerClientSocket(SocketChannel clientChannel,
                                     ClientSocketEventHandler handler) {
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> registerClientSocket(clientChannel, handler));
            return;
        }

        try {
            clientChannel.configureBlocking(false);

            // ChannelContext 생성 (통계 및 관리용)
            long channelId = channelIdGenerator.incrementAndGet();
            ChannelContext context = new ChannelContext(channelId, clientChannel, handler);
            channelContexts.put(clientChannel, context);

            // ⭐ 핵심 수정: handler를 직접 attachment로 설정
            SelectionKey key = clientChannel.register(selector, SelectionKey.OP_READ);
            key.attach(handler); // ChannelContext 대신 handler 직접 설정

            totalConnections++;
            activeConnections++;

            logger.debug("Registered client socket [{}]: {}",
                    channelId, clientChannel.getRemoteAddress());

        } catch (IOException e) {
            logger.error("Failed to register client socket", e);
            closeChannel(clientChannel);
        }
    }

    /**
     * 채널에서 데이터 읽기
     */
    public int readFromChannel(SocketChannel channel, ByteBuffer buffer) throws IOException {
        int bytesRead = channel.read(buffer);

        if (bytesRead > 0) {
            this.bytesRead += bytesRead;

            ChannelContext context = channelContexts.get(channel);
            if (context != null) {
                context.updateLastActivity();
                context.addBytesRead(bytesRead);
            }
        }

        return bytesRead;
    }

    /**
     * 채널에 데이터 쓰기
     */
    public int writeToChannel(SocketChannel channel, ByteBuffer buffer) throws IOException {
        int bytesWritten = channel.write(buffer);

        if (bytesWritten > 0) {
            this.bytesWritten += bytesWritten;

            ChannelContext context = channelContexts.get(channel);
            if (context != null) {
                context.updateLastActivity();
                context.addBytesWritten(bytesWritten);
            }
        }

        return bytesWritten;
    }

    /**
     * 쓰기 이벤트 활성화
     */
    public void enableWrite(SocketChannel channel) {
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> enableWrite(channel));
            return;
        }

        SelectionKey key = channel.keyFor(selector);
        if (key != null && key.isValid()) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            logger.debug("Enabled write for channel: {}", getChannelId(channel));
        }
    }

    /**
     * 쓰기 이벤트 비활성화
     */
    public void disableWrite(SocketChannel channel) {
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> disableWrite(channel));
            return;
        }

        SelectionKey key = channel.keyFor(selector);
        if (key != null && key.isValid()) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            logger.debug("Disabled write for channel: {}", getChannelId(channel));
        }
    }

    /**
     * 채널 안전 종료
     */
    public void closeChannel(SocketChannel channel) {
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> closeChannel(channel));
            return;
        }

        ChannelContext context = channelContexts.remove(channel);
        if (context != null) {
            activeConnections--;
            logger.debug("Closing channel [{}]: {} (lifetime: {}ms, read: {} bytes, written: {} bytes)",
                    context.getChannelId(),
                    getRemoteAddress(channel),
                    context.getLifetimeMillis(),
                    context.getBytesRead(),
                    context.getBytesWritten());
        }

        try {
            SelectionKey key = channel.keyFor(selector);
            if (key != null) {
                key.cancel();
            }

            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            logger.error("Error closing channel", e);
        }
    }

    /**
     * 연결 타임아웃 확인 및 정리
     */
    public void cleanupTimeoutConnections(long timeoutMillis) {
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> cleanupTimeoutConnections(timeoutMillis));
            return;
        }

        long currentTime = System.currentTimeMillis();
        int timeoutCount = 0;

        for (Map.Entry<SocketChannel, ChannelContext> entry : channelContexts.entrySet()) {
            ChannelContext context = entry.getValue();
            if (currentTime - context.getLastActivity() > timeoutMillis) {
                logger.debug("Closing timeout connection [{}]: {} (inactive for {}ms)",
                        context.getChannelId(),
                        getRemoteAddress(entry.getKey()),
                        currentTime - context.getLastActivity());

                closeChannel(entry.getKey());
                timeoutCount++;
            }
        }

        if (timeoutCount > 0) {
            logger.info("Closed {} timeout connections", timeoutCount);
        }
    }

    /**
     * 채널 컨텍스트 가져오기
     */
    public ChannelContext getChannelContext(SocketChannel channel) {
        return channelContexts.get(channel);
    }

    /**
     * 채널 ID 가져오기
     */
    public long getChannelId(SocketChannel channel) {
        ChannelContext context = channelContexts.get(channel);
        return context != null ? context.getChannelId() : -1;
    }

    /**
     * 원격 주소 안전 가져오기
     */
    private String getRemoteAddress(SocketChannel channel) {
        try {
            return channel.getRemoteAddress().toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 모든 연결 강제 종료
     */
    public void closeAllConnections() {
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(this::closeAllConnections);
            return;
        }

        logger.info("Closing all {} active connections", activeConnections);

        for (SocketChannel channel : channelContexts.keySet()) {
            closeChannel(channel);
        }

        channelContexts.clear();
        activeConnections = 0;
    }

    /**
     * 통계 정보
     */
    public SelectorStats getStats() {
        return new SelectorStats(
                totalConnections,
                activeConnections,
                bytesRead,
                bytesWritten,
                channelContexts.size()
        );
    }

    /**
     * 채널 컨텍스트 클래스
     */
    public static class ChannelContext {
        private final long channelId;
        private final SocketChannel channel;
        private final ClientSocketEventHandler handler;
        private final long createdTime;
        private volatile long lastActivity;
        private volatile long bytesRead;
        private volatile long bytesWritten;

        public ChannelContext(long channelId, SocketChannel channel, ClientSocketEventHandler handler) {
            this.channelId = channelId;
            this.channel = channel;
            this.handler = handler;
            this.createdTime = System.currentTimeMillis();
            this.lastActivity = createdTime;
        }

        public void updateLastActivity() {
            this.lastActivity = System.currentTimeMillis();
        }

        public void addBytesRead(long bytes) {
            this.bytesRead += bytes;
        }

        public void addBytesWritten(long bytes) {
            this.bytesWritten += bytes;
        }

        public long getChannelId() { return channelId; }
        public SocketChannel getChannel() { return channel; }
        public ClientSocketEventHandler getHandler() { return handler; }
        public long getCreatedTime() { return createdTime; }
        public long getLastActivity() { return lastActivity; }
        public long getBytesRead() { return bytesRead; }
        public long getBytesWritten() { return bytesWritten; }
        public long getLifetimeMillis() { return System.currentTimeMillis() - createdTime; }

        @Override
        public String toString() {
            return String.format("ChannelContext{id=%d, lifetime=%dms, read=%d, written=%d}",
                    channelId, getLifetimeMillis(), bytesRead, bytesWritten);
        }
    }

    /**
     * Selector 통계 정보
     */
    public static class SelectorStats {
        private final long totalConnections;
        private final long activeConnections;
        private final long bytesRead;
        private final long bytesWritten;
        private final int channelContextCount;

        public SelectorStats(long totalConnections, long activeConnections,
                             long bytesRead, long bytesWritten, int channelContextCount) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.bytesRead = bytesRead;
            this.bytesWritten = bytesWritten;
            this.channelContextCount = channelContextCount;
        }

        public long getTotalConnections() { return totalConnections; }
        public long getActiveConnections() { return activeConnections; }
        public long getBytesRead() { return bytesRead; }
        public long getBytesWritten() { return bytesWritten; }
        public int getChannelContextCount() { return channelContextCount; }

        @Override
        public String toString() {
            return String.format("SelectorStats{total=%d, active=%d, read=%dMB, written=%dMB}",
                    totalConnections, activeConnections, bytesRead / (1024*1024), bytesWritten / (1024*1024));
        }
    }
}