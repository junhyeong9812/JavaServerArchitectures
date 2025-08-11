package jakarta.servlet.http;

import jakarta.servlet.GenericServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;

/**
 * HTTP 프로토콜을 처리하는 서블릿을 위한 추상 클래스입니다.
 *
 * HttpServlet은 GenericServlet을 확장하여 HTTP 프로토콜에 특화된 기능을 제공합니다.
 * 대부분의 웹 애플리케이션에서 이 클래스를 상속받아 서블릿을 작성합니다.
 *
 * 주요 기능:
 * - HTTP 메서드별 전용 처리 메서드 (doGet, doPost, doPut, doDelete 등)
 * - HTTP 헤더 및 상태 코드 처리
 * - 세션 관리 지원
 * - 쿠키 처리 지원
 *
 * 개발자는 처리하고자 하는 HTTP 메서드에 따라 해당 메서드를 오버라이드합니다:
 * - GET 요청: doGet() 메서드
 * - POST 요청: doPost() 메서드
 * - PUT 요청: doPut() 메서드
 * - DELETE 요청: doDelete() 메서드
 */
public abstract class HttpServlet extends GenericServlet {

    /**
     * 직렬화를 위한 버전 ID입니다.
     */
    private static final long serialVersionUID = 1L;

    /**
     * HTTP 메서드 상수들 - 표준 HTTP 메서드를 정의합니다.
     */
    private static final String METHOD_DELETE = "DELETE";
    private static final String METHOD_HEAD = "HEAD";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_OPTIONS = "OPTIONS";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_TRACE = "TRACE";
    private static final String METHOD_PATCH = "PATCH";

    /**
     * HTTP 에러 메시지 상수들
     */
    private static final String LSTRING_FILE = "jakarta.servlet.http.LocalStrings";
    private static final String HTTP_METHOD_GET_NOT_SUPPORTED = "HTTP method GET is not supported by this URL";
    private static final String HTTP_METHOD_POST_NOT_SUPPORTED = "HTTP method POST is not supported by this URL";
    private static final String HTTP_METHOD_PUT_NOT_SUPPORTED = "HTTP method PUT is not supported by this URL";
    private static final String HTTP_METHOD_DELETE_NOT_SUPPORTED = "HTTP method DELETE is not supported by this URL";

    /**
     * 기본 생성자입니다.
     *
     * 서블릿 컨테이너가 HttpServlet 인스턴스를 생성할 때 호출됩니다.
     */
    public HttpServlet() {
        super();
    }

