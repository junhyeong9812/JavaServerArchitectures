package src.main.java.jakarta.servlet.http;

import src.main.java.jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.Collection;

/**
 * HTTP 서블릿 응답에 대한 정보를 제공하는 인터페이스입니다.
 *
 * HttpServletResponse는 ServletResponse를 확장하여 HTTP 프로토콜에 특화된
 * 추가 기능들을 제공합니다.
 *
 * 주요 기능:
 * - HTTP 상태 코드 설정
 * - HTTP 헤더 관리
 * - 쿠키 추가
 * - 리다이렉트 처리
 * - 에러 응답 생성
 * - 캐시 제어
 */
public interface HttpServletResponse extends ServletResponse {

    // ========== HTTP 상태 코드 상수들 ==========

    /**
     * 1xx 정보성 응답 (Informational)
     */
    /** 100 Continue - 클라이언트가 요청을 계속해도 됨을 나타냄 */
    int SC_CONTINUE = 100;
    /** 101 Switching Protocols - 프로토콜 전환 요청 수락 */
    int SC_SWITCHING_PROTOCOLS = 101;

    /**
     * 2xx 성공 응답 (Success)
     */
    /** 200 OK - 요청이 성공적으로 처리됨 */
    int SC_OK = 200;
    /** 201 Created - 새로운 리소스가 생성됨 */
    int SC_CREATED = 201;
    /** 202 Accepted - 요청이 접수되었지만 아직 처리되지 않음 */
    int SC_ACCEPTED = 202;
    /** 203 Non-Authoritative Information - 신뢰할 수 없는 정보 */
    int SC_NON_AUTHORITATIVE_INFORMATION = 203;
    /** 204 No Content - 요청은 성공했지만 응답할 내용이 없음 */
    int SC_NO_CONTENT = 204;
    /** 205 Reset Content - 클라이언트가 문서 뷰를 리셋해야 함 */
    int SC_RESET_CONTENT = 205;
    /** 206 Partial Content - 부분적인 GET 요청이 성공함 */
    int SC_PARTIAL_CONTENT = 206;

    /**
     * 3xx 리다이렉션 (Redirection)
     */
    /** 300 Multiple Choices - 여러 선택지가 있음 */
    int SC_MULTIPLE_CHOICES = 300;
    /** 301 Moved Permanently - 리소스가 영구적으로 이동됨 */
    int SC_MOVED_PERMANENTLY = 301;
    /** 302 Found - 리소스가 임시적으로 이동됨 */
    int SC_MOVED_TEMPORARILY = 302;
    /** 302 Found - SC_MOVED_TEMPORARILY와 동일 */
    int SC_FOUND = 302;
    /** 303 See Other - 다른 URI를 참조하라 */
    int SC_SEE_OTHER = 303;
    /** 304 Not Modified - 리소스가 수정되지 않음 */
    int SC_NOT_MODIFIED = 304;
    /** 305 Use Proxy - 프록시를 사용해야 함 */
    int SC_USE_PROXY = 305;
    /** 307 Temporary Redirect - 임시 리다이렉트 */
    int SC_TEMPORARY_REDIRECT = 307;

    /**
     * 4xx 클라이언트 오류 (Client Error)
     */
    /** 400 Bad Request - 잘못된 요청 */
    int SC_BAD_REQUEST = 400;
    /** 401 Unauthorized - 인증 필요 */
    int SC_UNAUTHORIZED = 401;
    /** 402 Payment Required - 결제 필요 (사용되지 않음) */
    int SC_PAYMENT_REQUIRED = 402;
    /** 403 Forbidden - 접근 금지 */
    int SC_FORBIDDEN = 403;
    /** 404 Not Found - 리소스를 찾을 수 없음 */
    int SC_NOT_FOUND = 404;
    /** 405 Method Not Allowed - 허용되지 않는 메서드 */
    int SC_METHOD_NOT_ALLOWED = 405;
    /** 406 Not Acceptable - 수용할 수 없는 응답 */
    int SC_NOT_ACCEPTABLE = 406;
    /** 407 Proxy Authentication Required - 프록시 인증 필요 */
    int SC_PROXY_AUTHENTICATION_REQUIRED = 407;
    /** 408 Request Timeout - 요청 시간 초과 */
    int SC_REQUEST_TIMEOUT = 408;
    /** 409 Conflict - 요청 충돌 */
    int SC_CONFLICT = 409;
    /** 410 Gone - 리소스가 영구적으로 사라짐 */
    int SC_GONE = 410;
    /** 411 Length Required - Content-Length 헤더 필요 */
    int SC_LENGTH_REQUIRED = 411;
    /** 412 Precondition Failed - 사전 조건 실패 */
    int SC_PRECONDITION_FAILED = 412;
    /** 413 Request Entity Too Large - 요청 엔터티가 너무 큼 */
    int SC_REQUEST_ENTITY_TOO_LARGE = 413;
    /** 414 Request-URI Too Long - 요청 URI가 너무 긺 */
    int SC_REQUEST_URI_TOO_LONG = 414;
    /** 415 Unsupported Media Type - 지원되지 않는 미디어 타입 */
    int SC_UNSUPPORTED_MEDIA_TYPE = 415;
    /** 416 Requested Range Not Satisfiable - 요청된 범위가 만족될 수 없음 */
    int SC_REQUESTED_RANGE_NOT_SATISFIABLE = 416;
    /** 417 Expectation Failed - Expect 헤더의 기대를 만족할 수 없음 */
    int SC_EXPECTATION_FAILED = 417;

