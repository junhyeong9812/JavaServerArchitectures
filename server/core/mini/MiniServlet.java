package server.core.mini;

// HTTP 관련 클래스들
import server.core.http.*;

/**
 * 미니 서블릿 인터페이스
 * 핵심 생명주기 (init/service/destroy)
 *
 * 역할:
 * - 서블릿의 생명주기 관리 (초기화, 서비스, 종료)
 * - HTTP 메서드별 요청 라우팅
 * - 서블릿 컨텍스트와의 연동
 * - 서블릿 상태 관리
 *
 * 디자인 패턴:
 * - 템플릿 메서드 패턴: service() 메서드에서 전체 흐름 제어
 * - 훅 메서드 패턴: doXxx() 메서드들을 서브클래스에서 구현
 */
public abstract class MiniServlet {

    // 서블릿 컨텍스트 참조
    // 애플리케이션 수준의 정보에 접근하기 위함
    private MiniContext context;

    // 초기화 완료 여부를 나타내는 플래그
    // volatile: 멀티스레드 환경에서 변수 값의 가시성 보장
    // 한 스레드에서 변경한 값이 다른 스레드에서 즉시 보이도록 함
    private volatile boolean initialized = false;

    /**
     * 서블릿 초기화
     * 서버 시작시 한 번 호출됨
     *
     * 호출 시점: 서버 시작 시 또는 첫 번째 요청 시
     * 용도: 데이터베이스 연결, 설정 로드, 리소스 할당 등
     */
    public void init(MiniContext context) throws Exception {
        // 컨텍스트 참조 저장
        this.context = context;

        // 초기화 완료 플래그 설정
        this.initialized = true;

        // 사용자 정의 초기화 로직 실행
        // 서브클래스에서 오버라이드하여 구체적인 초기화 수행
        doInit();
    }

    /**
     * 사용자 정의 초기화 로직
     * 서브클래스에서 오버라이드
     *
     * 훅 메서드 패턴: 부모 클래스에서 호출 시점을 정의하고
     * 자식 클래스에서 구체적인 구현을 제공
     */
    protected void doInit() throws Exception {
        // 기본 구현은 비어있음
        // 서브클래스에서 필요에 따라 오버라이드
    }

    /**
     * 요청 처리 메인 메서드
     * 모든 HTTP 요청이 이 메서드를 통해 처리됨
     *
     * 템플릿 메서드 패턴: 전체적인 처리 흐름을 정의하고
     * 세부적인 처리는 doXxx() 메서드에 위임
     */
    public final HttpResponse service(MiniRequest request, MiniResponse response) throws Exception {
        // 초기화 상태 확인
        if (!initialized) {
            // 초기화되지 않은 서블릿은 사용할 수 없음
            // IllegalStateException: 객체의 상태가 메서드 호출에 적합하지 않을 때 발생
            throw new IllegalStateException("Servlet not initialized");
        }

        // HTTP 메서드별 라우팅
        // 요청의 HTTP 메서드에 따라 적절한 처리 메서드 호출
        HttpMethod method = request.getMethod();

        // switch 문: 열거형 값에 따른 분기 처리
        switch (method) {
            case GET:
                // GET 요청: 리소스 조회
                // doGet(): 서브클래스에서 구현하는 GET 처리 메서드
                doGet(request, response);
                break;
            case POST:
                // POST 요청: 새로운 리소스 생성
                doPost(request, response);
                break;
            case PUT:
                // PUT 요청: 리소스 전체 수정 또는 생성
                doPut(request, response);
                break;
            case DELETE:
                // DELETE 요청: 리소스 삭제
                doDelete(request, response);
                break;
            case HEAD:
                // HEAD 요청: GET과 동일하지만 본문 없음
                doHead(request, response);
                break;
            case OPTIONS:
                // OPTIONS 요청: 서버가 지원하는 메서드 확인
                doOptions(request, response);
                break;
            case PATCH:
                // PATCH 요청: 리소스 부분 수정
                doPatch(request, response);
                break;
            default:
                // 지원하지 않는 메서드에 대한 기본 처리
                // 405 Method Not Allowed 상태 코드 설정
                response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);

                // Allow 헤더: 지원하는 HTTP 메서드들을 클라이언트에게 알려줌
                // RFC 7231에 따르면 405 응답 시 Allow 헤더가 필수
                response.setHeader("Allow", "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH");
                break;
        }

