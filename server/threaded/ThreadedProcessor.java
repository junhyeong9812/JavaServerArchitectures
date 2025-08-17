package server.threaded;

import server.core.routing.Router;
import java.net.Socket;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 스레드 기반 요청 처리기 (수정된 버전)
 *
 * 이 클래스는 클라이언트 연결을 스레드풀을 통해 처리하는 중간 계층입니다.
 * ServerSocket에서 수락된 연결을 받아 ThreadPoolManager에 작업으로 제출합니다.
 *
 * 주요 역할:
 * 1. 연결 수락과 실제 처리 사이의 브릿지 역할
 * 2. Router와 ServletContainer 통합 관리
 * 3. 연결별 통계 수집 및 모니터링
 * 4. 스레드풀 포화 시 예외 처리
 *
 * 아키텍처 특징:
 * - 각 연결을 개별 스레드에서 처리
 * - Router + ServletContainer 둘 다 지원 (하이브리드)
 * - 하위 호환성 유지 (기존 Router 전용 생성자)
 * - 실시간 통계 수집 및 제공
 *
 * 처리 흐름:
 * ServerSocket.accept() → ThreadedProcessor.processConnection()
 * → ThreadPoolManager.submit() → BlockingRequestHandler.run()
 */
public class ThreadedProcessor {

    /*
     * 스레드풀 관리자
     *
     * final 키워드 사용 이유:
     * - 객체 생성 후 참조 변경 방지
     * - 스레드 안전성 보장
     * - 의도치 않은 스레드풀 교체 방지
     *
     * ThreadPoolManager 역할:
     * - 톰캣 스타일 스레드 생성 전략 구현
     * - 작업 큐 관리 및 스레드 생명주기 제어
     * - 거부 정책 처리 및 통계 수집
     */
    private final ThreadPoolManager threadPoolManager;

    /*
     * HTTP 요청 라우터
     *
     * Router 역할:
     * - URL 패턴과 핸들러 매핑
     * - 미들웨어 체인 처리
     * - ServletContainer에서 처리하지 못한 요청의 대체 처리
     *
     * ServletContainer와의 관계:
     * - ServletContainer 우선 처리
     * - 실패 시 Router로 폴백 (fallback)
     * - 둘 다 실패 시 404 응답
     */
    private final Router router;

    /*
     * 서블릿 컨테이너 (추가됨)
     *
     * ThreadedMiniServletContainer:
     * - 서블릿 생명주기 관리 (init/service/destroy)
     * - URL 패턴 매칭 및 서블릿 라우팅
     * - 동기/비동기 서블릿 모두 지원
     *
     * null 가능성:
     * - 하위 호환성을 위해 null 허용
     * - null이면 Router만 사용하는 기존 동작
     * - null이 아니면 ServletContainer 우선 사용
     */
    private final ThreadedMiniServletContainer servletContainer;

    /*
     * 요청 핸들러 설정
     *
     * RequestHandlerConfig 포함 내용:
     * - 소켓 타임아웃, Keep-Alive 설정
     * - 버퍼 크기, 디버그 모드
     * - 연결당 최대 요청 수 제한
     *
     * BlockingRequestHandler에 전달되어 연결 처리 정책 결정
     */
    private final RequestHandlerConfig handlerConfig;

    // 통계 정보
    /*
     * 총 연결 수 카운터
     *
     * AtomicLong 사용 이유:
     * - 64비트 정수의 원자적 연산 지원
     * - 대용량 트래픽에서도 오버플로우 방지
     * - 멀티스레드 환경에서 정확한 카운팅
     *
     * 용도:
     * - 서버 시작 이후 총 연결 시도 수
     * - 연결 ID 생성 (incrementAndGet() 반환값 사용)
     * - 처리량 통계 계산
     */
    private final AtomicLong totalConnections = new AtomicLong(0);

    /*
     * 활성 연결 수 카운터
     *
     * 활성 연결의 정의:
     * - processConnection() 호출 후 처리 완료 전까지
     * - 스레드풀 큐 대기 + 실제 처리 중인 상태
     * - ConnectionTask의 finally 블록 실행 전까지
     *
     * 모니터링 용도:
     * - 현재 서버 부하 수준 확인
     * - 동시 처리 능력 측정
     * - 스레드풀 포화도 간접 확인
     */
    private final AtomicLong activeConnections = new AtomicLong(0);

