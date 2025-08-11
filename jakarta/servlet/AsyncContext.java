package jakarta.servlet;

/**
 * 비동기 요청 처리를 위한 컨텍스트를 제공하는 인터페이스입니다.
 *
 * AsyncContext는 Servlet 3.0에서 도입된 비동기 처리 기능의 핵심입니다.
 * 서블릿이 요청을 비동기적으로 처리할 수 있게 하여 스레드 리소스를
 * 효율적으로 사용할 수 있습니다.
 *
 * 비동기 처리의 장점:
 * - 긴 작업 중에도 서블릿 스레드를 다른 요청에 사용 가능
 * - 높은 동시성 처리 능력
 * - 서버 리소스의 효율적 활용
 * - 실시간 응답이나 스트리밍 구현 가능
 *
 * 사용 시나리오:
 * - 데이터베이스 조회나 외부 API 호출 등 시간이 오래 걸리는 작업
 * - 실시간 알림이나 채팅 같은 long polling
 * - 파일 업로드/다운로드
 * - 백그라운드 작업 처리
 */
public interface AsyncContext {

    /**
     * 비동기 작업 완료를 나타내는 상수입니다.
     *
     * 이 문자열은 요청 속성의 이름으로 사용되어
     * 비동기 처리가 완료되었음을 표시합니다.
     */
    String ASYNC_REQUEST_URI = "jakarta.servlet.async.request_uri";
    String ASYNC_CONTEXT_PATH = "jakarta.servlet.async.context_path";
    String ASYNC_PATH_INFO = "jakarta.servlet.async.path_info";
    String ASYNC_SERVLET_PATH = "jakarta.servlet.async.servlet_path";
    String ASYNC_QUERY_STRING = "jakarta.servlet.async.query_string";

    /**
     * 원본 요청 객체를 반환합니다.
     *
     * 비동기 처리를 시작할 때 사용된 원본 ServletRequest 객체를 반환합니다.
     * 이 객체를 통해 요청 매개변수, 헤더, 속성 등에 접근할 수 있습니다.
     *
     * @return 원본 ServletRequest 객체
     */
    ServletRequest getRequest();

    /**
     * 원본 응답 객체를 반환합니다.
     *
     * 비동기 처리를 시작할 때 사용된 원본 ServletResponse 객체를 반환합니다.
     * 이 객체를 통해 클라이언트에게 응답을 전송할 수 있습니다.
     *
     * @return 원본 ServletResponse 객체
     */
    ServletResponse getResponse();

    /**
     * 요청이 원본 요청 객체로 디스패치되었는지 확인합니다.
     *
     * startAsync()가 요청/응답 매개변수 없이 호출되었는지,
     * 아니면 새로운 요청/응답 객체와 함께 호출되었는지를 나타냅니다.
     *
     * @return 원본 요청/응답 객체가 사용되고 있으면 true, 래퍼가 사용되고 있으면 false
     */
    boolean hasOriginalRequestAndResponse();

    /**
     * 비동기 작업을 다른 경로로 디스패치합니다.
     *
     * 지정된 경로의 리소스에서 비동기 작업을 계속 처리하도록 합니다.
     * 현재 스레드에서 즉시 반환되며, 실제 디스패치는 별도 스레드에서 수행됩니다.
     *
     * 사용 예시:
     * @WebServlet(urlPatterns = "/async", asyncSupported = true)
     * public class AsyncServlet extends HttpServlet {
     *     protected void doGet(HttpServletRequest request, HttpServletResponse response) {
     *         AsyncContext asyncContext = request.startAsync();
     *         // 일부 처리...
     *         asyncContext.dispatch("/result.jsp");
     *     }
     * }
     *
     * @param path 디스패치할 경로
     * @throws IllegalStateException AsyncContext가 이미 완료되었거나 다른 디스패치가 진행 중인 경우
     */
    void dispatch(String path);

