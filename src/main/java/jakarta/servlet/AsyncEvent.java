package src.main.java.jakarta.servlet;

/**
 * 비동기 처리 중 발생하는 이벤트 정보를 담는 클래스입니다.
 *
 * AsyncEvent는 AsyncListener의 메서드들에 전달되어
 * 이벤트 발생 시점의 컨텍스트 정보를 제공합니다.
 *
 * 포함되는 정보:
 * - AsyncContext: 비동기 처리 컨텍스트
 * - ServletRequest: 요청 객체 (선택적)
 * - ServletResponse: 응답 객체 (선택적)
 * - Throwable: 오류 정보 (에러 이벤트인 경우)
 */
public class AsyncEvent {

    /**
     * 이벤트와 연관된 AsyncContext 객체입니다.
     */
    private final AsyncContext asyncContext;

    /**
     * 이벤트와 연관된 ServletRequest 객체입니다.
     * AsyncListener 등록 시 제공된 경우에만 설정됩니다.
     */
    private final ServletRequest request;

    /**
     * 이벤트와 연관된 ServletResponse 객체입니다.
     * AsyncListener 등록 시 제공된 경우에만 설정됩니다.
     */
    private final ServletResponse response;

    /**
     * 에러 이벤트의 경우 발생한 예외 정보입니다.
     * onError() 이벤트가 아닌 경우 null입니다.
     */
    private final Throwable throwable;

    /**
     * AsyncContext만으로 AsyncEvent를 생성합니다.
     *
     * 가장 기본적인 형태의 이벤트 생성자입니다.
     * request, response, throwable은 모두 null로 설정됩니다.
     *
     * @param asyncContext 이벤트와 연관된 AsyncContext
     * @throws IllegalArgumentException asyncContext가 null인 경우
     */
    public AsyncEvent(AsyncContext asyncContext) {
        this(asyncContext, null, null, null);
    }

    /**
     * AsyncContext와 요청/응답 객체로 AsyncEvent를 생성합니다.
     *
     * AsyncListener가 특정 요청/응답 객체와 함께 등록된 경우 사용됩니다.
     * throwable은 null로 설정됩니다.
     *
     * @param asyncContext 이벤트와 연관된 AsyncContext
     * @param request 이벤트와 연관된 ServletRequest (null 가능)
     * @param response 이벤트와 연관된 ServletResponse (null 가능)
     * @throws IllegalArgumentException asyncContext가 null인 경우
     */
    public AsyncEvent(AsyncContext asyncContext, ServletRequest request, ServletResponse response) {
        this(asyncContext, request, response, null);
    }

    /**
     * AsyncContext와 예외 정보로 AsyncEvent를 생성합니다.
     *
     * 주로 onError() 이벤트에서 사용되는 생성자입니다.
     * request와 response는 null로 설정됩니다.
     *
     * @param asyncContext 이벤트와 연관된 AsyncContext
     * @param throwable 발생한 예외 (null 가능)
     * @throws IllegalArgumentException asyncContext가 null인 경우
     */
    public AsyncEvent(AsyncContext asyncContext, Throwable throwable) {
        this(asyncContext, null, null, throwable);
    }

    /**
     * 모든 정보를 포함하여 AsyncEvent를 생성합니다.
     *
     * 가장 완전한 형태의 이벤트 생성자입니다.
     * 모든 매개변수가 제공되며, asyncContext를 제외한 나머지는 null일 수 있습니다.
     *
     * @param asyncContext 이벤트와 연관된 AsyncContext (필수)
     * @param request 이벤트와 연관된 ServletRequest (null 가능)
     * @param response 이벤트와 연관된 ServletResponse (null 가능)
     * @param throwable 발생한 예외 (null 가능)
     * @throws IllegalArgumentException asyncContext가 null인 경우
     */
    public AsyncEvent(AsyncContext asyncContext, ServletRequest request,
                      ServletResponse response, Throwable throwable) {

        if (asyncContext == null) {
            throw new IllegalArgumentException("AsyncContext는 null일 수 없습니다");
        }

        this.asyncContext = asyncContext;
        this.request = request;
        this.response = response;
        this.throwable = throwable;
    }

    /**
     * 이벤트와 연관된 AsyncContext를 반환합니다.
     *
     * AsyncContext를 통해 비동기 처리를 제어하거나
     * 원본 요청/응답 객체에 접근할 수 있습니다.
     *
     * 사용 예시:
     * AsyncContext context = event.getAsyncContext();
     * ServletRequest originalRequest = context.getRequest();
     * context.complete(); // 비동기 처리 완료
     *
     * @return 이벤트와 연관된 AsyncContext (항상 null이 아님)
     */
    public AsyncContext getAsyncContext() {
        return asyncContext;
    }

    /**
     * 이벤트와 연관된 ServletRequest를 반환합니다.
     *
     * AsyncListener 등록 시 요청 객체가 제공된 경우에만
     * null이 아닌 값을 반환합니다.
     *
     * null이 반환되는 경우 getAsyncContext().getRequest()를 통해
     * 원본 요청 객체에 접근할 수 있습니다.
     *
     * @return 이벤트와 연관된 ServletRequest, 제공되지 않았으면 null
     */
    public ServletRequest getSuppliedRequest() {
        return request;
    }

    /**
     * 이벤트와 연관된 ServletResponse를 반환합니다.
     *
     * AsyncListener 등록 시 응답 객체가 제공된 경우에만
     * null이 아닌 값을 반환합니다.
     *
     * null이 반환되는 경우 getAsyncContext().getResponse()를 통해
     * 원본 응답 객체에 접근할 수 있습니다.
     *
     * @return 이벤트와 연관된 ServletResponse, 제공되지 않았으면 null
     */
    public ServletResponse getSuppliedResponse() {
        return response;
    }

    /**
     * 에러 이벤트의 경우 발생한 예외를 반환합니다.
     *
     * onError() 이벤트에서만 null이 아닌 값을 반환합니다.
     * 다른 이벤트 타입(onComplete, onTimeout, onStartAsync)에서는
     * 항상 null을 반환합니다.
     *
     * 사용 예시:
     * @Override
     * public void onError(AsyncEvent event) throws IOException {
     *     Throwable error = event.getThrowable();
     *     if (error != null) {
     *         logger.error("비동기 처리 중 오류 발생", error);
     *         // 오류 타입에 따른 처리
     *         if (error instanceof TimeoutException) {
     *             // 타임아웃 처리
     *         } else if (error instanceof IOException) {
     *             // I/O 오류 처리
     *         }
     *     }
     * }
     *
     * @return 발생한 예외, 에러 이벤트가 아니거나 예외가 없으면 null
     */
    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * AsyncEvent의 문자열 표현을 반환합니다.
     *
     * 디버깅과 로깅 목적으로 이벤트의 주요 정보를 포함한
     * 문자열을 반환합니다.
     *
     * @return AsyncEvent의 문자열 표현
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AsyncEvent{");
        sb.append("asyncContext=").append(asyncContext);
        sb.append(", hasSuppliedRequest=").append(request != null);
        sb.append(", hasSuppliedResponse=").append(response != null);
        sb.append(", hasThrowable=").append(throwable != null);
        if (throwable != null) {
            sb.append(", throwableType=").append(throwable.getClass().getSimpleName());
        }
        sb.append('}');
        return sb.toString();
    }
}
