package server.core.http;

// java.util 패키지의 모든 클래스를 import
// List, Map, Set, Collections 등의 컬렉션 클래스들을 사용하기 위함
import java.util.*;
// 멀티스레드 환경에서 안전한 HashMap을 사용하기 위해 import
// 동시에 여러 스레드가 접근해도 데이터 일관성이 보장됨
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP 헤더 관리 클래스
 * RFC 7230에 따른 Case-insensitive 헤더 처리
 */
public class HttpHeaders {

    // Case-insensitive 헤더 저장을 위한 맵
    // Key: 소문자 헤더명 - HTTP 헤더는 대소문자를 구분하지 않으므로 소문자로 정규화
    // Value: {원본 헤더명, 값 리스트} - 원본 대소문자 보존과 다중 값 지원
    // ConcurrentHashMap 사용 이유: 멀티스레드 환경에서 안전한 동시 접근 보장
    private final Map<String, HeaderEntry> headers;

    // 기본 생성자
    public HttpHeaders() {
        // ConcurrentHashMap 인스턴스 생성
        // 초기 용량은 기본값 사용 (16)
        this.headers = new ConcurrentHashMap<>();
    }

    // 초기 헤더 맵을 받는 생성자
    public HttpHeaders(Map<String, String> initialHeaders) {
        // 기본 생성자 호출하여 headers 맵 초기화
        this();
        // initialHeaders가 null이 아닌 경우에만 처리
        if (initialHeaders != null) {
            // forEach 메서드로 각 엔트리를 순회하며 set 메서드 호출
            // this::set은 메서드 레퍼런스로 (key, value) -> this.set(key, value)와 동일
            initialHeaders.forEach(this::set);
        }
    }

    /**
     * 헤더 설정 (기존 값 덮어쓰기)
     */
    public HttpHeaders set(String name, String value) {
        // 헤더명 유효성 검사 - null, 빈 문자열, 잘못된 문자 체크
        validateHeaderName(name);
        // 헤더값 유효성 검사 - null, 제어 문자 체크
        validateHeaderValue(value);

        // 헤더명을 소문자로 변환하여 대소문자 구분 없이 저장
        // toLowerCase() 사용으로 "Content-Type"과 "content-type"을 같은 키로 처리
        String key = name.toLowerCase();

        // headers 맵에 새로운 HeaderEntry 저장
        // Collections.singletonList(value): 불변의 단일 요소 리스트 생성
        // 메모리 효율적이고 수정 불가능한 리스트
        headers.put(key, new HeaderEntry(name, Collections.singletonList(value)));

        // 메서드 체이닝을 위해 this 반환
        // response.setHeader("a", "1").setHeader("b", "2") 형태로 사용 가능
        return this;
    }

    /**
     * 헤더 추가 (기존 값에 추가)
     */
    public HttpHeaders add(String name, String value) {
        // 헤더명과 값의 유효성 검사
        validateHeaderName(name);
        validateHeaderValue(value);

        // 헤더명을 소문자로 정규화
        String key = name.toLowerCase();

        // compute 메서드: 키에 대한 값을 계산하여 저장
        // 기존 값이 있으면 수정, 없으면 새로 생성
        headers.compute(key, (k, existing) -> {
            // 기존 헤더가 없는 경우
            if (existing == null) {
                // 새로운 ArrayList에 값을 넣어 생성
                // ArrayList 사용 이유: 가변 리스트로 나중에 값 추가 가능
                return new HeaderEntry(name, new ArrayList<>(Collections.singletonList(value)));
            } else {
                // 기존 헤더가 있는 경우, 기존 값들을 복사한 새 리스트 생성
                List<String> values = new ArrayList<>(existing.values);
                // 새 값을 리스트 끝에 추가
                values.add(value);
                // 기존 원본 헤더명 유지하고 새 값 리스트로 HeaderEntry 생성
                return new HeaderEntry(existing.originalName, values);
            }
        });

        // 메서드 체이닝을 위해 this 반환
        return this;
    }

    /**
     * 헤더 값 가져오기 (첫 번째 값)
     */
    public String get(String name) {
        // name이 null인 경우 null 반환
        if (name == null) return null;

        // 소문자로 변환하여 헤더 검색
        HeaderEntry entry = headers.get(name.toLowerCase());

        // 헤더가 존재하고 값 리스트가 비어있지 않은 경우 첫 번째 값 반환
        // 삼항 연산자 사용: 조건 ? 참일때값 : 거짓일때값
        // get(0): 리스트의 첫 번째 요소 반환
        return entry != null && !entry.values.isEmpty() ? entry.values.get(0) : null;
    }

    /**
     * 헤더의 모든 값 가져오기
     */
    public List<String> getAll(String name) {
        // name이 null인 경우 빈 리스트 반환
        if (name == null) return Collections.emptyList();

        // 소문자로 변환하여 헤더 검색
        HeaderEntry entry = headers.get(name.toLowerCase());

        // 헤더가 존재하면 값 리스트의 복사본 반환, 없으면 빈 리스트 반환
        // new ArrayList<>(entry.values): 원본 리스트를 수정하지 못하도록 복사본 생성
        // Collections.emptyList(): 불변의 빈 리스트 반환
        return entry != null ? new ArrayList<>(entry.values) : Collections.emptyList();
    }

