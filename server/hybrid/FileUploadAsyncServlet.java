package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * 파일 업로드 비동기 서블릿
 * 파일 업로드를 비동기로 처리하여 스레드 블로킹 방지
 */
public class FileUploadAsyncServlet extends MiniAsyncServlet {
    // MiniAsyncServlet을 상속받아 비동기 서블릿 기능 확장
    // 상속을 통해 기본 비동기 서블릿 생명주기와 처리 인프라 활용

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        // GET 요청 처리를 위한 비동기 메서드 오버라이드
        // CompletableFuture<Void> 반환으로 비동기 작업 완료 시점 제어

        return CompletableFuture.runAsync(() -> {
            // runAsync()로 별도 스레드에서 HTML 폼 생성 작업 실행
            // 람다식 내부에서 파일 업로드 폼 HTML 생성

            // 업로드 폼 표시 - HTML 문자열 연결로 동적 폼 생성
            response.sendHtml(
                    "<html><body>" +
                            "<h2>Async File Upload Test</h2>" +
                            // 파일 업로드를 위한 multipart/form-data 폼
                            "<form method='post' enctype='multipart/form-data'>" +
                            // enctype='multipart/form-data' 사용 이유:
                            // 1. 파일 업로드에 필수적인 인코딩 방식
                            // 2. 바이너리 데이터와 텍스트 데이터 동시 전송 지원
                            // 3. 여러 파트로 구성된 데이터 전송 가능
                            "<p>File: <input type='file' name='file'></p>" +
                            // type='file' 입력 필드로 파일 선택 인터페이스 제공
                            "<p>Description: <input type='text' name='description'></p>" +
                            // 추가 텍스트 데이터 입력을 위한 필드
                            "<p><input type='submit' value='Upload Async'></p>" +
                            // 제출 버튼으로 POST 요청 트리거
                            "</form>" +
                            // 디버깅과 모니터링을 위한 정보 표시
                            "<p>Thread: " + Thread.currentThread().getName() + "</p>" +
                            // Thread.currentThread().getName()으로 현재 실행 스레드 표시
                            "<p>Server: HybridServer AsyncServlet</p>" +
                            // 서버 타입 명시로 클라이언트에서 구분 가능
                            "<p>Processing: Non-blocking async upload</p>" +
                            // 처리 방식 명시로 비동기 처리임을 알림
                            "</body></html>"
            );
            // response.sendHtml() 사용 이유:
            // 1. Content-Type을 text/html로 자동 설정
            // 2. 적절한 HTTP 헤더 자동 추가
            // 3. HTML 응답 전송에 최적화된 메서드
        });
    }

    @Override
    protected CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        // POST 요청 처리를 위한 비동기 메서드 오버라이드
        // 실제 파일 업로드 처리 로직 구현

        return CompletableFuture.runAsync(() -> {
            try {
                // 비동기 파일 업로드 처리 시뮬레이션
                Thread.sleep(300);
                // Thread.sleep() 사용 이유:
                // 1. 실제 파일 처리 시간 시뮬레이션 (300ms)
                // 2. I/O 대기 상황을 모방하여 비동기 처리 효과 확인
                // 3. 파일 저장, 검증 등의 처리 시간 대체

                // POST 요청에서 파라미터 추출
                String description = request.getParameter("description");
                // getParameter() 사용으로 폼 데이터에서 텍스트 필드 값 추출

                byte[] body = request.getBodyBytes();
                // getBodyBytes() 사용 이유:
                // 1. 업로드된 파일의 바이너리 데이터 접근
                // 2. multipart/form-data의 전체 바디 내용 추출
                // 3. 파일 크기 측정을 위한 바이트 배열 획득

                // JSON 형태의 처리 결과 생성
                String resultJson = String.format(
                        "{ \"status\": \"uploaded_async\", \"description\": \"%s\", " +
                                "\"bodySize\": %d, \"thread\": \"%s\", \"server\": \"HybridServer\", " +
                                "\"processing\": \"async\", \"processingTime\": \"300ms\" }",
                        description != null ? description : "No description",
                        // 삼항 연산자로 null 체크 및 기본값 설정
                        body.length,
                        // byte[].length로 업로드된 데이터 크기 측정
                        Thread.currentThread().getName()
                        // 처리 스레드 이름으로 비동기 처리 확인
                );
                // String.format() 사용으로 구조화된 JSON 응답 생성
                // 업로드 상태, 설명, 크기, 스레드 정보를 포함한 상세 결과

                // JSON 응답 전송
                response.sendJson(resultJson);
                // sendJson() 사용 이유:
                // 1. Content-Type을 application/json으로 자동 설정
                // 2. JSON 응답에 적절한 HTTP 헤더 자동 추가
                // 3. API 응답 형태로 클라이언트에서 파싱 용이

            } catch (InterruptedException e) {
                // Thread.sleep() 인터럽트 예외 처리
                Thread.currentThread().interrupt();
                // interrupt() 호출 이유:
                // 1. 인터럽트 상태 복원으로 상위 호출자에게 인터럽트 상황 전달
                // 2. 스레드 풀의 정상적인 인터럽트 처리 지원
                // 3. InterruptedException은 인터럽트 상태를 지우므로 명시적 복원 필요

                // 인터럽트 발생시 에러 응답 전송
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "Upload processing interrupted");
                // HttpStatus.INTERNAL_SERVER_ERROR 사용 이유:
                // 1. HTTP 500 상태 코드로 서버 내부 오류 표시
                // 2. 클라이언트에게 재시도 가능한 오류임을 알림
                // 3. 표준 HTTP 상태 코드로 일관된 오류 처리
            }
        });
    }
}