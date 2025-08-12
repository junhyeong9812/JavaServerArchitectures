package com.serverarch.common.http;

import java.util.Objects;
import java.util.Set;

/**
 * HTTP 버전을 관리하고 처리하는 클래스입니다.
 *
 * 이 클래스는 다양한 HTTP 버전들의 특성과 호환성을 관리하며,
 * 버전 간 비교, 검증, 기능 지원 여부 확인 등의 기능을 제공합니다.
 *
 * 지원하는 HTTP 버전:
 * - HTTP/0.9: 최초의 HTTP 버전 (GET만 지원)
 * - HTTP/1.0: 헤더와 상태 코드 도입
 * - HTTP/1.1: 지속 연결, 청킹, 파이프라이닝 등
 * - HTTP/2.0: 바이너리 프로토콜, 멀티플렉싱, 서버 푸시
 * - HTTP/3.0: QUIC 기반, UDP 사용
 *
 * final 클래스로 선언한 이유:
 * - 상속을 통한 변경 방지
 * - 버전 정보의 무결성 보장
 * - 성능 최적화 (final 클래스는 JVM이 더 효율적으로 처리)
 *
 * Comparable 인터페이스 구현 이유:
 * - 버전 간 대소 비교 기능 제공
 * - 컬렉션 정렬 지원
 * - 버전 범위 검사 등에 활용
 */
public final class HttpVersion implements Comparable<HttpVersion> {

    // ========== 표준 HTTP 버전 상수 ==========
    // public static final: 공개 정적 상수
    // 모든 곳에서 동일한 인스턴스 사용 보장 (싱글톤 패턴과 유사)

    /**
     * HTTP/0.9 - 최초의 HTTP 버전
     *
     * 특징:
     * - GET 메서드만 지원
     * - 헤더 없음
     * - 상태 코드 없음
     * - HTML만 전송 가능
     * - 연결은 요청 후 즉시 종료
     *
     * 역사적 의미:
     * - 1991년 팀 버너스리가 설계
     * - 월드 와이드 웹의 시작
     * - 현재는 거의 사용되지 않음
     */
    public static final HttpVersion HTTP_0_9 = new HttpVersion(0, 9);

    /**
     * HTTP/1.0 - 첫 번째 공식 버전 (RFC 1945)
     *
     * 특징:
     * - 헤더 지원
     * - 상태 코드 도입
     * - POST, HEAD 메서드 추가
     * - Content-Type 지원
     * - 각 요청마다 새 연결 생성
     *
     * 도입된 주요 기능:
     * - HTTP 헤더 시스템
     * - 상태 코드 (200, 404 등)
     * - 다양한 미디어 타입 지원
     */
    public static final HttpVersion HTTP_1_0 = new HttpVersion(1, 0);

    /**
     * HTTP/1.1 - 가장 널리 사용되는 버전 (RFC 2616, RFC 7230-7235)
     *
     * 특징:
     * - 지속 연결 (Keep-Alive)
     * - 청크 인코딩 (Chunked Encoding)
     * - 파이프라이닝
     * - PUT, DELETE, OPTIONS, TRACE 메서드 추가
     * - Host 헤더 필수
     * - 캐시 제어 개선
     *
     * 성능 개선 사항:
     * - 연결 재사용으로 오버헤드 감소
     * - 청크 인코딩으로 스트리밍 지원
     * - 가상 호스팅 지원 (Host 헤더)
     */
    public static final HttpVersion HTTP_1_1 = new HttpVersion(1, 1);

    /**
     * HTTP/2.0 - 현대적인 바이너리 프로토콜 (RFC 7540)
     *
     * 특징:
     * - 바이너리 프레이밍
     * - 멀티플렉싱 (단일 연결에서 여러 요청/응답)
     * - 서버 푸시
     * - 헤더 압축 (HPACK)
     * - 스트림 우선순위
     * - HTTPS 필수 (사실상)
     *
     * 혁신적 변화:
     * - 텍스트 기반에서 바이너리 기반으로 전환
     * - 헤드 오브 라인 블로킹 해결 (애플리케이션 레벨)
     * - 대폭적인 성능 향상
     */
    public static final HttpVersion HTTP_2_0 = new HttpVersion(2, 0);

