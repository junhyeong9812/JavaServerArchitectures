package src.main.java.jakarta.servlet;

// Jakarta Servlet API - ServletOutputStream Class
// 실제 프로젝트에서는 src/main/java/jakarta/servlet/ServletOutputStream.java 위치에 배치

import java.io.IOException;
import java.io.OutputStream;
import java.io.CharConversionException;
import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * 클라이언트에게 바이너리 데이터를 전송하기 위한 출력 스트림 클래스입니다.
 *
 * ServletOutputStream은 ServletResponse.getOutputStream()을 통해 얻을 수 있으며,
 * 서블릿에서 클라이언트로 바이너리 응답 데이터를 전송하는 데 사용됩니다.
 *
 * 주요 사용 사례:
 * - 파일 다운로드 (이미지, 문서, 압축파일 등)
 * - 바이너리 API 응답 (바이트 배열, 스트림)
 * - 동적 생성된 바이너리 콘텐츠 전송
 * - RESTful API의 바이너리 응답
 *
 * 주의사항:
 * - ServletResponse.getWriter()와 동시에 사용할 수 없음
 * - Content-Type과 Content-Length 헤더를 적절히 설정해야 함
 * - 응답이 커밋된 후에는 헤더 변경 불가
 *
 * @author JavaServerArchitectures Project
 * @version 1.0
 * @see ServletResponse#getOutputStream()
 * @see ServletInputStream
 */
public abstract class ServletOutputStream extends OutputStream {

    private static final String LSTRING_FILE = "jakarta.servlet.LocalStrings";
    private static ResourceBundle lStrings = ResourceBundle.getBundle(LSTRING_FILE);

    /**
     * 보호된 생성자입니다.
     *
     * ServletOutputStream은 서블릿 컨테이너에 의해서만 생성되어야 하므로
     * 직접 인스턴스화할 수 없습니다.
     */
    protected ServletOutputStream() {
    }

    // ===============================
    // 텍스트 출력 편의 메소드
    // ===============================

    /**
     * boolean 값을 출력 스트림에 씁니다.
     *
     * @param b 쓸 boolean 값
     * @throws IOException I/O 오류가 발생한 경우
     */
    public void print(boolean b) throws IOException {
        String msg;
        if (b) {
            msg = lStrings.getString("value.true");
        } else {
            msg = lStrings.getString("value.false");
        }
        print(msg);
    }

    /**
     * char 값을 출력 스트림에 씁니다.
     *
     * @param c 쓸 char 값
     * @throws IOException I/O 오류가 발생한 경우
     */
    public void print(char c) throws IOException {
        print(String.valueOf(c));
    }

    /**
     * int 값을 출력 스트림에 씁니다.
     *
     * @param i 쓸 int 값
     * @throws IOException I/O 오류가 발생한 경우
     */
    public void print(int i) throws IOException {
        print(String.valueOf(i));
    }

    /**
     * long 값을 출력 스트림에 씁니다.
     *
     * @param l 쓸 long 값
     * @throws IOException I/O 오류가 발생한 경우
     */
    public void print(long l) throws IOException {
        print(String.valueOf(l));
    }

    /**
     * float 값을 출력 스트림에 씁니다.
     *
     * @param f 쓸 float 값
     * @throws IOException I/O 오류가 발생한 경우
     */
    public void print(float f) throws IOException {
        print(String.valueOf(f));
    }

    /**
     * double 값을 출력 스트림에 씁니다.
     *
     * @param d 쓸 double 값
     * @throws IOException I/O 오류가 발생한 경우
     */
    public void print(double d) throws IOException {
        print(String.valueOf(d));
    }

    /**
     * 문자열을 출력 스트림에 씁니다.
     *
     * 문자열이 null이면 "null"을 출력합니다.
     * 문자열은 응답의 문자 인코딩을 사용하여 바이트로 변환됩니다.
     *
     * 예시:
     * <pre>
     * {@code
     * ServletOutputStream out = response.getOutputStream();
     * response.setContentType("text/plain; charset=UTF-8");
     * out.print("Hello, World!");
     * out.print("안녕하세요");  // UTF-8로 인코딩됨
     * }
     * </pre>
     *
     * @param s 쓸 문자열
     * @throws IOException I/O 오류가 발생한 경우
     */
    public void print(String s) throws IOException {
        if (s == null) {
            s = "null";
        }

        int len = s.length();
        byte[] buffer = new byte[len];

        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);

