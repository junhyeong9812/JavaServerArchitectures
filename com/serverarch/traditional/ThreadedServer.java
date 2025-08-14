package com.serverarch.traditional; // 패키지 선언 - 전통적인 스레드 기반 서버 아키텍처 패키지

// import 선언부 - 외부 클래스들을 현재 클래스에서 사용할 수 있도록 경로 지정
import com.serverarch.common.http.*; // HTTP 관련 공통 클래스들 전체 import - HttpRequest, HttpResponse, HttpHeaders 등
import com.serverarch.traditional.handlers.StaticFileHandler;
import com.serverarch.traditional.routing.*; // 라우팅 시스템 import 추가 - Router, RouteHandler 등 라우팅 관련 클래스들
import java.net.*; // 네트워크 관련 클래스들 전체 import - ServerSocket, Socket, InetSocketAddress 등
import java.io.*; // 입출력 관련 클래스들 전체 import - InputStream, OutputStream, IOException 등
import java.util.concurrent.*; // 동시성 관련 클래스들 전체 import - ExecutorService, ThreadFactory, AtomicLong 등
import java.util.concurrent.atomic.*; // 원자적 연산 클래스들 전체 import - AtomicBoolean, AtomicLong 등
import java.util.logging.*; // 로깅 관련 클래스들 전체 import - Logger, Level 등
import java.time.*; // 시간 관련 클래스 추가 - 메트릭 수집에 사용
import java.util.*; // Map, List 등 컬렉션 클래스들 추가

/**
 * Thread-per-Request 방식의 HTTP 서버 (개선 버전)
 *
 * 기존 설계 원칙 유지:
 * 1. 각 클라이언트 연결마다 별도 스레드 할당
 * 2. 블로킹 I/O 사용으로 구현 단순성 확보
 * 3. 스레드풀로 스레드 생성 비용 최적화
 * 4. 동기식 처리로 디버깅 용이성 제공
 *
 * 추가된 개선사항:
 * 5. 라우팅 시스템 통합으로 RESTful API 지원
 * 6. 미들웨어 체인으로 공통 기능 처리
 * 7. 연결 제한 및 백프레셔 처리 추가
 * 8. 상세한 메트릭 수집 및 모니터링
 * 9. 그레이스풀 셧다운 강화
 * 10. 정적 파일 서빙 기능 통합
 *
 * 기존 장점 유지:
 * - 구현이 단순하고 직관적
 * - 디버깅이 쉬움 (스택 추적 명확)
 * - 블로킹 I/O로 기존 라이브러리 활용 용이
 *
 * 개선된 단점:
 * - 스레드당 메모리 사용량 최적화 (ThreadPoolManager 활용)
 * - 컨텍스트 스위칭 오버헤드 감소 (스레드 풀 관리)
 * - 동시 연결 수 제한 개선 (백프레셔 및 연결 제한)
 */
public class ThreadedServer { // public 클래스 선언 - 다른 패키지에서 접근 가능한 HTTP 서버 클래스

    // 로거 - 서버 동작 상태와 문제 진단을 위한 로깅
    private static final Logger logger = Logger.getLogger(ThreadedServer.class.getName()); // static final - 클래스 레벨 상수, Logger.getLogger() - 클래스 이름 기반 로거 생성

    // 서버 바인딩 포트
    // final로 선언하여 서버 시작 후 변경 불가 - 설정 일관성 보장
    private final int port; // final int - 불변 정수 필드, 서버가 바인딩할 포트 번호

    // 스레드풀 크기 설정
    // final로 선언하여 서버 설정 불변성 보장
    private final int threadPoolSize; // final int - 불변 정수 필드, 동시 처리 가능한 최대 스레드 수

    // 연결 대기 큐 크기 (ServerSocket backlog)
    // 동시에 대기할 수 있는 연결 요청 수 제한
    private final int backlog; // final int - 불변 정수 필드, 연결 대기 큐의 최대 크기

    // 서버 실행 상태
    // AtomicBoolean 사용으로 멀티스레드 환경에서 안전한 상태 관리
    private final AtomicBoolean running = new AtomicBoolean(false); // AtomicBoolean - 원자적 boolean 연산 지원, new AtomicBoolean(false) - 초기값 false로 생성

    // 서버 소켓
    // volatile로 선언하여 다른 스레드에서 즉시 변경사항 확인 가능
    private volatile ServerSocket serverSocket; // volatile - 메모리 가시성 보장, ServerSocket - 서버 소켓 객체

    // 스레드풀 - 요청 처리용 스레드들을 관리
    // 스레드 생성/소멸 비용을 줄이고 동시 처리 수를 제어
    private ExecutorService threadPool; // ExecutorService - 스레드풀 인터페이스, 비동기 작업 실행 관리

    // 요청 처리기 - 실제 HTTP 요청/응답 처리 로직
    // 의존성 주입으로 테스트 용이성과 확장성 확보
    private ThreadedRequestProcessor requestProcessor; // ThreadedRequestProcessor - HTTP 요청 처리 담당 클래스

    // ========== 추가된 개선 컴포넌트들 ==========

    // 라우팅 시스템 - RESTful API 지원을 위한 URL 패턴 매칭 및 핸들러 관리
    private final Router router; // Router 클래스 - 기존 코드에서 제공하는 라우팅 시스템 활용

    // 스레드 풀 관리자 - 고급 스레드 풀 관리 기능 제공
    private ThreadPoolManager threadPoolManager; // ThreadPoolManager 클래스 - 기존 코드에서 제공하는 스레드 풀 관리자 활용

    // 연결 제한 - 동시 연결 수 제한으로 서버 과부하 방지
    private final Semaphore connectionSemaphore; // Semaphore - 동시 접근 수를 제한하는 동기화 도구
    private final int maxConnections; // 최대 동시 연결 수 설정값

    // 활성 연결 추적 - 현재 처리 중인 연결들을 추적하여 그레이스풀 셧다운에 활용
    private final Set<Socket> activeConnections; // Set<Socket> - 현재 활성 상태인 소켓들을 추적하는 집합

    // 서버 메트릭 수집 - 성능 모니터링을 위한 각종 지표 수집
    private final ServerMetrics metrics; // 서버 성능 지표를 수집하는 커스텀 클래스

    // 통계 카운터들 - 서버 성능 모니터링을 위한 메트릭 (기존 코드 유지)
    private final AtomicLong totalRequestsReceived = new AtomicLong(0);    // AtomicLong - 원자적 long 연산 지원, 받은 총 요청 수
    private final AtomicLong totalRequestsProcessed = new AtomicLong(0);   // AtomicLong - 처리 완료된 요청 수
    private final AtomicLong totalRequestsFailed = new AtomicLong(0);      // AtomicLong - 실패한 요청 수
    private final AtomicLong currentActiveConnections = new AtomicLong(0); // AtomicLong - 현재 활성 연결 수

    // 기본 설정 상수들 (기존 코드 유지)
    private static final int DEFAULT_THREAD_POOL_SIZE = 200;  // static final int - 기본 스레드풀 크기, CPU 코어 수의 배수로 설정
    private static final int DEFAULT_BACKLOG = 50;            // static final int - 기본 백로그 크기, 적당한 대기 큐 크기
    private static final int SOCKET_TIMEOUT = 30000;          // static final int - 소켓 타임아웃 30초, 무한 대기 방지

