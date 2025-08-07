package src.main.java.jakarta.servlet;

// Jakarta Servlet API - ServletInputStream Class
// 실제 프로젝트에서는 src/main/java/jakarta/servlet/ServletInputStream.java 위치에 배치

import java.io.IOException;
import java.io.InputStream;

/**
 * 클라이언트로부터 전송된 바이너리 데이터를 읽기 위한 입력 스트림 클래스입니다.
 *
 * ServletInputStream은 ServletRequest.getInputStream()을 통해 얻을 수 있으며,
 * 클라이언트가 전송한 요청 바디의 원시 바이너리 데이터를 읽는 데 사용됩니다.
 *
 * 주요 사용 사례:
 * - 파일 업로드 처리
 * - 이미지, 비디오 등 바이너리 데이터 수신
 * - JSON, XML 등의 원시 데이터 스트림 처리
 * - RESTful API의 바이너리 페이로드 처리
 *
 * 주의사항:
 * - ServletRequest.getReader()와 동시에 사용할 수 없음
 * - HTTP 요청의 Content-Length 헤더를 확인하여 데이터 크기를 알 수 있음
 * - 스트림은 한 번만 읽을 수 있음 (재사용 불가)
 *
 * @author JavaServerArchitectures Project
 * @version 1.0
 * @see ServletRequest#getInputStream()
 * @see ServletOutputStream
 */
public abstract class ServletInputStream extends InputStream {

    /**
     * 보호된 생성자입니다.
     *
     * ServletInputStream은 서블릿 컨테이너에 의해서만 생성되어야 하므로
     * 직접 인스턴스화할 수 없습니다.
     */
    protected ServletInputStream() {
    }

    /**
     * 입력 스트림에서 한 줄을 읽어 바이트 배열로 반환합니다.
     *
     * 한 줄은 캐리지 리턴('\r'), 라인 피드('\n'), 또는 이 둘의 조합('\r\n')으로
     * 구분됩니다. 줄 구분자는 반환되는 바이트 배열에 포함되지 않습니다.
     *
     * 이 메소드는 주로 HTTP 헤더 파싱이나 텍스트 기반 프로토콜 처리에 사용됩니다.
     *
     * 예시:
     * <pre>
     * {@code
     * ServletInputStream input = request.getInputStream();
     * byte[] buffer = new byte[1024];
     * int bytesRead = input.readLine(buffer, 0, buffer.length);
     *
     * if (bytesRead > 0) {
     *     String line = new String(buffer, 0, bytesRead, "UTF-8");
     *     System.out.println("Read line: " + line);
     * }
     * }
     * </pre>
     *
     * @param b 데이터를 저장할 바이트 배열
     * @param off 배열에서 데이터를 쓰기 시작할 오프셋
     * @param len 읽을 최대 바이트 수
     * @return 실제로 읽은 바이트 수, 스트림 끝에 도달했으면 -1
     * @throws IOException I/O 오류가 발생한 경우
     * @throws IndexOutOfBoundsException 오프셋이나 길이가 잘못된 경우
     */
    public int readLine(byte[] b, int off, int len) throws IOException {
        if (len <= 0) {
            return 0;
        }

        int count = 0;
        int c;

        while ((c = read()) != -1) {
            b[off++] = (byte) c;
            count++;

            // 라인 피드를 만나면 줄 끝
            if (c == '\n') {
                break;
            }

            // 버퍼가 가득 찬 경우
            if (count == len) {
                break;
            }
        }

        return (count > 0) ? count : -1;
    }

    // ===============================
    // 논블로킹 I/O 관련 메소드 (Servlet 3.1+)
    // ===============================

    /**
     * 스트림에서 데이터를 블로킹 없이 즉시 읽을 수 있는지 확인합니다.
     *
     * 논블로킹 I/O 모드에서 사용되며, read() 호출 시 블로킹되지 않고
     * 즉시 데이터를 읽을 수 있는지 확인합니다.
     *
     * @return 즉시 읽을 수 있는 데이터가 있으면 true,
     *         블로킹이 필요하면 false
     * @since Servlet 3.1
     */
    public abstract boolean isFinished();

    /**
     * 스트림에서 읽을 수 있는 데이터가 있는지 확인합니다.
     *
     * 논블로킹 I/O 모드에서 사용되며, 현재 스트림에 읽을 수 있는
     * 데이터가 버퍼링되어 있는지 확인합니다.
     *
     * @return 읽을 수 있는 데이터가 있으면 true, 없으면 false
     * @since Servlet 3.1
     */
    public abstract boolean isReady();

    /**
     * 논블로킹 I/O를 위한 리스너를 설정합니다.
     *
     * 논블로킹 I/O 모드에서 데이터가 읽기 가능해지거나
     * 에러가 발생했을 때 알림을 받기 위해 사용됩니다.
     *
     * 예시:
     * <pre>
     * {@code
     * ServletInputStream input = request.getInputStream();
     * input.setReadListener(new ReadListener() {
     *     @Override
     *     public void onDataAvailable() throws IOException {
     *         // 읽을 수 있는 데이터가 있을 때 호출
     *         while (input.isReady()) {
     *             int data = input.read();
     *             if (data == -1) break;
     *             // 데이터 처리...
     *         }
     *     }
     *
     *     @Override
     *     public void onAllDataRead() throws IOException {
     *         // 모든 데이터를 다 읽었을 때 호출
     *         System.out.println("All data has been read");
     *     }
     *
     *     @Override
     *     public void onError(Throwable t) {
     *         // 에러가 발생했을 때 호출
     *         System.err.println("Error reading data: " + t.getMessage());
     *     }
     * });
     * }
     * </pre>
     *
     * @param readListener 논블로킹 읽기 이벤트를 처리할 리스너
     * @throws IllegalStateException 이미 리스너가 설정되었거나
     *                              동기 I/O 모드인 경우
     * @since Servlet 3.1
     */
    public abstract void setReadListener(ReadListener readListener);
}