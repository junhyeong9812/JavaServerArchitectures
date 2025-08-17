package server.eventloop;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Iterator;
import java.util.Set;
import java.io.IOException;

/**
 * 핵심 이벤트루프 엔진
 * 단일 스레드에서 모든 I/O 이벤트를 처리
 *
 * 이벤트루프 패턴:
 * - 하나의 스레드가 모든 I/O 이벤트를 순차적으로 처리
 * - 논블로킹 I/O를 사용하여 높은 동시성 달성
 * - CPU 집약적 작업은 별도 스레드 풀로 위임
 * - 메모리 사용량이 적고 컨텍스트 스위칭 오버헤드 최소화
 */
public class EventLoop {

    // Logger 인스턴스 - 이벤트루프 동작 상황 추적용
    private static final Logger logger = LoggerFactory.getLogger(EventLoop.class);

    // NIO Selector: 여러 채널의 I/O 이벤트를 하나의 스레드에서 관리
    // 핵심 컴포넌트 - 모든 소켓 채널들을 등록하고 이벤트 감지
    private final Selector selector;

    // 이벤트루프 전용 스레드 - 모든 I/O 처리를 이 스레드에서 수행
    private final Thread eventLoopThread;

    // AtomicBoolean: 스레드 안전한 boolean 값
    // 이벤트루프 실행 상태 관리 (start/stop)
    private final AtomicBoolean running;

    // ConcurrentLinkedQueue: 스레드 안전한 논블로킹 큐
    // 다른 스레드에서 이벤트루프로 작업을 전달할 때 사용
    private final ConcurrentLinkedQueue<Runnable> taskQueue;

    // AtomicLong: 스레드 안전한 long 값
    // 큐에 추가된 작업의 총 개수 추적
    private final AtomicLong taskCount;

    // 성능 통계용 변수들
    // volatile: 다른 스레드에서 읽을 때 최신 값 보장
    private volatile long lastLoopTime;        // 마지막 루프 실행 시간 (나노초)
    private volatile long totalLoops;          // 총 루프 실행 횟수
    private volatile long totalTasksExecuted;  // 총 실행된 작업 수

    /**
     * EventLoop 생성자
     *
     * @throws IOException Selector 생성 실패시
     */
    public EventLoop() throws IOException {
        // Selector.open(): 새로운 Selector 인스턴스 생성
        // 운영체제의 I/O 멀티플렉싱 기능 활용 (epoll, kqueue 등)
        this.selector = Selector.open();

        // AtomicBoolean 초기값 false로 설정 (아직 실행되지 않음)
        this.running = new AtomicBoolean(false);

        // 작업 큐와 카운터 초기화
        this.taskQueue = new ConcurrentLinkedQueue<>();
        this.taskCount = new AtomicLong(0);

        // 이벤트루프 전용 스레드 생성
        // runEventLoop 메서드를 실행할 스레드
        this.eventLoopThread = new Thread(this::runEventLoop, "EventLoop-Main");

        // setDaemon(false): 메인 스레드로 설정
        // 이 스레드가 살아있는 동안 JVM이 종료되지 않음
        this.eventLoopThread.setDaemon(false);
    }

    /**
     * 이벤트루프 시작
     *
     * compareAndSet(): 원자적 조건부 설정
     * - 현재 값이 false이면 true로 변경하고 true 반환
     * - 이미 true이면 변경하지 않고 false 반환
     * - 중복 시작 방지
     */
    public void start() {
        // 중복 시작 방지를 위한 원자적 체크
        if (running.compareAndSet(false, true)) {
            logger.info("이벤트루프 시작 중...");
            // 스레드 실행 - runEventLoop 메서드가 별도 스레드에서 실행됨
            eventLoopThread.start();
        }
    }

