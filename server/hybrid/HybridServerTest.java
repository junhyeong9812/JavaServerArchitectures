package server.hybrid;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.http.*;
import server.core.routing.*;
import server.core.mini.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * 하이브리드 서버 테스트 및 런처 클래스
 *
 * 테스트 목적:
 * 1. 하이브리드 서버 기본 동작 확인
 * 2. 컨텍스트 스위칭 기능 테스트
 * 3. 비동기 서블릿 처리 테스트
 * 4. 성능 특성 확인
 */
public class HybridServerTest {

    private static final Logger logger = LoggerFactory.getLogger(HybridServerTest.class);

    public static void main(String[] args) {
        if (args.length > 0 && "test".equals(args[0])) {
            // 테스트 모드
            runAllTests();
        } else {
            // 서버 실행 모드
            runServer();
        }
    }

    /**
     * 실제 서버 실행 (기본 모드)
     */
    private static void runServer() {
        logger.info("=== HybridServer 시작 ===");

        try {
            HybridServer server = new HybridServer(8081);

            // 실제 서블릿들 등록
            registerServlets(server.getServletContainer());

            // 기본 라우트 설정
            setupBasicRoutes(server.getRouter());

            // 컨텍스트 스위칭 라우트 설정
            setupContextSwitchingRoutes(server.getRouter(), server.getSwitchingHandler());

            // 서버 시작
            server.start();

            logger.info("HybridServer 실행 중 - http://localhost:8081");
            logger.info("사용 가능한 엔드포인트:");
            logger.info("  GET  http://localhost:8081/hello");
            logger.info("  GET  http://localhost:8081/api/users");
            logger.info("  POST http://localhost:8081/api/users");
            logger.info("  GET  http://localhost:8081/static/test.js");
            logger.info("  GET  http://localhost:8081/upload");
            logger.info("  GET  http://localhost:8081/load-test");
            logger.info("  GET  http://localhost:8081/test/db");
            logger.info("  GET  http://localhost:8081/test/api");
            logger.info("\nCtrl+C로 서버 종료\n");

            // 종료 훅 등록
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("서버 종료 중...");
                server.stop();
            }));

            // 메인 스레드 대기
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("서버 실행 실패", e);
            System.exit(1);
        }
    }

    /**
     * 모든 테스트 실행
     */
    private static void runAllTests() {
        try {
            // 하이브리드 서버 테스트 실행
            runBasicTests();
            runContextSwitchingTests();
            runAsyncServletTests();
            runPerformanceTests();

            logger.info("모든 하이브리드 서버 테스트 완료");

        } catch (Exception e) {
            logger.error("하이브리드 서버 테스트 실패", e);
            System.exit(1);
        }
    }

    /**
     * 실제 서블릿들 등록
     */
    private static void registerServlets(HybridMiniServletContainer container) {
        // Hello World 비동기 서블릿
        container.registerServlet("HelloWorld", new HelloWorldAsyncServlet(), "/hello");

        // User API 비동기 서블릿
        container.registerServlet("UserApi", new UserApiAsyncServlet(), "/api/users/*");

        // 정적 파일 비동기 서블릿
        container.registerServlet("StaticFile", new StaticFileAsyncServlet(), "/static/*");

        // 파일 업로드 비동기 서블릿
        container.registerServlet("FileUpload", new FileUploadAsyncServlet(), "/upload");

        // 로드 테스트 비동기 서블릿
        container.registerServlet("LoadTest", new LoadTestAsyncServlet(), "/load-test");

        logger.info("모든 비동기 서블릿 등록 완료");
    }

    /**
     * 기본 기능 테스트
     */
    private static void runBasicTests() throws IOException {
        logger.info("=== 기본 기능 테스트 시작 ===");

        HybridServer server = new HybridServer(18081);

        // 기본 라우트 설정
        setupBasicRoutes(server.getRouter());

        try {
            // 서버 시작
            server.start();
            Thread.sleep(1000); // 서버 시작 대기

            // 상태 확인
            HybridServer.ServerStatus status = server.getStatus();
            assert status.isRunning() : "서버가 실행되지 않음";

            logger.info("기본 기능 테스트 통과 - {}", status);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("테스트 중 인터럽트 발생");
        } finally {
            server.stop();
        }

        logger.info("=== 기본 기능 테스트 완료 ===\n");
    }

    /**
     * 컨텍스트 스위칭 테스트
     */
    private static void runContextSwitchingTests() throws IOException {
        logger.info("=== 컨텍스트 스위칭 테스트 시작 ===");

        HybridServer server = new HybridServer(18082);
        ContextSwitchingHandler switchingHandler = server.getSwitchingHandler();

        // 컨텍스트 스위칭 라우트 설정
        setupContextSwitchingRoutes(server.getRouter(), switchingHandler);

        try {
            server.start();
            Thread.sleep(1000);

            // 초기 통계 확인
            ContextSwitchingHandler.SwitchingStats initialStats = switchingHandler.getStats();
            assert initialStats.getTotalSwitchOuts() == 0 : "초기 스위치 아웃이 0이 아님";

            // 모의 요청으로 컨텍스트 스위칭 테스트
            testContextSwitching(switchingHandler);

            // 최종 통계 확인
            ContextSwitchingHandler.SwitchingStats finalStats = switchingHandler.getStats();
            assert finalStats.getTotalSwitchOuts() > 0 : "컨텍스트 스위칭이 발생하지 않음";

            logger.info("컨텍스트 스위칭 테스트 통과 - {}", finalStats);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("테스트 중 인터럽트 발생");
        } finally {
            server.stop();
        }

        logger.info("=== 컨텍스트 스위칭 테스트 완료 ===\n");
    }

    /**
     * 비동기 서블릿 테스트
     */
    private static void runAsyncServletTests() throws IOException {
        logger.info("=== 비동기 서블릿 테스트 시작 ===");

        HybridServer server = new HybridServer(18083);
        HybridMiniServletContainer container = server.getServletContainer();

        // 실제 서블릿들 등록
        container.registerServlet("HelloWorldAsync", new HelloWorldAsyncServlet(), "/test/hello");
        container.registerServlet("UserApiAsync", new UserApiAsyncServlet(), "/test/users");

        try {
            server.start();
            Thread.sleep(1000);

            // 서블릿 등록 확인
            assert container.getServletNames().contains("HelloWorldAsync") : "Hello World 서블릿 등록 실패";
            assert container.getServletNames().contains("UserApiAsync") : "User API 서블릿 등록 실패";

            // 컨테이너 통계 확인
            HybridMiniServletContainer.ContainerStats stats = container.getStats();
            assert stats.getServletCount() == 2 : "서블릿 수가 일치하지 않음";

            logger.info("비동기 서블릿 테스트 통과 - {}", stats);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("테스트 중 인터럽트 발생");
        } finally {
            server.stop();
        }

        logger.info("=== 비동기 서블릿 테스트 완료 ===\n");
    }

    /**
     * 성능 테스트
     */
    private static void runPerformanceTests() throws IOException {
        logger.info("=== 성능 테스트 시작 ===");

        HybridServer server = new HybridServer(18084);

        // 성능 테스트 라우트 설정
        setupPerformanceRoutes(server.getRouter());

        // 로드 테스트 서블릿 등록
        server.getServletContainer().registerServlet("LoadTest", new LoadTestAsyncServlet(), "/servlet/load");

        try {
            server.start();
            Thread.sleep(1000);

            // 부하 생성 및 측정
            long startTime = System.currentTimeMillis();

            // 동시 요청 시뮬레이션 (간단한 형태)
            simulateLoad(server);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // 성능 통계 수집
            HybridServer.ServerStatus status = server.getStatus();

            logger.info("성능 테스트 결과:");
            logger.info("- 실행 시간: {}ms", duration);
            logger.info("- 총 요청: {}", status.getTotalRequests());
            logger.info("- 컨텍스트 스위치: {}", status.getContextSwitches());
            logger.info("- 활성 스레드: {}/{}", status.getActiveThreads(), status.getTotalThreads());

            assert duration < 10000 : "성능 테스트 시간 초과"; // 10초 이내

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("테스트 중 인터럽트 발생");
        } finally {
            server.stop();
        }

        logger.info("=== 성능 테스트 완료 ===\n");
    }

    // === 테스트 헬퍼 메서드들 ===

    private static void setupBasicRoutes(Router router) {
        router.addRoute(HttpMethod.GET, "/test/hello", (request) -> {
            return CompletableFuture.completedFuture(
                    HttpResponse.ok("Hello from Hybrid Server Test")
            );
        });

        router.addRoute(HttpMethod.GET, "/test/status", (request) -> {
            return CompletableFuture.completedFuture(
                    HttpResponse.ok("Status: OK")
                            .setHeader("Content-Type", "text/plain")
            );
        });

        // 인덱스 페이지
        router.addRoute(HttpMethod.GET, "/", (request) -> {
            return CompletableFuture.completedFuture(
                    HttpResponse.html(createIndexPage())
            );
        });
    }

    private static void setupContextSwitchingRoutes(Router router, ContextSwitchingHandler handler) {
        router.addRoute(HttpMethod.GET, "/test/db", (request) -> {
            return handler.executeDbOperation(request, req -> {
                // DB 작업 시뮬레이션
                try {
                    Thread.sleep(100); // 100ms DB 지연
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "DB Result for " + req.getPath();
            }).thenApply(result -> HttpResponse.ok(result)); // String -> HttpResponse 변환
        });

        router.addRoute(HttpMethod.GET, "/test/api", (request) -> {
            return handler.executeApiCall(request, req -> {
                // API 호출 시뮬레이션
                try {
                    Thread.sleep(200); // 200ms API 지연
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "API Result for " + req.getPath();
            }).thenApply(result -> HttpResponse.ok(result)); // String -> HttpResponse 변환
        });
    }

    private static void setupPerformanceRoutes(Router router) {
        router.addRoute(HttpMethod.GET, "/test/load", (request) -> {
            return CompletableFuture.supplyAsync(() -> {
                // CPU 집약적 작업 시뮬레이션
                int result = 0;
                for (int i = 0; i < 100000; i++) {
                    result += i;
                }
                return HttpResponse.ok("Load test result: " + result);
            });
        });
    }

    private static void testContextSwitching(ContextSwitchingHandler handler) {
        // 모의 HTTP 요청 생성
        HttpRequest testRequest = new HttpRequest(
                HttpMethod.GET,
                "/test/switch",
                "HTTP/1.1",
                new HttpHeaders(),
                new byte[0]
        );

        // 컨텍스트 스위칭 작업 실행
        CompletableFuture<String> future = handler.executeDbOperation(testRequest, req -> {
            return "Context switching test result";
        });

        try {
            String result = future.get();
            assert result.contains("test result") : "컨텍스트 스위칭 결과 불일치";
            logger.debug("컨텍스트 스위칭 테스트 결과: {}", result);
        } catch (Exception e) {
            throw new RuntimeException("컨텍스트 스위칭 테스트 실패", e);
        }
    }

    private static void simulateLoad(HybridServer server) {
        // 간단한 부하 시뮬레이션
        try {
            Thread.sleep(2000); // 2초간 서버 실행
            logger.debug("부하 시뮬레이션 완료");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 인덱스 페이지 HTML 생성
     */
    private static String createIndexPage() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>HybridServer Test</title>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; margin: 50px; background: #f5f5f5; }\n" +
                "        .container { background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }\n" +
                "        .endpoint { margin: 10px 0; padding: 15px; background: #e8f4fd; border-left: 4px solid #2196F3; }\n" +
                "        .method { font-weight: bold; color: #1976D2; }\n" +
                "        h1 { color: #333; border-bottom: 2px solid #2196F3; padding-bottom: 10px; }\n" +
                "        h2 { color: #666; margin-top: 30px; }\n" +
                "        .info { background: #fff3cd; padding: 10px; border: 1px solid #ffeaa7; border-radius: 5px; }\n" +
                "        pre { background: #f8f9fa; padding: 15px; border-radius: 5px; overflow-x: auto; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <h1>HybridServer Test Page</h1>\n" +
                "        \n" +
                "        <div class=\"info\">\n" +
                "            <strong>Server Type:</strong> Hybrid (NIO + ThreadPool)<br>\n" +
                "            <strong>Thread:</strong> " + Thread.currentThread().getName() + "<br>\n" +
                "            <strong>Timestamp:</strong> " + System.currentTimeMillis() + "<br>\n" +
                "        </div>\n" +
                "        \n" +
                "        <h2>비동기 서블릿 엔드포인트:</h2>\n" +
                "        \n" +
                "        <div class=\"endpoint\">\n" +
                "            <span class=\"method\">GET</span> \n" +
                "            <a href=\"/hello\">/hello</a> - HelloWorldAsyncServlet\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"endpoint\">\n" +
                "            <span class=\"method\">GET</span> \n" +
                "            <a href=\"/api/users\">/api/users</a> - UserApiAsyncServlet\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"endpoint\">\n" +
                "            <span class=\"method\">GET</span> \n" +
                "            <a href=\"/static/test.js\">/static/test.js</a> - StaticFileAsyncServlet\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"endpoint\">\n" +
                "            <span class=\"method\">GET</span> \n" +
                "            <a href=\"/upload\">/upload</a> - FileUploadAsyncServlet\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"endpoint\">\n" +
                "            <span class=\"method\">GET</span> \n" +
                "            <a href=\"/load-test\">/load-test</a> - LoadTestAsyncServlet\n" +
                "        </div>\n" +
                "        \n" +
                "        <h2>컨텍스트 스위칭 테스트:</h2>\n" +
                "        \n" +
                "        <div class=\"endpoint\">\n" +
                "            <span class=\"method\">GET</span> \n" +
                "            <a href=\"/test/db\">/test/db</a> - DB 작업 시뮬레이션 (100ms)\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"endpoint\">\n" +
                "            <span class=\"method\">GET</span> \n" +
                "            <a href=\"/test/api\">/test/api</a> - API 호출 시뮬레이션 (200ms)\n" +
                "        </div>\n" +
                "        \n" +
                "        <h2>테스트 명령어:</h2>\n" +
                "        <pre>\n" +
                "# 기본 테스트\n" +
                "curl http://localhost:8081/hello?name=HybridServer\n" +
                "\n" +
                "# JSON API 테스트\n" +
                "curl http://localhost:8081/api/users\n" +
                "\n" +
                "# 사용자 생성 테스트\n" +
                "curl -X POST http://localhost:8081/api/users \\\n" +
                "  -H \"Content-Type: application/json\" \\\n" +
                "  -d '{\"id\":\"1\", \"name\":\"John\", \"email\":\"john@example.com\"}'\n" +
                "\n" +
                "# 컨텍스트 스위칭 테스트\n" +
                "curl http://localhost:8081/test/db\n" +
                "curl http://localhost:8081/test/api\n" +
                "\n" +
                "# 로드 테스트\n" +
                "for i in {1..10}; do curl http://localhost:8081/load-test & done\n" +
                "        </pre>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }
}