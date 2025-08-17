package server.eventloop;

import server.core.routing.*;
import server.core.http.*;
import server.examples.*;
import java.util.concurrent.CompletableFuture;

/**
 * EventLoopServer 테스트 및 예시
 * ThreadedServerTest와 동일한 패턴으로 구성
 *
 * 목적:
 * - EventLoop 서버의 기본 동작 테스트
 * - 다양한 라우트 패턴 구현 예시
 * - 벤치마크용 엔드포인트 제공
 * - 개발자가 EventLoop 아키텍처를 이해할 수 있도록 도움
 */
public class EventLoopServerTest {

    /**
     * 메인 메서드 - 서버 시작점
     *
     * EventLoop 서버를 설정하고 시작하는 전체 과정을 보여줌
     *
     * @param args 명령줄 인수 (현재 사용하지 않음)
     * @throws Exception 서버 초기화 또는 실행 중 예외
     */
    public static void main(String[] args) throws Exception {
        // 콘솔에 테스트 시작 표시
        System.out.println("=== EventLoopServer Test ===\n");

        try {
            // 라우터 설정
            // createTestRouter(): 테스트용 라우트들이 설정된 Router 생성
            Router router = createTestRouter();

            // 서버 생성
            // EventLoopServer: 단일 스레드 + NIO Selector 기반 서버
            EventLoopServer server = new EventLoopServer(router);

            // 라우트 등록 (벤치마크용)
            // registerBenchmarkRoutes(): 성능 테스트용 엔드포인트들 추가
            registerBenchmarkRoutes(server);

            // 셧다운 훅 등록
            // setupShutdownHook(): Ctrl+C 등으로 종료시 안전한 정리 보장
            setupShutdownHook(server);

            // 서버 시작
            // start(8082): 포트 8082에서 서버 시작
            server.start(8082);

            // 사용자에게 서버 시작 정보와 사용 가능한 엔드포인트 안내
            System.out.println("EventLoopServer가 http://localhost:8082에서 실행 중");
            System.out.println("사용 가능한 엔드포인트:");
            System.out.println("  GET  http://localhost:8082/hello");
            System.out.println("  GET  http://localhost:8082/health");
            System.out.println("  GET  http://localhost:8082/cpu-intensive");
            System.out.println("  GET  http://localhost:8082/io-simulation");
            System.out.println("  GET  http://localhost:8082/server/info");
            System.out.println("  GET  http://localhost:8082/server/stats");
            System.out.println("\nCtrl+C로 서버를 중지하세요\n");

            // 메인 스레드 대기
            // join(): 현재 스레드가 종료될 때까지 무한 대기
            // 실제로는 Ctrl+C 신호로 인터럽트되어 종료됨
            Thread.currentThread().join();

        } catch (InterruptedException e) {
            // 인터럽트 신호 받음 (정상적인 종료)
            System.out.println("서버 인터럽트됨");
        } catch (Exception e) {
            // 기타 예외 발생
            System.err.println("서버 오류: " + e.getMessage());
            // printStackTrace(): 예외 스택 트레이스 출력
            e.printStackTrace();
        }
    }

    /**
     * 벤치마크용 EventLoopServer 생성 및 설정 (벤치마크에서 호출용)
     *
     * 외부 벤치마크 도구에서 EventLoop 서버를 테스트할 때 사용
     * ThreadedServer와 비교 테스트를 위해 동일한 엔드포인트 제공
     *
     * @param port 서버 포트 번호
     * @return 설정된 EventLoopServer 인스턴스
     * @throws Exception 서버 생성 실패시
     */
    public static EventLoopServer createBenchmarkServer(int port) throws Exception {
        // 벤치마크용 라우터 생성
        Router router = createBenchmarkRouter();

        // EventLoopServer 생성
        EventLoopServer server = new EventLoopServer(router);

        // 벤치마크용 라우트 등록
        registerBenchmarkRoutes(server);

        return server;
    }