    /**
     * 이벤트루프 종료
     *
     * 안전한 종료 절차:
     * 1. 실행 플래그를 false로 변경
     * 2. selector.wakeup()으로 블로킹된 select() 호출 깨우기
     * 3. 스레드가 종료될 때까지 대기
     * 4. 리소스 정리
     */
    public void shutdown() {
        // 중복 종료 방지를 위한 원자적 체크
        if (running.compareAndSet(true, false)) {
            logger.info("이벤트루프 종료 중...");

            // selector.wakeup(): 블로킹된 select() 호출을 즉시 리턴시킴
            // 이벤트루프가 종료 조건을 확인할 수 있도록 함
            selector.wakeup();

            try {
                // join(5000): 최대 5초 동안 스레드 종료 대기
                // 스레드가 5초 내에 종료되지 않으면 강제 진행
                eventLoopThread.join(5000);

                // selector.close(): Selector 리소스 해제
                // 운영체제 레벨의 I/O 멀티플렉싱 자원 해제
                selector.close();
            } catch (Exception e) {
                logger.error("이벤트루프 종료 중 오류 발생", e);
            }
        }
    }

    /**
     * Selector 반환 (SelectorManager에서 사용)
     *
     * 외부에서 채널을 직접 등록해야 할 때 사용
     *
     * @return 내부 Selector 인스턴스
     */
    public Selector getSelector() {
        return selector;
    }

    /**
     * 서버 소켓 채널 등록
     *
     * 새로운 클라이언트 연결을 수락하는 ServerSocketChannel을 등록
     * OP_ACCEPT 이벤트에 관심 표시
     *
     * @param serverChannel 등록할 서버 소켓 채널
     * @param handler 연결 수락 이벤트를 처리할 핸들러
     */
    public void registerServerSocket(ServerSocketChannel serverChannel,
                                     ServerSocketEventHandler handler) {
        // execute(): 이벤트루프 스레드에서 실행되도록 작업을 큐에 추가
        // 다른 스레드에서 호출되어도 안전하게 처리됨
        execute(() -> {
            try {
                // configureBlocking(false): 논블로킹 모드로 설정
                // 블로킹 모드에서는 Selector와 함께 사용할 수 없음
                serverChannel.configureBlocking(false);

                // register(): 채널을 Selector에 등록
                // SelectionKey.OP_ACCEPT: 새로운 연결 수락 이벤트에 관심 표시
                SelectionKey key = serverChannel.register(selector, SelectionKey.OP_ACCEPT);

                // attach(): SelectionKey에 핸들러 객체 연결
                // 나중에 이벤트 발생시 이 핸들러를 사용해 처리
                key.attach(handler);

                // getLocalAddress(): 서버가 바인딩된 주소 반환
                logger.info("서버 소켓 등록 완료: {}",
                        serverChannel.getLocalAddress());
            } catch (IOException e) {
                logger.error("서버 소켓 등록 실패", e);
            }
        });
    }

    /**
     * 클라이언트 소켓 채널 등록
     *
     * 클라이언트와의 통신을 위한 SocketChannel을 등록
     * OP_READ 이벤트에 관심 표시 (클라이언트로부터 데이터 읽기 준비)
     *
     * @param clientChannel 등록할 클라이언트 소켓 채널
     * @param handler 클라이언트 이벤트를 처리할 핸들러
     */
    public void registerClientSocket(SocketChannel clientChannel,
                                     ClientSocketEventHandler handler) {
        execute(() -> {
            try {
                // 논블로킹 모드로 설정
                clientChannel.configureBlocking(false);

                // SelectionKey.OP_READ: 읽기 가능 이벤트에 관심 표시
                // 클라이언트가 데이터를 보내면 이벤트 발생
                SelectionKey key = clientChannel.register(selector,
                        SelectionKey.OP_READ);

                // 핸들러를 키에 연결
                key.attach(handler);

                // getRemoteAddress(): 클라이언트의 주소 반환
                logger.debug("클라이언트 소켓 등록 완료: {}",
                        clientChannel.getRemoteAddress());
            } catch (IOException e) {
                logger.error("클라이언트 소켓 등록 실패", e);
                // 등록 실패시 채널 정리
                closeChannel(clientChannel);
            }
        });
    }

