package com.serverarch.traditional; // 패키지 선언 - 전통적인 스레드 기반 서버 아키텍처 패키지

// import 선언부 - 외부 클래스들을 현재 클래스에서 사용할 수 있도록 경로 지정
import com.serverarch.common.http.*; // HTTP 관련 공통 클래스들 전체 import - HttpRequest, HttpResponse, HttpHeaders, HttpStatus, HttpMethod 등
import java.io.*; // 입출력 관련 클래스들 전체 import - InputStream, OutputStream, BufferedReader, InputStreamReader 등
import java.nio.charset.StandardCharsets; // 문자 인코딩 표준 상수 클래스 - UTF_8, US_ASCII 등의 표준 인코딩 제공
import java.util.logging.*; // 로깅 관련 클래스들 전체 import - Logger, Level 등 로그 기능 제공

/**
 * Thread-per-Request 방식의 HTTP 요청 처리기
 *
 * 역할:
 * 1. HTTP 요청 파싱 (InputStream -> HttpRequest)
 * 2. 비즈니스 로직 실행 (HttpRequest -> HttpResponse)
 * 3. HTTP 응답 직렬화 (HttpResponse -> OutputStream)
 *
 * 특징:
 * - 동기식 블로킹 I/O 사용
 * - 각 요청을 별도 스레드에서 완전히 처리
 * - 간단하고 직관적인 요청/응답 흐름
 * - 디버깅과 에러 추적이 용이
 */
public class ThreadedRequestProcessor { // public 클래스 선언 - 다른 패키지에서 접근 가능한 HTTP 요청 처리 클래스

    // 로거 - 요청 처리 과정 추적용
    private static final Logger logger = Logger.getLogger(ThreadedRequestProcessor.class.getName()); // static final - 클래스 레벨 상수, Logger.getLogger() - 클래스 이름 기반 로거 생성

    // HTTP 프로토콜 상수들 - 프로토콜 표준에 따른 고정값들
    private static final String HTTP_VERSION = "HTTP/1.1";          // static final String - 지원하는 HTTP 버전 상수, HTTP/1.1 프로토콜 사용
    private static final String CRLF = "\r\n";                      // static final String - HTTP 줄바꿈 문자 상수, Carriage Return + Line Feed
    private static final int MAX_REQUEST_LINE_LENGTH = 8192;        // static final int - 요청 라인 최대 길이 제한 (8KB), DoS 공격 방지
    private static final int MAX_HEADER_SIZE = 8192;                // static final int - 헤더 최대 크기 제한 (8KB), 메모리 고갈 공격 방지
    private static final int MAX_BODY_SIZE = 10 * 1024 * 1024;     // static final int - 바디 최대 크기 제한 (10MB), 산술 연산으로 가독성 향상

