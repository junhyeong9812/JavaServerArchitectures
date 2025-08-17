package server.threaded;

import server.core.routing.*;
import server.core.mini.*;
import server.core.http.*;
import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.examples.*;
import java.util.concurrent.CompletableFuture;

/**
 * ThreadedServer 테스트 및 예시
 * 실제 서버를 구동하여 다양한 기능들을 테스트하는 클래스
 *
 * 주요 기능:
 * - 서버 설정 및 구동
 * - 다양한 엔드포인트 등록 (Router 기반, Servlet 기반)
 * - 동기/비동기 요청 처리 테스트
 * - 부하 테스트 및 통계 출력
 * - Graceful shutdown 구현
 */
public class ThreadedServerTest {

    // Logger 인스턴스 생성
    // 이 클래스 전용 로거로 테스트 과정을 추적
    private static final Logger logger = LoggerFactory.getLogger(ThreadedServerTest.class);

    /**
     * 메인 메서드 - 프로그램 진입점
     * 서버를 설정하고 시작하는 전체 흐름을 관리
     *
     * @param args 명령행 인수 (사용하지 않음)
     * @throws Exception 서버 실행 중 발생할 수 있는 모든 예외
     */
    public static void main(String[] args) throws Exception {
        logger.info("=== ThreadedServer Test ===");
        logger.info("");

        // === 서버 설정 구성 ===

        // ServerConfig: 서버의 전반적인 설정을 관리하는 클래스
        // 메서드 체이닝 패턴으로 설정을 구성
        ServerConfig config = new ServerConfig()
                // setDebugMode(true): 디버그 모드 활성화
                // 상세한 로그 출력 및 통계 정보 주기적 출력
                .setDebugMode(true)

                // setThreadPoolConfig(): 스레드 풀 설정
                .setThreadPoolConfig(
                        new ThreadPoolConfig()
                                // setCorePoolSize(5): 코어 스레드 수 설정
                                // 항상 유지되는 최소 스레드 개수
                                .setCorePoolSize(5)

                                // setMaxPoolSize(20): 최대 스레드 수 설정
                                // 요청이 많을 때 생성할 수 있는 최대 스레드 개수
                                .setMaxPoolSize(20)

                                // setDebugMode(true): 스레드 풀 디버그 모드
                                .setDebugMode(true)
                )

                // setRequestHandlerConfig(): 요청 처리 설정
                .setRequestHandlerConfig(
                        new RequestHandlerConfig()
                                .setDebugMode(true)

                                // setSocketTimeout(30000): 소켓 타임아웃 30초
                                // 클라이언트와의 연결에서 30초 동안 응답이 없으면 연결 종료
                                .setSocketTimeout(30000)
                );

        // === 라우터 설정 ===

        // createTestRouter(): 테스트용 라우터 생성
        // 다양한 HTTP 엔드포인트들을 등록한 Router 반환
        Router router = createTestRouter();

        // === 서버 생성 ===

        // ThreadedServer 인스턴스 생성
        // 포트 8080, 설정된 라우터, 서버 설정으로 초기화
        ThreadedServer server = new ThreadedServer(8080, router, config);

        // === 서블릿 등록 ===

        // registerServlets(): 다양한 서블릿들을 서버에 등록
        registerServlets(server);

        // === 셧다운 훅 등록 ===

        // addShutdownHook(): JVM 종료 시 서버를 안전하게 종료하는 훅 등록
        // Ctrl+C 입력 시 graceful shutdown 수행
        server.addShutdownHook();

        try {
            // === 서버 시작 ===

            // start(): 서버 초기화 및 실행
            // ServerSocket 바인딩, Accept 스레드 시작 등
            server.start();

            // === 사용자 안내 메시지 출력 ===

            logger.info("ThreadedServer is running on http://localhost:8080");
            logger.info("Available endpoints:");
            logger.info("  GET  http://localhost:8080/hello");
            logger.info("  GET  http://localhost:8080/servlet/hello");
            logger.info("  GET  http://localhost:8080/api/users");
            logger.info("  POST http://localhost:8080/api/users");
            logger.info("  GET  http://localhost:8080/status");
            logger.info("  GET  http://localhost:8080/load-test");
            logger.info("");
            logger.info("Press Ctrl+C to stop the server");
            logger.info("");

            // === 메인 스레드 대기 ===

            // Thread.currentThread().join(): 현재 스레드가 종료될 때까지 대기
            // 메인 스레드를 무한 대기 상태로 만들어 서버가 계속 실행되도록 함
            // InterruptedException 발생 시까지 대기 (Ctrl+C 등)
            Thread.currentThread().join();

        } catch (InterruptedException e) {
            // 인터럽트 신호 받음 (정상적인 종료 과정)
            logger.info("Server interrupted");
        } finally {
            // === 서버 종료 ===

            // finally 블록: 예외 발생 여부와 관계없이 항상 실행
            // 서버 안전 종료 보장
            server.stop();
        }
    }

