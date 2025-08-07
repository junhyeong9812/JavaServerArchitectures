package src.main.java.jakarta.servlet;

// Jakarta Servlet API - ServletContext Interface
// 실제 프로젝트에서는 src/main/java/jakarta/servlet/ServletContext.java 위치에 배치

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;

/**
 * 웹 애플리케이션과 서블릿 컨테이너 간의 통신을 위한 인터페이스입니다.
 *
 * ServletContext는 웹 애플리케이션의 "전역 공간"을 나타내며, 하나의 웹 애플리케이션당
 * 하나의 ServletContext가 존재합니다. 이 인터페이스를 통해 서블릿은 다음과 같은
 * 작업을 수행할 수 있습니다:
 *
 * - 웹 애플리케이션의 초기화 파라미터 접근
 * - 웹 애플리케이션의 리소스 파일 접근
 * - 애플리케이션 레벨에서 데이터 공유 (attributes)
 * - 다른 서블릿으로 요청 전달 (RequestDispatcher)
 * - 로깅 수행
 * - 서버 및 서블릿 API 정보 조회
 *
 * ServletContext는 웹 애플리케이션이 시작될 때 생성되고,
 * 웹 애플리케이션이 종료될 때 소멸됩니다.
 *
 * @author JavaServerArchitectures Project
 * @version 1.0
 * @see ServletConfig#getServletContext()
 */
public interface ServletContext {

    // ===============================
    // 기본 정보 및 설정 관련 메소드
    // ===============================

    /**
     * 이 ServletContext가 실행되고 있는 웹 애플리케이션의 context path를 반환합니다.
     *
     * Context path는 웹 애플리케이션을 식별하는 경로로, URL의 서버명과 포트번호 다음에 오는 부분입니다.
     *
     * 예시:
     * - http://localhost:8080/myapp/servlet → context path는 "/myapp"
     * - http://localhost:8080/servlet → context path는 "" (빈 문자열, ROOT 애플리케이션)
     *
     * @return 웹 애플리케이션의 context path, ROOT 애플리케이션인 경우 빈 문자열
     * @since Servlet 2.5
     */
    String getContextPath();

    /**
     * 서블릿 컨테이너의 이름과 버전을 반환합니다.
     *
     * 반환되는 문자열 형식은 "servername/versionnumber"입니다.
     * 예: "Apache Tomcat/9.0.65", "Jetty/11.0.12"
     *
     * @return 서버 정보를 나타내는 String
     */
    String getServerInfo();

    /**
     * 이 서블릿 컨테이너가 지원하는 Servlet API의 메이저 버전을 반환합니다.
     *
     * @return Servlet API 메이저 버전 (예: Servlet 5.0의 경우 5)
     */
    int getMajorVersion();

    /**
     * 이 서블릿 컨테이너가 지원하는 Servlet API의 마이너 버전을 반환합니다.
     *
     * @return Servlet API 마이너 버전 (예: Servlet 5.0의 경우 0)
     */
    int getMinorVersion();

    /**
     * 서블릿 컨테이너가 지원하는 Servlet API의 유효 메이저 버전을 반환합니다.
     * 이 값은 web.xml의 version 속성이나 @WebServlet의 설정에 따라 결정됩니다.
     *
     * @return 유효한 Servlet API 메이저 버전
     * @since Servlet 3.0
     */
    int getEffectiveMajorVersion();

    /**
     * 서블릿 컨테이너가 지원하는 Servlet API의 유효 마이너 버전을 반환합니다.
     *
     * @return 유효한 Servlet API 마이너 버전
     * @since Servlet 3.0
     */
    int getEffectiveMinorVersion();

    // ===============================
    // 초기화 파라미터 관련 메소드
    // ===============================

    /**
     * 지정된 이름의 웹 애플리케이션 초기화 파라미터를 반환합니다.
     *
     * 웹 애플리케이션 초기화 파라미터는 web.xml의 &lt;context-param&gt; 요소에서 정의됩니다.
     * 이는 서블릿별 초기화 파라미터와는 다르며, 애플리케이션 전체에서 공유됩니다.
     *
     * 예시:
     * <pre>
     * {@code
     * // web.xml에서:
     * <context-param>
     *   <param-name>database.driver</param-name>
     *   <param-value>com.mysql.cj.jdbc.Driver</param-value>
     * </context-param>
     *
     * // 서블릿에서 사용:
     * String driver = getServletContext().getInitParameter("database.driver");
     * }
     * </pre>
     *
     * @param name 초기화 파라미터의 이름
     * @return 파라미터 값, 해당 이름의 파라미터가 없으면 null
     * @see ServletConfig#getInitParameter(String)
     */
    String getInitParameter(String name);

