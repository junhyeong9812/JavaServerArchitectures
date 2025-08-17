package server.hybrid;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.http.*;
import server.core.routing.*;
import server.core.mini.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * 하이브리드 HTTP 서버 - NIO + Thread Pool 조합
 *
 * 핵심 개념:
 * 1. NIO Selector로 I/O 이벤트 감지 (Non-blocking)
 * 2. Thread Pool에서 CPU 집약적 작업 처리
 * 3. AsyncContext로 I/O 대기시 스레드 해제
 * 4. 컨텍스트 스위칭으로 효율적 자원 활용
 *
 * Threaded vs Hybrid 차이점:
 * - Threaded: 요청당 스레드 할당, I/O 대기시에도 스레드 점유
 * - Hybrid: I/O는 NIO로 감지, CPU 작업만 스레드 사용
 */
public class HybridServer {

    // === 로깅 시스템 ===
    // SLF4J 로거 인스턴스 생성 - 하이브리드 서버 관련 로깅용
    private static final Logger logger = LoggerFactory.getLogger(HybridServer.class);
    // static final로 클래스당 하나의 로거 인스턴스 공유

    // === 서버 설정 ===
    private final int port;                              // 서버 포트 번호
    private final ServerSocketChannel serverChannel;    // NIO 서버 소켓 채널
    // ServerSocketChannel 사용 이유:
    // 1. 논블로킹 방식으로 클라이언트 연결 수락
    // 2. Selector와 함께 사용하여 이벤트 기반 처리
    // 3. 전통적인 ServerSocket보다 높은 성능과 확장성

    private final Selector selector;                     // NIO Selector - I/O 이벤트 멀티플렉싱
    // Selector 사용 이유:
    // 1. 단일 스레드로 수천 개의 연결 동시 처리
    // 2. I/O 이벤트 발생시에만 처리하여 CPU 효율성 극대화
    // 3. 컨텍스트 스위칭 오버헤드 최소화

