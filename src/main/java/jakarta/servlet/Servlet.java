package src.main.java.jakarta.servlet;

import java.io.IOException;

/**
 * 서블릿의 기본 인터페이스입니다.
 * 모든 서블릿은 이 인터페이스를 구현해야 하며, 서블릿의 생명주기를 정의합니다.
 * 
 * 서블릿 생명주기:
 * 1. init() - 서블릿 초기화 (서버 시작 시 또는 첫 요청 시 한 번만 호출)
 * 2. service() - 요청 처리 (각 요청마다 호출)
 * 3. destroy() - 서블릿 종료 (서버 종료 시 또는 서블릿 언로드 시 한 번만 호출)
 */
public interface Servlet {
    
    /**
     * 서블릿 초기화 메서드입니다.
     * 
     * 서블릿 컨테이너가 서블릿을 인스턴스화한 후 서비스에 투입하기 전에 정확히 한 번 호출됩니다.
     * 이 메서드에서는 다음과 같은 초기화 작업을 수행합니다:
     * - 데이터베이스 연결 설정
     * - 설정 파일 읽기
     * - 초기화 매개변수 처리
     * - 필요한 리소스 할당
     * 
     * init() 메서드가 성공적으로 완료되어야만 서블릿이 요청을 처리할 수 있습니다.
     * 
     * @param config 서블릿의 설정 정보를 담고 있는 ServletConfig 객체
     * @throws ServletException 초기화 중 오류가 발생한 경우
     */
    void init(ServletConfig config) throws ServletException;
    
    /**
     * 클라이언트 요청을 처리하는 메서드입니다.
     * 
     * 서블릿 컨테이너가 각 요청에 대해 이 메서드를 호출합니다.
     * 이 메서드는 멀티스레드 환경에서 동시에 여러 번 호출될 수 있으므로,
     * 스레드 안전성을 고려해야 합니다.
     * 
     * 일반적으로 이 메서드에서는:
     * - 요청 타입 확인 (GET, POST, PUT, DELETE 등)
     * - 요청 매개변수 추출
     * - 비즈니스 로직 실행
     * - 응답 생성
     * 
     * @param req 클라이언트 요청 정보를 담고 있는 ServletRequest 객체
     * @param res 클라이언트에게 보낼 응답을 구성하는 ServletResponse 객체
     * @throws ServletException 서블릿 관련 오류가 발생한 경우
     * @throws IOException 입출력 오류가 발생한 경우
     */
    void service(ServletRequest req, ServletResponse res) 
            throws ServletException, IOException;
    
    /**
     * 서블릿 종료 메서드입니다.
     * 
     * 서블릿 컨테이너가 서블릿을 서비스에서 제거하기 전에 정확히 한 번 호출됩니다.
     * 이 메서드에서는 다음과 같은 정리 작업을 수행합니다:
     * - 데이터베이스 연결 해제
     * - 파일 핸들 닫기
     * - 백그라운드 스레드 정리
     * - 할당된 리소스 해제
     * 
     * destroy() 호출 후에는 서블릿이 더 이상 요청을 처리하지 않습니다.
     */
    void destroy();
    
    /**
     * 서블릿의 설정 정보를 반환합니다.
     * 
     * 이 메서드는 init() 메서드에서 전달받은 ServletConfig 객체를 반환해야 합니다.
     * ServletConfig를 통해 다음 정보에 접근할 수 있습니다:
     * - 서블릿 이름
     * - 초기화 매개변수
     * - ServletContext (웹 애플리케이션 전체 컨텍스트)
     * 
     * @return 서블릿의 ServletConfig 객체
     */
    ServletConfig getServletConfig();
    
    /**
     * 서블릿에 대한 정보를 반환합니다.
     * 
     * 서블릿에 대한 유용한 정보를 문자열로 반환합니다.
     * 예: 작성자, 버전, 저작권 정보 등
     * 
     * 이 정보는 관리 도구나 로깅에서 사용될 수 있습니다.
     * 
     * @return 서블릿에 대한 설명 문자열
     */
    String getServletInfo();
}