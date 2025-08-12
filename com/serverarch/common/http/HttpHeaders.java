package com.serverarch.common.http;

import java.util.*;

/**
 * HTTP 헤더들을 관리하는 클래스
 *
 * 설계 목표:
 * 1. 대소문자 무관한 헤더 이름 처리 (HTTP 표준 준수)
 * 2. 멀티값 헤더 지원 (예: Accept-Encoding: gzip, deflate)
 * 3. 자주 사용되는 헤더들을 위한 편의 메서드 제공
 * 4. 기존 HttpRequest/HttpResponse 코드와 완벽 호환
 *
 * HTTP 헤더 특성:
 * - 헤더 이름은 대소문자 무관 (RFC 7230)
 * - 하나의 헤더에 여러 값이 올 수 있음
 * - 일부 헤더는 특별한 의미를 가짐 (Content-Type, Content-Length 등)
 */
public class HttpHeaders {

    // ========== 인스턴스 필드 ==========

    /**
     * 헤더 저장소 - 헤더명을 소문자로 정규화하여 저장
     *
     * LinkedHashMap을 사용하는 이유:
     * 1. 헤더 순서 보존: HTTP 응답에서 헤더 순서가 중요할 수 있음
     * 2. 빠른 조회 성능: HashMap의 O(1) 조회 성능 유지
     * 3. 예측 가능한 순서: 디버깅과 테스트에 유리
     *
     * Key: 소문자로 정규화된 헤더 이름 (예: "content-type")
     * Value: 해당 헤더의 모든 값들을 담은 리스트
     */
    private final Map<String, List<String>> headers = new LinkedHashMap<>();

    /**
     * 기본 생성자
     * 빈 헤더 컬렉션으로 초기화
     *
     * 명시적으로 빈 상태로 초기화하는 이유:
     * - 필요시 기본 헤더들을 추가할 수 있도록 준비
     * - 초기화 로직을 명확히 표현
     */
    public HttpHeaders() {
        // LinkedHashMap은 이미 기본 생성자에서 빈 맵으로 초기화됨
        // 추가적인 초기화 로직이 필요한 경우 여기에 작성
    }

