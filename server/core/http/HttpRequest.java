package server.core.http;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP 요청 객체
 * 완전한 HTTP/1.1 요청 정보를 캡슐화
 */
public class HttpRequest {

    private final HttpMethod method;
    private final String uri;
    private final String version;
    private final HttpHeaders headers;
    private final byte[] body;

    // 파싱된 정보 캐시
    private volatile String path;
    private volatile String queryString;
    private volatile Map<String, List<String>> queryParameters;
    private volatile Map<String, List<String>> formParameters;
    private volatile Map<String, Object> attributes;

    public HttpRequest(HttpMethod method, String uri, String version,
                       HttpHeaders headers, byte[] body) {
        this.method = Objects.requireNonNull(method, "Method cannot be null");
        this.uri = Objects.requireNonNull(uri, "URI cannot be null");
        this.version = Objects.requireNonNull(version, "Version cannot be null");
        this.headers = Objects.requireNonNull(headers, "Headers cannot be null");
        this.body = body != null ? body.clone() : new byte[0];
        this.attributes = new ConcurrentHashMap<>();
    }

    // === 기본 정보 접근자 ===

    public HttpMethod getMethod() {
        return method;
    }

    public String getUri() {
        return uri;
    }

    public String getVersion() {
        return version;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body.clone();
    }

    public int getBodyLength() {
        return body.length;
    }

    // === URI 파싱 메서드 ===

    /**
     * URI에서 경로 부분만 추출 (쿼리 스트링 제외)
     */
    public String getPath() {
        if (path == null) {
            synchronized (this) {
                if (path == null) {
                    int queryIndex = uri.indexOf('?');
                    path = queryIndex == -1 ? uri : uri.substring(0, queryIndex);
                }
            }
        }
        return path;
    }

    /**
     * 쿼리 스트링 부분 추출
     */
    public String getQueryString() {
        if (queryString == null) {
            synchronized (this) {
                if (queryString == null) {
                    int queryIndex = uri.indexOf('?');
                    queryString = queryIndex == -1 ? "" : uri.substring(queryIndex + 1);
                }
            }
        }
        return queryString;
    }

    /**
     * 쿼리 파라미터 파싱
     */
    public Map<String, List<String>> getQueryParameters() {
        if (queryParameters == null) {
            synchronized (this) {
                if (queryParameters == null) {
                    queryParameters = parseParameters(getQueryString());
                }
            }
        }
        return new HashMap<>(queryParameters);
    }

