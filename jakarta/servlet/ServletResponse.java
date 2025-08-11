package jakarta.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * 클라이언트에게 보낼 응답을 구성하는 인터페이스입니다.
 *
 * ServletResponse는 HTTP, FTP, SMTP 등 다양한 프로토콜에서 공통적으로 사용할 수 있는
 * 범용적인 응답 인터페이스입니다. HTTP 특화 기능은 HttpServletResponse에서 제공됩니다.
 *
 * 주요 기능:
 * - 응답 본문 작성 (텍스트 또는 바이너리)
 * - 문자 인코딩 및 콘텐츠 타입 설정
 * - 출력 버퍼 관리
 * - 로케일 설정
 */
public interface ServletResponse {

    /**
     * 응답의 문자 인코딩을 반환합니다.
     *
     * 응답 텍스트를 인코딩할 때 사용되는 문자셋입니다.
     * 기본값은 보통 "ISO-8859-1"이지만 setCharacterEncoding()으로 변경할 수 있습니다.
     *
     * @return 현재 설정된 문자 인코딩
     */
    String getCharacterEncoding();

    /**
     * 응답의 문자 인코딩을 설정합니다.
     *
     * 이 메서드는 getWriter() 호출 전에 호출해야 합니다.
     * 한번 출력 스트림을 얻은 후에는 인코딩을 변경할 수 없습니다.
     *
     * 일반적으로 사용되는 인코딩:
     * - "UTF-8" : 유니코드, 다국어 지원
     * - "ISO-8859-1" : 서유럽 언어
     * - "EUC-KR" : 한국어
     *
     * @param charset 설정할 문자 인코딩
     */
    void setCharacterEncoding(String charset);

    /**
     * 응답의 콘텐츠 타입을 반환합니다.
     *
     * Content-Type 헤더에 설정될 값입니다.
     *
     * @return 현재 설정된 콘텐츠 타입
     */
    String getContentType();

    /**
     * 응답의 콘텐츠 타입을 설정합니다.
     *
     * 클라이언트가 응답 데이터를 올바르게 해석할 수 있도록 MIME 타입을 지정합니다.
     * 필요한 경우 문자 인코딩도 함께 지정할 수 있습니다.
     *
     * 일반적인 콘텐츠 타입:
     * - "text/html" : HTML 페이지
     * - "text/html; charset=UTF-8" : UTF-8 인코딩의 HTML
     * - "application/json" : JSON 데이터
     * - "text/plain" : 일반 텍스트
     * - "application/octet-stream" : 바이너리 파일
     * - "image/jpeg" : JPEG 이미지
     *
     * @param type 설정할 콘텐츠 타입
     */
    void setContentType(String type);

    /**
     * 응답 본문의 길이를 설정합니다.
     *
     * Content-Length 헤더에 설정될 값입니다.
     * 브라우저가 다운로드 진행률을 표시하거나 연결을 최적화하는 데 사용됩니다.
     *
     * 정확한 길이를 미리 알 수 있는 경우에만 설정하세요.
     * 길이가 틀리면 응답이 잘리거나 연결 문제가 발생할 수 있습니다.
     *
     * @param len 응답 본문의 바이트 길이
     */
    void setContentLength(int len);

    /**
     * 바이너리 데이터를 출력하기 위한 ServletOutputStream을 반환합니다.
     *
     * 이미지, 파일, 또는 기타 바이너리 데이터를 클라이언트에게 전송할 때 사용됩니다.
     * getWriter()와 getOutputStream()은 동시에 사용할 수 없습니다.
     *
     * 사용 예시:
     * ServletOutputStream out = response.getOutputStream();
     * byte[] imageData = loadImageFromDatabase();
     * out.write(imageData);
     * out.flush();
     *
     * @return 바이너리 출력을 위한 ServletOutputStream
     * @throws IOException 입출력 오류 시
     */
    ServletOutputStream getOutputStream() throws IOException;

