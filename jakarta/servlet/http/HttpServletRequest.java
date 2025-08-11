package jakarta.servlet.http;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.RequestDispatcher;

import java.io.IOException;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;

/**
 * HTTP 서블릿 요청에 대한 정보를 제공하는 인터페이스입니다.
 *
 * HttpServletRequest는 ServletRequest를 확장하여 HTTP 프로토콜에 특화된
 * 추가 기능들을 제공합니다.
 *
 * 주요 기능:
 * - HTTP 헤더 접근
 * - HTTP 메서드 정보
 * - URL 및 URI 정보
 * - 쿠키 처리
 * - 세션 관리
 * - 인증 정보
 * - 경로 정보
 */
public interface HttpServletRequest extends ServletRequest {

    // ========== HTTP 메서드 관련 ==========

    /**
     * HTTP 요청 메서드를 반환합니다.
     *
     * HTTP 요청의 메서드 (GET, POST, PUT, DELETE 등)를 문자열로 반환합니다.
     * 메서드는 대문자로 반환됩니다.
     *
     * 일반적인 HTTP 메서드들:
     * - "GET": 리소스 조회
     * - "POST": 데이터 전송, 리소스 생성
     * - "PUT": 리소스 전체 수정
     * - "DELETE": 리소스 삭제
     * - "HEAD": 헤더만 조회
     * - "OPTIONS": 지원 메서드 조회
     * - "PATCH": 리소스 부분 수정
     *
     * @return HTTP 메서드 이름 (대문자)
     */
    String getMethod();

    // ========== URL 및 URI 정보 ==========

    /**
     * 요청 URI를 반환합니다.
     *
     * 요청 URI는 프로토콜, 호스트, 포트를 제외한 경로 부분입니다.
     * 쿼리 스트링이 있는 경우 포함됩니다.
     *
     * 예시:
     * - 요청 URL: http://localhost:8080/myapp/servlet/hello?name=john
     * - 반환값: "/myapp/servlet/hello?name=john"
     *
     * @return 요청 URI 문자열
     */
    String getRequestURI();

    /**
     * 요청 URL을 완전한 형태로 재구성하여 반환합니다.
     *
     * 클라이언트가 요청을 보낼 때 사용한 URL을 재구성합니다.
     * 쿼리 스트링은 포함되지 않습니다.
     *
     * 예시:
     * - 클라이언트 요청: http://localhost:8080/myapp/servlet/hello?name=john
     * - 반환값: http://localhost:8080/myapp/servlet/hello
     *
     * @return 재구성된 요청 URL
     */
    StringBuffer getRequestURL();

    /**
     * 서블릿 경로를 반환합니다.
     *
     * 서블릿 경로는 클라이언트가 이 서블릿을 호출하기 위해 사용한 URL의 일부입니다.
     * 컨텍스트 경로와 추가 경로 정보를 제외한, 서블릿 매핑에 해당하는 부분입니다.
     *
     * 예시:
     * - URL: /myapp/servlet/hello/extra
     * - 컨텍스트 경로: /myapp
     * - 서블릿 경로: /servlet/hello  (서블릿 매핑이 /servlet/hello/*인 경우)
     * - 경로 정보: /extra
     *
     * @return 서블릿 경로
     */
    String getServletPath();

    /**
     * 추가 경로 정보를 반환합니다.
     *
     * 서블릿 경로 이후의 추가 경로 정보입니다.
     * 서블릿 내에서 세부 라우팅을 구현할 때 유용합니다.
     *
     * 예시:
     * - URL: /myapp/servlet/hello/user/123
     * - 서블릿 경로: /servlet/hello
     * - 경로 정보: /user/123
     *
     * @return 추가 경로 정보, 없으면 null
     */
    String getPathInfo();

    /**
     * 추가 경로 정보를 실제 파일 시스템 경로로 변환하여 반환합니다.
     *
     * getPathInfo()가 반환하는 가상 경로를 실제 파일 시스템의
     * 절대 경로로 변환합니다.
     *
     * @return 실제 파일 시스템 경로, 변환할 수 없으면 null
     */
    String getPathTranslated();

    /**
     * 쿼리 스트링을 반환합니다.
     *
     * URL에서 '?' 이후의 부분을 반환합니다.
     * URL 디코딩되지 않은 원시 형태로 반환됩니다.
     *
     * 예시:
     * - URL: /servlet/hello?name=john&age=25
     * - 반환값: "name=john&age=25"
     *
     * @return 쿼리 스트링, 없으면 null
     */
    String getQueryString();