    /**
     * HTTP GET 요청을 처리합니다.
     *
     * GET 메서드는 서버로부터 정보를 조회할 때 사용됩니다.
     * 멱등성(idempotent)을 가지며, 서버 상태를 변경하지 않아야 합니다.
     *
     * 일반적인 사용 사례:
     * - 웹 페이지 조회
     * - 데이터 검색
     * - 파일 다운로드
     * - API에서 리소스 조회
     *
     * 기본 구현은 405 (Method Not Allowed) 에러를 반환합니다.
     * 하위 클래스에서 이 메서드를 오버라이드하여 GET 요청을 처리해야 합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String protocol = req.getProtocol();
        String message = HTTP_METHOD_GET_NOT_SUPPORTED;

        // HTTP/1.1 이상에서는 405 Method Not Allowed 응답
        if (protocol.endsWith("1.1")) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message);
        } else {
            // HTTP/1.0에서는 400 Bad Request 응답
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
        }
    }

    /**
     * HTTP POST 요청을 처리합니다.
     *
     * POST 메서드는 서버에 데이터를 전송하여 리소스를 생성하거나 수정할 때 사용됩니다.
     * 멱등성을 갖지 않으며, 서버 상태를 변경할 수 있습니다.
     *
     * 일반적인 사용 사례:
     * - 폼 데이터 제출
     * - 파일 업로드
     * - 새 리소스 생성
     * - 로그인 처리
     * - API에서 데이터 생성/수정
     *
     * 기본 구현은 405 (Method Not Allowed) 에러를 반환합니다.
     * 하위 클래스에서 이 메서드를 오버라이드하여 POST 요청을 처리해야 합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String protocol = req.getProtocol();
        String message = HTTP_METHOD_POST_NOT_SUPPORTED;

        if (protocol.endsWith("1.1")) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message);
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
        }
    }

    /**
     * HTTP PUT 요청을 처리합니다.
     *
     * PUT 메서드는 지정된 URI에 리소스를 생성하거나 전체를 대체할 때 사용됩니다.
     * 멱등성을 가지며, 동일한 요청을 여러 번 수행해도 결과가 같아야 합니다.
     *
     * 일반적인 사용 사례:
     * - 리소스 전체 수정
     * - 파일 업로드 (특정 경로에)
     * - RESTful API에서 리소스 생성/대체
     *
     * 기본 구현은 405 (Method Not Allowed) 에러를 반환합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String protocol = req.getProtocol();
        String message = HTTP_METHOD_PUT_NOT_SUPPORTED;

        if (protocol.endsWith("1.1")) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message);
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
        }
    }

    /**
     * HTTP DELETE 요청을 처리합니다.
     *
     * DELETE 메서드는 지정된 리소스를 삭제할 때 사용됩니다.
     * 멱등성을 가지며, 동일한 삭제 요청을 여러 번 수행해도 안전해야 합니다.
     *
     * 일반적인 사용 사례:
     * - 리소스 삭제
     * - 계정 삭제
     * - RESTful API에서 리소스 제거
     *
     * 기본 구현은 405 (Method Not Allowed) 에러를 반환합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String protocol = req.getProtocol();
        String message = HTTP_METHOD_DELETE_NOT_SUPPORTED;

        if (protocol.endsWith("1.1")) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message);
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
        }
    }

    /**
     * HTTP OPTIONS 요청을 처리합니다.
     *
     * OPTIONS 메서드는 서버가 지원하는 HTTP 메서드를 조회할 때 사용됩니다.
     * CORS (Cross-Origin Resource Sharing) preflight 요청에서도 사용됩니다.
     *
     * 기본 구현은 현재 서블릿이 지원하는 메서드들을 Allow 헤더에 설정합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // 지원하는 메서드들을 확인하고 Allow 헤더에 설정
        StringBuilder allow = new StringBuilder();

        // 각 메서드가 구현되어 있는지 확인 (기본 구현인지 오버라이드된 구현인지)
        // 실제로는 리플렉션을 사용하여 확인하지만, 여기서는 간단히 구현

        // 기본적으로 지원하는 메서드들
        allow.append("GET, HEAD, POST, PUT, DELETE, OPTIONS");

        resp.setHeader("Allow", allow.toString());
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * HTTP HEAD 요청을 처리합니다.
     *
     * HEAD 메서드는 GET과 동일하지만 응답 본문 없이 헤더만 반환합니다.
     * 리소스의 메타정보를 확인하거나 존재 여부를 검사할 때 사용됩니다.
     *
     * 기본 구현은 doGet()을 호출한 후 응답 본문을 제거합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doHead(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // HEAD 요청을 위한 특별한 응답 래퍼를 생성
        // 이 래퍼는 응답 본문을 무시하고 헤더만 처리합니다
        NoBodyResponse response = new NoBodyResponse(resp);

        // GET 메서드 로직을 실행하되 본문은 무시
        doGet(req, response);

        // Content-Length 헤더를 정확히 설정
        response.setContentLength();
    }

    /**
     * HTTP TRACE 요청을 처리합니다.
     *
     * TRACE 메서드는 클라이언트가 보낸 요청을 그대로 반환합니다.
     * 주로 디버깅 목적으로 사용되지만, 보안상 위험할 수 있어 비활성화하는 경우가 많습니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // TRACE 메서드는 보안상 위험할 수 있으므로 기본적으로 비활성화
        String message = "TRACE method is not allowed";
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message);
    }

    /**
     * HTTP PATCH 요청을 처리합니다.
     *
     * PATCH 메서드는 리소스의 일부분만 수정할 때 사용됩니다.
     * PUT과 달리 전체가 아닌 부분적인 수정을 수행합니다.
     *
     * 기본 구현은 405 (Method Not Allowed) 에러를 반환합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doPatch(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String protocol = req.getProtocol();
        String message = "HTTP method PATCH is not supported by this URL";

        if (protocol.endsWith("1.1")) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message);
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
        }
    }

    /**
     * 모든 HTTP 요청의 진입점입니다.
     *
     * 이 메서드는 GenericServlet의 service() 메서드를 구현합니다.
     * HTTP 메서드를 확인하고 해당하는 doXXX() 메서드로 요청을 전달합니다.
     *
     * 일반적으로 이 메서드를 직접 오버라이드하지 않고,
     * 각 HTTP 메서드에 해당하는 doGet(), doPost() 등을 오버라이드합니다.
     *
     * @param req 클라이언트 요청 (ServletRequest 타입)
     * @param res 클라이언트 응답 (ServletResponse 타입)
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    @Override
    public void service(ServletRequest req, ServletResponse res)
            throws ServletException, IOException {

        HttpServletRequest request;
        HttpServletResponse response;

        // ServletRequest를 HttpServletRequest로 캐스팅
        try {
            request = (HttpServletRequest) req;
            response = (HttpServletResponse) res;
        } catch (ClassCastException e) {
            throw new ServletException("non-HTTP request or response");
        }

        // HTTP 전용 service 메서드로 위임
        service(request, response);
    }

    /**
     * HTTP 요청을 메서드별로 분기하여 처리합니다.
     *
     * 이 메서드는 HTTP 메서드를 확인하고 해당하는 doXXX() 메서드를 호출합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String method = req.getMethod();

        // HTTP 메서드에 따라 해당하는 처리 메서드 호출
        switch (method) {
            case METHOD_GET:
                // Last-Modified 헤더 처리
                long lastModified = getLastModified(req);
                if (lastModified == -1) {
                    // Last-Modified를 알 수 없는 경우 일반 GET 처리
                    doGet(req, resp);
                } else {
                    // If-Modified-Since 헤더 확인하여 304 Not Modified 응답 가능
                    long ifModifiedSince = req.getDateHeader("If-Modified-Since");
                    if (ifModifiedSince < lastModified) {
                        // 리소스가 수정되었으므로 정상 응답
                        maybeSetLastModified(resp, lastModified);
                        doGet(req, resp);
                    } else {
                        // 리소스가 수정되지 않았으므로 304 응답
                        resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    }
                }
                break;

            case METHOD_HEAD:
                // HEAD 요청은 GET과 동일하지만 본문 없음
                long lastModified2 = getLastModified(req);
                maybeSetLastModified(resp, lastModified2);
                doHead(req, resp);
                break;

            case METHOD_POST:
                doPost(req, resp);
                break;

            case METHOD_PUT:
                doPut(req, resp);
                break;

            case METHOD_DELETE:
                doDelete(req, resp);
                break;

            case METHOD_OPTIONS:
                doOptions(req, resp);
                break;

            case METHOD_TRACE:
                doTrace(req, resp);
                break;

            case METHOD_PATCH:
                doPatch(req, resp);
                break;

            default:
                // 지원하지 않는 HTTP 메서드
                String errMsg = "Method " + method + " is not defined in RFC 2068 and is not supported by the Servlet API";
                resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, errMsg);
        }
    }

    /**
     * 리소스의 마지막 수정 시간을 반환합니다.
     *
     * 이 메서드는 HTTP 캐싱 최적화를 위해 사용됩니다.
     * 클라이언트가 If-Modified-Since 헤더를 보낸 경우,
     * 리소스가 변경되지 않았으면 304 Not Modified 응답을 보낼 수 있습니다.
     *
     * 기본 구현은 -1을 반환하여 Last-Modified 헤더를 사용하지 않음을 나타냅니다.
     * 하위 클래스에서 이 메서드를 오버라이드하여 실제 수정 시간을 반환할 수 있습니다.
     *
     * @param req HTTP 요청 객체
     * @return 마지막 수정 시간 (밀리초), 알 수 없으면 -1
     */
    protected long getLastModified(HttpServletRequest req) {
        return -1;
    }