    /**
     * HTTP 요청 파싱
     * InputStream에서 HTTP 요청을 읽어 HttpRequest 객체로 변환
     *
     * @param inputStream 클라이언트로부터의 입력 스트림
     * @return 파싱된 HTTP 요청
     * @throws IOException 파싱 실패 또는 I/O 오류 시
     */
    public HttpRequest parseRequest(InputStream inputStream) throws IOException { // public 메서드 - 외부에서 호출 가능, throws IOException - 체크 예외 선언
        // BufferedReader로 라인 단위 읽기 효율성 향상
        // US-ASCII로 읽는 이유: HTTP 헤더는 ASCII로 정의되어 있음
        BufferedReader reader = new BufferedReader( // BufferedReader 생성 - 버퍼링으로 읽기 성능 향상
                new InputStreamReader(inputStream, StandardCharsets.US_ASCII) // InputStreamReader - 바이트 스트림을 문자 스트림으로 변환, US_ASCII 인코딩 사용
        );

        try { // try-catch 블록 시작 - 예외 처리를 위한 구조
            // 1. 요청 라인 파싱 (GET /path HTTP/1.1)
            RequestLine requestLine = parseRequestLine(reader); // parseRequestLine() 메서드 호출 - 첫 번째 라인을 RequestLine 객체로 파싱

            // 2. 헤더 파싱 (Host: example.com, Content-Type: text/html 등)
            HttpHeaders headers = parseHeaders(reader); // parseHeaders() 메서드 호출 - 헤더 라인들을 HttpHeaders 객체로 파싱

            // 3. 바디 파싱 (Content-Length에 따라)
            byte[] body = parseBody(inputStream, headers); // parseBody() 메서드 호출 - Content-Length 헤더 기준으로 바디 읽기

            // 4. HttpRequest 객체 생성
            HttpRequest request = new HttpRequest( // HttpRequest 생성자 호출 - 파싱된 구성요소들로 요청 객체 생성
                    requestLine.method,      // RequestLine.method 필드 - HTTP 메서드 (GET, POST 등)
                    requestLine.path,        // RequestLine.path 필드 - 요청 경로 (/index.html 등)
                    requestLine.queryString, // RequestLine.queryString 필드 - 쿼리 스트링 (name=value&key=val 등)
                    headers,                 // 파싱된 HTTP 헤더들
                    body                     // 파싱된 요청 바디 바이트 배열
            );

            // 파싱 성공 로그
            logger.fine(String.format("요청 파싱 완료: %s %s", // Logger.fine() - 상세 로그 레벨, String.format() - 문자열 템플릿 사용
                    requestLine.method, requestLine.path)); // method와 path 필드 접근

            return request; // 완성된 HttpRequest 객체 반환

        } catch (IOException e) { // IOException 예외 처리 - I/O 관련 오류
            // I/O 오류 - 네트워크 문제나 클라이언트 연결 끊김
            logger.log(Level.WARNING, "HTTP 요청 파싱 중 I/O 오류", e); // Logger.log() - 레벨과 메시지, 예외 객체를 함께 로깅
            throw e; // 예외 재던지기 - 호출자에게 예외 전파

        } catch (Exception e) { // Exception 예외 처리 - 모든 종류의 예외 처리
            // 기타 파싱 오류 - 잘못된 HTTP 형식 등
            logger.log(Level.WARNING, "HTTP 요청 파싱 실패", e); // WARNING 레벨로 오류 로깅
            throw new IOException("잘못된 HTTP 요청 형식", e); // 새로운 IOException 생성 - 원본 예외를 원인으로 포함
        }
    }

    /**
     * 요청 라인 파싱
     * "GET /path?query HTTP/1.1" 형태의 첫 번째 라인 파싱
     *
     * @param reader 입력 리더
     * @return 파싱된 요청 라인 정보
     * @throws IOException 파싱 실패 시
     */
    private RequestLine parseRequestLine(BufferedReader reader) throws IOException { // private 메서드 - 클래스 내부에서만 사용
        // 첫 번째 라인 읽기
        String line = reader.readLine(); // BufferedReader.readLine() - 한 줄씩 읽기, 줄바꿈 문자 제외하고 반환

        // 빈 라인이나 null 체크 - 잘못된 요청
        if (line == null || line.trim().isEmpty()) { // 논리 연산자 OR - 둘 중 하나라도 true면 전체가 true
            throw new IOException("빈 요청 라인"); // IOException 던지기 - 잘못된 HTTP 요청 형식
        }

        // 요청 라인 길이 제한 체크 - DoS 공격 방지
        if (line.length() > MAX_REQUEST_LINE_LENGTH) { // String.length() - 문자열 길이 반환
            throw new IOException("요청 라인이 너무 깁니다: " + line.length()); // 문자열 연결 - + 연산자로 에러 메시지와 실제 길이 결합
        }

        // 공백으로 분리 - "METHOD URI VERSION" 형태
        String[] parts = line.trim().split("\\s+"); // String.trim() - 앞뒤 공백 제거, split() - 정규식으로 문자열 분할, \\s+ - 하나 이상의 공백 문자
        if (parts.length != 3) { // 배열 길이 확인 - HTTP 요청 라인은 반드시 3개 부분으로 구성
            throw new IOException("잘못된 요청 라인 형식: " + line); // + 연산자 - 에러 메시지와 원본 라인 결합
        }

        // HTTP 메서드 파싱 및 검증
        String methodStr = parts[0].toUpperCase(); // 배열 인덱스 접근 [0] - 첫 번째 요소, toUpperCase() - 대문자로 변환
        String method; // String 타입으로 변경 - 기존 HttpMethod 상수와 호환
        if (!HttpMethod.isStandardMethod(methodStr)) { // 논리 부정 연산자 ! - HttpMethod.isStandardMethod() 결과를 반전
            throw new IOException("지원하지 않는 HTTP 메서드: " + methodStr); // 지원하지 않는 메서드에 대한 예외
        }
        method = methodStr; // 검증된 메서드 문자열 사용 - 할당 연산자로 값 저장

        // URI 파싱 - 경로와 쿼리 스트링 분리
        String uri = parts[1]; // 배열의 두 번째 요소 [1] - URI 부분
        String path; // 지역 변수 선언 - 경로 저장용
        String queryString = null; // 지역 변수 선언 및 초기화 - 쿼리 스트링 저장용, 기본값 null

        // '?' 기준으로 경로와 쿼리 스트링 분리
        int queryIndex = uri.indexOf('?'); // String.indexOf() - 특정 문자의 첫 번째 위치 반환, 없으면 -1
        if (queryIndex != -1) { // -1과 비교 - '?'가 발견된 경우
            path = uri.substring(0, queryIndex);           // String.substring() - 시작 인덱스부터 끝 인덱스 직전까지 부분 문자열
            queryString = uri.substring(queryIndex + 1);   // queryIndex + 1 - '?' 다음 문자부터 끝까지
        } else {
            path = uri;  // 쿼리 스트링이 없는 경우 - URI 전체가 경로
        }

        // HTTP 버전 검증
        String version = parts[2]; // 배열의 세 번째 요소 [2] - HTTP 버전
        if (!version.startsWith("HTTP/")) { // String.startsWith() - 특정 문자열로 시작하는지 확인
            throw new IOException("잘못된 HTTP 버전: " + version); // 잘못된 HTTP 버전 형식 예외
        }

        return new RequestLine(method, path, queryString, version); // RequestLine 생성자 호출 - 파싱된 구성요소들로 객체 생성
    }

