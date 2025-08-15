package com.serverarch.traditional; // 패키지 선언 - 전통적인 스레드 기반 서버 아키텍처 구현체들을 담는 패키지

// 필요한 클래스들을 import - 컴파일러가 클래스 위치를 찾을 수 있도록 경로 지정
import com.serverarch.common.http.*; // HTTP 관련 공통 클래스들 (HttpStatus, HttpHeaders 등) 전체 import
import java.nio.charset.StandardCharsets; // 문자 인코딩 상수 클래스 - UTF_8, US_ASCII 등 표준 인코딩 제공
import java.time.ZonedDateTime; // 시간대 정보를 포함한 날짜/시간 클래스 - HTTP Date 헤더 생성용
import java.time.format.DateTimeFormatter; // 날짜/시간 포맷터 클래스 - 다양한 형식으로 날짜 변환 기능 제공
import java.util.*; // Collections, Objects 등 유틸리티 클래스들 - List, Map, Arrays 등의 컬렉션과 유틸리티 기능

/**
 * 간단하고 현대적인 HTTP 응답 클래스
 *
 * 설계 목표:
 * 1. 빌더 패턴과 정적 팩토리 메서드로 편의성 제공
 * 2. 불변성 보장으로 스레드 안전성 확보
 * 3. 자주 사용되는 응답 타입들을 위한 편의 메서드 제공
 * 4. HTTP 표준을 준수하면서도 사용하기 쉬운 API 설계
 */
public class HttpResponse { // public 클래스 선언 - 다른 패키지에서도 접근 가능한 HTTP 응답 표현 클래스

    // HTTP 상태 코드 (200, 404, 500 등)
    // 기본값 200 OK로 설정 - 가장 일반적인 성공 응답
    private HttpStatus status = HttpStatus.OK; // private 필드 - 직접 접근 불가, HttpStatus enum의 OK 상수로 초기화

    // HTTP 헤더들 (Content-Type, Cache-Control 등)
    // final로 선언하여 헤더 객체 자체는 불변 - 헤더 내용은 변경 가능하지만 객체 교체는 불가
    private final HttpHeaders headers = new HttpHeaders(); // final 키워드 - 참조 변경 불가, new 연산자로 HttpHeaders 인스턴스 생성

    // 응답 바디
    // 기본값 빈 배열로 설정 - null보다 안전하고 일관성 있음
    private byte[] body = new byte[0]; // byte 배열 - 바이너리 데이터 저장 가능, new byte[0]으로 길이 0인 배열 생성

    // 응답 생성 시간 (성능 모니터링과 캐싱에 사용)
    // 객체 생성 시점을 기록 - 응답 처리 시간 계산이나 캐시 만료 시간 계산에 활용
    private final long creationTime = System.currentTimeMillis(); // System.currentTimeMillis() - 1970년 1월 1일부터 현재까지의 밀리초, final로 불변

    // ========== 생성자들 ==========

    /**
     * 기본 생성자 (200 OK 응답)
     * 가장 일반적인 성공 응답을 기본값으로 설정
     */
    public HttpResponse() { // public 기본 생성자 - 매개변수 없이 객체 생성 가능
        // 기본 헤더들 설정 - 표준 HTTP 응답에 필요한 최소한의 헤더들
        setDefaultHeaders(); // 메서드 호출 - this.setDefaultHeaders()와 동일, 현재 객체의 메서드 실행
    }

    /**
     * 상태 코드를 지정하는 생성자
     *
     * @param status HTTP 상태 코드
     */
    public HttpResponse(HttpStatus status) { // 매개변수가 있는 생성자 - HttpStatus 타입의 status 매개변수 받음
        // 기본 생성자 호출로 기본 헤더 설정
        this(); // this() - 같은 클래스의 다른 생성자 호출, 반드시 첫 번째 줄에 위치해야 함

        // 지정된 상태 코드 설정 - null 체크는 setStatus에서 처리
        setStatus(status); // setStatus 메서드 호출 - 매개변수로 받은 status를 설정
    }

    /**
     * 상태 코드와 바디를 지정하는 생성자
     *
     * @param status HTTP 상태 코드
     * @param body 응답 바디
     */
    public HttpResponse(HttpStatus status, String body) { // 두 개의 매개변수를 받는 생성자 - HttpStatus와 String 타입
        // 상태 코드 설정 생성자 호출
        this(status); // this(status) - 위의 HttpStatus 매개변수를 받는 생성자 호출

        // 바디 설정 - null 체크는 setBody에서 처리
        setBody(body); // setBody 메서드 호출 - String 타입의 body를 byte[]로 변환하여 저장
    }

    /**
     * 기본 헤더들을 설정하는 메서드
     * HTTP 표준에 따른 필수 또는 권장 헤더들을 자동 설정
     */
    private void setDefaultHeaders() { // private 메서드 - 클래스 내부에서만 사용, 외부 접근 불가
        // Server 헤더 설정 - 서버 식별 정보 제공
        headers.set("Server", "JavaServerArchitectures/1.0"); // headers 필드의 set 메서드 호출 - 키-값 쌍으로 헤더 설정

        // Date 헤더 설정 - HTTP 표준에서 권장하는 현재 시간
        headers.set("Date", formatHttpDate(ZonedDateTime.now())); // ZonedDateTime.now() - 현재 시간대 기준 현재 시간, formatHttpDate로 HTTP 형식 변환

        // Content-Length 헤더 설정 - 초기값 0 (바디 설정 시 자동 업데이트)
        headers.setContentLength(body.length); // body.length - 배열의 길이 속성, setContentLength 메서드로 Content-Length 헤더 설정
    }

    // ========== 정적 팩토리 메서드들 ==========

