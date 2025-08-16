package server.hybrid;

import server.core.http.HttpResponse;
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
 *
 * 하이브리드 서버에서의 중요성:
 * - NIO 스레드와 Worker 스레드 간 데이터 공유
 * - HTTP 프로토콜의 스트리밍 특성 처리
 * - Keep-Alive 연결 상태 관리
 */
public class ChannelContext {

    // === 기본 정보 ===
    private final long connectionId;                    // 연결 고유 ID
    private final SocketChannel channel;                // NIO 소켓 채널
    private final long createdTime;                     // 생성 시간

    // === HTTP 요청 데이터 ===
    private final StringBuilder requestBuffer;          // 요청 데이터 누적 버퍼
    private volatile boolean requestComplete;           // 요청 완성 여부
    private volatile String httpMethod;                 // HTTP 메서드 (GET, POST 등)
    private volatile String requestUri;                 // 요청 URI
    private volatile String httpVersion;                // HTTP 버전

    // === HTTP 응답 데이터 ===
    private final AtomicReference<HttpResponse> response; // 준비된 응답
    private volatile boolean responseReady;             // 응답 준비 완료 여부

    // === 연결 상태 ===
    private volatile boolean keepAlive;                 // Keep-Alive 여부
    private volatile long lastActivityTime;             // 마지막 활동 시간
    private volatile int requestCount;                  // 이 연결에서 처리한 요청 수

    // === 속성 저장소 ===
    private final ConcurrentHashMap<String, Object> attributes; // 커스텀 속성 저장

    // === HTTP 파싱 상태 ===
    private volatile ParsingState parsingState;         // 현재 파싱 상태
    private volatile int contentLength;                 // Content-Length 값
    private volatile int readBodyBytes;                 // 읽은 바디 바이트 수

    /**
     * HTTP 파싱 상태 열거형
     */
    public enum ParsingState {
        REQUEST_LINE,    // 요청 라인 파싱 중 (GET /path HTTP/1.1)
        HEADERS,         // 헤더 파싱 중
        BODY,            // 바디 파싱 중
        COMPLETE         // 파싱 완료
    }

    /**
     * ChannelContext 생성자
     *
     * @param connectionId 연결 ID
     * @param channel NIO 소켓 채널
     */
    public ChannelContext(long connectionId, SocketChannel channel) {
        this.connectionId = connectionId;
        this.channel = channel;
        this.createdTime = System.currentTimeMillis();

        // 요청 버퍼 초기화 (초기 용량 1KB)
        this.requestBuffer = new StringBuilder(1024);
        this.requestComplete = false;

        // 응답 관리 초기화
        this.response = new AtomicReference<>();
        this.responseReady = false;

        // 연결 상태 초기화
        this.keepAlive = true;  // 기본값은 Keep-Alive
        this.lastActivityTime = createdTime;
        this.requestCount = 0;

        // 속성 저장소 초기화
        this.attributes = new ConcurrentHashMap<>();

        // 파싱 상태 초기화
        this.parsingState = ParsingState.REQUEST_LINE;
        this.contentLength = 0;
        this.readBodyBytes = 0;
    }

    /**
     * 새로운 데이터를 요청 버퍼에 추가
     * NIO 스레드에서 호출
     *
     * @param buffer 읽은 데이터 버퍼
     */
    public synchronized void appendData(ByteBuffer buffer) {
        // ByteBuffer를 문자열로 변환하여 추가
        // remaining() - 읽을 수 있는 바이트 수
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);  // 버퍼에서 바이트 배열로 복사

        String data = new String(bytes);
        requestBuffer.append(data);

        // 마지막 활동 시간 업데이트
        updateLastActivity();

        // HTTP 요청 완성도 확인
        checkRequestComplete();
    }

    /**
     * HTTP 요청 완성도 확인
     * HTTP 프로토콜 스펙에 따른 파싱
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
                // 이미 완료됨
                break;
        }
    }

    /**
     * 요청 라인 파싱 확인 (GET /path HTTP/1.1)
     */
    private void checkRequestLine(String data) {
        int firstLineEnd = data.indexOf("\r\n");

        if (firstLineEnd != -1) {
            String requestLine = data.substring(0, firstLineEnd);
            String[] parts = requestLine.split(" ");

            if (parts.length >= 3) {
                this.httpMethod = parts[0];        // GET, POST 등
                this.requestUri = parts[1];        // /path
                this.httpVersion = parts[2];       // HTTP/1.1

                // 헤더 파싱 단계로 이동
                this.parsingState = ParsingState.HEADERS;
                checkHeaders(data);
            }
        }
    }

    /**
     * 헤더 파싱 확인
     */
    private void checkHeaders(String data) {
        // HTTP 헤더 끝을 나타내는 빈 줄 찾기 (\r\n\r\n)
        int headersEnd = data.indexOf("\r\n\r\n");

        if (headersEnd != -1) {
            // 헤더 부분 추출
            String headers = data.substring(0, headersEnd);

            // Content-Length 헤더 확인
            parseContentLength(headers);

            // Keep-Alive 헤더 확인
            parseKeepAlive(headers);

            if (contentLength > 0) {
                // 바디가 있는 요청 (POST, PUT 등)
                this.parsingState = ParsingState.BODY;
                checkBody(data);
            } else {
                // 바디가 없는 요청 (GET 등) - 파싱 완료
                this.parsingState = ParsingState.COMPLETE;
                this.requestComplete = true;
            }
        }
    }

    /**
     * 바디 파싱 확인
     */
    private void checkBody(String data) {
        int headersEnd = data.indexOf("\r\n\r\n");

        if (headersEnd != -1) {
            // 헤더 이후의 바디 데이터 길이 계산
            int bodyStart = headersEnd + 4; // "\r\n\r\n" 길이
            int currentBodyLength = data.length() - bodyStart;

            this.readBodyBytes = currentBodyLength;

            // 바디 완성도 확인
            if (currentBodyLength >= contentLength) {
                this.parsingState = ParsingState.COMPLETE;
                this.requestComplete = true;
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
     * Worker 스레드에서 호출
     */
    public void setResponse(HttpResponse response) {
        this.response.set(response);
        this.responseReady = true;
        updateLastActivity();
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
     * Keep-Alive 연결에서 재사용시 호출
     */
    public synchronized void resetForNewRequest() {
        // 요청 관련 데이터 초기화
        requestBuffer.setLength(0);  // StringBuilder 내용 클리어
        requestComplete = false;
        httpMethod = null;
        requestUri = null;
        httpVersion = null;

        // 응답 관련 데이터 초기화
        response.set(null);
        responseReady = false;

        // 파싱 상태 초기화
        parsingState = ParsingState.REQUEST_LINE;
        contentLength = 0;
        readBodyBytes = 0;

        // 요청 카운터 증가
        requestCount++;

        updateLastActivity();
    }

    /**
     * 마지막 활동 시간 업데이트
     */
    public void updateLastActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    /**
     * 연결 타임아웃 확인
     *
     * @param timeoutMs 타임아웃 시간 (밀리초)
     * @return 타임아웃 여부
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