    /**
     * HTTP 헤더들 파싱
     * "Header-Name: Header-Value" 형태의 라인들 파싱
     *
     * @param reader 입력 리더
     * @return 파싱된 HTTP 헤더들
     * @throws IOException 파싱 실패 시
     */
    private HttpHeaders parseHeaders(BufferedReader reader) throws IOException { // private 메서드 - HTTP 헤더 파싱 전용
        HttpHeaders headers = new HttpHeaders(); // HttpHeaders 객체 생성 - 헤더 저장용 컨테이너
        String line; // 지역 변수 선언 - 각 헤더 라인 저장용
        int totalHeaderSize = 0; // int 변수 초기화 - DoS 공격 방지를 위한 헤더 크기 누적 계산용

        // 빈 라인이 나올 때까지 헤더 읽기 (빈 라인은 헤더 섹션의 끝)
        while ((line = reader.readLine()) != null && !line.trim().isEmpty()) { // while 루프 - 조건이 true인 동안 반복, 할당과 동시에 조건 검사

            // 헤더 크기 제한 체크 - 메모리 고갈 공격 방지
            totalHeaderSize += line.length(); // 복합 할당 연산자 += - totalHeaderSize = totalHeaderSize + line.length()와 동일
            if (totalHeaderSize > MAX_HEADER_SIZE) { // 누적 헤더 크기가 제한을 초과하는지 확인
                throw new IOException("헤더 크기가 너무 큽니다: " + totalHeaderSize); // 헤더 크기 제한 초과 예외
            }

            // 헤더 라인 파싱 - "Name: Value" 형태
            int colonIndex = line.indexOf(':'); // String.indexOf() - 콜론(:) 문자의 위치 찾기
            if (colonIndex == -1) { // 콜론이 없는 경우 - 잘못된 헤더 형식
                // ':' 가 없으면 잘못된 헤더 형식
                throw new IOException("잘못된 헤더 형식: " + line); // 헤더 형식 오류 예외
            }

            // 헤더 이름과 값 추출
            String name = line.substring(0, colonIndex).trim(); // substring() - 콜론 이전 부분, trim() - 공백 제거
            String value = line.substring(colonIndex + 1).trim(); // colonIndex + 1 - 콜론 다음 문자부터, trim() - 공백 제거

            // 빈 헤더 이름 체크
            if (name.isEmpty()) { // String.isEmpty() - 문자열이 빈 문자열인지 확인
                throw new IOException("빈 헤더 이름: " + line); // 빈 헤더 이름 예외
            }

            // 헤더 추가 - 대소문자 무관하게 처리
            headers.add(name, value); // HttpHeaders.add() - 헤더 이름과 값을 추가, 같은 이름의 여러 헤더 지원
        }

        return headers; // 파싱 완료된 HttpHeaders 객체 반환
    }

