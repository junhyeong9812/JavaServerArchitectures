package server.eventloop;

// 필요한 클래스들을 import - 각각의 역할:
import server.core.logging.Logger;           // 로깅 기능을 위한 Logger 클래스
import server.core.logging.LoggerFactory;    // Logger 인스턴스 생성을 위한 팩토리 클래스

import java.io.IOException;                  // 입출력 예외 처리를 위한 IOException
import java.nio.channels.*;                  // NIO 채널 관련 클래스들 (SocketChannel, ServerSocketChannel, SelectionKey 등)
import java.nio.ByteBuffer;                  // NIO에서 바이트 데이터를 다루기 위한 ByteBuffer 클래스
import java.util.concurrent.ConcurrentHashMap; // 스레드 안전한 HashMap 구현체
import java.util.concurrent.atomic.AtomicLong; // 스레드 안전한 long 타입 카운터
import java.util.Map;                        // Map 인터페이스 - 키-값 쌍 저장소

/**
 * NIO Selector 관리자
 *
 * 역할:
 * - 채널 등록 및 관리 (서버 소켓, 클라이언트 소켓)
 * - 이벤트 감지 및 처리 (READ, WRITE, ACCEPT)
 * - 리소스 정리 및 타임아웃 관리
 * - 성능 통계 수집 및 제공
 *
 * NIO Selector의 핵심 개념:
 * - 하나의 스레드로 여러 채널을 동시에 모니터링
 * - 이벤트 기반 논블로킹 I/O 처리
 * - 메모리 효율적인 대용량 연결 처리
 */
public class SelectorManager {

    // static final: 클래스 레벨의 상수 - 모든 인스턴스가 공유하는 불변 값
    private static final Logger logger = LoggerFactory.getLogger(SelectorManager.class);

    // 핵심 컴포넌트들 - final로 선언하여 생성 후 변경 불가능하게 함
    private final EventLoop eventLoop;                                      // 이벤트 루프 - 이벤트 처리 스레드 관리
    private final Selector selector;                                        // NIO Selector - 여러 채널의 이벤트를 감지
    private final Map<SocketChannel, ChannelContext> channelContexts;       // 채널별 컨텍스트 정보 저장
    private final AtomicLong channelIdGenerator;                            // 채널 고유 ID 생성기

    // 성능 통계 - volatile로 선언하여 스레드 간 가시성 보장
    // volatile: 변수의 값이 메인 메모리에서 직접 읽히고 쓰여지도록 보장
    private volatile long totalConnections;     // 총 연결 수 (누적)
    private volatile long activeConnections;    // 현재 활성 연결 수
    private volatile long bytesRead;           // 읽은 총 바이트 수
    private volatile long bytesWritten;        // 쓴 총 바이트 수

    /**
     * SelectorManager 생성자
     * 의존성 주입(Dependency Injection) 패턴을 사용하여 필요한 컴포넌트들을 받음
     *
     * @param eventLoop 이벤트 루프 - 이벤트 처리를 담당하는 메인 스레드
     * @param selector NIO Selector - 채널 이벤트를 감지하는 멀티플렉서
     */
    public SelectorManager(EventLoop eventLoop, Selector selector) {
        // this 키워드: 현재 인스턴스의 필드를 가리킴 (매개변수와 구분하기 위해 사용)
        this.eventLoop = eventLoop;
        this.selector = selector;

        // 채널 컨텍스트 저장소 초기화
        // new ConcurrentHashMap<>(): 제네릭 타입 추론으로 타입 생략 가능 (Java 7+)
        // ConcurrentHashMap: 스레드 안전한 HashMap - 동시 접근시에도 데이터 일관성 보장
        this.channelContexts = new ConcurrentHashMap<>();

        // 채널 ID 생성기 초기화
        // new AtomicLong(0): 0부터 시작하는 스레드 안전한 long 카운터
        // AtomicLong: 원자적 연산을 지원하는 long 타입 래퍼 클래스
        this.channelIdGenerator = new AtomicLong(0);
    }

