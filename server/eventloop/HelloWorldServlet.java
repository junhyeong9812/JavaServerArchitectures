package server.eventloop;

import server.core.http.HttpRequest;
import server.core.http.HttpResponse;
import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * EventLoop 서버용 HelloWorld 서블릿
 * ThreadedServer의 HelloWorldServlet과 동일한 기능 제공
 *
 * 목적:
 * - 기본적인 HTTP 요청/응답 처리 데모
 * - 쿼리 파라미터 처리 예시
 * - EventLoop 서버의 비동기 처리 패턴 보여주기
 * - 개발자가 서버 기본 동작을 확인할 수 있는 간단한 엔드포인트
 *
 * 특징:
 * - 즉시 응답 가능한 간단한 로직 (CPU 집약적이지 않음)
 * - 사용자 정의 가능한 인사말 (name 파라미터)
 * - 텍스트와 JSON 두 가지 응답 형식 지원
 * - EventLoop 논블로킹 특성에 최적화
 */
public class HelloWorldServlet {

    // Logger 인스턴스 - Hello 요청 처리 상황 추적
    private static final Logger logger = LoggerFactory.getLogger(HelloWorldServlet.class);

    /**
     * Hello World 요청 처리
     *
     * 가장 기본적인 HTTP 요청 처리 예시
     * 쿼리 파라미터에서 이름을 추출하여 개인화된 인사말 제공
     *
     * URL 예시:
     * - /hello -> "Hello, World! (EventLoop Server)"
     * - /hello?name=Alice -> "Hello, Alice! (EventLoop Server)"
     * - /hello?name= -> "Hello, World! (EventLoop Server)" (빈 문자열 처리)
     *
     * @param request HTTP 요청 객체 (쿼리 파라미터 포함)
     * @return 인사말을 담은 CompletableFuture<HttpResponse>
     */
    public CompletableFuture<HttpResponse> handleRequest(HttpRequest request) {
        // 쿼리 파라미터에서 name 추출
        // getQueryParameter(): URL의 ?name=value 형태에서 value 추출
        // 예: /hello?name=Alice&age=30 -> name 파라미터는 "Alice"
        String name = request.getQueryParameter("name");

        // name 파라미터 검증 및 기본값 설정
        if (name == null || name.trim().isEmpty()) {
            // null이거나 빈 문자열/공백만 있는 경우 기본값 사용
            // trim(): 앞뒤 공백 제거 후 길이 확인
            name = "World";
        }

        // 응답 텍스트 생성
        // String.format(): printf 스타일의 문자열 포맷팅
        // 서버 타입 식별자 (EventLoop Server) 포함
        String responseText = String.format("Hello, %s! (EventLoop Server)", name);

        // 디버그 로그 출력 (개발 환경에서 요청 추적용)
        // logger.debug(): DEBUG 레벨 로그 (운영 환경에서는 보통 비활성화)
        // Thread.currentThread().getName(): 현재 처리 스레드 이름
        logger.debug("name: {}에 대한 hello 요청 처리 중, 스레드: {}",
                name, Thread.currentThread().getName());

        // 즉시 완료된 Future 반환
        // CompletableFuture.completedFuture(): 이미 결과가 준비된 Future 생성
        // 별도의 비동기 처리 없이 즉시 응답 (EventLoop 블로킹 방지)
        return CompletableFuture.completedFuture(
                // HttpResponse.text(): 단순 텍스트 응답 생성
                // Content-Type: text/plain으로 설정됨
                HttpResponse.text(responseText)
        );
    }

    /**
     * JSON 형태의 Hello 응답
     *
     * REST API나 AJAX 요청에 적합한 JSON 응답 제공
     * 구조화된 데이터로 더 많은 정보 포함 가능
     *
     * JSON 응답 구조:
     * {
     *   "message": "Hello, Alice!",
     *   "server": "eventloop",
     *   "thread": "EventLoop-Main",
     *   "timestamp": 1642123456789
     * }
     *
     * 사용 사례:
     * - REST API 엔드포인트
     * - JavaScript 클라이언트와의 통신
     * - 마이크로서비스 간 통신
     * - 모바일 앱 백엔드
     *
     * @param request HTTP 요청 객체
     * @return JSON 형태의 hello 응답을 담은 CompletableFuture<HttpResponse>
     */
    public CompletableFuture<HttpResponse> handleJsonRequest(HttpRequest request) {
        // 동일한 name 파라미터 처리 로직
        String name = request.getQueryParameter("name");
        if (name == null || name.trim().isEmpty()) {
            name = "World";
        }

        return CompletableFuture.completedFuture(
                // HttpResponse.json(): JSON 형태의 HTTP 응답 생성
                // Content-Type: application/json으로 자동 설정
                HttpResponse.json(String.format(
                        "{\"message\":\"Hello, %s!\",\"server\":\"eventloop\",\"thread\":\"%s\",\"timestamp\":%d}",

                        // %s: 문자열 치환 - 사용자 이름
                        name,

                        // 현재 처리 스레드 이름
                        // EventLoop 서버에서는 보통 하나의 메인 스레드에서 처리
                        Thread.currentThread().getName(),

                        // 응답 생성 시각 (Unix timestamp)
                        // 클라이언트에서 응답 시간 분석이나 캐싱에 활용 가능
                        System.currentTimeMillis()
                ))
        );
    }
}