    /**
     * HTTP 요청 바디 파싱
     * Content-Length 헤더에 따라 바디 크기만큼 읽기
     *
     * @param inputStream 입력 스트림 (바이너리 데이터용)
     * @param headers HTTP 헤더들 (Content-Length 확인용)
     * @return 파싱된 바디 바이트 배열
     * @throws IOException 파싱 실패 시
     */
    private byte[] parseBody(InputStream inputStream, HttpHeaders headers) throws IOException { // private 메서드 - 바디 파싱 전용
        // Content-Length 헤더 확인
        String contentLengthStr = headers.getContentLength(); // HttpHeaders.getContentLength() - Content-Length 헤더 값 조회
        if (contentLengthStr == null) { // null 체크 - Content-Length 헤더가 없는 경우
            // Content-Length가 없으면 바디 없음으로 간주
            return new byte[0]; // 길이 0인 바이트 배열 생성하여 반환
        }

        // Content-Length 파싱
        int contentLength; // int 변수 선언 - 바디 길이 저장용
        try { // try-catch 블록 - 숫자 파싱 예외 처리
            contentLength = Integer.parseInt(contentLengthStr); // Integer.parseInt() - 문자열을 정수로 변환
        } catch (NumberFormatException e) { // NumberFormatException - 숫자 형식이 잘못된 경우 발생하는 예외
            throw new IOException("잘못된 Content-Length: " + contentLengthStr); // IOException으로 감싸서 던지기
        }

        // 음수 체크
        if (contentLength < 0) { // 음수 확인 - Content-Length는 0 이상이어야 함
            throw new IOException("음수 Content-Length: " + contentLength); // 음수 Content-Length 예외
        }

        // 바디 크기 제한 체크 - DoS 공격 방지
        if (contentLength > MAX_BODY_SIZE) { // 최대 바디 크기 제한 확인
            throw new IOException("요청 바디가 너무 큽니다: " + contentLength); // 바디 크기 제한 초과 예외
        }

        // 바디가 없으면 빈 배열 반환
        if (contentLength == 0) { // 바디 길이가 0인 경우
            return new byte[0]; // 빈 바이트 배열 반환
        }

        // 바디 읽기
        byte[] body = new byte[contentLength]; // 지정된 크기의 바이트 배열 생성
        int totalRead = 0; // int 변수 초기화 - 읽은 바이트 수 누적 계산용

        // 지정된 크기만큼 모두 읽을 때까지 반복
        while (totalRead < contentLength) { // while 루프 - 모든 바이트를 읽을 때까지 반복
            int bytesRead = inputStream.read(body, totalRead, contentLength - totalRead); // InputStream.read() - 배열의 특정 위치부터 지정된 길이만큼 읽기

            if (bytesRead == -1) { // -1 확인 - 스트림의 끝을 의미
                // 스트림 끝에 도달했는데 아직 다 읽지 못한 경우
                throw new IOException("요청 바디가 예상보다 짧습니다"); // 불완전한 바디 예외
            }

            totalRead += bytesRead; // 복합 할당 연산자 - 읽은 바이트 수를 누적
        }

        return body; // 완전히 읽은 바디 바이트 배열 반환
    }

    /**
     * HTTP 요청 처리
     * 실제 비즈니스 로직을 실행하여 응답 생성
     *
     * @param request HTTP 요청
     * @return HTTP 응답
     */
    public HttpResponse processRequest(HttpRequest request) { // public 메서드 - 외부에서 호출 가능한 요청 처리 메서드
        try { // try-catch 블록 - 요청 처리 중 예외 처리
            // 요청 처리 시작 로그
            logger.fine(String.format("요청 처리 시작: %s %s", // Logger.fine() - 상세 로그 레벨
                    request.getMethod(), request.getPath())); // HttpRequest.getMethod(), getPath() - 메서드와 경로 조회

            // 간단한 라우팅 - 실제로는 더 정교한 라우팅 시스템 필요
            HttpResponse response = routeRequest(request); // routeRequest() 메서드 호출 - 요청 경로에 따른 적절한 핸들러 실행

            // 요청 처리 완료 로그
            logger.fine(String.format("요청 처리 완료: %s %s -> %s", // 처리 완료 로그
                    request.getMethod(), request.getPath(), response.getStatus())); // response.getStatus() - 응답 상태 코드 조회

            return response; // 처리된 응답 반환

        } catch (Exception e) { // Exception - 모든 예외 처리
            // 비즈니스 로직 실행 중 오류
            logger.log(Level.SEVERE, "요청 처리 중 오류 발생", e); // Level.SEVERE - 심각한 오류 레벨

            // 500 Internal Server Error 응답
            return HttpResponse.serverError("요청 처리 중 오류가 발생했습니다"); // HttpResponse.serverError() - 500 에러 응답 생성
        }
    }