    /**
     * 200 OK 응답 생성
     *
     * @return 빈 바디를 가진 성공 응답
     */
    public static HttpResponse ok() { // static 메서드 - 클래스 레벨 메서드, 인스턴스 생성 없이 호출 가능
        // 기본 생성자 사용 - 200 OK가 기본값이므로 그대로 반환
        return new HttpResponse(); // new 연산자로 새 HttpResponse 인스턴스 생성 후 반환
    }

    /**
     * 200 OK 응답을 바디와 함께 생성
     *
     * @param body 응답 바디 텍스트
     * @return HTML 형태의 성공 응답
     */
    public static HttpResponse ok(String body) { // static 메서드 오버로딩 - 같은 이름이지만 매개변수가 다른 메서드
        // 200 OK 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.OK); // HttpStatus.OK - enum의 정적 상수, 200 OK 상태 코드

        // 바디 설정 - String을 byte[]로 변환
        response.setBody(body); // response 객체의 setBody 메서드 호출 - 점(.) 연산자로 객체의 메서드 접근

        // Content-Type을 HTML로 설정 - 웹 브라우저에서 올바르게 표시되도록
        response.headers.setContentType("text/html; charset=UTF-8"); // response.headers - 객체의 필드 접근, setContentType - Content-Type 헤더 설정 메서드

        return response; // 설정이 완료된 response 객체 반환
    }

    /**
     * 201 Created 응답 생성 (리소스 생성 성공)
     * REST API에서 새로운 리소스가 성공적으로 생성되었을 때 사용
     * HTTP 표준에 따라 POST 요청의 성공적인 리소스 생성을 나타냄
     *
     * @return 빈 바디를 가진 201 Created 응답
     */
    public static HttpResponse created() { // static 팩토리 메서드 - 201 상태 코드 응답 생성, 매개변수 없음
        // 201 Created 상태로 새 응답 인스턴스 생성
        return new HttpResponse(HttpStatus.CREATED); // HttpStatus.CREATED - enum 상수로 201 상태 코드를 나타냄, 생성자 호출
    }

    /**
     * 201 Created 응답을 바디와 함께 생성
     * 새로 생성된 리소스 정보를 응답 바디에 포함하여 반환
     * 클라이언트가 생성된 리소스의 세부사항을 즉시 확인할 수 있도록 함
     *
     * @param body 생성된 리소스의 정보 (보통 JSON 형태)
     * @return JSON 타입의 201 Created 응답
     */
    public static HttpResponse created(String body) { // static 메서드 오버로딩 - String 매개변수를 받는 created 메서드
        // 201 Created 상태로 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.CREATED); // HttpStatus.CREATED로 201 상태 응답 객체 생성

        // 생성된 리소스 정보를 바디에 설정
        response.setBody(body); // setBody 메서드 호출 - 매개변수 body를 응답 바디로 설정

        // Content-Type을 JSON으로 설정 - API 응답에서 일반적으로 사용
        response.headers.setContentType("application/json; charset=UTF-8"); // JSON MIME 타입 설정 - 클라이언트가 JSON으로 파싱하도록 지시

        return response; // 완성된 201 Created 응답 반환
    }

    /**
     * 201 Created 응답을 Location 헤더와 함께 생성
     * 새로 생성된 리소스의 위치(URL)를 Location 헤더로 제공
     * REST API 모범 사례 - 클라이언트가 생성된 리소스에 접근할 수 있는 URL 제공
     *
     * @param location 생성된 리소스의 URL
     * @param body 생성된 리소스의 정보
     * @return Location 헤더를 포함한 201 Created 응답
     */
    public static HttpResponse created(String location, String body) { // 두 개의 String 매개변수를 받는 created 메서드
        // 바디와 함께 201 Created 응답 생성
        HttpResponse response = created(body); // 위에서 정의한 created(String body) 메서드 호출 - 메서드 재사용

        // Location 헤더 설정 - 새로 생성된 리소스의 URL 제공
        response.setHeader("Location", location); // setHeader 메서드 호출 - "Location" 헤더에 리소스 URL 설정

        return response; // Location 헤더가 포함된 201 응답 반환
    }

    /**
     * 204 No Content 응답 생성
     * 요청이 성공했지만 응답 바디가 없는 경우 사용
     * DELETE, PUT 등의 요청에서 성공적인 처리 후 반환할 데이터가 없을 때 사용
     * HTTP 표준에 따라 바디를 포함하지 않아야 함
     *
     * @return 빈 바디를 가진 204 No Content 응답
     */
    public static HttpResponse noContent() { // static 팩토리 메서드 - 204 상태 코드 응답 생성
        // 204 No Content 상태로 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.NO_CONTENT); // HttpStatus.NO_CONTENT - enum 상수로 204 상태 코드를 나타냄

        // 명시적으로 빈 바디 설정 - 204 응답은 바디를 가지지 않음
        response.setBody(""); // 빈 문자열로 바디 설정 - HTTP 표준에 따라 204는 빈 바디를 가져야 함

        // Content-Length를 0으로 명시적 설정 - 일부 클라이언트 호환성을 위해
        response.headers.setContentLength(0); // setContentLength(0) - Content-Length 헤더를 0으로 설정

        return response; // 204 No Content 응답 반환
    }

    // ========== JSON 변환 유틸리티 메서드 ==========