    /*
     * 거부된 연결 수 카운터
     *
     * 거부 발생 조건:
     * - 스레드풀이 완전히 포화됨
     * - ThreadPoolManager.submit()에서 예외 발생
     * - 시스템 리소스 부족
     *
     * 중요성:
     * - 시스템 한계 도달 신호
     * - 용량 확장 필요성 판단 기준
     * - SLA 위반 가능성 경고
     */
    private final AtomicLong rejectedConnections = new AtomicLong(0);

    /*
     * 시작 시간 기록
     *
     * volatile 키워드 사용 이유:
     * - 메모리 가시성 보장 (모든 스레드에서 동일한 값 보기)
     * - 단순 읽기/쓰기 연산이므로 원자성 필요 없음
     * - 생성자에서 한 번 설정되고 이후 읽기 전용
     *
     * 용도:
     * - 서버 가동 시간(uptime) 계산
     * - 성능 통계 기간 설정
     * - 재시작 여부 확인
     */
    private volatile long startTime;

    /*
     * 기존 생성자 (하위 호환성 유지)
     *
     * 이 생성자는 ServletContainer 없이 Router만 사용하는 기존 방식을 지원합니다.
     * 새로운 생성자에 null을 전달하여 기존 동작을 유지합니다.
     *
     * @param router HTTP 요청 라우터
     * @param threadPoolConfig 스레드풀 설정
     * @param handlerConfig 요청 핸들러 설정
     */
    public ThreadedProcessor(Router router, ThreadPoolConfig threadPoolConfig,
                             RequestHandlerConfig handlerConfig) {
        /*
         * this(router, null, threadPoolConfig, handlerConfig);
         *
         * 생성자 위임 (Constructor Delegation):
         * - this() 키워드로 같은 클래스의 다른 생성자 호출
         * - ServletContainer를 null로 설정하여 기존 동작 유지
         * - 코드 중복 방지 및 유지보수성 향상
         *
         * 매개변수 전달:
         * - router: 그대로 전달
         * - null: ServletContainer 비활성화
         * - threadPoolConfig: 그대로 전달
         * - handlerConfig: 그대로 전달
         */
        this(router, null, threadPoolConfig, handlerConfig);
    }

    /*
     * 새로운 생성자 (ServletContainer 포함)
     *
     * ServletContainer와 Router를 모두 지원하는 완전한 생성자입니다.
     * 이 생성자가 실제 초기화 로직을 담당합니다.
     *
     * @param router HTTP 요청 라우터
     * @param servletContainer 서블릿 컨테이너 (null 가능)
     * @param threadPoolConfig 스레드풀 설정
     * @param handlerConfig 요청 핸들러 설정
     */
    public ThreadedProcessor(Router router, ThreadedMiniServletContainer servletContainer,
                             ThreadPoolConfig threadPoolConfig, RequestHandlerConfig handlerConfig) {
        /*
         * this.router = router;
         *
         * 라우터 인스턴스 저장:
         * - HTTP 요청을 URL 패턴에 따라 적절한 핸들러로 라우팅
         * - ServletContainer 처리 실패 시 대체 처리기 역할
         * - 미들웨어 체인 처리 기능 포함
         */
        this.router = router;

        /*
         * this.servletContainer = servletContainer;
         *
         * ServletContainer 저장:
         * - null 가능 (하위 호환성)
         * - null이 아닌 경우 Router보다 우선 처리
         * - 서블릿 생명주기 및 컨텍스트 관리
         */
        this.servletContainer = servletContainer;

        /*
         * this.handlerConfig = handlerConfig;
         *
         * 핸들러 설정 저장:
         * - BlockingRequestHandler 생성 시 전달
         * - 연결 처리 정책 및 성능 파라미터 포함
         * - 디버그 모드, 타임아웃 등 설정
         */
        this.handlerConfig = handlerConfig;

        /*
         * this.threadPoolManager = new ThreadPoolManager(threadPoolConfig);
         *
         * ThreadPoolManager 인스턴스 생성:
         * - threadPoolConfig: 스레드풀 동작 파라미터
         * - 톰캣 스타일 스레드 생성 전략 구현
         * - 작업 큐, 거부 정책, 모니터링 포함
         *
         * 생성자에서 수행되는 작업:
         * 1. 톰캣 스타일 ThreadPoolExecutor 생성
         * 2. 커스텀 TaskQueue 설정
         * 3. 모든 코어 스레드 미리 생성 (prestartAllCoreThreads)
         * 4. 모니터링 스케줄러 시작
         */
        this.threadPoolManager = new ThreadPoolManager(threadPoolConfig);

        /*
         * this.startTime = System.currentTimeMillis();
         *
         * 시작 시간 기록:
         * - System.currentTimeMillis(): 현재 시간을 밀리초로 반환
         * - Unix timestamp (1970-01-01 00:00:00 UTC 기준)
         * - uptime 계산의 기준점
         */
        this.startTime = System.currentTimeMillis();

        /*
         * System.out.println("[ThreadedProcessor] Initialized with config: " + handlerConfig);
         *
         * 초기화 완료 로그:
         * - 관리자에게 초기화 상태 알림
         * - handlerConfig.toString(): 설정 정보 출력
         * - 디버깅 및 설정 확인 목적
         */
        System.out.println("[ThreadedProcessor] Initialized with config: " + handlerConfig);

        /*
         * if (servletContainer != null) {
         *     System.out.println("[ThreadedProcessor] ServletContainer integration enabled");
         * }
         *
         * ServletContainer 통합 상태 로그:
         * - null 체크로 ServletContainer 활성화 여부 확인
         * - 활성화된 경우에만 로그 출력
         * - 하이브리드 모드 동작 확인
         */
        if (servletContainer != null) {
            System.out.println("[ThreadedProcessor] ServletContainer integration enabled");
        }
    }