    /**
     * 모든 웹 애플리케이션 초기화 파라미터의 이름들을 Enumeration으로 반환합니다.
     *
     * @return 초기화 파라미터 이름들의 String Enumeration
     */
    Enumeration<String> getInitParameterNames();

    /**
     * 웹 애플리케이션 초기화 파라미터를 설정합니다.
     *
     * 이 메소드는 ServletContextListener.contextInitialized() 메소드 내에서만 호출할 수 있습니다.
     * 웹 애플리케이션이 초기화된 후에는 호출할 수 없습니다.
     *
     * @param name 파라미터 이름
     * @param value 파라미터 값
     * @return 설정에 성공하면 true, 이미 같은 이름의 파라미터가 존재하거나
     *         초기화 단계가 아닌 경우 false
     * @throws IllegalStateException 웹 애플리케이션 초기화가 완료된 후 호출된 경우
     * @since Servlet 3.0
     */
    boolean setInitParameter(String name, String value);

    // ===============================
    // 속성(Attribute) 관리 메소드
    // ===============================

    /**
     * 지정된 이름의 ServletContext 속성을 반환합니다.
     *
     * ServletContext 속성은 웹 애플리케이션 전체에서 공유되는 데이터를 저장하는 데 사용됩니다.
     * 이는 애플리케이션의 전역 변수 역할을 합니다.
     *
     * 예시:
     * <pre>
     * {@code
     * // 데이터베이스 커넥션 풀을 전역으로 공유
     * DataSource ds = (DataSource) getServletContext().getAttribute("dataSource");
     *
     * // 애플리케이션 설정 객체 공유
     * AppConfig config = (AppConfig) getServletContext().getAttribute("appConfig");
     * }
     * </pre>
     *
     * @param name 속성의 이름
     * @return 속성 값을 담은 Object, 해당 이름의 속성이 없으면 null
     */
    Object getAttribute(String name);

    /**
     * 모든 ServletContext 속성의 이름들을 Enumeration으로 반환합니다.
     *
     * @return 속성 이름들의 String Enumeration
     */
    Enumeration<String> getAttributeNames();

    /**
     * ServletContext에 속성을 설정합니다.
     *
     * 만약 같은 이름의 속성이 이미 존재한다면 새로운 값으로 대체됩니다.
     * 속성의 추가/변경/삭제 시 ServletContextAttributeListener들에게 알림이 전송됩니다.
     *
     * @param name 속성의 이름
     * @param object 저장할 속성 값
     */
    void setAttribute(String name, Object object);

    /**
     * 지정된 이름의 ServletContext 속성을 제거합니다.
     *
     * @param name 제거할 속성의 이름
     */
    void removeAttribute(String name);

    // ===============================
    // 리소스 접근 관련 메소드
    // ===============================

    /**
     * 지정된 경로에 해당하는 리소스의 URL을 반환합니다.
     *
     * 경로는 "/"로 시작해야 하며, 웹 애플리케이션 루트를 기준으로 합니다.
     *
     * 예시:
     * <pre>
     * {@code
     * URL configUrl = getServletContext().getResource("/WEB-INF/config.xml");
     * URL imageUrl = getServletContext().getResource("/images/logo.png");
     * }
     * </pre>
     *
     * @param path 리소스 경로 ("/"로 시작)
     * @return 리소스의 URL, 리소스가 존재하지 않으면 null
     * @throws java.net.MalformedURLException 경로 형식이 잘못된 경우
     */
    URL getResource(String path) throws java.net.MalformedURLException;

    /**
     * 지정된 경로에 해당하는 리소스의 InputStream을 반환합니다.
     *
     * @param path 리소스 경로 ("/"로 시작)
     * @return 리소스의 InputStream, 리소스가 존재하지 않으면 null
     */
    InputStream getResourceAsStream(String path);

    /**
     * 지정된 가상 경로에 해당하는 실제 파일 시스템 경로를 반환합니다.
     *
     * 웹 애플리케이션이 WAR 파일로 배포된 경우 null을 반환할 수 있습니다.
     *
     * @param path 가상 경로 ("/"로 시작)
     * @return 실제 파일 시스템 경로, 또는 null
     */
    String getRealPath(String path);

