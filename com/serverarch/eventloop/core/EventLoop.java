package com.serverarch.eventloop.core; // 패키지 선언 - 이벤트 루프 서버 아키텍처 패키지

// === Java NIO 라이브러리 Import ===
// java.nio.*: New I/O API - 논블로킹 I/O 처리를 위한 핵심 라이브러리
import java.nio.channels.*; // SelectableChannel, ServerSocketChannel, SocketChannel 등 채널 클래스들
// Selector: 멀티플렉싱 I/O를 위한 핵심 클래스, 여러 채널을 하나의 스레드에서 관리
// SelectionKey: Selector에 등록된 채널의 관심사(interest)와 상태를 나타내는 키
// ServerSocketChannel: 서버 소켓의 논블로킹 채널 구현체
// SocketChannel: 클라이언트 소켓의 논블로킹 채널 구현체
// SelectableChannel: 셀렉터와 함께 사용할 수 있는 채널의 추상 클래스

// === Java 표준 라이브러리 Import ===
import java.io.*; // IOException 등 예외 클래스
import java.util.*; // Queue, Map, Set 등 컬렉션 클래스들
import java.util.concurrent.*; // 동시성 처리를 위한 클래스들
import java.util.concurrent.atomic.*; // 원자적 연산을 위한 클래스들
import java.util.function.Supplier; //java.util.function.Supplier는 Java 8에서 도입된 함수형 인터페이스
import java.util.logging.*; // 로깅을 위한 클래스들


/**
 * EventLoop - 단일 스레드 이벤트 루프 구현체
 * 
 * 이 클래스는 Node.js나 Netty와 같은 이벤트 기반 서버의 핵심 컴포넌트입니다.
 * 
 * 핵심 개념:
 * 1. 단일 스레드: 모든 I/O 이벤트를 하나의 스레드에서 처리
 * 2. 논블로킹 I/O: NIO Selector를 사용하여 채널들을 멀티플렉싱
 * 3. 이벤트 기반: I/O 준비 완료 시점에 콜백 실행
 * 4. 비동기 처리: CompletableFuture를 사용한 비동기 작업 체이닝
 * 
 * 이벤트 루프 사이클:
 * 1. 등록된 채널들에서 I/O 이벤트 대기 (select)
 * 2. 준비된 이벤트들을 순차적으로 처리
 * 3. 대기 중인 태스크들 실행
 * 4. 다음 사이클로 반복
 * 
 * 장점:
 * - 높은 동시성: 수만 개의 연결을 단일 스레드로 처리 가능
 * - 낮은 메모리 사용량: 스레드당 스택 메모리 없음
 * - 컨텍스트 스위칭 오버헤드 없음
 * - CPU 효율성: 논블로킹으로 CPU 낭비 최소화
 * 
 * 단점:
 * - 구현 복잡도: 모든 작업이 비동기로 처리되어야 함
 * - CPU 집약적 작업 부적합: 이벤트 루프 블로킹 위험
 * - 디버깅 어려움: 비동기 스택 추적의 복잡성
 */
public class EventLoop { // public 클래스 선언 - 외부에서 사용 가능한 이벤트 루프 구현체

    // === 로깅 시스템 ===
    // static final: 클래스 레벨 상수로 선언하여 모든 인스턴스가 공유
    // Logger.getLogger(): 클래스 이름을 기반으로 로거 생성, 이벤트 루프의 동작을 추적하기 위함
    private static final Logger logger = Logger.getLogger(EventLoop.class.getName());

    // === NIO 셀렉터 - 이벤트 루프의 핵심 ===
    // Selector: 여러 채널을 하나의 스레드에서 모니터링하는 멀티플렉서
    // 논블로킹 I/O의 핵심으로, 준비된 채널들만 선택적으로 처리
    private final Selector selector; // final: 생성 후 변경 불가, 이벤트 루프의 생명주기와 동일

    // === 이벤트 루프 실행 상태 ===
    // AtomicBoolean: 멀티스레드 환경에서 안전한 boolean 연산을 보장
    // 이벤트 루프의 시작/정지를 원자적으로 제어
    private final AtomicBoolean running = new AtomicBoolean(false); // 초기값 false (정지 상태)

    // === 이벤트 루프 스레드 ===
    // volatile: 메모리 가시성 보장, 다른 스레드에서 변경사항을 즉시 확인 가능
    // 이벤트 루프는 전용 스레드에서 실행되므로 스레드 참조를 보관
    private volatile Thread eventLoopThread; // 이벤트 루프를 실행하는 전용 스레드