    /**
     * 벤치마크용 라우터 생성
     *
     * 성능 테스트에 필요한 기본 라우트들을 설정
     * ThreadedServer와 동일한 엔드포인트를 제공하여 공정한 비교 가능
     *
     * @return 설정된 Router 인스턴스
     */
    private static Router createBenchmarkRouter() {
        Router router = new Router();

        // 기본 핸들러들
        // 루트 경로 - 서버 정보를 보여주는 인덱스 페이지
        router.get("/", RouteHandler.sync(request ->
                // createIndexPage(): EventLoop 서버용 인덱스 페이지 HTML 생성
                HttpResponse.html(createIndexPage())
        ));

        // 비동기 JSON API
        // CompletableFuture.supplyAsync(): 별도 스레드에서 비동기 작업 수행
        router.get("/api/test", request -> CompletableFuture.supplyAsync(() -> {
            // 가상의 비동기 작업
            try {
                // Thread.sleep(50): 50밀리초 대기 (EventLoop에서는 짧게)
                // EventLoop는 블로킹 작업을 피해야 하므로 짧은 시간으로 설정
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // interrupt(): 현재 스레드의 인터럽트 상태 설정
                Thread.currentThread().interrupt();
            }

            // JSON 응답 생성
            // Thread.currentThread().getName(): 현재 스레드 이름
            // System.currentTimeMillis(): 현재 시간 (밀리초)
            return HttpResponse.json(String.format(
                    "{ \"message\": \"비동기 응답\", \"thread\": \"%s\", \"timestamp\": %d, \"server\": \"eventloop\" }",
                    Thread.currentThread().getName(), System.currentTimeMillis()
            ));
        }));

        // 서버 상태 API
        router.get("/status", RouteHandler.sync(request -> {
            // 간단한 서버 상태 정보 반환
            return HttpResponse.json(
                    "{ \"server\": \"EventLoopServer\", \"status\": \"running\", " +
                            "\"thread\": \"" + Thread.currentThread().getName() + "\" }"
            );
        }));

        // 부하 테스트 엔드포인트 (EventLoop 최적화)
        router.get("/load-test", RouteHandler.sync(request -> {
            // 가벼운 CPU 작업 (EventLoop는 CPU 집약적 작업을 피해야 함)
            // System.currentTimeMillis(): 시작 시간 기록
            long start = System.currentTimeMillis();
            double result = 0;

            // ThreadedServer보다 10배 적게 계산 (10,000회)
            // EventLoop는 단일 스레드이므로 무거운 계산은 다른 스레드로 위임해야 함
            for (int i = 0; i < 10000; i++) {
                // Math.sqrt(): 제곱근 계산
                // Math.sin(): 사인 값 계산
                result += Math.sqrt(i) * Math.sin(i);
            }

            // 작업 소요 시간 계산
            long duration = System.currentTimeMillis() - start;

            // 결과를 JSON으로 반환
            return HttpResponse.json(String.format(
                    "{ \"computation\": %.2f, \"duration\": %d, \"thread\": \"%s\", \"server\": \"eventloop\" }",
                    result, duration, Thread.currentThread().getName()
            ));
        }));

        // 에러 테스트
        // {type}: 경로 파라미터 - 다양한 에러 타입 테스트
        router.get("/error/{type}", request -> {
            // getAttribute(): 요청에서 경로 파라미터 추출
            String type = request.getAttribute("path.type", String.class);

            // switch 문으로 에러 타입별 처리
            // != null ? type : "500": null 안전 처리
            switch (type != null ? type : "500") {
                case "400":
                    // completedFuture(): 이미 완료된 Future 반환
                    // badRequest(): 400 Bad Request 응답 생성
                    return CompletableFuture.completedFuture(HttpResponse.badRequest("테스트 400 에러"));
                case "404":
                    // notFound(): 404 Not Found 응답 생성
                    return CompletableFuture.completedFuture(HttpResponse.notFound("테스트 404 에러"));
                case "exception":
                    // RuntimeException: 의도적으로 예외 발생시켜 에러 처리 테스트
                    throw new RuntimeException("EventLoop 핸들러에서 테스트 예외");
                default:
                    // internalServerError(): 500 Internal Server Error 응답 생성
                    return CompletableFuture.completedFuture(HttpResponse.internalServerError("테스트 500 에러"));
            }
        });

        return router;
    }

