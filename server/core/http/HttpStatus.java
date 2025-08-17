package server.core.http;

/**
 * HTTP 상태 코드 정의
 * RFC 7231, 7232, 7233, 7235에 정의된 모든 표준 상태 코드
 */
// enum: 상수들의 집합을 정의하는 특별한 클래스
// HTTP 상태 코드는 정해진 값들만 존재하므로 enum이 적합
public enum HttpStatus {

    // === 1xx Informational (정보성 응답) ===
    // 요청을 받았으며 프로세스를 계속 진행

    // 100 Continue: 클라이언트가 계속해서 요청을 보내도 됨
    CONTINUE(100, "Continue"),

    // 101 Switching Protocols: 서버가 프로토콜을 변경함 (HTTP -> WebSocket 등)
    SWITCHING_PROTOCOLS(101, "Switching Protocols"),

    // 102 Processing: 요청을 받았지만 아직 처리 중 (WebDAV)
    PROCESSING(102, "Processing"),

    // 103 Early Hints: 최종 응답 전에 일부 헤더를 미리 전송
    EARLY_HINTS(103, "Early Hints"),

    // === 2xx Success (성공) ===
    // 요청을 성공적으로 받았으며 인식했고 수용함

    // 200 OK: 요청이 성공적으로 처리됨 (가장 일반적인 성공 응답)
    OK(200, "OK"),

    // 201 Created: 요청이 성공적이었으며 새로운 리소스가 생성됨
    CREATED(201, "Created"),

    // 202 Accepted: 요청을 수신하였지만 아직 처리하지 않음 (비동기 처리)
    ACCEPTED(202, "Accepted"),

    // 203 Non-Authoritative Information: 신뢰할 수 없는 정보
    NON_AUTHORITATIVE_INFORMATION(203, "Non-Authoritative Information"),

    // 204 No Content: 요청은 성공했지만 응답할 내용이 없음
    NO_CONTENT(204, "No Content"),

    // 205 Reset Content: 요청은 성공했고 사용자 에이전트는 문서 뷰를 리셋해야 함
    RESET_CONTENT(205, "Reset Content"),

    // 206 Partial Content: 범위 요청의 일부만 전송 (동영상 스트리밍 등)
    PARTIAL_CONTENT(206, "Partial Content"),

    // 207 Multi-Status: 여러 리소스의 상태 정보 (WebDAV)
    MULTI_STATUS(207, "Multi-Status"),

    // 208 Already Reported: 이미 보고된 항목 (WebDAV)
    ALREADY_REPORTED(208, "Already Reported"),

    // 226 IM Used: Instance Manipulation이 사용됨
    IM_USED(226, "IM Used"),

    // === 3xx Redirection (리다이렉션) ===
    // 요청 완료를 위해 추가 행동이 필요함

    // 300 Multiple Choices: 여러 개의 가능한 응답이 있음
    MULTIPLE_CHOICES(300, "Multiple Choices"),

    // 301 Moved Permanently: 리소스가 영구적으로 이동됨 (SEO에서 중요)
    MOVED_PERMANENTLY(301, "Moved Permanently"),

    // 302 Found: 리소스가 일시적으로 다른 위치에 있음
    FOUND(302, "Found"),

    // 303 See Other: 다른 위치에서 GET으로 요청하라
    SEE_OTHER(303, "See Other"),

    // 304 Not Modified: 캐시된 버전을 사용하라 (조건부 요청)
    NOT_MODIFIED(304, "Not Modified"),

    // 305 Use Proxy: 프록시를 통해서만 접근 가능 (deprecated)
    USE_PROXY(305, "Use Proxy"),

    // 307 Temporary Redirect: 일시적 리다이렉트, 메서드 변경 금지
    TEMPORARY_REDIRECT(307, "Temporary Redirect"),

    // 308 Permanent Redirect: 영구적 리다이렉트, 메서드 변경 금지
    PERMANENT_REDIRECT(308, "Permanent Redirect"),

    // === 4xx Client Error (클라이언트 오류) ===
    // 클라이언트에 오류가 있음을 나타냄

    // 400 Bad Request: 잘못된 요청 문법
    BAD_REQUEST(400, "Bad Request"),

    // 401 Unauthorized: 인증이 필요함
    UNAUTHORIZED(401, "Unauthorized"),