    // === 태스크 큐 - 이벤트 루프에서 실행할 작업들 ===
    // ConcurrentLinkedQueue: 논블로킹 concurrent 큐, 락 없이 안전한 멀티스레드 접근
    // 이벤트 루프 외부에서 작업을 스케줄링할 때 사용
    private final Queue<Runnable> taskQueue = new ConcurrentLinkedQueue<>(); // 대기 중인 작업들의 큐

    // === 스케줄된 태스크 관리 ===
    // PriorityQueue: 우선순위 큐로 실행 시간순으로 태스크 정렬
    // 지연된 태스크나 주기적 태스크를 시간 순서대로 실행하기 위함
    private final Queue<ScheduledTask> scheduledTasks = new PriorityQueue<>(); // 스케줄된 작업들의 우선순위 큐

    // === 채널 관리 ===
    // ConcurrentHashMap: 동시성 안전한 해시맵, 채널과 핸들러의 매핑 관리
    // SelectionKey를 키로 하여 각 채널에 대한 이벤트 핸들러를 저장
    private final Map<SelectionKey, ChannelHandler> channelHandlers = new ConcurrentHashMap<>(); // 채널별 핸들러 매핑

    // === 성능 메트릭 수집 ===
    // 이벤트 루프의 성능을 모니터링하기 위한 카운터들
    private final AtomicLong totalIterations = new AtomicLong(0); // 총 이벤트 루프 반복 횟수
    private final AtomicLong totalEvents = new AtomicLong(0); // 총 처리된 I/O 이벤트 수
    private final AtomicLong totalTasks = new AtomicLong(0); // 총 실행된 태스크 수
    private final AtomicLong selectTime = new AtomicLong(0); // selector.select()에 소요된 총 시간

    // === 이벤트 루프 설정 상수 ===
    // static final: 컴파일 타임 상수, 이벤트 루프의 동작 파라미터들
    private static final long SELECT_TIMEOUT_MS = 1000; // Selector.select() 타임아웃 (1초)
    private static final int MAX_TASKS_PER_ITERATION = 1000; // 한 반복당 최대 처리 태스크 수 (무한 루프 방지)
    private static final long METRICS_LOG_INTERVAL_MS = 30000; // 메트릭 로그 출력 간격 (30초)

    // === 메트릭 로깅 관련 ===
    private volatile long lastMetricsLogTime = 0; // 마지막 메트릭 로그 출력 시간

    /**
     * EventLoop 생성자
     * 
     * NIO Selector를 생성하고 이벤트 루프를 초기화합니다.
     * 
     * @throws IOException Selector 생성 실패 시
     */
    public EventLoop() throws IOException { // public 생성자, IOException: Selector 생성 실패 예외
        // Selector 생성 - 이벤트 루프의 핵심 컴포넌트
        // Selector.open(): 시스템의 기본 SelectorProvider를 사용하여 새 Selector 생성
        // 내부적으로 epoll(Linux), kqueue(macOS), select(Windows) 등 플랫폼별 멀티플렉싱 메커니즘 사용
        this.selector = Selector.open(); // 플랫폼에 최적화된 I/O 멀티플렉서 생성

        // 초기화 완료 로그
        logger.info("EventLoop 초기화 완료 - Selector: " + selector.getClass().getSimpleName()); // Selector 구현체 클래스명 로그
    }

    /**
     * 이벤트 루프 시작
     * 
     * 전용 스레드를 생성하여 이벤트 루프를 실행합니다.
     * 이 메서드는 즉시 반환되며, 실제 이벤트 루프는 백그라운드에서 실행됩니다.
     * 
     * @throws IllegalStateException 이미 실행 중인 경우
     */
    public void start() { // public 메서드, 외부에서 이벤트 루프 시작 가능
        // 중복 시작 방지 - 원자적 상태 변경으로 동시성 제어
        // compareAndSet(expected, update): 현재 값이 expected와 같으면 update로 변경하고 true 반환
        if (!running.compareAndSet(false, true)) { // false에서 true로 변경 시도
            throw new IllegalStateException("EventLoop가 이미 실행 중입니다"); // 이미 실행 중인 경우 예외 발생
        }

        // 이벤트 루프 전용 스레드 생성 및 시작
        // new Thread(): 새 스레드 생성, 람다 표현식으로 실행할 작업 정의
        eventLoopThread = new Thread(this::runEventLoop, "EventLoop-Thread"); // 스레드 이름 지정으로 디버깅 용이성 향상
        eventLoopThread.setDaemon(false); // 일반 스레드로 설정 (JVM이 이 스레드 완료를 기다림)
        eventLoopThread.start(); // 스레드 시작 - runEventLoop() 메서드가 별도 스레드에서 실행됨

        logger.info("EventLoop 시작됨 - 스레드: " + eventLoopThread.getName()); // 시작 로그
    }

