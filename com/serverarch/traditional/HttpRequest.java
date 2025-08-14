package com.serverarch.traditional;

// 공통 HTTP 라이브러리 임포트
// com.serverarch.common.http.*: 프로젝트의 공통 HTTP 관련 클래스들
// HttpMethod: HTTP 메서드 상수와 유틸리티를 제공하는 클래스
// HttpHeaders: HTTP 헤더들을 관리하는 클래스
import com.serverarch.common.http.*;

// Java 문자 인코딩 라이브러리
// java.nio.charset.StandardCharsets: 표준 문자 인코딩을 제공하는 클래스
// StandardCharsets.UTF_8: UTF-8 인코딩 상수
import java.nio.charset.StandardCharsets;

// Java 유틸리티 라이브러리
// java.util.*: 컬렉션, 유틸리티 클래스들
// Arrays: 배열 관련 유틸리티 메서드 제공 클래스
// HashMap: 해시테이블 기반 맵 구현 클래스
// HashSet: 해시테이블 기반 집합 구현 클래스
// List: 순서가 있는 컬렉션 인터페이스
// ArrayList: 동적 배열 기반 리스트 구현 클래스
// Map: 키-값 쌍을 저장하는 컬렉션 인터페이스
// Objects: 객체 관련 유틸리티 메서드 제공 클래스
// Set: 중복을 허용하지 않는 컬렉션 인터페이스
import java.util.*;

/**
 * 간단하고 현대적인 HTTP 요청 클래스 (파서 호환 버전)
 *
 * 설계 목표:
 * 1. 무거운 서블릿 API 대신 HTTP 처리 본질에 집중
 * 2. 3가지 서버 아키텍처가 공통으로 사용할 수 있는 단순한 구조
 * 3. 현대적 Java 기능 활용 (불변성, 방어적 복사)
 * 4. 메모리 효율성과 스레드 안전성 고려
 * 5. HttpRequestParser와 완벽 호환성 보장
 */
public class HttpRequest {

    // HTTP 메서드 (GET, POST, PUT, DELETE 등)
    // final로 선언하여 불변성 보장 - 요청이 생성된 후 변경되면 안 되기 때문
    // String 타입 사용 - 기존 HttpMethod 클래스의 상수와 호환성 유지
    private final String method;

    // 요청 경로 (/users/123 같은 URI path)
    // final로 선언하여 불변성 보장 - 라우팅의 기준이 되므로 변경되면 안 됨
    private final String path;

    // HTTP 버전 (HTTP/1.1, HTTP/1.0 등)
    // final로 선언하여 불변성 보장 - 프로토콜 버전은 요청 생성 후 변경되면 안 됨
    // 파서에서 요청 라인 파싱 시 추출되는 정보
    private final String version;

    // HTTP 헤더들 (Host, Content-Type, User-Agent 등)
    // final로 선언하여 불변성 보장 - 헤더는 요청의 메타데이터이므로 변경되면 안 됨
    private final HttpHeaders headers;

    // 요청 바디 (POST/PUT 요청의 데이터)
    // byte[]로 설계한 이유: 텍스트뿐만 아니라 바이너리 데이터도 처리해야 하기 때문
    private final byte[] body;

    // 경로 파라미터 (/users/{id}에서 {id} 부분)
    // mutable Map으로 설계한 이유: 라우팅 과정에서 동적으로 추출되어 설정되기 때문
    // Map<String, String>: 키와 값이 모두 String인 맵 타입
    // new HashMap<>(): 빈 HashMap으로 초기화
    private final Map<String, String> pathParameters = new HashMap<>();

    // 쿼리 파라미터 (?name=value&age=30 부분을 파싱한 결과)
    // Map<String, List<String>>로 설계한 이유: 같은 키가 여러 번 나올 수 있기 때문 (?tag=java&tag=spring)
    // mutable Map으로 설계한 이유: 파서에서 파싱 과정에서 동적으로 추가되기 때문
    // 파서가 쿼리 스트링을 파싱하여 직접 저장하는 방식으로 성능 최적화
    private final Map<String, List<String>> queryParameters = new HashMap<>();

