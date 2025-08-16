package server.eventloop;

import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * ByteBuffer를 InputStream으로 래핑하는 유틸리티 클래스
 * HttpParser와 호환되도록 ByteBuffer 데이터를 스트림으로 제공
 */
public class ByteBufferInputStream extends InputStream {

    private final ByteBuffer buffer;

    /**
     * ByteBuffer로부터 InputStream 생성
     *
     * @param buffer 래핑할 ByteBuffer
     */
    public ByteBufferInputStream(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * 한 바이트 읽기
     *
     * @return 읽은 바이트 (0-255) 또는 EOF인 경우 -1
     */
    @Override
    public int read() {
        if (!buffer.hasRemaining()) {
            return -1; // EOF
        }
        return buffer.get() & 0xFF; // unsigned byte
    }

    /**
     * 바이트 배열로 읽기
     *
     * @param b 데이터를 저장할 배열
     * @param off 배열에서 시작할 오프셋
     * @param len 읽을 최대 바이트 수
     * @return 실제로 읽은 바이트 수 또는 EOF인 경우 -1
     */
    @Override
    public int read(byte[] b, int off, int len) {
        if (!buffer.hasRemaining()) {
            return -1; // EOF
        }

        // 읽을 수 있는 바이트 수 계산
        len = Math.min(len, buffer.remaining());
        buffer.get(b, off, len);
        return len;
    }

    /**
     * 사용 가능한 바이트 수 반환
     *
     * @return 즉시 읽을 수 있는 바이트 수
     */
    @Override
    public int available() {
        return buffer.remaining();
    }

    /**
     * 현재 위치 표시 지원 여부
     *
     * @return ByteBuffer는 mark를 지원하므로 true
     */
    @Override
    public boolean markSupported() {
        return true;
    }

    /**
     * 현재 위치 표시
     *
     * @param readlimit 표시 유효성을 위한 읽기 제한 (ByteBuffer에서는 무시됨)
     */
    @Override
    public synchronized void mark(int readlimit) {
        buffer.mark();
    }

    /**
     * 마지막으로 표시된 위치로 리셋
     */
    @Override
    public synchronized void reset() {
        buffer.reset();
    }

    /**
     * 지정된 바이트 수만큼 건너뛰기
     *
     * @param n 건너뛸 바이트 수
     * @return 실제로 건너뛴 바이트 수
     */
    @Override
    public long skip(long n) {
        if (n <= 0) {
            return 0;
        }

        int skipBytes = (int) Math.min(n, buffer.remaining());
        buffer.position(buffer.position() + skipBytes);
        return skipBytes;
    }

    /**
     * 스트림 닫기 (ByteBuffer에서는 특별한 작업 없음)
     */
    @Override
    public void close() {
        // ByteBuffer는 명시적으로 닫을 필요가 없음
    }

    /**
     * 현재 상태 정보 반환
     */
    @Override
    public String toString() {
        return String.format("ByteBufferInputStream{position=%d, remaining=%d, capacity=%d}",
                buffer.position(), buffer.remaining(), buffer.capacity());
    }
}