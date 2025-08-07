package src.main.java.jakarta.servlet;

// Jakarta Servlet API - ServletRequest Interface
// 실제 프로젝트에서는 src/main/java/jakarta/servlet/ServletRequest.java 위치에 배치

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

/**
 * 클라이언트의 서블릿 요청 정보를 제공하는 인터페이스입니다.
 *
 * ServletRequest는 서블릿 컨테이너가 클라이언트 요청을 캡슐화하여 서블릿의
 * service() 메소드에 전달하기 위해 생성하는 객체입니다.
 *
 * 이 인터페이스는 프로토콜에 독립적인 요청 정보를 제공하며, HTTP 프로토콜에
 * 특화된 정보는 HttpServletRequest에서 제공됩니다.
 *
 * ServletRequest를 통해 다음과 같은 정보에 접근할 수 있습니다:
 * - 요청 파라미터 (폼 데이터, 쿼리 스트링)
 * - 요청 속성 (애플리케이션 내부 데이터 전달)
 * - 요청 바디 내용 (InputStream이나 Reader를 통해)
 * - 클라이언트 정보 (IP 주소, 로케일 등)
 * - 서버 정보 (호스트명, 포트 등)
 *
 * @author JavaServerArchitectures Project
 * @version 1.0
 * @see HttpServletRequest
 * @see ServletResponse
 */
public interface ServletRequest {

    // ===============================
    // 파라미터 관리 메소드
    // ===============================

    /**
     * 지정된 이름의 요청 파라미터 값을 반환합니다.
     *
     * 요청 파라미터는 다음 소스에서 올 수 있습니다:
     * - 쿼리 스트링 (GET 요청의 URL 뒤쪽)
     * - POST 폼 데이터 (application/x-www-form-urlencoded)
     * - Multipart 폼 데이터의 일부
     *
     * 같은 이름의 파라미터가 여러 개 있는 경우 첫 번째 값만 반환됩니다.
     *
     * 예시:
     * <pre>
     * {@code
     * // URL: /servlet?name=John&age=25
     * String name = request.getParameter("name");  // "John"
     * String age = request.getParameter("age");    // "25"
     * String city = request.getParameter("city");  // null (존재하지 않음)
     * }
     * </pre>
     *
     * @param name 파라미터 이름
     * @return 파라미터 값, 해당 파라미터가 존재하지 않으면 null
     */
    String getParameter(String name);

    /**
     * 모든 요청 파라미터 이름들을 Enumeration으로 반환합니다.
     *
     * @return 파라미터 이름들의 String Enumeration
     */
    Enumeration<String> getParameterNames();

    /**
     * 지정된 이름의 요청 파라미터의 모든 값들을 배열로 반환합니다.
     *
     * HTML 폼에서 같은 name을 가진 여러 개의 입력 요소가 있는 경우
     * (예: 체크박스, 다중 선택 리스트) 사용됩니다.
     *
     * 예시:
     * <pre>
     * {@code
     * // URL: /servlet?hobby=reading&hobby=swimming&hobby=coding
     * String[] hobbies = request.getParameterValues("hobby");
     * // hobbies = ["reading", "swimming", "coding"]
     * }
     * </pre>
     *
     * @param name 파라미터 이름
     * @return 파라미터 값들의 String 배열, 해당 파라미터가 없으면 null
     */
    String[] getParameterValues(String name);

    /**
     * 모든 요청 파라미터들을 Map으로 반환합니다.
     *
     * Map의 키는 파라미터 이름(String)이고, 값은 파라미터 값들의 배열(String[])입니다.
     *
     * @return 파라미터 이름을 키로, 값 배열을 값으로 하는 Map
     */
    Map<String, String[]> getParameterMap();

    // ===============================
    // 속성 관리 메소드
    // ===============================

    /**
     * 지정된 이름의 요청 속성을 반환합니다.
     *
     * 요청 속성은 애플리케이션 내부에서 데이터를 전달하는 데 사용됩니다.
     * ServletRequest.setAttribute()로 설정하고, RequestDispatcher.forward()나
     * include()를 통해 다른 서블릿으로 전달할 수 있습니다.
     *
     * 예시:
     * <pre>
     * {@code
     * // 첫 번째 서블릿에서
     * User user = getUserFromDatabase(userId);
     * request.setAttribute("user", user);
     * RequestDispatcher rd = request.getRequestDispatcher("/profile.jsp");
     * rd.forward(request, response);
     *
     * // profile.jsp에서
     * User user = (User) request.getAttribute("user");
     * }
     * </pre>
     *
     * @param name 속성 이름
     * @return 속성 값을 담은 Object, 해당 속성이 없으면 null
     */
    Object getAttribute(String name);

    /**
     * 모든 요청 속성의 이름들을 Enumeration으로 반환합니다.
     *
     * @return 속성 이름들의 String Enumeration
     */
    Enumeration<String> getAttributeNames();

