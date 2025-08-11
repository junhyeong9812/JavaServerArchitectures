package jakarta.servlet;

import java.io.IOException;
import java.io.InputStream;

/**
 * 서블릿에서 요청 본문을 읽기 위한 입력 스트림입니다.
 *
 * ServletInputStream은 표준 InputStream을 확장하여 서블릿 환경에 특화된
 * 기능을 제공합니다. 주로 바이너리 데이터나 대용량 데이터를 처리할 때 사용됩니다.
 *
 * 사용 예시:
 * - 파일 업로드 처리
 * - 이미지나 동영상 등의 바이너리 데이터 수신
 * - JSON이나 XML 등의 구조화된 데이터 읽기
 * - 스트리밍 데이터 처리
 */
public abstract class ServletInputStream extends InputStream {

    /**
     * 기본 생성자입니다.
     *
     * 서블릿 컨테이너가 구체적인 구현체를 생성할 때 사용됩니다.
     */
    protected ServletInputStream() {
        // 추상 클래스이므로 직접 인스턴스화할 수 없습니다.
        // 서블릿 컨테이너가 구체적인 구현을 제공해야 합니다.
    }

    /**
     * 입력 스트림에서 한 줄을 읽어 바이트 배열로 반환합니다.
     *
     * 이 메서드는 '\n' (LF) 또는 '\r\n' (CRLF)로 끝나는 한 줄을 읽습니다.
     * HTTP 프로토콜에서 헤더를 파싱하거나 라인 단위로 데이터를 처리할 때 유용합니다.
     *
     * 동작 방식:
     * 1. 바이트를 하나씩 읽으면서 줄바꿈 문자를 찾습니다
     * 2. '\r\n' 또는 '\n'을 만나면 해당 줄을 반환합니다
     * 3. 줄바꿈 문자는 결과에 포함되지 않습니다
     * 4. 스트림 끝에 도달하면 마지막 줄을 반환합니다
     *
     * 주의사항:
     * - 매우 긴 줄이 있으면 메모리 문제가 발생할 수 있습니다
     * - 바이너리 데이터에는 적합하지 않습니다
     * - 문자 인코딩은 고려되지 않습니다 (바이트 단위 처리)
     *
     * @param b 읽은 데이터를 저장할 바이트 배열
     * @param off 배열에서 데이터를 저장할 시작 위치
     * @param len 읽을 최대 바이트 수
     * @return 실제로 읽은 바이트 수, 줄이 없으면 -1
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public int readLine(byte[] b, int off, int len) throws IOException {
        // 매개변수 검증
        if (b == null) {
            throw new NullPointerException("바이트 배열이 null입니다");
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException("잘못된 배열 범위입니다");
        }

        // 읽을 수 있는 길이가 0이면 즉시 반환
        if (len == 0) {
            return 0;
        }

        int count = 0;  // 읽은 바이트 수
        int c;          // 현재 읽은 바이트

        // 바이트를 하나씩 읽으면서 줄바꿈 문자를 찾습니다
        while (count < len) {
            c = read();  // 한 바이트 읽기

            // 스트림 끝에 도달한 경우
            if (c == -1) {
                // 아무것도 읽지 못했으면 -1 반환
                return count == 0 ? -1 : count;
            }

            // 읽은 바이트를 배열에 저장
            b[off + count] = (byte) c;
            count++;

            // 줄바꿈 문자 '\n'을 만난 경우 줄 읽기 완료
            if (c == '\n') {
                break;
            }
        }

        return count;
    }

    /**
     * 논블로킹 모드에서 데이터를 즉시 읽을 수 있는지 확인합니다.
     *
     * 이 메서드는 Servlet 3.1에서 추가된 비동기 I/O 기능과 관련됩니다.
     * 논블로킹 I/O에서는 데이터가 준비되지 않았을 때 블로킹되지 않고
     * 즉시 false를 반환하여 다른 작업을 수행할 수 있게 합니다.
     *
     * 전통적인 블로킹 I/O에서는 항상 true를 반환합니다.
     *
     * @return 데이터를 즉시 읽을 수 있으면 true, 그렇지 않으면 false
     */
    public abstract boolean isReady();

    /**
     * 비동기 I/O 완료 시 호출될 리스너를 설정합니다.
     *
     * 논블로킹 I/O 모드에서 데이터 읽기가 가능해졌을 때나
     * 모든 데이터를 읽었을 때 알림을 받기 위해 사용됩니다.
     *
     * @param readListener 비동기 읽기 이벤트를 처리할 리스너
     */
    public abstract void setReadListener(ReadListener readListener);

    /**
     * 현재 논블로킹 모드로 설정되어 있는지 확인합니다.
     *
     * @return 논블로킹 모드이면 true, 블로킹 모드이면 false
     */
    public abstract boolean isFinished();
}