    /**
     * 5xx 서버 오류 (Server Error)
     */
    /** 500 Internal Server Error - 내부 서버 오류 */
    int SC_INTERNAL_SERVER_ERROR = 500;
    /** 501 Not Implemented - 구현되지 않음 */
    int SC_NOT_IMPLEMENTED = 501;
    /** 502 Bad Gateway - 잘못된 게이트웨이 */
    int SC_BAD_GATEWAY = 502;
    /** 503 Service Unavailable - 서비스 이용 불가 */
    int SC_SERVICE_UNAVAILABLE = 503;
    /** 504 Gateway Timeout - 게이트웨이 시간 초과 */
    int SC_GATEWAY_TIMEOUT = 504;
    /** 505 HTTP Version Not Supported - 지원되지 않는 HTTP 버전 */
    int SC_HTTP_VERSION_NOT_SUPPORTED = 505;

    // ========== 상태 코드 관련 메서드 ==========

    /**
     * HTTP 응답 상태 코드를 설정합니다.
     *
     * HTTP 응답의 상태 코드를 설정합니다. 이 메서드는 응답이 커밋되기 전에
     * 호출되어야 합니다. 커밋된 후에는 상태 코드를 변경할 수 없습니다.
     *
     * 일반적인 상태 코드:
     * - 200: 성공
     * - 302: 리다이렉트
     * - 404: 페이지를 찾을 수 없음
     * - 500: 서버 내부 오류
     *
     * @param sc HTTP 상태 코드
     */
    void setStatus(int sc);

    /**
     * HTTP 응답 상태 코드와 메시지를 설정합니다.
     *
     * @deprecated Servlet API 2.1부터 deprecated.
     * 대신 setStatus(int)와 sendError(int, String)을 사용하세요.
     *
     * @param sc HTTP 상태 코드
     * @param sm 상태 메시지
     */
    @Deprecated
    void setStatus(int sc, String sm);

    /**
     * 에러 응답을 클라이언트에게 전송합니다.
     *
     * 지정된 상태 코드로 에러 응답을 생성합니다.
     * 서버의 기본 에러 페이지가 사용됩니다.
     *
     * 이 메서드 호출 후에는 응답이 커밋되며, 추가적인 출력이나
     * 헤더 변경이 불가능합니다.
     *
     * @param sc 에러 상태 코드
     * @throws IOException 입출력 오류 시
     * @throws IllegalStateException 응답이 이미 커밋된 경우
     */
    void sendError(int sc) throws IOException;

    /**
     * 에러 응답을 사용자 정의 메시지와 함께 전송합니다.
     *
     * 지정된 상태 코드와 메시지로 에러 응답을 생성합니다.
     * 메시지는 에러 페이지에 표시될 수 있습니다.
     *
     * @param sc 에러 상태 코드
     * @param msg 에러 메시지
     * @throws IOException 입출력 오류 시
     * @throws IllegalStateException 응답이 이미 커밋된 경우
     */
    void sendError(int sc, String msg) throws IOException;

    // ========== 리다이렉트 ==========

    /**
     * 클라이언트를 다른 URL로 리다이렉트합니다.
     *
     * 302 Found 상태 코드와 Location 헤더를 사용하여
     * 클라이언트를 지정된 URL로 리다이렉트합니다.
     *
     * URL은 절대 URL이어야 합니다. 상대 URL의 경우 자동으로
     * 절대 URL로 변환됩니다.
     *
     * 이 메서드 호출 후에는 응답이 커밋되며, 추가적인 출력이나
     * 헤더 변경이 불가능합니다.
     *
     * 사용 예시:
     * response.sendRedirect("http://www.example.com");
     * response.sendRedirect("/newpage.jsp");
     *
     * @param location 리다이렉트할 URL
     * @throws IOException 입출력 오류 시
     * @throws IllegalStateException 응답이 이미 커밋된 경우
     */
    void sendRedirect(String location) throws IOException;

    // ========== HTTP 헤더 관리 ==========

    /**
     * 지정된 이름과 값으로 응답 헤더를 설정합니다.
     *
     * 동일한 이름의 헤더가 이미 존재하는 경우 새 값으로 대체됩니다.
     * 여러 값을 가져야 하는 헤더의 경우 addHeader()를 사용하세요.
     *
     * 일반적인 응답 헤더:
     * - "Content-Type": 응답 내용의 MIME 타입
     * - "Cache-Control": 캐시 제어 지시자
     * - "Expires": 만료 시간
     * - "Last-Modified": 마지막 수정 시간
     *
     * @param name 헤더 이름
     * @param value 헤더 값
     */
    void setHeader(String name, String value);

