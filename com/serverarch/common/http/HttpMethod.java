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
 *
 * 클래스를 final로 선언한 이유:
 * - 상속을 방지하여 유틸리티 클래스의 의도를 명확히 함
 * - 오직 정적 메서드와 상수만 제공하는 클래스임을 표현
 */
public final class HttpMethod {

    /**
     * 이 클래스는 상수만 제공하므로 인스턴스화 방지
     *
     * private 생성자를 만드는 이유:
     * 1. 유틸리티 클래스는 인스턴스를 만들 필요가 없음
     * 2. new HttpMethod() 같은 잘못된 사용을 컴파일 타임에 방지
     * 3. 정적 팩토리 패턴의 일종
     */
    private HttpMethod() {
        // UnsupportedOperationException: 지원하지 않는 연산 예외
        // 실수로 리플렉션 등으로 호출될 경우를 대비
        throw new UnsupportedOperationException("이 클래스는 인스턴스화할 수 없습니다");
    }

    // ========== 표준 HTTP 메서드 상수 ==========
    // public static final: 공개 정적 상수
    // - public: 외부에서 접근 가능
    // - static: 클래스 레벨에서 접근 (인스턴스 불필요)
    // - final: 값 변경 불가능

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
     *
     * String 리터럴을 사용하는 이유:
     * - HTTP 표준에서 메서드는 문자열로 정의됨
     * - 네트워크 프로토콜에서 직접 사용 가능
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
     *
     * POST가 멱등하지 않은 이유:
     * - 매번 새로운 리소스를 생성할 수 있음
     * - 동일한 요청도 서버 상태에 따라 다른 결과 가능
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
     *
     * PUT이 멱등한 이유:
     * - 동일한 데이터로 여러 번 요청해도 최종 상태는 같음
     * - "완전 대체"의 특성상 반복 실행 시 동일한 결과
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
     *
     * DELETE가 멱등한 이유:
     * - 이미 삭제된 리소스를 다시 삭제해도 결과는 "삭제됨"
     * - 404 응답도 멱등성을 위반하지 않음
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
     *
     * HEAD를 사용하는 이유:
     * - 대용량 파일의 메타데이터만 확인할 때 효율적
     * - 네트워크 대역폭 절약
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
     *
     * OPTIONS가 캐시 불가능한 이유:
     * - 서버 설정이 동적으로 변경될 수 있음
     * - 실시간 서버 상태를 반영해야 함
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
     *
     * TRACE가 위험한 이유:
     * - 중간 프록시의 헤더 조작을 악용할 수 있음
     * - 보안 헤더가 클라이언트에 노출될 수 있음
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
     *
     * CONNECT가 특별한 이유:
     * - TCP 터널을 생성하는 특수한 메서드
     * - 일반적인 HTTP 요청/응답 패턴과 다름
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
     *
     * PATCH가 멱등하지 않은 이유:
     * - 패치 적용 순서에 따라 결과가 달라질 수 있음
     * - 상대적 변경(+1, -1)을 포함할 수 있음
     */
    public static final String PATCH = "PATCH";

    // ========== 메서드 집합 상수 ==========
    // Set.of(): Java 9+에서 제공하는 불변 Set 생성 메서드
    // - 수정 불가능한 Set 반환
    // - null 요소 허용하지 않음
    // - 중복 요소 자동 제거

    /**
     * 안전한(Safe) 메서드들의 집합
     * 서버 상태를 변경하지 않는 메서드들
     *
     * 안전한 메서드를 별도로 관리하는 이유:
     * - 캐싱 정책 결정에 사용
     * - 재시도 로직에서 안전성 판단
     * - 로깅/모니터링에서 읽기/쓰기 구분
     */
    public static final Set<String> SAFE_METHODS = Set.of(
            GET, HEAD, OPTIONS, TRACE
    );

    /**
     * 멱등(Idempotent) 메서드들의 집합
     * 동일한 요청을 여러 번 수행해도 결과가 같은 메서드들
     *
     * 멱등 메서드를 관리하는 이유:
     * - 네트워크 오류 시 안전한 재시도 판단
     * - 부하 분산에서 중복 요청 처리
     * - 트랜잭션 롤백 시 영향도 판단
     */
    public static final Set<String> IDEMPOTENT_METHODS = Set.of(
            GET, HEAD, PUT, DELETE, OPTIONS, TRACE
    );

