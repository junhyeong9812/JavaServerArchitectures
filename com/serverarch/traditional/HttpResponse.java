package com.serverarch.traditional;

import com.serverarch.common.http.*;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 간단하고 현대적인 HTTP 응답 클래스
 *
 * 설계 목표:
 * 1. 빌더 패턴과 정적 팩토리 메서드로 편의성 제공
 * 2. 불변성 보장으로 스레드 안전성 확보
 * 3. 자주 사용되는 응답 타입들을 위한 편의 메서드 제공
 * 4. HTTP 표준을 준수하면서도 사용하기 쉬운 API 설계
 */
public class HttpResponse {

    // HTTP 상태 코드 (200, 404, 500 등)
    // 기본값 200 OK로 설정 - 가장 일반적인 성공 응답
    private HttpStatus status = HttpStatus.OK;

    // HTTP 헤더들 (Content-Type, Cache-Control 등)
    // final로 선언하여 헤더 객체 자체는 불변 - 헤더 내용은 변경 가능하지만 객체 교체는 불가
    private final HttpHeaders headers = new HttpHeaders();

    // 응답 바디
    // 기본값 빈 배열로 설정 - null보다 안전하고 일관성 있음
    private byte[] body = new byte[0];

    // 응답 생성 시간 (성능 모니터링과 캐싱에 사용)
    // 객체 생성 시점을 기록 - 응답 처리 시간 계산이나 캐시 만료 시간 계산에 활용
    private final long creationTime = System.currentTimeMillis();

    // ========== 생성자들 ==========

    /**
     * 기본 생성자 (200 OK 응답)
     * 가장 일반적인 성공 응답을 기본값으로 설정
     */
    public HttpResponse() {
        // 기본 헤더들 설정 - 표준 HTTP 응답에 필요한 최소한의 헤더들
        setDefaultHeaders();
    }

    /**
     * 상태 코드를 지정하는 생성자
     *
     * @param status HTTP 상태 코드
     */
    public HttpResponse(HttpStatus status) {
        // 기본 생성자 호출로 기본 헤더 설정
        this();

        // 지정된 상태 코드 설정 - null 체크는 setStatus에서 처리
        setStatus(status);
    }

    /**
     * 상태 코드와 바디를 지정하는 생성자
     *
     * @param status HTTP 상태 코드
     * @param body 응답 바디
     */
    public HttpResponse(HttpStatus status, String body) {
        // 상태 코드 설정 생성자 호출
        this(status);

        // 바디 설정 - null 체크는 setBody에서 처리
        setBody(body);
    }

    /**
     * 기본 헤더들을 설정하는 메서드
     * HTTP 표준에 따른 필수 또는 권장 헤더들을 자동 설정
     */
    private void setDefaultHeaders() {
        // Server 헤더 설정 - 서버 식별 정보 제공
        headers.set("Server", "JavaServerArchitectures/1.0");

        // Date 헤더 설정 - HTTP 표준에서 권장하는 현재 시간
        headers.set("Date", formatHttpDate(ZonedDateTime.now()));

        // Content-Length 헤더 설정 - 초기값 0 (바디 설정 시 자동 업데이트)
        headers.setContentLength(body.length);
    }

    // ========== 정적 팩토리 메서드들 ==========

    /**
     * 200 OK 응답 생성
     *
     * @return 빈 바디를 가진 성공 응답
     */
    public static HttpResponse ok() {
        // 기본 생성자 사용 - 200 OK가 기본값이므로 그대로 반환
        return new HttpResponse();
    }

    /**
     * 200 OK 응답을 바디와 함께 생성
     *
     * @param body 응답 바디 텍스트
     * @return HTML 형태의 성공 응답
     */
    public static HttpResponse ok(String body) {
        // 200 OK 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.OK);

        // 바디 설정 - String을 byte[]로 변환
        response.setBody(body);

        // Content-Type을 HTML로 설정 - 웹 브라우저에서 올바르게 표시되도록
        response.headers.setContentType("text/html; charset=UTF-8");

        return response;
    }