            // ASCII 범위의 문자는 직접 변환
            if (c < 256) {
                buffer[i] = (byte) c;
            } else {
                // 비ASCII 문자는 인코딩 오류
                String errMsg = lStrings.getString("err.not_iso8859_1");
                Object[] errArgs = new Object[1];
                errArgs[0] = Character.valueOf(c);
                errMsg = MessageFormat.format(errMsg, errArgs);
                throw new CharConversionException(errMsg);
            }
        }

        write(buffer);
    }

    /**
     * 줄 바꿈 문자를 출력 스트림에 씁니다.
     *
     * 시스템의 줄 바꿈 문자(line.separator 프로퍼티)를 사용합니다.
     *
     * @throws IOException I/O 오류가 발생한 경우
     */
    public void println() throws IOException {
        print("\r\n");
    }

    /**
     * boolean 값과 줄 바꿈을 출력 스트림에 씁니다.
     *
     * @param b 쓸 boolean 값
     * @throws IOException I/O 오류가 발생한 경우
     */
    public void println(boolean b) throws IOException {
        print(b);
        println();
    }

    /**
     * char 값과 줄 바꿈을 출력 스트림에 씁니다.
     *
     * @param c 쓸 char 값
     * @throws IOException I/O 오류가 발생한 경우
     */
    public void println(char c) throws IOException {
        print(c);
        println();
    }

    /**
     * int 값과 줄 바꿈을 출력 스트림에 씁니다.
     *
     * @param i 쓸 int 값
     * @throws IOException I/O 오료가 발생한 경우
     */
    public void println(int i) throws IOException {
        print(i);
        println();
    }

    /**
     * long 값과 줄 바꿈을 출력 스트림에 씁니다.
     *
     * @param l 쓸 long 값
     * @throws IOException I/O 오류가 발생한 경우
     */
    public void println(long l) throws IOException {
        print(l);
        println();
    }

    /**
     * float 값과 줄 바꿈을 출력 스트림에 씁니다.
     *
     * @param f 쓸 float 값
     * @throws IOException I/O 오류가 발생한 경우
     */
    public void println(float f) throws IOException {
        print(f);
        println();
    }

    /**
     * double 값과 줄 바꿈을 출력 스트림에 씁니다.
     *
     * @param d 쓸 double 값
     * @throws IOException I/O 오류가 발생한 경우
     */
    public void println(double d) throws IOException {
        print(d);
        println();
    }

    /**
     * 문자열과 줄 바꿈을 출력 스트림에 씁니다.
     *
     * @param s 쓸 문자열
     * @throws IOException I/O 오류가 발생한 경우
     */
    public void println(String s) throws IOException {
        print(s);
        println();
    }

    // ===============================
    // 논블로킹 I/O 관련 메소드 (Servlet 3.1+)
    // ===============================

    /**
     * 출력 스트림에서 데이터를 블로킹 없이 즉시 쓸 수 있는지 확인합니다.
     *
     * 논블로킹 I/O 모드에서 사용되며, write() 호출 시 블로킹되지 않고
     * 즉시 데이터를 쓸 수 있는지 확인합니다.
     *
     * @return 즉시 쓸 수 있으면 true, 블로킹이 필요하면 false
     * @since Servlet 3.1
     */
    public abstract boolean isReady();

    /**
     * 논블로킹 I/O를 위한 리스너를 설정합니다.
     *
     * 논블로킹 I/O 모드에서 데이터를 쓸 수 있게 되거나
     * 에러가 발생했을 때 알림을 받기 위해 사용됩니다.
     *
     * 예시:
     * <pre>
     * {@code
     * ServletOutputStream output = response.getOutputStream();
     * output.setWriteListener(new WriteListener() {
     *     @Override
     *     public void onWritePossible() throws IOException {
     *         // 쓰기가 가능할 때 호출
     *         while (output.isReady()) {
     *             // 데이터 쓰기...
     *             if (hasMoreData()) {
     *                 output.write(getNextData());
     *             } else {
     *                 break;
     *             }
     *         }
     *     }
     *
     *     @Override
     *     public void onError(Throwable t) {
     *         // 에러가 발생했을 때 호출
     *         System.err.println("Error writing data: " + t.getMessage());
     *     }
     * });
     * }
     * </pre>
     *
     * @param writeListener 논블로킹 쓰기 이벤트를 처리할 리스너
     * @throws IllegalStateException 이미 리스너가 설정되었거나
     *                              동기 I/O 모드인 경우
     * @since Servlet 3.1
     */
    public abstract void setWriteListener(WriteListener writeListener);
}