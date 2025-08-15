package com.serverarch.hybrid;

// === 기본 Java 라이브러리 Import ===
// java.net.*: 네트워크 프로그래밍을 위한 클래스들 (Socket, ServerSocket, InetSocketAddress 등)
import java.net.*;
// java.io.*: 입출력 스트림을 위한 클래스들 (InputStream, OutputStream, IOException 등)
import java.io.*;
// java.util.*: 컬렉션 프레임워크와 유틸리티 클래스들 (Map, List, Set, HashMap 등)
import java.util.*;
// java.time.*: 날짜와 시간 처리를 위한 클래스들 (Instant, Duration, LocalDateTime 등)
import java.time.*;

// === Java 동시성 라이브러리 Import ===
// java.util.concurrent.*: 동시성 처리를 위한 클래스들
import java.util.concurrent.*;
// ExecutorService: 비동기 작업 실행을 위한 인터페이스, 스레드풀 관리 기능 제공
// CompletableFuture: 비동기 프로그래밍을 위한 Future의 구현체, 콜백 체이닝과 조합 기능 제공
// Executors: ExecutorService 생성을 위한 팩토리 클래스
// ScheduledExecutorService: 지연 및 주기적 작업 실행을 위한 인터페이스
// ThreadFactory: 새로운 스레드 생성을 위한 팩토리 인터페이스
// Semaphore: 동시 접근 수를 제한하는 동기화 도구
// TimeUnit: 시간 단위를 나타내는 열거형 (SECONDS, MILLISECONDS 등)
// TimeoutException: 작업 타임아웃 시 발생하는 예외 클래스
// RejectedExecutionException: 스레드풀이 작업을 거부할 때 발생하는 예외

// === Java 원자적 연산 라이브러리 Import ===
// java.util.concurrent.atomic.*: 멀티스레드 환경에서 안전한 원자적 연산을 위한 클래스들
import java.util.concurrent.atomic.*;
// AtomicBoolean: 원자적 boolean 연산을 지원하는 클래스, compareAndSet() 등의 메서드 제공
// AtomicLong: 원자적 long 연산을 지원하는 클래스, incrementAndGet(), addAndGet() 등의 메서드 제공
// AtomicInteger: 원자적 int 연산을 지원하는 클래스

// === Java 로깅 라이브러리 Import ===
// java.util.logging.*: Java 내장 로깅 API
import java.util.logging.*;
// Logger: 로그 메시지를 기록하는 클래스, 다양한 레벨의 로그 메서드 제공
// Level: 로그 레벨을 정의하는 클래스 (SEVERE, WARNING, INFO, FINE 등)

// === 공통 모듈 Import ===
// HTTP 처리를 위한 공통 클래스들 - Traditional 서버와 동일한 HTTP 코어 사용
import com.serverarch.common.http.*;
// HttpRequest: HTTP 요청 정보를 담는 클래스 (메서드, 경로, 헤더, 바디 등)
// HttpResponse: HTTP 응답 정보를 담는 클래스 (상태코드, 헤더, 바디 등)
// HttpHeaders: HTTP 헤더 정보를 관리하는 클래스
// HttpMethod: HTTP 메서드를 나타내는 열거형 (GET, POST, PUT, DELETE 등)
// HttpStatus: HTTP 상태 코드를 나타내는 열거형 (200, 404, 500 등)

// 라우팅 시스템 - Traditional 서버와 동일한 라우팅 사용
import com.serverarch.traditional.HttpRequest;
import com.serverarch.traditional.HttpResponse;
import com.serverarch.traditional.routing.*;
// Router: URL 패턴과 핸들러를 매핑하는 라우팅 클래스
// RouteHandler: 요청을 처리하는 핸들러 인터페이스
// Middleware: 횡단 관심사를 처리하는 미들웨어 인터페이스
// MiddlewareChain: 미들웨어들을 체인으로 연결하여 순차적으로 실행하는 클래스

/**
 * Hybrid 서버 - AsyncContext 기반 컨텍스트 스위칭 서버
 *
 * 이 서버는 Traditional 서버와 EventLoop 서버의 중간 형태로,
 * 다음과 같은 핵심 특징을 가집니다:
 *
 * 핵심 아키텍처:
 * 1. AsyncContext 활용: I/O 대기 시 스레드를 해제하고 완료 시 다른 스레드에서 재개
 * 2. CompletableFuture 체이닝: 비동기 작업들을 연결하여 논블로킹 처리
 * 3. 적응적 스레드풀: CPU 집약적 작업과 I/O 집약적 작업을 구분하여 처리
 * 4. 백프레셔 처리: 시스템 과부하 시 작업 거부 및 타임아웃 처리
 *
 * Traditional 서버 대비 개선점:
 * - 스레드 효율성: I/O 대기 중 스레드 재활용으로 동시 처리 수 증가
 * - 메모리 효율성: 스레드당 메모리 사용량 감소
 * - 응답성: 논블로킹 I/O로 전체적인 응답 시간 개선
 *
 * EventLoop 서버 대비 장점:
 * - 구현 복잡도: 완전한 논블로킹보다 구현이 단순
 * - 기존 코드 호환성: 블로킹 라이브러리들을 부분적으로 활용 가능
 * - 디버깅 용이성: 스택 추적이 EventLoop보다 명확
 *
 * 적용 시나리오:
 * - 중간 수준의 동시성이 필요한 애플리케이션
 * - I/O 집약적 작업이 많은 웹 서비스
 * - 기존 블로킹 코드를 점진적으로 개선하려는 경우
 */
public class HybridServer { // public 클래스 선언 - 다른 패키지에서 접근 가능한 하이브리드 HTTP 서버

    // === 로깅 시스템 ===
    // static final: 클래스 레벨 상수로 선언하여 모든 인스턴스가 공유
    // Logger.getLogger(): 클래스 이름을 기반으로 로거 인스턴스 생성
    // 로거는 서버의 동작 상태, 에러, 성능 정보 등을 기록하는 데 사용
    private static final Logger logger = Logger.getLogger(HybridServer.class.getName());

    // === 서버 기본 설정 ===
    // final: 서버 시작 후 변경되지 않는 불변 설정값들
    private final int port; // 서버가 바인딩할 포트 번호 (1-65535)
    private final int maxConnections; // 동시에 처리할 수 있는 최대 연결 수 제한
    private final int backlog; // ServerSocket의 연결 대기 큐 크기

    // === 서버 상태 관리 ===
    // AtomicBoolean: 멀티스레드 환경에서 안전한 boolean 연산을 위한 클래스
    // compareAndSet(), get(), set() 등의 원자적 연산 메서드 제공
    private final AtomicBoolean running = new AtomicBoolean(false); // 서버 실행 상태 (true: 실행중, false: 중지)

    // === 네트워크 소켓 ===
    // volatile: 멀티스레드 환경에서 변수의 메모리 가시성을 보장하는 키워드
    // 한 스레드에서 변경한 값이 다른 스레드에서 즉시 보이도록 함
    private volatile ServerSocket serverSocket; // 클라이언트 연결을 수락하는 서버 소켓

    // === 비동기 컨텍스트 관리자 ===
    // Hybrid 서버의 핵심 컴포넌트로, 비동기 작업 실행과 컨텍스트 스위칭을 담당
    private AsyncContextManager asyncContextManager; // I/O와 CPU 작업을 구분하여 처리하는 관리자

    // === 라우팅 시스템 ===
    // Router: URL 패턴과 핸들러를 매핑하여 요청을 적절한 처리기로 라우팅
    private final Router router; // RESTful API 지원을 위한 라우팅 시스템

    // === 동시성 제어 ===
    // Semaphore: 동시 접근 수를 제한하는 동기화 도구
    // acquire()로 허가를 얻고 release()로 허가를 반환하는 방식으로 동작
    private final Semaphore connectionSemaphore; // 최대 연결 수 제한을 위한 세마포어

    // === 활성 연결 추적 ===
    // ConcurrentHashMap.newKeySet(): 동시성 안전한 Set 구현체
    // 현재 처리 중인 모든 연결을 추적하여 그레이스풀 셧다운에 활용
    private final Set<Socket> activeConnections = ConcurrentHashMap.newKeySet(); // 현재 활성 상태인 소켓들의 집합

    // === 서버 메트릭 수집 ===
    // 서버 성능 모니터링을 위한 각종 통계 정보 수집
    private final HybridServerMetrics metrics; // 처리 시간, 에러율, 처리량 등의 성능 지표 관리

    // === 성능 통계 카운터 ===
    // AtomicLong: 멀티스레드 환경에서 안전한 long 연산을 위한 클래스
    // incrementAndGet(), addAndGet(), get() 등의 원자적 연산 메서드 제공
    private final AtomicLong totalRequestsReceived = new AtomicLong(0); // 서버가 받은 총 요청 수
    private final AtomicLong totalRequestsProcessed = new AtomicLong(0); // 성공적으로 처리된 요청 수
    private final AtomicLong totalRequestsFailed = new AtomicLong(0); // 처리 실패한 요청 수
    private final AtomicLong currentActiveConnections = new AtomicLong(0); // 현재 활성 연결 수