    /**
     * 클라이언트 연결 처리
     */
    /*
     * public Future<?> processConnection(Socket clientSocket)
     *
     * 클라이언트 연결 처리 메서드:
     * - public: 외부 클래스 (ThreadedServer)에서 호출
     * - Future<?> 반환: 비동기 작업 추적 가능
     * - Socket clientSocket: 클라이언트와의 TCP 연결
     *
     * 메서드 역할:
     * 1. 연결 통계 업데이트
     * 2. 고유 연결 ID 생성
     * 3. 스레드풀에 처리 작업 제출
     * 4. 예외 상황 처리 (스레드풀 포화 등)
     *
     * 반환값 Future<?> 활용:
     * - 작업 완료 여부 확인 (isDone())
     * - 작업 취소 (cancel())
     * - 완료까지 대기 (get())
     * - 예외 발생 여부 확인
     */
    public Future<?> processConnection(Socket clientSocket) {
        /*
         * long connectionId = totalConnections.incrementAndGet();
         *
         * 고유 연결 ID 생성:
         * - AtomicLong.incrementAndGet(): 원자적으로 1 증가 후 값 반환
         * - 멀티스레드 환경에서 중복되지 않는 고유 ID 보장
         * - 1, 2, 3, ... 순서로 증가하는 연결 식별자
         *
         * 연결 ID 용도:
         * - 로그에서 특정 연결 추적
         * - 디버깅 시 연결별 처리 과정 확인
         * - 통계 수집 및 모니터링
         * - 연결 상태 관리
         */
        long connectionId = totalConnections.incrementAndGet();

        /*
         * activeConnections.incrementAndGet();
         *
         * 활성 연결 수 증가:
         * - 연결 처리 시작 시점에 즉시 증가
         * - 스레드풀 큐 대기 시간도 활성 상태로 간주
         * - ConnectionTask의 finally에서 감소됨
         *
         * 이 시점에 증가시키는 이유:
         * - 연결 수락 직후부터 서버 리소스 사용 시작
         * - 큐 대기 중인 연결도 시스템 부하에 포함
         * - 정확한 동시 처리 연결 수 추적
         */
        activeConnections.incrementAndGet();

        /*
         * if (handlerConfig.isDebugMode()) { ... }
         *
         * 디버그 모드 확인:
         * - handlerConfig.isDebugMode(): 설정에서 디버그 모드 여부 확인
         * - true인 경우에만 상세 로그 출력
         * - 프로덕션 환경에서 로그 오버헤드 방지
         */
        if (handlerConfig.isDebugMode()) {
            /*
             * System.out.println("[ThreadedProcessor] New connection #" + connectionId +
             *                   " from " + clientSocket.getRemoteSocketAddress());
             *
             * 새 연결 로그:
             * - connectionId: 고유 연결 식별자
             * - clientSocket.getRemoteSocketAddress(): 클라이언트 IP와 포트
             *
             * getRemoteSocketAddress() 메서드:
             * - SocketAddress 타입 반환 (실제로는 InetSocketAddress)
             * - 클라이언트의 IP 주소와 포트 번호 포함
             * - toString()으로 "192.168.1.100:54321" 형태 출력
             *
             * 로그 용도:
             * - 연결 패턴 분석 (어떤 IP에서 많이 접속하는지)
             * - 부하 테스트 시 연결 상태 확인
             * - 보안 모니터링 (비정상적인 연결 감지)
             */
            System.out.println("[ThreadedProcessor] New connection #" + connectionId +
                    " from " + clientSocket.getRemoteSocketAddress());
        }

        /*
         * try { ... } catch (Exception e) { ... }
         *
         * 예외 처리 블록:
         * - 스레드풀 포화나 시스템 오류 대응
         * - 연결 거부 시 적절한 정리 작업 수행
         * - 서버 안정성 보장
         */
        try {
            // 스레드풀에 작업 제출
            /*
             * return threadPoolManager.submit(new ConnectionTask(clientSocket, connectionId));
             *
             * 스레드풀에 작업 제출:
             *
             * threadPoolManager.submit() 메서드:
             * - Runnable 객체를 스레드풀에 제출
             * - Future<?> 객체 반환 (작업 추적 가능)
             * - 톰캣 스타일 스레드 생성 전략 적용
             *
             * new ConnectionTask(clientSocket, connectionId):
             * - 내부 클래스 ConnectionTask 인스턴스 생성
             * - clientSocket: 처리할 클라이언트 연결
             * - connectionId: 연결 식별을 위한 고유 ID
             *
             * 스레드풀 내부 동작:
             * 1. 코어 풀에 여유 있으면 즉시 실행
             * 2. 코어 풀 가득하면 새 스레드 생성 (톰캣 스타일)
             * 3. 최대 스레드 도달하면 큐에 대기
             * 4. 큐도 가득하면 거부 정책 실행
             *
             * Future 반환의 의미:
             * - 호출자가 작업 상태 추적 가능
             * - 필요시 작업 취소 가능
             * - 작업 완료 대기 가능
             */
            return threadPoolManager.submit(new ConnectionTask(clientSocket, connectionId));

        } catch (Exception e) {
            /*
             * catch (Exception e) 블록:
             *
             * 예외 발생 상황:
             * - 스레드풀이 완전히 포화됨
             * - 시스템 리소스 부족 (메모리, 파일 디스크립터 등)
             * - ThreadPoolManager 내부 오류
             *
             * 일반적인 원인:
             * - 과도한 트래픽으로 인한 스레드풀 포화
             * - 시스템 메모리 부족
             * - OS 레벨 리소스 한계 도달
             * - 네트워크 문제
             */

            // 스레드풀이 포화된 경우
            /*
             * rejectedConnections.incrementAndGet();
             *
             * 거부된 연결 수 증가:
             * - AtomicLong의 원자적 증가 연산
             * - 시스템 포화 상태 통계 수집
             * - SLA 모니터링 및 경고 기준
             */
            rejectedConnections.incrementAndGet();

            /*
             * activeConnections.decrementAndGet();
             *
             * 활성 연결 수 감소:
             * - 위에서 증가시켰지만 실제 처리되지 않으므로 롤백
             * - 정확한 활성 연결 수 유지
             * - 통계 정확성 보장
             */
            activeConnections.decrementAndGet();

            /*
             * System.err.println("[ThreadedProcessor] Connection #" + connectionId +
             *                   " rejected - thread pool saturated");
             *
             * 연결 거부 에러 로그:
             * - System.err: 표준 에러 스트림 (긴급한 오류용)
             * - connectionId: 거부된 연결 식별
             * - "thread pool saturated": 거부 원인 명시
             *
             * 에러 로그의 중요성:
             * - 시스템 관리자에게 즉시 알림
             * - 모니터링 시스템에서 경고 발생 트리거
             * - 용량 확장 필요성 판단 기준
             */
            System.err.println("[ThreadedProcessor] Connection #" + connectionId +
                    " rejected - thread pool saturated");

            // 연결 즉시 종료
            /*
             * try { clientSocket.close(); } catch (Exception closeException) { }
             *
             * 클라이언트 소켓 정리:
             *
             * clientSocket.close() 메서드:
             * - TCP 연결 즉시 종료
             * - 클라이언트에게 연결 종료 신호 전송
             * - 시스템 리소스 (파일 디스크립터) 해제
             *
             * 중첩 try-catch 이유:
             * - close() 메서드도 IOException 발생 가능
             * - 이미 예외 처리 중이므로 추가 예외 무시
             * - 최소한의 정리 작업 보장
             *
             * 즉시 종료하는 이유:
             * - 처리할 수 없는 연결을 계속 유지할 이유 없음
             * - 시스템 리소스 절약
             * - 클라이언트가 빠르게 재시도 가능
             */
            try {
                clientSocket.close();
            } catch (Exception closeException) {
                // 무시 - 이미 예외 상황이므로 추가 처리 안 함
            }

            /*
             * throw new RuntimeException("Thread pool saturated", e);
             *
             * 예외 재발생:
             * - RuntimeException: 체크되지 않은 예외로 래핑
             * - "Thread pool saturated": 구체적인 오류 메시지
             * - e: 원본 예외를 cause로 설정
             *
             * 예외를 다시 던지는 이유:
             * - 호출자 (ThreadedServer)에게 오류 상황 알림
             * - 서버 레벨에서 추가 대응 가능
             * - 로그 수집 시스템에서 오류 추적
             *
             * RuntimeException 사용 이유:
             * - 메서드 시그니처에 throws 선언 불필요
             * - 호출자가 선택적으로 처리 가능
             * - 시스템 오류이므로 복구 어려움
             */
            throw new RuntimeException("Thread pool saturated", e);
        }
    }

