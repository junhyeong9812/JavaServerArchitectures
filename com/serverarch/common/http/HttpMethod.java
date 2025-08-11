package com.serverarch.common.http;

import java.util.Set;

/**
 * HTTP 메서드와 관련된 상수와 유틸리티를 제공하는 클래스입니다.
 *
 * 이 클래스는 RFC 7231, RFC 5789 등에서 정의된 표준 HTTP 메서드들과
 * 각 메서드의 특성(안전성, 멱등성, 캐시 가능성 등)을 정리하여 제공합니다.
 *
 * HTTP 메서드의 특성:
 * - Safe (안전): 서버 상태를 변경하지 않는 메서드
 * - Idempotent (멱등): 동일한 요청을 여러 번 수행해도 결과가 같은 메서드
 * - Cacheable (캐시 가능): 응답을 캐시할 수 있는 메서드
 */
public final class HttpMethod {

    // 이 클래스는 상수만 제공하므로 인스턴스화 방지
    private HttpMethod() {
        throw new UnsupportedOperationException("이 클래스는 인스턴스화할 수 없습니다");
    }

    // ========== 표준 HTTP 메서드 상수 ==========

    /**
     * GET 메서드
     *
     * 리소스를 조회하는 데 사용됩니다.
     *
     * 특성:
     * - Safe: 예 (서버 상태를 변경하지 않음)
     * - Idempotent: 예 (여러 번 요청해도 결과가 동일)
     * - Cacheable: 예 (응답을 캐시할 수 있음)
     *
     * 사용 예시:
     * - 웹 페이지 조회
     * - API 데이터 조회
     * - 파일 다운로드
     * - 검색 결과 조회
     */
    public static final String GET = "GET";

    /**
     * POST 메서드
     *
     * 서버에 데이터를 제출하여 리소스를 생성하거나 처리하는 데 사용됩니다.
     *
     * 특성:
     * - Safe: 아니오 (서버 상태를 변경할 수 있음)
     * - Idempotent: 아니오 (여러 번 요청 시 다른 결과 가능)
     * - Cacheable: 조건부 (특정 조건에서만 캐시 가능)
     *
     * 사용 예시:
     * - 폼 데이터 제출
     * - 파일 업로드
     * - 새 리소스 생성
     * - 로그인 처리
     * - 결제 처리
     */
    public static final String POST = "POST";

    /**
     * PUT 메서드
     *
     * 리소스를 생성하거나 완전히 대체하는 데 사용됩니다.
     *
     * 특성:
     * - Safe: 아니오 (서버 상태를 변경함)
     * - Idempotent: 예 (동일한 요청을 여러 번 해도 결과가 같음)
     * - Cacheable: 아니오
     *
     * 사용 예시:
     * - 파일 업로드 (특정 경로에)
     * - 리소스 전체 업데이트
     * - 설정 파일 교체
     * - 사용자 프로필 완전 업데이트
     */
    public static final String PUT = "PUT";

    /**
     * DELETE 메서드
     *
     * 지정된 리소스를 삭제하는 데 사용됩니다.
     *
     * 특성:
     * - Safe: 아니오 (서버 상태를 변경함)
     * - Idempotent: 예 (이미 삭제된 리소스를 다시 삭제해도 안전)
     * - Cacheable: 아니오
     *
     * 사용 예시:
     * - 파일 삭제
     * - 사용자 계정 삭제
     * - 게시글 삭제
     * - 임시 데이터 정리
     */
    public static final String DELETE = "DELETE";

    /**
     * HEAD 메서드
     *
     * GET과 동일하지만 응답 본문 없이 헤더만 반환합니다.
     *
     * 특성:
     * - Safe: 예 (서버 상태를 변경하지 않음)
     * - Idempotent: 예
     * - Cacheable: 예
     *
     * 사용 예시:
     * - 리소스 존재 여부 확인
     * - 파일 크기나 수정 시간 확인
     * - 링크 유효성 검사
     * - 캐시 검증
     */
    public static final String HEAD = "HEAD";

    /**
     * OPTIONS 메서드
     *
     * 서버가 지원하는 HTTP 메서드를 조회하는 데 사용됩니다.
     *
     * 특성:
     * - Safe: 예 (서버 상태를 변경하지 않음)
     * - Idempotent: 예
     * - Cacheable: 아니오
     *
     * 사용 예시:
     * - CORS preflight 요청
     * - API 지원 메서드 확인
     * - 서버 기능 탐색
     * - 디버깅 및 테스팅
     */
    public static final String OPTIONS = "OPTIONS";

