package server.core.mini;

// HTTP 관련 클래스들
import server.core.http.*;
// 비동기 처리를 위한 CompletableFuture
import java.util.concurrent.CompletableFuture;

/**
 * 비동기 서블릿 지원
 *
 * 역할:
 * - 비동기 요청 처리 지원
 * - CompletableFuture 기반의 비동기 프로그래밍 모델
 * - 논블로킹 I/O와 고성능 처리
 * - 기존 MiniServlet의 모든 기능 상속
 *
 * 비동기 처리의 장점:
 * - 스레드 풀 효율성 향상
 * - 높은 동시성 처리 가능
 * - 블로킹 작업에서 스레드 해제
 * - 확장성 개선
 */
public abstract class MiniAsyncServlet extends MiniServlet {

    /**
     * 비동기 요청 처리
     *
     * CompletableFuture를 반환하여 비동기 처리 지원
     * 호출자는 이 Future를 통해 결과를 비동기적으로 받을 수 있음
     */
    public final CompletableFuture<HttpResponse> serviceAsync(MiniRequest request, MiniResponse response) {
        // 초기화 상태 확인
        if (!isInitialized()) {
            // 초기화되지 않은 경우 즉시 완료된 Future 반환
            // CompletableFuture.completedFuture(): 이미 완료된 상태의 Future 생성
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Servlet not initialized"));
        }

        try {
            // HTTP 메서드별 라우팅 (비동기 버전)
            HttpMethod method = request.getMethod();

            // switch 문으로 각 HTTP 메서드를 해당 비동기 처리 메서드로 분기
            switch (method) {
                case GET:
                    // 비동기 GET 처리
                    return doGetAsync(request, response);
                case POST:
                    // 비동기 POST 처리
                    return doPostAsync(request, response);
                case PUT:
                    // 비동기 PUT 처리
                    return doPutAsync(request, response);
                case DELETE:
                    // 비동기 DELETE 처리
                    return doDeleteAsync(request, response);
                case HEAD:
                    // 비동기 HEAD 처리
                    return doHeadAsync(request, response);
                case OPTIONS:
                    // 비동기 OPTIONS 처리
                    return doOptionsAsync(request, response);
                case PATCH:
                    // 비동기 PATCH 처리
                    return doPatchAsync(request, response);
                default:
                    // 지원하지 않는 메서드에 대한 처리
                    response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
                    // Allow 헤더에 지원하는 메서드들 명시
                    response.setHeader("Allow", "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH");
                    // 즉시 완료된 Future로 응답 반환
                    return CompletableFuture.completedFuture(response.build());
            }
        } catch (Exception e) {
            // 예외 발생 시 500 에러로 즉시 완료된 Future 반환
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Servlet error: " + e.getMessage()));
        }
    }

    /**
     * 비동기 GET 처리
     * 서브클래스에서 오버라이드하여 실제 GET 로직 구현
     *
     * 사용 예시:
     * - 데이터베이스 비동기 조회
     * - 외부 API 호출
     * - 파일 시스템 비동기 접근
     */
    protected CompletableFuture<HttpResponse> doGetAsync(MiniRequest request, MiniResponse response) {
        // 기본 구현: Method Not Allowed 응답
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        return CompletableFuture.completedFuture(response.build());
    }

    /**
     * 비동기 POST 처리
     *
     * 사용 예시:
     * - 비동기 데이터 생성
     * - 파일 업로드 처리
     * - 메시지 큐 전송
     */
    protected CompletableFuture<HttpResponse> doPostAsync(MiniRequest request, MiniResponse response) {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        return CompletableFuture.completedFuture(response.build());
    }

    /**
     * 비동기 PUT 처리
     *
     * 사용 예시:
     * - 비동기 리소스 업데이트
     * - 대용량 데이터 처리
     */
    protected CompletableFuture<HttpResponse> doPutAsync(MiniRequest request, MiniResponse response) {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        return CompletableFuture.completedFuture(response.build());
    }

    /**
     * 비동기 DELETE 처리
     *
     * 사용 예시:
     * - 비동기 리소스 삭제
     * - 정리 작업 수행
     */
    protected CompletableFuture<HttpResponse> doDeleteAsync(MiniRequest request, MiniResponse response) {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        return CompletableFuture.completedFuture(response.build());
    }

