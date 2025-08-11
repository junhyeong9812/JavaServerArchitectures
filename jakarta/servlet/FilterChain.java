package jakarta.servlet;

import java.io.IOException;

/**
 * 필터 체인을 나타내는 인터페이스입니다.
 *
 * FilterChain은 여러 필터들이 순차적으로 실행되는 체인 패턴을 구현합니다.
 * 각 필터는 이 인터페이스를 통해 다음 필터나 최종 서블릿으로 요청을 전달합니다.
 *
 * 체인 동작 방식:
 * 1. 첫 번째 필터가 doFilter() 호출
 * 2. 각 필터에서 전처리 수행
 * 3. chain.doFilter() 호출로 다음 단계 진행
 * 4. 모든 필터 통과 후 서블릿 실행
 * 5. 응답 시 역순으로 필터들의 후처리 수행
 *
 * 예시 실행 순서:
 * Request -> Filter1 -> Filter2 -> Filter3 -> Servlet
 * Response <- Filter1 <- Filter2 <- Filter3 <- Servlet
 */
public interface FilterChain {

    /**
     * 필터 체인의 다음 단계를 실행합니다.
     *
     * 이 메서드는 현재 필터에서 다음 필터나 서블릿으로 요청을 전달하기 위해 호출됩니다.
     *
     * 실행 흐름:
     * 1. 현재 필터의 전처리 작업 수행
     * 2. doFilter() 호출로 다음 단계 진행
     * 3. 다음 단계 완료 후 현재 필터의 후처리 작업 수행
     *
     * 주의사항:
     * - 필터에서 이 메서드를 호출하지 않으면 요청 처리가 중단됩니다
     * - 한 번만 호출해야 합니다 (중복 호출 시 예외 발생 가능)
     * - 호출 전후로 요청/응답 객체를 수정할 수 있습니다
     *
     * 사용 예시:
     * <pre>
     * {@code
     * public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
     *         throws IOException, ServletException {
     *
     *     // 전처리 작업
     *     System.out.println("요청 전처리: " + request.getRemoteAddr());
     *
     *     // 다음 필터나 서블릿으로 진행
     *     chain.doFilter(request, response);
     *
     *     // 후처리 작업
     *     System.out.println("응답 후처리 완료");
     * }
     * }
     * </pre>
     *
     * @param request 클라이언트의 요청을 나타내는 ServletRequest 객체
     *                필터에서 수정된 요청 객체가 전달될 수 있습니다
     * @param response 클라이언트에게 보낼 응답을 나타내는 ServletResponse 객체
     *                 필터에서 수정된 응답 객체가 전달될 수 있습니다
     * @throws IOException 입출력 오류가 발생한 경우
     *                     예: 네트워크 연결 오류, 파일 읽기/쓰기 오류
     * @throws ServletException 서블릿 처리 중 오류가 발생한 경우
     *                          예: 설정 오류, 필터 초기화 실패, 서블릿 실행 오류
     * @throws IllegalStateException 체인이 이미 완료된 상태에서 다시 호출된 경우
     */
    void doFilter(ServletRequest request, ServletResponse response)
            throws IOException, ServletException;
}