    // 추가된 설정 상수들
    private static final int DEFAULT_MAX_CONNECTIONS = 1000;  // 기본 최대 동시 연결 수 - 서버 과부하 방지를 위한 제한
    private static final int CONNECTION_QUEUE_CAPACITY = 2000; // 연결 대기 큐 용량 - ThreadPoolManager에서 사용

    /**
     * 기본 설정으로 서버 생성 (기존 생성자 유지)
     *
     * @param port 서버 포트
     */
    public ThreadedServer(int port) { // public 생성자 - 포트만 받는 간단한 생성자
        // 기본 설정값들로 상세 생성자 호출
        this(port, DEFAULT_THREAD_POOL_SIZE, DEFAULT_BACKLOG); // this() - 같은 클래스의 다른 생성자 호출, 기본값들과 함께 호출
    }

    /**
     * 포트와 스레드풀 크기만 지정하는 생성자 (기존 생성자 유지)
     * ThreadedServerLauncher에서 사용하는 생성자
     *
     * @param port 서버 포트 (1-65535)
     * @param threadPoolSize 스레드풀 크기 (최소 1)
     */
    public ThreadedServer(int port, int threadPoolSize) { // public 생성자 - 포트와 스레드풀 크기를 받는 생성자
        // 기본 백로그 크기와 함께 상세 생성자 호출
        this(port, threadPoolSize, DEFAULT_BACKLOG); // this() - 세 개 매개변수를 받는 생성자 호출, 백로그는 기본값 사용
    }

    /**
     * 상세 설정으로 서버 생성 (기존 생성자 확장)
     *
     * @param port 서버 포트 (1-65535)
     * @param threadPoolSize 스레드풀 크기 (최소 1)
     * @param backlog 연결 대기 큐 크기 (최소 1)
     */
    public ThreadedServer(int port, int threadPoolSize, int backlog) { // public 생성자 - 모든 설정을 받는 상세 생성자
        // 포트 번호 유효성 검증 - 시스템 포트와 사용자 포트 범위 확인 (기존 검증 로직 유지)
        if (port < 1 || port > 65535) { // 조건문 - 포트 번호가 유효 범위(1-65535)를 벗어나는지 확인
            throw new IllegalArgumentException("포트 번호는 1-65535 사이여야 합니다: " + port); // IllegalArgumentException - 잘못된 인수 예외
        }

        // 스레드풀 크기 유효성 검증 - 최소 1개는 있어야 요청 처리 가능 (기존 검증 로직 유지)
        if (threadPoolSize < 1) { // 조건문 - 스레드풀 크기가 1보다 작은지 확인
            throw new IllegalArgumentException("스레드풀 크기는 1 이상이어야 합니다: " + threadPoolSize); // 스레드풀 크기 오류 예외
        }

        // 백로그 크기 유효성 검증 - 최소 1개는 있어야 연결 대기 가능 (기존 검증 로직 유지)
        if (backlog < 1) { // 조건문 - 백로그 크기가 1보다 작은지 확인
            throw new IllegalArgumentException("백로그 크기는 1 이상이어야 합니다: " + backlog); // 백로그 크기 오류 예외
        }

        // 인스턴스 변수 초기화 - 검증된 값들로 설정 (기존 로직 유지)
        this.port = port; // this.port - 현재 객체의 port 필드에 매개변수 값 할당
        this.threadPoolSize = threadPoolSize; // this.threadPoolSize - 현재 객체의 threadPoolSize 필드에 매개변수 값 할당
        this.backlog = backlog; // this.backlog - 현재 객체의 backlog 필드에 매개변수 값 할당

        // ========== 추가된 초기화 로직 ==========

        // 라우팅 시스템 초기화 - 기존 Router 클래스 활용
        this.router = new Router(); // Router 인스턴스 생성 - 기존 코드에서 제공하는 라우팅 시스템 사용

        // 연결 제한 초기화 - 동시 연결 수 제한을 위한 Semaphore 설정
        this.maxConnections = DEFAULT_MAX_CONNECTIONS; // 최대 연결 수를 기본값으로 설정
        this.connectionSemaphore = new Semaphore(maxConnections); // Semaphore 생성 - 최대 연결 수만큼 permit 생성

        // 활성 연결 추적을 위한 동시성 안전 Set 초기화
        this.activeConnections = ConcurrentHashMap.newKeySet(); // ConcurrentHashMap.newKeySet() - 동시성 안전한 Set 생성

        // 메트릭 수집 시스템 초기화
        this.metrics = new ServerMetrics(); // 서버 성능 지표 수집을 위한 커스텀 클래스 인스턴스 생성

        // 기본 라우트 설정 - 헬스 체크, 메트릭, 정보 엔드포인트 등록
        setupDefaultRoutes(); // 기본 라우트들을 설정하는 메서드 호출

        // 로그로 서버 설정 기록 - 디버깅과 모니터링에 유용 (기존 로직 유지하되 추가 정보 포함)
        logger.info(String.format("ThreadedServer 생성됨 - 포트: %d, 스레드풀: %d, 백로그: %d, 최대연결: %d", // Logger.info() - 정보 레벨 로그, String.format() - 문자열 템플릿
                port, threadPoolSize, backlog, maxConnections)); // 생성자 매개변수들과 추가 설정값을 로그에 기록
    }

    /**
     * 기본 라우트 설정 - 서버 관리 및 모니터링을 위한 기본 엔드포인트들 등록
     * 이 메서드는 서버 생성 시 자동으로 호출되어 필수 관리 기능들을 제공
     */
    private void setupDefaultRoutes() { // private 메서드 - 클래스 내부에서만 사용하는 초기화 메서드
        // 헬스 체크 엔드포인트 - 서버 상태 확인용 (/health)
        router.get("/health", new RouteHandler() { // Router.get() - GET 메서드용 라우트 등록, 익명 클래스로 RouteHandler 구현
            @Override // 어노테이션 - 인터페이스 메서드 재정의 명시
            public HttpResponse handle(HttpRequest request) throws Exception { // RouteHandler.handle() - 요청 처리 메서드
                Map<String, Object> healthData = new HashMap<>(); // HashMap 생성 - 헬스 체크 데이터를 담을 맵
                healthData.put("status", "UP"); // 서버 상태 - "UP"으로 설정 (정상 동작 중)
                healthData.put("timestamp", Instant.now().toString()); // 현재 시간 추가 - ISO 8601 형식
                healthData.put("activeConnections", currentActiveConnections.get()); // 현재 활성 연결 수
                healthData.put("totalRequests", totalRequestsReceived.get()); // 총 요청 수

                return HttpResponse.json(convertToJson(healthData)); // JSON 형태로 응답 반환
            }
        });

        // 메트릭 엔드포인트 - 서버 성능 지표 확인용 (/metrics)
        router.get("/metrics", new RouteHandler() { // 메트릭 조회용 라우트 등록
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                Map<String, Object> metricsData = metrics.getAllMetrics(); // ServerMetrics에서 모든 지표 수집
                metricsData.put("totalRequestsReceived", totalRequestsReceived.get()); // 기존 카운터 값들도 추가
                metricsData.put("totalRequestsProcessed", totalRequestsProcessed.get());
                metricsData.put("totalRequestsFailed", totalRequestsFailed.get());
                metricsData.put("currentActiveConnections", currentActiveConnections.get());

                return HttpResponse.json(convertToJson(metricsData)); // JSON 형태로 메트릭 데이터 반환
            }
        });

