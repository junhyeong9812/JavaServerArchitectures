package server.core.mini;

// HTTP 관련 클래스들
import server.core.http.*;
// I/O 관련 클래스들
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
// 문자 인코딩 관련
import java.nio.charset.StandardCharsets;
// 컬렉션 관련
import java.util.*;

/**
 * HTTP 응답 래퍼
 * HttpResponse를 서블릿 친화적 인터페이스로 래핑
 *
 * 역할:
 * - HttpResponse 생성을 위한 편리한 인터페이스 제공
 * - 서블릿 API 스타일의 응답 조작 기능
 * - 상태 관리 (committed 상태)
 * - 다양한 콘텐츠 타입 지원
 * - 에러 페이지 자동 생성
 */
public class MiniResponse {

    // HTTP 상태 코드
    // 기본값은 200 OK
    private HttpStatus status;

    // HTTP 헤더들을 관리하는 객체
    private final HttpHeaders headers;

    // 응답 본문을 저장하는 스트림
    // ByteArrayOutputStream: 메모리에 바이트 데이터를 쓸 수 있는 스트림
    private final ByteArrayOutputStream bodyStream;

    // 텍스트 출력을 위한 Writer
    // PrintWriter: 텍스트 데이터를 편리하게 출력할 수 있는 클래스
    private PrintWriter writer;

    // 응답이 커밋되었는지 여부
    // 커밋된 후에는 상태나 헤더를 변경할 수 없음
    private boolean committed;

    // 문자 인코딩 (기본값: UTF-8)
    private String characterEncoding;

    // 생성자
    public MiniResponse() {
        // 기본 상태 코드: 200 OK
        this.status = HttpStatus.OK;

        // 헤더 관리 객체 초기화
        this.headers = new HttpHeaders();

        // 본문 저장용 스트림 초기화
        this.bodyStream = new ByteArrayOutputStream();

        // 아직 커밋되지 않음
        this.committed = false;

        // 기본 문자 인코딩: UTF-8
        // StandardCharsets.UTF_8.name(): UTF-8 인코딩의 표준 이름
        this.characterEncoding = StandardCharsets.UTF_8.name();

        // 기본 헤더들 설정
        setDefaultHeaders();
    }

    // === 상태 코드 관리 ===

    /**
     * HTTP 상태 코드 설정
     */
    public void setStatus(HttpStatus status) {
        // 이미 커밋된 응답은 수정할 수 없음
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }

