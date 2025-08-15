package com.serverarch.eventloop.http;

import com.serverarch.common.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.net.URLDecoder;

/**
 * EventLoop용 HttpRequest 구현체
 *
 * HttpChannelHandler에서 HTTP 요청을 파싱한 후 생성하는 구현체
 * 경로 파라미터와 쿼리 파라미터 기능을 포함
 */
public class SimpleHttpRequest implements HttpRequest {

    private final String method;
    private final String path;
    private final HttpHeaders headers;
    private final byte[] body;
    private final Map<String, String> pathParameters;
    private final Map<String, String> queryParameters;

    /**
     * SimpleHttpRequest 생성자
     *
     * @param method HTTP 메서드
     * @param path 요청 경로
     * @param headers HTTP 헤더들
     * @param body 요청 바디 (바이트 배열)
     */
    public SimpleHttpRequest(String method, String path, HttpHeaders headers, byte[] body) {
        this.method = method != null ? method : "GET";
        this.headers = headers != null ? headers : new HttpHeaders();
        this.body = body != null ? body.clone() : new byte[0]; // 방어적 복사

        // 경로에서 쿼리 파라미터 분리
        String[] pathAndQuery = (path != null ? path : "/").split("\\?", 2);
        this.path = pathAndQuery[0];

        // 쿼리 파라미터 파싱
        this.queryParameters = parseQueryParameters(pathAndQuery.length > 1 ? pathAndQuery[1] : null);

        // 경로 파라미터는 초기에는 빈 맵 (라우터에서 설정됨)
        this.pathParameters = new HashMap<>();
    }

    /**
     * 경로 파라미터를 포함한 생성자
     * 라우터에서 경로 파라미터를 매칭한 후 사용
     */
    public SimpleHttpRequest(String method, String path, HttpHeaders headers, byte[] body,
                             Map<String, String> pathParameters) {
        this.method = method != null ? method : "GET";
        this.headers = headers != null ? headers : new HttpHeaders();
        this.body = body != null ? body.clone() : new byte[0];

        // 경로에서 쿼리 파라미터 분리
        String[] pathAndQuery = (path != null ? path : "/").split("\\?", 2);
        this.path = pathAndQuery[0];

        // 쿼리 파라미터 파싱
        this.queryParameters = parseQueryParameters(pathAndQuery.length > 1 ? pathAndQuery[1] : null);

        // 경로 파라미터 설정
        this.pathParameters = pathParameters != null ?
                new HashMap<>(pathParameters) : new HashMap<>();
    }