    /**
     * 비동기 작업을 지정된 컨텍스트와 경로로 디스패치합니다.
     *
     * 다른 웹 애플리케이션의 리소스로 디스패치할 때 사용합니다.
     *
     * @param context 대상 서블릿 컨텍스트
     * @param path 디스패치할 경로
     * @throws IllegalStateException AsyncContext가 이미 완료되었거나 다른 디스패치가 진행 중인 경우
     */
    void dispatch(ServletContext context, String path);

    /**
     * 원본 요청 경로로 디스패치합니다.
     *
     * 매개변수 없이 호출하면 원래 요청된 경로로 다시 디스패치됩니다.
     *
     * @throws IllegalStateException AsyncContext가 이미 완료되었거나 다른 디스패치가 진행 중인 경우
     */
    void dispatch();

    /**
     * 비동기 작업을 완료하고 응답을 클라이언트에게 전송합니다.
     *
     * 이 메서드를 호출하면 비동기 처리가 완료되고 응답이 클라이언트에게 전송됩니다.
     * complete() 호출 후에는 요청/응답 객체를 더 이상 사용할 수 없습니다.
     *
     * 사용 예시:
     * AsyncContext asyncContext = request.startAsync();
     *
     * // 백그라운드에서 작업 수행
     * CompletableFuture.supplyAsync(() -> {
     *     // 긴 작업 수행
     *     return processData();
     * }).thenAccept(result -> {
     *     try {
     *         ServletResponse response = asyncContext.getResponse();
     *         response.getWriter().write(result);
     *         asyncContext.complete(); // 작업 완료
     *     } catch (IOException e) {
     *         asyncContext.complete();
     *     }
     * });
     *
     * @throws IllegalStateException AsyncContext가 이미 완료되었거나 다른 디스패치가 진행 중인 경우
     */
    void complete();

    /**
     * 비동기 리스너를 추가합니다.
     *
     * 비동기 처리의 생명주기 이벤트(완료, 오류, 타임아웃 등)를
     * 처리하는 리스너를 등록합니다.
     *
     * @param listener 등록할 AsyncListener
     * @throws IllegalStateException AsyncContext가 이미 완료되었거나 다른 디스패치가 진행 중인 경우
     */
    void addListener(AsyncListener listener);

    /**
     * 요청/응답 객체와 함께 비동기 리스너를 추가합니다.
     *
     * 리스너가 특정 요청/응답 객체를 사용해야 하는 경우에 사용합니다.
     *
     * @param listener 등록할 AsyncListener
     * @param request 리스너가 사용할 ServletRequest 객체
     * @param response 리스너가 사용할 ServletResponse 객체
     * @throws IllegalStateException AsyncContext가 이미 완료되었거나 다른 디스패치가 진행 중인 경우
     */
    void addListener(AsyncListener listener, ServletRequest request, ServletResponse response);

    /**
     * 비동기 작업의 타임아웃을 설정합니다.
     *
     * 지정된 시간(밀리초) 내에 비동기 작업이 완료되지 않으면
     * AsyncListener.onTimeout()이 호출됩니다.
     *
     * 0을 설정하면 타임아웃이 비활성화됩니다.
     *
     * 사용 예시:
     * AsyncContext asyncContext = request.startAsync();
     * asyncContext.setTimeout(30000); // 30초 타임아웃
     *
     * asyncContext.addListener(new AsyncListener() {
     *     @Override
     *     public void onTimeout(AsyncEvent event) {
     *         // 타임아웃 처리
     *         ServletResponse response = event.getAsyncContext().getResponse();
     *         try {
     *             response.getWriter().write("Request timed out");
     *             event.getAsyncContext().complete();
     *         } catch (IOException e) {
     *             // 로깅
     *         }
     *     }
     *     // 다른 메서드들...
     * });
     *
     * @param timeout 타임아웃 시간 (밀리초), 0이면 타임아웃 없음
     * @throws IllegalStateException AsyncContext가 이미 완료되었거나 다른 디스패치가 진행 중인 경우
     */
    void setTimeout(long timeout);

    /**
     * 현재 설정된 타임아웃 값을 반환합니다.
     *
     * @return 타임아웃 시간 (밀리초), 타임아웃이 설정되지 않았으면 구현 의존적인 값
     */
    long getTimeout();
}