    /**
     * JSON 응답 생성
     * API 서버에서 가장 많이 사용되는 응답 형태
     *
     * @param jsonContent JSON 문자열
     * @return JSON 타입의 성공 응답
     */
    public static HttpResponse json(String jsonContent) {
        // 200 OK 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.OK);

        // JSON 바디 설정
        response.setBody(jsonContent);

        // Content-Type을 JSON으로 설정 - 클라이언트가 JSON으로 파싱하도록 지시
        response.headers.setContentType("application/json; charset=UTF-8");

        return response;
    }

    /**
     * 텍스트 응답 생성
     * 단순한 텍스트 응답이 필요한 경우 사용
     *
     * @param textContent 텍스트 내용
     * @return 텍스트 타입의 성공 응답
     */
    public static HttpResponse text(String textContent) {
        // 200 OK 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.OK);

        // 텍스트 바디 설정
        response.setBody(textContent);

        // Content-Type을 plain text로 설정 - 브라우저에서 텍스트로 표시
        response.headers.setContentType("text/plain; charset=UTF-8");

        return response;
    }

    /**
     * HTML 응답 생성
     * 웹 페이지 응답에 사용
     *
     * @param htmlContent HTML 내용
     * @return HTML 타입의 성공 응답
     */
    public static HttpResponse html(String htmlContent) {
        // 200 OK 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.OK);

        // HTML 바디 설정
        response.setBody(htmlContent);

        // Content-Type을 HTML로 설정 - 브라우저에서 HTML로 렌더링
        response.headers.setContentType("text/html; charset=UTF-8");

        return response;
    }

    /**
     * 404 Not Found 응답 생성
     *
     * @return 기본 메시지를 가진 404 응답
     */
    public static HttpResponse notFound() {
        // 기본 404 메시지로 응답 생성
        return notFound("요청한 리소스를 찾을 수 없습니다");
    }

    /**
     * 404 Not Found 응답을 메시지와 함께 생성
     *
     * @param message 에러 메시지
     * @return 커스텀 메시지를 가진 404 응답
     */
    public static HttpResponse notFound(String message) {
        // 404 상태 코드로 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.NOT_FOUND);

        // HTML 형태의 에러 페이지 생성 - 사용자 친화적인 에러 표시
        response.setBody(createErrorHtml(404, "Not Found", message));

        // Content-Type을 HTML로 설정
        response.headers.setContentType("text/html; charset=UTF-8");

        return response;
    }

    /**
     * 400 Bad Request 응답 생성
     *
     * @param message 에러 메시지
     * @return 클라이언트 요청 오류 응답
     */
    public static HttpResponse badRequest(String message) {
        // 400 상태 코드로 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.BAD_REQUEST);

        // HTML 형태의 에러 페이지 생성
        response.setBody(createErrorHtml(400, "Bad Request", message));

        // Content-Type을 HTML로 설정
        response.headers.setContentType("text/html; charset=UTF-8");

        return response;
    }

    /**
     * 500 Internal Server Error 응답 생성
     *
     * @return 기본 메시지를 가진 서버 에러 응답
     */
    public static HttpResponse serverError() {
        // 기본 500 메시지로 응답 생성
        return serverError("내부 서버 오류가 발생했습니다");
    }

    /**
     * 500 Internal Server Error 응답을 메시지와 함께 생성
     *
     * @param message 에러 메시지
     * @return 커스텀 메시지를 가진 서버 에러 응답
     */
    public static HttpResponse serverError(String message) {
        // 500 상태 코드로 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR);

        // HTML 형태의 에러 페이지 생성
        response.setBody(createErrorHtml(500, "Internal Server Error", message));

        // Content-Type을 HTML로 설정
        response.headers.setContentType("text/html; charset=UTF-8");

        return response;
    }

