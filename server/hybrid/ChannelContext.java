package server.hybrid;

import server.core.http.HttpResponse;
import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 채널별 컨텍스트 클래스
 *
 * 역할:
 * 1. NIO 채널과 HTTP 요청/응답 상태 관리
 * 2. 부분적으로 읽은 데이터 버퍼링
 * 3. HTTP 요청 완성도 추적
 * 4. 응답 준비 상태 관리
 * 5. 채널별 메타데이터 저장
 */
public class ChannelContext {

    private static final Logger logger = LoggerFactory.getLogger(ChannelContext.class);

    // === 기본 정보 ===
    private final long connectionId;
    private final SocketChannel channel;
    private final long createdTime;

    // === HTTP 요청 데이터 ===
    private final StringBuilder requestBuffer;
    private volatile boolean requestComplete;
    private volatile String httpMethod;
    private volatile String requestUri;
    private volatile String httpVersion;

    // === HTTP 응답 데이터 ===
    private final AtomicReference<HttpResponse> response;
    private volatile boolean responseReady;

    // === 연결 상태 ===
    private volatile boolean keepAlive;
    private volatile long lastActivityTime;
    private volatile int requestCount;

    // === 속성 저장소 ===
    private final ConcurrentHashMap<String, Object> attributes;

    // === HTTP 파싱 상태 ===
    private volatile ParsingState parsingState;
    private volatile int contentLength;
    private volatile int readBodyBytes;

    /**
     * HTTP 파싱 상태 열거형
     */
    public enum ParsingState {
        REQUEST_LINE,    // 요청 라인 파싱 중
        HEADERS,         // 헤더 파싱 중
        BODY,            // 바디 파싱 중
        COMPLETE         // 파싱 완료
    }

    /**
     * ChannelContext 생성자
     */
    public ChannelContext(long connectionId, SocketChannel channel) {
        this.connectionId = connectionId;
        this.channel = channel;
        this.createdTime = System.currentTimeMillis();

        this.requestBuffer = new StringBuilder(1024);
        this.requestComplete = false;

        this.response = new AtomicReference<>();
        this.responseReady = false;

        this.keepAlive = true;
        this.lastActivityTime = createdTime;
        this.requestCount = 0;

        this.attributes = new ConcurrentHashMap<>();

        this.parsingState = ParsingState.REQUEST_LINE;
        this.contentLength = 0;
        this.readBodyBytes = 0;

        logger.debug("ChannelContext 생성 - 연결 ID: {}", connectionId);
    }

    /**
     * 새로운 데이터를 요청 버퍼에 추가
     */
    public synchronized void appendData(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        String data = new String(bytes);
        requestBuffer.append(data);

        updateLastActivity();
        checkRequestComplete();

        logger.debug("데이터 추가 - 연결 ID: {}, 크기: {} bytes", connectionId, bytes.length);
    }

    /**
     * HTTP 요청 완성도 확인
     */
    private void checkRequestComplete() {
        String currentData = requestBuffer.toString();

        switch (parsingState) {
            case REQUEST_LINE:
                checkRequestLine(currentData);
                break;
            case HEADERS:
                checkHeaders(currentData);
                break;
            case BODY:
                checkBody(currentData);
                break;
            case COMPLETE:
                break;
        }
    }

    /**
     * 요청 라인 파싱 확인
     */
    private void checkRequestLine(String data) {
        int firstLineEnd = data.indexOf("\r\n");

        if (firstLineEnd != -1) {
            String requestLine = data.substring(0, firstLineEnd);
            String[] parts = requestLine.split(" ");

            if (parts.length >= 3) {
                this.httpMethod = parts[0];
                this.requestUri = parts[1];
                this.httpVersion = parts[2];

                this.parsingState = ParsingState.HEADERS;
                checkHeaders(data);

                logger.debug("요청 라인 파싱 완료 - 연결 ID: {}, {} {} {}",
                        connectionId, httpMethod, requestUri, httpVersion);
            }
        }
    }

    /**
     * 헤더 파싱 확인
     */
    private void checkHeaders(String data) {
        int headersEnd = data.indexOf("\r\n\r\n");

        if (headersEnd != -1) {
            String headers = data.substring(0, headersEnd);

            parseContentLength(headers);
            parseKeepAlive(headers);

            if (contentLength > 0) {
                this.parsingState = ParsingState.BODY;
                checkBody(data);
            } else {
                this.parsingState = ParsingState.COMPLETE;
                this.requestComplete = true;
            }

            logger.debug("헤더 파싱 완료 - 연결 ID: {}, Content-Length: {}, Keep-Alive: {}",
                    connectionId, contentLength, keepAlive);
        }
    }

