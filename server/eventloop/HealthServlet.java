package server.eventloop;

import server.core.http.HttpResponse;
import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * EventLoop 서버용 헬스체크 서블릿
 * ThreadedServer의 HealthServlet과 동일한 기능 제공
 *
 * 목적:
 * - 서버의 생존 상태 확인 (Health Check)
 * - 로드 밸런서와 모니터링 시스템에서 서버 상태 점검용
 * - EventLoop 서버의 비동기 특성에 맞춘 구현
 * - 간단하고 빠른 응답으로 서버 부하 최소화
 *
 * 사용 사례:
 * - Kubernetes liveness/readiness probe
 * - AWS ALB health check
 * - 서비스 디스커버리 시스템
 * - 모니터링 도구 (Prometheus, Grafana 등)
 */
public class HealthServlet {

    // Logger 인스턴스 - 헬스체크 요청 추적 (선택적 로깅)
    private static final Logger logger = LoggerFactory.getLogger(HealthServlet.class);

    /**
     * 헬스체크 요청 처리
     *
     * 가장 기본적인 헬스체크 응답 제공
     * 서버가 살아있고 요청을 처리할 수 있음을 확인
     *
     * 응답 특징:
     * - 매우 빠른 응답 (즉시 완료된 Future 반환)
     * - 최소한의 JSON 응답으로 부하 최소화
     * - 서버 타입 식별자 포함 (eventloop)
     *
     * @return 헬스 상태를 담은 CompletableFuture<HttpResponse>
     */
    public CompletableFuture<HttpResponse> handleRequest() {
        // CompletableFuture.completedFuture(): 이미 완료된 Future 반환
        // 별도의 비동기 처리 없이 즉시 응답 가능
        // EventLoop의 논블로킹 특성에 최적화됨
        return CompletableFuture.completedFuture(
                // HttpResponse.json(): JSON 형태의 HTTP 응답 생성
                // status: 서버 상태 (healthy = 정상)
                // server: 서버 타입 식별자 (eventloop)
                HttpResponse.json("{\"status\":\"healthy\",\"server\":\"eventloop\"}")
        );
    }

    /**
     * 서버 상태 정보 포함한 상세 헬스체크
     *
     * 기본 헬스체크보다 더 많은 정보를 제공
     * 디버깅과 상세 모니터링에 유용
     *
     * 포함 정보:
     * - 서버 상태 (healthy)
     * - 서버 타입 (eventloop)
     * - 응답 생성 시각 (timestamp)
     * - 현재 처리 스레드 이름 (EventLoop 메인 스레드)
     *
     * 사용 사례:
     * - 개발 환경에서의 상세 모니터링
     * - 성능 분석 및 디버깅
     * - 서버 인스턴스 식별
     *
     * @return 상세 헬스 정보를 담은 CompletableFuture<HttpResponse>
     */
    public CompletableFuture<HttpResponse> handleDetailedHealth() {
        return CompletableFuture.completedFuture(
                // String.format(): printf 스타일의 문자열 포맷팅
                // JSON 응답에 동적 정보 삽입
                HttpResponse.json(String.format(
                        "{\"status\":\"healthy\",\"server\":\"eventloop\",\"timestamp\":%d,\"thread\":\"%s\"}",

                        // System.currentTimeMillis(): 현재 시간을 밀리초로 반환
                        // Unix timestamp 형태로 응답 생성 시각 기록
                        // 클라이언트에서 응답 시간 분석 가능
                        System.currentTimeMillis(),

                        // Thread.currentThread().getName(): 현재 스레드의 이름
                        // EventLoop 서버에서는 보통 "EventLoop-Main" 형태
                        // 멀티 인스턴스 환경에서 어느 스레드가 처리했는지 확인 가능
                        Thread.currentThread().getName()
                ))
        );
    }
}