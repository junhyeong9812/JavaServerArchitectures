package com.serverarch.common.http;

import com.serverarch.traditional.HttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 응답을 바이트 배열로 빌드하는 유틸리티 클래스
 *
 * 이 클래스의 역할:
 * 1. HttpResponse 객체를 HTTP 프로토콜 형식의 바이트 배열로 변환
 * 2. 자주 사용되는 응답 타입을 위한 편의 메서드 제공
 * 3. HTTP 표준에 맞는 응답 형식 보장
 *
 * public 클래스로 선언한 이유:
 * - 다른 패키지에서 HTTP 응답 빌드 기능 사용
 * - 웹 서버의 핵심 기능으로 외부 접근 필요
 * - 테스트 코드에서도 사용 가능
 */
public class HttpResponseBuilder {

    /**
     * HttpResponse 객체를 HTTP 프로토콜 형식의 바이트 배열로 변환합니다.
     *
     * HTTP 응답 형식:
     * HTTP/1.1 200 OK\r\n
     * Content-Type: text/html\r\n
     * Content-Length: 13\r\n
     * \r\n
     * Hello, World!
     *
     * static 메서드로 구현한 이유:
     * - 상태를 유지하지 않는 순수 함수
     * - 인스턴스 생성 없이 사용 가능
     * - 스레드 안전성 보장
     *
     * @param response 변환할 HttpResponse 객체
     * @return HTTP 프로토콜 형식의 바이트 배열
     * @throws RuntimeException 빌드 실패 시 (IOException을 래핑)
     */
    public static byte[] buildResponse(HttpResponse response) {
        try {
            // ByteArrayOutputStream: 메모리에 바이트 데이터를 축적하는 스트림
            // 동적으로 크기가 확장되는 바이트 배열 버퍼 역할
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // ========== 상태 라인 작성 ==========
            // HTTP/1.1 200 OK\r\n 형식

            // String.format(): 형식화된 문자열 생성
            // %d: 정수 포맷팅
            // %s: 문자열 포맷팅
            // response.getStatus().getCode(): HttpStatus enum의 숫자 코드
            // response.getStatus().getReasonPhrase(): HttpStatus enum의 설명 문구
            String statusLine = String.format("HTTP/1.1 %d %s\r\n",
                    response.getStatus().getCode(),
                    response.getStatus().getReasonPhrase());

            // getBytes(): 문자열을 바이트 배열로 변환
            // StandardCharsets.UTF_8: UTF-8 인코딩 사용
            // write(): ByteArrayOutputStream에 바이트 배열 쓰기
            baos.write(statusLine.getBytes(StandardCharsets.UTF_8));

            // ========== 헤더 작성 ==========
            // Header-Name: Header-Value\r\n 형식으로 모든 헤더 출력

            // HttpResponse의 headers 필드는 HttpHeaders 타입
            // HttpHeaders에서 모든 헤더 정보를 가져와서 처리
            writeHeaders(baos, response);

            // ========== 헤더와 바디 구분자 ==========
            // 빈 줄(\r\n)로 헤더 섹션과 바디 섹션을 구분
            // HTTP 표준에서 정의된 구분 방식
            baos.write("\r\n".getBytes(StandardCharsets.UTF_8));

            // ========== 바디 작성 ==========
            // 응답 바디가 있는 경우에만 작성

            // response.getBody(): 응답 바디를 바이트 배열로 반환
            // length: 배열의 길이 속성
            if (response.getBody().length > 0) {
                // 바디 데이터를 그대로 출력 스트림에 쓰기
                baos.write(response.getBody());
            }

            // toByteArray(): ByteArrayOutputStream의 내용을 바이트 배열로 변환
            // 메모리에 축적된 모든 데이터를 하나의 바이트 배열로 반환
            return baos.toByteArray();

        } catch (IOException e) {
            // ByteArrayOutputStream은 메모리 기반이므로 일반적으로 IOException 발생하지 않음
            // 하지만 예외적인 상황(메모리 부족 등)을 대비

            // RuntimeException: 언체크드 예외로 변환
            // 호출자가 강제로 예외 처리할 필요 없도록 함
            throw new RuntimeException("Failed to build HTTP response", e);
        }
    }