    /**
     * HTTP/3.0 - QUIC 기반의 최신 버전 (RFC 9114)
     *
     * 특징:
     * - QUIC 전송 프로토콜 사용 (UDP 기반)
     * - 내장된 TLS 1.3
     * - 향상된 멀티플렉싱
     * - 연결 마이그레이션
     * - 0-RTT 연결 설정
     * - 헤드 오브 라인 블로킹 제거
     *
     * 근본적 변화:
     * - TCP에서 UDP로 전환
     * - 전송 계층부터 재설계
     * - 모바일 네트워크 최적화
     */
    public static final HttpVersion HTTP_3_0 = new HttpVersion(3, 0);

    // ========== 인스턴스 필드 ==========

    /**
     * HTTP 메이저 버전
     *
     * final로 선언한 이유:
     * - 생성 후 변경 불가능 (불변 객체)
     * - 스레드 안전성 보장
     * - 버전 정보의 무결성 보장
     */
    private final int major;

    /**
     * HTTP 마이너 버전
     *
     * int 타입을 사용한 이유:
     * - 간단한 정수 비교 가능
     * - 메모리 효율성
     * - 산술 연산 지원
     */
    private final int minor;

    /**
     * 문자열 표현 (캐시됨)
     *
     * 문자열을 캐시하는 이유:
     * - toString() 호출 시 매번 생성하지 않음
     * - 성능 최적화 (String 생성 비용 절약)
     * - 메모리 효율성 (동일한 문자열 재사용)
     */
    private final String stringRepresentation;

    /**
     * 지원되는 표준 HTTP 버전들
     *
     * Set으로 관리하는 이유:
     * - 빠른 포함 여부 확인 (O(1))
     * - 중복 방지
     * - 표준 버전의 명확한 정의
     *
     * static final로 선언한 이유:
     * - 모든 인스턴스에서 공유
     * - 한 번만 초기화되어 메모리 효율적
     * - 불변 컬렉션으로 안전성 보장
     */
    private static final Set<HttpVersion> STANDARD_VERSIONS = Set.of(
            HTTP_0_9, HTTP_1_0, HTTP_1_1, HTTP_2_0, HTTP_3_0
    );

    // ========== 생성자 ==========

    /**
     * 메이저와 마이너 버전으로 HttpVersion을 생성합니다.
     *
     * @param major 메이저 버전 (0 이상)
     * @param minor 마이너 버전 (0 이상)
     * @throws IllegalArgumentException 버전이 음수인 경우
     */
    public HttpVersion(int major, int minor) {
        // 입력 검증: 음수 버전 방지
        // HTTP 버전은 0.9부터 시작하므로 음수는 유효하지 않음
        if (major < 0 || minor < 0) {
            // IllegalArgumentException: 잘못된 인수 예외
            // 생성자에서 잘못된 파라미터 전달 시 사용하는 표준 예외
            throw new IllegalArgumentException("HTTP 버전은 음수일 수 없습니다: " + major + "." + minor);
        }

        // 필드 초기화
        this.major = major;
        this.minor = minor;

        // 문자열 표현 미리 생성하여 캐시
        // "HTTP/" + major + "." + minor 형식
        // 예: "HTTP/1.1", "HTTP/2.0"
        this.stringRepresentation = "HTTP/" + major + "." + minor;
    }