    /**
     * 간단한 라우팅 시스템
     * 요청 경로에 따라 적절한 핸들러 호출
     *
     * @param request HTTP 요청
     * @return HTTP 응답
     */
    private HttpResponse routeRequest(HttpRequest request) { // private 메서드 - 라우팅 로직
        String path = request.getPath(); // HttpRequest.getPath() - 요청 경로 조회
        String method = request.getMethod(); // HttpRequest.getMethod() - HTTP 메서드 조회, String 타입으로 변경

        // 경로별 라우팅 - 실제로는 더 정교한 패턴 매칭 필요
        switch (path) { // switch 문 - 경로에 따른 분기 처리
            case "/": // 루트 경로
                return handleHome(request); // handleHome() 메서드 호출 - 홈 페이지 처리

            case "/hello": // hello 경로
                return handleHello(request); // handleHello() 메서드 호출 - 인사말 페이지 처리

            case "/echo": // echo 경로
                return handleEcho(request); // handleEcho() 메서드 호출 - 에코 서비스 처리

            case "/stats": // stats 경로
                return handleStats(request); // handleStats() 메서드 호출 - 서버 통계 페이지 처리

            default: // 위에 해당하지 않는 모든 경우
                // 등록되지 않은 경로 - 404 Not Found
                return HttpResponse.notFound("요청한 경로를 찾을 수 없습니다: " + path); // HttpResponse.notFound() - 404 에러 응답 생성
        }
    }

    /**
     * 홈 페이지 핸들러
     * 서버 정보와 사용 가능한 엔드포인트 안내
     */
    private HttpResponse handleHome(HttpRequest request) { // private 메서드 - 홈 페이지 처리 핸들러
        String html = "<!DOCTYPE html>\n" + // 문자열 연결 - HTML 문서 시작
                "<html>\n" + // HTML 루트 요소
                "<head>\n" + // HTML 헤드 섹션
                "    <title>ThreadedServer</title>\n" + // 페이지 제목
                "    <style>\n" + // CSS 스타일 시작
                "        body { font-family: Arial, sans-serif; margin: 40px; }\n" + // body 스타일 - 폰트, 여백 설정
                "        h1 { color: #2196F3; }\n" + // h1 스타일 - 파란색 제목
                "        .endpoint { background: #f5f5f5; padding: 10px; margin: 10px 0; border-radius: 4px; }\n" + // 엔드포인트 스타일 - 회색 배경, 패딩, 둥근 모서리
                "    </style>\n" + // CSS 스타일 종료
                "</head>\n" + // HTML 헤드 섹션 종료
                "<body>\n" + // HTML 바디 시작
                "    <h1>ThreadedServer</h1>\n" + // 메인 제목
                "    <p>Thread-per-Request 방식의 HTTP 서버입니다.</p>\n" + // 서버 설명
                "    <h2>사용 가능한 엔드포인트:</h2>\n" + // 엔드포인트 목록 제목
                "    <div class=\"endpoint\"><strong>GET /</strong> - 이 홈 페이지</div>\n" + // 루트 엔드포인트 설명
                "    <div class=\"endpoint\"><strong>GET /hello</strong> - 인사말</div>\n" + // hello 엔드포인트 설명
                "    <div class=\"endpoint\"><strong>POST /echo</strong> - 에코 서비스</div>\n" + // echo 엔드포인트 설명
                "    <div class=\"endpoint\"><strong>GET /stats</strong> - 서버 통계</div>\n" + // stats 엔드포인트 설명
                "    <hr>\n" + // 구분선
                "    <p><em>JavaServerArchitectures - Traditional Thread-per-Request Server</em></p>\n" + // 서버 식별 정보
                "</body>\n" + // HTML 바디 종료
                "</html>"; // HTML 문서 종료

        return HttpResponse.html(html); // HttpResponse.html() - HTML 응답 생성하여 반환
    }

