package server.core.mini;

import server.core.http.*;

/**
 * 미니 서블릿 인터페이스
 * 핵심 생명주기 (init/service/destroy)
 */
public abstract class MiniServlet {

    private MiniContext context;
    private volatile boolean initialized = false;

    /**
     * 서블릿 초기화
     * 서버 시작시 한 번 호출됨
     */
    public void init(MiniContext context) throws Exception {
        this.context = context;
        this.initialized = true;
        doInit();
    }

    /**
     * 사용자 정의 초기화 로직
     * 서브클래스에서 오버라이드
     */
    protected void doInit() throws Exception {
        // 기본 구현은 비어있음
    }

    /**
     * 요청 처리 메인 메서드
     */
    public final HttpResponse service(MiniRequest request, MiniResponse response) throws Exception {
        if (!initialized) {
            throw new IllegalStateException("Servlet not initialized");
        }

        // HTTP 메서드별 라우팅
        HttpMethod method = request.getMethod();
        switch (method) {
            case GET:
                doGet(request, response);
                break;
            case POST:
                doPost(request, response);
                break;
            case PUT:
                doPut(request, response);
                break;
            case DELETE:
                doDelete(request, response);
                break;
            case HEAD:
                doHead(request, response);
                break;
            case OPTIONS:
                doOptions(request, response);
                break;
            case PATCH:
                doPatch(request, response);
                break;
            default:
                response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
                response.setHeader("Allow", "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH");
                break;
        }

        return response.build();
    }

    /**
     * GET 요청 처리
     */
    protected void doGet(MiniRequest request, MiniResponse response) throws Exception {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * POST 요청 처리
     */
    protected void doPost(MiniRequest request, MiniResponse response) throws Exception {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * PUT 요청 처리
     */
    protected void doPut(MiniRequest request, MiniResponse response) throws Exception {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * DELETE 요청 처리
     */
    protected void doDelete(MiniRequest request, MiniResponse response) throws Exception {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * HEAD 요청 처리 (기본적으로 GET과 동일하지만 body 제외)
     */
    protected void doHead(MiniRequest request, MiniResponse response) throws Exception {
        doGet(request, response);
        response.clearBody(); // HEAD는 body가 없음
    }

    /**
     * OPTIONS 요청 처리
     */
    protected void doOptions(MiniRequest request, MiniResponse response) throws Exception {
        response.setStatus(HttpStatus.OK);
        response.setHeader("Allow", "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH");
    }

    /**
     * PATCH 요청 처리
     */
    protected void doPatch(MiniRequest request, MiniResponse response) throws Exception {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * 서블릿 종료
     * 서버 종료시 호출됨
     */
    public void destroy() {
        doDestroy();
        initialized = false;
        context = null;
    }

    /**
     * 사용자 정의 종료 로직
     * 서브클래스에서 오버라이드
     */
    protected void doDestroy() {
        // 기본 구현은 비어있음
    }

    /**
     * 서블릿 컨텍스트 반환
     */
    protected MiniContext getContext() {
        return context;
    }

    /**
     * 초기화 여부 확인
     */
    public boolean isInitialized() {
        return initialized;
    }
}