    /**
     * 테스트용 라우터 생성
     * 다양한 HTTP 엔드포인트들을 등록한 Router 인스턴스 반환
     *
     * @return 설정된 Router 인스턴스
     */
    private static Router createTestRouter() {
        // Router 인스턴스 생성
        Router router = new Router();

        // === 기본 핸들러들 ===

        // router.get(): HTTP GET 메서드에 대한 핸들러 등록
        // RouteHandler.sync(): 동기 핸들러 생성 (즉시 응답 반환)
        router.get("/", RouteHandler.sync(request ->
                // HttpResponse.html(): HTML 응답 생성
                HttpResponse.html(createIndexPage())
        ));

        // 파라미터를 받는 Hello World 엔드포인트
        router.get("/hello", RouteHandler.sync(request -> {
            // request.getQueryParameter(): URL 쿼리 파라미터 추출
            // 예: /hello?name=John에서 "John" 추출
            String name = request.getQueryParameter("name");

            // null 체크 및 기본값 설정
            if (name == null) name = "World";

            // Thread.currentThread().getName(): 현재 스레드 이름
            // 어떤 스레드에서 요청이 처리되는지 확인용
            return HttpResponse.html(
                    "<h1>Hello, " + name + "!</h1>" +
                            "<p>Handled by ThreadedServer Router</p>" +
                            "<p>Thread: " + Thread.currentThread().getName() + "</p>"
            );
        }));

        // === 비동기 JSON API ===

        // router.get()에 CompletableFuture를 반환하는 핸들러
        router.get("/api/test", request ->
                // CompletableFuture.supplyAsync(): 별도 스레드에서 비동기 실행
                CompletableFuture.supplyAsync(() -> {
                    // 가상의 비동기 작업 시뮬레이션
                    try {
                        // Thread.sleep(): 현재 스레드를 100ms 동안 일시정지
                        // I/O 작업이나 데이터베이스 쿼리 등의 지연 시뮬레이션
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        // 인터럽트 발생 시 스레드 상태 복원
                        Thread.currentThread().interrupt();
                    }

                    // JSON 응답 생성
                    // System.currentTimeMillis(): 현재 시간 (밀리초)
                    return HttpResponse.json(String.format(
                            "{ \"message\": \"Async response\", \"thread\": \"%s\", \"timestamp\": %d }",
                            Thread.currentThread().getName(), // 처리 스레드명
                            System.currentTimeMillis()        // 응답 생성 시간
                    ));
                })
        );

        // === 서버 상태 API ===

        router.get("/status", RouteHandler.sync(request -> {
            // 서버 상태 정보를 JSON으로 반환
            return HttpResponse.json(
                    "{ \"server\": \"ThreadedServer\", \"status\": \"running\", " +
                            "\"thread\": \"" + Thread.currentThread().getName() + "\" }"
            );
        }));

        // === 부하 테스트 엔드포인트 ===

        router.get("/load-test", RouteHandler.sync(request -> {
            // CPU 집약적 작업 시뮬레이션

            // 시작 시간 기록
            long start = System.currentTimeMillis();
            double result = 0;

            // 반복 연산으로 CPU 부하 생성
            for (int i = 0; i < 100000; i++) {
                // Math.sqrt(): 제곱근 계산
                // Math.sin(): 사인 값 계산
                // 복잡한 수학 연산으로 CPU 사용률 증가
                result += Math.sqrt(i) * Math.sin(i);
            }

            // 처리 시간 계산
            long duration = System.currentTimeMillis() - start;

            // 결과를 JSON으로 반환
            return HttpResponse.json(String.format(
                    "{ \"computation\": %.2f, \"duration\": %d, \"thread\": \"%s\" }",
                    result,                            // 계산 결과
                    duration,                          // 처리 시간 (밀리초)
                    Thread.currentThread().getName()   // 처리 스레드명
            ));
        }));

        // === 에러 테스트 ===

        // router.get()에서 경로 파라미터 사용
        // "/error/{type}": type 부분이 경로 파라미터
        router.get("/error/{type}", request -> {
            // request.getAttribute(): 경로 파라미터 추출
            // "path.type": 경로에서 {type} 부분의 값
            String type = request.getAttribute("path.type", String.class);

            // switch 문으로 에러 타입별 처리
            switch (type != null ? type : "500") {
                case "400":
                    // HttpResponse.badRequest(): 400 Bad Request 응답
                    return CompletableFuture.completedFuture(HttpResponse.badRequest("Test 400 error"));
                case "404":
                    // HttpResponse.notFound(): 404 Not Found 응답
                    return CompletableFuture.completedFuture(HttpResponse.notFound("Test 404 error"));
                case "exception":
                    // RuntimeException: 의도적 예외 발생으로 에러 처리 테스트
                    throw new RuntimeException("Test exception in handler");
                default:
                    // HttpResponse.internalServerError(): 500 Internal Server Error 응답
                    return CompletableFuture.completedFuture(HttpResponse.internalServerError("Test 500 error"));
            }
        });

        // === 미들웨어 추가 ===

        // router.use(): 모든 요청에 적용되는 미들웨어 등록
        router.use((request, next) -> {
            // 요청 처리 시작 시간 기록
            long start = System.currentTimeMillis();

            // request.getMethod().toString(): HTTP 메서드명 (GET, POST 등)
            String method = request.getMethod().toString();
            String path = request.getPath();

            // next.handle(): 다음 핸들러 체인 호출
            // thenApply(): CompletableFuture의 결과를 변환하는 메서드
            return next.handle(request).thenApply(response -> {
                // 요청 처리 완료 후 실행되는 코드

                // 처리 시간 계산
                long duration = System.currentTimeMillis() - start;

                // 요청 처리 로그 출력
                logger.info("[Router] {} {} -> {} ({}ms)",
                        method,                    // HTTP 메서드
                        path,                      // 요청 경로
                        response.getStatus(),      // 응답 상태 코드
                        duration);                 // 처리 시간

                // === CORS 헤더 추가 ===

                // response.setHeader(): HTTP 응답 헤더 설정
                // CORS (Cross-Origin Resource Sharing): 다른 도메인에서의 요청 허용
                response.setHeader("Access-Control-Allow-Origin", "*");

                // 커스텀 헤더로 응답 시간 정보 제공
                response.setHeader("X-Response-Time", duration + "ms");

                return response;
            });
        });

        return router;
    }