    /**
     * 연결 처리 작업 래퍼 (수정된 버전)
     */
    /*
     * private class ConnectionTask implements Runnable
     *
     * 내부 클래스로 연결 처리 작업 정의:
     * - private: 외부에서 직접 접근 불가
     * - implements Runnable: 스레드에서 실행 가능한 작업
     *
     * 내부 클래스 사용 이유:
     * 1. 캡슐화: 연결 처리 로직을 ThreadedProcessor 내부에 은닉
     * 2. 외부 클래스 멤버 접근: servletContainer, router, handlerConfig 직접 사용
     * 3. 응집성: 관련 기능을 하나의 클래스에 묶음
     * 4. 네임스페이스: 의미 있는 컨텍스트 내에서만 존재
     *
     * Runnable 인터페이스:
     * - 함수형 인터페이스 (단일 추상 메서드)
     * - run() 메서드 구현 필요
     * - 스레드에서 실행할 작업 정의
     * - 반환값 없음, 예외 던지기 불가
     */
    private class ConnectionTask implements Runnable {
        /*
         * private final Socket clientSocket;
         *
         * 클라이언트 소켓 저장:
         * - final: 생성 후 참조 변경 불가
         * - 실제 HTTP 통신이 이루어지는 TCP 연결
         * - BlockingRequestHandler에 전달되어 요청/응답 처리
         */
        private final Socket clientSocket;