    // 요청 속성 (필터나 인터셉터에서 설정하는 메타데이터)
    // mutable Map으로 설계한 이유: 요청 처리 과정에서 동적으로 추가되는 정보이기 때문
    // Map<String, Object>: 키는 String, 값은 Object인 맵 타입 (다양한 타입 저장 가능)
    private final Map<String, Object> attributes = new HashMap<>();

    /**
     * HttpRequest 생성자 (파서 전용)
     * HttpRequestParser에서 HTTP 요청을 파싱한 후 객체 생성 시 사용
     *
     * @param method HTTP 메서드 (null이면 GET으로 기본값 설정)
     * @param path 요청 경로 (null이면 "/"로 기본값 설정)
     * @param version HTTP 버전 (null이면 HTTP/1.1로 기본값 설정)
     * @param headers HTTP 헤더들 (null이면 빈 헤더로 기본값 설정)
     * @param body 요청 바디 (null이면 빈 배열로 기본값 설정)
     */
    public HttpRequest(String method, String path, String version,
                       HttpHeaders headers, byte[] body) {
        // null 체크하여 기본값 설정 - 방어적 프로그래밍으로 NPE 방지
        // method != null: null 체크
        // ? method : HttpMethod.GET: 삼항 연산자로 조건부 할당
        // HttpMethod.GET: HttpMethod 클래스의 GET 상수
        this.method = method != null ? method : HttpMethod.GET;

        // path가 null이면 루트 경로로 설정 - 모든 HTTP 요청은 경로를 가져야 하기 때문
        this.path = path != null ? path : "/";

        // version이 null이면 HTTP/1.1로 기본값 설정 - 현재 가장 널리 사용되는 버전
        this.version = version != null ? version : "HTTP/1.1";

        // headers가 null이면 빈 헤더 객체 생성 - 헤더 접근 시 NPE 방지
        // new HttpHeaders(): 빈 헤더 객체 생성
        this.headers = headers != null ? headers : new HttpHeaders();

        // body가 null이면 빈 배열로 설정 - 바디 접근 시 NPE 방지 및 일관성 보장
        // new byte[0]: 크기가 0인 바이트 배열 생성
        this.body = body != null ? body : new byte[0];
    }

    /**
     * HttpRequest 생성자 (기존 호환성 유지)
     * 기존 코드와의 호환성을 위해 유지되는 생성자
     *
     * @param method HTTP 메서드 (null이면 GET으로 기본값 설정)
     * @param path 요청 경로 (null이면 "/"로 기본값 설정)
     * @param queryString 쿼리 스트링 (사용되지 않음 - 파서에서 직접 파라미터 설정)
     * @param headers HTTP 헤더들 (null이면 빈 헤더로 기본값 설정)
     * @param body 요청 바디 (null이면 빈 배열로 기본값 설정)
     */
//    public HttpRequest(String method, String path, String queryString,
//                       HttpHeaders headers, byte[] body) {
//        // 파서 전용 생성자에 위임 - 코드 중복 방지
//        // queryString은 무시하고 HTTP/1.1을 기본 버전으로 사용
//        // 파서가 직접 쿼리 파라미터를 설정하므로 queryString 파라미터는 사용하지 않음
//        this(method, path, "HTTP/1.1", headers, body);
//    }

    // ========== 파서 전용 메서드들 (HttpRequestParser에서 호출) ==========

