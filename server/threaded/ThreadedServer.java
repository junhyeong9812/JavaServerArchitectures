package server.threaded;

import server.core.routing.Router;
import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 스레드 기반 HTTP 서버
 * ServerSocket + Thread Pool을 사용한 전통적인 서버 아키텍처
 *
 * 주요 특징:
 * - 다중 스레드 환경에서 동시 다발적인 클라이언트 요청 처리
 * - Accept 전용 스레드와 Request 처리용 스레드 풀로 역할 분리
 * - 스레드 안전성을 위한 AtomicBoolean 사용
 * - 서버 생명주기 관리 (초기화 -> 시작 -> 종료)
 */
public class ThreadedServer {

    // === 서버 핵심 구성 요소들 ===
    // final: 생성 후 변경 불가능한 필드 (불변성 보장)
    private final int port;                                    // 서버가 바인딩될 포트 번호
    private final Router router;                               // HTTP 요청 라우팅 처리기
    private final ThreadedProcessor processor;                 // 다중 스레드 요청 처리기
    private final ThreadedMiniServletContainer servletContainer; // 서블릿 컨테이너
    private final ServerConfig config;                         // 서버 설정 정보

    // === 네트워킹 및 스레드 관련 ===
    private ServerSocket serverSocket;                         // TCP 서버 소켓 (클라이언트 연결 수신)
    private Thread acceptorThread;                             // 클라이언트 연결 수락 전용 스레드

    // AtomicBoolean: 스레드 안전한 boolean 값 (원자적 연산 지원)
    // 여러 스레드가 동시에 접근해도 안전하게 값을 읽고 쓸 수 있음
    private final AtomicBoolean running = new AtomicBoolean(false);      // 서버 실행 상태
    private final AtomicBoolean initialized = new AtomicBoolean(false);  // 초기화 완료 상태

    // === 통계 정보 ===
    // volatile: 멀티스레드 환경에서 변수 값의 가시성 보장
    // 한 스레드에서 값을 변경하면 다른 스레드에서 즉시 볼 수 있음
    private volatile long startTime;                           // 서버 시작 시간 (밀리초)
    private volatile long totalAcceptedConnections = 0;        // 총 수락된 연결 수
    private volatile long totalFailedConnections = 0;          // 총 실패한 연결 수

    // Logger 인스턴스 생성
    // LoggerFactory.getLogger(): 클래스별 전용 로거 생성
    // 각 클래스마다 고유한 로거를 가져 로그 출처를 명확히 식별 가능
    private static final Logger logger = LoggerFactory.getLogger(ThreadedServer.class);

    // === 생성자들 ===
    // 생성자 오버로딩: 다양한 매개변수 조합으로 객체 생성 가능

    /**
     * 기본 생성자 - 포트만 지정
     * Router와 ServerConfig는 기본값 사용
     */
    public ThreadedServer(int port) {
        // this(): 같은 클래스의 다른 생성자 호출
        // 생성자 체이닝으로 코드 중복 방지
        this(port, new Router(), new ServerConfig());
    }

    /**
     * Router 지정 생성자
     * ServerConfig는 기본값 사용
     */
    public ThreadedServer(int port, Router router) {
        this(port, router, new ServerConfig());
    }

    /**
     * 모든 매개변수 지정 생성자 (메인 생성자)
     * 실제 초기화 로직이 수행되는 곳
     */
    public ThreadedServer(int port, Router router, ServerConfig config) {
        // 매개변수를 final 필드에 할당
        this.port = port;
        this.router = router;
        this.config = config;

        // ThreadedMiniServletContainer 생성
        // config.getContextPath(): 서블릿 컨텍스트 경로 반환
        this.servletContainer = new ThreadedMiniServletContainer(config.getContextPath());

        // ThreadedProcessor 초기화
        // 요청 처리를 담당하는 스레드 풀과 핸들러 설정
        this.processor = new ThreadedProcessor(
                router,                                  // HTTP 요청 라우팅 처리기
                servletContainer,                        // 서블릿 컨테이너
                config.getThreadPoolConfig(),            // 스레드 풀 설정 (코어/최대 크기 등)
                config.getRequestHandlerConfig()         // 요청 핸들러 설정 (타임아웃 등)
        );

        // 초기화 완료 로그 출력
        // logger.info(): INFO 레벨 로그 메시지 출력
        logger.info("ThreadedServer initialized on port {}", port);
    }

