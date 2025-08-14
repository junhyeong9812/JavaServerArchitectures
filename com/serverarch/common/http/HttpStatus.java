package com.serverarch.common.http;

/**
 * HTTP 상태 코드를 관리하는 열거형(Enum) 클래스
 *
 * 이 클래스를 enum으로 구현한 이유:
 * 1. 타입 안전성: 컴파일 타임에 잘못된 상태 코드 사용을 방지
 * 2. 싱글톤 보장: 각 상태 코드는 JVM에서 하나의 인스턴스만 존재
 * 3. 성능: switch문에서 효율적인 분기 처리 가능
 * 4. 유지보수성: 새로운 상태 코드 추가가 간단함
 */
public enum HttpStatus {
    // ========== 2xx Success (성공) 상태 코드 ==========
    // 클라이언트의 요청이 성공적으로 처리되었음을 나타냄

    // 200 OK: 가장 일반적인 성공 응답
    // GET, POST 등 대부분의 성공적인 요청에 사용
    OK(200, "OK"),

    // 201 Created: 새로운 리소스가 성공적으로 생성됨
    // 주로 POST 요청으로 새 데이터를 생성했을 때 사용
    CREATED(201, "Created"),

    // 204 No Content: 요청은 성공했지만 응답 본문이 없음
    // DELETE 요청이나 업데이트 후 추가 정보가 불필요할 때 사용
    NO_CONTENT(204, "No Content"),

    // 206 Partial Content: 부분 내용 응답 (Range 요청에 대한 응답)
    // Enhanced 코드에서 StaticFileHandler의 Range 요청 처리에 사용
    PARTIAL_CONTENT(206, "Partial Content"),

    // ========== 3xx Redirection (리다이렉션) 상태 코드 ==========
    // 요청을 완료하기 위해 추가 작업이 필요함을 나타냄

    // 301 Moved Permanently: 리소스가 영구적으로 이동됨
    // SEO에 중요하며, 검색엔진이 새 URL로 인덱스를 업데이트함
    MOVED_PERMANENTLY(301, "Moved Permanently"),

    // 302 Found: 리소스가 임시적으로 이동됨
    // 원본 URL이 나중에 다시 사용될 수 있음
    FOUND(302, "Found"),

    // 304 Not Modified: 리소스가 수정되지 않음
    // Enhanced 코드에서 StaticFileHandler의 조건부 요청 처리에 사용
    // If-Modified-Since나 If-None-Match 헤더를 통한 캐시 검증
    NOT_MODIFIED(304, "Not Modified"),

    // ========== 4xx Client Error (클라이언트 오류) 상태 코드 ==========
    // 클라이언트의 잘못된 요청으로 인한 오류

    // 400 Bad Request: 요청 구문이 잘못됨
    // JSON 파싱 오류, 필수 파라미터 누락 등에 사용
    BAD_REQUEST(400, "Bad Request"),

    // 401 Unauthorized: 인증이 필요함
    // Enhanced 코드에서 AuthMiddleware에 사용
    // 로그인이 필요한 리소스에 접근할 때 사용
    UNAUTHORIZED(401, "Unauthorized"),

    // 403 Forbidden: 권한이 부족함
    // Enhanced 코드에서 MiddlewareChain의 SecurityException 처리에 사용
    // 인증은 되었지만 해당 리소스에 대한 권한이 없을 때 사용
    FORBIDDEN(403, "Forbidden"),

    // 404 Not Found: 요청한 리소스를 찾을 수 없음
    // 가장 잘 알려진 HTTP 상태 코드
    NOT_FOUND(404, "Not Found"),