    /**
     * 서블릿 등록
     * 다양한 타입의 서블릿들을 서버에 등록하여 기능 테스트
     *
     * @param server 서블릿을 등록할 ThreadedServer 인스턴스
     */
    private static void registerServlets(ThreadedServer server) {
        // === 헬스체크 서블릿 등록 ===

        // registerServlet(): 동기 서블릿 등록
        // "/health": 서버 상태 확인용 엔드포인트
        server.registerServlet("/health", new HealthServlet());

        // === Hello World 서블릿 ===

        // 기본적인 서블릿 동작 테스트용
        server.registerServlet("/servlet/hello", new HelloWorldServlet());

        // === User API 비동기 서블릿 ===

        // registerAsyncServlet(): 비동기 서블릿 등록
        // "/api/users/*": users로 시작하는 모든 경로 처리
        server.registerAsyncServlet("/api/users/*", new UserApiServlet());

        // === 정적 파일 서블릿 ===

        // 이미지, CSS, JavaScript 등 정적 파일 서빙
        server.registerServlet("/static/*", new StaticFileServlet());

        // === 업로드 서블릿 ===

        // 파일 업로드 기능 테스트용
        server.registerServlet("/upload", new FileUploadServlet());

        // === CPU 집약적 작업용 서블릿 (벤치마크용) ===

        // 서버 성능 테스트 및 부하 테스트용
        server.registerServlet("/cpu-intensive", new CpuIntensiveServlet());

        // === I/O 시뮬레이션용 서블릿 (벤치마크용) ===

        // 데이터베이스나 외부 API 호출 등의 I/O 작업 시뮬레이션
        server.registerServlet("/io-simulation", new IoSimulationServlet());
    }