    /**
     * 쿼리 파라미터 추가 (파서 전용)
     * HttpRequestParser에서 쿼리 스트링을 파싱하면서 호출
     * 같은 키에 여러 값이 올 수 있으므로 리스트로 관리
     *
     * @param name 파라미터 이름
     * @param value 파라미터 값
     */
    public void addQueryParameter(String name, String value) {
        // name이나 value가 null이면 무시 - 잘못된 파라미터 방지
        // name != null: null 체크
        // value != null: null 체크
        if (name != null && value != null) {
            // computeIfAbsent(): 키가 없으면 새 리스트 생성, 있으면 기존 리스트 반환
            // k -> new ArrayList<>(): 람다 표현식으로 새 ArrayList 생성 함수
            // .add(): List의 요소 추가 메서드
            queryParameters.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
    }

    /**
     * 여러 쿼리 파라미터를 한 번에 추가 (파서 편의 메서드)
     * 파서에서 배치 처리 시 사용할 수 있는 최적화된 메서드
     *
     * @param parameters 추가할 파라미터 맵
     */
    public void addQueryParameters(Map<String, String> parameters) {
        // parameters가 null이면 무시 - NPE 방지
        if (parameters != null) {
            // parameters.entrySet(): Map의 모든 키-값 쌍을 Set으로 반환
            // for-each 루프: Map의 모든 엔트리를 순회
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                // entry.getKey(): Map.Entry의 키 반환 메서드
                // entry.getValue(): Map.Entry의 값 반환 메서드
                addQueryParameter(entry.getKey(), entry.getValue());
            }
        }
    }

    // ========== 기본 Getter 메서드들 ==========

    /**
     * HTTP 메서드 반환
     * @return HTTP 메서드 (GET, POST, PUT, DELETE 등)
     */
    public String getMethod() {
        // 단순 반환 - final 필드이므로 불변성 보장됨
        return method;
    }

    /**
     * 요청 경로 반환
     * @return 요청 경로 (예: "/users/123")
     */
    public String getPath() {
        // 단순 반환 - String은 불변 객체이므로 안전
        return path;
    }

    /**
     * 요청 URI 반환 (getPath()와 동일)
     * @return 요청 URI - 호환성을 위한 별칭 메서드
     */
    public String getUri() {
        // path와 동일한 값 반환 - 서블릿 API 호환성을 위한 별칭
        return path;
    }

    /**
     * HTTP 버전 반환
     * @return HTTP 버전 (예: "HTTP/1.1")
     */
    public String getVersion() {
        // 단순 반환 - final 필드이므로 불변성 보장됨
        // 파서에서 설정된 HTTP 버전 정보 반환
        return version;
    }

    /**
     * 쿼리 스트링 반환 (재구성)
     * 파서에서 파싱된 쿼리 파라미터들을 다시 쿼리 스트링 형태로 재구성
     * 호환성을 위해 제공되는 메서드
     * @return 재구성된 쿼리 스트링 또는 null
     */
    public String getQueryString() {
        // 쿼리 파라미터가 없으면 null 반환
        // queryParameters.isEmpty(): Map이 비어있는지 확인
        if (queryParameters.isEmpty()) {
            return null;
        }

        // StringBuilder로 쿼리 스트링 재구성
        // new StringBuilder(): 가변 문자열 빌더 생성
        StringBuilder sb = new StringBuilder();

        // 첫 번째 파라미터인지 확인하는 플래그
        boolean first = true;

        // queryParameters.entrySet(): Map의 모든 키-값 쌍을 Set으로 반환
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            // entry.getKey(): 파라미터 이름
            String name = entry.getKey();
            // entry.getValue(): 파라미터 값들의 리스트
            List<String> values = entry.getValue();

            // 같은 이름의 파라미터가 여러 개 있을 수 있으므로 리스트 순회
            for (String value : values) {
                // 첫 번째 파라미터가 아니면 '&' 추가
                if (!first) {
                    // sb.append(): StringBuilder의 문자열 추가 메서드
                    sb.append("&");
                }
                first = false;

                // 파라미터 이름과 값을 추가
                sb.append(name).append("=").append(value);
            }
        }

