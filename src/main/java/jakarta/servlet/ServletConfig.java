package src.main.java.jakarta.servlet;

// Jakarta Servlet API - ServletConfig Interface
// 실제 프로젝트에서는 src/main/java/jakarta/servlet/ServletConfig.java 위치에 배치

import java.util.Enumeration;

/**
 * 서블릿의 설정 정보를 제공하는 인터페이스입니다.
 *
 * 서블릿 컨테이너는 서블릿을 초기화할 때 ServletConfig 객체를 생성하여
 * 서블릿의 init() 메서드에 전달합니다. 이 객체를 통해 서블릿은 자신의
 * 설정 정보와 ServletContext에 접근할 수 있습니다.
 *
 * ServletConfig는 다음과 같은 정보를 제공합니다:
 * - 서블릿 이름
 * - 초기화 파라미터들
 * - ServletContext 참조
 *
 * 일반적으로 web.xml이나 어노테이션을 통해 설정된 정보들이 이 인터페이스를 통해
 * 서블릿에 전달됩니다.
 *
 * 예시:
 * <pre>
 * {@code
 * // web.xml 설정
 * <servlet>
 *   <servlet-name>MyServlet</servlet-name>
 *   <servlet-class>com.example.MyServlet</servlet-class>
 *   <init-param>
 *     <param-name>databaseUrl</param-name>
 *     <param-value>jdbc:mysql://localhost:3306/mydb</param-value>
 *   </init-param>
 *   <init-param>
 *     <param-name>cacheSize</param-name>
 *     <param-value>1000</param-value>
 *   </init-param>
 * </servlet>
 *
 * // 서블릿에서 사용
 * public void init(ServletConfig config) throws ServletException {
 *     String dbUrl = config.getInitParameter("databaseUrl");
 *     String cacheSize = config.getInitParameter("cacheSize");
 *     // 설정 값을 이용한 초기화 작업...
 * }
 * }
 * </pre>
 *
 * @author JavaServerArchitectures Project
 * @version 1.0
 * @see Servlet#init(ServletConfig)
 * @see ServletContext
 */
public interface ServletConfig {

    /**
     * 서블릿의 이름을 반환합니다.
     *
     * 서블릿 이름은 web.xml의 &lt;servlet-name&gt; 요소나
     * @WebServlet 어노테이션의 name 속성에서 정의됩니다.
     * 만약 명시적으로 이름이 지정되지 않았다면, 서블릿 클래스의
     * 완전한 클래스 이름(패키지명 포함)이 사용됩니다.
     *
     * @return 서블릿의 이름을 나타내는 String 객체, 또는 null
     */
    String getServletName();

    /**
     * 이 서블릿이 실행되고 있는 ServletContext에 대한 참조를 반환합니다.
     *
     * ServletContext를 통해 서블릿은 다음과 같은 작업을 수행할 수 있습니다:
     * - 웹 애플리케이션 전역 파라미터 조회
     * - 웹 애플리케이션의 리소스 접근
     * - 다른 서블릿과 데이터 공유
     * - 로깅 수행
     * - RequestDispatcher 획득
     *
     * @return 이 서블릿의 ServletContext 객체
     * @see ServletContext
     */
    ServletContext getServletContext();

    /**
     * 지정된 이름의 초기화 파라미터 값을 반환합니다.
     *
     * 초기화 파라미터는 web.xml의 &lt;init-param&gt; 요소나
     * @WebServlet 어노테이션의 initParams 속성에서 정의됩니다.
     *
     * 예시:
     * <pre>
     * {@code
     * // web.xml에서:
     * <init-param>
     *   <param-name>database.url</param-name>
     *   <param-value>jdbc:mysql://localhost/mydb</param-value>
     * </init-param>
     *
     * // 서블릿에서 사용:
     * String dbUrl = config.getInitParameter("database.url");
     * }
     * </pre>
     *
     * @param name 초기화 파라미터의 이름
     * @return 지정된 이름의 초기화 파라미터 값을 담은 String 객체,
     *         해당 이름의 파라미터가 존재하지 않으면 null
     */
    String getInitParameter(String name);

    /**
     * 이 서블릿의 모든 초기화 파라미터 이름들을 Enumeration으로 반환합니다.
     *
     * 반환되는 Enumeration을 통해 모든 초기화 파라미터의 이름을 순회할 수 있습니다.
     * 각 이름에 대해 getInitParameter()를 호출하여 해당 값을 얻을 수 있습니다.
     *
     * 예시:
     * <pre>
     * {@code
     * Enumeration<String> paramNames = config.getInitParameterNames();
     * while (paramNames.hasMoreElements()) {
     *     String paramName = paramNames.nextElement();
     *     String paramValue = config.getInitParameter(paramName);
     *     System.out.println(paramName + " = " + paramValue);
     * }
     * }
     * </pre>
     *
     * 서블릿에 초기화 파라미터가 없는 경우, 빈 Enumeration이 반환됩니다.
     *
     * @return 초기화 파라미터 이름들의 String Enumeration 객체
     * @see #getInitParameter(String)
     */
    Enumeration<String> getInitParameterNames();
}