        /*
         * private final long connectionId;
         *
         * 연결 고유 식별자:
         * - processConnection()에서 생성된 고유 ID
         * - 로그 추적 및 디버깅용
         * - 통계 수집 시 연결 구분
         */
        private final long connectionId;

        /*
         * public ConnectionTask(Socket clientSocket, long connectionId)
         *
         * 생성자:
         * - 클라이언트 소켓과 연결 ID를 받아 저장
         * - final 필드 초기화
         * - 간단한 래퍼 객체 생성
         */
        public ConnectionTask(Socket clientSocket, long connectionId) {
            this.clientSocket = clientSocket;
            this.connectionId = connectionId;
        }

        /*
         * @Override
         * public void run()
         *
         * Runnable 인터페이스 구현:
         * - 스레드풀의 워커 스레드에서 실행됨
         * - 실제 HTTP 요청/응답 처리 로직
         * - 예외 처리 및 리소스 정리 포함
         *
         * 실행 컨텍스트:
         * - ThreadPoolManager의 워커 스레드
         * - 독립적인 스레드 스택
         * - 동시에 여러 ConnectionTask 실행 가능
         */
        @Override
        public void run() {
            /*
             * try { ... } catch (Exception e) { ... } finally { ... }
             *
             * 구조화된 예외 처리:
             * - try: 정상 처리 로직
             * - catch: 예외 상황 처리
             * - finally: 리소스 정리 (반드시 실행)
             */
            try {
                // ServletContainer가 있으면 ServletContainer 우선 사용
                /*
                 * if (servletContainer != null) { ... } else { ... }
                 *
                 * 하이브리드 처리 전략:
                 * - ServletContainer 우선, Router 대체
                 * - 하위 호환성과 유연성 모두 제공
                 * - 설정에 따른 동적 처리 방식 결정
                 */
                if (servletContainer != null) {
                    // ServletContainer 통합 핸들러 사용
                    /*
                     * BlockingRequestHandler handler = new BlockingRequestHandler(
                     *     clientSocket, router, servletContainer, handlerConfig);
                     *
                     * ServletContainer 통합 모드:
                     * - clientSocket: 클라이언트 연결
                     * - router: 대체 처리기 (fallback)
                     * - servletContainer: 주 처리기 (primary)
                     * - handlerConfig: 처리 설정
                     *
                     * 처리 순서:
                     * 1. ServletContainer에서 URL 패턴 매칭 시도
                     * 2. 매칭되는 서블릿 있으면 서블릿으로 처리
                     * 3. 매칭 실패하면 Router로 처리
                     * 4. Router도 실패하면 404 응답
                     */
                    BlockingRequestHandler handler = new BlockingRequestHandler(
                            clientSocket, router, servletContainer, handlerConfig
                    );
                    /*
                     * handler.run();
                     *
                     * BlockingRequestHandler 실행:
                     * - 현재 스레드에서 동기적 실행
                     * - HTTP Keep-Alive 루프 처리
                     * - 요청 파싱, 처리, 응답 전송
                     * - 연결 종료까지 모든 과정
                     */
                    handler.run();
                } else {
                    // 기존 Router만 사용
                    /*
                     * BlockingRequestHandler handler = new BlockingRequestHandler(
                     *     clientSocket, router, handlerConfig);
                     *
                     * Router 전용 모드:
                     * - ServletContainer 없이 Router만 사용
                     * - 기존 방식과 완전히 호환
                     * - 단순하고 가벼운 처리
                     *
                     * 처리 방식:
                     * 1. Router에서 URL 패턴 매칭
                     * 2. 매칭되는 핸들러로 처리
                     * 3. 실패하면 404 응답
                     */
                    BlockingRequestHandler handler = new BlockingRequestHandler(
                            clientSocket, router, handlerConfig
                    );
                    handler.run();
                }

            } catch (Exception e) {
                /*
                 * 예외 처리:
                 *
                 * 예외 발생 가능 상황:
                 * - HTTP 파싱 오류 (잘못된 요청 형식)
                 * - 네트워크 연결 끊김
                 * - 서블릿이나 핸들러 내부 오류
                 * - 시스템 리소스 부족
                 * - 타임아웃 발생
                 *
                 * System.err.println() 사용 이유:
                 * - 표준 에러 스트림 (즉시 출력)
                 * - 로그 수집 시스템에서 에러로 분류
                 * - 긴급한 오류 상황임을 명시
                 */
                System.err.println("[ThreadedProcessor] Error in connection #" + connectionId +
                        ": " + e.getMessage());

                /*
                 * e.getMessage() 사용:
                 * - 예외의 간단한 메시지만 출력
                 * - 전체 스택 트레이스는 로그 노이즈 방지
                 * - 핵심 오류 정보만 간결하게 표시
                 *
                 * 상세 디버깅이 필요한 경우:
                 * - e.printStackTrace() 사용
                 * - 로깅 프레임워크 활용
                 * - 디버그 모드에서만 상세 출력
                 */

            } finally {
                /*
                 * finally 블록:
                 * - try/catch 결과와 무관하게 항상 실행
                 * - 리소스 정리 및 통계 업데이트
                 * - 메모리 누수 방지
                 * - 정확한 통계 유지
                 */

                /*
                 * activeConnections.decrementAndGet();
                 *
                 * 활성 연결 수 감소:
                 * - processConnection()에서 증가시킨 값 복원
                 * - 원자적 감소 연산으로 정확성 보장
                 * - 예외 발생 여부와 무관하게 반드시 실행
                 *
                 * 중요성:
                 * - 정확한 동시 연결 수 추적
                 * - 메모리 누수 방지 (카운터 오버플로우)
                 * - 모니터링 시스템 정확성 보장
                 */
                activeConnections.decrementAndGet();

                /*
                 * if (handlerConfig.isDebugMode()) { ... }
                 *
                 * 디버그 모드에서만 완료 로그 출력:
                 * - 성능 오버헤드 최소화
                 * - 프로덕션 환경에서 로그 노이즈 방지
                 * - 개발/테스트 시에만 상세 추적
                 */
                if (handlerConfig.isDebugMode()) {
                    /*
                     * System.out.println("[ThreadedProcessor] Connection #" + connectionId +
                     *                   " finished - active: " + activeConnections.get());
                     *
                     * 연결 처리 완료 로그:
                     * - connectionId: 완료된 연결 식별
                     * - activeConnections.get(): 현재 활성 연결 수
                     *
                     * 모니터링 정보:
                     * - 개별 연결 처리 완료 추적
                     * - 현재 서버 부하 수준 확인
                     * - 연결 처리 패턴 분석
                     * - 성능 병목 지점 식별
                     */
                    System.out.println("[ThreadedProcessor] Connection #" + connectionId +
                            " finished - active: " + activeConnections.get());
                }
            }
        }
    }