    /**
     * 캐시 가능한(Cacheable) 메서드들의 집합
     * 응답을 캐시할 수 있는 메서드들
     *
     * 캐시 가능 메서드를 관리하는 이유:
     * - HTTP 캐시 구현 시 정책 결정
     * - CDN 설정에서 캐시 대상 판단
     * - 성능 최적화 전략 수립
     *
     * POST가 조건부 캐시 가능한 이유:
     * - 명시적 캐시 헤더가 있을 때만 캐시 가능
     * - 대부분의 경우 캐시하지 않음
     */
    public static final Set<String> CACHEABLE_METHODS = Set.of(
            GET, HEAD, POST  // POST는 조건부로 캐시 가능
    );

    /**
     * 요청 본문을 가질 수 있는 메서드들의 집합
     *
     * 본문 가능 메서드를 관리하는 이유:
     * - Content-Length 헤더 필요성 판단
     * - 요청 파싱 로직에서 본문 처리 여부 결정
     * - 보안 검사에서 페이로드 검증 대상 판단
     *
     * GET/HEAD가 포함되지 않는 이유:
     * - HTTP 표준에서 본문을 권장하지 않음
     * - 프록시나 서버에서 무시될 수 있음
     */
    public static final Set<String> METHODS_WITH_BODY = Set.of(
            POST, PUT, PATCH
    );

    /**
     * 표준 HTTP 메서드들의 집합
     *
     * 표준 메서드를 관리하는 이유:
     * - 알 수 없는 메서드에 대한 처리 방침 결정
     * - 확장 메서드와 표준 메서드 구분
     * - API 문서화 시 지원 메서드 명시
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
     * static 메서드로 구현한 이유:
     * - 인스턴스 생성 없이 사용 가능
     * - 유틸리티 성격의 기능
     * - 다른 클래스에서 쉽게 참조 가능
     *
     * @param method 확인할 HTTP 메서드
     * @return 안전한 메서드이면 true, 그렇지 않으면 false
     */
    public static boolean isSafe(String method) {
        // method != null: null 체크로 NPE 방지
        // SAFE_METHODS.contains(): Set의 포함 여부 확인 메서드
        // method.toUpperCase(): 대소문자 무관 비교를 위한 대문자 변환
        return method != null && SAFE_METHODS.contains(method.toUpperCase());
    }

    /**
     * 주어진 메서드가 멱등(Idempotent) 메서드인지 확인합니다.
     *
     * 멱등 메서드는 동일한 요청을 여러 번 수행해도 결과가 같은 메서드입니다.
     * 네트워크 오류 시 안전하게 재시도할 수 있습니다.
     *
     * 멱등성 확인이 중요한 이유:
     * - 재시도 로직에서 안전성 보장
     * - 분산 시스템에서 중복 요청 처리
     * - 트랜잭션 설계 시 일관성 보장
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
     * 캐시 가능성 확인이 중요한 이유:
     * - HTTP 캐시 미들웨어에서 캐시 정책 결정
     * - CDN 설정에서 캐시 대상 결정
     * - 성능 최적화 전략 수립
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
     * 본문 가능성 확인이 중요한 이유:
     * - 요청 파싱 시 본문 읽기 여부 결정
     * - 보안 검사에서 페이로드 검증 여부 결정
     * - 메모리 관리에서 버퍼 크기 결정
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
     * 표준 메서드 확인이 중요한 이유:
     * - 알 수 없는 메서드에 대한 처리 방침 결정
     * - 보안 정책에서 허용 메서드 제한
     * - API 문서화에서 지원 범위 명시
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
     * 정규화가 필요한 이유:
     * - 일관된 비교를 위해
     * - HTTP 표준 관례 준수
     * - 로깅이나 디버깅에서 일관된 표시
     *
     * @param method 정규화할 HTTP 메서드
     * @return 대문자로 변환된 메서드 이름, null이면 null 반환
     */
    public static String normalize(String method) {
        // 삼항 연산자: method != null이면 toUpperCase() 호출, 아니면 null 반환
        // null-safe 처리로 NPE 방지
        return method != null ? method.toUpperCase() : null;
    }