    /**
     * 302 Found 리다이렉트 응답 생성
     *
     * @param location 리다이렉트할 URL
     * @return 임시 리다이렉트 응답
     */
    public static HttpResponse redirect(String location) {
        // 302 상태 코드로 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.FOUND);

        // Location 헤더 설정 - 브라우저가 이 URL로 이동하도록 지시
        response.headers.set("Location", location);

        // 리다이렉트는 보통 빈 바디 사용 - 바디는 무시되므로 비워둠
        response.setBody("");

        return response;
    }

    /**
     * 301 Moved Permanently 리다이렉트 응답 생성
     *
     * @param location 리다이렉트할 URL
     * @return 영구 리다이렉트 응답
     */
    public static HttpResponse permanentRedirect(String location) {
        // 301 상태 코드로 응답 생성 - 검색 엔진이 새 URL을 인덱싱하도록 지시
        HttpResponse response = new HttpResponse(HttpStatus.MOVED_PERMANENTLY);

        // Location 헤더 설정
        response.headers.set("Location", location);

        // 빈 바디 설정
        response.setBody("");

        return response;
    }

    // ========== Getter/Setter 메서드들 ==========

    /**
     * HTTP 상태 코드 반환
     *
     * @return HTTP 상태 코드
     */
    public HttpStatus getStatus() {
        // 직접 반환 - HttpStatus는 enum이므로 불변
        return status;
    }

    /**
     * HTTP 상태 코드 설정
     *
     * @param status HTTP 상태 코드
     */
    public void setStatus(HttpStatus status) {
        // null 체크하여 기본값 설정 - 항상 유효한 상태 코드 보장
        this.status = status != null ? status : HttpStatus.OK;
    }

    /**
     * HTTP 헤더들 반환
     *
     * @return HTTP 헤더 객체 (변경 가능하지만 객체 자체는 불변)
     */
    public HttpHeaders getHeaders() {
        // 직접 반환 - 내부 상태 변경 허용 (빌더 패턴 스타일)
        return headers;
    }

    /**
     * 응답 바디를 바이트 배열로 반환
     *
     * @return 응답 바디의 복사본
     */
    public byte[] getBody() {
        // 방어적 복사로 반환 - 외부에서 배열을 수정해도 원본이 변경되지 않음
        return body.clone();
    }

    /**
     * 응답 바디를 바이트 배열로 설정
     *
     * @param body 응답 바디
     */
    public void setBody(byte[] body) {
        // null 체크하여 기본값 설정
        this.body = body != null ? body.clone() : new byte[0];

        // Content-Length 헤더 자동 업데이트 - HTTP 표준 준수
        updateContentLength();
    }