    /**
     * 서버 초기화
     * 서버 시작 전에 필요한 모든 구성 요소들을 준비하는 단계
     *
     * @throws Exception 초기화 중 발생할 수 있는 모든 예외
     */
    public void initialize() throws Exception {
        // 중복 초기화 방지
        // AtomicBoolean.get(): 현재 값을 원자적으로 읽기
        if (initialized.get()) {
            return;  // 이미 초기화되었으면 즉시 리턴
        }

        logger.info("Initializing server...");

        try {
            // === ServerSocket 생성 및 설정 ===

            // ServerSocket(): 기본 생성자로 언바운드 소켓 생성
            // 아직 특정 포트에 바인딩되지 않은 상태
            serverSocket = new ServerSocket();

            // setReuseAddress(true): SO_REUSEADDR 소켓 옵션 활성화
            // 서버 재시작 시 "Address already in use" 오류 방지
            // TIME_WAIT 상태의 소켓 주소를 재사용 가능하게 함
            serverSocket.setReuseAddress(true);

            // setSoTimeout(): accept() 메서드의 타임아웃 설정
            // 1초마다 타임아웃 발생시켜 graceful shutdown 체크 가능
            // 0이면 무한 대기, 양수면 밀리초 단위 타임아웃
            serverSocket.setSoTimeout(1000);

            // === 소켓 옵션 설정 ===

            // 수신 버퍼 크기 설정 (선택사항)
            if (config.getReceiveBufferSize() > 0) {
                // setReceiveBufferSize(): TCP 수신 버퍼 크기 설정
                // 더 큰 버퍼는 높은 처리량을 제공하지만 메모리 사용량 증가
                serverSocket.setReceiveBufferSize(config.getReceiveBufferSize());
            }

            // === 포트 바인딩 ===

            // InetSocketAddress: IP주소와 포트를 함께 나타내는 클래스
            // config.getBindAddress(): 바인딩할 IP 주소 (null이면 모든 인터페이스)
            InetSocketAddress address = new InetSocketAddress(config.getBindAddress(), port);

            // bind(): 소켓을 특정 주소와 포트에 바인딩
            // config.getBacklogSize(): 대기 중인 연결 요청의 최대 큐 크기
            // 백로그가 가득 차면 새로운 연결 요청은 거부됨
            serverSocket.bind(address, config.getBacklogSize());

            // === 서블릿 컨테이너 초기화 ===

            // 등록된 모든 서블릿들의 init() 메서드 호출
            servletContainer.initialize();

            // 초기화 완료 표시
            // AtomicBoolean.set(): 값을 원자적으로 설정
            initialized.set(true);

            // 초기화 성공 로그 및 상세 정보 출력
            logger.info("Server initialized successfully");
            logger.info("Listening on: {}", address);
            logger.info("Backlog size: {}", config.getBacklogSize());
            logger.info("Thread pool: {}-{}",
                    config.getThreadPoolConfig().getCorePoolSize(),     // 코어 스레드 수
                    config.getThreadPoolConfig().getMaxPoolSize());     // 최대 스레드 수

        } catch (Exception e) {
            // 초기화 실패 시 오류 로그 출력
            logger.error("Initialization failed: {}", e.getMessage());

            // 부분적으로 초기화된 자원들 정리
            cleanup();

            // 예외를 다시 던져서 호출자에게 실패 알림
            // throw: 예외를 발생시키거나 다시 던지기
            throw e;
        }
    }

