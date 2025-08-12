package jakarta.servlet; // 패키지 선언 - Jakarta EE 서블릿 API 패키지, 비동기 처리 관련 클래스들이 포함된 패키지

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
public class AsyncEvent { // public class 선언 - 모든 패키지에서 접근 가능한 비동기 이벤트 정보 클래스

    /**
     * 이벤트와 연관된 AsyncContext 객체입니다.
     */
    private final AsyncContext asyncContext; // private final 필드 - 외부 접근 불가, 불변 참조, 비동기 컨텍스트 저장

    /**
     * 이벤트와 연관된 ServletRequest 객체입니다.
     * AsyncListener 등록 시 제공된 경우에만 설정됩니다.
     */
    private final ServletRequest request; // private final 필드 - 불변 참조, 요청 객체 저장, null 가능

    /**
     * 이벤트와 연관된 ServletResponse 객체입니다.
     * AsyncListener 등록 시 제공된 경우에만 설정됩니다.
     */
    private final ServletResponse response; // private final 필드 - 불변 참조, 응답 객체 저장, null 가능

    /**
     * 에러 이벤트의 경우 발생한 예외 정보입니다.
     * onError() 이벤트가 아닌 경우 null입니다.
     */
    private final Throwable throwable; // private final 필드 - 불변 참조, 예외 객체 저장, 에러 이벤트가 아니면 null

    /**
     * AsyncContext만으로 AsyncEvent를 생성합니다.
     *
     * 가장 기본적인 형태의 이벤트 생성자입니다.
     * request, response, throwable은 모두 null로 설정됩니다.
     *
     * @param asyncContext 이벤트와 연관된 AsyncContext
     * @throws IllegalArgumentException asyncContext가 null인 경우
     */
    public AsyncEvent(AsyncContext asyncContext) { // public 생성자 - 외부에서 호출 가능, AsyncContext만 받는 기본 생성자
        this(asyncContext, null, null, null); // this() - 같은 클래스의 다른 생성자 호출, 나머지 매개변수는 null로 전달
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
    public AsyncEvent(AsyncContext asyncContext, ServletRequest request, ServletResponse response) { // public 생성자 - 세 개의 매개변수를 받는 생성자
        this(asyncContext, request, response, null); // this() - 네 번째 매개변수(throwable)는 null로 전달하여 완전한 생성자 호출
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
    public AsyncEvent(AsyncContext asyncContext, Throwable throwable) { // public 생성자 - AsyncContext와 Throwable을 받는 에러 전용 생성자
        this(asyncContext, null, null, throwable); // this() - request, response는 null로, throwable만 전달하여 완전한 생성자 호출
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
    public AsyncEvent(AsyncContext asyncContext, ServletRequest request, // public 생성자 - 모든 매개변수를 받는 완전한 생성자
                      ServletResponse response, Throwable throwable) { // 매개변수 선언 - ServletResponse와 Throwable 매개변수

        if (asyncContext == null) { // 조건문 - asyncContext의 null 체크, == 연산자로 null과 비교
            throw new IllegalArgumentException("AsyncContext는 null일 수 없습니다"); // IllegalArgumentException 던지기 - 잘못된 인수 예외, 문자열 메시지 포함
        }

        this.asyncContext = asyncContext; // this.asyncContext - 현재 객체의 asyncContext 필드에 매개변수 값 할당
        this.request = request; // this.request - 현재 객체의 request 필드에 매개변수 값 할당, null 가능
        this.response = response; // this.response - 현재 객체의 response 필드에 매개변수 값 할당, null 가능
        this.throwable = throwable; // this.throwable - 현재 객체의 throwable 필드에 매개변수 값 할당, null 가능
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
    public AsyncContext getAsyncContext() { // public getter 메서드 - 외부에서 AsyncContext 조회 가능
        return asyncContext; // return 문 - asyncContext 필드 값 반환, final 필드이므로 불변 객체 안전하게 반환
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
    public ServletRequest getSuppliedRequest() { // public getter 메서드 - "Supplied"가 붙은 이유는 리스너 등록 시 제공된 객체임을 명시
        return request; // return 문 - request 필드 값 반환, null일 수 있음
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
    public ServletResponse getSuppliedResponse() { // public getter 메서드 - "Supplied"가 붙은 이유는 리스너 등록 시 제공된 객체임을 명시
        return response; // return 문 - response 필드 값 반환, null일 수 있음
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
    public Throwable getThrowable() { // public getter 메서드 - Throwable 반환형, 예외 정보 조회
        return throwable; // return 문 - throwable 필드 값 반환, 에러 이벤트가 아니면 null
    }

    /**
     * AsyncEvent의 문자열 표현을 반환합니다.
     *
     * 디버깅과 로깅 목적으로 이벤트의 주요 정보를 포함한
     * 문자열을 반환합니다.
     *
     * @return AsyncEvent의 문자열 표현
     */
    @Override // 어노테이션 - Object.toString() 메서드 오버라이드임을 명시
    public String toString() { // public 메서드 - Object 클래스의 toString() 메서드 재정의
        StringBuilder sb = new StringBuilder(); // StringBuilder 생성 - 효율적인 문자열 조합을 위한 가변 문자열 클래스
        sb.append("AsyncEvent{"); // StringBuilder.append() - 문자열 추가, 객체 정보 시작 부분
        sb.append("asyncContext=").append(asyncContext); // 메서드 체이닝 - append() 메서드 연속 호출, asyncContext 객체 정보 추가
        sb.append(", hasSuppliedRequest=").append(request != null); // != 연산자 - request가 null이 아닌지 확인, boolean 결과를 문자열로 추가
        sb.append(", hasSuppliedResponse=").append(response != null); // != 연산자 - response가 null이 아닌지 확인
        sb.append(", hasThrowable=").append(throwable != null); // != 연산자 - throwable이 null이 아닌지 확인
        if (throwable != null) { // 조건문 - throwable이 null이 아닌 경우에만 실행
            sb.append(", throwableType=").append(throwable.getClass().getSimpleName()); // throwable.getClass() - 예외 객체의 클래스 타입 조회, getSimpleName() - 간단한 클래스명만 반환
        }
        sb.append('}'); // 객체 정보 종료 부분 추가
        return sb.toString(); // StringBuilder.toString() - 최종 문자열 생성하여 반환
    }
}