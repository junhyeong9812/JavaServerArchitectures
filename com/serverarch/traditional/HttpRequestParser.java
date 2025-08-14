package com.serverarch.traditional;

import com.serverarch.common.http.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;

/**
 * HTTP 요청 파싱 전담 클래스
 * InputStream에서 HTTP 요청을 파싱하여 HttpRequest 객체로 변환
 */
public class HttpRequestParser {
    private static final Logger logger = Logger.getLogger(HttpRequestParser.class.getName());

    // HTTP 파싱 관련 상수들
    private static final String HTTP_VERSION_1_1 = "HTTP/1.1";
    private static final String HTTP_VERSION_1_0 = "HTTP/1.0";
    private static final int MAX_REQUEST_LINE_LENGTH = 8192; // 8KB
    private static final int MAX_HEADER_SIZE = 65536; // 64KB
    private static final int MAX_BODY_SIZE = 10485760; // 10MB

    /**
     * InputStream에서 HTTP 요청을 파싱
     * @param inputStream 클라이언트 소켓의 입력 스트림
     * @return 파싱된 HttpRequest 객체
     * @throws IOException 파싱 실패 시
     */
    public static HttpRequest parseRequest(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

        try {
            // 1. 요청 라인 파싱 (GET /path HTTP/1.1)
            String requestLine = readRequestLine(reader);
            if (requestLine == null || requestLine.trim().isEmpty()) {
                throw new IOException("빈 요청 라인");
            }

            RequestLineInfo requestInfo = parseRequestLine(requestLine);

            // 2. 헤더들 파싱
            HttpHeaders headers = parseHeaders(reader);

            // 3. 바디 파싱 (있는 경우)
            byte[] body = parseBody(reader, headers);

            // 4. HttpRequest 객체 생성
            HttpRequest request = new HttpRequest(
                    requestInfo.method,
                    requestInfo.path,
                    requestInfo.version,
                    headers,
                    body
            );

            // 5. 쿼리 파라미터 파싱 및 설정
            parseAndSetQueryParameters(request, requestInfo.queryString);

            logger.fine(String.format("요청 파싱 완료: %s %s",
                    requestInfo.method, requestInfo.path));

            return request;

        } catch (Exception e) {
            logger.log(Level.WARNING, "HTTP 요청 파싱 실패", e);
            throw new IOException("요청 파싱 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 요청 라인 읽기
     */
    private static String readRequestLine(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line != null && line.length() > MAX_REQUEST_LINE_LENGTH) {
            throw new IOException("요청 라인이 너무 깁니다: " + line.length());
        }
        return line;
    }

    /**
     * 요청 라인 파싱 (GET /path?query HTTP/1.1)
     */
    private static RequestLineInfo parseRequestLine(String requestLine) throws IOException {
        String[] parts = requestLine.split("\\s+");
        if (parts.length != 3) {
            throw new IOException("잘못된 요청 라인 형식: " + requestLine);
        }

        String method = parts[0].toUpperCase();
        String pathAndQuery = parts[1];
        String version = parts[2];

        // HTTP 버전 검증
        if (!HTTP_VERSION_1_1.equals(version) && !HTTP_VERSION_1_0.equals(version)) {
            throw new IOException("지원하지 않는 HTTP 버전: " + version);
        }

        // 경로와 쿼리 스트링 분리
        String path;
        String queryString = "";

        int queryIndex = pathAndQuery.indexOf('?');
        if (queryIndex >= 0) {
            path = pathAndQuery.substring(0, queryIndex);
            queryString = pathAndQuery.substring(queryIndex + 1);
        } else {
            path = pathAndQuery;
        }

        // URL 디코딩
        path = urlDecode(path);

        return new RequestLineInfo(method, path, queryString, version);
    }

    /**
     * HTTP 헤더들 파싱
     */
    private static HttpHeaders parseHeaders(BufferedReader reader) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        String line;
        int totalHeaderSize = 0;

        while ((line = reader.readLine()) != null) {
            // 빈 줄이면 헤더 종료
            if (line.trim().isEmpty()) {
                break;
            }

            totalHeaderSize += line.length();
            if (totalHeaderSize > MAX_HEADER_SIZE) {
                throw new IOException("헤더 크기가 너무 큽니다");
            }

            // 헤더 라인 파싱 (Name: Value)
            int colonIndex = line.indexOf(':');
            if (colonIndex <= 0) {
                logger.warning("잘못된 헤더 형식 무시: " + line);
                continue;
            }

            String name = line.substring(0, colonIndex).trim();
            String value = line.substring(colonIndex + 1).trim();

            headers.add(name, value);
        }

        return headers;
    }

    /**
     * 요청 바디 파싱
     */
    private static byte[] parseBody(BufferedReader reader, HttpHeaders headers) throws IOException {
        // Content-Length 확인
        String contentLengthStr = headers.getFirst("Content-Length");
        if (contentLengthStr == null) {
            return new byte[0]; // 바디 없음
        }

        int contentLength;
        try {
            contentLength = Integer.parseInt(contentLengthStr);
        } catch (NumberFormatException e) {
            throw new IOException("잘못된 Content-Length: " + contentLengthStr);
        }

        if (contentLength < 0) {
            throw new IOException("음수 Content-Length: " + contentLength);
        }

        if (contentLength > MAX_BODY_SIZE) {
            throw new IOException("요청 바디가 너무 큽니다: " + contentLength);
        }

        if (contentLength == 0) {
            return new byte[0];
        }

        // 바디 읽기
        char[] buffer = new char[contentLength];
        int totalRead = 0;

        while (totalRead < contentLength) {
            int read = reader.read(buffer, totalRead, contentLength - totalRead);
            if (read == -1) {
                throw new IOException("예상보다 적은 바디 데이터");
            }
            totalRead += read;
        }

        return new String(buffer).getBytes("UTF-8");
    }

    /**
     * 쿼리 파라미터 파싱 및 설정
     */
    private static void parseAndSetQueryParameters(HttpRequest request, String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return;
        }

        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            int equalsIndex = pair.indexOf('=');
            String key, value;

            if (equalsIndex >= 0) {
                key = urlDecode(pair.substring(0, equalsIndex));
                value = urlDecode(pair.substring(equalsIndex + 1));
            } else {
                key = urlDecode(pair);
                value = "";
            }

            request.addQueryParameter(key, value);
        }
    }

    /**
     * 간단한 URL 디코딩
     */
    private static String urlDecode(String encoded) {
        try {
            return java.net.URLDecoder.decode(encoded, "UTF-8");
        } catch (Exception e) {
            logger.warning("URL 디코딩 실패: " + encoded);
            return encoded; // 디코딩 실패 시 원본 반환
        }
    }

    /**
     * 요청 라인 정보를 담는 내부 클래스
     */
    private static class RequestLineInfo {
        final String method;
        final String path;
        final String queryString;
        final String version;

        RequestLineInfo(String method, String path, String queryString, String version) {
            this.method = method;
            this.path = path;
            this.queryString = queryString;
            this.version = version;
        }
    }
}