package com.serverarch.eventloop.core; // 패키지 선언 - 이벤트 루프 서버 아키텍처 패키지

// === Java NIO 라이브러리 Import ===
import java.nio.channels.*; // SelectionKey, ServerSocketChannel, SocketChannel 등 NIO 채널 클래스들
import java.nio.*; // ByteBuffer 등 NIO 버퍼 클래스들
import java.net.*; // InetSocketAddress 등 네트워크 주소 클래스들
import java.io.*; // IOException 등 예외 클래스들
import java.util.concurrent.*; // CompletableFuture, ConcurrentHashMap 등 동시성 클래스들
import java.util.concurrent.atomic.*; // AtomicLong, AtomicBoolean 등 원자적 연산 클래스들
import java.util.logging.*; // Logger, Level 등 로깅 클래스들
import java.util.*; // Map, Set 등 컬렉션 클래스들
import java.time.*; // LocalDateTime 등 시간 클래스들

// === EventLoop HTTP 처리 Import ===
import com.serverarch.eventloop.http.HttpRequest; // EventLoop용 HttpRequest 인터페이스
import com.serverarch.eventloop.http.HttpResponse; // EventLoop용 HttpResponse 인터페이스
import com.serverarch.eventloop.routing.Router; // EventLoop용 Router 인터페이스
import com.serverarch.eventloop.routing.SimpleEventLoopRouter; // EventLoop용 Router 구현체
import com.serverarch.eventloop.handler.HttpChannelHandler; // EventLoop용 HTTP 채널 핸들러

/**
 * EventLoopServer - 단일 스레드 이벤트 루프 기반 HTTP 서버
 *
 * 이 클래스는 Node.js나 Netty 같은 이벤트 기반 서버의 핵심 원리를 구현합니다.
 *
 * 핵심 설계 원칙:
 * 1. 단일 스레드 이벤트 루프: 모든 I/O 이벤트를 하나의 스레드에서 처리
 * 2. 완전 논블로킹 I/O: NIO Selector 기반으로 블로킹 없는 I/O 처리
 * 3. 이벤트 기반 처리: I/O 준비 완료 시점에만 처리 수행
 * 4. 높은 동시성: 수만 개의 동시 연결 지원 가능
 *
 * 아키텍처 구성요소:
 * - EventLoop: 핵심 이벤트 루프 (Selector 기반)
 * - ChannelHandler: 채널별 이벤트 처리기
 * - HttpChannelHandler: HTTP 프로토콜 전용 처리기
 * - Router: HTTP 요청 라우팅 시스템 (EventLoop 전용)
 *
 * 성능 특징:
 * - 메모리 효율: 스레드당 스택 메모리 없음 (vs ThreadedServer)
 * - CPU 효율: 컨텍스트 스위칭 오버헤드 없음 (vs HybridServer)
 * - 확장성: 동시 연결 수가 스레드 수에 비례하지 않음
 *
 * 주의사항:
 * - CPU 집약적 작업은 별도 스레드풀에서 처리
 * - 모든 I/O는 논블로킹으로 구현
 * - 콜백 체인으로 인한 복잡성 존재
 */
public class EventLoopServer { // public 클래스 선언 - 외부에서 사용 가능한 이벤트 루프 HTTP 서버

    // === 로깅 시스템 ===
    // static final: 클래스 레벨 상수로 모든 인스턴스가 공유
    // 이벤트 루프 서버의 모든 동작을 추적하고 디버깅에 활용
    private static final Logger logger = Logger.getLogger(EventLoopServer.class.getName()); // Logger.getLogger() - 클래스명 기반 로거 생성

    // === 서버 설정 상수들 ===
    private final int port; // final int - 서버가 바인딩할 포트 번호 (생성 후 변경 불가)
    private final int backlog; // final int - 연결 대기 큐 크기 (ServerSocketChannel의 백로그)

    // === 이벤트 루프 시스템 ===
    private EventLoop eventLoop; // EventLoop - 핵심 이벤트 루프 인스턴스
    private ServerSocketChannel serverChannel; // ServerSocketChannel - NIO 기반 서버 소켓 채널
    private SelectionKey serverKey; // SelectionKey - 서버 채널이 Selector에 등록될 때 반환되는 키

    // === 서버 상태 관리 ===
    // AtomicBoolean: 멀티스레드 환경에서 안전한 상태 변경 보장
    private final AtomicBoolean running = new AtomicBoolean(false); // 서버 실행 상태 (초기값: false)

    // === HTTP 처리 시스템 ===
    private final Router router; // Router - EventLoop용 HTTP 요청 라우팅 시스템
    private HttpChannelHandler httpHandler; // HttpChannelHandler - EventLoop용 HTTP 프로토콜 전용 채널 핸들러

    // === 성능 메트릭 수집 ===
    // 이벤트 루프 서버의 성능 특성을 모니터링하기 위한 카운터들
    private final AtomicLong totalConnections = new AtomicLong(0); // 총 연결 수 카운터
    private final AtomicLong activeConnections = new AtomicLong(0); // 현재 활성 연결 수 카운터
    private final AtomicLong totalRequests = new AtomicLong(0); // 총 요청 수 카운터
    private final AtomicLong totalResponses = new AtomicLong(0); // 총 응답 수 카운터
    private final AtomicLong totalErrors = new AtomicLong(0); // 총 에러 수 카운터

