package src.main.java.jakarta.servlet;

// Jakarta Servlet API - ServletResponse Interface
// 실제 프로젝트에서는 src/main/java/jakarta/servlet/ServletResponse.java 위치에 배치

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * 서블릿이 클라이언트에게 응답을 보내는 데 사용하는 인터페이스입니다.
 *
 * ServletResponse는 서블릿 컨테이너가 클라이언트로의 응답을 캡슐화하여
 * 서블릿의 service() 메소드에 전달하기 위해 생성하는 객체입니다.
 *
 * 이 인터페이스는 프로토콜에 독립적인 응답 기능을 제공하며, HTTP 프로토콜에
 * 특화된 기능은 HttpServletResponse에서 제공됩니다.
 *
 * ServletResponse를 통해 다음과 같은 작업을 수행할 수 있습니다:
 * - 응답 바디 작성 (텍스트 또는 바이너리)
 * - 응답 헤더 설정 (콘텐츠 타입, 문자 인코딩 등)
 * - 버퍼링 제어
 * - 로케일 설정
 *
 * @author JavaServerArchitectures Project
 * @version 1.0
 * @see HttpServletResponse
 * @see ServletRequest
 */
public interface ServletResponse {

    // ===============================
    // 응답 바디 작성 메소드
    // ===============================

    /**
     * 응답 바디에 바이너리 데이터를 쓰기 위한 ServletOutputStream을 반환합니다.
     *
     * 이미지, 파일, 압축된 데이터 등 바이너리 콘텐츠를 전송할 때 사용합니다.
     * getWriter()와 함께 사용할 수 없습니다.
     *
     * 예시:
     * <pre>
     * {@code
     * // 이미지 파일 전송
     * response.setContentType("image/jpeg");
     * ServletOutputStream out = response.getOutputStream();
     *
     * byte[] imageData = loadImageFromFile();
     * out.write(imageData);
     * out.flush();
     * }
     * </pre>
     *
     * @return 바이너리 데이터를 쓸 수 있는 ServletOutputStream
     * @throws IOException I/O 오류가 발생한 경우
     * @throws IllegalStateException 이미 getWriter()가 호출된 경우
     */
    ServletOutputStream getOutputStream() throws IOException;

    /**
     * 응답 바디에 텍스트를 쓰기 위한 PrintWriter를 반환합니다.
     *
     * HTML, JSON, XML, 일반 텍스트 등을 전송할 때 사용합니다.
     * 문자 인코딩을 자동으로 처리합니다.
     * getOutputStream()과 함께 사용할 수 없습니다.
     *
     * 예시:
     * <pre>
     * {@code
     * // JSON 응답 전송
     * response.setContentType("application/json");
     * response.setCharacterEncoding("UTF-8");
     *
     * PrintWriter out = response.getWriter();
     * out.println("{\"message\": \"Hello, World!\"}");
     * out.flush();
     * }
     * </pre>
     *
     * @return 텍스트를 쓸 수 있는 PrintWriter
     * @throws IOException I/O 오류가 발생한 경우
     * @throws IllegalStateException 이미 getOutputStream()이 호출된 경우
     */
    PrintWriter getWriter() throws IOException;

    // ===============================
    // 문자 인코딩 및 콘텐츠 타입 설정
    // ===============================

    /**
     * 응답 바디의 문자 인코딩을 설정합니다.
     *
     * getWriter()를 호출하기 전에 설정해야 효과가 있습니다.
     * Content-Type 헤더의 charset 파라미터도 함께 설정됩니다.
     *
     * @param charset 문자 인코딩 (예: "UTF-8", "ISO-8859-1")
     */
    void setCharacterEncoding(String charset);

    /**
     * 현재 설정된 응답의 문자 인코딩을 반환합니다.
     *
     * @return 문자 인코딩, 설정되지 않았으면 null
     * @since Servlet 2.4
     */
    String getCharacterEncoding();

    /**
     * 응답의 콘텐츠 타입을 설정합니다.
     *
     * Content-Type 헤더를 설정하고, charset이 포함된 경우 문자 인코딩도 설정합니다.
     *
     * 예시:
     * <pre>
     * {@code
     * response.setContentType("text/html; charset=UTF-8");
     * response.setContentType("application/json");
     * response.setContentType("image/png");
     * }
     * </pre>
     *
     * @param type 콘텐츠 타입 (MIME 타입)
     */
    void setContentType(String type);

