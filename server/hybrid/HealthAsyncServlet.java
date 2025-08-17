package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * Health Check 비동기 서블릿
 * 서버 상태 확인을 위한 헬스체크 엔드포인트
 */
public class HealthAsyncServlet extends MiniAsyncServlet {
    // MiniAsyncServlet을 상속받아 비동기 헬스체크 기능 구현
    // 헬스체크는 빠른 응답이 중요하므로 비동기 처리로 최적화

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        // GET 요청 처리를 위한 비동기 메서드 오버라이드
        // 헬스체크의 가장 일반적인 HTTP 메서드인 GET 지원

        return CompletableFuture.runAsync(() -> {
            // runAsync()로 별도 스레드에서 헬스체크 처리
            // 간단한 작업이지만 일관된 비동기 패턴 유지

            // 간단한 헬스체크 응답 생성
            String healthJson = String.format(
                    "{ \"status\": \"healthy\", \"server\": \"HybridServer\", " +
                            "\"thread\": \"%s\", \"timestamp\": %d, \"processing\": \"async\" }",
                    Thread.currentThread().getName(),
                    // Thread.currentThread().getName() 사용 이유:
                    // 1. 현재 처리 스레드 식별로 비동기 처리 확인
                    // 2. 디버깅시 요청 처리 흐름 추적 가능
                    // 3. 로드 밸런싱과 스레드 분산 상태 모니터링
                    System.currentTimeMillis()
                    // System.currentTimeMillis() 사용 이유:
                    // 1. 응답 생성 시각 기록으로 서버 응답성 측정
                    // 2. 에포크 시간으로 클라이언트에서 시간 계산 용이
                    // 3. 헬스체크 응답의 신선도 확인 가능
            );
            // String.format() 사용으로 구조화된 JSON 헬스체크 응답 생성
            // 헬스체크에 필요한 핵심 정보들을 포함:
            // - status: 서버 상태 (healthy/unhealthy)
            // - server: 서버 타입 식별
            // - thread: 처리 스레드 정보
            // - timestamp: 응답 생성 시각
            // - processing: 처리 방식 (async/sync)

            // JSON 헬스체크 응답 전송
            response.sendJson(healthJson);
            // sendJson() 사용 이유:
            // 1. Content-Type을 application/json으로 자동 설정
            // 2. 모니터링 도구에서 파싱하기 쉬운 JSON 형태
            // 3. 구조화된 데이터로 헬스체크 세부 정보 제공
        });
    }

    @Override
    protected CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        // POST 요청 처리를 위한 비동기 메서드 오버라이드
        // 일부 헬스체크 도구나 로드밸런서에서 POST 방식 사용

        return CompletableFuture.runAsync(() -> {
            // POST 요청으로도 헬스체크 가능하도록 지원
            response.sendJson(
                    "{ \"status\": \"healthy\", \"method\": \"POST\", \"server\": \"HybridServer\" }"
            );
            // POST 방식 헬스체크 응답:
            // - status: 서버 상태 확인
            // - method: HTTP 메서드 명시로 요청 방식 구분
            // - server: 서버 타입 식별
            // 간결한 응답으로 빠른 헬스체크 지원
        });
    }
}