    /**
     * 바디 파싱 확인
     */
    private void checkBody(String data) {
        int headersEnd = data.indexOf("\r\n\r\n");

        if (headersEnd != -1) {
            int bodyStart = headersEnd + 4;
            int currentBodyLength = data.length() - bodyStart;

            this.readBodyBytes = currentBodyLength;

            if (currentBodyLength >= contentLength) {
                this.parsingState = ParsingState.COMPLETE;
                this.requestComplete = true;

                logger.debug("바디 파싱 완료 - 연결 ID: {}, 바디 크기: {} bytes",
                        connectionId, currentBodyLength);
            }
        }
    }

    /**
     * Content-Length 헤더 파싱
     */
    private void parseContentLength(String headers) {
        String[] lines = headers.split("\r\n");

        for (String line : lines) {
            if (line.toLowerCase().startsWith("content-length:")) {
                try {
                    String value = line.substring("content-length:".length()).trim();
                    this.contentLength = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    this.contentLength = 0;
                    logger.warn("잘못된 Content-Length 값 - 연결 ID: {}, 값: {}", connectionId, line);
                }
                break;
            }
        }
    }

    /**
     * Keep-Alive 헤더 파싱
     */
    private void parseKeepAlive(String headers) {
        String[] lines = headers.split("\r\n");

        for (String line : lines) {
            String lowerLine = line.toLowerCase();

            if (lowerLine.startsWith("connection:")) {
                String value = line.substring("connection:".length()).trim().toLowerCase();
                this.keepAlive = value.equals("keep-alive");
                break;
            }
        }

        // HTTP/1.1의 경우 기본값이 Keep-Alive
        if ("HTTP/1.1".equals(httpVersion) && !headers.toLowerCase().contains("connection:")) {
            this.keepAlive = true;
        }
    }

    /**
     * 완성된 요청 데이터 반환
     */
    public String getRequestData() {
        return requestBuffer.toString();
    }

    /**
     * 응답 설정
     */
    public void setResponse(HttpResponse response) {
        this.response.set(response);
        this.responseReady = true;
        updateLastActivity();

        logger.debug("응답 설정 완료 - 연결 ID: {}, 상태: {}",
                connectionId, response.getStatusCode());
    }

    /**
     * 응답 조회
     */
    public HttpResponse getResponse() {
        return response.get();
    }

    /**
     * 응답 준비 여부 확인
     */
    public boolean hasResponse() {
        return responseReady && response.get() != null;
    }

    /**
     * 새로운 요청을 위한 초기화
     */
    public synchronized void resetForNewRequest() {
        requestBuffer.setLength(0);
        requestComplete = false;
        httpMethod = null;
        requestUri = null;
        httpVersion = null;

        response.set(null);
        responseReady = false;

        parsingState = ParsingState.REQUEST_LINE;
        contentLength = 0;
        readBodyBytes = 0;

        requestCount++;
        updateLastActivity();

        logger.debug("요청 초기화 완료 - 연결 ID: {}, 요청 카운트: {}",
                connectionId, requestCount);
    }

    /**
     * 마지막 활동 시간 업데이트
     */
    public void updateLastActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    /**
     * 연결 타임아웃 확인
     */
    public boolean isTimedOut(long timeoutMs) {
        return (System.currentTimeMillis() - lastActivityTime) > timeoutMs;
    }

    /**
     * 연결 생존 시간 반환 (밀리초)
     */
    public long getLifetimeMs() {
        return System.currentTimeMillis() - createdTime;
    }

    /**
     * 유휴 시간 반환 (밀리초)
     */
    public long getIdleTimeMs() {
        return System.currentTimeMillis() - lastActivityTime;
    }

    /**
     * 속성 설정
     */
    public void setAttribute(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
        updateLastActivity();
    }

    /**
     * 속성 조회
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * 모든 속성 키 반환
     */
    public java.util.Set<String> getAttributeKeys() {
        return new java.util.HashSet<>(attributes.keySet());
    }

    // === Getters ===

    public long getConnectionId() { return connectionId; }
    public SocketChannel getChannel() { return channel; }
    public long getCreatedTime() { return createdTime; }
    public boolean isRequestComplete() { return requestComplete; }
    public String getHttpMethod() { return httpMethod; }
    public String getRequestUri() { return requestUri; }
    public String getHttpVersion() { return httpVersion; }
    public boolean isKeepAlive() { return keepAlive; }
    public long getLastActivityTime() { return lastActivityTime; }
    public int getRequestCount() { return requestCount; }
    public ParsingState getParsingState() { return parsingState; }
    public int getContentLength() { return contentLength; }
    public int getReadBodyBytes() { return readBodyBytes; }

    // === Setters ===

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
        updateLastActivity();
    }

    @Override
    public String toString() {
        return String.format(
                "ChannelContext{id=%d, method=%s, uri=%s, state=%s, " +
                        "complete=%s, keepAlive=%s, requests=%d, lifetime=%dms}",
                connectionId, httpMethod, requestUri, parsingState,
                requestComplete, keepAlive, requestCount, getLifetimeMs()
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ChannelContext that = (ChannelContext) obj;
        return connectionId == that.connectionId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(connectionId);
    }
}