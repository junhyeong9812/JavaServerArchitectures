package server.eventloop;

// 필요한 클래스들을 import - 각각의 역할:
import server.core.logging.Logger;           // 로깅 기능을 위한 Logger 클래스
import server.core.logging.LoggerFactory;    // Logger 인스턴스 생성을 위한 팩토리 클래스
import server.core.http.*;                   // HTTP 관련 클래스들 (HttpRequest, HttpResponse, HttpMethod 등)
import server.core.routing.Router;           // URL 라우팅 처리를 위한 Router 클래스

import java.io.IOException;                  // 입출력 예외 처리를 위한 IOException
import java.nio.ByteBuffer;                  // NIO에서 바이트 데이터를 다루기 위한 ByteBuffer 클래스
import java.nio.channels.SocketChannel;      // NIO 논블로킹 소켓 채널 클래스
import java.util.concurrent.CompletableFuture; // 비동기 작업 처리를 위한 CompletableFuture
import java.util.concurrent.ConcurrentHashMap; // 스레드 안전한 HashMap 구현체
import java.util.Map;                        // Map 인터페이스 - 키-값 쌍 저장소
import java.util.List;                       // List 인터페이스 - 순서가 있는 컬렉션
import java.util.ArrayList;                  // List의 구현체 - 동적 배열

/**
 * 수정된 완전 논블로킹 HTTP 핸들러 (기존 HttpRequest 기반)
 *
 * 주요 수정사항:
 * 1. 기존 HttpRequest 클래스 활용
 * 2. 논블로킹 HTTP 파싱 구현
 * 3. 효율적인 버퍼 관리 (ByteBuffer 체인)
 * 4. 강화된 에러 처리
 * 5. 메모리 사용량 최적화
 *
 * 역할:
 * - EventLoop에서 발생하는 모든 네트워크 이벤트 처리
 * - HTTP 프로토콜 파싱과 응답 생성
 * - 연결 상태 관리와 Keep-Alive 지원
 * - 논블로킹 방식으로 높은 동시성 달성
 *
 * EventLoop 아키텍처에서의 위치:
 * - SelectorManager로부터 네트워크 이벤트 수신
 * - Router를 통해 요청을 적절한 핸들러로 라우팅
 * - EventQueue를 사용해 비동기 작업 스케줄링
 */
// implements 키워드: 인터페이스를 구현한다는 의미
// 두 개의 인터페이스를 동시에 구현하여 서버와 클라이언트 소켓 이벤트 모두 처리
public class NonBlockingHandler implements ServerSocketEventHandler, ClientSocketEventHandler {

    // static final: 클래스 레벨의 상수 - 모든 인스턴스가 공유하는 불변 값
    // Logger 인스턴스 - HTTP 처리 과정 추적을 위한 로깅 도구
    private static final Logger logger = LoggerFactory.getLogger(NonBlockingHandler.class);

    // 핵심 컴포넌트들 - final로 선언하여 생성 후 변경 불가능하게 함
    private final Router router;                    // URL 라우팅 처리 - 요청 URL을 적절한 핸들러로 매핑
    private final SelectorManager selectorManager;  // NIO Selector 관리 - 네트워크 이벤트 감지 및 처리
    private final EventQueue eventQueue;           // 비동기 작업 큐 - 논블로킹 작업 스케줄링

    // 연결별 상태 관리
    // ConcurrentHashMap: 스레드 안전한 Map 구현 - 동시 접근시에도 데이터 일관성 보장
    // Key: SocketChannel (클라이언트 연결), Value: ConnectionState (해당 연결의 상태 정보)
    private final Map<SocketChannel, ConnectionState> connectionStates;

    // 설정값들 - 보안과 성능을 위한 제한값들
    private final int maxRequestSize;      // 최대 요청 크기 (DoS 공격 방지) - 1MB로 제한
    private final int responseBufferSize;  // 응답 버퍼 크기 (메모리 효율성) - 8KB 단위로 전송
    private final long connectionTimeout;  // 연결 타임아웃 (좀비 연결 방지) - 30초 후 자동 해제

    /**
     * NonBlockingHandler 생성자
     * 의존성 주입(Dependency Injection) 패턴을 사용하여 필요한 컴포넌트들을 받음
     *
     * @param router 요청 라우팅을 처리할 Router - URL을 핸들러로 매핑
     * @param selectorManager NIO Selector를 관리하는 SelectorManager - 네트워크 이벤트 관리
     * @param eventQueue 비동기 작업을 처리할 EventQueue - 논블로킹 작업 큐
     */
    public NonBlockingHandler(Router router, SelectorManager selectorManager, EventQueue eventQueue) {
        // this 키워드: 현재 인스턴스의 필드를 가리킴 (매개변수와 구분하기 위해 사용)
        this.router = router;
        this.selectorManager = selectorManager;
        this.eventQueue = eventQueue;

        // 연결 상태 저장소 초기화
        // new ConcurrentHashMap<>(): 제네릭 타입 추론으로 타입 생략 가능 (Java 7+)
        this.connectionStates = new ConcurrentHashMap<>();

        // 기본 설정값들 - 일반적인 HTTP 서버 설정값들
        this.maxRequestSize = 1024 * 1024;    // 1MB - 일반적인 HTTP 요청 크기 제한 (1024 * 1024 = 1,048,576 bytes)
        this.responseBufferSize = 8192;       // 8KB - 효율적인 네트워크 전송 단위 (TCP 버퍼 크기와 유사)
        this.connectionTimeout = 30000;       // 30초 - 일반적인 HTTP 타임아웃 (30 * 1000 = 30,000 밀리초)
    }