    /**
     * 프로세서 상태 정보
     */
    /*
     * public ProcessorStatus getStatus()
     *
     * 현재 프로세서 상태 반환:
     * - public: 외부 모니터링 시스템에서 접근
     * - ProcessorStatus: 하단에 정의된 상태 정보 클래스
     * - 모든 통계를 하나의 객체로 패키징
     *
     * 용도:
     * - 관리 대시보드 데이터 제공
     * - REST API 상태 엔드포인트
     * - 모니터링 시스템 연동
     * - 성능 분석 데이터 수집
     */
    public ProcessorStatus getStatus() {
        /*
         * ThreadPoolManager.ThreadPoolStatus poolStatus = threadPoolManager.getStatus();
         *
         * 스레드풀 상태 조회:
         * - threadPoolManager.getStatus(): 스레드풀의 모든 통계 반환
         * - ThreadPoolStatus: 스레드 수, 큐 크기, 처리 시간 등 포함
         * - 현재 시점의 스냅샷
         */
        ThreadPoolManager.ThreadPoolStatus poolStatus = threadPoolManager.getStatus();

        /*
         * return new ProcessorStatus(...)
         *
         * ProcessorStatus 객체 생성:
         * - 모든 통계 정보를 하나의 불변 객체로 패키징
         * - 일관성 있는 스냅샷 제공
         * - 외부 시스템에서 사용하기 쉬운 형태
         */
        return new ProcessorStatus(
                /*
                 * totalConnections.get()
                 * 총 연결 수 (누적)
                 */
                totalConnections.get(),

                /*
                 * activeConnections.get()
                 * 현재 활성 연결 수
                 */
                activeConnections.get(),

                /*
                 * rejectedConnections.get()
                 * 거부된 연결 수 (누적)
                 */
                rejectedConnections.get(),

                /*
                 * System.currentTimeMillis() - startTime
                 * 서버 가동 시간 (밀리초)
                 */
                System.currentTimeMillis() - startTime,

                /*
                 * poolStatus
                 * 스레드풀 상태 정보
                 */
                poolStatus
        );
    }