    /**
     * 이벤트 루프 중지
     * 
     * 실행 중인 이벤트 루프를 안전하게 중지하고 리소스를 정리합니다.
     * 현재 처리 중인 이벤트들은 완료된 후 중지됩니다.
     */
    public void stop() { // public 메서드, 외부에서 이벤트 루프 중지 가능
        // 이미 중지된 경우 무시
        if (!running.compareAndSet(true, false)) { // true에서 false로 변경 시도
            logger.info("EventLoop가 이미 중지되었거나 실행 중이 아닙니다"); // 이미 중지된 상태 로그
            return; // early return으로 중복 처리 방지
        }

        logger.info("EventLoop 중지 요청됨"); // 중지 요청 로그

        // Selector 깨우기 - select() 메서드에서 블로킹 중일 수 있으므로 강제로 깨움
        // wakeup(): 현재 select() 호출을 즉시 반환시켜 중지 요청을 빠르게 처리
        selector.wakeup(); // 블로킹된 select() 호출 해제

        // 이벤트 루프 스레드 종료 대기
        if (eventLoopThread != null && eventLoopThread.isAlive()) { // 스레드가 존재하고 살아있는 경우
            try {
                // join(): 현재 스레드가 eventLoopThread의 종료를 기다림
                // 5000ms 타임아웃으로 무한 대기 방지
                eventLoopThread.join(5000); // 최대 5초 대기
                
                if (eventLoopThread.isAlive()) { // 타임아웃 후에도 살아있는 경우
                    logger.warning("EventLoop 스레드가 5초 내에 종료되지 않았습니다"); // 강제 종료 경고
                    eventLoopThread.interrupt(); // 스레드 인터럽트로 강제 종료 시도
                }
            } catch (InterruptedException e) { // join() 대기 중 인터럽트 발생
                Thread.currentThread().interrupt(); // 현재 스레드의 인터럽트 상태 복원
                logger.warning("EventLoop 중지 대기 중 인터럽트됨"); // 인터럽트 로그
            }
        }

        // 리소스 정리
        cleanup(); // 최종 리소스 정리 메서드 호출

        logger.info("EventLoop가 성공적으로 중지되었습니다"); // 중지 완료 로그
    }

    /**
     * 리소스 정리
     * 
     * Selector와 관련된 모든 리소스를 정리합니다.
     * 채널들을 닫고 Selector를 닫아 메모리 누수를 방지합니다.
     */
    private void cleanup() { // private 메서드, 클래스 내부에서만 사용
        try {
            // 등록된 모든 채널 닫기
            // selector.keys(): Selector에 등록된 모든 SelectionKey 반환
            for (SelectionKey key : selector.keys()) { // 모든 등록된 키 순회
                try {
                    if (key.channel() != null) { // 채널이 null이 아닌 경우
                        key.channel().close(); // 채널 닫기
                    }
                } catch (IOException e) { // 개별 채널 닫기 실패
                    logger.log(Level.WARNING, "채널 닫기 실패", e); // 경고 레벨로 로그
                }
            }

            // Selector 닫기
            if (selector != null && selector.isOpen()) { // Selector가 존재하고 열려있는 경우
                selector.close(); // Selector 닫기 - 모든 등록된 키들도 자동으로 취소됨
            }

            // 핸들러 맵 정리
            channelHandlers.clear(); // 모든 핸들러 매핑 제거

            // 대기 중인 태스크들 정리
            taskQueue.clear(); // 일반 태스크 큐 비우기
            scheduledTasks.clear(); // 스케줄된 태스크 큐 비우기

            logger.fine("EventLoop 리소스 정리 완료"); // 상세 로그

        } catch (IOException e) { // Selector 닫기 실패
            logger.log(Level.SEVERE, "EventLoop 리소스 정리 중 오류", e); // 심각한 오류 레벨로 로그
        }
    }