        // 서버 정보 엔드포인트 - 서버 구성 정보 확인용 (/info)
        router.get("/info", new RouteHandler() { // 서버 정보 조회용 라우트 등록
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                Map<String, Object> infoData = new HashMap<>(); // 서버 정보를 담을 맵 생성
                infoData.put("name", "ThreadedServer"); // 서버 이름
                infoData.put("version", "2.3"); // 서버 버전
                infoData.put("port", port); // 서버 포트
                infoData.put("threadPoolSize", threadPoolSize); // 스레드 풀 크기
                infoData.put("maxConnections", maxConnections); // 최대 연결 수
                infoData.put("backlog", backlog); // 백로그 크기
                infoData.put("startTime", metrics.getStartTime()); // 서버 시작 시간 (메트릭에서 제공)

                return HttpResponse.json(convertToJson(infoData)); // JSON 형태로 서버 정보 반환
            }
        });

        // 정적 파일 서빙 라우트 - /static/* 경로로 정적 파일 제공
        // 기존 StaticFileHandler 클래스를 활용하여 정적 파일 서빙 기능 제공
        router.get("/static/*", new StaticFileHandler("static", "/static")); // StaticFileHandler - 기존 코드에서 제공하는 정적 파일 처리 클래스

        logger.fine("기본 라우트 설정 완료: /health, /metrics, /info, /static/*"); // 설정된 기본 라우트들을 로그에 기록
    }

    /**
     * 서버 시작 (기존 메서드 확장)
     *
     * 개선된 시작 과정:
     * 1. ServerSocket 생성 및 포트 바인딩 (기존 로직 유지)
     * 2. 고급 스레드풀 관리자 초기화 (ThreadPoolManager 활용)
     * 3. 요청 처리기 생성 (기존 로직 유지)
     * 4. 메인 루프 시작 (개선된 연결 처리)
     *
     * @throws IOException 서버 소켓 생성 실패 또는 포트 바인딩 실패
     * @throws IllegalStateException 서버가 이미 실행 중인 경우
     */
    public void start() throws IOException { // public 메서드 - 서버 시작, throws IOException - 체크 예외 선언 (기존 시그니처 유지)
        // 중복 시작 방지 - AtomicBoolean의 compareAndSet으로 원자적 상태 변경 (기존 로직 유지)
        if (!running.compareAndSet(false, true)) { // AtomicBoolean.compareAndSet() - 예상값과 같으면 새 값으로 변경, 원자적 연산
            throw new IllegalStateException("서버가 이미 실행 중입니다"); // IllegalStateException - 잘못된 상태 예외
        }

        try { // try-catch 블록 - 서버 시작 중 예외 처리 (기존 구조 유지)
            // 1. ServerSocket 생성 및 설정 (기존 로직 완전 유지)
            serverSocket = new ServerSocket(); // new ServerSocket() - 기본 생성자로 서버 소켓 생성

            // 주소 재사용 허용 - 서버 재시작 시 "Address already in use" 오류 방지
            serverSocket.setReuseAddress(true); // ServerSocket.setReuseAddress() - 주소 재사용 옵션 활성화

            // 포트 바인딩 - 지정된 포트에서 클라이언트 연결 대기
            serverSocket.bind(new InetSocketAddress(port), backlog); // ServerSocket.bind() - 소켓을 주소에 바인딩, InetSocketAddress - IP 주소와 포트 조합

            // 2. 고급 스레드풀 관리자 초기화 (ThreadPoolManager 활용)
            // 기존의 단순한 Executors.newFixedThreadPool 대신 고급 관리자 사용
            this.threadPoolManager = new ThreadPoolManager( // ThreadPoolManager 생성 - 기존 코드에서 제공하는 고급 스레드 풀 관리자
                    Math.max(10, threadPoolSize / 4), // 코어 스레드 수 - 스레드 풀 크기의 1/4 또는 최소 10개
                    threadPoolSize, // 최대 스레드 수 - 생성자에서 받은 스레드 풀 크기
                    60L, // 유지 시간 - 60초간 유휴 상태일 때 스레드 제거
                    TimeUnit.SECONDS, // 시간 단위 - 초 단위
                    CONNECTION_QUEUE_CAPACITY // 큐 용량 - 대기 중인 작업들을 저장할 큐 크기
            );

            // 3. 요청 처리기 생성 (기존 로직 유지)
            this.requestProcessor = new ThreadedRequestProcessor(); // new ThreadedRequestProcessor() - 요청 처리기 인스턴스 생성

            // 4. 메트릭 시작 시간 기록
            metrics.recordServerStart(); // 서버 시작 시간을 메트릭에 기록

            // 서버 시작 로그 (기존 로직 유지하되 추가 정보 포함)
            logger.info(String.format("ThreadedServer가 포트 %d에서 시작되었습니다 (스레드풀: %d, 최대연결: %d)", // 서버 시작 정보 로그
                    port, threadPoolSize, maxConnections)); // 포트, 스레드풀 크기, 최대 연결 수 정보

            // 5. 메인 서버 루프 시작 (개선된 연결 처리)
            runEnhancedServerLoop(); // 개선된 서버 루프 실행 - 기존 runServerLoop()을 확장

        } catch (Exception e) { // Exception - 모든 예외 처리 (기존 로직 유지)
            // 시작 실패 시 상태 복원 - 다음 시작 시도를 위해
            running.set(false); // AtomicBoolean.set() - 값을 false로 설정

            // 리소스 정리 - 메모리 누수 방지
            cleanup(); // cleanup() - 리소스 정리 메서드 호출

            // 원본 예외를 포장하여 재던짐 - 호출자에게 구체적인 실패 원인 전달
            throw new IOException("서버 시작 실패", e); // IOException - 원본 예외를 원인으로 포함
        }
    }

    /**
     * 개선된 메인 서버 루프
     * 기존 runServerLoop()을 확장하여 연결 제한, 백프레셔 처리, 메트릭 수집 등을 추가
     *
     * 개선된 처리 과정:
     * 1. 연결 수 제한 확인 (Semaphore 활용)
     * 2. 클라이언트 연결 수락 (기존 로직 유지)
     * 3. 연결 추적 및 메트릭 수집
     * 4. 고급 스레드 풀 관리자를 통한 요청 처리
     * 5. 완료 시 정리 작업
     */
    private void runEnhancedServerLoop() { // private 메서드 - 개선된 메인 서버 루프
        // 서버가 실행 중인 동안 계속 반복 (기존 조건 유지)
        while (running.get()) { // while 루프 - running.get()이 true인 동안 계속 실행
            Socket clientSocket = null; // 클라이언트 소켓 변수 선언 - 스코프 확장을 위해 try 밖에서 선언

            try { // try-catch 블록 - 연결 수락 중 예외 처리
                // 1. 연결 수 제한 확인 - 백프레셔 처리
                // tryAcquire()로 비차단적으로 permit 획득 시도
                if (!connectionSemaphore.tryAcquire(100, TimeUnit.MILLISECONDS)) { // Semaphore.tryAcquire() - 지정된 시간 동안 permit 획득 시도
                    continue; // permit 획득 실패 시 다음 반복으로 건너뜀 - 연결 한도 초과 시 대기
                }

                // 2. 클라이언트 연결 수락 - 블로킹 호출 (기존 로직 유지)
                // 클라이언트가 연결할 때까지 이 지점에서 대기
                clientSocket = serverSocket.accept(); // ServerSocket.accept() - 클라이언트 연결 대기 및 수락, 블로킹 메서드

                // 3. 연결 추적 및 메트릭 업데이트
                activeConnections.add(clientSocket); // Set.add() - 활성 연결 목록에 추가
                totalRequestsReceived.incrementAndGet(); // AtomicLong.incrementAndGet() - 원자적으로 1 증가 후 값 반환 (기존 로직 유지)
                currentActiveConnections.incrementAndGet(); // 현재 활성 연결 수 증가 (기존 로직 유지)
                metrics.recordConnection(); // 새로운 메트릭 시스템에 연결 기록

                // 4. 클라이언트 소켓 설정 - 타임아웃과 성능 최적화 (기존 로직 유지)
                configureClientSocket(clientSocket); // configureClientSocket() - 클라이언트 소켓 옵션 설정

                // 5. 고급 스레드풀 관리자를 통한 요청 처리
                // 기존의 threadPool.submit() 대신 ThreadPoolManager 활용
                Socket finalClientSocket = clientSocket; // final 변수 - 람다에서 사용하기 위해 final로 선언
                CompletableFuture<Void> requestFuture = CompletableFuture.runAsync(() -> { // CompletableFuture.runAsync() - 비동기 작업 실행
                    handleClientConnection(finalClientSocket); // 클라이언트 연결 처리 메서드 호출
                }, task -> threadPoolManager.submit(task)); // ThreadPoolManager를 Executor로 사용

                // 6. 완료 시 정리 작업 - 비동기로 처리
                requestFuture.whenComplete((result, throwable) -> { // CompletableFuture.whenComplete() - 작업 완료 시 콜백 실행
                    // 연결 정리
                    activeConnections.remove(finalClientSocket); // Set.remove() - 활성 연결 목록에서 제거
                    currentActiveConnections.decrementAndGet(); // AtomicLong.decrementAndGet() - 원자적으로 1 감소 후 값 반환
                    connectionSemaphore.release(); // Semaphore.release() - permit 반환

                    // 에러 로깅
                    if (throwable != null) { // 예외가 발생한 경우
                        logger.log(Level.WARNING, "클라이언트 연결 처리 중 오류", throwable); // 경고 레벨로 오류 로그
                        totalRequestsFailed.incrementAndGet(); // 실패 요청 수 증가 (기존 로직 유지)
                        metrics.recordError(); // 새로운 메트릭 시스템에 오류 기록
                    } else {
                        totalRequestsProcessed.incrementAndGet(); // 성공 처리 시 완료 요청 수 증가 (기존 로직 유지)
                    }
                });

            } catch (SocketException e) { // SocketException - 소켓 관련 예외 (기존 처리 로직 유지)
                // ServerSocket이 닫힌 경우 (정상적인 종료)
                if (running.get()) { // running.get() - 현재 실행 상태 확인
                    // 서버가 실행 중인데 SocketException이 발생하면 비정상 상황
                    logger.log(Level.WARNING, "서버 소켓 예외 발생", e); // WARNING 레벨로 소켓 예외 로그
                }
                // 연결 수락에 실패했으므로 permit 반환
                if (clientSocket == null) { // 소켓 생성 전에 예외가 발생한 경우
                    connectionSemaphore.release(); // permit 반환
                }
                break; // break 문 - while 루프 종료

            } catch (IOException e) { // IOException - 입출력 예외 (기존 처리 로직 유지)
                // 연결 수락 실패 - 일시적인 문제일 수 있으므로 계속 진행
                if (running.get()) { // 서버가 실행 중인 경우에만 로그
                    logger.log(Level.WARNING, "클라이언트 연결 수락 실패", e); // 연결 수락 실패 로그
                }
                // 연결 수락에 실패했으므로 permit 반환
                if (clientSocket == null) { // 소켓 생성 전에 예외가 발생한 경우
                    connectionSemaphore.release(); // permit 반환
                }

            } catch (RejectedExecutionException e) { // RejectedExecutionException - 스레드풀 작업 거부 예외 (기존 처리 로직 유지)
                // 스레드풀이 포화 상태 - 백프레셔 처리
                logger.log(Level.WARNING, "스레드풀 포화로 요청 거부", e); // 스레드풀 포화 경고 로그
                totalRequestsFailed.incrementAndGet(); // AtomicLong.incrementAndGet() - 실패 요청 수 증가

                // 연결 정리
                if (clientSocket != null) { // 소켓이 생성된 경우
                    activeConnections.remove(clientSocket); // 활성 연결에서 제거
                    currentActiveConnections.decrementAndGet(); // 활성 연결 수 감소
                    try {
                        clientSocket.close(); // 소켓 닫기
                    } catch (IOException ioException) {
                        // 소켓 닫기 실패는 무시
                    }
                }
                connectionSemaphore.release(); // permit 반환

            } catch (InterruptedException e) { // InterruptedException - 대기 중 인터럽트 발생
                // 스레드 인터럽트 발생 시 정리
                Thread.currentThread().interrupt(); // 인터럽트 상태 복원
                if (clientSocket == null) { // 소켓 생성 전에 인터럽트된 경우
                    connectionSemaphore.release(); // permit 반환
                }
                break; // 루프 종료
            }
        }
    }

    /**
     * 클라이언트 연결 처리 - 기존 RequestHandler.run() 로직을 메서드로 분리
     * 이 메서드는 ThreadPoolManager의 스레드에서 실행됨
     *
     * @param clientSocket 처리할 클라이언트 소켓
     */
    private void handleClientConnection(Socket clientSocket) { // private 메서드 - 개별 클라이언트 연결 처리
        // 요청 처리 시작 시간 기록 - 성능 측정용 (기존 로직 유지)
        long startTime = System.currentTimeMillis(); // System.currentTimeMillis() - 현재 시간(밀리초) 조회

        // try-with-resources로 자동 리소스 관리 (기존 로직 유지)
        // 예외 발생 여부와 관계없이 소켓이 확실히 닫힘
        try (Socket socket = clientSocket; // try-with-resources - 자동 리소스 관리, Socket은 AutoCloseable 구현
             InputStream inputStream = socket.getInputStream(); // Socket.getInputStream() - 소켓의 입력 스트림 조회
             OutputStream outputStream = socket.getOutputStream()) { // Socket.getOutputStream() - 소켓의 출력 스트림 조회

            // 1. HTTP 요청 파싱 - 블로킹 I/O (기존 로직 유지)
            HttpRequest request = requestProcessor.parseRequest(inputStream); // HttpRequestParser를 통한 요청 파싱

            // 2. 라우팅 시스템을 통한 요청 처리 (개선된 부분)
            // 기존의 단순한 processRequest() 대신 Router를 통한 정교한 라우팅
            HttpResponse response;
            try {
                response = router.route(request); // Router.route() - 요청을 적절한 핸들러로 라우팅
            } catch (Exception routingException) { // 라우팅 중 예외 발생 시
                logger.log(Level.WARNING, "라우팅 처리 중 오류", routingException); // 라우팅 오류 로그
                response = HttpResponse.serverError("라우팅 처리 중 오류가 발생했습니다"); // 500 에러 응답 생성
            }

            // 3. HTTP 응답 전송 - 블로킹 I/O (기존 로직 유지)
            requestProcessor.sendResponse(outputStream, response); // sendHttpResponse() - 출력 스트림으로 응답 전송

            // 4. 성공 처리 메트릭 기록
            long processingTime = System.currentTimeMillis() - startTime; // 처리 시간 계산 - 종료 시간 - 시작 시간
            metrics.recordRequest(processingTime); // 새로운 메트릭 시스템에 요청 처리 시간 기록

            // 처리 시간 로그 (디버그 레벨) (기존 로직 유지)
            logger.fine(String.format("요청 처리 완료: %s %s (%dms)", // Logger.fine() - 상세 로그 레벨
                    request.getMethod(), request.getPath(), processingTime)); // 메서드, 경로, 처리 시간 로그

        } catch (IOException e) { // IOException - 입출력 예외 처리 (기존 로직 유지)
            // I/O 오류 처리 - 네트워크 문제나 클라이언트 연결 끊김
            logger.log(Level.WARNING, "요청 처리 중 I/O 오류", e); // WARNING 레벨로 I/O 오류 로그

        } catch (Exception e) { // Exception - 모든 예외 처리 (기존 로직 유지)
            // 기타 예외 처리 - 파싱 오류나 애플리케이션 오류
            logger.log(Level.SEVERE, "요청 처리 중 예상치 못한 오류", e); // SEVERE 레벨로 심각한 오류 로그

            // 500 에러 응답 시도 - 클라이언트에게 에러 상황 알림 (기존 로직 유지)
            try (OutputStream outputStream = clientSocket.getOutputStream()) { // try-with-resources - 출력 스트림 자동 관리
                HttpResponse errorResponse = HttpResponse.serverError("내부 서버 오류가 발생했습니다"); // HttpResponse.serverError() - 500 에러 응답 생성
                requestProcessor.sendResponse(outputStream, errorResponse); // 에러 응답 전송 시도
            } catch (IOException ioException) { // IOException - 에러 응답 전송 실패
                // 에러 응답 전송도 실패한 경우 - 로그만 남기고 포기
                logger.log(Level.SEVERE, "에러 응답 전송 실패", ioException); // 에러 응답 전송 실패 로그
            }
        }
    }

    /**
     * 클라이언트 소켓 설정 (기존 메서드 완전 유지)
     * 성능 최적화와 안정성을 위한 소켓 옵션 설정
     *
     * @param clientSocket 설정할 클라이언트 소켓
     * @throws SocketException 소켓 설정 실패 시
     */
    private void configureClientSocket(Socket clientSocket) throws SocketException { // private 메서드 - 클라이언트 소켓 설정 (기존 시그니처 완전 유지)
        // 소켓 타임아웃 설정 - 무한 대기 방지
        clientSocket.setSoTimeout(SOCKET_TIMEOUT); // Socket.setSoTimeout() - 읽기 작업 타임아웃 설정 (밀리초)

        // TCP_NODELAY 활성화 - Nagle 알고리즘 비활성화로 응답성 향상
        clientSocket.setTcpNoDelay(true); // Socket.setTcpNoDelay() - TCP_NODELAY 옵션 설정, 작은 패킷 즉시 전송

        // Keep-Alive 활성화 - 연결 유지로 성능 향상
        clientSocket.setKeepAlive(true); // Socket.setKeepAlive() - TCP Keep-Alive 옵션 활성화, 연결 상태 주기적 확인
    }

    /**
     * 서버 중지 (기존 메서드 확장)
     *
     * 개선된 중지 과정:
     * 1. 새로운 연결 수락 중지 (기존 로직 유지)
     * 2. 활성 연결들의 완료 대기 (추가된 기능)
     * 3. 고급 스레드풀 관리자 종료 (ThreadPoolManager 활용)
     * 4. 리소스 정리 (기존 로직 확장)
     */
    public void stop() { // public 메서드 - 서버 중지 (기존 시그니처 유지)
        // 이미 중지된 경우 무시 - 중복 호출 방지 (기존 로직 유지)
        if (!running.compareAndSet(true, false)) { // AtomicBoolean.compareAndSet() - true에서 false로 원자적 변경 시도
            logger.info("서버가 이미 중지되었거나 실행 중이 아닙니다"); // 이미 중지된 상태 로그
            return; // early return - 메서드 즉시 종료
        }

        logger.info("서버 중지를 시작합니다..."); // 서버 중지 시작 로그

        try { // try-catch 블록 - 중지 과정 중 예외 처리 (기존 구조 유지)
            // 1. ServerSocket 닫기 - 새로운 연결 수락 중지 (기존 로직 유지)
            if (serverSocket != null && !serverSocket.isClosed()) { // null 체크와 닫힘 상태 확인
                serverSocket.close(); // ServerSocket.close() - 서버 소켓 닫기
            }

            // 2. 활성 연결들의 완료 대기 (추가된 기능)
            waitForActiveConnections(30); // 최대 30초간 활성 연결 완료 대기

            // 3. 고급 스레드풀 관리자 우아한 종료 (ThreadPoolManager 활용)
            if (threadPoolManager != null) { // ThreadPoolManager가 초기화된 경우
                boolean terminated = threadPoolManager.shutdown(30); // ThreadPoolManager.shutdown() - 우아한 종료 시도
                if (!terminated) { // 정상 종료되지 않은 경우
                    logger.warning("스레드풀 관리자가 시간 내에 종료되지 않았습니다"); // 종료 실패 경고
                }
            }

            // 4. 최종 통계 로그 출력 (기존 로직 유지)
            logFinalStatistics(); // logFinalStatistics() - 최종 서버 통계 로그 출력

            logger.info("ThreadedServer가 성공적으로 중지되었습니다"); // 서버 중지 완료 로그

        } catch (Exception e) { // Exception - 중지 과정 중 예외 처리
            logger.log(Level.SEVERE, "서버 종료 중 오류 발생", e); // 심각한 오류 레벨로 로그
        } finally { // finally 블록 - 예외 발생 여부와 관계없이 실행
            // 5. 최종 리소스 정리 - 예외 발생 여부와 관계없이 실행
            cleanup(); // cleanup() - 최종 리소스 정리
            metrics.recordServerStop(); // 서버 중지 시간 기록
        }
    }

    /**
     * 활성 연결들의 완료 대기 (추가된 메서드)
     * 그레이스풀 셧다운을 위해 현재 처리 중인 연결들이 완료될 때까지 대기
     *
     * @param timeoutSeconds 최대 대기 시간 (초)
     */
    private void waitForActiveConnections(int timeoutSeconds) { // private 메서드 - 활성 연결 완료 대기
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L; // 데드라인 계산 - 현재 시간 + 타임아웃

        logger.info(String.format("활성 연결 %d개의 완료를 최대 %d초간 대기합니다",
                activeConnections.size(), timeoutSeconds)); // 대기 시작 로그

        // 데드라인까지 활성 연결이 모두 완료되기를 대기
        while (!activeConnections.isEmpty() && System.currentTimeMillis() < deadline) { // 활성 연결이 있고 데드라인 전이면 계속 대기
            try {
                Thread.sleep(100); // 100ms마다 확인 - 너무 자주 확인하지 않도록 조절
            } catch (InterruptedException e) { // 대기 중 인터럽트 발생
                Thread.currentThread().interrupt(); // 인터럽트 상태 복원
                break; // 루프 종료
            }
        }

        // 여전히 활성 연결이 남아있으면 강제 종료
        if (!activeConnections.isEmpty()) { // 타임아웃 후에도 활성 연결이 남은 경우
            logger.warning(String.format("타임아웃으로 인해 %d개의 활성 연결을 강제 종료합니다",
                    activeConnections.size())); // 강제 종료 경고 로그

            // 남은 연결들 강제 종료
            for (Socket socket : activeConnections) { // 모든 활성 연결 순회
                try {
                    socket.close(); // 소켓 강제 닫기
                } catch (IOException e) {
                    // 강제 닫기 실패는 무시 - 이미 닫혔을 수도 있음
                }
            }
            activeConnections.clear(); // 활성 연결 목록 초기화
        } else {
            logger.info("모든 활성 연결이 정상적으로 완료되었습니다"); // 정상 완료 로그
        }
    }

    /**
     * 최종 통계를 로그에 출력 (기존 메서드 확장)
     * 서버 종료 시 전체 처리 통계 요약
     */
    private void logFinalStatistics() { // private 메서드 - 최종 통계 출력 (기존 시그니처 유지)
        // 기존 통계 로그 (기존 로직 유지)
        logger.info(String.format( // Logger.info() - 정보 레벨 로그, String.format() - 문자열 템플릿
                "서버 통계 - 총 요청: %d, 처리 완료: %d, 실패: %d, 활성 연결: %d", // 통계 정보 템플릿
                totalRequestsReceived.get(), // AtomicLong.get() - 현재 값 조회, 총 요청 수
                totalRequestsProcessed.get(), // 처리 완료된 요청 수
                totalRequestsFailed.get(), // 실패한 요청 수
                currentActiveConnections.get() // 현재 활성 연결 수
        ));

        // 추가된 고급 메트릭 로그
        Map<String, Object> finalMetrics = metrics.getAllMetrics(); // 모든 메트릭 수집
        logger.info(String.format("고급 메트릭 - 평균 응답시간: %.2fms, 총 처리시간: %dms, 에러율: %.2f%%",
                finalMetrics.get("averageResponseTime"), // 평균 응답 시간
                finalMetrics.get("totalProcessingTime"), // 총 처리 시간
                finalMetrics.get("errorRate"))); // 에러율

        // ThreadPoolManager 통계 (있는 경우)
        if (threadPoolManager != null) { // ThreadPoolManager가 있는 경우
            ThreadPoolManager.ThreadPoolStatus poolStatus = threadPoolManager.getStatus(); // 스레드풀 상태 조회
            logger.info(String.format("스레드풀 통계 - 완료된 작업: %d, 거부된 작업: %d",
                    poolStatus.getCompletedTaskCount(), // 완료된 작업 수
                    poolStatus.getRejectedTaskCount())); // 거부된 작업 수
        }
    }

    /**
     * 리소스 정리 (기존 메서드 확장)
     * 메모리 누수 방지를 위한 최종 정리 작업
     */
    private void cleanup() { // private 메서드 - 리소스 정리 (기존 시그니처 유지)
        // ServerSocket 정리 (기존 로직 유지)
        if (serverSocket != null && !serverSocket.isClosed()) { // null 체크와 닫힘 상태 확인
            try { // try-catch 블록 - 소켓 닫기 중 예외 처리
                serverSocket.close(); // ServerSocket.close() - 서버 소켓 닫기
            } catch (IOException e) { // IOException - 소켓 닫기 실패
                logger.log(Level.WARNING, "서버 소켓 닫기 실패", e); // 소켓 닫기 실패 로그
            }
        }

        // ThreadPoolManager 강제 종료 (추가된 정리 작업)
        if (threadPoolManager != null) { // ThreadPoolManager가 있는 경우
            threadPoolManager.shutdownNow(); // 강제 종료
        }

        // 활성 연결들 강제 정리 (추가된 정리 작업)
        for (Socket socket : activeConnections) { // 모든 활성 연결 순회
            try {
                socket.close(); // 소켓 닫기
            } catch (IOException e) {
                // 강제 정리이므로 예외 무시
            }
        }
        activeConnections.clear(); // 활성 연결 목록 초기화

        // 요청 처리기 정리 (기존 로직 유지)
        if (requestProcessor != null) { // null 체크
            // 현재는 정리할 리소스가 없지만 확장성을 위해 준비
            requestProcessor = null; // 참조 해제 - 가비지 컬렉션 대상으로 만들기
        }
    }

    // ========== 상태 조회 메서드들 (기존 메서드들 유지) ==========

    /**
     * 서버 실행 상태 확인 (기존 메서드 유지)
     *
     * @return 서버가 실행 중이면 true
     */
    public boolean isRunning() { // public getter 메서드 - 서버 실행 상태 조회
        return running.get(); // AtomicBoolean.get() - 현재 boolean 값 반환
    }

    /**
     * 현재 서버 통계 반환 (기존 메서드 확장)
     * 모니터링과 운영에 사용
     *
     * @return 서버 통계 객체
     */
    public ServerStatistics getStatistics() { // public getter 메서드 - 서버 통계 조회
        return new ServerStatistics( // ServerStatistics 생성자 호출 - 현재 통계 값들로 객체 생성
                totalRequestsReceived.get(), // 총 요청 수
                totalRequestsProcessed.get(), // 처리 완료 수
                totalRequestsFailed.get(), // 실패 수
                currentActiveConnections.get(), // 활성 연결 수
                port, // 서버 포트
                threadPoolSize, // 스레드풀 크기
                maxConnections, // 최대 연결 수 (추가됨)
                metrics.getAllMetrics() // 고급 메트릭 (추가됨)
        );
    }

    /**
     * 라우터 반환 (추가된 메서드)
     * 외부에서 라우트를 추가할 수 있도록 라우터 접근 제공
     *
     * @return Router 인스턴스
     */
    public Router getRouter() { // public getter 메서드 - 라우터 접근
        return router; // Router 인스턴스 반환
    }

    /**
     * ThreadPoolManager 반환 (추가된 메서드)
     * 외부에서 스레드풀 상태를 모니터링할 수 있도록 접근 제공
     *
     * @return ThreadPoolManager 인스턴스
     */
    public ThreadPoolManager getThreadPoolManager() { // public getter 메서드 - 스레드풀 관리자 접근
        return threadPoolManager; // ThreadPoolManager 인스턴스 반환
    }

    /**
     * 서버 메트릭 반환 (추가된 메서드)
     * 외부에서 서버 성능 지표를 확인할 수 있도록 접근 제공
     *
     * @return ServerMetrics 인스턴스
     */
    public ServerMetrics getMetrics() { // public getter 메서드 - 메트릭 접근
        return metrics; // ServerMetrics 인스턴스 반환
    }

    // ========== 유틸리티 메서드들 ==========

    /**
     * Map을 간단한 JSON 문자열로 변환하는 유틸리티 메서드
     * 기본 엔드포인트에서 JSON 응답을 생성하기 위해 사용
     *
     * @param map 변환할 Map 객체
     * @return JSON 형태의 문자열
     */
    private String convertToJson(Map<String, Object> map) { // private 메서드 - JSON 변환 유틸리티
        if (map == null || map.isEmpty()) { // null 체크와 빈 맵 확인
            return "{}"; // 빈 JSON 객체 반환
        }

        StringBuilder json = new StringBuilder(); // StringBuilder - 효율적인 문자열 조합
        json.append("{"); // JSON 객체 시작

        boolean first = true; // 첫 번째 요소 플래그
        for (Map.Entry<String, Object> entry : map.entrySet()) { // Map의 모든 엔트리 순회
            if (!first) { // 첫 번째가 아닌 경우
                json.append(","); // 쉼표 추가
            }
            first = false; // 첫 번째 플래그 해제

            // 키 추가
            json.append("\"").append(entry.getKey()).append("\":"); // "key": 형태로 키 추가

            // 값 추가 (타입별 처리)
            Object value = entry.getValue(); // 값 추출
            if (value instanceof String) { // 문자열인 경우
                json.append("\"").append(value).append("\""); // 따옴표로 감싸기
            } else if (value instanceof Number || value instanceof Boolean) { // 숫자나 불린인 경우
                json.append(value); // 그대로 추가
            } else if (value == null) { // null인 경우
                json.append("null"); // JSON null 추가
            } else { // 기타 객체인 경우
                json.append("\"").append(value.toString()).append("\""); // toString()으로 변환 후 따옴표로 감싸기
            }
        }

        json.append("}"); // JSON 객체 종료
        return json.toString(); // 완성된 JSON 문자열 반환
    }

    // ========== 내부 클래스들 ==========

    /**
     * 서버 통계 정보를 담는 불변 클래스 (기존 클래스 확장)
     * 모니터링과 운영에 사용되는 메트릭들
     */
    public static class ServerStatistics { // public static 내부 클래스 - 외부에서 독립적으로 사용 가능
        // 기존 필드들 (모든 필드를 final로 선언하여 불변성 보장)
        private final long totalRequestsReceived;    // final long - 불변 필드, 받은 총 요청 수
        private final long totalRequestsProcessed;   // final long - 불변 필드, 처리 완료된 요청 수
        private final long totalRequestsFailed;      // final long - 불변 필드, 실패한 요청 수
        private final long currentActiveConnections; // final long - 불변 필드, 현재 활성 연결 수
        private final int port;                      // final int - 불변 필드, 서버 포트
        private final int threadPoolSize;            // final int - 불변 필드, 스레드풀 크기

        // 추가된 필드들
        private final int maxConnections;            // final int - 불변 필드, 최대 동시 연결 수
        private final Map<String, Object> advancedMetrics; // final Map - 불변 필드, 고급 메트릭 정보

        /**
         * ServerStatistics 생성자 (기존 생성자 확장)
         * 모든 통계 값을 한 번에 설정
         */
        public ServerStatistics(long totalRequestsReceived, long totalRequestsProcessed, // public 생성자 - 모든 통계 값을 매개변수로 받음
                                long totalRequestsFailed, long currentActiveConnections,
                                int port, int threadPoolSize, int maxConnections, Map<String, Object> advancedMetrics) {
            this.totalRequestsReceived = totalRequestsReceived; // this.totalRequestsReceived - 현재 객체의 필드에 매개변수 값 할당
            this.totalRequestsProcessed = totalRequestsProcessed; // this.totalRequestsProcessed - 처리 완료 수 할당
            this.totalRequestsFailed = totalRequestsFailed; // this.totalRequestsFailed - 실패 수 할당
            this.currentActiveConnections = currentActiveConnections; // this.currentActiveConnections - 활성 연결 수 할당
            this.port = port; // this.port - 포트 번호 할당
            this.threadPoolSize = threadPoolSize; // this.threadPoolSize - 스레드풀 크기 할당
            this.maxConnections = maxConnections; // this.maxConnections - 최대 연결 수 할당 (추가됨)
            this.advancedMetrics = new HashMap<>(advancedMetrics); // 방어적 복사로 고급 메트릭 저장 (추가됨)
        }

        // 기존 Getter 메서드들 - 불변 객체이므로 setter는 없음
        public long getTotalRequestsReceived() { return totalRequestsReceived; } // public getter - 총 요청 수 반환
        public long getTotalRequestsProcessed() { return totalRequestsProcessed; } // public getter - 처리 완료 수 반환
        public long getTotalRequestsFailed() { return totalRequestsFailed; } // public getter - 실패 수 반환
        public long getCurrentActiveConnections() { return currentActiveConnections; } // public getter - 활성 연결 수 반환
        public int getPort() { return port; } // public getter - 포트 번호 반환
        public int getThreadPoolSize() { return threadPoolSize; } // public getter - 스레드풀 크기 반환

        // 추가된 Getter 메서드들
        public int getMaxConnections() { return maxConnections; } // public getter - 최대 연결 수 반환
        public Map<String, Object> getAdvancedMetrics() { return new HashMap<>(advancedMetrics); } // public getter - 고급 메트릭 반환 (방어적 복사)

        /**
         * 성공률 계산 (기존 메서드 유지)
         *
         * @return 성공률 (0.0 ~ 1.0)
         */
        public double getSuccessRate() { // public 메서드 - 성공률 계산
            return totalRequestsReceived > 0 ? // 삼항 연산자 - 총 요청이 0보다 큰 경우
                    (double) totalRequestsProcessed / totalRequestsReceived : 0.0; // 형변환 - long을 double로 변환하여 나눗셈, 성공률 계산
        }

        /**
         * 실패율 계산 (기존 메서드 유지)
         *
         * @return 실패율 (0.0 ~ 1.0)
         */
        public double getFailureRate() { // public 메서드 - 실패율 계산
            return totalRequestsReceived > 0 ? // 삼항 연산자 - 총 요청이 0보다 큰 경우
                    (double) totalRequestsFailed / totalRequestsReceived : 0.0; // 형변환 - long을 double로 변환하여 나눗셈, 실패율 계산
        }

        /**
         * 연결 사용률 계산 (추가된 메서드)
         *
         * @return 연결 사용률 (0.0 ~ 1.0)
         */
        public double getConnectionUsageRate() { // public 메서드 - 연결 사용률 계산
            return maxConnections > 0 ? // 최대 연결 수가 0보다 큰 경우
                    (double) currentActiveConnections / maxConnections : 0.0; // 현재 활성 연결 / 최대 연결 수
        }

        /**
         * 통계 정보를 문자열로 표현 (기존 메서드 확장)
         * 모니터링 대시보드나 로그에서 사용
         */
        @Override // 어노테이션 - Object.toString() 메서드 재정의
        public String toString() { // public 메서드 - 객체의 문자열 표현
            return String.format( // String.format() - 문자열 템플릿 사용
                    "ServerStatistics{받은요청=%d, 처리완료=%d, 실패=%d, 활성연결=%d, " + // 기존 통계 정보 템플릿
                            "포트=%d, 스레드풀=%d, 최대연결=%d, 성공률=%.2f%%, 실패율=%.2f%%, 연결사용률=%.2f%%}", // 포트, 스레드풀, 추가 정보 템플릿
                    totalRequestsReceived, totalRequestsProcessed, totalRequestsFailed, // 요청 관련 통계들
                    currentActiveConnections, port, threadPoolSize, maxConnections, // 연결, 포트, 스레드풀, 최대 연결 정보
                    getSuccessRate() * 100, getFailureRate() * 100, getConnectionUsageRate() * 100 // 각종 비율을 백분율로 변환
            );
        }
    }

    /**
     * 서버 메트릭 수집 클래스 (추가된 클래스)
     * 서버 성능 지표를 수집하고 관리하는 클래스
     */
    public static class ServerMetrics { // public static 내부 클래스 - 외부에서 독립적으로 사용 가능
        // 메트릭 수집용 원자적 변수들
        private final AtomicLong totalRequestCount = new AtomicLong(0); // 총 요청 수 카운터
        private final AtomicLong totalProcessingTime = new AtomicLong(0); // 총 처리 시간 누적 (밀리초)
        private final AtomicLong totalErrors = new AtomicLong(0); // 총 에러 수 카운터
        private final AtomicLong totalConnections = new AtomicLong(0); // 총 연결 수 카운터

        // 서버 생명주기 시간
        private volatile long serverStartTime = 0; // 서버 시작 시간
        private volatile long serverStopTime = 0; // 서버 중지 시간

        /**
         * 서버 시작 시간 기록
         */
        public void recordServerStart() { // public 메서드 - 서버 시작 시간 기록
            this.serverStartTime = System.currentTimeMillis(); // 현재 시간을 시작 시간으로 기록
            logger.fine("서버 시작 시간 기록됨: " + new Date(serverStartTime)); // 시작 시간 로그
        }

        /**
         * 서버 중지 시간 기록
         */
        public void recordServerStop() { // public 메서드 - 서버 중지 시간 기록
            this.serverStopTime = System.currentTimeMillis(); // 현재 시간을 중지 시간으로 기록
            logger.fine("서버 중지 시간 기록됨: " + new Date(serverStopTime)); // 중지 시간 로그
        }

        /**
         * 요청 처리 기록
         *
         * @param processingTimeMs 처리 시간 (밀리초)
         */
        public void recordRequest(long processingTimeMs) { // public 메서드 - 요청 처리 기록
            totalRequestCount.incrementAndGet(); // 총 요청 수 증가
            totalProcessingTime.addAndGet(processingTimeMs); // 처리 시간 누적
        }

        /**
         * 에러 발생 기록
         */
        public void recordError() { // public 메서드 - 에러 발생 기록
            totalErrors.incrementAndGet(); // 에러 수 증가
        }

        /**
         * 연결 발생 기록
         */
        public void recordConnection() { // public 메서드 - 연결 발생 기록
            totalConnections.incrementAndGet(); // 연결 수 증가
        }

        /**
         * 모든 메트릭 반환
         *
         * @return 모든 메트릭을 담은 Map
         */
        public Map<String, Object> getAllMetrics() { // public 메서드 - 모든 메트릭 조회
            Map<String, Object> metrics = new HashMap<>(); // 메트릭을 담을 맵 생성

            long requestCount = totalRequestCount.get(); // 현재 요청 수 조회
            long processingTime = totalProcessingTime.get(); // 현재 총 처리 시간 조회

            // 기본 메트릭들
            metrics.put("totalRequests", requestCount); // 총 요청 수
            metrics.put("totalErrors", totalErrors.get()); // 총 에러 수
            metrics.put("totalConnections", totalConnections.get()); // 총 연결 수
            metrics.put("totalProcessingTime", processingTime); // 총 처리 시간

            // 계산된 메트릭들
            metrics.put("averageResponseTime", requestCount > 0 ? // 평균 응답 시간 계산
                    (double) processingTime / requestCount : 0.0); // 총 처리 시간 / 요청 수
            metrics.put("errorRate", requestCount > 0 ? // 에러율 계산
                    (double) totalErrors.get() / requestCount * 100.0 : 0.0); // 에러 수 / 요청 수 * 100

            // 서버 생명주기 메트릭들
            metrics.put("serverStartTime", serverStartTime); // 서버 시작 시간
            if (serverStopTime > 0) { // 서버가 중지된 경우
                metrics.put("serverStopTime", serverStopTime); // 서버 중지 시간
                metrics.put("totalUptime", serverStopTime - serverStartTime); // 총 업타임
            } else if (serverStartTime > 0) { // 서버가 실행 중인 경우
                long currentUptime = System.currentTimeMillis() - serverStartTime; // 현재 업타임 계산
                metrics.put("currentUptime", currentUptime); // 현재 업타임
            }

            return metrics; // 완성된 메트릭 맵 반환
        }

        /**
         * 서버 시작 시간 반환
         *
         * @return 서버 시작 시간 (밀리초)
         */
        public long getStartTime() { // public getter 메서드 - 시작 시간 조회
            return serverStartTime; // 서버 시작 시간 반환
        }

        /**
         * 현재 업타임 반환
         *
         * @return 현재 업타임 (밀리초)
         */
        public long getCurrentUptime() { // public 메서드 - 현재 업타임 계산
            if (serverStartTime == 0) { // 서버가 시작되지 않은 경우
                return 0; // 0 반환
            }

            if (serverStopTime > 0) { // 서버가 중지된 경우
                return serverStopTime - serverStartTime; // 중지 시간 - 시작 시간
            } else { // 서버가 실행 중인 경우
                return System.currentTimeMillis() - serverStartTime; // 현재 시간 - 시작 시간
            }
        }

        /**
         * 평균 응답 시간 반환
         *
         * @return 평균 응답 시간 (밀리초)
         */
        public double getAverageResponseTime() { // public 메서드 - 평균 응답 시간 계산
            long requestCount = totalRequestCount.get(); // 총 요청 수
            return requestCount > 0 ? // 요청이 있는 경우
                    (double) totalProcessingTime.get() / requestCount : 0.0; // 총 처리 시간 / 요청 수
        }

        /**
         * 에러율 반환
         *
         * @return 에러율 (백분율)
         */
        public double getErrorRate() { // public 메서드 - 에러율 계산
            long requestCount = totalRequestCount.get(); // 총 요청 수
            return requestCount > 0 ? // 요청이 있는 경우
                    (double) totalErrors.get() / requestCount * 100.0 : 0.0; // 에러 수 / 요청 수 * 100
        }
    }
}