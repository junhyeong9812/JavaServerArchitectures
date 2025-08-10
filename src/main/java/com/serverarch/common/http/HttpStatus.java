package src.main.java.com.serverarch.common.http;

/**
 * HTTP 상태 코드와 관련된 상수와 유틸리티를 제공하는 클래스입니다.
 *
 * 이 클래스는 RFC 7231, RFC 6585, RFC 7538 등에서 정의된
 * 표준 HTTP 상태 코드들을 정리하여 제공합니다.
 *
 * 각 상태 코드는 다음과 같이 분류됩니다:
 * - 1xx: Informational (정보성)
 * - 2xx: Success (성공)
 * - 3xx: Redirection (리다이렉션)
 * - 4xx: Client Error (클라이언트 오류)
 * - 5xx: Server Error (서버 오류)
 */
public final class HttpStatus {

    // 이 클래스는 상수만 제공하므로 인스턴스화 방지
    private HttpStatus() {
        throw new UnsupportedOperationException("이 클래스는 인스턴스화할 수 없습니다");
    }

    // ========== 1xx Informational (정보성 응답) ==========

    /**
     * 100 Continue
     *
     * 클라이언트가 요청의 일부를 보냈고, 서버가 이를 받았으며
     * 나머지 요청을 계속 보내도 된다는 의미입니다.
     * 주로 큰 요청 본문을 보내기 전에 사용됩니다.
     */
    public static final int CONTINUE = 100;

    /**
     * 101 Switching Protocols
     *
     * 서버가 클라이언트의 프로토콜 변경 요청을 수락했다는 의미입니다.
     * 예: HTTP에서 WebSocket으로 업그레이드
     */
    public static final int SWITCHING_PROTOCOLS = 101;

    /**
     * 102 Processing (WebDAV)
     *
     * 서버가 요청을 받았고 처리 중이지만, 아직 사용할 수 있는 응답이 없다는 의미입니다.
     * 긴 처리 시간이 필요한 요청에서 타임아웃을 방지하기 위해 사용됩니다.
     */
    public static final int PROCESSING = 102;

    // ========== 2xx Success (성공) ==========

    /**
     * 200 OK
     *
     * 요청이 성공적으로 처리되었습니다.
     * 가장 일반적인 성공 응답입니다.
     */
    public static final int OK = 200;

    /**
     * 201 Created
     *
     * 요청이 성공적이었고 새로운 리소스가 생성되었습니다.
     * 주로 POST 요청의 결과로 사용됩니다.
     */
    public static final int CREATED = 201;

    /**
     * 202 Accepted
     *
     * 요청이 접수되었지만 아직 처리되지 않았습니다.
     * 비동기 처리나 배치 작업에서 사용됩니다.
     */
    public static final int ACCEPTED = 202;

    /**
     * 203 Non-Authoritative Information
     *
     * 요청이 성공했지만 반환된 메타정보가 원본 서버의 것이 아닙니다.
     * 프록시나 캐시 서버에서 사용됩니다.
     */
    public static final int NON_AUTHORITATIVE_INFORMATION = 203;

    /**
     * 204 No Content
     *
     * 요청이 성공했지만 반환할 내용이 없습니다.
     * 주로 DELETE 요청이나 폼 제출 후에 사용됩니다.
     */
    public static final int NO_CONTENT = 204;

    /**
     * 205 Reset Content
     *
     * 요청이 성공했으며 클라이언트가 문서 뷰를 리셋해야 합니다.
     * 주로 폼을 클리어할 때 사용됩니다.
     */
    public static final int RESET_CONTENT = 205;

    /**
     * 206 Partial Content
     *
     * 부분적인 GET 요청이 성공했습니다.
     * Range 헤더를 사용한 요청에 대한 응답입니다.
     */
    public static final int PARTIAL_CONTENT = 206;

    // ========== 3xx Redirection (리다이렉션) ==========

    /**
     * 300 Multiple Choices
     *
     * 요청에 대해 여러 개의 선택지가 있습니다.
     * 클라이언트가 그중 하나를 선택해야 합니다.
     */
    public static final int MULTIPLE_CHOICES = 300;

