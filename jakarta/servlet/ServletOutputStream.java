package jakarta.servlet;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 서블릿에서 응답 본문을 쓰기 위한 출력 스트림입니다.
 *
 * ServletOutputStream은 표준 OutputStream을 확장하여 서블릿 환경에 특화된
 * 기능을 제공합니다. 주로 바이너리 데이터나 대용량 데이터를 전송할 때 사용됩니다.
 *
 * 사용 예시:
 * - 이미지, 동영상, PDF 등의 파일 다운로드
 * - 바이너리 데이터 스트리밍
 * - 압축된 데이터 전송
 * - 대용량 데이터의 청크 단위 전송
 */
public abstract class ServletOutputStream extends OutputStream {

    /**
     * 기본 생성자입니다.
     *
     * 서블릿 컨테이너가 구체적인 구현체를 생성할 때 사용됩니다.
     */
    protected ServletOutputStream() {
        // 추상 클래스이므로 직접 인스턴스화할 수 없습니다.
        // 서블릿 컨테이너가 구체적인 구현을 제공해야 합니다.
    }

    /**
     * 문자열을 출력 스트림에 씁니다.
     *
     * 이 메서드는 편의를 위해 제공되는 것으로, 문자열을 바이트로 변환하여 출력합니다.
     * 문자 인코딩은 플랫폼의 기본 인코딩을 사용합니다.
     *
     * 주의: 국제화가 중요한 애플리케이션에서는 PrintWriter를 사용하여
     * 명시적인 문자 인코딩을 지정하는 것이 좋습니다.
     *
     * @param s 출력할 문자열
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public void print(String s) throws IOException {
        if (s == null) {
            s = "null";  // null 문자열은 "null"로 출력
        }

        // 문자열을 플랫폼 기본 인코딩으로 바이트 배열로 변환
        byte[] bytes = s.getBytes();

        // 바이트 배열을 출력 스트림에 씀
        write(bytes);
    }

    /**
     * boolean 값을 문자열로 변환하여 출력합니다.
     *
     * @param b 출력할 boolean 값
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public void print(boolean b) throws IOException {
        print(String.valueOf(b));
    }

    /**
     * char 값을 문자열로 변환하여 출력합니다.
     *
     * @param c 출력할 char 값
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public void print(char c) throws IOException {
        print(String.valueOf(c));
    }

    /**
     * int 값을 문자열로 변환하여 출력합니다.
     *
     * @param i 출력할 int 값
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public void print(int i) throws IOException {
        print(String.valueOf(i));
    }

    /**
     * long 값을 문자열로 변환하여 출력합니다.
     *
     * @param l 출력할 long 값
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public void print(long l) throws IOException {
        print(String.valueOf(l));
    }

    /**
     * float 값을 문자열로 변환하여 출력합니다.
     *
     * @param f 출력할 float 값
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public void print(float f) throws IOException {
        print(String.valueOf(f));
    }

    /**
     * double 값을 문자열로 변환하여 출력합니다.
     *
     * @param d 출력할 double 값
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public void print(double d) throws IOException {
        print(String.valueOf(d));
    }

    /**
     * 줄바꿈 문자를 출력합니다.
     *
     * 플랫폼에 따른 줄바꿈 문자를 출력합니다.
     * - Windows: \r\n
     * - Unix/Linux: \n
     * - Mac (구버전): \r
     *
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public void println() throws IOException {
        print(System.lineSeparator());
    }

    /**
     * 문자열을 출력하고 줄바꿈을 추가합니다.
     *
     * @param s 출력할 문자열
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public void println(String s) throws IOException {
        print(s);
        println();
    }

    /**
     * boolean 값을 출력하고 줄바꿈을 추가합니다.
     *
     * @param b 출력할 boolean 값
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public void println(boolean b) throws IOException {
        print(b);
        println();
    }

    /**
     * char 값을 출력하고 줄바꿈을 추가합니다.
     *
     * @param c 출력할 char 값
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public void println(char c) throws IOException {
        print(c);
        println();
    }

    /**
     * int 값을 출력하고 줄바꿈을 추가합니다.
     *
     * @param i 출력할 int 값
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public void println(int i) throws IOException {
        print(i);
        println();
    }

    /**
     * long 값을 출력하고 줄바꿈을 추가합니다.
     *
     * @param l 출력할 long 값
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public void println(long l) throws IOException {
        print(l);
        println();
    }

    /**
     * float 값을 출력하고 줄바꿈을 추가합니다.
     *
     * @param f 출력할 float 값
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public void println(float f) throws IOException {
        print(f);
        println();
    }

    /**
     * double 값을 출력하고 줄바꿈을 추가합니다.
     *
     * @param d 출력할 double 값
     * @throws IOException 입출력 오류가 발생한 경우
     */
    public void println(double d) throws IOException {
        print(d);
        println();
    }

    /**
     * 논블로킹 모드에서 데이터를 즉시 쓸 수 있는지 확인합니다.
     *
     * 이 메서드는 Servlet 3.1에서 추가된 비동기 I/O 기능과 관련됩니다.
     * 논블로킹 I/O에서는 출력 버퍼가 가득 찼을 때 블로킹되지 않고
     * 즉시 false를 반환하여 다른 작업을 수행할 수 있게 합니다.
     *
     * 전통적인 블로킹 I/O에서는 항상 true를 반환합니다.
     *
     * @return 데이터를 즉시 쓸 수 있으면 true, 그렇지 않으면 false
     */
    public abstract boolean isReady();

    /**
     * 비동기 I/O 완료 시 호출될 리스너를 설정합니다.
     *
     * 논블로킹 I/O 모드에서 출력이 가능해졌을 때나
     * 모든 데이터 전송이 완료되었을 때 알림을 받기 위해 사용됩니다.
     *
     * @param writeListener 비동기 쓰기 이벤트를 처리할 리스너
     */
    public abstract void setWriteListener(WriteListener writeListener);
}