    /**
     * 비동기 작업을 이벤트루프에 스케줄링
     *
     * 다른 스레드에서 이벤트루프 스레드로 작업을 전달하는 메커니즘
     * 스레드 안전하게 작업을 큐에 추가하고 selector를 깨움
     *
     * @param task 실행할 작업 (Runnable)
     */
    public void execute(Runnable task) {
        // null 체크 - 잘못된 작업 전달 방지
        if (task == null) return;

        // offer(): 큐에 작업 추가 (논블로킹 연산)
        // ConcurrentLinkedQueue는 용량 제한이 없으므로 항상 성공
        taskQueue.offer(task);

        // incrementAndGet(): 원자적으로 1 증가하고 새 값 반환
        // 작업 통계 카운팅
        taskCount.incrementAndGet();

        // wakeup(): 블로킹된 select() 호출을 즉시 리턴시킨다
        // 새로운 작업이 추가되었음을 이벤트루프에 알림
        selector.wakeup();
    }

    /**
     * 현재 스레드가 이벤트루프 스레드인지 확인
     *
     * 이벤트루프 스레드에서만 수행해야 하는 작업들의 안전성 검증용
     *
     * @return 현재 스레드가 이벤트루프 스레드이면 true
     */
    public boolean inEventLoop() {
        // Thread.currentThread(): 현재 실행 중인 스레드 반환
        // == 연산자로 레퍼런스 비교 (동일한 스레드 객체인지)
        return Thread.currentThread() == eventLoopThread;
    }

    /**
     * 메인 이벤트루프 실행
     *
     * 이벤트루프의 핵심 로직:
     * 1. I/O 이벤트 처리 (selector.select())
     * 2. 큐에 있는 작업들 처리
     * 3. 통계 업데이트
     * 4. 반복
     */
    private void runEventLoop() {
        // Thread.currentThread().getName(): 현재 스레드 이름
        logger.info("이벤트루프가 스레드에서 시작됨: {}",
                Thread.currentThread().getName());

        // running.get(): AtomicBoolean 값 읽기
        // 이벤트루프가 실행 중인 동안 계속 반복
        while (running.get()) {
            try {
                // System.nanoTime(): 고정밀 시간 측정 (나노초)
                // 루프 성능 측정을 위한 시작 시간
                long loopStart = System.nanoTime();

                // 1. I/O 이벤트 처리
                processIOEvents();

                // 2. 큐에 쌓인 작업들 처리
                processTasks();

                // 3. 통계 업데이트
                updateStatistics(loopStart);

            } catch (OutOfMemoryError | StackOverflowError fatal) {
                // 치명적 오류 - 즉시 종료
                // 메모리 부족이나 스택 오버플로우는 복구 불가능
                logger.error("이벤트루프에서 치명적 오류: {}", fatal.getClass().getSimpleName(), fatal);
                break;
            } catch (Exception e) {
                // 일반적인 예외 - 로그 남기고 계속 실행
                // 하나의 요청 처리 실패가 전체 서버를 중단시키지 않도록
                logger.error("이벤트루프에서 오류 발생", e);

                // 연속적인 오류 발생시 잠시 대기
                // CPU 사용률 급증 방지
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    // interrupt 신호 받으면 종료
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Throwable throwable) {
                // 기타 심각한 오류
                logger.error("이벤트루프에서 심각한 오류 발생", throwable);
                break;
            }
        }

        // 이벤트루프 종료 로그와 통계
        logger.info("이벤트루프 종료됨. 총 루프: {}, 총 작업: {}",
                totalLoops, totalTasksExecuted);
    }