    /**
     * 지정된 이름과 값으로 응답 헤더를 추가합니다.
     *
     * 동일한 이름의 헤더가 이미 존재하는 경우 새 값을 추가합니다.
     * 기존 값을 대체하지 않고 추가하는 점이 setHeader()와 다릅니다.
     *
     * Set-Cookie 헤더처럼 여러 값을 가질 수 있는 헤더에 유용합니다.
     *
     * @param name 헤더 이름
     * @param value 헤더 값
     */
    void addHeader(String name, String value);

    /**
     * 정수 값으로 응답 헤더를 설정합니다.
     *
     * Content-Length 같은 숫자 헤더를 설정할 때 편리합니다.
     *
     * @param name 헤더 이름
     * @param value 헤더 값 (정수)
     */
    void setIntHeader(String name, int value);

    /**
     * 정수 값으로 응답 헤더를 추가합니다.
     *
     * @param name 헤더 이름
     * @param value 헤더 값 (정수)
     */
    void addIntHeader(String name, int value);

    /**
     * 날짜 값으로 응답 헤더를 설정합니다.
     *
     * Last-Modified, Expires 같은 날짜 헤더를 설정할 때 사용합니다.
     *
     * @param name 헤더 이름
     * @param date 헤더 값 (밀리초 단위 시간)
     */
    void setDateHeader(String name, long date);

    /**
     * 날짜 값으로 응답 헤더를 추가합니다.
     *
     * @param name 헤더 이름
     * @param date 헤더 값 (밀리초 단위 시간)
     */
    void addDateHeader(String name, long date);

    /**
     * 지정된 이름의 헤더 값을 반환합니다.
     *
     * 응답에 설정된 헤더 값을 조회할 때 사용합니다.
     * 동일한 이름의 헤더가 여러 개인 경우 첫 번째 값을 반환합니다.
     *
     * @param name 헤더 이름
     * @return 헤더 값, 존재하지 않으면 null
     */
    String getHeader(String name);

    /**
     * 지정된 이름의 모든 헤더 값을 반환합니다.
     *
     * @param name 헤더 이름
     * @return 해당 헤더의 모든 값들의 Collection
     */
    Collection<String> getHeaders(String name);

    /**
     * 모든 헤더의 이름을 반환합니다.
     *
     * @return 모든 헤더 이름들의 Collection
     */
    Collection<String> getHeaderNames();

    /**
     * 지정된 이름의 헤더가 설정되어 있는지 확인합니다.
     *
     * @param name 헤더 이름
     * @return 헤더가 설정되어 있으면 true, 그렇지 않으면 false
     */
    boolean containsHeader(String name);

    // ========== 쿠키 관리 ==========

    /**
     * 응답에 쿠키를 추가합니다.
     *
     * 클라이언트에게 전송할 쿠키를 추가합니다.
     * 여러 쿠키를 추가하려면 이 메서드를 여러 번 호출합니다.
     *
     * 사용 예시:
     * Cookie cookie = new Cookie("username", "john");
     * cookie.setMaxAge(3600); // 1시간
     * cookie.setPath("/");
     * response.addCookie(cookie);
     *
     * @param cookie 추가할 쿠키 객체
     */
    void addCookie(Cookie cookie);

    // ========== URL 인코딩 ==========

    /**
     * 지정된 URL에 세션 ID를 인코딩하여 반환합니다.
     *
     * 쿠키를 지원하지 않는 브라우저를 위해 URL에 세션 ID를 추가합니다.
     * 쿠키가 활성화되어 있으면 URL을 그대로 반환할 수 있습니다.
     *
     * 사용 예시:
     * String encodedURL = response.encodeURL("/servlet/next");
     * out.println("<a href='" + encodedURL + "'>Next</a>");
     *
     * @param url 인코딩할 URL
     * @return 세션 ID가 인코딩된 URL
     */
    String encodeURL(String url);

    /**
     * 리다이렉트 URL에 세션 ID를 인코딩하여 반환합니다.
     *
     * sendRedirect()를 사용할 때 세션을 유지하기 위해 사용합니다.
     *
     * 사용 예시:
     * String redirectURL = response.encodeRedirectURL("/success.jsp");
     * response.sendRedirect(redirectURL);
     *
     * @param url 리다이렉트할 URL
     * @return 세션 ID가 인코딩된 URL
     */
    String encodeRedirectURL(String url);

    /**
     * @deprecated Servlet API 2.1부터 deprecated.
     * 대신 encodeURL(String)을 사용하세요.
     */
    @Deprecated
    String encodeUrl(String url);

    /**
     * @deprecated Servlet API 2.1부터 deprecated.
     * 대신 encodeRedirectURL(String)을 사용하세요.
     */
    @Deprecated
    String encodeRedirectUrl(String url);

    // ========== 응답 상태 조회 ==========

    /**
     * 현재 설정된 HTTP 상태 코드를 반환합니다.
     *
     * Servlet 3.0에서 추가된 메서드입니다.
     *
     * @return HTTP 상태 코드
     */
    int getStatus();
}