    /**
     * 객체를 JSON 문자열로 변환하는 유틸리티 메서드
     * 간단한 Map이나 기본 타입들을 JSON으로 변환할 때 사용
     * 복잡한 객체의 경우 Jackson이나 Gson 같은 전문 라이브러리 사용 권장
     *
     * @param object JSON으로 변환할 객체 (Map, List, String, Number, Boolean 등)
     * @return JSON 형태의 문자열
     */
    public static String mapToJson(Object object) { // public static 메서드 - 객체를 JSON 문자열로 변환하는 유틸리티
        // null 체크 - 안전한 처리
        if (object == null) { // 조건문 - object가 null인지 확인
            return "null"; // null 객체는 "null" 문자열로 반환 - JSON 표준에 따라
        }

        // 문자열인 경우 따옴표로 감싸기 - JSON 문자열 형식
        if (object instanceof String) { // instanceof 연산자 - 객체가 String 타입인지 확인
            return "\"" + escapeJsonString((String) object) + "\""; // 형변환 후 JSON 문자열 이스케이프 처리, 양쪽에 따옴표 추가
        }

        // 숫자나 불린값은 그대로 문자열로 변환 - JSON에서 따옴표 없이 표현
        if (object instanceof Number || object instanceof Boolean) { // 논리 연산자 - Number 타입이거나 Boolean 타입인지 확인
            return object.toString(); // toString() 메서드 - 객체를 문자열로 변환
        }

        // Map인 경우 JSON 객체 형태로 변환 - {key: value, ...} 형식
        if (object instanceof Map) { // Map 인터페이스 구현 객체인지 확인
            Map<?, ?> map = (Map<?, ?>) object; // 형변환 - Object를 Map으로 캐스팅, 와일드카드 타입 사용
            StringBuilder json = new StringBuilder("{"); // StringBuilder 생성 - JSON 객체 시작 중괄호

            boolean first = true; // boolean 플래그 - 첫 번째 요소인지 확인용
            for (Map.Entry<?, ?> entry : map.entrySet()) { // for-each 반복문 - Map의 모든 엔트리를 순회
                if (!first) { // 첫 번째가 아니면 쉼표 추가
                    json.append(", "); // 쉼표와 공백 추가 - JSON 요소 구분자
                }
                // 키-값 쌍을 JSON 형식으로 추가 - "key": value 형태
                json.append("\"").append(escapeJsonString(String.valueOf(entry.getKey()))).append("\": "); // 키를 문자열로 변환 후 이스케이프, 콜론과 공백 추가
                json.append(mapToJson(entry.getValue())); // 재귀 호출 - 값을 JSON으로 변환하여 추가
                first = false; // 첫 번째 플래그 해제
            }
            json.append("}"); // JSON 객체 종료 중괄호
            return json.toString(); // StringBuilder를 문자열로 변환하여 반환
        }

        // List나 배열인 경우 JSON 배열 형태로 변환 - [element1, element2, ...] 형식
        if (object instanceof List) { // List 인터페이스 구현 객체인지 확인
            List<?> list = (List<?>) object; // 형변환 - Object를 List로 캐스팅
            StringBuilder json = new StringBuilder("["); // JSON 배열 시작 대괄호

            for (int i = 0; i < list.size(); i++) { // for 반복문 - 인덱스 기반 리스트 순회
                if (i > 0) { // 첫 번째가 아니면 쉼표 추가
                    json.append(", "); // 배열 요소 구분자
                }
                json.append(mapToJson(list.get(i))); // 재귀 호출 - 리스트 요소를 JSON으로 변환
            }
            json.append("]"); // JSON 배열 종료 대괄호
            return json.toString(); // 완성된 JSON 배열 반환
        }

        // 기타 객체들은 toString()으로 문자열 변환 후 JSON 문자열로 처리
        return "\"" + escapeJsonString(object.toString()) + "\""; // toString() 호출 후 JSON 문자열로 이스케이프
    }

    /**
     * JSON 문자열 내의 특수 문자들을 이스케이프 처리
     * JSON 파싱 오류와 보안 문제를 방지하기 위한 필수 처리
     *
     * @param str 이스케이프할 문자열
     * @return JSON에서 안전한 이스케이프된 문자열
     */
    private static String escapeJsonString(String str) { // private static 메서드 - JSON 문자열 이스케이프 유틸리티
        // null 체크 - 안전한 처리
        if (str == null) { // null 확인 조건문
            return ""; // null이면 빈 문자열 반환
        }

        // JSON에서 특별한 의미를 가지는 문자들을 이스케이프 - JSON 표준 준수
        return str.replace("\\", "\\\\")  // 백슬래시 이스케이프 - 가장 먼저 처리 (다른 이스케이프에 영향 주지 않도록)
                .replace("\"", "\\\"")    // 따옴표 이스케이프 - JSON 문자열 구분자와 충돌 방지
                .replace("\b", "\\b")     // 백스페이스 문자 이스케이프
                .replace("\f", "\\f")     // 폼 피드 문자 이스케이프
                .replace("\n", "\\n")     // 개행 문자 이스케이프 - 줄바꿈을 JSON에서 안전하게 표현
                .replace("\r", "\\r")     // 캐리지 리턴 이스케이프
                .replace("\t", "\\t");    // 탭 문자 이스케이프
        // replace() 메서드 체이닝 - 각 특수 문자를 순서대로 이스케이프 처리
    }

    /**
     * JSON 응답 생성
     * API 서버에서 가장 많이 사용되는 응답 형태
     *
     * @param jsonContent JSON 문자열
     * @return JSON 타입의 성공 응답
     */
    public static HttpResponse json(String jsonContent) { // static 메서드 - JSON 응답 생성용 팩토리 메서드
        // 200 OK 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.OK); // 지역 변수 선언 및 초기화 - HttpStatus.OK로 성공 응답 생성

        // JSON 바디 설정
        response.setBody(jsonContent); // 매개변수로 받은 JSON 문자열을 응답 바디로 설정

        // Content-Type을 JSON으로 설정 - 클라이언트가 JSON으로 파싱하도록 지시
        response.headers.setContentType("application/json; charset=UTF-8"); // MIME 타입을 JSON으로 설정 - 브라우저가 JSON으로 해석

        return response; // 완성된 JSON 응답 반환
    }