    /**
     * 테스트용 라우터 생성 (확장된 기능)
     *
     * 벤치마크용 라우터에 추가적인 테스트 기능들을 더한 라우터
     * 개발 중에 다양한 기능을 테스트하기 위한 용도
     *
     * @return 확장된 기능이 포함된 Router 인스턴스
     */
    private static Router createTestRouter() {
        // 벤치마크 라우터를 기반으로 시작
        Router router = createBenchmarkRouter();

        // EventLoop 특화 미들웨어 추가
        // use(): 모든 요청에 대해 실행되는 미들웨어 등록
        router.use((request, next) -> {
            // System.nanoTime(): 고정밀 시간 측정 (나노초)
            long start = System.nanoTime();

            // 요청 정보 추출
            String method = request.getMethod().toString();
            String path = request.getPath();

            // next.handle(): 다음 핸들러로 요청 전달
            // thenApply(): CompletableFuture의 결과를 변환
            return next.handle(request).thenApply(response -> {
                // 응답 시간 계산
                long durationNanos = System.nanoTime() - start;
                double durationMs = durationNanos / 1_000_000.0; // 나노초를 밀리초로 변환

                // 요청 처리 정보를 콘솔에 출력
                // String.format(): printf 스타일 문자열 포맷팅
                System.out.println(String.format("[EventLoop] %s %s -> %s (%.2fms)",
                        method, path, response.getStatus(), durationMs));

                // CORS 헤더 추가
                // setHeader(): HTTP 응답 헤더 설정
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
     *
     * ThreadedServer와 동일한 엔드포인트를 제공하여 공정한 성능 비교 가능
     * EventLoop 특성에 맞게 일부 구현은 최적화됨
     *
     * @param server 라우트를 등록할 EventLoopServer 인스턴스
     */
    private static void registerBenchmarkRoutes(EventLoopServer server) {
        // ThreadedServerTest와 동일한 엔드포인트 제공

        // 헬스체크 (이미 기본 제공됨)
        server.get("/health", RouteHandler.sync(request ->
                HttpResponse.json("{\"status\":\"healthy\",\"server\":\"eventloop\"}")
        ));

        // Hello World
        server.get("/hello", RouteHandler.sync(request -> {
            // getQueryParameter(): URL 쿼리 스트링에서 파라미터 추출
            String name = request.getQueryParameter("name");

            // name이 null이면 기본값 "World" 사용
            if (name == null) name = "World";

            // text(): 단순 텍스트 응답 생성
            return HttpResponse.text("Hello, " + name + "! (EventLoop Server)");
        }));

        // CPU 집약적 작업 (EventLoop에 맞게 최적화)
        server.get("/cpu-intensive", request ->
                // getProcessor(): EventLoopServer에서 EventLoopProcessor 반환
                // executeAsync(): CPU 작업을 별도 스레드로 위임 (EventLoop 블로킹 방지)
                server.getProcessor().executeAsync(() -> {
                    // CPU 작업을 별도 스레드에서 수행 (EventLoop 블로킹 방지)
                    double result = 0;

                    // ThreadedServer와 동일한 계산량으로 공정한 비교
                    for (int i = 0; i < 100000; i++) {
                        result += Math.sqrt(i) * Math.sin(i);
                    }

                    // JSON 응답 생성
                    return HttpResponse.json(
                            String.format("{\"server\":\"eventloop\",\"result\":%.2f,\"thread\":\"%s\"}",
                                    result, Thread.currentThread().getName())
                    );
                })
        );

        // I/O 시뮬레이션 (EventLoop 방식 - 즉시 응답)
        server.get("/io-simulation", RouteHandler.sync(request -> {
            // EventLoop에서는 실제 블로킹 I/O를 피하고 즉시 응답
            // 실제 운영에서는 비동기 I/O 라이브러리 사용 권장
            return HttpResponse.json(
                    String.format("{\"server\":\"eventloop\",\"io\":\"completed\",\"thread\":\"%s\"}",
                            Thread.currentThread().getName())
            );
        }));

        // 동시성 테스트용 엔드포인트
        server.get("/concurrent", RouteHandler.sync(request -> {
            // 현재 서버 상태 정보를 JSON으로 반환
            return HttpResponse.json(String.format(
                    "{\"server\":\"eventloop\",\"timestamp\":%d,\"thread\":\"%s\",\"connections\":%d}",
                    System.currentTimeMillis(),                                    // 현재 시간
                    Thread.currentThread().getName(),                              // 현재 스레드 이름
                    server.getStats().getHandlerStats().getActiveConnections()    // 활성 연결 수
            ));
        }));
    }

    /**
     * 셧다운 훅 설정
     *
     * JVM 종료시 (Ctrl+C, SIGTERM 등) 서버를 안전하게 종료
     * 리소스 누수 방지와 정상적인 연결 정리를 보장
     *
     * @param server 종료할 EventLoopServer 인스턴스
     */
    private static void setupShutdownHook(EventLoopServer server) {
        // Runtime.getRuntime(): 현재 JVM 런타임 인스턴스
        // addShutdownHook(): JVM 종료시 실행할 스레드 등록
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[EventLoopServer] 종료 훅 실행됨");
            // stop(): 서버 안전 종료
            server.stop();
        }, "EventLoopServer-ShutdownHook")); // 스레드 이름 지정
    }

    /**
     * 인덱스 페이지 생성
     *
     * EventLoop 서버의 특징과 사용법을 설명하는 인터랙티브 HTML 페이지
     * 개발자가 EventLoop 아키텍처를 이해하고 테스트할 수 있도록 도움
     *
     * @return HTML 문자열
     */
    private static String createIndexPage() {
        // 여러 줄 문자열 리터럴로 HTML 구성
        // Thread.currentThread().getName(): 현재 스레드 이름 (EventLoop 메인 스레드)
        // System.currentTimeMillis(): 현재 시간 (페이지 생성 시점)
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>EventLoopServer Test</title>\n" +
                "    <style>\n" +
                // CSS 스타일링 - 현대적이고 시각적으로 매력적인 디자인
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
                "            <h1>EventLoop Server</h1>\n" +
                "            <p><strong>단일 스레드 + NIO Selector 아키텍처</strong></p>\n" +
                // 현재 스레드 정보 표시 (EventLoop 메인 스레드명)
                "            <p>스레드: <span class=\"highlight\">" + Thread.currentThread().getName() + "</span></p>\n" +
                // 페이지 생성 시간 표시
                "            <p>타임스탬프: <span class=\"highlight\">" + System.currentTimeMillis() + "</span></p>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"feature\">\n" +
                "            <h3>EventLoop 특징</h3>\n" +
                "            <ul>\n" +
                "                <li><strong>단일 이벤트 루프:</strong> 모든 I/O를 하나의 스레드에서 논블로킹 처리</li>\n" +
                "                <li><strong>NIO Selector:</strong> 수만 개의 동시 연결을 효율적으로 관리</li>\n" +
                "                <li><strong>제로 컨텍스트 스위칭:</strong> 스레드 전환 오버헤드 제거</li>\n" +
                "                <li><strong>메모리 효율적:</strong> 연결당 메모리 사용량 최소화</li>\n" +
                "                <li><strong>높은 처리량:</strong> I/O 집약적 작업에 최적화</li>\n" +
                "            </ul>\n" +
                "        </div>\n" +
                "        \n" +
                // 서버 통계 정보를 그리드 레이아웃으로 표시
                "        <div class=\"stats\">\n" +
                "            <div class=\"stat\">\n" +
                "                <h4>아키텍처</h4>\n" +
                "                <div>EventLoop</div>\n" +
                "            </div>\n" +
                "            <div class=\"stat\">\n" +
                "                <h4>포트</h4>\n" +
                "                <div>8082</div>\n" +
                "            </div>\n" +
                "            <div class=\"stat\">\n" +
                "                <h4>동시성 모델</h4>\n" +
                "                <div>단일 스레드</div>\n" +
                "            </div>\n" +
                "            <div class=\"stat\">\n" +
                "                <h4>I/O 모델</h4>\n" +
                "                <div>논블로킹 NIO</div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        \n" +
                // API 엔드포인트 목록
                "        <div class=\"feature\">\n" +
                "            <h3>API 엔드포인트</h3>\n" +
                "            <div class=\"endpoints\">\n" +
                "                <div class=\"endpoint\">\n" +
                "                    <span class=\"method\">GET</span> \n" +
                "                    <a href=\"/hello\">/hello</a> - EventLoop hello world\n" +
                "                </div>\n" +
                "                \n" +
                "                <div class=\"endpoint\">\n" +
                "                    <span class=\"method\">GET</span> \n" +
                "                    <a href=\"/api/test\">/api/test</a> - 비동기 JSON API\n" +
                "                </div>\n" +
                "                \n" +
                "                <div class=\"endpoint\">\n" +
                "                    <span class=\"method\">GET</span> \n" +
                "                    <a href=\"/server/info\">/server/info</a> - 서버 정보\n" +
                "                </div>\n" +
                "                \n" +
                "                <div class=\"endpoint\">\n" +
                "                    <span class=\"method\">GET</span> \n" +
                "                    <a href=\"/server/stats\">/server/stats</a> - 실시간 통계\n" +
                "                </div>\n" +
                "                \n" +
                "                <div class=\"endpoint\">\n" +
                "                    <span class=\"method\">GET</span> \n" +
                "                    <a href=\"/health\">/health</a> - 헬스체크\n" +
                "                </div>\n" +
                "                \n" +
                "                <div class=\"endpoint\">\n" +
                "                    <span class=\"method\">GET</span> \n" +
                "                    <a href=\"/load-test\">/load-test</a> - EventLoop 최적화된 부하 테스트\n" +
                "                </div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        \n" +
                // EventLoop 서버 테스트 방법 안내
                "        <div class=\"feature\">\n" +
                "            <h3>EventLoop 테스트</h3>\n" +
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
                // EventLoop 서버의 성능 특성 설명
                "        <div class=\"feature\">\n" +
                "            <h3>성능 특성</h3>\n" +
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