    // === 연결 관리 ===
    // 활성 클라이언트 연결들을 추적하여 정리 작업에 활용
    private final Map<SocketChannel, ChannelContext> activeChannels = new ConcurrentHashMap<>(); // 활성 채널과 컨텍스트 매핑

    // === 서버 설정 기본값들 ===
    private static final int DEFAULT_BACKLOG = 1024; // 기본 백로그 크기 - 이벤트 루프는 더 많은 대기 연결 처리 가능
    private static final int BUFFER_SIZE = 8192; // 기본 I/O 버퍼 크기 (8KB)
    private static final long METRICS_LOG_INTERVAL = 30000; // 메트릭 로그 출력 간격 (30초)

    // === 메트릭 로깅 관련 ===
    private volatile long lastMetricsTime = 0; // 마지막 메트릭 로그 출력 시간

    /**
     * 기본 설정으로 이벤트 루프 서버 생성
     *
     * @param port 서버 포트 (1-65535)
     * @throws IllegalArgumentException 포트 번호가 유효하지 않은 경우
     */
    public EventLoopServer(int port) { // public 생성자 - 포트만 받는 간단한 생성자
        this(port, DEFAULT_BACKLOG); // this() - 같은 클래스의 다른 생성자 호출
    }

    /**
     * 상세 설정으로 이벤트 루프 서버 생성
     *
     * @param port 서버 포트 (1-65535)
     * @param backlog 연결 대기 큐 크기 (최소 1)
     * @throws IllegalArgumentException 매개변수가 유효하지 않은 경우
     */
    public EventLoopServer(int port, int backlog) { // public 생성자 - 포트와 백로그를 받는 상세 생성자
        // 포트 번호 유효성 검증
        if (port < 1 || port > 65535) { // 유효한 포트 범위 확인
            throw new IllegalArgumentException("포트 번호는 1-65535 사이여야 합니다: " + port);
        }

        // 백로그 크기 유효성 검증
        if (backlog < 1) { // 최소 백로그 크기 확인
            throw new IllegalArgumentException("백로그 크기는 1 이상이어야 합니다: " + backlog);
        }

        // 인스턴스 변수 초기화
        this.port = port; // 포트 설정
        this.backlog = backlog; // 백로그 설정
        this.router = new SimpleEventLoopRouter(); // EventLoop용 라우터 인스턴스 생성

        // 기본 라우트 설정
        setupDefaultRoutes(); // 헬스 체크, 메트릭 등 기본 엔드포인트 설정

        // 초기화 완료 로그
        logger.info(String.format("EventLoopServer 생성됨 - 포트: %d, 백로그: %d", port, backlog));
    }