    /**
     * 지정된 디렉토리 경로의 리소스들을 Set으로 반환합니다.
     *
     * @param path 디렉토리 경로 ("/"로 시작하고 "/"로 끝남)
     * @return 리소스 경로들의 Set, 해당 경로가 존재하지 않으면 null
     * @since Servlet 2.3
     */
    Set<String> getResourcePaths(String path);

    // ===============================
    // 요청 디스패칭 관련 메소드
    // ===============================

    /**
     * 지정된 경로에 대한 RequestDispatcher를 반환합니다.
     *
     * RequestDispatcher를 사용하여 다른 서블릿이나 JSP로 요청을 전달(forward)하거나
     * 포함(include)시킬 수 있습니다.
     *
     * @param path 대상 경로 ("/"로 시작)
     * @return RequestDispatcher 객체, 해당 경로가 유효하지 않으면 null
     */
    RequestDispatcher getRequestDispatcher(String path);

    /**
     * 지정된 이름의 서블릿에 대한 RequestDispatcher를 반환합니다.
     *
     * @param name 서블릿 이름 (web.xml의 servlet-name)
     * @return RequestDispatcher 객체, 해당 서블릿이 존재하지 않으면 null
     */
    RequestDispatcher getNamedDispatcher(String name);

    // ===============================
    // 로깅 관련 메소드
    // ===============================

    /**
     * 지정된 메시지를 서블릿 로그에 기록합니다.
     *
     * @param msg 로그 메시지
     */
    void log(String msg);

    /**
     * 지정된 메시지와 예외를 서블릿 로그에 기록합니다.
     *
     * @param message 로그 메시지
     * @param throwable 예외 객체
     */
    void log(String message, Throwable throwable);

    // ===============================
    // Servlet 3.0+ 고급 기능
    // ===============================

    /**
     * 지정된 클래스에 대한 MIME 타입을 반환합니다.
     *
     * @param file 파일명 또는 확장자
     * @return MIME 타입, 알 수 없으면 null
     */
    String getMimeType(String file);

    /**
     * 웹 애플리케이션에 동적으로 서블릿을 추가합니다.
     *
     * 이 메소드는 ServletContextListener.contextInitialized() 내에서만 호출 가능합니다.
     *
     * @param servletName 서블릿 이름
     * @param className 서블릿 클래스 이름
     * @return ServletRegistration.Dynamic 객체
     * @throws IllegalStateException 초기화 단계가 아닌 경우
     * @since Servlet 3.0
     */
    ServletRegistration.Dynamic addServlet(String servletName, String className);