    /**
     * 요청에 속성을 설정합니다.
     *
     * @param name 속성 이름
     * @param o 속성 값 (null이면 removeAttribute()와 동일)
     */
    void setAttribute(String name, Object o);

    /**
     * 지정된 이름의 요청 속성을 제거합니다.
     *
     * @param name 제거할 속성 이름
     */
    void removeAttribute(String name);

    // ===============================
    // 요청 바디 읽기 메소드
    // ===============================

    /**
     * 요청 바디를 읽기 위한 ServletInputStream을 반환합니다.
     *
     * 바이너리 데이터(파일 업로드, 이미지 등)를 읽을 때 사용합니다.
     * getReader()와 함께 사용할 수 없습니다.
     *
     * 예시:
     * <pre>
     * {@code
     * ServletInputStream inputStream = request.getInputStream();
     * byte[] buffer = new byte[1024];
     * int bytesRead;
     * while ((bytesRead = inputStream.read(buffer)) != -1) {
     *     // 바이너리 데이터 처리
     * }
     * }
     * </pre>
     *
     * @return 요청 바디의 ServletInputStream
     * @throws IOException I/O 오류가 발생한 경우
     * @throws IllegalStateException 이미 getReader()가 호출된 경우
     */
    ServletInputStream getInputStream() throws IOException;

    /**
     * 요청 바디를 읽기 위한 BufferedReader를 반환합니다.
     *
     * 텍스트 데이터(JSON, XML, 일반 텍스트)를 읽을 때 사용합니다.
     * 문자 인코딩을 자동으로 처리합니다.
     * getInputStream()과 함께 사용할 수 없습니다.
     *
     * 예시:
     * <pre>
     * {@code
     * BufferedReader reader = request.getReader();
     * StringBuilder sb = new StringBuilder();
     * String line;
     * while ((line = reader.readLine()) != null) {
     *     sb.append(line);
     * }
     * String jsonData = sb.toString();
     * }
     * </pre>
     *
     * @return 요청 바디의 BufferedReader
     * @throws IOException I/O 오류가 발생한 경우
     * @throws IllegalStateException 이미 getInputStream()이 호출된 경우
     */
    BufferedReader getReader() throws IOException;

    // ===============================
    // 문자 인코딩 관리 메소드
    // ===============================

    /**
     * 요청 바디의 문자 인코딩을 반환합니다.
     *
     * Content-Type 헤더에서 charset을 추출하거나, 설정된 기본 인코딩을 반환합니다.
     *
     * @return 문자 인코딩, 지정되지 않았으면 null
     */
    String getCharacterEncoding();

    /**
     * 요청 바디의 문자 인코딩을 설정합니다.
     *
     * getReader()나 getInputStream()을 호출하기 전에 설정해야 효과가 있습니다.
     *
     * @param env 문자 인코딩 (예: "UTF-8", "ISO-8859-1")
     * @throws java.io.UnsupportedEncodingException 지원되지 않는 인코딩인 경우
     */
    void setCharacterEncoding(String env) throws java.io.UnsupportedEncodingException;

    /**
     * 요청 바디의 길이(바이트 수)를 반환합니다.
     *
     * Content-Length 헤더 값을 반환합니다.
     *
     * @return 바디 길이, 알 수 없으면 -1
     * @deprecated Servlet 5.0부터 getContentLengthLong() 사용 권장
     */
    @Deprecated
    int getContentLength();

    /**
     * 요청 바디의 길이(바이트 수)를 long으로 반환합니다.
     *
     * 2GB를 넘는 요청을 처리할 때 사용합니다.
     *
     * @return 바디 길이, 알 수 없으면 -1L
     * @since Servlet 3.1
     */
    long getContentLengthLong();

    /**
     * 요청의 콘텐츠 타입을 반환합니다.
     *
     * Content-Type 헤더 값을 반환합니다.
     *
     * @return 콘텐츠 타입 (예: "application/json", "text/html"),
     *         지정되지 않았으면 null
     */
    String getContentType();

    // ===============================
    // 프로토콜 및 서버 정보 메소드
    // ===============================

    /**
     * 요청에 사용된 프로토콜 이름과 버전을 반환합니다.
     *
     * @return 프로토콜 (예: "HTTP/1.1", "HTTP/2.0")
     */
    String getProtocol();

    /**
     * 요청을 받은 서버의 호스트 이름을 반환합니다.
     *
     * @return 서버 호스트명 (예: "localhost", "example.com")
     */
    String getServerName();

    /**
     * 요청을 받은 서버의 포트 번호를 반환합니다.
     *
     * @return 서버 포트 번호 (예: 8080, 443)
     */
    int getServerPort();

    /**
     * 요청에 사용된 스키마를 반환합니다.
     *
     * @return 스키마 (예: "http", "https")
     */
    String getScheme();

