package com.serverarch.eventloop.http; // 패키지 선언 - EventLoop 서버의 HTTP 관련 클래스들을 담는 패키지

// === 공통 HTTP 라이브러리 Import ===
import com.serverarch.common.http.HttpHeaders; // HttpHeaders 클래스 - HTTP 헤더들을 관리하는 클래스
import com.serverarch.common.http.HttpStatus; // HttpStatus 열거형 - HTTP 상태 코드를 관리하는 enum 클래스

/**
 * EventLoop 서버용 HttpResponse 인터페이스
 *
 * 설계 목적:
 * 1. 기존 Traditional 서버의 HttpResponse 클래스와 명확한 분리
 * 2. EventLoop 서버의 비동기 처리 모델에 최적화
 * 3. 바이트 기반 바디 처리로 바이너리 데이터 지원
 * 4. 불변 객체 설계를 통한 스레드 안전성 보장
 * 5. 정적 팩토리 메서드를 통한 편리한 응답 생성
 */
public interface HttpResponse { // public 인터페이스 선언 - 다른 패키지에서도 접근 가능

    // ========== 기본 정보 조회 메서드들 ==========

    /**
     * HTTP 상태 코드 반환
     * HTTP 응답의 처리 결과를 나타내는 3자리 숫자 코드
     *
     * @return HTTP 상태 코드 (200, 404, 500 등의 int 값)
     */
    int getStatusCode(); // 추상 메서드 - 구현체에서 반드시 구현해야 함

    /**
     * HTTP 상태 메시지 반환
     * HTTP 상태 코드에 대응하는 설명 문구
     *
     * @return HTTP 상태 메시지 ("OK", "Not Found", "Internal Server Error" 등의 String 값)
     */
    String getStatusMessage(); // 추상 메서드 - HTTP 표준에 정의된 reason phrase 반환

    /**
     * HTTP 상태 객체 반환
     * 상태 코드와 메시지를 포함하는 HttpStatus 열거형 객체
     *
     * @return HttpStatus 객체 (HttpStatus.OK, HttpStatus.NOT_FOUND 등)
     */
    HttpStatus getStatus(); // 추상 메서드 - 전체 상태 정보를 담은 객체 반환

    /**
     * HTTP 헤더들 반환
     * Content-Type, Content-Length, Cache-Control 등의 HTTP 메타데이터
     *
     * @return HTTP 헤더 객체 (HttpHeaders 타입, never null)
     */
    HttpHeaders getHeaders(); // 추상 메서드 - 응답의 모든 헤더 정보 반환

    /**
     * 응답 바디를 바이트 배열로 반환
     * 텍스트뿐만 아니라 이미지, 파일 등 바이너리 데이터도 처리 가능
     *
     * @return 응답 바디 바이트 배열 (byte[] 타입, null 가능)
     */
    byte[] getBody(); // 추상 메서드 - 원시 바이트 데이터 반환으로 모든 형태의 컨텐츠 지원

    // ========== 편의 메서드들 (default 구현) ==========

    /**
     * 응답 바디를 문자열로 반환 (편의 메서드)
     * 바이트 배열을 UTF-8로 디코딩하여 사람이 읽을 수 있는 문자열로 변환
     *
     * @return 응답 바디를 UTF-8로 디코딩한 문자열 (String 타입)
     */
    default String getBodyAsString() { // default 메서드 - 인터페이스에서 기본 구현 제공, 구현체에서 선택적으로 오버라이드 가능
        byte[] body = getBody(); // getBody() 메서드 호출하여 바이트 배열 조회
        // 삼항 연산자 - body가 null이 아니면 문자열 변환, null이면 빈 문자열 반환
        return body != null ? new String(body, java.nio.charset.StandardCharsets.UTF_8) : "";
        // new String() 생성자 - 바이트 배열과 문자 인코딩을 받아 문자열로 변환
        // java.nio.charset.StandardCharsets.UTF_8 - UTF-8 인코딩 상수, 웹에서 가장 널리 사용되는 인코딩
    }

    // ========== 상태 확인 메서드들 (default 구현) ==========

    /**
     * 응답이 성공적인지 확인
     * HTTP 상태 코드가 2xx 범위(200-299)인지 검사
     *
     * @return 2xx 상태 코드이면 true, 그 외는 false
     */
    default boolean isSuccess() { // default 메서드 - 성공 상태 확인 로직
        return getStatus().isSuccess(); // getStatus() 호출 후 HttpStatus.isSuccess() 메서드 사용
        // 메서드 체이닝 - 객체에서 메서드를 연속으로 호출하는 패턴
    }

