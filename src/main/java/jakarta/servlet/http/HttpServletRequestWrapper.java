package src.main.java.jakarta.servlet.http;

import src.main.java.jakarta.servlet.RequestDispatcher;
import src.main.java.jakarta.servlet.ServletException;
import src.main.java.jakarta.servlet.ServletRequestWrapper;
import java.io.IOException;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;

/**
 * HttpServletRequest의 편리한 구현을 제공하는 래퍼 클래스입니다.
 *
 * HttpServletRequestWrapper는 기존 HttpServletRequest 객체를 감싸서
 * 개발자가 필요한 메서드만 오버라이드할 수 있도록 합니다.
 *
 * 주요 사용 사례:
 * - HTTP 헤더를 추가하거나 수정
 * - 요청 매개변수를 필터링하거나 변환
 * - 인증 정보를 수정하거나 추가
 * - 요청 URL이나 경로 정보 수정
 * - 쿠키나 세션 정보 조작
 *
 * 이 클래스는 Decorator 패턴을 구현하여, 원본 HTTP 요청 객체의
 * 기능을 확장하거나 수정할 수 있게 해줍니다.
 */
public class HttpServletRequestWrapper extends ServletRequestWrapper
        implements HttpServletRequest {

    /**
     * 감싸고 있는 HttpServletRequest 객체입니다.
     */
    private HttpServletRequest request;

    /**
     * 주어진 HttpServletRequest를 감싸는 래퍼를 생성합니다.
     *
     * @param request 감쌀 HttpServletRequest 객체
     * @throws IllegalArgumentException request가 null인 경우
     */
    public HttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        this.request = request;
    }

    /**
     * 감싸고 있는 HttpServletRequest 객체를 반환합니다.
     *
     * 하위 클래스에서 원본 HTTP 요청 객체에 직접 접근해야 할 때 사용할 수 있습니다.
     *
     * @return 감싸고 있는 HttpServletRequest 객체
     */
    public HttpServletRequest getRequest() {
        return this.request;
    }

    /**
     * 감쌀 요청 객체를 설정합니다.
     *
     * 런타임에 감싸고 있는 HTTP 요청 객체를 변경해야 할 때 사용합니다.
     *
     * @param request 새로운 HttpServletRequest 객체
     * @throws IllegalArgumentException request가 null인 경우
     */
    public void setRequest(HttpServletRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        this.request = request;
        super.setRequest(request);
    }

    // ========== HttpServletRequest 메서드 위임 ==========

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMethod() {
        return this.request.getMethod();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRequestURI() {
        return this.request.getRequestURI();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StringBuffer getRequestURL() {
        return this.request.getRequestURL();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getServletPath() {
        return this.request.getServletPath();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPathInfo() {
        return this.request.getPathInfo();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPathTranslated() {
        return this.request.getPathTranslated();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getQueryString() {
        return this.request.getQueryString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHeader(String name) {
        return this.request.getHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration<String> getHeaders(String name) {
        return this.request.getHeaders(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration<String> getHeaderNames() {
        return this.request.getHeaderNames();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getIntHeader(String name) {
        return this.request.getIntHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getDateHeader(String name) {
        return this.request.getDateHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Cookie[] getCookies() {
        return this.request.getCookies();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpSession getSession() {
        return this.request.getSession();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpSession getSession(boolean create) {
        return this.request.getSession(create);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRequestedSessionId() {
        return this.request.getRequestedSessionId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRequestedSessionIdValid() {
        return this.request.isRequestedSessionIdValid();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return this.request.isRequestedSessionIdFromCookie();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRequestedSessionIdFromURL() {
        return this.request.isRequestedSessionIdFromURL();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRemoteUser() {
        return this.request.getRemoteUser();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isUserInRole(String role) {
        return this.request.isUserInRole(role);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Principal getUserPrincipal() {
        return this.request.getUserPrincipal();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void login(String username, String password) throws ServletException {
        this.request.login(username, password);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logout() throws ServletException {
        this.request.logout();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        return this.request.getParts();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Part getPart(String name) throws IOException, ServletException {
        return this.request.getPart(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return this.request.getRequestDispatcher(path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String encodeURL(String url) {
        return this.request.encodeURL(url);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String encodeRedirectURL(String url) {
        return this.request.encodeRedirectURL(url);
    }
}
