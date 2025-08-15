package com.serverarch.traditional;

// 공통 HTTP 라이브러리 임포트
// com.serverarch.common.http.*: 프로젝트의 공통 HTTP 관련 클래스들
// HttpStatus: HTTP 상태 코드를 나타내는 enum 클래스 (200, 404, 500 등)
// HttpHeaders: HTTP 헤더들을 관리하는 클래스 (Content-Type, Cache-Control 등)
import com.serverarch.common.http.*;

// Java I/O 라이브러리
// java.io.*: 입출력 관련 클래스들
// IOException: 입출력 예외 클래스 - 파일이나 네트워크 오류 시 발생
import java.io.*;

// Java 유틸리티 라이브러리
// java.util.*: 컬렉션, 유틸리티 클래스들
// List: 순서가 있는 컬렉션 인터페이스 - 여러 헤더 값 저장용
// Locale: 지역 설정 클래스 - 날짜 포맷에서 영어 로케일 사용
import java.util.*;

// Java 로깅 라이브러리
// java.util.logging.*: 자바 표준 로깅 시스템
// Logger: 로그 메시지 출력 클래스 - 디버깅과 모니터링용
// Level: 로그 레벨 enum - INFO, WARNING, SEVERE 등
import java.util.logging.*;

// Java 시간 처리 라이브러리
// java.time.ZonedDateTime: 시간대 정보를 포함한 날짜/시간 클래스
// java.time.format.DateTimeFormatter: 날짜/시간 포맷터 클래스
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
* HTTP 응답 생성 전담 클래스 (수정 버전)
* HttpResponse 객체를 바이트 배열로 변환하여 클라이언트에게 전송할 수 있는 형태로 만듦
*
* 설계 원칙:
* 1. HttpResponse의 byte[] body와 완벽 호환
* 2. HTTP/1.1 표준 준수한 응답 포맷 생성
* 3. 기본 헤더 자동 추가로 표준 준수 보장
* 4. 에러 상황에 대한 안전한 폴백 제공
*/
public class HttpResponseBuilder {
    // Logger 인스턴스 생성 - 이 클래스의 모든 로그 메시지 담당
    // Logger.getLogger(): 지정된 클래스 이름으로 로거 인스턴스 반환
    // HttpResponseBuilder.class.getName(): 현재 클래스의 완전한 이름 반환
    private static final Logger logger = Logger.getLogger(HttpResponseBuilder.class.getName());

    // HTTP 응답 관련 상수들
    // HTTP 버전 상수 - 모든 응답에서 일관되게 사용
    private static final String HTTP_VERSION = "HTTP/1.1";

    // CRLF (Carriage Return + Line Feed) - HTTP 표준에서 줄바꿈으로 사용
    // \r\n: HTTP 프로토콜에서 필수적인 줄바꿈 문자 조합
    private static final String CRLF = "\r\n";

    // 헤더 구분자 - 헤더 이름과 값 사이의 구분 문자
    // "Name: Value" 형태를 만들기 위한 구분자
    private static final String HEADER_SEPARATOR = ": ";

    // 기본 헤더들
    // 서버 식별 정보 - 클라이언트에게 서버 정보 제공
    private static final String SERVER_NAME = "EnhancedThreadedServer/2.2.0";