    // === ServerSocketEventHandler 구현 ===
    // @Override 어노테이션: 상위 인터페이스의 메서드를 재정의함을 명시

    /**
     * 새로운 클라이언트 연결 수락 처리
     *
     * ServerSocketChannel에서 새로운 연결이 들어왔을 때 호출
     * accept() 이벤트 발생시 EventLoop에 의해 자동 호출됨
     *
     * @param eventLoop EventLoop 인스턴스 - 현재 이벤트를 처리하는 이벤트 루프
     * @param clientChannel 새로 연결된 클라이언트 채널 - 클라이언트와의 통신 채널
     */
    @Override
    public void onAccept(EventLoop eventLoop, SocketChannel clientChannel) {
        // try-catch 블록: 예외 처리를 위한 구문
        try {
            // 새로운 클라이언트 연결을 SelectorManager에 등록
            // 이후 이 연결에서 데이터가 오면 onRead가 호출됨
            // this 전달: 현재 객체를 핸들러로 등록 (ClientSocketEventHandler 인터페이스 구현체)
            selectorManager.registerClientSocket(clientChannel, this);

            // 연결 상태 초기화
            // 각 연결마다 독립적인 상태 정보를 관리
            // new ConnectionState(): 새로운 연결 상태 객체 생성
            ConnectionState state = new ConnectionState(clientChannel);

            // put() 메서드: Map에 키-값 쌍을 저장
            // clientChannel을 키로, state를 값으로 저장
            connectionStates.put(clientChannel, state);

            // 연결 수락 로그 (디버깅용)
            // debug() 메서드: 디버그 레벨 로그 출력 (개발시에만 출력)
            // {} 플레이스홀더: 뒤의 매개변수로 치환됨
            logger.debug("새로운 연결이 수락되었습니다: {} (총 연결 수: {})",
                    clientChannel.getRemoteAddress(),     // getRemoteAddress(): 클라이언트의 IP 주소와 포트 반환
                    connectionStates.size());             // size(): Map에 저장된 요소 개수 반환

        } catch (IOException e) {
            // IOException: 입출력 작업 중 발생하는 예외
            // error() 메서드: 에러 레벨 로그 출력 (심각한 오류시 사용)
            logger.error("연결 수락 중 오류가 발생했습니다", e);

            // 오류 발생시 즉시 연결 정리
            // closeChannel(): 채널을 안전하게 닫고 리소스 해제
            selectorManager.closeChannel(clientChannel);
        }
    }

    // === ClientSocketEventHandler 구현 ===