    // 405 Method Not Allowed: 허용되지 않는 HTTP 메서드
    // GET만 지원하는 엔드포인트에 POST 요청을 보냈을 때 등
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),

    // 429 Too Many Requests: 요청 빈도가 너무 높음
    // Enhanced 코드에서 RateLimitMiddleware에 사용
    // 레이트 리미팅에서 요청 한도를 초과했을 때 사용
    TOO_MANY_REQUESTS(429, "Too Many Requests"),

    // ========== 5xx Server Error (서버 오류) 상태 코드 ==========
    // 서버 측 오류로 인해 요청을 처리할 수 없음

    // 500 Internal Server Error: 서버 내부 오류
    // 예상치 못한 예외나 서버 장애 시 사용
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),

    // 501 Not Implemented: 서버가 요청 메서드를 지원하지 않음
    // 아직 구현되지 않은 기능에 대한 요청 시 사용
    NOT_IMPLEMENTED(501, "Not Implemented"),

    // 503 Service Unavailable: 서비스를 일시적으로 사용할 수 없음
    // 서버 과부하나 유지보수 중일 때 사용
    SERVICE_UNAVAILABLE(503, "Service Unavailable");

    // ========== 인스턴스 필드 ==========

    // HTTP 상태 코드의 숫자 값 (예: 200, 404, 500)
    // final로 선언하여 불변성 보장
    private final int code;

    // HTTP 상태 코드에 대한 설명 문구 (예: "OK", "Not Found")
    // HTTP 응답 라인에 포함되는 reason phrase
    private final String reasonPhrase;

    /**
     * HttpStatus 열거형 생성자
     *
     * 생성자를 private로 만들지 않은 이유:
     * - enum의 생성자는 기본적으로 private 또는 package-private만 가능
     * - 외부에서 직접 인스턴스를 생성할 수 없음
     *
     * @param code HTTP 상태 코드 숫자 (100-599 범위)
     * @param reasonPhrase 상태 코드 설명 문구 (HTTP 표준에 정의됨)
     */
    HttpStatus(int code, String reasonPhrase) {
        // this.code: 현재 인스턴스의 code 필드에 값 할당
        // 파라미터와 필드명이 같을 때 this를 사용하여 구분
        this.code = code;

        // reasonPhrase를 그대로 저장
        // HTTP/1.1 RFC에서 정의된 표준 문구 사용
        this.reasonPhrase = reasonPhrase;
    }

    /**
     * HTTP 상태 코드 숫자 반환
     *
     * public으로 공개한 이유:
     * - 외부에서 상태 코드 숫자가 필요한 경우가 많음
     * - HTTP 응답 헤더 생성 시 필요
     *
     * @return 상태 코드 (예: 200, 404, 500)
     */
    public int getCode() {
        // 단순히 저장된 code 값을 반환
        // 불변 객체이므로 복사본을 만들 필요 없음
        return code;
    }

    /**
     * HTTP 상태 코드 설명 문구 반환
     *
     * @return 설명 문구 (예: "OK", "Not Found", "Internal Server Error")
     */
    public String getReasonPhrase() {
        // String은 불변 객체이므로 안전하게 직접 반환
        return reasonPhrase;
    }

    /**
     * 상태 코드 숫자로부터 HttpStatus 열거형 찾기
     *
     * static 메서드로 구현한 이유:
     * - 특정 인스턴스와 관련 없는 유틸리티 기능
     * - 외부에서 숫자로 enum을 찾는 경우가 자주 있음
     *
     * @param code HTTP 상태 코드 숫자
     * @return 해당하는 HttpStatus 또는 기본값 INTERNAL_SERVER_ERROR
     */
    public static HttpStatus fromCode(int code) {
        // values(): enum의 모든 값을 배열로 반환하는 컴파일러 생성 메서드
        // 모든 HttpStatus 값들을 순회하며 일치하는 코드 찾기
        for (HttpStatus status : values()) {
            // status.code: 현재 순회 중인 enum 인스턴스의 code 필드
            // ==를 사용한 이유: int는 primitive 타입이므로 값 비교
            if (status.code == code) return status;
        }

        // 일치하는 코드가 없으면 서버 오류로 처리
        // 알 수 없는 상태 코드는 보통 서버 오류로 간주하는 것이 안전
        return INTERNAL_SERVER_ERROR;
    }

    // ========== 추가된 유틸리티 메서드들 ==========
    // HTTP 상태 코드의 범위별 특성을 확인하는 메서드들

    /**
     * 상태 코드가 성공 범위(2xx)인지 확인
     *
     * static 메서드로 구현한 이유:
     * - 임의의 int 값에 대해서도 확인할 수 있도록
     * - HttpStatus 인스턴스가 없어도 사용 가능
     *
     * @param code 확인할 상태 코드
     * @return 2xx 범위이면 true
     */
    public static boolean isSuccess(int code) {
        // HTTP 표준에 따르면 200-299 범위가 성공 응답
        // >= 200: 200 이상 (포함)
        // < 300: 300 미만 (미포함)
        return code >= 200 && code < 300;
    }

    /**
     * 상태 코드가 리다이렉션 범위(3xx)인지 확인
     *
     * @param code 확인할 상태 코드
     * @return 3xx 범위이면 true
     */
    public static boolean isRedirection(int code) {
        // 300-399 범위가 리다이렉션 응답
        // 클라이언트가 추가 작업을 수행해야 함을 의미
        return code >= 300 && code < 400;
    }

    /**
     * 상태 코드가 클라이언트 오류 범위(4xx)인지 확인
     *
     * @param code 확인할 상태 코드
     * @return 4xx 범위이면 true
     */
    public static boolean isClientError(int code) {
        // 400-499 범위가 클라이언트 오류
        // 클라이언트의 잘못된 요청으로 인한 오류
        return code >= 400 && code < 500;
    }

    /**
     * 상태 코드가 서버 오류 범위(5xx)인지 확인
     *
     * @param code 확인할 상태 코드
     * @return 5xx 범위이면 true
     */
    public static boolean isServerError(int code) {
        // 500-599 범위가 서버 오류
        // 서버 측 문제로 인한 오류
        return code >= 500 && code < 600;
    }

    /**
     * 현재 HttpStatus 인스턴스가 성공 상태인지 확인
     *
     * 인스턴스 메서드로도 제공하는 이유:
     * - 객체 지향적 사용을 위해
     * - 메서드 체이닝이나 람다 표현식에서 사용하기 편함
     *
     * @return 성공 상태이면 true
     */
    public boolean isSuccess() {
        // static 메서드 재사용하여 중복 코드 방지
        // this.code: 현재 인스턴스의 code 값
        return isSuccess(this.code);
    }

    /**
     * 현재 HttpStatus 인스턴스가 리다이렉션 상태인지 확인
     *
     * @return 리다이렉션 상태이면 true
     */
    public boolean isRedirection() {
        // static 메서드를 호출하여 구현 일관성 유지
        return isRedirection(this.code);
    }

    /**
     * 현재 HttpStatus 인스턴스가 클라이언트 오류 상태인지 확인
     *
     * @return 클라이언트 오류 상태이면 true
     */
    public boolean isClientError() {
        // static 메서드와 동일한 로직 사용
        return isClientError(this.code);
    }

    /**
     * 현재 HttpStatus 인스턴스가 서버 오류 상태인지 확인
     *
     * @return 서버 오류 상태이면 true
     */
    public boolean isServerError() {
        // static 메서드를 재사용하여 코드 중복 방지
        return isServerError(this.code);
    }
}