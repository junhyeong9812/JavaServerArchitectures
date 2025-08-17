package server.core.http;

// I/O 관련 클래스들
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
// URL 디코딩을 위한 클래스
import java.net.URLDecoder;
// UTF-8 등 표준 문자 인코딩
import java.nio.charset.StandardCharsets;
// 컬렉션 및 유틸리티 클래스들
import java.util.*;
// 멀티스레드 안전한 HashMap
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP 요청 객체
 * 완전한 HTTP/1.1 요청 정보를 캡슐화
 */
public class HttpRequest {

    // 기본 HTTP 요청 정보 (불변)
    private final HttpMethod method;   // HTTP 메서드 (GET, POST 등)
    private final String uri;          // 요청 URI (전체 경로 + 쿼리스트링)
    private final String version;      // HTTP 버전 (HTTP/1.1)
    private final HttpHeaders headers; // HTTP 헤더들
    private final byte[] body;         // 요청 본문 (바이트 배열)

    // 파싱된 정보 캐시 (성능 최적화를 위한 지연 로딩)
    // volatile: 멀티스레드 환경에서 변수 값의 가시성 보장
    // 한 스레드에서 변경한 값이 다른 스레드에서 즉시 보이도록 함
    private volatile String path;                              // URI에서 경로 부분만 (/path)
    private volatile String queryString;                       // URI에서 쿼리스트링 부분만 (a=1&b=2)
    private volatile Map<String, List<String>> queryParameters;  // 파싱된 쿼리 파라미터
    private volatile Map<String, List<String>> formParameters;   // 파싱된 폼 파라미터
    private volatile Map<String, Object> attributes;             // 요청 처리 중 임시 저장용 속성들

    // 생성자
    public HttpRequest(HttpMethod method, String uri, String version,
                       HttpHeaders headers, byte[] body) {
        // Objects.requireNonNull(): null 체크 후 예외 발생
        // null이면 NullPointerException 발생, 아니면 그대로 반환
        this.method = Objects.requireNonNull(method, "Method cannot be null");
        this.uri = Objects.requireNonNull(uri, "URI cannot be null");
        this.version = Objects.requireNonNull(version, "Version cannot be null");
        this.headers = Objects.requireNonNull(headers, "Headers cannot be null");

        // body가 null이면 빈 배열, 아니면 복사본 생성
        // clone(): 배열의 얕은 복사본 생성 (외부에서 수정하지 못하도록)
        this.body = body != null ? body.clone() : new byte[0];

        // 요청 속성을 저장할 스레드 안전한 맵 초기화
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

    // 본문의 복사본 반환 (원본 보호)
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
        // 이미 파싱되었으면 캐시된 값 반환 (성능 최적화)
        if (path == null) {
            // synchronized: 동기화 블록으로 멀티스레드 안전성 보장
            // 한 번에 하나의 스레드만 이 블록을 실행할 수 있음
            synchronized (this) {
                // double-checked locking: 동기화 안에서 한번 더 확인
                // 다른 스레드가 이미 초기화했을 수 있음
                if (path == null) {
                    // '?' 문자의 위치 찾기 (쿼리스트링 시작점)
                    // indexOf(): 문자열에서 특정 문자의 첫 번째 위치, 없으면 -1
                    int queryIndex = uri.indexOf('?');

                    // 쿼리스트링이 없으면 전체 URI가 경로
                    // 있으면 '?' 이전까지가 경로
                    // substring(start, end): start부터 end 직전까지 부분 문자열
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

                    // 쿼리스트링이 없으면 빈 문자열
                    // 있으면 '?' 다음부터 끝까지
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
                    // 쿼리스트링을 파싱하여 파라미터 맵 생성
                    queryParameters = parseParameters(getQueryString());
                }
            }
        }

        // 원본 맵을 수정하지 못하도록 복사본 반환
        // HashMap 생성자에 기존 맵을 전달하면 복사본 생성
        return new HashMap<>(queryParameters);
    }

