package server.eventloop;

import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * ByteBuffer를 InputStream으로 래핑하는 유틸리티 클래스
 * HttpParser와 호환되도록 ByteBuffer 데이터를 스트림으로 제공
 *
 * 왜 필요한가?
 * - ByteBuffer는 NIO에서 사용하는 데이터 컨테이너
 * - 기존 HttpParser는 InputStream을 기대함
 * - 이 클래스가 둘 사이의 어댑터 역할을 수행
 */
public class ByteBufferInputStream extends InputStream {

    // final로 선언하여 생성 후 변경 불가능하게 만듦
    // ByteBuffer: NIO에서 사용하는 바이트 데이터 컨테이너
    private final ByteBuffer buffer;

    /**
     * ByteBuffer로부터 InputStream 생성
     *
     * 생성자에서 ByteBuffer를 받아서 내부에 저장
     * 이후 모든 InputStream 메서드는 이 buffer를 기반으로 동작
     *
     * @param buffer 래핑할 ByteBuffer - 읽을 데이터가 들어있는 버퍼
     */
    public ByteBufferInputStream(ByteBuffer buffer) {
        // 전달받은 ByteBuffer를 내부 필드에 저장
        // null 체크는 하지 않음 - 호출자가 올바른 버퍼를 전달해야 함
        this.buffer = buffer;
    }

    /**
     * 한 바이트 읽기
     * InputStream의 핵심 메서드 - 다른 read 메서드들이 이를 기반으로 동작
     *
     * ByteBuffer.hasRemaining(): 읽을 수 있는 데이터가 있는지 확인
     * - position < limit 이면 true 반환
     * - 더 읽을 데이터가 없으면 false 반환
     *
     * @return 읽은 바이트 (0-255) 또는 EOF인 경우 -1
     */
    @Override
    public int read() {
        // hasRemaining(): ByteBuffer에 읽을 수 있는 데이터가 남아있는지 확인
        // position이 limit보다 작으면 true, 같거나 크면 false
        if (!buffer.hasRemaining()) {
            // EOF(End Of File) - 더 읽을 데이터가 없음을 의미
            // InputStream 규약에 따라 -1 반환
            return -1;
        }

        // buffer.get(): ByteBuffer에서 현재 position의 바이트를 읽고 position을 1 증가
        // & 0xFF: byte를 unsigned int로 변환 (자바의 byte는 -128~127이므로)
        // 예: -1 (0xFF) -> 255로 변환
        return buffer.get() & 0xFF;
    }

    /**
     * 바이트 배열로 읽기
     * 여러 바이트를 한번에 읽는 효율적인 방법
     *
     * Math.min(): 두 값 중 작은 값을 반환하는 정적 메서드
     * - 요청한 길이와 실제 남은 데이터 중 작은 값을 선택
     * - 버퍼 오버플로우 방지
     *
     * @param b 데이터를 저장할 배열 - 읽은 바이트들이 여기에 저장됨
     * @param off 배열에서 시작할 오프셋 - b[off]부터 데이터 저장 시작
     * @param len 읽을 최대 바이트 수 - 실제로는 이보다 적게 읽힐 수 있음
     * @return 실제로 읽은 바이트 수 또는 EOF인 경우 -1
     */
    @Override
    public int read(byte[] b, int off, int len) {
        // 읽을 데이터가 없으면 EOF 표시
        if (!buffer.hasRemaining()) {
            return -1;
        }

        // 읽을 수 있는 바이트 수 계산
        // buffer.remaining(): 현재 position부터 limit까지의 남은 바이트 수
        // Math.min(): 요청한 길이와 실제 남은 데이터 중 작은 값 선택
        len = Math.min(len, buffer.remaining());

        // buffer.get(byte[] dst, int offset, int length):
        // ByteBuffer에서 length만큼의 바이트를 읽어서 dst[offset]부터 저장
        // 자동으로 buffer의 position이 length만큼 증가됨
        buffer.get(b, off, len);

        // 실제로 읽은 바이트 수 반환
        return len;
    }

    /**
     * 사용 가능한 바이트 수 반환
     * 스트림에서 블로킹 없이 즉시 읽을 수 있는 바이트 수
     *
     * ByteBuffer는 메모리 기반이므로 모든 데이터가 즉시 사용 가능
     *
     * @return 즉시 읽을 수 있는 바이트 수
     */
    @Override
    public int available() {
        // buffer.remaining(): position과 limit 사이의 남은 바이트 수
        // ByteBuffer는 메모리 기반이므로 모든 remaining 데이터가 즉시 사용 가능
        return buffer.remaining();
    }