    /**
     * TRACE 메서드
     *
     * 클라이언트가 보낸 요청을 그대로 반환하는 루프백 테스트를 수행합니다.
     *
     * 특성:
     * - Safe: 예 (서버 상태를 변경하지 않음)
     * - Idempotent: 예
     * - Cacheable: 아니오
     *
     * 보안 고려사항:
     * - XST (Cross-Site Tracing) 공격에 취약할 수 있음
     * - 많은 서버에서 비활성화됨
     *
     * 사용 예시:
     * - 네트워크 진단
     * - 프록시 경로 추적
     * - 디버깅 (주의해서 사용)
     */
    public static final String TRACE = "TRACE";

    /**
     * CONNECT 메서드
     *
     * 프록시 서버와 터널 연결을 설정하는 데 사용됩니다.
     *
     * 특성:
     * - Safe: 아니오
     * - Idempotent: 아니오
     * - Cacheable: 아니오
     *
     * 사용 예시:
     * - HTTPS 프록시 터널링
     * - SSL/TLS 연결 설정
     * - 웹 프록시를 통한 보안 연결
     */
    public static final String CONNECT = "CONNECT";

    /**
     * PATCH 메서드 (RFC 5789)
     *
     * 리소스의 부분적인 수정을 수행하는 데 사용됩니다.
     *
     * 특성:
     * - Safe: 아니오 (서버 상태를 변경함)
     * - Idempotent: 아니오 (구현에 따라 다름)
     * - Cacheable: 아니오
     *
     * 사용 예시:
     * - 사용자 프로필 일부 업데이트
     * - JSON 객체의 특정 필드만 수정
     * - 파일의 일부 섹션 수정
     * - 설정의 특정 항목만 변경
     */
    public static final String PATCH = "PATCH";

    // ========== 메서드 집합 상수 ==========

    /**
     * 안전한(Safe) 메서드들의 집합
     * 서버 상태를 변경하지 않는 메서드들
     */
    public static final Set<String> SAFE_METHODS = Set.of(
            GET, HEAD, OPTIONS, TRACE
    );

    /**
     * 멱등(Idempotent) 메서드들의 집합
     * 동일한 요청을 여러 번 수행해도 결과가 같은 메서드들
     */
    public static final Set<String> IDEMPOTENT_METHODS = Set.of(
            GET, HEAD, PUT, DELETE, OPTIONS, TRACE
    );

    /**
     * 캐시 가능한(Cacheable) 메서드들의 집합
     * 응답을 캐시할 수 있는 메서드들
     */
    public static final Set<String> CACHEABLE_METHODS = Set.of(
            GET, HEAD, POST  // POST는 조건부로 캐시 가능
    );

    /**
     * 요청 본문을 가질 수 있는 메서드들의 집합
     */
    public static final Set<String> METHODS_WITH_BODY = Set.of(
            POST, PUT, PATCH
    );

    /**
     * 표준 HTTP 메서드들의 집합
     */
    public static final Set<String> STANDARD_METHODS = Set.of(
            GET, POST, PUT, DELETE, HEAD, OPTIONS, TRACE, CONNECT, PATCH
    );

    // ========== 유틸리티 메서드들 ==========

    /**
     * 주어진 메서드가 안전한(Safe) 메서드인지 확인합니다.
     *
     * 안전한 메서드는 서버의 상태를 변경하지 않는 메서드입니다.
     * 읽기 전용 작업에 사용됩니다.
     *
     * @param method 확인할 HTTP 메서드
     * @return 안전한 메서드이면 true, 그렇지 않으면 false
     */
    public static boolean isSafe(String method) {
        return method != null && SAFE_METHODS.contains(method.toUpperCase());
    }

    /**
     * 주어진 메서드가 멱등(Idempotent) 메서드인지 확인합니다.
     *
     * 멱등 메서드는 동일한 요청을 여러 번 수행해도 결과가 같은 메서드입니다.
     * 네트워크 오류 시 안전하게 재시도할 수 있습니다.
     *
     * @param method 확인할 HTTP 메서드
     * @return 멱등 메서드이면 true, 그렇지 않으면 false
     */
    public static boolean isIdempotent(String method) {
        return method != null && IDEMPOTENT_METHODS.contains(method.toUpperCase());
    }