    /**
     * 주어진 메서드가 유효한 HTTP 메서드인지 확인합니다.
     *
     * 메서드 이름이 null이 아니고, 공백이 아니며, 유효한 문자로 구성되어 있는지 확인합니다.
     * HTTP 메서드는 토큰 문자만 사용할 수 있습니다.
     *
     * 유효성 검증이 중요한 이유:
     * - 보안: 잘못된 메서드로 인한 공격 방지
     * - 안정성: 파싱 오류나 예외 방지
     * - 표준 준수: HTTP 프로토콜 규격 준수
     *
     * @param method 확인할 HTTP 메서드
     * @return 유효한 메서드이면 true, 그렇지 않으면 false
     */
    public static boolean isValidMethod(String method) {
        // null이나 빈 문자열 체크
        // trim(): 앞뒤 공백 제거
        // isEmpty(): 문자열 길이가 0인지 확인
        if (method == null || method.trim().isEmpty()) {
            return false;
        }

        // HTTP 토큰 문자 검증 (RFC 7230)
        // tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+"
        //       / "-" / "." / "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA

        // for 루프로 각 문자 검사
        // method.length(): 문자열 길이 반환
        for (int i = 0; i < method.length(); i++) {
            // charAt(i): 지정된 인덱스의 문자 반환
            char c = method.charAt(i);

            // isTokenChar(): 아래에서 정의한 토큰 문자 검증 메서드
            if (!isTokenChar(c)) {
                return false; // 유효하지 않은 문자 발견 시 즉시 false 반환
            }
        }

        return true; // 모든 문자가 유효함
    }

    /**
     * 문자가 HTTP 토큰에서 사용할 수 있는 문자인지 확인합니다.
     *
     * private 메서드로 구현한 이유:
     * - 내부적으로만 사용하는 검증 로직
     * - 캡슐화 원칙 준수
     * - HTTP 표준의 정확한 구현을 위한 세부 사항
     */
    private static boolean isTokenChar(char c) {
        // HTTP 토큰 문자 규칙 (RFC 7230):
        // - 영문 대문자 (A-Z): ASCII 65-90
        // - 영문 소문자 (a-z): ASCII 97-122
        // - 숫자 (0-9): ASCII 48-57
        // - 특수문자: ! # $ % & ' * + - . ^ _ ` | ~

        return (c >= 'A' && c <= 'Z') ||      // 대문자 범위 확인
                (c >= 'a' && c <= 'z') ||      // 소문자 범위 확인
                (c >= '0' && c <= '9') ||      // 숫자 범위 확인
                // 허용되는 특수문자들
                c == '!' || c == '#' || c == '$' || c == '%' || c == '&' ||
                c == '\'' || c == '*' || c == '+' || c == '-' || c == '.' ||
                c == '^' || c == '_' || c == '`' || c == '|' || c == '~';
    }

    /**
     * 메서드의 특성을 설명하는 문자열을 반환합니다.
     *
     * 디버깅이나 로깅 목적으로 메서드의 특성을 요약합니다.
     *
     * 특성 설명 기능을 제공하는 이유:
     * - 디버깅 시 메서드 특성 확인 용이
     * - 로깅에서 상세한 정보 제공
     * - 개발자 도구나 문서화에 활용
     *
     * @param method 분석할 HTTP 메서드
     * @return 메서드 특성을 설명하는 문자열
     */
    public static String getMethodCharacteristics(String method) {
        // null 체크
        if (method == null) {
            return "Invalid method (null)";
        }

        // normalize(): 앞서 정의한 메서드 정규화 메서드
        String normalizedMethod = normalize(method);

        // StringBuilder: 문자열 연결에 효율적인 클래스
        // String 연결(+)보다 메모리 효율적
        StringBuilder characteristics = new StringBuilder();

        // append(): StringBuilder에 문자열 추가
        characteristics.append("Method: ").append(normalizedMethod);

        // 표준 메서드인지 확인
        if (!isStandardMethod(method)) {
            characteristics.append(" [NON-STANDARD]"); // 비표준 메서드 표시
        }

        characteristics.append(" - Characteristics: ");

        // 각 특성을 확인하여 문자열에 추가
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
        // toString(): StringBuilder를 String으로 변환
        String result = characteristics.toString();

        // endsWith(): 문자열이 특정 접미사로 끝나는지 확인
        if (result.endsWith(", ")) {
            // substring(): 부분 문자열 추출
            // 0부터 (길이-2)까지 = 마지막 ", " 제거
            result = result.substring(0, result.length() - 2);
        }

        return result;
    }
}