    /**
     * 통계 정보 출력
     */
    /*
     * public void printStatistics()
     *
     * 현재 통계를 콘솔에 출력:
     * - 관리자용 상태 확인
     * - 디버깅 및 모니터링
     * - 정기적 상태 보고
     */
    public void printStatistics() {
        /*
         * ProcessorStatus status = getStatus();
         *
         * 현재 상태 스냅샷 획득:
         * - 일관성 있는 통계 데이터
         * - 출력 중 값 변경 방지
         */
        ProcessorStatus status = getStatus();

        /*
         * 통계 출력 시작 헤더
         */
        System.out.println("\n=== ThreadedProcessor Statistics ===");

        /*
         * 기본 연결 통계
         */
        System.out.println("Total Connections: " + status.getTotalConnections());
        System.out.println("Active Connections: " + status.getActiveConnections());
        System.out.println("Rejected Connections: " + status.getRejectedConnections());

        /*
         * status.getUptime() / 1000
         * 가동 시간을 초 단위로 변환 (밀리초 → 초)
         */
        System.out.println("Uptime: " + (status.getUptime() / 1000) + " seconds");

        /*
         * if (status.getTotalConnections() > 0) { ... }
         *
         * 총 연결이 있을 때만 비율 계산:
         * - 0으로 나누기 오류 방지
         * - 의미 있는 통계가 있을 때만 출력
         */
        if (status.getTotalConnections() > 0) {
            /*
             * double rejectionRate = (double) status.getRejectedConnections() / status.getTotalConnections() * 100;
             *
             * 거부율 계산:
             * - (double) 캐스팅: 정수 나눗셈을 실수 나눗셈으로 변환
             * - * 100: 백분율로 변환
             *
             * 거부율 의미:
             * - 전체 연결 중 처리하지 못한 비율
             * - 시스템 포화도 지표
             * - 0%에 가까울수록 이상적
             */
            double rejectionRate = (double) status.getRejectedConnections() / status.getTotalConnections() * 100;

            /*
             * String.format("%.2f", rejectionRate)
             * 소수점 둘째자리까지 포맷팅
             */
            System.out.println("Rejection Rate: " + String.format("%.2f", rejectionRate) + "%");
        }

        /*
         * 스레드풀 상태 출력
         */
        System.out.println("\nThread Pool Status:");
        System.out.println(status.getThreadPoolStatus());

        /*
         * 통계 출력 종료 구분선
         */
        System.out.println("=====================================\n");
    }

