package server.eventloop;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.routing.Router;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 이벤트 처리 루프
 * EventLoop, SelectorManager, NonBlockingHandler를 조합하여 완전한 처리 파이프라인 구성
 *
 * 역할:
 * - 여러 컴포넌트들을 통합하여 완전한 HTTP 서버 기능 제공
 * - 서버 생명주기 관리 (시작, 종료, 상태 모니터링)
 * - 리소스 정리와 성능 모니터링
 * - 설정 관리와 통계 수집
 */
public class EventLoopProcessor {

    // Logger 인스턴스 - 프로세서 동작 상황 추적
    private static final Logger logger = LoggerFactory.getLogger(EventLoopProcessor.class);

    // 핵심 컴포넌트들
    private final EventLoop eventLoop;            // 메인 이벤트 루프
    private final SelectorManager selectorManager; // NIO Selector 관리자
    private final EventQueue eventQueue;          // 비동기 작업 큐
    private final NonBlockingHandler handler;     // HTTP 요청 처리기
    private final Router router;                  // URL 라우팅

    // 서버 상태 관리
    // AtomicBoolean: 스레드 안전한 boolean 값
    private final AtomicBoolean started;          // 서버 시작 여부

    // NIO 서버 소켓 - 클라이언트 연결을 수락하는 소켓
    private ServerSocketChannel serverChannel;

    // 스케줄된 정리 작업을 관리하는 Future
    private ScheduledFuture<?> cleanupTask;

    // 프로세서 설정
    private final ProcessorConfig config;

    /**
     * 기본 설정으로 프로세서 생성
     *
     * @param router URL 라우팅을 처리할 Router 인스턴스
     * @throws IOException I/O 초기화 실패시
     */
    public EventLoopProcessor(Router router) throws IOException {
        // this(): 같은 클래스의 다른 생성자 호출
        // new ProcessorConfig(): 기본 설정으로 ProcessorConfig 생성
        this(router, new ProcessorConfig());
    }

    /**
     * 사용자 설정으로 프로세서 생성
     *
     * @param router URL 라우팅을 처리할 Router 인스턴스
     * @param config 프로세서 설정
     * @throws IOException I/O 초기화 실패시
     */
    public EventLoopProcessor(Router router, ProcessorConfig config) throws IOException {
        this.router = router;
        this.config = config;

        // AtomicBoolean 초기값 false로 설정 (아직 시작되지 않음)
        this.started = new AtomicBoolean(false);

        // 컴포넌트 초기화
        // 1. EventLoop: 핵심 이벤트 루프 생성
        this.eventLoop = new EventLoop();

        // 2. SelectorManager: EventLoop와 Selector를 연결하는 관리자
        this.selectorManager = new SelectorManager(eventLoop, eventLoop.getSelector());

        // 3. EventQueue: 비동기 작업 처리를 위한 큐
        this.eventQueue = new EventQueue(eventLoop);

        // 4. NonBlockingHandler: HTTP 요청을 실제로 처리하는 핸들러
        this.handler = new NonBlockingHandler(router, selectorManager, eventQueue);
    }

    /**
     * 서버 시작
     *
     * @param port 서버 포트 번호
     * @throws IOException 서버 시작 실패시
     */
    public void start(int port) throws IOException {
        // "localhost"를 기본 호스트로 사용하여 시작
        start("localhost", port);
    }

    /**
     * 서버 시작 (호스트 지정)
     *
     * @param host 바인딩할 호스트 주소
     * @param port 서버 포트 번호
     * @throws IOException 서버 시작 실패시
     */
    public void start(String host, int port) throws IOException {
        // compareAndSet(): 원자적 조건부 설정
        // 현재 값이 false이면 true로 변경하고 true 반환, 이미 true이면 false 반환
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("EventLoopProcessor가 이미 시작됨");
        }

        logger.info("EventLoopProcessor를 {}:{}에서 시작 중", host, port);

