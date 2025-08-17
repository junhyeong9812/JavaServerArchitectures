package server.eventloop;

import server.core.routing.*;
import server.core.http.*;
import server.examples.*;
import java.util.concurrent.CompletableFuture;

/**
 * EventLoopServer 테스트 및 예시
 * ThreadedServerTest와 동일한 패턴으로 구성
 */
public class EventLoopServerTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== EventLoopServer Test ===\n");

        try {
            // 라우터 설정
            Router router = createTestRouter();

            // 서버 생성
            EventLoopServer server = new EventLoopServer(router);

            // 라우트 등록 (벤치마크용)
            registerBenchmarkRoutes(server);

            // 셧다운 훅 등록
            setupShutdownHook(server);

            // 서버 시작
            server.start(8082);

            System.out.println("EventLoopServer is running on http://localhost:8082");
            System.out.println("Available endpoints:");
            System.out.println("  GET  http://localhost:8082/hello");
            System.out.println("  GET  http://localhost:8082/health");
            System.out.println("  GET  http://localhost:8082/cpu-intensive");
            System.out.println("  GET  http://localhost:8082/io-simulation");
            System.out.println("  GET  http://localhost:8082/server/info");
            System.out.println("  GET  http://localhost:8082/server/stats");
            System.out.println("\nPress Ctrl+C to stop the server\n");

            // 메인 스레드 대기
            Thread.currentThread().join();

        } catch (InterruptedException e) {
            System.out.println("Server interrupted");
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 벤치마크용 EventLoopServer 생성 및 설정 (벤치마크에서 호출용)
     */
    public static EventLoopServer createBenchmarkServer(int port) throws Exception {
        Router router = createBenchmarkRouter();
        EventLoopServer server = new EventLoopServer(router);
        registerBenchmarkRoutes(server);
        return server;
    }

    /**
     * 벤치마크용 라우터 생성
     */
    private static Router createBenchmarkRouter() {
        Router router = new Router();

        // 기본 핸들러들
        router.get("/", RouteHandler.sync(request ->
                HttpResponse.html(createIndexPage())
        ));

        // 비동기 JSON API
        router.get("/api/test", request -> CompletableFuture.supplyAsync(() -> {
            // 가상의 비동기 작업
            try {
                Thread.sleep(50); // EventLoop에서는 짧게
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return HttpResponse.json(String.format(
                    "{ \"message\": \"Async response\", \"thread\": \"%s\", \"timestamp\": %d, \"server\": \"eventloop\" }",
                    Thread.currentThread().getName(), System.currentTimeMillis()
            ));
        }));

        // 서버 상태 API
        router.get("/status", RouteHandler.sync(request -> {
            return HttpResponse.json(
                    "{ \"server\": \"EventLoopServer\", \"status\": \"running\", " +
                            "\"thread\": \"" + Thread.currentThread().getName() + "\" }"
            );
        }));

        // 부하 테스트 엔드포인트 (EventLoop 최적화)
        router.get("/load-test", RouteHandler.sync(request -> {
            // 가벼운 CPU 작업 (EventLoop는 CPU 집약적 작업을 피해야 함)
            long start = System.currentTimeMillis();
            double result = 0;
            for (int i = 0; i < 10000; i++) { // ThreadedServer보다 10배 적게
                result += Math.sqrt(i) * Math.sin(i);
            }
            long duration = System.currentTimeMillis() - start;

            return HttpResponse.json(String.format(
                    "{ \"computation\": %.2f, \"duration\": %d, \"thread\": \"%s\", \"server\": \"eventloop\" }",
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
                    throw new RuntimeException("Test exception in EventLoop handler");
                default:
                    return CompletableFuture.completedFuture(HttpResponse.internalServerError("Test 500 error"));
            }
        });

        return router;
    }

    /**
     * 테스트용 라우터 생성 (확장된 기능)
     */
    private static Router createTestRouter() {
        Router router = createBenchmarkRouter();

        // EventLoop 특화 미들웨어 추가
        router.use((request, next) -> {
            long start = System.nanoTime();
            String method = request.getMethod().toString();
            String path = request.getPath();

            return next.handle(request).thenApply(response -> {
                long durationNanos = System.nanoTime() - start;
                double durationMs = durationNanos / 1_000_000.0;

                System.out.println(String.format("[EventLoop] %s %s -> %s (%.2fms)",
                        method, path, response.getStatus(), durationMs));

                // CORS 헤더 추가
                response.setHeader("Access-Control-Allow-Origin", "*");
                response.setHeader("X-Response-Time", String.format("%.2fms", durationMs));
                response.setHeader("X-Server-Type", "EventLoop");

                return response;
            });
        });

        return router;
    }

    /**
     * 벤치마크용 라우트 등록
     */
    private static void registerBenchmarkRoutes(EventLoopServer server) {
        // ThreadedServerTest와 동일한 엔드포인트 제공

        // 헬스체크 (이미 기본 제공됨)
        server.get("/health", RouteHandler.sync(request ->
                HttpResponse.json("{\"status\":\"healthy\",\"server\":\"eventloop\"}")
        ));

        // Hello World
        server.get("/hello", RouteHandler.sync(request -> {
            String name = request.getQueryParameter("name");
            if (name == null) name = "World";
            return HttpResponse.text("Hello, " + name + "! (EventLoop Server)");
        }));

        // CPU 집약적 작업 (EventLoop에 맞게 최적화)
        server.get("/cpu-intensive", request ->
                server.getProcessor().executeAsync(() -> {
                    // CPU 작업을 별도 스레드로 위임 (EventLoop 블로킹 방지)
                    double result = 0;
                    for (int i = 0; i < 100000; i++) {
                        result += Math.sqrt(i) * Math.sin(i);
                    }
                    return HttpResponse.json(
                            String.format("{\"server\":\"eventloop\",\"result\":%.2f,\"thread\":\"%s\"}",
                                    result, Thread.currentThread().getName())
                    );
                })
        );

        // I/O 시뮬레이션 (EventLoop 방식 - 즉시 응답)
        server.get("/io-simulation", RouteHandler.sync(request -> {
            // EventLoop에서는 실제 블로킹 I/O를 피하고 즉시 응답
            return HttpResponse.json(
                    String.format("{\"server\":\"eventloop\",\"io\":\"completed\",\"thread\":\"%s\"}",
                            Thread.currentThread().getName())
            );
        }));

        // 동시성 테스트용 엔드포인트
        server.get("/concurrent", RouteHandler.sync(request -> {
            return HttpResponse.json(String.format(
                    "{\"server\":\"eventloop\",\"timestamp\":%d,\"thread\":\"%s\",\"connections\":%d}",
                    System.currentTimeMillis(),
                    Thread.currentThread().getName(),
                    server.getStats().getHandlerStats().getActiveConnections()
            ));
        }));
    }

    /**
     * 셧다운 훅 설정
     */
    private static void setupShutdownHook(EventLoopServer server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[EventLoopServer] Shutdown hook triggered");
            server.stop();
        }, "EventLoopServer-ShutdownHook"));
    }

    /**
     * 인덱스 페이지 생성
     */
    private static String createIndexPage() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>EventLoopServer Test</title>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; margin: 50px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }\n" +
                "        .container { max-width: 900px; margin: 0 auto; background: rgba(255,255,255,0.1); padding: 30px; border-radius: 15px; backdrop-filter: blur(10px); }\n" +
                "        .header { text-align: center; border-bottom: 2px solid rgba(255,255,255,0.3); padding-bottom: 20px; }\n" +
                "        .feature { background: rgba(255,255,255,0.1); padding: 20px; margin: 15px 0; border-radius: 10px; border-left: 4px solid #00ff88; }\n" +
                "        .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin: 20px 0; }\n" +
                "        .stat { background: rgba(0,255,136,0.2); padding: 20px; border-radius: 10px; text-align: center; }\n" +
                "        .endpoints { background: rgba(255,255,255,0.1); padding: 15px; border-radius: 10px; }\n" +
                "        .endpoint { margin: 8px 0; padding: 8px; background: rgba(0,0,0,0.2); border-radius: 5px; }\n" +
                "        .method { font-weight: bold; color: #00ff88; }\n" +
                "        pre { background: rgba(0,0,0,0.4); padding: 20px; border-radius: 10px; overflow-x: auto; border-left: 4px solid #ff6b6b; }\n" +
                "        a { color: #00ff88; text-decoration: none; }\n" +
                "        a:hover { color: #fff; }\n" +
                "        .highlight { color: #00ff88; font-weight: bold; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <h1>⚡ EventLoop Server</h1>\n" +
                "            <p><strong>Single Thread + NIO Selector Architecture</strong></p>\n" +
                "            <p>Thread: <span class=\"highlight\">" + Thread.currentThread().getName() + "</span></p>\n" +
                "            <p>Timestamp: <span class=\"highlight\">" + System.currentTimeMillis() + "</span></p>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"feature\">\n" +
                "            <h3>🚀 EventLoop Features</h3>\n" +
                "            <ul>\n" +
                "                <li><strong>Single Event Loop:</strong> 모든 I/O를 하나의 스레드에서 논블로킹 처리</li>\n" +
                "                <li><strong>NIO Selector:</strong> 수만 개의 동시 연결을 효율적으로 관리</li>\n" +
                "                <li><strong>Zero Context Switching:</strong> 스레드 전환 오버헤드 제거</li>\n" +
                "                <li><strong>Memory Efficient:</strong> 연결당 메모리 사용량 최소화</li>\n" +
                "                <li><strong>High Throughput:</strong> I/O 집약적 작업에 최적화</li>\n" +
                "            </ul>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"stats\">\n" +
                "            <div class=\"stat\">\n" +
                "                <h4>Architecture</h4>\n" +
                "                <div>EventLoop</div>\n" +
                "            </div>\n" +
                "            <div class=\"stat\">\n" +
                "                <h4>Port</h4>\n" +
                "                <div>8082</div>\n" +
                "            </div>\n" +
                "            <div class=\"stat\">\n" +
                "                <h4>Concurrency Model</h4>\n" +
                "                <div>Single Thread</div>\n" +
                "            </div>\n" +
                "            <div class=\"stat\">\n" +
                "                <h4>I/O Model</h4>\n" +
                "                <div>Non-blocking NIO</div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"feature\">\n" +
                "            <h3>🔗 API Endpoints</h3>\n" +
                "            <div class=\"endpoints\">\n" +
                "                <div class=\"endpoint\">\n" +
                "                    <span class=\"method\">GET</span> \n" +
                "                    <a href=\"/hello\">/hello</a> - EventLoop hello world\n" +
                "                </div>\n" +
                "                \n" +
                "                <div class=\"endpoint\">\n" +
                "                    <span class=\"method\">GET</span> \n" +
                "                    <a href=\"/api/test\">/api/test</a> - Async JSON API\n" +
                "                </div>\n" +
                "                \n" +
                "                <div class=\"endpoint\">\n" +
                "                    <span class=\"method\">GET</span> \n" +
                "                    <a href=\"/server/info\">/server/info</a> - Server information\n" +
                "                </div>\n" +
                "                \n" +
                "                <div class=\"endpoint\">\n" +
                "                    <span class=\"method\">GET</span> \n" +
                "                    <a href=\"/server/stats\">/server/stats</a> - Real-time statistics\n" +
                "                </div>\n" +
                "                \n" +
                "                <div class=\"endpoint\">\n" +
                "                    <span class=\"method\">GET</span> \n" +
                "                    <a href=\"/health\">/health</a> - Health check\n" +
                "                </div>\n" +
                "                \n" +
                "                <div class=\"endpoint\">\n" +
                "                    <span class=\"method\">GET</span> \n" +
                "                    <a href=\"/load-test\">/load-test</a> - EventLoop optimized load test\n" +
                "                </div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"feature\">\n" +
                "            <h3>💡 EventLoop Testing</h3>\n" +
                "            <pre>\n" +
                "# EventLoop 서버는 동시 연결 테스트에 특히 강합니다\n" +
                "# 수천 개의 동시 연결도 단일 스레드로 처리 가능\n" +
                "\n" +
                "# 기본 테스트\n" +
                "curl http://localhost:8082/hello?name=EventLoop\n" +
                "\n" +
                "# 대량 동시 연결 테스트 (EventLoop의 강점)\n" +
                "for i in {1..1000}; do\n" +
                "  curl http://localhost:8082/concurrent &\n" +
                "done\n" +
                "\n" +
                "# 서버 통계 확인\n" +
                "curl http://localhost:8082/server/stats\n" +
                "\n" +
                "# 비동기 API 테스트\n" +
                "curl http://localhost:8082/api/test\n" +
                "            </pre>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"feature\">\n" +
                "            <h3>📊 Performance Characteristics</h3>\n" +
                "            <ul>\n" +
                "                <li><strong>최적:</strong> I/O 집약적 작업, 높은 동시 연결 수</li>\n" +
                "                <li><strong>좋음:</strong> 웹 API, 마이크로서비스, 실시간 애플리케이션</li>\n" +
                "                <li><strong>주의:</strong> CPU 집약적 작업은 별도 스레드로 위임 필요</li>\n" +
                "                <li><strong>장점:</strong> 낮은 메모리 사용량, 높은 처리량</li>\n" +
                "            </ul>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }
}