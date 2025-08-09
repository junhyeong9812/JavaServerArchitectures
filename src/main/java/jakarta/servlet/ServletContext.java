package src.main.java.jakarta.servlet;

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;

/**
 * 웹 애플리케이션의 실행 환경에 대한 정보를 제공하는 인터페이스입니다.
 *
 * ServletContext는 웹 애플리케이션당 하나씩 생성되며, 애플리케이션의 전역 정보와
 * 리소스에 대한 접근을 제공합니다. 모든 서블릿과 필터가 동일한 ServletContext를 공유합니다.
 *
 * 주요 기능:
 * - 웹 애플리케이션 초기화 매개변수 관리
 * - 애플리케이션 스코프 데이터 저장/조회 (모든 서블릿이 공유)
 * - 웹 애플리케이션 리소스 파일 접근
 * - 다른 웹 애플리케이션과의 통신
 * - 로깅 기능
 */
public interface ServletContext {

    /**
     * 웹 애플리케이션의 컨텍스트 경로를 반환합니다.
     *
     * 컨텍스트 경로는 웹 애플리케이션을 식별하는 URL의 일부입니다.
     * 예: http://localhost:8080/myapp/servlet/hello 에서 "/myapp"가 컨텍스트 경로
     *
     * 루트 컨텍스트의 경우 빈 문자열("")을 반환합니다.
     *
     * @return 컨텍스트 경로 문자열
     */
    String getContextPath();

    /**
     * 웹 애플리케이션의 초기화 매개변수 값을 반환합니다.
     *
     * 애플리케이션 초기화 매개변수는 web.xml에서 <context-param>으로 정의됩니다.
     * 서블릿별 초기화 매개변수와 달리, 전체 웹 애플리케이션에서 공유됩니다.
     *
     * 예시 web.xml:
     * <context-param>
     *   <param-name>appName</param-name>
     *   <param-value>MyWebApplication</param-value>
     * </context-param>
     *
     * @param name 매개변수 이름
     * @return 매개변수 값, 존재하지 않으면 null
     */
    String getInitParameter(String name);

    /**
     * 모든 초기화 매개변수의 이름을 열거형으로 반환합니다.
     *
     * @return 초기화 매개변수 이름들의 Enumeration
     */
    Enumeration<String> getInitParameterNames();

    /**
     * 애플리케이션 스코프에 속성을 저장합니다.
     *
     * 저장된 속성은 웹 애플리케이션의 모든 서블릿과 JSP에서 접근할 수 있습니다.
     * 서버가 재시작되면 모든 속성이 사라집니다.
     *
     * 사용 예시:
     * - 애플리케이션 전역 설정 정보
     * - 캐시된 데이터
     * - 공유 리소스 (데이터베이스 연결 풀 등)
     *
     * @param name 속성 이름
     * @param object 저장할 객체
     */
    void setAttribute(String name, Object object);

    /**
     * 애플리케이션 스코프에서 속성을 조회합니다.
     *
     * @param name 속성 이름
     * @return 속성 값, 존재하지 않으면 null
     */
    Object getAttribute(String name);

    /**
     * 애플리케이션 스코프에서 속성을 제거합니다.
     *
     * @param name 제거할 속성 이름
     */
    void removeAttribute(String name);

    /**
     * 모든 속성의 이름을 열거형으로 반환합니다.
     *
     * @return 속성 이름들의 Enumeration
     */
    Enumeration<String> getAttributeNames();

    /**
     * 지정된 경로의 리소스를 URL로 반환합니다.
     *
     * 웹 애플리케이션 내의 리소스 파일에 접근할 때 사용됩니다.
     * 경로는 "/"로 시작해야 하며, 웹 애플리케이션 루트를 기준으로 합니다.
     *
     * 예: getResource("/WEB-INF/config.properties")
     *
     * @param path 리소스 경로 ("/"로 시작)
     * @return 리소스의 URL, 존재하지 않으면 null
     */
    URL getResource(String path);

    /**
     * 지정된 경로의 리소스를 InputStream으로 반환합니다.
     *
     * 설정 파일이나 정적 리소스를 읽을 때 편리합니다.
     *
     * @param path 리소스 경로 ("/"로 시작)
     * @return 리소스의 InputStream, 존재하지 않으면 null
     */
    InputStream getResourceAsStream(String path);

    /**
     * 지정된 가상 경로가 나타내는 실제 파일 시스템 경로를 반환합니다.
     *
     * 웹 애플리케이션이 WAR 파일로 배포된 경우 null을 반환할 수 있습니다.
     *
     * @param path 가상 경로 ("/"로 시작)
     * @return 실제 파일 시스템 경로, 또는 null
     */
    String getRealPath(String path);

    /**
     * 지정된 디렉토리 경로에 있는 리소스들의 경로를 반환합니다.
     *
     * 디렉토리 탐색이나 동적 리소스 발견에 사용됩니다.
     *
     * @param path 디렉토리 경로 ("/"로 시작)
     * @return 해당 디렉토리의 리소스 경로들, 빈 Set 또는 null
     */
    Set<String> getResourcePaths(String path);

    /**
     * 로그 메시지를 기록합니다.
     *
     * 서블릿 컨테이너의 로그 파일에 메시지를 기록합니다.
     * 일반적으로 서버의 로그 디렉토리에 저장됩니다.
     *
     * @param msg 로그 메시지
     */
    void log(String msg);

    /**
     * 예외와 함께 로그 메시지를 기록합니다.
     *
     * 예외의 스택 트레이스도 함께 로그에 기록됩니다.
     *
     * @param message 로그 메시지
     * @param throwable 예외 객체
     */
    void log(String message, Throwable throwable);

    /**
     * 서블릿 컨테이너의 이름과 버전을 반환합니다.
     *
     * 예: "Apache Tomcat/9.0.0"
     *
     * @return 서버 정보 문자열
     */
    String getServerInfo();

    /**
     * 서블릿 API의 주 버전을 반환합니다.
     *
     * 예: Servlet API 3.1의 경우 3을 반환
     *
     * @return 주 버전 번호
     */
    int getMajorVersion();

    /**
     * 서블릿 API의 부 버전을 반환합니다.
     *
     * 예: Servlet API 3.1의 경우 1을 반환
     *
     * @return 부 버전 번호
     */
    int getMinorVersion();
}