    // ===============================
    // 클라이언트 정보 메소드
    // ===============================

    /**
     * 요청을 보낸 클라이언트의 IP 주소를 반환합니다.
     *
     * @return 클라이언트 IP 주소 (IPv4 또는 IPv6)
     */
    String getRemoteAddr();

    /**
     * 요청을 보낸 클라이언트의 호스트 이름을 반환합니다.
     *
     * DNS lookup이 가능한 경우 호스트명을, 그렇지 않으면 IP 주소를 반환합니다.
     * 성능상 이유로 대부분의 서버는 DNS lookup을 비활성화해 둡니다.
     *
     * @return 클라이언트 호스트명 또는 IP 주소
     */
    String getRemoteHost();

    /**
     * 요청을 보낸 클라이언트의 포트 번호를 반환합니다.
     *
     * @return 클라이언트 포트 번호
     * @since Servlet 2.4
     */
    int getRemotePort();

    /**
     * 요청을 받은 로컬 IP 주소를 반환합니다.
     *
     * 서버가 여러 네트워크 인터페이스를 가진 경우 유용합니다.
     *
     * @return 로컬 IP 주소
     * @since Servlet 2.4
     */
    String getLocalAddr();

    /**
     * 요청을 받은 로컬 호스트 이름을 반환합니다.
     *
     * @return 로컬 호스트명
     * @since Servlet 2.4
     */
    String getLocalName();

    /**
     * 요청을 받은 로컬 포트 번호를 반환합니다.
     *
     * @return 로컬 포트 번호
     * @since Servlet 2.4
     */
    int getLocalPort();

    // ===============================
    // 로케일 관련 메소드
    // ===============================

    /**
     * 클라이언트가 선호하는 로케일을 반환합니다.
     *
     * Accept-Language 헤더를 기반으로 결정됩니다.
     *
     * @return 클라이언트의 선호 로케일
     */
    Locale getLocale();

    /**
     * 클라이언트가 허용 가능한 모든 로케일들을 선호도 순으로 반환합니다.
     *
     * Accept-Language 헤더의 모든 언어를 파싱합니다.
     *
     * @return 로케일들의 Enumeration (선호도 내림차순)
     */
    Enumeration<Locale> getLocales();

    // ===============================
    // 보안 관련 메소드
    // ===============================

    /**
     * 요청이 보안 채널(HTTPS)을 통해 이루어졌는지 확인합니다.
     *
     * @return HTTPS 연결이면 true, HTTP 연결이면 false
     */
    boolean isSecure();

    // ===============================
    // ServletContext 접근 메소드
    // ===============================

    /**
     * 요청을 받은 서블릿이 실행되고 있는 ServletContext를 반환합니다.
     *
     * @return ServletContext 객체
     * @since Servlet 3.0
     */
    ServletContext getServletContext();

    // ===============================
    // 비동기 처리 관련 메소드 (Servlet 3.0+)
    // ===============================

    /**
     * 이 요청이 비동기 처리 모드인지 확인합니다.
     *
     * @return 비동기 모드면 true, 동기 모드면 false
     * @since Servlet 3.0
     */
    boolean isAsyncStarted();

    /**
     * 이 요청이 비동기 처리를 지원하는지 확인합니다.
     *
     * 서블릿이나 필터가 비동기를 지원하지 않으면 false를 반환합니다.
     *
     * @return 비동기 지원 가능하면 true
     * @since Servlet 3.0
     */
    boolean isAsyncSupported();

    /**
     * 이 요청에 대해 비동기 처리를 시작합니다.
     *
     * @return AsyncContext 객체
     * @throws IllegalStateException 비동기가 지원되지 않거나 이미 시작된 경우
     * @since Servlet 3.0
     */
    AsyncContext startAsync() throws IllegalStateException;

    /**
     * 지정된 요청/응답 객체로 비동기 처리를 시작합니다.
     *
     * @param servletRequest 비동기 처리에 사용할 ServletRequest
     * @param servletResponse 비동기 처리에 사용할 ServletResponse
     * @return AsyncContext 객체
     * @throws IllegalStateException 비동기가 지원되지 않거나 이미 시작된 경우
     * @since Servlet 3.0
     */
    AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse)
            throws IllegalStateException;

    /**
     * 현재 요청의 AsyncContext를 반환합니다.
     *
     * @return AsyncContext 객체
     * @throws IllegalStateException 비동기가 시작되지 않은 경우
     * @since Servlet 3.0
     */
    AsyncContext getAsyncContext();

    /**
     * 현재 요청의 디스패처 타입을 반환합니다.
     *
     * @return DispatcherType (REQUEST, FORWARD, INCLUDE, ASYNC, ERROR)
     * @since Servlet 3.0
     */
    DispatcherType getDispatcherType();
}