    /**
     * 복사 생성자
     * 기존 헤더들을 복사하여 새로운 인스턴스 생성
     *
     * 복사 생성자를 제공하는 이유:
     * 1. 헤더 수정 시 원본을 보호
     * 2. 템플릿 패턴에서 기본 헤더를 복사해서 사용
     * 3. 스레드 안전성 확보
     *
     * @param other 복사할 헤더 객체
     */
    public HttpHeaders(HttpHeaders other) {
        // null 체크: 방어적 프로그래밍
        if (other != null) {
            // 깊은 복사 수행 - 리스트까지 복사하여 독립성 보장
            // other.headers.entrySet(): Map의 모든 key-value 쌍을 Set<Entry>로 반환
            for (Map.Entry<String, List<String>> entry : other.headers.entrySet()) {
                // entry.getKey(): Map.Entry의 키(헤더 이름) 반환
                // entry.getValue(): Map.Entry의 값(헤더 값 리스트) 반환
                // new ArrayList<>(): 새로운 ArrayList 인스턴스 생성으로 깊은 복사
                this.headers.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
    }

    // ========== 헤더 추가/설정 메서드들 ==========

    /**
     * 헤더 값 추가 (기존 값에 추가)
     * 동일한 헤더에 여러 값이 있을 수 있는 경우 사용
     *
     * 기존 값에 추가하는 이유:
     * - Accept-Encoding: gzip, deflate처럼 멀티값 헤더 지원
     * - HTTP 표준에서 허용하는 방식
     *
     * @param name 헤더 이름 (대소문자 무관)
     * @param value 헤더 값
     */
    public void add(String name, String value) {
        // null 체크 - 잘못된 입력 방지
        // 방어적 프로그래밍: 예외 상황에서도 안정적 동작
        if (name == null || value == null) {
            return; // 조용히 무시 (로그 기록 권장)
        }

        // 헤더 이름을 소문자로 정규화 - HTTP 표준에 따라 대소문자 무관 처리
        // name.toLowerCase(): String 클래스의 소문자 변환 메서드
        // .trim(): 앞뒤 공백 제거 메서드
        String normalizedName = name.toLowerCase().trim();

        // 빈 헤더 이름 방지
        // isEmpty(): String의 길이가 0인지 확인하는 메서드
        if (normalizedName.isEmpty()) {
            return;
        }

        // 기존 값 리스트에 추가
        // computeIfAbsent: 키가 없으면 새 값을 계산해서 추가, 있으면 기존 값 반환
        // k -> new ArrayList<>(): 람다 표현식으로 키가 없을 때 새 ArrayList 생성
        // .add(): List 인터페이스의 요소 추가 메서드
        // value.trim(): 헤더 값의 앞뒤 공백 제거
        headers.computeIfAbsent(normalizedName, k -> new ArrayList<>()).add(value.trim());
    }

    /**
     * 헤더 값 설정 (기존 값 대체)
     * 하나의 헤더에 하나의 값만 있어야 하는 경우 사용
     *
     * 기존 값을 대체하는 이유:
     * - Content-Type처럼 단일 값만 허용하는 헤더
     * - 헤더 값을 완전히 새로 설정할 때
     *
     * @param name 헤더 이름 (대소문자 무관)
     * @param value 헤더 값 (null이면 헤더 제거)
     */
    public void set(String name, String value) {
        // null 체크
        if (name == null) {
            return;
        }

        // 헤더 이름 정규화 (add 메서드와 동일한 로직)
        String normalizedName = name.toLowerCase().trim();

        // 빈 헤더 이름 방지
        if (normalizedName.isEmpty()) {
            return;
        }

        if (value == null) {
            // 값이 null이면 헤더 제거
            // remove(): Map 인터페이스의 키-값 쌍 제거 메서드
            headers.remove(normalizedName);
        } else {
            // 새 리스트 생성하여 기존 값들 모두 대체
            List<String> values = new ArrayList<>();
            // add(): List의 요소 추가 메서드
            values.add(value.trim());
            // put(): Map의 키-값 쌍 저장 메서드 (기존 값 덮어씀)
            headers.put(normalizedName, values);
        }
    }

    /**
     * 헤더 제거
     *
     * 별도 메서드로 제공하는 이유:
     * - API의 명확성
     * - set(name, null)보다 의도가 명확함
     *
     * @param name 제거할 헤더 이름 (대소문자 무관)
     */
    public void remove(String name) {
        if (name != null) {
            // 헤더 이름 정규화 후 제거
            headers.remove(name.toLowerCase().trim());
        }
    }

    // ========== 헤더 조회 메서드들 ==========

    /**
     * 지정된 헤더의 첫 번째 값 반환
     * 가장 많이 사용되는 조회 메서드
     *
     * 첫 번째 값만 반환하는 이유:
     * - 대부분의 헤더는 단일 값
     * - 멀티값 헤더에서도 첫 번째 값이 주요 값인 경우가 많음
     *
     * @param name 헤더 이름 (대소문자 무관)
     * @return 첫 번째 헤더 값 또는 null (헤더가 없는 경우)
     */
    public String getFirst(String name) {
        if (name == null) {
            return null;
        }

        // 헤더 값 리스트 조회
        // get(): Map의 값 조회 메서드
        List<String> values = headers.get(name.toLowerCase().trim());

        // 리스트가 존재하고 비어있지 않으면 첫 번째 값 반환
        // values != null: null 체크
        // !values.isEmpty(): 리스트가 비어있지 않은지 확인
        // values.get(0): List의 첫 번째 요소 반환 (인덱스 0)
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    /**
     * 지정된 헤더의 모든 값 반환
     * 멀티값 헤더 처리용
     *
     * 방어적 복사를 사용하는 이유:
     * - 외부에서 반환된 리스트를 수정해도 원본에 영향 없음
     * - 캡슐화 원칙 준수
     *
     * @param name 헤더 이름 (대소문자 무관)
     * @return 헤더 값들의 복사본 (변경해도 원본에 영향 없음)
     */
    public List<String> get(String name) {
        if (name == null) {
            // Collections.emptyList(): 불변의 빈 리스트 반환
            // 메모리 효율적이고 안전함
            return Collections.emptyList();
        }

        // 헤더 값 리스트 조회
        List<String> values = headers.get(name.toLowerCase().trim());

        // 방어적 복사로 반환 - 외부에서 수정해도 원본에 영향 없음
        // values != null: null 체크
        // new ArrayList<>(values): 새로운 ArrayList로 복사 생성
        // Collections.emptyList(): 값이 없을 때 빈 리스트 반환
        return values != null ? new ArrayList<>(values) : Collections.emptyList();
    }

    /**
     * 모든 헤더 이름 반환
     *
     * Set을 반환하는 이유:
     * - 헤더 이름은 중복되지 않음
     * - 순서보다는 유일성이 중요
     *
     * @return 헤더 이름들의 복사본 (소문자로 정규화된 상태)
     */
    public Set<String> getNames() {
        // 방어적 복사로 반환
        // headers.keySet(): Map의 모든 키를 Set으로 반환
        // new HashSet<>(): 새로운 HashSet으로 복사 생성
        return new HashSet<>(headers.keySet());
    }

    /**
     * 지정된 헤더가 존재하는지 확인
     *
     * boolean 반환하는 이유:
     * - 단순한 존재 여부만 확인
     * - 성능상 getFirst()보다 효율적
     *
     * @param name 확인할 헤더 이름 (대소문자 무관)
     * @return 헤더가 존재하면 true
     */
    public boolean contains(String name) {
        // name != null: null 체크
        // headers.containsKey(): Map에서 키 존재 여부 확인
        return name != null && headers.containsKey(name.toLowerCase().trim());
    }

    /**
     * 전체 헤더 수 반환
     *
     * 헤더 수를 제공하는 이유:
     * - 디버깅 목적
     * - 메모리 사용량 추정
     *
     * @return 등록된 헤더의 개수
     */
    public int size() {
        // size(): Map의 키-값 쌍 개수 반환
        return headers.size();
    }

    /**
     * 헤더가 비어있는지 확인
     *
     * @return 헤더가 없으면 true
     */
    public boolean isEmpty() {
        // isEmpty(): Map이 비어있는지 확인
        return headers.isEmpty();
    }

    // ========== 자주 사용되는 헤더들을 위한 편의 메서드들 ==========

    /**
     * Content-Type 헤더 반환
     * HTTP 요청/응답에서 가장 중요한 헤더 중 하나
     *
     * 편의 메서드를 제공하는 이유:
     * - 자주 사용되는 헤더라서 타이핑 줄임
     * - 오타 방지
     * - 코드 가독성 향상
     *
     * @return Content-Type 값 (예: "text/html; charset=UTF-8")
     */
    public String getContentType() {
        // getFirst(): 앞서 정의한 첫 번째 값 조회 메서드 재사용
        return getFirst("content-type");
    }

    /**
     * Content-Type 헤더 설정
     *
     * @param contentType Content-Type 값
     */
    public void setContentType(String contentType) {
        // set(): 앞서 정의한 헤더 설정 메서드 재사용
        // "Content-Type": HTTP 표준 헤더 이름 (대소문자는 자동으로 정규화됨)
        set("Content-Type", contentType);
    }

    /**
     * Content-Length 헤더 반환
     * 요청/응답 바디의 크기를 나타냄
     *
     * String으로 반환하는 이유:
     * - HTTP 헤더는 모두 문자열 형태
     * - 숫자 변환은 사용하는 곳에서 처리
     *
     * @return Content-Length 값 (문자열)
     */
    public String getContentLength() {
        return getFirst("content-length");
    }

    /**
     * Content-Length 헤더 설정 (정수값)
     *
     * long 타입을 받는 이유:
     * - 파일 크기는 int 범위를 초과할 수 있음
     * - 안전한 타입 선택
     *
     * @param length 바디 크기
     */
    public void setContentLength(long length) {
        // String.valueOf(): long을 String으로 변환하는 정적 메서드
        set("Content-Length", String.valueOf(length));
    }

    /**
     * Host 헤더 반환
     * HTTP/1.1에서 필수 헤더
     *
     * Host 헤더가 중요한 이유:
     * - 가상 호스팅 지원
     * - HTTP/1.1에서 필수
     *
     * @return Host 값 (예: "www.example.com:8080")
     */
    public String getHost() {
        return getFirst("host");
    }

    /**
     * Host 헤더 설정
     *
     * @param host Host 값
     */
    public void setHost(String host) {
        set("Host", host);
    }

    /**
     * User-Agent 헤더 반환
     * 클라이언트 정보를 나타냄
     *
     * User-Agent의 용도:
     * - 브라우저/클라이언트 식별
     * - 통계 수집
     * - 호환성 처리
     *
     * @return User-Agent 값
     */
    public String getUserAgent() {
        return getFirst("user-agent");
    }

    /**
     * User-Agent 헤더 설정
     *
     * @param userAgent User-Agent 값
     */
    public void setUserAgent(String userAgent) {
        set("User-Agent", userAgent);
    }

    /**
     * Accept 헤더 반환
     * 클라이언트가 수용 가능한 미디어 타입들
     *
     * Accept 헤더의 용도:
     * - 콘텐츠 협상 (Content Negotiation)
     * - 적절한 응답 형식 결정
     *
     * @return Accept 값 (예: "text/html,application/json")
     */
    public String getAccept() {
        return getFirst("accept");
    }

    /**
     * Accept 헤더 설정
     *
     * @param accept Accept 값
     */
    public void setAccept(String accept) {
        set("Accept", accept);
    }

    /**
     * Authorization 헤더 반환
     * 인증 정보를 담음
     *
     * Authorization 헤더의 중요성:
     * - 보안 인증의 핵심
     * - 다양한 인증 방식 지원 (Basic, Bearer 등)
     *
     * @return Authorization 값 (예: "Bearer token123")
     */
    public String getAuthorization() {
        return getFirst("authorization");
    }

    /**
     * Authorization 헤더 설정
     *
     * @param authorization Authorization 값
     */
    public void setAuthorization(String authorization) {
        set("Authorization", authorization);
    }

    /**
     * Cache-Control 헤더 반환
     * 캐싱 동작을 제어
     *
     * Cache-Control의 중요성:
     * - 성능 최적화
     * - 네트워크 트래픽 절약
     *
     * @return Cache-Control 값 (예: "no-cache, no-store")
     */
    public String getCacheControl() {
        return getFirst("cache-control");
    }

    /**
     * Cache-Control 헤더 설정
     *
     * @param cacheControl Cache-Control 값
     */
    public void setCacheControl(String cacheControl) {
        set("Cache-Control", cacheControl);
    }

    /**
     * Connection 헤더 반환
     * 연결 관리 옵션
     *
     * Connection 헤더의 용도:
     * - Keep-Alive 설정
     * - 연결 관리 최적화
     *
     * @return Connection 값 (예: "keep-alive", "close")
     */
    public String getConnection() {
        return getFirst("connection");
    }

    /**
     * Connection 헤더 설정
     *
     * @param connection Connection 값
     */
    public void setConnection(String connection) {
        set("Connection", connection);
    }

    // ========== 헤더 조작 편의 메서드들 ==========

    /**
     * 모든 헤더 삭제
     * 헤더 컬렉션을 완전히 비움
     *
     * clear 메서드를 제공하는 이유:
     * - 대량 헤더 제거 시 효율적
     * - 초기화 용도
     */
    public void clear() {
        // clear(): Map의 모든 키-값 쌍 제거
        headers.clear();
    }

    /**
     * 다른 HttpHeaders의 모든 헤더를 현재 객체에 추가
     * 기존 헤더와 병합됨
     *
     * 병합 기능을 제공하는 이유:
     * - 공통 헤더와 특정 헤더 결합
     * - 헤더 템플릿 활용
     *
     * @param other 추가할 헤더들
     */
    public void addAll(HttpHeaders other) {
        if (other == null) {
            return; // 방어적 프로그래밍
        }

        // 다른 객체의 모든 헤더를 순회하며 추가
        // other.headers.entrySet(): 다른 객체의 헤더 맵의 모든 엔트리
        for (Map.Entry<String, List<String>> entry : other.headers.entrySet()) {
            // entry.getKey(): 헤더 이름
            String headerName = entry.getKey();

            // 각 헤더의 모든 값들을 추가
            // entry.getValue(): 해당 헤더의 값 리스트
            for (String value : entry.getValue()) {
                // add 메서드 사용으로 기존 값에 추가 (덮어쓰지 않음)
                add(headerName, value);
            }
        }
    }

    /**
     * 헤더 값들을 쉼표로 구분된 단일 문자열로 반환
     * Accept, Accept-Encoding 같은 멀티값 헤더에 유용
     *
     * 쉼표 구분 문자열을 제공하는 이유:
     * - HTTP 표준에서 멀티값 헤더는 쉼표로 구분
     * - 클라이언트/서버 간 호환성
     *
     * @param name 헤더 이름
     * @return 쉼표로 구분된 헤더 값들 또는 null
     */
    public String getCommaSeparated(String name) {
        // get(): 앞서 정의한 모든 값 조회 메서드
        List<String> values = get(name);

        // isEmpty(): List가 비어있는지 확인
        if (values.isEmpty()) {
            return null;
        }

        // 쉼표와 공백으로 구분하여 조합
        // String.join(): Java 8+에서 제공하는 문자열 결합 메서드
        // ", ": 구분자 (쉼표 + 공백)
        // values: 결합할 문자열 컬렉션
        return String.join(", ", values);
    }

    /**
     * 쉼표로 구분된 문자열을 파싱하여 멀티값 헤더로 설정
     * "gzip, deflate, br" -> ["gzip", "deflate", "br"]
     *
     * 파싱 기능을 제공하는 이유:
     * - 클라이언트에서 받은 헤더 처리
     * - 문자열을 구조화된 데이터로 변환
     *
     * @param name 헤더 이름
     * @param commaSeparatedValues 쉼표로 구분된 값들
     */
    public void setCommaSeparated(String name, String commaSeparatedValues) {
        if (name == null || commaSeparatedValues == null) {
            return; // 방어적 프로그래밍
        }

        // 기존 값들 제거
        remove(name);

        // 쉼표로 분리하여 각각 추가
        // split(","): String의 분할 메서드, 쉼표를 기준으로 배열로 분할
        String[] values = commaSeparatedValues.split(",");

        // for-each 루프로 각 값 처리
        for (String value : values) {
            // trim(): 앞뒤 공백 제거
            String trimmedValue = value.trim();

            // isEmpty(): 빈 문자열 체크
            if (!trimmedValue.isEmpty()) {
                // 유효한 값만 추가
                add(name, trimmedValue);
            }
        }
    }

    // ========== Object 메서드 오버라이드 ==========

    /**
     * 헤더 정보를 문자열로 표현
     * 디버깅과 로깅에 유용
     *
     * toString()을 오버라이드하는 이유:
     * - 디버깅 시 객체 내용 확인 용이
     * - 로깅에서 의미 있는 정보 제공
     */
    @Override
    public String toString() {
        // isEmpty(): 빈 헤더인지 확인
        if (headers.isEmpty()) {
            return "HttpHeaders{}"; // 빈 상태 표현
        }

        // StringBuilder: 문자열 연결에 효율적인 클래스
        StringBuilder sb = new StringBuilder();
        sb.append("HttpHeaders{"); // append(): 문자열 추가 메서드

        boolean first = true; // 첫 번째 요소 표시용 플래그

        // 모든 헤더를 순회하며 문자열 구성
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!first) {
                sb.append(", "); // 구분자 추가 (첫 번째가 아닌 경우)
            }

            // 헤더명=값들 형태로 표현
            // append(): StringBuilder의 문자열 추가 메서드
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false; // 첫 번째 플래그 해제
        }

        sb.append("}");
        return sb.toString(); // StringBuilder를 String으로 변환
    }

    /**
     * 두 HttpHeaders 객체가 같은지 비교
     * 테스트에서 주로 사용
     *
     * equals()를 오버라이드하는 이유:
     * - 객체의 논리적 동등성 정의
     * - 테스트 코드에서 검증 용도
     * - HashSet, HashMap 등에서 올바른 동작 보장
     */
    @Override
    public boolean equals(Object o) {
        // 동일 객체 참조 확인 (성능 최적화)
        if (this == o) return true;

        // null 체크와 클래스 타입 확인
        // getClass(): 객체의 실제 클래스 반환
        if (o == null || getClass() != o.getClass()) return false;

        // 타입 캐스팅
        HttpHeaders that = (HttpHeaders) o;

        // 헤더 맵 전체 비교
        // Objects.equals(): null-safe 동등성 비교 유틸리티
        return Objects.equals(headers, that.headers);
    }

    /**
     * 해시 코드 생성
     * HashMap, HashSet 등에서 사용
     *
     * hashCode()를 오버라이드하는 이유:
     * - equals()를 오버라이드했으면 hashCode()도 오버라이드 필요
     * - 해시 기반 컬렉션에서 올바른 동작 보장
     */
    @Override
    public int hashCode() {
        // Objects.hash(): 여러 값의 해시코드를 조합하는 유틸리티
        return Objects.hash(headers);
    }

    // ========== 내부 유틸리티 메서드들 ==========

    /**
     * 헤더 이름이 유효한지 검증
     * HTTP 표준에 따른 토큰 문자만 허용
     *
     * private 메서드로 구현한 이유:
     * - 내부적으로만 사용하는 검증 로직
     * - 캡슐화 원칙 준수
     *
     * @param name 검증할 헤더 이름
     * @return 유효하면 true
     */
    private boolean isValidHeaderName(String name) {
        // null이나 빈 문자열 체크
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        // HTTP 토큰 문자만 허용 (RFC 7230)
        // for 루프로 각 문자 검사
        for (int i = 0; i < name.length(); i++) {
            // charAt(i): 지정된 인덱스의 문자 반환
            char c = name.charAt(i);

            // isTokenChar(): 아래에서 정의한 토큰 문자 검증 메서드
            if (!isTokenChar(c)) {
                return false; // 유효하지 않은 문자 발견 시 즉시 반환
            }
        }

        return true; // 모든 문자가 유효함
    }

    /**
     * HTTP 토큰에서 사용할 수 있는 문자인지 확인
     * RFC 7230에 정의된 tchar 규칙
     *
     * private 메서드로 구현한 이유:
     * - 헤더 이름 검증에서만 사용
     * - HTTP 표준 규칙의 정확한 구현
     */
    private boolean isTokenChar(char c) {
        // HTTP 토큰 문자 규칙:
        // - 영문 대문자 (A-Z)
        // - 영문 소문자 (a-z)
        // - 숫자 (0-9)
        // - 특수문자: ! # $ % & ' * + - . ^ _ ` | ~

        return (c >= 'A' && c <= 'Z') ||      // 대문자 범위 확인
                (c >= 'a' && c <= 'z') ||      // 소문자 범위 확인
                (c >= '0' && c <= '9') ||      // 숫자 범위 확인
                c == '!' || c == '#' || c == '$' || c == '%' || c == '&' ||
                c == '\'' || c == '*' || c == '+' || c == '-' || c == '.' ||
                c == '^' || c == '_' || c == '`' || c == '|' || c == '~';
    }
}