    /**
     * 기본 라우트 설정
     * 서버 관리와 모니터링을 위한 필수 엔드포인트들을 자동으로 등록
     * EventLoop용 비동기 라우터를 사용하여 CompletableFuture 기반 처리
     */
    private void setupDefaultRoutes() { // private 메서드 - 기본 라우트 설정
        // 헬스 체크 엔드포인트 - 서버 상태 확인
        // CompletableFuture 기반으로 비동기 응답 처리
        router.get("/health", request -> { // Router.get() - GET 요청 라우트 등록, 람다로 AsyncRouteHandler 구현
            Map<String, Object> health = new HashMap<>(); // 헬스 정보를 담을 맵
            health.put("status", "UP"); // 서버 상태
            health.put("timestamp", LocalDateTime.now().toString()); // 현재 시간
            health.put("activeConnections", activeConnections.get()); // 활성 연결 수
            health.put("totalRequests", totalRequests.get()); // 총 요청 수
            health.put("totalResponses", totalResponses.get()); // 총 응답 수
            health.put("architecture", "EventLoop"); // 아키텍처 타입

            // EventLoop용 HttpResponse로 JSON 응답 반환
            String jsonResponse = convertToJson(health); // JSON 문자열 변환
            HttpResponse response = HttpResponse.ok(jsonResponse); // EventLoop용 HttpResponse.ok() 팩토리 메서드 사용

            // Content-Type을 JSON으로 설정 (SimpleHttpResponse에 withContentType 메서드가 있다고 가정)
            response.getHeaders().set("Content-Type", "application/json; charset=UTF-8"); // HttpHeaders.set() - Content-Type 헤더 설정

            return CompletableFuture.completedFuture(response); // CompletableFuture.completedFuture() - 즉시 완료된 Future 생성
        });

        // 메트릭 엔드포인트 - 성능 지표 확인
        router.get("/metrics", request -> { // 메트릭 조회 라우트
            Map<String, Object> metrics = new HashMap<>(); // 메트릭 정보를 담을 맵

            // 기본 메트릭들
            metrics.put("totalConnections", totalConnections.get()); // 총 연결 수
            metrics.put("activeConnections", activeConnections.get()); // 활성 연결 수
            metrics.put("totalRequests", totalRequests.get()); // 총 요청 수
            metrics.put("totalResponses", totalResponses.get()); // 총 응답 수
            metrics.put("totalErrors", totalErrors.get()); // 총 에러 수

            // 이벤트 루프 메트릭 (EventLoop에서 제공)
            if (eventLoop != null) { // 이벤트 루프가 초기화된 경우
                metrics.putAll(eventLoop.getMetrics()); // 이벤트 루프 메트릭 추가
            }

            // 계산된 메트릭들
            long requests = totalRequests.get(); // 총 요청 수
            if (requests > 0) { // 요청이 있는 경우 비율 계산
                metrics.put("errorRate", (double) totalErrors.get() / requests * 100.0); // 에러율 (%)
                metrics.put("responseRate", (double) totalResponses.get() / requests * 100.0); // 응답율 (%)
            }

            // EventLoop용 JSON 응답 생성
            String jsonResponse = convertToJson(metrics); // JSON 문자열 변환
            HttpResponse response = HttpResponse.ok(jsonResponse); // EventLoop용 HttpResponse 사용
            response.getHeaders().set("Content-Type", "application/json; charset=UTF-8"); // JSON Content-Type 설정

            return CompletableFuture.completedFuture(response); // 비동기 JSON 응답
        });

        // 서버 정보 엔드포인트 - 서버 구성 정보
        router.get("/info", request -> { // 서버 정보 조회 라우트
            Map<String, Object> info = new HashMap<>(); // 서버 정보를 담을 맵
            info.put("name", "EventLoopServer"); // 서버 이름
            info.put("version", "1.0"); // 서버 버전
            info.put("architecture", "Single-Threaded Event Loop"); // 아키텍처 설명
            info.put("port", port); // 서버 포트
            info.put("backlog", backlog); // 백로그 크기
            info.put("bufferSize", BUFFER_SIZE); // 버퍼 크기
            info.put("eventLoopRunning", eventLoop != null && eventLoop.isRunning()); // 이벤트 루프 실행 상태

            // EventLoop용 JSON 응답 생성
            String jsonResponse = convertToJson(info); // JSON 문자열 변환
            HttpResponse response = HttpResponse.ok(jsonResponse); // EventLoop용 HttpResponse 사용
            response.getHeaders().set("Content-Type", "application/json; charset=UTF-8"); // JSON Content-Type 설정

            return CompletableFuture.completedFuture(response); // 비동기 JSON 응답
        });

        // 간단한 Hello World 엔드포인트 - 기본 동작 확인용
        router.get("/hello", request -> { // Hello World 라우트
            HttpResponse response = HttpResponse.ok("Hello from EventLoopServer!"); // EventLoop용 HttpResponse.ok() 사용
            response.getHeaders().set("Content-Type", "text/plain; charset=UTF-8"); // 텍스트 Content-Type 설정

            return CompletableFuture.completedFuture(response); // 즉시 완료되는 응답
        });

        logger.fine("기본 라우트 설정 완료: /health, /metrics, /info, /hello");
    }

