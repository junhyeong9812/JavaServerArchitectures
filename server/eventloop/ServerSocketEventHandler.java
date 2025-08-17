package server.eventloop;

// 필요한 클래스들을 import
import java.nio.channels.SocketChannel;      // NIO 논블로킹 소켓 채널 클래스

/**
 * 서버 소켓 이벤트 핸들러 인터페이스
 *
 * 역할:
 * - 새로운 클라이언트 연결 요청을 처리
 * - ServerSocketChannel에서 발생하는 OP_ACCEPT 이벤트 처리
 * - 연결 수락 후 후속 처리 로직 정의
 *
 * 사용 패턴:
 * - EventLoop에서 accept 이벤트 감지시 호출
 * - 구현체에서 새 연결에 대한 초기화 작업 수행
 * - 일반적으로 NonBlockingHandler가 구현하여 클라이언트 소켓 등록 담당
 *
 * 함수형 인터페이스:
 * - @FunctionalInterface 어노테이션으로 람다 표현식 사용 가능
 * - 하나의 추상 메서드만 가지므로 함수형 프로그래밍 스타일 적용 가능
 */
@FunctionalInterface  // 함수형 인터페이스임을 명시 - 람다 표현식으로 구현 가능
public interface ServerSocketEventHandler {

    /**
     * 새로운 클라이언트 연결 수락 처리
     *
     * ServerSocketChannel.accept()를 통해 새로운 클라이언트 연결이 생성되었을 때 호출됩니다.
     * 이 메서드는 EventLoop의 메인 스레드에서 실행되므로 빠르게 처리되어야 합니다.
     *
     * 처리 과정:
     * 1. 새로 연결된 클라이언트 채널을 받음
     * 2. 채널을 논블로킹 모드로 설정
     * 3. Selector에 등록하여 READ 이벤트 감지 시작
     * 4. 연결별 상태 정보 초기화
     * 5. 로깅 및 통계 정보 업데이트
     *
     * 주의사항:
     * - 이 메서드는 논블로킹으로 빠르게 실행되어야 함
     * - 시간이 오래 걸리는 작업은 별도 스레드에서 수행
     * - 예외 발생시 연결을 적절히 정리해야 함
     *
     * @param eventLoop 이벤트루프 인스턴스
     *                  - 현재 이벤트를 처리하는 EventLoop 객체
     *                  - 추가 작업 스케줄링이나 상태 확인에 사용
     *                  - inEventLoop() 메서드로 현재 스레드 확인 가능
     *
     * @param clientChannel 새로 연결된 클라이언트 채널
     *                     - ServerSocketChannel.accept()로 생성된 새 연결
     *                     - 클라이언트와의 실제 데이터 통신에 사용될 채널
     *                     - 논블로킹 모드로 설정하고 Selector에 등록해야 함
     *                     - getRemoteAddress()로 클라이언트 주소 확인 가능
     */
    void onAccept(EventLoop eventLoop, SocketChannel clientChannel);

    /*
     * 함수형 인터페이스 사용 예시:
     *
     * // 1. 람다 표현식으로 구현
     * ServerSocketEventHandler handler = (eventLoop, clientChannel) -> {
     *     // 새 연결 처리 로직
     *     selectorManager.registerClientSocket(clientChannel, clientHandler);
     * };
     *
     * // 2. 메서드 참조로 구현 (기존 메서드가 있는 경우)
     * ServerSocketEventHandler handler = nonBlockingHandler::onAccept;
     *
     * // 3. 익명 클래스로 구현 (전통적인 방식)
     * ServerSocketEventHandler handler = new ServerSocketEventHandler() {
     *     @Override
     *     public void onAccept(EventLoop eventLoop, SocketChannel clientChannel) {
     *         // 구현 로직
     *     }
     * };
     */
}