    /**
     * 서버 소켓 등록
     *
     * 새로운 클라이언트 연결을 수락할 서버 소켓을 Selector에 등록
     * OP_ACCEPT 이벤트를 감지하여 새 연결을 처리
     *
     * @param serverChannel 등록할 서버 소켓 채널 - 클라이언트 연결을 수락할 채널
     * @param handler 서버 소켓 이벤트 핸들러 - 새 연결 수락시 호출될 핸들러
     * @throws IOException 채널 등록 중 I/O 오류 발생시
     */
    public void registerServerSocket(ServerSocketChannel serverChannel,
                                     ServerSocketEventHandler handler) throws IOException {
        // inEventLoop(): 현재 스레드가 EventLoop 스레드인지 확인
        // EventLoop 스레드가 아닌 경우 스레드 안전성을 위해 작업을 EventLoop로 위임
        if (!eventLoop.inEventLoop()) {
            // execute(): 작업을 EventLoop 스레드에서 실행하도록 스케줄링
            // () -> { ... }: 람다 표현식 - 매개변수 없는 Runnable 구현
            eventLoop.execute(() -> {
                try {
                    // 재귀 호출로 EventLoop 스레드에서 실제 등록 수행
                    registerServerSocket(serverChannel, handler);
                } catch (IOException e) {
                    // error(): 에러 레벨 로그 출력 (심각한 오류시 사용)
                    logger.error("서버 소켓 등록에 실패했습니다", e);
                }
            });
            return;  // 메서드 조기 종료 (EventLoop에서 실행될 것이므로)
        }

        // configureBlocking(false): 채널을 논블로킹 모드로 설정
        // 논블로킹 모드: I/O 작업이 즉시 반환되며 Selector와 함께 사용 필수
        serverChannel.configureBlocking(false);

        // register(): 채널을 Selector에 등록하고 관심 있는 이벤트 지정
        // SelectionKey.OP_ACCEPT: 새로운 연결 수락 이벤트에 관심 표시
        // 반환값: SelectionKey - 등록된 채널과 이벤트 정보를 담는 객체
        SelectionKey key = serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // attach(): SelectionKey에 객체를 첨부
        // 이벤트 발생시 첨부된 객체를 통해 적절한 핸들러 호출 가능
        key.attach(handler);

        // info(): 정보 레벨 로그 출력 (중요한 상태 변화시 사용)
        // getLocalAddress(): 서버 소켓의 바인딩된 주소 반환
        logger.info("서버 소켓이 등록되었습니다: {}", serverChannel.getLocalAddress());
    }

