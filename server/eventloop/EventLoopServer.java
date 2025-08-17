package server.eventloop;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.routing.Router;
import server.core.routing.RouteHandler;
import server.core.http.HttpResponse;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 단일 스레드 + Selector 기반 EventLoop 서버
 *
 * 특징:
 * - 단일 스레드에서 모든 I/O 이벤트 처리
 * - NIO Selector를 사용한 논블로킹 I/O
 * - 높은 동시 연결 처리 능력 (10,000+ 연결)
 * - 메모리 효율적 (스레드 풀 없음)
 * - CPU 효율적 (컨텍스트 스위칭 최소화)
 *
 * EventLoop 서버는 Node.js와 유사한 아키텍처:
 * - 단일 메인 스레드가 모든 I/O 이벤트 처리
 * - CPU 집약적 작업은 별도 스레드 풀로 위임
 * - 높은 동시성과 낮은 리소스 사용량
 */
public class EventLoopServer {

    // Logger 인스턴스 - 서버 동작 상황 추적
    private static final Logger logger = LoggerFactory.getLogger(EventLoopServer.class);

    // 핵심 컴포넌트들
    private final Router router;                    // URL 라우팅 처리
    private final EventLoopProcessor processor;     // 실제 이벤트 처리 로직

    // 서버 상태 관리
    // AtomicBoolean: 스레드 안전한 boolean 값
    private final AtomicBoolean started;           // 서버 시작 여부

    // CountDownLatch: 스레드 간 동기화를 위한 동기화 장치
    // 서버 종료 신호를 기다리는 용도로 사용
    private final CountDownLatch shutdownLatch;

    // 서버 설정값들
    private String host = "localhost";             // 기본 호스트
    private int port = 8082;                      // 기본 포트
    private boolean autoShutdownHook = true;      // 자동 종료 훅 설정 여부

    /**
     * 기본 생성자 - 새로운 Router로 서버 생성
     *
     * @throws IOException EventLoopProcessor 초기화 실패시
     */
    public EventLoopServer() throws IOException {
        // this(): 같은 클래스의 다른 생성자 호출
        // new Router(): 새로운 라우터 인스턴스 생성
        this(new Router());
    }

    /**
     * Router를 받는 생성자
     *
     * @param router 사용할 Router 인스턴스
     * @throws IOException EventLoopProcessor 초기화 실패시
     */
    public EventLoopServer(Router router) throws IOException {
        this.router = router;

        // EventLoopProcessor 생성 - 실제 HTTP 처리 로직
        this.processor = new EventLoopProcessor(router);

        // AtomicBoolean 초기화 - 시작되지 않은 상태
        this.started = new AtomicBoolean(false);

        // CountDownLatch 초기화 - 1개의 신호를 기다림
        // 서버 종료시 countDown()을 호출하여 대기 중인 스레드를 깨움
        this.shutdownLatch = new CountDownLatch(1);
    }

    /**
     * Router와 설정을 받는 생성자
     *
     * @param router 사용할 Router 인스턴스
     * @param config EventLoopProcessor 설정
     * @throws IOException EventLoopProcessor 초기화 실패시
     */
    public EventLoopServer(Router router, EventLoopProcessor.ProcessorConfig config) throws IOException {
        this.router = router;

        // 사용자 정의 설정으로 EventLoopProcessor 생성
        this.processor = new EventLoopProcessor(router, config);

        this.started = new AtomicBoolean(false);
        this.shutdownLatch = new CountDownLatch(1);
    }

    // === 설정 메서드들 ===

    /**
     * 서버 호스트 설정
     *
     * 서버 시작 전에만 설정 가능 (런타임 변경 불가)
     *
     * @param host 바인딩할 호스트 주소 (예: "localhost", "0.0.0.0")
     * @return 메서드 체이닝을 위한 자기 자신
     */
    public EventLoopServer host(String host) {
        // 서버가 이미 시작된 경우 설정 변경 방지
        if (started.get()) {
            throw new IllegalStateException("서버 시작 후에는 호스트를 변경할 수 없습니다");
        }
        this.host = host;
        return this; // 메서드 체이닝 지원
    }

    /**
     * 서버 포트 설정
     *
     * @param port 바인딩할 포트 번호
     * @return 메서드 체이닝을 위한 자기 자신
     */
    public EventLoopServer port(int port) {
        if (started.get()) {
            throw new IllegalStateException("서버 시작 후에는 포트를 변경할 수 없습니다");
        }
        this.port = port;
        return this;
    }