        // sb.toString(): StringBuilder를 String으로 변환
        return sb.toString();
    }

    /**
     * HTTP 헤더들 반환
     * @return HTTP 헤더 객체 (never null)
     */
    public HttpHeaders getHeaders() {
        // 항상 non-null 반환 - 생성자에서 null 체크했으므로 안전
        return headers;
    }

    /**
     * 요청 바디를 바이트 배열로 반환
     * @return 요청 바디의 복사본 (방어적 복사)
     */
    public byte[] getBody() {
        // clone()으로 방어적 복사 - 외부에서 배열을 수정해도 원본이 변경되지 않도록 보호
        // body.clone(): 배열의 얕은 복사본 생성
        return body.clone();
    }

    /**
     * 요청 바디를 문자열로 반환
     * @return 요청 바디를 UTF-8로 디코딩한 문자열
     */
    public String getBodyAsString() {
        // UTF-8로 디코딩 - 웹에서 가장 널리 사용되는 인코딩이므로 기본값으로 사용
        // new String(): 바이트 배열을 문자열로 변환하는 생성자
        // StandardCharsets.UTF_8: UTF-8 인코딩 상수
        return new String(body, StandardCharsets.UTF_8);
    }

    // ========== 헤더 편의 메서드들 ==========

    /**
     * 특정 헤더의 첫 번째 값 반환
     * @param name 헤더 이름
     * @return 헤더 값 또는 null
     */
    public String getHeader(String name) {
        // HttpHeaders에 위임 - 헤더 처리 로직을 중앙화하여 일관성 보장
        // headers.getFirst(): HttpHeaders의 첫 번째 헤더 값 반환 메서드
        return headers.getFirst(name);
    }

    /**
     * Host 헤더 반환
     * @return Host 헤더 값 (예: "www.example.com:8080")
     */
    public String getHost() {
        // 자주 사용되는 헤더를 위한 편의 메서드 - 코드 가독성 향상
        // headers.getHost(): HttpHeaders의 Host 헤더 반환 메서드
        return headers.getHost();
    }

    /**
     * User-Agent 헤더 반환
     * @return User-Agent 헤더 값 (클라이언트 정보)
     */
    public String getUserAgent() {
        // 로깅이나 분석에 자주 사용되는 헤더 - 편의성 제공
        // headers.getUserAgent(): HttpHeaders의 User-Agent 헤더 반환 메서드
        return headers.getUserAgent();
    }

    /**
     * Content-Type 헤더 반환
     * @return Content-Type 헤더 값 (예: "application/json")
     */
    public String getContentType() {
        // 요청 바디 해석에 필수적인 헤더 - 자주 접근하므로 편의 메서드 제공
        // headers.getContentType(): HttpHeaders의 Content-Type 헤더 반환 메서드
        return headers.getContentType();
    }

    /**
     * Content-Length 반환
     * @return Content-Length 값 또는 실제 바디 크기
     */
    public int getContentLength() {
        // Content-Length 헤더를 우선 확인
        // headers.getContentLength(): HttpHeaders의 Content-Length 헤더 반환 메서드
        String length = headers.getContentLength();

        // 헤더가 없으면 실제 바디 크기 반환 - 실용적인 폴백 전략
        // length != null: null 체크
        // Integer.parseInt(): String을 int로 변환하는 정적 메서드
        // body.length: 배열의 길이 속성
        return length != null ? Integer.parseInt(length) : body.length;
    }

    // ========== 경로 파라미터 (라우팅에서 설정) ==========

    /**
     * 경로 파라미터 설정
     * 라우팅 시스템에서 호출됨 (예: /users/{id}에서 id 값 설정)
     *
     * @param name 파라미터 이름
     * @param value 파라미터 값
     */
    public void setPathParameter(String name, String value) {
        // mutable Map에 직접 저장 - 라우팅 과정에서만 호출되므로 안전
        // pathParameters.put(): Map의 키-값 쌍 저장 메서드
        pathParameters.put(name, value);
    }

    /**
     * 특정 경로 파라미터 값 반환
     *
     * @param name 파라미터 이름
     * @return 파라미터 값 또는 null
     */
    public String getPathParameter(String name) {
        // Map에서 직접 조회 - 단순하고 효율적
        // pathParameters.get(): Map의 값 조회 메서드
        return pathParameters.get(name);
    }

    /**
     * 모든 경로 파라미터 반환
     *
     * @return 경로 파라미터의 복사본
     */
    public Map<String, String> getPathParameters() {
        // 방어적 복사로 반환 - 외부에서 수정해도 원본에 영향 없음
        // new HashMap<>(): 새로운 HashMap 생성으로 복사
        return new HashMap<>(pathParameters);
    }

    // ========== 쿼리 파라미터 접근 메서드들 ==========

    /**
     * 특정 쿼리 파라미터의 첫 번째 값 반환
     * 서블릿 API와 호환성을 위한 메서드
     *
     * @param name 파라미터 이름
     * @return 파라미터의 첫 번째 값 또는 null
     */
    public String getParameter(String name) {
        // queryParameters.get(): Map의 값 조회 메서드
        List<String> values = queryParameters.get(name);

        // 값 리스트가 존재하고 비어있지 않으면 첫 번째 값 반환
        // values != null: null 체크
        // !values.isEmpty(): 리스트가 비어있지 않은지 확인
        // values.get(0): 첫 번째 요소 접근
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    /**
     * 특정 쿼리 파라미터의 모든 값 반환
     * 같은 이름의 파라미터가 여러 개 있을 때 사용 (?tag=java&tag=spring)
     *
     * @param name 파라미터 이름
     * @return 파라미터 값들의 배열 (never null)
     */
    public String[] getParameterValues(String name) {
        // queryParameters.get(): Map의 값 조회 메서드
        List<String> values = queryParameters.get(name);

        // 값이 없으면 빈 배열 반환 - null 대신 빈 배열로 일관성 보장
        if (values == null || values.isEmpty()) {
            // new String[0]: 크기가 0인 String 배열 생성
            return new String[0];
        }

        // List를 배열로 변환
        // values.toArray(): List를 배열로 변환하는 메서드
        // new String[0]: 타입 정보를 위한 빈 배열 (크기는 자동 조정됨)
        return values.toArray(new String[0]);
    }

    /**
     * 모든 쿼리 파라미터 이름 반환
     *
     * @return 파라미터 이름들의 복사본
     */
    public Set<String> getParameterNames() {
        // 방어적 복사로 반환 - 외부에서 수정해도 원본에 영향 없음
        // new HashSet<>(): 새로운 HashSet 생성으로 복사
        // queryParameters.keySet(): Map의 모든 키를 Set으로 반환
        return new HashSet<>(queryParameters.keySet());
    }

    /**
     * 모든 쿼리 파라미터를 Map으로 반환 (호환성 메서드)
     * 첫 번째 값만 포함하는 단순 Map으로 변환
     *
     * @return 쿼리 파라미터의 단순 Map (첫 번째 값만)
     */
    public Map<String, String> getQueryParameters() {
        // 결과를 저장할 Map 생성
        Map<String, String> result = new HashMap<>();

        // queryParameters.entrySet(): Map의 모든 키-값 쌍을 Set으로 반환
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            // entry.getKey(): 파라미터 이름
            String name = entry.getKey();
            // entry.getValue(): 파라미터 값들의 리스트
            List<String> values = entry.getValue();

            // 값이 있으면 첫 번째 값만 저장
            // !values.isEmpty(): 리스트가 비어있지 않은지 확인
            if (!values.isEmpty()) {
                // result.put(): Map의 키-값 쌍 저장 메서드
                // values.get(0): 첫 번째 요소 접근
                result.put(name, values.get(0));
            }
        }

        return result;
    }

    /**
     * 모든 쿼리 파라미터를 다중값 Map으로 반환
     * 고급 사용자를 위한 메서드 - 같은 이름의 여러 값을 모두 접근 가능
     *
     * @return 쿼리 파라미터의 다중값 Map (방어적 복사)
     */
    public Map<String, List<String>> getAllQueryParameters() {
        // 방어적 복사로 반환 - 외부에서 수정해도 원본에 영향 없음
        Map<String, List<String>> result = new HashMap<>();

        // queryParameters.entrySet(): Map의 모든 키-값 쌍을 Set으로 반환
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            // entry.getKey(): 파라미터 이름
            // entry.getValue(): 파라미터 값들의 리스트
            // new ArrayList<>(): 리스트의 방어적 복사
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        return result;
    }

    // ========== 속성 관리 (필터 체인에서 사용) ==========

    /**
     * 요청 속성 설정
     * 필터나 인터셉터에서 요청 처리 과정에서 메타데이터를 저장할 때 사용
     *
     * @param name 속성 이름
     * @param value 속성 값 (null이면 속성 제거)
     */
    public void setAttribute(String name, Object value) {
        if (value == null) {
            // null 값이면 속성 제거 - 메모리 절약 및 명확한 의미 전달
            // attributes.remove(): Map의 키-값 쌍 제거 메서드
            attributes.remove(name);
        } else {
            // 속성 저장 - Object 타입으로 다양한 데이터 지원
            // attributes.put(): Map의 키-값 쌍 저장 메서드
            attributes.put(name, value);
        }
    }

    /**
     * 요청 속성 조회
     *
     * @param name 속성 이름
     * @return 속성 값 또는 null
     */
    public Object getAttribute(String name) {
        // Map에서 직접 조회 - 단순하고 효율적
        return attributes.get(name);
    }

    /**
     * 특정 타입으로 속성 조회 (타입 안전성 제공)
     *
     * @param name 속성 이름
     * @param type 기대하는 타입
     * @return 지정된 타입의 속성 값 또는 null
     */
    @SuppressWarnings("unchecked")
    // @SuppressWarnings: 컴파일러 경고를 억제하는 어노테이션
    // "unchecked": 제네릭 타입 캐스팅 경고 억제
    public <T> T getAttribute(String name, Class<T> type) {
        // <T>: 제네릭 타입 매개변수
        // Class<T>: 타입 정보를 담는 클래스 객체

        // 속성 조회
        Object value = attributes.get(name);

        // 타입 확인 후 캐스팅 - ClassCastException 방지
        // value != null: null 체크
        // type.isInstance(): 객체가 지정된 타입인지 확인하는 메서드
        if (value != null && type.isInstance(value)) {
            // (T): 제네릭 타입으로 캐스팅
            return (T) value;
        }

        // 타입이 맞지 않거나 null이면 null 반환
        return null;
    }

    /**
     * 모든 속성 이름 반환
     *
     * @return 속성 이름들의 복사본
     */
    public Set<String> getAttributeNames() {
        // 방어적 복사로 반환 - 외부에서 수정해도 원본에 영향 없음
        // new HashSet<>(): 새로운 HashSet 생성으로 복사
        // attributes.keySet(): Map의 모든 키를 Set으로 반환
        return new HashSet<>(attributes.keySet());
    }

    // ========== 편의 메서드들 ==========

    /**
     * 요청이 AJAX 요청인지 확인
     *
     * @return AJAX 요청이면 true
     */
    public boolean isAjax() {
        // X-Requested-With 헤더로 AJAX 요청 판별 - jQuery 등에서 자동 설정
        String requestedWith = getHeader("X-Requested-With");

        // "XMLHttpRequest".equalsIgnoreCase(): 대소문자 무관 문자열 비교
        return "XMLHttpRequest".equalsIgnoreCase(requestedWith);
    }

    /**
     * 요청 바디가 JSON 형태인지 확인
     *
     * @return JSON 요청이면 true
     */
    public boolean isJson() {
        // Content-Type 헤더로 JSON 판별 - API 서버에서 자주 사용
        String contentType = getContentType();

        // contentType != null: null 체크
        // contentType.toLowerCase(): 소문자로 변환
        // .contains(): 문자열 포함 여부 확인
        return contentType != null && contentType.toLowerCase().contains("application/json");
    }

    /**
     * 요청 바디가 폼 데이터인지 확인
     *
     * @return 폼 데이터 요청이면 true
     */
    public boolean isFormData() {
        // Content-Type 헤더로 폼 데이터 판별 - HTML 폼 제출에서 사용
        String contentType = getContentType();

        // "application/x-www-form-urlencoded": HTML 폼의 기본 인코딩 타입
        return contentType != null &&
                contentType.toLowerCase().contains("application/x-www-form-urlencoded");
    }

    /**
     * HTTP 메서드가 안전한(Safe) 메서드인지 확인
     *
     * @return 안전한 메서드이면 true
     */
    public boolean isSafeMethod() {
        // HttpMethod 클래스의 유틸리티 메서드에 위임 - 메서드별 특성 판별 로직 중앙화
        // HttpMethod.isSafe(): HttpMethod 클래스의 안전성 확인 정적 메서드
        return HttpMethod.isSafe(method);
    }

    /**
     * 요청 바디가 있는지 확인
     *
     * @return 바디가 있으면 true
     */
    public boolean hasBody() {
        // 바디 길이로 판별 - 단순하고 직관적
        // body.length > 0: 배열 길이가 0보다 큰지 확인
        return body.length > 0;
    }

    /**
     * 쿼리 스트링이 있는지 확인 (쿼리 파라미터 기반)
     *
     * @return 쿼리 파라미터가 있으면 true
     */
    public boolean hasQueryString() {
        // 쿼리 파라미터 맵이 비어있지 않으면 쿼리 스트링이 있는 것으로 판단
        // !queryParameters.isEmpty(): Map이 비어있지 않은지 확인
        return !queryParameters.isEmpty();
    }

    /**
     * 특정 쿼리 파라미터가 존재하는지 확인
     *
     * @param name 파라미터 이름
     * @return 파라미터가 존재하면 true
     */
    public boolean hasParameter(String name) {
        // queryParameters.containsKey(): Map에 특정 키가 있는지 확인
        return queryParameters.containsKey(name);
    }

    // ========== Object 메서드 오버라이드 ==========

    /**
     * 요청 정보를 문자열로 표현
     * 디버깅과 로깅에 유용
     */
    @Override
    public String toString() {
        // StringBuilder 사용으로 효율적인 문자열 조합
        // new StringBuilder(): 가변 문자열 빌더 생성
        StringBuilder sb = new StringBuilder();

        // sb.append(): StringBuilder의 문자열 추가 메서드
        sb.append("HttpRequest{");
        sb.append("method=").append(method);
        sb.append(", path='").append(path).append('\'');
        sb.append(", version='").append(version).append('\'');

        // 쿼리 파라미터가 있을 때만 포함 - 간결성을 위해
        if (!queryParameters.isEmpty()) {
            sb.append(", queryParams=").append(queryParameters.size());
        }

        sb.append(", contentLength=").append(getContentLength());

        // headers.size(): HttpHeaders의 크기 반환 메서드
        sb.append(", headerCount=").append(headers.size());
        sb.append('}');

        // sb.toString(): StringBuilder를 String으로 변환
        return sb.toString();
    }

    /**
     * 두 요청 객체가 같은지 비교
     * 테스트와 캐싱에서 사용
     */
    @Override
    public boolean equals(Object o) {
        // 동일 객체 참조 확인 - 가장 빠른 비교
        // this == o: 참조 동등성 확인
        if (this == o) return true;

        // null 체크와 클래스 타입 확인 - 안전한 비교를 위해
        // o == null: null 체크
        // getClass() != o.getClass(): 클래스 타입 비교
        // getClass(): Object의 클래스 정보 반환 메서드
        if (o == null || getClass() != o.getClass()) return false;

        // 타입 캐스팅
        HttpRequest that = (HttpRequest) o;

        // 모든 주요 필드 비교 - 완전한 동등성 확인
        // Objects.equals(): null-safe 동등성 비교 유틸리티 메서드
        // Arrays.equals(): 배열의 내용 비교 메서드 (배열은 Arrays.equals 사용)
        return Objects.equals(method, that.method) &&
                Objects.equals(path, that.path) &&
                Objects.equals(version, that.version) &&
                Objects.equals(headers, that.headers) &&
                Arrays.equals(body, that.body) &&
                Objects.equals(queryParameters, that.queryParameters);
    }

    /**
     * 해시 코드 생성
     * HashMap, HashSet 등에서 사용
     */
    @Override
    public int hashCode() {
        // Objects.hash로 여러 필드 조합 - 표준적인 방법
        // Objects.hash(): 여러 객체의 해시코드를 조합하는 유틸리티 메서드
        int result = Objects.hash(method, path, version, headers, queryParameters);

        // 배열은 별도로 해시 코드 계산 후 조합
        // Arrays.hashCode(): 배열의 해시코드를 계산하는 메서드
        // 31 * result: 해시코드 조합을 위한 소수 곱셈 (해시 충돌 최소화)
        result = 31 * result + Arrays.hashCode(body);

        return result;
    }
}