    /**
     * 메인 이벤트 루프 실행
     * 
     * 이 메서드는 별도 스레드에서 실행되며, 다음 과정을 반복합니다:
     * 1. I/O 이벤트 대기 (select)
     * 2. 준비된 이벤트들 처리
     * 3. 스케줄된 태스크들 실행
     * 4. 일반 태스크들 실행
     */
    private void runEventLoop() { // private 메서드, 전용 스레드에서만 실행
        logger.info("EventLoop 메인 루프 시작"); // 메인 루프 시작 로그

        // 이벤트 루프가 실행 중인 동안 계속 반복
        while (running.get()) { // AtomicBoolean.get(): 현재 실행 상태 확인
            try {
                // 한 반복 시작 시간 기록 - 성능 측정용
                long iterationStart = System.currentTimeMillis(); // 현재 시간(밀리초) 기록

                // 1. I/O 이벤트 대기 및 처리
                processIOEvents(); // 준비된 I/O 이벤트들을 처리

                // 2. 스케줄된 태스크들 실행
                processScheduledTasks(); // 실행 시간이 된 태스크들 처리

                // 3. 일반 태스크들 실행
                processTasks(); // 태스크 큐에 대기 중인 작업들 처리

                // 반복 완료 통계 업데이트
                totalIterations.incrementAndGet(); // 총 반복 횟수 증가

                // 주기적 메트릭 로깅
                long now = System.currentTimeMillis(); // 현재 시간
                if (now - lastMetricsLogTime > METRICS_LOG_INTERVAL_MS) { // 마지막 로그 출력 후 30초 이상 경과
                    logMetrics(); // 성능 메트릭 로그 출력
                    lastMetricsLogTime = now; // 마지막 로그 시간 업데이트
                }

            } catch (IOException e) { // I/O 예외 발생
                if (running.get()) { // 아직 실행 중인 경우 (정상 종료 과정이 아님)
                    logger.log(Level.SEVERE, "EventLoop에서 I/O 오류 발생", e); // 심각한 오류로 로그
                } else { // 종료 과정에서 발생한 예외
                    logger.fine("EventLoop 종료 중 I/O 예외 (정상): " + e.getMessage()); // 정상적인 종료 과정
                }
            } catch (Exception e) { // 기타 모든 예외
                logger.log(Level.SEVERE, "EventLoop에서 예상치 못한 오류 발생", e); // 예상치 못한 오류
                // 심각한 오류 발생 시에도 루프 계속 실행 (서버 안정성 확보)
            }
        }

        logger.info("EventLoop 메인 루프 종료"); // 메인 루프 종료 로그
    }

    /**
     * I/O 이벤트 처리
     * 
     * Selector를 사용하여 준비된 채널들의 I/O 이벤트를 처리합니다.
     * 
     * @throws IOException Selector 관련 I/O 오류
     */
    private void processIOEvents() throws IOException { // private 메서드, I/O 이벤트 처리 전용
        // Selector.select() 호출 시간 측정 시작
        long selectStart = System.currentTimeMillis(); // select() 호출 전 시간 기록

        // I/O 이벤트 대기 - 이벤트 루프의 핵심 부분
        // select(timeout): 준비된 채널이 있거나 타임아웃까지 블로킹
        // 타임아웃을 사용하는 이유: 태스크 큐 처리와 우아한 종료를 위해
        int readyChannels = selector.select(SELECT_TIMEOUT_MS); // 1초 타임아웃으로 이벤트 대기

        // select() 소요 시간 기록
        selectTime.addAndGet(System.currentTimeMillis() - selectStart); // 누적 select 시간 업데이트

        if (readyChannels == 0) { // 준비된 채널이 없는 경우 (타임아웃 또는 wakeup 호출)
            return; // 다음 루프 반복으로 이동
        }

        // 준비된 채널들의 SelectionKey 집합 가져오기
        // selectedKeys(): 현재 I/O 작업이 준비된 채널들의 키 집합
        Set<SelectionKey> selectedKeys = selector.selectedKeys(); // 준비된 키들의 Set

        // 준비된 각 키를 순차적으로 처리
        // iterator(): Set의 반복자, 순회하면서 처리된 키는 제거해야 함
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator(); // 키 집합의 반복자 생성

        while (keyIterator.hasNext()) { // 모든 준비된 키 순회
            SelectionKey key = keyIterator.next(); // 다음 키 가져오기
            keyIterator.remove(); // 처리된 키를 selectedKeys에서 제거 (중요: 제거하지 않으면 중복 처리됨)

            if (!key.isValid()) { // 키가 유효하지 않은 경우 (채널이 닫혔거나 등록 해제됨)
                continue; // 다음 키로 건너뜀
            }

            try {
                // 키에 대응하는 채널 핸들러 가져오기
                ChannelHandler handler = channelHandlers.get(key); // 키에 매핑된 핸들러 조회
                if (handler != null) { // 핸들러가 존재하는 경우
                    // 핸들러를 통해 이벤트 처리
                    handler.handleEvent(key); // 구체적인 I/O 이벤트 처리를 핸들러에게 위임
                    totalEvents.incrementAndGet(); // 처리된 이벤트 수 증가
                } else { // 핸들러가 없는 경우 (등록 오류 또는 정리 중)
                    logger.warning("SelectionKey에 대한 핸들러가 없습니다: " + key); // 경고 로그
                    key.cancel(); // 키 등록 해제
                }
            } catch (Exception e) { // 개별 이벤트 처리 중 예외 발생
                logger.log(Level.WARNING, "이벤트 처리 중 오류: " + key, e); // 경고 레벨로 로그
                // 해당 키 정리
                try {
                    key.cancel(); // 키 등록 해제
                    if (key.channel() != null) { // 채널이 존재하는 경우
                        key.channel().close(); // 채널 닫기
                    }
                } catch (IOException closeException) { // 채널 닫기 실패
                    logger.log(Level.WARNING, "채널 닫기 실패", closeException); // 추가 오류 로그
                }
                channelHandlers.remove(key); // 핸들러 매핑 제거
            }
        }
    }

