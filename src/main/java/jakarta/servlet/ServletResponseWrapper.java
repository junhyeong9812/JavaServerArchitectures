package src.main.java.jakarta.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * ServletResponse의 편리한 구현을 제공하는 래퍼 클래스입니다.
 *
 * ServletResponseWrapper는 기존 ServletResponse 객체를 감싸서
 * 개발자가 필요한 메서드만 오버라이드할 수 있도록 합니다.
 *
 * 주요 사용 사례:
 * - 응답 내용을 필터링하거나 수정
 * - 응답 데이터를 로깅하거나 모니터링
 * - 응답 압축이나 암호화
 * - 문자 인코딩 변환
 * - 출력 스트림 래핑
 *
 * 이 클래스는 Decorator 패턴을 구현하여, 원본 응답 객체의
 * 기능을 확장하거나 수정할 수 있게 해줍니다.
 */
public class ServletResponseWrapper implements ServletResponse {

    /**
     * 감싸고 있는 ServletResponse 객체입니다.
     */
    private ServletResponse response;

    /**
     * 주어진 ServletResponse를 감싸는 래퍼를 생성합니다.
     *
     * @param response 감쌀 ServletResponse 객체
     * @throws IllegalArgumentException response가 null인 경우
     */
    public ServletResponseWrapper(ServletResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Response cannot be null");
        }
        this.response = response;
    }

    /**
     * 감싸고 있는 ServletResponse 객체를 반환합니다.
     *
     * 하위 클래스에서 원본 응답 객체에 직접 접근해야 할 때 사용할 수 있습니다.
     *
     * @return 감싸고 있는 ServletResponse 객체
     */
    public ServletResponse getResponse() {
        return this.response;
    }

    /**
     * 감쌀 응답 객체를 설정합니다.
     *
     * 런타임에 감싸고 있는 응답 객체를 변경해야 할 때 사용합니다.
     *
     * @param response 새로운 ServletResponse 객체
     * @throws IllegalArgumentException response가 null인 경우
     */
    public void setResponse(ServletResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Response cannot be null");
        }
        this.response = response;
    }

    // ========== ServletResponse 메서드 위임 ==========

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCharacterEncoding() {
        return this.response.getCharacterEncoding();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCharacterEncoding(String charset) {
        this.response.setCharacterEncoding(charset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentType() {
        return this.response.getContentType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setContentType(String type) {
        this.response.setContentType(type);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setContentLength(int len) {
        this.response.setContentLength(len);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return this.response.getOutputStream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PrintWriter getWriter() throws IOException {
        return this.response.getWriter();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBufferSize(int size) {
        this.response.setBufferSize(size);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getBufferSize() {
        return this.response.getBufferSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flushBuffer() throws IOException {
        this.response.flushBuffer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCommitted() {
        return this.response.isCommitted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        this.response.reset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetBuffer() {
        this.response.resetBuffer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLocale(Locale loc) {
        this.response.setLocale(loc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Locale getLocale() {
        return this.response.getLocale();
    }
}