        // MiniResponse를 HttpResponse로 변환하여 반환
        // build(): 모든 설정을 마무리하고 최종 응답 객체 생성
        return response.build();
    }

    /**
     * GET 요청 처리
     * 용도: 리소스 조회, 페이지 표시 등
     * 특징: 안전한 메서드 (서버 상태 변경 없음), 멱등성
     *
     * RFC 7231에 따르면 GET은 "safe" 메서드로 서버 상태를 변경하지 않아야 함
     */
    protected void doGet(MiniRequest request, MiniResponse response) throws Exception {
        // 기본 구현: 405 Method Not Allowed 응답
        // 서브클래스에서 오버라이드하지 않으면 GET을 지원하지 않음을 의미
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * POST 요청 처리
     * 용도: 새로운 리소스 생성, 폼 데이터 처리 등
     * 특징: 안전하지 않은 메서드 (서버 상태 변경), 멱등성 없음
     *
     * POST는 서버에 데이터를 제출하여 상태를 변경하는 메서드
     */
    protected void doPost(MiniRequest request, MiniResponse response) throws Exception {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * PUT 요청 처리
     * 용도: 리소스 전체 교체 또는 생성
     * 특징: 안전하지 않은 메서드, 멱등성 있음
     *
     * PUT은 지정된 리소스를 요청 본문의 데이터로 완전히 교체
     */
    protected void doPut(MiniRequest request, MiniResponse response) throws Exception {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * DELETE 요청 처리
     * 용도: 리소스 삭제
     * 특징: 안전하지 않은 메서드, 멱등성 있음
     *
     * DELETE는 지정된 리소스를 삭제 (여러 번 실행해도 결과 동일)
     */
    protected void doDelete(MiniRequest request, MiniResponse response) throws Exception {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * HEAD 요청 처리 (기본적으로 GET과 동일하지만 body 제외)
     * 용도: 리소스의 메타데이터만 확인 (파일 크기, 수정 시간 등)
     * 특징: GET과 동일한 헤더를 반환하지만 본문은 없음
     *
     * RFC 7231: HEAD 메서드는 GET과 동일한 응답 헤더를 반환해야 하지만
     * 메시지 본문은 전송하지 않음
     */
    protected void doHead(MiniRequest request, MiniResponse response) throws Exception {
        // 1. GET 메서드와 동일한 처리 수행
        // 이렇게 하면 GET과 동일한 상태 코드, 헤더들이 설정됨
        doGet(request, response);

        // 2. 응답 본문 제거 (HEAD는 헤더만 반환)
        // clearBody(): 응답 본문의 모든 내용을 제거
        // Content-Length는 유지되어야 함 (GET과 동일한 값)
        response.clearBody(); // HEAD는 body가 없음
    }

    /**
     * OPTIONS 요청 처리
     * 용도: 서버가 지원하는 HTTP 메서드 확인 (CORS 등)
     * 특징: 안전한 메서드, 멱등성
     *
     * CORS(Cross-Origin Resource Sharing)에서 preflight 요청으로 사용됨
     */
    protected void doOptions(MiniRequest request, MiniResponse response) throws Exception {
        // 200 OK 상태 설정
        response.setStatus(HttpStatus.OK);

        // Allow 헤더: 이 리소스에서 지원하는 HTTP 메서드들 명시
        // 클라이언트가 어떤 메서드를 사용할 수 있는지 알려줌
        response.setHeader("Allow", "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH");
    }

    /**
     * PATCH 요청 처리
     * 용도: 리소스의 부분적 수정
     * 특징: 안전하지 않은 메서드, 멱등성 없음 (일반적으로)
     *
     * PUT과 달리 PATCH는 리소스의 일부분만 수정
     * 예: 사용자의 이메일만 변경, 게시글의 제목만 수정
     */
    protected void doPatch(MiniRequest request, MiniResponse response) throws Exception {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * 서블릿 종료
     * 서버 종료시 호출됨
     *
     * 호출 시점: 서버 종료 시 또는 서블릿 언로드 시
     * 용도: 리소스 정리, 연결 해제, 임시 파일 삭제 등
     */
    public void destroy() {
        // 사용자 정의 종료 로직 실행
        // 서브클래스에서 구현한 정리 작업 수행
        doDestroy();

        // 초기화 상태 해제
        // 서블릿이 더 이상 사용할 수 없음을 표시
        initialized = false;

        // 컨텍스트 참조 해제 (가비지 컬렉션 도움)
        // null로 설정하여 메모리 누수 방지
        context = null;
    }

    /**
     * 사용자 정의 종료 로직
     * 서브클래스에서 오버라이드
     *
     * 훅 메서드 패턴: 정리 작업의 구체적인 구현을 서브클래스에서 제공
     *
     * 일반적인 정리 작업:
     * - 데이터베이스 연결 해제
     * - 파일 핸들 닫기
     * - 스레드 풀 종료
     * - 임시 파일 삭제
     */
    protected void doDestroy() {
        // 기본 구현은 비어있음
        // 서브클래스에서 필요에 따라 오버라이드
    }

    /**
     * 서블릿 컨텍스트 반환
     * 서브클래스에서 애플리케이션 수준의 정보에 접근할 때 사용
     *
     * 사용 예시:
     * - 초기화 파라미터 읽기: getContext().getInitParameter("db.url")
     * - 애플리케이션 속성 접근: getContext().getAttribute("dataSource")
     * - 로그 출력: getContext().log("Processing request")
     */
    protected MiniContext getContext() {
        return context;
    }

    /**
     * 초기화 여부 확인
     * 서블릿이 사용 가능한 상태인지 확인
     *
     * 반환값:
     * - true: 서블릿이 초기화되어 요청 처리 가능
     * - false: 서블릿이 아직 초기화되지 않았거나 이미 종료됨
     */
    public boolean isInitialized() {
        return initialized;
    }
}