    /**
     * 현재 위치 표시 지원 여부
     * mark/reset 기능을 지원하는지 알려주는 메서드
     *
     * ByteBuffer.mark(): 현재 position을 mark로 저장
     * ByteBuffer.reset(): position을 마지막 mark 위치로 되돌림
     *
     * @return ByteBuffer는 mark를 지원하므로 true
     */
    @Override
    public boolean markSupported() {
        // ByteBuffer는 내장된 mark/reset 기능이 있음
        // 따라서 항상 true 반환
        return true;
    }

    /**
     * 현재 위치 표시
     * 나중에 reset()으로 이 위치로 돌아올 수 있도록 표시
     *
     * synchronized: 멀티스레드 환경에서 동시 접근 방지
     * - 여러 스레드가 동시에 mark를 호출해도 안전
     *
     * @param readlimit 표시 유효성을 위한 읽기 제한 (ByteBuffer에서는 무시됨)
     */
    @Override
    public synchronized void mark(int readlimit) {
        // buffer.mark(): 현재 position을 내부 mark 변수에 저장
        // readlimit 매개변수는 ByteBuffer에서는 사용되지 않음
        // (InputStream 인터페이스 호환성을 위해 존재)
        buffer.mark();
    }

    /**
     * 마지막으로 표시된 위치로 리셋
     * mark()로 표시한 위치로 position을 되돌림
     *
     * synchronized: mark()와 동일하게 스레드 안전성 보장
     */
    @Override
    public synchronized void reset() {
        // buffer.reset(): position을 마지막 mark 위치로 설정
        // mark가 설정되지 않았으면 InvalidMarkException 발생 가능
        buffer.reset();
    }

    /**
     * 지정된 바이트 수만큼 건너뛰기
     * 데이터를 읽지 않고 position만 앞으로 이동
     *
     * Math.min(): 요청한 건너뛸 바이트 수와 실제 남은 바이트 수 중 작은 값
     * position(): 현재 position 값 반환
     * position(int): position을 특정 값으로 설정
     *
     * @param n 건너뛸 바이트 수 - 음수이면 0으로 처리
     * @return 실제로 건너뛴 바이트 수
     */
    @Override
    public long skip(long n) {
        // 음수이거나 0이면 아무것도 건너뛰지 않음
        if (n <= 0) {
            return 0;
        }

        // 실제로 건너뛸 수 있는 바이트 수 계산
        // (int) 캐스팅: long을 int로 변환 (ByteBuffer는 int 기반)
        // Math.min(): 요청 바이트 수와 남은 바이트 수 중 작은 값
        int skipBytes = (int) Math.min(n, buffer.remaining());

        // buffer.position(): 현재 position 반환
        // buffer.position(int): position을 특정 값으로 설정
        // 현재 position + skipBytes로 이동하여 데이터 건너뛰기
        buffer.position(buffer.position() + skipBytes);

        // 실제로 건너뛴 바이트 수 반환 (long 타입으로)
        return skipBytes;
    }

    /**
     * 스트림 닫기 (ByteBuffer에서는 특별한 작업 없음)
     * InputStream 인터페이스 구현을 위해 존재
     *
     * ByteBuffer는 메모리 기반이므로 명시적으로 닫을 필요 없음
     * 파일이나 네트워크 스트림과 달리 리소스 해제가 불필요
     */
    @Override
    public void close() {
        // ByteBuffer는 메모리 기반이므로 명시적인 정리 작업 불필요
        // 가비지 컬렉터가 자동으로 메모리 해제
        // 이 메서드는 InputStream 인터페이스 호환성을 위해서만 존재
    }

    /**
     * 현재 상태 정보 반환
     * 디버깅과 로깅에 유용한 객체 상태 정보
     *
     * String.format(): printf 스타일의 문자열 포맷팅
     * buffer.position(): 현재 읽기 위치
     * buffer.remaining(): 남은 읽을 수 있는 바이트 수
     * buffer.capacity(): 버퍼의 전체 크기
     */
    @Override
    public String toString() {
        // String.format(): 형식화된 문자열 생성
        // %d: 정수 값을 10진수로 출력
        return String.format("ByteBufferInputStream{position=%d, remaining=%d, capacity=%d}",
                buffer.position(),    // 현재 읽기 위치
                buffer.remaining(),   // 아직 읽지 않은 바이트 수
                buffer.capacity());   // 버퍼의 전체 크기
    }
}