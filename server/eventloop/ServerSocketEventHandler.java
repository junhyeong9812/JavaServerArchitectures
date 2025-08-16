package server.eventloop;

import java.nio.channels.SocketChannel;

/**
 * 서버 소켓 이벤트 핸들러 인터페이스
 * 새로운 클라이언트 연결을 처리
 */
@FunctionalInterface
public interface ServerSocketEventHandler {

    /**
     * 새로운 클라이언트 연결 수락 처리
     *
     * @param eventLoop 이벤트루프 인스턴스
     * @param clientChannel 새로 연결된 클라이언트 채널
     */
    void onAccept(EventLoop eventLoop, SocketChannel clientChannel);
}