    /**
     * Last-Modified 헤더를 설정합니다.
     *
     * @param resp HTTP 응답 객체
     * @param lastModified 마지막 수정 시간
     */
    private void maybeSetLastModified(HttpServletResponse resp, long lastModified) {
        if (lastModified >= 0) {
            resp.setDateHeader("Last-Modified", lastModified);
        }
    }

    /**
     * HEAD 요청을 위한 응답 래퍼 클래스입니다.
     *
     * 이 클래스는 응답 본문을 무시하고 헤더만 처리하여
     * HEAD 요청의 정확한 Content-Length를 계산합니다.
     */
    private static class NoBodyResponse extends HttpServletResponseWrapper {
        private NoBodyOutputStream noBody;
        private PrintWriter writer;
        private boolean didSetContentLength;

        public NoBodyResponse(HttpServletResponse response) {
            super(response);
            noBody = new NoBodyOutputStream();
        }

        public void setContentLength() {
            if (!didSetContentLength) {
                if (writer != null) {
                    writer.flush();
                }
                super.setContentLength(noBody.getContentLength());
            }
        }

        @Override
        public void setContentLength(int len) {
            super.setContentLength(len);
            didSetContentLength = true;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            return noBody;
        }

        @Override
        public PrintWriter getWriter() throws UnsupportedEncodingException {
            if (writer == null) {
                OutputStreamWriter w = new OutputStreamWriter(noBody, getCharacterEncoding());
                writer = new PrintWriter(w);
            }
            return writer;
        }
    }

    /**
     * HEAD 요청을 위한 더미 출력 스트림입니다.
     *
     * 실제로 데이터를 출력하지 않고 바이트 수만 계산합니다.
     */
    private static class NoBodyOutputStream extends ServletOutputStream {
        private int contentLength = 0;

        public int getContentLength() {
            return contentLength;
        }

        @Override
        public void write(int b) {
            contentLength++;
        }

        @Override
        public void write(byte[] buf) throws IOException {
            if (buf == null) {
                throw new NullPointerException("buf");
            }
            contentLength += buf.length;
        }

        @Override
        public void write(byte[] buf, int offset, int len) throws IOException {
            if (buf == null) {
                throw new NullPointerException("buf");
            }
            if (offset < 0 || len < 0 || offset + len > buf.length) {
                throw new IndexOutOfBoundsException();
            }
            contentLength += len;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            // NoBody이므로 리스너는 즉시 호출
            try {
                writeListener.onWritePossible();
            } catch (IOException e) {
                writeListener.onError(e);
            }
        }
    }
}