    /**
     * 301 Moved Permanently
     *
     * 요청한 리소스가 영구적으로 새 위치로 이동했습니다.
     * 검색 엔진이 새 URL을 인덱싱하게 됩니다.
     */
    public static final int MOVED_PERMANENTLY = 301;

    /**
     * 302 Found
     *
     * 요청한 리소스가 임시적으로 다른 위치에 있습니다.
     * 가장 일반적인 리다이렉트입니다.
     */
    public static final int FOUND = 302;

    /**
     * 303 See Other
     *
     * 요청에 대한 응답이 다른 URI에서 찾을 수 있습니다.
     * GET 메서드로 요청해야 합니다.
     */
    public static final int SEE_OTHER = 303;

    /**
     * 304 Not Modified
     *
     * 리소스가 수정되지 않았습니다.
     * 캐시된 버전을 사용할 수 있습니다.
     */
    public static final int NOT_MODIFIED = 304;

    /**
     * 305 Use Proxy
     *
     * 요청한 리소스는 프록시를 통해서만 접근할 수 있습니다.
     * 보안상 이유로 거의 사용되지 않습니다.
     */
    public static final int USE_PROXY = 305;

    /**
     * 307 Temporary Redirect
     *
     * 요청한 리소스가 임시적으로 다른 URI로 이동했습니다.
     * 원래 요청 메서드를 유지해야 합니다.
     */
    public static final int TEMPORARY_REDIRECT = 307;

    /**
     * 308 Permanent Redirect
     *
     * 요청한 리소스가 영구적으로 다른 URI로 이동했습니다.
     * 원래 요청 메서드를 유지해야 합니다.
     */
    public static final int PERMANENT_REDIRECT = 308;

    // ========== 4xx Client Error (클라이언트 오류) ==========

    /**
     * 400 Bad Request
     *
     * 클라이언트의 요청이 잘못되었습니다.
     * 구문 오류나 잘못된 매개변수 등이 원인입니다.
     */
    public static final int BAD_REQUEST = 400;

    /**
     * 401 Unauthorized
     *
     * 인증이 필요합니다.
     * 사용자가 로그인하지 않았거나 인증 정보가 잘못되었습니다.
     */
    public static final int UNAUTHORIZED = 401;

    /**
     * 402 Payment Required
     *
     * 결제가 필요합니다.
     * 미래 사용을 위해 예약된 상태 코드입니다.
     */
    public static final int PAYMENT_REQUIRED = 402;

    /**
     * 403 Forbidden
     *
     * 서버가 요청을 이해했지만 권한이 없어 거부했습니다.
     * 인증과 달리 재인증해도 접근할 수 없습니다.
     */
    public static final int FORBIDDEN = 403;

    /**
     * 404 Not Found
     *
     * 요청한 리소스를 찾을 수 없습니다.
     * 가장 잘 알려진 HTTP 상태 코드 중 하나입니다.
     */
    public static final int NOT_FOUND = 404;

    /**
     * 405 Method Not Allowed
     *
     * 요청 메서드가 해당 리소스에서 허용되지 않습니다.
     * Allow 헤더에 허용되는 메서드를 포함해야 합니다.
     */
    public static final int METHOD_NOT_ALLOWED = 405;

    /**
     * 406 Not Acceptable
     *
     * 요청의 Accept 헤더에 명시된 형식으로 응답할 수 없습니다.
     */
    public static final int NOT_ACCEPTABLE = 406;

    /**
     * 407 Proxy Authentication Required
     *
     * 프록시 인증이 필요합니다.
     */
    public static final int PROXY_AUTHENTICATION_REQUIRED = 407;

    /**
     * 408 Request Timeout
     *
     * 요청 시간이 초과되었습니다.
     * 클라이언트가 요청을 완성하는데 너무 오래 걸렸습니다.
     */
    public static final int REQUEST_TIMEOUT = 408;

    /**
     * 409 Conflict
     *
     * 요청이 현재 리소스 상태와 충돌합니다.
     * 주로 PUT 요청에서 발생합니다.
     */
    public static final int CONFLICT = 409;

    /**
     * 410 Gone
     *
     * 요청한 리소스가 영구적으로 사라졌습니다.
     * 404와 달리 리소스가 의도적으로 제거되었음을 의미합니다.
     */
    public static final int GONE = 410;

