package server.core.http;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP 헤더 관리 클래스
 * RFC 7230에 따른 Case-insensitive 헤더 처리
 */
public class HttpHeaders {

    // Case-insensitive 헤더 저장을 위한 맵
    // Key: 소문자 헤더명, Value: {원본 헤더명, 값 리스트}
    private final Map<String, HeaderEntry> headers;

    public HttpHeaders() {
        this.headers = new ConcurrentHashMap<>();
    }

    public HttpHeaders(Map<String, String> initialHeaders) {
        this();
        if (initialHeaders != null) {
            initialHeaders.forEach(this::set);
        }
    }

    /**
     * 헤더 설정 (기존 값 덮어쓰기)
     */
    public HttpHeaders set(String name, String value) {
        validateHeaderName(name);
        validateHeaderValue(value);

        String key = name.toLowerCase();
        headers.put(key, new HeaderEntry(name, Collections.singletonList(value)));
        return this;
    }

    /**
     * 헤더 추가 (기존 값에 추가)
     */
    public HttpHeaders add(String name, String value) {
        validateHeaderName(name);
        validateHeaderValue(value);

        String key = name.toLowerCase();
        headers.compute(key, (k, existing) -> {
            if (existing == null) {
                return new HeaderEntry(name, new ArrayList<>(Collections.singletonList(value)));
            } else {
                List<String> values = new ArrayList<>(existing.values);
                values.add(value);
                return new HeaderEntry(existing.originalName, values);
            }
        });
        return this;
    }

    /**
     * 헤더 값 가져오기 (첫 번째 값)
     */
    public String get(String name) {
        if (name == null) return null;

        HeaderEntry entry = headers.get(name.toLowerCase());
        return entry != null && !entry.values.isEmpty() ? entry.values.get(0) : null;
    }

    /**
     * 헤더의 모든 값 가져오기
     */
    public List<String> getAll(String name) {
        if (name == null) return Collections.emptyList();

        HeaderEntry entry = headers.get(name.toLowerCase());
        return entry != null ? new ArrayList<>(entry.values) : Collections.emptyList();
    }

    /**
     * 헤더 존재 여부 확인
     */
    public boolean contains(String name) {
        return name != null && headers.containsKey(name.toLowerCase());
    }

    /**
     * 헤더 제거
     */
    public HttpHeaders remove(String name) {
        if (name != null) {
            headers.remove(name.toLowerCase());
        }
        return this;
    }

    /**
     * 모든 헤더명 가져오기 (원본 대소문자 유지)
     */
    public Set<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>();
        for (HeaderEntry entry : headers.values()) {
            names.add(entry.originalName);
        }
        return names;
    }

    /**
     * 헤더 개수
     */
    public int size() {
        return headers.size();
    }

    /**
     * 헤더가 비어있는지 확인
     */
    public boolean isEmpty() {
        return headers.isEmpty();
    }

    /**
     * 모든 헤더 제거
     */
    public void clear() {
        headers.clear();
    }

    /**
     * Content-Type 헤더 편의 메서드
     */
    public String getContentType() {
        return get("Content-Type");
    }

    public HttpHeaders setContentType(String contentType) {
        return set("Content-Type", contentType);
    }

    /**
     * Content-Length 헤더 편의 메서드
     */
    public long getContentLength() {
        String value = get("Content-Length");
        if (value == null) return -1;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public HttpHeaders setContentLength(long length) {
        return set("Content-Length", String.valueOf(length));
    }

    /**
     * Connection 헤더 편의 메서드
     */
    public boolean isKeepAlive() {
        String connection = get("Connection");
        return connection != null &&
                "keep-alive".equalsIgnoreCase(connection.trim());
    }

    public HttpHeaders setKeepAlive(boolean keepAlive) {
        return set("Connection", keepAlive ? "keep-alive" : "close");
    }

    /**
     * HTTP 헤더 문자열로 변환 (응답용)
     */
    public String toHeaderString() {
        StringBuilder sb = new StringBuilder();
        for (HeaderEntry entry : headers.values()) {
            String name = entry.originalName;
            for (String value : entry.values) {
                sb.append(name).append(": ").append(value).append("\r\n");
            }
        }
        return sb.toString();
    }

    /**
     * 헤더명 유효성 검사
     */
    private void validateHeaderName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Header name cannot be null or empty");
        }

        // RFC 7230: 헤더명은 token 형식
        String trimmed = name.trim();
        for (char c : trimmed.toCharArray()) {
            if (!isTokenChar(c)) {
                throw new IllegalArgumentException("Invalid header name: " + name);
            }
        }
    }

    /**
     * 헤더값 유효성 검사
     */
    private void validateHeaderValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Header value cannot be null");
        }

        // RFC 7230: 제어 문자 검사 (CR, LF 제외)
        for (char c : value.toCharArray()) {
            if (c == '\r' || c == '\n') {
                throw new IllegalArgumentException("Header value cannot contain CR or LF");
            }
        }
    }

    /**
     * RFC 7230 token 문자 검사
     */
    private boolean isTokenChar(char c) {
        return c > 32 && c < 127 &&
                "\"(),/:;<=>?@[\\]{}".indexOf(c) == -1;
    }

    /**
     * 헤더 엔트리 내부 클래스
     */
    private static class HeaderEntry {
        final String originalName;
        final List<String> values;

        HeaderEntry(String originalName, List<String> values) {
            this.originalName = originalName;
            this.values = values;
        }
    }

    @Override
    public String toString() {
        return "HttpHeaders{" + headers.size() + " headers}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof HttpHeaders)) return false;

        HttpHeaders other = (HttpHeaders) obj;
        return headers.equals(other.headers);
    }

    @Override
    public int hashCode() {
        return headers.hashCode();
    }
}