        try {
            // 서버 소켓 설정
            // ServerSocketChannel.open(): 새로운 서버 소켓 채널 생성
            serverChannel = ServerSocketChannel.open();

            // configureBlocking(false): 논블로킹 모드로 설정
            // 논블로킹 모드에서만 Selector와 함께 사용 가능
            serverChannel.configureBlocking(false);

            // bind(): 지정된 주소와 포트에 소켓 바인딩
            // InetSocketAddress: IP 주소와 포트를 담는 클래스
            serverChannel.bind(new InetSocketAddress(host, port));

            // EventLoop 시작
            eventLoop.start();

            // 서버 소켓을 SelectorManager에 등록
            // registerServerSocket(): 새로운 연결 수락을 위해 서버 소켓 등록
            selectorManager.registerServerSocket(serverChannel, handler);

            // 정리 작업 스케줄링
            scheduleCleanupTasks();

            logger.info("EventLoopProcessor가 {}:{}에서 성공적으로 시작됨", host, port);

        } catch (IOException e) {
            // 시작 실패시 상태를 원래대로 되돌림
            started.set(false);

            // 부분적으로 초기화된 리소스 정리
            cleanup();
            throw e;
        }
    }

    /**
     * 서버 종료
     *
     * 안전한 종료 절차를 수행하여 모든 리소스 정리
     */
    public void shutdown() {
        // 중복 종료 방지
        if (!started.compareAndSet(true, false)) {
            return; // 이미 종료됨
        }

        logger.info("EventLoopProcessor 종료 중...");

        try {
            // 정리 작업 취소
            if (cleanupTask != null) {
                // cancel(false): 스케줄된 작업 취소 (이미 실행 중인 작업은 중단하지 않음)
                cleanupTask.cancel(false);
            }

            // 모든 연결 종료
            // closeAllConnections(): SelectorManager를 통해 모든 활성 연결 정리
            selectorManager.closeAllConnections();

            // 서버 소켓 종료
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }

            // EventQueue 종료
            eventQueue.shutdown();

            // EventLoop 종료
            eventLoop.shutdown();

            logger.info("EventLoopProcessor 종료 완료");

        } catch (Exception e) {
            logger.error("종료 중 오류 발생", e);
        } finally {
            // 마지막 정리 작업
            cleanup();
        }
    }

    /**
     * 주기적 정리 작업 스케줄링
     *
     * 메모리 누수 방지와 성능 유지를 위한 백그라운드 작업들
     */
    private void scheduleCleanupTasks() {
        // 타임아웃 연결 정리 (30초마다)
        // scheduleAtFixedRate(): 고정 간격으로 작업 반복 실행
        cleanupTask = eventQueue.scheduleAtFixedRate(
                // handler::cleanupTimeoutConnections: 메서드 레퍼런스
                // 타임아웃된 연결들을 찾아서 정리하는 작업
                handler::cleanupTimeoutConnections,
                config.getCleanupInterval(),  // 초기 지연 시간
                config.getCleanupInterval(),  // 반복 간격
                TimeUnit.SECONDS             // 시간 단위
        );

        // 통계 출력 (1분마다)
        eventQueue.scheduleAtFixedRate(
                // this::logStatistics: 현재 객체의 logStatistics 메서드 레퍼런스
                this::logStatistics,
                60,                          // 1분 후 시작
                60,                          // 1분마다 반복
                TimeUnit.SECONDS
        );
    }

    /**
     * 통계 정보 로그 출력
     *
     * 시스템 상태를 주기적으로 모니터링하기 위한 로그
     */
    private void logStatistics() {
        try {
            // 각 컴포넌트에서 통계 정보 수집
            SelectorManager.SelectorStats selectorStats = selectorManager.getStats();
            EventQueue.QueueStats queueStats = eventQueue.getStats();
            NonBlockingHandler.HandlerStats handlerStats = handler.getStats();

            // 통계 정보를 구조화된 형태로 로그 출력
            logger.info("EventLoop 통계:");
            logger.info("  EventLoop: {}", eventLoop);
            logger.info("  Selector: {}", selectorStats);
            logger.info("  Queue: {}", queueStats);
            logger.info("  Handler: {}", handlerStats);

        } catch (Exception e) {
            logger.error("통계 로그 출력 중 오류", e);
        }
    }

    /**
     * 리소스 정리
     *
     * 추가적인 정리 작업이 필요한 경우 이 메서드에 구현
     * 현재는 비어있지만 확장 가능성을 위해 존재
     */
    private void cleanup() {
        // 추가 정리 작업이 필요한 경우 여기에 추가
        // 예: 캐시 정리, 외부 연결 해제, 임시 파일 삭제 등
    }

    /**
     * 비동기 작업 실행
     *
     * 외부에서 EventLoop에 작업을 제출할 수 있는 인터페이스
     *
     * @param task 실행할 작업 (Runnable)
     */
    public void execute(Runnable task) {
        // EventQueue를 통해 작업을 EventLoop에 전달
        eventQueue.execute(task);
    }

    /**
     * 비동기 작업 실행 (CompletableFuture 반환)
     *
     * 작업의 결과를 비동기적으로 받을 수 있는 인터페이스
     *
     * @param task 실행할 작업 (Supplier<T>)
     * @param <T> 작업 결과 타입
     * @return 작업 결과를 담은 CompletableFuture
     */
    public <T> java.util.concurrent.CompletableFuture<T> executeAsync(java.util.function.Supplier<T> task) {
        return eventQueue.executeAsync(task);
    }

    /**
     * 지연 실행
     *
     * 특정 시간 후에 작업을 실행하도록 스케줄링
     *
     * @param task 실행할 작업
     * @param delay 지연 시간
     * @param unit 시간 단위
     * @return 스케줄된 작업을 제어할 수 있는 ScheduledFuture
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return eventQueue.schedule(task, delay, unit);
    }

    /**
     * 현재 실행 중인지 확인
     *
     * @return 프로세서와 EventLoop가 모두 실행 중이면 true
     */
    public boolean isRunning() {
        // started.get(): AtomicBoolean의 현재 값
        // eventLoop.isRunning(): EventLoop의 실행 상태
        // &&: 논리곱 연산 (둘 다 true여야 true)
        return started.get() && eventLoop.isRunning();
    }

    /**
     * 서버 포트 정보
     *
     * 실제로 바인딩된 포트 번호 반환 (포트 0으로 시작한 경우 유용)
     *
     * @return 서버 포트 번호, 오류시 -1
     */
    public int getPort() {
        try {
            // 서버 채널이 열려있는지 확인
            if (serverChannel != null && serverChannel.isOpen()) {
                // getLocalAddress(): 바인딩된 로컬 주소 반환
                // InetSocketAddress로 캐스팅하여 포트 정보 추출
                return ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
            }
        } catch (IOException e) {
            logger.error("서버 포트 정보 획득 중 오류", e);
        }
        return -1; // 오류 또는 서버가 시작되지 않음
    }

    /**
     * EventQueue 반환
     *
     * 외부에서 EventQueue에 직접 접근해야 할 때 사용
     *
     * @return EventQueue 인스턴스
     */
    public EventQueue getEventQueue() {
        return eventQueue;
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
     * 통계 정보 반환
     *
     * 모든 컴포넌트의 통계를 종합한 정보 제공
     *
     * @return ProcessorStats 객체
     */
    public ProcessorStats getStats() {
        return new ProcessorStats(
                isRunning(),                      // 실행 상태
                eventLoop.getTotalLoops(),        // 총 이벤트 루프 실행 횟수
                eventLoop.getTotalTasksExecuted(), // 총 실행된 작업 수
                selectorManager.getStats(),       // Selector 관련 통계
                eventQueue.getStats(),            // EventQueue 관련 통계
                handler.getStats()                // Handler 관련 통계
        );
    }

    /**
     * EventLoopProcessor 설정 클래스
     *
     * 빌더 패턴으로 구현하여 유연한 설정 가능
     * 각 설정값에 대한 기본값과 세터 메서드 제공
     */
    public static class ProcessorConfig {
        // 기본 설정 값들
        private int cleanupInterval = 30;        // 정리 작업 주기 (초)
        private int connectionTimeout = 30000;   // 연결 타임아웃 (밀리초)
        private int maxRequestSize = 1024 * 1024; // 최대 요청 크기 (1MB)
        private int responseBufferSize = 8192;   // 응답 버퍼 크기 (8KB)

        // === Getter 메서드들 ===

        /**
         * 정리 작업 주기 반환
         */
        public int getCleanupInterval() {
            return cleanupInterval;
        }

        /**
         * 정리 작업 주기 설정
         *
         * @param cleanupInterval 정리 작업 주기 (초)
         * @return 메서드 체이닝을 위한 자기 자신
         */
        public ProcessorConfig setCleanupInterval(int cleanupInterval) {
            this.cleanupInterval = cleanupInterval;
            return this; // 메서드 체이닝 지원
        }

        /**
         * 연결 타임아웃 반환
         */
        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        /**
         * 연결 타임아웃 설정
         *
         * @param connectionTimeout 연결 타임아웃 (밀리초)
         * @return 메서드 체이닝을 위한 자기 자신
         */
        public ProcessorConfig setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        /**
         * 최대 요청 크기 반환
         */
        public int getMaxRequestSize() {
            return maxRequestSize;
        }

        /**
         * 최대 요청 크기 설정
         *
         * @param maxRequestSize 최대 요청 크기 (바이트)
         * @return 메서드 체이닝을 위한 자기 자신
         */
        public ProcessorConfig setMaxRequestSize(int maxRequestSize) {
            this.maxRequestSize = maxRequestSize;
            return this;
        }

        /**
         * 응답 버퍼 크기 반환
         */
        public int getResponseBufferSize() {
            return responseBufferSize;
        }

        /**
         * 응답 버퍼 크기 설정
         *
         * @param responseBufferSize 응답 버퍼 크기 (바이트)
         * @return 메서드 체이닝을 위한 자기 자신
         */
        public ProcessorConfig setResponseBufferSize(int responseBufferSize) {
            this.responseBufferSize = responseBufferSize;
            return this;
        }
    }

    /**
     * EventLoopProcessor 통계 정보
     *
     * 모든 하위 컴포넌트의 통계를 종합한 불변 객체
     */
    public static class ProcessorStats {
        // final: 생성 후 변경 불가능한 필드들
        private final boolean running;                                      // 실행 상태
        private final long totalLoops;                                      // 총 이벤트 루프 실행 횟수
        private final long totalTasks;                                      // 총 실행된 작업 수
        private final SelectorManager.SelectorStats selectorStats;          // Selector 통계
        private final EventQueue.QueueStats queueStats;                     // EventQueue 통계
        private final NonBlockingHandler.HandlerStats handlerStats;         // Handler 통계

        /**
         * 생성자 - 모든 통계 정보를 한번에 초기화
         *
         * @param running 실행 상태
         * @param totalLoops 총 루프 수
         * @param totalTasks 총 작업 수
         * @param selectorStats Selector 통계
         * @param queueStats Queue 통계
         * @param handlerStats Handler 통계
         */
        public ProcessorStats(boolean running, long totalLoops, long totalTasks,
                              SelectorManager.SelectorStats selectorStats,
                              EventQueue.QueueStats queueStats,
                              NonBlockingHandler.HandlerStats handlerStats) {
            this.running = running;
            this.totalLoops = totalLoops;
            this.totalTasks = totalTasks;
            this.selectorStats = selectorStats;
            this.queueStats = queueStats;
            this.handlerStats = handlerStats;
        }

        // === Getter 메서드들 ===

        /**
         * 실행 상태 반환
         */
        public boolean isRunning() {
            return running;
        }

        /**
         * 총 루프 실행 횟수 반환
         */
        public long getTotalLoops() {
            return totalLoops;
        }

        /**
         * 총 실행된 작업 수 반환
         */
        public long getTotalTasks() {
            return totalTasks;
        }

        /**
         * Selector 통계 반환
         */
        public SelectorManager.SelectorStats getSelectorStats() {
            return selectorStats;
        }

        /**
         * EventQueue 통계 반환
         */
        public EventQueue.QueueStats getQueueStats() {
            return queueStats;
        }

        /**
         * Handler 통계 반환
         */
        public NonBlockingHandler.HandlerStats getHandlerStats() {
            return handlerStats;
        }

        /**
         * 통계 정보의 문자열 표현
         *
         * 모든 통계를 포함한 요약 정보
         *
         * @return 포맷된 통계 문자열
         */
        @Override
        public String toString() {
            // String.format(): printf 스타일의 문자열 포맷팅
            // 각 컴포넌트의 통계를 하나의 문자열로 결합
            return String.format("ProcessorStats{running=%s, loops=%d, tasks=%d, %s, %s, %s}",
                    running,        // 실행 상태
                    totalLoops,     // 총 루프 수
                    totalTasks,     // 총 작업 수
                    selectorStats,  // Selector 통계 (toString() 자동 호출)
                    queueStats,     // Queue 통계 (toString() 자동 호출)
                    handlerStats);  // Handler 통계 (toString() 자동 호출)
        }
    }
}