    /**
     * 현재 설정된 응답의 콘텐츠 타입을 반환합니다.
     *
     * @return 콘텐츠 타입, 설정되지 않았으면 null
     * @since Servlet 2.4
     */
    String getContentType();

    /**
     * 응답 바디의 길이(바이트 수)를 설정합니다.
     *
     * Content-Length 헤더를 설정합니다. HTTP/1.1에서 연결 유지(Keep-Alive)를
     * 위해서는 정확한 콘텐츠 길이 설정이 중요합니다.
     *
     * @param len 바디 길이 (바이트 단위)
     * @deprecated Servlet 5.0부터 setContentLengthLong() 사용 권장
     */
    @Deprecated
    void setContentLength(int len);

    /**
     * 응답 바디의 길이(바이트 수)를 long으로 설정합니다.
     *
     * 2GB를 넘는 응답을 전송할 때 사용합니다.
     *
     * @param length 바디 길이 (바이트 단위)
     * @since Servlet 3.1
     */
    void setContentLengthLong(long length);

    // ===============================
    // 버퍼링 제어 메소드
    // ===============================

    /**
     * 응답 버퍼의 크기를 설정합니다.
     *
     * 응답을 버퍼링하여 성능을 향상시킬 수 있습니다. 버퍼가 가득 차거나
     * flushBuffer()가 호출되면 클라이언트로 전송됩니다.
     *
     * 첫 번째 출력이 발생하기 전에 설정해야 합니다.
     *
     * @param size 버퍼 크기 (바이트 단위)
     * @throws IllegalStateException 이미 출력이 시작된 경우
     */
    void setBufferSize(int size);

    /**
     * 현재 설정된 응답 버퍼의 크기를 반환합니다.
     *
     * @return 버퍼 크기 (바이트 단위)
     */
    int getBufferSize();

    /**
     * 응답 버퍼를 강제로 클라이언트에게 전송합니다.
     *
     * 버퍼에 있는 모든 내용이 즉시 클라이언트로 전송되고 버퍼가 비워집니다.
     * 한 번 flush되면 응답 헤더는 더 이상 변경할 수 없습니다.
     *
     * @throws IOException I/O 오류가 발생한 경우
     */
    void flushBuffer() throws IOException;

    /**
     * 버퍼가 클라이언트에게 전송되었는지 확인합니다.
     *
     * @return 버퍼가 전송되었으면 true, 아직 버퍼링 중이면 false
     */
    boolean isCommitted();

    /**
     * 응답 버퍼를 지우고 상태 코드와 헤더를 초기화합니다.
     *
     * 응답이 커밋되기 전에만 호출할 수 있습니다.
     *
     * @throws IllegalStateException 응답이 이미 커밋된 경우
     */
    void reset();

    /**
     * 응답 버퍼만 지웁니다.
     *
     * 상태 코드와 헤더는 그대로 유지됩니다.
     * 응답이 커밋되기 전에만 호출할 수 있습니다.
     *
     * @throws IllegalStateException 응답이 이미 커밋된 경우
     */
    void resetBuffer();

    // ===============================
    // 로케일 설정 메소드
    // ===============================

    /**
     * 응답의 로케일을 설정합니다.
     *
     * 로케일 설정은 문자 인코딩과 Content-Language 헤더에 영향을 줄 수 있습니다.
     * 명시적으로 문자 인코딩이 설정되지 않은 경우, 로케일에 따른 기본 인코딩이 사용됩니다.
     *
     * 예시:
     * <pre>
     * {@code
     * response.setLocale(Locale.KOREA);     // 한국어, charset은 EUC-KR 또는 UTF-8
     * response.setLocale(Locale.JAPANESE);  // 일본어, charset은 Shift_JIS 등
     * response.setLocale(Locale.US);        // 영어, charset은 ISO-8859-1
     * }
     * </pre>
     *
     * @param loc 로케일
     */
    void setLocale(Locale loc);

    /**
     * 현재 설정된 응답의 로케일을 반환합니다.
     *
     * 명시적으로 설정되지 않은 경우 서버의 기본 로케일을 반환합니다.
     *
     * @return 응답 로케일
     */
    Locale getLocale();
}