    /**
     * 인덱스 페이지 생성
     * 서버의 루트 경로("/")에서 보여줄 HTML 페이지 생성
     *
     * @return HTML 형태의 인덱스 페이지 문자열
     */
    private static String createIndexPage() {
        // HTML 문서 구조를 문자열로 생성
        // 여러 줄 문자열 연결로 가독성 있는 HTML 작성
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>ThreadedServer Test</title>\n" +

                // CSS 스타일 정의
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; margin: 50px; }\n" +
                "        .endpoint { margin: 10px 0; padding: 10px; background: #f5f5f5; }\n" +
                "        .method { font-weight: bold; color: #007acc; }\n" +
                "        h1 { color: #333; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +

                // 페이지 제목 및 동적 정보
                "    <h1>ThreadedServer Test Page</h1>\n" +

                // 현재 스레드 정보 표시 (동적 콘텐츠)
                "    <p>Thread: " + Thread.currentThread().getName() + "</p>\n" +

                // 현재 시간 표시 (페이지 생성 시점)
                "    <p>Timestamp: " + System.currentTimeMillis() + "</p>\n" +
                "    \n" +
                "    <h2>Available Endpoints:</h2>\n" +
                "    \n" +

                // === 사용 가능한 엔드포인트 목록 ===

                // Router 기반 Hello World
                "    <div class=\"endpoint\">\n" +
                "        <span class=\"method\">GET</span> \n" +
                "        <a href=\"/hello\">/hello</a> - Router-based hello world\n" +
                "    </div>\n" +
                "    \n" +

                // Servlet 기반 Hello World
                "    <div class=\"endpoint\">\n" +
                "        <span class=\"method\">GET</span> \n" +
                "        <a href=\"/servlet/hello\">/servlet/hello</a> - Servlet-based hello world\n" +
                "    </div>\n" +
                "    \n" +

                // 비동기 JSON API
                "    <div class=\"endpoint\">\n" +
                "        <span class=\"method\">GET</span> \n" +
                "        <a href=\"/api/test\">/api/test</a> - Async JSON API\n" +
                "    </div>\n" +
                "    \n" +

                // User API (비동기 서블릿)
                "    <div class=\"endpoint\">\n" +
                "        <span class=\"method\">GET</span> \n" +
                "        <a href=\"/api/users\">/api/users</a> - User API (async servlet)\n" +
                "    </div>\n" +
                "    \n" +

                // 서버 상태 API
                "    <div class=\"endpoint\">\n" +
                "        <span class=\"method\">GET</span> \n" +
                "        <a href=\"/status\">/status</a> - Server status\n" +
                "    </div>\n" +
                "    \n" +

                // 부하 테스트 엔드포인트
                "    <div class=\"endpoint\">\n" +
                "        <span class=\"method\">GET</span> \n" +
                "        <a href=\"/load-test\">/load-test</a> - Load test endpoint\n" +
                "    </div>\n" +
                "    \n" +

                // 에러 테스트 엔드포인트
                "    <div class=\"endpoint\">\n" +
                "        <span class=\"method\">GET</span> \n" +
                "        <a href=\"/error/400\">/error/400</a> - Test 400 error\n" +
                "    </div>\n" +
                "    \n" +

                // === 테스트 명령어 예시 ===

                "    <h2>Test Commands:</h2>\n" +

                // <pre>: 공백과 줄바꿈을 그대로 표시하는 HTML 태그
                "    <pre>\n" +

                // cURL 명령어 예시들
                "# Basic test\n" +
                "curl http://localhost:8080/hello?name=ThreadedServer\n" +
                "\n" +
                "# JSON API test\n" +
                "curl http://localhost:8080/api/test\n" +
                "\n" +
                "# User creation test\n" +
                "curl -X POST http://localhost:8080/api/users \\\n" +
                "  -H \"Content-Type: application/json\" \\\n" +
                "  -d '{\"id\":\"1\", \"name\":\"John\", \"email\":\"john@example.com\"}'\n" +
                "\n" +
                "# Load test\n" +
                "for i in {1..10}; do curl http://localhost:8080/load-test & done\n" +
                "    </pre>\n" +
                "</body>\n" +
                "</html>";
    }
}