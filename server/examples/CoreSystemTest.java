package server.examples;

import server.core.mini.*;
import server.core.http.*;
import server.core.routing.*;
import java.util.concurrent.CompletableFuture;

/**
 * 코어 시스템 테스트 러너
 */
public class CoreSystemTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== JavaServerArchitectures Core System Test ===\n");

        // 1. HTTP 기본 클래스들 테스트
        testHttpBasics();

        // 2. 라우터 테스트
        testRouter();

        // 3. 서블릿 테스트
        testServlets();

        // 4. 통합 테스트
        testIntegration();

        System.out.println("\n=== All Tests Completed ===");
    }

    /**
     * HTTP 기본 클래스들 테스트
     */
    private static void testHttpBasics() {
        System.out.println("1. Testing HTTP Basic Classes...");

        // HttpMethod 테스트
        System.out.println("  - HttpMethod:");
        for (HttpMethod method : HttpMethod.values()) {
            System.out.println("    " + method + " (safe=" + method.isSafe() +
                    ", idempotent=" + method.isIdempotent() +
                    ", canHaveBody=" + method.canHaveBody() + ")");
        }

        // HttpStatus 테스트
        System.out.println("  - HttpStatus:");
        HttpStatus[] testStatuses = {
                HttpStatus.OK, HttpStatus.CREATED, HttpStatus.NOT_FOUND,
                HttpStatus.INTERNAL_SERVER_ERROR
        };
        for (HttpStatus status : testStatuses) {
            System.out.println("    " + status + " (success=" + status.isSuccess() +
                    ", error=" + status.isError() + ")");
        }

        // HttpHeaders 테스트
        System.out.println("  - HttpHeaders:");
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.add("Accept", "text/html");
        headers.add("Accept", "application/json");
        headers.setKeepAlive(true);

        System.out.println("    Content-Type: " + headers.getContentType());
        System.out.println("    Accept values: " + headers.getAll("Accept"));
        System.out.println("    Keep-Alive: " + headers.isKeepAlive());
        System.out.println("    Header count: " + headers.size());

        // HttpRequest 테스트
        System.out.println("  - HttpRequest:");
        HttpRequest request = new HttpRequest(
                HttpMethod.GET,
                "/test?name=John&age=25",
                "HTTP/1.1",
                headers,
                "test body".getBytes()
        );

        System.out.println("    Path: " + request.getPath());
        System.out.println("    Query String: " + request.getQueryString());
        System.out.println("    Query Parameter 'name': " + request.getQueryParameter("name"));
        System.out.println("    Body: " + request.getBodyAsString());
        System.out.println("    Is JSON: " + request.isJsonRequest());

        // HttpResponse 테스트
        System.out.println("  - HttpResponse:");
        HttpResponse response = HttpResponse.ok("Hello World")
                .setContentType("text/plain");

        System.out.println("    Status: " + response.getStatus());
        System.out.println("    Body: " + response.getBodyAsString());
        System.out.println("    Content-Type: " + response.getHeaders().getContentType());

        System.out.println("  ✓ HTTP Basic Classes test completed\n");
    }

    /**
     * 라우터 테스트
     */
    private static void testRouter() throws Exception {
        System.out.println("2. Testing Router...");

        Router router = TestRouterSetup.createTestRouter();
        System.out.println("  - Created router with " + router.getRouteCount() + " routes");

        // 테스트 요청들
        HttpRequest[] testRequests = {
                new HttpRequest(HttpMethod.GET, "/", "HTTP/1.1", new HttpHeaders(), new byte[0]),
                new HttpRequest(HttpMethod.GET, "/hello?name=Alice", "HTTP/1.1", new HttpHeaders(), new byte[0]),
                new HttpRequest(HttpMethod.GET, "/users/123", "HTTP/1.1", new HttpHeaders(), new byte[0]),
                new HttpRequest(HttpMethod.GET, "/numbers/456", "HTTP/1.1", new HttpHeaders(), new byte[0]),
                new HttpRequest(HttpMethod.GET, "/api/users", "HTTP/1.1", new HttpHeaders(), new byte[0]),
                new HttpRequest(HttpMethod.GET, "/nonexistent", "HTTP/1.1", new HttpHeaders(), new byte[0])
        };

        System.out.println("  - Testing routes:");
        for (HttpRequest req : testRequests) {
            try {
                HttpResponse resp = router.routeWithMiddlewares(req).get();
                System.out.println("    " + req.getMethod() + " " + req.getUri() +
                        " -> " + resp.getStatus());
            } catch (Exception e) {
                System.out.println("    " + req.getMethod() + " " + req.getUri() +
                        " -> ERROR: " + e.getMessage());
            }
        }

        System.out.println("  ✓ Router test completed\n");
    }

    /**
     * 서블릿 테스트
     */
    private static void testServlets() throws Exception {
        System.out.println("3. Testing Servlets...");

        // MiniContext 생성
        MiniContext context = new MiniContext("/test");
        context.setInitParameter("debug", "true");

        // HelloWorldServlet 테스트
        System.out.println("  - Testing HelloWorldServlet:");
        HelloWorldServlet helloServlet = new HelloWorldServlet();
        helloServlet.init(context);

        HttpRequest httpReq = new HttpRequest(
                HttpMethod.GET, "/hello?name=TestUser", "HTTP/1.1",
                new HttpHeaders(), new byte[0]
        );
        MiniRequest miniReq = new MiniRequest(httpReq, context);
        MiniResponse miniResp = new MiniResponse();

        HttpResponse response = helloServlet.service(miniReq, miniResp);
        System.out.println("    Status: " + response.getStatus());
        System.out.println("    Content-Type: " + response.getHeaders().getContentType());
        System.out.println("    Body length: " + response.getBodyLength());

        // UserApiServlet 테스트
        System.out.println("  - Testing UserApiServlet:");
        UserApiServlet userServlet = new UserApiServlet();
        userServlet.init(context);

        // GET 요청 테스트
        HttpRequest getUserReq = new HttpRequest(
                HttpMethod.GET, "/api/users", "HTTP/1.1",
                new HttpHeaders(), new byte[0]
        );
        MiniRequest getUserMiniReq = new MiniRequest(getUserReq, context);
        MiniResponse getUserMiniResp = new MiniResponse();

        HttpResponse getUserResponse = userServlet.serviceAsync(getUserMiniReq, getUserMiniResp).get();
        System.out.println("    GET /api/users -> " + getUserResponse.getStatus());
        System.out.println("    Response: " + getUserResponse.getBodyAsString());

        // POST 요청 테스트
        String userJson = "{ \"id\": \"1\", \"name\": \"John Doe\", \"email\": \"john@example.com\" }";
        HttpRequest postUserReq = new HttpRequest(
                HttpMethod.POST, "/api/users", "HTTP/1.1",
                new HttpHeaders().setContentType("application/json"),
                userJson.getBytes()
        );
        MiniRequest postUserMiniReq = new MiniRequest(postUserReq, context);
        MiniResponse postUserMiniResp = new MiniResponse();

        HttpResponse postUserResponse = userServlet.serviceAsync(postUserMiniReq, postUserMiniResp).get();
        System.out.println("    POST /api/users -> " + postUserResponse.getStatus());
        System.out.println("    Response: " + postUserResponse.getBodyAsString());

        // 정리
        helloServlet.destroy();
        userServlet.destroy();

        System.out.println("  ✓ Servlets test completed\n");
    }

    /**
     * 통합 테스트
     */
    private static void testIntegration() throws Exception {
        System.out.println("4. Testing Integration...");

        // HTTP 파싱 테스트
        System.out.println("  - Testing HTTP parsing:");
        String rawHttpRequest =
                "GET /hello?name=World HTTP/1.1\r\n" +
                        "Host: localhost:8080\r\n" +
                        "User-Agent: TestClient/1.0\r\n" +
                        "Accept: text/html,application/json\r\n" +
                        "Connection: keep-alive\r\n" +
                        "\r\n";

        try {
            java.io.ByteArrayInputStream input = new java.io.ByteArrayInputStream(
                    rawHttpRequest.getBytes()
            );
            HttpRequest parsed = HttpParser.parseRequest(input);

            System.out.println("    Parsed method: " + parsed.getMethod());
            System.out.println("    Parsed URI: " + parsed.getUri());
            System.out.println("    Parsed headers: " + parsed.getHeaders().size());
            System.out.println("    Host header: " + parsed.getHeader("Host"));
            System.out.println("    User-Agent: " + parsed.getHeader("User-Agent"));

        } catch (Exception e) {
            System.out.println("    Parsing failed: " + e.getMessage());
        }

        // 응답 생성 테스트
        System.out.println("  - Testing response generation:");
        HttpResponse testResponse = HttpResponse.builder(HttpStatus.OK)
                .contentType("application/json")
                .body("{ \"message\": \"Integration test successful\" }")
                .keepAlive(true)
                .build();

        byte[] responseBytes = testResponse.toByteArray();
        String responseString = new String(responseBytes);

        System.out.println("    Response size: " + responseBytes.length + " bytes");
        System.out.println("    Response preview:");
        String[] lines = responseString.split("\r\n");
        for (int i = 0; i < Math.min(5, lines.length); i++) {
            System.out.println("      " + lines[i]);
        }
        if (lines.length > 5) {
            System.out.println("      ... (" + (lines.length - 5) + " more lines)");
        }

        // 메모리 및 성능 정보
        System.out.println("  - System information:");
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        System.out.println("    Used memory: " + usedMemory + " MB");
        System.out.println("    Free memory: " + freeMemory + " MB");
        System.out.println("    Total memory: " + totalMemory + " MB");
        System.out.println("    Available processors: " + runtime.availableProcessors());

        System.out.println("  ✓ Integration test completed\n");
    }

    /**
     * 성능 벤치마크 테스트
     */
    public static void runBenchmark() {
        System.out.println("5. Running Performance Benchmark...");

        Router router = TestRouterSetup.createTestRouter();
        int iterations = 10000;

        System.out.println("  - Testing " + iterations + " requests...");

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            try {
                HttpRequest request = new HttpRequest(
                        HttpMethod.GET,
                        "/hello?name=User" + i,
                        "HTTP/1.1",
                        new HttpHeaders(),
                        new byte[0]
                );

                HttpResponse response = router.route(request).get();

                if (response.getStatus() != HttpStatus.OK) {
                    System.out.println("    Error at iteration " + i + ": " + response.getStatus());
                }

            } catch (Exception e) {
                System.out.println("    Exception at iteration " + i + ": " + e.getMessage());
            }
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        double requestsPerSecond = (double) iterations / duration * 1000;

        System.out.println("  - Benchmark results:");
        System.out.println("    Total time: " + duration + " ms");
        System.out.println("    Requests per second: " + String.format("%.2f", requestsPerSecond));
        System.out.println("    Average time per request: " + String.format("%.3f", (double) duration / iterations) + " ms");

        System.out.println("  ✓ Benchmark completed\n");
    }
}