package src.main.java.jakarta.servlet;

import java.io.IOException;

/**
 * 다른 리소스(서블릿, JSP, HTML 파일 등)로 요청을 전달하는 인터페이스입니다.
 *
 * RequestDispatcher는 서버 내부에서 요청을 다른 리소스로 전달하거나
 * 다른 리소스의 내용을 현재 응답에 포함시키는 기능을 제공합니다.
 *
 * 두 가지 주요 기능:
 * 1. forward: 요청을 다른 리소스로 전달 (제어권 완전 이양)
 * 2. include: 다른 리소스의 응답을 현재 응답에 포함
 *
 * 사용 사례:
 * - MVC 패턴에서 컨트롤러가 뷰로 요청 전달
 * - 에러 페이지로 리다이렉트
 * - 공통 헤더/푸터 포함
 * - 동적 페이지 구성
 */
public interface RequestDispatcher {

    /**
     * 요청을 다른 리소스로 전달합니다.
     *
     * forward는 현재 서블릿에서 다른 리소스로 요청의 제어권을 완전히 넘깁니다.
     * 대상 리소스가 클라이언트에 대한 응답을 담당하게 됩니다.
     *
     * 동작 방식:
     * 1. 현재 서블릿의 실행이 중단됩니다
     * 2. 요청과 응답 객체가 대상 리소스로 전달됩니다
     * 3. 대상 리소스가 응답을 생성합니다
     * 4. 클라이언트는 대상 리소스의 응답을 받습니다
     *
     * 제약사항:
     * - 응답이 이미 커밋된 상태에서는 forward할 수 없습니다
     * - forward 후에는 현재 서블릿으로 제어권이 돌아오지 않습니다
     * - 출력 버퍼의 내용은 클리어됩니다 (커밋되지 않은 경우)
     *
     * 사용 예시:
     * // 컨트롤러 서블릿에서 뷰로 전달
     * request.setAttribute("user", userObject);
     * RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/views/user.jsp");
     * dispatcher.forward(request, response);
     *
     * // 에러 처리
     * if (error) {
     *     request.setAttribute("errorMessage", "처리 중 오류가 발생했습니다");
     *     request.getRequestDispatcher("/error.jsp").forward(request, response);
     * }
     *
     * @param request 전달할 ServletRequest 객체
     * @param response 전달할 ServletResponse 객체
     * @throws ServletException 대상 리소스에서 예외가 발생한 경우
     * @throws IOException 입출력 오류가 발생한 경우
     * @throws IllegalStateException 응답이 이미 커밋된 경우
     */
    void forward(ServletRequest request, ServletResponse response)
            throws ServletException, IOException;

    /**
     * 다른 리소스의 응답을 현재 응답에 포함시킵니다.
     *
     * include는 다른 리소스의 출력을 현재 응답에 포함시키면서
     * 현재 서블릿이 계속 실행됩니다. 대상 리소스는 응답 내용만 기여하고
     * 제어권은 현재 서블릿에 유지됩니다.
     *
     * 동작 방식:
     * 1. 대상 리소스가 실행됩니다
     * 2. 대상 리소스의 출력이 현재 응답에 추가됩니다
     * 3. 제어권이 현재 서블릿으로 돌아옵니다
     * 4. 현재 서블릿이 계속 실행됩니다
     *
     * 제약사항:
     * - 대상 리소스는 응답 상태나 헤더를 변경할 수 없습니다
     * - 대상 리소스에서 sendRedirect()나 sendError() 호출은 무시됩니다
     * - 출력 스트림만 사용 가능합니다
     *
     * 사용 예시:
     * // 공통 헤더 포함
     * RequestDispatcher header = request.getRequestDispatcher("/header.jsp");
     * header.include(request, response);
     *
     * // 메인 콘텐츠 작성
     * PrintWriter out = response.getWriter();
     * out.println("<h1>메인 페이지</h1>");
     * out.println("<p>환영합니다!</p>");
     *
     * // 공통 푸터 포함
     * RequestDispatcher footer = request.getRequestDispatcher("/footer.jsp");
     * footer.include(request, response);
     *
     * // 동적 콘텐츠 조합 예시
     * String[] sections = {"news", "weather", "sports"};
     * for (String section : sections) {
     *     RequestDispatcher dispatcher = request.getRequestDispatcher("/" + section + ".jsp");
     *     dispatcher.include(request, response);
     * }
     *
     * @param request 전달할 ServletRequest 객체
     * @param response 전달할 ServletResponse 객체
     * @throws ServletException 대상 리소스에서 예외가 발생한 경우
     * @throws IOException 입출력 오류가 발생한 경우
     */
    void include(ServletRequest request, ServletResponse response)
            throws ServletException, IOException;
}