    /**
     * 단일 쿼리 파라미터 값 가져오기
     */
    public String getQueryParameter(String name) {
        // 해당 이름의 모든 값들을 가져옴
        List<String> values = getQueryParameters().get(name);

        // 값이 있고 리스트가 비어있지 않으면 첫 번째 값 반환
        // &&: 논리곱, 모든 조건이 true여야 전체가 true
        // !values.isEmpty(): 리스트가 비어있지 않음
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    /**
     * 쿼리 파라미터의 모든 값 가져오기
     */
    public List<String> getQueryParameterValues(String name) {
        List<String> values = getQueryParameters().get(name);

        // 값이 있으면 복사본 반환, 없으면 빈 리스트 반환
        // Collections.emptyList(): 불변의 빈 리스트
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
                    // Content-Type 헤더 확인
                    String contentType = headers.getContentType();

                    // Content-Type이 form 데이터인지 확인
                    // toLowerCase(): 대소문자 구분 없이 비교하기 위해 소문자 변환
                    // startsWith(): 문자열이 특정 문자열로 시작하는지 확인
                    if (contentType != null &&
                            contentType.toLowerCase().startsWith("application/x-www-form-urlencoded")) {

                        // 본문을 UTF-8 문자열로 변환
                        String bodyString = new String(body, StandardCharsets.UTF_8);

                        // form 데이터를 파싱하여 파라미터 맵 생성
                        formParameters = parseParameters(bodyString);
                    } else {
                        // form 데이터가 아니면 빈 맵
                        formParameters = Collections.emptyMap();
                    }
                }
            }
        }

        // 원본을 수정하지 못하도록 복사본 반환
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
        // 기본 UTF-8 인코딩으로 변환
        return getBodyAsString(StandardCharsets.UTF_8.name());
    }

    /**
     * Body를 지정된 인코딩으로 문자열 반환
     */
    public String getBodyAsString(String encoding) {
        // 본문이 비어있으면 빈 문자열 반환
        if (body.length == 0) return "";

        try {
            // 지정된 인코딩으로 바이트 배열을 문자열로 변환
            return new String(body, encoding);
        } catch (UnsupportedEncodingException e) {
            // 지원하지 않는 인코딩이면 UTF-8로 폴백
            // UnsupportedEncodingException: 인코딩을 지원하지 않을 때 발생
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /**
     * Body를 InputStream으로 반환
     */
    public ByteArrayInputStream getBodyAsStream() {
        // 바이트 배열을 읽을 수 있는 InputStream으로 감싸서 반환
        // ByteArrayInputStream: 바이트 배열을 InputStream처럼 읽을 수 있게 해주는 클래스
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

        // Accept 헤더가 없거나 비어있으면 모든 타입 허용
        if (accept == null || accept.trim().isEmpty()) {
            // */*: 모든 미디어 타입을 의미
            // Collections.singletonList(): 단일 요소를 가진 불변 리스트 생성
            return Collections.singletonList("*/*");
        }

        // Accept 헤더를 파싱하여 미디어 타입 목록 생성
        List<String> mediaTypes = new ArrayList<>();

        // 쉼표로 구분된 미디어 타입들을 분리
        // split(","): 쉼표를 구분자로 문자열 분리
        String[] parts = accept.split(",");

        for (String part : parts) {
            // 각 부분에서 미디어 타입만 추출 (q값 등의 매개변수 제거)
            // "text/html;q=0.9" -> "text/html"
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
        // 속성명이 null이면 예외 발생
        if (name == null) {
            throw new IllegalArgumentException("Attribute name cannot be null");
        }

        if (value == null) {
            // 값이 null이면 속성 제거
            attributes.remove(name);
        } else {
            // 값이 있으면 속성 저장
            // ConcurrentHashMap.put(): 스레드 안전한 키-값 저장
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
    @SuppressWarnings("unchecked")  // 제네릭 형변환 경고 억제
    public <T> T getAttribute(String name, Class<T> type) {
        Object value = attributes.get(name);

        // 값이 있고 요청한 타입의 인스턴스인지 확인
        // Class.isInstance(): 객체가 해당 클래스의 인스턴스인지 확인
        if (value != null && type.isInstance(value)) {
            // 안전한 형변환
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
        // 키 집합의 복사본 반환 (원본 수정 방지)
        // keySet(): 맵의 모든 키들을 Set으로 반환
        return new HashSet<>(attributes.keySet());
    }

    // === 유틸리티 메서드 ===

    /**
     * 파라미터 문자열 파싱 (쿼리 스트링 또는 form 데이터)
     */
    private Map<String, List<String>> parseParameters(String paramString) {
        // 파라미터들을 저장할 맵 생성
        Map<String, List<String>> params = new HashMap<>();

        // 파라미터 문자열이 비어있으면 빈 맵 반환
        if (paramString == null || paramString.trim().isEmpty()) {
            return params;
        }

        // '&'로 구분된 키=값 쌍들을 분리
        // "a=1&b=2&c=3" -> ["a=1", "b=2", "c=3"]
        String[] pairs = paramString.split("&");

        for (String pair : pairs) {
            // 각 쌍을 '='로 분리하여 키와 값으로 나누기
            // split("=", 2): 최대 2개 부분으로만 분리 (값에 =가 있을 수 있음)
            String[] keyValue = pair.split("=", 2);

            if (keyValue.length >= 1) {
                try {
                    // URL 디코딩하여 키 추출
                    // URLDecoder.decode(): URL 인코딩된 문자열을 원래 문자열로 복원
                    // %20 -> 공백, %3D -> = 등
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());

                    // 값이 있으면 URL 디코딩, 없으면 빈 문자열
                    String value = keyValue.length == 2 ?
                            URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name()) : "";

                    // 같은 키의 여러 값을 지원하기 위해 List 사용
                    // computeIfAbsent(): 키가 없으면 새 리스트 생성, 있으면 기존 리스트 반환
                    params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                } catch (UnsupportedEncodingException e) {
                    // UTF-8은 항상 지원되므로 발생하지 않음
                    // 하지만 컴파일러 오류를 피하기 위해 catch 블록 필요
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

        // Content-Type에 "application/json"이 포함되어 있는지 확인
        // contains(): 문자열이 특정 부분 문자열을 포함하는지 확인
        return contentType != null &&
                contentType.toLowerCase().contains("application/json");
    }

    /**
     * 요청이 AJAX인지 확인
     */
    public boolean isAjaxRequest() {
        // X-Requested-With 헤더로 AJAX 요청 판별
        // 대부분의 JavaScript 라이브러리가 AJAX 요청 시 이 헤더를 설정
        String xmlHttpRequest = headers.get("X-Requested-With");

        // "XMLHttpRequest" 값이면 AJAX 요청
        // equals(): 문자열 내용이 정확히 일치하는지 확인
        return "XMLHttpRequest".equals(xmlHttpRequest);
    }

    /**
     * 객체의 문자열 표현
     * 로깅과 디버깅에 유용
     */
    @Override
    public String toString() {
        // "GET /path HTTP/1.1" 형태로 요청의 기본 정보 반환
        // String.format(): printf 스타일의 문자열 포맷팅
        return String.format("%s %s %s", method, uri, version);
    }

    /**
     * 객체 동등성 비교
     * 두 HttpRequest가 동일한 요청인지 확인
     */
    @Override
    public boolean equals(Object obj) {
        // 같은 객체 참조면 true (성능 최적화)
        if (this == obj) return true;

        // HttpRequest 타입이 아니면 false
        if (!(obj instanceof HttpRequest)) return false;

        // HttpRequest로 형변환
        HttpRequest other = (HttpRequest) obj;

        // 모든 필드가 동일한지 확인
        // enum은 == 사용 (같은 enum 상수인지 확인)
        // String과 객체는 equals() 사용 (내용 비교)
        // Arrays.equals(): 배열의 모든 요소가 동일한지 확인
        return method == other.method &&
                uri.equals(other.uri) &&
                version.equals(other.version) &&
                headers.equals(other.headers) &&
                Arrays.equals(body, other.body);
    }

    /**
     * 해시코드 계산
     * equals()와 함께 오버라이드해야 하는 메서드
     * HashMap, HashSet에서 객체를 올바르게 저장/검색하기 위함
     */
    @Override
    public int hashCode() {
        // Objects.hash(): 여러 값의 해시코드를 결합하여 계산
        // Arrays.hashCode(): 배열의 해시코드 계산
        return Objects.hash(method, uri, version, headers, Arrays.hashCode(body));
    }
}