package server.hybrid;

// 로깅 관련 클래스들을 임포트 - SLF4J와 유사한 구조의 사용자 정의 로거
import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
// HTTP 관련 핵심 클래스들을 임포트 - HTTP 요청/응답, 헤더, 상태 등을 처리
import server.core.http.*;
// 라우팅 관련 클래스들을 임포트 - URL 패턴 매칭과 핸들러 연결
import server.core.routing.*;
// 미니 서블릿 컨테이너 관련 클래스들을 임포트 - 경량화된 서블릿 구현
import server.core.mini.*;

// Java I/O 예외 처리를 위한 임포트
import java.io.IOException;
// 비동기 처리를 위한 CompletableFuture 임포트 - Java 8의 비동기 프로그래밍 지원
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

    // LoggerFactory.getLogger()를 사용하여 클래스별 로거 인스턴스 생성
    // static final로 선언하여 클래스 로딩 시 한 번만 초기화되고 변경 불가능하게 함
    private static final Logger logger = LoggerFactory.getLogger(HybridServerTest.class);

    // 메인 메서드 - JVM에서 프로그램 시작점
    public static void main(String[] args) {
        // args.length > 0: 명령행 인수가 있는지 확인
        // "test".equals(args[0]): 첫 번째 인수가 "test"인지 확인 (NullPointerException 방지를 위해 리터럴을 앞에 배치)
        if (args.length > 0 && "test".equals(args[0])) {
            // 테스트 모드 - 자동화된 테스트 실행
            runAllTests();
        } else {
            // 서버 실행 모드 - 실제 서버 구동
            runServer();
        }
    }

    /**
     * 실제 서버 실행 (기본 모드)
     */
    private static void runServer() {
        // Logger.info() 메서드를 사용하여 INFO 레벨 로그 출력
        // 서버 시작을 사용자에게 알리는 로그
        logger.info("하이브리드 서버를 시작합니다");

        try {
            // HybridServer 인스턴스 생성 - 포트 8081에서 수신 대기
            // 포트 번호는 생성자 매개변수로 전달되어 서버 소켓 바인딩에 사용됨
            HybridServer server = new HybridServer(8081);

            // 실제 서블릿들을 서블릿 컨테이너에 등록
            // server.getServletContainer()는 HybridMiniServletContainer 인스턴스를 반환
            registerServlets(server.getServletContainer());

            // 기본 라우트 설정 - URL 패턴과 핸들러 함수를 매핑
            // server.getRouter()는 Router 인스턴스를 반환하여 라우트 규칙을 관리
            setupBasicRoutes(server.getRouter());

            // 컨텍스트 스위칭 라우트 설정 - I/O 바운드 작업을 위한 특별한 라우트
            // server.getSwitchingHandler()는 ContextSwitchingHandler를 반환
            setupContextSwitchingRoutes(server.getRouter(), server.getSwitchingHandler());

            // 서버 시작 - 실제로 포트에서 요청 수신 시작
            // 내부적으로 ServerSocket을 생성하고 바인딩함
            server.start();

            // 사용자에게 서버 구동 상태와 사용 가능한 엔드포인트 정보 제공
            logger.info("하이브리드 서버가 실행 중입니다 - http://localhost:8081");
            logger.info("사용 가능한 엔드포인트:");
            logger.info("  GET  http://localhost:8081/hello");
            logger.info("  GET  http://localhost:8081/api/users");
            logger.info("  POST http://localhost:8081/api/users");
            logger.info("  GET  http://localhost:8081/static/test.js");
            logger.info("  GET  http://localhost:8081/upload");
            logger.info("  GET  http://localhost:8081/load-test");
            logger.info("  GET  http://localhost:8081/test/db");
            logger.info("  GET  http://localhost:8081/test/api");
            logger.info("\nCtrl+C로 서버를 종료하세요\n");

            // Runtime.getRuntime(): JVM 런타임 인스턴스 획득
            // addShutdownHook(): JVM 종료 시 실행될 훅(콜백) 등록
            // 사용자가 Ctrl+C를 누르거나 프로세스가 종료될 때 깔끔한 정리 작업 수행
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("서버를 종료하는 중...");
                // 서버 정리 작업 수행 - 소켓 닫기, 스레드 풀 종료 등
                server.stop();
            }));

            // Thread.currentThread(): 현재 실행 중인 스레드(메인 스레드) 참조 획득
            // join(): 현재 스레드가 종료될 때까지 대기 - 메인 스레드를 계속 살려둠
            // 이렇게 하지 않으면 main 메서드가 종료되어 프로그램이 끝남
            Thread.currentThread().join();

        } catch (Exception e) {
            // 모든 예외를 포괄적으로 처리 - IOException, InterruptedException 등
            // Logger.error()는 ERROR 레벨 로그와 함께 스택 트레이스 출력
            logger.error("서버 실행에 실패했습니다", e);
            // System.exit(1): 오류 상태(1)로 JVM 종료
            // 0이 아닌 값은 비정상 종료를 의미함
            System.exit(1);
        }
    }

    /**
     * 모든 테스트 실행
     */
    private static void runAllTests() {
        try {
            // 각 테스트를 순차적으로 실행 - 서로 다른 포트를 사용하여 충돌 방지
            runBasicTests();           // 기본 기능 테스트
            runContextSwitchingTests(); // 컨텍스트 스위칭 테스트
            runAsyncServletTests();    // 비동기 서블릿 테스트
            runPerformanceTests();     // 성능 테스트

            logger.info("모든 하이브리드 서버 테스트가 완료되었습니다");

        } catch (Exception e) {
            // 테스트 실행 중 발생한 예외 처리
            logger.error("하이브리드 서버 테스트가 실패했습니다", e);
            // 테스트 실패 시 프로그램 종료
            System.exit(1);
        }
    }

    /**
     * 실제 서블릿들 등록
     */
    private static void registerServlets(HybridMiniServletContainer container) {
        // registerServlet() 메서드는 세 개의 매개변수를 받음:
        // 1. 서블릿 이름 (문자열) - 컨테이너 내에서 서블릿을 식별하는 고유 이름
        // 2. 서블릿 인스턴스 - MiniAsyncServlet을 상속받은 구현체
        // 3. URL 패턴 - 이 서블릿이 처리할 URL 패턴 (와일드카드 * 지원)

        // Hello World 비동기 서블릿 등록 - 간단한 인사말 응답
        container.registerServlet("HelloWorld", new HelloWorldAsyncServlet(), "/hello");

        // User API 비동기 서블릿 등록 - RESTful API 스타일의 사용자 관리
        // "/api/users/*" 패턴으로 하위 경로까지 모두 처리
        container.registerServlet("UserApi", new UserApiAsyncServlet(), "/api/users/*");

        // 정적 파일 비동기 서블릿 등록 - CSS, JS, 이미지 등 정적 리소스 제공
        container.registerServlet("StaticFile", new StaticFileAsyncServlet(), "/static/*");

        // 파일 업로드 비동기 서블릿 등록 - 멀티파트 폼 데이터 처리
        container.registerServlet("FileUpload", new FileUploadAsyncServlet(), "/upload");

        // 로드 테스트 비동기 서블릿 등록 - 성능 테스트용 CPU 집약적 작업 수행
        container.registerServlet("LoadTest", new LoadTestAsyncServlet(), "/load-test");

        logger.info("모든 비동기 서블릿 등록이 완료되었습니다");
    }

    /**
     * 기본 기능 테스트
     */
    private static void runBasicTests() throws IOException {
        logger.info("기본 기능 테스트를 시작합니다");

        // 테스트용 서버 인스턴스 생성 - 실제 서버와 다른 포트(18081) 사용
        // 포트 충돌을 방지하기 위해 테스트마다 다른 포트 사용
        HybridServer server = new HybridServer(18081);

        // 기본 라우트 설정 - 테스트용 간단한 엔드포인트들
        setupBasicRoutes(server.getRouter());

        try {
            // 서버 시작
            server.start();
            // Thread.sleep(): 현재 스레드를 지정된 밀리초 동안 일시 정지
            // 서버가 완전히 시작될 때까지 1초 대기
            Thread.sleep(1000);

            // 서버 상태 확인 - getStatus()는 현재 서버의 실행 상태 정보를 반환
            HybridServer.ServerStatus status = server.getStatus();
            // assert 문: 조건이 false일 경우 AssertionError 발생
            // isRunning()이 false를 반환하면 테스트 실패로 간주
            assert status.isRunning() : "서버가 실행되지 않았습니다";

            // 테스트 통과 로그 출력 - 상태 정보와 함께
            logger.info("기본 기능 테스트가 통과되었습니다 - {}", status);

        } catch (InterruptedException e) {
            // InterruptedException: 스레드가 대기 중일 때 인터럽트 신호를 받은 경우
            // Thread.currentThread().interrupt(): 인터럽트 상태를 다시 설정
            // 인터럽트 신호를 무시하지 않고 상위 호출자에게 전파
            Thread.currentThread().interrupt();
            logger.warn("테스트 중 인터럽트가 발생했습니다");
        } finally {
            // finally 블록: 예외 발생 여부와 관계없이 항상 실행
            // 서버 리소스 정리 - 소켓 닫기, 스레드 풀 종료 등
            server.stop();
        }

        logger.info("기본 기능 테스트가 완료되었습니다\n");
    }

    /**
     * 컨텍스트 스위칭 테스트
     */
    private static void runContextSwitchingTests() throws IOException {
        logger.info("컨텍스트 스위칭 테스트를 시작합니다");

        // 다른 포트(18082)로 테스트 서버 생성
        HybridServer server = new HybridServer(18082);
        // ContextSwitchingHandler: I/O 바운드 작업 시 스레드를 효율적으로 관리하는 핸들러
        ContextSwitchingHandler switchingHandler = server.getSwitchingHandler();

        // 컨텍스트 스위칭 전용 라우트 설정
        setupContextSwitchingRoutes(server.getRouter(), switchingHandler);

        try {
            server.start();
            Thread.sleep(1000);

            // 초기 통계 확인 - 테스트 시작 전 상태
            // getStats(): 컨텍스트 스위칭 발생 횟수, 성능 지표 등을 포함한 통계 정보
            ContextSwitchingHandler.SwitchingStats initialStats = switchingHandler.getStats();
            // getTotalSwitchOuts(): 지금까지 발생한 컨텍스트 스위치 아웃 총 횟수
            assert initialStats.getTotalSwitchOuts() == 0 : "초기 스위치 아웃 횟수가 0이 아닙니다";

            // 모의 요청으로 컨텍스트 스위칭 테스트 실행
            testContextSwitching(switchingHandler);

            // 최종 통계 확인 - 테스트 후 상태
            ContextSwitchingHandler.SwitchingStats finalStats = switchingHandler.getStats();
            // 컨텍스트 스위칭이 실제로 발생했는지 확인
            assert finalStats.getTotalSwitchOuts() > 0 : "컨텍스트 스위칭이 발생하지 않았습니다";

            logger.info("컨텍스트 스위칭 테스트가 통과되었습니다 - {}", finalStats);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("테스트 중 인터럽트가 발생했습니다");
        } finally {
            server.stop();
        }

        logger.info("컨텍스트 스위칭 테스트가 완료되었습니다\n");
    }

    /**
     * 비동기 서블릿 테스트
     */
    private static void runAsyncServletTests() throws IOException {
        logger.info("비동기 서블릿 테스트를 시작합니다");

        // 또 다른 포트(18083)로 테스트 서버 생성
        HybridServer server = new HybridServer(18083);
        // getServletContainer(): 서블릿들을 관리하는 컨테이너 인스턴스 반환
        HybridMiniServletContainer container = server.getServletContainer();

        // 테스트용 서블릿들 등록
        container.registerServlet("HelloWorldAsync", new HelloWorldAsyncServlet(), "/test/hello");
        container.registerServlet("UserApiAsync", new UserApiAsyncServlet(), "/test/users");

        try {
            server.start();
            Thread.sleep(1000);

            // 서블릿 등록 확인
            // getServletNames(): 등록된 모든 서블릿의 이름 목록을 Set으로 반환
            // contains(): Set에 특정 요소가 포함되어 있는지 확인 (boolean 반환)
            assert container.getServletNames().contains("HelloWorldAsync") : "Hello World 서블릿 등록에 실패했습니다";
            assert container.getServletNames().contains("UserApiAsync") : "User API 서블릿 등록에 실패했습니다";

            // 컨테이너 통계 확인
            // getStats(): 서블릿 개수, 요청 처리 횟수 등의 통계 정보
            HybridMiniServletContainer.ContainerStats stats = container.getStats();
            // getServletCount(): 등록된 서블릿의 총 개수
            assert stats.getServletCount() == 2 : "서블릿 개수가 일치하지 않습니다";

            logger.info("비동기 서블릿 테스트가 통과되었습니다 - {}", stats);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("테스트 중 인터럽트가 발생했습니다");
        } finally {
            server.stop();
        }

        logger.info("비동기 서블릿 테스트가 완료되었습니다\n");
    }

    /**
     * 성능 테스트
     */
    private static void runPerformanceTests() throws IOException {
        logger.info("성능 테스트를 시작합니다");

        // 마지막 포트(18084)로 테스트 서버 생성
        HybridServer server = new HybridServer(18084);

        // 성능 테스트 전용 라우트 설정
        setupPerformanceRoutes(server.getRouter());

        // 로드 테스트용 서블릿 등록
        server.getServletContainer().registerServlet("LoadTest", new LoadTestAsyncServlet(), "/servlet/load");

        try {
            server.start();
            Thread.sleep(1000);

            // 성능 측정 시작 시점 기록
            // System.currentTimeMillis(): 현재 시간을 밀리초로 반환 (1970년 1월 1일 UTC 기준)
            long startTime = System.currentTimeMillis();

            // 동시 요청 시뮬레이션 실행
            simulateLoad(server);

            // 성능 측정 종료 시점 기록
            long endTime = System.currentTimeMillis();
            // 총 소요 시간 계산
            long duration = endTime - startTime;

            // 성능 통계 수집
            HybridServer.ServerStatus status = server.getStatus();

            // 성능 테스트 결과 출력
            logger.info("성능 테스트 결과:");
            logger.info("- 실행 시간: {}ms", duration);
            // getTotalRequests(): 서버가 처리한 총 요청 수
            logger.info("- 총 요청 수: {}", status.getTotalRequests());
            // getContextSwitches(): 발생한 컨텍스트 스위치 총 횟수
            logger.info("- 컨텍스트 스위치: {}", status.getContextSwitches());
            // getActiveThreads(): 현재 활성 상태인 스레드 수
            // getTotalThreads(): 스레드 풀의 총 스레드 수
            logger.info("- 활성 스레드: {}/{}", status.getActiveThreads(), status.getTotalThreads());

            // 성능 테스트 시간 제한 확인 (10초 이내)
            assert duration < 10000 : "성능 테스트 시간이 초과되었습니다";

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("테스트 중 인터럽트가 발생했습니다");
        } finally {
            server.stop();
        }

        logger.info("성능 테스트가 완료되었습니다\n");
    }

    // === 테스트 헬퍼 메서드들 ===

    // Router에 기본적인 라우트들을 설정하는 메서드
    private static void setupBasicRoutes(Router router) {
        // addRoute() 메서드: HTTP 메서드, URL 패턴, 핸들러 함수를 연결
        // HttpMethod.GET: HTTP GET 요청을 나타내는 열거형
        // 람다 표현식 (request) -> { ... }: 요청을 받아 응답을 반환하는 함수
        router.addRoute(HttpMethod.GET, "/test/hello", (request) -> {
            // CompletableFuture.completedFuture(): 이미 완료된 상태의 Future 객체 생성
            // 비동기 처리가 필요 없는 간단한 응답에 사용
            return CompletableFuture.completedFuture(
                    // HttpResponse.ok(): 200 OK 상태 코드와 함께 응답 생성
                    HttpResponse.ok("Hello from Hybrid Server Test")
            );
        });

        // 서버 상태 확인용 엔드포인트
        router.addRoute(HttpMethod.GET, "/test/status", (request) -> {
            return CompletableFuture.completedFuture(
                    HttpResponse.ok("Status: OK")
                            // setHeader(): HTTP 응답 헤더 설정
                            // Content-Type 헤더로 응답 데이터 타입 명시
                            .setHeader("Content-Type", "text/plain")
            );
        });

        // 인덱스 페이지 - 루트 경로 "/"에 대한 처리
        router.addRoute(HttpMethod.GET, "/", (request) -> {
            return CompletableFuture.completedFuture(
                    // HttpResponse.html(): HTML 컨텐츠를 위한 응답 생성 (Content-Type: text/html 자동 설정)
                    HttpResponse.html(createIndexPage())
            );
        });
    }

    // 컨텍스트 스위칭 전용 라우트들을 설정하는 메서드
    private static void setupContextSwitchingRoutes(Router router, ContextSwitchingHandler handler) {
        // 데이터베이스 작업 시뮬레이션 라우트
        router.addRoute(HttpMethod.GET, "/test/db", (request) -> {
            // executeDbOperation(): DB 작업을 별도 스레드에서 실행하고 컨텍스트 스위칭 수행
            // 첫 번째 매개변수: HTTP 요청 객체
            // 두 번째 매개변수: 실제 DB 작업을 수행할 함수 (Function<HttpRequest, String>)
            return handler.executeDbOperation(request, req -> {
                // DB 작업 시뮬레이션
                try {
                    // Thread.sleep(): 현재 스레드를 100ms 동안 블로킹하여 DB 지연 시뮬레이션
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // 인터럽트 발생 시 스레드의 인터럽트 상태 복원
                    Thread.currentThread().interrupt();
                }
                // req.getPath(): 요청 URL의 경로 부분 반환
                return "DB Result for " + req.getPath();
            }).thenApply(result -> HttpResponse.ok(result)); // thenApply(): Future의 결과를 변환 (String -> HttpResponse)
        });

        // API 호출 시뮬레이션 라우트
        router.addRoute(HttpMethod.GET, "/test/api", (request) -> {
            // executeApiCall(): 외부 API 호출을 별도 스레드에서 실행
            return handler.executeApiCall(request, req -> {
                // API 호출 시뮬레이션
                try {
                    // 200ms 지연으로 외부 API 호출 시뮬레이션 (DB보다 더 오래 걸림)
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "API Result for " + req.getPath();
            }).thenApply(result -> HttpResponse.ok(result));
        });
    }

    // 성능 테스트용 라우트들을 설정하는 메서드
    private static void setupPerformanceRoutes(Router router) {
        router.addRoute(HttpMethod.GET, "/test/load", (request) -> {
            // CompletableFuture.supplyAsync(): 별도 스레드에서 작업을 비동기로 실행
            // Supplier 함수를 받아서 결과를 반환하는 CompletableFuture 생성
            return CompletableFuture.supplyAsync(() -> {
                // CPU 집약적 작업 시뮬레이션
                int result = 0;
                // 10만 번의 반복으로 CPU 부하 생성
                for (int i = 0; i < 100000; i++) {
                    result += i; // 단순한 산술 연산으로 CPU 사용
                }
                return HttpResponse.ok("Load test result: " + result);
            });
        });
    }

    // 컨텍스트 스위칭 기능을 테스트하는 메서드
    private static void testContextSwitching(ContextSwitchingHandler handler) {
        // 모의 HTTP 요청 생성 - 실제 클라이언트 요청을 시뮬레이션
        HttpRequest testRequest = new HttpRequest(
                HttpMethod.GET,           // HTTP 메서드
                "/test/switch",          // 요청 경로
                "HTTP/1.1",              // HTTP 프로토콜 버전
                new HttpHeaders(),       // 빈 헤더 객체
                new byte[0]              // 빈 바디 (GET 요청이므로)
        );

        // 컨텍스트 스위칭 작업 실행
        CompletableFuture<String> future = handler.executeDbOperation(testRequest, req -> {
            return "Context switching test result";
        });

        try {
            // future.get(): CompletableFuture의 결과를 동기적으로 기다림 (블로킹)
            // 작업이 완료될 때까지 현재 스레드를 대기시킴
            String result = future.get();
            // String.contains(): 문자열에 특정 부분 문자열이 포함되어 있는지 확인
            assert result.contains("test result") : "컨텍스트 스위칭 결과가 일치하지 않습니다";
            // Logger.debug(): DEBUG 레벨 로그 출력 (일반적으로 개발/디버깅 시에만 표시)
            logger.debug("컨텍스트 스위칭 테스트 결과: {}", result);
        } catch (Exception e) {
            // ExecutionException, InterruptedException 등을 포괄적으로 처리
            // RuntimeException: 체크되지 않은 예외로 변환하여 상위로 전파
            throw new RuntimeException("컨텍스트 스위칭 테스트가 실패했습니다", e);
        }
    }

    // 서버에 부하를 가하는 시뮬레이션 메서드
    private static void simulateLoad(HybridServer server) {
        // 간단한 부하 시뮬레이션
        try {
            // 2초간 서버를 실행 상태로 유지하여 부하 테스트
            Thread.sleep(2000);
            logger.debug("부하 시뮬레이션이 완료되었습니다");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 인덱스 페이지 HTML 생성
     */
    private static String createIndexPage() {
        // 멀티라인 문자열을 + 연산자로 연결하여 HTML 페이지 구성
        // 각 줄은 별도의 문자열 리터럴로 작성되어 가독성 향상
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>HybridServer Test</title>\n" +
                "    <style>\n" +
                // CSS 스타일 정의 - 인라인 스타일시트 사용
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
                // Thread.currentThread().getName(): 현재 스레드의 이름 반환
                "            <strong>Thread:</strong> " + Thread.currentThread().getName() + "<br>\n" +
                // System.currentTimeMillis(): 현재 타임스탬프 (밀리초)
                "            <strong>Timestamp:</strong> " + System.currentTimeMillis() + "<br>\n" +
                "        </div>\n" +
                "        \n" +
                "        <h2>비동기 서블릿 엔드포인트:</h2>\n" +
                "        \n" +
                // 각 엔드포인트를 HTML 링크로 제공하여 브라우저에서 직접 테스트 가능
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
                // <pre> 태그: 사전 포맷된 텍스트로 공백과 줄바꿈이 그대로 유지됨
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