    /**
     * 비동기 HEAD 처리
     * GET과 동일한 처리를 수행하지만 응답 본문은 제거
     *
     * CompletableFuture의 체이닝 기능 활용:
     * 1. doGetAsync() 실행
     * 2. 결과를 받아서 본문 제거한 새로운 응답 생성
     */
    protected CompletableFuture<HttpResponse> doHeadAsync(MiniRequest request, MiniResponse response) {
        // doGetAsync() 결과를 비동기적으로 처리
        return doGetAsync(request, response)
                // thenApply(): Future의 결과를 변환하는 메서드
                // 이전 단계의 결과(httpResponse)를 받아서 새로운 값으로 변환
                .thenApply(httpResponse -> {
                    // HEAD는 body가 없음 - GET 응답에서 본문만 제거
                    // HttpResponse.builder(): 빌더 패턴으로 새 응답 생성
                    return HttpResponse.builder(httpResponse.getStatus())
                            // Content-Length 헤더는 유지 (실제 본문 크기 정보)
                            .header("Content-Length", String.valueOf(httpResponse.getBodyLength()))
                            .build();
                });
    }

    /**
     * 비동기 OPTIONS 처리
     * CORS(Cross-Origin Resource Sharing) 등에서 사용
     */
    protected CompletableFuture<HttpResponse> doOptionsAsync(MiniRequest request, MiniResponse response) {
        // 200 OK 상태 설정
        response.setStatus(HttpStatus.OK);

        // 지원하는 HTTP 메서드들을 Allow 헤더에 설정
        response.setHeader("Allow", "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH");

        // 즉시 완료된 Future 반환
        return CompletableFuture.completedFuture(response.build());
    }

    /**
     * 비동기 PATCH 처리
     *
     * 사용 예시:
     * - 비동기 부분 업데이트
     * - 증분 데이터 처리
     */
    protected CompletableFuture<HttpResponse> doPatchAsync(MiniRequest request, MiniResponse response) {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        return CompletableFuture.completedFuture(response.build());
    }
}

/*
 * CompletableFuture 사용 패턴 예시:
 *
 * 1. 간단한 비동기 응답:
 * protected CompletableFuture<HttpResponse> doGetAsync(MiniRequest request, MiniResponse response) {
 *     return CompletableFuture.supplyAsync(() -> {
 *         // 비동기 작업 수행
 *         String data = database.findData(request.getParameter("id"));
 *         response.sendJson(data);
 *         return response.build();
 *     });
 * }
 *
 * 2. 체이닝을 활용한 복잡한 비동기 처리:
 * protected CompletableFuture<HttpResponse> doPostAsync(MiniRequest request, MiniResponse response) {
 *     return CompletableFuture
 *         .supplyAsync(() -> validateInput(request.getBody()))
 *         .thenCompose(validation -> {
 *             if (validation.isValid()) {
 *                 return database.saveAsync(validation.getData());
 *             } else {
 *                 return CompletableFuture.completedFuture(null);
 *             }
 *         })
 *         .thenApply(result -> {
 *             if (result != null) {
 *                 response.sendJson("{\"success\": true}");
 *             } else {
 *                 response.sendError(HttpStatus.BAD_REQUEST);
 *             }
 *             return response.build();
 *         });
 * }
 *
 * 3. 예외 처리가 포함된 비동기 처리:
 * protected CompletableFuture<HttpResponse> doGetAsync(MiniRequest request, MiniResponse response) {
 *     return CompletableFuture
 *         .supplyAsync(() -> expensiveOperation(request))
 *         .exceptionally(throwable -> {
 *             // 예외 발생 시 에러 응답 생성
 *             response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, throwable.getMessage());
 *             return response.build();
 *         });
 * }
 *
 * 4. 여러 비동기 작업 조합:
 * protected CompletableFuture<HttpResponse> doGetAsync(MiniRequest request, MiniResponse response) {
 *     CompletableFuture<String> userFuture = getUserAsync(request.getParameter("userId"));
 *     CompletableFuture<String> orderFuture = getOrdersAsync(request.getParameter("userId"));
 *
 *     return userFuture.thenCombine(orderFuture, (user, orders) -> {
 *         String combinedData = combineUserAndOrders(user, orders);
 *         response.sendJson(combinedData);
 *         return response.build();
 *     });
 * }
 */