        // Objects.requireNonNull(): null 체크 후 예외 발생
        this.status = Objects.requireNonNull(status, "Status cannot be null");
    }

    /**
     * HTTP 상태 코드 설정 (정수)
     * 편의를 위해 정수 코드를 받아서 HttpStatus로 변환
     */
    public void setStatus(int statusCode) {
        // HttpStatus.fromCode(): 정수 코드를 HttpStatus 열거형으로 변환
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

        // HttpHeaders.set(): 헤더 설정 (기존 값 덮어쓰기)
        headers.set(name, value);
    }

    /**
     * 헤더 추가 (기존 값에 추가)
     * 같은 이름의 헤더가 여러 개 있을 수 있는 경우 사용 (예: Set-Cookie)
     */
    public void addHeader(String name, String value) {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }

        // HttpHeaders.add(): 헤더 추가 (기존 값 유지하면서 새 값 추가)
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

        // Content-Type 헤더 업데이트 (텍스트 타입인 경우)
        String contentType = getContentType();
        if (contentType != null && contentType.startsWith("text/")) {
            // 기존 charset 파라미터가 있는지 확인
            if (contentType.contains("charset=")) {
                // replaceAll(): 정규표현식으로 문자열 치환
                // "charset=[^;]+" : charset= 다음에 오는 세미콜론이 아닌 문자들
                contentType = contentType.replaceAll("charset=[^;]+", "charset=" + encoding);
            } else {
                // charset 파라미터가 없으면 추가
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
        // Set-Cookie 헤더로 쿠키 설정
        // "name=value" 형태의 간단한 쿠키
        addHeader("Set-Cookie", name + "=" + value);
    }

    /**
     * 쿠키 옵션과 함께 추가
     */
    public void addCookie(String name, String value, int maxAge) {
        // Max-Age 속성을 포함한 쿠키
        // Max-Age: 쿠키의 유효 시간(초)
        addHeader("Set-Cookie", name + "=" + value + "; Max-Age=" + maxAge);
    }

    /**
     * 쿠키 옵션과 함께 추가 (상세)
     * 모든 쿠키 속성을 지원하는 완전한 메서드
     */
    public void addCookie(String name, String value, int maxAge, String path, String domain, boolean secure, boolean httpOnly) {
        // StringBuilder: 효율적인 문자열 조합을 위한 클래스
        StringBuilder cookie = new StringBuilder();

        // 기본 name=value 부분
        cookie.append(name).append("=").append(value);

        // Max-Age 속성 (0 이상인 경우에만 추가)
        if (maxAge >= 0) {
            cookie.append("; Max-Age=").append(maxAge);
        }

        // Path 속성 (쿠키가 유효한 경로)
        if (path != null) {
            cookie.append("; Path=").append(path);
        }

        // Domain 속성 (쿠키가 유효한 도메인)
        if (domain != null) {
            cookie.append("; Domain=").append(domain);
        }

        // Secure 속성 (HTTPS에서만 전송)
        if (secure) {
            cookie.append("; Secure");
        }

        // HttpOnly 속성 (JavaScript에서 접근 불가, XSS 방지)
        if (httpOnly) {
            cookie.append("; HttpOnly");
        }

        // 완성된 쿠키 문자열을 헤더에 추가
        addHeader("Set-Cookie", cookie.toString());
    }

    // === 리다이렉트 ===

    /**
     * 302 Found 리다이렉트
     * 임시 리다이렉트 (브라우저가 지정된 URL로 이동)
     */
    public void sendRedirect(String location) {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }

        // 302 상태 코드 설정
        setStatus(HttpStatus.FOUND);

        // Location 헤더에 리다이렉트할 URL 설정
        setHeader("Location", location);

        // 기존 본문 내용 제거 (리다이렉트는 본문이 필요 없음)
        clearBody();

        // 응답 커밋 (더 이상 수정 불가)
        commit();
    }

    /**
     * 301 Moved Permanently 리다이렉트
     * 영구 리다이렉트 (SEO에서 중요)
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
        // 상태 코드의 기본 메시지 사용
        sendError(status, status.getReasonPhrase());
    }

    /**
     * 에러 응답 전송 (메시지 포함)
     */
    public void sendError(HttpStatus status, String message) {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }

        // 에러 상태 코드 설정
        setStatus(status);

        // 기존 본문 제거
        clearBody();

        // 기본 HTML 에러 페이지 생성
        String errorPage = generateErrorPage(status, message);

        // HTML 콘텐츠 타입 설정
        setContentType("text/html; charset=" + characterEncoding);

        // 에러 페이지 내용을 본문에 작성
        writeBody(errorPage);

        // 응답 커밋
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
     * 서블릿에서 텍스트 출력을 위해 사용하는 표준 방법
     */
    public PrintWriter getWriter() {
        if (writer == null) {
            // StringWriter를 익명 클래스로 확장하여 사용
            // write 메서드를 오버라이드하여 실제 본문에 쓰기
            writer = new PrintWriter(new StringWriter() {
                @Override
                public void write(String str) {
                    // 문자열을 본문에 추가
                    writeBody(str);
                }

                @Override
                public void write(char[] cbuf, int off, int len) {
                    // 문자 배열의 일부를 문자열로 변환하여 본문에 추가
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
                // 문자열을 지정된 인코딩으로 바이트 배열로 변환하여 스트림에 쓰기
                bodyStream.write(content.getBytes(characterEncoding));
            } catch (IOException e) {
                // ByteArrayOutputStream은 메모리 내 작업이므로 IOException이 거의 발생하지 않음
                // 하지만 인터페이스 제약으로 예외 처리 필요
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
                // 바이트 배열을 직접 스트림에 쓰기
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
        // ByteArrayOutputStream의 내용을 바이트 배열로 반환
        return bodyStream.toByteArray();
    }

    /**
     * Body 내용을 문자열로 가져오기
     */
    public String getBodyAsString() {
        try {
            // 지정된 문자 인코딩으로 바이트를 문자열로 변환
            return bodyStream.toString(characterEncoding);
        } catch (Exception e) {
            // 인코딩 실패 시 기본 인코딩 사용
            return bodyStream.toString();
        }
    }

    /**
     * Body 크기 가져오기
     */
    public int getBodySize() {
        // ByteArrayOutputStream의 현재 크기 반환
        return bodyStream.size();
    }

    /**
     * Body 내용 지우기
     */
    public void clearBody() {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }

        // 스트림 내용 초기화
        // reset(): ByteArrayOutputStream의 내용을 지우고 크기를 0으로 만듦
        bodyStream.reset();

        // PrintWriter도 초기화
        if (writer != null) {
            writer = null;
        }
    }

    // === 응답 상태 관리 ===

    /**
     * 응답 커밋 (더 이상 변경 불가)
     * 응답이 클라이언트로 전송되기 전에 호출됨
     */
    public void commit() {
        if (!committed) {
            // Content-Length 헤더가 없으면 자동으로 설정
            if (!headers.contains("Content-Length")) {
                headers.setContentLength(bodyStream.size());
            }

            // 커밋 상태로 변경
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

        // 모든 상태를 초기값으로 재설정
        status = HttpStatus.OK;
        headers.clear();      // 모든 헤더 제거
        clearBody();          // 본문 내용 제거
        setDefaultHeaders();  // 기본 헤더 다시 설정
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

        // Content-Disposition 헤더: 브라우저에게 파일 다운로드로 처리하도록 지시
        setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        writeBody(fileContent);
    }

    // === HttpResponse 빌드 ===

    /**
     * HttpResponse 객체로 변환
     * MiniResponse의 모든 설정을 HttpResponse 객체로 변환
     */
    public HttpResponse build() {
        // 응답 완료 처리
        commit();

        // HttpResponse 생성자를 통해 최종 응답 객체 생성
        return new HttpResponse(status, headers, getBodyBytes());
    }

    // === 내부 유틸리티 ===

    /**
     * 기본 헤더 설정
     */
    private void setDefaultHeaders() {
        // Server 헤더: 서버 소프트웨어 정보
        if (!headers.contains("Server")) {
            headers.set("Server", "JavaServerArchitectures/1.0");
        }

        // Connection 헤더: 연결 유지 방식
        if (!headers.contains("Connection")) {
            headers.set("Connection", "keep-alive");
        }
    }

    /**
     * 에러 페이지 HTML 생성
     * 기본적인 에러 페이지를 동적으로 생성
     */
    private String generateErrorPage(HttpStatus status, String message) {
        // String.format(): printf 스타일의 문자열 포맷팅
        // %d: 정수, %s: 문자열 형식 지정자
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
                status.getCode(), status.getReasonPhrase(),  // <title>에 사용
                status.getCode(), status.getReasonPhrase(),  // 본문에 사용
                message != null ? message : status.getReasonPhrase()  // 에러 메시지
        );
    }

    /**
     * 응답 정보의 문자열 표현
     * 디버깅과 로깅에 유용
     */
    @Override
    public String toString() {
        return String.format("MiniResponse{status=%s, bodySize=%d, committed=%s}",
                status,           // HTTP 상태
                getBodySize(),    // 본문 크기
                committed         // 커밋 여부
        );
    }
}