    /**
     * 클라이언트로부터 데이터 읽기 이벤트 처리
     *
     * 논블로킹 방식으로 HTTP 요청을 점진적으로 파싱
     * OP_READ 이벤트 발생시 EventLoop에 의해 호출됨
     *
     * @param eventLoop EventLoop 인스턴스 - 현재 이벤트를 처리하는 이벤트 루프
     * @param channel 데이터를 받은 클라이언트 채널 - 데이터가 도착한 연결
     * @param buffer 읽은 데이터가 담긴 ByteBuffer - 실제 받은 데이터
     * @throws IOException I/O 오류 발생시 던져지는 예외
     */
    @Override
    public void onRead(EventLoop eventLoop, SocketChannel channel, ByteBuffer buffer) throws IOException {
        // 연결 상태 조회
        // get() 메서드: Map에서 키에 해당하는 값을 가져옴, 없으면 null 반환
        ConnectionState state = connectionStates.get(channel);

        // null 체크: 상태가 없으면 비정상 상황
        if (state == null) {
            // warn() 메서드: 경고 레벨 로그 출력 (주의가 필요한 상황)
            logger.warn("채널에 대한 연결 상태를 찾을 수 없습니다: {}",
                    selectorManager.getChannelId(channel));  // getChannelId(): 채널의 고유 ID 반환

            // 상태가 없으면 비정상 상황 - 연결 정리
            selectorManager.closeChannel(channel);
            return;  // 메서드 조기 종료
        }

        try {
            // 수정: 읽은 데이터를 연결 상태의 버퍼에 추가
            // 여러 번의 read 이벤트를 통해 점진적으로 데이터 수집
            // appendData(): 버퍼 체인에 새로운 데이터 추가하는 메서드
            state.appendData(buffer);

            // HTTP 요청 파싱 시도
            // getState(): 현재 연결의 상태를 반환 (READING_REQUEST, PROCESSING_REQUEST, WRITING_RESPONSE)
            // == 연산자: enum 비교시 equals() 대신 사용 가능
            if (state.getState() == ConnectionState.State.READING_REQUEST) {
                // tryParseRequest(): HTTP 요청 파싱을 시도하는 메서드
                tryParseRequest(channel, state);
            }

        } catch (OutOfMemoryError e) {
            // OutOfMemoryError: 메모리 부족시 발생하는 에러
            // 메모리 부족시 즉시 연결 종료 (DoS 공격 방어)
            logger.error("채널 {}에 대해 메모리가 부족하여 연결을 종료합니다",
                    selectorManager.getChannelId(channel));
            selectorManager.closeChannel(channel);
        } catch (Exception e) {
            // Exception: 모든 예외의 상위 클래스 - 예상치 못한 모든 예외 처리
            logger.error("채널 {}의 읽기 데이터 처리 중 오류가 발생했습니다",
                    selectorManager.getChannelId(channel), e);

            // 500 Internal Server Error 응답 전송
            // sendErrorResponse(): 에러 응답을 생성하고 전송하는 메서드
            // HttpStatus.INTERNAL_SERVER_ERROR: HTTP 500 상태 코드
            sendErrorResponse(channel, state, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 클라이언트로 데이터 쓰기 이벤트 처리
     *
     * 응답 데이터를 논블로킹 방식으로 전송
     * OP_WRITE 이벤트 발생시 EventLoop에 의해 호출됨
     *
     * @param eventLoop EventLoop 인스턴스 - 현재 이벤트를 처리하는 이벤트 루프
     * @param channel 데이터를 보낼 클라이언트 채널 - 응답을 전송할 연결
     * @throws IOException I/O 오류 발생시 던져지는 예외
     */
    @Override
    public void onWrite(EventLoop eventLoop, SocketChannel channel) throws IOException {
        // 연결 상태 조회
        ConnectionState state = connectionStates.get(channel);
        if (state == null) {
            // 상태가 없으면 연결 종료
            selectorManager.closeChannel(channel);
            return;
        }

        try {
            // 응답 데이터 쓰기
            // writeResponse(): 응답 데이터를 실제로 전송하는 메서드
            // boolean 반환값: 전송 완료 여부
            boolean writeComplete = writeResponse(channel, state);

            if (writeComplete) {
                // 쓰기 완료시 후속 처리
                // handleWriteComplete(): 응답 전송 완료 후 Keep-Alive 등 후속 처리
                handleWriteComplete(channel, state);
            }
            // writeComplete가 false면 아직 쓸 데이터가 남아있음
            // 다음 WRITE 이벤트에서 계속 처리

        } catch (Exception e) {
            logger.error("채널 {}의 응답 쓰기 중 오류가 발생했습니다",
                    selectorManager.getChannelId(channel), e);
            selectorManager.closeChannel(channel);
        }
    }

    /**
     * 클라이언트 연결 해제 이벤트 처리
     *
     * 연결별 리소스 정리
     * 클라이언트가 연결을 끊거나 네트워크 오류로 연결이 해제될 때 호출됨
     *
     * @param eventLoop EventLoop 인스턴스 - 현재 이벤트를 처리하는 이벤트 루프
     * @param channel 연결이 해제된 클라이언트 채널 - 해제된 연결
     */
    @Override
    public void onDisconnect(EventLoop eventLoop, SocketChannel channel) {
        // 연결 상태 제거 및 정리
        // remove() 메서드: Map에서 키에 해당하는 항목을 제거하고 값을 반환
        ConnectionState state = connectionStates.remove(channel);

        if (state != null) {
            // 연결 해제 로그 (연결 지속 시간 포함)
            // getLifetimeMillis(): 연결 생성부터 현재까지의 시간을 밀리초로 반환
            logger.debug("연결이 해제되었습니다: {} (지속 시간: {}ms)",
                    selectorManager.getChannelId(channel),
                    state.getLifetimeMillis());
        }
    }

    // === 수정된 HTTP 요청 처리 (기존 HttpRequest 사용) ===

    /**
     * 논블로킹 HTTP 요청 파싱 시도
     *
     * 수신된 데이터가 완전한 HTTP 요청을 구성하는지 확인하고 파싱
     * HTTP 요청은 여러 번의 read 이벤트를 통해 점진적으로 도착할 수 있음
     *
     * @param channel 클라이언트 채널 - 요청이 온 연결
     * @param state 연결 상태 - 해당 연결의 현재 상태와 버퍼
     */
    private void tryParseRequest(SocketChannel channel, ConnectionState state) {
        // 수정: ByteBuffer 체인에서 데이터 가져오기
        // getCombinedBuffer(): 체인에 있는 모든 버퍼를 하나로 합쳐서 반환
        ByteBuffer combinedBuffer = state.getCombinedBuffer();

        // remaining() 메서드: 버퍼에서 읽을 수 있는 남은 바이트 수 반환
        if (combinedBuffer.remaining() == 0) {
            return; // 데이터가 없으면 대기
        }

        // 요청 크기 제한 확인 (DoS 공격 방어)
        if (combinedBuffer.remaining() > maxRequestSize) {
            logger.warn("요청이 너무 큽니다: {} bytes", combinedBuffer.remaining());
            // sendErrorResponse(): HTTP 413 Payload Too Large 응답 전송
            sendErrorResponse(channel, state, HttpStatus.PAYLOAD_TOO_LARGE);
            return;
        }

        // 수정: 논블로킹 HTTP 헤더 파싱
        // findHeaderEnd(): HTTP 헤더의 끝(\r\n\r\n)을 찾는 메서드
        int headerEndIndex = findHeaderEnd(combinedBuffer);
        if (headerEndIndex == -1) {
            // 헤더가 아직 완료되지 않음
            logger.debug("요청 헤더가 아직 완료되지 않아 더 많은 데이터를 기다리는 중입니다...");
            return;
        }

        try {
            // 수정: 기존 HttpRequest 생성
            // parseHttpRequestFromBuffer(): ByteBuffer에서 HttpRequest 객체를 파싱
            HttpRequest request = parseHttpRequestFromBuffer(combinedBuffer, headerEndIndex);

            if (request == null) {
                logger.warn("HTTP 요청 파싱에 실패했습니다");
                // sendErrorResponse(): HTTP 400 Bad Request 응답 전송
                sendErrorResponse(channel, state, HttpStatus.BAD_REQUEST);
                return;
            }

            // setRequest(): 파싱된 요청을 연결 상태에 저장
            state.setRequest(request);
            // setState(): 연결 상태를 "요청 처리 중"으로 변경
            state.setState(ConnectionState.State.PROCESSING_REQUEST);

            // 수정: 사용된 데이터 제거
            // consumeBytes(): 파싱에 사용된 바이트만큼 버퍼에서 제거
            state.consumeBytes(headerEndIndex);

            // getMethod(): HTTP 메서드 반환 (GET, POST 등)
            // getPath(): 요청 경로 반환 (/index.html 등)
            logger.debug("HTTP 요청이 파싱되었습니다: {} {}",
                    request.getMethod(), request.getPath());

            // 라우터를 통한 요청 처리 (비동기)
            // processRequestAsync(): 비동기로 요청을 처리하는 메서드
            processRequestAsync(channel, state, request);

        } catch (Exception e) {
            logger.error("HTTP 요청 파싱 중 오류가 발생했습니다", e);
            sendErrorResponse(channel, state, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 새로운 논블로킹 HTTP 파싱 메서드 - 기존 HttpRequest 클래스 사용
     *
     * ByteBuffer에서 HTTP 요청을 파싱하여 HttpRequest 객체 생성
     * HTTP/1.1 프로토콜 스펙에 따른 파싱 구현
     *
     * @param buffer HTTP 데이터가 담긴 ByteBuffer - 파싱할 원본 데이터
     * @param headerEndIndex HTTP 헤더의 끝 위치 - \r\n\r\n의 위치
     * @return 파싱된 HttpRequest 객체
     * @throws HttpParsingException 파싱 실패시 던져지는 커스텀 예외
     */
    private HttpRequest parseHttpRequestFromBuffer(ByteBuffer buffer, int headerEndIndex) throws HttpParsingException {
        try {
            // 헤더 부분만 추출
            // headerEndIndex - 4: \r\n\r\n (4바이트) 제외
            byte[] headerBytes = new byte[headerEndIndex - 4];

            // position() 메서드: 현재 버퍼의 위치를 반환
            int originalPosition = buffer.position();

            // get() 메서드: 버퍼에서 바이트 배열로 데이터를 읽어옴
            buffer.get(headerBytes);

            // 바이트 배열을 UTF-8 문자열로 변환
            // new String(byte[], String): 바이트 배열을 지정된 인코딩으로 문자열 변환
            String headerString = new String(headerBytes, "UTF-8");

            // CRLF(\r\n)로 헤더 라인들 분리
            // split() 메서드: 정규표현식으로 문자열을 배열로 분할
            String[] lines = headerString.split("\r\n");

            // length 속성: 배열의 길이 반환
            if (lines.length == 0) {
                throw new HttpParsingException("빈 HTTP 요청입니다");
            }

            // 요청 라인 파싱 (GET /path HTTP/1.1)
            // split(" "): 공백으로 문자열 분할
            String[] requestLineParts = lines[0].split(" ");
            if (requestLineParts.length != 3) {
                throw new HttpParsingException("잘못된 요청 라인입니다: " + lines[0]);
            }

            // HTTP 메서드 파싱
            HttpMethod method;
            try {
                // valueOf(): enum 상수 이름으로 enum 값 조회
                // toUpperCase(): 문자열을 대문자로 변환 (GET, POST 등)
                method = HttpMethod.valueOf(requestLineParts[0].toUpperCase());
            } catch (IllegalArgumentException e) {
                // IllegalArgumentException: 잘못된 매개변수 전달시 발생하는 예외
                throw new HttpParsingException("지원하지 않는 HTTP 메서드입니다: " + requestLineParts[0]);
            }

            String uri = requestLineParts[1];        // 요청 URI (/path?query=value)
            String version = requestLineParts[2];    // HTTP 버전 (HTTP/1.1)

            // 헤더 파싱
            // new HttpHeaders(): HTTP 헤더 컨테이너 객체 생성
            HttpHeaders headers = new HttpHeaders();

            // for 루프: 1번 인덱스부터 시작 (0번은 요청 라인이므로 제외)
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];

                // indexOf(':'): 콜론의 위치 찾기 (헤더 이름과 값 구분자)
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    // substring(): 문자열 일부 추출
                    // trim(): 앞뒤 공백 제거
                    String name = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();

                    // add() 메서드: 헤더에 이름-값 쌍 추가
                    headers.add(name, value);
                }
            }

            // Body 처리 (현재는 간단한 GET 요청만 처리하므로 빈 body)
            byte[] body = new byte[0];  // 빈 바이트 배열 생성

            // Content-Length가 있는 경우 body 읽기 (향후 확장 가능)
            // getContentLength(): Content-Length 헤더 값을 long으로 반환
            long contentLength = headers.getContentLength();
            if (contentLength > 0) {
                // 현재는 body가 있는 요청은 지원하지 않음 (GET 요청 위주)
                logger.debug("body가 있는 요청이 감지되었습니다 (Content-Length: {}), body 파싱은 구현되지 않았습니다", contentLength);
            }

            // 수정: 기존 HttpRequest 클래스 사용
            // new HttpRequest(): HTTP 요청 객체 생성 (메서드, URI, 버전, 헤더, body)
            return new HttpRequest(method, uri, version, headers, body);

        } catch (Exception e) {
            // 파싱 중 발생한 모든 예외를 HttpParsingException으로 감싸서 던짐
            throw new HttpParsingException("HTTP 요청 파싱에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * HTTP 헤더 끝 찾기 (\r\n\r\n)
     *
     * HTTP 헤더는 \r\n\r\n으로 끝나므로 이 패턴을 찾아야 함
     * 이 패턴을 찾으면 헤더가 완전히 도착했다는 의미
     *
     * @param buffer 검색할 ByteBuffer - HTTP 데이터가 담긴 버퍼
     * @return 헤더 끝 위치, 찾지 못하면 -1
     */
    private int findHeaderEnd(ByteBuffer buffer) {
        // position(): 현재 읽기 위치
        int position = buffer.position();
        // limit(): 버퍼의 끝 위치
        int limit = buffer.limit();

        // 버퍼에서 \r\n\r\n 패턴 검색
        // for 루프: position부터 limit-4까지 (4바이트 패턴이므로)
        for (int i = position; i <= limit - 4; i++) {
            // get(int): 특정 위치의 바이트 값 가져오기 (위치 변경 없이)
            // '\r': 캐리지 리턴 (13), '\n': 라인 피드 (10)
            if (buffer.get(i) == '\r' &&
                    buffer.get(i + 1) == '\n' &&
                    buffer.get(i + 2) == '\r' &&
                    buffer.get(i + 3) == '\n') {
                // 상대적 위치 반환 (position 기준)
                return i + 4 - position;
            }
        }
        return -1; // 헤더 끝을 찾지 못함
    }

    /**
     * 비동기 요청 처리
     *
     * Router를 통해 요청을 적절한 핸들러로 라우팅하고 비동기로 처리
     * 논블로킹 방식으로 작업하기 위해 CompletableFuture 사용
     *
     * @param channel 클라이언트 채널 - 응답을 보낼 연결
     * @param state 연결 상태 - 현재 연결의 상태 정보
     * @param request 파싱된 HTTP 요청 - 처리할 요청 객체
     */
    private void processRequestAsync(SocketChannel channel, ConnectionState state, HttpRequest request) {
        try {
            // 라우터를 통한 비동기 처리
            // routeWithMiddlewares(): 미들웨어와 라우트 핸들러를 순차 실행
            // CompletableFuture<HttpResponse>: 비동기 작업의 결과를 나타내는 객체
            CompletableFuture<HttpResponse> responseFuture = router.routeWithMiddlewares(request);

            // whenComplete(): 비동기 작업 완료시 콜백 실행
            // (response, error) -> { ... }: 람다 표현식 (Java 8+)
            responseFuture.whenComplete((response, error) -> {
                // EventQueue를 통해 EventLoop 스레드에서 결과 처리
                // execute(): 작업을 이벤트 큐에 추가하여 EventLoop 스레드에서 실행
                eventQueue.execute(() -> {
                    if (error != null) {
                        // 비동기 작업 중 오류 발생
                        logger.error("요청 처리 중 오류가 발생했습니다", error);
                        sendErrorResponse(channel, state, HttpStatus.INTERNAL_SERVER_ERROR);
                    } else if (response == null) {
                        // 라우터가 null 응답 반환 (매칭되는 라우트 없음)
                        logger.warn("라우터가 null 응답을 반환했습니다");
                        sendErrorResponse(channel, state, HttpStatus.NOT_FOUND);
                    } else {
                        // 정상 응답 전송
                        sendResponse(channel, state, response);
                    }
                });
            });

        } catch (Exception e) {
            logger.error("비동기 요청 처리 중 오류가 발생했습니다", e);
            sendErrorResponse(channel, state, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 정상 응답 전송
     *
     * HttpResponse를 HTTP 프로토콜 형식으로 변환하여 전송
     * Keep-Alive 처리 및 응답 상태 관리
     *
     * @param channel 클라이언트 채널 - 응답을 보낼 연결
     * @param state 연결 상태 - 현재 연결의 상태 정보
     * @param response 전송할 HTTP 응답 - 실제 응답 데이터
     */
    private void sendResponse(SocketChannel channel, ConnectionState state, HttpResponse response) {
        try {
            // Keep-Alive 헤더 처리
            HttpRequest request = state.getRequest();

            // && 연산자: 논리곱 (모든 조건이 true여야 true)
            // isKeepAlive(): 클라이언트가 Keep-Alive를 요청했는지 확인
            // getStatus().getCode(): HTTP 상태 코드 반환 (200, 404 등)
            if (request != null && request.isKeepAlive() &&
                    response.getStatus().getCode() < 400) {
                // 클라이언트가 Keep-Alive를 요청하고 정상 응답인 경우
                // setKeepAlive(): 응답에 Keep-Alive 헤더 설정
                response.setKeepAlive(true);
            } else {
                // 에러 응답이거나 Keep-Alive 미지원시 연결 종료
                response.setKeepAlive(false);
            }

            // 응답 상태 저장 (Keep-Alive 판단용)
            // setResponseStatus(): 응답 상태 코드를 연결 상태에 저장
            state.setResponseStatus(response.getStatus().getCode());

            // 응답 데이터를 바이트 배열로 변환
            // toByteArray(): HttpResponse를 HTTP 프로토콜 형식의 바이트 배열로 직렬화
            byte[] responseBytes = response.toByteArray();

            // setResponse(): 전송할 응답 데이터를 연결 상태에 저장
            state.setResponse(responseBytes);
            // setState(): 연결 상태를 "응답 쓰기 중"으로 변경
            state.setState(ConnectionState.State.WRITING_RESPONSE);

            logger.debug("응답을 전송합니다: {} bytes, 상태: {}",
                    responseBytes.length, response.getStatus().getCode());

            // 첫 번째 쓰기 시도
            boolean writeComplete = writeResponse(channel, state);

            if (!writeComplete) {
                // 쓰기가 완료되지 않으면 WRITE 이벤트 활성화
                // 다음 쓰기 가능한 시점에 계속 전송
                // enableWrite(): OP_WRITE 이벤트를 활성화하여 쓰기 가능시 알림 받음
                selectorManager.enableWrite(channel);
            } else {
                // 쓰기가 완료되면 처리 완료
                handleWriteComplete(channel, state);
            }

        } catch (Exception e) {
            logger.error("응답 전송 중 오류가 발생했습니다", e);
            selectorManager.closeChannel(channel);
        }
    }

    /**
     * 오류 응답 전송
     *
     * HTTP 상태 코드에 맞는 에러 응답 생성 및 전송
     * 표준 HTTP 에러 페이지 형태로 응답
     *
     * @param channel 클라이언트 채널 - 에러 응답을 보낼 연결
     * @param state 연결 상태 - 현재 연결의 상태 정보
     * @param status HTTP 에러 상태 - 전송할 에러 상태 코드
     */
    private void sendErrorResponse(SocketChannel channel, ConnectionState state, HttpStatus status) {
        try {
            // HttpResponse.builder(): 빌더 패턴으로 응답 생성
            // 빌더 패턴: 복잡한 객체를 단계별로 생성하는 디자인 패턴
            HttpResponse errorResponse = HttpResponse.builder(status)
                    .contentType("text/plain; charset=utf-8")    // 텍스트 응답, UTF-8 인코딩
                    .body(status.getReasonPhrase())              // 상태 메시지 (예: "Not Found")
                    .keepAlive(false)                            // 에러시 연결 종료
                    .build();                                    // 최종 객체 생성

            // 생성된 에러 응답을 전송
            sendResponse(channel, state, errorResponse);

        } catch (Exception e) {
            logger.error("오류 응답 전송 중 오류가 발생했습니다", e);
            selectorManager.closeChannel(channel);
        }
    }

    /**
     * 응답 데이터 쓰기
     *
     * 논블로킹 방식으로 응답 데이터를 점진적으로 전송
     * 큰 응답도 여러 번에 나누어 전송하여 논블로킹 보장
     *
     * @param channel 클라이언트 채널 - 데이터를 전송할 연결
     * @param state 연결 상태 - 전송 진행 상태 추적
     * @return 모든 데이터 전송 완료시 true, 아직 남은 데이터가 있으면 false
     * @throws IOException I/O 오류 발생시 던져지는 예외
     */
    private boolean writeResponse(SocketChannel channel, ConnectionState state) throws IOException {
        // getResponseData(): 전송할 응답 데이터 가져오기
        byte[] responseData = state.getResponseData();
        // getWriteOffset(): 현재까지 전송된 바이트 수
        int writeOffset = state.getWriteOffset();

        // 이미 모든 데이터 전송 완료
        if (writeOffset >= responseData.length) {
            return true;
        }

        // 전송할 데이터 크기 계산 (버퍼 크기 제한)
        // Math.min(): 두 값 중 작은 값 반환
        // wrap(): 바이트 배열의 일부를 ByteBuffer로 감싸기
        ByteBuffer buffer = ByteBuffer.wrap(responseData, writeOffset,
                Math.min(responseBufferSize, responseData.length - writeOffset));

        // 실제 데이터 전송
        // writeToChannel(): SelectorManager를 통한 실제 네트워크 쓰기
        int bytesWritten = selectorManager.writeToChannel(channel, buffer);

        if (bytesWritten > 0) {
            // 전송 완료된 바이트 수만큼 오프셋 증가
            // addWriteOffset(): 쓰기 오프셋에 전송된 바이트 수 추가
            state.addWriteOffset(bytesWritten);

            logger.debug("채널 {}에 {} bytes 쓰기가 완료되었습니다 ({}/{})",
                    selectorManager.getChannelId(channel),
                    bytesWritten,
                    state.getWriteOffset(),
                    responseData.length);
        }

        // 모든 데이터 전송 완료 여부 반환
        return state.getWriteOffset() >= responseData.length;
    }

    /**
     * 쓰기 완료 처리
     *
     * 응답 전송 완료 후 Keep-Alive 여부에 따른 후속 처리
     * 연결 유지 또는 종료 결정
     *
     * @param channel 클라이언트 채널 - 응답 전송이 완료된 연결
     * @param state 연결 상태 - 현재 연결의 상태 정보
     */
    private void handleWriteComplete(SocketChannel channel, ConnectionState state) {
        // 쓰기 이벤트 비활성화 (더 이상 쓸 데이터 없음)
        // disableWrite(): OP_WRITE 이벤트를 비활성화하여 불필요한 알림 방지
        selectorManager.disableWrite(channel);

        HttpRequest request = state.getRequest();

        // Keep-Alive 판단: 클라이언트 요청 + 정상 응답(< 400)
        // getResponseStatus(): 응답 상태 코드 반환
        boolean keepAlive = request != null && request.isKeepAlive() && state.getResponseStatus() < 400;

        if (keepAlive) {
            // Keep-Alive 연결 - 다음 요청을 위해 상태 리셋
            // reset(): 연결 상태를 초기 상태로 리셋 (다음 요청 대기)
            state.reset();
            logger.debug("채널 {}에 대해 연결이 유지됩니다",
                    selectorManager.getChannelId(channel));
        } else {
            // 연결 종료
            logger.debug("채널 {} 연결을 종료합니다",
                    selectorManager.getChannelId(channel));
            selectorManager.closeChannel(channel);
        }
    }

    /**
     * 타임아웃 연결 정리
     *
     * 주기적으로 호출되어 오래된 연결들을 정리
     * 좀비 연결 방지와 메모리 누수 방지를 위한 정기 정리 작업
     */
    public void cleanupTimeoutConnections() {
        // EventQueue를 통해 EventLoop 스레드에서 실행
        // () -> { ... }: 매개변수 없는 람다 표현식
        eventQueue.execute(() -> {
            // SelectorManager를 통해 타임아웃된 연결들 정리
            // cleanupTimeoutConnections(): 지정된 시간보다 오래된 연결들 해제
            selectorManager.cleanupTimeoutConnections(connectionTimeout);
        });
    }

    /**
     * 통계 정보 반환
     *
     * 현재 핸들러의 상태와 성능 지표
     * 모니터링과 디버깅을 위한 정보 제공
     *
     * @return HandlerStats 객체 - 핸들러의 현재 통계 정보
     */
    public HandlerStats getStats() {
        // new HandlerStats(): 현재 상태를 담은 통계 객체 생성
        return new HandlerStats(
                connectionStates.size(),        // 활성 연결 수
                selectorManager.getStats()      // Selector 통계
        );
    }

    /**
     * 수정된 연결 상태 클래스
     *
     * 각 클라이언트 연결의 상태와 데이터를 관리
     * HTTP 요청 처리의 전체 생명주기를 추적
     */
    private static class ConnectionState {
        /**
         * 연결의 현재 상태를 나타내는 enum
         * enum: 상수들의 집합을 정의하는 특별한 클래스
         */
        enum State {
            READING_REQUEST,      // HTTP 요청 읽는 중
            PROCESSING_REQUEST,   // 요청 처리 중 (라우팅, 비즈니스 로직 실행)
            WRITING_RESPONSE      // 응답 쓰는 중
        }

        private final SocketChannel channel;        // 연결된 소켓 채널
        private final long createdTime;             // 연결 생성 시간 (타임아웃 계산용)
        private final List<ByteBuffer> bufferChain; // 수정: ByteBuffer 체인 사용 (메모리 효율성)

        private State state;                        // 현재 상태
        private HttpRequest request;                // 수정: 기존 HttpRequest 사용
        private byte[] responseData;                // 전송할 응답 데이터
        private int writeOffset;                    // 쓰기 오프셋 (전송 진행 상황)
        private int responseStatus;                 // 응답 상태 코드 (Keep-Alive 판단용)

        /**
         * ConnectionState 생성자
         *
         * @param channel 연결된 소켓 채널 - 이 상태가 관리할 연결
         */
        public ConnectionState(SocketChannel channel) {
            this.channel = channel;
            // currentTimeMillis(): 현재 시간을 밀리초로 반환 (1970년 1월 1일 기준)
            this.createdTime = System.currentTimeMillis();
            // new ArrayList<>(): 동적 크기 조절 가능한 리스트 생성
            this.bufferChain = new ArrayList<>(); // 수정: 버퍼 체인 초기화
            this.state = State.READING_REQUEST;   // 초기 상태: 요청 읽기
            this.writeOffset = 0;                 // 쓰기 시작 위치
            this.responseStatus = 200;            // 기본 응답 상태: 200 OK
        }

        /**
         * 수정: 효율적인 데이터 추가
         *
         * 새로 읽은 데이터를 버퍼 체인에 추가
         * 메모리 복사를 최소화하여 성능 향상
         *
         * @param data 추가할 데이터 - 새로 읽은 ByteBuffer
         */
        public void appendData(ByteBuffer data) {
            // remaining(): 버퍼에서 읽을 수 있는 남은 바이트 수
            if (data.remaining() > 0) {
                // 복사본을 만들어서 체인에 추가 (원본 보존)
                // allocate(): 지정된 크기의 새 ByteBuffer 생성
                ByteBuffer copy = ByteBuffer.allocate(data.remaining());
                // put(): 다른 버퍼의 내용을 현재 버퍼에 복사
                copy.put(data);
                // flip(): 쓰기 모드에서 읽기 모드로 전환 (position을 0으로, limit을 position으로)
                copy.flip();
                // add(): 리스트에 요소 추가
                bufferChain.add(copy);
            }
        }

        /**
         * 수정: 모든 버퍼를 하나로 합치기
         *
         * 체인에 있는 모든 버퍼를 하나의 큰 버퍼로 결합
         * HTTP 파싱시에만 사용하여 메모리 효율성 유지
         *
         * @return 결합된 ByteBuffer - 모든 데이터가 합쳐진 하나의 버퍼
         */
        public ByteBuffer getCombinedBuffer() {
            // isEmpty(): 컬렉션이 비어있는지 확인
            if (bufferChain.isEmpty()) {
                // allocate(0): 크기가 0인 빈 버퍼 생성
                return ByteBuffer.allocate(0);
            }

            // 전체 크기 계산
            // stream(): 컬렉션을 스트림으로 변환 (Java 8+)
            // mapToInt(): 각 요소를 int 값으로 매핑
            // ByteBuffer::remaining: 메서드 참조 (각 버퍼의 남은 크기)
            // sum(): 모든 값의 합계
            int totalSize = bufferChain.stream()
                    .mapToInt(ByteBuffer::remaining)
                    .sum();

            if (totalSize == 0) {
                return ByteBuffer.allocate(0);
            }

            // 새 버퍼에 모든 데이터 복사
            ByteBuffer combined = ByteBuffer.allocate(totalSize);
            // for-each 루프: 컬렉션의 모든 요소를 순회
            for (ByteBuffer buffer : bufferChain) {
                // duplicate(): 버퍼의 복사본 생성 (원본 보존, position과 limit 공유하지 않음)
                combined.put(buffer.duplicate());
            }
            combined.flip(); // 읽기 모드로 전환

            return combined;
        }

        /**
         * 수정: 사용된 바이트 제거
         *
         * HTTP 요청 파싱 후 사용된 데이터를 버퍼 체인에서 제거
         * 메모리 효율성을 위해 처리된 데이터는 즉시 제거
         *
         * @param bytesToConsume 제거할 바이트 수 - 파싱에 사용된 바이트 수
         */
        public void consumeBytes(int bytesToConsume) {
            int remaining = bytesToConsume;

            // 체인의 첫 번째 버퍼부터 순차적으로 제거
            // while 루프: 조건이 참인 동안 반복
            while (remaining > 0 && !bufferChain.isEmpty()) {
                // get(0): 리스트의 첫 번째 요소 가져오기
                ByteBuffer firstBuffer = bufferChain.get(0);
                int available = firstBuffer.remaining();

                if (available <= remaining) {
                    // 첫 번째 버퍼를 완전히 소비
                    // remove(0): 리스트의 첫 번째 요소 제거
                    bufferChain.remove(0);
                    remaining -= available;
                } else {
                    // 첫 번째 버퍼를 부분적으로 소비
                    // position(): 현재 위치 가져오기
                    // position(int): 위치 설정
                    firstBuffer.position(firstBuffer.position() + remaining);
                    remaining = 0;
                }
            }
        }

        /**
         * 수정: 상태 리셋
         *
         * Keep-Alive 연결에서 다음 요청을 위해 상태 초기화
         * 연결은 유지하되 요청 처리 상태만 초기화
         */
        public void reset() {
            this.state = State.READING_REQUEST;
            this.request = null;
            this.responseData = null;
            this.writeOffset = 0;
            this.responseStatus = 200;
            // clear(): 리스트의 모든 요소 제거
            this.bufferChain.clear(); // 수정: 버퍼 체인 정리
        }

        /**
         * 연결 지속 시간 계산
         *
         * @return 연결 생성부터 현재까지의 시간 (밀리초)
         */
        public long getLifetimeMillis() {
            return System.currentTimeMillis() - createdTime;
        }

        // === Getters and Setters ===
        // getter/setter 메서드들: 필드에 대한 접근 제어

        public State getState() { return state; }
        public void setState(State state) { this.state = state; }

        public HttpRequest getRequest() { return request; } // 수정: 기존 HttpRequest 반환
        public void setRequest(HttpRequest request) { this.request = request; } // 수정

        public byte[] getResponseData() { return responseData; }
        public void setResponse(byte[] responseData) { this.responseData = responseData; }

        public int getWriteOffset() { return writeOffset; }
        public void addWriteOffset(int bytes) { this.writeOffset += bytes; }

        public int getResponseStatus() { return responseStatus; }
        public void setResponseStatus(int status) { this.responseStatus = status; }
    }

    /**
     * 핸들러 통계 정보
     *
     * NonBlockingHandler의 현재 상태를 나타내는 불변 객체
     * 모니터링과 성능 분석을 위한 데이터 제공
     */
    public static class HandlerStats {
        private final int activeConnections;                              // 활성 연결 수
        private final SelectorManager.SelectorStats selectorStats;       // Selector 통계

        /**
         * 생성자
         *
         * @param activeConnections 활성 연결 수 - 현재 처리 중인 연결 개수
         * @param selectorStats Selector 통계 - NIO Selector의 성능 정보
         */
        public HandlerStats(int activeConnections, SelectorManager.SelectorStats selectorStats) {
            this.activeConnections = activeConnections;
            this.selectorStats = selectorStats;
        }

        public int getActiveConnections() { return activeConnections; }
        public SelectorManager.SelectorStats getSelectorStats() { return selectorStats; }

        // @Override: 상위 클래스(Object)의 메서드를 재정의
        // toString(): 객체의 문자열 표현을 반환
        @Override
        public String toString() {
            // String.format(): C의 printf와 유사한 형식화된 문자열 생성
            return String.format("HandlerStats{active=%d, %s}",
                    activeConnections, selectorStats);
        }
    }

    /**
     * 추가: HTTP 파싱 예외 클래스
     *
     * HTTP 요청 파싱 중 발생하는 예외를 위한 커스텀 예외
     * 표준 Exception을 상속하여 HTTP 파싱 관련 예외임을 명시
     */
    public static class HttpParsingException extends Exception {
        // 생성자 오버로딩: 다양한 형태의 생성자 제공

        public HttpParsingException(String message) {
            // super(): 상위 클래스의 생성자 호출
            super(message);
        }

        public HttpParsingException(String message, Throwable cause) {
            // cause: 원인이 되는 예외 (예외 체이닝)
            super(message, cause);
        }
    }
}