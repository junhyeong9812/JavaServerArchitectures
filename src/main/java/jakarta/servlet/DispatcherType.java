package src.main.java.jakarta.servlet;

// Jakarta Servlet API - DispatcherType Enum
// 실제 프로젝트에서는 src/main/java/jakarta/servlet/DispatcherType.java 위치에 배치

/**
 * 서블릿 요청의 디스패처 타입을 나타내는 열거형입니다.
 *
 * DispatcherType은 현재 요청이 어떤 방식으로 서블릿에 도달했는지를 나타냅니다.
 * 이 정보는 필터에서 특정 타입의 요청만 처리하고 싶을 때 유용합니다.
 *
 * 각 디스패처 타입별 특징:
 * - REQUEST: 클라이언트로부터 직접 온 요청
 * - FORWARD: RequestDispatcher.forward()로 전달된 요청
 * - INCLUDE: RequestDispatcher.include()로 포함된 요청
 * - ASYNC: 비동기 처리 중인 요청
 * - ERROR: 에러 처리를 위해 전달된 요청
 *
 * 예시 사용법:
 * <pre>
 * {@code
 * // 필터에서 특정 디스패처 타입만 처리
 * public void doFilter(ServletRequest request, ServletResponse response,
 *                      FilterChain chain) throws IOException, ServletException {
 *
 *     DispatcherType dispatcherType = request.getDispatcherType();
 *
 *     if (dispatcherType == DispatcherType.REQUEST) {
 *         // 직접 요청에만 로깅 적용
 *         logRequest((HttpServletRequest) request);
 *     }
 *
 *     chain.doFilter(request, response);
 * }
 * }
 * </pre>
 *
 * @author JavaServerArchitectures Project
 * @version 1.0
 * @since Servlet 3.0
 * @see ServletRequest#getDispatcherType()
 * @see Filter
 */
public enum DispatcherType {

    /**
     * 클라이언트로부터 직접 온 요청입니다.
     *
     * 가장 일반적인 요청 타입으로, 웹 브라우저나 HTTP 클라이언트에서
     * 서버로 직접 보낸 요청입니다.
     *
     * 특징:
     * - HTTP 메소드(GET, POST 등)에 따른 처리
     * - 인증, 권한 검사 필요
     * - 로깅, 모니터링 대상
     * - 캐싱 정책 적용 가능
     */
    REQUEST,

    /**
     * RequestDispatcher.forward()에 의해 전달된 요청입니다.
     *
     * 서버 내부에서 다른 서블릿이나 JSP로 요청이 전달된 경우입니다.
     * 클라이언트는 이러한 전달을 알 수 없습니다.
     *
     * 특징:
     * - URL은 변경되지 않음
     * - 원본 요청 정보가 request attribute에 저장됨
     * - MVC 패턴에서 Controller → View 전달에 사용
     * - 인증은 보통 원본 요청에서 처리됨
     *
     * 예시 시나리오:
     * 1. 사용자가 /login POST 요청
     * 2. LoginServlet에서 인증 처리
     * 3. 성공 시 RequestDispatcher.forward()로 /dashboard로 전달
     * 4. DashboardServlet은 FORWARD 타입으로 요청 받음
     */
    FORWARD,

    /**
     * RequestDispatcher.include()에 의해 포함된 요청입니다.
     *
     * 현재 서블릿 실행 중에 다른 서블릿이나 JSP의 내용을
     * 현재 응답에 포함시키는 경우입니다.
     *
     * 특징:
     * - 포함된 리소스는 응답 헤더나 상태 코드 변경 불가
     * - 여러 번 include 가능
     * - 현재 서블릿이 응답 제어권 유지
     * - 공통 헤더/푸터, 위젯 포함에 사용
     *
     * 예시 시나리오:
     * 1. MainServlet에서 메인 콘텐츠 생성
     * 2. RequestDispatcher.include()로 HeaderServlet 포함
     * 3. RequestDispatcher.include()로 FooterServlet 포함
     * 4. 각각은 INCLUDE 타입으로 실행됨
     */
    INCLUDE,

    /**
     * 비동기 처리 중인 요청입니다.
     *
     * ServletRequest.startAsync()로 시작된 비동기 처리에서
     * AsyncContext.dispatch()에 의해 전달된 요청입니다.
     *
     * 특징:
     * - 원래 요청을 처리하던 스레드와 다른 스레드에서 실행 가능
     * - 긴 처리 시간이 필요한 작업에 사용
     * - 스레드 효율성 향상
     * - Servlet 3.0+에서 지원
     *
     * 예시 시나리오:
     * 1. 클라이언트가 /api/process POST 요청
     * 2. ProcessServlet에서 startAsync() 호출
     * 3. 백그라운드에서 긴 작업 수행
     * 4. 작업 완료 후 AsyncContext.dispatch()로 결과 처리
     * 5. 결과 처리 서블릿은 ASYNC 타입으로 실행됨
     */
    ASYNC,

    /**
     * 에러 처리를 위해 전달된 요청입니다.
     *
     * 서블릿에서 예외가 발생했거나 HTTP 에러 상태 코드가
     * 발생했을 때 에러 페이지로 전달되는 요청입니다.
     *
     * 특징:
     * - web.xml의 <error-page> 설정에 의해 전달
     * - 에러 관련 정보가 request attribute에 설정됨
     * - 사용자 친화적 에러 페이지 표시
     * - 에러 로깅 및 알림에 사용
     *
     * 에러 관련 request attribute:
     * - jakarta.servlet.error.status_code: HTTP 상태 코드
     * - jakarta.servlet.error.exception: 발생한 예외 객체
     * - jakarta.servlet.error.message: 에러 메시지
     * - jakarta.servlet.error.request_uri: 에러가 발생한 URI
     *
     * 예시 시나리오:
     * 1. 사용자가 /api/data 요청
     * 2. DataServlet에서 NullPointerException 발생
     * 3. 컨테이너가 500 에러 감지
     * 4. web.xml 설정에 따라 ErrorPageServlet으로 전달
     * 5. ErrorPageServlet은 ERROR 타입으로 실행됨
     */
    ERROR
}