    /**
     * 텍스트 응답 생성
     * 단순한 텍스트 응답이 필요한 경우 사용
     *
     * @param textContent 텍스트 내용
     * @return 텍스트 타입의 성공 응답
     */
    public static HttpResponse text(String textContent) { // static 메서드 - 텍스트 응답 생성용
        // 200 OK 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.OK); // HttpResponse 생성자 호출 - OK 상태로 초기화

        // 텍스트 바디 설정
        response.setBody(textContent); // 텍스트 내용을 바디로 설정

        // Content-Type을 plain text로 설정 - 브라우저에서 텍스트로 표시
        response.headers.setContentType("text/plain; charset=UTF-8"); // text/plain MIME 타입 - 일반 텍스트로 해석

        return response; // 텍스트 응답 반환
    }

    /**
     * HTML 응답 생성
     * 웹 페이지 응답에 사용
     *
     * @param htmlContent HTML 내용
     * @return HTML 타입의 성공 응답
     */
    public static HttpResponse html(String htmlContent) { // HTML 전용 팩토리 메서드
        // 200 OK 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.OK); // 성공 상태로 응답 생성

        // HTML 바디 설정
        response.setBody(htmlContent); // HTML 문자열을 바디로 설정

        // Content-Type을 HTML로 설정 - 브라우저에서 HTML로 렌더링
        response.headers.setContentType("text/html; charset=UTF-8"); // text/html MIME 타입 - 브라우저가 HTML로 파싱

        return response; // HTML 응답 반환
    }

    /**
     * 404 Not Found 응답 생성
     *
     * @return 기본 메시지를 가진 404 응답
     */
    public static HttpResponse notFound() { // 매개변수 없는 404 응답 생성 메서드
        // 기본 404 메시지로 응답 생성
        return notFound("요청한 리소스를 찾을 수 없습니다"); // 오버로드된 notFound 메서드 호출 - 기본 메시지 전달
    }

    /**
     * 404 Not Found 응답을 메시지와 함께 생성
     *
     * @param message 에러 메시지
     * @return 커스텀 메시지를 가진 404 응답
     */
    public static HttpResponse notFound(String message) { // 메시지를 받는 404 응답 생성 메서드
        // 404 상태 코드로 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.NOT_FOUND); // HttpStatus.NOT_FOUND - 404 상태 코드 enum 상수

        // HTML 형태의 에러 페이지 생성 - 사용자 친화적인 에러 표시
        response.setBody(createErrorHtml(404, "Not Found", message)); // createErrorHtml 메서드 호출 - 에러 페이지 HTML 생성

        // Content-Type을 HTML로 설정
        response.headers.setContentType("text/html; charset=UTF-8"); // HTML 형태로 에러 페이지 표시

        return response; // 404 에러 응답 반환
    }

    /**
     * 400 Bad Request 응답 생성
     *
     * @param message 에러 메시지
     * @return 클라이언트 요청 오류 응답
     */
    public static HttpResponse badRequest(String message) { // 400 에러 응답 생성 메서드
        // 400 상태 코드로 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.BAD_REQUEST); // HttpStatus.BAD_REQUEST - 400 상태 코드

        // HTML 형태의 에러 페이지 생성
        response.setBody(createErrorHtml(400, "Bad Request", message)); // 400 에러용 HTML 페이지 생성

        // Content-Type을 HTML로 설정
        response.headers.setContentType("text/html; charset=UTF-8"); // HTML 에러 페이지로 설정

        return response; // 400 에러 응답 반환
    }

    /**
     * 500 Internal Server Error 응답 생성
     *
     * @return 기본 메시지를 가진 서버 에러 응답
     */
    public static HttpResponse serverError() { // 매개변수 없는 500 에러 응답 메서드
        // 기본 500 메시지로 응답 생성
        return serverError("내부 서버 오류가 발생했습니다"); // 오버로드된 serverError 메서드 호출
    }

    /**
     * 500 Internal Server Error 응답을 메시지와 함께 생성
     *
     * @param message 에러 메시지
     * @return 커스텀 메시지를 가진 서버 에러 응답
     */
    public static HttpResponse serverError(String message) { // 메시지를 받는 500 에러 응답 메서드
        // 500 상태 코드로 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR); // HttpStatus.INTERNAL_SERVER_ERROR - 500 상태 코드

        // HTML 형태의 에러 페이지 생성
        response.setBody(createErrorHtml(500, "Internal Server Error", message)); // 500 에러용 HTML 생성

        // Content-Type을 HTML로 설정
        response.headers.setContentType("text/html; charset=UTF-8"); // HTML 에러 페이지 설정

        return response; // 500 에러 응답 반환
    }

    /**
     * 302 Found 리다이렉트 응답 생성
     *
     * @param location 리다이렉트할 URL
     * @return 임시 리다이렉트 응답
     */
    public static HttpResponse redirect(String location) { // 302 리다이렉트 응답 생성 메서드
        // 302 상태 코드로 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.FOUND); // HttpStatus.FOUND - 302 상태 코드, 임시 이동

        // Location 헤더 설정 - 브라우저가 이 URL로 이동하도록 지시
        response.headers.set("Location", location); // Location 헤더 - 리다이렉트할 URL 지정

        // 리다이렉트는 보통 빈 바디 사용 - 바디는 무시되므로 비워둠
        response.setBody(""); // 빈 문자열로 바디 설정 - 리다이렉트는 바디가 필요없음

        return response; // 리다이렉트 응답 반환
    }