    /**
     * 문자열 바디를 받는 편의 생성자
     */
    public SimpleHttpRequest(String method, String path, HttpHeaders headers, String body) {
        this(method, path, headers,
                body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0]);
    }

    /**
     * 쿼리 파라미터 문자열을 파싱하여 Map으로 변환
     * 예: "name=john&age=25&city=seoul" -> {"name": "john", "age": "25", "city": "seoul"}
     */
    private Map<String, String> parseQueryParameters(String queryString) {
        if (queryString == null || queryString.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> params = new HashMap<>();
        String[] pairs = queryString.split("&");

        for (String pair : pairs) {
            if (pair.trim().isEmpty()) {
                continue;
            }

            String[] keyValue = pair.split("=", 2);
            try {
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                String value = keyValue.length > 1 ?
                        URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name()) : "";
                params.put(key, value);
            } catch (Exception e) {
                // URL 디코딩 실패 시 원본 문자열 사용
                String key = keyValue[0];
                String value = keyValue.length > 1 ? keyValue[1] : "";
                params.put(key, value);
            }
        }

        return Collections.unmodifiableMap(params);
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public byte[] getBody() {
        return body.clone(); // 방어적 복사
    }

    @Override
    public Map<String, String> getPathParameters() {
        return Collections.unmodifiableMap(pathParameters);
    }

    @Override
    public Map<String, String> getQueryParameters() {
        return queryParameters; // 이미 unmodifiableMap으로 생성됨
    }

    @Override
    public String getBodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * 경로 파라미터 설정 (라우터에서 사용)
     * 패키지 내부에서만 접근 가능하도록 설정
     */
    void setPathParameter(String name, String value) {
        if (name != null && value != null) {
            pathParameters.put(name, value);
        }
    }

    /**
     * 여러 경로 파라미터 일괄 설정 (라우터에서 사용)
     */
    void setPathParameters(Map<String, String> parameters) {
        if (parameters != null) {
            pathParameters.clear();
            pathParameters.putAll(parameters);
        }
    }

    /**
     * 새로운 인스턴스를 경로 파라미터와 함께 생성
     * 라우터에서 매칭된 파라미터를 설정할 때 사용
     */
    public SimpleHttpRequest withPathParameters(Map<String, String> pathParams) {
        return new SimpleHttpRequest(method, path, headers, body, pathParams);
    }

    // ========== 편의 메서드들 ==========

    /**
     * 경로 파라미터를 정수로 반환
     * @param name 파라미터 이름
     * @param defaultValue 파싱 실패 시 기본값
     * @return 정수 값 또는 기본값
     */
    public int getPathParameterAsInt(String name, int defaultValue) {
        String value = getPathParameter(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 쿼리 파라미터를 정수로 반환
     * @param name 파라미터 이름
     * @param defaultValue 파싱 실패 시 기본값
     * @return 정수 값 또는 기본값
     */
    public int getQueryParameterAsInt(String name, int defaultValue) {
        String value = getQueryParameter(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 경로 파라미터를 long으로 반환
     * @param name 파라미터 이름
     * @param defaultValue 파싱 실패 시 기본값
     * @return long 값 또는 기본값
     */
    public long getPathParameterAsLong(String name, long defaultValue) {
        String value = getPathParameter(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 쿼리 파라미터를 boolean으로 반환
     * "true", "1", "yes", "on"을 true로 인식
     * @param name 파라미터 이름
     * @param defaultValue 파싱 실패 시 기본값
     * @return boolean 값 또는 기본값
     */
    public boolean getQueryParameterAsBoolean(String name, boolean defaultValue) {
        String value = getQueryParameter(name);
        if (value == null) {
            return defaultValue;
        }

        String lowerValue = value.toLowerCase().trim();
        return "true".equals(lowerValue) ||
                "1".equals(lowerValue) ||
                "yes".equals(lowerValue) ||
                "on".equals(lowerValue);
    }

    /**
     * 원본 요청 URI 반환 (경로 + 쿼리 파라미터)
     * @return 완전한 요청 URI
     */
    public String getFullPath() {
        if (queryParameters.isEmpty()) {
            return path;
        }

        StringBuilder fullPath = new StringBuilder(path);
        fullPath.append("?");

        boolean first = true;
        for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
            if (!first) {
                fullPath.append("&");
            }
            fullPath.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }

        return fullPath.toString();
    }

    @Override
    public String toString() {
        return String.format(
                "SimpleHttpRequest{method='%s', path='%s', bodyLength=%d, headerCount=%d, pathParams=%d, queryParams=%d}",
                method, path, body.length, headers.size(),
                pathParameters.size(), queryParameters.size());
    }

    /**
     * 디버깅을 위한 상세 정보 출력
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SimpleHttpRequest {\n");
        sb.append("  method: ").append(method).append("\n");
        sb.append("  path: ").append(path).append("\n");
        sb.append("  bodyLength: ").append(body.length).append("\n");

        if (!pathParameters.isEmpty()) {
            sb.append("  pathParameters: ").append(pathParameters).append("\n");
        }

        if (!queryParameters.isEmpty()) {
            sb.append("  queryParameters: ").append(queryParameters).append("\n");
        }

        sb.append("  headers: ").append(headers.size()).append(" items\n");
        sb.append("}");

        return sb.toString();
    }
}