    /**
     * 주어진 메서드가 캐시 가능한(Cacheable) 메서드인지 확인합니다.
     *
     * 캐시 가능한 메서드의 응답은 향후 동일한 요청에 재사용될 수 있습니다.
     *
     * @param method 확인할 HTTP 메서드
     * @return 캐시 가능한 메서드이면 true, 그렇지 않으면 false
     */
    public static boolean isCacheable(String method) {
        return method != null && CACHEABLE_METHODS.contains(method.toUpperCase());
    }

    /**
     * 주어진 메서드가 요청 본문을 가질 수 있는 메서드인지 확인합니다.
     *
     * 이런 메서드들은 Content-Length나 Transfer-Encoding 헤더를 가질 수 있습니다.
     *
     * @param method 확인할 HTTP 메서드
     * @return 요청 본문을 가질 수 있으면 true, 그렇지 않으면 false
     */
    public static boolean canHaveBody(String method) {
        return method != null && METHODS_WITH_BODY.contains(method.toUpperCase());
    }

    /**
     * 주어진 메서드가 표준 HTTP 메서드인지 확인합니다.
     *
     * RFC에서 정의된 표준 메서드인지 검증합니다.
     *
     * @param method 확인할 HTTP 메서드
     * @return 표준 메서드이면 true, 그렇지 않으면 false
     */
    public static boolean isStandardMethod(String method) {
        return method != null && STANDARD_METHODS.contains(method.toUpperCase());
    }

    /**
     * 메서드 이름을 정규화합니다 (대문자로 변환).
     *
     * HTTP 메서드는 대소문자를 구분하지만 관례상 대문자로 사용됩니다.
     *
     * @param method 정규화할 HTTP 메서드
     * @return 대문자로 변환된 메서드 이름, null이면 null 반환
     */
    public static String normalize(String method) {
        return method != null ? method.toUpperCase() : null;
    }

    /**
     * 주어진 메서드가 유효한 HTTP 메서드인지 확인합니다.
     *
     * 메서드 이름이 null이 아니고, 공백이 아니며, 유효한 문자로 구성되어 있는지 확인합니다.
     * HTTP 메서드는 토큰 문자만 사용할 수 있습니다.
     *
     * @param method 확인할 HTTP 메서드
     * @return 유효한 메서드이면 true, 그렇지 않으면 false
     */
    public static boolean isValidMethod(String method) {
        if (method == null || method.trim().isEmpty()) {
            return false;
        }

        // HTTP 토큰 문자 검증 (RFC 7230)
        // tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+"
        //       / "-" / "." / "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA
        for (int i = 0; i < method.length(); i++) {
            char c = method.charAt(i);
            if (!isTokenChar(c)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 문자가 HTTP 토큰에서 사용할 수 있는 문자인지 확인합니다.
     */
    private static boolean isTokenChar(char c) {
        return (c >= 'A' && c <= 'Z') ||
                (c >= 'a' && c <= 'z') ||
                (c >= '0' && c <= '9') ||
                c == '!' || c == '#' || c == '$' || c == '%' || c == '&' ||
                c == '\'' || c == '*' || c == '+' || c == '-' || c == '.' ||
                c == '^' || c == '_' || c == '`' || c == '|' || c == '~';
    }

    /**
     * 메서드의 특성을 설명하는 문자열을 반환합니다.
     *
     * 디버깅이나 로깅 목적으로 메서드의 특성을 요약합니다.
     *
     * @param method 분석할 HTTP 메서드
     * @return 메서드 특성을 설명하는 문자열
     */
    public static String getMethodCharacteristics(String method) {
        if (method == null) {
            return "Invalid method (null)";
        }

        String normalizedMethod = normalize(method);
        StringBuilder characteristics = new StringBuilder();
        characteristics.append("Method: ").append(normalizedMethod);

        if (!isStandardMethod(method)) {
            characteristics.append(" [NON-STANDARD]");
        }

        characteristics.append(" - Characteristics: ");

        if (isSafe(method)) {
            characteristics.append("Safe, ");
        }
        if (isIdempotent(method)) {
            characteristics.append("Idempotent, ");
        }
        if (isCacheable(method)) {
            characteristics.append("Cacheable, ");
        }
        if (canHaveBody(method)) {
            characteristics.append("Can have body, ");
        }

        // 마지막 쉼표 제거
        String result = characteristics.toString();
        if (result.endsWith(", ")) {
            result = result.substring(0, result.length() - 2);
        }

        return result;
    }
}