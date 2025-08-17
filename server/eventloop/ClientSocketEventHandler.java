package server.eventloop;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * 클라이언트 소켓 이벤트 핸들러 인터페이스
 * 클라이언트 연결의 읽기/쓰기/연결 해제 이벤트를 처리
 *
 * 인터페이스를 사용하는 이유:
 * - 다양한 프로토콜(HTTP, WebSocket 등)에 대해 다른 구현체 제공 가능
 * - EventLoop는 구체적인 프로토콜을 몰라도 됨 (의존성 역전)
 * - 테스트용 Mock 구현체 작성 용이
 * - 코드의 확장성과 유지보수성 향상
 */
public interface ClientSocketEventHandler {

    /**
     * 클라이언트로부터 데이터 읽기 이벤트 처리
     *
     * 언제 호출되는가?
     * - Selector.select()에서 SelectionKey.OP_READ 이벤트가 감지될 때
     * - 클라이언트가 서버로 데이터를 전송했을 때
     * - SocketChannel.read()로 데이터를 읽은 후 호출됨
     *
     * 구현체에서 해야 할 일:
     * - ByteBuffer에서 읽은 데이터를 파싱 (HTTP 요청, WebSocket 프레임 등)
     * - 요청 처리 로직 수행
     * - 필요시 응답 데이터 준비
     * - 연결 상태 관리
     *
     * @param eventLoop 이벤트루프 인스턴스 - 비동기 작업 스케줄링에 사용
     * @param channel 클라이언트 채널 - 응답 전송시 사용할 SocketChannel
     * @param buffer 읽은 데이터가 담긴 버퍼 - flip() 호출된 상태로 전달됨 (읽기 준비 완료)
     * @throws IOException I/O 오류 발생시 - 네트워크 문제, 채널 닫힘 등
     */
    void onRead(EventLoop eventLoop, SocketChannel channel, ByteBuffer buffer) throws IOException;

    /**
     * 클라이언트로 데이터 쓰기 이벤트 처리
     *
     * 언제 호출되는가?
     * - Selector.select()에서 SelectionKey.OP_WRITE 이벤트가 감지될 때
     * - SocketChannel의 송신 버퍼에 여유 공간이 생겼을 때
     * - 이전에 channel.write()가 0을 반환했던 경우 (버퍼가 가득 찼었음)
     *
     * 구현체에서 해야 할 일:
     * - 대기 중인 응답 데이터를 SocketChannel.write()로 전송
     * - 부분 전송된 데이터의 남은 부분 계속 전송
     * - 모든 데이터 전송 완료시 OP_WRITE interest 제거
     * - 전송 실패시 적절한 에러 처리
     *
     * @param eventLoop 이벤트루프 인스턴스 - 후속 작업 스케줄링에 사용
     * @param channel 클라이언트 채널 - 데이터 전송에 사용할 SocketChannel
     * @throws IOException I/O 오류 발생시 - 전송 실패, 채널 닫힘 등
     */
    void onWrite(EventLoop eventLoop, SocketChannel channel) throws IOException;

    /**
     * 클라이언트 연결 해제 이벤트 처리
     *
     * 언제 호출되는가?
     * - SocketChannel.read()가 -1을 반환했을 때 (클라이언트가 연결 종료)
     * - 네트워크 오류로 연결이 끊어졌을 때
     * - 서버에서 명시적으로 연결을 끊을 때
     * - I/O 처리 중 예외가 발생했을 때
     *
     * 구현체에서 해야 할 일:
     * - 해당 클라이언트 관련 리소스 정리
     * - 세션 정보 삭제
     * - 대기 중인 응답 데이터 폐기
     * - 연결 통계 업데이트
     * - 로그 기록
     *
     * 주의사항:
     * - 이 메서드에서는 IOException을 던지지 않음 (이미 연결이 끊어진 상태)
     * - 정리 작업에서 예외가 발생해도 다른 연결에 영향 주지 않도록 주의
     *
     * @param eventLoop 이벤트루프 인스턴스 - 정리 작업 스케줄링에 사용
     * @param channel 연결이 해제된 클라이언트 채널 - 이미 닫혀있을 수 있음
     */
    void onDisconnect(EventLoop eventLoop, SocketChannel channel);
}