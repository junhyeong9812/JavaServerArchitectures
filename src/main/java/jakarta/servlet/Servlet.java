package src.main.java.jakarta.servlet;

// Jakarta Servlet API - Servlet Interface
// 실제 프로젝트에서는 src/main/java/jakarta/servlet/Servlet.java 위치에 배치

/**
 * 모든 서블릿이 구현해야 하는 기본 인터페이스입니다.
 *
 * 서블릿은 웹 서버에서 실행되는 자바 프로그램으로, 클라이언트의 요청을 받아 처리하고
 * 응답을 생성하는 역할을 합니다. 이 인터페이스는 서블릿의 생명주기를 정의합니다.
 *
 * 서블릿 생명주기:
 * 1. init() - 서블릿 초기화 (한 번만 호출)
 * 2. service() - 클라이언트 요청 처리 (요청마다 호출)
 * 3. destroy() - 서블릿 소멸 (한 번만 호출)
 *
 * @author JavaServerArchitectures Project
 * @version 1.0
 */
public interface Servlet {

    /**
     * 서블릿을 초기화합니다.
     *
     * 서블릿 컨테이너는 서블릿을 로드한 후 정확히 한 번 이 메서드를 호출합니다.
     * 서블릿이 요청을 받기 전에 초기화 작업을 수행할 수 있습니다.
     *
     * 이 메서드에서는 다음과 같은 작업을 수행할 수 있습니다:
     * - 데이터베이스 연결 설정
     * - 설정 파일 읽기
     * - 필요한 리소스 초기화
     * - 백그라운드 스레드 시작
     *
     * @param config 서블릿 설정 정보를 담은 ServletConfig 객체
     * @throws ServletException 초기화 중 오류가 발생한 경우
     */
    void init(ServletConfig config) throws ServletException;

    /**
     * 클라이언트의 요청을 처리합니다.
     *
     * 서블릿 컨테이너는 클라이언트로부터 요청을 받을 때마다 이 메서드를 호출합니다.
     * 이 메서드는 멀티스레드 환경에서 동시에 실행될 수 있으므로 스레드 안전성을 고려해야 합니다.
     *
     * 일반적으로 이 메서드에서는:
     * - 요청 데이터 파싱
     * - 비즈니스 로직 실행
     * - 응답 데이터 생성
     * - 클라이언트에게 응답 전송
     *
     * @param req 클라이언트 요청 정보를 담은 ServletRequest 객체
     * @param res 클라이언트 응답을 생성하기 위한 ServletResponse 객체
     * @throws ServletException 요청 처리 중 서블릿 관련 오류가 발생한 경우
     * @throws java.io.IOException I/O 오류가 발생한 경우
     */
    void service(ServletRequest req, ServletResponse res)
            throws ServletException, java.io.IOException;

    /**
     * 서블릿을 소멸시킵니다.
     *
     * 서블릿 컨테이너가 서블릿을 메모리에서 제거하기 전에 이 메서드를 호출합니다.
     * 서블릿이 사용한 리소스를 정리하는 작업을 수행할 수 있습니다.
     *
     * 이 메서드에서는 다음과 같은 정리 작업을 수행합니다:
     * - 데이터베이스 연결 종료
     * - 파일 핸들 닫기
     * - 백그라운드 스레드 종료
     * - 캐시 정리
     *
     * 이 메서드가 완료된 후 서블릿 인스턴스는 가비지 컬렉션 대상이 됩니다.
     */
    void destroy();

    /**
     * 서블릿 설정 정보를 반환합니다.
     *
     * @return 이 서블릿의 ServletConfig 객체
     */
    ServletConfig getServletConfig();

    /**
     * 서블릿에 대한 정보를 반환합니다.
     *
     * 이 메서드는 서블릿의 작성자, 버전, 저작권 정보 등을 포함하는
     * 문자열을 반환합니다. 반환되는 문자열은 일반 텍스트여야 하며
     * 마크업 언어(HTML, XML 등)는 포함되어서는 안 됩니다.
     *
     * @return 서블릿 정보를 담은 문자열, 또는 null
     */
    String getServletInfo();
}