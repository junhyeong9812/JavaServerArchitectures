package server.threaded;

import server.core.routing.*;
import server.core.mini.*;
import server.core.http.*;
import server.examples.*;
import java.util.concurrent.CompletableFuture;

/**
 * ThreadedServer 테스트 및 예시
 */
public class ThreadedServerTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== ThreadedServer Test ===\n");

        // 서버 설정
        ServerConfig config = new ServerConfig()
                .setDebugMode(true)
                .setThreadPoolConfig(
                        new ThreadPoolConfig()
                                .setCorePoolSize(5)
                                .setMaxPoolSize(20)
                                .setDebugMode(true)
                )
                .setRequestHandlerConfig(
                        new RequestHandlerConfig()
                                .setDebugMode(true)
                                .setSocketTimeout(30000)
                );

        // 라우터 설정
        Router router = createTestRouter();

        // 서버 생성
        ThreadedServer server = new ThreadedServer(8080, router, config);

        // 서블릿 등록
        registerServlets(server);

        // 셧다운 훅 등록
        server.addShutdownHook();

        try {
            // 서버 시작
            server.start();

            System.out.println("ThreadedServer is running on http://localhost:8080");
            System.out.println("Available endpoints:");
            System.out.println("  GET  http://localhost:8080/hello");
            System.out.println("  GET  http://localhost:8080/servlet/hello");
            System.out.println("  GET  http://localhost:8080/api/users");
            System.out.println("  POST http://localhost:8080/api/users");
            System.out.println("  GET  http://localhost:8080/status");
            System.out.println("  GET  http://localhost:8080/load-test");
            System.out.println("\nPress Ctrl+C to stop the server\n");

            // 메인 스레드 대기
            Thread.currentThread().join();

        } catch (InterruptedException e) {
            System.out.println("Server interrupted");
        } finally {
            server.stop();
        }
    }

    /**
     * 테스트용 라우터 생성
     */
    private static Router createTestRouter() {
        Router router = new Router();

        // 기본 핸들러들
        router.get("/", RouteHandler.sync(request ->
                HttpResponse.html(createIndexPage())
        ));

        router.get("/hello", RouteHandler.sync(request -> {
            String name = request.getQueryParameter("name");
            if (name == null) name = "World";
            return HttpResponse.html(
                    "<h1>Hello, " + name + "!</h1>" +
                            "<p>Handled by ThreadedServer Router</p>" +
                            "<p>Thread: " + Thread.currentThread().getName() + "</p>"
            );
        }));

        // 비동기 JSON API
        router.get("/api/test", request -> CompletableFuture.supplyAsync(() -> {
            // 가상의 비동기 작업
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return HttpResponse.json(String.format(
                    "{ \"message\": \"Async response\", \"thread\": \"%s\", \"timestamp\": %d }",
                    Thread.currentThread().getName(), System.currentTimeMillis()
            ));
        }));

        // 서버 상태 API
        router.get("/status", RouteHandler.sync(request -> {
            return HttpResponse.json(
                    "{ \"server\": \"ThreadedServer\", \"status\": \"running\", " +
                            "\"thread\": \"" + Thread.currentThread().getName() + "\" }"
            );
        }));

        // 부하 테스트 엔드포인트
        router.get("/load-test", RouteHandler.sync(request -> {
            // CPU 집약적 작업 시뮬레이션
            long start = System.currentTimeMillis();
            double result = 0;
            for (int i = 0; i < 100000; i++) {
                result += Math.sqrt(i) * Math.sin(i);
            }
            long duration = System.currentTimeMillis() - start;

            return HttpResponse.json(String.format(
                    "{ \"computation\": %.2f, \"duration\": %d, \"thread\": \"%s\" }",
                    result, duration, Thread.currentThread().getName()
            ));
        }));

        // 에러 테스트
        router.get("/error/{type}", request -> {
            String type = request.getAttribute("path.type", String.class);

            switch (type != null ? type : "500") {
                case "400":
                    return CompletableFuture.completedFuture(HttpResponse.badRequest("Test 400 error"));
                case "404":
                    return CompletableFuture.completedFuture(HttpResponse.notFound("Test 404 error"));
                case "exception":
                    throw new RuntimeException("Test exception in handler");
                default:
                    return CompletableFuture.completedFuture(HttpResponse.internalServerError("Test 500 error"));
            }
        });

        // 미들웨어 추가
        router.use((request, next) -> {
            long start = System.currentTimeMillis();
            String method = request.getMethod().toString();
            String path = request.getPath();

            return next.handle(request).thenApply(response -> {
                long duration = System.currentTimeMillis() - start;
                System.out.println(String.format("[Router] %s %s -> %s (%dms)",
                        method, path, response.getStatus(), duration));

                // CORS 헤더 추가
                response.setHeader("Access-Control-Allow-Origin", "*");
                response.setHeader("X-Response-Time", duration + "ms");

                return response;
            });
        });

        return router;
    }

    /**
     * 서블릿 등록
     */
    private static void registerServlets(ThreadedServer server) {
        // 헬스체크 서블릿 (새로 추가)
        server.registerServlet("/health", new HealthServlet());

        // Hello World 서블릿
        server.registerServlet("/servlet/hello", new HelloWorldServlet());

        // User API 비동기 서블릿
        server.registerAsyncServlet("/api/users/*", new UserApiServlet());

        // 정적 파일 서블릿
        server.registerServlet("/static/*", new StaticFileServlet());

        // 업로드 서블릿
        server.registerServlet("/upload", new FileUploadServlet());

        // CPU 집약적 작업용 서블릿 (벤치마크용)
        server.registerServlet("/cpu-intensive", new CpuIntensiveServlet());

        // I/O 시뮬레이션용 서블릿 (벤치마크용)
        server.registerServlet("/io-simulation", new IoSimulationServlet());
    }

    /**
     * 인덱스 페이지 생성
     */
    private static String createIndexPage() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>ThreadedServer Test</title>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; margin: 50px; }\n" +
                "        .endpoint { margin: 10px 0; padding: 10px; background: #f5f5f5; }\n" +
                "        .method { font-weight: bold; color: #007acc; }\n" +
                "        h1 { color: #333; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>ThreadedServer Test Page</h1>\n" +
                "    <p>Thread: " + Thread.currentThread().getName() + "</p>\n" +
                "    <p>Timestamp: " + System.currentTimeMillis() + "</p>\n" +
                "    \n" +
                "    <h2>Available Endpoints:</h2>\n" +
                "    \n" +
                "    <div class=\"endpoint\">\n" +
                "        <span class=\"method\">GET</span> \n" +
                "        <a href=\"/hello\">/hello</a> - Router-based hello world\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"endpoint\">\n" +
                "        <span class=\"method\">GET</span> \n" +
                "        <a href=\"/servlet/hello\">/servlet/hello</a> - Servlet-based hello world\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"endpoint\">\n" +
                "        <span class=\"method\">GET</span> \n" +
                "        <a href=\"/api/test\">/api/test</a> - Async JSON API\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"endpoint\">\n" +
                "        <span class=\"method\">GET</span> \n" +
                "        <a href=\"/api/users\">/api/users</a> - User API (async servlet)\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"endpoint\">\n" +
                "        <span class=\"method\">GET</span> \n" +
                "        <a href=\"/status\">/status</a> - Server status\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"endpoint\">\n" +
                "        <span class=\"method\">GET</span> \n" +
                "        <a href=\"/load-test\">/load-test</a> - Load test endpoint\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"endpoint\">\n" +
                "        <span class=\"method\">GET</span> \n" +
                "        <a href=\"/error/400\">/error/400</a> - Test 400 error\n" +
                "    </div>\n" +
                "    \n" +
                "    <h2>Test Commands:</h2>\n" +
                "    <pre>\n" +
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
