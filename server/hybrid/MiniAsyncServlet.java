package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * 하이브리드 서버용 비동기 서블릿 추상 클래스
 *
 * Core의 MiniServlet을 상속받아 비동기 처리 기능만 추가
 * 기존 동기 메서드들은 그대로 유지하고, 비동기 버전만 새로 제공
 */
public abstract class MiniAsyncServlet extends MiniServlet {
    // MiniServlet을 상속받아 기존 동기 서블릿 기능을 모두 유지
    // abstract 클래스로 설계하여 하위 클래스에서 필요한 메서드만 구현하도록 유도
    // 상속을 통해 기존 서블릿 생명주기(init, destroy 등)와 호환성 보장

    /**
     * 비동기 요청 처리 메서드 - 하이브리드 서버 전용
     *
     * @param request MiniRequest 래퍼
     * @param response MiniResponse 래퍼
     * @return CompletableFuture<Void> 비동기 처리 완료 시그널
     */
    public CompletableFuture<Void> processAsync(MiniRequest request, MiniResponse response) {
        // 하이브리드 서버에서 호출되는 메인 비동기 처리 메서드
        // CompletableFuture<Void> 반환으로 비동기 작업 완료 시점 제어
        // 기존 동기 service() 메서드와 대응되는 비동기 버전

        HttpMethod method = request.getMethod();
        // HttpRequest에서 HTTP 메서드 추출하여 적절한 핸들러로 분기

        // HTTP 메서드별 비동기 처리 분기
        switch (method) {
            case GET:
                return doGetAsync(request, response);
            // GET 요청을 비동기 GET 핸들러로 전달
            case POST:
                return doPostAsync(request, response);
            // POST 요청을 비동기 POST 핸들러로 전달
            case PUT:
                return doPutAsync(request, response);
            // PUT 요청을 비동기 PUT 핸들러로 전달
            case DELETE:
                return doDeleteAsync(request, response);
            // DELETE 요청을 비동기 DELETE 핸들러로 전달
            case HEAD:
                return doHeadAsync(request, response);
            // HEAD 요청을 비동기 HEAD 핸들러로 전달
            case OPTIONS:
                return doOptionsAsync(request, response);
            // OPTIONS 요청을 비동기 OPTIONS 핸들러로 전달
            case PATCH:
                return doPatchAsync(request, response);
            // PATCH 요청을 비동기 PATCH 핸들러로 전달
            default:
                // 지원하지 않는 HTTP 메서드에 대한 처리
                return CompletableFuture.runAsync(() -> {
                    // runAsync()로 에러 응답도 비동기적으로 처리
                    response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
                    // HTTP 405 Method Not Allowed 상태 코드 설정
                    response.setHeader("Allow", "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH");
                    // Allow 헤더로 지원하는 HTTP 메서드 목록 제공
                    // RFC 7231 표준에 따른 적절한 에러 응답
                });
        }
        // switch 문 사용 이유:
        // 1. enum과 함께 사용하여 컴파일 타임 안전성 확보
        // 2. 새로운 HTTP 메서드 추가시 누락 방지
        // 3. 가독성이 좋고 성능상 if-else보다 효율적
    }

    /**
     * HTTP 메서드별 비동기 처리 메서드들
     * 서브클래스에서 필요한 메서드만 오버라이드
     */
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        // GET 요청의 기본 비동기 처리 구현
        // 하위 클래스에서 오버라이드하지 않으면 동기 doGet() 메서드를 비동기로 래핑

        return CompletableFuture.runAsync(() -> {
            // runAsync()로 동기 메서드를 별도 스레드에서 실행
            try {
                doGet(request, response);
                // 부모 클래스(MiniServlet)의 동기 doGet() 메서드 호출
                // 기존 동기 서블릿과의 호환성 보장
            } catch (Exception e) {
                // 동기 메서드 실행 중 예외 발생시 처리
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
                // 예외를 HTTP 500 에러로 변환하여 클라이언트에 전달
                // e.getMessage()로 구체적인 오류 정보 제공
            }
        });
    }

    protected CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        // POST 요청의 기본 비동기 처리 구현
        return CompletableFuture.runAsync(() -> {
            try {
                doPost(request, response);
                // 부모 클래스의 동기 doPost() 메서드 호출
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    protected CompletableFuture<Void> doPutAsync(MiniRequest request, MiniResponse response) {
        // PUT 요청의 기본 비동기 처리 구현
        return CompletableFuture.runAsync(() -> {
            try {
                doPut(request, response);
                // 부모 클래스의 동기 doPut() 메서드 호출
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    protected CompletableFuture<Void> doDeleteAsync(MiniRequest request, MiniResponse response) {
        // DELETE 요청의 기본 비동기 처리 구현
        return CompletableFuture.runAsync(() -> {
            try {
                doDelete(request, response);
                // 부모 클래스의 동기 doDelete() 메서드 호출
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    protected CompletableFuture<Void> doHeadAsync(MiniRequest request, MiniResponse response) {
        // HEAD 요청의 특별한 비동기 처리 구현
        return doGetAsync(request, response)
                .thenRun(() -> response.clearBody());
        // HEAD는 GET과 동일하지만 body가 없음
        // doGetAsync() 실행 후 thenRun()으로 체인 연결
        // thenRun() 사용 이유:
        // 1. 이전 작업(GET 처리) 완료 후 추가 작업 실행
        // 2. 람다식으로 body 제거 작업 간결하게 표현
        // 3. 비동기 체인 유지하면서 HEAD 특성 구현
        // clearBody() 호출로 HTTP HEAD 명세 준수
    }

    protected CompletableFuture<Void> doOptionsAsync(MiniRequest request, MiniResponse response) {
        // OPTIONS 요청의 기본 비동기 처리 구현
        return CompletableFuture.runAsync(() -> {
            try {
                doOptions(request, response);
                // 부모 클래스의 동기 doOptions() 메서드 호출
                // CORS 프리플라이트 요청 등에서 사용
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    protected CompletableFuture<Void> doPatchAsync(MiniRequest request, MiniResponse response) {
        // PATCH 요청의 기본 비동기 처리 구현
        return CompletableFuture.runAsync(() -> {
            try {
                doPatch(request, response);
                // 부모 클래스의 동기 doPatch() 메서드 호출
                // 리소스 부분 업데이트를 위한 HTTP PATCH 메서드 지원
            } catch (Exception e) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    // 모든 비동기 메서드의 공통 패턴:
    // 1. CompletableFuture.runAsync()로 별도 스레드에서 실행
    // 2. try-catch로 예외를 HTTP 에러 응답으로 변환
    // 3. 기존 동기 메서드 호출로 호환성 유지
    // 4. protected 접근 제한자로 하위 클래스에서만 오버라이드 가능

    // 이 설계의 장점:
    // 1. 기존 동기 서블릿을 수정 없이 비동기로 사용 가능
    // 2. 점진적 마이그레이션 지원 (필요한 메서드만 비동기로 오버라이드)
    // 3. 일관된 예외 처리 패턴 제공
    // 4. HTTP 메서드별 특성 고려 (HEAD의 body 제거 등)
}