package src.main.java.jakarta.servlet;

// Jakarta Servlet API - AsyncContext Interface
// 실제 프로젝트에서는 src/main/java/jakarta/servlet/AsyncContext.java 위치에 배치

/**
 * 서블릿의 비동기 처리를 위한 컨텍스트 인터페이스입니다.
 *
 * AsyncContext는 ServletRequest.startAsync()를 통해 얻을 수 있으며,
 * 요청 처리를 비동기적으로 수행할 수 있게 해줍니다.
 *
 * 비동기 처리의 장점:
 * - 스레드 효율성: I/O 대기 시간 동안 스레드를 다른 요청 처리에 활용
 * - 확장성: 더 많은 동시 연결 처리 가능
 * - 응답성: 긴 처리 작업 시 서버 응답성 유지
 *
 * 일반적인 사용 패턴:
 * 1. ServletRequest.startAsync()로 비동기 모드 시작
 * 2. 백그라운드에서 긴 작업 수행 (다른 스레드)
 * 3. 작업 완료 후 AsyncContext.complete() 호출
 *
 * @author JavaServerArchitectures Project
 * @version 1.0
 * @since Servlet 3.0
 * @see ServletRequest#startAsync()
 */
public interface AsyncContext {

    /**
     * 기본 비동기 타임아웃 시간을 나타내는 상수입니다.
     *
     * 타임아웃이 설정되지 않은 경우 사용되는 기본값으로,
     * 30초(30,000 밀리초)입니다.
     */
    static final long ASYNC_REQUEST_TIMEOUT = 30000;

    /**
     * 이 AsyncContext와 연관된 ServletRequest를 반환합니다.
     *
     * @return ServletRequest 객체
     * @throws IllegalStateException AsyncContext가 이미 완료된 경우
     */
    ServletRequest getRequest();

    /**
     * 이 AsyncContext와 연관된 ServletResponse를 반환합니다.
     *
     * @return ServletResponse 객체
     * @throws IllegalStateException AsyncContext가 이미 완료된 경우
     */
    ServletResponse getResponse();

    /**
     * 요청에 원래 전달된 ServletRequest가 있는지 확인합니다.
     *
     * startAsync()가 파라미터 없이 호출된 경우 true를 반환합니다.
     *
     * @return 원래 요청/응답 객체를 사용하는 경우 true
     */
    boolean hasOriginalRequestAndResponse();

    /**
     * 요청을 컨테이너가 관리하는 스레드로 디스패치합니다.
     *
     * 현재 요청 URI로 요청을 전달합니다.
     *
     * 예시:
     * <pre>
     * {@code
     * AsyncContext asyncContext = request.startAsync();
     *
     * // 백그라운드 작업 수행
     * executor.submit(() -> {
     *     try {
     *         // 긴 처리 작업...
     *         Thread.sleep(5000);
     *
     *         // 처리 완료 후 원래 서블릿으로 다시 디스패치
     *         asyncContext.dispatch();
     *     } catch (Exception e) {
     *         asyncContext.complete();
     *     }
     * });
     * }
     * </pre>
     *
     * @throws IllegalStateException AsyncContext가 이미 완료되었거나
     *                              이미 디스패치된 경우
     */
    void dispatch();

    /**
     * 지정된 경로로 요청을 디스패치합니다.
     *
     * @param path 디스패치할 경로
     * @throws IllegalStateException AsyncContext가 이미 완료되었거나
     *                              이미 디스패치된 경우
     */
    void dispatch(String path);

    /**
     * 지정된 ServletContext와 경로로 요청을 디스패치합니다.
     *
     * @param context 대상 ServletContext
     * @param path 디스패치할 경로
     * @throws IllegalStateException AsyncContext가 이미 완료되었거나
     *                              이미 디스패치된 경우
     */
    void dispatch(ServletContext context, String path);

    /**
     * 비동기 처리를 완료합니다.
     *
     * 이 메소드를 호출하면 응답이 클라이언트에게 커밋되고
     * 연결이 종료됩니다. 반드시 호출해야 리소스가 정리됩니다.
     *
     * 예시:
     * <pre>
     * {@code
     * AsyncContext asyncContext = request.startAsync();
     *
     * CompletableFuture.supplyAsync(() -> {
     *     // 데이터베이스에서 데이터 조회
     *     return databaseService.getUserData(userId);
     * }).thenAccept(userData -> {
     *     try {
     *         // 응답 생성
     *         ServletResponse response = asyncContext.getResponse();
     *         response.setContentType("application/json");
     *         response.getWriter().write(userData.toJson());
     *
     *         // 비동기 처리 완료
     *         asyncContext.complete();
     *     } catch (Exception e) {
     *         // 에러 처리 후 완료
     *         asyncContext.complete();
     *     }
     * });
     * }
     * </pre>
     *
     * @throws IllegalStateException AsyncContext가 이미 완료된 경우
     */
    void complete();

    /**
     * 새로운 스레드에서 Runnable을 실행합니다.
     *
     * 컨테이너가 관리하는 스레드 풀을 사용하여 비동기 작업을 실행합니다.
     * 이 방법은 애플리케이션에서 별도의 ExecutorService를 관리할 필요가 없어 편리합니다.
     *
     * 예시:
     * <pre>
     * {@code
     * AsyncContext asyncContext = request.startAsync();
     *
     * asyncContext.start(new Runnable() {
     *     @Override
     *     public void run() {
     *         try {
     *             // 긴 처리 작업
     *             String result = performLongRunningTask();
     *
     *             // 응답 생성
     *             ServletResponse response = asyncContext.getResponse();
     *             response.getWriter().write(result);
     *             asyncContext.complete();
     *         } catch (Exception e) {
     *             asyncContext.complete();
     *         }
     *     }
     * });
     * }
     * </pre>
     *
     * @param run 실행할 Runnable 객체
     * @throws IllegalStateException AsyncContext가 이미 완료된 경우
     */
    void start(Runnable run);

    /**
     * 비동기 리스너를 추가합니다.
     *
     * 비동기 처리의 생명주기 이벤트를 모니터링할 수 있습니다.
     *
     * @param listener 추가할 AsyncListener
     * @throws IllegalStateException AsyncContext가 이미 완료된 경우
     */
    void addListener(AsyncListener listener);

    /**
     * 지정된 ServletRequest와 ServletResponse로 비동기 리스너를 추가합니다.
     *
     * @param listener 추가할 AsyncListener
     * @param servletRequest 리스너에서 사용할 ServletRequest
     * @param servletResponse 리스너에서 사용할 ServletResponse
     * @throws IllegalStateException AsyncContext가 이미 완료된 경우
     */
    void addListener(AsyncListener listener, ServletRequest servletRequest,
                     ServletResponse servletResponse);

    /**
     * AsyncListener를 생성하여 추가합니다.
     *
     * @param clazz AsyncListener 구현 클래스
     * @return 생성된 AsyncListener 인스턴스
     * @throws ServletException 리스너 생성에 실패한 경우
     * @since Servlet 3.0
     */
    <T extends AsyncListener> T createListener(Class<T> clazz) throws ServletException;

    /**
     * 비동기 처리의 타임아웃 시간을 설정합니다.
     *
     * 지정된 시간 내에 complete()나 dispatch()가 호출되지 않으면
     * AsyncListener.onTimeout()이 호출되고 자동으로 완료됩니다.
     *
     * @param timeout 타임아웃 시간 (밀리초)
     */
    void setTimeout(long timeout);

    /**
     * 현재 설정된 타임아웃 시간을 반환합니다.
     *
     * @return 타임아웃 시간 (밀리초)
     */
    long getTimeout();
}