    /**
     * 응답이 에러인지 확인
     * HTTP 상태 코드가 4xx(클라이언트 오류) 또는 5xx(서버 오류) 범위인지 검사
     *
     * @return 4xx 또는 5xx 상태 코드이면 true, 그 외는 false
     */
    default boolean isError() { // default 메서드 - 에러 상태 확인 로직
        HttpStatus status = getStatus(); // getStatus() 메서드 호출하여 상태 객체 조회
        // 논리 OR 연산자(||) - 두 조건 중 하나라도 true이면 true 반환
        return status.isClientError() || status.isServerError();
        // status.isClientError() - 4xx 범위 확인 (400-499)
        // status.isServerError() - 5xx 범위 확인 (500-599)
    }

    /**
     * 응답이 리다이렉션인지 확인
     * HTTP 상태 코드가 3xx 범위(300-399)인지 검사
     *
     * @return 3xx 상태 코드이면 true, 그 외는 false
     */
    default boolean isRedirection() { // default 메서드 - 리다이렉션 상태 확인 로직
        return getStatus().isRedirection(); // HttpStatus.isRedirection() 메서드로 3xx 범위 확인
    }

    /**
     * 응답에 바디가 있는지 확인
     * Content-Length가 0보다 크거나 바디 바이트 배열이 비어있지 않은지 검사
     *
     * @return 바디가 있으면 true, 없으면 false
     */
    default boolean hasBody() { // default 메서드 - 바디 존재 여부 확인
        byte[] body = getBody(); // getBody() 메서드로 바디 바이트 배열 조회
        // body != null - null 체크 (NullPointerException 방지)
        // body.length > 0 - 배열 길이가 0보다 큰지 확인
        // 논리 AND 연산자(&&) - 두 조건이 모두 true일 때만 true 반환
        return body != null && body.length > 0;
    }

    // ========== 정적 팩토리 메서드들 ==========
    // static 메서드 - 인스턴스 생성 없이 인터페이스명으로 직접 호출 가능
    // 팩토리 패턴 - 객체 생성 로직을 캡슐화하여 사용자에게 편의성 제공

    /**
     * 200 OK 응답 생성 (문자열 바디)
     * 가장 일반적인 성공 응답을 간편하게 생성
     *
     * @param body 응답 바디 문자열 (String 타입)
     * @return HttpResponse 객체 (200 OK 상태)
     */
    static HttpResponse ok(String body) { // static 메서드 - 클래스 레벨 메서드, 인스턴스 없이 호출 가능
        // SimpleHttpResponse.create() 정적 팩토리 메서드 호출
        return SimpleHttpResponse.create(HttpStatus.OK, body);
        // HttpStatus.OK - 200 OK 상태 코드 상수
        // body - 사용자가 전달한 응답 내용
    }

    /**
     * 200 OK 응답 생성 (바이트 배열 바디)
     * 바이너리 데이터(이미지, 파일 등)를 포함한 성공 응답 생성
     *
     * @param body 응답 바디 바이트 배열 (byte[] 타입)
     * @return HttpResponse 객체 (200 OK 상태)
     */
    static HttpResponse ok(byte[] body) { // 메서드 오버로딩 - 같은 이름이지만 매개변수 타입이 다른 메서드
        // 바이트 배열을 직접 받는 팩토리 메서드 호출
        return SimpleHttpResponse.create(HttpStatus.OK, body);
    }

    /**
     * 404 Not Found 응답 생성
     * 요청한 리소스를 찾을 수 없을 때 사용하는 표준 에러 응답
     *
     * @param message 에러 메시지 (String 타입)
     * @return HttpResponse 객체 (404 Not Found 상태)
     */
    static HttpResponse notFound(String message) {
        return SimpleHttpResponse.create(HttpStatus.NOT_FOUND, message);
        // HttpStatus.NOT_FOUND - 404 Not Found 상태 코드 상수
    }

    /**
     * 400 Bad Request 응답 생성
     * 클라이언트의 잘못된 요청(구문 오류, 유효하지 않은 매개변수 등)에 대한 응답
     *
     * @param message 에러 메시지 (String 타입)
     * @return HttpResponse 객체 (400 Bad Request 상태)
     */
    static HttpResponse badRequest(String message) {
        return SimpleHttpResponse.create(HttpStatus.BAD_REQUEST, message);
        // HttpStatus.BAD_REQUEST - 400 Bad Request 상태 코드 상수
    }

    /**
     * 500 Internal Server Error 응답 생성
     * 서버 내부 오류(예상치 못한 예외, 시스템 장애 등)에 대한 응답
     *
     * @param message 에러 메시지 (String 타입)
     * @return HttpResponse 객체 (500 Internal Server Error 상태)
     */
    static HttpResponse serverError(String message) {
        return SimpleHttpResponse.create(HttpStatus.INTERNAL_SERVER_ERROR, message);
        // HttpStatus.INTERNAL_SERVER_ERROR - 500 Internal Server Error 상태 코드 상수
    }