    /**
     * 문자열에서 HttpVersion을 파싱합니다.
     *
     * 지원하는 형식:
     * - "HTTP/1.1"
     * - "HTTP/2.0"
     * - "1.1" (HTTP/ 접두사 없이)
     *
     * static 메서드로 구현한 이유:
     * - 팩토리 메서드 패턴
     * - 생성자의 한계 극복 (문자열 파싱)
     * - 명확한 의도 표현 (parse = 파싱한다는 의미)
     *
     * @param version 파싱할 버전 문자열
     * @return 파싱된 HttpVersion 객체
     * @throws IllegalArgumentException 잘못된 형식인 경우
     */
    public static HttpVersion parse(String version) {
        // null이나 빈 문자열 체크
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("HTTP 버전이 null이거나 비어있습니다");
        }

        // trim(): 앞뒤 공백 제거
        String trimmed = version.trim();

        // "HTTP/" 접두사 제거
        // toUpperCase(): 대소문자 무관 처리
        // startsWith(): 문자열이 특정 접두사로 시작하는지 확인
        if (trimmed.toUpperCase().startsWith("HTTP/")) {
            // substring(5): "HTTP/"는 5글자이므로 5번째 인덱스부터 추출
            trimmed = trimmed.substring(5);
        }

        // 점으로 분리
        // split("\\."): 마침표로 분할 (정규표현식에서 .은 특수문자이므로 \\. 사용)
        String[] parts = trimmed.split("\\.");
        if (parts.length != 2) {
            // 정확히 "major.minor" 형식이어야 함
            throw new IllegalArgumentException("잘못된 HTTP 버전 형식: " + version);
        }

        try {
            // Integer.parseInt(): 문자열을 정수로 변환
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);

