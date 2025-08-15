package com.serverarch.eventloop.routing;

import com.serverarch.eventloop.http.HttpRequest;
import com.serverarch.eventloop.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * EventLoop용 비동기 라우트 핸들러 인터페이스
 *
 * 함수형 인터페이스로 설계되어 람다 표현식과 메서드 참조를 지원
 * EventLoop의 비동기 처리 모델에 맞춰 CompletableFuture를 반환
 */
@FunctionalInterface
public interface AsyncRouteHandler {

    /**
     * 비동기로 요청을 처리
     *
     * @param request HTTP 요청
     * @return 비동기 HTTP 응답 Future
     * @throws Exception 처리 중 발생할 수 있는 예외
     */
    CompletableFuture<HttpResponse> handle(HttpRequest request) throws Exception;

    /**
     * 여러 핸들러를 체이닝하여 순차적으로 실행
     *
     * @param next 다음에 실행할 핸들러
     * @return 체이닝된 핸들러
     */
    default AsyncRouteHandler andThen(AsyncRouteHandler next) {
        return request -> this.handle(request)
                .thenCompose(response -> {
                    // 첫 번째 핸들러가 에러 응답을 반환했다면 다음 핸들러는 실행하지 않음
                    if (response != null && !response.isError()) {
                        try {
                            return next.handle(request);
                        } catch (Exception e) {
                            return CompletableFuture.completedFuture(
                                    HttpResponse.serverError("핸들러 체이닝 중 오류: " + e.getMessage())
                            );
                        }
                    }
                    return CompletableFuture.completedFuture(response);
                });
    }

    /**
     * 핸들러 실행 전에 전처리를 수행
     *
     * @param preprocessor 전처리 핸들러
     * @return 전처리가 포함된 핸들러
     */
    default AsyncRouteHandler withPreprocessor(AsyncRouteHandler preprocessor) {
        return request -> preprocessor.handle(request)
                .thenCompose(response -> {
                    // 전처리에서 에러가 발생했다면 메인 핸들러는 실행하지 않음
                    if (response != null && response.isError()) {
                        return CompletableFuture.completedFuture(response);
                    }
                    try {
                        return this.handle(request);
                    } catch (Exception e) {
                        return CompletableFuture.completedFuture(
                                HttpResponse.serverError("핸들러 실행 중 오류: " + e.getMessage())
                        );
                    }
                });
    }

    /**
     * 예외 처리를 포함한 안전한 핸들러로 변환
     *
     * @return 예외 처리가 포함된 핸들러
     */
    default AsyncRouteHandler withExceptionHandling() {
        return request -> {
            try {
                return this.handle(request)
                        .exceptionally(throwable -> {
                            // 비동기 처리 중 발생한 예외 처리
                            return HttpResponse.serverError(
                                    "비동기 처리 중 오류가 발생했습니다: " + throwable.getMessage()
                            );
                        });
            } catch (Exception e) {
                // 동기 처리 중 발생한 예외 처리
                return CompletableFuture.completedFuture(
                        HttpResponse.serverError("핸들러 실행 중 오류가 발생했습니다: " + e.getMessage())
                );
            }
        };
    }

    /**
     * 타임아웃이 있는 핸들러로 변환
     *
     * @param timeoutMs 타임아웃 시간 (밀리초)
     * @return 타임아웃이 적용된 핸들러
     */
    default AsyncRouteHandler withTimeout(long timeoutMs) {
        return request -> {
            try {
                return this.handle(request)
                        .orTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .exceptionally(throwable -> {
                            if (throwable instanceof java.util.concurrent.TimeoutException) {
                                return HttpResponse.of(
                                        com.serverarch.common.http.HttpStatus.SERVICE_UNAVAILABLE,
                                        "요청 처리 시간이 초과되었습니다"
                                );
                            }
                            return HttpResponse.serverError("요청 처리 중 오류: " + throwable.getMessage());
                        });
            } catch (Exception e) {
                return CompletableFuture.completedFuture(
                        HttpResponse.serverError("핸들러 실행 중 오류: " + e.getMessage())
                );
            }
        };
    }

    // ========== 정적 팩토리 메서드들 ==========

    /**
     * 단순한 문자열 응답을 반환하는 핸들러 생성
     *
     * @param response 응답 문자열
     * @return 핸들러
     */
    static AsyncRouteHandler of(String response) {
        return request -> CompletableFuture.completedFuture(HttpResponse.ok(response));
    }

    /**
     * 단순한 HTTP 응답을 반환하는 핸들러 생성
     *
     * @param response HTTP 응답
     * @return 핸들러
     */
    static AsyncRouteHandler of(HttpResponse response) {
        return request -> CompletableFuture.completedFuture(response);
    }

    /**
     * 항상 404를 반환하는 핸들러 생성
     *
     * @return 404 핸들러
     */
    static AsyncRouteHandler notFound() {
        return request -> CompletableFuture.completedFuture(
                HttpResponse.notFound("요청한 경로를 찾을 수 없습니다: " + request.getPath())
        );
    }

    /**
     * 항상 특정 상태 코드를 반환하는 핸들러 생성
     *
     * @param status HTTP 상태
     * @param message 응답 메시지
     * @return 핸들러
     */
    static AsyncRouteHandler status(com.serverarch.common.http.HttpStatus status, String message) {
        return request -> CompletableFuture.completedFuture(HttpResponse.of(status, message));
    }

    /**
     * 요청 정보를 에코하는 핸들러 생성 (디버깅용)
     *
     * @return 에코 핸들러
     */
    static AsyncRouteHandler echo() {
        return request -> {
            String responseBody = String.format(
                    "Echo Response:\n" +
                            "Method: %s\n" +
                            "Path: %s\n" +
                            "Headers: %s\n" +
                            "Body: %s\n",
                    request.getMethod(),
                    request.getPath(),
                    request.getHeaders(),
                    request.getBodyAsString()
            );
            return CompletableFuture.completedFuture(HttpResponse.ok(responseBody));
        };
    }
}