    /**
     * 응답 바디를 문자열로 설정
     *
     * @param body 응답 바디 문자열
     */
    public void setBody(String body) {
        if (body == null) {
            // null이면 빈 배열로 설정
            setBody(new byte[0]);
        } else {
            // UTF-8로 인코딩하여 설정 - 웹에서 표준 인코딩
            setBody(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 응답 바디를 문자열로 반환
     *
     * @return UTF-8로 디코딩된 바디 문자열
     */
    public String getBodyAsString() {
        // UTF-8로 디코딩 - setBody(String)과 일관성 유지
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * 응답 생성 시간 반환
     *
     * @return 응답 생성 시간 (밀리초)
     */
    public long getCreationTime() {
        // 직접 반환 - 불변 값이므로 안전
        return creationTime;
    }

    // ========== 헤더 편의 메서드들 ==========

    /**
     * Content-Type 헤더 설정
     *
     * @param contentType Content-Type 값
     */
    public void setContentType(String contentType) {
        // HttpHeaders에 위임 - 헤더 처리 로직 중앙화
        headers.setContentType(contentType);
    }

    /**
     * Content-Type 헤더 반환
     *
     * @return Content-Type 값
     */
    public String getContentType() {
        // HttpHeaders에 위임 - 일관성 보장
        return headers.getContentType();
    }

    /**
     * Content-Length 헤더를 현재 바디 크기로 업데이트
     * 바디가 변경될 때마다 자동 호출됨
     */
    private void updateContentLength() {
        // 현재 바디 크기로 Content-Length 헤더 설정 - HTTP 표준 준수
        headers.setContentLength(body.length);
    }

    /**
     * 특정 헤더 값 설정
     *
     * @param name 헤더 이름
     * @param value 헤더 값
     */
    public void setHeader(String name, String value) {
        // HttpHeaders에 위임 - 중복 코드 방지
        headers.set(name, value);
    }

    /**
     * 특정 헤더 값 추가 (기존 값에 추가)
     *
     * @param name 헤더 이름
     * @param value 헤더 값
     */
    public void addHeader(String name, String value) {
        // HttpHeaders에 위임 - 멀티 값 헤더 지원
        headers.add(name, value);
    }

    /**
     * 특정 헤더의 첫 번째 값 반환
     *
     * @param name 헤더 이름
     * @return 헤더 값 또는 null
     */
    public String getHeader(String name) {
        // HttpHeaders에 위임 - 일관된 헤더 처리
        return headers.getFirst(name);
    }

    // ========== 캐시 제어 메서드들 ==========

    /**
     * Cache-Control 헤더 설정
     *
     * @param cacheControl 캐시 제어 지시어
     */
    public void setCacheControl(String cacheControl) {
        // Cache-Control 헤더 설정 - 브라우저 캐싱 동작 제어
        headers.set("Cache-Control", cacheControl);
    }

    /**
     * 캐시 만료 시간 설정 (초 단위)
     *
     * @param maxAgeSeconds 최대 캐시 유지 시간
     */
    public void setMaxAge(int maxAgeSeconds) {
        // max-age 지시어로 캐시 만료 시간 설정 - 성능 최적화에 중요
        setCacheControl("max-age=" + maxAgeSeconds);
    }

    /**
     * 캐시 비활성화
     * 동적 콘텐츠나 민감한 데이터에 사용
     */
    public void disableCache() {
        // 강력한 캐시 비활성화 설정 - 브라우저와 프록시 모두에게 적용
        setCacheControl("no-cache, no-store, must-revalidate");

        // IE 호환성을 위한 추가 헤더
        setHeader("Pragma", "no-cache");

        // 만료 시간을 과거로 설정하여 즉시 만료
        setHeader("Expires", "0");
    }

    // ========== CORS 설정 메서드들 ==========

    /**
     * CORS 헤더 설정
     *
     * @param allowOrigin 허용할 오리진 (null이면 "*" 사용)
     */
    public void setCorsHeaders(String allowOrigin) {
        // Access-Control-Allow-Origin 설정 - CORS 정책 제어
        setHeader("Access-Control-Allow-Origin", allowOrigin != null ? allowOrigin : "*");

        // 허용할 HTTP 메서드 설정 - RESTful API 지원
        setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");

        // 허용할 헤더 설정 - 일반적인 API 요청 헤더들
        setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
    }

    // ========== 쿠키 설정 메서드들 ==========

    /**
     * 기본 쿠키 추가
     *
     * @param name 쿠키 이름
     * @param value 쿠키 값
     */
    public void addCookie(String name, String value) {
        // 기본 옵션으로 쿠키 설정 - 간단한 사용을 위한 편의 메서드
        addCookie(name, value, null, "/", -1, false, false);
    }

    /**
     * 상세 옵션과 함께 쿠키 추가
     *
     * @param name 쿠키 이름
     * @param value 쿠키 값
     * @param domain 쿠키 도메인
     * @param path 쿠키 경로
     * @param maxAge 쿠키 유효 시간 (초, -1이면 세션 쿠키)
     * @param secure HTTPS에서만 전송할지 여부
     * @param httpOnly JavaScript에서 접근 불가하게 할지 여부
     */
    public void addCookie(String name, String value, String domain, String path,
                          int maxAge, boolean secure, boolean httpOnly) {
        // 쿠키 이름 검증 - 필수 값이므로 체크
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        // Set-Cookie 헤더 값 구성
        StringBuilder cookieValue = new StringBuilder();

        // 기본 name=value 형태
        cookieValue.append(name).append("=").append(value != null ? value : "");

        // 도메인 설정 (지정된 경우에만)
        if (domain != null) {
            cookieValue.append("; Domain=").append(domain);
        }

        // 경로 설정 (기본값 "/")
        if (path != null) {
            cookieValue.append("; Path=").append(path);
        } else {
            cookieValue.append("; Path=/");  // 기본 경로 설정
        }

        // 유효 시간 설정 (양수인 경우에만)
        if (maxAge >= 0) {
            cookieValue.append("; Max-Age=").append(maxAge);
        }

        // HTTPS 전용 설정
        if (secure) {
            cookieValue.append("; Secure");
        }

        // JavaScript 접근 차단 설정 (보안 강화)
        if (httpOnly) {
            cookieValue.append("; HttpOnly");
        }

        // Set-Cookie 헤더로 추가 - 여러 쿠키 지원을 위해 add 사용
        addHeader("Set-Cookie", cookieValue.toString());
    }

    // ========== 상태 확인 메서드들 ==========

    /**
     * 응답이 성공 상태인지 확인
     *
     * @return 2xx 상태 코드이면 true
     */
    public boolean isSuccessful() {
        // HttpStatus에 위임 - 상태 코드 분류 로직 중앙화
        return HttpStatus.isSuccess(status.getCode());
    }

    /**
     * 응답이 리다이렉트 상태인지 확인
     *
     * @return 3xx 상태 코드이면 true
     */
    public boolean isRedirect() {
        // HttpStatus에 위임 - 일관된 상태 분류
        return HttpStatus.isRedirection(status.getCode());
    }

    /**
     * 응답이 클라이언트 오류 상태인지 확인
     *
     * @return 4xx 상태 코드이면 true
     */
    public boolean isClientError() {
        // HttpStatus에 위임 - 표준적인 상태 분류
        return HttpStatus.isClientError(status.getCode());
    }

    /**
     * 응답이 서버 오류 상태인지 확인
     *
     * @return 5xx 상태 코드이면 true
     */
    public boolean isServerError() {
        // HttpStatus에 위임 - 통일된 오류 분류
        return HttpStatus.isServerError(status.getCode());
    }

    /**
     * 응답 바디가 있는지 확인
     *
     * @return 바디가 있으면 true
     */
    public boolean hasBody() {
        // 바디 길이로 판별 - 직관적이고 효율적
        return body.length > 0;
    }

    // ========== 유틸리티 메서드들 ==========

    /**
     * HTTP 날짜 형식으로 포맷
     * Date 헤더나 Expires 헤더에 사용
     *
     * @param dateTime 포맷할 날짜/시간
     * @return RFC 1123 형식의 날짜 문자열
     */
    private static String formatHttpDate(ZonedDateTime dateTime) {
        // RFC 1123 형식 사용 - HTTP 표준 날짜 형식
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(dateTime);
    }

    /**
     * 에러 페이지 HTML 생성
     * 사용자 친화적인 에러 페이지 제공
     *
     * @param statusCode HTTP 상태 코드
     * @param statusText 상태 텍스트
     * @param message 에러 메시지
     * @return 완성된 HTML 에러 페이지
     */
    private static String createErrorHtml(int statusCode, String statusText, String message) {
        // HTML 템플릿 생성 - 간단하지만 보기 좋은 에러 페이지
        return String.format(
                "<!DOCTYPE html>\n" +
                        "<html>\n" +
                        "<head>\n" +
                        "    <title>%d %s</title>\n" +  // 브라우저 탭 제목
                        "    <style>\n" +
                        // 인라인 CSS로 스타일링 - 외부 파일 의존성 없이 깔끔한 디자인
                        "        body { font-family: Arial, sans-serif; margin: 40px; background: #f5f5f5; }\n" +
                        "        .container { background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }\n" +
                        "        h1 { color: #d32f2f; margin-bottom: 20px; }\n" +  // 빨간색으로 에러 강조
                        "        p { color: #666; line-height: 1.6; }\n" +
                        "        .footer { margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; font-size: 14px; color: #999; }\n" +
                        "    </style>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "    <div class=\"container\">\n" +
                        "        <h1>%d %s</h1>\n" +  // 상태 코드와 텍스트
                        "        <p>%s</p>\n" +  // 에러 메시지 (HTML 이스케이프 처리됨)
                        "        <div class=\"footer\">\n" +
                        "            <p>JavaServerArchitectures/1.0 - Traditional Thread-per-Request Server</p>\n" +  // 서버 식별 정보
                        "        </div>\n" +
                        "    </div>\n" +
                        "</body>\n" +
                        "</html>",
                statusCode, statusText,  // 제목용
                statusCode, statusText,  // 헤딩용
                escapeHtml(message)      // 본문용 (XSS 방지를 위해 이스케이프)
        );
    }

    /**
     * HTML 이스케이프 처리
     * XSS 공격 방지를 위한 보안 조치
     *
     * @param text 이스케이프할 텍스트
     * @return HTML 이스케이프된 텍스트
     */
    private static String escapeHtml(String text) {
        // null 체크 - 안전한 처리
        if (text == null) return "";

        // 위험한 HTML 문자들을 엔티티로 변환 - XSS 공격 방지
        return text.replace("&", "&amp;")     // & -> &amp; (먼저 처리해야 함)
                .replace("<", "&lt;")       // < -> &lt;
                .replace(">", "&gt;")       // > -> &gt;
                .replace("\"", "&quot;")    // " -> &quot;
                .replace("'", "&#39;");     // ' -> &#39;
    }

    // ========== Object 메서드 오버라이드 ==========

    /**
     * 응답 정보를 문자열로 표현
     * 디버깅과 로깅에 유용
     */
    @Override
    public String toString() {
        // StringBuilder로 효율적인 문자열 조합
        StringBuilder sb = new StringBuilder();
        sb.append("HttpResponse{");
        sb.append("status=").append(status);
        sb.append(", contentType='").append(getContentType()).append('\'');
        sb.append(", bodyLength=").append(body.length);
        sb.append(", headerCount=").append(headers.size());
        sb.append('}');

        return sb.toString();
    }

    /**
     * 두 응답 객체가 같은지 비교
     * 테스트와 캐싱에서 사용
     */
    @Override
    public boolean equals(Object o) {
        // 동일 객체 참조 확인 - 가장 빠른 비교
        if (this == o) return true;

        // null 체크와 클래스 타입 확인 - 안전한 비교를 위해
        if (o == null || getClass() != o.getClass()) return false;

        HttpResponse that = (HttpResponse) o;

        // 주요 필드들 비교 - 상태, 헤더, 바디가 모두 같아야 동일한 응답
        return Objects.equals(status, that.status) &&
                Objects.equals(headers, that.headers) &&
                Arrays.equals(body, that.body);  // 배열은 Arrays.equals 사용
    }

    /**
     * 해시 코드 생성
     * HashMap, HashSet 등에서 사용
     */
    @Override
    public int hashCode() {
        // Objects.hash로 여러 필드 조합
        int result = Objects.hash(status, headers);

        // 배열은 별도로 해시 코드 계산 후 조합
        result = 31 * result + Arrays.hashCode(body);

        return result;
    }
}