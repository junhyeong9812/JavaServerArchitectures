package jakarta.servlet; // 패키지 선언 - Jakarta EE 서블릿 API 패키지, 서블릿과 필터 관련 인터페이스들이 포함

import java.io.IOException; // import 선언 - 입출력 예외 클래스, 네트워크나 파일 I/O 중 발생할 수 있는 체크 예외

/**
 * 서블릿 요청 및 응답을 필터링하는 인터페이스입니다.
 *
 * Filter는 서블릿 실행 전후에 요청과 응답을 가로채서
 * 인증, 로깅, 데이터 변환 등의 작업을 수행할 수 있습니다.
 *
 * 필터는 체인 패턴으로 여러 개가 순차적으로 실행되며,
 * 각 필터는 다음 필터나 서블릿으로 요청을 전달할지 결정합니다.
 *
 * 필터 실행 순서:
 * 1. init() - 필터 초기화 (한 번만 실행)
 * 2. doFilter() - 요청 처리 (요청마다 실행)
 * 3. destroy() - 필터 정리 (종료 시 한 번만 실행)
 */
public interface Filter { // public interface 선언 - 모든 클래스에서 구현 가능한 서블릿 필터 인터페이스

    /**
     * 필터를 초기화합니다.
     *
     * 이 메서드는 컨테이너가 필터를 서비스에 투입하기 전에 한 번만 호출됩니다.
     * 필터가 초기화 작업을 수행할 수 있는 기회를 제공합니다.
     *
     * 초기화 작업 예시:
     * - 필터 설정 매개변수 읽기
     * - 외부 리소스 연결 설정
     * - 로깅 시스템 초기화
     * - 캐시 초기화
     *
     * @param filterConfig 필터 설정 정보를 담은 FilterConfig 객체
     * @throws ServletException 초기화 실패 시 발생
     */
    default void init(FilterConfig filterConfig) throws ServletException { // default 메서드 - Java 8부터 지원, 인터페이스에서 기본 구현 제공, ServletException 던질 수 있음
        // 기본 구현은 아무것도 하지 않음
        // 필터 구현체에서 필요시 오버라이드
    } // 메서드 본문 - 빈 구현, 필터 구현 클래스에서 필요에 따라 오버라이드

    /**
     * 필터링 로직을 수행합니다.
     *
     * 이 메서드는 클라이언트 요청이 필터의 URL 패턴과 매칭될 때마다 호출됩니다.
     * 요청과 응답을 필터링하고, 다음 필터나 서블릿으로 제어를 넘길지 결정합니다.
     *
     * 주요 작업들:
     * - 요청 검증 및 인증
     * - 요청/응답 데이터 변환
     * - 로깅 및 모니터링
     * - 캐시 처리
     * - 압축 처리
     * - 보안 검사
     *
     * 중요: chain.doFilter()를 호출해야 다음 필터나 서블릿이 실행됩니다.
     * 호출하지 않으면 요청 처리가 중단됩니다.
     *
     * @param request 클라이언트의 요청을 나타내는 ServletRequest 객체
     * @param response 클라이언트에게 보낼 응답을 나타내는 ServletResponse 객체
     * @param chain 다음 필터나 서블릿을 호출하기 위한 FilterChain 객체
     * @throws IOException 입출력 오류가 발생한 경우
     * @throws ServletException 서블릿 처리 중 오류가 발생한 경우
     */
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) // abstract 메서드 - interface의 메서드는 기본적으로 public abstract, 필터 핵심 로직
            throws IOException, ServletException; // throws 절 - 두 가지 체크 예외를 던질 수 있음을 선언

    /**
     * 필터를 정리합니다.
     *
     * 이 메서드는 컨테이너가 필터를 서비스에서 제거하기 전에 한 번만 호출됩니다.
     * 필터가 사용한 리소스를 정리할 수 있는 기회를 제공합니다.
     *
     * 정리 작업 예시:
     * - 데이터베이스 연결 해제
     * - 파일 핸들 닫기
     * - 스레드 풀 종료
     * - 캐시 정리
     * - 임시 파일 삭제
     */
    default void destroy() { // default 메서드 - 기본 구현 제공, 예외 선언 없음 (정리 작업은 예외를 던지지 않는 것이 일반적)
        // 기본 구현은 아무것도 하지 않음
        // 필터 구현체에서 필요시 오버라이드
    } // 메서드 본문 - 빈 구현, 리소스 정리가 필요한 필터에서 오버라이드
}