    /**
     * 클라이언트 소켓 등록
     *
     * 새로 연결된 클라이언트 소켓을 Selector에 등록하여 READ 이벤트 감지
     * 각 클라이언트마다 고유한 ChannelContext 생성하여 상태 관리
     *
     * @param clientChannel 등록할 클라이언트 소켓 채널 - 새로 연결된 클라이언트
     * @param handler 클라이언트 소켓 이벤트 핸들러 - READ/WRITE 이벤트 처리 핸들러
     */
    public void registerClientSocket(SocketChannel clientChannel,
                                     ClientSocketEventHandler handler) {
        // EventLoop 스레드 검사 및 위임
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> registerClientSocket(clientChannel, handler));
            return;
        }

        try {
            // 클라이언트 채널을 논블로킹 모드로 설정
            clientChannel.configureBlocking(false);

            // ChannelContext 생성 (통계 및 관리용)
            // incrementAndGet(): 원자적으로 값을 1 증가시키고 증가된 값 반환
            // 스레드 안전한 방식으로 고유한 채널 ID 생성
            long channelId = channelIdGenerator.incrementAndGet();

            // new ChannelContext(): 채널별 컨텍스트 정보 객체 생성
            ChannelContext context = new ChannelContext(channelId, clientChannel, handler);

            // put(): Map에 키-값 쌍을 저장
            // clientChannel을 키로, context를 값으로 저장
            channelContexts.put(clientChannel, context);

            // 핵심 수정: handler를 직접 attachment로 설정
            // SelectionKey.OP_READ: 데이터 읽기 이벤트에 관심 표시
            SelectionKey key = clientChannel.register(selector, SelectionKey.OP_READ);
            // ChannelContext 대신 handler 직접 설정 - 이벤트 처리시 바로 핸들러 접근 가능
            key.attach(handler);

            // 통계 정보 업데이트
            // ++: 전위 증가 연산자 - 값을 먼저 증가시킨 후 사용
            totalConnections++;     // 총 연결 수 증가
            activeConnections++;    // 활성 연결 수 증가

            // debug(): 디버그 레벨 로그 출력 (개발시에만 출력)
            // getRemoteAddress(): 클라이언트의 원격 주소 반환
            logger.debug("클라이언트 소켓이 등록되었습니다 [{}]: {}",
                    channelId, clientChannel.getRemoteAddress());

        } catch (IOException e) {
            logger.error("클라이언트 소켓 등록에 실패했습니다", e);
            // 등록 실패시 즉시 채널 정리
            closeChannel(clientChannel);
        }
    }

    /**
     * 채널에서 데이터 읽기
     *
     * 실제 네트워크에서 데이터를 읽고 통계 정보 업데이트
     * 논블로킹 방식으로 동작하여 데이터가 없으면 즉시 반환
     *
     * @param channel 읽기를 수행할 소켓 채널 - 데이터를 읽을 연결
     * @param buffer 읽은 데이터를 저장할 ByteBuffer - 데이터 저장소
     * @return 읽은 바이트 수, 연결 종료시 -1, 데이터 없으면 0
     * @throws IOException I/O 오류 발생시
     */
    public int readFromChannel(SocketChannel channel, ByteBuffer buffer) throws IOException {
        // read(): 채널에서 데이터를 읽어 버퍼에 저장
        // 반환값: 읽은 바이트 수 (양수), 데이터 없음(0), 연결 종료(-1)
        int bytesRead = channel.read(buffer);

        // 실제로 데이터를 읽은 경우에만 통계 업데이트
        if (bytesRead > 0) {
            // this.bytesRead: 인스턴스 필드와 매개변수 bytesRead 구분
            // +=: 복합 대입 연산자 - this.bytesRead = this.bytesRead + bytesRead와 동일
            this.bytesRead += bytesRead;

            // 채널별 컨텍스트 정보 업데이트
            // get(): Map에서 키에 해당하는 값을 가져옴, 없으면 null 반환
            ChannelContext context = channelContexts.get(channel);
            if (context != null) {
                // updateLastActivity(): 마지막 활동 시간을 현재 시간으로 업데이트
                context.updateLastActivity();
                // addBytesRead(): 읽은 바이트 수를 컨텍스트 통계에 추가
                context.addBytesRead(bytesRead);
            }
        }

        return bytesRead;
    }

    /**
     * 채널에 데이터 쓰기
     *
     * 실제 네트워크로 데이터를 전송하고 통계 정보 업데이트
     * 논블로킹 방식으로 동작하여 버퍼가 가득하면 일부만 전송 가능
     *
     * @param channel 쓰기를 수행할 소켓 채널 - 데이터를 전송할 연결
     * @param buffer 전송할 데이터가 담긴 ByteBuffer - 전송할 데이터
     * @return 전송한 바이트 수, 버퍼 가득하면 0
     * @throws IOException I/O 오류 발생시
     */
    public int writeToChannel(SocketChannel channel, ByteBuffer buffer) throws IOException {
        // write(): 버퍼의 데이터를 채널로 전송
        // 반환값: 전송한 바이트 수 (논블로킹이므로 전체가 전송되지 않을 수 있음)
        int bytesWritten = channel.write(buffer);

        // 실제로 데이터를 전송한 경우에만 통계 업데이트
        if (bytesWritten > 0) {
            this.bytesWritten += bytesWritten;

            // 채널별 컨텍스트 정보 업데이트
            ChannelContext context = channelContexts.get(channel);
            if (context != null) {
                context.updateLastActivity();
                // addBytesWritten(): 쓴 바이트 수를 컨텍스트 통계에 추가
                context.addBytesWritten(bytesWritten);
            }
        }

        return bytesWritten;
    }

    /**
     * 쓰기 이벤트 활성화
     *
     * 전송할 데이터가 있을 때 WRITE 이벤트를 활성화하여
     * 네트워크 버퍼에 공간이 생기면 알림을 받도록 설정
     *
     * @param channel 쓰기 이벤트를 활성화할 채널 - 데이터 전송 대기 중인 연결
     */
    public void enableWrite(SocketChannel channel) {
        // EventLoop 스레드 검사 및 위임
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> enableWrite(channel));
            return;
        }

        // keyFor(): 특정 Selector에 대한 채널의 SelectionKey 반환
        SelectionKey key = channel.keyFor(selector);

        // null 체크와 유효성 검사
        if (key != null && key.isValid()) {
            // interestOps(): 현재 관심 있는 이벤트 집합 반환
            // |: 비트 OR 연산자 - 기존 이벤트에 WRITE 이벤트 추가
            // SelectionKey.OP_WRITE: 쓰기 가능 이벤트 플래그
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);

            logger.debug("채널의 쓰기 이벤트가 활성화되었습니다: {}", getChannelId(channel));
        }
    }

    /**
     * 쓰기 이벤트 비활성화
     *
     * 모든 데이터 전송 완료시 WRITE 이벤트를 비활성화하여
     * 불필요한 이벤트 알림을 방지하고 성능 향상
     *
     * @param channel 쓰기 이벤트를 비활성화할 채널 - 데이터 전송 완료된 연결
     */
    public void disableWrite(SocketChannel channel) {
        // EventLoop 스레드 검사 및 위임
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> disableWrite(channel));
            return;
        }

        SelectionKey key = channel.keyFor(selector);
        if (key != null && key.isValid()) {
            // ~: 비트 NOT 연산자 - 모든 비트 반전
            // &: 비트 AND 연산자 - 기존 이벤트에서 WRITE 이벤트만 제거
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);

            logger.debug("채널의 쓰기 이벤트가 비활성화되었습니다: {}", getChannelId(channel));
        }
    }

    /**
     * 채널 안전 종료
     *
     * 채널과 관련된 모든 리소스를 정리하고 통계 정보 업데이트
     * SelectionKey 취소, 채널 닫기, 컨텍스트 정리를 순서대로 수행
     *
     * @param channel 종료할 소켓 채널 - 정리할 연결
     */
    public void closeChannel(SocketChannel channel) {
        // EventLoop 스레드 검사 및 위임
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> closeChannel(channel));
            return;
        }

        // 채널 컨텍스트 제거 및 통계 업데이트
        // remove(): Map에서 키에 해당하는 항목을 제거하고 값을 반환
        ChannelContext context = channelContexts.remove(channel);
        if (context != null) {
            // --: 전위 감소 연산자 - 값을 먼저 감소시킨 후 사용
            activeConnections--;

            // 상세한 연결 정보와 함께 로그 출력
            logger.debug("채널을 닫습니다 [{}]: {} (지속시간: {}ms, 읽기: {} bytes, 쓰기: {} bytes)",
                    context.getChannelId(),                    // 채널 고유 ID
                    getRemoteAddress(channel),                 // 원격 주소
                    context.getLifetimeMillis(),              // 연결 지속 시간
                    context.getBytesRead(),                   // 읽은 바이트 수
                    context.getBytesWritten());               // 쓴 바이트 수
        }

        try {
            // SelectionKey 정리
            SelectionKey key = channel.keyFor(selector);
            if (key != null) {
                // cancel(): SelectionKey를 취소하여 Selector에서 제거
                // 다음 select() 호출시 실제로 제거됨
                key.cancel();
            }

            // 채널이 열려있으면 닫기
            // isOpen(): 채널이 열려있는지 확인
            if (channel.isOpen()) {
                // close(): 채널을 닫고 관련 리소스 해제
                channel.close();
            }
        } catch (IOException e) {
            // 채널 닫기 중 오류 발생시 로그만 출력 (치명적이지 않음)
            logger.error("채널 닫기 중 오류가 발생했습니다", e);
        }
    }

    /**
     * 연결 타임아웃 확인 및 정리
     *
     * 지정된 시간보다 오래된 비활성 연결들을 찾아서 정리
     * 좀비 연결 방지와 리소스 절약을 위한 정기 정리 작업
     *
     * @param timeoutMillis 타임아웃 시간 (밀리초) - 이 시간보다 오래된 연결 정리
     */
    public void cleanupTimeoutConnections(long timeoutMillis) {
        // EventLoop 스레드 검사 및 위임
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> cleanupTimeoutConnections(timeoutMillis));
            return;
        }

        // currentTimeMillis(): 현재 시간을 밀리초로 반환 (1970년 1월 1일 기준)
        long currentTime = System.currentTimeMillis();
        int timeoutCount = 0;

        // entrySet(): Map의 모든 키-값 쌍을 Set으로 반환
        // for-each 루프: 컬렉션의 모든 요소를 순회
        for (Map.Entry<SocketChannel, ChannelContext> entry : channelContexts.entrySet()) {
            ChannelContext context = entry.getValue();

            // 마지막 활동 시간과 현재 시간의 차이가 타임아웃보다 크면 정리
            if (currentTime - context.getLastActivity() > timeoutMillis) {
                logger.debug("타임아웃 연결을 닫습니다 [{}]: {} (비활성 시간: {}ms)",
                        context.getChannelId(),                              // 채널 ID
                        getRemoteAddress(entry.getKey()),                   // 원격 주소
                        currentTime - context.getLastActivity());          // 비활성 시간

                // 타임아웃된 채널 정리
                closeChannel(entry.getKey());
                timeoutCount++;
            }
        }

        // 정리된 연결이 있으면 정보 로그 출력
        if (timeoutCount > 0) {
            logger.info("{}개의 타임아웃 연결을 정리했습니다", timeoutCount);
        }
    }

    /**
     * 채널 컨텍스트 가져오기
     *
     * 특정 채널의 상세 정보와 통계를 조회
     *
     * @param channel 조회할 소켓 채널 - 정보를 얻고자 하는 연결
     * @return 채널 컨텍스트 객체, 없으면 null
     */
    public ChannelContext getChannelContext(SocketChannel channel) {
        return channelContexts.get(channel);
    }

    /**
     * 채널 ID 가져오기
     *
     * 채널의 고유 식별자를 반환하여 로깅과 디버깅에 활용
     *
     * @param channel 조회할 소켓 채널 - ID를 얻고자 하는 연결
     * @return 채널 고유 ID, 컨텍스트가 없으면 -1
     */
    public long getChannelId(SocketChannel channel) {
        ChannelContext context = channelContexts.get(channel);
        // 삼항 연산자: condition ? value1 : value2
        // context가 null이 아니면 getChannelId() 호출, null이면 -1 반환
        return context != null ? context.getChannelId() : -1;
    }

    /**
     * 원격 주소 안전 가져오기
     *
     * 채널의 원격 주소를 안전하게 조회 (예외 발생시 기본값 반환)
     * 이미 닫힌 채널에서도 안전하게 동작
     *
     * @param channel 조회할 소켓 채널 - 원격 주소를 얻고자 하는 연결
     * @return 원격 주소 문자열, 조회 실패시 "unknown"
     */
    private String getRemoteAddress(SocketChannel channel) {
        try {
            // getRemoteAddress(): 연결된 원격 주소 반환
            // toString(): 주소 객체를 문자열로 변환
            return channel.getRemoteAddress().toString();
        } catch (Exception e) {
            // 채널이 이미 닫혔거나 오류 발생시 기본값 반환
            return "unknown";
        }
    }

    /**
     * 모든 연결 강제 종료
     *
     * 서버 종료시 모든 활성 연결을 정리
     * 리소스 누수 방지와 깔끔한 종료를 위한 메서드
     */
    public void closeAllConnections() {
        // EventLoop 스레드 검사 및 위임
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(this::closeAllConnections);  // 메서드 참조 사용
            return;
        }

        logger.info("모든 {}개의 활성 연결을 닫습니다", activeConnections);

        // keySet(): Map의 모든 키를 Set으로 반환
        // for-each 루프로 모든 채널에 대해 closeChannel 호출
        for (SocketChannel channel : channelContexts.keySet()) {
            closeChannel(channel);
        }

        // 모든 데이터 정리
        channelContexts.clear();   // Map의 모든 요소 제거
        activeConnections = 0;     // 활성 연결 수 초기화
    }

    /**
     * 통계 정보 반환
     *
     * 현재 SelectorManager의 성능 지표와 상태 정보
     * 모니터링과 성능 분석을 위한 데이터 제공
     *
     * @return SelectorStats 객체 - 현재 통계 정보
     */
    public SelectorStats getStats() {
        // new SelectorStats(): 현재 상태를 담은 통계 객체 생성
        return new SelectorStats(
                totalConnections,              // 총 연결 수 (누적)
                activeConnections,             // 현재 활성 연결 수
                bytesRead,                    // 읽은 총 바이트 수
                bytesWritten,                 // 쓴 총 바이트 수
                channelContexts.size()        // 현재 관리 중인 채널 수
        );
    }

    /**
     * 채널 컨텍스트 클래스
     *
     * 각 클라이언트 연결의 상세 정보와 통계를 관리
     * 연결별 성능 모니터링과 타임아웃 관리를 위한 데이터 보관
     */
    public static class ChannelContext {
        // 불변 필드들 - 연결 생성시 설정되고 변경되지 않음
        private final long channelId;                      // 채널 고유 식별자
        private final SocketChannel channel;               // 실제 소켓 채널
        private final ClientSocketEventHandler handler;    // 이벤트 처리 핸들러
        private final long createdTime;                    // 연결 생성 시간

        // 가변 필드들 - 연결 사용 중 지속적으로 업데이트
        // volatile: 스레드 간 가시성 보장 (메인 메모리에서 직접 읽기/쓰기)
        private volatile long lastActivity;    // 마지막 활동 시간 (타임아웃 계산용)
        private volatile long bytesRead;      // 읽은 총 바이트 수
        private volatile long bytesWritten;   // 쓴 총 바이트 수

        /**
         * ChannelContext 생성자
         *
         * @param channelId 채널 고유 식별자 - 로깅과 디버깅용 ID
         * @param channel 실제 소켓 채널 - 네트워크 연결
         * @param handler 이벤트 처리 핸들러 - READ/WRITE 이벤트 처리기
         */
        public ChannelContext(long channelId, SocketChannel channel, ClientSocketEventHandler handler) {
            this.channelId = channelId;
            this.channel = channel;
            this.handler = handler;
            // 생성 시간과 마지막 활동 시간을 현재 시간으로 초기화
            this.createdTime = System.currentTimeMillis();
            this.lastActivity = createdTime;  // 생성 즉시 활동한 것으로 간주
        }

        /**
         * 마지막 활동 시간 업데이트
         *
         * 데이터 읽기/쓰기 발생시 호출하여 타임아웃 계산에 사용
         * 연결이 활발히 사용되고 있음을 표시
         */
        public void updateLastActivity() {
            // volatile 필드에 현재 시간 저장 (스레드 안전)
            this.lastActivity = System.currentTimeMillis();
        }

        /**
         * 읽은 바이트 수 추가
         *
         * @param bytes 새로 읽은 바이트 수 - 통계에 누적할 값
         */
        public void addBytesRead(long bytes) {
            // +=: 복합 대입 연산자 - 기존 값에 새 값을 더함
            this.bytesRead += bytes;
        }

        /**
         * 쓴 바이트 수 추가
         *
         * @param bytes 새로 쓴 바이트 수 - 통계에 누적할 값
         */
        public void addBytesWritten(long bytes) {
            this.bytesWritten += bytes;
        }

        // === Getter 메서드들 ===
        // 필드에 대한 읽기 전용 접근을 제공

        public long getChannelId() { return channelId; }
        public SocketChannel getChannel() { return channel; }
        public ClientSocketEventHandler getHandler() { return handler; }
        public long getCreatedTime() { return createdTime; }
        public long getLastActivity() { return lastActivity; }
        public long getBytesRead() { return bytesRead; }
        public long getBytesWritten() { return bytesWritten; }

        /**
         * 연결 지속 시간 계산
         *
         * @return 연결 생성부터 현재까지의 시간 (밀리초)
         */
        public long getLifetimeMillis() {
            return System.currentTimeMillis() - createdTime;
        }

        /**
         * 객체의 문자열 표현
         *
         * 디버깅과 로깅을 위한 읽기 쉬운 문자열 형태로 정보 제공
         *
         * @return 컨텍스트 정보를 담은 문자열
         */
        @Override
        public String toString() {
            // String.format(): C의 printf와 유사한 형식화된 문자열 생성
            return String.format("ChannelContext{id=%d, lifetime=%dms, read=%d, written=%d}",
                    channelId, getLifetimeMillis(), bytesRead, bytesWritten);
        }
    }

    /**
     * Selector 통계 정보 클래스
     *
     * SelectorManager의 전체 성능 지표를 담는 불변 객체
     * 모니터링 시스템에서 활용할 수 있는 구조화된 데이터 제공
     */
    public static class SelectorStats {
        // final 필드들 - 생성 후 변경 불가능한 스냅샷 데이터
        private final long totalConnections;      // 서버 시작부터의 총 연결 수
        private final long activeConnections;     // 현재 활성 상태인 연결 수
        private final long bytesRead;            // 총 읽은 바이트 수
        private final long bytesWritten;         // 총 쓴 바이트 수
        private final int channelContextCount;   // 현재 관리 중인 채널 컨텍스트 수

        /**
         * SelectorStats 생성자
         *
         * @param totalConnections 총 연결 수 - 누적 연결 통계
         * @param activeConnections 활성 연결 수 - 현재 처리 중인 연결
         * @param bytesRead 읽은 바이트 수 - 수신 데이터 총량
         * @param bytesWritten 쓴 바이트 수 - 송신 데이터 총량
         * @param channelContextCount 채널 컨텍스트 수 - 관리 중인 채널 수
         */
        public SelectorStats(long totalConnections, long activeConnections,
                             long bytesRead, long bytesWritten, int channelContextCount) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.bytesRead = bytesRead;
            this.bytesWritten = bytesWritten;
            this.channelContextCount = channelContextCount;
        }

        // === Getter 메서드들 ===
        // 통계 데이터에 대한 읽기 전용 접근 제공

        public long getTotalConnections() { return totalConnections; }
        public long getActiveConnections() { return activeConnections; }
        public long getBytesRead() { return bytesRead; }
        public long getBytesWritten() { return bytesWritten; }
        public int getChannelContextCount() { return channelContextCount; }

        /**
         * 통계 정보의 문자열 표현
         *
         * 모니터링 도구나 로그에서 사용하기 적합한 형태로 정보 제공
         * MB 단위로 변환하여 가독성 향상
         *
         * @return 통계 정보를 담은 포맷된 문자열
         */
        @Override
        public String toString() {
            // / (1024*1024): 바이트를 메가바이트로 변환 (1MB = 1,048,576 bytes)
            return String.format("SelectorStats{total=%d, active=%d, read=%dMB, written=%dMB}",
                    totalConnections, activeConnections,
                    bytesRead / (1024*1024), bytesWritten / (1024*1024));
        }
    }
}