    /**
     * Hello 페이지 핸들러
     * 간단한 인사말과 현재 시간 표시
     */
    private HttpResponse handleHello(HttpRequest request) { // private 메서드 - Hello 페이지 처리 핸들러
        String name = request.getParameter("name"); // HttpRequest.getParameter() - 쿼리 스트링에서 name 파라미터 조회
        if (name == null) { // null 체크 - name 파라미터가 없는 경우
            name = "World"; // 기본값 설정 - "World"로 설정
        }

        String html = String.format( // String.format() - 문자열 템플릿 사용
                "<!DOCTYPE html>\n" + // HTML5 문서 타입
                        "<html>\n" + // HTML 루트 요소
                        "<head><title>Hello</title></head>\n" + // 헤드 섹션 - 페이지 제목
                        "<body>\n" + // 바디 시작
                        "    <h1>Hello, %s!</h1>\n" + // %s - 문자열 플레이스홀더, name 값이 들어감
                        "    <p>현재 시간: %s</p>\n" + // %s - 현재 시간이 들어갈 플레이스홀더
                        "    <p>처리 스레드: %s</p>\n" + // %s - 스레드 이름이 들어갈 플레이스홀더
                        "    <p><a href=\"/\">홈으로 돌아가기</a></p>\n" + // 링크 - 홈 페이지로 이동
                        "</body>\n" + // 바디 종료
                        "</html>", // HTML 종료
                escapeHtml(name), // escapeHtml() - XSS 공격 방지를 위한 HTML 이스케이프 처리
                new java.util.Date(), // new Date() - 현재 날짜/시간 객체 생성
                Thread.currentThread().getName() // Thread.currentThread() - 현재 스레드 객체 조회, getName() - 스레드 이름 조회
        );

        return HttpResponse.html(html); // HTML 응답 반환
    }

    /**
     * Echo 서비스 핸들러
     * 요청 바디를 그대로 응답으로 반환
     */
    private HttpResponse handleEcho(HttpRequest request) { // private 메서드 - Echo 서비스 처리 핸들러
        if (!HttpMethod.POST.equals(request.getMethod())) { // String.equals() - 문자열 동등성 비교, POST 메서드가 아닌 경우
            // POST가 아닌 경우 405 Method Not Allowed
            HttpResponse response = new HttpResponse(HttpStatus.METHOD_NOT_ALLOWED); // HttpStatus.METHOD_NOT_ALLOWED - 405 상태 코드
            response.setHeader("Allow", "POST"); // Allow 헤더 - 허용되는 HTTP 메서드 명시
            response.setBody("이 엔드포인트는 POST 메서드만 지원합니다"); // 에러 메시지 설정
            return response; // 405 에러 응답 반환
        }

        // 요청 바디를 그대로 응답으로 반환
        String requestBody = request.getBodyAsString(); // HttpRequest.getBodyAsString() - 요청 바디를 문자열로 조회
        String responseBody = "에코 응답:\n" + requestBody; // 문자열 연결 - 에코 응답 메시지와 원본 바디 결합

        return HttpResponse.text(responseBody); // HttpResponse.text() - 텍스트 응답 생성하여 반환
    }

    /**
     * 서버 통계 핸들러
     * 현재 서버 상태와 JVM 정보 표시
     */
    private HttpResponse handleStats(HttpRequest request) { // private 메서드 - 서버 통계 처리 핸들러
        // JVM 메모리 정보
        Runtime runtime = Runtime.getRuntime(); // Runtime.getRuntime() - 현재 JVM 런타임 객체 조회
        long totalMemory = runtime.totalMemory(); // Runtime.totalMemory() - JVM이 사용할 수 있는 총 메모리
        long freeMemory = runtime.freeMemory(); // Runtime.freeMemory() - JVM의 여유 메모리
        long usedMemory = totalMemory - freeMemory; // 산술 연산 - 사용 중인 메모리 = 총 메모리 - 여유 메모리

        // 스레드 정보
        int activeThreads = Thread.activeCount(); // Thread.activeCount() - 현재 활성 스레드 수 조회

        String html = String.format( // String.format() - 복잡한 HTML 템플릿 처리
                "<!DOCTYPE html>\n" + // HTML5 문서 타입
                        "<html>\n" + // HTML 루트 요소
                        "<head>\n" + // 헤드 섹션 시작
                        "    <title>서버 통계</title>\n" + // 페이지 제목
                        "    <style>\n" + // CSS 스타일 시작
                        "        body { font-family: Arial, sans-serif; margin: 40px; }\n" + // body 스타일
                        "        .stat { background: #f5f5f5; padding: 10px; margin: 5px 0; border-radius: 4px; }\n" + // stat 클래스 스타일
                        "    </style>\n" + // CSS 스타일 종료
                        "</head>\n" + // 헤드 섹션 종료
                        "<body>\n" + // 바디 시작
                        "    <h1>ThreadedServer 통계</h1>\n" + // 메인 제목
                        "    \n" + // 빈 줄
                        "    <h2>JVM 메모리</h2>\n" + // 메모리 섹션 제목
                        "    <div class=\"stat\">총 메모리: %.2f MB</div>\n" + // %.2f - 소수점 둘째 자리까지 포맷
                        "    <div class=\"stat\">사용 메모리: %.2f MB</div>\n" + // 사용 메모리 표시
                        "    <div class=\"stat\">여유 메모리: %.2f MB</div>\n" + // 여유 메모리 표시
                        "    \n" + // 빈 줄
                        "    <h2>스레드 정보</h2>\n" + // 스레드 섹션 제목
                        "    <div class=\"stat\">활성 스레드 수: %d</div>\n" + // %d - 정수 플레이스홀더
                        "    <div class=\"stat\">현재 처리 스레드: %s</div>\n" + // %s - 문자열 플레이스홀더
                        "    \n" + // 빈 줄
                        "    <h2>시스템 정보</h2>\n" + // 시스템 섹션 제목
                        "    <div class=\"stat\">프로세서 수: %d</div>\n" + // 프로세서 수 표시
                        "    <div class=\"stat\">현재 시간: %s</div>\n" + // 현재 시간 표시
                        "    \n" + // 빈 줄
                        "    <p><a href=\"/\">홈으로 돌아가기</a></p>\n" + // 홈 링크
                        "</body>\n" + // 바디 종료
                        "</html>", // HTML 종료
                totalMemory / 1024.0 / 1024.0, // 바이트를 MB로 변환 - 1024로 두 번 나누기
                usedMemory / 1024.0 / 1024.0, // 사용 메모리를 MB로 변환
                freeMemory / 1024.0 / 1024.0, // 여유 메모리를 MB로 변환
                activeThreads, // 활성 스레드 수
                Thread.currentThread().getName(), // 현재 스레드 이름
                runtime.availableProcessors(), // Runtime.availableProcessors() - 사용 가능한 프로세서 수
                new java.util.Date() // 현재 날짜/시간
        );

        return HttpResponse.html(html); // HTML 응답 반환
    }