    // ========== HTTP 헤더 관련 ==========

    /**
     * 지정된 이름의 HTTP 헤더 값을 반환합니다.
     *
     * HTTP 헤더는 대소문자를 구분하지 않습니다.
     * 동일한 이름의 헤더가 여러 개 있는 경우 첫 번째 값을 반환합니다.
     *
     * 일반적인 HTTP 헤더들:
     * - "Content-Type": 요청 본문의 MIME 타입
     * - "Content-Length": 요청 본문의 길이
     * - "User-Agent": 클라이언트 정보
     * - "Accept": 클라이언트가 수용할 수 있는 응답 타입
     * - "Authorization": 인증 정보
     *
     * @param name 헤더 이름 (대소문자 무관)
     * @return 헤더 값, 존재하지 않으면 null
     */
    String getHeader(String name);

    /**
     * 지정된 이름의 모든 HTTP 헤더 값을 반환합니다.
     *
     * 동일한 이름의 헤더가 여러 개 있는 경우 모든 값을 반환합니다.
     *
     * 예시: Accept-Language 헤더가 여러 개인 경우
     * Accept-Language: en-US,en;q=0.9
     * Accept-Language: ko;q=0.8
     *
     * @param name 헤더 이름
     * @return 해당 헤더의 모든 값들의 Enumeration
     */
    Enumeration<String> getHeaders(String name);

    /**
     * 모든 HTTP 헤더의 이름을 반환합니다.
     *
     * 요청에 포함된 모든 헤더의 이름을 반환합니다.
     * 디버깅이나 로깅 목적으로 유용합니다.
     *
     * @return 모든 헤더 이름들의 Enumeration
     */
    Enumeration<String> getHeaderNames();

    /**
     * 지정된 헤더를 정수로 파싱하여 반환합니다.
     *
     * Content-Length 같은 숫자 헤더를 편리하게 처리할 수 있습니다.
     *
     * @param name 헤더 이름
     * @return 헤더 값을 정수로 변환한 값, 헤더가 없거나 변환할 수 없으면 -1
     */
    int getIntHeader(String name);

    /**
     * 지정된 헤더를 날짜로 파싱하여 반환합니다.
     *
     * If-Modified-Since, Date 같은 날짜 헤더를 처리할 때 사용합니다.
     * RFC 2616에서 정의된 날짜 형식을 파싱합니다.
     *
     * @param name 헤더 이름
     * @return 헤더 값을 날짜(밀리초)로 변환한 값, 헤더가 없거나 변환할 수 없으면 -1
     */
    long getDateHeader(String name);

    // ========== 쿠키 관련 ==========

    /**
     * 요청에 포함된 모든 쿠키를 반환합니다.
     *
     * 클라이언트가 보낸 Cookie 헤더를 파싱하여 Cookie 객체 배열로 반환합니다.
     * 쿠키가 없는 경우 null을 반환합니다.
     *
     * 사용 예시:
     * Cookie[] cookies = request.getCookies();
     * if (cookies != null) {
     *     for (Cookie cookie : cookies) {
     *         if ("sessionId".equals(cookie.getName())) {
     *             String sessionId = cookie.getValue();
     *             // 세션 처리
     *         }
     *     }
     * }
     *
     * @return 쿠키 배열, 쿠키가 없으면 null
     */
    Cookie[] getCookies();

    // ========== 세션 관련 ==========

    /**
     * 현재 세션을 반환하거나 필요한 경우 새 세션을 생성합니다.
     *
     * 이 메서드는 getSession(true)와 동일합니다.
     * 기존 세션이 있으면 반환하고, 없으면 새로 생성합니다.
     *
     * @return HttpSession 객체
     */
    HttpSession getSession();

    /**
     * 현재 세션을 반환하거나 조건에 따라 새 세션을 생성합니다.
     *
     * @param create true면 세션이 없을 때 새로 생성, false면 기존 세션만 반환
     * @return HttpSession 객체, create가 false이고 세션이 없으면 null
     */
    HttpSession getSession(boolean create);

    /**
     * 요청된 세션 ID를 반환합니다.
     *
     * 클라이언트가 요청과 함께 보낸 세션 ID입니다.
     * 쿠키나 URL 인코딩을 통해 전달될 수 있습니다.
     *
     * @return 요청된 세션 ID, 없으면 null
     */
    String getRequestedSessionId();

    /**
     * 요청된 세션 ID가 유효한지 확인합니다.
     *
     * 클라이언트가 보낸 세션 ID가 현재 유효한 세션과 일치하는지 확인합니다.
     *
     * @return 세션 ID가 유효하면 true, 그렇지 않으면 false
     */
    boolean isRequestedSessionIdValid();