    /**
     * 웹 애플리케이션에 동적으로 서블릿을 추가합니다.
     *
     * @param servletName 서블릿 이름
     * @param servlet 서블릿 인스턴스
     * @return ServletRegistration.Dynamic 객체
     * @throws IllegalStateException 초기화 단계가 아닌 경우
     * @since Servlet 3.0
     */
    ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet);

    /**
     * 웹 애플리케이션에 동적으로 서블릿을 추가합니다.
     *
     * @param servletName 서블릿 이름
     * @param servletClass 서블릿 클래스
     * @return ServletRegistration.Dynamic 객체
     * @throws IllegalStateException 초기화 단계가 아닌 경우
     * @since Servlet 3.0
     */
    ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass);

    /**
     * 지정된 이름의 서블릿 등록 정보를 반환합니다.
     *
     * @param servletName 서블릿 이름
     * @return ServletRegistration 객체, 해당 서블릿이 없으면 null
     * @since Servlet 3.0
     */
    ServletRegistration getServletRegistration(String servletName);

    /**
     * 모든 서블릿 등록 정보를 Map으로 반환합니다.
     *
     * @return 서블릿 이름을 키로 하는 ServletRegistration Map
     * @since Servlet 3.0
     */
    Map<String, ? extends ServletRegistration> getServletRegistrations();

    /**
     * 웹 애플리케이션에 동적으로 필터를 추가합니다.
     *
     * @param filterName 필터 이름
     * @param className 필터 클래스 이름
     * @return FilterRegistration.Dynamic 객체
     * @throws IllegalStateException 초기화 단계가 아닌 경우
     * @since Servlet 3.0
     */
    FilterRegistration.Dynamic addFilter(String filterName, String className);

    /**
     * 웹 애플리케이션에 동적으로 필터를 추가합니다.
     *
     * @param filterName 필터 이름
     * @param filter 필터 인스턴스
     * @return FilterRegistration.Dynamic 객체
     * @throws IllegalStateException 초기화 단계가 아닌 경우
     * @since Servlet 3.0
     */
    FilterRegistration.Dynamic addFilter(String filterName, Filter filter);

    /**
     * 웹 애플리케이션에 동적으로 필터를 추가합니다.
     *
     * @param filterName 필터 이름
     * @param filterClass 필터 클래스
     * @return FilterRegistration.Dynamic 객체
     * @throws IllegalStateException 초기화 단계가 아닌 경우
     * @since Servlet 3.0
     */
    FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass);

    /**
     * 웹 애플리케이션에 이벤트 리스너를 추가합니다.
     *
     * @param className 리스너 클래스 이름
     * @throws IllegalStateException 초기화 단계가 아닌 경우
     * @since Servlet 3.0
     */
    void addListener(String className);

    /**
     * 웹 애플리케이션에 이벤트 리스너를 추가합니다.
     *
     * @param t 리스너 인스턴스
     * @throws IllegalStateException 초기화 단계가 아닌 경우
     * @since Servlet 3.0
     */
    <T extends EventListener> void addListener(T t);

    /**
     * 웹 애플리케이션에 이벤트 리스너를 추가합니다.
     *
     * @param listenerClass 리스너 클래스
     * @throws IllegalStateException 초기화 단계가 아닌 경우
     * @since Servlet 3.0
     */
    void addListener(Class<? extends EventListener> listenerClass);

    /**
     * 현재 웹 애플리케이션의 세션 쿠키 설정을 반환합니다.
     *
     * @return SessionCookieConfig 객체
     * @since Servlet 3.0
     */
    SessionCookieConfig getSessionCookieConfig();

    /**
     * 세션 트래킹 모드를 설정합니다.
     *
     * @param sessionTrackingModes 세션 트래킹 모드 Set
     * @throws IllegalStateException 초기화 단계가 아닌 경우
     * @throws IllegalArgumentException 잘못된 조합의 트래킹 모드인 경우
     * @since Servlet 3.0
     */
    void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes);

    /**
     * 현재 설정된 세션 트래킹 모드들을 반환합니다.
     *
     * @return 세션 트래킹 모드들의 Set
     * @since Servlet 3.0
     */
    Set<SessionTrackingMode> getEffectiveSessionTrackingModes();

    /**
     * 가상 서버명을 반환합니다.
     *
     * @return 가상 서버명, 설정되지 않았으면 null
     * @since Servlet 3.1
     */
    String getVirtualServerName();

    /**
     * 웹 애플리케이션의 기본 세션 타임아웃을 반환합니다.
     *
     * @return 세션 타임아웃 (분 단위)
     * @since Servlet 4.0
     */
    int getSessionTimeout();

    /**
     * 웹 애플리케이션의 기본 세션 타임아웃을 설정합니다.
     *
     * @param sessionTimeout 세션 타임아웃 (분 단위)
     * @throws IllegalStateException 초기화 단계가 아닌 경우
     * @since Servlet 4.0
     */
    void setSessionTimeout(int sessionTimeout);

    /**
     * 웹 애플리케이션의 요청 문자 인코딩을 반환합니다.
     *
     * @return 요청 문자 인코딩, 설정되지 않았으면 null
     * @since Servlet 4.0
     */
    String getRequestCharacterEncoding();

    /**
     * 웹 애플리케이션의 요청 문자 인코딩을 설정합니다.
     *
     * @param encoding 요청 문자 인코딩
     * @throws IllegalStateException 초기화 단계가 아닌 경우
     * @since Servlet 4.0
     */
    void setRequestCharacterEncoding(String encoding);

    /**
     * 웹 애플리케이션의 응답 문자 인코딩을 반환합니다.
     *
     * @return 응답 문자 인코딩, 설정되지 않았으면 null
     * @since Servlet 4.0
     */
    String getResponseCharacterEncoding();

    /**
     * 웹 애플리케이션의 응답 문자 인코딩을 설정합니다.
     *
     * @param encoding 응답 문자 인코딩
     * @throws IllegalStateException 초기화 단계가 아닌 경우
     * @since Servlet 4.0
     */
    void setResponseCharacterEncoding(String encoding);
}