    /**
     * 411 Length Required
     *
     * Content-Length 헤더가 필요합니다.
     */
    public static final int LENGTH_REQUIRED = 411;

    /**
     * 412 Precondition Failed
     *
     * 요청 헤더의 사전 조건이 실패했습니다.
     * If-Match, If-None-Match 등의 조건부 헤더 관련입니다.
     */
    public static final int PRECONDITION_FAILED = 412;

    /**
     * 413 Payload Too Large
     *
     * 요청 본문이 너무 큽니다.
     * 서버가 처리할 수 있는 크기를 초과했습니다.
     */
    public static final int PAYLOAD_TOO_LARGE = 413;

    /**
     * 414 URI Too Long
     *
     * 요청 URI가 너무 깁니다.
     * 서버가 처리할 수 있는 길이를 초과했습니다.
     */
    public static final int URI_TOO_LONG = 414;

    /**
     * 415 Unsupported Media Type
     *
     * 요청의 미디어 타입을 서버가 지원하지 않습니다.
     */
    public static final int UNSUPPORTED_MEDIA_TYPE = 415;

    /**
     * 416 Range Not Satisfiable
     *
     * 요청한 Range를 만족할 수 없습니다.
     * 파일 크기보다 큰 범위를 요청한 경우 등입니다.
     */
    public static final int RANGE_NOT_SATISFIABLE = 416;

    /**
     * 417 Expectation Failed
     *
     * Expect 헤더의 기대를 만족할 수 없습니다.
     */
    public static final int EXPECTATION_FAILED = 417;

    /**
     * 418 I'm a teapot
     *
     * 만우절 RFC의 일부입니다.
     * 일부 서버에서 유머러스한 목적으로 사용됩니다.
     */
    public static final int IM_A_TEAPOT = 418;

    /**
     * 422 Unprocessable Entity
     *
     * 요청은 올바르지만 의미상 오류가 있어 처리할 수 없습니다.
     */
    public static final int UNPROCESSABLE_ENTITY = 422;

    /**
     * 429 Too Many Requests
     *
     * 클라이언트가 너무 많은 요청을 보냈습니다.
     * 속도 제한(rate limiting) 적용 시 사용됩니다.
     */
    public static final int TOO_MANY_REQUESTS = 429;

    // ========== 5xx Server Error (서버 오류) ==========

    /**
     * 500 Internal Server Error
     *
     * 서버 내부 오류가 발생했습니다.
     * 가장 일반적인 서버 오류 응답입니다.
     */
    public static final int INTERNAL_SERVER_ERROR = 500;

    /**
     * 501 Not Implemented
     *
     * 서버가 요청을 이행하는 데 필요한 기능을 지원하지 않습니다.
     */
    public static final int NOT_IMPLEMENTED = 501;

    /**
     * 502 Bad Gateway
     *
     * 게이트웨이나 프록시 서버가 상위 서버로부터 잘못된 응답을 받았습니다.
     */
    public static final int BAD_GATEWAY = 502;

    /**
     * 503 Service Unavailable
     *
     * 서비스를 이용할 수 없습니다.
     * 일시적인 과부하나 유지보수로 인한 경우가 많습니다.
     */
    public static final int SERVICE_UNAVAILABLE = 503;

    /**
     * 504 Gateway Timeout
     *
     * 게이트웨이나 프록시 서버가 상위 서버로부터 응답을 받지 못했습니다.
     */
    public static final int GATEWAY_TIMEOUT = 504;

    /**
     * 505 HTTP Version Not Supported
     *
     * 서버가 요청에 사용된 HTTP 버전을 지원하지 않습니다.
     */
    public static final int HTTP_VERSION_NOT_SUPPORTED = 505;

    /**
     * 511 Network Authentication Required
     *
     * 네트워크 접근을 위해 인증이 필요합니다.
     * 주로 WiFi 핫스팟에서 사용됩니다.
     */
    public static final int NETWORK_AUTHENTICATION_REQUIRED = 511;

    // ========== 유틸리티 메서드들 ==========

