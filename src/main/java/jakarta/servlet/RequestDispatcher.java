package src.main.java.jakarta.servlet;

// Jakarta Servlet API - RequestDispatcher Interface
// 실제 프로젝트에서는 src/main/java/jakarta/servlet/RequestDispatcher.java 위치에 배치

import java.io.IOException;

/**
 * 다른 웹 리소스(서블릿, JSP, HTML 파일 등)로 클라이언트 요청을 전달하거나 포함시키는 인터페이스입니다.
 *
 * RequestDispatcher는 서버 측에서 요청을 다른 리소스로 전달하는 메커니즘을 제공합니다.
 * 클라이언트는 이러한 내부 전달을 알 수 없으며, URL도 변경되지 않습니다.
 *
 * RequestDispatcher를 얻는 방법:
 * - ServletContext.getRequestDispatcher(path) - 절대 경로 사용
 * - ServletContext.getNamedDispatcher(name) - 서블릿 이름 사용
 * - ServletRequest.getRequestDispatcher(path) - 상대 경로 사용 가능
 *
 * 주요 사용 사례:
 * - MVC 패턴에서 Controller → View 전달
 * - 공통 헤더/푸터 포함
 * - 에러 페이지 처리
 * - 여러 서블릿의 결과 조합
 *
 * @author JavaServerArchitectures Project
 * @version 1.0
 * @see ServletContext#getRequestDispatcher(String)
 * @see ServletRequest#getRequestDispatcher(String)
 */
public interface RequestDispatcher {

    /**
     * forward 요청 시 사용되는 속성 이름 상수입니다.
     * 원본 요청의 context path를 저장합니다.
     */
    static final String FORWARD_CONTEXT_PATH = "jakarta.servlet.forward.context_path";

    /**
     * forward 요청 시 사용되는 속성 이름 상수입니다.
     * 원본 요청의 path info를 저장합니다.
     */
    static final String FORWARD_PATH_INFO = "jakarta.servlet.forward.path_info";

    /**
     * forward 요청 시 사용되는 속성 이름 상수입니다.
     * 원본 요청의 query string을 저장합니다.
     */
    static final String FORWARD_QUERY_STRING = "jakarta.servlet.forward.query_string";

    /**
     * forward 요청 시 사용되는 속성 이름 상수입니다.
     * 원본 요청의 request URI를 저장합니다.
     */
    static final String FORWARD_REQUEST_URI = "jakarta.servlet.forward.request_uri";

    /**
     * forward 요청 시 사용되는 속성 이름 상수입니다.
     * 원본 요청의 servlet path를 저장합니다.
     */
    static final String FORWARD_SERVLET_PATH = "jakarta.servlet.forward.servlet_path";

    /**
     * include 요청 시 사용되는 속성 이름 상수입니다.
     * 포함되는 리소스의 context path를 저장합니다.
     */
    static final String INCLUDE_CONTEXT_PATH = "jakarta.servlet.include.context_path";

    /**
     * include 요청 시 사용되는 속성 이름 상수입니다.
     * 포함되는 리소스의 path info를 저장합니다.
     */
    static final String INCLUDE_PATH_INFO = "jakarta.servlet.include.path_info";

    /**
     * include 요청 시 사용되는 속성 이름 상수입니다.
     * 포함되는 리소스의 query string을 저장합니다.
     */
    static final String INCLUDE_QUERY_STRING = "jakarta.servlet.include.query_string";

    /**
     * include 요청 시 사용되는 속성 이름 상수입니다.
     * 포함되는 리소스의 request URI를 저장합니다.
     */
    static final String INCLUDE_REQUEST_URI = "jakarta.servlet.include.request_uri";

    /**
     * include 요청 시 사용되는 속성 이름 상수입니다.
     * 포함되는 리소스의 servlet path를 저장합니다.
     */
    static final String INCLUDE_SERVLET_PATH = "jakarta.servlet.include.servlet_path";

    /**
     * 에러 처리 시 사용되는 속성 이름 상수입니다.
     * 에러를 발생시킨 예외 객체를 저장합니다.
     */
    static final String ERROR_EXCEPTION = "jakarta.servlet.error.exception";

    /**
     * 에러 처리 시 사용되는 속성 이름 상수입니다.
     * 에러를 발생시킨 예외의 타입을 저장합니다.
     */
    static final String ERROR_EXCEPTION_TYPE = "jakarta.servlet.error.exception_type";