    /**
     * HttpResponse의 헤더들을 ByteArrayOutputStream에 작성합니다.
     *
     * private static 메서드로 분리한 이유:
     * - buildResponse 메서드의 복잡도 감소
     * - 헤더 처리 로직의 재사용 가능성
     * - 단일 책임 원칙 준수
     *
     * @param baos 출력 스트림
     * @param response HTTP 응답 객체
     * @throws IOException 출력 실패시
     */
    private static void writeHeaders(ByteArrayOutputStream baos, HttpResponse response) throws IOException {
        // HttpHeaders 객체 가져오기
        // getHeaders(): HttpResponse에서 HttpHeaders 객체 반환
        HttpHeaders headers = response.getHeaders();

        // 모든 헤더 이름을 순회하면서 출력
        // getNames(): HttpHeaders에서 모든 헤더 이름들을 Set<String>으로 반환
        for (String headerName : headers.getNames()) {
            // get(): HttpHeaders에서 특정 헤더의 모든 값들을 List<String>으로 반환
            // 하나의 헤더 이름에 여러 값이 있을 수 있음 (예: Set-Cookie)
            for (String headerValue : headers.get(headerName)) {
                // 각 헤더를 "이름: 값\r\n" 형식으로 작성
                // HTTP 표준에서 정의된 헤더 형식
                String headerLine = headerName + ": " + headerValue + "\r\n";

                // getBytes(): 문자열을 UTF-8 바이트 배열로 변환
                // StandardCharsets.UTF_8: UTF-8 인코딩 사용 (웹 표준)
                // write(): ByteArrayOutputStream에 바이트 배열 쓰기
                baos.write(headerLine.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    // ========== 편의 메소드들 ==========
    // 자주 사용되는 응답 타입을 쉽게 생성할 수 있는 팩토리 메서드들

    /**
     * 200 OK 응답을 생성합니다 (텍스트 응답)
     *
     * 편의 메서드를 제공하는 이유:
     * - 가장 자주 사용되는 응답 타입
     * - 코드 간소화 및 실수 방지
     * - 일관된 헤더 설정 보장
     *
     * @param body 응답 본문 (텍스트)
     * @return 설정된 HttpResponse 객체
     */
    public static HttpResponse ok(String body) {
        // new HttpResponse(): HttpResponse 객체 생성
        // HttpStatus.OK: 200 상태 코드 enum 값
        HttpResponse response = new HttpResponse(HttpStatus.OK);

        // setBody(): 응답 본문 설정
        // String을 그대로 전달 (내부에서 바이트 배열로 변환)
        response.setBody(body);

        // setContentType(): Content-Type 헤더 설정
        // "text/plain; charset=UTF-8": 텍스트 형식 + UTF-8 인코딩 명시
        response.setContentType("text/plain; charset=UTF-8");

        return response; // 설정된 응답 객체 반환
    }

    /**
     * 200 OK JSON 응답을 생성합니다
     *
     * JSON 전용 메서드를 제공하는 이유:
     * - JSON API에서 매우 자주 사용됨
     * - 올바른 Content-Type 설정 보장
     * - API 개발 시 편의성 제공
     *
     * @param json 응답 본문 (JSON 문자열)
     * @return 설정된 HttpResponse 객체
     */
    public static HttpResponse json(String json) {
        // HttpStatus.OK: 200 상태 코드로 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.OK);

        // setBody(): JSON 문자열을 응답 바디로 설정
        response.setBody(json);

        // "application/json; charset=UTF-8": JSON 미디어 타입 + UTF-8 인코딩
        // 클라이언트가 JSON으로 인식하도록 정확한 Content-Type 설정
        response.setContentType("application/json; charset=UTF-8");

        return response;
    }

    /**
     * 404 Not Found 응답을 생성합니다
     *
     * 404 전용 메서드를 제공하는 이유:
     * - 웹 개발에서 매우 자주 사용되는 오류 응답
     * - 일관된 오류 메시지 제공
     * - 개발자 실수 방지 (상태 코드 오타 등)
     *
     * @param message 오류 메시지 (null이면 기본 메시지 사용)
     * @return 설정된 HttpResponse 객체
     */
    public static HttpResponse notFound(String message) {
        // HttpStatus.NOT_FOUND: 404 상태 코드 enum 값
        HttpResponse response = new HttpResponse(HttpStatus.NOT_FOUND);

        // 삼항 연산자로 null 체크
        // message != null이면 message 사용, 아니면 "Not Found" 사용
        response.setBody(message != null ? message : "Not Found");

        // 오류 응답도 텍스트 형식으로 설정
        // "text/plain; charset=UTF-8": 단순 텍스트 형식 + UTF-8 인코딩
        response.setContentType("text/plain; charset=UTF-8");

        return response;
    }

    /**
     * 500 Internal Server Error 응답을 생성합니다
     *
     * 500 전용 메서드를 제공하는 이유:
     * - 서버 오류 처리에서 자주 사용됨
     * - 예외 처리 코드에서 간편하게 사용
     * - 일관된 서버 오류 응답 형식 보장
     *
     * @param message 오류 메시지 (null이면 기본 메시지 사용)
     * @return 설정된 HttpResponse 객체
     */
    public static HttpResponse serverError(String message) {
        // HttpStatus.INTERNAL_SERVER_ERROR: 500 상태 코드 enum 값
        HttpResponse response = new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR);

        // null 체크 후 기본 메시지 또는 사용자 메시지 설정
        // message != null: null이 아닌지 확인
        // ? message : "Internal Server Error": 삼항 연산자로 기본값 설정
        response.setBody(message != null ? message : "Internal Server Error");

        // 서버 오류도 텍스트 형식으로 설정
        // "text/plain; charset=UTF-8": 단순 텍스트 + UTF-8 인코딩
        response.setContentType("text/plain; charset=UTF-8");

        return response;
    }

    /**
     * HTML 응답을 생성합니다
     *
     * HTML 전용 메서드를 제공하는 이유:
     * - 웹 페이지 응답에서 자주 사용됨
     * - 올바른 Content-Type 설정 보장
     * - 브라우저 호환성 확보
     *
     * @param html HTML 내용
     * @return 설정된 HttpResponse 객체
     */
    public static HttpResponse html(String html) {
        // HttpStatus.OK: 200 상태 코드로 성공 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.OK);

        // setBody(): HTML 문자열을 응답 바디로 설정
        response.setBody(html);

        // "text/html; charset=UTF-8": HTML 미디어 타입 + UTF-8 인코딩
        // 브라우저가 HTML로 렌더링하도록 지시
        response.setContentType("text/html; charset=UTF-8");

        return response;
    }

    /**
     * 바이너리 데이터 응답을 생성합니다
     *
     * 바이너리 전용 메서드를 제공하는 이유:
     * - 파일 다운로드, 이미지 전송 등에 사용
     * - 적절한 Content-Type 설정 필요
     * - 바이트 배열 직접 처리
     *
     * @param data 바이너리 데이터
     * @param contentType MIME 타입 (예: "image/jpeg", "application/pdf")
     * @return 설정된 HttpResponse 객체
     */
    public static HttpResponse binary(byte[] data, String contentType) {
        // HttpStatus.OK: 200 상태 코드로 성공 응답 생성
        HttpResponse response = new HttpResponse(HttpStatus.OK);

        // setBody(): 바이트 배열을 직접 응답 바디로 설정
        response.setBody(data);

        // contentType이 제공되면 설정, 아니면 기본값 사용
        // contentType != null: null 체크
        // "application/octet-stream": 일반적인 바이너리 데이터 기본 타입
        response.setContentType(contentType != null ? contentType : "application/octet-stream");

        return response;
    }

    /**
     * 리다이렉트 응답을 생성합니다 (302 Found)
     *
     * 리다이렉트 전용 메서드를 제공하는 이유:
     * - 웹 개발에서 자주 사용되는 기능
     * - Location 헤더 설정 자동화
     * - HTTP 표준 준수
     *
     * @param location 리다이렉트할 URL
     * @return 설정된 HttpResponse 객체
     */
    public static HttpResponse redirect(String location) {
        // HttpStatus.FOUND: 302 상태 코드 (임시 리다이렉트)
        HttpResponse response = new HttpResponse(HttpStatus.FOUND);

        // Location 헤더 설정
        // 브라우저가 이 URL로 자동 이동하도록 지시
        // setHeader(): HttpResponse의 헤더 설정 메서드
        response.setHeader("Location", location);

        // 리다이렉트는 보통 빈 바디 사용
        // 바디 내용은 무시되므로 비워둠
        response.setBody("");

        return response;
    }

    /**
     * CORS 프리플라이트 응답을 생성합니다 (OPTIONS 요청용)
     *
     * CORS 전용 메서드를 제공하는 이유:
     * - 브라우저 CORS 정책 지원 필수
     * - OPTIONS 요청에 대한 표준 응답
     * - 복잡한 CORS 헤더 설정 자동화
     *
     * @param allowOrigin 허용할 오리진 ("*" 또는 특정 도메인)
     * @param allowMethods 허용할 HTTP 메서드들
     * @param allowHeaders 허용할 헤더들
     * @return 설정된 HttpResponse 객체
     */
    public static HttpResponse corsOptions(String allowOrigin, String allowMethods, String allowHeaders) {
        // HttpStatus.OK: 200 상태 코드로 성공 응답
        HttpResponse response = new HttpResponse(HttpStatus.OK);

        // CORS 관련 헤더들 설정
        // Access-Control-Allow-Origin: 요청을 허용할 오리진
        response.setHeader("Access-Control-Allow-Origin", allowOrigin != null ? allowOrigin : "*");

        // Access-Control-Allow-Methods: 허용할 HTTP 메서드들
        response.setHeader("Access-Control-Allow-Methods", allowMethods != null ? allowMethods : "GET, POST, PUT, DELETE, OPTIONS");

        // Access-Control-Allow-Headers: 허용할 요청 헤더들
        response.setHeader("Access-Control-Allow-Headers", allowHeaders != null ? allowHeaders : "Content-Type, Authorization");

        // Access-Control-Max-Age: 프리플라이트 응답 캐시 시간 (초)
        // 86400초 = 24시간, 브라우저가 이 시간동안 프리플라이트 요청을 캐시
        response.setHeader("Access-Control-Max-Age", "86400");

        // OPTIONS 응답은 보통 빈 바디
        response.setBody("");

        return response;
    }

    /**
     * 파일 다운로드 응답을 생성합니다
     *
     * 파일 다운로드 전용 메서드를 제공하는 이유:
     * - 파일 다운로드 시 필요한 헤더들 자동 설정
     * - Content-Disposition 헤더로 다운로드 동작 제어
     * - 브라우저 호환성 확보
     *
     * @param fileData 파일 데이터
     * @param fileName 다운로드될 파일명
     * @param contentType 파일의 MIME 타입
     * @return 설정된 HttpResponse 객체
     */
    public static HttpResponse fileDownload(byte[] fileData, String fileName, String contentType) {
        // HttpStatus.OK: 200 상태 코드로 성공 응답
        HttpResponse response = new HttpResponse(HttpStatus.OK);

        // setBody(): 파일 데이터를 응답 바디로 설정
        response.setBody(fileData);

        // Content-Type 설정 (파일 형식 지정)
        response.setContentType(contentType != null ? contentType : "application/octet-stream");

        // Content-Disposition 헤더 설정 - 브라우저에게 다운로드 동작 지시
        // attachment: 브라우저가 파일을 다운로드하도록 지시 (인라인 표시 대신)
        // filename="...": 다운로드될 파일명 지정
        if (fileName != null) {
            // String.format(): 형식화된 문자열 생성
            // %s: 문자열 포맷팅 플레이스홀더
            response.setHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", fileName));
        } else {
            // 파일명이 없으면 기본 다운로드 동작만 설정
            response.setHeader("Content-Disposition", "attachment");
        }

        return response;
    }
}