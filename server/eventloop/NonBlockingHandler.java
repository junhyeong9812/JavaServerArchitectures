package server.eventloop;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.http.*;
import server.core.routing.Router;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 완전 논블로킹 HTTP 핸들러 (수정된 버전)
 * 모든 I/O 작업이 논블로킹으로 처리됨
 */
public class NonBlockingHandler implements ServerSocketEventHandler, ClientSocketEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(NonBlockingHandler.class);

    private final Router router;
    private final SelectorManager selectorManager;
    private final EventQueue eventQueue;
    private final Map<SocketChannel, ConnectionState> connectionStates;

    // 설정
    private final int maxRequestSize;
    private final int responseBufferSize;
    private final long connectionTimeout;

    public NonBlockingHandler(Router router, SelectorManager selectorManager, EventQueue eventQueue) {
        this.router = router;
        this.selectorManager = selectorManager;
        this.eventQueue = eventQueue;
        this.connectionStates = new ConcurrentHashMap<>();

        // 기본 설정값
        this.maxRequestSize = 1024 * 1024; // 1MB
        this.responseBufferSize = 8192; // 8KB
        this.connectionTimeout = 30000; // 30초
    }

    // === ServerSocketEventHandler 구현 ===

    @Override
    public void onAccept(EventLoop eventLoop, SocketChannel clientChannel) {
        try {
            // 새로운 클라이언트 연결을 SelectorManager에 등록
            selectorManager.registerClientSocket(clientChannel, this);

            // 연결 상태 초기화
            ConnectionState state = new ConnectionState(clientChannel);
            connectionStates.put(clientChannel, state);

            logger.debug("Accepted new connection: {} (total connections: {})",
                    clientChannel.getRemoteAddress(),
                    connectionStates.size());

        } catch (IOException e) {
            logger.error("Error accepting connection", e);
            selectorManager.closeChannel(clientChannel);
        }
    }

    // === ClientSocketEventHandler 구현 ===

    @Override
    public void onRead(EventLoop eventLoop, SocketChannel channel, ByteBuffer buffer) throws IOException {
        ConnectionState state = connectionStates.get(channel);
        if (state == null) {
            logger.warn("No connection state found for channel: {}",
                    selectorManager.getChannelId(channel));
            selectorManager.closeChannel(channel);
            return;
        }

        try {
            // ⭐ 읽은 데이터를 연결 상태의 버퍼에 추가 (수정됨)
            state.appendData(buffer);

            // HTTP 요청 파싱 시도
            if (state.getState() == ConnectionState.State.READING_REQUEST) {
                tryParseRequest(channel, state);
            }

        } catch (Exception e) {
            logger.error("Error processing read data for channel: {}",
                    selectorManager.getChannelId(channel), e);
            sendErrorResponse(channel, state, HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public void onWrite(EventLoop eventLoop, SocketChannel channel) throws IOException {
        ConnectionState state = connectionStates.get(channel);
        if (state == null) {
            selectorManager.closeChannel(channel);
            return;
        }

        try {
            // 응답 데이터 쓰기
            boolean writeComplete = writeResponse(channel, state);

            if (writeComplete) {
                // 쓰기 완료
                handleWriteComplete(channel, state);
            }

        } catch (Exception e) {
            logger.error("Error writing response for channel: {}",
                    selectorManager.getChannelId(channel), e);
            selectorManager.closeChannel(channel);
        }
    }

    @Override
    public void onDisconnect(EventLoop eventLoop, SocketChannel channel) {
        ConnectionState state = connectionStates.remove(channel);
        if (state != null) {
            logger.debug("Connection disconnected: {} (lifetime: {}ms)",
                    selectorManager.getChannelId(channel),
                    state.getLifetimeMillis());
        }
    }

    // === HTTP 요청 처리 ===

    /**
     * ⭐ HTTP 요청 파싱 시도 (수정됨)
     */
    private void tryParseRequest(SocketChannel channel, ConnectionState state) {
        byte[] data = state.getBufferedData();

        // ⭐ 데이터가 없으면 대기
        if (data.length == 0) {
            return;
        }

        // 요청 크기 제한 확인
        if (data.length > maxRequestSize) {
            logger.warn("Request too large: {} bytes", data.length);
            sendErrorResponse(channel, state, HttpStatus.PAYLOAD_TOO_LARGE);
            return;
        }

        // HTTP 요청 헤더 완료 확인 (빈 줄 찾기)
        int headerEndIndex = findHeaderEnd(data);
        if (headerEndIndex == -1) {
            // 헤더가 아직 완료되지 않음
            logger.debug("Request header not complete yet, waiting for more data...");
            return;
        }

        try {
            // ⭐ HttpParser를 사용하여 요청 파싱 (ByteArrayInputStream 사용)
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            HttpRequest request = HttpParser.parseRequest(inputStream);

            if (request == null) {
                logger.warn("Failed to parse HTTP request");
                sendErrorResponse(channel, state, HttpStatus.BAD_REQUEST);
                return;
            }

            state.setRequest(request);
            state.setState(ConnectionState.State.PROCESSING_REQUEST);

            logger.debug("Parsed HTTP request: {} {}", request.getMethod(), request.getPath());

            // 라우터를 통한 요청 처리 (비동기)
            processRequestAsync(channel, state, request);

        } catch (Exception e) {
            logger.error("Error parsing HTTP request", e);
            sendErrorResponse(channel, state, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * HTTP 헤더 끝 찾기 (\r\n\r\n)
     */
    private int findHeaderEnd(byte[] data) {
        for (int i = 0; i < data.length - 3; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' &&
                    data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i + 4;
            }
        }
        return -1;
    }

    /**
     * 비동기 요청 처리
     */
    private void processRequestAsync(SocketChannel channel, ConnectionState state, HttpRequest request) {
        try {
            // 라우터를 통한 비동기 처리
            CompletableFuture<HttpResponse> responseFuture = router.routeWithMiddlewares(request);

            responseFuture.whenComplete((response, error) -> {
                eventQueue.execute(() -> {
                    if (error != null) {
                        logger.error("Error processing request", error);
                        sendErrorResponse(channel, state, HttpStatus.INTERNAL_SERVER_ERROR);
                    } else if (response == null) {
                        logger.warn("Router returned null response");
                        sendErrorResponse(channel, state, HttpStatus.NOT_FOUND);
                    } else {
                        sendResponse(channel, state, response);
                    }
                });
            });

        } catch (Exception e) {
            logger.error("Error in async request processing", e);
            sendErrorResponse(channel, state, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 정상 응답 전송
     */
    private void sendResponse(SocketChannel channel, ConnectionState state, HttpResponse response) {
        try {
            // Keep-Alive 헤더 처리
            HttpRequest request = state.getRequest();
            if (request != null && request.isKeepAlive() &&
                    response.getStatus().getCode() < 400) {
                response.setKeepAlive(true);
            } else {
                response.setKeepAlive(false);
            }

            // ⭐ 응답 상태 저장
            state.setResponseStatus(response.getStatus().getCode());

            // 응답 데이터를 바이트 배열로 변환
            byte[] responseBytes = response.toByteArray();

            state.setResponse(responseBytes);
            state.setState(ConnectionState.State.WRITING_RESPONSE);

            logger.debug("Sending response: {} bytes, status: {}",
                    responseBytes.length, response.getStatus().getCode());

            // 첫 번째 쓰기 시도
            boolean writeComplete = writeResponse(channel, state);

            if (!writeComplete) {
                // 쓰기가 완료되지 않으면 WRITE 이벤트 활성화
                selectorManager.enableWrite(channel);
            } else {
                // 쓰기가 완료되면 처리 완료
                handleWriteComplete(channel, state);
            }

        } catch (Exception e) {
            logger.error("Error sending response", e);
            selectorManager.closeChannel(channel);
        }
    }

    /**
     * 오류 응답 전송
     */
    private void sendErrorResponse(SocketChannel channel, ConnectionState state, HttpStatus status) {
        try {
            HttpResponse errorResponse = HttpResponse.builder(status)
                    .contentType("text/plain; charset=utf-8")
                    .body(status.getReasonPhrase())
                    .keepAlive(false)
                    .build();

            sendResponse(channel, state, errorResponse);

        } catch (Exception e) {
            logger.error("Error sending error response", e);
            selectorManager.closeChannel(channel);
        }
    }

    /**
     * 응답 데이터 쓰기
     */
    private boolean writeResponse(SocketChannel channel, ConnectionState state) throws IOException {
        byte[] responseData = state.getResponseData();
        int writeOffset = state.getWriteOffset();

        if (writeOffset >= responseData.length) {
            return true; // 이미 모든 데이터 전송 완료
        }

        ByteBuffer buffer = ByteBuffer.wrap(responseData, writeOffset,
                Math.min(responseBufferSize, responseData.length - writeOffset));

        int bytesWritten = selectorManager.writeToChannel(channel, buffer);

        if (bytesWritten > 0) {
            state.addWriteOffset(bytesWritten);
            logger.debug("Wrote {} bytes to channel {} ({}/{})",
                    bytesWritten,
                    selectorManager.getChannelId(channel),
                    state.getWriteOffset(),
                    responseData.length);
        }

        return state.getWriteOffset() >= responseData.length;
    }

    /**
     * 쓰기 완료 처리
     */
    private void handleWriteComplete(SocketChannel channel, ConnectionState state) {
        // 쓰기 이벤트 비활성화
        selectorManager.disableWrite(channel);

        HttpRequest request = state.getRequest();
        boolean keepAlive = request != null && request.isKeepAlive() && state.getResponseStatus() < 400;

        if (keepAlive) {
            // Keep-Alive 연결 - 다음 요청을 위해 상태 리셋
            state.reset();
            logger.debug("Connection kept alive for channel: {}",
                    selectorManager.getChannelId(channel));
        } else {
            // 연결 종료
            logger.debug("Closing connection for channel: {}",
                    selectorManager.getChannelId(channel));
            selectorManager.closeChannel(channel);
        }
    }

    /**
     * 타임아웃 연결 정리
     */
    public void cleanupTimeoutConnections() {
        eventQueue.execute(() -> {
            selectorManager.cleanupTimeoutConnections(connectionTimeout);
        });
    }

    /**
     * 통계 정보 반환
     */
    public HandlerStats getStats() {
        return new HandlerStats(
                connectionStates.size(),
                selectorManager.getStats()
        );
    }

    /**
     * ⭐ 연결 상태 클래스 (수정됨)
     */
    private static class ConnectionState {
        enum State {
            READING_REQUEST,
            PROCESSING_REQUEST,
            WRITING_RESPONSE
        }

        private final SocketChannel channel;
        private final long createdTime;
        private byte[] requestBuffer; // ⭐ ByteBuffer 대신 byte[] 사용

        private State state;
        private HttpRequest request;
        private byte[] responseData;
        private int writeOffset;
        private int responseStatus;

        public ConnectionState(SocketChannel channel) {
            this.channel = channel;
            this.createdTime = System.currentTimeMillis();
            this.requestBuffer = new byte[0]; // ⭐ 빈 배열로 시작
            this.state = State.READING_REQUEST;
            this.writeOffset = 0;
            this.responseStatus = 200;
        }

        /**
         * ⭐ 데이터 추가 (수정됨)
         */
        public void appendData(ByteBuffer data) {
            if (data.remaining() == 0) {
                return;
            }

            // 새로운 데이터를 기존 버퍼에 추가
            byte[] newData = new byte[data.remaining()];
            data.get(newData);

            byte[] combinedBuffer = new byte[requestBuffer.length + newData.length];
            System.arraycopy(requestBuffer, 0, combinedBuffer, 0, requestBuffer.length);
            System.arraycopy(newData, 0, combinedBuffer, requestBuffer.length, newData.length);

            this.requestBuffer = combinedBuffer;
        }

        /**
         * ⭐ 버퍼된 데이터 반환 (수정됨)
         */
        public byte[] getBufferedData() {
            return requestBuffer.clone(); // ⭐ 복사본 반환 (원본 보존)
        }

        /**
         * ⭐ 상태 리셋 (수정됨)
         */
        public void reset() {
            this.state = State.READING_REQUEST;
            this.request = null;
            this.responseData = null;
            this.writeOffset = 0;
            this.responseStatus = 200;
            this.requestBuffer = new byte[0]; // ⭐ 버퍼 초기화
        }

        public long getLifetimeMillis() {
            return System.currentTimeMillis() - createdTime;
        }

        // Getters and Setters
        public State getState() { return state; }
        public void setState(State state) { this.state = state; }
        public HttpRequest getRequest() { return request; }
        public void setRequest(HttpRequest request) { this.request = request; }
        public byte[] getResponseData() { return responseData; }
        public void setResponse(byte[] responseData) { this.responseData = responseData; }
        public int getWriteOffset() { return writeOffset; }
        public void addWriteOffset(int bytes) { this.writeOffset += bytes; }
        public int getResponseStatus() { return responseStatus; }
        public void setResponseStatus(int status) { this.responseStatus = status; }
    }

    /**
     * 핸들러 통계 정보
     */
    public static class HandlerStats {
        private final int activeConnections;
        private final SelectorManager.SelectorStats selectorStats;

        public HandlerStats(int activeConnections, SelectorManager.SelectorStats selectorStats) {
            this.activeConnections = activeConnections;
            this.selectorStats = selectorStats;
        }

        public int getActiveConnections() { return activeConnections; }
        public SelectorManager.SelectorStats getSelectorStats() { return selectorStats; }

        @Override
        public String toString() {
            return String.format("HandlerStats{active=%d, %s}",
                    activeConnections, selectorStats);
        }
    }
}