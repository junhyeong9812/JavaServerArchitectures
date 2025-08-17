package server.eventloop;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.http.*;
import server.core.routing.Router;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * 🔧 수정된 완전 논블로킹 HTTP 핸들러 (기존 HttpRequest 기반)
 *
 * 주요 수정사항:
 * 1. 기존 HttpRequest 클래스 활용
 * 2. 논블로킹 HTTP 파싱 구현
 * 3. 효율적인 버퍼 관리 (ByteBuffer 체인)
 * 4. 강화된 에러 처리
 * 5. 메모리 사용량 최적화
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
            // 🔧 수정: 읽은 데이터를 연결 상태의 버퍼에 추가
            state.appendData(buffer);

            // HTTP 요청 파싱 시도
            if (state.getState() == ConnectionState.State.READING_REQUEST) {
                tryParseRequest(channel, state);
            }

        } catch (OutOfMemoryError e) {
            logger.error("Memory exhausted for channel: {}, closing connection",
                    selectorManager.getChannelId(channel));
            selectorManager.closeChannel(channel);
        } catch (Exception e) {
            logger.error("Error processing read data for channel: {}",
                    selectorManager.getChannelId(channel), e);
            sendErrorResponse(channel, state, HttpStatus.INTERNAL_SERVER_ERROR);
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

    // === 🔧 수정된 HTTP 요청 처리 (기존 HttpRequest 사용) ===

    /**
     * 🔧 논블로킹 HTTP 요청 파싱 시도
     */
    private void tryParseRequest(SocketChannel channel, ConnectionState state) {
        // 🔧 수정: ByteBuffer 체인에서 데이터 가져오기
        ByteBuffer combinedBuffer = state.getCombinedBuffer();

        if (combinedBuffer.remaining() == 0) {
            return; // 데이터가 없으면 대기
        }

        // 요청 크기 제한 확인
        if (combinedBuffer.remaining() > maxRequestSize) {
            logger.warn("Request too large: {} bytes", combinedBuffer.remaining());
            sendErrorResponse(channel, state, HttpStatus.PAYLOAD_TOO_LARGE);
            return;
        }

        // 🔧 수정: 논블로킹 HTTP 헤더 파싱
        int headerEndIndex = findHeaderEnd(combinedBuffer);
        if (headerEndIndex == -1) {
            // 헤더가 아직 완료되지 않음
            logger.debug("Request header not complete yet, waiting for more data...");
            return;
        }

        try {
            // 🔧 수정: 기존 HttpRequest 생성
            HttpRequest request = parseHttpRequestFromBuffer(combinedBuffer, headerEndIndex);

            if (request == null) {
                logger.warn("Failed to parse HTTP request");
                sendErrorResponse(channel, state, HttpStatus.BAD_REQUEST);
                return;
            }

            state.setRequest(request);
            state.setState(ConnectionState.State.PROCESSING_REQUEST);

            // 🔧 수정: 사용된 데이터 제거
            state.consumeBytes(headerEndIndex);

            logger.debug("Parsed HTTP request: {} {}", request.getMethod(), request.getPath());

            // 라우터를 통한 요청 처리 (비동기)
            processRequestAsync(channel, state, request);

        } catch (Exception e) {
            logger.error("Error parsing HTTP request", e);
            sendErrorResponse(channel, state, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 🔧 새로운 논블로킹 HTTP 파싱 메서드 - 기존 HttpRequest 클래스 사용
     */
    private HttpRequest parseHttpRequestFromBuffer(ByteBuffer buffer, int headerEndIndex) throws HttpParsingException {
        try {
            // 헤더 부분만 추출
            byte[] headerBytes = new byte[headerEndIndex - 4]; // \r\n\r\n 제외
            int originalPosition = buffer.position();
            buffer.get(headerBytes);

            String headerString = new String(headerBytes, "UTF-8");
            String[] lines = headerString.split("\r\n");

            if (lines.length == 0) {
                throw new HttpParsingException("Empty HTTP request");
            }

            // 요청 라인 파싱 (GET /path HTTP/1.1)
            String[] requestLineParts = lines[0].split(" ");
            if (requestLineParts.length != 3) {
                throw new HttpParsingException("Invalid request line: " + lines[0]);
            }

            HttpMethod method;
            try {
                method = HttpMethod.valueOf(requestLineParts[0].toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new HttpParsingException("Unsupported HTTP method: " + requestLineParts[0]);
            }

            String uri = requestLineParts[1];
            String version = requestLineParts[2];

            // 헤더 파싱
            HttpHeaders headers = new HttpHeaders();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    String name = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    headers.add(name, value);
                }
            }

            // Body 처리 (현재는 간단한 GET 요청만 처리하므로 빈 body)
            byte[] body = new byte[0];

            // Content-Length가 있는 경우 body 읽기 (향후 확장 가능)
            long contentLength = headers.getContentLength();
            if (contentLength > 0) {
                // 현재는 body가 있는 요청은 지원하지 않음 (GET 요청 위주)
                logger.debug("Request with body detected (Content-Length: {}), but body parsing not implemented", contentLength);
            }

            // 🔧 수정: 기존 HttpRequest 클래스 사용
            return new HttpRequest(method, uri, version, headers, body);

        } catch (Exception e) {
            throw new HttpParsingException("Failed to parse HTTP request: " + e.getMessage(), e);
        }
    }

    /**
     * HTTP 헤더 끝 찾기 (\r\n\r\n)
     */
    private int findHeaderEnd(ByteBuffer buffer) {
        int position = buffer.position();
        int limit = buffer.limit();

        for (int i = position; i <= limit - 4; i++) {
            if (buffer.get(i) == '\r' &&
                    buffer.get(i + 1) == '\n' &&
                    buffer.get(i + 2) == '\r' &&
                    buffer.get(i + 3) == '\n') {
                return i + 4 - position; // 상대적 위치 반환
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

            // 응답 상태 저장
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
     * 🔧 수정된 연결 상태 클래스
     */
    private static class ConnectionState {
        enum State {
            READING_REQUEST,
            PROCESSING_REQUEST,
            WRITING_RESPONSE
        }

        private final SocketChannel channel;
        private final long createdTime;
        private final List<ByteBuffer> bufferChain; // 🔧 수정: ByteBuffer 체인 사용

        private State state;
        private HttpRequest request; // 🔧 수정: 기존 HttpRequest 사용
        private byte[] responseData;
        private int writeOffset;
        private int responseStatus;

        public ConnectionState(SocketChannel channel) {
            this.channel = channel;
            this.createdTime = System.currentTimeMillis();
            this.bufferChain = new ArrayList<>(); // 🔧 수정
            this.state = State.READING_REQUEST;
            this.writeOffset = 0;
            this.responseStatus = 200;
        }

        /**
         * 🔧 수정: 효율적인 데이터 추가
         */
        public void appendData(ByteBuffer data) {
            if (data.remaining() > 0) {
                // 복사본을 만들어서 체인에 추가 (원본 보존)
                ByteBuffer copy = ByteBuffer.allocate(data.remaining());
                copy.put(data);
                copy.flip();
                bufferChain.add(copy);
            }
        }

        /**
         * 🔧 수정: 모든 버퍼를 하나로 합치기
         */
        public ByteBuffer getCombinedBuffer() {
            if (bufferChain.isEmpty()) {
                return ByteBuffer.allocate(0);
            }

            // 전체 크기 계산
            int totalSize = bufferChain.stream()
                    .mapToInt(ByteBuffer::remaining)
                    .sum();

            if (totalSize == 0) {
                return ByteBuffer.allocate(0);
            }

            // 새 버퍼에 모든 데이터 복사
            ByteBuffer combined = ByteBuffer.allocate(totalSize);
            for (ByteBuffer buffer : bufferChain) {
                combined.put(buffer.duplicate());
            }
            combined.flip();

            return combined;
        }

        /**
         * 🔧 수정: 사용된 바이트 제거
         */
        public void consumeBytes(int bytesToConsume) {
            int remaining = bytesToConsume;

            while (remaining > 0 && !bufferChain.isEmpty()) {
                ByteBuffer firstBuffer = bufferChain.get(0);
                int available = firstBuffer.remaining();

                if (available <= remaining) {
                    // 첫 번째 버퍼를 완전히 소비
                    bufferChain.remove(0);
                    remaining -= available;
                } else {
                    // 첫 번째 버퍼를 부분적으로 소비
                    firstBuffer.position(firstBuffer.position() + remaining);
                    remaining = 0;
                }
            }
        }

        /**
         * 🔧 수정: 상태 리셋
         */
        public void reset() {
            this.state = State.READING_REQUEST;
            this.request = null;
            this.responseData = null;
            this.writeOffset = 0;
            this.responseStatus = 200;
            this.bufferChain.clear(); // 🔧 수정: 버퍼 체인 정리
        }

        public long getLifetimeMillis() {
            return System.currentTimeMillis() - createdTime;
        }

        // Getters and Setters
        public State getState() { return state; }
        public void setState(State state) { this.state = state; }
        public HttpRequest getRequest() { return request; } // 🔧 수정: 기존 HttpRequest 반환
        public void setRequest(HttpRequest request) { this.request = request; } // 🔧 수정
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

    /**
     * 🔧 추가: HTTP 파싱 예외 클래스
     */
    public static class HttpParsingException extends Exception {
        public HttpParsingException(String message) {
            super(message);
        }

        public HttpParsingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}