    /**
     * HTTP 응답 전송
     * HttpResponse 객체를 HTTP 프로토콜 형식으로 직렬화하여 전송
     *
     * @param outputStream 클라이언트로의 출력 스트림
     * @param response 전송할 HTTP 응답
     * @throws IOException 전송 실패 시
     */
    public void sendResponse(OutputStream outputStream, HttpResponse response) throws IOException { // public 메서드 - 응답 전송
        try { // try-catch 블록 - 전송 중 예외 처리
            // HTTP 응답을 바이트 배열로 직렬화
            byte[] responseBytes = buildHttpResponse(response); // buildHttpResponse() - HttpResponse를 HTTP 프로토콜 형식의 바이트 배열로 변환

            // 클라이언트에게 전송
            outputStream.write(responseBytes); // OutputStream.write() - 바이트 배열을 스트림에 쓰기
            outputStream.flush(); // OutputStream.flush() - 버퍼에 있는 데이터를 즉시 전송

            // 전송 완료 로그
            logger.fine(String.format("응답 전송 완료: %d bytes, 상태: %s", // 전송 완료 로그
                    responseBytes.length, response.getStatus())); // responseBytes.length - 전송된 바이트 수

        } catch (IOException e) { // IOException 예외 처리
            logger.log(Level.WARNING, "HTTP 응답 전송 실패", e); // 전송 실패 로그
            throw e; // 예외 재던지기 - 호출자에게 전파
        }
    }