    // === 실행 상태 관리 ===
    private final AtomicBoolean running = new AtomicBoolean(false);     // 서버 실행 상태
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false); // 종료 요청 플래그
    // AtomicBoolean 사용으로 멀티스레드 환경에서 안전한 상태 관리

    private Thread selectorThread;                       // Selector 루프 실행 스레드
    // 별도 스레드에서 Selector 이벤트 루프 실행

    // === 하이브리드 처리 컴포넌트 ===
    private final HybridProcessor processor;             // 하이브리드 요청 처리기
    // 동기/비동기 처리 전략을 동적으로 결정하는 핵심 컴포넌트

    private final AsyncContextManager contextManager;    // 비동기 컨텍스트 관리자
    // 스레드와 분리된 요청 상태 보관 및 생명주기 관리

    private final AdaptiveThreadPool threadPool;         // 적응형 스레드풀
    // 동적 크기 조정과 우선순위 기반 작업 스케줄링

    private final ContextSwitchingHandler switchingHandler; // 컨텍스트 스위칭 핸들러
    // I/O 대기시 스레드 해제와 재할당을 담당

    // === 라우팅 및 서블릿 ===
    private final Router router;                         // HTTP 라우팅 시스템
    // URL 패턴 기반 요청 라우팅과 핸들러 매핑

    private final HybridMiniServletContainer servletContainer; // 하이브리드 서블릿 컨테이너
    // 서블릿 생명주기 관리와 비동기 서블릿 지원

    // === 연결 관리 ===
    private final Map<SocketChannel, ChannelContext> channelContexts; // 채널별 컨텍스트 맵
    // 각 클라이언트 연결의 상태와 메타데이터 관리
    // ConcurrentHashMap으로 멀티스레드 안전성 보장

    private final AtomicLong connectionCounter = new AtomicLong(0);    // 연결 카운터
    // 고유한 연결 ID 생성을 위한 원자적 카운터

    // === 성능 메트릭 ===
    private final AtomicLong totalRequests = new AtomicLong(0);        // 총 요청 수
    private final AtomicLong activeConnections = new AtomicLong(0);     // 활성 연결 수
    private final AtomicLong contextSwitches = new AtomicLong(0);       // 컨텍스트 스위치 횟수
    // AtomicLong으로 멀티스레드 환경에서 정확한 성능 메트릭 수집

    /**
     * HybridServer 생성자
     *
     * @param port 서버 포트 번호
     * @throws IOException NIO 초기화 실패시
     */
    public HybridServer(int port) throws IOException {
        this.port = port;

        // NIO 컴포넌트 초기화
        this.serverChannel = ServerSocketChannel.open();
        // ServerSocketChannel.open() 사용으로 새로운 서버 소켓 채널 생성

        this.serverChannel.configureBlocking(false);
        // configureBlocking(false) 설정 이유:
        // 1. 논블로킹 모드로 설정하여 accept() 호출시 블로킹 방지
        // 2. Selector와 함께 사용하기 위한 필수 설정
        // 3. 단일 스레드에서 여러 연결을 효율적으로 처리

        this.serverChannel.bind(new InetSocketAddress(port));
        // 지정된 포트에 서버 소켓 바인딩

        this.selector = Selector.open();
        // 새로운 Selector 인스턴스 생성

        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        // 서버 채널을 Selector에 등록하여 ACCEPT 이벤트 감지
        // OP_ACCEPT: 새로운 클라이언트 연결 수락 준비 완료 이벤트

        // 하이브리드 처리 컴포넌트 초기화
        this.threadPool = new AdaptiveThreadPool("Hybrid-Worker", 4, 64, 60L);
        // 적응형 스레드풀 생성:
        // - 이름: "Hybrid-Worker" (디버깅용)
        // - 최소 4개, 최대 64개 스레드
        // - 60초 유휴 시간 후 스레드 정리

        this.contextManager = new AsyncContextManager();
        // 기본 30초 타임아웃으로 컨텍스트 매니저 생성

        this.switchingHandler = new ContextSwitchingHandler(threadPool, contextManager);
        // 컨텍스트 스위칭 핸들러 초기화 (스레드풀과 컨텍스트 매니저 연결)

        this.processor = new HybridProcessor(threadPool, contextManager);
        // 하이브리드 처리기 초기화 (적응형 처리 전략 지원)

        // 라우팅 시스템 초기화
        this.router = new Router();
        // HTTP 요청 라우팅을 위한 라우터 생성

        this.servletContainer = new HybridMiniServletContainer(router, processor);
        // 서블릿 컨테이너 초기화 (라우터와 처리기 연결)

        // 동시성 안전한 컬렉션으로 채널 컨텍스트 관리
        this.channelContexts = new ConcurrentHashMap<>();
        // ConcurrentHashMap 사용으로 멀티스레드 환경에서 안전한 채널 상태 관리

        logger.info("하이브리드 서버 초기화 완료 - 포트: {}, NIO + Thread Pool 모드", port);
    }

    /**
     * 서버 시작
     */
    public void start() {
        // 서버 실행 상태를 원자적으로 변경 (false -> true)
        if (!running.compareAndSet(false, true)) {
            logger.warn("서버가 이미 실행 중입니다");
            return;
        }
        // compareAndSet() 사용으로 중복 시작 방지

        logger.info("하이브리드 서버 시작 중... 포트: {}", port);

        // Selector 이벤트 루프를 별도 스레드에서 실행
        selectorThread = new Thread(this::runSelectorLoop, "Hybrid-Selector");
        // 메서드 참조(::)로 Selector 루프 메서드 전달
        selectorThread.setDaemon(false);
        // 데몬 스레드가 아니므로 JVM 종료를 방해하지 않음
        selectorThread.start();
        // 스레드 시작으로 NIO 이벤트 루프 실행

        logger.info("하이브리드 서버 시작 완료 - NIO Selector 루프 실행 중");
    }

    /**
     * NIO Selector 이벤트 루프 - 하이브리드 서버의 핵심!
     */
    private void runSelectorLoop() {
        logger.info("NIO Selector 루프 시작");

        try {
            // 서버 실행 중이고 종료 요청이 없는 동안 계속 루프
            while (running.get() && !shutdownRequested.get()) {
                // select() 호출로 I/O 이벤트 대기 (최대 1초)
                int readyChannels = selector.select(1000);
                // select(timeout) 사용 이유:
                // 1. I/O 이벤트 발생까지 대기하되 최대 1초로 제한
                // 2. 타임아웃으로 주기적인 상태 체크 가능
                // 3. 무한 대기 방지로 정상적인 종료 지원

                if (readyChannels == 0) {
                    continue; // 이벤트가 없으면 다음 루프 계속
                }

                // 준비된 SelectionKey들을 순회하며 처리
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                // Iterator 사용으로 순회 중 안전한 제거 가능

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove(); // 처리한 키는 즉시 제거
                    // remove() 호출로 다음 select() 호출에서 중복 처리 방지

                    if (!key.isValid()) {
                        continue; // 유효하지 않은 키는 건너뛰기
                    }

                    try {
                        // 이벤트 타입별 처리 분기
                        if (key.isAcceptable()) {
                            handleAccept(key); // 새로운 연결 수락
                        } else if (key.isReadable()) {
                            handleRead(key); // 클라이언트로부터 데이터 읽기
                        } else if (key.isWritable()) {
                            handleWrite(key); // 클라이언트에게 데이터 쓰기
                        }
                    } catch (IOException e) {
                        // I/O 오류 발생시 해당 채널 정리
                        logger.warn("채널 처리 중 오류 발생: {}", e.getMessage());
                        closeChannel(key);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Selector 루프 오류", e);
        } finally {
            cleanup(); // 리소스 정리
            logger.info("NIO Selector 루프 종료");
        }
    }

    /**
     * 새로운 클라이언트 연결 수락
     */
    private void handleAccept(SelectionKey key) throws IOException {
        // 서버 채널에서 새로운 클라이언트 연결 수락
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        // accept() 호출로 대기 중인 클라이언트 연결 수락

        if (clientChannel != null) {
            // 클라이언트 채널을 논블로킹 모드로 설정
            clientChannel.configureBlocking(false);
            // 논블로킹 모드 설정으로 읽기/쓰기 작업에서 블로킹 방지

            // 클라이언트 채널을 Selector에 등록하여 READ 이벤트 감지
            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
            // OP_READ: 클라이언트로부터 데이터 읽기 준비 완료 이벤트

            // 고유한 연결 ID 생성
            long connectionId = connectionCounter.incrementAndGet();
            // 원자적 증가로 중복 없는 연결 ID 보장

            // 연결별 컨텍스트 생성
            ChannelContext context = new ChannelContext(connectionId, clientChannel);
            // 채널의 상태와 HTTP 요청 데이터를 관리하는 컨텍스트

            // SelectionKey에 컨텍스트 첨부
            clientKey.attach(context);
            // attach()로 키와 컨텍스트 연결하여 이벤트 처리시 사용

            // 채널 컨텍스트 맵에 등록
            channelContexts.put(clientChannel, context);
            activeConnections.incrementAndGet(); // 활성 연결 수 증가

            logger.debug("새 연결 수락 - ID: {}, 활성 연결: {}", connectionId, activeConnections.get());
        }
    }

    /**
     * 클라이언트로부터 데이터 읽기
     */
    private void handleRead(SelectionKey key) throws IOException {
        // SelectionKey에서 채널 컨텍스트 추출
        ChannelContext context = (ChannelContext) key.attachment();

        if (context == null) {
            logger.warn("컨텍스트가 없는 READ 이벤트");
            closeChannel(key);
            return;
        }

        SocketChannel channel = context.getChannel();
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        // 8KB 버퍼로 충분한 HTTP 요청 데이터 수용

        try {
            // 채널에서 데이터 읽기
            int bytesRead = channel.read(buffer);
            // read() 호출로 논블로킹 방식으로 데이터 읽기

            if (bytesRead == -1) {
                // 클라이언트가 연결을 정상적으로 종료한 경우
                logger.debug("클라이언트 연결 종료 - ID: {}", context.getConnectionId());
                closeChannel(key);
                return;
            }

            if (bytesRead == 0) {
                return; // 읽을 데이터가 없으면 다음 이벤트까지 대기
            }

            // 읽은 데이터를 컨텍스트에 추가
            buffer.flip(); // 읽기 모드로 전환
            context.appendData(buffer);
            // 부분적으로 받은 HTTP 요청 데이터를 누적

            // HTTP 요청이 완성되었는지 확인
            if (context.isRequestComplete()) {
                processCompleteRequest(context, key);
                // 완성된 요청을 스레드 풀에서 처리
            }

        } catch (IOException e) {
            logger.warn("데이터 읽기 실패 - 연결 ID: {}, 오류: {}", context.getConnectionId(), e.getMessage());
            closeChannel(key);
        }
    }

    /**
     * 완전한 HTTP 요청을 Thread Pool에서 처리
     */
    private void processCompleteRequest(ChannelContext context, SelectionKey key) {
        totalRequests.incrementAndGet(); // 총 요청 수 증가

        // READ 이벤트 관심 제거 (중복 처리 방지)
        key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
        // 비트 연산으로 OP_READ 플래그만 제거

        // 스레드 풀에 요청 처리 작업 제출
        threadPool.submit(() -> {
            try {
                contextSwitches.incrementAndGet(); // 컨텍스트 스위치 카운트 증가

                logger.debug("Thread Pool에서 요청 처리 시작 - 연결 ID: {}, 스레드: {}",
                        context.getConnectionId(), Thread.currentThread().getName());

                // HTTP 요청 파싱 - Core의 HttpParser 사용
                String requestData = context.getRequestData();
                ByteArrayInputStream inputStream = new ByteArrayInputStream(
                        requestData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                // 문자열을 InputStream으로 변환하여 파서에 전달

                HttpRequest request = HttpParser.parseRequest(inputStream);
                // Core 모듈의 HTTP 파서로 요청 객체 생성

                // 라우터를 통한 요청 처리
                CompletableFuture<HttpResponse> responseFuture = router.route(request);
                // 라우터가 적절한 핸들러를 찾아 요청 처리

                responseFuture.whenComplete((response, throwable) -> {
                    // 비동기 처리 완료 후 결과 처리
                    if (throwable != null) {
                        logger.error("요청 처리 중 오류", throwable);
                        response = HttpResponse.internalServerError("Internal Server Error");
                        // 예외 발생시 500 에러 응답 생성
                    }

                    context.setResponse(response); // 컨텍스트에 응답 저장
                    selector.wakeup(); // Selector 루프 깨우기
                    // wakeup() 호출로 select() 호출에서 즉시 반환
                    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                    // WRITE 이벤트 관심 추가로 응답 전송 준비

                    logger.debug("응답 준비 완료 - 연결 ID: {}", context.getConnectionId());
                });

            } catch (Exception e) {
                logger.error("요청 처리 실패 - 연결 ID: {}", context.getConnectionId(), e);
                context.setResponse(HttpResponse.internalServerError("Internal Server Error"));
                selector.wakeup();
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            }
        });
        // 람다식으로 작업 정의하여 스레드 풀에 제출
    }

    /**
     * 클라이언트에게 응답 데이터 쓰기
     */
    private void handleWrite(SelectionKey key) throws IOException {
        // SelectionKey에서 채널 컨텍스트 추출
        ChannelContext context = (ChannelContext) key.attachment();

        if (context == null || !context.hasResponse()) {
            return; // 컨텍스트나 응답이 없으면 대기
        }

        SocketChannel channel = context.getChannel();
        HttpResponse response = context.getResponse();

        // HTTP 응답을 바이트 배열로 변환
        byte[] responseBytes = response.toByteArray();
        ByteBuffer buffer = ByteBuffer.wrap(responseBytes);
        // ByteBuffer로 래핑하여 NIO 채널에서 전송 준비

        try {
            // 채널에 응답 데이터 쓰기
            int bytesWritten = channel.write(buffer);
            // write() 호출로 논블로킹 방식으로 데이터 전송

            logger.debug("응답 전송 완료 - 연결 ID: {}, 바이트: {}",
                    context.getConnectionId(), bytesWritten);

            closeChannel(key); // 응답 전송 후 연결 종료
            // HTTP/1.0 방식으로 요청-응답 후 연결 종료

        } catch (IOException e) {
            logger.warn("응답 쓰기 실패 - 연결 ID: {}", context.getConnectionId(), e);
            closeChannel(key);
        }
    }

    /**
     * 채널 정리 및 종료
     */
    private void closeChannel(SelectionKey key) {
        try {
            // SelectionKey에서 채널 컨텍스트 추출
            ChannelContext context = (ChannelContext) key.attachment();

            if (context != null) {
                SocketChannel channel = context.getChannel();
                channelContexts.remove(channel); // 컨텍스트 맵에서 제거
                activeConnections.decrementAndGet(); // 활성 연결 수 감소

                logger.debug("연결 종료 - ID: {}, 활성 연결: {}",
                        context.getConnectionId(), activeConnections.get());
            }

            // SelectionKey 취소 및 채널 닫기
            key.cancel(); // Selector에서 키 제거
            if (key.channel().isOpen()) {
                key.channel().close(); // 채널 닫기
            }

        } catch (IOException e) {
            logger.warn("채널 종료 중 오류", e);
        }
    }

    /**
     * 서버 우아한 종료
     */
    public void stop() {
        // 서버 실행 상태를 원자적으로 변경 (true -> false)
        if (!running.compareAndSet(true, false)) {
            logger.warn("서버가 이미 중지되었습니다");
            return;
        }

        logger.info("하이브리드 서버 종료 시작...");
        shutdownRequested.set(true); // 종료 요청 플래그 설정
        selector.wakeup(); // Selector 루프 깨우기

        try {
            // Selector 스레드 종료 대기 (최대 5초)
            if (selectorThread != null) {
                selectorThread.join(5000);
                // join(timeout)으로 정상 종료 대기
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("서버 종료 대기 중 인터럽트 발생");
        }

        logger.info("하이브리드 서버 종료 완료");
    }

    /**
     * 리소스 정리
     */
    private void cleanup() {
        try {
            // 모든 활성 연결 정리
            for (ChannelContext context : channelContexts.values()) {
                try {
                    context.getChannel().close();
                } catch (IOException e) {
                    logger.warn("채널 정리 중 오류", e);
                }
            }
            channelContexts.clear(); // 컨텍스트 맵 정리

            threadPool.shutdown(); // 스레드 풀 종료

            // NIO 리소스 정리
            if (selector.isOpen()) {
                selector.close(); // Selector 닫기
            }

            if (serverChannel.isOpen()) {
                serverChannel.close(); // 서버 채널 닫기
            }

        } catch (IOException e) {
            logger.error("리소스 정리 중 오류", e);
        }
    }

    // === Getters ===

    public Router getRouter() {
        return router;
    }

    public HybridMiniServletContainer getServletContainer() {
        return servletContainer;
    }

    public ContextSwitchingHandler getSwitchingHandler() {
        return switchingHandler;
    }

    public ServerStatus getStatus() {
        // 현재 서버 상태의 스냅샷을 불변 객체로 반환
        return new ServerStatus(
                running.get(),
                activeConnections.get(),
                totalRequests.get(),
                contextSwitches.get(),
                threadPool.getActiveCount(),
                threadPool.getPoolSize()
        );
    }

    /**
     * 서버 상태 정보 클래스
     */
    public static class ServerStatus {
        // 서버 상태 정보를 담는 불변 데이터 클래스

        private final boolean running; // 서버 실행 상태
        private final long activeConnections; // 활성 연결 수
        private final long totalRequests; // 총 요청 수
        private final long contextSwitches; // 컨텍스트 스위치 횟수
        private final int activeThreads; // 활성 스레드 수
        private final int totalThreads; // 총 스레드 수
        // 모든 필드를 final로 선언하여 불변성 보장

        public ServerStatus(boolean running, long activeConnections, long totalRequests,
                            long contextSwitches, int activeThreads, int totalThreads) {
            // 생성자에서 모든 상태 값 초기화
            this.running = running;
            this.activeConnections = activeConnections;
            this.totalRequests = totalRequests;
            this.contextSwitches = contextSwitches;
            this.activeThreads = activeThreads;
            this.totalThreads = totalThreads;
        }

        // 접근자 메서드들 - 불변 객체이므로 getter만 제공
        public boolean isRunning() { return running; }
        public long getActiveConnections() { return activeConnections; }
        public long getTotalRequests() { return totalRequests; }
        public long getContextSwitches() { return contextSwitches; }
        public int getActiveThreads() { return activeThreads; }
        public int getTotalThreads() { return totalThreads; }

        @Override
        public String toString() {
            // 모든 상태 정보를 읽기 쉬운 형태로 포맷팅
            return String.format(
                    "HybridServer{running=%s, connections=%d, requests=%d, " +
                            "contextSwitches=%d, threads=%d/%d}",
                    running, activeConnections, totalRequests,
                    contextSwitches, activeThreads, totalThreads
            );
            // 운영자가 한눈에 파악할 수 있는 핵심 지표들을 간결하게 표현
        }
    }
}