    // 402 Payment Required: 결제가 필요함 (거의 사용 안됨)
    PAYMENT_REQUIRED(402, "Payment Required"),

    // 403 Forbidden: 서버가 요청을 이해했지만 권한 부족으로 거부
    FORBIDDEN(403, "Forbidden"),

    // 404 Not Found: 요청한 리소스를 찾을 수 없음
    NOT_FOUND(404, "Not Found"),

    // 405 Method Not Allowed: 허용되지 않는 HTTP 메서드
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),

    // 406 Not Acceptable: Accept 헤더에 맞는 콘텐츠가 없음
    NOT_ACCEPTABLE(406, "Not Acceptable"),

    // 407 Proxy Authentication Required: 프록시 인증이 필요
    PROXY_AUTHENTICATION_REQUIRED(407, "Proxy Authentication Required"),

    // 408 Request Timeout: 요청 시간 초과
    REQUEST_TIMEOUT(408, "Request Timeout"),

    // 409 Conflict: 요청이 현재 리소스 상태와 충돌
    CONFLICT(409, "Conflict"),

    // 410 Gone: 리소스가 영구적으로 삭제됨
    GONE(410, "Gone"),

    // 411 Length Required: Content-Length 헤더가 필요함
    LENGTH_REQUIRED(411, "Length Required"),

    // 412 Precondition Failed: 전제 조건이 실패함
    PRECONDITION_FAILED(412, "Precondition Failed"),

    // 413 Payload Too Large: 요청 엔터티가 너무 큼
    PAYLOAD_TOO_LARGE(413, "Payload Too Large"),

    // 414 URI Too Long: 요청 URI가 너무 김
    URI_TOO_LONG(414, "URI Too Long"),

    // 415 Unsupported Media Type: 지원하지 않는 미디어 타입
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),

    // 416 Range Not Satisfiable: 요청한 범위를 충족할 수 없음
    RANGE_NOT_SATISFIABLE(416, "Range Not Satisfiable"),

    // 417 Expectation Failed: Expect 헤더의 기대를 충족할 수 없음
    EXPECTATION_FAILED(417, "Expectation Failed"),

    // 418 I'm a teapot: 만우절 농담으로 만들어진 상태 코드 (RFC 2324)
    IM_A_TEAPOT(418, "I'm a teapot"),

    // 421 Misdirected Request: 잘못된 서버로 요청이 전달됨
    MISDIRECTED_REQUEST(421, "Misdirected Request"),

    // 422 Unprocessable Entity: 문법은 맞지만 의미상 오류 (WebDAV)
    UNPROCESSABLE_ENTITY(422, "Unprocessable Entity"),

    // 423 Locked: 리소스가 잠겨있음 (WebDAV)
    LOCKED(423, "Locked"),

    // 424 Failed Dependency: 의존성 실패 (WebDAV)
    FAILED_DEPENDENCY(424, "Failed Dependency"),

    // 425 Too Early: 너무 이른 요청 (replay attack 방지)
    TOO_EARLY(425, "Too Early"),

    // 426 Upgrade Required: 프로토콜 업그레이드가 필요함
    UPGRADE_REQUIRED(426, "Upgrade Required"),

    // 428 Precondition Required: 전제 조건 헤더가 필요함
    PRECONDITION_REQUIRED(428, "Precondition Required"),

    // 429 Too Many Requests: 너무 많은 요청 (rate limiting)
    TOO_MANY_REQUESTS(429, "Too Many Requests"),

    // 431 Request Header Fields Too Large: 요청 헤더가 너무 큼
    REQUEST_HEADER_FIELDS_TOO_LARGE(431, "Request Header Fields Too Large"),

    // 451 Unavailable For Legal Reasons: 법적 이유로 사용 불가
    UNAVAILABLE_FOR_LEGAL_REASONS(451, "Unavailable For Legal Reasons"),

    // === 5xx Server Error (서버 오류) ===
    // 서버가 요청을 수행할 수 없음을 나타냄

    // 500 Internal Server Error: 일반적인 서버 내부 오류
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),

    // 501 Not Implemented: 서버가 요청 메서드를 지원하지 않음
    NOT_IMPLEMENTED(501, "Not Implemented"),

    // 502 Bad Gateway: 게이트웨이나 프록시 서버에서 잘못된 응답
    BAD_GATEWAY(502, "Bad Gateway"),

    // 503 Service Unavailable: 서비스를 일시적으로 사용할 수 없음
    SERVICE_UNAVAILABLE(503, "Service Unavailable"),

    // 504 Gateway Timeout: 게이트웨이 응답 시간 초과
    GATEWAY_TIMEOUT(504, "Gateway Timeout"),

    // 505 HTTP Version Not Supported: HTTP 버전을 지원하지 않음
    HTTP_VERSION_NOT_SUPPORTED(505, "HTTP Version Not Supported"),

    // 506 Variant Also Negotiates: 내부 구성 오류
    VARIANT_ALSO_NEGOTIATES(506, "Variant Also Negotiates"),

    // 507 Insufficient Storage: 저장 공간 부족 (WebDAV)
    INSUFFICIENT_STORAGE(507, "Insufficient Storage"),

    // 508 Loop Detected: 무한 루프 감지 (WebDAV)
    LOOP_DETECTED(508, "Loop Detected"),

    // 510 Not Extended: 요청에 필요한 확장이 없음
    NOT_EXTENDED(510, "Not Extended"),

    // 511 Network Authentication Required: 네트워크 인증이 필요함
    NETWORK_AUTHENTICATION_REQUIRED(511, "Network Authentication Required");

    // HTTP 상태 코드의 구성 요소들
    // final: 한번 초기화되면 변경 불가능
    private final int code;           // 상태 코드 숫자 (200, 404 등)
    private final String reasonPhrase; // 상태 메시지 ("OK", "Not Found" 등)

    // enum 생성자 (항상 private)
    // 각 enum 상수가 생성될 때 호출됨
    HttpStatus(int code, String reasonPhrase) {
        this.code = code;
        this.reasonPhrase = reasonPhrase;
    }

    // 상태 코드 숫자를 반환하는 getter
    public int getCode() {
        return code;
    }

    // 상태 메시지를 반환하는 getter
    public String getReasonPhrase() {
        return reasonPhrase;
    }

    /**
     * 상태 코드로부터 HttpStatus 찾기
     */
    public static HttpStatus fromCode(int code) {
        // 모든 enum 상수들을 순회하며 일치하는 코드 찾기
        // values(): enum의 모든 상수들을 배열로 반환 (자동 생성 메서드)
        for (HttpStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }

        // 일치하는 상태 코드가 없으면 예외 발생
        // IllegalArgumentException: 잘못된 매개변수 예외
        throw new IllegalArgumentException("Unknown HTTP status code: " + code);
    }

    /**
     * 상태 코드 카테고리 확인
     */

    // 1xx: 정보성 응답인지 확인
    public boolean isInformational() {
        return code >= 100 && code < 200;
    }

    // 2xx: 성공 응답인지 확인
    public boolean isSuccess() {
        return code >= 200 && code < 300;
    }

    // 3xx: 리다이렉션 응답인지 확인
    public boolean isRedirection() {
        return code >= 300 && code < 400;
    }

    // 4xx: 클라이언트 오류인지 확인
    public boolean isClientError() {
        return code >= 400 && code < 500;
    }

    // 5xx: 서버 오류인지 확인
    public boolean isServerError() {
        return code >= 500 && code < 600;
    }

    // 4xx 또는 5xx: 오류 응답인지 확인
    public boolean isError() {
        // ||: 논리합 연산자, 하나라도 true면 전체가 true
        return isClientError() || isServerError();
    }

    /**
     * HTTP 응답 라인 형식으로 반환
     */
    public String toStatusLine() {
        // "HTTP/1.1 200 OK" 형태의 완전한 상태 라인 생성
        // HTTP 응답의 첫 번째 줄에 사용됨
        return "HTTP/1.1 " + code + " " + reasonPhrase;
    }

    /**
     * 문자열 표현 반환
     * Object 클래스의 toString() 메서드 오버라이드
     */
    @Override  // 어노테이션: 부모 클래스 메서드를 재정의함을 명시
    public String toString() {
        // "200 OK" 형태로 반환
        // 로깅이나 디버깅에서 상태 정보를 확인할 때 유용
        return code + " " + reasonPhrase;
    }
}