    /**
     * 프로세서 종료
     */
    /*
     * public void shutdown()
     *
     * 프로세서 우아한 종료:
     * - 진행 중인 작업 완료 대기
     * - 스레드풀 정리
     * - 최종 통계 출력
     */
    public void shutdown() {
        System.out.println("[ThreadedProcessor] Shutting down...");

        // 통계 출력
        /*
         * printStatistics();
         *
         * 종료 전 최종 통계 출력:
         * - 운영 기간 동안의 성능 요약
         * - 문제 분석을 위한 데이터 보존
         * - 다음 시작 시 참고 자료
         */
        printStatistics();

        // 스레드풀 종료
        /*
         * threadPoolManager.shutdown();
         *
         * ThreadPoolManager 종료:
         * - 새 작업 제출 거부
         * - 진행 중인 작업 완료 대기
         * - 스레드 정리 및 리소스 해제
         * - 우아한 종료 프로세스 수행
         */
        threadPoolManager.shutdown();

        System.out.println("[ThreadedProcessor] Shutdown completed");
    }

    // Getters
    /*
     * 외부 접근을 위한 간단한 getter 메서드들
     * - 캡슐화 원칙 준수
     * - 읽기 전용 접근 제공
     * - 실시간 통계 조회
     */

    /*
     * public long getActiveConnections()
     * 현재 활성 연결 수 반환
     */
    public long getActiveConnections() {
        return activeConnections.get();
    }

    /*
     * public long getTotalConnections()
     * 총 연결 수 반환 (누적)
     */
    public long getTotalConnections() {
        return totalConnections.get();
    }

    /*
     * public long getRejectedConnections()
     * 거부된 연결 수 반환 (누적)
     */
    public long getRejectedConnections() {
        return rejectedConnections.get();
    }

    /**
     * 프로세서 상태 클래스
     */
    /*
     * public static class ProcessorStatus
     *
     * 프로세서 상태 정보를 담는 불변 객체:
     * - public: 외부에서 접근 가능
     * - static: 외부 클래스 인스턴스와 독립적
     * - 모든 필드가 final (불변성)
     *
     * 불변 객체의 장점:
     * - 스레드 안전성 자동 보장
     * - 메서드 인자로 안전하게 전달
     * - 캐싱 및 공유 가능
     * - 예측 가능한 동작
     */
    public static class ProcessorStatus {
        /*
         * 모든 상태 정보를 final 필드로 저장
         * - 생성 시점에 모든 값 고정
         * - 이후 변경 불가능
         * - getter 메서드로만 접근
         */
        private final long totalConnections;      // 총 연결 수
        private final long activeConnections;     // 활성 연결 수
        private final long rejectedConnections;   // 거부된 연결 수
        private final long uptime;                // 가동 시간 (밀리초)
        private final ThreadPoolManager.ThreadPoolStatus threadPoolStatus;  // 스레드풀 상태

        /*
         * 생성자: 모든 상태 값을 받아 final 필드 초기화
         */
        public ProcessorStatus(long totalConnections, long activeConnections,
                               long rejectedConnections, long uptime,
                               ThreadPoolManager.ThreadPoolStatus threadPoolStatus) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.rejectedConnections = rejectedConnections;
            this.uptime = uptime;
            this.threadPoolStatus = threadPoolStatus;
        }

        /*
         * Getter 메서드들: 필드 값을 외부에 제공
         * - public: 외부 접근 허용
         * - 단순히 필드 값 반환
         * - setter 없음 (불변 객체)
         */
        public long getTotalConnections() { return totalConnections; }
        public long getActiveConnections() { return activeConnections; }
        public long getRejectedConnections() { return rejectedConnections; }
        public long getUptime() { return uptime; }
        public ThreadPoolManager.ThreadPoolStatus getThreadPoolStatus() { return threadPoolStatus; }

        /*
         * @Override
         * public String toString()
         *
         * 객체의 문자열 표현:
         * - 디버깅 및 로깅 목적
         * - 주요 정보만 간결하게 표시
         * - 가독성 좋은 형태로 포맷팅
         */
        @Override
        public String toString() {
            /*
             * String.format() 사용:
             * - 일관된 형식으로 정보 표시
             * - uptime / 1000: 밀리초를 초로 변환
             * - 핵심 지표만 포함하여 간결성 유지
             */
            return String.format(
                    "ProcessorStatus{total=%d, active=%d, rejected=%d, uptime=%ds}",
                    totalConnections, activeConnections, rejectedConnections, uptime / 1000
            );
        }
    }
}