    /**
     * 스케줄된 태스크들 처리
     * 
     * 실행 시간이 된 스케줄된 태스크들을 찾아서 실행합니다.
     * 우선순위 큐를 사용하여 시간 순서대로 처리합니다.
     */
    private void processScheduledTasks() { // private 메서드, 스케줄된 태스크 처리 전용
        long currentTime = System.currentTimeMillis(); // 현재 시간 기록

        // 실행 시간이 된 태스크들을 모두 처리
        // peek(): 큐의 첫 번째 요소를 제거하지 않고 조회
        while (!scheduledTasks.isEmpty() && scheduledTasks.peek().getExecutionTime() <= currentTime) { // 실행 시간이 된 태스크가 있는 동안
            ScheduledTask task = scheduledTasks.poll(); // poll(): 큐에서 첫 번째 요소 제거 및 반환
            if (task != null) { // 태스크가 존재하는 경우
                try {
                    task.run(); // 태스크 실행
                    totalTasks.incrementAndGet(); // 실행된 태스크 수 증가
                } catch (Exception e) { // 태스크 실행 중 예외 발생
                    logger.log(Level.WARNING, "스케줄된 태스크 실행 중 오류", e); // 경고 레벨로 로그
                }
            }
        }
    }

    /**
     * 일반 태스크들 처리
     * 
     * 태스크 큐에 대기 중인 작업들을 순차적으로 실행합니다.
     * 한 번에 처리할 태스크 수를 제한하여 이벤트 루프 블로킹을 방지합니다.
     */
    private void processTasks() { // private 메서드, 일반 태스크 처리 전용
        int processedTasks = 0; // 현재 반복에서 처리된 태스크 수

        // 태스크 큐에서 작업을 하나씩 가져와서 처리
        // 최대 처리 개수 제한으로 이벤트 루프가 무한히 블로킹되는 것을 방지
        while (processedTasks < MAX_TASKS_PER_ITERATION && !taskQueue.isEmpty()) { // 최대 개수 미만이고 큐가 비어있지 않은 동안
            Runnable task = taskQueue.poll(); // poll(): 큐에서 첫 번째 태스크 제거 및 반환
            if (task != null) { // 태스크가 존재하는 경우
                try {
                    task.run(); // 태스크 실행
                    totalTasks.incrementAndGet(); // 실행된 태스크 수 증가
                    processedTasks++; // 현재 반복에서 처리된 태스크 수 증가
                } catch (Exception e) { // 태스크 실행 중 예외 발생
                    logger.log(Level.WARNING, "태스크 실행 중 오류", e); // 경고 레벨로 로그
                }
            }
        }

        // 처리하지 못한 태스크가 남아있는 경우 로그
        if (!taskQueue.isEmpty()) { // 큐에 태스크가 남아있는 경우
            logger.fine(String.format("처리하지 못한 태스크 %d개 남음 (이번 반복에서 %d개 처리)", 
                    taskQueue.size(), processedTasks)); // 상세 로그 레벨로 기록
        }
    }