    /**
     * 상태 코드가 정보성 응답(1xx)인지 확인합니다.
     */
    public static boolean isInformational(int statusCode) {
        return statusCode >= 100 && statusCode < 200;
    }

    /**
     * 상태 코드가 성공(2xx)인지 확인합니다.
     */
    public static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * 상태 코드가 리다이렉션(3xx)인지 확인합니다.
     */
    public static boolean isRedirection(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
    }

    /**
     * 상태 코드가 클라이언트 오류(4xx)인지 확인합니다.
     */
    public static boolean isClientError(int statusCode) {
        return statusCode >= 400 && statusCode < 500;
    }

    /**
     * 상태 코드가 서버 오류(5xx)인지 확인합니다.
     */
    public static boolean isServerError(int statusCode) {
        return statusCode >= 500 && statusCode < 600;
    }

    /**
     * 상태 코드가 에러(4xx 또는 5xx)인지 확인합니다.
     */
    public static boolean isError(int statusCode) {
        return isClientError(statusCode) || isServerError(statusCode);
    }

    /**
     * 상태 코드에 대한 기본 상태 메시지를 반환합니다.
     */
    public static String getReasonPhrase(int statusCode) {
        switch (statusCode) {
            // 1xx Informational
            case CONTINUE: return "Continue";
            case SWITCHING_PROTOCOLS: return "Switching Protocols";
            case PROCESSING: return "Processing";

            // 2xx Success
            case OK: return "OK";
            case CREATED: return "Created";
            case ACCEPTED: return "Accepted";
            case NON_AUTHORITATIVE_INFORMATION: return "Non-Authoritative Information";
            case NO_CONTENT: return "No Content";
            case RESET_CONTENT: return "Reset Content";
            case PARTIAL_CONTENT: return "Partial Content";

            // 3xx Redirection
            case MULTIPLE_CHOICES: return "Multiple Choices";
            case MOVED_PERMANENTLY: return "Moved Permanently";
            case FOUND: return "Found";
            case SEE_OTHER: return "See Other";
            case NOT_MODIFIED: return "Not Modified";
            case USE_PROXY: return "Use Proxy";
            case TEMPORARY_REDIRECT: return "Temporary Redirect";
            case PERMANENT_REDIRECT: return "Permanent Redirect";

            // 4xx Client Error
            case BAD_REQUEST: return "Bad Request";
            case UNAUTHORIZED: return "Unauthorized";
            case PAYMENT_REQUIRED: return "Payment Required";
            case FORBIDDEN: return "Forbidden";
            case NOT_FOUND: return "Not Found";
            case METHOD_NOT_ALLOWED: return "Method Not Allowed";
            case NOT_ACCEPTABLE: return "Not Acceptable";
            case PROXY_AUTHENTICATION_REQUIRED: return "Proxy Authentication Required";
            case REQUEST_TIMEOUT: return "Request Timeout";
            case CONFLICT: return "Conflict";
            case GONE: return "Gone";
            case LENGTH_REQUIRED: return "Length Required";
            case PRECONDITION_FAILED: return "Precondition Failed";
            case PAYLOAD_TOO_LARGE: return "Payload Too Large";
            case URI_TOO_LONG: return "URI Too Long";
            case UNSUPPORTED_MEDIA_TYPE: return "Unsupported Media Type";
            case RANGE_NOT_SATISFIABLE: return "Range Not Satisfiable";
            case EXPECTATION_FAILED: return "Expectation Failed";
            case IM_A_TEAPOT: return "I'm a teapot";
            case UNPROCESSABLE_ENTITY: return "Unprocessable Entity";
            case TOO_MANY_REQUESTS: return "Too Many Requests";

            // 5xx Server Error
            case INTERNAL_SERVER_ERROR: return "Internal Server Error";
            case NOT_IMPLEMENTED: return "Not Implemented";
            case BAD_GATEWAY: return "Bad Gateway";
            case SERVICE_UNAVAILABLE: return "Service Unavailable";
            case GATEWAY_TIMEOUT: return "Gateway Timeout";
            case HTTP_VERSION_NOT_SUPPORTED: return "HTTP Version Not Supported";
            case NETWORK_AUTHENTICATION_REQUIRED: return "Network Authentication Required";

            default: return "Unknown Status";
        }
    }
}