    /**
     * 301 Moved Permanently 리다이렉트 응답 생성
     *
     * @param location 리다이렉트할 URL
     * @return 영구 리다이렉트 응답
     */
    public static HttpResponse permanentRedirect(String location) { // 301 영구 리다이렉트 생성 메서드
        // 301 상태 코드로 응답 생성 - 검색 엔진이 새 URL을 인덱싱하도록 지시
        HttpResponse response = new HttpResponse(HttpStatus.MOVED_PERMANENTLY); // HttpStatus.MOVED_PERMANENTLY - 301 상태 코드, 영구 이동

        // Location 헤더 설정
        response.headers.set("Location", location); // 새로운 영구 URL 설정

        // 빈 바디 설정
        response.setBody(""); // 리다이렉트용 빈 바디

        return response; // 영구 리다이렉트 응답 반환
    }

    // ========== Getter/Setter 메서드들 ==========

    /**
     * HTTP 상태 코드 반환
     *
     * @return HTTP 상태 코드
     */
    public HttpStatus getStatus() { // public getter 메서드 - 외부에서 상태 코드 조회 가능
        // 직접 반환 - HttpStatus는 enum이므로 불변
        return status; // status 필드 값 반환 - enum은 불변 객체이므로 안전하게 직접 반환
    }

    /**
     * HTTP 상태 코드 설정
     *
     * @param status HTTP 상태 코드
     */
    public void setStatus(HttpStatus status) { // public setter 메서드 - 외부에서 상태 코드 변경 가능
        // null 체크하여 기본값 설정 - 항상 유효한 상태 코드 보장
        this.status = status != null ? status : HttpStatus.OK; // 삼항 연산자 - null이 아니면 매개변수 값, null이면 OK 사용
    }

    /**
     * HTTP 헤더들 반환
     *
     * @return HTTP 헤더 객체 (변경 가능하지만 객체 자체는 불변)
     */
    public HttpHeaders getHeaders() { // public getter 메서드 - 헤더 객체 반환
        // 직접 반환 - 내부 상태 변경 허용 (빌더 패턴 스타일)
        return headers; // headers 필드 직접 반환 - 호출자가 헤더를 수정할 수 있도록 허용
    }

    /**
     * 응답 바디를 바이트 배열로 반환
     *
     * @return 응답 바디의 복사본
     */
    public byte[] getBody() { // public getter 메서드 - 바디 데이터 반환
        // 방어적 복사로 반환 - 외부에서 배열을 수정해도 원본이 변경되지 않음
        return body.clone(); // clone() 메서드 - 배열의 복사본 생성하여 반환, 원본 보호
    }

    /**
     * 응답 바디를 바이트 배열로 설정
     *
     * @param body 응답 바디
     */
    public void setBody(byte[] body) { // public setter 메서드 - 바이트 배열로 바디 설정
        // null 체크하여 기본값 설정
        this.body = body != null ? body.clone() : new byte[0]; // 방어적 복사 - 외부 배열 수정이 내부에 영향 주지 않도록

        // Content-Length 헤더 자동 업데이트 - HTTP 표준 준수
        updateContentLength(); // private 메서드 호출 - Content-Length 헤더를 현재 바디 크기로 업데이트
    }

    /**
     * 응답 바디를 문자열로 설정
     *
     * @param body 응답 바디 문자열
     */
    public void setBody(String body) { // 메서드 오버로딩 - String 타입 바디 설정
        if (body == null) { // null 체크 - 조건문으로 null 값 처리
            // null이면 빈 배열로 설정
            setBody(new byte[0]); // 재귀 호출 - byte[] 버전의 setBody 메서드 호출
        } else {
            // UTF-8로 인코딩하여 설정 - 웹에서 표준 인코딩
            setBody(body.getBytes(StandardCharsets.UTF_8)); // String.getBytes() - 문자열을 바이트 배열로 변환, UTF_8 인코딩 사용
        }
    }

    /**
     * 응답 바디를 문자열로 반환
     *
     * @return UTF-8로 디코딩된 바디 문자열
     */
    public String getBodyAsString() { // public getter 메서드 - 바디를 문자열로 반환
        // UTF-8로 디코딩 - setBody(String)과 일관성 유지
        return new String(body, StandardCharsets.UTF_8); // String 생성자 - 바이트 배열과 인코딩을 받아 문자열 생성
    }

    /**
     * 응답 생성 시간 반환
     *
     * @return 응답 생성 시간 (밀리초)
     */
    public long getCreationTime() { // public getter 메서드 - 생성 시간 반환
        // 직접 반환 - 불변 값이므로 안전
        return creationTime; // long 타입 원시값 반환 - 참조 타입이 아니므로 복사되어 안전
    }

    // ========== 헤더 편의 메서드들 ==========

    /**
     * Content-Type 헤더 설정
     *
     * @param contentType Content-Type 값
     */
    public void setContentType(String contentType) { // public 편의 메서드 - Content-Type 헤더 설정
        // HttpHeaders에 위임 - 헤더 처리 로직 중앙화
        headers.setContentType(contentType); // headers 객체의 setContentType 메서드 호출 - 위임 패턴
    }

    /**
     * Content-Type 헤더 반환
     *
     * @return Content-Type 값
     */
    public String getContentType() { // public getter 메서드 - Content-Type 헤더 조회
        // HttpHeaders에 위임 - 일관성 보장
        return headers.getContentType(); // headers 객체에서 Content-Type 헤더 값 조회하여 반환
    }

    /**
     * Content-Length 헤더를 현재 바디 크기로 업데이트
     * 바디가 변경될 때마다 자동 호출됨
     */
    private void updateContentLength() { // private 메서드 - 내부에서만 사용하는 유틸리티 메서드
        // 현재 바디 크기로 Content-Length 헤더 설정 - HTTP 표준 준수
        headers.setContentLength(body.length); // body.length - 배열 길이, setContentLength - Content-Length 헤더 설정
    }