    /**
     * 에러 처리 시 사용되는 속성 이름 상수입니다.
     * 에러 메시지를 저장합니다.
     */
    static final String ERROR_MESSAGE = "jakarta.servlet.error.message";

    /**
     * 에러 처리 시 사용되는 속성 이름 상수입니다.
     * 에러가 발생한 요청 URI를 저장합니다.
     */
    static final String ERROR_REQUEST_URI = "jakarta.servlet.error.request_uri";

    /**
     * 에러 처리 시 사용되는 속성 이름 상수입니다.
     * 에러를 발생시킨 서블릿 이름을 저장합니다.
     */
    static final String ERROR_SERVLET_NAME = "jakarta.servlet.error.servlet_name";

    /**
     * 에러 처리 시 사용되는 속성 이름 상수입니다.
     * HTTP 상태 코드를 저장합니다.
     */
    static final String ERROR_STATUS_CODE = "jakarta.servlet.error.status_code";

    /**
     * 요청을 다른 웹 리소스로 전달합니다.
     *
     * forward는 현재 서블릿에서 다른 리소스로 요청 처리를 완전히 넘기는 것입니다.
     * forward 이후에는 현재 서블릿에서 더 이상 응답을 생성할 수 없습니다.
     *
     * 주요 특징:
     * - 클라이언트의 URL은 변경되지 않음 (서버 내부 전달)
     * - 원본 요청의 파라미터와 속성이 그대로 전달됨
     * - 응답이 커밋되기 전에만 호출 가능
     * - forward 후에는 현재 서블릿의 응답 생성 불가
     *
     * MVC 패턴 예시:
     * <pre>
     * {@code
     * // Controller 서블릿에서
     * protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
     *     // 비즈니스 로직 처리
     *     User user = userService.getUser(userId);
     *
     *     // 결과 데이터를 request에 저장
     *     req.setAttribute("user", user);
     *
     *     // View (JSP)로 forward
     *     RequestDispatcher rd = req.getRequestDispatcher("/WEB-INF/views/userProfile.jsp");
     *     rd.forward(req, resp);
     * }
     * }
     * </pre>
     *
     * @param request 전달할 ServletRequest 객체
     * @param response 전달할 ServletResponse 객체
     * @throws ServletException 대상 리소스에서 예외가 발생한 경우
     * @throws IOException I/O 오류가 발생한 경우
     * @throws IllegalStateException 응답이 이미 커밋된 경우
     */
    void forward(ServletRequest request, ServletResponse response)
            throws ServletException, IOException;

    /**
     * 다른 웹 리소스의 내용을 현재 응답에 포함시킵니다.
     *
     * include는 다른 리소스의 출력을 현재 응답에 삽입하는 것입니다.
     * include 이후에도 현재 서블릿에서 계속 응답을 생성할 수 있습니다.
     *
     * 주요 특징:
     * - 포함되는 리소스의 출력이 현재 응답에 삽입됨
     * - 포함되는 리소스는 상태 코드나 응답 헤더를 변경할 수 없음
     * - 여러 번 호출하여 여러 리소스를 포함시킬 수 있음
     * - 현재 서블릿에서 include 전후로 추가 출력 가능
     *
     * 공통 헤더/푸터 포함 예시:
     * <pre>
     * {@code
     * protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
     *     PrintWriter out = resp.getWriter();
     *
     *     // 공통 헤더 포함
     *     RequestDispatcher header = req.getRequestDispatcher("/common/header.jsp");
     *     header.include(req, resp);
     *
     *     // 메인 콘텐츠 출력
     *     out.println("<h1>Main Content</h1>");
     *     out.println("<p>This is the main content.</p>");
     *
     *     // 공통 푸터 포함
     *     RequestDispatcher footer = req.getRequestDispatcher("/common/footer.jsp");
     *     footer.include(req, resp);
     * }
     * }
     * </pre>
     *
     * @param request 전달할 ServletRequest 객체
     * @param response 전달할 ServletResponse 객체
     * @throws ServletException 대상 리소스에서 예외가 발생한 경우
     * @throws IOException I/O 오류가 발생한 경우
     */
    void include(ServletRequest request, ServletResponse response)
            throws ServletException, IOException;
}