    // === 기본 설정 상수 ===
    // static final: 컴파일 타임 상수로, 메모리에 한 번만 로드되는 클래스 레벨 설정값들
    private static final int DEFAULT_MAX_CONNECTIONS = 2000; // 기본 최대 동시 연결 수 - Traditional 서버보다 높게 설정
    private static final int DEFAULT_BACKLOG = 100; // 기본 연결 대기 큐 크기
    private static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 30; // 기본 요청 타임아웃 (초)
    private static final int SOCKET_TIMEOUT_MS = 30000; // 소켓 타임아웃 (밀리초)

    /**
     * 기본 설정으로 HybridServer 생성
     *
     * @param port 서버 포트 번호 (1-65535 범위의 유효한 포트)
     */
    public HybridServer(int port) { // public 생성자 - 포트만 받는 간단한 생성자
        // this(): 같은 클래스의 다른 생성자를 호출하는 키워드
        // 기본 설정값들을 사용하여 상세 생성자를 호출
        this(port, DEFAULT_MAX_CONNECTIONS, DEFAULT_BACKLOG);
    }

    /**
     * 상세 설정으로 HybridServer 생성
     *
     * @param port 서버 포트 번호 (1-65535)
     * @param maxConnections 최대 동시 연결 수 (1 이상)
     * @param backlog 연결 대기 큐 크기 (1 이상)
     */
    public HybridServer(int port, int maxConnections, int backlog) { // public 생성자 - 모든 설정을 받는 상세 생성자
        // 입력값 유효성 검증 - 잘못된 값으로 서버가 생성되는 것을 방지
        validateConstructorParameters(port, maxConnections, backlog);

        // 인스턴스 필드 초기화 - 검증된 값들로 서버 설정
        this.port = port; // this.port: 현재 객체의 port 필드에 매개변수 값 할당
        this.maxConnections = maxConnections; // 최대 연결 수 설정
        this.backlog = backlog; // 백로그 크기 설정

        // 라우팅 시스템 초기화 - URL 패턴 매칭과 핸들러 관리
        this.router = new Router(); // Router(): 기본 생성자로 라우터 인스턴스 생성

        // 연결 제한을 위한 세마포어 생성
        // Semaphore(permits): 지정된 수만큼의 허가를 가진 세마포어 생성
        this.connectionSemaphore = new Semaphore(maxConnections); // 최대 연결 수만큼 허가 생성

        // 서버 메트릭 수집 시스템 초기화
        this.metrics = new HybridServerMetrics(); // 성능 지표 수집을 위한 메트릭 객체 생성

        // 기본 라우트 설정 - 헬스 체크, 메트릭, 정보 제공 엔드포인트
        setupDefaultRoutes(); // 서버 관리를 위한 기본 API 엔드포인트들 등록

        // 서버 생성 완료 로그 - 디버깅과 운영 모니터링에 유용
        logger.info(String.format(
                "HybridServer 생성됨 - 포트: %d, 최대연결: %d, 백로그: %d",
                port, maxConnections, backlog // 생성자 매개변수들을 로그에 기록
        ));
    }

    /**
     * 생성자 매개변수 유효성 검증
     * IllegalArgumentException을 발생시켜 잘못된 설정으로 서버가 생성되는 것을 방지
     *
     * @param port 포트 번호
     * @param maxConnections 최대 연결 수
     * @param backlog 백로그 크기
     */
    private void validateConstructorParameters(int port, int maxConnections, int backlog) { // private: 클래스 내부에서만 사용
        // 포트 번호 유효성 검증 - TCP/IP 표준 포트 범위 확인
        if (port < 1 || port > 65535) { // 1-65535는 유효한 TCP 포트 범위
            // IllegalArgumentException: 잘못된 인수가 전달되었을 때 발생시키는 예외
            throw new IllegalArgumentException("포트 번호는 1-65535 사이여야 합니다: " + port);
        }

        // 최대 연결 수 유효성 검증 - 최소 1개는 있어야 요청 처리 가능
        if (maxConnections < 1) { // 1보다 작으면 연결을 받을 수 없음
            throw new IllegalArgumentException("최대 연결 수는 1 이상이어야 합니다: " + maxConnections);
        }

        // 백로그 크기 유효성 검증 - 최소 1개는 있어야 연결 대기 가능
        if (backlog < 1) { // 1보다 작으면 대기 큐를 만들 수 없음
            throw new IllegalArgumentException("백로그 크기는 1 이상이어야 합니다: " + backlog);
        }
    }

    /**
     * 기본 라우트 설정 - 서버 관리와 모니터링을 위한 필수 엔드포인트들
     * 이 메서드는 서버 생성 시 자동으로 호출되어 운영에 필요한 API들을 제공
     */
    private void setupDefaultRoutes() { // private: 클래스 내부 초기화 과정에서만 사용
        // 헬스 체크 엔드포인트 - 서버 생존 여부 확인용 (/health)
        // 로드 밸런서나 모니터링 시스템에서 서버 상태를 확인하는 데 사용
        router.get("/health", new RouteHandler() { // router.get(): GET 메서드용 라우트 등록, 익명 클래스로 핸들러 구현
            @Override // 어노테이션: 인터페이스 메서드 재정의를 명시적으로 표현
            public HttpResponse handle(HttpRequest request) throws Exception { // RouteHandler.handle(): 요청 처리 메서드
                // Map<String, Object>: 키는 문자열, 값은 모든 객체 타입을 허용하는 맵
                Map<String, Object> healthData = new HashMap<>(); // 헬스 체크 응답 데이터를 담을 맵 생성
                healthData.put("status", "UP"); // 서버 상태 - "UP"은 정상 동작 중을 의미
                healthData.put("timestamp", Instant.now().toString()); // Instant.now(): 현재 UTC 시간, toString(): ISO 8601 형식 문자열로 변환
                healthData.put("serverType", "HybridServer"); // 서버 타입 정보
                healthData.put("activeConnections", currentActiveConnections.get()); // 현재 활성 연결 수
                healthData.put("totalRequests", totalRequestsReceived.get()); // 총 요청 수

                // AsyncContextManager 상태 정보 추가
                if (asyncContextManager != null) { // null 체크 - 서버 시작 전에는 null일 수 있음
                    healthData.put("asyncContextStatus", asyncContextManager.getStatus()); // 비동기 컨텍스트 관리자 상태
                }

                // HttpResponse.json(): JSON 형태의 HTTP 응답 생성
                // convertToJson(): Map을 JSON 문자열로 변환하는 유틸리티 메서드
                return HttpResponse.json(convertToJson(healthData)); // JSON 응답 반환
            }
        });

        // 메트릭 엔드포인트 - 서버 성능 지표 확인용 (/metrics)
        // 운영팀이나 모니터링 시스템에서 서버 성능을 추적하는 데 사용
        router.get("/metrics", new RouteHandler() { // 메트릭 조회용 라우트 등록
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                Map<String, Object> metricsData = new HashMap<>(); // 메트릭 데이터를 담을 맵 생성

                // 기본 통계 정보 추가
                metricsData.put("totalRequestsReceived", totalRequestsReceived.get()); // 받은 총 요청 수
                metricsData.put("totalRequestsProcessed", totalRequestsProcessed.get()); // 처리 완료된 요청 수
                metricsData.put("totalRequestsFailed", totalRequestsFailed.get()); // 실패한 요청 수
                metricsData.put("currentActiveConnections", currentActiveConnections.get()); // 현재 활성 연결 수

                // HybridServerMetrics에서 수집한 고급 메트릭 추가
                metricsData.putAll(metrics.getAllMetrics()); // putAll(): 다른 맵의 모든 엔트리를 현재 맵에 추가

                // AsyncContextManager 메트릭 추가
                if (asyncContextManager != null) { // null 체크
                    metricsData.putAll(asyncContextManager.getMetrics()); // 비동기 컨텍스트 관리자의 메트릭 추가
                }

                return HttpResponse.json(convertToJson(metricsData)); // JSON 형태로 메트릭 데이터 반환
            }
        });

