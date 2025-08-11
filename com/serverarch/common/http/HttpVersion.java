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
 */
public final class HttpVersion implements Comparable<HttpVersion> {

    // ========== 표준 HTTP 버전 상수 ==========

    /**
     * HTTP/0.9 - 최초의 HTTP 버전
     *
     * 특징:
     * - GET 메서드만 지원
     * - 헤더 없음
     * - 상태 코드 없음
     * - HTML만 전송 가능
     * - 연결은 요청 후 즉시 종료
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
     */
    public static final HttpVersion HTTP_3_0 = new HttpVersion(3, 0);

    // ========== 인스턴스 필드 ==========

    /**
     * HTTP 메이저 버전
     */
    private final int major;

    /**
     * HTTP 마이너 버전
     */
    private final int minor;

    /**
     * 문자열 표현 (캐시됨)
     */
    private final String stringRepresentation;

    /**
     * 지원되는 표준 HTTP 버전들
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
        if (major < 0 || minor < 0) {
            throw new IllegalArgumentException("HTTP 버전은 음수일 수 없습니다: " + major + "." + minor);
        }
        this.major = major;
        this.minor = minor;
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
     * @param version 파싱할 버전 문자열
     * @return 파싱된 HttpVersion 객체
     * @throws IllegalArgumentException 잘못된 형식인 경우
     */
    public static HttpVersion parse(String version) {
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("HTTP 버전이 null이거나 비어있습니다");
        }

        String trimmed = version.trim();

        // "HTTP/" 접두사 제거
        if (trimmed.toUpperCase().startsWith("HTTP/")) {
            trimmed = trimmed.substring(5);
        }

        // 점으로 분리
        String[] parts = trimmed.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("잘못된 HTTP 버전 형식: " + version);
        }

        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return new HttpVersion(major, minor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("잘못된 HTTP 버전 형식: " + version, e);
        }
    }

    // ========== Getter 메서드 ==========

    /**
     * 메이저 버전을 반환합니다.
     */
    public int getMajor() {
        return major;
    }

    /**
     * 마이너 버전을 반환합니다.
     */
    public int getMinor() {
        return minor;
    }

    // ========== 비교 및 검증 메서드 ==========

    /**
     * 이 버전이 표준 HTTP 버전인지 확인합니다.
     */
    public boolean isStandardVersion() {
        return STANDARD_VERSIONS.contains(this);
    }

    /**
     * 이 버전이 지정된 버전보다 높은지 확인합니다.
     */
    public boolean isHigherThan(HttpVersion other) {
        return compareTo(other) > 0;
    }

    /**
     * 이 버전이 지정된 버전보다 낮은지 확인합니다.
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
            return false;
        }

        // 메이저 버전이 같으면 하위 호환성 가정
        return true;
    }

    // ========== 기능 지원 확인 메서드 ==========

    /**
     * 지속 연결 (Keep-Alive)을 지원하는지 확인합니다.
     */
    public boolean supportsKeepAlive() {
        return isHigherThan(HTTP_1_0) || equals(HTTP_1_0);
    }

    /**
     * 청크 인코딩을 지원하는지 확인합니다.
     */
    public boolean supportsChunkedEncoding() {
        return isHigherThan(HTTP_1_0);
    }

    /**
     * 파이프라이닝을 지원하는지 확인합니다.
     */
    public boolean supportsPipelining() {
        return equals(HTTP_1_1);
    }

    /**
     * 멀티플렉싱을 지원하는지 확인합니다.
     */
    public boolean supportsMultiplexing() {
        return isHigherThan(HTTP_1_1);
    }

    /**
     * 서버 푸시를 지원하는지 확인합니다.
     */
    public boolean supportsServerPush() {
        return equals(HTTP_2_0) || isHigherThan(HTTP_2_0);
    }

    /**
     * 헤더 압축을 지원하는지 확인합니다.
     */
    public boolean supportsHeaderCompression() {
        return isHigherThan(HTTP_1_1);
    }

    /**
     * Host 헤더가 필수인지 확인합니다.
     */
    public boolean requiresHostHeader() {
        return isHigherThan(HTTP_1_0);
    }

    /**
     * 기본적으로 지속 연결을 사용하는지 확인합니다.
     */
    public boolean defaultKeepAlive() {
        return isHigherThan(HTTP_1_0);
    }

    // ========== Object 메서드 오버라이드 ==========

    /**
     * HTTP 버전의 문자열 표현을 반환합니다.
     */
    @Override
    public String toString() {
        return stringRepresentation;
    }

    /**
     * 두 HTTP 버전이 같은지 확인합니다.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        HttpVersion that = (HttpVersion) obj;
        return major == that.major && minor == that.minor;
    }

    /**
     * HTTP 버전의 해시 코드를 반환합니다.
     */
    @Override
    public int hashCode() {
        return Objects.hash(major, minor);
    }

    /**
     * HTTP 버전을 비교합니다.
     *
     * 메이저 버전을 먼저 비교하고, 같으면 마이너 버전을 비교합니다.
     */
    @Override
    public int compareTo(HttpVersion other) {
        if (other == null) {
            return 1;
        }

        int majorComparison = Integer.compare(this.major, other.major);
        if (majorComparison != 0) {
            return majorComparison;
        }

        return Integer.compare(this.minor, other.minor);
    }

    // ========== 유틸리티 메서드 ==========

    /**
     * 두 버전 중 더 높은 버전을 반환합니다.
     */
    public static HttpVersion max(HttpVersion v1, HttpVersion v2) {
        if (v1 == null) return v2;
        if (v2 == null) return v1;
        return v1.isHigherThan(v2) ? v1 : v2;
    }

    /**
     * 두 버전 중 더 낮은 버전을 반환합니다.
     */
    public static HttpVersion min(HttpVersion v1, HttpVersion v2) {
        if (v1 == null) return v2;
        if (v2 == null) return v1;
        return v1.isLowerThan(v2) ? v1 : v2;
    }

    /**
     * 지원되는 모든 표준 HTTP 버전을 반환합니다.
     */
    public static Set<HttpVersion> getSupportedVersions() {
        return Set.copyOf(STANDARD_VERSIONS);
    }

    /**
     * 기본 HTTP 버전을 반환합니다 (HTTP/1.1).
     */
    public static HttpVersion getDefault() {
        return HTTP_1_1;
    }

    /**
     * HTTP 버전의 상세 특성을 설명하는 문자열을 반환합니다.
     */
    public String getDetailedInfo() {
        StringBuilder info = new StringBuilder();
        info.append("HTTP Version: ").append(toString()).append("\n");
        info.append("Standard Version: ").append(isStandardVersion()).append("\n");
        info.append("Features:\n");
        info.append("  - Keep-Alive: ").append(supportsKeepAlive()).append("\n");
        info.append("  - Chunked Encoding: ").append(supportsChunkedEncoding()).append("\n");
        info.append("  - Pipelining: ").append(supportsPipelining()).append("\n");
        info.append("  - Multiplexing: ").append(supportsMultiplexing()).append("\n");
        info.append("  - Server Push: ").append(supportsServerPush()).append("\n");
        info.append("  - Header Compression: ").append(supportsHeaderCompression()).append("\n");
        info.append("  - Requires Host Header: ").append(requiresHostHeader()).append("\n");
        info.append("  - Default Keep-Alive: ").append(defaultKeepAlive());
        return info.toString();
    }
}