    /**
     * HttpResponse 객체를 HTTP 프로토콜 형식의 바이트 배열로 변환
     *
     * @param response 직렬화할 HTTP 응답
     * @return HTTP 프로토콜 형식의 바이트 배열
     * @throws IOException 직렬화 실패 시
     */
    private byte[] buildHttpResponse(HttpResponse response) throws IOException { // private 메서드 - HTTP 응답 직렬화
        StringBuilder responseBuilder = new StringBuilder(); // StringBuilder 생성 - 효율적인 문자열 조합을 위해

        // 1. 상태 라인 (HTTP/1.1 200 OK)
        responseBuilder.append(HTTP_VERSION) // HTTP_VERSION 상수 추가
                .append(" ") // 공백 문자 추가
                .append(response.getStatus().getCode()) // HttpResponse.getStatus().getCode() - 상태 코드 숫자 조회
                .append(" ") // 공백 문자 추가
                .append(response.getStatus().getReasonPhrase()) // getReasonPhrase() - 상태 메시지 조회 (OK, Not Found 등)
                .append(CRLF); // CRLF 상수 추가 - HTTP 줄바꿈

        // 2. 헤더들 (Content-Type: text/html)
        HttpHeaders headers = response.getHeaders(); // HttpResponse.getHeaders() - 헤더 객체 조회

        // 헤더가 없는 경우를 대비해 기본 헤더 설정
        if (headers.getContentType() == null) { // HttpHeaders.getContentType() - Content-Type 헤더 조회
            headers.setContentType("text/html; charset=UTF-8"); // 기본 Content-Type 설정
        }

        // Content-Length 자동 설정
        headers.setContentLength(response.getBody().length); // response.getBody().length - 바디 바이트 배열 길이

        // Connection 헤더 설정 (Keep-Alive 미지원으로 close)
        headers.set("Connection", "close"); // Connection 헤더 - 연결 종료 지시

        // 헤더들을 문자열로 변환
        for (String name : headers.getNames()) { // for-each 루프 - 모든 헤더 이름 순회
            for (String value : headers.get(name)) { // headers.get() - 해당 이름의 모든 헤더 값 조회
                responseBuilder.append(name) // 헤더 이름 추가
                        .append(": ") // 콜론과 공백 추가
                        .append(value) // 헤더 값 추가
                        .append(CRLF); // 줄바꿈 추가
            }
        }

        // 3. 헤더와 바디 구분자 (빈 라인)
        responseBuilder.append(CRLF); // 빈 줄 추가 - 헤더와 바디 구분

        // 헤더 부분을 바이트 배열로 변환
        byte[] headerBytes = responseBuilder.toString().getBytes(StandardCharsets.US_ASCII); // toString() - StringBuilder를 String으로 변환, getBytes() - 문자열을 바이트 배열로 변환

        // 4. 바디 바이트 배열 가져오기
        byte[] bodyBytes = response.getBody(); // HttpResponse.getBody() - 응답 바디 바이트 배열 조회

        // 5. 헤더와 바디 합치기
        byte[] fullResponse = new byte[headerBytes.length + bodyBytes.length]; // 전체 응답 크기의 바이트 배열 생성
        System.arraycopy(headerBytes, 0, fullResponse, 0, headerBytes.length); // System.arraycopy() - 배열 복사, 헤더를 전체 배열 앞부분에 복사
        System.arraycopy(bodyBytes, 0, fullResponse, headerBytes.length, bodyBytes.length); // 바디를 전체 배열 뒷부분에 복사

        return fullResponse; // 완성된 HTTP 응답 바이트 배열 반환
    }

    /**
     * HTML 이스케이프 처리
     * XSS 공격 방지를 위한 보안 조치
     *
     * @param text 이스케이프할 텍스트
     * @return HTML 이스케이프된 텍스트
     */
    private String escapeHtml(String text) { // private 메서드 - HTML 이스케이프 유틸리티
        if (text == null) return ""; // null 체크 - null이면 빈 문자열 반환

        return text.replace("&", "&amp;") // String.replace() - 문자열 치환, & -> &amp; (가장 먼저 처리)
                .replace("<", "&lt;") // < -> &lt; (HTML 태그 시작 문자)
                .replace(">", "&gt;") // > -> &gt; (HTML 태그 종료 문자)
                .replace("\"", "&quot;") // " -> &quot; (속성 값 구분 문자)
                .replace("'", "&#39;"); // ' -> &#39; (속성 값 구분 문자)
        // 메서드 체이닝 - 각 replace가 새 String을 반환하므로 연속 호출 가능
    }

    // ========== 내부 클래스: RequestLine ==========

    /**
     * HTTP 요청 라인 정보를 담는 클래스
     * "GET /path?query HTTP/1.1" 파싱 결과
     */
    private static class RequestLine { // private static 내부 클래스 - 외부 클래스의 인스턴스 없이 사용 가능
        final String method;        // final 필드 - HTTP 메서드 (String 타입으로 호환성 유지)
        final String path;          // final 필드 - 요청 경로
        final String queryString;   // final 필드 - 쿼리 스트링 (nullable)
        final String version;       // final 필드 - HTTP 버전

        RequestLine(String method, String path, String queryString, String version) { // 패키지 전용 생성자 - 접근 제한자 없음
            this.method = method; // this.method - 현재 객체의 method 필드에 매개변수 값 할당
            this.path = path; // this.path - 현재 객체의 path 필드에 매개변수 값 할당
            this.queryString = queryString; // this.queryString - 현재 객체의 queryString 필드에 매개변수 값 할당
            this.version = version; // this.version - 현재 객체의 version 필드에 매개변수 값 할당
        }
    }
}