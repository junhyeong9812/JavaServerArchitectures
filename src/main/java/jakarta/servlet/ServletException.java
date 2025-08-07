package src.main.java.jakarta.servlet;

// Jakarta Servlet API - ServletException Class
// 실제 프로젝트에서는 src/main/java/jakarta/servlet/ServletException.java 위치에 배치

/**
 * 서블릿에서 발생하는 일반적인 예외를 나타내는 클래스입니다.
 *
 * ServletException은 서블릿 실행 중 발생하는 다양한 문제들을 나타내는 데 사용됩니다:
 * - 서블릿 초기화 실패
 * - 요청 처리 중 비즈니스 로직 오류
 * - 설정 오류
 * - 리소스 접근 실패
 *
 * 이 예외는 checked exception으로, 서블릿의 init(), service(), destroy()
 * 메소드에서 반드시 선언하거나 처리해야 합니다.
 *
 * ServletException은 root cause를 포함할 수 있어 예외 체인을 형성할 수 있습니다.
 *
 * @author JavaServerArchitectures Project
 * @version 1.0
 */
public class ServletException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * 메시지 없이 새로운 ServletException을 생성합니다.
     */
    public ServletException() {
        super();
    }

    /**
     * 지정된 메시지를 가진 새로운 ServletException을 생성합니다.
     *
     * @param message 예외 메시지
     */
    public ServletException(String message) {
        super(message);
    }

    /**
     * 지정된 메시지와 원인 예외를 가진 새로운 ServletException을 생성합니다.
     *
     * 예시:
     * <pre>
     * {@code
     * try {
     *     // 데이터베이스 연결 시도
     *     connection = DriverManager.getConnection(url, user, password);
     * } catch (SQLException e) {
     *     throw new ServletException("Database connection failed", e);
     * }
     * }
     * </pre>
     *
     * @param message 예외 메시지
     * @param cause 원인이 된 예외 (root cause)
     */
    public ServletException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 원인 예외만 가진 새로운 ServletException을 생성합니다.
     *
     * 메시지는 원인 예외의 메시지를 사용합니다.
     *
     * @param cause 원인이 된 예외 (root cause)
     */
    public ServletException(Throwable cause) {
        super(cause);
    }

    /**
     * 이 예외의 원인이 된 예외를 반환합니다.
     *
     * 이 메소드는 Servlet 2.3 이전 버전과의 호환성을 위해 유지됩니다.
     * 새로운 코드에서는 Throwable.getCause()를 사용하는 것이 권장됩니다.
     *
     * @return 원인 예외, 없으면 null
     */
    public Throwable getRootCause() {
        return getCause();
    }
}