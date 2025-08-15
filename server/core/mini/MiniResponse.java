package server.core.mini;

import server.core.http.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HTTP 응답 래퍼
 * HttpResponse를 서블릿 친화적 인터페이스로 래핑
 */
public class MiniResponse {

    private HttpStatus status;
    private final HttpHeaders headers;
    private final ByteArrayOutputStream bodyStream;
    private PrintWriter writer;
    private boolean committed;
    private String characterEncoding;

    public MiniResponse() {
        this.status = HttpStatus.OK;
        this.headers = new HttpHeaders();
        this.bodyStream = new ByteArrayOutputStream();
        this.committed = false;
        this.characterEncoding = StandardCharsets.UTF_8.name();

        // 기본 헤더 설정
        setDefaultHeaders();
    }

    // === 상태 코드 관리 ===

    /**
     * HTTP 상태 코드 설정
     */
    public void setStatus(HttpStatus status) {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }
        this.status = Objects.requireNonNull(status, "Status cannot be null");
    }

    /**
     * HTTP 상태 코드 설정 (정수)
     */
    public void setStatus(int statusCode) {
        setStatus(HttpStatus.fromCode(statusCode));
    }

    /**
     * 현재 상태 코드 반환
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * 현재 상태 코드 반환 (정수)
     */
    public int getStatusCode() {
        return status.getCode();
    }

    // === 헤더 관리 ===

    /**
     * 헤더 설정 (기존 값 덮어쓰기)
     */
    public void setHeader(String name, String value) {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }
        headers.set(name, value);
    }

    /**
     * 헤더 추가 (기존 값에 추가)
     */
    public void addHeader(String name, String value) {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }
        headers.add(name, value);
    }

    /**
     * 헤더 값 가져오기
     */
    public String getHeader(String name) {
        return headers.get(name);
    }

    /**
     * 헤더의 모든 값 가져오기
     */
    public List<String> getHeaders(String name) {
        return headers.getAll(name);
    }

    /**
     * 모든 헤더명 가져오기
     */
    public Set<String> getHeaderNames() {
        return headers.getHeaderNames();
    }

    /**
     * 헤더 존재 여부 확인
     */
    public boolean containsHeader(String name) {
        return headers.contains(name);
    }

    // === Content-Type 관리 ===

    /**
     * Content-Type 설정
     */
    public void setContentType(String contentType) {
        setHeader("Content-Type", contentType);
    }

    /**
     * Content-Type 가져오기
     */
    public String getContentType() {
        return getHeader("Content-Type");
    }

    /**
     * 문자 인코딩 설정
     */
    public void setCharacterEncoding(String encoding) {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }
        this.characterEncoding = encoding;

        // Content-Type 헤더 업데이트
        String contentType = getContentType();
        if (contentType != null && contentType.startsWith("text/")) {
            if (contentType.contains("charset=")) {
                contentType = contentType.replaceAll("charset=[^;]+", "charset=" + encoding);
            } else {
                contentType += "; charset=" + encoding;
            }
            setContentType(contentType);
        }
    }

    /**
     * 문자 인코딩 가져오기
     */
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    // === 쿠키 관리 ===

    /**
     * 기본 쿠키 추가
     */
    public void addCookie(String name, String value) {
        addHeader("Set-Cookie", name + "=" + value);
    }

    /**
     * 쿠키 옵션과 함께 추가
     */
    public void addCookie(String name, String value, int maxAge) {
        addHeader("Set-Cookie", name + "=" + value + "; Max-Age=" + maxAge);
    }

    /**
     * 쿠키 옵션과 함께 추가 (상세)
     */
    public void addCookie(String name, String value, int maxAge, String path, String domain, boolean secure, boolean httpOnly) {
        StringBuilder cookie = new StringBuilder();
        cookie.append(name).append("=").append(value);

        if (maxAge >= 0) {
            cookie.append("; Max-Age=").append(maxAge);
        }
        if (path != null) {
            cookie.append("; Path=").append(path);
        }
        if (domain != null) {
            cookie.append("; Domain=").append(domain);
        }
        if (secure) {
            cookie.append("; Secure");
        }
        if (httpOnly) {
            cookie.append("; HttpOnly");
        }

        addHeader("Set-Cookie", cookie.toString());
    }

    // === 리다이렉트 ===

    /**
     * 302 Found 리다이렉트
     */
    public void sendRedirect(String location) {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }

        setStatus(HttpStatus.FOUND);
        setHeader("Location", location);
        clearBody();
        commit();
    }

    /**
     * 301 Moved Permanently 리다이렉트
     */
    public void sendPermanentRedirect(String location) {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }

        setStatus(HttpStatus.MOVED_PERMANENTLY);
        setHeader("Location", location);
        clearBody();
        commit();
    }

    // === 에러 응답 ===

    /**
     * 에러 응답 전송
     */
    public void sendError(HttpStatus status) {
        sendError(status, status.getReasonPhrase());
    }

    /**
     * 에러 응답 전송 (메시지 포함)
     */
    public void sendError(HttpStatus status, String message) {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }

        setStatus(status);
        clearBody();

        // 기본 HTML 에러 페이지 생성
        String errorPage = generateErrorPage(status, message);
        setContentType("text/html; charset=" + characterEncoding);
        writeBody(errorPage);
        commit();
    }

    /**
     * 에러 응답 전송 (정수 코드)
     */
    public void sendError(int statusCode) {
        sendError(HttpStatus.fromCode(statusCode));
    }

    /**
     * 에러 응답 전송 (정수 코드 + 메시지)
     */
    public void sendError(int statusCode, String message) {
        sendError(HttpStatus.fromCode(statusCode), message);
    }

    // === Body 작성 ===

    /**
     * PrintWriter 가져오기 (텍스트 출력용)
     */
    public PrintWriter getWriter() {
        if (writer == null) {
            writer = new PrintWriter(new StringWriter() {
                @Override
                public void write(String str) {
                    writeBody(str);
                }

                @Override
                public void write(char[] cbuf, int off, int len) {
                    writeBody(new String(cbuf, off, len));
                }
            });
        }
        return writer;
    }

    /**
     * 문자열을 body에 작성
     */
    public void writeBody(String content) {
        if (content != null) {
            try {
                bodyStream.write(content.getBytes(characterEncoding));
            } catch (IOException e) {
                throw new RuntimeException("Error writing response body", e);
            }
        }
    }

    /**
     * 바이트 배열을 body에 작성
     */
    public void writeBody(byte[] content) {
        if (content != null) {
            try {
                bodyStream.write(content);
            } catch (IOException e) {
                throw new RuntimeException("Error writing response body", e);
            }
        }
    }

    /**
     * Body 내용 가져오기
     */
    public byte[] getBodyBytes() {
        return bodyStream.toByteArray();
    }

    /**
     * Body 내용을 문자열로 가져오기
     */
    public String getBodyAsString() {
        try {
            return bodyStream.toString(characterEncoding);
        } catch (Exception e) {
            return bodyStream.toString();
        }
    }

    /**
     * Body 크기 가져오기
     */
    public int getBodySize() {
        return bodyStream.size();
    }

    /**
     * Body 내용 지우기
     */
    public void clearBody() {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }
        bodyStream.reset();
        if (writer != null) {
            writer = null;
        }
    }

    // === 응답 상태 관리 ===

    /**
     * 응답 커밋 (더 이상 변경 불가)
     */
    public void commit() {
        if (!committed) {
            // Content-Length 설정
            if (!headers.contains("Content-Length")) {
                headers.setContentLength(bodyStream.size());
            }

            committed = true;
        }
    }

    /**
     * 응답이 커밋되었는지 확인
     */
    public boolean isCommitted() {
        return committed;
    }

    /**
     * 응답 초기화 (커밋 전에만 가능)
     */
    public void reset() {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }

        status = HttpStatus.OK;
        headers.clear();
        clearBody();
        setDefaultHeaders();
    }

    // === 편의 메서드 ===

    /**
     * JSON 응답 설정
     */
    public void sendJson(String jsonContent) {
        setContentType("application/json; charset=" + characterEncoding);
        writeBody(jsonContent);
    }

    /**
     * HTML 응답 설정
     */
    public void sendHtml(String htmlContent) {
        setContentType("text/html; charset=" + characterEncoding);
        writeBody(htmlContent);
    }

    /**
     * 텍스트 응답 설정
     */
    public void sendText(String textContent) {
        setContentType("text/plain; charset=" + characterEncoding);
        writeBody(textContent);
    }

    /**
     * 파일 다운로드 응답 설정
     */
    public void sendFile(String filename, byte[] fileContent, String mimeType) {
        setContentType(mimeType);
        setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        writeBody(fileContent);
    }

    // === HttpResponse 빌드 ===

    /**
     * HttpResponse 객체로 변환
     */
    public HttpResponse build() {
        commit(); // 응답 완료 처리

        return new HttpResponse(status, headers, getBodyBytes());
    }

    // === 내부 유틸리티 ===

    /**
     * 기본 헤더 설정
     */
    private void setDefaultHeaders() {
        // Server 헤더
        if (!headers.contains("Server")) {
            headers.set("Server", "JavaServerArchitectures/1.0");
        }

        // Connection 헤더
        if (!headers.contains("Connection")) {
            headers.set("Connection", "keep-alive");
        }
    }

    /**
     * 에러 페이지 HTML 생성
     */
    private String generateErrorPage(HttpStatus status, String message) {
        return String.format(
                "<!DOCTYPE html>\n" +
                        "<html>\n" +
                        "<head>\n" +
                        "    <title>%d %s</title>\n" +
                        "    <style>\n" +
                        "        body { font-family: Arial, sans-serif; margin: 50px; }\n" +
                        "        .error { color: #d32f2f; }\n" +
                        "        .code { font-size: 2em; font-weight: bold; }\n" +
                        "    </style>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "    <div class=\"error\">\n" +
                        "        <div class=\"code\">%d</div>\n" +
                        "        <h1>%s</h1>\n" +
                        "        <p>%s</p>\n" +
                        "    </div>\n" +
                        "    <hr>\n" +
                        "    <small>JavaServerArchitectures/1.0</small>\n" +
                        "</body>\n" +
                        "</html>",
                status.getCode(), status.getReasonPhrase(),
                status.getCode(), status.getReasonPhrase(),
                message != null ? message : status.getReasonPhrase()
        );
    }

    @Override
    public String toString() {
        return String.format("MiniResponse{status=%s, bodySize=%d, committed=%s}",
                status, getBodySize(), committed);
    }
}