    /**
     * 특정 헤더 값 설정
     *
     * @param name 헤더 이름
     * @param value 헤더 값
     */
    public void setHeader(String name, String value) { // public 편의 메서드 - 임의의 헤더 설정
        // HttpHeaders에 위임 - 중복 코드 방지
        headers.set(name, value); // headers.set() - 헤더 이름과 값을 받아 헤더 설정, 기존 값 덮어쓰기
    }

    /**
     * 특정 헤더 값 추가 (기존 값에 추가)
     *
     * @param name 헤더 이름
     * @param value 헤더 값
     */
    public void addHeader(String name, String value) { // public 편의 메서드 - 헤더 값 추가
        // HttpHeaders에 위임 - 멀티 값 헤더 지원
        headers.add(name, value); // headers.add() - 기존 값을 유지하면서 새 값 추가, 같은 헤더에 여러 값 가능
    }

    /**
     * 특정 헤더의 첫 번째 값 반환
     *
     * @param name 헤더 이름
     * @return 헤더 값 또는 null
     */
    public String getHeader(String name) { // public getter 메서드 - 특정 헤더 조회
        // HttpHeaders에 위임 - 일관된 헤더 처리
        return headers.getFirst(name); // headers.getFirst() - 해당 이름의 첫 번째 헤더 값 반환, 없으면 null
    }

    // ========== 캐시 제어 메서드들 ==========

    /**
     * Cache-Control 헤더 설정
     *
     * @param cacheControl 캐시 제어 지시어
     */
    public void setCacheControl(String cacheControl) { // public 메서드 - 캐시 제어 헤더 설정
        // Cache-Control 헤더 설정 - 브라우저 캐싱 동작 제어
        headers.set("Cache-Control", cacheControl); // "Cache-Control" 헤더 - HTTP 캐싱 동작 제어하는 표준 헤더
    }

    /**
     * 캐시 만료 시간 설정 (초 단위)
     *
     * @param maxAgeSeconds 최대 캐시 유지 시간
     */
    public void setMaxAge(int maxAgeSeconds) { // public 메서드 - 캐시 만료 시간 설정
        // max-age 지시어로 캐시 만료 시간 설정 - 성능 최적화에 중요
        setCacheControl("max-age=" + maxAgeSeconds); // 문자열 연결 - "max-age=" + 숫자값으로 Cache-Control 값 생성
    }

    /**
     * 캐시 비활성화
     * 동적 콘텐츠나 민감한 데이터에 사용
     */
    public void disableCache() { // public 메서드 - 캐시 완전 비활성화
        // 강력한 캐시 비활성화 설정 - 브라우저와 프록시 모두에게 적용
        setCacheControl("no-cache, no-store, must-revalidate"); // 여러 지시어 조합 - 모든 종류의 캐싱 차단

        // IE 호환성을 위한 추가 헤더
        setHeader("Pragma", "no-cache"); // "Pragma" 헤더 - HTTP/1.0 호환성을 위한 구식 캐시 제어 헤더

        // 만료 시간을 과거로 설정하여 즉시 만료
        setHeader("Expires", "0"); // "Expires" 헤더 - 절대 만료 시간, "0"은 즉시 만료를 의미
    }

    // ========== CORS 설정 메서드들 ==========

    /**
     * CORS 헤더 설정
     *
     * @param allowOrigin 허용할 오리진 (null이면 "*" 사용)
     */
    public void setCorsHeaders(String allowOrigin) { // public 메서드 - CORS(Cross-Origin Resource Sharing) 헤더 설정
        // Access-Control-Allow-Origin 설정 - CORS 정책 제어
        setHeader("Access-Control-Allow-Origin", allowOrigin != null ? allowOrigin : "*"); // 삼항 연산자 - null이면 "*"(모든 오리진 허용), 아니면 지정된 오리진

        // 허용할 HTTP 메서드 설정 - RESTful API 지원
        setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS"); // HTTP 메서드 목록 - 쉼표로 구분된 허용 메서드들

        // 허용할 헤더 설정 - 일반적인 API 요청 헤더들
        setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With"); // 요청 헤더 목록 - API에서 자주 사용하는 헤더들
    }

    // ========== 쿠키 설정 메서드들 ==========