        // 서버 정보 엔드포인트 - 서버 구성 정보 확인용 (/info)
        // 개발자나 운영팀이 서버 설정 정보를 확인하는 데 사용
        router.get("/info", new RouteHandler() { // 서버 정보 조회용 라우트 등록
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                Map<String, Object> infoData = new HashMap<>(); // 서버 정보를 담을 맵 생성
                infoData.put("name", "HybridServer"); // 서버 이름
                infoData.put("version", "1.0"); // 서버 버전
                infoData.put("architecture", "AsyncContext + Context Switching"); // 아키텍처 설명
                infoData.put("port", port); // 서버 포트
                infoData.put("maxConnections", maxConnections); // 최대 연결 수
                infoData.put("backlog", backlog); // 백로그 크기
                infoData.put("startTime", metrics.getStartTime()); // 서버 시작 시간

                return HttpResponse.json(convertToJson(infoData)); // JSON 형태로 서버 정보 반환
            }
        });

        // 비동기 작업 상태 엔드포인트 - AsyncContext 상태 확인용 (/async-status)
        // Hybrid 서버 특화 엔드포인트로, 비동기 작업 처리 상태를 모니터링
        router.get("/async-status", new RouteHandler() { // 비동기 상태 조회용 라우트 등록
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                Map<String, Object> asyncData = new HashMap<>(); // 비동기 상태 데이터를 담을 맵 생성

                if (asyncContextManager != null) { // AsyncContextManager가 초기화된 경우
                    asyncData.putAll(asyncContextManager.getDetailedStatus()); // 상세한 비동기 상태 정보 추가
                } else {
                    asyncData.put("status", "NOT_INITIALIZED"); // 초기화되지 않은 상태
                }

                return HttpResponse.json(convertToJson(asyncData)); // JSON 형태로 비동기 상태 반환
            }
        });

        // 로그로 기본 라우트 설정 완료를 기록
        logger.fine("기본 라우트 설정 완료: /health, /metrics, /info, /async-status"); // Logger.fine(): 상세 로그 레벨
    }

    /**
     * 서버 시작 - 모든 컴포넌트를 초기화하고 클라이언트 요청 수락을 시작
     *
     * 시작 과정:
     * 1. 중복 시작 방지 체크
     * 2. ServerSocket 생성 및 포트 바인딩
     * 3. AsyncContextManager 초기화
     * 4. 메트릭 시작 시간 기록
     * 5. 메인 서버 루프 시작
     *
     * @throws IOException 서버 소켓 생성 실패 또는 포트 바인딩 실패
     * @throws IllegalStateException 서버가 이미 실행 중인 경우
     */
    public void start() throws IOException { // public: 외부에서 호출 가능, throws IOException: 체크 예외 선언
        // 중복 시작 방지 - AtomicBoolean의 compareAndSet으로 원자적 상태 변경
        // compareAndSet(expectedValue, newValue): 현재 값이 예상값과 같으면 새 값으로 변경하고 true 반환
        if (!running.compareAndSet(false, true)) { // false에서 true로 변경 시도
            // IllegalStateException: 객체의 상태가 메서드 호출에 부적절할 때 발생시키는 예외
            throw new IllegalStateException("서버가 이미 실행 중입니다"); // 이미 실행 중인 경우 예외 발생
        }

        try { // try-catch 블록 - 서버 시작 중 발생할 수 있는 예외 처리
            // 1. ServerSocket 생성 및 설정
            // ServerSocket(): 기본 생성자로 서버 소켓 생성
            serverSocket = new ServerSocket(); // 클라이언트 연결을 수락할 서버 소켓 생성

            // 주소 재사용 허용 - 서버 재시작 시 "Address already in use" 오류 방지
            // setReuseAddress(true): SO_REUSEADDR 소켓 옵션 활성화
            serverSocket.setReuseAddress(true); // TIME_WAIT 상태의 소켓 주소를 즉시 재사용 가능

            // 포트 바인딩 - 지정된 포트에서 클라이언트 연결 대기 시작
            // InetSocketAddress(port): 모든 네트워크 인터페이스의 지정된 포트에 바인딩
            // bind(address, backlog): 소켓을 주소에 바인딩하고 백로그 크기 설정
            serverSocket.bind(new InetSocketAddress(port), backlog); // 포트 바인딩과 연결 대기 큐 설정

            // 2. AsyncContextManager 초기화 - Hybrid 서버의 핵심 컴포넌트
            // 비동기 작업 처리와 컨텍스트 스위칭을 담당하는 관리자 생성
            this.asyncContextManager = new AsyncContextManager(); // I/O와 CPU 작업을 분리하여 처리하는 관리자

            // 3. 메트릭 시작 시간 기록 - 성능 측정을 위한 기준 시간 설정
            metrics.recordServerStart(); // 서버 시작 시간을 메트릭 시스템에 기록

            // 서버 시작 완료 로그 - 운영팀이 서버 상태를 확인할 수 있도록 정보 제공
            logger.info(String.format(
                    "HybridServer가 포트 %d에서 시작되었습니다 (최대연결: %d, 백로그: %d)",
                    port, maxConnections, backlog // 서버 설정 정보를 로그에 기록
            ));

            // 4. 메인 서버 루프 시작 - 클라이언트 연결 수락 및 처리
            runServerLoop(); // 무한 루프로 클라이언트 요청을 처리하는 메인 로직

        } catch (Exception e) { // Exception: 모든 예외를 포괄하는 상위 예외 클래스
            // 시작 실패 시 상태 복원 - 다음 시작 시도를 위해 실행 상태를 false로 되돌림
            running.set(false); // AtomicBoolean.set(): 값을 지정된 값으로 설정

            // 리소스 정리 - 메모리 누수 방지와 깔끔한 상태 유지
            cleanup(); // 생성된 리소스들을 정리하는 메서드 호출

            // 원본 예외를 포장하여 재던짐 - 호출자에게 구체적인 실패 원인 전달
            // IOException(message, cause): 메시지와 원인 예외를 포함하는 IOException 생성
            throw new IOException("HybridServer 시작 실패", e); // 원본 예외 정보를 보존하면서 적절한 예외 타입으로 변환
        }
    }

    /**
     * 메인 서버 루프 - 클라이언트 연결을 수락하고 비동기로 처리
     *
     * 처리 과정:
     * 1. 연결 수 제한 확인 (Semaphore)
     * 2. 클라이언트 연결 수락 (블로킹)
     * 3. 연결 추적 및 메트릭 업데이트
     * 4. 비동기 요청 처리 시작
     * 5. 완료 시 정리 작업
     */
    private void runServerLoop() { // private: 클래스 내부에서만 사용하는 메인 루프 메서드
        // 서버가 실행 중인 동안 계속 반복
        // running.get(): AtomicBoolean의 현재 값을 원자적으로 읽음
        while (running.get()) { // while 루프: 조건이 true인 동안 계속 실행
            Socket clientSocket = null; // 클라이언트 소켓 변수 선언, 스코프 확장을 위해 try 밖에서 선언

            try { // try-catch 블록: 연결 수락 과정에서 발생할 수 있는 예외 처리
                // 1. 연결 수 제한 확인 - 백프레셔 처리
                // tryAcquire(): 비차단적으로 세마포어 허가 획득 시도, 즉시 성공/실패 반환
                if (!connectionSemaphore.tryAcquire(100, TimeUnit.MILLISECONDS)) { // 100ms 동안 허가 획득 시도
                    continue; // 허가 획득 실패 시 다음 반복으로 건너뜀 (연결 한도 초과)
                }

                // 2. 클라이언트 연결 수락 - 블로킹 호출
                // accept(): 클라이언트 연결이 들어올 때까지 현재 스레드를 블로킹
                clientSocket = serverSocket.accept(); // 새로운 클라이언트 연결 수락

                // 3. 연결 추적 및 메트릭 업데이트
                activeConnections.add(clientSocket); // Set.add(): 활성 연결 목록에 추가
                totalRequestsReceived.incrementAndGet(); // AtomicLong.incrementAndGet(): 원자적으로 1 증가 후 값 반환
                currentActiveConnections.incrementAndGet(); // 현재 활성 연결 수 증가
                metrics.recordConnection(); // 메트릭 시스템에 새 연결 기록

                // 4. 클라이언트 소켓 설정 - 타임아웃과 성능 최적화
                configureClientSocket(clientSocket); // 소켓 옵션 설정 메서드 호출

                // 5. 비동기 요청 처리 시작 - Hybrid 서버의 핵심 부분
                // final: 람다 표현식에서 사용하기 위해 final로 선언
                final Socket finalClientSocket = clientSocket; // 람다에서 접근 가능하도록 final 변수 생성

                // CompletableFuture.runAsync(): 비동기로 작업 실행
                // () -> { ... }: 람다 표현식으로 실행할 작업 정의
                CompletableFuture<Void> requestFuture = CompletableFuture.runAsync(() -> {
                    handleClientConnectionAsync(finalClientSocket); // 비동기 연결 처리 메서드 호출
                }, asyncContextManager.getIOExecutor()); // AsyncContextManager의 I/O 전용 스레드풀에서 실행

                // 6. 완료 시 정리 작업 - 비동기로 처리
                // whenComplete(): 작업 완료(성공/실패 무관) 시 실행할 콜백 등록
                // (result, throwable): 결과와 예외를 매개변수로 받는 람다
                requestFuture.whenComplete((result, throwable) -> {
                    // 연결 정리 작업
                    activeConnections.remove(finalClientSocket); // Set.remove(): 활성 연결 목록에서 제거
                    currentActiveConnections.decrementAndGet(); // AtomicLong.decrementAndGet(): 원자적으로 1 감소 후 값 반환
                    connectionSemaphore.release(); // Semaphore.release(): 허가 반환하여 다른 연결이 사용할 수 있도록 함

                    // 처리 결과에 따른 통계 업데이트
                    if (throwable != null) { // 예외가 발생한 경우
                        logger.log(Level.WARNING, "클라이언트 연결 처리 중 오류", throwable); // 경고 레벨로 오류 로그
                        totalRequestsFailed.incrementAndGet(); // 실패 요청 수 증가
                        metrics.recordError(); // 메트릭 시스템에 오류 기록
                    } else { // 정상 처리된 경우
                        totalRequestsProcessed.incrementAndGet(); // 성공 처리 요청 수 증가
                    }
                });

            } catch (SocketException e) { // SocketException: 소켓 관련 예외 (연결 끊김, 소켓 닫힘 등)
                // ServerSocket이 닫힌 경우 (정상적인 종료 과정)
                if (running.get()) { // 서버가 여전히 실행 중이라면 비정상 상황
                    logger.log(Level.WARNING, "서버 소켓 예외 발생", e); // 예상치 못한 소켓 예외 로그
                }
                // 연결 수락 실패 시 허가 반환
                if (clientSocket == null) { // 소켓 생성 전에 예외가 발생한 경우
                    connectionSemaphore.release(); // 세마포어 허가 반환
                }
                break; // while 루프 종료

            } catch (IOException e) { // IOException: 입출력 예외 (네트워크 오류, 파일 시스템 오류 등)
                // 연결 수락 실패 - 일시적인 네트워크 문제일 수 있으므로 계속 진행
                if (running.get()) { // 서버가 실행 중인 경우에만 로그 (종료 과정에서는 정상)
                    logger.log(Level.WARNING, "클라이언트 연결 수락 실패", e); // 연결 수락 실패 로그
                }
                // 연결 수락 실패 시 허가 반환
                if (clientSocket == null) { // 소켓 생성 전에 예외가 발생한 경우
                    connectionSemaphore.release(); // 세마포어 허가 반환
                }

            } catch (InterruptedException e) { // InterruptedException: 스레드 대기 중 인터럽트 발생
                // 스레드 인터럽트 발생 시 정리 후 종료
                Thread.currentThread().interrupt(); // 현재 스레드의 인터럽트 상태 복원
                if (clientSocket == null) { // 소켓 생성 전에 인터럽트된 경우
                    connectionSemaphore.release(); // 세마포어 허가 반환
                }
                logger.info("서버 루프가 인터럽트되었습니다"); // 인터럽트 로그
                break; // while 루프 종료

            } catch (Exception e) { // Exception: 기타 모든 예외
                // 예상치 못한 예외 - 서버 안정성을 위해 로그 후 계속 진행
                logger.log(Level.SEVERE, "서버 루프에서 예상치 못한 오류", e); // 심각한 오류 레벨로 로그
                if (clientSocket == null) { // 소켓 생성 전에 예외가 발생한 경우
                    connectionSemaphore.release(); // 세마포어 허가 반환
                }
                // 서버를 계속 실행하여 복구 가능성을 유지
            }
        }
    }

    /**
     * 클라이언트 연결을 비동기로 처리 - Hybrid 서버의 핵심 로직
     *
     * 처리 과정:
     * 1. HTTP 요청 파싱 (블로킹)
     * 2. 라우팅 및 핸들러 실행 (비동기)
     * 3. HTTP 응답 전송 (블로킹)
     *
     * @param clientSocket 처리할 클라이언트 소켓
     */
    private void handleClientConnectionAsync(Socket clientSocket) { // private: 클래스 내부에서만 사용
        // 요청 처리 시작 시간 기록 - 성능 측정을 위한 기준점
        long startTime = System.currentTimeMillis(); // System.currentTimeMillis(): 현재 시간을 밀리초로 반환

        // try-with-resources: 자동 리소스 관리로 예외 발생 여부와 관계없이 소켓 닫기 보장
        // Socket, InputStream, OutputStream은 모두 AutoCloseable 인터페이스를 구현
        try (Socket socket = clientSocket; // try-with-resources에 소켓 등록
             InputStream inputStream = socket.getInputStream(); // 클라이언트로부터 데이터를 읽기 위한 입력 스트림
             OutputStream outputStream = socket.getOutputStream()) { // 클라이언트에게 데이터를 전송하기 위한 출력 스트림

            // 1. HTTP 요청 파싱 - 블로킹 I/O
            // 클라이언트가 전송한 HTTP 요청을 파싱하여 HttpRequest 객체로 변환
            HttpRequest request = parseHttpRequest(inputStream); // HTTP 요청 라인, 헤더, 바디를 파싱

            // 2. 비동기 요청 처리 시작 - Hybrid 서버의 핵심 특징
            // AsyncContextManager를 사용하여 요청을 비동기로 처리
            // 이 과정에서 I/O 대기 시 스레드가 해제되고 완료 시 다른 스레드에서 재개됨
            asyncContextManager.processRequestAsync(request) // 비동기 요청 처리 시작
                    .thenCompose(this::routeRequest) // 라우팅 처리 - thenCompose(): 다음 비동기 작업과 체이닝
                    .thenAccept(response -> { // 응답 전송 - thenAccept(): 최종 결과를 소비하는 단말 연산
                        try {
                            // 3. HTTP 응답 전송 - 블로킹 I/O
                            sendHttpResponse(outputStream, response); // 처리된 응답을 클라이언트에게 전송

                            // 성공 처리 메트릭 기록
                            long processingTime = System.currentTimeMillis() - startTime; // 처리 시간 계산
                            metrics.recordRequest(processingTime); // 메트릭 시스템에 성공적인 요청 기록

                            // 성공 로그 (상세 레벨)
                            logger.fine(String.format("요청 처리 완료: %s %s (%dms)",
                                    request.getMethod(), request.getPath(), processingTime));

                        } catch (IOException e) { // 응답 전송 중 I/O 오류
                            logger.log(Level.WARNING, "응답 전송 중 오류", e); // 네트워크 오류 로그
                            metrics.recordError(); // 메트릭에 오류 기록
                        }
                    })
                    .exceptionally(throwable -> { // 예외 처리 - exceptionally(): 예외 발생 시 실행할 콜백
                        // 요청 처리 중 발생한 모든 예외를 여기서 처리
                        logger.log(Level.WARNING, "비동기 요청 처리 중 오류", throwable);
                        metrics.recordError(); // 메트릭에 오류 기록

                        try {
                            // 500 에러 응답 전송 시도 - 클라이언트에게 서버 오류 상황 알림
                            HttpResponse errorResponse = HttpResponse.serverError("내부 서버 오류가 발생했습니다");
                            sendHttpResponse(outputStream, errorResponse); // 에러 응답 전송
                        } catch (IOException ioException) { // 에러 응답 전송도 실패한 경우
                            logger.log(Level.SEVERE, "에러 응답 전송 실패", ioException); // 심각한 오류로 로그
                        }
                        return null; // exceptionally는 반환값이 필요하므로 null 반환
                    })
                    .join(); // join(): 비동기 작업이 완료될 때까지 현재 스레드에서 대기

        } catch (IOException e) { // try-with-resources에서 발생한 I/O 예외
            // 소켓 I/O 오류 처리 - 네트워크 연결 문제나 클라이언트 연결 끊김
            logger.log(Level.WARNING, "소켓 I/O 오류", e);
            metrics.recordError(); // 메트릭에 오류 기록

        } catch (Exception e) { // 기타 모든 예외
            // 예상치 못한 예외 처리 - 파싱 오류나 기타 시스템 오류
            logger.log(Level.SEVERE, "클라이언트 연결 처리 중 예상치 못한 오류", e);
            metrics.recordError(); // 메트릭에 오류 기록
        }
    }

    /**
     * HTTP 요청을 파싱하여 HttpRequest 객체로 변환
     *
     * @param inputStream 클라이언트로부터의 입력 스트림
     * @return 파싱된 HttpRequest 객체
     * @throws IOException 파싱 중 I/O 오류 발생
     */
    private HttpRequest parseHttpRequest(InputStream inputStream) throws IOException { // private: 클래스 내부에서만 사용
        // Traditional 서버의 HTTP 파서 사용 - 동일한 파싱 로직
        // com.serverarch.traditional.HttpRequestParser.parseRequest(): traditional 패키지의 HTTP 요청 파싱 메서드
        return com.serverarch.traditional.HttpRequestParser.parseRequest(inputStream); // InputStream에서 HTTP 요청 데이터를 읽어 파싱
    }

    /**
     * 요청을 라우팅하여 적절한 핸들러로 처리
     *
     * @param request 처리할 HTTP 요청
     * @return 처리된 HTTP 응답을 포함하는 CompletableFuture
     */
    private CompletableFuture<HttpResponse> routeRequest(HttpRequest request) { // private: 클래스 내부에서만 사용
        try {
            // Router를 통한 요청 라우팅 - URL 패턴과 핸들러 매칭
            // router.route(): 요청 경로와 메서드에 맞는 핸들러를 찾아 실행
            HttpResponse response = router.route(request); // 동기식 라우팅 처리

            // 동기식 결과를 비동기 Future로 래핑
            // CompletableFuture.completedFuture(): 이미 완료된 결과를 가진 Future 생성
            return CompletableFuture.completedFuture(response); // 완료된 응답을 비동기 컨테이너에 래핑

        } catch (Exception e) { // 라우팅 중 예외 발생
            logger.log(Level.WARNING, "라우팅 처리 중 오류", e); // 라우팅 오류 로그

            // 500 에러 응답 생성
            HttpResponse errorResponse = HttpResponse.serverError("라우팅 처리 중 오류가 발생했습니다");

            // 예외가 발생한 Future 반환
            // CompletableFuture.failedFuture(): 예외를 포함하는 실패한 Future 생성 (Java 9+)
            // 또는 completedFuture()로 에러 응답 반환
            return CompletableFuture.completedFuture(errorResponse); // 에러 응답을 비동기 컨테이너에 래핑
        }
    }

    /**
     * HTTP 응답을 클라이언트에게 전송
     *
     * @param outputStream 클라이언트로의 출력 스트림
     * @param response 전송할 HTTP 응답
     * @throws IOException 전송 중 I/O 오류 발생
     */
    private void sendHttpResponse(OutputStream outputStream, HttpResponse response) throws IOException { // private: 클래스 내부에서만 사용
        // Traditional 서버의 HTTP 응답 빌더 사용 - 동일한 응답 전송 로직
        // com.serverarch.traditional.HttpResponseBuilder.buildAndSend(): traditional 패키지의 HTTP 응답 전송 메서드
        com.serverarch.traditional.HttpResponseBuilder.buildAndSend(outputStream, response); // HttpResponse 객체를 HTTP 프로토콜 형식으로 변환하여 전송
    }

    /**
     * 클라이언트 소켓 옵션 설정 - 성능 최적화와 안정성을 위한 설정
     *
     * @param clientSocket 설정할 클라이언트 소켓
     * @throws SocketException 소켓 설정 실패
     */
    private void configureClientSocket(Socket clientSocket) throws SocketException { // private: 클래스 내부에서만 사용
        // 소켓 타임아웃 설정 - 무한 대기 방지
        // setSoTimeout(): 읽기 작업의 최대 대기 시간 설정 (밀리초)
        clientSocket.setSoTimeout(SOCKET_TIMEOUT_MS); // 30초 후 SocketTimeoutException 발생

        // TCP_NODELAY 활성화 - Nagle 알고리즘 비활성화로 지연 시간 단축
        // setTcpNoDelay(true): 작은 패킷도 즉시 전송하여 응답성 향상
        clientSocket.setTcpNoDelay(true); // 버퍼링 없이 즉시 전송

        // Keep-Alive 활성화 - 연결 유지로 재연결 오버헤드 감소
        // setKeepAlive(true): TCP Keep-Alive 옵션으로 연결 상태 주기적 확인
        clientSocket.setKeepAlive(true); // 연결이 살아있는지 주기적으로 확인
    }

    /**
     * 서버 중지 - 모든 리소스를 정리하고 안전하게 종료
     *
     * 중지 과정:
     * 1. 새로운 연결 수락 중지
     * 2. 활성 연결들의 완료 대기
     * 3. AsyncContextManager 종료
     * 4. 리소스 정리
     */
    public void stop() { // public: 외부에서 서버 종료 가능
        // 이미 중지된 경우 무시 - 중복 호출 방지
        // compareAndSet(true, false): 현재 값이 true이면 false로 변경하고 true 반환
        if (!running.compareAndSet(true, false)) { // true에서 false로 변경 시도
            logger.info("서버가 이미 중지되었거나 실행 중이 아닙니다"); // 이미 중지된 상태 로그
            return; // early return으로 메서드 즉시 종료
        }

        logger.info("HybridServer 중지를 시작합니다..."); // 서버 중지 시작 로그

        try { // try-catch 블록: 중지 과정에서 발생할 수 있는 예외 처리
            // 1. ServerSocket 닫기 - 새로운 연결 수락 중지
            if (serverSocket != null && !serverSocket.isClosed()) { // null 체크와 닫힘 상태 확인
                serverSocket.close(); // accept() 메서드를 차단하여 새 연결 수락 중지
            }

            // 2. 활성 연결들의 완료 대기 (최대 30초)
            waitForActiveConnections(30); // 현재 처리 중인 연결들이 완료될 때까지 대기

            // 3. AsyncContextManager 종료
            if (asyncContextManager != null) { // null 체크
                asyncContextManager.shutdown(30); // 30초 타임아웃으로 우아한 종료 시도
            }

            // 4. 최종 통계 로그 출력
            logFinalStatistics(); // 서버 운영 기간 동안의 통계 정보 출력

            logger.info("HybridServer가 성공적으로 중지되었습니다"); // 정상 종료 완료 로그

        } catch (Exception e) { // 중지 과정에서 발생한 예외
            logger.log(Level.SEVERE, "서버 종료 중 오류 발생", e); // 심각한 오류 레벨로 로그
        } finally { // finally 블록: 예외 발생 여부와 관계없이 실행
            // 5. 최종 리소스 정리
            cleanup(); // 모든 리소스를 강제로 정리
            metrics.recordServerStop(); // 서버 중지 시간을 메트릭에 기록
        }
    }

    /**
     * 활성 연결들의 완료 대기 - 그레이스풀 셧다운을 위한 대기 로직
     *
     * @param timeoutSeconds 최대 대기 시간 (초)
     */
    private void waitForActiveConnections(int timeoutSeconds) { // private: 클래스 내부에서만 사용
        // 데드라인 계산 - 현재 시간 + 타임아웃 시간
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L; // 밀리초 단위로 변환

        logger.info(String.format("활성 연결 %d개의 완료를 최대 %d초간 대기합니다",
                activeConnections.size(), timeoutSeconds)); // 대기 시작 로그

        // 데드라인까지 활성 연결이 모두 완료되기를 대기
        // !activeConnections.isEmpty(): 활성 연결이 있는 동안
        // System.currentTimeMillis() < deadline: 데드라인 전까지
        while (!activeConnections.isEmpty() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100); // 100ms마다 상태 확인 (너무 자주 확인하지 않도록 조절)
            } catch (InterruptedException e) { // 대기 중 인터럽트 발생
                Thread.currentThread().interrupt(); // 인터럽트 상태 복원
                break; // 루프 종료
            }
        }

        // 타임아웃 후에도 활성 연결이 남아있으면 강제 종료
        if (!activeConnections.isEmpty()) { // 여전히 활성 연결이 남은 경우
            logger.warning(String.format("타임아웃으로 인해 %d개의 활성 연결을 강제 종료합니다",
                    activeConnections.size())); // 강제 종료 경고 로그

            // 남은 연결들 강제 종료
            for (Socket socket : activeConnections) { // Set의 모든 요소 순회
                try {
                    socket.close(); // 소켓 강제 닫기
                } catch (IOException e) {
                    // 강제 닫기 실패는 무시 (이미 닫혔을 수도 있음)
                }
            }
            activeConnections.clear(); // Set의 모든 요소 제거
        } else {
            logger.info("모든 활성 연결이 정상적으로 완료되었습니다"); // 정상 완료 로그
        }
    }

    /**
     * 최종 통계를 로그에 출력 - 서버 운영 성과 요약
     */
    private void logFinalStatistics() { // private: 클래스 내부에서만 사용
        // 기본 통계 로그
        logger.info(String.format(
                "서버 통계 - 총 요청: %d, 처리 완료: %d, 실패: %d, 활성 연결: %d",
                totalRequestsReceived.get(), // 받은 총 요청 수
                totalRequestsProcessed.get(), // 처리 완료된 요청 수
                totalRequestsFailed.get(), // 실패한 요청 수
                currentActiveConnections.get() // 현재 활성 연결 수
        ));

        // 고급 메트릭 로그
        Map<String, Object> finalMetrics = metrics.getAllMetrics(); // 모든 메트릭 수집
        logger.info(String.format("고급 메트릭 - 평균 응답시간: %.2fms, 총 처리시간: %dms, 에러율: %.2f%%",
                finalMetrics.get("averageResponseTime"), // 평균 응답 시간
                finalMetrics.get("totalProcessingTime"), // 총 처리 시간
                finalMetrics.get("errorRate"))); // 에러율

        // AsyncContextManager 통계 (있는 경우)
        if (asyncContextManager != null) { // AsyncContextManager가 초기화된 경우
            Map<String, Object> asyncMetrics = asyncContextManager.getMetrics(); // 비동기 메트릭 수집
            logger.info(String.format("비동기 메트릭 - 컨텍스트 스위칭: %d, I/O 작업: %d, CPU 작업: %d",
                    asyncMetrics.get("contextSwitches"), // 컨텍스트 스위칭 횟수
                    asyncMetrics.get("ioTasks"), // I/O 작업 수
                    asyncMetrics.get("cpuTasks"))); // CPU 작업 수
        }
    }

    /**
     * 리소스 정리 - 메모리 누수 방지를 위한 최종 정리 작업
     */
    private void cleanup() { // private: 클래스 내부에서만 사용
        // ServerSocket 정리
        if (serverSocket != null && !serverSocket.isClosed()) { // null 체크와 닫힘 상태 확인
            try {
                serverSocket.close(); // 서버 소켓 닫기
            } catch (IOException e) { // 소켓 닫기 실패
                logger.log(Level.WARNING, "서버 소켓 닫기 실패", e); // 실패 로그
            }
        }

        // AsyncContextManager 강제 정리
        if (asyncContextManager != null) { // null 체크
            asyncContextManager.shutdownNow(); // 강제 종료
        }

        // 활성 연결들 강제 정리
        for (Socket socket : activeConnections) { // 모든 활성 연결 순회
            try {
                socket.close(); // 소켓 닫기
            } catch (IOException e) {
                // 강제 정리이므로 예외 무시
            }
        }
        activeConnections.clear(); // 활성 연결 목록 초기화
    }

    // ========== 상태 조회 메서드들 ==========

    /**
     * 서버 실행 상태 확인
     *
     * @return 서버가 실행 중이면 true
     */
    public boolean isRunning() { // public getter: 외부에서 서버 상태 확인 가능
        return running.get(); // AtomicBoolean의 현재 값을 원자적으로 반환
    }

    /**
     * 현재 서버 통계 반환 - 모니터링과 운영에 사용
     *
     * @return 서버 통계 객체
     */
    public HybridServerStatistics getStatistics() { // public getter: 외부에서 통계 정보 조회 가능
        return new HybridServerStatistics( // HybridServerStatistics 생성자 호출
                totalRequestsReceived.get(), // 총 요청 수
                totalRequestsProcessed.get(), // 처리 완료 수
                totalRequestsFailed.get(), // 실패 수
                currentActiveConnections.get(), // 활성 연결 수
                port, // 서버 포트
                maxConnections, // 최대 연결 수
                metrics.getAllMetrics(), // 고급 메트릭
                asyncContextManager != null ? asyncContextManager.getMetrics() : new HashMap<>() // 비동기 메트릭
        );
    }

    /**
     * 라우터 반환 - 외부에서 라우트 추가 가능
     *
     * @return Router 인스턴스
     */
    public Router getRouter() { // public getter: 라우터 접근 제공
        return router; // Router 인스턴스 반환
    }

    /**
     * AsyncContextManager 반환 - 외부에서 비동기 상태 모니터링 가능
     *
     * @return AsyncContextManager 인스턴스
     */
    public AsyncContextManager getAsyncContextManager() { // public getter: 비동기 컨텍스트 관리자 접근 제공
        return asyncContextManager; // AsyncContextManager 인스턴스 반환
    }

    /**
     * 서버 메트릭 반환 - 외부에서 성능 지표 확인 가능
     *
     * @return HybridServerMetrics 인스턴스
     */
    public HybridServerMetrics getMetrics() { // public getter: 메트릭 접근 제공
        return metrics; // HybridServerMetrics 인스턴스 반환
    }

    // ========== 유틸리티 메서드들 ==========

    /**
     * Map을 간단한 JSON 문자열로 변환하는 유틸리티 메서드
     * 기본 엔드포인트에서 JSON 응답을 생성하기 위해 사용
     *
     * @param map 변환할 Map 객체
     * @return JSON 형태의 문자열
     */
    private String convertToJson(Map<String, Object> map) { // private: 클래스 내부 유틸리티
        if (map == null || map.isEmpty()) { // null 체크와 빈 맵 확인
            return "{}"; // 빈 JSON 객체 반환
        }

        StringBuilder json = new StringBuilder(); // StringBuilder: 효율적인 문자열 조합
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
     * 비동기 컨텍스트 관리자 - Hybrid 서버의 핵심 컴포넌트
     *
     * 이 클래스는 다음과 같은 기능을 담당:
     * 1. I/O 집약적 작업과 CPU 집약적 작업을 구분하여 처리
     * 2. 컨텍스트 스위칭을 통한 스레드 재활용
     * 3. 비동기 작업 체이닝과 타임아웃 관리
     * 4. 백프레셔 처리와 작업 부하 분산
     */
    public static class AsyncContextManager { // public static: 외부에서 독립적으로 사용 가능한 정적 중첩 클래스

        // === 스레드풀 관리 ===
        // I/O 집약적 작업을 위한 전용 스레드풀 - 대기 시간이 많은 작업 처리
        private final ExecutorService ioExecutor; // I/O 작업용 스레드풀
        // CPU 집약적 작업을 위한 전용 스레드풀 - 연산 처리 작업
        private final ExecutorService cpuExecutor; // CPU 작업용 스레드풀
        // 타임아웃 관리를 위한 스케줄러
        private final ScheduledExecutorService timeoutExecutor; // 타임아웃 처리용 스케줄러

        // === 통계 수집 ===
        // 비동기 작업 처리 통계를 위한 원자적 카운터들
        private final AtomicLong totalContextSwitches = new AtomicLong(0); // 컨텍스트 스위칭 총 횟수
        private final AtomicLong totalIOTasks = new AtomicLong(0); // I/O 작업 총 수
        private final AtomicLong totalCPUTasks = new AtomicLong(0); // CPU 작업 총 수
        private final AtomicLong totalTimeouts = new AtomicLong(0); // 타임아웃 발생 총 수

        // === 성능 설정 ===
        // 기본 타임아웃 설정 - 비동기 작업이 너무 오래 걸리지 않도록 제한
        private static final int DEFAULT_TIMEOUT_SECONDS = DEFAULT_REQUEST_TIMEOUT_SECONDS; // 30초 기본 타임아웃

        /**
         * AsyncContextManager 생성자 - 스레드풀들을 초기화
         */
        public AsyncContextManager() { // public 생성자
            // I/O 스레드풀 생성 - I/O 대기가 많으므로 스레드 수를 많이 할당
            // newCachedThreadPool(): 필요에 따라 스레드를 생성하고 60초 후 제거하는 동적 풀
            this.ioExecutor = Executors.newCachedThreadPool(new CustomThreadFactory("Hybrid-IO")); // I/O 작업용

            // CPU 스레드풀 생성 - CPU 집약적 작업이므로 CPU 코어 수에 맞춰 제한
            // Runtime.getRuntime().availableProcessors(): 사용 가능한 CPU 코어 수 반환
            this.cpuExecutor = Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors(), // CPU 코어 수만큼 스레드 생성
                    new CustomThreadFactory("Hybrid-CPU") // CPU 작업용
            );

            // 타임아웃 스케줄러 생성 - 주기적인 타임아웃 체크를 위한 별도 스레드
            this.timeoutExecutor = Executors.newScheduledThreadPool(2, // 2개 스레드로 타임아웃 관리
                    new CustomThreadFactory("Hybrid-Timeout")); // 타임아웃 처리용

            logger.info("AsyncContextManager 초기화 완료 - I/O 풀: 동적, CPU 풀: " +
                    Runtime.getRuntime().availableProcessors() + "개"); // 초기화 완료 로그
        }

        /**
         * 요청을 비동기로 처리 시작 - 컨텍스트 스위칭의 시작점
         *
         * @param request 처리할 HTTP 요청
         * @return 처리된 요청을 포함하는 CompletableFuture
         */
        public CompletableFuture<HttpRequest> processRequestAsync(HttpRequest request) { // public: 외부에서 호출 가능
            // 컨텍스트 스위칭 통계 업데이트
            totalContextSwitches.incrementAndGet(); // 원자적으로 카운터 증가

            // I/O 작업으로 분류하여 처리 - HTTP 요청 처리는 주로 I/O 집약적
            // CompletableFuture.supplyAsync(): 비동기로 작업 실행하고 결과 반환
            // () -> request: 람다 표현식으로 요청 객체 반환
            return CompletableFuture.supplyAsync(() -> {
                        totalIOTasks.incrementAndGet(); // I/O 작업 수 증가

                        // 실제로는 여기서 추가적인 I/O 작업을 수행할 수 있음
                        // (예: 데이터베이스 조회, 외부 API 호출 등)
                        // 현재는 단순히 요청 객체를 반환
                        return request; // 처리된 요청 반환 (현재는 원본 그대로)
                    }, ioExecutor) // I/O 전용 스레드풀에서 실행
                    .orTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS) // 타임아웃 설정
                    .exceptionally(throwable -> { // 예외 처리
                        if (throwable instanceof TimeoutException) { // 타임아웃 예외인 경우
                            totalTimeouts.incrementAndGet(); // 타임아웃 카운터 증가
                            logger.warning("요청 처리 타임아웃 발생"); // 타임아웃 경고 로그
                        }
                        // RuntimeException으로 예외 전파
                        throw new RuntimeException("비동기 요청 처리 실패", throwable); // 예외 래핑
                    });
        }

        /**
         * CPU 집약적 작업을 비동기로 실행
         *
         * @param task 실행할 CPU 집약적 작업
         * @return 작업 결과를 포함하는 CompletableFuture
         */
        public <T> CompletableFuture<T> executeCPUTask(Callable<T> task) { // public: 외부에서 호출 가능, 제네릭 메서드
            totalCPUTasks.incrementAndGet(); // CPU 작업 수 증가

            // CPU 전용 스레드풀에서 작업 실행
            // CompletableFuture.supplyAsync(): 비동기로 Callable 실행
            return CompletableFuture.supplyAsync(() -> {
                        try {
                            return task.call(); // Callable.call(): 실제 작업 실행
                        } catch (Exception e) { // Callable에서 발생한 예외
                            throw new RuntimeException("CPU 작업 실행 실패", e); // 예외 래핑
                        }
                    }, cpuExecutor) // CPU 전용 스레드풀에서 실행
                    .orTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS); // 타임아웃 설정
        }

        /**
         * I/O 집약적 작업을 비동기로 실행
         *
         * @param task 실행할 I/O 집약적 작업
         * @return 작업 결과를 포함하는 CompletableFuture
         */
        public <T> CompletableFuture<T> executeIOTask(Callable<T> task) { // public: 외부에서 호출 가능, 제네릭 메서드
            totalIOTasks.incrementAndGet(); // I/O 작업 수 증가

            // I/O 전용 스레드풀에서 작업 실행
            return CompletableFuture.supplyAsync(() -> {
                        try {
                            return task.call(); // Callable.call(): 실제 작업 실행
                        } catch (Exception e) { // Callable에서 발생한 예외
                            throw new RuntimeException("I/O 작업 실행 실패", e); // 예외 래핑
                        }
                    }, ioExecutor) // I/O 전용 스레드풀에서 실행
                    .orTimeout(DEFAULT_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS); // I/O 작업은 더 긴 타임아웃
        }

        /**
         * 타임아웃과 함께 CompletableFuture 실행
         *
         * @param future 타임아웃을 적용할 Future
         * @param timeout 타임아웃 시간
         * @param unit 시간 단위
         * @return 타임아웃이 적용된 CompletableFuture
         */
        public <T> CompletableFuture<T> withTimeout(CompletableFuture<T> future, long timeout, TimeUnit unit) { // public: 외부에서 호출 가능
            // CompletableFuture에 타임아웃 적용
            return future.orTimeout(timeout, unit) // orTimeout(): 지정된 시간 후 TimeoutException 발생
                    .exceptionally(throwable -> { // 예외 처리
                        if (throwable instanceof TimeoutException) { // 타임아웃 예외인 경우
                            totalTimeouts.incrementAndGet(); // 타임아웃 카운터 증가
                            logger.warning(String.format("작업 타임아웃 발생: %d %s", timeout, unit)); // 타임아웃 로그
                        }
                        throw new RuntimeException("작업 타임아웃", throwable); // 예외 재발생
                    });
        }

        /**
         * I/O 전용 Executor 반환
         *
         * @return I/O 작업용 ExecutorService
         */
        public ExecutorService getIOExecutor() { // public getter: I/O 스레드풀 접근 제공
            return ioExecutor; // I/O 전용 ExecutorService 반환
        }

        /**
         * CPU 전용 Executor 반환
         *
         * @return CPU 작업용 ExecutorService
         */
        public ExecutorService getCPUExecutor() { // public getter: CPU 스레드풀 접근 제공
            return cpuExecutor; // CPU 전용 ExecutorService 반환
        }

        /**
         * 현재 상태 반환
         *
         * @return 상태 정보 맵
         */
        public Map<String, Object> getStatus() { // public: 외부에서 상태 확인 가능
            Map<String, Object> status = new HashMap<>(); // 상태 정보를 담을 맵 생성
            status.put("totalContextSwitches", totalContextSwitches.get()); // 총 컨텍스트 스위칭 수
            status.put("totalIOTasks", totalIOTasks.get()); // 총 I/O 작업 수
            status.put("totalCPUTasks", totalCPUTasks.get()); // 총 CPU 작업 수
            status.put("totalTimeouts", totalTimeouts.get()); // 총 타임아웃 수
            return status; // 상태 맵 반환
        }

        /**
         * 상세한 상태 정보 반환
         *
         * @return 상세 상태 정보 맵
         */
        public Map<String, Object> getDetailedStatus() { // public: 외부에서 상세 상태 확인 가능
            Map<String, Object> detailedStatus = new HashMap<>(); // 상세 상태 정보를 담을 맵 생성

            // 기본 상태 정보 추가
            detailedStatus.putAll(getStatus()); // 기본 상태 정보 포함

            // 스레드풀 상태 정보 추가 (ExecutorService의 구체 타입에 따라 다름)
            if (cpuExecutor instanceof ThreadPoolExecutor) { // CPU 스레드풀이 ThreadPoolExecutor인 경우
                ThreadPoolExecutor tpe = (ThreadPoolExecutor) cpuExecutor; // 타입 캐스팅
                detailedStatus.put("cpuPoolSize", tpe.getPoolSize()); // CPU 풀 크기
                detailedStatus.put("cpuActiveCount", tpe.getActiveCount()); // CPU 활성 스레드 수
                detailedStatus.put("cpuQueueSize", tpe.getQueue().size()); // CPU 큐 크기
            }

            // I/O 풀은 CachedThreadPool이므로 별도 정보 추가
            detailedStatus.put("ioPoolType", "CachedThreadPool"); // I/O 풀 타입

            return detailedStatus; // 상세 상태 맵 반환
        }

        /**
         * 메트릭 정보 반환
         *
         * @return 메트릭 정보 맵
         */
        public Map<String, Object> getMetrics() { // public: 외부에서 메트릭 확인 가능
            Map<String, Object> metrics = new HashMap<>(); // 메트릭 정보를 담을 맵 생성
            metrics.put("contextSwitches", totalContextSwitches.get()); // 컨텍스트 스위칭 수
            metrics.put("ioTasks", totalIOTasks.get()); // I/O 작업 수
            metrics.put("cpuTasks", totalCPUTasks.get()); // CPU 작업 수
            metrics.put("timeouts", totalTimeouts.get()); // 타임아웃 수

            // 비율 계산
            long totalTasks = totalIOTasks.get() + totalCPUTasks.get(); // 총 작업 수
            if (totalTasks > 0) { // 0으로 나누기 방지
                metrics.put("ioTaskRatio", (double) totalIOTasks.get() / totalTasks * 100.0); // I/O 작업 비율 (%)
                metrics.put("cpuTaskRatio", (double) totalCPUTasks.get() / totalTasks * 100.0); // CPU 작업 비율 (%)
                metrics.put("timeoutRatio", (double) totalTimeouts.get() / totalTasks * 100.0); // 타임아웃 비율 (%)
            }

            return metrics; // 메트릭 맵 반환
        }

        /**
         * AsyncContextManager 종료
         *
         * @param timeoutSeconds 종료 대기 시간 (초)
         * @return 정상 종료되면 true
         */
        public boolean shutdown(long timeoutSeconds) { // public: 외부에서 종료 가능
            logger.info("AsyncContextManager 종료를 시작합니다..."); // 종료 시작 로그

            boolean allTerminated = true; // 모든 스레드풀 종료 여부

            // 1. I/O 스레드풀 종료
            ioExecutor.shutdown(); // 새로운 작업 수락 중지
            try {
                if (!ioExecutor.awaitTermination(timeoutSeconds / 3, TimeUnit.SECONDS)) { // 1/3 시간 대기
                    ioExecutor.shutdownNow(); // 강제 종료
                    allTerminated = false; // 정상 종료 실패
                }
            } catch (InterruptedException e) { // 대기 중 인터럽트
                ioExecutor.shutdownNow(); // 강제 종료
                Thread.currentThread().interrupt(); // 인터럽트 상태 복원
                allTerminated = false; // 정상 종료 실패
            }

            // 2. CPU 스레드풀 종료
            cpuExecutor.shutdown(); // 새로운 작업 수락 중지
            try {
                if (!cpuExecutor.awaitTermination(timeoutSeconds / 3, TimeUnit.SECONDS)) { // 1/3 시간 대기
                    cpuExecutor.shutdownNow(); // 강제 종료
                    allTerminated = false; // 정상 종료 실패
                }
            } catch (InterruptedException e) { // 대기 중 인터럽트
                cpuExecutor.shutdownNow(); // 강제 종료
                Thread.currentThread().interrupt(); // 인터럽트 상태 복원
                allTerminated = false; // 정상 종료 실패
            }

            // 3. 타임아웃 스케줄러 종료
            timeoutExecutor.shutdown(); // 새로운 작업 수락 중지
            try {
                if (!timeoutExecutor.awaitTermination(timeoutSeconds / 3, TimeUnit.SECONDS)) { // 1/3 시간 대기
                    timeoutExecutor.shutdownNow(); // 강제 종료
                    allTerminated = false; // 정상 종료 실패
                }
            } catch (InterruptedException e) { // 대기 중 인터럽트
                timeoutExecutor.shutdownNow(); // 강제 종료
                Thread.currentThread().interrupt(); // 인터럽트 상태 복원
                allTerminated = false; // 정상 종료 실패
            }

            if (allTerminated) { // 모든 스레드풀이 정상 종료된 경우
                logger.info("AsyncContextManager가 성공적으로 종료되었습니다"); // 성공 로그
            } else { // 일부 스레드풀이 강제 종료된 경우
                logger.warning("일부 스레드풀이 강제 종료되었습니다"); // 경고 로그
            }

            return allTerminated; // 종료 결과 반환
        }

        /**
         * AsyncContextManager 즉시 강제 종료
         */
        public void shutdownNow() { // public: 외부에서 강제 종료 가능
            logger.warning("AsyncContextManager를 즉시 강제 종료합니다"); // 강제 종료 경고 로그
            ioExecutor.shutdownNow(); // I/O 스레드풀 즉시 종료
            cpuExecutor.shutdownNow(); // CPU 스레드풀 즉시 종료
            timeoutExecutor.shutdownNow(); // 타임아웃 스케줄러 즉시 종료
        }
    }

    /**
     * 커스텀 스레드 팩토리 - 의미있는 스레드 이름 생성
     */
    private static class CustomThreadFactory implements ThreadFactory { // private static: 클래스 내부에서만 사용하는 정적 중첩 클래스
        private final AtomicInteger threadNumber = new AtomicInteger(1); // 스레드 번호 카운터
        private final String namePrefix; // 스레드 이름 접두사

        public CustomThreadFactory(String namePrefix) { // 생성자: 이름 접두사 설정
            this.namePrefix = namePrefix; // 접두사 저장
        }

        @Override
        public Thread newThread(Runnable r) { // ThreadFactory.newThread() 구현
            // 의미있는 스레드 이름 생성
            Thread thread = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            thread.setDaemon(false); // 일반 스레드로 설정 (JVM이 이 스레드들의 완료를 기다림)
            thread.setPriority(Thread.NORM_PRIORITY); // 기본 우선순위 설정
            return thread; // 생성된 스레드 반환
        }
    }

    /**
     * Hybrid 서버 통계 정보를 담는 불변 클래스
     */
    public static class HybridServerStatistics { // public static: 외부에서 독립적으로 사용 가능
        // 모든 필드를 final로 선언하여 불변성 보장
        private final long totalRequestsReceived; // 받은 총 요청 수
        private final long totalRequestsProcessed; // 처리 완료된 요청 수
        private final long totalRequestsFailed; // 실패한 요청 수
        private final long currentActiveConnections; // 현재 활성 연결 수
        private final int port; // 서버 포트
        private final int maxConnections; // 최대 연결 수
        private final Map<String, Object> advancedMetrics; // 고급 메트릭
        private final Map<String, Object> asyncMetrics; // 비동기 메트릭

        /**
         * HybridServerStatistics 생성자
         */
        public HybridServerStatistics(long totalRequestsReceived, long totalRequestsProcessed,
                                      long totalRequestsFailed, long currentActiveConnections,
                                      int port, int maxConnections, Map<String, Object> advancedMetrics,
                                      Map<String, Object> asyncMetrics) { // 모든 통계 값을 매개변수로 받는 생성자
            this.totalRequestsReceived = totalRequestsReceived; // 총 요청 수 설정
            this.totalRequestsProcessed = totalRequestsProcessed; // 처리 완료 수 설정
            this.totalRequestsFailed = totalRequestsFailed; // 실패 수 설정
            this.currentActiveConnections = currentActiveConnections; // 활성 연결 수 설정
            this.port = port; // 포트 설정
            this.maxConnections = maxConnections; // 최대 연결 수 설정
            this.advancedMetrics = new HashMap<>(advancedMetrics); // 방어적 복사로 고급 메트릭 저장
            this.asyncMetrics = new HashMap<>(asyncMetrics); // 방어적 복사로 비동기 메트릭 저장
        }

        // Getter 메서드들 - 불변 객체이므로 필드 값 반환만 수행
        public long getTotalRequestsReceived() { return totalRequestsReceived; }
        public long getTotalRequestsProcessed() { return totalRequestsProcessed; }
        public long getTotalRequestsFailed() { return totalRequestsFailed; }
        public long getCurrentActiveConnections() { return currentActiveConnections; }
        public int getPort() { return port; }
        public int getMaxConnections() { return maxConnections; }
        public Map<String, Object> getAdvancedMetrics() { return new HashMap<>(advancedMetrics); } // 방어적 복사 반환
        public Map<String, Object> getAsyncMetrics() { return new HashMap<>(asyncMetrics); } // 방어적 복사 반환

        /**
         * 성공률 계산
         *
         * @return 성공률 (0.0 ~ 1.0)
         */
        public double getSuccessRate() { // public: 외부에서 성공률 계산 가능
            return totalRequestsReceived > 0 ? // 삼항 연산자: 총 요청이 0보다 큰 경우
                    (double) totalRequestsProcessed / totalRequestsReceived : 0.0; // 성공률 = 처리 완료 / 총 요청
        }

        /**
         * 실패율 계산
         *
         * @return 실패율 (0.0 ~ 1.0)
         */
        public double getFailureRate() { // public: 외부에서 실패율 계산 가능
            return totalRequestsReceived > 0 ? // 삼항 연산자: 총 요청이 0보다 큰 경우
                    (double) totalRequestsFailed / totalRequestsReceived : 0.0; // 실패율 = 실패 수 / 총 요청
        }

        /**
         * 연결 사용률 계산
         *
         * @return 연결 사용률 (0.0 ~ 1.0)
         */
        public double getConnectionUsageRate() { // public: 외부에서 연결 사용률 계산 가능
            return maxConnections > 0 ? // 삼항 연산자: 최대 연결 수가 0보다 큰 경우
                    (double) currentActiveConnections / maxConnections : 0.0; // 사용률 = 현재 연결 / 최대 연결
        }

        /**
         * 통계 정보를 문자열로 표현
         */
        @Override
        public String toString() { // Object.toString() 재정의
            return String.format(
                    "HybridServerStatistics{받은요청=%d, 처리완료=%d, 실패=%d, 활성연결=%d, " +
                            "포트=%d, 최대연결=%d, 성공률=%.2f%%, 실패율=%.2f%%, 연결사용률=%.2f%%, " +
                            "컨텍스트스위칭=%s, I/O작업=%s, CPU작업=%s}",
                    totalRequestsReceived, totalRequestsProcessed, totalRequestsFailed,
                    currentActiveConnections, port, maxConnections,
                    getSuccessRate() * 100, getFailureRate() * 100, getConnectionUsageRate() * 100,
                    asyncMetrics.get("contextSwitches"), asyncMetrics.get("ioTasks"), asyncMetrics.get("cpuTasks")
            );
        }
    }

    /**
     * Hybrid 서버 메트릭 수집 클래스
     */
    public static class HybridServerMetrics { // public static: 외부에서 독립적으로 사용 가능
        // 메트릭 수집용 원자적 변수들
        private final AtomicLong totalRequestCount = new AtomicLong(0); // 총 요청 수
        private final AtomicLong totalProcessingTime = new AtomicLong(0); // 총 처리 시간 (밀리초)
        private final AtomicLong totalErrors = new AtomicLong(0); // 총 에러 수
        private final AtomicLong totalConnections = new AtomicLong(0); // 총 연결 수

        // 서버 생명주기 시간
        private volatile long serverStartTime = 0; // 서버 시작 시간
        private volatile long serverStopTime = 0; // 서버 중지 시간

        /**
         * 서버 시작 시간 기록
         */
        public void recordServerStart() { // public: 외부에서 시작 시간 기록 가능
            this.serverStartTime = System.currentTimeMillis(); // 현재 시간을 시작 시간으로 기록
            logger.fine("Hybrid 서버 시작 시간 기록됨: " + new Date(serverStartTime)); // 시작 시간 로그
        }

        /**
         * 서버 중지 시간 기록
         */
        public void recordServerStop() { // public: 외부에서 중지 시간 기록 가능
            this.serverStopTime = System.currentTimeMillis(); // 현재 시간을 중지 시간으로 기록
            logger.fine("Hybrid 서버 중지 시간 기록됨: " + new Date(serverStopTime)); // 중지 시간 로그
        }

        /**
         * 요청 처리 기록
         *
         * @param processingTimeMs 처리 시간 (밀리초)
         */
        public void recordRequest(long processingTimeMs) { // public: 외부에서 요청 기록 가능
            totalRequestCount.incrementAndGet(); // 요청 수 증가
            totalProcessingTime.addAndGet(processingTimeMs); // 처리 시간 누적
        }

        /**
         * 에러 발생 기록
         */
        public void recordError() { // public: 외부에서 에러 기록 가능
            totalErrors.incrementAndGet(); // 에러 수 증가
        }

        /**
         * 연결 발생 기록
         */
        public void recordConnection() { // public: 외부에서 연결 기록 가능
            totalConnections.incrementAndGet(); // 연결 수 증가
        }

        /**
         * 모든 메트릭 반환
         *
         * @return 모든 메트릭을 담은 Map
         */
        public Map<String, Object> getAllMetrics() { // public: 외부에서 모든 메트릭 조회 가능
            Map<String, Object> metrics = new HashMap<>(); // 메트릭을 담을 맵 생성

            long requestCount = totalRequestCount.get(); // 현재 요청 수
            long processingTime = totalProcessingTime.get(); // 현재 총 처리 시간

            // 기본 메트릭들
            metrics.put("totalRequests", requestCount); // 총 요청 수
            metrics.put("totalErrors", totalErrors.get()); // 총 에러 수
            metrics.put("totalConnections", totalConnections.get()); // 총 연결 수
            metrics.put("totalProcessingTime", processingTime); // 총 처리 시간

            // 계산된 메트릭들
            metrics.put("averageResponseTime", requestCount > 0 ? // 평균 응답 시간
                    (double) processingTime / requestCount : 0.0); // 총 시간 / 요청 수
            metrics.put("errorRate", requestCount > 0 ? // 에러율
                    (double) totalErrors.get() / requestCount * 100.0 : 0.0); // 에러 수 / 요청 수 * 100

            // 서버 생명주기 메트릭들
            metrics.put("serverStartTime", serverStartTime); // 서버 시작 시간
            if (serverStopTime > 0) { // 서버가 중지된 경우
                metrics.put("serverStopTime", serverStopTime); // 서버 중지 시간
                metrics.put("totalUptime", serverStopTime - serverStartTime); // 총 업타임
            } else if (serverStartTime > 0) { // 서버가 실행 중인 경우
                long currentUptime = System.currentTimeMillis() - serverStartTime; // 현재 업타임
                metrics.put("currentUptime", currentUptime); // 현재 업타임
            }

            return metrics; // 완성된 메트릭 맵 반환
        }

        /**
         * 서버 시작 시간 반환
         *
         * @return 서버 시작 시간 (밀리초)
         */
        public long getStartTime() { // public getter: 외부에서 시작 시간 조회 가능
            return serverStartTime; // 서버 시작 시간 반환
        }

        /**
         * 현재 업타임 반환
         *
         * @return 현재 업타임 (밀리초)
         */
        public long getCurrentUptime() { // public: 외부에서 현재 업타임 조회 가능
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
        public double getAverageResponseTime() { // public: 외부에서 평균 응답 시간 조회 가능
            long requestCount = totalRequestCount.get(); // 총 요청 수
            return requestCount > 0 ? // 요청이 있는 경우
                    (double) totalProcessingTime.get() / requestCount : 0.0; // 총 처리 시간 / 요청 수
        }

        /**
         * 에러율 반환
         *
         * @return 에러율 (백분율)
         */
        public double getErrorRate() { // public: 외부에서 에러율 조회 가능
            long requestCount = totalRequestCount.get(); // 총 요청 수
            return requestCount > 0 ? // 요청이 있는 경우
                    (double) totalErrors.get() / requestCount * 100.0 : 0.0; // 에러 수 / 요청 수 * 100
        }
    }
}