    /**
     * 성능 메트릭 로깅
     * 
     * 이벤트 루프의 성능 지표들을 주기적으로 로그에 출력합니다.
     */
    private void logMetrics() { // private 메서드, 메트릭 로깅 전용
        long iterations = totalIterations.get(); // 총 반복 횟수 조회
        long events = totalEvents.get(); // 총 이벤트 수 조회
        long tasks = totalTasks.get(); // 총 태스크 수 조회
        long avgSelectTime = iterations > 0 ? selectTime.get() / iterations : 0; // 평균 select 시간 계산

        // 성능 메트릭 로그 출력
        logger.info(String.format(
                "EventLoop 메트릭 - 반복: %d, 이벤트: %d, 태스크: %d, 평균Select시간: %dms, " +
                "대기태스크: %d, 스케줄태스크: %d, 등록채널: %d", 
                iterations, events, tasks, avgSelectTime,
                taskQueue.size(), scheduledTasks.size(), channelHandlers.size()
        ));
    }

    // ========== 공개 API 메서드들 ==========

    /**
     * 채널을 이벤트 루프에 등록
     * 
     * 주어진 채널을 Selector에 등록하고 핸들러를 매핑합니다.
     * 
     * @param channel 등록할 채널
     * @param ops 관심사 (SelectionKey.OP_READ, OP_WRITE, OP_ACCEPT 등)
     * @param handler 채널 이벤트를 처리할 핸들러
     * @return 등록된 SelectionKey
     * @throws IOException 채널 등록 실패
     */
    public SelectionKey registerChannel(SelectableChannel channel, int ops, ChannelHandler handler) throws IOException { // public 메서드, 외부에서 채널 등록 가능
        // 매개변수 유효성 검증
        if (channel == null) { // 채널이 null인 경우
            throw new IllegalArgumentException("채널이 null입니다"); // 잘못된 인수 예외
        }
        if (handler == null) { // 핸들러가 null인 경우
            throw new IllegalArgumentException("핸들러가 null입니다"); // 잘못된 인수 예외
        }

        // 이벤트 루프 스레드에서 실행되어야 하는 작업인지 확인
        if (Thread.currentThread() == eventLoopThread) { // 현재 스레드가 이벤트 루프 스레드인 경우
            // 직접 등록 수행
            return doRegisterChannel(channel, ops, handler); // 동기적으로 채널 등록
        } else { // 다른 스레드에서 호출한 경우
            // 이벤트 루프 스레드에서 실행되도록 태스크 스케줄링
            CompletableFuture<SelectionKey> future = new CompletableFuture<>(); // 비동기 결과를 담을 Future
            execute(() -> { // execute(): 태스크를 이벤트 루프에 스케줄링
                try {
                    SelectionKey key = doRegisterChannel(channel, ops, handler); // 채널 등록 수행
                    future.complete(key); // 성공 시 결과 설정
                } catch (Exception e) { // 등록 실패 시
                    future.completeExceptionally(e); // 예외 설정
                }
            });

            try {
                return future.get(5, TimeUnit.SECONDS); // 최대 5초 대기 후 결과 반환
            } catch (Exception e) { // 대기 중 예외 발생
                throw new IOException("채널 등록 실패", e); // IOException으로 래핑
            }
        }
    }

    /**
     * 실제 채널 등록 수행
     * 
     * 이 메서드는 반드시 이벤트 루프 스레드에서 호출되어야 합니다.
     * 
     * @param channel 등록할 채널
     * @param ops 관심사
     * @param handler 이벤트 핸들러
     * @return 등록된 SelectionKey
     * @throws IOException 등록 실패
     */
    private SelectionKey doRegisterChannel(SelectableChannel channel, int ops, ChannelHandler handler) throws IOException { // private 메서드, 실제 등록 로직
        // 채널을 논블로킹 모드로 설정
        // configureBlocking(false): 채널을 논블로킹 모드로 변경 (Selector 사용의 필수 조건)
        channel.configureBlocking(false); // 논블로킹 모드 설정

        // Selector에 채널 등록
        // register(selector, ops): 채널을 Selector에 등록하고 관심사 설정
        SelectionKey key = channel.register(selector, ops); // 채널 등록 및 SelectionKey 반환

        // 핸들러 매핑 저장
        channelHandlers.put(key, handler); // 키와 핸들러 매핑 저장

        logger.fine(String.format("채널 등록됨 - 타입: %s, 관심사: %d", 
                channel.getClass().getSimpleName(), ops)); // 등록 완료 로그

        return key; // 등록된 키 반환
    }

