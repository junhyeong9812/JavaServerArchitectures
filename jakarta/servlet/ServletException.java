package jakarta.servlet;

/**
 * 서블릿 관련 예외를 나타내는 클래스입니다.
 *
 * ServletException은 서블릿 실행 중 발생할 수 있는 다양한 예외 상황을 나타냅니다:
 * - 서블릿 초기화 실패
 * - 요청 처리 중 비즈니스 로직 오류
 * - 설정 오류
 * - 리소스 접근 실패
 *
 * 이 예외는 체크드 예외(checked exception)로, 반드시 처리하거나 선언해야 합니다.
 * IOException과 달리 서블릿 특화된 오류 상황을 나타내는 데 사용됩니다.
 */
public class ServletException extends Exception {

    /**
     * 직렬화를 위한 버전 ID입니다.
     * 클래스 구조가 변경되어도 이전 버전과의 호환성을 유지하기 위해 사용됩니다.
     */
    private static final long serialVersionUID = 1L;

    /**
     * 이 예외의 원인이 된 근본 예외입니다.
     *
     * 예를 들어, 데이터베이스 연결 실패로 인한 SQLException이 발생했을 때,
     * 그 SQLException을 rootCause로 보관하고 ServletException으로 감싸서 던질 수 있습니다.
     * 이렇게 하면 원래 예외 정보를 잃지 않으면서도 서블릿 계층에 맞는 예외로 변환할 수 있습니다.
     */
    private Throwable rootCause;

    /**
     * 메시지 없는 기본 생성자입니다.
     *
     * 단순히 예외 발생 사실만 알려주고 싶을 때 사용합니다.
     * 보통은 더 구체적인 정보를 제공하는 다른 생성자를 사용하는 것이 좋습니다.
     */
    public ServletException() {
        super();
    }

    /**
     * 예외 메시지를 포함하는 생성자입니다.
     *
     * 예외가 발생한 이유를 설명하는 메시지를 포함할 수 있습니다.
     * 사용자나 개발자가 문제를 이해하는 데 도움이 되는 정보를 제공해야 합니다.
     *
     * 예시:
     * throw new ServletException("사용자 인증에 실패했습니다");
     * throw new ServletException("데이터베이스 연결을 설정할 수 없습니다");
     *
     * @param message 예외에 대한 설명 메시지
     */
    public ServletException(String message) {
        super(message);
    }

    /**
     * 메시지와 근본 원인 예외를 함께 포함하는 생성자입니다.
     *
     * 다른 예외를 감싸면서 서블릿 계층에 맞는 새로운 예외로 변환할 때 사용합니다.
     * 원본 예외의 정보를 보존하면서도 서블릿 관련 컨텍스트를 추가할 수 있습니다.
     *
     * 예시:
     * try {
     *     Connection conn = dataSource.getConnection();
     * } catch (SQLException e) {
     *     throw new ServletException("데이터베이스 연결 실패", e);
     * }
     *
     * @param message 예외에 대한 설명 메시지
     * @param rootCause 이 예외의 원인이 된 예외
     */
    public ServletException(String message, Throwable rootCause) {
        super(message, rootCause);
        this.rootCause = rootCause;
    }

    /**
     * 근본 원인 예외만을 포함하는 생성자입니다.
     *
     * 별도의 메시지 없이 원본 예외를 ServletException으로 감쌀 때 사용합니다.
     * 원본 예외의 메시지가 충분히 설명적일 때 유용합니다.
     *
     * @param rootCause 이 예외의 원인이 된 예외
     */
    public ServletException(Throwable rootCause) {
        super(rootCause);
        this.rootCause = rootCause;
    }

    /**
     * 이 예외의 근본 원인이 된 예외를 반환합니다.
     *
     * 예외 체인에서 원래 발생한 예외를 추적할 때 사용합니다.
     * 디버깅이나 로깅에서 전체 예외 스택을 확인하는 데 유용합니다.
     *
     * 예시 사용법:
     * try {
     *     // 서블릿 작업
     * } catch (ServletException e) {
     *     Throwable cause = e.getRootCause();
     *     if (cause instanceof SQLException) {
     *         // 데이터베이스 관련 특별 처리
     *     }
     * }
     *
     * @return 근본 원인 예외, 없으면 null
     */
    public Throwable getRootCause() {
        return rootCause;
    }
}