    /**
     * I/O 이벤트 처리
     *
     * Selector를 사용하여 준비된 채널들의 이벤트를 처리
     *
     * @throws IOException I/O 오류 발생시
     */
    private void processIOEvents() throws IOException {
        // select(1000): 최대 1초 동안 이벤트 대기
        // 준비된 채널 수 반환, 타임아웃시 0 반환
        int readyChannels = selector.select(1000);

        // 준비된 채널이 없으면 즉시 리턴
        if (readyChannels == 0) {
            return; // 타임아웃 또는 wakeup() 호출
        }

        // selectedKeys(): 이벤트가 발생한 SelectionKey들의 Set
        Set<SelectionKey> selectedKeys = selector.selectedKeys();

        // Iterator: 컬렉션의 요소들을 순차적으로 접근
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        // hasNext(): 다음 요소가 있는지 확인
        while (keyIterator.hasNext()) {
            // next(): 다음 SelectionKey 반환
            SelectionKey key = keyIterator.next();

            // remove(): 현재 키를 Set에서 제거
            // 중요: 제거하지 않으면 다음 select()에서도 계속 나타남
            keyIterator.remove();

            // isValid(): 키가 유효한지 확인
            // 채널이 닫히거나 취소되면 invalid 상태가 됨
            if (!key.isValid()) {
                logger.debug("유효하지 않은 키 발견, 건너뜀");
                continue;
            }

            try {
                // 이벤트 타입별 처리
                if (key.isAcceptable()) {
                    // 새로운 클라이언트 연결 수락
                    handleAccept(key);
                } else if (key.isReadable()) {
                    // 클라이언트로부터 데이터 읽기
                    handleRead(key);
                } else if (key.isWritable()) {
                    // 클라이언트로 데이터 쓰기
                    handleWrite(key);
                }
            } catch (Exception e) {
                // 더 상세한 에러 로그
                SocketChannel channel = null;
                try {
                    // instanceof: 객체의 타입 확인
                    if (key.channel() instanceof SocketChannel) {
                        channel = (SocketChannel) key.channel();
                        logger.error("채널 I/O 이벤트 처리 중 오류: {} - 작업: 읽기={}, 쓰기={}, 수락={} - 오류: {}",
                                channel.getRemoteAddress(),
                                key.isReadable(),
                                key.isWritable(),
                                key.isAcceptable(),
                                e.getMessage(), e);
                    } else {
                        logger.error("소켓이 아닌 채널에서 I/O 이벤트 처리 중 오류: {} - 오류: {}",
                                key.channel(), e.getMessage(), e);
                    }
                } catch (Exception ex) {
                    logger.error("오류 처리 중 채널 정보 획득 실패: {}", ex.getMessage());
                    logger.error("원본 오류: {}", e.getMessage(), e);
                }

                // 오류 발생한 연결 정리
                closeKey(key);
            }
        }
    }