    /**
     * 기본 쿠키 추가
     *
     * @param name 쿠키 이름
     * @param value 쿠키 값
     */
    public void addCookie(String name, String value) { // public 메서드 - 간단한 쿠키 추가
        // 기본 옵션으로 쿠키 설정 - 간단한 사용을 위한 편의 메서드
        addCookie(name, value, null, "/", -1, false, false); // 오버로드된 메서드 호출 - 나머지 매개변수는 기본값 사용
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
                          int maxAge, boolean secure, boolean httpOnly) { // 매개변수가 많은 메서드 - 모든 쿠키 옵션 지원
        // 쿠키 이름 검증 - 필수 값이므로 체크
        if (name == null || name.trim().isEmpty()) { // 논리 연산자 - name이 null이거나 공백 문자열이면
            return; // early return - 조건에 맞지 않으면 메서드 즉시 종료
        }

        // Set-Cookie 헤더 값 구성
        StringBuilder cookieValue = new StringBuilder(); // StringBuilder 생성 - 문자열 조합에 효율적인 클래스

        // 기본 name=value 형태
        cookieValue.append(name).append("=").append(value != null ? value : ""); // append() 메서드 체이닝 - 여러 문자열을 연속으로 추가

        // 도메인 설정 (지정된 경우에만)
        if (domain != null) { // 조건문 - domain이 null이 아닌 경우에만 실행
            cookieValue.append("; Domain=").append(domain); // 쿠키 속성 추가 - 세미콜론과 공백으로 구분
        }

        // 경로 설정 (기본값 "/")
        if (path != null) { // path가 지정된 경우
            cookieValue.append("; Path=").append(path); // 지정된 경로 사용
        } else {
            cookieValue.append("; Path=/");  // 기본 경로 설정 - 루트 경로로 설정
        }

        // 유효 시간 설정 (양수인 경우에만)
        if (maxAge >= 0) { // 비교 연산자 - 0 이상인 경우 (세션 쿠키가 아닌 경우)
            cookieValue.append("; Max-Age=").append(maxAge); // Max-Age 속성 - 초 단위 유효 시간
        }

        // HTTPS 전용 설정
        if (secure) { // boolean 값 직접 확인 - true인 경우
            cookieValue.append("; Secure"); // Secure 속성 - HTTPS에서만 쿠키 전송
        }

        // JavaScript 접근 차단 설정 (보안 강화)
        if (httpOnly) { // httpOnly가 true인 경우
            cookieValue.append("; HttpOnly"); // HttpOnly 속성 - JavaScript에서 쿠키 접근 차단
        }

        // Set-Cookie 헤더로 추가 - 여러 쿠키 지원을 위해 add 사용
        addHeader("Set-Cookie", cookieValue.toString()); // StringBuilder.toString() - 최종 문자열 생성, addHeader로 헤더 추가
    }

    // ========== 상태 확인 메서드들 ==========

    /**
     * 응답이 성공 상태인지 확인
     *
     * @return 2xx 상태 코드이면 true
     */
    public boolean isSuccessful() { // public 메서드 - 성공 상태 확인
        // HttpStatus에 위임 - 상태 코드 분류 로직 중앙화
        return HttpStatus.isSuccess(status.getCode()); // status.getCode() - enum에서 숫자 코드 추출, HttpStatus.isSuccess() - 2xx 범위 확인
    }

    /**
     * 응답이 리다이렉트 상태인지 확인
     *
     * @return 3xx 상태 코드이면 true
     */
    public boolean isRedirect() { // public 메서드 - 리다이렉트 상태 확인
        // HttpStatus에 위임 - 일관된 상태 분류
        return HttpStatus.isRedirection(status.getCode()); // HttpStatus.isRedirection() - 3xx 상태 코드 범위 확인
    }

    /**
     * 응답이 클라이언트 오류 상태인지 확인
     *
     * @return 4xx 상태 코드이면 true
     */
    public boolean isClientError() { // public 메서드 - 클라이언트 오류 확인
        // HttpStatus에 위임 - 표준적인 상태 분류
        return HttpStatus.isClientError(status.getCode()); // HttpStatus.isClientError() - 4xx 상태 코드 범위 확인
    }

    /**
     * 응답이 서버 오류 상태인지 확인
     *
     * @return 5xx 상태 코드이면 true
     */
    public boolean isServerError() { // public 메서드 - 서버 오류 확인
        // HttpStatus에 위임 - 통일된 오류 분류
        return HttpStatus.isServerError(status.getCode()); // HttpStatus.isServerError() - 5xx 상태 코드 범위 확인
    }

    /**
     * 응답 바디가 있는지 확인
     *
     * @return 바디가 있으면 true
     */
    public boolean hasBody() { // public 메서드 - 바디 존재 여부 확인
        // 바디 길이로 판별 - 직관적이고 효율적
        return body.length > 0; // 배열 길이 비교 - 0보다 크면 바디가 있는 것으로 판단
    }

    // ========== 유틸리티 메서드들 ==========