    /**
     * 자동 종료 훅 설정
     *
     * true로 설정하면 JVM 종료시 자동으로 서버도 종료
     *
     * @param enable 자동 종료 훅 활성화 여부
     * @return 메서드 체이닝을 위한 자기 자신
     */
    public EventLoopServer autoShutdownHook(boolean enable) {
        this.autoShutdownHook = enable;
        return this;
    }

    // === 라우트 등록 편의 메서드들 ===

    /**
     * GET 라우트 등록
     *
     * HTTP GET 요청을 처리하는 라우트 추가
     *
     * @param pattern URL 패턴 (예: "/api/users", "/users/{id}")
     * @param handler 요청을 처리할 핸들러
     * @return 메서드 체이닝을 위한 자기 자신
     */
    public EventLoopServer get(String pattern, RouteHandler handler) {
        // router.get(): Router에 GET 메서드 라우트 등록
        router.get(pattern, handler);
        return this;
    }

    /**
     * POST 라우트 등록
     *
     * HTTP POST 요청을 처리하는 라우트 추가
     *
     * @param pattern URL 패턴
     * @param handler 요청을 처리할 핸들러
     * @return 메서드 체이닝을 위한 자기 자신
     */
    public EventLoopServer post(String pattern, RouteHandler handler) {
        router.post(pattern, handler);
        return this;
    }

    /**
     * PUT 라우트 등록
     *
     * HTTP PUT 요청을 처리하는 라우트 추가
     *
     * @param pattern URL 패턴
     * @param handler 요청을 처리할 핸들러
     * @return 메서드 체이닝을 위한 자기 자신
     */
    public EventLoopServer put(String pattern, RouteHandler handler) {
        router.put(pattern, handler);
        return this;
    }

    /**
     * DELETE 라우트 등록
     *
     * HTTP DELETE 요청을 처리하는 라우트 추가
     *
     * @param pattern URL 패턴
     * @param handler 요청을 처리할 핸들러
     * @return 메서드 체이닝을 위한 자기 자신
     */
    public EventLoopServer delete(String pattern, RouteHandler handler) {
        router.delete(pattern, handler);
        return this;
    }

    /**
     * 정적 파일 핸들러 (간단한 구현)
     *
     * 지정된 경로의 정적 파일들을 서빙하는 라우트 추가
     * 실제 운영환경에서는 더 복잡한 구현 필요 (MIME 타입, 캐싱 등)
     *
     * @param path URL 경로 접두사 (예: "/static")
     * @param directory 실제 파일이 위치한 디렉터리
     * @return 메서드 체이닝을 위한 자기 자신
     */
    public EventLoopServer staticFiles(String path, String directory) {
        // "/*": 와일드카드 패턴으로 하위 모든 경로 매칭
        router.get(path + "/*", RouteHandler.sync(request -> {
            // 간단한 정적 파일 서빙 (실제로는 더 복잡한 구현 필요)
            // substring(): 문자열의 일부분 추출
            String filePath = request.getPath().substring(path.length());

            // 실제로는 파일 시스템에서 파일을 읽어야 하지만, 여기서는 간단한 텍스트 응답
            return HttpResponse.text("정적 파일: " + filePath + " from " + directory);
        }));
        return this;
    }

    // === 서버 생명주기 ===

    /**
     * 서버 시작
     *
     * 기본 포트로 서버 시작
     */
    public void start() {
        start(port);
    }

    /**
     * 서버 시작 (포트 지정)
     *
     * @param port 서버 포트 번호
     */
    public void start(int port) {
        start(host, port);
    }

    /**
     * 서버 시작 (호스트, 포트 지정)
     *
     * 실제 서버를 시작하는 핵심 메서드
     *
     * @param host 바인딩할 호스트 주소
     * @param port 서버 포트 번호
     */
    public void start(String host, int port) {
        // compareAndSet(): 원자적 조건부 설정
        // 현재 값이 false이면 true로 변경하고 true 반환
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("EventLoopServer가 이미 시작됨");
        }

