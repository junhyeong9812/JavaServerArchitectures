package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * 정적 파일 비동기 서블릿
 * CSS, JS, HTML 등 정적 파일을 비동기로 서빙
 */
public class StaticFileAsyncServlet extends MiniAsyncServlet {
    // MiniAsyncServlet을 상속받아 정적 파일 서빙 기능을 비동기로 구현
    // 웹 애플리케이션의 CSS, JavaScript, HTML 등 정적 리소스 제공

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        // GET 요청 처리를 위한 비동기 메서드 오버라이드
        // 정적 파일은 일반적으로 GET 요청으로만 접근하므로 GET만 구현

        return CompletableFuture.runAsync(() -> {
            // runAsync()로 별도 스레드에서 파일 서빙 처리
            // 파일 I/O가 포함될 수 있으므로 비동기 처리로 스레드 블로킹 방지

            try {
                // 요청 경로에서 파일 경로 정보 추출
                String path = request.getPathInfo();
                // getPathInfo() 사용 이유:
                // 1. 서블릿 매핑 이후의 추가 경로 정보 획득
                // 2. /static/* 패턴에서 * 부분에 해당하는 실제 파일 경로
                // 3. 상대 경로로 파일 시스템의 구조 반영

                // 비동기 파일 읽기 시뮬레이션
                Thread.sleep(50);
                // Thread.sleep() 사용 이유:
                // 1. 실제 파일 시스템 I/O 지연 시뮬레이션 (50ms)
                // 2. 디스크 읽기, 네트워크 파일 시스템 접근 등의 지연 모방
                // 3. 비동기 처리의 효과를 명확히 보여주기 위한 인위적 지연

                // 파일 확장자별 적절한 Content-Type과 내용 제공
                if (path.endsWith(".css")) {
                    // CSS 파일 처리
                    response.setContentType("text/css");
                    // setContentType() 사용 이유:
                    // 1. 브라우저가 CSS로 인식하도록 MIME 타입 설정
                    // 2. text/css는 CSS 파일의 표준 Content-Type
                    // 3. 올바른 파싱과 스타일 적용을 위한 필수 헤더

                    response.writeBody("/* Async CSS content for " + path + " */\n" +
                            "body { color: green; background: #f0f0f0; }\n" +
                            ".async { animation: blink 1s infinite; }\n" +
                            "/* Served by: " + Thread.currentThread().getName() + " */");
                    // 동적 CSS 내용 생성:
                    // 1. 경로 정보를 주석으로 포함하여 디버깅 지원
                    // 2. 실제 스타일 규칙으로 동작하는 CSS 제공
                    // 3. 처리 스레드 이름을 주석으로 포함하여 비동기 처리 확인
                    // 4. .async 클래스로 애니메이션 효과 제공

                } else if (path.endsWith(".js")) {
                    // JavaScript 파일 처리
                    response.setContentType("application/javascript");
                    // application/javascript는 JavaScript 파일의 표준 MIME 타입
                    // 브라우저가 스크립트로 실행하도록 보장

                    response.writeBody("// Async JavaScript content for " + path + "\n" +
                            "console.log('Hello from async " + path + "');\n" +
                            "console.log('Thread: " + Thread.currentThread().getName() + "');\n" +
                            "console.log('Processing: Non-blocking async');");
                    // 동적 JavaScript 내용 생성:
                    // 1. 경로별로 구분되는 로그 메시지 제공
                    // 2. console.log()로 브라우저 개발자 도구에서 확인 가능
                    // 3. 처리 스레드와 방식 정보로 디버깅 지원
                    // 4. 실제 실행 가능한 JavaScript 코드 제공

                } else if (path.endsWith(".html")) {
                    // HTML 파일 처리
                    response.setContentType("text/html");
                    // text/html은 HTML 문서의 표준 MIME 타입
                    // 브라우저가 HTML로 파싱하고 렌더링하도록 보장

                    response.writeBody("<!DOCTYPE html>\n<html><body>\n" +
                            "<h1>Async Static HTML: " + path + "</h1>\n" +
                            "<p>Served by: " + Thread.currentThread().getName() + "</p>\n" +
                            "<p>Server: HybridServer AsyncServlet</p>\n" +
                            "<p>Processing: Non-blocking async</p>\n" +
                            "</body></html>");
                    // 완전한 HTML 문서 구조:
                    // 1. DOCTYPE 선언으로 HTML5 표준 준수
                    // 2. 의미 있는 제목과 내용으로 실제 웹 페이지 기능
                    // 3. 서버 정보와 처리 방식을 시각적으로 표시
                    // 4. 웹 브라우저에서 바로 확인 가능한 형태

                } else {
                    // 기타 파일 또는 확장자가 없는 파일 처리
                    response.setContentType("text/plain");
                    // text/plain으로 일반 텍스트 파일로 처리
                    // 알 수 없는 파일 타입에 대한 안전한 기본값

                    response.writeBody("Async Static file: " + path + "\n" +
                            "Served by: " + Thread.currentThread().getName() + "\n" +
                            "Server: HybridServer\n" +
                            "Processing: Non-blocking async\n" +
                            "Timestamp: " + System.currentTimeMillis());
                    // 일반 텍스트 형태의 파일 정보:
                    // 1. 파일 경로와 기본 정보 제공
                    // 2. 서버 식별 정보로 로드 밸런싱 환경에서 구분
                    // 3. 타임스탬프로 캐싱 여부와 응답 신선도 확인
                    // 4. 처리 방식 명시로 성능 특성 표시
                }

                // 각 파일 타입별 처리의 공통점:
                // 1. 적절한 Content-Type 헤더 설정
                // 2. 경로 정보를 내용에 포함하여 디버깅 지원
                // 3. 처리 스레드 정보로 비동기 처리 확인
                // 4. 실제 동작하는 내용 제공 (CSS 스타일, JS 실행 등)

            } catch (InterruptedException e) {
                // Thread.sleep() 인터럽트 예외 처리
                Thread.currentThread().interrupt();
                // interrupt() 호출로 인터럽트 상태 복원
                // 스레드 풀에서 정상적인 인터럽트 처리 지원

                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "File serving interrupted");
                // 파일 서빙 중단시 적절한 에러 응답
                // HTTP 500 상태로 서버 내부 오류임을 표시
            }
        });
    }

    // 다른 HTTP 메서드(POST, PUT 등)를 오버라이드하지 않는 이유:
    // 1. 정적 파일은 일반적으로 읽기 전용으로 GET 요청만 지원
    // 2. RESTful 설계에서 정적 리소스는 GET으로만 접근
    // 3. 보안상 정적 파일에 대한 수정 요청은 차단하는 것이 일반적
    // 4. 부모 클래스에서 제공하는 기본 구현(405 Method Not Allowed)으로 충분
}