    /**
     * 태스크를 이벤트 루프에서 실행하도록 스케줄링
     * 
     * 이 메서드는 스레드 안전하며, 어떤 스레드에서든 호출할 수 있습니다.
     * 태스크는 다음 이벤트 루프 반복에서 실행됩니다.
     * 
     * @param task 실행할 태스크
     */
    public void execute(Runnable task) { // public 메서드, 외부에서 태스크 스케줄링 가능
        if (task == null) { // 태스크가 null인 경우
            throw new IllegalArgumentException("태스크가 null입니다"); // 잘못된 인수 예외
        }

        // 태스크를 큐에 추가
        // offer(): 큐에 요소 추가, ConcurrentLinkedQueue는 무제한이므로 항상 성공
        taskQueue.offer(task); // 논블로킹으로 태스크 큐에 추가

        // Selector 깨우기 - select() 메서드가 블로킹 중일 수 있으므로
        // wakeup(): 현재 select() 호출을 즉시 반환시켜 태스크를 빠르게 처리
        selector.wakeup(); // 블로킹된 select() 해제로 즉시 태스크 처리 가능
    }

    /**
     * 지연된 태스크를 스케줄링
     * 
     * 지정된 지연 시간 후에 태스크를 실행하도록 스케줄링합니다.
     * 
     * @param task 실행할 태스크
     * @param delay 지연 시간
     * @param unit 시간 단위
     */
    public void schedule(Runnable task, long delay, TimeUnit unit) { // public 메서드, 지연된 태스크 스케줄링 가능
        if (task == null) { // 태스크가 null인 경우
            throw new IllegalArgumentException("태스크가 null입니다"); // 잘못된 인수 예외
        }
        if (delay < 0) { // 지연 시간이 음수인 경우
            throw new IllegalArgumentException("지연 시간은 음수일 수 없습니다"); // 잘못된 인수 예외
        }

        // 실행 시간 계산
        // unit.toMillis(delay): 주어진 시간 단위를 밀리초로 변환
        long executionTime = System.currentTimeMillis() + unit.toMillis(delay); // 현재 시간 + 지연 시간

        // 스케줄된 태스크 생성 및 큐에 추가
        ScheduledTask scheduledTask = new ScheduledTask(task, executionTime); // 실행 시간을 포함한 태스크 래퍼 생성
        
        // 스케줄된 태스크는 이벤트 루프에서 관리해야 하므로 동기화 필요
        execute(() -> { // 이벤트 루프에서 스케줄 등록 작업 실행
            scheduledTasks.offer(scheduledTask); // 우선순위 큐에 추가 (실행 시간 순으로 정렬됨)
        });
    }

    /**
     * CompletableFuture를 이벤트 루프에서 실행
     * 
     * 주어진 Supplier를 이벤트 루프에서 실행하고 결과를 CompletableFuture로 반환합니다.
     * 
     * @param supplier 실행할 작업
     * @return 작업 결과를 담은 CompletableFuture
     */
    public <T> CompletableFuture<T> submit(Supplier<T> supplier) { // public 메서드, 제네릭으로 타입 안전성 보장
        if (supplier == null) { // Supplier가 null인 경우
            throw new IllegalArgumentException("Supplier가 null입니다"); // 잘못된 인수 예외
        }

        // 결과를 담을 CompletableFuture 생성
        CompletableFuture<T> future = new CompletableFuture<>(); // 비동기 결과 컨테이너

        // 이벤트 루프에서 실행할 태스크 생성
        execute(() -> { // 태스크 스케줄링
            try {
                T result = supplier.get(); // Supplier 실행 및 결과 얻기
                future.complete(result); // 성공 시 결과 설정
            } catch (Exception e) { // 실행 중 예외 발생
                future.completeExceptionally(e); // 예외 설정
            }
        });

        return future; // 비동기 결과 반환
    }

    // ========== 상태 조회 메서드들 ==========

    /**
     * 이벤트 루프 실행 상태 확인
     * 
     * @return 실행 중이면 true
     */
    public boolean isRunning() { // public getter, 외부에서 실행 상태 확인 가능
        return running.get(); // AtomicBoolean의 현재 값을 원자적으로 반환
    }

    /**
     * 현재 스레드가 이벤트 루프 스레드인지 확인
     * 
     * @return 이벤트 루프 스레드이면 true
     */
    public boolean inEventLoop() { // public 메서드, 현재 스레드 확인
        return Thread.currentThread() == eventLoopThread; // 현재 스레드와 이벤트 루프 스레드 비교
    }

