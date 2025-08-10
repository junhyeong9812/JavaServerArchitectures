package src.main.java.jakarta.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

/**
 * ServletRequest의 편리한 구현을 제공하는 래퍼 클래스입니다.
 *
 * ServletRequestWrapper는 기존 ServletRequest 객체를 감싸서
 * 개발자가 필요한 메서드만 오버라이드할 수 있도록 합니다.
 *
 * 주요 사용 사례:
 * - 요청 매개변수를 필터링하거나 수정
 * - 요청 본문을 로깅하거나 모니터링
 * - 요청 데이터를 암호화/복호화
 * - 문자 인코딩 변환
 * - 입력 스트림 래핑
 *
 * 이 클래스는 Decorator 패턴을 구현하여, 원본 요청 객체의
 * 기능을 확장하거나 수정할 수 있게 해줍니다.
 */
public class ServletRequestWrapper implements ServletRequest {

    /**
     * 감싸고 있는 ServletRequest 객체입니다.
     */
    private ServletRequest request;

    /**
     * 주어진 ServletRequest를 감싸는 래퍼를 생성합니다.
     *
     * @param request 감쌀 ServletRequest 객체
     * @throws IllegalArgumentException request가 null인 경우
     */
    public ServletRequestWrapper(ServletRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        this.request = request;
    }

    /**
     * 감싸고 있는 ServletRequest 객체를 반환합니다.
     *
     * 하위 클래스에서 원본 요청 객체에 직접 접근해야 할 때 사용할 수 있습니다.
     *
     * @return 감싸고 있는 ServletRequest 객체
     */
    public ServletRequest getRequest() {
        return this.request;
    }

    /**
     * 감쌀 요청 객체를 설정합니다.
     *
     * 런타임에 감싸고 있는 요청 객체를 변경해야 할 때 사용합니다.
     *
     * @param request 새로운 ServletRequest 객체
     * @throws IllegalArgumentException request가 null인 경우
     */
    public void setRequest(ServletRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        this.request = request;
    }

    // ========== ServletRequest 메서드 위임 ==========

    /**
     * {@inheritDoc}
     */
    @Override
    public String getParameter(String name) {
        return this.request.getParameter(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration<String> getParameterNames() {
        return this.request.getParameterNames();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getParameterValues(String name) {
        return this.request.getParameterValues(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String[]> getParameterMap() {
        return this.request.getParameterMap();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getAttribute(String name) {
        return this.request.getAttribute(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        return this.request.getAttributeNames();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAttribute(String name, Object o) {
        this.request.setAttribute(name, o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeAttribute(String name) {
        this.request.removeAttribute(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCharacterEncoding() {
        return this.request.getCharacterEncoding();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        this.request.setCharacterEncoding(env);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getContentLength() {
        return this.request.getContentLength();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentType() {
        return this.request.getContentType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        return this.request.getInputStream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BufferedReader getReader() throws IOException {
        return this.request.getReader();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRemoteAddr() {
        return this.request.getRemoteAddr();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRemoteHost() {
        return this.request.getRemoteHost();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalAddr() {
        return this.request.getLocalAddr();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalName() {
        return this.request.getLocalName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLocalPort() {
        return this.request.getLocalPort();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRemotePort() {
        return this.request.getRemotePort();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProtocol() {
        return this.request.getProtocol();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getScheme() {
        return this.request.getScheme();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getServerName() {
        return this.request.getServerName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getServerPort() {
        return this.request.getServerPort();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSecure() {
        return this.request.isSecure();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Locale getLocale() {
        return this.request.getLocale();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration<Locale> getLocales() {
        return this.request.getLocales();
    }
}