    /**
     * HTTP 날짜 형식으로 포맷
     * Date 헤더나 Expires 헤더에 사용
     *
     * @param dateTime 포맷할 날짜/시간
     * @return RFC 1123 형식의 날짜 문자열
     */
    private static String formatHttpDate(ZonedDateTime dateTime) { // private static 메서드 - 유틸리티 함수, 인스턴스 없이 사용
        // RFC 1123 형식 사용 - HTTP 표준 날짜 형식
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(dateTime); // DateTimeFormatter.RFC_1123_DATE_TIME - 표준 HTTP 날짜 포맷터, format() - 날짜를 문자열로 변환
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
    private static String createErrorHtml(int statusCode, String statusText, String message) { // private static 메서드 - HTML 생성 유틸리티
        // HTML 템플릿 생성 - 간단하지만 보기 좋은 에러 페이지
        return String.format( // String.format() - 문자열 템플릿에 값을 대입하여 완성된 문자열 생성
                "<!DOCTYPE html>\n" + // HTML5 문서 타입 선언
                        "<html>\n" + // HTML 루트 요소 시작
                        "<head>\n" + // HTML 헤드 섹션 시작 - 메타데이터 포함
                        "    <title>%d %s</title>\n" +  // %d - 정수 플레이스홀더(상태코드), %s - 문자열 플레이스홀더(상태텍스트)
                        "    <style>\n" + // CSS 스타일 시작
                        // 인라인 CSS로 스타일링 - 외부 파일 의존성 없이 깔끔한 디자인
                        "        body { font-family: Arial, sans-serif; margin: 40px; background: #f5f5f5; }\n" + // body 스타일 - 폰트, 여백, 배경색 설정
                        "        .container { background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }\n" + // 컨테이너 스타일 - 카드 형태 디자인
                        "        h1 { color: #d32f2f; margin-bottom: 20px; }\n" +  // h1 스타일 - 빨간색으로 에러 강조
                        "        p { color: #666; line-height: 1.6; }\n" + // p 스타일 - 회색 텍스트, 줄 간격 설정
                        "        .footer { margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; font-size: 14px; color: #999; }\n" + // 푸터 스타일 - 구분선과 작은 글씨
                        "    </style>\n" + // CSS 스타일 종료
                        "</head>\n" + // HTML 헤드 섹션 종료
                        "<body>\n" + // HTML 바디 섹션 시작
                        "    <div class=\"container\">\n" + // 컨테이너 div 시작
                        "        <h1>%d %s</h1>\n" +  // 에러 제목 - 상태 코드와 텍스트 표시
                        "        <p>%s</p>\n" +  // 에러 메시지 표시 - HTML 이스케이프 처리됨
                        "        <div class=\"footer\">\n" + // 푸터 div 시작
                        "            <p>JavaServerArchitectures/1.0 - Traditional Thread-per-Request Server</p>\n" +  // 서버 식별 정보
                        "        </div>\n" + // 푸터 div 종료
                        "    </div>\n" + // 컨테이너 div 종료
                        "</body>\n" + // HTML 바디 섹션 종료
                        "</html>", // HTML 루트 요소 종료
                statusCode, statusText,  // format의 첫 번째와 두 번째 %d, %s에 대입될 값들
                statusCode, statusText,  // format의 세 번째와 네 번째 %d, %s에 대입될 값들
                escapeHtml(message)      // format의 다섯 번째 %s에 대입될 값 - XSS 방지를 위해 이스케이프 처리
        );
    }

    /**
     * HTML 이스케이프 처리
     * XSS 공격 방지를 위한 보안 조치
     *
     * @param text 이스케이프할 텍스트
     * @return HTML 이스케이프된 텍스트
     */
    private static String escapeHtml(String text) { // private static 메서드 - HTML 이스케이프 유틸리티
        // null 체크 - 안전한 처리
        if (text == null) return ""; // text가 null이면 빈 문자열 반환

        // 위험한 HTML 문자들을 엔티티로 변환 - XSS 공격 방지
        return text.replace("&", "&amp;")     // & -> &amp; (가장 먼저 처리해야 함 - 다른 엔티티에 영향 주지 않도록)
                .replace("<", "&lt;")       // < -> &lt; (HTML 태그 시작 문자 이스케이프)
                .replace(">", "&gt;")       // > -> &gt; (HTML 태그 종료 문자 이스케이프)
                .replace("\"", "&quot;")    // " -> &quot; (속성 값 구분 문자 이스케이프)
                .replace("'", "&#39;");     // ' -> &#39; (속성 값 구분 문자 이스케이프)
        // replace() 메서드 체이닝 - 각 replace는 새로운 String 객체를 반환하므로 연속 호출 가능
    }

    // ========== Object 메서드 오버라이드 ==========

    /**
     * 응답 정보를 문자열로 표현
     * 디버깅과 로깅에 유용
     */
    @Override // 어노테이션 - 부모 클래스(Object)의 메서드를 재정의함을 명시
    public String toString() { // toString() 메서드 오버라이드 - Object 클래스의 기본 메서드 재정의
        // StringBuilder로 효율적인 문자열 조합
        StringBuilder sb = new StringBuilder(); // StringBuilder 생성 - 가변 문자열 조작에 효율적
        sb.append("HttpResponse{"); // append() - 문자열 추가
        sb.append("status=").append(status); // status 필드 값 추가 - enum의 toString() 자동 호출
        sb.append(", contentType='").append(getContentType()).append('\''); // getContentType() 메서드 호출 결과 추가, 작은따옴표로 감싸기
        sb.append(", bodyLength=").append(body.length); // body.length - 배열 길이 속성
        sb.append(", headerCount=").append(headers.size()); // headers.size() - HttpHeaders 객체의 헤더 개수 반환 메서드
        sb.append('}'); // 중괄호로 종료

        return sb.toString(); // StringBuilder.toString() - 최종 문자열 생성하여 반환
    }

    /**
     * 두 응답 객체가 같은지 비교
     * 테스트와 캐싱에서 사용
     */
    @Override // Object.equals() 메서드 오버라이드
    public boolean equals(Object o) { // equals() 메서드 - 객체 동등성 비교
        // 동일 객체 참조 확인 - 가장 빠른 비교
        if (this == o) return true; // this == o - 참조 동등성 확인, 같은 객체를 가리키면 true

        // null 체크와 클래스 타입 확인 - 안전한 비교를 위해
        if (o == null || getClass() != o.getClass()) return false; // getClass() - 런타임 클래스 타입 반환, 다른 타입이면 false

        HttpResponse that = (HttpResponse) o; // 형변환 - Object를 HttpResponse로 캐스팅

        // 주요 필드들 비교 - 상태, 헤더, 바디가 모두 같아야 동일한 응답
        return Objects.equals(status, that.status) && // Objects.equals() - null 안전한 동등성 비교
                Objects.equals(headers, that.headers) && // headers 객체 비교
                Arrays.equals(body, that.body);  // Arrays.equals() - 배열 내용 비교 (배열은 별도 메서드 사용)
    }

    /**
     * 해시 코드 생성
     * HashMap, HashSet 등에서 사용
     */
    @Override // Object.hashCode() 메서드 오버라이드
    public int hashCode() { // hashCode() 메서드 - 해시 기반 컬렉션에서 사용할 해시 코드 생성
        // Objects.hash로 여러 필드 조합
        int result = Objects.hash(status, headers); // Objects.hash() - 여러 객체의 해시 코드를 조합하여 하나의 해시 코드 생성

        // 배열은 별도로 해시 코드 계산 후 조합
        result = 31 * result + Arrays.hashCode(body); // Arrays.hashCode() - 배열 내용 기반 해시 코드 생성, 31은 해시 코드 조합에 사용하는 소수

        return result; // 최종 해시 코드 반환
    }
}