    // HTTP 날짜 포맷터 - RFC 1123 형식을 영어 로케일로 생성
    // DateTimeFormatter.ofPattern(): 지정된 패턴으로 포맷터 생성
    // "EEE, dd MMM yyyy HH:mm:ss 'GMT'": HTTP 표준 날짜 형식 (예: "Mon, 01 Jan 2024 12:00:00 GMT")
    // Locale.ENGLISH: 영어 로케일 사용으로 월, 요일 이름을 영어로 출력
    private static final DateTimeFormatter HTTP_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);

    /**
     * HttpResponse 객체를 HTTP 프로토콜 형식으로 변환하여 OutputStream에 직접 전송합니다.
     *
     * static 메서드로 구현한 이유:
     * - 상태를 유지하지 않는 순수 함수
     * - 인스턴스 생성 없이 사용 가능
     * - 스레드 안전성 보장
     *
     * @param outputStream 클라이언트로의 출력 스트림
     * @param response 전송할 HttpResponse 객체
     * @throws IOException 전송 실패 시
     */
    public static void buildAndSend(OutputStream outputStream, HttpResponse response) throws IOException {
        // buildResponse()로 바이트 배열 생성 후 스트림에 직접 전송
        byte[] responseBytes = buildResponse(response);
        outputStream.write(responseBytes);
        outputStream.flush(); // 버퍼 플러시로 즉시 전송 보장
    }

    /**
     * HttpResponse 객체를 바이트 배열로 변환
     *
     * @param response HTTP 응답 객체
     * @return 전송 가능한 바이트 배열
     * @throws IOException 변환 실패 시
     */
    public static byte[] buildResponse(HttpResponse response) throws IOException {
        try {
            // 1. 응답 헤더에 기본 헤더들 추가
            // addDefaultHeaders(): 표준 HTTP 헤더들을 자동으로 추가하는 메서드 호출
            addDefaultHeaders(response);

            // 2. 응답 라인 생성 (HTTP/1.1 200 OK)
            // StringBuilder: 가변 문자열 조작에 효율적인 클래스
            // HTTP 응답의 첫 번째 줄인 상태 라인을 담을 빌더 생성
            StringBuilder responseBuilder = new StringBuilder();

            // buildStatusLine(): 상태 라인을 생성하는 메서드 호출
            // append(): StringBuilder에 문자열 추가
            // CRLF 추가로 상태 라인 완료
            responseBuilder.append(buildStatusLine(response)).append(CRLF);

            // 3. 헤더들 추가
            // appendHeaders(): 모든 HTTP 헤더를 StringBuilder에 추가하는 메서드 호출
            appendHeaders(responseBuilder, response);

            // 4. 헤더와 바디 구분선
            // HTTP 표준에 따라 헤더와 바디 사이에 빈 줄(CRLF) 추가
            responseBuilder.append(CRLF);

            // 5. 헤더 부분을 바이트로 변환
            // responseBuilder.toString(): StringBuilder를 String으로 변환
            // .getBytes("UTF-8"): String을 UTF-8 인코딩으로 바이트 배열 변환
            byte[] headerBytes = responseBuilder.toString().getBytes("UTF-8");

            // 6. 바디 부분 가져오기 (수정: HttpResponse의 byte[] body 직접 사용)
            // getBodyBytes(): HttpResponse에서 바디 데이터를 바이트 배열로 추출
            byte[] bodyBytes = getBodyBytes(response);

            // 7. 헤더와 바디 결합
            // 전체 응답 크기 = 헤더 바이트 수 + 바디 바이트 수
            byte[] fullResponse = new byte[headerBytes.length + bodyBytes.length];

            // System.arraycopy(): 배열 복사 메서드 - 네이티브 코드로 구현되어 매우 빠름
            // 첫 번째 호출: 헤더 바이트들을 결과 배열의 앞부분에 복사
            // (원본 배열, 원본 시작 인덱스, 대상 배열, 대상 시작 인덱스, 복사할 길이)
            System.arraycopy(headerBytes, 0, fullResponse, 0, headerBytes.length);

            // 두 번째 호출: 바디 바이트들을 결과 배열의 헤더 뒷부분에 복사
            System.arraycopy(bodyBytes, 0, fullResponse, headerBytes.length, bodyBytes.length);

            // 성공 로그 출력 - 응답 생성 완료 정보
            // logger.fine(): 상세 정보 레벨 로그 - 개발/디버깅 시에만 보임
            // String.format(): 문자열 템플릿에 값을 대입하여 포맷된 문자열 생성
            // response.getStatus(): HttpResponse의 상태 코드 반환
            // fullResponse.length: 최종 응답의 총 바이트 크기
            logger.fine(String.format("응답 생성 완료: %s (%d bytes)",
                    response.getStatus(), fullResponse.length));

            // 완성된 HTTP 응답 바이트 배열 반환
            return fullResponse;

        } catch (Exception e) { // 모든 예외를 catch - 예상치 못한 오류 상황 대응
            // 경고 레벨로 오류 로그 출력
            // logger.log(): 레벨과 메시지, 예외 객체를 함께 로깅
            // Level.WARNING: 경고 레벨 - 문제가 발생했지만 서버는 계속 실행 가능
            logger.log(Level.WARNING, "HTTP 응답 생성 실패", e);

            // 실패 시 기본 500 에러 응답 생성 - 서버 안정성 보장
            // buildErrorResponse(): 안전한 기본 에러 응답 생성 메서드
            // e.getMessage(): 예외의 상세 메시지 추출
            return buildErrorResponse(500, "Internal Server Error: " + e.getMessage());
        }
    }

    /**
     * 상태 라인 생성 (HTTP/1.1 200 OK)
     * HTTP 응답의 첫 번째 줄을 생성하는 메서드
     *
     * @param response HTTP 응답 객체
     * @return 완성된 상태 라인 문자열
     */
    private static String buildStatusLine(HttpResponse response) {
        // response.getStatus(): HttpResponse에서 HttpStatus 객체 반환
        HttpStatus status = response.getStatus();

        // String.format(): 템플릿 문자열에 값들을 대입하여 완성된 문자열 생성
        // "%s %d %s": 문자열, 정수, 문자열 순서의 템플릿
        // HTTP_VERSION: "HTTP/1.1" 상수
        // status.getCode(): HttpStatus enum에서 숫자 코드 반환 (200, 404, 500 등)
        // status.getReasonPhrase(): HttpStatus enum에서 이유 문구 반환 ("OK", "Not Found" 등)
        return String.format("%s %d %s",
                HTTP_VERSION,
                status.getCode(),
                status.getReasonPhrase()
        );
    }

    /**
     * 기본 헤더들 추가
     * HTTP 표준을 준수하기 위한 필수/권장 헤더들을 자동으로 설정
     *
     * @param response 헤더를 추가할 HTTP 응답 객체
     */
    private static void addDefaultHeaders(HttpResponse response) {
        // response.getHeaders(): HttpResponse에서 HttpHeaders 객체 반환
        HttpHeaders headers = response.getHeaders();

        // Server 헤더 설정 - 서버 소프트웨어 정보 제공
        // headers.contains(): 특정 헤더가 이미 존재하는지 확인
        // !: 논리 부정 연산자 - 헤더가 없을 때만 실행
        if (!headers.contains("Server")) {
            // headers.set(): 헤더 이름과 값을 설정 (기존 값 덮어쓰기)
            headers.set("Server", SERVER_NAME);
        }

        // Date 헤더 설정 - HTTP 표준에서 권장하는 응답 생성 시간
        if (!headers.contains("Date")) {
            // ZonedDateTime.now(): 현재 시간대 기준 현재 시간 반환
            // format(): DateTimeFormatter를 사용하여 날짜를 문자열로 변환
            String httpDate = ZonedDateTime.now().format(HTTP_DATE_FORMAT);
            headers.set("Date", httpDate);
        }

        // Connection 헤더 설정 - 연결 유지 정책 명시
        if (!headers.contains("Connection")) {
            // "close": 응답 후 연결 종료 - HTTP/1.0 스타일, 단순하고 안정적
            headers.set("Connection", "close");
        }

        // Content-Length 설정 (바디가 있는 경우)
        // getBodyBytes(): 응답 바디를 바이트 배열로 추출
        byte[] bodyBytes = getBodyBytes(response);

        // bodyBytes.length > 0: 바디가 실제로 존재하는지 확인
        // &&: 논리 AND 연산자 - 두 조건이 모두 true일 때만 실행
        if (bodyBytes.length > 0 && !headers.contains("Content-Length")) {
            // String.valueOf(): 숫자를 문자열로 변환
            // Content-Length 헤더: 바디의 바이트 크기를 명시 (HTTP 표준)
            headers.set("Content-Length", String.valueOf(bodyBytes.length));
        }

        // Content-Type 기본값 설정 - 브라우저가 콘텐츠를 올바르게 해석하도록
        if (!headers.contains("Content-Type")) {
            // determineDefaultContentType(): 바디 내용을 분석하여 적절한 MIME 타입 결정
            String defaultContentType = determineDefaultContentType(response);
            headers.set("Content-Type", defaultContentType);
        }
    }

    /**
     * 헤더들을 응답에 추가
     * HttpHeaders 객체의 모든 헤더를 HTTP 형식으로 변환
     *
     * @param responseBuilder 응답을 구성중인 StringBuilder
     * @param response HTTP 응답 객체
     */
    private static void appendHeaders(StringBuilder responseBuilder, HttpResponse response) {
        // response.getHeaders(): HttpHeaders 객체 반환
        HttpHeaders headers = response.getHeaders();

        // headers.getNames(): 모든 헤더 이름들을 Set으로 반환
        // for-each 루프: 컬렉션의 모든 요소를 순회
        for (String headerName : headers.getNames()) {
            // headers.get(): 특정 헤더 이름에 대한 모든 값들을 List로 반환
            // HTTP는 같은 이름의 헤더가 여러 개 있을 수 있음 (예: Set-Cookie)
            List<String> headerValues = headers.get(headerName);

            // 해당 헤더의 모든 값들을 순회
            for (String headerValue : headerValues) {
                // HTTP 헤더 형식으로 추가: "Name: Value\r\n"
                // append() 메서드 체이닝: 여러 문자열을 연속으로 추가
                responseBuilder.append(headerName)         // 헤더 이름
                        .append(HEADER_SEPARATOR)           // ": " 구분자
                        .append(headerValue)                // 헤더 값
                        .append(CRLF);                      // 줄바꿈
            }
        }
    }

    /**
     * 응답 바디 바이트 가져오기 (수정: HttpResponse의 byte[] 필드 직접 사용)
     * HttpResponse 객체에서 바디 데이터를 바이트 배열로 추출
     *
     * @param response HTTP 응답 객체
     * @return 바디 바이트 배열 (실패 시 빈 배열)
     */
    private static byte[] getBodyBytes(HttpResponse response) {
        try {
            // HttpResponse.getBody(): 이미 byte[] 형태로 저장된 바디 반환
            // 수정 전에는 String으로 처리했지만, HttpResponse는 원래 byte[]를 사용
            byte[] body = response.getBody();

            // null 체크 - 안전한 처리
            if (body == null) {
                // null이면 빈 배열 반환 - NPE 방지
                return new byte[0];
            }

            // 바디 데이터 그대로 반환 - 이미 올바른 형태
            return body;

        } catch (Exception e) { // 예상치 못한 오류 상황 대응
            // 경고 로그 출력 - 바디 추출 실패 상황 기록
            logger.warning("바디 추출 실패: " + e.getMessage());

            // 실패 시 빈 배열 반환 - 서버 안정성 보장
            return new byte[0];
        }
    }

    /**
     * 기본 Content-Type 결정 (수정: byte[] 바디를 String으로 변환하여 분석)
     * 바디 내용을 분석하여 적절한 MIME 타입을 자동으로 결정
     *
     * @param response HTTP 응답 객체
     * @return 결정된 Content-Type 문자열
     */
    private static String determineDefaultContentType(HttpResponse response) {
        try {
            // HttpResponse에서 바디를 바이트 배열로 가져옴
            byte[] bodyBytes = response.getBody();

            // 바디가 없거나 빈 경우 기본 텍스트 타입 반환
            if (bodyBytes == null || bodyBytes.length == 0) {
                return "text/plain; charset=UTF-8";
            }

            // 바이트 배열을 UTF-8 문자열로 변환하여 내용 분석
            // new String(): 바이트 배열과 인코딩을 받아 문자열 생성
            String body = new String(bodyBytes, "UTF-8");

            // 문자열 앞뒤 공백 제거 - 정확한 분석을 위해
            String trimmedBody = body.trim();

            // JSON인지 확인 - 가장 널리 사용되는 API 응답 형식
            // startsWith()와 endsWith(): 문자열 시작/끝 확인 메서드
            // ||: 논리 OR 연산자 - 둘 중 하나라도 true이면 true
            if ((trimmedBody.startsWith("{") && trimmedBody.endsWith("}")) ||
                    (trimmedBody.startsWith("[") && trimmedBody.endsWith("]"))) {
                // JSON 객체 {} 또는 JSON 배열 [] 형태면 JSON 타입 반환
                return "application/json; charset=UTF-8";
            }

            // HTML인지 확인 - 웹 페이지 응답 형식
            // toLowerCase(): 대소문자 구분 없이 비교하기 위해 소문자로 변환
            // contains(): 특정 문자열이 포함되어 있는지 확인
            if (trimmedBody.toLowerCase().contains("<html") ||
                    trimmedBody.toLowerCase().contains("<!doctype")) {
                // HTML 태그나 DOCTYPE 선언이 있으면 HTML 타입 반환
                return "text/html; charset=UTF-8";
            }

            // XML인지 확인 - 구조화된 데이터 형식
            if (trimmedBody.toLowerCase().startsWith("<?xml")) {
                // XML 선언이 있으면 XML 타입 반환
                return "application/xml; charset=UTF-8";
            }

            // 기본값 - 위 조건에 해당하지 않으면 일반 텍스트로 처리
            return "text/plain; charset=UTF-8";

        } catch (Exception e) { // 문자열 변환 또는 분석 중 오류 발생 시
            // 경고 로그 출력 - Content-Type 결정 실패 상황 기록
            logger.warning("Content-Type 결정 실패: " + e.getMessage());

            // 실패 시 안전한 기본값 반환
            return "text/plain; charset=UTF-8";
        }
    }

    /**
     * 에러 응답 생성 (빌드 실패 시 사용)
     * 응답 생성 중 오류가 발생했을 때 안전한 기본 에러 응답을 생성
     *
     * @param statusCode HTTP 상태 코드
     * @param message 에러 메시지
     * @return 완성된 에러 응답 바이트 배열
     */
    private static byte[] buildErrorResponse(int statusCode, String message) {
        try {
            // StringBuilder로 에러 응답 구성 - 효율적인 문자열 조합
            StringBuilder response = new StringBuilder();

            // 상태 라인 구성 - HTTP/1.1 statusCode ReasonPhrase
            response.append(HTTP_VERSION).append(" ")      // HTTP 버전
                    .append(statusCode).append(" ")        // 상태 코드 (숫자)
                    .append(getReasonPhrase(statusCode))    // 상태 문구 (텍스트)
                    .append(CRLF);                          // 줄바꿈

            // 기본 헤더들 추가 - 표준 HTTP 응답 형식 준수
            // Server 헤더
            response.append("Server").append(HEADER_SEPARATOR)
                    .append(SERVER_NAME).append(CRLF);

            // Date 헤더 - 현재 시간을 HTTP 형식으로
            response.append("Date").append(HEADER_SEPARATOR)
                    .append(ZonedDateTime.now().format(HTTP_DATE_FORMAT))
                    .append(CRLF);

            // Content-Type 헤더 - 에러 메시지는 일반 텍스트
            response.append("Content-Type").append(HEADER_SEPARATOR)
                    .append("text/plain; charset=UTF-8").append(CRLF);

            // Content-Length 헤더 - 메시지 바이트 크기 계산
            // message.getBytes("UTF-8").length: 메시지를 UTF-8로 인코딩했을 때의 바이트 길이
            response.append("Content-Length").append(HEADER_SEPARATOR)
                    .append(message.getBytes("UTF-8").length).append(CRLF);

            // Connection 헤더 - 에러 후 연결 종료
            response.append("Connection").append(HEADER_SEPARATOR)
                    .append("close").append(CRLF);

            // 헤더와 바디 구분선 - HTTP 표준
            response.append(CRLF);

            // 바디 - 에러 메시지 추가
            response.append(message);

            // 완성된 응답을 바이트 배열로 변환하여 반환
            return response.toString().getBytes("UTF-8");

        } catch (Exception e) { // 에러 응답 생성마저 실패한 경우
            // 최후의 수단 - 가장 기본적인 하드코딩된 에러 응답
            // 어떤 상황에서도 실패하지 않는 안전한 응답
            String basicError = "HTTP/1.1 500 Internal Server Error\r\n" +
                    "Content-Length: 21\r\n" +
                    "Connection: close\r\n\r\n" +
                    "Internal Server Error";

            // getBytes(): 기본 인코딩으로 바이트 배열 변환 (UTF-8이 기본)
            return basicError.getBytes();
        }
    }

    /**
     * 상태 코드에 대한 기본 Reason Phrase 반환
     * HTTP 표준에 정의된 상태 코드별 표준 문구를 반환
     *
     * @param statusCode HTTP 상태 코드 (숫자)
     * @return 해당 상태 코드의 표준 문구
     */
    private static String getReasonPhrase(int statusCode) {
        // switch 문으로 상태 코드별 분기 처리
        switch (statusCode) {
            // 2xx 성공 상태 코드들
            case 200: return "OK";                      // 성공
            case 201: return "Created";                 // 리소스 생성 성공
            case 204: return "No Content";              // 성공, 응답 바디 없음

            // 3xx 리다이렉션 상태 코드들
            case 301: return "Moved Permanently";       // 영구 이동
            case 302: return "Found";                   // 임시 이동
            case 304: return "Not Modified";            // 수정되지 않음 (캐시 유효)

            // 4xx 클라이언트 오류 상태 코드들
            case 400: return "Bad Request";             // 잘못된 요청
            case 401: return "Unauthorized";            // 인증 필요
            case 403: return "Forbidden";               // 접근 금지
            case 404: return "Not Found";               // 리소스 없음
            case 405: return "Method Not Allowed";      // 허용되지 않는 메서드
            case 429: return "Too Many Requests";       // 요청 횟수 초과

            // 5xx 서버 오류 상태 코드들
            case 500: return "Internal Server Error";   // 내부 서버 오류
            case 502: return "Bad Gateway";             // 잘못된 게이트웨이
            case 503: return "Service Unavailable";     // 서비스 사용 불가

            // 정의되지 않은 상태 코드 - 기본값 반환
            default: return "Unknown Status";
        }
    }

    /**
     * 압축 지원 여부 확인 (향후 확장용)
     * 클라이언트가 압축을 지원하는지 확인하여 응답 크기 최적화
     * 현재는 미구현 상태로 항상 false 반환
     *
     * @param response HTTP 응답 객체
     * @param acceptEncoding 클라이언트의 Accept-Encoding 헤더 값
     * @return 압축 사용 여부 (현재는 항상 false)
     */
    private static boolean shouldCompress(HttpResponse response, String acceptEncoding) {
        // 현재는 압축 미지원, 향후 gzip, deflate 등 구현 가능
        // 압축 구현 시 고려사항:
        // 1. Accept-Encoding 헤더에서 지원 압축 방식 확인
        // 2. 바디 크기가 압축할 만큼 큰지 확인 (보통 1KB 이상)
        // 3. 이미 압축된 파일(이미지, 비디오)은 제외
        // 4. Content-Encoding 헤더 추가 필요
        return false;
    }

    /**
     * 청크 전송 인코딩 지원 여부 확인 (향후 확장용)
     * 큰 응답을 여러 청크로 나누어 전송할지 결정
     * 현재는 미구현 상태로 항상 false 반환
     *
     * @param response HTTP 응답 객체
     * @return 청크 인코딩 사용 여부 (현재는 항상 false)
     */
    private static boolean shouldUseChunkedEncoding(HttpResponse response) {
        // 현재는 청크 인코딩 미지원, 향후 구현 가능
        // 청크 인코딩 구현 시 고려사항:
        // 1. Content-Length 헤더 제거 필요
        // 2. Transfer-Encoding: chunked 헤더 추가
        // 3. 각 청크 앞에 크기(16진수) + CRLF 추가
        // 4. 마지막에 0 크기 청크 + CRLF + CRLF 추가
        // 5. 스트리밍 데이터나 큰 파일 전송에 유용
        return false;
    }
}