    /**
     * 커스텀 상태 코드 응답 생성 (문자열 바디)
     * 사용자가 직접 지정한 HTTP 상태와 메시지로 응답 생성
     *
     * @param status HTTP 상태 (HttpStatus 타입)
     * @param body 응답 바디 (String 타입)
     * @return HttpResponse 객체 (지정된 상태)
     */
    static HttpResponse of(HttpStatus status, String body) {
        return SimpleHttpResponse.create(status, body);
        // 범용 팩토리 메서드 - 모든 HTTP 상태 코드에 대응 가능
    }

    /**
     * 커스텀 상태 코드 응답 생성 (바이트 배열 바디)
     * 사용자가 직접 지정한 HTTP 상태와 바이너리 데이터로 응답 생성
     *
     * @param status HTTP 상태 (HttpStatus 타입)
     * @param body 응답 바디 (byte[] 타입)
     * @return HttpResponse 객체 (지정된 상태)
     */
    static HttpResponse of(HttpStatus status, byte[] body) {
        return SimpleHttpResponse.create(status, body);
        // 바이너리 데이터를 지원하는 범용 팩토리 메서드
    }

    // ========== 추가 편의 팩토리 메서드들 ==========

    /**
     * 빈 바디를 가진 응답 생성
     * HEAD 요청이나 204 No Content 응답 등에 사용
     *
     * @param status HTTP 상태 (HttpStatus 타입)
     * @return HttpResponse 객체 (빈 바디)
     */
    static HttpResponse empty(HttpStatus status) {
        // new byte[0] - 크기가 0인 빈 바이트 배열 생성
        return SimpleHttpResponse.create(status, new byte[0]);
    }

    /**
     * 204 No Content 응답 생성
     * 요청은 성공했지만 응답 바디가 없는 경우(PUT, DELETE 등)에 사용
     *
     * @return HttpResponse 객체 (204 No Content 상태, 빈 바디)
     */
    static HttpResponse noContent() {
        return empty(HttpStatus.NO_CONTENT);
        // HttpStatus.NO_CONTENT - 204 No Content 상태 코드 상수
        // empty() 메서드 재사용으로 코드 중복 방지
    }

    /**
     * 302 Found (임시 리다이렉션) 응답 생성
     * 클라이언트를 다른 URL로 임시 이동시킬 때 사용
     *
     * @param location 리다이렉션할 URL (String 타입)
     * @return HttpResponse 객체 (302 Found 상태, Location 헤더 포함)
     */
    static HttpResponse redirect(String location) {
        // 리다이렉션 응답 생성 후 Location 헤더 설정
        HttpResponse response = SimpleHttpResponse.create(HttpStatus.FOUND, "");
        // HttpStatus.FOUND - 302 Found 상태 코드 상수

        // Location 헤더 설정 - 브라우저가 이동할 URL 지정
        response.getHeaders().set("Location", location);
        // getHeaders() - HttpHeaders 객체 반환
        // set() - 헤더 이름과 값을 설정하는 메서드

        return response;
    }

    /**
     * 401 Unauthorized 응답 생성
     * 인증이 필요하거나 인증이 실패했을 때 사용
     *
     * @param message 인증 오류 메시지 (String 타입)
     * @return HttpResponse 객체 (401 Unauthorized 상태)
     */
    static HttpResponse unauthorized(String message) {
        return SimpleHttpResponse.create(HttpStatus.UNAUTHORIZED, message);
        // HttpStatus.UNAUTHORIZED - 401 Unauthorized 상태 코드 상수
    }

    /**
     * 403 Forbidden 응답 생성
     * 인증은 되었지만 권한이 부족할 때 사용
     *
     * @param message 권한 오류 메시지 (String 타입)
     * @return HttpResponse 객체 (403 Forbidden 상태)
     */
    static HttpResponse forbidden(String message) {
        return SimpleHttpResponse.create(HttpStatus.FORBIDDEN, message);
        // HttpStatus.FORBIDDEN - 403 Forbidden 상태 코드 상수
    }

    /**
     * 413 Request Entity Too Large 응답 생성
     * 클라이언트가 전송한 요청 바디의 크기가 서버의 제한을 초과했을 때 사용
     *
     * @param message 크기 제한 오류 메시지 (String 타입)
     * @return HttpResponse 객체 (413 Request Entity Too Large 상태)
     */
    static HttpResponse requestEntityTooLarge(String message) {
        return SimpleHttpResponse.create(HttpStatus.REQUEST_ENTITY_TOO_LARGE, message);
        // HttpStatus.REQUEST_ENTITY_TOO_LARGE - 413 Request Entity Too Large 상태 코드 상수
    }
}