    /**
     * 서버 시작
     * 초기화된 서버를 실제로 실행하여 클라이언트 요청 수신 시작
     *
     * @throws Exception 서버 시작 중 발생할 수 있는 예외
     */
    public void start() throws Exception {
        // 이미 실행 중인지 확인
        if (running.get()) {
            // IllegalStateException: 잘못된 상태에서 메서드 호출 시 발생
            throw new IllegalStateException("Server is already running");
        }

        // 초기화되지 않았으면 먼저 초기화 수행
        if (!initialized.get()) {
            initialize();
        }

        // 실행 상태로 변경
        running.set(true);

        // 시작 시간 기록 (통계용)
        // System.currentTimeMillis(): 현재 시간을 밀리초로 반환
        startTime = System.currentTimeMillis();

        // === Accept 스레드 시작 ===

        // Thread 생성: 클라이언트 연결 수락 전용 스레드
        // this::acceptLoop: 메서드 참조 (람다 표현식의 축약형)
        // () -> acceptLoop()와 동일한 의미
        acceptorThread = new Thread(this::acceptLoop, "ServerAcceptor-" + port);

        // start(): 스레드 실행 시작 (별도 스레드에서 acceptLoop 메서드 실행)
        acceptorThread.start();

        logger.info("Server started on port {}", port);

        // === 통계 출력 스케줄러 시작 (디버그 모드) ===

        // 디버그 모드가 활성화된 경우에만 통계 정보 주기적 출력
        if (config.isDebugMode()) {
            startStatisticsReporter();
        }
    }