    /**
     * 새로운 연결 수락
     *
     * ServerSocketChannel에서 새로운 클라이언트 연결을 수락
     *
     * @param key ACCEPT 이벤트가 발생한 SelectionKey
     * @throws IOException I/O 오류 발생시
     */
    private void handleAccept(SelectionKey key) throws IOException {
        // key.channel(): SelectionKey에 연결된 채널 반환
        // ServerSocketChannel로 캐스팅
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

        // key.attachment(): 등록시 attach한 객체 반환
        // ServerSocketEventHandler로 캐스팅
        ServerSocketEventHandler handler = (ServerSocketEventHandler) key.attachment();

        // accept(): 대기 중인 연결을 수락하고 SocketChannel 반환
        // 논블로킹 모드에서는 대기 중인 연결이 없으면 null 반환
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel != null) {
            // getRemoteAddress(): 클라이언트의 주소 정보
            logger.debug("새로운 연결 수락: {}",
                    clientChannel.getRemoteAddress());

            // 핸들러에게 새 연결 처리 위임
            handler.onAccept(this, clientChannel);
        }
    }

    /**
     * 데이터 읽기
     *
     * 클라이언트로부터 전송된 데이터를 읽어서 핸들러에 전달
     *
     * @param key READ 이벤트가 발생한 SelectionKey
     * @throws IOException I/O 오류 발생시
     */
    private void handleRead(SelectionKey key) throws IOException {
        // SocketChannel로 캐스팅
        SocketChannel channel = (SocketChannel) key.channel();

        // 클라이언트 이벤트 핸들러 획득
        ClientSocketEventHandler handler = (ClientSocketEventHandler) key.attachment();

        try {
            // ByteBuffer.allocate(8192): 8KB 크기의 새 바이트 버퍼 생성
            // 8192는 일반적인 네트워크 I/O 버퍼 크기
            ByteBuffer buffer = ByteBuffer.allocate(8192);

            // channel.read(buffer): 채널에서 데이터를 읽어 버퍼에 저장
            // 반환값: 실제 읽은 바이트 수, 연결 종료시 -1
            int bytesRead = channel.read(buffer);

            if (bytesRead > 0) {
                // flip(): 버퍼를 읽기 모드로 전환
                // position을 0으로, limit을 현재 position으로 설정
                buffer.flip();

                logger.debug("{}에서 {} 바이트 읽음", channel.getRemoteAddress(), bytesRead);

                // 핸들러에게 읽은 데이터 전달
                handler.onRead(this, channel, buffer);

            } else if (bytesRead == -1) {
                // 클라이언트가 연결을 정상적으로 종료
                logger.debug("클라이언트 연결 종료: {}", channel.getRemoteAddress());

                // 연결 해제 이벤트 처리
                handler.onDisconnect(this, channel);

                // SelectionKey와 채널 정리
                closeKey(key);
            }
        } catch (Exception e) {
            logger.error("채널 읽기 중 오류 발생: {} - 오류: {}",
                    channel.getRemoteAddress(), e.getMessage(), e);

            // 구체적인 에러 정보 출력
            if (e instanceof java.nio.channels.ClosedChannelException) {
                logger.debug("채널이 이미 닫혀있음");
            } else if (e instanceof java.io.IOException) {
                logger.debug("읽기 작업 중 I/O 오류");
            } else {
                logger.error("예상치 못한 오류 타입: {}", e.getClass().getName());
            }

            // 에러 발생한 연결 정리
            try {
                handler.onDisconnect(this, channel);
            } catch (Exception ex) {
                logger.error("연결 해제 처리 중 오류", ex);
            }
            closeKey(key);
        }
    }

    /**
     * 데이터 쓰기
     *
     * 클라이언트로 응답 데이터를 전송
     *
     * @param key WRITE 이벤트가 발생한 SelectionKey
     * @throws IOException I/O 오류 발생시
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientSocketEventHandler handler = (ClientSocketEventHandler) key.attachment();

        // 핸들러에게 쓰기 작업 위임
        handler.onWrite(this, channel);

        // 쓰기 완료 후 WRITE interest 제거
        // interestOps(): 현재 관심 있는 연산들 반환
        // & ~SelectionKey.OP_WRITE: 비트 연산으로 WRITE 비트 제거
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
    }

    /**
     * 큐에 쌓인 작업들 처리
     *
     * 다른 스레드에서 execute()로 추가한 작업들을 순차적으로 실행
     * 한 번에 너무 많은 작업을 처리하지 않도록 제한
     */
    private void processTasks() {
        int processedTasks = 0;
        Runnable task;

        // 한 번에 너무 많은 작업을 처리하지 않도록 제한
        // I/O 이벤트 처리가 지연되지 않도록 균형 유지
        while (processedTasks < 10000 && (task = taskQueue.poll()) != null) {
            try {
                // 작업 실행
                task.run();
                processedTasks++;
                totalTasksExecuted++;
            } catch (Exception e) {
                logger.error("작업 실행 중 오류", e);
            }
        }

        if (processedTasks > 0) {
            logger.debug("{} 개 작업 처리됨", processedTasks);
        }
    }

    /**
     * 통계 정보 업데이트
     *
     * 이벤트루프의 성능 모니터링을 위한 통계 수집
     *
     * @param loopStart 루프 시작 시간 (나노초)
     */
    private void updateStatistics(long loopStart) {
        // 총 루프 실행 횟수 증가
        totalLoops++;

        // System.nanoTime(): 현재 시간 (나노초)
        // 마지막 루프 실행 시간 계산 (나노초 단위)
        lastLoopTime = System.nanoTime() - loopStart;

        // 주기적으로 통계 로그 출력
        // % 연산자: 나머지 연산 (10000으로 나눈 나머지가 0이면)
        if (totalLoops % 10000 == 0) {
            // 마이크로초 변환: 나노초 / 1000
            logger.debug("이벤트루프 통계 - 루프: {}, 작업: {}, 마지막 루프: {}μs",
                    totalLoops, totalTasksExecuted, lastLoopTime / 1000);
        }
    }

    /**
     * SelectionKey 안전 종료
     *
     * SelectionKey와 연결된 채널을 안전하게 정리
     *
     * @param key 정리할 SelectionKey
     */
    private void closeKey(SelectionKey key) {
        try {
            // key.cancel(): SelectionKey를 취소하여 다음 select()에서 제외
            // Selector에서 이 키를 더 이상 모니터링하지 않음
            key.cancel();

            // key.channel(): 키에 연결된 채널 반환
            Channel channel = key.channel();

            // isOpen(): 채널이 열려있는지 확인
            if (channel.isOpen()) {
                // close(): 채널을 닫고 관련 시스템 리소스 해제
                channel.close();
            }
        } catch (IOException e) {
            logger.error("키/채널 닫기 중 오류", e);
        }
    }

    /**
     * 채널 안전 종료
     *
     * 채널만 단독으로 정리할 때 사용
     *
     * @param channel 정리할 채널
     */
    private void closeChannel(Channel channel) {
        try {
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            logger.error("채널 닫기 중 오류", e);
        }
    }

    /**
     * 쓰기 준비 상태로 변경
     *
     * 클라이언트로 데이터를 보낼 준비가 되었을 때 호출
     * SelectionKey에 OP_WRITE interest를 추가하여 쓰기 가능 이벤트 모니터링
     *
     * @param channel 쓰기 모드로 설정할 SocketChannel
     */
    public void enableWrite(SocketChannel channel) {
        // 이벤트루프 스레드에서 실행되도록 스케줄링
        execute(() -> {
            // keyFor(): 특정 Selector에 대한 이 채널의 SelectionKey 반환
            SelectionKey key = channel.keyFor(selector);

            // 키가 존재하고 유효한지 확인
            if (key != null && key.isValid()) {
                // | 연산자: 비트 OR 연산으로 OP_WRITE 추가
                // 기존 관심사(OP_READ 등)는 유지하면서 OP_WRITE 추가
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            }
        });
    }

    // === 성능 통계 접근자 메서드들 ===

    /**
     * 총 루프 실행 횟수 반환
     *
     * @return 이벤트루프가 실행된 총 횟수
     */
    public long getTotalLoops() {
        return totalLoops;
    }

    /**
     * 총 실행된 작업 수 반환
     *
     * @return execute()로 추가되어 실행된 총 작업 수
     */
    public long getTotalTasksExecuted() {
        return totalTasksExecuted;
    }

    /**
     * 마지막 루프 실행 시간 반환 (나노초)
     *
     * @return 가장 최근 루프의 실행 시간 (나노초)
     */
    public long getLastLoopTimeNanos() {
        return lastLoopTime;
    }

    /**
     * 큐에 대기 중인 작업 수 반환
     *
     * @return 아직 실행되지 않고 큐에 대기 중인 작업 수
     */
    public int getQueuedTaskCount() {
        // size(): ConcurrentLinkedQueue의 현재 크기
        // 주의: 동시성 환경에서는 정확하지 않을 수 있음 (근사값)
        return taskQueue.size();
    }

    /**
     * 이벤트루프 실행 상태 반환
     *
     * @return 이벤트루프가 실행 중이면 true, 종료 상태면 false
     */
    public boolean isRunning() {
        // get(): AtomicBoolean의 현재 값 반환
        return running.get();
    }

    /**
     * 이벤트루프 상태의 문자열 표현
     *
     * 디버깅과 모니터링에 유용한 현재 상태 정보
     *
     * @return 이벤트루프의 주요 상태 정보가 담긴 문자열
     */
    @Override
    public String toString() {
        // String.format(): printf 스타일의 문자열 포맷팅
        // %s: 문자열, %d: 정수 값 출력
        return String.format("EventLoop{running=%s, loops=%d, tasks=%d, queued=%d}",
                running.get(),          // 실행 상태
                totalLoops,             // 총 루프 수
                totalTasksExecuted,     // 총 실행된 작업 수
                taskQueue.size());      // 대기 중인 작업 수
    }
}