        try {
            logger.info("EventLoop 서버 시작 중...");
            logger.info("   호스트: {}", host);
            logger.info("   포트: {}", port);

            // getRouteCount(): Router에 등록된 라우트 수 반환
            logger.info("   라우트: {}", router.getRouteCount());

            // 기본 라우트 설정 (등록된 라우트가 없는 경우)
            setupDefaultRoutes();

            // EventLoopProcessor 시작
            processor.start(host, port);

            // JVM 종료 훅 등록 (설정된 경우)
            if (autoShutdownHook) {
                setupShutdownHook();
            }

            logger.info("EventLoop 서버 시작 완료!");
            logger.info("   서버 주소: http://{}:{}", host, port);
            logger.info("   아키텍처: 단일 스레드 + NIO Selector");
            logger.info("   예상 동시 연결 수: 10,000+");

            // 라우트 정보 출력 (디버그 모드인 경우)
            // isDebugEnabled(): DEBUG 레벨 로그가 활성화되어 있는지 확인
            if (logger.isDebugEnabled()) {
                // printRoutes(): Router의 모든 라우트 정보를 출력
                router.printRoutes();
            }

        } catch (IOException e) {
            // 시작 실패시 상태를 원래대로 되돌림
            started.set(false);
            logger.error("EventLoop 서버 시작 실패", e);

            // RuntimeException: 체크되지 않은 예외로 변환
            throw new RuntimeException("서버 시작 실패", e);
        }
    }

    /**
     * 서버 종료
     *
     * 안전한 종료 절차를 통해 모든 리소스 정리
     */
    public void stop() {
        // 중복 종료 방지
        if (!started.compareAndSet(true, false)) {
            return; // 이미 종료됨
        }

        logger.info("EventLoop 서버 종료 중...");

        try {
            // EventLoopProcessor 종료
            processor.shutdown();

            // 대기 중인 스레드들에게 종료 신호 전송
            // countDown(): CountDownLatch의 카운트를 1 감소
            shutdownLatch.countDown();

            logger.info("EventLoop 서버 종료 완료");

        } catch (Exception e) {
            logger.error("서버 종료 중 오류", e);
        }
    }

    /**
     * 서버가 종료될 때까지 대기
     *
     * 메인 스레드에서 호출하여 서버가 종료될 때까지 블로킹
     *
     * @throws InterruptedException 대기 중 인터럽트 발생시
     */
    public void awaitShutdown() throws InterruptedException {
        // await(): CountDownLatch가 0이 될 때까지 대기
        // 다른 스레드에서 countDown()을 호출할 때까지 블로킹
        shutdownLatch.await();
    }

    /**
     * 타임아웃과 함께 서버 종료 대기
     *
     * 지정된 시간 내에 서버가 종료되지 않으면 대기 중단
     *
     * @param timeout 최대 대기 시간
     * @param unit 시간 단위
     * @return 타임아웃 내에 종료되면 true, 타임아웃 발생시 false
     * @throws InterruptedException 대기 중 인터럽트 발생시
     */
    public boolean awaitShutdown(long timeout, TimeUnit unit) throws InterruptedException {
        // await(timeout, unit): 지정된 시간만큼만 대기
        return shutdownLatch.await(timeout, unit);
    }

    // === 내부 유틸리티 메서드들 ===

    /**
     * 기본 라우트 설정
     *
     * 사용자가 라우트를 등록하지 않은 경우 기본 라우트들을 자동 등록
     * 서버 정보, 통계, 헬스체크 등의 기본 엔드포인트 제공
     */
    private void setupDefaultRoutes() {
        // 기본 헬스체크 라우트 (라우트가 하나도 없는 경우에만)
        if (router.getRouteCount() == 0) {
            // RouteHandler.sync(): 동기 핸들러 생성 헬퍼 메서드
            router.get("/", RouteHandler.sync(request ->
                    // generateWelcomePage(): 환영 페이지 HTML 생성
                    HttpResponse.html(generateWelcomePage())
            ));
        }

        // 서버 정보 라우트
        router.get("/server/info", RouteHandler.sync(request ->
                // generateServerInfo(): 서버 정보 JSON 생성
                HttpResponse.json(generateServerInfo())
        ));

        // 서버 통계 라우트
        router.get("/server/stats", RouteHandler.sync(request ->
                // generateServerStats(): 실시간 서버 통계 JSON 생성
                HttpResponse.json(generateServerStats())
        ));

        // 헬스체크 라우트
        router.get("/health", RouteHandler.sync(request ->
                // 간단한 텍스트 응답으로 서버 생존 확인
                HttpResponse.text("OK")
        ));
    }

    /**
     * 환영 페이지 HTML 생성
     *
     * 서버의 루트 경로("/")에 접속했을 때 보여줄 HTML 페이지 생성
     * EventLoop 서버의 특징과 사용법을 설명하는 인터랙티브 페이지
     *
     * @return HTML 문자열
     */
    private String generateWelcomePage() {
        // String.format(): printf 스타일의 문자열 포맷팅
        // %d: 정수 값 삽입
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <title>EventLoop Server</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 50px; background: #f5f5f5; }
                    .container { max-width: 800px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    .header { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 20px; }
                    .feature { background: #ecf0f1; padding: 15px; margin: 10px 0; border-radius: 5px; }
                    .stats { display: flex; gap: 20px; }
                    .stat { background: #3498db; color: white; padding: 15px; border-radius: 5px; text-align: center; flex: 1; }
                    .routes { background: #2ecc71; color: white; padding: 10px; border-radius: 5px; }
                    pre { background: #34495e; color: #ecf0f1; padding: 15px; border-radius: 5px; overflow-x: auto; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>EventLoop 서버</h1>
                        <p><strong>단일 스레드 + NIO Selector 아키텍처</strong></p>
                    </div>
                    
                    <div class="feature">
                        <h3>서버 특징</h3>
                        <ul>
                            <li><strong>단일 스레드:</strong> 모든 I/O를 하나의 스레드에서 처리</li>
                            <li><strong>NIO Selector:</strong> 논블로킹 I/O로 높은 성능</li>
                            <li><strong>높은 동시성:</strong> 10,000+ 동시 연결 지원</li>
                            <li><strong>메모리 효율적:</strong> 스레드 풀 없이 메모리 절약</li>
                            <li><strong>CPU 효율적:</strong> 컨텍스트 스위칭 최소화</li>
                        </ul>
                    </div>
                    
                    <div class="stats">
                        <div class="stat">
                            <h4>서버 포트</h4>
                            <div>%d</div>
                        </div>
                        <div class="stat">
                            <h4>라우트</h4>
                            <div>%d</div>
                        </div>
                        <div class="stat">
                            <h4>아키텍처</h4>
                            <div>EventLoop</div>
                        </div>
                    </div>
                    
                    <div class="feature">
                        <h3>API 엔드포인트</h3>
                        <div class="routes">
                            <a href="/server/info" style="color: white;">GET /server/info</a> - 서버 정보<br>
                            <a href="/server/stats" style="color: white;">GET /server/stats</a> - 실시간 통계<br>
                            <a href="/health" style="color: white;">GET /health</a> - 헬스체크
                        </div>
                    </div>
                    
                    <div class="feature">
                        <h3>사용 예시</h3>
                        <pre>// EventLoop 서버 특징 테스트
curl http://localhost:%d/server/stats

// 동시 연결 테스트 (EventLoop 서버는 이런 테스트에 최적화됨)
for i in {1..1000}; do
  curl http://localhost:%d/health &
done</pre>
                    </div>
                </div>
            </body>
            </html>
            """,
                getPort(),                    // 서버 포트
                router.getRouteCount(),       // 등록된 라우트 수
                getPort(),                    // curl 예시용 포트 (첫 번째)
                getPort());                   // curl 예시용 포트 (두 번째)
    }

    /**
     * 서버 정보 JSON 생성
     *
     * 서버의 기본 정보와 특징을 JSON 형태로 반환
     *
     * @return JSON 형태의 서버 정보 문자열
     */
    private String generateServerInfo() {
        // System.currentTimeMillis(): 현재 시간 (밀리초)
        return String.format("""
            {
                "server": "EventLoop Server",
                "architecture": "Single Thread + NIO Selector",
                "version": "1.0",
                "host": "%s",
                "port": %d,
                "routes": %d,
                "features": [
                    "Single Thread Event Loop",
                    "NIO Selector",
                    "Non-blocking I/O",
                    "High Concurrency (10,000+ connections)",
                    "Memory Efficient",
                    "CPU Efficient"
                ],
                "startTime": %d,
                "uptime": %d
            }""",
                host,                           // 서버 호스트
                getPort(),                      // 서버 포트
                router.getRouteCount(),         // 라우트 수
                System.currentTimeMillis(),     // 시작 시간 (임시)
                System.currentTimeMillis());    // 업타임 (임시)
    }

    /**
     * 서버 통계 JSON 생성
     *
     * 실시간 서버 성능 통계와 시스템 정보를 JSON으로 반환
     *
     * @return JSON 형태의 서버 통계 문자열
     */
    private String generateServerStats() {
        // getStats(): EventLoopProcessor에서 통계 정보 수집
        EventLoopProcessor.ProcessorStats stats = processor.getStats();

        // Runtime.getRuntime(): 현재 JVM 런타임 인스턴스
        return String.format("""
            {
                "running": %s,
                "eventLoops": %d,
                "tasksExecuted": %d,
                "activeConnections": %d,
                "totalConnections": %d,
                "bytesRead": %d,
                "bytesWritten": %d,
                "architecture": "EventLoop",
                "memoryUsage": {
                    "total": %d,
                    "free": %d,
                    "used": %d
                }
            }""",
                stats.isRunning(),                                          // 실행 상태
                stats.getTotalLoops(),                                      // 총 이벤트 루프 수
                stats.getTotalTasks(),                                      // 총 실행된 작업 수
                stats.getHandlerStats().getActiveConnections(),             // 활성 연결 수
                stats.getSelectorStats().getTotalConnections(),             // 총 연결 수
                stats.getSelectorStats().getBytesRead(),                    // 읽은 바이트 수
                stats.getSelectorStats().getBytesWritten(),                 // 쓴 바이트 수
                Runtime.getRuntime().totalMemory(),                        // 총 메모리
                Runtime.getRuntime().freeMemory(),                         // 사용 가능한 메모리
                Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()); // 사용 중인 메모리
    }

    /**
     * JVM 종료 훅 설정
     *
     * JVM이 종료될 때 자동으로 서버도 안전하게 종료하도록 설정
     * Ctrl+C나 시스템 종료시 리소스 정리를 보장
     */
    private void setupShutdownHook() {
        // Runtime.getRuntime(): 현재 JVM 런타임 인스턴스
        // addShutdownHook(): JVM 종료시 실행할 스레드 등록
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("종료 신호 수신됨");
            // stop(): 서버 안전 종료
            stop();
        }, "EventLoopServer-ShutdownHook")); // 스레드 이름 지정
    }

    // === 상태 확인 메서드들 ===

    /**
     * 서버 실행 중인지 확인
     *
     * @return 서버와 프로세서가 모두 실행 중이면 true
     */
    public boolean isRunning() {
        // started.get(): AtomicBoolean의 현재 값
        // processor.isRunning(): EventLoopProcessor의 실행 상태
        // &&: 논리곱 연산 (둘 다 true여야 true)
        return started.get() && processor.isRunning();
    }

    /**
     * 서버 포트 반환
     *
     * 실제로 바인딩된 포트 번호 (포트 0으로 시작한 경우 유용)
     *
     * @return 서버 포트 번호
     */
    public int getPort() {
        // processor.getPort(): EventLoopProcessor에서 실제 포트 정보 획득
        return processor.getPort();
    }

    /**
     * 라우터 반환
     *
     * 외부에서 라우트를 추가하거나 수정해야 할 때 사용
     *
     * @return Router 인스턴스
     */
    public Router getRouter() {
        return router;
    }

    /**
     * 프로세서 반환
     *
     * 외부에서 EventLoopProcessor에 직접 접근해야 할 때 사용
     *
     * @return EventLoopProcessor 인스턴스
     */
    public EventLoopProcessor getProcessor() {
        return processor;
    }

    /**
     * 통계 정보 반환
     *
     * 서버의 실시간 통계 정보
     *
     * @return ProcessorStats 객체
     */
    public EventLoopProcessor.ProcessorStats getStats() {
        return processor.getStats();
    }

    /**
     * 서버 상태의 문자열 표현
     *
     * 디버깅과 로깅에 유용한 서버 상태 요약
     *
     * @return 포맷된 서버 상태 문자열
     */
    @Override
    public String toString() {
        // String.format(): printf 스타일의 문자열 포맷팅
        return String.format("EventLoopServer{host='%s', port=%d, running=%s, routes=%d}",
                host,                       // 서버 호스트
                port,                       // 서버 포트
                isRunning(),               // 실행 상태
                router.getRouteCount());   // 등록된 라우트 수
    }
}