    /**
     * 연결 수락 루프
     * Accept 전용 스레드에서 실행되는 메서드
     * 클라이언트의 연결 요청을 지속적으로 수신하고 처리
     */
    private void acceptLoop() {
        logger.info("Accept loop started");

        // 서버가 실행 중이고 현재 스레드가 중단되지 않은 동안 계속 실행
        // Thread.currentThread().isInterrupted(): 현재 스레드의 중단 상태 확인
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // === 클라이언트 연결 수락 ===

                // accept(): 클라이언트 연결 요청 대기 및 수락
                // 블로킹 메서드: 연결 요청이 올 때까지 대기
                // setSoTimeout()으로 설정한 시간마다 타임아웃 예외 발생
                Socket clientSocket = serverSocket.accept();

                // 성공한 연결 수 증가 (원자적 연산)
                totalAcceptedConnections++;

                // === 클라이언트 소켓 설정 ===
                configureClientSocket(clientSocket);

                // === 연결을 ThreadedProcessor에 위임 ===

                // 실제 HTTP 요청 처리는 별도 스레드 풀에서 수행
                // 메인 Accept 스레드는 연결 수락에만 집중
                processor.processConnection(clientSocket);

            } catch (SocketTimeoutException e) {
                // 정상적인 타임아웃 - 셧다운 체크를 위함
                // setSoTimeout()으로 설정한 주기적 타임아웃
                // 이때는 로그 출력하지 않고 루프 계속 진행
                continue;

            } catch (IOException e) {
                // I/O 오류 발생 시 처리
                if (running.get()) {
                    // 서버가 실행 중일 때만 오류로 간주
                    totalFailedConnections++;
                    logger.error("Accept failed: {}", e.getMessage());

                    // 연속적인 실패시 잠시 대기 (CPU 사용률 방지)
                    try {
                        // Thread.sleep(): 현재 스레드를 지정된 시간만큼 일시정지
                        // 100ms 대기로 과도한 CPU 사용 방지
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        // sleep 중 인터럽트 발생 시
                        // Thread.currentThread().interrupt(): 인터럽트 상태 복원
                        Thread.currentThread().interrupt();
                        break;  // 루프 종료
                    }
                }
            } catch (Exception e) {
                // 예상치 못한 오류 처리
                totalFailedConnections++;
                logger.error("Unexpected error in accept loop: {}", e.getMessage());
            }
        }

        logger.info("Accept loop terminated");
    }

    /**
     * 클라이언트 소켓 설정
     * 새로 연결된 클라이언트 소켓에 TCP 옵션들을 적용
     *
     * @param clientSocket 설정할 클라이언트 소켓
     * @throws IOException 소켓 설정 중 I/O 오류
     */
    private void configureClientSocket(Socket clientSocket) throws IOException {
        // setTcpNoDelay(): Nagle 알고리즘 비활성화 여부
        // true: 작은 패킷도 즉시 전송 (낮은 지연시간)
        // false: 작은 패킷들을 모아서 전송 (높은 처리량)
        clientSocket.setTcpNoDelay(config.isTcpNoDelay());

        // setKeepAlive(): TCP Keep-Alive 활성화 여부
        // true: 연결 상태를 주기적으로 확인하여 끊어진 연결 감지
        clientSocket.setKeepAlive(config.isKeepAlive());

        // 송신 버퍼 크기 설정 (선택사항)
        if (config.getSendBufferSize() > 0) {
            // setSendBufferSize(): TCP 송신 버퍼 크기 설정
            clientSocket.setSendBufferSize(config.getSendBufferSize());
        }

        // 수신 버퍼 크기 설정 (선택사항)
        if (config.getReceiveBufferSize() > 0) {
            // setReceiveBufferSize(): TCP 수신 버퍼 크기 설정
            clientSocket.setReceiveBufferSize(config.getReceiveBufferSize());
        }
    }

    /**
     * 통계 리포터 시작
     * 별도 데몬 스레드에서 주기적으로 서버 통계 정보 출력
     */
    private void startStatisticsReporter() {
        // 람다 표현식으로 Runnable 구현
        // () -> { ... }: 매개변수 없는 람다
        Thread statsThread = new Thread(() -> {
            // 서버가 실행 중인 동안 계속 반복
            while (running.get()) {
                try {
                    // 30초마다 통계 출력
                    Thread.sleep(30000);

                    // 서버가 여전히 실행 중인지 다시 확인
                    if (running.get()) {
                        printDetailedStatistics();
                    }
                } catch (InterruptedException e) {
                    // 스레드 인터럽트 시 루프 종료
                    break;
                }
            }
        }, "StatisticsReporter-" + port);

        // setDaemon(true): 데몬 스레드로 설정
        // 메인 스레드가 종료되면 이 스레드도 자동으로 종료됨
        // 애플리케이션 종료를 방해하지 않음
        statsThread.setDaemon(true);
        statsThread.start();
    }

    /**
     * 서블릿 등록
     * 일반 동기 서블릿을 특정 URL 패턴에 등록
     *
     * @param pattern URL 패턴 (예: "/api/*", "/hello")
     * @param servlet 등록할 MiniServlet 인스턴스
     * @return 메서드 체이닝을 위한 자기 자신 반환
     */
    public ThreadedServer registerServlet(String pattern, server.core.mini.MiniServlet servlet) {
        // 서블릿 컨테이너에 서블릿 등록 위임
        servletContainer.registerServlet(pattern, servlet);
        return this;  // 메서드 체이닝 지원: server.registerServlet().registerServlet()
    }

    /**
     * 비동기 서블릿 등록
     * 비동기 처리가 가능한 서블릿을 특정 URL 패턴에 등록
     *
     * @param pattern URL 패턴
     * @param servlet 등록할 MiniAsyncServlet 인스턴스
     * @return 메서드 체이닝을 위한 자기 자신 반환
     */
    public ThreadedServer registerAsyncServlet(String pattern, server.core.mini.MiniAsyncServlet servlet) {
        servletContainer.registerAsyncServlet(pattern, servlet);
        return this;
    }

    /**
     * 라우터 핸들러 등록
     * Router 기반의 핸들러를 특정 URL 패턴에 등록
     *
     * @param pattern URL 패턴
     * @param handler 등록할 RouteHandler 인스턴스
     * @return 메서드 체이닝을 위한 자기 자신 반환
     */
    public ThreadedServer registerHandler(String pattern, server.core.routing.RouteHandler handler) {
        servletContainer.registerHandler(pattern, handler);
        return this;
    }

    /**
     * 서버 중지
     * 실행 중인 서버를 안전하게 종료하는 메서드
     * Graceful shutdown 패턴 구현
     */
    public void stop() {
        // 이미 중지된 서버인지 확인
        if (!running.get()) {
            return;  // 이미 중지되었으면 즉시 리턴
        }

        logger.info("Stopping server...");

        // 실행 상태 변경 (새로운 연결 수락 중지)
        running.set(false);

        // === Accept 스레드 중지 ===

        if (acceptorThread != null) {
            // interrupt(): 스레드에 인터럽트 신호 전송
            // 블로킹 상태(accept, sleep 등)에서 즉시 깨어나게 함
            acceptorThread.interrupt();

            try {
                // join(timeout): 해당 스레드가 종료될 때까지 최대 지정 시간 대기
                // 5초 내에 Accept 스레드가 종료되길 기다림
                acceptorThread.join(5000);
            } catch (InterruptedException e) {
                // 현재 스레드가 인터럽트된 경우 상태 복원
                Thread.currentThread().interrupt();
            }
        }

        // === ServerSocket 닫기 ===

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                // close(): 서버 소켓 닫기
                // 더 이상 새로운 연결 요청을 받지 않음
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing server socket: {}", e.getMessage());
        }

        // === ThreadedProcessor 종료 ===

        // 요청 처리 스레드 풀 종료
        processor.shutdown();

        // === 서블릿 컨테이너 종료 ===

        // 모든 서블릿의 destroy() 메서드 호출
        servletContainer.destroy();

        // === 최종 통계 출력 ===
        printDetailedStatistics();

        logger.info("Server stopped");
    }

    /**
     * 정리 작업
     * 초기화 중 오류 발생 시 부분적으로 초기화된 자원들을 정리
     */
    private void cleanup() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // 정리 중 발생하는 예외는 무시
            // 이미 오류 상황이므로 추가 예외 처리 불필요
        }

        // 초기화 상태 해제
        initialized.set(false);
    }

    /**
     * 서버 상태 정보
     * 현재 서버의 전체적인 상태 정보를 수집하여 반환
     *
     * @return 서버 상태 정보를 담은 ServerStatus 객체
     */
    public ServerStatus getStatus() {
        // ThreadedProcessor에서 스레드 풀 상태 정보 수집
        ThreadedProcessor.ProcessorStatus processorStatus = processor.getStatus();

        // 서블릿 컨테이너에서 서블릿 상태 정보 수집
        ThreadedMiniServletContainer.ContainerStatus containerStatus = servletContainer.getStatus();

        // 모든 상태 정보를 하나로 통합
        return new ServerStatus(
                running.get(),                                      // 현재 실행 상태
                initialized.get(),                                  // 초기화 완료 상태
                port,                                              // 서버 포트
                System.currentTimeMillis() - startTime,            // 업타임 (밀리초)
                totalAcceptedConnections,                          // 총 수락된 연결 수
                totalFailedConnections,                            // 총 실패한 연결 수
                processorStatus,                                   // 프로세서 상태
                containerStatus                                    // 컨테이너 상태
        );
    }

    /**
     * 상세 통계 출력
     * 서버의 상세한 통계 정보를 포맷팅하여 로그로 출력
     */
    public void printDetailedStatistics() {
        // 현재 서버 상태 정보 수집
        ServerStatus status = getStatus();

        // "=".repeat(50): "=" 문자를 50번 반복하여 구분선 생성
        // String.repeat(): Java 11부터 제공되는 문자열 반복 메서드
        logger.info("");
        logger.info("==================================================");
        logger.info("ThreadedServer Statistics (Port {})", port);
        logger.info("==================================================");

        // 삼항 연산자: condition ? valueIfTrue : valueIfFalse
        logger.info("Status: {}", (status.isRunning() ? "RUNNING" : "STOPPED"));

        // 업타임을 초 단위로 변환하여 출력
        logger.info("Uptime: {} seconds", (status.getUptime() / 1000));
        logger.info("Total Accepted: {}", status.getTotalAcceptedConnections());
        logger.info("Total Failed: {}", status.getTotalFailedConnections());

        // 실패율 계산 및 출력 (연결이 있었던 경우에만)
        if (status.getTotalAcceptedConnections() > 0) {
            // 실패율 = 실패한 연결 수 / 전체 연결 시도 수 * 100
            double failureRate = (double) status.getTotalFailedConnections() /
                    (status.getTotalAcceptedConnections() + status.getTotalFailedConnections()) * 100;

            // String.format(): printf 스타일의 문자열 포맷팅
            // "%.2f": 소수점 둘째 자리까지 표시
            logger.info("Failure Rate: {}%", String.format("%.2f", failureRate));
        }

        // 프로세서 상태 정보 출력
        logger.info("");
        logger.info("Processor Status:");
        logger.info("{}", status.getProcessorStatus());

        // 컨테이너 상태 정보 출력
        logger.info("");
        logger.info("Container Status:");
        logger.info("{}", status.getContainerStatus());

        logger.info("==================================================");
        logger.info("");
    }

    /**
     * Graceful shutdown hook 등록
     * JVM 종료 시 서버를 안전하게 종료하기 위한 셧다운 훅 등록
     */
    public void addShutdownHook() {
        // Runtime.getRuntime(): 현재 JVM 런타임 인스턴스 반환
        // addShutdownHook(): JVM 종료 시 실행될 스레드 등록
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered");
            stop();  // 서버 안전 종료
        }, "ShutdownHook-" + port));
    }

    // === Getter 메서드들 ===
    // 외부에서 서버 정보를 읽기 위한 접근자 메서드들

    /**
     * 서버 포트 반환
     */
    public int getPort() {
        return port;
    }

    /**
     * 서버 실행 상태 반환
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 서버 초기화 상태 반환
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * 총 수락된 연결 수 반환
     */
    public long getTotalAcceptedConnections() {
        return totalAcceptedConnections;
    }

    /**
     * 총 실패한 연결 수 반환
     */
    public long getTotalFailedConnections() {
        return totalFailedConnections;
    }

    /**
     * 서버 상태 클래스
     * 서버의 현재 상태를 나타내는 불변 데이터 객체
     *
     * 불변 객체 패턴:
     * - 모든 필드가 final
     * - setter 메서드 없음
     * - 생성 후 상태 변경 불가능
     * - 스레드 안전성 보장
     */
    public static class ServerStatus {
        // final: 생성 후 변경 불가능한 필드들
        private final boolean running;                                              // 실행 상태
        private final boolean initialized;                                          // 초기화 상태
        private final int port;                                                    // 서버 포트
        private final long uptime;                                                 // 업타임 (밀리초)
        private final long totalAcceptedConnections;                               // 총 수락된 연결 수
        private final long totalFailedConnections;                                 // 총 실패한 연결 수
        private final ThreadedProcessor.ProcessorStatus processorStatus;           // 프로세서 상태
        private final ThreadedMiniServletContainer.ContainerStatus containerStatus; // 컨테이너 상태

        /**
         * ServerStatus 생성자
         * 모든 상태 정보를 한번에 초기화
         */
        public ServerStatus(boolean running, boolean initialized, int port, long uptime,
                            long totalAcceptedConnections, long totalFailedConnections,
                            ThreadedProcessor.ProcessorStatus processorStatus,
                            ThreadedMiniServletContainer.ContainerStatus containerStatus) {
            this.running = running;
            this.initialized = initialized;
            this.port = port;
            this.uptime = uptime;
            this.totalAcceptedConnections = totalAcceptedConnections;
            this.totalFailedConnections = totalFailedConnections;
            this.processorStatus = processorStatus;
            this.containerStatus = containerStatus;
        }

        // === Getter 메서드들 ===
        // 모든 필드에 대한 읽기 전용 접근자

        public boolean isRunning() {
            return running;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public int getPort() {
            return port;
        }

        public long getUptime() {
            return uptime;
        }

        public long getTotalAcceptedConnections() {
            return totalAcceptedConnections;
        }

        public long getTotalFailedConnections() {
            return totalFailedConnections;
        }

        public ThreadedProcessor.ProcessorStatus getProcessorStatus() {
            return processorStatus;
        }

        public ThreadedMiniServletContainer.ContainerStatus getContainerStatus() {
            return containerStatus;
        }

        /**
         * 객체의 문자열 표현
         * 주요 상태 정보를 간략하게 요약
         *
         * @return 서버 상태의 간략한 문자열 표현
         */
        @Override
        public String toString() {
            // String.format(): printf 스타일 포맷팅으로 가독성 있는 문자열 생성
            return String.format("ServerStatus{running=%s, port=%d, uptime=%ds, connections=%d/%d}",
                    running,                        // 실행 상태
                    port,                          // 포트 번호
                    uptime / 1000,                 // 업타임 (초 단위)
                    totalAcceptedConnections,      // 성공한 연결 수
                    totalFailedConnections);       // 실패한 연결 수
        }
    }
}