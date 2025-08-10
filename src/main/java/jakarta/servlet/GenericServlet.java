package src.main.java.jakarta.servlet;

import java.io.IOException;
import java.util.Enumeration;

/**
 * 프로토콜에 독립적인 범용 서블릿을 위한 추상 클래스입니다.
 *
 * GenericServlet은 Servlet 인터페이스의 기본 구현을 제공하여
 * 개발자가 서블릿을 쉽게 작성할 수 있도록 도와줍니다.
 *
 * 이 클래스의 주요 기능:
 * - Servlet 인터페이스의 기본 구현 제공
 * - ServletConfig 객체 관리
 * - 초기화 매개변수 접근 메서드 제공
 * - 로깅 기능 제공
 *
 * 개발자는 이 클래스를 상속받아 service() 메서드만 구현하면 됩니다.
 * HTTP 전용 서블릿의 경우 HttpServlet을 사용하는 것이 좋습니다.
 */
public abstract class GenericServlet implements Servlet, ServletConfig {

    /**
     * 직렬화를 위한 버전 ID입니다.
     */
    private static final long serialVersionUID = 1L;

    /**
     * 서블릿의 설정 정보를 저장하는 ServletConfig 객체입니다.
     *
     * 이 객체는 init() 메서드에서 서블릿 컨테이너로부터 전달받아 저장됩니다.
     * 서블릿의 생명주기 동안 설정 정보에 접근할 때 사용됩니다.
     */
    private transient ServletConfig config;

    /**
     * 기본 생성자입니다.
     *
     * 서블릿 컨테이너가 서블릿 인스턴스를 생성할 때 호출됩니다.
     * 아무런 작업도 수행하지 않습니다.
     */
    public GenericServlet() {
        // 기본 생성자는 특별한 초기화 작업을 하지 않습니다.
        // 실제 초기화는 init() 메서드에서 수행됩니다.
    }

    /**
     * 서블릿을 초기화합니다.
     *
     * 이 메서드는 서블릿 컨테이너가 서블릿을 서비스에 투입하기 전에 호출합니다.
     * ServletConfig 객체를 저장하고 init() 오버로드 메서드를 호출합니다.
     *
     * 개발자는 보통 이 메서드를 오버라이드하지 않고,
     * 매개변수가 없는 init() 메서드를 오버라이드합니다.
     *
     * @param config 서블릿의 설정 정보
     * @throws ServletException 초기화 중 오류가 발생한 경우
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        // ServletConfig 객체를 인스턴스 변수에 저장
        this.config = config;

        // 매개변수가 없는 init() 메서드 호출
        // 개발자가 오버라이드하기 쉽도록 제공되는 편의 메서드입니다.
        init();
    }

    /**
     * 서블릿을 초기화하는 편의 메서드입니다.
     *
     * 개발자는 이 메서드를 오버라이드하여 서블릿 초기화 로직을 작성할 수 있습니다.
     * ServletConfig를 매개변수로 받지 않으므로 더 간단하게 사용할 수 있습니다.
     *
     * 기본 구현은 아무 작업도 하지 않습니다.
     *
     * 예시:
     * @Override
     * public void init() throws ServletException {
     *     // 데이터베이스 연결 초기화
     *     String dbUrl = getInitParameter("dbUrl");
     *     // 캐시 초기화
     *     // 기타 리소스 설정
     * }
     *
     * @throws ServletException 초기화 중 오류가 발생한 경우
     */
    public void init() throws ServletException {
        // 기본 구현은 아무 작업도 하지 않습니다.
        // 하위 클래스에서 필요에 따라 오버라이드할 수 있습니다.
    }

    /**
     * 클라이언트 요청을 처리하는 추상 메서드입니다.
     *
     * 이 메서드는 각 요청에 대해 서블릿 컨테이너가 호출합니다.
     * 하위 클래스에서 반드시 구현해야 하는 메서드입니다.
     *
     * 멀티스레드 환경에서 동시에 여러 번 호출될 수 있으므로
     * 스레드 안전성을 고려해야 합니다.
     *
     * @param req 클라이언트 요청 정보
     * @param res 클라이언트 응답 정보
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    @Override
    public abstract void service(ServletRequest req, ServletResponse res)
            throws ServletException, IOException;

    /**
     * 서블릿을 종료합니다.
     *
     * 서블릿 컨테이너가 서블릿을 서비스에서 제거할 때 호출됩니다.
     * 기본 구현은 아무 작업도 하지 않습니다.
     *
     * 하위 클래스에서 리소스 정리가 필요한 경우 이 메서드를 오버라이드합니다.
     *
     * 예시:
     * @Override
     * public void destroy() {
     *     // 데이터베이스 연결 해제
     *     // 파일 핸들 정리
     *     // 백그라운드 스레드 종료
     * }
     */
    @Override
    public void destroy() {
        // 기본 구현은 아무 작업도 하지 않습니다.
        // 하위 클래스에서 필요에 따라 오버라이드할 수 있습니다.
    }

    /**
     * 서블릿 정보를 반환합니다.
     *
     * 서블릿에 대한 간단한 설명을 제공합니다.
     * 관리 도구나 로깅에서 사용될 수 있습니다.
     *
     * @return 서블릿에 대한 설명 문자열
     */
    @Override
    public String getServletInfo() {
        return "GenericServlet";
    }

    // ========== ServletConfig 인터페이스 구현 ==========

    /**
     * 서블릿의 ServletConfig 객체를 반환합니다.
     *
     * @return 서블릿의 설정 객체
     */
    @Override
    public ServletConfig getServletConfig() {
        return config;
    }

    /**
     * 서블릿의 이름을 반환합니다.
     *
     * @return 서블릿 이름
     */
    @Override
    public String getServletName() {
        return config != null ? config.getServletName() : null;
    }

    /**
     * 웹 애플리케이션의 ServletContext를 반환합니다.
     *
     * @return ServletContext 객체
     */
    @Override
    public ServletContext getServletContext() {
        return config != null ? config.getServletContext() : null;
    }

    /**
     * 지정된 이름의 초기화 매개변수 값을 반환합니다.
     *
     * @param name 매개변수 이름
     * @return 매개변수 값, 존재하지 않으면 null
     */
    @Override
    public String getInitParameter(String name) {
        return config != null ? config.getInitParameter(name) : null;
    }

    /**
     * 모든 초기화 매개변수의 이름을 열거형으로 반환합니다.
     *
     * @return 매개변수 이름들의 Enumeration
     */
    @Override
    public Enumeration<String> getInitParameterNames() {
        return config != null ? config.getInitParameterNames() : null;
    }

    // ========== 편의 메서드들 ==========

    /**
     * 로그 메시지를 기록합니다.
     *
     * ServletContext의 log() 메서드를 사용하여 메시지를 로깅합니다.
     * 서블릿에서 간편하게 로깅을 할 수 있도록 제공되는 편의 메서드입니다.
     *
     * @param msg 로그 메시지
     */
    public void log(String msg) {
        ServletContext context = getServletContext();
        if (context != null) {
            context.log(getClass().getName() + ": " + msg);
        }
    }

    /**
     * 예외와 함께 로그 메시지를 기록합니다.
     *
     * @param message 로그 메시지
     * @param t 예외 객체
     */
    public void log(String message, Throwable t) {
        ServletContext context = getServletContext();
        if (context != null) {
            context.log(getClass().getName() + ": " + message, t);
        }
    }
}