    /**
     * 서버 시작
     *
     * 이벤트 루프 서버 시작 과정:
     * 1. EventLoop 인스턴스 생성
     * 2. ServerSocketChannel 생성 및 설정
     * 3. EventLoop용 HTTP 채널 핸들러 생성
     * 4. 서버 채널을 이벤트 루프에 등록
     * 5. 이벤트 루프 시작
     *
     * @throws IOException 서버 시작 중 I/O 오류 발생 시
     * @throws IllegalStateException 서버가 이미 실행 중인 경우
     */
    public void start() throws IOException { // public 메서드 - 서버 시작
        // 중복 시작 방지
        if (!running.compareAndSet(false, true)) { // AtomicBoolean.compareAndSet() - 원자적 상태 변경
            throw new IllegalStateException("EventLoopServer가 이미 실행 중입니다");
        }

        try {
            // 1. EventLoop 인스턴스 생성
            // 이벤트 루프는 모든 I/O 이벤트를 처리하는 핵심 컴포넌트
            eventLoop = new EventLoop(); // EventLoop 생성 - NIO Selector 기반 이벤트 루프
            logger.info("EventLoop 인스턴스 생성됨");

            // 2. ServerSocketChannel 생성 및 설정
            // NIO 기반 논블로킹 서버 소켓 채널 생성
            serverChannel = ServerSocketChannel.open(); // ServerSocketChannel.open() - NIO 서버 채널 생성
            serverChannel.configureBlocking(false); // SocketChannel.configureBlocking() - 논블로킹 모드 설정 (필수)
            serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true); // SocketChannel.setOption() - 주소 재사용 허용

            // 포트 바인딩
            serverChannel.bind(new InetSocketAddress(port), backlog); // ServerSocketChannel.bind() - 지정된 포트에 바인딩
            logger.info(String.format("서버 채널이 포트 %d에 바인딩됨 (백로그: %d)", port, backlog));

            // 3. EventLoop용 HTTP 채널 핸들러 생성
            // HTTP 프로토콜 처리를 위한 전용 핸들러 (EventLoop용)
            httpHandler = new HttpChannelHandler(this, router); // EventLoop용 HTTP 핸들러 생성 - 서버와 라우터 참조 전달
            logger.info("EventLoop용 HTTP 채널 핸들러 생성됨");

            // 4. 서버 채널을 이벤트 루프에 등록
            // ACCEPT 이벤트에 대한 관심사 등록 (새로운 클라이언트 연결 수락)
            serverKey = eventLoop.registerChannel( // EventLoop.registerChannel() - 채널을 Selector에 등록
                    serverChannel, // 등록할 서버 채널
                    SelectionKey.OP_ACCEPT, // SelectionKey.OP_ACCEPT - ACCEPT 이벤트에 관심 등록
                    new ServerChannelHandler() // 서버 채널 전용 핸들러
            );
            logger.info("서버 채널이 이벤트 루프에 등록됨 (ACCEPT 이벤트)");

            // 5. 이벤트 루프 시작
            // 별도 스레드에서 이벤트 루프 실행
            eventLoop.start(); // EventLoop.start() - 이벤트 루프 스레드 시작
            logger.info("이벤트 루프 시작됨");

            // 서버 시작 완료 로그
            logger.info(String.format("🚀 EventLoopServer가 포트 %d에서 시작되었습니다", port));
            logger.info("서버 아키텍처: Single-Threaded Event Loop (NIO Selector 기반)");

        } catch (Exception e) { // 시작 실패 시 정리
            // 상태 복원
            running.set(false); // AtomicBoolean.set() - 실행 상태를 false로 복원

            // 리소스 정리
            cleanup(); // 부분적으로 초기화된 리소스들 정리

            // 원본 예외를 포장하여 재던짐
            throw new IOException("EventLoopServer 시작 실패", e);
        }
    }

    /**
     * 서버 중지
     *
     * 이벤트 루프 서버 중지 과정:
     * 1. 새로운 연결 수락 중지 (서버 채널 등록 해제)
     * 2. 활성 클라이언트 연결들 정리
     * 3. 이벤트 루프 중지
     * 4. 리소스 정리
     */
    public void stop() { // public 메서드 - 서버 중지
        // 이미 중지된 경우 무시
        if (!running.compareAndSet(true, false)) { // AtomicBoolean.compareAndSet() - 원자적 상태 변경 시도
            logger.info("EventLoopServer가 이미 중지되었거나 실행 중이 아닙니다");
            return; // early return
        }

        logger.info("EventLoopServer 중지를 시작합니다...");

        try {
            // 1. 서버 채널 등록 해제 (새로운 연결 수락 중지)
            if (serverKey != null) { // 서버 키가 존재하는 경우
                serverKey.cancel(); // SelectionKey.cancel() - Selector에서 키 등록 해제
                logger.fine("서버 채널 등록 해제됨");
            }

            // 2. 활성 클라이언트 연결들 정리
            closeActiveChannels(); // 모든 활성 채널 닫기

            // 3. 이벤트 루프 중지
            if (eventLoop != null) { // 이벤트 루프가 존재하는 경우
                eventLoop.stop(); // EventLoop.stop() - 이벤트 루프 중지
                logger.info("이벤트 루프 중지됨");
            }

            // 4. 최종 통계 로그 출력
            logFinalStatistics(); // 서버 종료 시 최종 통계 출력

            logger.info("EventLoopServer가 성공적으로 중지되었습니다");

        } catch (Exception e) { // 중지 과정 중 예외 처리
            logger.log(Level.SEVERE, "서버 중지 중 오류 발생", e);
        } finally { // 예외 발생 여부와 관계없이 최종 정리
            cleanup(); // 최종 리소스 정리
        }
    }

    /**
     * 활성 클라이언트 채널들 닫기
     * 그레이스풀 셧다운을 위해 연결된 모든 클라이언트에게 연결 종료 알림
     */
    private void closeActiveChannels() { // private 메서드 - 활성 채널 정리
        if (activeChannels.isEmpty()) { // Map.isEmpty() - 활성 채널이 없는 경우
            logger.fine("닫을 활성 채널이 없습니다");
            return; // early return
        }

        logger.info(String.format("활성 채널 %d개를 닫는 중...", activeChannels.size()));

        // 모든 활성 채널을 순회하며 정리
        for (Map.Entry<SocketChannel, ChannelContext> entry : activeChannels.entrySet()) { // Map.entrySet() - 채널과 컨텍스트 순회
            SocketChannel channel = entry.getKey(); // Map.Entry.getKey() - 소켓 채널
            ChannelContext context = entry.getValue(); // Map.Entry.getValue() - 채널 컨텍스트

            try {
                // 채널이 열려있는 경우에만 닫기
                if (channel.isOpen()) { // SocketChannel.isOpen() - 채널 열림 상태 확인
                    channel.close(); // SocketChannel.close() - 채널 닫기
                }
            } catch (IOException e) { // 채널 닫기 실패
                logger.log(Level.WARNING, "채널 닫기 실패: " + channel, e);
            }
        }

        // 활성 채널 맵 초기화
        activeChannels.clear(); // Map.clear() - 모든 엔트리 제거
        activeConnections.set(0); // AtomicLong.set() - 활성 연결 수 0으로 리셋

        logger.fine("모든 활성 채널이 닫혔습니다");
    }

    /**
     * 최종 통계 로그 출력
     * 서버 종료 시 전체 처리 통계 요약
     */
    private void logFinalStatistics() { // private 메서드 - 최종 통계 출력
        logger.info("=== EventLoopServer 최종 통계 ===");
        logger.info(String.format("총 연결: %d", totalConnections.get())); // AtomicLong.get() - 총 연결 수
        logger.info(String.format("총 요청: %d", totalRequests.get())); // 총 요청 수
        logger.info(String.format("총 응답: %d", totalResponses.get())); // 총 응답 수
        logger.info(String.format("총 에러: %d", totalErrors.get())); // 총 에러 수
        logger.info(String.format("최종 활성 연결: %d", activeConnections.get())); // 최종 활성 연결 수

        // 성공률 계산
        long requests = totalRequests.get(); // 총 요청 수
        if (requests > 0) { // 요청이 있었던 경우
            double successRate = (double) totalResponses.get() / requests * 100.0; // 성공률 계산
            double errorRate = (double) totalErrors.get() / requests * 100.0; // 에러율 계산
            logger.info(String.format("성공률: %.2f%%, 에러율: %.2f%%", successRate, errorRate));
        }

        // 이벤트 루프 메트릭
        if (eventLoop != null) { // 이벤트 루프가 존재하는 경우
            Map<String, Object> eventLoopMetrics = eventLoop.getMetrics(); // EventLoop.getMetrics() - 이벤트 루프 메트릭 조회
            logger.info(String.format("이벤트 루프 반복: %s, 처리된 이벤트: %s",
                    eventLoopMetrics.get("totalIterations"),
                    eventLoopMetrics.get("totalEvents")));
        }

        logger.info("=== 통계 종료 ===");
    }

    /**
     * 리소스 정리
     * 메모리 누수 방지를 위한 최종 정리 작업
     */
    private void cleanup() { // private 메서드 - 리소스 정리
        // 서버 채널 정리
        if (serverChannel != null && serverChannel.isOpen()) { // 서버 채널이 열려있는 경우
            try {
                serverChannel.close(); // ServerSocketChannel.close() - 서버 채널 닫기
            } catch (IOException e) {
                logger.log(Level.WARNING, "서버 채널 닫기 실패", e);
            }
        }

        // 활성 채널들 정리
        closeActiveChannels(); // 모든 활성 채널 닫기

        // 이벤트 루프 정리
        if (eventLoop != null) { // 이벤트 루프가 존재하는 경우
            if (eventLoop.isRunning()) { // EventLoop.isRunning() - 아직 실행 중인 경우
                eventLoop.stop(); // 강제 중지
            }
            eventLoop = null; // 참조 해제
        }

        // 핸들러 정리
        httpHandler = null; // HTTP 핸들러 참조 해제
        serverKey = null; // 서버 키 참조 해제

        logger.fine("EventLoopServer 리소스 정리 완료");
    }

    // ========== 메트릭 관리 메서드들 ==========

    /**
     * 새로운 연결 기록
     * 클라이언트 연결 시 호출되어 통계 업데이트
     */
    public void recordConnection() { // public 메서드 - 연결 기록 (패키지 내부에서 호출)
        totalConnections.incrementAndGet(); // AtomicLong.incrementAndGet() - 총 연결 수 원자적 증가
        activeConnections.incrementAndGet(); // 활성 연결 수 증가
        logger.fine(String.format("새 연결 기록됨 - 총: %d, 활성: %d",
                totalConnections.get(), activeConnections.get()));
    }

    /**
     * 연결 종료 기록
     * 클라이언트 연결 종료 시 호출되어 통계 업데이트
     */
    public void recordDisconnection() { // public 메서드 - 연결 해제 기록
        long active = activeConnections.decrementAndGet(); // AtomicLong.decrementAndGet() - 활성 연결 수 원자적 감소
        logger.fine(String.format("연결 종료 기록됨 - 활성: %d", active));
    }

    /**
     * HTTP 요청 기록
     * HTTP 요청 처리 시 호출되어 통계 업데이트
     */
    public void recordRequest() { // public 메서드 - 요청 기록
        totalRequests.incrementAndGet(); // 총 요청 수 증가

        // 주기적 메트릭 로깅
        long now = System.currentTimeMillis(); // System.currentTimeMillis() - 현재 시간 (밀리초)
        if (now - lastMetricsTime > METRICS_LOG_INTERVAL) { // 메트릭 로그 주기 확인
            logCurrentMetrics(); // 현재 메트릭 로그 출력
            lastMetricsTime = now; // 마지막 로그 시간 업데이트
        }
    }

    /**
     * HTTP 응답 기록
     * HTTP 응답 전송 시 호출되어 통계 업데이트
     */
    public void recordResponse() { // public 메서드 - 응답 기록
        totalResponses.incrementAndGet(); // 총 응답 수 증가
    }

    /**
     * 에러 기록
     * 에러 발생 시 호출되어 통계 업데이트
     */
    public void recordError() { // public 메서드 - 에러 기록
        totalErrors.incrementAndGet(); // 총 에러 수 증가
    }

    /**
     * 현재 메트릭 로그 출력
     * 주기적으로 서버 상태를 로그에 기록
     */
    private void logCurrentMetrics() { // private 메서드 - 현재 메트릭 로깅
        logger.info(String.format("EventLoop 메트릭 - 연결: %d/%d, 요청: %d, 응답: %d, 에러: %d",
                activeConnections.get(), // 현재 활성 연결
                totalConnections.get(), // 총 연결 수
                totalRequests.get(), // 총 요청 수
                totalResponses.get(), // 총 응답 수
                totalErrors.get())); // 총 에러 수
    }

    // ========== 채널 관리 메서드들 ==========

    /**
     * 새로운 클라이언트 채널 등록
     * 새로운 클라이언트 연결 시 채널을 관리 목록에 추가
     *
     * @param channel 클라이언트 소켓 채널
     * @param context 채널 컨텍스트
     */
    public void addActiveChannel(SocketChannel channel, ChannelContext context) { // public 메서드 - 활성 채널 추가
        activeChannels.put(channel, context); // Map.put() - 채널과 컨텍스트를 맵에 추가
        logger.fine(String.format("활성 채널 추가됨: %s (총 %d개)", channel, activeChannels.size()));
    }

    /**
     * 클라이언트 채널 제거
     * 클라이언트 연결 종료 시 관리 목록에서 제거
     *
     * @param channel 제거할 클라이언트 소켓 채널
     */
    public void removeActiveChannel(SocketChannel channel) { // public 메서드 - 활성 채널 제거
        ChannelContext removed = activeChannels.remove(channel); // Map.remove() - 맵에서 채널 제거
        if (removed != null) { // 제거된 채널이 있는 경우
            logger.fine(String.format("활성 채널 제거됨: %s (총 %d개)", channel, activeChannels.size()));
        }
    }

    // ========== 상태 조회 메서드들 ==========

    /**
     * 서버 실행 상태 확인
     *
     * @return 서버가 실행 중이면 true
     */
    public boolean isRunning() { // public getter - 서버 실행 상태 조회
        return running.get(); // AtomicBoolean.get() - 현재 상태 반환
    }

    /**
     * 라우터 반환
     * 외부에서 라우트를 추가할 수 있도록 접근 제공
     *
     * @return Router 인스턴스
     */
    public Router getRouter() { // public getter - 라우터 접근
        return router; // Router 인스턴스 반환
    }

    /**
     * 이벤트 루프 반환
     * 외부에서 이벤트 루프 상태를 모니터링할 수 있도록 접근 제공
     *
     * @return EventLoop 인스턴스
     */
    public EventLoop getEventLoop() { // public getter - 이벤트 루프 접근
        return eventLoop; // EventLoop 인스턴스 반환
    }

    /**
     * 서버 통계 반환
     * 모니터링과 운영에 사용되는 서버 메트릭 제공
     *
     * @return 서버 통계를 담은 Map
     */
    public Map<String, Object> getStatistics() { // public getter - 서버 통계 조회
        Map<String, Object> stats = new HashMap<>(); // 통계를 담을 맵 생성

        // 기본 통계
        stats.put("totalConnections", totalConnections.get()); // 총 연결 수
        stats.put("activeConnections", activeConnections.get()); // 활성 연결 수
        stats.put("totalRequests", totalRequests.get()); // 총 요청 수
        stats.put("totalResponses", totalResponses.get()); // 총 응답 수
        stats.put("totalErrors", totalErrors.get()); // 총 에러 수

        // 서버 설정
        stats.put("port", port); // 서버 포트
        stats.put("backlog", backlog); // 백로그 크기
        stats.put("bufferSize", BUFFER_SIZE); // 버퍼 크기
        stats.put("running", running.get()); // 실행 상태

        // 이벤트 루프 통계
        if (eventLoop != null) { // 이벤트 루프가 존재하는 경우
            stats.put("eventLoop", eventLoop.getMetrics()); // 이벤트 루프 메트릭 추가
        }

        // 계산된 메트릭
        long requests = totalRequests.get(); // 총 요청 수
        if (requests > 0) { // 요청이 있는 경우
            stats.put("successRate", (double) totalResponses.get() / requests * 100.0); // 성공률
            stats.put("errorRate", (double) totalErrors.get() / requests * 100.0); // 에러율
        }

        return stats; // 완성된 통계 맵 반환
    }

    // ========== 유틸리티 메서드들 ==========

    /**
     * Map을 간단한 JSON 문자열로 변환
     * 기본 엔드포인트에서 JSON 응답 생성에 사용
     *
     * @param map 변환할 Map 객체
     * @return JSON 형태의 문자열
     */
    private String convertToJson(Map<String, Object> map) { // private 메서드 - JSON 변환 유틸리티
        if (map == null || map.isEmpty()) { // null 체크와 빈 맵 확인
            return "{}"; // 빈 JSON 객체
        }

        StringBuilder json = new StringBuilder(); // StringBuilder - 효율적인 문자열 조합
        json.append("{"); // JSON 시작

        boolean first = true; // 첫 번째 요소 플래그
        for (Map.Entry<String, Object> entry : map.entrySet()) { // Map.entrySet() - 모든 엔트리 순회
            if (!first) { // 첫 번째가 아닌 경우
                json.append(","); // 쉼표 추가
            }
            first = false; // 첫 번째 플래그 해제

            // 키-값 쌍 추가
            json.append("\"").append(entry.getKey()).append("\":"); // 키 추가

            Object value = entry.getValue(); // Map.Entry.getValue() - 값 추출
            if (value instanceof String) { // instanceof - 문자열인지 타입 확인
                json.append("\"").append(value).append("\""); // 따옴표로 감싸기
            } else if (value instanceof Number || value instanceof Boolean) { // 숫자나 불린인 경우
                json.append(value); // 그대로 추가
            } else if (value == null) { // null인 경우
                json.append("null"); // JSON null
            } else { // 기타 객체인 경우
                json.append("\"").append(value.toString()).append("\""); // Object.toString() - 문자열로 변환
            }
        }

        json.append("}"); // JSON 종료
        return json.toString(); // StringBuilder.toString() - 완성된 JSON 문자열 반환
    }

    // ========== 내부 클래스들 ==========

    /**
     * 서버 채널 핸들러
     * ServerSocketChannel의 ACCEPT 이벤트를 처리하는 핸들러
     * 새로운 클라이언트 연결을 수락하고 적절히 등록
     */
    private class ServerChannelHandler implements EventLoop.ChannelHandler { // 내부 클래스 - 서버 채널 전용 핸들러

        /**
         * ACCEPT 이벤트 처리
         * 새로운 클라이언트 연결을 수락하고 이벤트 루프에 등록
         *
         * @param key ACCEPT 이벤트가 발생한 SelectionKey
         * @throws IOException I/O 처리 중 오류 발생 시
         */
        @Override
        public void handleEvent(SelectionKey key) throws IOException { // EventLoop.ChannelHandler 인터페이스 구현
            // ACCEPT 이벤트인지 확인
            if (!key.isAcceptable()) { // SelectionKey.isAcceptable() - ACCEPT 이벤트 확인
                logger.warning("서버 채널에 ACCEPT가 아닌 이벤트 발생: " + key.readyOps());
                return; // early return
            }

            // 서버 채널에서 새로운 클라이언트 연결 수락
            ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel(); // SelectionKey.channel() - 채널 캐스팅
            SocketChannel clientChannel = serverChannel.accept(); // ServerSocketChannel.accept() - 새 클라이언트 연결 수락

            if (clientChannel != null) { // 클라이언트 채널이 성공적으로 생성된 경우
                try {
                    // 클라이언트 채널 설정
                    configureClientChannel(clientChannel); // 클라이언트 채널 논블로킹 모드 설정

                    // 채널 컨텍스트 생성
                    ChannelContext context = new ChannelContext(clientChannel); // 채널별 상태 관리 컨텍스트 생성

                    // 이벤트 루프에 클라이언트 채널 등록 (READ 이벤트에 관심)
                    SelectionKey clientKey = eventLoop.registerChannel( // 클라이언트 채널을 이벤트 루프에 등록
                            clientChannel, // 클라이언트 소켓 채널
                            SelectionKey.OP_READ, // SelectionKey.OP_READ - READ 이벤트에 관심 등록
                            httpHandler // EventLoop용 HTTP 프로토콜 처리 핸들러
                    );

                    // SelectionKey에 ChannelContext를 attachment로 설정
                    clientKey.attach(context); // SelectionKey.attach() - 컨텍스트를 키에 첨부

                    // 채널 컨텍스트에 키 저장
                    context.setSelectionKey(clientKey); // 컨텍스트에 SelectionKey 저장

                    // 활성 채널 목록에 추가
                    addActiveChannel(clientChannel, context); // 채널 관리 목록에 추가

                    // 연결 통계 업데이트
                    recordConnection(); // 새 연결 기록

                    logger.fine(String.format("새 클라이언트 연결 수락됨: %s", clientChannel.getRemoteAddress()));

                } catch (IOException e) { // 클라이언트 채널 설정 실패
                    logger.log(Level.WARNING, "클라이언트 채널 설정 실패", e);

                    // 실패한 채널 정리
                    try {
                        clientChannel.close(); // 채널 닫기
                    } catch (IOException closeException) {
                        // 정리 과정에서 발생한 예외는 무시
                    }

                    recordError(); // 에러 기록
                }
            }
        }

        /**
         * 클라이언트 채널 설정
         * 새로 연결된 클라이언트 채널을 논블로킹 모드로 설정
         *
         * @param clientChannel 설정할 클라이언트 채널
         * @throws IOException 채널 설정 실패 시
         */
        private void configureClientChannel(SocketChannel clientChannel) throws IOException { // private 메서드 - 클라이언트 채널 설정
            // 논블로킹 모드 설정 (필수)
            clientChannel.configureBlocking(false); // SocketChannel.configureBlocking() - 논블로킹 모드 설정

            // TCP 옵션 설정
            clientChannel.setOption(StandardSocketOptions.TCP_NODELAY, true); // SocketChannel.setOption() - Nagle 알고리즘 비활성화 (응답성 향상)
            clientChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true); // Keep-Alive 활성화 (연결 유지)

            logger.fine("클라이언트 채널 설정 완료: 논블로킹 모드, TCP_NODELAY, SO_KEEPALIVE");
        }
    }

    /**
     * 채널 컨텍스트
     * 각 클라이언트 채널별 상태와 데이터를 관리하는 컨텍스트 클래스
     * HTTP 요청/응답 처리 과정에서 필요한 상태 정보를 보관
     */
    public static class ChannelContext { // public static 내부 클래스 - 채널별 컨텍스트
        private final SocketChannel channel; // final - 채널 참조 (변경 불가)
        private SelectionKey selectionKey; // 채널이 등록된 SelectionKey
        private ByteBuffer readBuffer; // 읽기용 바이트 버퍼
        private ByteBuffer writeBuffer; // 쓰기용 바이트 버퍼
        private HttpRequest currentRequest; // 현재 처리 중인 EventLoop용 HTTP 요청
        private boolean requestComplete; // 요청 파싱 완료 여부
        private final long createdTime; // 컨텍스트 생성 시간

        /**
         * 채널 컨텍스트 생성자
         *
         * @param channel 연관된 소켓 채널
         */
        public ChannelContext(SocketChannel channel) { // public 생성자
            this.channel = channel; // 채널 참조 저장
            this.readBuffer = ByteBuffer.allocate(BUFFER_SIZE); // ByteBuffer.allocate() - 읽기 버퍼 할당 (8KB)
            this.writeBuffer = ByteBuffer.allocate(BUFFER_SIZE * 2); // 쓰기 버퍼 할당 (16KB, 응답 데이터를 위해 더 크게)
            this.requestComplete = false; // 초기값: 요청 미완료
            this.createdTime = System.currentTimeMillis(); // System.currentTimeMillis() - 생성 시간 기록
        }

        // === Getter/Setter 메서드들 ===

        public SocketChannel getChannel() { return channel; } // 채널 반환

        public SelectionKey getSelectionKey() { return selectionKey; } // SelectionKey 반환
        public void setSelectionKey(SelectionKey selectionKey) { this.selectionKey = selectionKey; } // SelectionKey 설정

        public ByteBuffer getReadBuffer() { return readBuffer; } // 읽기 버퍼 반환
        public ByteBuffer getWriteBuffer() { return writeBuffer; } // 쓰기 버퍼 반환

        /**
         * 쓰기 버퍼 설정 (필요시 버퍼 크기 조정)
         * HttpChannelHandler에서 응답 크기에 따라 버퍼를 교체할 때 사용
         *
         * @param writeBuffer 새로운 쓰기 버퍼
         */
        public void setWriteBuffer(ByteBuffer writeBuffer) { // 쓰기 버퍼 설정 메서드 추가
            this.writeBuffer = writeBuffer; // 새로운 버퍼로 교체
        }

        public HttpRequest getCurrentRequest() { return currentRequest; } // 현재 요청 반환
        public void setCurrentRequest(HttpRequest currentRequest) { this.currentRequest = currentRequest; } // 현재 요청 설정

        public boolean isRequestComplete() { return requestComplete; } // 요청 완료 여부 반환
        public void setRequestComplete(boolean requestComplete) { this.requestComplete = requestComplete; } // 요청 완료 여부 설정

        public long getCreatedTime() { return createdTime; } // 생성 시간 반환

        /**
         * 버퍼 초기화
         * 새로운 요청/응답 처리를 위해 버퍼들을 초기화
         */
        public void resetBuffers() { // public 메서드 - 버퍼 초기화
            readBuffer.clear(); // ByteBuffer.clear() - 버퍼 포지션/리밋 초기화
            writeBuffer.clear(); // 쓰기 버퍼도 초기화
            requestComplete = false; // 요청 완료 상태 리셋
            currentRequest = null; // 현재 요청 초기화
        }

        /**
         * 컨텍스트 정리
         * 채널 종료 시 리소스 정리
         */
        public void cleanup() { // public 메서드 - 컨텍스트 정리
            // 버퍼들은 GC가 처리하므로 참조만 해제
            readBuffer = null; // 버퍼 참조 해제
            writeBuffer = null; // 버퍼 참조 해제
            currentRequest = null; // 요청 참조 해제

            // SelectionKey 정리
            if (selectionKey != null && selectionKey.isValid()) { // SelectionKey.isValid() - 키가 유효한 경우
                selectionKey.cancel(); // SelectionKey.cancel() - 키 등록 해제
            }
        }

        /**
         * 컨텍스트 정보를 문자열로 표현
         * 디버깅과 로깅에 활용
         */
        @Override
        public String toString() { // Object.toString() 재정의
            return String.format("ChannelContext{channel=%s, requestComplete=%s, age=%dms}",
                    channel, requestComplete, System.currentTimeMillis() - createdTime);
        }
    }
}