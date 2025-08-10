package src.main.java.jakarta.servlet.http;

import src.main.java.jakarta.servlet.ServletOutputStream;
import src.main.java.jakarta.servlet.ServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collection;

/**
 * HttpServletResponse의 편리한 구현을 제공하는 래퍼 클래스입니다.
 *
 * HttpServletResponseWrapper는 기존 HttpServletResponse 객체를 감싸서
 * 개발자가 필요한 메서드만 오버라이드할 수 있도록 합니다.
 *
 * 주요 사용 사례:
 * - 응답 내용을 필터링하거나 수정
 * - 응답 헤더를 추가하거나 변경
 * - 응답 내용을 로깅하거나 모니터링
 * - 응답 압축이나 암호화
 * - 캐싱 메커니즘 구현
 *
 * 이 클래스는 Decorator 패턴을 구현하여, 원본 응답 객체의
 * 기능을 확장하거나 수정할 수 있게 해줍니다.
 */
public class HttpServletResponseWrapper extends ServletResponseWrapper
        implements HttpServletResponse {

    /**
     * 감싸고 있는 HttpServletResponse 객체입니다.
     */
    private HttpServletResponse response;

    /**
     * 주어진 HttpServletResponse를 감싸는 래퍼를 생성합니다.
     *
     * @param response 감쌀 HttpServletResponse 객체
     * @throws IllegalArgumentException response가 null인 경우
     */
    public HttpServletResponseWrapper(HttpServletResponse response) {
        super(response);
        if (response == null) {
            throw new IllegalArgumentException("Response cannot be null");
        }
        this.response = response;
    }

    /**
     * 감싸고 있는 HttpServletResponse 객체를 반환합니다.
     *
     * 하위 클래스에서 원본 응답 객체에 직접 접근해야 할 때 사용할 수 있습니다.
     *
     * @return 감싸고 있는 HttpServletResponse 객체
     */
    public HttpServletResponse getResponse() {
        return this.response;
    }

    /**
     * 감쌀 응답 객체를 설정합니다.
     *
     * 런타임에 감싸고 있는 응답 객체를 변경해야 할 때 사용합니다.
     *
     * @param response 새로운 HttpServletResponse 객체
     * @throws IllegalArgumentException response가 null인 경우
     */
    public void setResponse(HttpServletResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Response cannot be null");
        }
        this.response = response;
        super.setResponse(response);
    }

    // ========== HttpServletResponse 메서드 위임 ==========

    /**
     * {@inheritDoc}
     */
    @Override
    public void addCookie(Cookie cookie) {
        this.response.addCookie(cookie);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsHeader(String name) {
        return this.response.containsHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String encodeURL(String url) {
        return this.response.encodeURL(url);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String encodeRedirectURL(String url) {
        return this.response.encodeRedirectURL(url);
    }

    /**
     * {@inheritDoc}
     * @deprecated
     */
    @Override
    @Deprecated
    public String encodeUrl(String url) {
        return this.response.encodeUrl(url);
    }

    /**
     * {@inheritDoc}
     * @deprecated
     */
    @Override
    @Deprecated
    public String encodeRedirectUrl(String url) {
        return this.response.encodeRedirectUrl(url);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendError(int sc, String msg) throws IOException {
        this.response.sendError(sc, msg);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendError(int sc) throws IOException {
        this.response.sendError(sc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendRedirect(String location) throws IOException {
        this.response.sendRedirect(location);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDateHeader(String name, long date) {
        this.response.setDateHeader(name, date);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDateHeader(String name, long date) {
        this.response.addDateHeader(name, date);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeader(String name, String value) {
        this.response.setHeader(name, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addHeader(String name, String value) {
        this.response.addHeader(name, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIntHeader(String name, int value) {
        this.response.setIntHeader(name, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addIntHeader(String name, int value) {
        this.response.addIntHeader(name, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStatus(int sc) {
        this.response.setStatus(sc);
    }

    /**
     * {@inheritDoc}
     * @deprecated
     */
    @Override
    @Deprecated
    public void setStatus(int sc, String sm) {
        this.response.setStatus(sc, sm);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getStatus() {
        return this.response.getStatus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHeader(String name) {
        return this.response.getHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getHeaders(String name) {
        return this.response.getHeaders(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getHeaderNames() {
        return this.response.getHeaderNames();
    }
}
