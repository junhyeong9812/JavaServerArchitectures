package server.eventloop;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * 클라이언트 소켓 이벤트 핸들러 인터페이스
 * 클라이언트 연결의 읽기/쓰기/연결 해제 이벤트를 처리
 */
public interface ClientSocketEventHandler {

    /**
     * 클라이언트로부터 데이터 읽기 이벤트 처리
     *
     * @param eventLoop 이벤트루프 인스턴스
     * @param channel 클라이언트 채널
     * @param buffer 읽은 데이터가 담긴 버퍼
     * @throws IOException I/O 오류 발생시
     */
    void onRead(EventLoop eventLoop, SocketChannel channel, ByteBuffer buffer) throws IOException;

    /**
     * 클라이언트로 데이터 쓰기 이벤트 처리
     *
     * @param eventLoop 이벤트루프 인스턴스
     * @param channel 클라이언트 채널
     * @throws IOException I/O 오류 발생시
     */
    void onWrite(EventLoop eventLoop, SocketChannel channel) throws IOException;

    /**
     * 클라이언트 연결 해제 이벤트 처리
     *
     * @param eventLoop 이벤트루프 인스턴스
     * @param channel 연결이 해제된 클라이언트 채널
     */
    void onDisconnect(EventLoop eventLoop, SocketChannel channel);
}