            // 변환된 정수로 새 인스턴스 생성
            return new HttpVersion(major, minor);
        } catch (NumberFormatException e) {
            // 숫자 변환 실패 시 의미 있는 예외 메시지 제공
            throw new IllegalArgumentException("잘못된 HTTP 버전 형식: " + version, e);
        }
    }

    // ========== Getter 메서드 ==========

    /**
     * 메이저 버전을 반환합니다.
     *
     * @return 메이저 버전 (예: 1, 2, 3)
     */
    public int getMajor() {
        return major;
    }

    /**
     * 마이너 버전을 반환합니다.
     *
     * @return 마이너 버전 (예: 0, 1)
     */
    public int getMinor() {
        return minor;
    }

    // ========== 비교 및 검증 메서드 ==========

    /**
     * 이 버전이 표준 HTTP 버전인지 확인합니다.
     *
     * @return 표준 버전이면 true
     */
    public boolean isStandardVersion() {
        // contains(): Set의 포함 여부 확인 메서드
        // equals()와 hashCode()를 사용한 비교
        return STANDARD_VERSIONS.contains(this);
    }

    /**
     * 이 버전이 지정된 버전보다 높은지 확인합니다.
     *
     * @param other 비교할 버전
     * @return 더 높은 버전이면 true
     */
    public boolean isHigherThan(HttpVersion other) {
        // compareTo(): Comparable 인터페이스의 비교 메서드
        // > 0: 현재 객체가 더 큼
        // = 0: 같음
        // < 0: 현재 객체가 더 작음
        return compareTo(other) > 0;
    }

    /**
     * 이 버전이 지정된 버전보다 낮은지 확인합니다.
     *
     * @param other 비교할 버전
     * @return 더 낮은 버전이면 true
     */
    public boolean isLowerThan(HttpVersion other) {
        return compareTo(other) < 0;
    }

    /**
     * 이 버전이 지정된 버전과 호환되는지 확인합니다.
     *
     * 호환성 규칙:
     * - 동일한 메이저 버전 내에서는 하위 호환성 보장
     * - 다른 메이저 버전 간에는 호환성 없음
     *
     * 호환성 체크가 중요한 이유:
     * - 클라이언트-서버 간 프로토콜 협상
     * - 안전한 통신 보장
     * - 기능 지원 여부 판단
     *
     * @param other 비교할 버전
     * @return 호환되면 true
     */
    public boolean isCompatibleWith(HttpVersion other) {
        if (other == null) {
            return false;
        }

        // 동일한 버전
        if (this.equals(other)) {
            return true;
        }

        // 동일한 메이저 버전 내에서만 호환성 확인
        if (this.major != other.major) {
            return false; // 메이저 버전이 다르면 호환 불가
        }

        // 메이저 버전이 같으면 하위 호환성 가정
        // 예: HTTP/1.0과 HTTP/1.1은 호환 가능
        return true;
    }

    // ========== 기능 지원 확인 메서드 ==========
    // HTTP 버전별로 지원하는 기능들을 확인하는 메서드들

    /**
     * 지속 연결 (Keep-Alive)을 지원하는지 확인합니다.
     *
     * Keep-Alive의 중요성:
     * - 연결 설정/해제 오버헤드 감소
     * - 성능 향상
     * - 네트워크 효율성 증대
     *
     * @return Keep-Alive 지원 시 true
     */
    public boolean supportsKeepAlive() {
        // HTTP/1.0에서 도입되었지만 HTTP/1.1부터 기본 동작
        return isHigherThan(HTTP_1_0) || equals(HTTP_1_0);
    }

    /**
     * 청크 인코딩을 지원하는지 확인합니다.
     *
     * 청크 인코딩의 이점:
     * - 전체 크기를 미리 알 필요 없음
     * - 스트리밍 가능
     * - 동적 콘텐츠 전송에 유리
     *
     * @return 청크 인코딩 지원 시 true
     */
    public boolean supportsChunkedEncoding() {
        // HTTP/1.1부터 지원
        return isHigherThan(HTTP_1_0);
    }

    /**
     * 파이프라이닝을 지원하는지 확인합니다.
     *
     * 파이프라이닝:
     * - 여러 요청을 연속으로 보내고 순서대로 응답 받기
     * - HTTP/1.1의 성능 개선 기능
     * - 실제로는 구현 복잡성으로 잘 사용되지 않음
     *
     * @return 파이프라이닝 지원 시 true
     */
    public boolean supportsPipelining() {
        // HTTP/1.1에서만 지원 (HTTP/2.0부터는 멀티플렉싱으로 대체)
        return equals(HTTP_1_1);
    }

    /**
     * 멀티플렉싱을 지원하는지 확인합니다.
     *
     * 멀티플렉싱:
     * - 단일 연결에서 여러 요청/응답을 동시에 처리
     * - HTTP/2.0의 핵심 기능
     * - 파이프라이닝의 한계를 극복
     *
     * @return 멀티플렉싱 지원 시 true
     */
    public boolean supportsMultiplexing() {
        // HTTP/2.0부터 지원
        return isHigherThan(HTTP_1_1);
    }

    /**
     * 서버 푸시를 지원하는지 확인합니다.
     *
     * 서버 푸시:
     * - 클라이언트 요청 전에 서버가 리소스를 미리 전송
     * - 페이지 로딩 성능 향상
     * - HTTP/2.0의 혁신적 기능
     *
     * @return 서버 푸시 지원 시 true
     */
    public boolean supportsServerPush() {
        // HTTP/2.0부터 지원
        return equals(HTTP_2_0) || isHigherThan(HTTP_2_0);
    }

    /**
     * 헤더 압축을 지원하는지 확인합니다.
     *
     * 헤더 압축:
     * - HPACK (HTTP/2.0) 또는 QPACK (HTTP/3.0)
     * - 중복되는 헤더 정보 압축
     * - 네트워크 사용량 절약
     *
     * @return 헤더 압축 지원 시 true
     */
    public boolean supportsHeaderCompression() {
        // HTTP/2.0부터 지원
        return isHigherThan(HTTP_1_1);
    }

    /**
     * Host 헤더가 필수인지 확인합니다.
     *
     * Host 헤더의 중요성:
     * - 가상 호스팅 지원
     * - 하나의 IP에 여러 도메인 운영 가능
     * - HTTP/1.1부터 필수
     *
     * @return Host 헤더 필수 시 true
     */
    public boolean requiresHostHeader() {
        // HTTP/1.1부터 필수
        return isHigherThan(HTTP_1_0);
    }

    /**
     * 기본적으로 지속 연결을 사용하는지 확인합니다.
     *
     * 기본 동작의 변화:
     * - HTTP/1.0: 기본적으로 연결 종료
     * - HTTP/1.1+: 기본적으로 연결 유지
     *
     * @return 기본 Keep-Alive 사용 시 true
     */
    public boolean defaultKeepAlive() {
        // HTTP/1.1부터 기본 동작
        return isHigherThan(HTTP_1_0);
    }

    // ========== Object 메서드 오버라이드 ==========

    /**
     * HTTP 버전의 문자열 표현을 반환합니다.
     *
     * toString() 오버라이드 이유:
     * - 디버깅 시 의미 있는 정보 제공
     * - 로깅에서 읽기 쉬운 형태로 출력
     * - String 변환 시 자동 호출
     *
     * @return "HTTP/major.minor" 형태의 문자열
     */
    @Override
    public String toString() {
        // 생성자에서 미리 계산한 문자열 반환 (캐시된 값)
        return stringRepresentation;
    }

    /**
     * 두 HTTP 버전이 같은지 확인합니다.
     *
     * equals() 오버라이드 규칙:
     * 1. 반사성: x.equals(x) == true
     * 2. 대칭성: x.equals(y) == y.equals(x)
     * 3. 이행성: x.equals(y) && y.equals(z) → x.equals(z)
     * 4. 일관성: 여러 번 호출해도 같은 결과
     * 5. null 처리: x.equals(null) == false
     *
     * @param obj 비교할 객체
     * @return 같은 버전이면 true
     */
    @Override
    public boolean equals(Object obj) {
        // 1. 동일 객체 참조 확인 (성능 최적화)
        if (this == obj) return true;

        // 2. null 체크와 클래스 타입 확인
        // getClass(): 객체의 실제 클래스 반환
        // 상속을 고려한 정확한 타입 비교
        if (obj == null || getClass() != obj.getClass()) return false;

        // 3. 타입 캐스팅
        HttpVersion that = (HttpVersion) obj;

        // 4. 실제 필드 값 비교
        // major와 minor가 모두 같아야 동일한 버전
        return major == that.major && minor == that.minor;
    }

    /**
     * HTTP 버전의 해시 코드를 반환합니다.
     *
     * hashCode() 오버라이드 규칙:
     * - equals()를 오버라이드했으면 반드시 hashCode()도 오버라이드
     * - equals()가 true인 객체들은 같은 해시 코드 반환
     * - 해시 기반 컬렉션(HashMap, HashSet)에서 올바른 동작 보장
     *
     * @return 해시 코드 값
     */
    @Override
    public int hashCode() {
        // Objects.hash(): 여러 값의 해시코드를 조합하는 유틸리티
        // major와 minor를 조합하여 일관된 해시코드 생성
        return Objects.hash(major, minor);
    }

    /**
     * HTTP 버전을 비교합니다.
     *
     * Comparable 인터페이스 구현:
     * - 메이저 버전을 먼저 비교하고, 같으면 마이너 버전을 비교
     * - 자연스러운 순서 정의 (버전 순서)
     * - Collections.sort() 등에서 사용 가능
     *
     * @param other 비교할 다른 HttpVersion
     * @return 음수(작음), 0(같음), 양수(큼)
     */
    @Override
    public int compareTo(HttpVersion other) {
        if (other == null) {
            return 1; // null보다는 현재 객체가 큼
        }

        // 메이저 버전 먼저 비교
        // Integer.compare(): 두 int 값을 비교하는 정적 메서드
        int majorComparison = Integer.compare(this.major, other.major);
        if (majorComparison != 0) {
            return majorComparison; // 메이저 버전이 다르면 그 결과 반환
        }

        // 메이저 버전이 같으면 마이너 버전 비교
        return Integer.compare(this.minor, other.minor);
    }

    // ========== 유틸리티 메서드 ==========

    /**
     * 두 버전 중 더 높은 버전을 반환합니다.
     *
     * static 유틸리티 메서드를 제공하는 이유:
     * - 편의성: 간단한 최대값 찾기
     * - 재사용성: 다양한 곳에서 활용 가능
     * - 명확성: 메서드 이름으로 의도 명확히 표현
     *
     * @param v1 첫 번째 버전
     * @param v2 두 번째 버전
     * @return 더 높은 버전 (null 처리 포함)
     */
    public static HttpVersion max(HttpVersion v1, HttpVersion v2) {
        // null 처리: 하나가 null이면 다른 하나 반환
        if (v1 == null) return v2;
        if (v2 == null) return v1;

        // 삼항 연산자로 더 높은 버전 반환
        return v1.isHigherThan(v2) ? v1 : v2;
    }

    /**
     * 두 버전 중 더 낮은 버전을 반환합니다.
     *
     * @param v1 첫 번째 버전
     * @param v2 두 번째 버전
     * @return 더 낮은 버전 (null 처리 포함)
     */
    public static HttpVersion min(HttpVersion v1, HttpVersion v2) {
        if (v1 == null) return v2;
        if (v2 == null) return v1;
        return v1.isLowerThan(v2) ? v1 : v2;
    }

    /**
     * 지원되는 모든 표준 HTTP 버전을 반환합니다.
     *
     * 방어적 복사를 하는 이유:
     * - 원본 컬렉션 보호
     * - 외부에서 수정해도 내부 상태에 영향 없음
     * - 불변성 보장
     *
     * @return 표준 버전들의 복사본
     */
    public static Set<HttpVersion> getSupportedVersions() {
        // Set.copyOf(): 불변 복사본 생성 (Java 10+)
        return Set.copyOf(STANDARD_VERSIONS);
    }

    /**
     * 기본 HTTP 버전을 반환합니다 (HTTP/1.1).
     *
     * HTTP/1.1을 기본으로 선택한 이유:
     * - 가장 널리 사용되는 버전
     * - 안정성과 호환성 보장
     * - 대부분의 웹 서버/클라이언트에서 지원
     *
     * @return HTTP/1.1 버전
     */
    public static HttpVersion getDefault() {
        return HTTP_1_1;
    }

    /**
     * HTTP 버전의 상세 특성을 설명하는 문자열을 반환합니다.
     *
     * 상세 정보를 제공하는 이유:
     * - 디버깅 지원
     * - 개발자 도구에서 활용
     * - 시스템 분석 및 문서화
     *
     * @return 버전 특성을 설명하는 상세 문자열
     */
    public String getDetailedInfo() {
        // StringBuilder: 효율적인 문자열 연결
        StringBuilder info = new StringBuilder();

        // 기본 정보
        info.append("HTTP Version: ").append(toString()).append("\n");
        info.append("Standard Version: ").append(isStandardVersion()).append("\n");

        // 기능 지원 정보
        info.append("Features:\n");
        info.append("  - Keep-Alive: ").append(supportsKeepAlive()).append("\n");
        info.append("  - Chunked Encoding: ").append(supportsChunkedEncoding()).append("\n");
        info.append("  - Pipelining: ").append(supportsPipelining()).append("\n");
        info.append("  - Multiplexing: ").append(supportsMultiplexing()).append("\n");
        info.append("  - Server Push: ").append(supportsServerPush()).append("\n");
        info.append("  - Header Compression: ").append(supportsHeaderCompression()).append("\n");
        info.append("  - Requires Host Header: ").append(requiresHostHeader()).append("\n");
        info.append("  - Default Keep-Alive: ").append(defaultKeepAlive());

        // StringBuilder를 String으로 변환
        return info.toString();
    }
}