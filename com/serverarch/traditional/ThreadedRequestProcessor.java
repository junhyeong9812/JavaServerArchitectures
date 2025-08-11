package com.serverarch.traditional;

import com.serverarch.common.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.*;

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
public class ThreadedRequestProcessor {

    // 로거 - 요청 처리 과정 추적용
    private static final Logger logger = Logger.getLogger(ThreadedRequestProcessor.class.getName());

    // HTTP 프로토콜 상수들
    private static final String HTTP_VERSION = "HTTP/1.1";          // 지원하는 HTTP 버전
    private static final String CRLF = "\r\n";                      // HTTP 줄바꿈 문자
    private static final int MAX_REQUEST_LINE_LENGTH = 8192;        // 요청 라인 최대 길이 (8KB)
    private static final int MAX_HEADER_SIZE = 8192;                // 헤더 최대 크기 (8KB)
    private static final int MAX_BODY_SIZE = 10 * 1024 * 1024;     // 바디 최대 크기 (10MB)

    /**
     * HTTP 요청 파싱
     * InputStream에서 HTTP 요청을 읽어 HttpRequest 객체로 변환
     *
     * @param inputStream 클라이언트로부터의 입력 스트림
     * @return 파싱된 HTTP 요청
     * @throws IOException 파싱 실패 또는 I/O 오류 시
     */
    public HttpRequest parseRequest(InputStream inputStream) throws IOException {
        // BufferedReader로 라인 단위 읽기 효율성 향상
        // US-ASCII로 읽는 이유: HTTP 헤더는 ASCII로 정의되어 있음
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.US_ASCII)
        );

        try {
            // 1. 요청 라인 파싱 (GET /path HTTP/1.1)
            RequestLine requestLine = parseRequestLine(reader);

            // 2. 헤더 파싱 (Host: example.com, Content-Type: text/html 등)
            HttpHeaders headers = parseHeaders(reader);

            // 3. 바디 파싱 (Content-Length에 따라)
            byte[] body = parseBody(inputStream, headers);

            // 4. HttpRequest 객체 생성
            HttpRequest request = new HttpRequest(
                    requestLine.method,      // HTTP 메서드
                    requestLine.path,        // 요청 경로
                    requestLine.queryString, // 쿼리 스트링
                    headers,                 // HTTP 헤더들
                    body                     // 요청 바디
            );

            // 파싱 성공 로그
            logger.fine(String.format("요청 파싱 완료: %s %s",
                    requestLine.method, requestLine.path));

            return request;

        } catch (IOException e) {
            // I/O 오류 - 네트워크 문제나 클라이언트 연결 끊김
            logger.log(Level.WARNING, "HTTP 요청 파싱 중 I/O 오류", e);
            throw e;

        } catch (Exception e) {
            // 기타 파싱 오류 - 잘못된 HTTP 형식 등
            logger.log(Level.WARNING, "HTTP 요청 파싱 실패", e);
            throw new IOException("잘못된 HTTP 요청 형식", e);
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
    private RequestLine parseRequestLine(BufferedReader reader) throws IOException {
        // 첫 번째 라인 읽기
        String line = reader.readLine();

        // 빈 라인이나 null 체크 - 잘못된 요청
        if (line == null || line.trim().isEmpty()) {
            throw new IOException("빈 요청 라인");
        }

        // 요청 라인 길이 제한 체크 - DoS 공격 방지
        if (line.length() > MAX_REQUEST_LINE_LENGTH) {
            throw new IOException("요청 라인이 너무 깁니다: " + line.length());
        }

        // 공백으로 분리 - "METHOD URI VERSION" 형태
        String[] parts = line.trim().split("\\s+");
        if (parts.length != 3) {
            throw new IOException("잘못된 요청 라인 형식: " + line);
        }

        // HTTP 메서드 파싱 및 검증
        String methodStr = parts[0].toUpperCase();
        String method; // String 타입으로 변경 - 기존 HttpMethod 상수와 호환
        if (!HttpMethod.isStandardMethod(methodStr)) {
            throw new IOException("지원하지 않는 HTTP 메서드: " + methodStr);
        }
        method = methodStr; // 검증된 메서드 문자열 사용

        // URI 파싱 - 경로와 쿼리 스트링 분리
        String uri = parts[1];
        String path;
        String queryString = null;

        // '?' 기준으로 경로와 쿼리 스트링 분리
        int queryIndex = uri.indexOf('?');
        if (queryIndex != -1) {
            path = uri.substring(0, queryIndex);           // ? 이전 부분이 경로
            queryString = uri.substring(queryIndex + 1);   // ? 이후 부분이 쿼리 스트링
        } else {
            path = uri;  // 쿼리 스트링이 없는 경우
        }

        // HTTP 버전 검증
        String version = parts[2];
        if (!version.startsWith("HTTP/")) {
            throw new IOException("잘못된 HTTP 버전: " + version);
        }

        return new RequestLine(method, path, queryString, version);
    }

    /**
     * HTTP 헤더들 파싱
     * "Header-Name: Header-Value" 형태의 라인들 파싱
     *
     * @param reader 입력 리더
     * @return 파싱된 HTTP 헤더들
     * @throws IOException 파싱 실패 시
     */
    private HttpHeaders parseHeaders(BufferedReader reader) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        String line;
        int totalHeaderSize = 0; // DoS 공격 방지를 위한 헤더 크기 제한

        // 빈 라인이 나올 때까지 헤더 읽기 (빈 라인은 헤더 섹션의 끝)
        while ((line = reader.readLine()) != null && !line.trim().isEmpty()) {

            // 헤더 크기 제한 체크 - 메모리 고갈 공격 방지
            totalHeaderSize += line.length();
            if (totalHeaderSize > MAX_HEADER_SIZE) {
                throw new IOException("헤더 크기가 너무 큽니다: " + totalHeaderSize);
            }

            // 헤더 라인 파싱 - "Name: Value" 형태
            int colonIndex = line.indexOf(':');
            if (colonIndex == -1) {
                // ':' 가 없으면 잘못된 헤더 형식
                throw new IOException("잘못된 헤더 형식: " + line);
            }

            // 헤더 이름과 값 추출
            String name = line.substring(0, colonIndex).trim();
            String value = line.substring(colonIndex + 1).trim();

            // 빈 헤더 이름 체크
            if (name.isEmpty()) {
                throw new IOException("빈 헤더 이름: " + line);
            }

            // 헤더 추가 - 대소문자 무관하게 처리
            headers.add(name, value);
        }

        return headers;
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
    private byte[] parseBody(InputStream inputStream, HttpHeaders headers) throws IOException {
        // Content-Length 헤더 확인
        String contentLengthStr = headers.getContentLength();
        if (contentLengthStr == null) {
            // Content-Length가 없으면 바디 없음으로 간주
            return new byte[0];
        }

        // Content-Length 파싱
        int contentLength;
        try {
            contentLength = Integer.parseInt(contentLengthStr);
        } catch (NumberFormatException e) {
            throw new IOException("잘못된 Content-Length: " + contentLengthStr);
        }

        // 음수 체크
        if (contentLength < 0) {
            throw new IOException("음수 Content-Length: " + contentLength);
        }

        // 바디 크기 제한 체크 - DoS 공격 방지
        if (contentLength > MAX_BODY_SIZE) {
            throw new IOException("요청 바디가 너무 큽니다: " + contentLength);
        }

        // 바디가 없으면 빈 배열 반환
        if (contentLength == 0) {
            return new byte[0];
        }

        // 바디 읽기
        byte[] body = new byte[contentLength];
        int totalRead = 0;

        // 지정된 크기만큼 모두 읽을 때까지 반복
        while (totalRead < contentLength) {
            int bytesRead = inputStream.read(body, totalRead, contentLength - totalRead);

            if (bytesRead == -1) {
                // 스트림 끝에 도달했는데 아직 다 읽지 못한 경우
                throw new IOException("요청 바디가 예상보다 짧습니다");
            }

            totalRead += bytesRead;
        }

        return body;
    }

    /**
     * HTTP 요청 처리
     * 실제 비즈니스 로직을 실행하여 응답 생성
     *
     * @param request HTTP 요청
     * @return HTTP 응답
     */
    public HttpResponse processRequest(HttpRequest request) {
        try {
            // 요청 처리 시작 로그
            logger.fine(String.format("요청 처리 시작: %s %s",
                    request.getMethod(), request.getPath()));

            // 간단한 라우팅 - 실제로는 더 정교한 라우팅 시스템 필요
            HttpResponse response = routeRequest(request);

            // 요청 처리 완료 로그
            logger.fine(String.format("요청 처리 완료: %s %s -> %s",
                    request.getMethod(), request.getPath(), response.getStatus()));

            return response;

        } catch (Exception e) {
            // 비즈니스 로직 실행 중 오류
            logger.log(Level.SEVERE, "요청 처리 중 오류 발생", e);

            // 500 Internal Server Error 응답
            return HttpResponse.serverError("요청 처리 중 오류가 발생했습니다");
        }
    }

    /**
     * 간단한 라우팅 시스템
     * 요청 경로에 따라 적절한 핸들러 호출
     *
     * @param request HTTP 요청
     * @return HTTP 응답
     */
    private HttpResponse routeRequest(HttpRequest request) {
        String path = request.getPath();
        String method = request.getMethod(); // String 타입으로 변경

        // 경로별 라우팅 - 실제로는 더 정교한 패턴 매칭 필요
        switch (path) {
            case "/":
                return handleHome(request);

            case "/hello":
                return handleHello(request);

            case "/echo":
                return handleEcho(request);

            case "/stats":
                return handleStats(request);

            default:
                // 등록되지 않은 경로 - 404 Not Found
                return HttpResponse.notFound("요청한 경로를 찾을 수 없습니다: " + path);
        }
    }

    /**
     * 홈 페이지 핸들러
     * 서버 정보와 사용 가능한 엔드포인트 안내
     */
    private HttpResponse handleHome(HttpRequest request) {
        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>ThreadedServer</title>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; margin: 40px; }\n" +
                "        h1 { color: #2196F3; }\n" +
                "        .endpoint { background: #f5f5f5; padding: 10px; margin: 10px 0; border-radius: 4px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>ThreadedServer</h1>\n" +
                "    <p>Thread-per-Request 방식의 HTTP 서버입니다.</p>\n" +
                "    <h2>사용 가능한 엔드포인트:</h2>\n" +
                "    <div class=\"endpoint\"><strong>GET /</strong> - 이 홈 페이지</div>\n" +
                "    <div class=\"endpoint\"><strong>GET /hello</strong> - 인사말</div>\n" +
                "    <div class=\"endpoint\"><strong>POST /echo</strong> - 에코 서비스</div>\n" +
                "    <div class=\"endpoint\"><strong>GET /stats</strong> - 서버 통계</div>\n" +
                "    <hr>\n" +
                "    <p><em>JavaServerArchitectures - Traditional Thread-per-Request Server</em></p>\n" +
                "</body>\n" +
                "</html>";

        return HttpResponse.html(html);
    }

    /**
     * Hello 페이지 핸들러
     * 간단한 인사말과 현재 시간 표시
     */
    private HttpResponse handleHello(HttpRequest request) {
        String name = request.getParameter("name");
        if (name == null) {
            name = "World";
        }

        String html = String.format(
                "<!DOCTYPE html>\n" +
                        "<html>\n" +
                        "<head><title>Hello</title></head>\n" +
                        "<body>\n" +
                        "    <h1>Hello, %s!</h1>\n" +
                        "    <p>현재 시간: %s</p>\n" +
                        "    <p>처리 스레드: %s</p>\n" +
                        "    <p><a href=\"/\">홈으로 돌아가기</a></p>\n" +
                        "</body>\n" +
                        "</html>",
                escapeHtml(name),
                new java.util.Date(),
                Thread.currentThread().getName()
        );

        return HttpResponse.html(html);
    }

    /**
     * Echo 서비스 핸들러
     * 요청 바디를 그대로 응답으로 반환
     */
    private HttpResponse handleEcho(HttpRequest request) {
        if (!HttpMethod.POST.equals(request.getMethod())) {
            // POST가 아닌 경우 405 Method Not Allowed
            HttpResponse response = new HttpResponse(HttpStatus.METHOD_NOT_ALLOWED);
            response.setHeader("Allow", "POST");
            response.setBody("이 엔드포인트는 POST 메서드만 지원합니다");
            return response;
        }

        // 요청 바디를 그대로 응답으로 반환
        String requestBody = request.getBodyAsString();
        String responseBody = "에코 응답:\n" + requestBody;

        return HttpResponse.text(responseBody);
    }

    /**
     * 서버 통계 핸들러
     * 현재 서버 상태와 JVM 정보 표시
     */
    private HttpResponse handleStats(HttpRequest request) {
        // JVM 메모리 정보
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        // 스레드 정보
        int activeThreads = Thread.activeCount();

        String html = String.format(
                "<!DOCTYPE html>\n" +
                        "<html>\n" +
                        "<head>\n" +
                        "    <title>서버 통계</title>\n" +
                        "    <style>\n" +
                        "        body { font-family: Arial, sans-serif; margin: 40px; }\n" +
                        "        .stat { background: #f5f5f5; padding: 10px; margin: 5px 0; border-radius: 4px; }\n" +
                        "    </style>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "    <h1>ThreadedServer 통계</h1>\n" +
                        "    \n" +
                        "    <h2>JVM 메모리</h2>\n" +
                        "    <div class=\"stat\">총 메모리: %.2f MB</div>\n" +
                        "    <div class=\"stat\">사용 메모리: %.2f MB</div>\n" +
                        "    <div class=\"stat\">여유 메모리: %.2f MB</div>\n" +
                        "    \n" +
                        "    <h2>스레드 정보</h2>\n" +
                        "    <div class=\"stat\">활성 스레드 수: %d</div>\n" +
                        "    <div class=\"stat\">현재 처리 스레드: %s</div>\n" +
                        "    \n" +
                        "    <h2>시스템 정보</h2>\n" +
                        "    <div class=\"stat\">프로세서 수: %d</div>\n" +
                        "    <div class=\"stat\">현재 시간: %s</div>\n" +
                        "    \n" +
                        "    <p><a href=\"/\">홈으로 돌아가기</a></p>\n" +
                        "</body>\n" +
                        "</html>",
                totalMemory / 1024.0 / 1024.0,
                usedMemory / 1024.0 / 1024.0,
                freeMemory / 1024.0 / 1024.0,
                activeThreads,
                Thread.currentThread().getName(),
                runtime.availableProcessors(),
                new java.util.Date()
        );

        return HttpResponse.html(html);
    }

    /**
     * HTTP 응답 전송
     * HttpResponse 객체를 HTTP 프로토콜 형식으로 직렬화하여 전송
     *
     * @param outputStream 클라이언트로의 출력 스트림
     * @param response 전송할 HTTP 응답
     * @throws IOException 전송 실패 시
     */
    public void sendResponse(OutputStream outputStream, HttpResponse response) throws IOException {
        try {
            // HTTP 응답을 바이트 배열로 직렬화
            byte[] responseBytes = buildHttpResponse(response);

            // 클라이언트에게 전송
            outputStream.write(responseBytes);
            outputStream.flush(); // 버퍼 플러시하여 즉시 전송

            // 전송 완료 로그
            logger.fine(String.format("응답 전송 완료: %d bytes, 상태: %s",
                    responseBytes.length, response.getStatus()));

        } catch (IOException e) {
            logger.log(Level.WARNING, "HTTP 응답 전송 실패", e);
            throw e;
        }
    }

    /**
     * HttpResponse 객체를 HTTP 프로토콜 형식의 바이트 배열로 변환
     *
     * @param response 직렬화할 HTTP 응답
     * @return HTTP 프로토콜 형식의 바이트 배열
     * @throws IOException 직렬화 실패 시
     */
    private byte[] buildHttpResponse(HttpResponse response) throws IOException {
        StringBuilder responseBuilder = new StringBuilder();

        // 1. 상태 라인 (HTTP/1.1 200 OK)
        responseBuilder.append(HTTP_VERSION)
                .append(" ")
                .append(response.getStatus().getCode())
                .append(" ")
                .append(response.getStatus().getReasonPhrase())
                .append(CRLF);

        // 2. 헤더들 (Content-Type: text/html)
        HttpHeaders headers = response.getHeaders();

        // 헤더가 없는 경우를 대비해 기본 헤더 설정
        if (headers.getContentType() == null) {
            headers.setContentType("text/html; charset=UTF-8");
        }

        // Content-Length 자동 설정
        headers.setContentLength(response.getBody().length);

        // Connection 헤더 설정 (Keep-Alive 미지원으로 close)
        headers.set("Connection", "close");

        // 헤더들을 문자열로 변환
        for (String name : headers.getNames()) {
            for (String value : headers.get(name)) {
                responseBuilder.append(name)
                        .append(": ")
                        .append(value)
                        .append(CRLF);
            }
        }

        // 3. 헤더와 바디 구분자 (빈 라인)
        responseBuilder.append(CRLF);

        // 헤더 부분을 바이트 배열로 변환
        byte[] headerBytes = responseBuilder.toString().getBytes(StandardCharsets.US_ASCII);

        // 4. 바디 바이트 배열 가져오기
        byte[] bodyBytes = response.getBody();

        // 5. 헤더와 바디 합치기
        byte[] fullResponse = new byte[headerBytes.length + bodyBytes.length];
        System.arraycopy(headerBytes, 0, fullResponse, 0, headerBytes.length);
        System.arraycopy(bodyBytes, 0, fullResponse, headerBytes.length, bodyBytes.length);

        return fullResponse;
    }

    /**
     * HTML 이스케이프 처리
     * XSS 공격 방지를 위한 보안 조치
     *
     * @param text 이스케이프할 텍스트
     * @return HTML 이스케이프된 텍스트
     */
    private String escapeHtml(String text) {
        if (text == null) return "";

        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // ========== 내부 클래스: RequestLine ==========

    /**
     * HTTP 요청 라인 정보를 담는 클래스
     * "GET /path?query HTTP/1.1" 파싱 결과
     */
    private static class RequestLine {
        final String method;        // HTTP 메서드 (String 타입으로 호환성 유지)
        final String path;          // 요청 경로
        final String queryString;   // 쿼리 스트링 (nullable)
        final String version;       // HTTP 버전

        RequestLine(String method, String path, String queryString, String version) {
            this.method = method;
            this.path = path;
            this.queryString = queryString;
            this.version = version;
        }
    }
}