    /**
     * 헤더 존재 여부 확인
     */
    public boolean contains(String name) {
        // name이 null이 아니고 소문자로 변환한 키가 맵에 존재하는지 확인
        // &&(논리곱): 두 조건이 모두 참일 때만 true
        // containsKey(): Map 인터페이스 메서드로 키 존재 여부 확인
        return name != null && headers.containsKey(name.toLowerCase());
    }

    /**
     * 헤더 제거
     */
    public HttpHeaders remove(String name) {
        // name이 null이 아닌 경우에만 제거 수행
        if (name != null) {
            // 소문자로 변환한 키로 맵에서 제거
            // remove(): Map의 키-값 쌍을 제거하는 메서드
            headers.remove(name.toLowerCase());
        }

        // 메서드 체이닝을 위해 this 반환
        return this;
    }

    /**
     * 모든 헤더명 가져오기 (원본 대소문자 유지)
     */
    public Set<String> getHeaderNames() {
        // LinkedHashSet: 삽입 순서를 유지하는 Set
        // 헤더가 추가된 순서대로 이름들을 반환하기 위함
        Set<String> names = new LinkedHashSet<>();

        // headers 맵의 모든 값(HeaderEntry)들을 순회
        // values(): Map의 모든 값들을 Collection으로 반환
        for (HeaderEntry entry : headers.values()) {
            // 각 HeaderEntry의 원본 헤더명을 Set에 추가
            // originalName: 사용자가 원래 입력한 대소문자가 보존된 헤더명
            names.add(entry.originalName);
        }

        return names;
    }

    /**
     * 헤더 개수
     */
    public int size() {
        // Map의 size() 메서드로 저장된 헤더 개수 반환
        return headers.size();
    }

    /**
     * 헤더가 비어있는지 확인
     */
    public boolean isEmpty() {
        // Map의 isEmpty() 메서드로 헤더가 하나도 없는지 확인
        return headers.isEmpty();
    }

    /**
     * 모든 헤더 제거
     */
    public void clear() {
        // Map의 clear() 메서드로 모든 키-값 쌍 제거
        headers.clear();
    }

    /**
     * Content-Type 헤더 편의 메서드
     */
    public String getContentType() {
        // get 메서드를 사용하여 Content-Type 헤더 값 반환
        // 자주 사용되는 헤더라서 편의 메서드 제공
        return get("Content-Type");
    }

    public HttpHeaders setContentType(String contentType) {
        // set 메서드를 사용하여 Content-Type 헤더 설정
        // 메서드 체이닝을 위해 반환값을 그대로 반환
        return set("Content-Type", contentType);
    }

    /**
     * Content-Length 헤더 편의 메서드
     */
    public long getContentLength() {
        // Content-Length 헤더 값을 문자열로 가져오기
        String value = get("Content-Length");

        // 값이 없으면 -1 반환 (HTTP에서 Content-Length가 없음을 의미)
        if (value == null) return -1;

        try {
            // 문자열을 long 타입 숫자로 파싱
            // Long.parseLong(): String을 long으로 변환하는 정적 메서드
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            // 숫자가 아닌 잘못된 형식이면 -1 반환
            // NumberFormatException: 숫자 형식이 잘못되었을 때 발생하는 예외
            return -1;
        }
    }

    public HttpHeaders setContentLength(long length) {
        // long 값을 문자열로 변환하여 Content-Length 헤더 설정
        // String.valueOf(): 기본 타입을 문자열로 변환하는 정적 메서드
        return set("Content-Length", String.valueOf(length));
    }

    /**
     * Connection 헤더 편의 메서드
     */
    public boolean isKeepAlive() {
        // Connection 헤더 값 가져오기
        String connection = get("Connection");

        // connection이 null이 아니고 "keep-alive"와 대소문자 구분 없이 일치하는지 확인
        // trim(): 문자열 앞뒤 공백 제거
        // equalsIgnoreCase(): 대소문자 구분 없이 문자열 비교
        return connection != null &&
                "keep-alive".equalsIgnoreCase(connection.trim());
    }

    public HttpHeaders setKeepAlive(boolean keepAlive) {
        // keepAlive가 true면 "keep-alive", false면 "close" 설정
        // 삼항 연산자 사용으로 조건에 따른 값 선택
        return set("Connection", keepAlive ? "keep-alive" : "close");
    }