    /**
     * 등록된 채널 수 반환
     * 
     * @return 현재 등록된 채널 수
     */
    public int getChannelCount() { // public getter, 등록된 채널 수 조회
        return channelHandlers.size(); // 핸들러 맵의 크기 반환
    }

    /**
     * 대기 중인 태스크 수 반환
     * 
     * @return 태스크 큐에 대기 중인 태스크 수
     */
    public int getPendingTaskCount() { // public getter, 대기 태스크 수 조회
        return taskQueue.size(); // 태스크 큐의 크기 반환
    }

    /**
     * 스케줄된 태스크 수 반환
     * 
     * @return 스케줄된 태스크 큐에 대기 중인 태스크 수
     */
    public int getScheduledTaskCount() { // public getter, 스케줄된 태스크 수 조회
        return scheduledTasks.size(); // 스케줄된 태스크 큐의 크기 반환
    }

    /**
     * 이벤트 루프 성능 메트릭 반환
     * 
     * @return 성능 메트릭 정보를 담은 Map
     */
    public Map<String, Object> getMetrics() { // public getter, 성능 메트릭 조회
        Map<String, Object> metrics = new HashMap<>(); // 메트릭 정보를 담을 Map 생성
        
        // 기본 통계 정보
        long iterations = totalIterations.get(); // 총 반복 횟수
        metrics.put("totalIterations", iterations); // 총 반복 횟수
        metrics.put("totalEvents", totalEvents.get()); // 총 이벤트 수
        metrics.put("totalTasks", totalTasks.get()); // 총 태스크 수
        metrics.put("channelCount", channelHandlers.size()); // 등록된 채널 수
        metrics.put("pendingTasks", taskQueue.size()); // 대기 태스크 수
        metrics.put("scheduledTasks", scheduledTasks.size()); // 스케줄된 태스크 수
        
        // 계산된 메트릭
        metrics.put("averageSelectTime", iterations > 0 ? selectTime.get() / iterations : 0); // 평균 select 시간
        metrics.put("eventsPerIteration", iterations > 0 ? (double) totalEvents.get() / iterations : 0.0); // 반복당 평균 이벤트 수
        metrics.put("tasksPerIteration", iterations > 0 ? (double) totalTasks.get() / iterations : 0.0); // 반복당 평균 태스크 수
        
        return metrics; // 메트릭 맵 반환
    }

    // ========== 내부 클래스들 ==========

    /**
     * 채널 이벤트 핸들러 인터페이스
     * 
     * 각 채널 타입별로 이 인터페이스를 구현하여 특화된 이벤트 처리를 제공합니다.
     */
    public interface ChannelHandler { // public interface, 외부에서 구현 가능
        /**
         * 채널 이벤트 처리
         * 
         * @param key 이벤트가 발생한 SelectionKey
         * @throws IOException I/O 처리 중 오류
         */
        void handleEvent(SelectionKey key) throws IOException; // 추상 메서드, 구현체에서 정의 필요
    }

    /**
     * 스케줄된 태스크 래퍼 클래스
     * 
     * 실행 시간 정보를 포함하여 우선순위 큐에서 시간 순으로 정렬됩니다.
     */
    private static class ScheduledTask implements Comparable<ScheduledTask>, Runnable { // private static, 실행 시간을 포함한 태스크 래퍼
        private final Runnable task; // 실제 실행할 태스크
        private final long executionTime; // 실행 예정 시간 (밀리초)

        /**
         * ScheduledTask 생성자
         * 
         * @param task 실행할 태스크
         * @param executionTime 실행 시간 (밀리초)
         */
        public ScheduledTask(Runnable task, long executionTime) { // 생성자
            this.task = task; // 태스크 설정
            this.executionTime = executionTime; // 실행 시간 설정
        }

        /**
         * 실행 시간 반환
         * 
         * @return 실행 예정 시간
         */
        public long getExecutionTime() { // getter, 실행 시간 조회
            return executionTime; // 실행 시간 반환
        }

        /**
         * 태스크 실행
         */
        @Override
        public void run() { // Runnable.run() 구현
            task.run(); // 실제 태스크 실행
        }

        /**
         * 실행 시간 기준 정렬을 위한 비교
         * 
         * @param other 비교할 다른 ScheduledTask
         * @return 비교 결과 (음수: 더 이른 시간, 0: 같은 시간, 양수: 더 늦은 시간)
         */
        @Override
        public int compareTo(ScheduledTask other) { // Comparable.compareTo() 구현
            return Long.compare(this.executionTime, other.executionTime); // 실행 시간 기준 비교
        }
    }
}