    /**
     * 단일 쿼리 파라미터 값 가져오기
     */
    public String getQueryParameter(String name) {
        List<String> values = getQueryParameters().get(name);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    /**
     * 쿼리 파라미터의 모든 값 가져오기
     */
    public List<String> getQueryParameterValues(String name) {
        List<String> values = getQueryParameters().get(name);
        return values != null ? new ArrayList<>(values) : Collections.emptyList();
    }

    // === Form 데이터 파싱 ===

    /**
     * Form 파라미터 파싱 (application/x-www-form-urlencoded)
     */
    public Map<String, List<String>> getFormParameters() {
        if (formParameters == null) {
            synchronized (this) {
                if (formParameters == null) {
                    String contentType = headers.getContentType();
                    if (contentType != null &&
                            contentType.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
                        String bodyString = new String(body, StandardCharsets.UTF_8);
                        formParameters = parseParameters(bodyString);
                    } else {
                        formParameters = Collections.emptyMap();
                    }
                }
            }
        }
        return new HashMap<>(formParameters);
    }

    /**
     * 단일 form 파라미터 값 가져오기
     */
    public String getFormParameter(String name) {
        List<String> values = getFormParameters().get(name);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    /**
     * Form 파라미터의 모든 값 가져오기
     */
    public List<String> getFormParameterValues(String name) {
        List<String> values = getFormParameters().get(name);
        return values != null ? new ArrayList<>(values) : Collections.emptyList();
    }

    // === Body 접근 메서드 ===

    /**
     * Body를 문자열로 반환
     */
    public String getBodyAsString() {
        return getBodyAsString(StandardCharsets.UTF_8.name());
    }

    /**
     * Body를 지정된 인코딩으로 문자열 반환
     */
    public String getBodyAsString(String encoding) {
        if (body.length == 0) return "";

        try {
            return new String(body, encoding);
        } catch (UnsupportedEncodingException e) {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /**
     * Body를 InputStream으로 반환
     */
    public ByteArrayInputStream getBodyAsStream() {
        return new ByteArrayInputStream(body);
    }

    // === 헤더 편의 메서드 ===

    /**
     * 특정 헤더 값 가져오기
     */
    public String getHeader(String name) {
        return headers.get(name);
    }

    /**
     * 모든 헤더명 가져오기
     */
    public Set<String> getHeaderNames() {
        return headers.getHeaderNames();
    }

    /**
     * Accept 헤더 파싱
     */
    public List<String> getAcceptedMediaTypes() {
        String accept = headers.get("Accept");
        if (accept == null || accept.trim().isEmpty()) {
            return Collections.singletonList("*/*");
        }

        List<String> mediaTypes = new ArrayList<>();
        String[] parts = accept.split(",");
        for (String part : parts) {
            String mediaType = part.split(";")[0].trim(); // q값 제거
            if (!mediaType.isEmpty()) {
                mediaTypes.add(mediaType);
            }
        }
        return mediaTypes;
    }

    /**
     * User-Agent 헤더
     */
    public String getUserAgent() {
        return headers.get("User-Agent");
    }

    /**
     * Content-Type 헤더
     */
    public String getContentType() {
        return headers.getContentType();
    }

    /**
     * Content-Length 헤더
     */
    public long getContentLength() {
        return headers.getContentLength();
    }

    /**
     * Keep-Alive 연결 여부
     */
    public boolean isKeepAlive() {
        return headers.isKeepAlive();
    }

    // === 속성 관리 (요청 처리 중 데이터 저장용) ===

    /**
     * 요청 속성 설정
     */
    public void setAttribute(String name, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("Attribute name cannot be null");
        }
        if (value == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, value);
        }
    }

    /**
     * 요청 속성 가져오기
     */
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    /**
     * 타입 안전한 속성 가져오기
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name, Class<T> type) {
        Object value = attributes.get(name);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 속성 제거
     */
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    /**
     * 모든 속성명 가져오기
     */
    public Set<String> getAttributeNames() {
        return new HashSet<>(attributes.keySet());
    }

    // === 유틸리티 메서드 ===

    /**
     * 파라미터 문자열 파싱 (쿼리 스트링 또는 form 데이터)
     */
    private Map<String, List<String>> parseParameters(String paramString) {
        Map<String, List<String>> params = new HashMap<>();

        if (paramString == null || paramString.trim().isEmpty()) {
            return params;
        }

        String[] pairs = paramString.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length >= 1) {
                try {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                    String value = keyValue.length == 2 ?
                            URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name()) : "";

                    params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                } catch (UnsupportedEncodingException e) {
                    // UTF-8은 항상 지원되므로 발생하지 않음
                }
            }
        }

        return params;
    }

    /**
     * 요청이 JSON인지 확인
     */
    public boolean isJsonRequest() {
        String contentType = getContentType();
        return contentType != null &&
                contentType.toLowerCase().contains("application/json");
    }

    /**
     * 요청이 AJAX인지 확인
     */
    public boolean isAjaxRequest() {
        String xmlHttpRequest = headers.get("X-Requested-With");
        return "XMLHttpRequest".equals(xmlHttpRequest);
    }

    @Override
    public String toString() {
        return String.format("%s %s %s", method, uri, version);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof HttpRequest)) return false;

        HttpRequest other = (HttpRequest) obj;
        return method == other.method &&
                uri.equals(other.uri) &&
                version.equals(other.version) &&
                headers.equals(other.headers) &&
                Arrays.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, uri, version, headers, Arrays.hashCode(body));
    }
}