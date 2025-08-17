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

    // SLF4J 로거 인스턴스 생성 - 채널 컨텍스트 관련 로깅용
    private static final Logger logger = LoggerFactory.getLogger(ChannelContext.class);
    // static final로 클래스당 하나의 로거 인스턴스 공유하여 메모리 효율성 확보

    // === 기본 정보 ===
    private final long connectionId; // 연결 고유 식별자
    private final SocketChannel channel; // NIO 소켓 채널 객체
    private final long createdTime; // 컨텍스트 생성 시간
    // final로 선언하여 생성 후 변경 불가능한 기본 정보들

    // === HTTP 요청 데이터 ===
    // HTTP 요청 데이터를 점진적으로 누적하는 문자열 빌더
    private final StringBuilder requestBuffer;
    // StringBuilder 사용으로 문자열 연결 성능 최적화

    private volatile boolean requestComplete; // 요청 완성 여부
    private volatile String httpMethod; // HTTP 메서드 (GET, POST 등)
    private volatile String requestUri; // 요청 URI
    private volatile String httpVersion; // HTTP 버전 (HTTP/1.1 등)
    // volatile 키워드로 멀티스레드 환경에서 변수 변경의 가시성 보장

    // === HTTP 응답 데이터 ===
    // 응답 객체를 원자적으로 관리하는 참조
    private final AtomicReference<HttpResponse> response;
    // AtomicReference로 응답 객체의 원자적 교체 보장

    private volatile boolean responseReady; // 응답 준비 완료 여부

    // === 연결 상태 ===
    private volatile boolean keepAlive; // Keep-Alive 연결 여부
    private volatile long lastActivityTime; // 마지막 활동 시간
    private volatile int requestCount; // 이 연결에서 처리한 요청 수
    // HTTP/1.1의 연결 재사용 지원을 위한 상태 관리

    // === 속성 저장소 ===
    // 채널별 사용자 정의 속성을 저장하는 동시성 맵
    private final ConcurrentHashMap<String, Object> attributes;
    // Key-Value 형태로 임의의 메타데이터 저장 가능

    // === HTTP 파싱 상태 ===
    private volatile ParsingState parsingState; // 현재 파싱 단계
    private volatile int contentLength; // Content-Length 헤더 값
    private volatile int readBodyBytes; // 현재까지 읽은 바디 바이트 수
    // HTTP 프로토콜의 점진적 파싱을 위한 상태 추적

    /**
     * HTTP 파싱 상태 열거형
     */
    public enum ParsingState {
        REQUEST_LINE,    // 요청 라인 파싱 중 (첫 번째 줄)
        HEADERS,         // 헤더 파싱 중 (두 번째 줄부터 빈 줄까지)
        BODY,            // 바디 파싱 중 (빈 줄 이후)
        COMPLETE         // 파싱 완료
    }
    // HTTP 요청의 구조적 파싱 단계를 명확히 정의
    // 각 단계는 특정 파싱 로직이 적용됨

    /**
     * ChannelContext 생성자
     */
    public ChannelContext(long connectionId, SocketChannel channel) {
        // 기본 정보 초기화
        this.connectionId = connectionId;
        this.channel = channel;
        this.createdTime = System.currentTimeMillis(); // 현재 시간으로 생성 시간 설정

        // HTTP 요청 관련 초기화
        this.requestBuffer = new StringBuilder(1024); // 1KB 초기 용량으로 생성
        // 일반적인 HTTP 요청 크기를 고려한 적절한 초기 용량 설정
        this.requestComplete = false; // 초기에는 요청 미완성 상태

        // HTTP 응답 관련 초기화
        this.response = new AtomicReference<>(); // null로 초기화된 원자적 참조
        this.responseReady = false; // 초기에는 응답 미준비 상태

        // 연결 상태 초기화
        this.keepAlive = true; // HTTP/1.1 기본값은 Keep-Alive
        this.lastActivityTime = createdTime; // 생성 시간을 초기 활동 시간으로 설정
        this.requestCount = 0; // 처리한 요청 수를 0으로 초기화

        // 속성 저장소 초기화
        this.attributes = new ConcurrentHashMap<>(); // 빈 동시성 맵 생성

        // HTTP 파싱 상태 초기화
        this.parsingState = ParsingState.REQUEST_LINE; // 요청 라인부터 시작
        this.contentLength = 0; // Content-Length 초기값 0
        this.readBodyBytes = 0; // 읽은 바디 바이트 수 초기값 0

        // 생성 완료 로그
        logger.debug("채널 컨텍스트 생성 - 연결 ID: {}", connectionId);
    }

    /**
     * 새로운 데이터를 요청 버퍼에 추가
     */
    public synchronized void appendData(ByteBuffer buffer) {
        // synchronized로 동시 접근 방지 - 요청 버퍼 일관성 보장

        // ByteBuffer에서 남은 바이트 배열 추출
        byte[] bytes = new byte[buffer.remaining()];
        // remaining()으로 읽을 수 있는 바이트 수 확인
        buffer.get(bytes); // ByteBuffer에서 바이트 배열로 데이터 복사

        // 바이트 배열을 문자열로 변환하여 버퍼에 추가
        String data = new String(bytes); // 기본 문자 인코딩 사용
        requestBuffer.append(data); // StringBuilder에 문자열 추가
        // append() 메서드로 기존 데이터에 새 데이터 연결

        // 활동 시간 업데이트
        updateLastActivity();
        // 데이터 수신은 채널 활동으로 간주

        // 요청 완성도 확인
        checkRequestComplete();
        // 새로운 데이터 추가 후 요청이 완성되었는지 검사

        // 데이터 추가 로그
        logger.debug("데이터 추가 - 연결 ID: {}, 크기: {} bytes", connectionId, bytes.length);
    }

    /**
     * HTTP 요청 완성도 확인
     */
    private void checkRequestComplete() {
        // 현재까지 누적된 모든 요청 데이터 획득
        String currentData = requestBuffer.toString();
        // StringBuilder를 문자열로 변환하여 파싱 대상 준비

        // 현재 파싱 상태에 따라 적절한 검사 로직 실행
        switch (parsingState) {
            case REQUEST_LINE:
                checkRequestLine(currentData); // 요청 라인 파싱 시도
                break;
            case HEADERS:
                checkHeaders(currentData); // 헤더 파싱 시도
                break;
            case BODY:
                checkBody(currentData); // 바디 파싱 시도
                break;
            case COMPLETE:
                break; // 이미 완성된 상태면 추가 작업 없음
        }
    }

    /**
     * 요청 라인 파싱 확인
     */
    private void checkRequestLine(String data) {
        // HTTP 요청의 첫 번째 줄 끝을 찾기 (CRLF로 구분)
        int firstLineEnd = data.indexOf("\r\n");
        // HTTP 프로토콜은 줄 구분자로 \r\n 사용

        if (firstLineEnd != -1) {
            // 요청 라인이 완성된 경우
            String requestLine = data.substring(0, firstLineEnd);
            // 첫 번째 줄만 추출하여 요청 라인으로 사용

            String[] parts = requestLine.split(" "); // 공백으로 분할
            // HTTP 요청 라인 형식: "METHOD URI VERSION"

            if (parts.length >= 3) {
                // 최소 3개 부분이 있어야 유효한 요청 라인
                this.httpMethod = parts[0]; // 첫 번째 부분: HTTP 메서드
                this.requestUri = parts[1]; // 두 번째 부분: 요청 URI
                this.httpVersion = parts[2]; // 세 번째 부분: HTTP 버전

                // 다음 단계로 진행
                this.parsingState = ParsingState.HEADERS;
                checkHeaders(data); // 헤더 파싱 즉시 시도
                // 요청 라인 완성 후 연속적으로 헤더 파싱 진행

                // 요청 라인 파싱 완료 로그
                logger.debug("요청 라인 파싱 완료 - 연결 ID: {}, {} {} {}",
                        connectionId, httpMethod, requestUri, httpVersion);
            }
        }
    }

    /**
     * 헤더 파싱 확인
     */
    private void checkHeaders(String data) {
        // HTTP 헤더 끝을 나타내는 빈 줄 찾기 (CRLF + CRLF)
        int headersEnd = data.indexOf("\r\n\r\n");
        // 헤더와 바디를 구분하는 빈 줄 탐지

        if (headersEnd != -1) {
            // 헤더가 완성된 경우
            String headers = data.substring(0, headersEnd);
            // 헤더 부분만 추출

            // 헤더에서 중요 정보 파싱
            parseContentLength(headers); // Content-Length 헤더 파싱
            parseKeepAlive(headers); // Connection 헤더 파싱

            if (contentLength > 0) {
                // 바디가 있는 요청인 경우
                this.parsingState = ParsingState.BODY;
                checkBody(data); // 바디 파싱 즉시 시도
            } else {
                // 바디가 없는 요청인 경우 (GET 등)
                this.parsingState = ParsingState.COMPLETE;
                this.requestComplete = true; // 요청 완성 표시
            }

            // 헤더 파싱 완료 로그
            logger.debug("헤더 파싱 완료 - 연결 ID: {}, Content-Length: {}, Keep-Alive: {}",
                    connectionId, contentLength, keepAlive);
        }
    }

    /**
     * 바디 파싱 확인
     */
    private void checkBody(String data) {
        // 헤더 끝 위치 재탐지
        int headersEnd = data.indexOf("\r\n\r\n");

        if (headersEnd != -1) {
            // 바디 시작 위치 계산
            int bodyStart = headersEnd + 4; // "\r\n\r\n"의 길이 4를 더함
            int currentBodyLength = data.length() - bodyStart;
            // 전체 데이터에서 바디 시작 위치를 빼서 현재 바디 길이 계산

            this.readBodyBytes = currentBodyLength; // 읽은 바디 바이트 수 업데이트

            // Content-Length와 비교하여 바디 완성 여부 확인
            if (currentBodyLength >= contentLength) {
                // 필요한 만큼의 바디를 모두 읽은 경우
                this.parsingState = ParsingState.COMPLETE;
                this.requestComplete = true; // 요청 완성 표시

                // 바디 파싱 완료 로그
                logger.debug("바디 파싱 완료 - 연결 ID: {}, 바디 크기: {} bytes",
                        connectionId, currentBodyLength);
            }
        }
    }

    /**
     * Content-Length 헤더 파싱
     */
    private void parseContentLength(String headers) {
        // 헤더를 줄 단위로 분할
        String[] lines = headers.split("\r\n");
        // 각 헤더는 별도 줄에 위치

        // 모든 헤더 라인 순회하며 Content-Length 찾기
        for (String line : lines) {
            if (line.toLowerCase().startsWith("content-length:")) {
                // 대소문자 구분 없이 Content-Length 헤더 탐지
                try {
                    // 헤더 이름 제거하고 값 부분만 추출
                    String value = line.substring("content-length:".length()).trim();
                    // substring()으로 헤더명 제거, trim()으로 공백 제거
                    this.contentLength = Integer.parseInt(value);
                    // 문자열을 정수로 변환하여 바이트 길이 저장
                } catch (NumberFormatException e) {
                    // 잘못된 Content-Length 값 처리
                    this.contentLength = 0; // 기본값 0으로 설정
                    logger.warn("잘못된 Content-Length 값 - 연결 ID: {}, 값: {}", connectionId, line);
                }
                break; // Content-Length 찾으면 루프 종료
            }
        }
    }

    /**
     * Keep-Alive 헤더 파싱
     */
    private void parseKeepAlive(String headers) {
        // 헤더를 줄 단위로 분할
        String[] lines = headers.split("\r\n");

        // 모든 헤더 라인 순회하며 Connection 헤더 찾기
        for (String line : lines) {
            String lowerLine = line.toLowerCase(); // 대소문자 구분 없이 비교

            if (lowerLine.startsWith("connection:")) {
                // Connection 헤더 발견
                String value = line.substring("connection:".length()).trim().toLowerCase();
                // 헤더 값 추출 및 소문자 변환
                this.keepAlive = value.equals("keep-alive");
                // "keep-alive" 값이면 true, 아니면 false
                break; // Connection 헤더 찾으면 루프 종료
            }
        }

        // HTTP/1.1의 경우 기본값이 Keep-Alive
        if ("HTTP/1.1".equals(httpVersion) && !headers.toLowerCase().contains("connection:")) {
            this.keepAlive = true;
            // HTTP/1.1에서 Connection 헤더가 없으면 기본적으로 Keep-Alive
        }
    }

    /**
     * 완성된 요청 데이터 반환
     */
    public String getRequestData() {
        // StringBuilder의 모든 내용을 문자열로 변환하여 반환
        return requestBuffer.toString();
        // 누적된 모든 HTTP 요청 데이터 제공
    }

    /**
     * 응답 설정
     */
    public void setResponse(HttpResponse response) {
        // 응답 객체를 원자적으로 설정
        this.response.set(response);
        // AtomicReference.set()으로 스레드 안전한 응답 설정

        this.responseReady = true; // 응답 준비 완료 표시
        updateLastActivity(); // 활동 시간 갱신

        // 응답 설정 완료 로그
        logger.debug("응답 설정 완료 - 연결 ID: {}, 상태: {}",
                connectionId, response.getStatusCode());
    }

    /**
     * 응답 조회
     */
    public HttpResponse getResponse() {
        // 원자적 참조에서 응답 객체 반환
        return response.get();
        // AtomicReference.get()으로 현재 설정된 응답 조회
    }

    /**
     * 응답 준비 여부 확인
     */
    public boolean hasResponse() {
        // 응답 준비 플래그와 실제 응답 객체 존재 여부 확인
        return responseReady && response.get() != null;
        // 두 조건을 모두 만족해야 응답 준비 완료로 판단
    }

    /**
     * 새로운 요청을 위한 초기화
     */
    public synchronized void resetForNewRequest() {
        // synchronized로 초기화 과정의 원자성 보장

        // HTTP 요청 관련 데이터 초기화
        requestBuffer.setLength(0); // StringBuilder 내용 모두 삭제
        // setLength(0)으로 빠른 버퍼 비우기
        requestComplete = false; // 요청 완성 상태 리셋
        httpMethod = null; // HTTP 메서드 초기화
        requestUri = null; // 요청 URI 초기화
        httpVersion = null; // HTTP 버전 초기화

        // HTTP 응답 관련 데이터 초기화
        response.set(null); // 응답 객체 null로 리셋
        responseReady = false; // 응답 준비 상태 리셋

        // HTTP 파싱 상태 초기화
        parsingState = ParsingState.REQUEST_LINE; // 파싱 상태를 처음부터 시작
        contentLength = 0; // Content-Length 리셋
        readBodyBytes = 0; // 읽은 바디 바이트 수 리셋

        // 연결 통계 업데이트
        requestCount++; // 처리한 요청 수 증가
        updateLastActivity(); // 활동 시간 갱신

        // 초기화 완료 로그
        logger.debug("요청 초기화 완료 - 연결 ID: {}, 요청 카운트: {}",
                connectionId, requestCount);
    }

    /**
     * 마지막 활동 시간 업데이트
     */
    public void updateLastActivity() {
        // 현재 시간으로 마지막 활동 시간 갱신
        this.lastActivityTime = System.currentTimeMillis();
        // 연결 활성 상태 추적을 위한 타임스탬프 업데이트
    }

    /**
     * 연결 타임아웃 확인
     */
    public boolean isTimedOut(long timeoutMs) {
        // 현재 시간과 마지막 활동 시간의 차이가 타임아웃보다 큰지 확인
        return (System.currentTimeMillis() - lastActivityTime) > timeoutMs;
        // 지정된 시간보다 오래 비활성 상태면 타임아웃으로 판단
    }

    /**
     * 연결 생존 시간 반환 (밀리초)
     */
    public long getLifetimeMs() {
        // 현재 시간에서 생성 시간을 빼서 생존 시간 계산
        return System.currentTimeMillis() - createdTime;
        // 연결이 얼마나 오래 유지되었는지 측정
    }

    /**
     * 유휴 시간 반환 (밀리초)
     */
    public long getIdleTimeMs() {
        // 현재 시간에서 마지막 활동 시간을 빼서 유휴 시간 계산
        return System.currentTimeMillis() - lastActivityTime;
        // 마지막 활동 이후 경과 시간 측정
    }

    /**
     * 속성 설정
     */
    public void setAttribute(String key, Object value) {
        if (value == null) {
            attributes.remove(key); // null 값이면 속성 제거
            // null 저장 대신 제거로 메모리 효율성 확보
        } else {
            attributes.put(key, value); // 속성 저장
            // ConcurrentHashMap.put()으로 스레드 안전한 저장
        }
        updateLastActivity(); // 속성 변경도 활동으로 간주
    }

    /**
     * 속성 조회
     */
    public Object getAttribute(String key) {
        // 지정된 키의 속성값 반환
        return attributes.get(key);
        // 존재하지 않으면 null 반환
    }

    /**
     * 모든 속성 키 반환
     */
    public java.util.Set<String> getAttributeKeys() {
        // 현재 저장된 모든 속성 키의 복사본 반환
        return new java.util.HashSet<>(attributes.keySet());
        // 새로운 HashSet 생성으로 원본 데이터 보호
    }

    // === Getters ===
    // 모든 private 필드에 대한 접근자 메서드들
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
    // 필요한 경우에만 제공되는 설정자 메서드들
    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive; // Keep-Alive 상태 직접 설정
        updateLastActivity(); // 설정 변경을 활동으로 간주
    }

    @Override
    public String toString() {
        // 채널 컨텍스트의 주요 정보를 읽기 쉬운 형태로 포맷팅
        return String.format(
                "ChannelContext{id=%d, method=%s, uri=%s, state=%s, " +
                        "complete=%s, keepAlive=%s, requests=%d, lifetime=%dms}",
                connectionId, httpMethod, requestUri, parsingState,
                requestComplete, keepAlive, requestCount, getLifetimeMs()
        );
        // 디버깅과 로깅에 유용한 정보들을 간결하게 표현
    }

    @Override
    public boolean equals(Object obj) {
        // 객체 동등성 비교 - 연결 ID 기반
        if (this == obj) return true; // 같은 인스턴스면 동일
        if (obj == null || getClass() != obj.getClass()) return false; // null이거나 다른 클래스면 다름
        ChannelContext that = (ChannelContext) obj; // 타입 캐스팅
        return connectionId == that.connectionId; // 연결 ID 기반 동등성 비교
    }

    @Override
    public int hashCode() {
        // 해시코드 생성 - 연결 ID 기반
        return Long.hashCode(connectionId);
        // Long.hashCode()로 연결 ID의 해시값 생성
    }
}