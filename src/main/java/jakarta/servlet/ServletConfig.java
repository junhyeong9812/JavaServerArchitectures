package src.main.java.jakarta.servlet;

import java.util.Enumeration;

/**
 * 서블릿의 설정 정보를 제공하는 인터페이스입니다.
 *
 * ServletConfig는 서블릿 컨테이너가 서블릿을 초기화할 때 사용하는 설정 객체입니다.
 * 이 객체를 통해 서블릿은 다음 정보에 접근할 수 있습니다:
 * - 서블릿의 논리적 이름
 * - web.xml에서 정의된 초기화 매개변수
 * - 웹 애플리케이션의 ServletContext
 *
 * 예시 web.xml 설정:
 * <servlet>
 *   <servlet-name>MyServlet</servlet-name>
 *   <servlet-class>com.example.MyServlet</servlet-class>
 *   <init-param>
 *     <param-name>dbUrl</param-name>
 *     <param-value>jdbc:mysql://localhost:3306/mydb</param-value>
 *   </init-param>
 *   <init-param>
 *     <param-name>maxConnections</param-name>
 *     <param-value>100</param-value>
 *   </init-param>
 * </servlet>
 */
public interface ServletConfig {

    /**
     * 서블릿의 이름을 반환합니다.
     *
     * 이 이름은 web.xml의 <servlet-name> 요소에서 정의된 논리적 이름입니다.
     * 서블릿 매핑이나 로깅에서 서블릿을 식별하는 데 사용됩니다.
     *
     * 예: web.xml에서 <servlet-name>UserService</servlet-name>로 정의했다면
     * 이 메서드는 "UserService"를 반환합니다.
     *
     * @return 서블릿의 이름
     */
    String getServletName();

    /**
     * 웹 애플리케이션의 ServletContext를 반환합니다.
     *
     * ServletContext는 웹 애플리케이션 전체에 대한 정보와 리소스를 제공합니다.
     * 여러 서블릿 간에 데이터를 공유하거나, 웹 애플리케이션 레벨의 설정에 접근할 때 사용됩니다.
     *
     * ServletContext를 통해 할 수 있는 작업:
     * - 웹 애플리케이션의 초기화 매개변수 읽기
     * - 서블릿 간 데이터 공유 (애플리케이션 스코프)
     * - 웹 애플리케이션의 리소스 파일 접근
     * - 로깅
     *
     * @return 웹 애플리케이션의 ServletContext 객체
     */
    ServletContext getServletContext();

    /**
     * 지정된 이름의 초기화 매개변수 값을 반환합니다.
     *
     * 초기화 매개변수는 web.xml에서 <init-param> 요소로 정의됩니다.
     * 이를 통해 서블릿별 설정 값을 하드코딩 없이 외부에서 주입할 수 있습니다.
     *
     * 예시 사용법:
     * String dbUrl = config.getInitParameter("dbUrl");
     * String maxConn = config.getInitParameter("maxConnections");
     * int maxConnections = Integer.parseInt(maxConn);
     *
     * @param name 초기화 매개변수의 이름
     * @return 매개변수의 값, 존재하지 않으면 null
     */
    String getInitParameter(String name);

    /**
     * 모든 초기화 매개변수의 이름을 열거형으로 반환합니다.
     *
     * 이 메서드를 사용하여 서블릿에 정의된 모든 초기화 매개변수를 순회할 수 있습니다.
     * 디버깅이나 설정 정보 출력 시 유용합니다.
     *
     * 예시 사용법:
     * Enumeration<String> paramNames = config.getInitParameterNames();
     * while (paramNames.hasMoreElements()) {
     *     String paramName = paramNames.nextElement();
     *     String paramValue = config.getInitParameter(paramName);
     *     System.out.println(paramName + " = " + paramValue);
     * }
     *
     * @return 초기화 매개변수 이름들의 Enumeration, 매개변수가 없으면 빈 Enumeration
     */
    Enumeration<String> getInitParameterNames();
}