    /**
     * HTTP 헤더 문자열로 변환 (응답용)
     */
    public String toHeaderString() {
        // StringBuilder: 문자열을 효율적으로 연결하기 위한 클래스
        // String 연결보다 메모리 효율적 (String은 불변이라 연결할 때마다 새 객체 생성)
        StringBuilder sb = new StringBuilder();

        // 모든 HeaderEntry를 순회
        for (HeaderEntry entry : headers.values()) {
            // 원본 헤더명 가져오기 (대소문자 보존)
            String name = entry.originalName;

            // 해당 헤더의 모든 값들을 순회
            for (String value : entry.values) {
                // HTTP 헤더 형식으로 문자열 추가: "Name: Value\r\n"
                // append(): StringBuilder에 문자열 추가하는 메서드
                // \r\n: HTTP 프로토콜에서 요구하는 줄바꿈 (Carriage Return + Line Feed)
                sb.append(name).append(": ").append(value).append("\r\n");
            }
        }

        // StringBuilder를 String으로 변환하여 반환
        return sb.toString();
    }

    /**
     * 헤더명 유효성 검사
     */
    private void validateHeaderName(String name) {
        // 헤더명이 null이거나 공백으로만 이루어져 있는지 확인
        // trim().isEmpty(): 공백 제거 후 빈 문자열인지 확인
        if (name == null || name.trim().isEmpty()) {
            // IllegalArgumentException: 잘못된 인수가 전달되었을 때 발생시키는 예외
            throw new IllegalArgumentException("Header name cannot be null or empty");
        }

        // RFC 7230: 헤더명은 token 형식이어야 함
        // 앞뒤 공백 제거
        String trimmed = name.trim();

        // 헤더명의 각 문자를 검사
        // toCharArray(): 문자열을 char 배열로 변환
        for (char c : trimmed.toCharArray()) {
            // RFC 7230 token 문자가 아니면 예외 발생
            if (!isTokenChar(c)) {
                throw new IllegalArgumentException("Invalid header name: " + name);
            }
        }
    }

    /**
     * 헤더값 유효성 검사
     */
    private void validateHeaderValue(String value) {
        // 헤더값이 null인지 확인
        if (value == null) {
            throw new IllegalArgumentException("Header value cannot be null");
        }

        // RFC 7230: 제어 문자 검사 (CR, LF 제외)
        // HTTP 헤더값에는 개행 문자가 포함될 수 없음 (보안상 이유)
        for (char c : value.toCharArray()) {
            // \r (Carriage Return, 13) 또는 \n (Line Feed, 10) 문자 검사
            if (c == '\r' || c == '\n') {
                throw new IllegalArgumentException("Header value cannot contain CR or LF");
            }
        }
    }

    /**
     * RFC 7230 token 문자 검사
     */
    private boolean isTokenChar(char c) {
        // RFC 7230에 정의된 token 문자 규칙:
        // 1. ASCII 33-126 범위 (인쇄 가능한 문자)
        // 2. 특정 구분자 문자는 제외

        // c > 32: 제어 문자(0-32) 제외
        // c < 127: DEL 문자(127) 제외
        // indexOf(c) == -1: 구분자 문자가 아님을 확인
        // indexOf(): 문자열에서 특정 문자의 위치를 찾는 메서드, 없으면 -1 반환
        return c > 32 && c < 127 &&
                "\"(),/:;<=>?@[\\]{}".indexOf(c) == -1;
    }

    /**
     * 헤더 엔트리 내부 클래스
     * 각 헤더의 원본 이름과 값들을 저장하는 불변 클래스
     */
    private static class HeaderEntry {
        // final: 한번 초기화되면 변경 불가능한 필드
        final String originalName;  // 사용자가 입력한 원본 헤더명 (대소문자 보존)
        final List<String> values;  // 해당 헤더의 모든 값들 (다중 값 지원)

        // 생성자
        HeaderEntry(String originalName, List<String> values) {
            this.originalName = originalName;
            this.values = values;
        }
    }

    /**
     * 객체의 문자열 표현 반환
     * 디버깅과 로깅에 유용
     */
    @Override
    public String toString() {
        // 헤더 개수 정보를 포함한 간단한 문자열 반환
        return "HttpHeaders{" + headers.size() + " headers}";
    }

    /**
     * 객체 동등성 비교
     * 두 HttpHeaders 객체가 같은 헤더들을 가지고 있는지 확인
     */
    @Override
    public boolean equals(Object obj) {
        // 같은 객체 참조인지 확인 (성능 최적화)
        if (this == obj) return true;

        // HttpHeaders 타입인지 확인
        // instanceof: 객체가 특정 타입의 인스턴스인지 확인하는 연산자
        if (!(obj instanceof HttpHeaders)) return false;

        // HttpHeaders로 형변환
        HttpHeaders other = (HttpHeaders) obj;

        // 내부 headers 맵이 동일한지 비교
        // Map의 equals()는 모든 키-값 쌍이 동일한지 확인
        return headers.equals(other.headers);
    }

    /**
     * 해시코드 반환
     * equals()와 함께 오버라이드해야 하는 메서드
     * HashMap, HashSet 등에서 객체를 올바르게 저장/검색하기 위함
     */
    @Override
    public int hashCode() {
        // headers 맵의 해시코드를 그대로 사용
        // 동일한 객체는 동일한 해시코드를 가져야 함
        return headers.hashCode();
    }
}