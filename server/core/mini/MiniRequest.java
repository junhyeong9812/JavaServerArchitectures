package server.core.mini;

import server.core.http.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP 요청 래퍼
 * HttpRequest를 서블릿 친화적 인터페이스로 래핑
 */
public class MiniRequest {

    private final HttpRequest httpRequest;
    private final MiniContext context;
    private final Map<String, Object> attributes;

    public MiniRequest(HttpRequest httpRequest, MiniContext context) {
        this.httpRequest = Objects.requireNonNull(httpRequest);
        this.context = Objects.requireNonNull(context);
        this.attributes = new ConcurrentHashMap<>();
    }

    // === HTTP 요청 정보 위임 ===

    public HttpMethod getMethod() {
        return httpRequest.getMethod();
    }

    public String getRequestURI() {
        return httpRequest.getUri();
    }

    public String getRequestURL() {
        return "http://localhost:8080" + httpRequest.getUri(); // 간단한 구현
    }

    public String getServletPath() {
        String contextPath = context.getContextPath();
        String uri = httpRequest.getUri();
        if (uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    public String getPathInfo() {
        return httpRequest.getPath();
    }

    public String getQueryString() {
        return httpRequest.getQueryString();
    }

    // === 파라미터 접근 ===

    public String getParameter(String name) {
        // 쿼리 파라미터 우선, 그 다음 form 파라미터
        String value = httpRequest.getQueryParameter(name);
        return value != null ? value : httpRequest.getFormParameter(name);
    }

    public String[] getParameterValues(String name) {
        List<String> queryValues = httpRequest.getQueryParameterValues(name);
        List<String> formValues = httpRequest.getFormParameterValues(name);

        List<String> allValues = new ArrayList<>(queryValues);
        allValues.addAll(formValues);

        return allValues.isEmpty() ? null : allValues.toArray(new String[0]);
    }

    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> paramMap = new HashMap<>();

        // 쿼리 파라미터 추가
        httpRequest.getQueryParameters().forEach((key, values) ->
                paramMap.put(key, values.toArray(new String[0])));

        // Form 파라미터 추가 (중복시 쿼리 파라미터가 우선)
        httpRequest.getFormParameters().forEach((key, values) ->
                paramMap.putIfAbsent(key, values.toArray(new String[0])));

        return paramMap;
    }

    public Set<String> getParameterNames() {
        Set<String> names = new HashSet<>();
        names.addAll(httpRequest.getQueryParameters().keySet());
        names.addAll(httpRequest.getFormParameters().keySet());
        return names;
    }

    // === 헤더 접근 ===

    public String getHeader(String name) {
        return httpRequest.getHeader(name);
    }

    public List<String> getHeaders(String name) {
        return httpRequest.getHeaders().getAll(name);
    }

    public Set<String> getHeaderNames() {
        return httpRequest.getHeaderNames();
    }

    public String getContentType() {
        return httpRequest.getContentType();
    }

    public int getContentLength() {
        return (int) httpRequest.getContentLength();
    }

    // === Body 접근 ===

    public String getBody() {
        return httpRequest.getBodyAsString();
    }

    public byte[] getBodyBytes() {
        return httpRequest.getBody();
    }

    // === 속성 관리 ===

    public void setAttribute(String name, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("Attribute name cannot be null");
        }
        if (value == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, value);
        }
        // HttpRequest에도 설정
        httpRequest.setAttribute(name, value);
    }

    public Object getAttribute(String name) {
        Object value = attributes.get(name);
        return value != null ? value : httpRequest.getAttribute(name);
    }

    public void removeAttribute(String name) {
        attributes.remove(name);
        httpRequest.removeAttribute(name);
    }

    public Set<String> getAttributeNames() {
        Set<String> names = new HashSet<>(attributes.keySet());
        names.addAll(httpRequest.getAttributeNames());
        return names;
    }

    // === 경로 파라미터 (라우팅에서 설정됨) ===

    public String getPathParameter(String name) {
        return getAttribute("path." + name, String.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getPathParameters() {
        return getAttribute("path.parameters", Map.class);
    }

    // === 유틸리티 메서드 ===

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name, Class<T> type) {
        Object value = getAttribute(name);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public boolean isJsonRequest() {
        return httpRequest.isJsonRequest();
    }

    public boolean isAjaxRequest() {
        return httpRequest.isAjaxRequest();
    }

    public MiniContext getContext() {
        return context;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    @Override
    public String toString() {
        return String.format("MiniRequest{%s %s}", getMethod(), getRequestURI());
    }
}