    /**
     * 텍스트 데이터를 출력하기 위한 PrintWriter를 반환합니다.
     *
     * HTML, JSON, XML 등의 텍스트 기반 응답을 작성할 때 사용됩니다.
     * 설정된 문자 인코딩이 자동으로 적용됩니다.
     *
     * 사용 예시:
     * PrintWriter out = response.getWriter();
     * out.println("<html><body>");
     * out.println("<h1>Hello World</h1>");
     * out.println("</body></html>");
     * out.flush();
     *
     * @return 텍스트 출력을 위한 PrintWriter
     * @throws IOException 입출력 오류 시
     */
    PrintWriter getWriter() throws IOException;

    /**
     * 출력 버퍼의 크기를 설정합니다.
     *
     * 서블릿 컨테이너는 성능 향상을 위해 응답 데이터를 버퍼에 모았다가 한번에 전송합니다.
     * 이 메서드는 해당 버퍼의 크기를 지정합니다.
     *
     * 버퍼 크기 고려사항:
     * - 작은 버퍼: 메모리 절약, 빠른 응답 시작
     * - 큰 버퍼: 네트워크 효율성 향상
     *
     * 이 메서드는 어떤 출력도 하기 전에 호출해야 합니다.
     *
     * @param size 버퍼 크기 (바이트)
     */
    void setBufferSize(int size);

    /**
     * 현재 출력 버퍼의 크기를 반환합니다.
     *
     * @return 버퍼 크기 (바이트)
     */
    int getBufferSize();

    /**
     * 출력 버퍼를 비우고 클라이언트에게 즉시 전송합니다.
     *
     * 버퍼에 쌓인 모든 데이터가 즉시 네트워크로 전송됩니다.
     * 실시간 스트리밍이나 진행률 표시 등에 유용합니다.
     *
     * flush() 후에는 응답 헤더를 변경할 수 없습니다.
     *
     * @throws IOException 입출력 오류 시
     */
    void flushBuffer() throws IOException;

    /**
     * 현재 응답이 커밋되었는지 확인합니다.
     *
     * 응답이 커밋되면:
     * - HTTP 헤더가 클라이언트에게 전송됨
     * - 상태 코드나 헤더를 더 이상 변경할 수 없음
     * - 응답 리다이렉션이 불가능함
     *
     * 응답이 커밋되는 경우:
     * - flushBuffer() 호출 시
     * - 버퍼가 가득 차서 자동으로 flush될 때
     * - 정상적인 응답 완료 시
     *
     * @return 응답이 커밋되었으면 true, 그렇지 않으면 false
     */
    boolean isCommitted();

    /**
     * 출력 버퍼를 지우고 응답을 초기화합니다.
     *
     * 버퍼의 모든 데이터가 삭제되고 응답 상태가 초기 상태로 돌아갑니다.
     * 상태 코드, 헤더, 쿠키 등도 모두 초기화됩니다.
     *
     * 이 메서드는 응답이 커밋되기 전에만 호출할 수 있습니다.
     * 커밋된 후에 호출하면 IllegalStateException이 발생합니다.
     *
     * 에러 처리나 응답 내용을 완전히 다시 작성할 때 사용됩니다.
     *
     * @throws IllegalStateException 응답이 이미 커밋된 경우
     */
    void reset();

    /**
     * 출력 버퍼만 지우고 헤더와 상태 코드는 유지합니다.
     *
     * reset()과 달리 헤더 정보는 보존되고 버퍼 내용만 삭제됩니다.
     * 응답 본문만 다시 작성하고 싶을 때 사용됩니다.
     *
     * @throws IllegalStateException 응답이 이미 커밋된 경우
     */
    void resetBuffer();

    /**
     * 응답에 사용할 로케일을 설정합니다.
     *
     * 로케일은 언어와 지역 정보를 나타내며, 다음에 영향을 줍니다:
     * - Content-Language 헤더 설정
     * - 기본 문자 인코딩 결정
     * - 날짜, 숫자 형식 등
     *
     * 예시:
     * response.setLocale(Locale.KOREAN);  // 한국어
     * response.setLocale(Locale.US);      // 미국 영어
     * response.setLocale(Locale.JAPAN);   // 일본어
     *
     * @param loc 설정할 로케일
     */
    void setLocale(Locale loc);

    /**
     * 현재 설정된 로케일을 반환합니다.
     *
     * @return 현재 로케일
     */
    Locale getLocale();
}