    /**
     * 요청된 세션 ID가 쿠키를 통해 전달되었는지 확인합니다.
     *
     * @return 쿠키를 통해 전달되었으면 true, 그렇지 않으면 false
     */
    boolean isRequestedSessionIdFromCookie();

    /**
     * 요청된 세션 ID가 URL 인코딩을 통해 전달되었는지 확인합니다.
     *
     * @return URL 인코딩을 통해 전달되었으면 true, 그렇지 않으면 false
     */
    boolean isRequestedSessionIdFromURL();

    // ========== 인증 및 보안 관련 ==========

    /**
     * 요청을 보낸 사용자의 로그인 이름을 반환합니다.
     *
     * HTTP 기본 인증이나 폼 기반 인증 후에 사용자명을 반환합니다.
     * 인증되지 않은 요청의 경우 null을 반환합니다.
     *
     * @return 사용자 로그인 이름, 인증되지 않았으면 null
     */
    String getRemoteUser();

    /**
     * 사용자가 지정된 역할에 속하는지 확인합니다.
     *
     * 보안 제약이나 역할 기반 접근 제어에서 사용됩니다.
     *
     * @param role 확인할 역할 이름
     * @return 사용자가 해당 역할에 속하면 true, 그렇지 않으면 false
     */
    boolean isUserInRole(String role);

    /**
     * 인증된 사용자의 Principal 객체를 반환합니다.
     *
     * Principal은 사용자의 신원 정보를 담고 있는 객체입니다.
     * 더 상세한 사용자 정보가 필요한 경우 사용합니다.
     *
     * @return 사용자의 Principal 객체, 인증되지 않았으면 null
     */
    Principal getUserPrincipal();

    /**
     * 프로그래매틱 인증을 수행합니다.
     *
     * 사용자명과 비밀번호를 사용하여 프로그램적으로 인증을 시도합니다.
     * Servlet 3.0에서 추가된 기능입니다.
     *
     * @param username 사용자명
     * @param password 비밀번호
     * @throws ServletException 인증 실패 시
     */
    void login(String username, String password) throws ServletException;

    /**
     * 현재 사용자를 로그아웃합니다.
     *
     * 현재 요청과 연관된 인증 정보를 제거합니다.
     * Servlet 3.0에서 추가된 기능입니다.
     *
     * @throws ServletException 로그아웃 실패 시
     */
    void logout() throws ServletException;

    /**
     * 멀티파트 요청의 Part들을 반환합니다.
     *
     * 파일 업로드 등 multipart/form-data 요청을 처리할 때 사용합니다.
     * Servlet 3.0에서 추가된 기능입니다.
     *
     * @return Part 객체들의 Collection
     * @throws IOException 입출력 오류 시
     * @throws ServletException 멀티파트 요청이 아니거나 파싱 오류 시
     */
    Collection<Part> getParts() throws IOException, ServletException;

    /**
     * 지정된 이름의 Part를 반환합니다.
     *
     * @param name Part 이름
     * @return 해당 이름의 Part 객체, 없으면 null
     * @throws IOException 입출력 오류 시
     * @throws ServletException 멀티파트 요청이 아니거나 파싱 오류 시
     */
    Part getPart(String name) throws IOException, ServletException;

    // ========== 요청 디스패처 관련 ==========

    /**
     * 지정된 경로에 대한 RequestDispatcher를 반환합니다.
     *
     * forward나 include 작업을 수행할 때 사용합니다.
     * 경로는 현재 요청의 컨텍스트 내에서 해석됩니다.
     *
     * @param path 대상 경로
     * @return RequestDispatcher 객체, 경로가 유효하지 않으면 null
     */
    RequestDispatcher getRequestDispatcher(String path);

    // ========== 기타 유틸리티 메서드 ==========

    /**
     * 요청이 보안 채널(HTTPS)을 통해 이루어졌는지 확인합니다.
     *
     * @return HTTPS 요청이면 true, HTTP 요청이면 false
     */
    boolean isSecure();

    /**
     * 지정된 URL에 세션 ID를 인코딩하여 반환합니다.
     *
     * 쿠키를 지원하지 않는 브라우저를 위해 URL에 세션 ID를 추가합니다.
     * 쿠키가 활성화되어 있으면 URL을 그대로 반환할 수 있습니다.
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
     * @param url 리다이렉트할 URL
     * @return 세션 ID가 인코딩된 URL
     */
    String encodeRedirectURL(String url);
}
