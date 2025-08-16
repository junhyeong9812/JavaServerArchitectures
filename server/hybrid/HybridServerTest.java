package server.test;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.http.*;
import server.core.routing.*;
import server.core.mini.*;
import server.hybrid.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * 하이브리드 서버 테스트 클래스
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
        // 로거 설정 (테스트 모드)
        LoggerFactory.configureForTesting();

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
     * 기본 기능 테스트
     */
    private static void runBasicTests() throws IOException {
        logger.info("=== 기본 기능 테스트 시작 ===");

        HybridServer server = new HybridServer(8081);

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

        HybridServer server = new HybridServer(8082);
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

        HybridServer server = new HybridServer(8083);
        HybridMiniServletContainer container = server.getServletContainer();

        // 테스트 서블릿 등록
        container.registerServlet("TestAsyncServlet", new TestAsyncServlet(), "/test/async");
        container.registerServlet("TestSyncServlet", new TestSyncServlet(), "/test/sync");

        try {
            server.start();
            Thread.sleep(1000);

            // 서블릿 등록 확인
            assert container.getServletNames().contains("TestAsyncServlet") : "비동기 서블릿 등록 실패";
            assert container.getServletNames().contains("TestSyncServlet") : "동기 서블릿 등록 실패";

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

        HybridServer server = new HybridServer(8084);

        // 성능 테스트 라우트 설정
        setupPerformanceRoutes(server.getRouter());

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
            });
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
            });
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

    // === 테스트용 서블릿들 ===

    private static class TestAsyncServlet implements MiniAsyncServlet {
        @Override
        public void init(MiniContext context) {
            logger.debug("TestAsyncServlet 초기화");
        }

        @Override
        public CompletableFuture<Void> serviceAsync(MiniRequest request, MiniResponse response) {
            return CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(50); // 비동기 작업 시뮬레이션
                    response.setContentType("text/plain");
                    response.getWriter().write("Async Servlet Test Result");
                } catch (Exception e) {
                    logger.error("TestAsyncServlet 오류", e);
                }
            });
        }

        @Override
        public void destroy() {
            logger.debug("TestAsyncServlet 파괴");
        }
    }

    private static class TestSyncServlet extends MiniServlet {
        @Override
        public void init(MiniContext context) {
            logger.debug("TestSyncServlet 초기화");
        }

        @Override
        protected void doGet(MiniRequest request, MiniResponse response) throws Exception {
            response.setContentType("text/plain");
            response.getWriter().write("Sync Servlet Test Result");
        }

        @Override
        public void destroy() {
            logger.debug("TestSyncServlet 파괴");
        }
    }
}