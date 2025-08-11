package jakarta.servlet;

import java.io.IOException;

/**
 * 비동기 요청 처리의 생명주기 이벤트를 처리하는 리스너 인터페이스입니다.
 *
 * AsyncListener는 비동기 요청 처리 중에 발생하는 다양한 이벤트들을
 * 모니터링하고 대응할 수 있게 해줍니다.
 *
 * 처리 가능한 이벤트들:
 * - onComplete: 비동기 작업이 정상적으로 완료됨
 * - onTimeout: 비동기 작업이 타임아웃됨
 * - onError: 비동기 작업 중 오류 발생
 * - onStartAsync: 새로운 비동기 작업이 시작됨
 *
 * 사용 사례:
 * - 리소스 정리 (연결, 파일 핸들 등)
 * - 로깅 및 모니터링
 * - 에러 처리 및 복구
 * - 성능 측정
 */
public interface AsyncListener {

    /**
     * 비동기 작업이 성공적으로 완료되었을 때 호출됩니다.
     *
     * AsyncContext.complete()가 호출되거나 AsyncContext.dispatch()를 통해
     * 디스패치된 요청 처리가 완료되었을 때 발생합니다.
     *
     * 이 메서드에서는 다음과 같은 정리 작업을 수행할 수 있습니다:
     * - 사용된 리소스 해제
     * - 로깅 및 통계 업데이트
     * - 후속 작업 트리거
     *
     * 주의사항:
     * - 이 메서드 실행 중 예외가 발생해도 응답에는 영향을 주지 않습니다
     * - 응답이 이미 커밋된 상태이므로 응답 내용을 변경할 수 없습니다
     *
     * 사용 예시:
     * @Override
     * public void onComplete(AsyncEvent event) throws IOException {
     *     // 작업 완료 로깅
     *     logger.info("비동기 요청 처리 완료: " + event.getAsyncContext().getRequest().getRequestURI());
     *
     *     // 리소스 정리
     *     closeConnections();
     *
     *     // 통계 업데이트
     *     updateMetrics();
     * }
     *
     * @param event 비동기 이벤트 정보
     * @throws IOException 입출력 오류 시
     */
    void onComplete(AsyncEvent event) throws IOException;

    /**
     * 비동기 작업이 타임아웃되었을 때 호출됩니다.
     *
     * AsyncContext.setTimeout()으로 설정된 시간 내에 작업이 완료되지 않을 때 발생합니다.
     * 타임아웃 처리 후에는 반드시 AsyncContext.complete()를 호출해야 합니다.
     *
     * 타임아웃 처리 전략:
     * - 기본 응답 전송 (예: "요청 처리 시간 초과")
     * - 부분 결과 반환
     * - 재시도 안내
     * - 로깅 및 알림
     *
     * 사용 예시:
     * @Override
     * public void onTimeout(AsyncEvent event) throws IOException {
     *     logger.warn("비동기 요청 타임아웃: " + event.getAsyncContext().getRequest().getRequestURI());
     *
     *     AsyncContext asyncContext = event.getAsyncContext();
     *     ServletResponse response = asyncContext.getResponse();
     *
     *     // 타임아웃 응답 전송
     *     response.setStatus(HttpServletResponse.SC_REQUEST_TIMEOUT);
     *     response.setContentType("application/json");
     *     response.getWriter().write("{\"error\": \"Request timeout\"}");
     *
     *     // 비동기 처리 완료
     *     asyncContext.complete();
     * }
     *
     * @param event 비동기 이벤트 정보
     * @throws IOException 입출력 오류 시
     */
    void onTimeout(AsyncEvent event) throws IOException;

    /**
     * 비동기 작업 중 오류가 발생했을 때 호출됩니다.
     *
     * 비동기 처리 중에 예외가 발생하거나 네트워크 오류 등이 발생할 때 호출됩니다.
     * 오류 처리 후에는 AsyncContext.complete()를 호출하여 처리를 완료해야 합니다.
     *
     * 오류 처리 전략:
     * - 적절한 HTTP 상태 코드 설정
     * - 에러 메시지 또는 페이지 전송
     * - 로깅 및 모니터링 시스템에 알림
     * - 대체 응답 제공
     *
     * 사용 예시:
     * @Override
     * public void onError(AsyncEvent event) throws IOException {
     *     Throwable throwable = event.getThrowable();
     *     logger.error("비동기 요청 처리 중 오류 발생", throwable);
     *
     *     AsyncContext asyncContext = event.getAsyncContext();
     *     ServletResponse response = asyncContext.getResponse();
     *
     *     // 응답이 아직 커밋되지 않은 경우에만 에러 응답 전송
     *     if (!response.isCommitted()) {
     *         response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
     *         response.setContentType("text/html");
     *         response.getWriter().write("<h1>Internal Server Error</h1>");
     *     }
     *
     *     asyncContext.complete();
     * }
     *
     * @param event 비동기 이벤트 정보 (getThrowable()로 예외 정보 확인 가능)
     * @throws IOException 입출력 오류 시
     */
    void onError(AsyncEvent event) throws IOException;

    /**
     * 새로운 비동기 작업이 시작되었을 때 호출됩니다.
     *
     * ServletRequest.startAsync()가 호출되어 새로운 비동기 컨텍스트가
     * 생성될 때 발생합니다. 여러 단계의 비동기 처리가 있을 때
     * 각 단계의 시작을 추적할 수 있습니다.
     *
     * 사용 사례:
     * - 비동기 처리 시작 시점 로깅
     * - 성능 측정 시작
     * - 초기 설정 작업
     * - 모니터링 시스템에 시작 이벤트 전송
     *
     * 사용 예시:
     * @Override
     * public void onStartAsync(AsyncEvent event) throws IOException {
     *     logger.info("새로운 비동기 작업 시작: " +
     *                event.getAsyncContext().getRequest().getRequestURI());
     *
     *     // 성능 측정 시작
     *     long startTime = System.currentTimeMillis();
     *     event.getAsyncContext().getRequest().setAttribute("startTime", startTime);
     *
     *     // 처리 통계 업데이트
     *     incrementAsyncRequestCounter();
     * }
     *
     * @param event 비동기 이벤트 정보
     * @throws IOException 입출력 오류 시
     */
    void onStartAsync(AsyncEvent event) throws IOException;
}
