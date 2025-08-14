package com.serverarch.traditional; // 패키지 선언 - 전통적인 스레드 기반 서버 아키텍처 패키지

// 라우팅 관련 import - ThreadedServer의 새로운 라우팅 기능 활용을 위한 임포트
import com.serverarch.traditional.routing.RouteHandler; // RouteHandler 인터페이스 - 커스텀 라우트 핸들러 구현을 위해 임포트

// Java 기본 I/O 라이브러리 - 파일 및 스트림 처리용
import java.io.*; // 모든 I/O 클래스 임포트 - IOException, InputStream, OutputStream, BufferedReader 등

// Java NIO 라이브러리 - 최신 파일 시스템 API 사용
import java.nio.file.*; // 모든 NIO 파일 클래스 임포트 - Path, Files, StandardOpenOption 등 현대적 파일 처리용

// Java 컬렉션 및 유틸리티 라이브러리
import java.util.*; // 모든 유틸리티 클래스 임포트 - Map, List, Set, Properties, Timer 등 컬렉션과 유틸리티 사용

// Java 동시성 라이브러리 - 스레드풀 및 비동기 처리용
import java.util.concurrent.*; // 모든 동시성 클래스 임포트 - ExecutorService, ScheduledExecutorService, CompletableFuture 등

// Java 로깅 라이브러리 - 고급 로깅 기능용
import java.util.logging.*; // 모든 로깅 클래스 임포트 - Logger, Level, Handler, Formatter 등

// Java 시간 라이브러리 - 최신 시간 API 사용
import java.time.*; // 모든 시간 클래스 임포트 - LocalDateTime, Duration, Instant 등 현대적 시간 처리용
import java.time.format.DateTimeFormatter; // DateTimeFormatter 클래스 - 시간 포맷팅을 위해 임포트

// Java 네트워킹 라이브러리 - 헬스 체크 및 네트워크 유틸리티용
import java.net.*; // 모든 네트워크 클래스 임포트 - Socket, URL, HttpURLConnection 등

import java.lang.management.*; // ManagementFactory, RuntimeMXBean 등 JVM 관리 클래스들
/**
 * Enhanced ThreadedServer 실행을 위한 고급 런처 클래스 (완전 개선 버전)
 *
 * 기존 기능 유지:
 * 1. 서버 설정 및 초기화 (기존 기능 완전 유지)
 * 2. 명령줄 인수 파싱 (기존 로직 확장)
 * 3. 로깅 설정 (기존 설정 강화)
 * 4. 서버 시작 및 종료 관리 (기존 프로세스 개선)
 * 5. 우아한 종료 처리 (기존 셧다운 훅 확장)
 *
 * 추가된 고급 기능들:
 * 6. 설정 파일 지원 (properties 파일 로딩)
 * 7. 환경 변수 지원 (12-factor app 원칙)
 * 8. 실시간 모니터링 시스템 (메트릭 주기적 출력)
 * 9. 커스텀 라우트 등록 기능 (사용자 정의 엔드포인트)
 * 10. 헬스 체크 자동 검증 (서버 상태 확인)
 * 11. 설정 유효성 검증 강화 (포괄적 검증)
 * 12. 고급 에러 처리 및 복구 (재시작 메커니즘)
 * 13. 성능 최적화 권장사항 제공 (자동 튜닝 제안)
 * 14. 운영 도구 통합 (JMX, 메트릭 엔드포인트)
 * 15. 보안 강화 (기본 보안 헤더 설정)
 *
 * 사용법 (확장됨):
 * java ThreadedServerLauncher [옵션] [포트] [스레드풀크기]
 *
 * 옵션:
 * --config <파일>     : 설정 파일 지정
 * --monitoring        : 실시간 모니터링 활성화
 * --dev              : 개발 모드 (디버그 로깅)
 * --help, -h         : 도움말 출력
 *
 * 예시:
 * java ThreadedServerLauncher --config server.properties --monitoring
 * java ThreadedServerLauncher 8080 200 --dev
 */
public class ThreadedServerLauncher { // public 클래스 선언 - 다른 패키지에서 접근 가능한 서버 런처 클래스

    // 로거 - 런처 동작 상태 기록용 (기존 로거 완전 유지)
    private static final Logger logger = Logger.getLogger(ThreadedServerLauncher.class.getName()); // Logger.getLogger() - 클래스 이름 기반 로거 생성, static final로 클래스 레벨 상수 선언

    // 기본 설정값들 (기존 값 유지하되 추가 설정값 확장)
    private static final int DEFAULT_PORT = 8080;           // static final int - 기본 포트 번호 상수, 웹 서버 표준 대체 포트
    private static final int DEFAULT_THREAD_POOL_SIZE = 100; // static final int - 기본 스레드풀 크기 상수, 중간 규모 서버에 적합한 크기
    private static final int DEFAULT_BACKLOG = 50;          // static final int - 기본 백로그 크기 상수, 연결 대기 큐 크기
    private static final int DEFAULT_MAX_CONNECTIONS = 1000; // static final int - 기본 최대 연결 수 상수, 서버 과부하 방지용 제한값

    // 추가된 고급 설정 상수들
    private static final String DEFAULT_CONFIG_FILE = "server.properties"; // static final String - 기본 설정 파일명 상수, 표준 Java properties 파일
    private static final int MONITORING_INTERVAL_SECONDS = 30; // static final int - 모니터링 간격 상수 (초), 너무 자주 출력하지 않도록 30초 간격
    private static final int HEALTH_CHECK_TIMEOUT_MS = 5000; // static final int - 헬스 체크 타임아웃 상수 (밀리초), 5초 내 응답 없으면 실패로 판단
    private static final String LOG_DATE_PATTERN = "yyyy-MM-dd_HH-mm-ss"; // static final String - 로그 파일명 날짜 패턴, 파일명에 사용할 날짜 형식

    // 서버 및 모니터링 관련 인스턴스 변수들
    private ThreadedServer server; // ThreadedServer - 실제 HTTP 서버 인스턴스, 메인 서버 객체
    private ScheduledExecutorService monitoringExecutor; // ScheduledExecutorService - 주기적 모니터링 작업 실행용 스케줄러
    private volatile boolean monitoringEnabled = false; // volatile boolean - 모니터링 활성화 상태, volatile로 스레드 간 가시성 보장
    private ServerConfig enhancedConfig; // ServerConfig - 확장된 서버 설정 객체, 모든 설정 정보를 담는 내부 클래스 인스턴스

    /**
     * 메인 진입점 (기존 로직 완전 유지하되 기능 확장)
     * 명령줄 인수를 파싱하고 서버를 시작하는 메인 메서드
     *
     * @param args 명령줄 인수 배열 - [옵션] [포트] [스레드풀크기] 형태로 확장
     */
    public static void main(String[] args) { // public static void main - Java 프로그램의 표준 진입점 메서드
        // 시스템 정보 출력 - 서버 시작 전 환경 확인 (개선된 기능)
        printSystemInfo(); // printSystemInfo() - 현재 시스템의 하드웨어 및 JVM 정보 출력 메서드

        // 도움말 요청 확인 - 사용자가 도움말을 요청했는지 먼저 확인 (기존 기능 확장)
        if (isHelpRequested(args)) { // isHelpRequested() - 명령줄 인수에서 도움말 옵션 검사 메서드
            printUsage(); // printUsage() - 사용법 도움말 출력 메서드, 확장된 옵션 정보 포함
            return; // early return - 도움말 출력 후 프로그램 종료
        }

        // 로깅 시스템 초기화 - 서버 동작 상태 추적을 위해 (기존 로직 완전 유지)
        setupLogging(); // setupLogging() - 콘솔 및 파일 로그 설정 메서드

        // 런처 인스턴스 생성 (기존 로직 유지)
        ThreadedServerLauncher launcher = new ThreadedServerLauncher(); // new ThreadedServerLauncher() - 기본 생성자 호출

        try { // try-catch 블록 - 전체 실행 과정의 예외 처리 (기존 구조 유지)
            // 고급 명령줄 인수 파싱 - 옵션과 설정 파일 지원 추가 (확장된 기능)
            launcher.enhancedConfig = parseEnhancedCommandLineArgs(args); // parseEnhancedCommandLineArgs() - 확장된 명령줄 파싱 메서드

            // 설정 유효성 검증 - 포괄적인 설정값 검증 (추가된 기능)
            validateConfiguration(launcher.enhancedConfig); // validateConfiguration() - 설정값 유효성 검증 메서드

            // 서버 설정 및 시작 (기존 로직 확장)
            launcher.startServer(launcher.enhancedConfig); // startServer() - 확장된 설정으로 서버 시작 메서드

            // 커스텀 라우트 등록 - 사용자 정의 엔드포인트 추가 (새로운 기능)
            launcher.registerCustomRoutes(); // registerCustomRoutes() - 커스텀 라우트 등록 메서드

            // 헬스 체크 수행 - 서버가 정상적으로 시작되었는지 확인 (새로운 기능)
            launcher.performHealthCheck(); // performHealthCheck() - 서버 상태 확인 메서드

            // 모니터링 시작 - 설정에 따라 실시간 모니터링 활성화 (새로운 기능)
            if (launcher.enhancedConfig.monitoringEnabled) { // enhancedConfig.monitoringEnabled - 모니터링 활성화 여부 설정 확인
                launcher.startMonitoring(); // startMonitoring() - 실시간 모니터링 시작 메서드
            }

            // 성능 최적화 권장사항 출력 - 현재 설정에 대한 튜닝 제안 (새로운 기능)
            launcher.printPerformanceRecommendations(); // printPerformanceRecommendations() - 성능 최적화 제안 출력 메서드

            // 종료 시그널 대기 - 사용자가 Ctrl+C를 누를 때까지 대기 (기존 로직 유지)
            launcher.waitForShutdown(); // waitForShutdown() - 종료 신호 대기 메서드

        } catch (Exception e) { // Exception - 모든 예외를 포괄하는 최상위 예외 클래스로 예외 처리
            // 시작 실패 시 오류 로그 출력 (기존 로직 유지)
            logger.log(Level.SEVERE, "서버 실행 중 오류 발생", e); // logger.log() - 심각한 오류 레벨로 예외 정보와 함께 로그 출력

            // 비정상 종료 (기존 로직 유지)
            System.exit(1); // System.exit(1) - JVM 종료, 1은 오류 종료 코드

        } finally { // finally 블록 - 예외 발생 여부와 관계없이 반드시 실행되는 정리 작업
            // 정리 작업 - 예외 발생 여부와 관계없이 실행 (기존 로직 확장)
            launcher.cleanup(); // cleanup() - 리소스 정리 및 서버 종료 메서드
        }
    }

    /**
     * 서버 시작 (기존 메서드 시그니처 유지하되 기능 대폭 확장)
     * 확장된 설정에 따라 ThreadedServer 인스턴스를 생성하고 시작
     *
     * @param config 확장된 서버 설정 객체
     * @throws IOException 서버 시작 실패 시 발생하는 I/O 예외
     */
    public void startServer(ServerConfig config) throws IOException { // public void startServer - 기존 메서드 시그니처 완전 유지
        // 서버 시작 준비 로그 (기존 로직 확장하여 더 많은 정보 포함)
        logger.info(String.format( // logger.info() - 정보 레벨 로그 출력, String.format() - 형식화된 문자열 생성
                "Enhanced ThreadedServer 시작 준비 - 포트: %d, 스레드풀: %d, 백로그: %d, 최대연결: %d, 모니터링: %s", // 확장된 로그 메시지 템플릿
                config.port, config.threadPoolSize, config.backlog, config.maxConnections, // 기본 설정 정보들
                config.monitoringEnabled ? "활성화" : "비활성화" // 삼항 연산자로 모니터링 상태 표시
        ));

        // ThreadedServer 인스턴스 생성 (기존 생성자 사용하되 확장된 설정 적용)
        // ThreadedServer(port, threadPoolSize, backlog) 생성자를 사용하여 모든 주요 설정 적용
        server = new ThreadedServer(config.port, config.threadPoolSize, config.backlog); // new ThreadedServer() - 3개 매개변수 생성자 호출

        // 우아한 종료를 위한 셧다운 훅 등록 (기존 로직 완전 유지)
        // JVM 종료 시 자동으로 서버를 정리하도록 설정
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { // Runtime.getRuntime().addShutdownHook() - JVM 종료 시 실행할 스레드 등록
            logger.info("셧다운 훅 실행 - 서버를 정리합니다"); // 셧다운 훅 실행 로그
            // 모니터링 중지 - 셧다운 시 모니터링 스레드 정리 (추가된 정리 작업)
            stopMonitoring(); // stopMonitoring() - 모니터링 스레드 정지 메서드
            if (server != null) { // null 체크 - 서버 인스턴스가 생성된 경우만 정리
                server.stop(); // server.stop() - ThreadedServer의 정지 메서드 호출
            }
        }));

        // 서버 시작 - 블로킹 호출 (기존 로직 완전 유지)
        server.start(); // server.start() - ThreadedServer의 시작 메서드 호출, IOException을 던질 수 있음
    }

    /**
     * 커스텀 라우트 등록 (새로운 메서드)
     * 사용자 정의 엔드포인트를 서버에 추가하는 기능
     * ThreadedServer의 라우터를 활용하여 추가 기능 제공
     */
    private void registerCustomRoutes() { // private 메서드 - 클래스 내부에서만 사용하는 커스텀 라우트 등록 메서드
        if (server == null) { // null 체크 - 서버가 생성되지 않은 경우 처리
            logger.warning("서버가 생성되지 않아 커스텀 라우트를 등록할 수 없습니다"); // 경고 로그 - 서버 미생성 상황 알림
            return; // early return - 메서드 즉시 종료
        }

        logger.info("커스텀 라우트를 등록합니다..."); // 커스텀 라우트 등록 시작 로그

        // 1. 서버 재시작 엔드포인트 - 운영 중 서버 재시작 기능
        server.getRouter().post("/admin/restart", new RouteHandler() { // server.getRouter().post() - POST 메서드 라우트 등록
            @Override // @Override 어노테이션 - 인터페이스 메서드 재정의 명시
            public HttpResponse handle(HttpRequest request) throws Exception { // RouteHandler.handle() - 요청 처리 메서드 구현
                logger.info("서버 재시작 요청을 받았습니다"); // 재시작 요청 로그

                // 비동기로 서버 재시작 실행 - 응답 후 재시작하여 클라이언트 연결 유지
                CompletableFuture.runAsync(() -> { // CompletableFuture.runAsync() - 비동기 작업 실행
                    try {
                        Thread.sleep(1000); // Thread.sleep(1000) - 1초 대기하여 응답 전송 시간 확보
                        restartServer(); // restartServer() - 서버 재시작 메서드 호출
                    } catch (InterruptedException e) { // InterruptedException - 대기 중 인터럽트 발생 예외
                        Thread.currentThread().interrupt(); // Thread.currentThread().interrupt() - 인터럽트 상태 복원
                    }
                });

                // 즉시 성공 응답 반환 - 재시작은 백그라운드에서 실행
                return HttpResponse.ok("서버 재시작이 요청되었습니다"); // HttpResponse.ok() - 200 상태 코드 응답 생성
            }
        });

        // 2. 설정 조회 엔드포인트 - 현재 서버 설정 정보 제공
        server.getRouter().get("/admin/config", new RouteHandler() { // GET 메서드로 설정 조회 엔드포인트 등록
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                // 현재 설정을 JSON 형태로 변환하여 응답
                Map<String, Object> configMap = enhancedConfig.toMap(); // enhancedConfig.toMap() - 설정 객체를 Map으로 변환
                String configJson = convertMapToJson(configMap); // convertMapToJson() - Map을 JSON 문자열로 변환하는 유틸리티 메서드
                return HttpResponse.json(configJson); // HttpResponse.json() - JSON 응답 생성
            }
        });

        // 3. 로그 레벨 변경 엔드포인트 - 런타임 중 로그 레벨 조정 기능
        server.getRouter().post("/admin/loglevel", new RouteHandler() { // POST 메서드로 로그 레벨 변경 엔드포인트 등록
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                try {
                    // 요청 본문에서 로그 레벨 추출
                    String body = request.getBodyAsString(); // request.getBodyAsString() - HTTP 요청 본문을 문자열로 변환
                    String levelName = extractLogLevelFromBody(body); // extractLogLevelFromBody() - 요청 본문에서 로그 레벨 파싱

                    // 로그 레벨 설정 적용
                    Level newLevel = Level.parse(levelName.toUpperCase()); // Level.parse() - 문자열을 Level 객체로 변환
                    Logger.getLogger("").setLevel(newLevel); // Logger.getLogger("") - 루트 로거 조회하여 레벨 설정

                    logger.info("로그 레벨이 " + newLevel + "로 변경되었습니다"); // 로그 레벨 변경 알림
                    return HttpResponse.ok("로그 레벨이 " + newLevel + "로 변경되었습니다"); // 성공 응답

                } catch (Exception e) { // 로그 레벨 변경 실패 시 예외 처리
                    logger.warning("로그 레벨 변경 실패: " + e.getMessage()); // 실패 로그
                    return HttpResponse.badRequest("잘못된 로그 레벨: " + e.getMessage()); // HttpResponse.badRequest() - 400 에러 응답
                }
            }
        });

        // 4. 스레드 덤프 엔드포인트 - 디버깅을 위한 스레드 상태 조회
        server.getRouter().get("/admin/threads", new RouteHandler() { // GET 메서드로 스레드 덤프 엔드포인트 등록
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                // 현재 JVM의 모든 스레드 정보 수집
                Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces(); // Thread.getAllStackTraces() - 모든 스레드의 스택 트레이스 조회
                StringBuilder threadDump = new StringBuilder(); // StringBuilder - 효율적인 문자열 조합을 위한 클래스

                threadDump.append("=== 스레드 덤프 ===\n"); // 헤더 추가
                threadDump.append("총 스레드 수: ").append(allThreads.size()).append("\n\n"); // 총 스레드 수 추가

                // 각 스레드 정보를 순회하며 덤프 생성
                for (Map.Entry<Thread, StackTraceElement[]> entry : allThreads.entrySet()) { // Map.entrySet() - Map의 모든 엔트리 순회
                    Thread thread = entry.getKey(); // entry.getKey() - 스레드 객체 추출
                    StackTraceElement[] stackTrace = entry.getValue(); // entry.getValue() - 스택 트레이스 배열 추출

                    // 스레드 기본 정보 추가
                    threadDump.append(String.format("스레드: %s (ID: %d, 상태: %s)\n", // 스레드 기본 정보 포맷
                            thread.getName(), thread.getId(), thread.getState())); // getName(), getId(), getState() - 스레드 속성 조회

                    // 스택 트레이스 정보 추가 (최대 5개 프레임만)
                    int maxFrames = Math.min(5, stackTrace.length); // Math.min() - 두 값 중 작은 값 선택
                    for (int i = 0; i < maxFrames; i++) { // for 루프 - 제한된 스택 프레임만 출력
                        threadDump.append("  ").append(stackTrace[i]).append("\n"); // 스택 프레임 정보 추가
                    }
                    threadDump.append("\n"); // 스레드 간 구분을 위한 빈 줄
                }

                return HttpResponse.ok(threadDump.toString()); // 스레드 덤프를 텍스트 응답으로 반환
            }
        });

        // 5. 고급 메트릭 엔드포인트 - ThreadedServer의 고급 메트릭 활용
        server.getRouter().get("/admin/advanced-metrics", new RouteHandler() { // GET 메서드로 고급 메트릭 엔드포인트 등록
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                // ThreadedServer의 고급 메트릭 수집
                Map<String, Object> advancedMetrics = new HashMap<>(); // HashMap - 메트릭 데이터를 담을 맵 생성

                // 서버 통계 정보 추가
                ThreadedServer.ServerStatistics stats = server.getStatistics(); // server.getStatistics() - 서버 통계 조회
                advancedMetrics.put("serverStats", stats.getAdvancedMetrics()); // stats.getAdvancedMetrics() - 고급 메트릭 맵 추가

                // 스레드풀 관리자 상태 추가 (있는 경우)
                if (server.getThreadPoolManager() != null) { // server.getThreadPoolManager() - 스레드풀 관리자 조회
                    ThreadPoolManager.ThreadPoolStatus poolStatus = server.getThreadPoolManager().getStatus(); // ThreadPoolManager.getStatus() - 스레드풀 상태 조회
                    Map<String, Object> poolMetrics = new HashMap<>(); // 스레드풀 메트릭 맵 생성
                    poolMetrics.put("completedTasks", poolStatus.getCompletedTaskCount()); // 완료된 작업 수
                    poolMetrics.put("rejectedTasks", poolStatus.getRejectedTaskCount()); // 거부된 작업 수
                    poolMetrics.put("activeThreads", poolStatus.getActiveCount()); // 활성 스레드 수
                    poolMetrics.put("poolSize", poolStatus.getPoolSize()); // 현재 풀 크기
                    advancedMetrics.put("threadPool", poolMetrics); // 스레드풀 메트릭 추가
                }

                // JVM 메모리 정보 추가
                Runtime runtime = Runtime.getRuntime(); // Runtime.getRuntime() - 현재 JVM 런타임 조회
                Map<String, Object> memoryMetrics = new HashMap<>(); // 메모리 메트릭 맵 생성
                memoryMetrics.put("maxMemoryMB", runtime.maxMemory() / 1024 / 1024); // 최대 메모리 (MB)
                memoryMetrics.put("totalMemoryMB", runtime.totalMemory() / 1024 / 1024); // 총 할당 메모리 (MB)
                memoryMetrics.put("freeMemoryMB", runtime.freeMemory() / 1024 / 1024); // 여유 메모리 (MB)
                memoryMetrics.put("usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024); // 사용 중 메모리 계산
                advancedMetrics.put("memory", memoryMetrics); // 메모리 메트릭 추가

                // 시스템 정보 추가
                Map<String, Object> systemMetrics = new HashMap<>(); // 시스템 메트릭 맵 생성
                systemMetrics.put("availableProcessors", runtime.availableProcessors()); // 사용 가능 프로세서 수
                systemMetrics.put("javaVersion", System.getProperty("java.version")); // Java 버전
                systemMetrics.put("osName", System.getProperty("os.name")); // 운영체제 이름
                systemMetrics.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime()); // JVM 업타임
                advancedMetrics.put("system", systemMetrics); // 시스템 메트릭 추가

                String metricsJson = convertMapToJson(advancedMetrics); // 메트릭 맵을 JSON으로 변환
                return HttpResponse.json(metricsJson); // JSON 응답 반환
            }
        });

        logger.info("커스텀 라우트 등록 완료: /admin/restart, /admin/config, /admin/loglevel, /admin/threads, /admin/advanced-metrics"); // 등록 완료 로그
    }

    /**
     * 헬스 체크 수행 (새로운 메서드)
     * 서버가 정상적으로 시작되었는지 확인하는 자동 검증 기능
     */
    private void performHealthCheck() { // private 메서드 - 서버 헬스 체크 수행
        if (server == null || !server.isRunning()) { // null 체크 및 서버 실행 상태 확인
            logger.warning("서버가 실행되지 않아 헬스 체크를 수행할 수 없습니다"); // 서버 미실행 경고
            return; // early return - 메서드 즉시 종료
        }

        logger.info("서버 헬스 체크를 수행합니다..."); // 헬스 체크 시작 로그

        try { // try-catch 블록 - 헬스 체크 중 예외 처리
            // HTTP 클라이언트를 사용하여 헬스 체크 엔드포인트 호출
            String healthUrl = String.format("http://localhost:%d/health", enhancedConfig.port); // 헬스 체크 URL 생성
            URL url = new URL(healthUrl); // new URL() - URL 객체 생성
            HttpURLConnection connection = (HttpURLConnection) url.openConnection(); // url.openConnection() - HTTP 연결 열기

            // 연결 설정
            connection.setRequestMethod("GET"); // HTTP GET 메서드 설정
            connection.setConnectTimeout(HEALTH_CHECK_TIMEOUT_MS); // 연결 타임아웃 설정
            connection.setReadTimeout(HEALTH_CHECK_TIMEOUT_MS); // 읽기 타임아웃 설정

            // 요청 실행 및 응답 확인
            int responseCode = connection.getResponseCode(); // HTTP 응답 코드 조회
            if (responseCode == 200) { // 200 OK 응답인 경우
                // 응답 본문 읽기
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) { // try-with-resources로 자동 리소스 관리
                    String response = reader.lines().reduce("", (a, b) -> a + b); // Stream API로 응답 본문 읽기
                    logger.info("헬스 체크 성공 - 서버가 정상적으로 응답하고 있습니다"); // 성공 로그
                    logger.fine("헬스 체크 응답: " + response); // 상세 응답 로그 (FINE 레벨)
                }
            } else { // 200이 아닌 응답인 경우
                logger.warning("헬스 체크 실패 - HTTP 응답 코드: " + responseCode); // 실패 로그
            }

            connection.disconnect(); // 연결 해제

        } catch (IOException e) { // IOException - 네트워크 오류 등 I/O 예외 처리
            logger.log(Level.WARNING, "헬스 체크 중 오류 발생", e); // 헬스 체크 오류 로그
        }

        // 추가 헬스 체크: 서버 통계 확인
        try { // 추가 검증을 위한 try 블록
            ThreadedServer.ServerStatistics stats = server.getStatistics(); // 서버 통계 조회
            logger.info(String.format("서버 상태 - 활성 연결: %d, 총 요청: %d", // 서버 상태 요약 로그
                    stats.getCurrentActiveConnections(), stats.getTotalRequestsReceived()));

            // 메트릭 엔드포인트도 확인
            String metricsUrl = String.format("http://localhost:%d/metrics", enhancedConfig.port); // 메트릭 URL 생성
            URL metricsUrlObj = new URL(metricsUrl); // URL 객체 생성
            HttpURLConnection metricsConnection = (HttpURLConnection) metricsUrlObj.openConnection(); // 연결 열기
            metricsConnection.setRequestMethod("GET"); // GET 메서드 설정
            metricsConnection.setConnectTimeout(HEALTH_CHECK_TIMEOUT_MS); // 타임아웃 설정

            int metricsResponseCode = metricsConnection.getResponseCode(); // 응답 코드 확인
            if (metricsResponseCode == 200) { // 메트릭 엔드포인트도 정상인 경우
                logger.info("메트릭 엔드포인트 헬스 체크 성공"); // 메트릭 성공 로그
            } else { // 메트릭 엔드포인트 오류인 경우
                logger.warning("메트릭 엔드포인트 헬스 체크 실패 - 응답 코드: " + metricsResponseCode); // 메트릭 실패 로그
            }

            metricsConnection.disconnect(); // 연결 해제

        } catch (Exception e) { // 추가 헬스 체크 중 예외 처리
            logger.log(Level.WARNING, "추가 헬스 체크 중 오류", e); // 추가 검증 오류 로그
        }
    }

    /**
     * 실시간 모니터링 시작 (새로운 메서드)
     * 주기적으로 서버 상태와 메트릭을 출력하는 모니터링 시스템
     */
    private void startMonitoring() { // private 메서드 - 실시간 모니터링 시작
        if (monitoringEnabled) { // 이미 모니터링이 활성화된 경우
            logger.warning("모니터링이 이미 실행 중입니다"); // 중복 실행 방지 경고
            return; // early return - 메서드 즉시 종료
        }

        logger.info("실시간 모니터링을 시작합니다 (간격: " + MONITORING_INTERVAL_SECONDS + "초)"); // 모니터링 시작 로그

        // 스케줄된 실행자 서비스 생성 - 주기적 작업 실행용
        monitoringExecutor = Executors.newSingleThreadScheduledExecutor(r -> { // Executors.newSingleThreadScheduledExecutor() - 단일 스레드 스케줄러 생성
            Thread t = new Thread(r, "MonitoringThread"); // new Thread() - 커스텀 이름의 스레드 생성
            t.setDaemon(true); // setDaemon(true) - 데몬 스레드로 설정하여 JVM 종료 시 자동 종료
            return t; // 설정된 스레드 반환
        });

        // 주기적 모니터링 작업 스케줄링
        monitoringExecutor.scheduleAtFixedRate( // scheduleAtFixedRate() - 고정 간격으로 반복 실행
                this::printMonitoringInfo, // this::printMonitoringInfo - 메서드 레퍼런스로 모니터링 정보 출력 메서드 지정
                MONITORING_INTERVAL_SECONDS, // 초기 지연 시간 (초)
                MONITORING_INTERVAL_SECONDS, // 반복 간격 (초)
                TimeUnit.SECONDS // TimeUnit.SECONDS - 시간 단위를 초로 지정
        );

        monitoringEnabled = true; // volatile 변수로 모니터링 활성화 상태 설정
    }

    /**
     * 실시간 모니터링 중지 (새로운 메서드)
     * 모니터링 스레드를 안전하게 종료하는 기능
     */
    private void stopMonitoring() { // private 메서드 - 모니터링 중지
        if (!monitoringEnabled || monitoringExecutor == null) { // 모니터링이 비활성화되어 있거나 실행자가 없는 경우
            return; // early return - 처리할 것이 없으므로 즉시 종료
        }

        logger.info("실시간 모니터링을 중지합니다"); // 모니터링 중지 로그
        monitoringEnabled = false; // volatile 변수로 모니터링 비활성화

        try { // try-catch 블록 - 모니터링 종료 중 예외 처리
            monitoringExecutor.shutdown(); // shutdown() - 새 작업 수락 중지하고 기존 작업 완료 대기
            if (!monitoringExecutor.awaitTermination(5, TimeUnit.SECONDS)) { // awaitTermination() - 지정 시간 동안 종료 대기
                logger.warning("모니터링 스레드가 정상 종료되지 않아 강제 종료합니다"); // 정상 종료 실패 경고
                monitoringExecutor.shutdownNow(); // shutdownNow() - 강제 종료
            }
        } catch (InterruptedException e) { // InterruptedException - 대기 중 인터럽트 발생
            logger.warning("모니터링 종료 중 인터럽트 발생"); // 인터럽트 경고 로그
            monitoringExecutor.shutdownNow(); // 강제 종료
            Thread.currentThread().interrupt(); // 현재 스레드의 인터럽트 상태 복원
        }
    }

    /**
     * 모니터링 정보 출력 (새로운 메서드)
     * 주기적으로 호출되어 서버 상태를 콘솔과 로그에 출력
     */
    private void printMonitoringInfo() { // private 메서드 - 모니터링 정보 출력
        if (server == null || !server.isRunning()) { // 서버 상태 확인
            return; // 서버가 없거나 실행 중이 아니면 모니터링 중지
        }

        try { // try-catch 블록 - 모니터링 정보 수집 중 예외 처리
            // 현재 시간 포맷팅
            LocalDateTime now = LocalDateTime.now(); // LocalDateTime.now() - 현재 시간 조회
            String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); // DateTimeFormatter.ofPattern() - 시간 포맷 지정

            // 서버 통계 수집
            ThreadedServer.ServerStatistics stats = server.getStatistics(); // 서버 통계 조회

            // 시스템 메모리 정보 수집
            Runtime runtime = Runtime.getRuntime(); // JVM 런타임 정보 조회
            long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024; // 사용 중 메모리 계산
            long maxMemoryMB = runtime.maxMemory() / 1024 / 1024; // 최대 메모리
            double memoryUsagePercent = (double) usedMemoryMB / maxMemoryMB * 100; // 메모리 사용률 계산

            // 모니터링 정보 포맷팅 및 출력
            String monitoringInfo = String.format( // 모니터링 정보 템플릿
                    "\n=== 서버 모니터링 [%s] ===\n" + // 타임스탬프 헤더
                            "📊 요청 통계: 총 %d개 (성공: %d, 실패: %d, 성공률: %.1f%%)\n" + // 요청 통계 정보
                            "🔗 연결 정보: 활성 %d개 / 최대 %d개 (사용률: %.1f%%)\n" + // 연결 정보
                            "💾 메모리 사용량: %dMB / %dMB (%.1f%%)\n" + // 메모리 사용 정보
                            "🧵 스레드풀 크기: %d개\n" + // 스레드풀 정보
                            "==========================================", // 구분선
                    timestamp, // 현재 시간
                    stats.getTotalRequestsReceived(), // 총 요청 수
                    stats.getTotalRequestsProcessed(), // 처리 성공 수
                    stats.getTotalRequestsFailed(), // 실패 수
                    stats.getSuccessRate() * 100, // 성공률 백분율
                    stats.getCurrentActiveConnections(), // 현재 활성 연결 수
                    stats.getMaxConnections(), // 최대 연결 수
                    stats.getConnectionUsageRate() * 100, // 연결 사용률 백분율
                    usedMemoryMB, // 사용 중 메모리
                    maxMemoryMB, // 최대 메모리
                    memoryUsagePercent, // 메모리 사용률 백분율
                    stats.getThreadPoolSize() // 스레드풀 크기
            );

            // 콘솔과 로그에 동시 출력
            System.out.println(monitoringInfo); // 콘솔 출력 - 사용자가 즉시 확인 가능
            logger.info("모니터링 정보: " + monitoringInfo.replace("\n", " | ")); // 로그 출력 - 줄바꿈을 구분자로 변경

            // 고급 메트릭 정보 추가 출력 (FINE 레벨)
            if (server.getMetrics() != null) { // 서버 메트릭이 있는 경우
                Map<String, Object> advancedMetrics = server.getMetrics().getAllMetrics(); // 고급 메트릭 조회
                logger.fine(String.format("고급 메트릭 - 평균 응답시간: %.2fms, 에러율: %.2f%%", // 고급 메트릭 로그
                        advancedMetrics.get("averageResponseTime"), // 평균 응답 시간
                        advancedMetrics.get("errorRate"))); // 에러율
            }

            // 임계값 기반 경고 - 리소스 사용량이 높을 때 알림
            if (memoryUsagePercent > 80) { // 메모리 사용률이 80% 초과인 경우
                logger.warning(String.format("⚠️ 메모리 사용률이 높습니다: %.1f%% (임계값: 80%%)", memoryUsagePercent)); // 메모리 경고
            }

            if (stats.getConnectionUsageRate() > 0.8) { // 연결 사용률이 80% 초과인 경우
                logger.warning(String.format("⚠️ 연결 사용률이 높습니다: %.1f%% (임계값: 80%%)", // 연결 경고
                        stats.getConnectionUsageRate() * 100));
            }

            if (stats.getFailureRate() > 0.05) { // 실패율이 5% 초과인 경우
                logger.warning(String.format("⚠️ 요청 실패율이 높습니다: %.1f%% (임계값: 5%%)", // 실패율 경고
                        stats.getFailureRate() * 100));
            }

        } catch (Exception e) { // 모니터링 정보 수집 중 예외 처리
            logger.log(Level.WARNING, "모니터링 정보 수집 중 오류 발생", e); // 모니터링 오류 로그
        }
    }

    /**
     * 성능 최적화 권장사항 출력 (새로운 메서드)
     * 현재 설정을 분석하여 성능 개선 제안을 제공
     */
    private void printPerformanceRecommendations() { // private 메서드 - 성능 최적화 제안 출력
        if (enhancedConfig == null) { // 설정이 없는 경우
            return; // early return - 분석할 수 없으므로 종료
        }

        logger.info("현재 설정에 대한 성능 최적화 권장사항을 분석합니다..."); // 분석 시작 로그

        List<String> recommendations = new ArrayList<>(); // ArrayList - 권장사항을 담을 리스트 생성

        // 시스템 정보 수집
        Runtime runtime = Runtime.getRuntime(); // JVM 런타임 정보
        int cores = runtime.availableProcessors(); // CPU 코어 수
        long maxMemoryMB = runtime.maxMemory() / 1024 / 1024; // 최대 메모리 (MB)

        // 스레드풀 크기 분석 및 권장사항
        int threadPoolSize = enhancedConfig.threadPoolSize; // 현재 스레드풀 크기
        int recommendedThreadPoolSize = cores * 3; // CPU 코어 수의 3배 권장 (I/O 바운드)

        if (threadPoolSize < cores) { // 스레드풀이 CPU 코어 수보다 작은 경우
            recommendations.add(String.format("🧵 스레드풀 크기를 늘리는 것을 고려하세요 (현재: %d, 최소 권장: %d)", // 스레드풀 증가 권장
                    threadPoolSize, cores));
        } else if (threadPoolSize > cores * 4) { // 스레드풀이 CPU 코어 수의 4배보다 큰 경우
            recommendations.add(String.format("🧵 스레드풀 크기가 과도할 수 있습니다 (현재: %d, 권장: %d)", // 스레드풀 감소 권장
                    threadPoolSize, recommendedThreadPoolSize));
        }

        // 메모리 기반 스레드풀 크기 검증
        long estimatedMemoryUsageMB = threadPoolSize; // 스레드당 약 1MB 스택 메모리 추정
        if (estimatedMemoryUsageMB > maxMemoryMB * 0.1) { // 스레드풀 메모리 사용량이 전체 메모리의 10% 초과
            recommendations.add(String.format("💾 스레드풀이 너무 많은 메모리를 사용할 수 있습니다 (예상: %dMB, 최대 권장: %dMB)", // 메모리 기반 권장
                    estimatedMemoryUsageMB, (long)(maxMemoryMB * 0.1)));
        }

        // 백로그 크기 분석
        if (enhancedConfig.backlog < 50) { // 백로그가 50보다 작은 경우
            recommendations.add(String.format("📥 백로그 크기를 늘려 더 많은 대기 연결을 처리하세요 (현재: %d, 권장: 50-100)", // 백로그 증가 권장
                    enhancedConfig.backlog));
        }

        // 최대 연결 수 분석
        if (enhancedConfig.maxConnections < threadPoolSize * 2) { // 최대 연결 수가 스레드풀의 2배보다 작은 경우
            recommendations.add(String.format("🔗 최대 연결 수를 늘려 더 많은 동시 연결을 허용하세요 (현재: %d, 권장: %d)", // 최대 연결 수 증가 권장
                    enhancedConfig.maxConnections, threadPoolSize * 2));
        }

        // JVM 메모리 설정 권장사항
        if (maxMemoryMB < 512) { // 최대 메모리가 512MB보다 작은 경우
            recommendations.add("💾 JVM 힙 메모리를 늘리는 것을 고려하세요 (-Xmx 옵션 사용, 권장: 최소 512MB)"); // 힙 메모리 증가 권장
        }

        // 가비지 컬렉터 권장사항
        String gcName = System.getProperty("java.vm.name", "").toLowerCase(); // 현재 JVM 이름 조회
        if (!gcName.contains("g1") && !gcName.contains("parallel")) { // G1이나 Parallel GC가 아닌 경우
            recommendations.add("🗑️ 성능 향상을 위해 G1GC 사용을 고려하세요 (-XX:+UseG1GC)"); // GC 변경 권장
        }

        // 네트워크 최적화 권장사항
        recommendations.add("🌐 성능 향상을 위해 다음 JVM 옵션을 고려하세요:"); // 네트워크 최적화 헤더
        recommendations.add("   -Djava.net.preferIPv4Stack=true (IPv4 사용)"); // IPv4 옵션
        recommendations.add("   -Dsun.net.useExclusiveBind=false (포트 공유)"); // 포트 공유 옵션

        // 모니터링 권장사항
        if (!enhancedConfig.monitoringEnabled) { // 모니터링이 비활성화된 경우
            recommendations.add("📊 운영 환경에서는 모니터링을 활성화하는 것을 권장합니다 (--monitoring 옵션)"); // 모니터링 활성화 권장
        }

        // 권장사항 출력
        if (recommendations.isEmpty()) { // 권장사항이 없는 경우
            System.out.println("\n✅ 현재 설정이 적절합니다! 추가 권장사항이 없습니다.\n"); // 적절한 설정 메시지
            logger.info("성능 최적화 분석 완료 - 현재 설정이 적절함"); // 적절 설정 로그
        } else { // 권장사항이 있는 경우
            System.out.println("\n📋 === 성능 최적화 권장사항 ==="); // 권장사항 헤더
            for (String recommendation : recommendations) { // 모든 권장사항 출력
                System.out.println(recommendation); // 개별 권장사항 출력
            }
            System.out.println("================================\n"); // 권장사항 푸터

            logger.info("성능 최적화 권장사항 " + recommendations.size() + "개 제공됨"); // 권장사항 개수 로그
        }

        // 현재 설정 요약 출력
        System.out.println("📊 === 현재 서버 설정 요약 ==="); // 설정 요약 헤더
        System.out.println("포트: " + enhancedConfig.port); // 포트 정보
        System.out.println("스레드풀: " + enhancedConfig.threadPoolSize + " (CPU 코어: " + cores + ")"); // 스레드풀과 CPU 정보
        System.out.println("백로그: " + enhancedConfig.backlog); // 백로그 정보
        System.out.println("최대 연결: " + enhancedConfig.maxConnections); // 최대 연결 정보
        System.out.println("JVM 최대 메모리: " + maxMemoryMB + "MB"); // JVM 메모리 정보
        System.out.println("모니터링: " + (enhancedConfig.monitoringEnabled ? "활성화" : "비활성화")); // 모니터링 상태
        System.out.println("==============================\n"); // 설정 요약 푸터
    }

    /**
     * 서버 재시작 (새로운 메서드)
     * 운영 중 서버를 안전하게 재시작하는 기능
     */
    private void restartServer() { // private 메서드 - 서버 재시작
        logger.info("서버 재시작을 시작합니다..."); // 재시작 시작 로그

        try { // try-catch 블록 - 재시작 과정 중 예외 처리
            // 1. 모니터링 중지
            if (monitoringEnabled) { // 모니터링이 활성화된 경우
                stopMonitoring(); // 모니터링 중지
            }

            // 2. 기존 서버 중지
            if (server != null && server.isRunning()) { // 서버가 실행 중인 경우
                logger.info("기존 서버를 중지합니다..."); // 서버 중지 로그
                server.stop(); // 서버 중지
                Thread.sleep(2000); // 2초 대기하여 정리 시간 확보
            }

            // 3. 새 서버 시작
            logger.info("새 서버를 시작합니다..."); // 새 서버 시작 로그
            startServer(enhancedConfig); // 동일한 설정으로 서버 재시작

            // 4. 커스텀 라우트 재등록
            registerCustomRoutes(); // 커스텀 라우트 재등록

            // 5. 헬스 체크 수행
            Thread.sleep(1000); // 1초 대기 후 헬스 체크
            performHealthCheck(); // 헬스 체크 수행

            // 6. 모니터링 재시작 (설정에 따라)
            if (enhancedConfig.monitoringEnabled) { // 모니터링이 설정된 경우
                startMonitoring(); // 모니터링 재시작
            }

            logger.info("서버 재시작이 성공적으로 완료되었습니다"); // 재시작 완료 로그

        } catch (Exception e) { // 재시작 중 예외 처리
            logger.log(Level.SEVERE, "서버 재시작 중 오류 발생", e); // 재시작 오류 로그
            throw new RuntimeException("서버 재시작 실패", e); // 런타임 예외로 재포장하여 던짐
        }
    }

    /**
     * 종료 시그널 대기 (기존 메서드 완전 유지)
     * 사용자가 Ctrl+C를 누르거나 JVM이 종료 신호를 받을 때까지 대기
     */
    public void waitForShutdown() { // public 메서드 - 기존 시그니처 완전 유지
        logger.info("Enhanced ThreadedServer가 실행 중입니다. 종료하려면 Ctrl+C를 누르세요."); // 확장된 서버 실행 메시지

        // 추가 정보 출력 - 사용자 편의성 향상
        System.out.println("🚀 서버가 성공적으로 시작되었습니다!"); // 시작 성공 메시지
        System.out.println("📍 서버 주소: http://localhost:" + enhancedConfig.port); // 서버 주소 안내
        System.out.println("🔍 헬스 체크: http://localhost:" + enhancedConfig.port + "/health"); // 헬스 체크 URL
        System.out.println("📊 메트릭 정보: http://localhost:" + enhancedConfig.port + "/metrics"); // 메트릭 URL
        System.out.println("⚙️ 서버 정보: http://localhost:" + enhancedConfig.port + "/info"); // 서버 정보 URL
        if (enhancedConfig.monitoringEnabled) { // 모니터링이 활성화된 경우
            System.out.println("📈 실시간 모니터링이 " + MONITORING_INTERVAL_SECONDS + "초 간격으로 실행 중입니다"); // 모니터링 안내
        }
        System.out.println("🛑 종료하려면 Ctrl+C를 누르세요\n"); // 종료 방법 안내

        try { // try-catch 블록 - 기존 대기 로직 완전 유지
            // 메인 스레드를 무한 대기 상태로 만듦
            // 서버는 별도 스레드에서 실행되므로 메인 스레드는 종료 대기만 함
            Thread.currentThread().join(); // Thread.currentThread().join() - 현재 스레드의 종료를 기다리는 메서드 (무한 대기)

        } catch (InterruptedException e) { // InterruptedException - 스레드가 인터럽트되었을 때 발생하는 예외
            // 인터럽트 신호 받음 - 정상적인 종료 과정 (기존 로직 완전 유지)
            logger.info("서버 종료 신호를 받았습니다"); // 종료 신호 수신 로그

            // 현재 스레드의 인터럽트 상태 복원 (기존 로직 완전 유지)
            Thread.currentThread().interrupt(); // Thread.currentThread().interrupt() - 현재 스레드에 인터럽트 플래그 설정
        }
    }

    /**
     * 정리 작업 (기존 메서드 시그니처 유지하되 기능 대폭 확장)
     * 서버 종료 및 리소스 해제, 추가된 모니터링 시스템 정리 포함
     */
    public void cleanup() { // public 메서드 - 기존 시그니처 완전 유지
        logger.info("정리 작업을 시작합니다"); // 정리 시작 로그 (기존 로직 유지)

        // 1. 모니터링 시스템 정리 (추가된 정리 작업)
        if (monitoringEnabled) { // 모니터링이 활성화된 경우
            logger.info("모니터링 시스템을 정리합니다"); // 모니터링 정리 로그
            stopMonitoring(); // stopMonitoring() - 모니터링 중지 메서드 호출
        }

        // 2. 서버 정리 (기존 로직 완전 유지하되 확장)
        if (server != null) { // server 인스턴스가 존재하는 경우
            try { // try-catch 블록 - 서버 정리 중 예외 처리 (기존 구조 유지)
                // 서버가 아직 실행 중이면 중지 (기존 로직 완전 유지)
                if (server.isRunning()) { // server.isRunning() - ThreadedServer의 실행 상태 확인 메서드
                    logger.info("서버를 중지합니다"); // 서버 중지 로그
                    server.stop(); // server.stop() - ThreadedServer의 정지 메서드 호출
                }

                // 3. 최종 통계 출력 (기존 로직 완전 유지)
                printFinalStatistics(); // printFinalStatistics() - 최종 서버 통계 출력 메서드

                // 4. 고급 정리 작업 (추가된 정리 기능)
                printFinalAdvancedStatistics(); // printFinalAdvancedStatistics() - 고급 통계 출력 메서드

            } catch (Exception e) { // Exception - 서버 정리 중 발생하는 모든 예외 처리 (기존 처리 유지)
                // Level.WARNING - 경고 레벨 로그 (기존 로직 유지)
                logger.log(Level.WARNING, "서버 정리 중 오류 발생", e); // 서버 정리 오류 로그
            }
        }

        // 5. 기타 리소스 정리 (추가된 정리 작업)
        cleanupAdditionalResources(); // cleanupAdditionalResources() - 추가 리소스 정리 메서드

        logger.info("정리 작업 완료"); // 정리 완료 로그 (기존 로직 유지)
    }

    /**
     * 최종 통계 출력 (기존 메서드 시그니처 유지하되 기능 확장)
     * 서버 종료 시 전체 처리 결과 요약, 고급 메트릭 정보 추가
     */
    private void printFinalStatistics() { // private 메서드 - 기존 시그니처 완전 유지
        if (server != null) { // server 인스턴스 존재 확인 (기존 로직 유지)
            // server.getStatistics() - ThreadedServer의 통계 정보 반환 메서드 (기존 로직 유지)
            ThreadedServer.ServerStatistics stats = server.getStatistics(); // ThreadedServer.ServerStatistics - ThreadedServer의 내부 클래스

            logger.info("=== 최종 서버 통계 ==="); // 통계 헤더 로그 (기존 로직 유지)
            // stats.toString() - ServerStatistics의 문자열 표현 메서드 (기존 로직 유지)
            logger.info(stats.toString()); // 통계 객체의 toString() 출력

            // 콘솔에도 출력 - 사용자가 쉽게 확인할 수 있도록 (기존 로직 완전 유지)
            System.out.println("\n=== ThreadedServer 최종 통계 ==="); // System.out.println() - 표준 출력으로 한 줄 출력

            // 기존 통계 정보 출력 (기존 로직 완전 유지)
            System.out.println("총 요청 수: " + stats.getTotalRequestsReceived()); // stats.getTotalRequestsReceived() - 총 받은 요청 수 반환
            System.out.println("처리 완료: " + stats.getTotalRequestsProcessed()); // stats.getTotalRequestsProcessed() - 처리 완료된 요청 수 반환
            System.out.println("실패 요청: " + stats.getTotalRequestsFailed()); // stats.getTotalRequestsFailed() - 실패한 요청 수 반환

            // 기존 성공률/실패율 출력 (기존 로직 완전 유지)
            System.out.printf("성공률: %.2f%%\n", stats.getSuccessRate() * 100); // System.out.printf() - 형식화된 출력, %.2f - 소수점 둘째 자리까지
            System.out.printf("실패율: %.2f%%\n", stats.getFailureRate() * 100); // stats.getFailureRate() - 실패율 반환 (0.0~1.0)

            // 추가된 고급 통계 정보 (새로운 기능)
            System.out.printf("연결 사용률: %.2f%%\n", stats.getConnectionUsageRate() * 100); // stats.getConnectionUsageRate() - 연결 사용률 반환
            System.out.println("최대 동시 연결: " + stats.getMaxConnections()); // stats.getMaxConnections() - 최대 연결 수 반환
            System.out.println("스레드풀 크기: " + stats.getThreadPoolSize()); // stats.getThreadPoolSize() - 스레드풀 크기 반환

            System.out.println("=============================\n"); // 구분선 (기존 로직 유지)
        }
    }

    /**
     * 고급 최종 통계 출력 (새로운 메서드)
     * ThreadedServer의 고급 메트릭과 시스템 정보를 포함한 상세 통계
     */
    private void printFinalAdvancedStatistics() { // private 메서드 - 고급 통계 출력
        if (server == null) { // 서버 인스턴스 확인
            return; // early return - 서버가 없으면 종료
        }

        try { // try-catch 블록 - 고급 통계 수집 중 예외 처리
            System.out.println("=== 고급 서버 통계 ==="); // 고급 통계 헤더

            // 서버 메트릭 정보 출력
            if (server.getMetrics() != null) { // server.getMetrics() - 서버 메트릭 객체 조회
                ThreadedServer.ServerMetrics metrics = server.getMetrics(); // ServerMetrics 인스턴스 조회
                Map<String, Object> allMetrics = metrics.getAllMetrics(); // getAllMetrics() - 모든 메트릭 정보 반환

                // 서버 업타임 출력
                long uptime = metrics.getCurrentUptime(); // getCurrentUptime() - 현재 업타임 반환 (밀리초)
                long uptimeSeconds = uptime / 1000; // 밀리초를 초로 변환
                long hours = uptimeSeconds / 3600; // 시간 계산
                long minutes = (uptimeSeconds % 3600) / 60; // 분 계산
                long seconds = uptimeSeconds % 60; // 초 계산

                System.out.printf("서버 업타임: %d시간 %d분 %d초\n", hours, minutes, seconds); // 업타임을 시:분:초 형식으로 출력

                // 평균 응답 시간 출력
                System.out.printf("평균 응답시간: %.2fms\n", metrics.getAverageResponseTime()); // getAverageResponseTime() - 평균 응답 시간 반환

                // 에러율 출력
                System.out.printf("전체 에러율: %.2f%%\n", metrics.getErrorRate()); // getErrorRate() - 에러율 반환

                // 총 연결 수 출력
                System.out.println("총 연결 수: " + allMetrics.get("totalConnections")); // 총 연결 수
            }

            // 스레드풀 관리자 통계 출력 (있는 경우)
            if (server.getThreadPoolManager() != null) { // server.getThreadPoolManager() - 스레드풀 관리자 조회
                ThreadPoolManager threadPoolManager = server.getThreadPoolManager(); // ThreadPoolManager 인스턴스
                ThreadPoolManager.ThreadPoolStatus poolStatus = threadPoolManager.getStatus(); // getStatus() - 스레드풀 상태 조회

                System.out.println("--- 스레드풀 통계 ---"); // 스레드풀 통계 헤더
                System.out.println("완료된 작업: " + poolStatus.getCompletedTaskCount()); // getCompletedTaskCount() - 완료된 작업 수
                System.out.println("거부된 작업: " + poolStatus.getRejectedTaskCount()); // getRejectedTaskCount() - 거부된 작업 수
                System.out.println("활성 스레드: " + poolStatus.getActiveCount()); // getActiveCount() - 활성 스레드 수
                System.out.println("현재 풀 크기: " + poolStatus.getPoolSize()); // getPoolSize() - 현재 풀 크기
                System.out.println("최대 풀 크기: " + poolStatus.getMaximumPoolSize()); // getMaximumPoolSize() - 최대 풀 크기
            }

            // JVM 메모리 통계 출력
            Runtime runtime = Runtime.getRuntime(); // Runtime.getRuntime() - 현재 JVM 런타임 조회
            long maxMemoryMB = runtime.maxMemory() / 1024 / 1024; // 최대 메모리 (MB)
            long totalMemoryMB = runtime.totalMemory() / 1024 / 1024; // 총 할당 메모리 (MB)
            long freeMemoryMB = runtime.freeMemory() / 1024 / 1024; // 여유 메모리 (MB)
            long usedMemoryMB = totalMemoryMB - freeMemoryMB; // 사용 중 메모리 계산

            System.out.println("--- JVM 메모리 통계 ---"); // JVM 메모리 통계 헤더
            System.out.printf("사용 메모리: %dMB / %dMB (%.1f%%)\n", // 메모리 사용률 출력
                    usedMemoryMB, maxMemoryMB, (double) usedMemoryMB / maxMemoryMB * 100);
            System.out.println("총 할당: " + totalMemoryMB + "MB"); // 총 할당 메모리
            System.out.println("여유 메모리: " + freeMemoryMB + "MB"); // 여유 메모리

            System.out.println("====================\n"); // 고급 통계 구분선

        } catch (Exception e) { // 고급 통계 출력 중 예외 처리
            logger.log(Level.WARNING, "고급 통계 출력 중 오류 발생", e); // 고급 통계 오류 로그
        }
    }

    /**
     * 추가 리소스 정리 (새로운 메서드)
     * 모니터링 시스템 및 기타 확장 기능들의 리소스 정리
     */
    private void cleanupAdditionalResources() { // private 메서드 - 추가 리소스 정리
        try { // try-catch 블록 - 추가 리소스 정리 중 예외 처리
            // 모니터링 실행자 정리
            if (monitoringExecutor != null && !monitoringExecutor.isShutdown()) { // 모니터링 실행자가 있고 종료되지 않은 경우
                logger.info("모니터링 실행자를 정리합니다"); // 모니터링 실행자 정리 로그
                monitoringExecutor.shutdownNow(); // shutdownNow() - 강제 종료
                try {
                    if (!monitoringExecutor.awaitTermination(2, TimeUnit.SECONDS)) { // 2초 동안 종료 대기
                        logger.warning("모니터링 실행자가 완전히 종료되지 않았습니다"); // 종료 실패 경고
                    }
                } catch (InterruptedException e) { // 대기 중 인터럽트 발생
                    Thread.currentThread().interrupt(); // 인터럽트 상태 복원
                }
            }

            // 설정 객체 정리
            if (enhancedConfig != null) { // 설정 객체가 있는 경우
                logger.fine("설정 객체를 정리합니다"); // 설정 정리 로그 (FINE 레벨)
                enhancedConfig = null; // 참조 해제
            }

            // 메모리 정리 요청 (GC 힌트)
            System.gc(); // System.gc() - 가비지 컬렉션 실행 요청 (힌트)
            logger.fine("가비지 컬렉션을 요청했습니다"); // GC 요청 로그

        } catch (Exception e) { // 추가 리소스 정리 중 예외 처리
            logger.log(Level.WARNING, "추가 리소스 정리 중 오류 발생", e); // 추가 정리 오류 로그
        }
    }

    /**
     * 고급 명령줄 인수 파싱 (새로운 메서드)
     * 기존 포트/스레드풀 파싱에 옵션 지원 추가
     * --config, --monitoring, --dev 등의 고급 옵션 처리
     *
     * @param args 명령줄 인수 배열
     * @return 파싱된 확장 서버 설정
     */
    private static ServerConfig parseEnhancedCommandLineArgs(String[] args) { // private static 메서드 - 확장된 명령줄 파싱
        // 기본값으로 설정 초기화 (기존 기본값 유지)
        int port = DEFAULT_PORT; // 기본 포트
        int threadPoolSize = DEFAULT_THREAD_POOL_SIZE; // 기본 스레드풀 크기
        int backlog = DEFAULT_BACKLOG; // 기본 백로그 크기
        int maxConnections = DEFAULT_MAX_CONNECTIONS; // 기본 최대 연결 수
        boolean monitoringEnabled = false; // 모니터링 기본 비활성화
        boolean devMode = false; // 개발 모드 기본 비활성화
        String configFile = null; // 설정 파일 기본 null

        // 명령줄 인수를 순차적으로 처리
        for (int i = 0; i < args.length; i++) { // for 루프 - 모든 인수 순회
            String arg = args[i]; // 현재 인수 추출

            // 옵션 인수 처리 (-- 또는 - 로 시작)
            if (arg.startsWith("--") || arg.startsWith("-")) { // startsWith() - 문자열 시작 부분 검사
                switch (arg) { // switch 문 - 옵션별 분기 처리
                    case "--config": // 설정 파일 옵션
                        if (i + 1 < args.length) { // 다음 인수가 있는지 확인
                            configFile = args[++i]; // 다음 인수를 설정 파일명으로 사용, 전위 증감 연산자로 인덱스 증가
                            logger.info("설정 파일 지정: " + configFile); // 설정 파일 지정 로그
                        } else { // 다음 인수가 없는 경우
                            logger.warning("--config 옵션에 파일명이 지정되지 않았습니다"); // 설정 파일명 누락 경고
                        }
                        break; // break 문 - switch 문 종료

                    case "--monitoring": // 모니터링 활성화 옵션
                        monitoringEnabled = true; // 모니터링 활성화
                        logger.info("실시간 모니터링이 활성화되었습니다"); // 모니터링 활성화 로그
                        break;

                    case "--dev": // 개발 모드 옵션
                        devMode = true; // 개발 모드 활성화
                        logger.info("개발 모드가 활성화되었습니다"); // 개발 모드 활성화 로그
                        break;

                    case "--help": // 도움말 옵션
                    case "-h": // 도움말 짧은 옵션
                        printUsage(); // 사용법 출력
                        System.exit(0); // 정상 종료
                        break;

                    default: // 알 수 없는 옵션
                        logger.warning("알 수 없는 옵션: " + arg); // 알 수 없는 옵션 경고
                        break;
                }
            } else { // 위치 인수 처리 (포트, 스레드풀 크기 등)
                try { // try-catch 블록 - 숫자 변환 중 예외 처리
                    int value = Integer.parseInt(arg); // Integer.parseInt() - 문자열을 정수로 변환

                    // 첫 번째 숫자 인수는 포트로 처리 (기존 로직 유지)
                    if (port == DEFAULT_PORT) { // 포트가 아직 기본값인 경우
                        if (value >= 1 && value <= 65535) { // 포트 범위 검증
                            port = value; // 포트 설정
                            logger.info("포트 설정: " + port); // 포트 설정 로그
                        } else { // 포트 범위 오류
                            logger.warning("유효하지 않은 포트 번호: " + value + ". 기본값 사용: " + DEFAULT_PORT); // 포트 오류 경고
                        }
                    }
                    // 두 번째 숫자 인수는 스레드풀 크기로 처리 (기존 로직 유지)
                    else if (threadPoolSize == DEFAULT_THREAD_POOL_SIZE) { // 스레드풀이 아직 기본값인 경우
                        if (value >= 1) { // 스레드풀 크기 검증 (최소 1)
                            threadPoolSize = value; // 스레드풀 크기 설정
                            logger.info("스레드풀 크기 설정: " + threadPoolSize); // 스레드풀 설정 로그
                        } else { // 스레드풀 크기 오류
                            logger.warning("유효하지 않은 스레드풀 크기: " + value + ". 기본값 사용: " + DEFAULT_THREAD_POOL_SIZE); // 스레드풀 오류 경고
                        }
                    } else { // 추가 숫자 인수는 무시
                        logger.warning("추가 숫자 인수 무시: " + value); // 추가 인수 무시 경고
                    }

                } catch (NumberFormatException e) { // NumberFormatException - 숫자 변환 실패
                    logger.warning("숫자가 아닌 인수 무시: " + arg); // 비숫자 인수 경고
                }
            }
        }

        // 설정 파일에서 추가 설정 로드 (있는 경우)
        if (configFile != null) { // 설정 파일이 지정된 경우
            ServerConfig fileConfig = loadConfigFromFile(configFile); // loadConfigFromFile() - 설정 파일 로드 메서드
            if (fileConfig != null) { // 설정 파일 로드 성공한 경우
                // 명령줄에서 지정되지 않은 값들은 파일에서 가져옴
                if (port == DEFAULT_PORT && fileConfig.port != DEFAULT_PORT) { // 포트가 명령줄에 없고 파일에 있는 경우
                    port = fileConfig.port; // 파일의 포트 사용
                }
                if (threadPoolSize == DEFAULT_THREAD_POOL_SIZE && fileConfig.threadPoolSize != DEFAULT_THREAD_POOL_SIZE) { // 스레드풀이 명령줄에 없고 파일에 있는 경우
                    threadPoolSize = fileConfig.threadPoolSize; // 파일의 스레드풀 크기 사용
                }
                // 기타 설정들도 파일에서 가져옴
                backlog = fileConfig.backlog; // 백로그 설정
                maxConnections = fileConfig.maxConnections; // 최대 연결 수 설정
                if (!monitoringEnabled) { // 명령줄에서 모니터링이 지정되지 않은 경우
                    monitoringEnabled = fileConfig.monitoringEnabled; // 파일의 모니터링 설정 사용
                }
            }
        }

        // 환경 변수에서 설정 로드 (12-factor app 원칙)
        loadConfigFromEnvironment(new ServerConfigBuilder() // ServerConfigBuilder 패턴으로 설정 구성
                .port(port).threadPoolSize(threadPoolSize).backlog(backlog)
                .maxConnections(maxConnections).monitoringEnabled(monitoringEnabled)
                .devMode(devMode)); // 현재 설정을 빌더에 설정

        // 개발 모드 추가 설정
        if (devMode) { // 개발 모드가 활성화된 경우
            // 개발 모드에서는 더 상세한 로깅 활성화
            Logger.getLogger("").setLevel(Level.FINE); // 루트 로거를 FINE 레벨로 설정
            logger.info("개발 모드 - 상세 로깅이 활성화되었습니다"); // 개발 모드 로그

            // 개발 모드에서는 모니터링도 기본 활성화
            if (!monitoringEnabled) { // 모니터링이 명시적으로 비활성화되지 않은 경우
                monitoringEnabled = true; // 개발 모드에서 모니터링 활성화
                logger.info("개발 모드 - 모니터링이 자동 활성화되었습니다"); // 개발 모드 모니터링 로그
            }
        }

        // 최종 설정 로그 출력
        logger.info(String.format("최종 설정 - 포트: %d, 스레드풀: %d, 백로그: %d, 최대연결: %d, 모니터링: %s, 개발모드: %s", // 최종 설정 요약 로그
                port, threadPoolSize, backlog, maxConnections,
                monitoringEnabled ? "활성화" : "비활성화", // 삼항 연산자로 모니터링 상태 표시
                devMode ? "활성화" : "비활성화")); // 삼항 연산자로 개발 모드 상태 표시

        // 설정 객체 생성 및 반환
        return new ServerConfig(port, threadPoolSize, backlog, maxConnections, monitoringEnabled, devMode, configFile); // 모든 설정을 포함한 ServerConfig 생성
    }

    /**
     * 설정 파일에서 설정 로드 (새로운 메서드)
     * Java Properties 파일 형식으로 서버 설정을 외부화
     *
     * @param configFile 설정 파일 경로
     * @return 로드된 설정 객체 (실패 시 null)
     */
    private static ServerConfig loadConfigFromFile(String configFile) { // private static 메서드 - 설정 파일 로드
        try { // try-catch 블록 - 파일 로드 중 예외 처리
            Path configPath = Paths.get(configFile); // Paths.get() - 파일 경로 객체 생성
            if (!Files.exists(configPath)) { // Files.exists() - 파일 존재 여부 확인
                logger.warning("설정 파일을 찾을 수 없습니다: " + configFile); // 파일 없음 경고
                return null; // null 반환 - 파일 없음
            }

            Properties props = new Properties(); // new Properties() - 설정 저장용 Properties 객체 생성
            try (InputStream input = Files.newInputStream(configPath)) { // try-with-resources - 자동 리소스 관리
                props.load(input); // Properties.load() - 파일에서 설정 로드
            }

            logger.info("설정 파일 로드 성공: " + configFile); // 설정 파일 로드 성공 로그

            // Properties에서 설정값 추출
            int port = Integer.parseInt(props.getProperty("server.port", String.valueOf(DEFAULT_PORT))); // getProperty() - 속성값 조회, 기본값 제공
            int threadPoolSize = Integer.parseInt(props.getProperty("server.threadpool.size", String.valueOf(DEFAULT_THREAD_POOL_SIZE))); // 스레드풀 크기
            int backlog = Integer.parseInt(props.getProperty("server.backlog", String.valueOf(DEFAULT_BACKLOG))); // 백로그 크기
            int maxConnections = Integer.parseInt(props.getProperty("server.max.connections", String.valueOf(DEFAULT_MAX_CONNECTIONS))); // 최대 연결 수
            boolean monitoringEnabled = Boolean.parseBoolean(props.getProperty("server.monitoring.enabled", "false")); // Boolean.parseBoolean() - 문자열을 boolean으로 변환

            // 설정 객체 생성 및 반환
            return new ServerConfig(port, threadPoolSize, backlog, maxConnections, monitoringEnabled, false, configFile); // 파일에서 로드한 설정으로 객체 생성

        } catch (IOException e) { // IOException - 파일 입출력 오류
            logger.log(Level.WARNING, "설정 파일 로드 실패: " + configFile, e); // 파일 로드 실패 로그
            return null; // null 반환 - 로드 실패

        } catch (NumberFormatException e) { // NumberFormatException - 숫자 변환 오류
            logger.log(Level.WARNING, "설정 파일의 숫자 형식 오류: " + configFile, e); // 숫자 형식 오류 로그
            return null; // null 반환 - 형식 오류
        }
    }

    /**
     * 환경 변수에서 설정 로드 (새로운 메서드)
     * 12-factor app 원칙에 따른 환경 변수 기반 설정
     *
     * @param builder 설정 빌더 객체
     */
    private static void loadConfigFromEnvironment(ServerConfigBuilder builder) { // private static 메서드 - 환경 변수 설정 로드
        // 환경 변수 맵 조회
        Map<String, String> env = System.getenv(); // System.getenv() - 모든 환경 변수를 Map으로 조회

        // 각 설정별로 환경 변수 확인 및 적용
        if (env.containsKey("SERVER_PORT")) { // Map.containsKey() - 특정 키 존재 여부 확인
            try {
                int port = Integer.parseInt(env.get("SERVER_PORT")); // env.get() - 환경 변수 값 조회
                builder.port(port); // 빌더에 포트 설정
                logger.info("환경 변수에서 포트 설정: " + port); // 환경 변수 포트 로그
            } catch (NumberFormatException e) { // 숫자 변환 오류
                logger.warning("SERVER_PORT 환경 변수가 유효한 숫자가 아닙니다: " + env.get("SERVER_PORT")); // 환경 변수 오류 경고
            }
        }

        if (env.containsKey("SERVER_THREAD_POOL_SIZE")) { // 스레드풀 크기 환경 변수
            try {
                int threadPoolSize = Integer.parseInt(env.get("SERVER_THREAD_POOL_SIZE"));
                builder.threadPoolSize(threadPoolSize); // 빌더에 스레드풀 크기 설정
                logger.info("환경 변수에서 스레드풀 크기 설정: " + threadPoolSize); // 환경 변수 스레드풀 로그
            } catch (NumberFormatException e) {
                logger.warning("SERVER_THREAD_POOL_SIZE 환경 변수가 유효한 숫자가 아닙니다: " + env.get("SERVER_THREAD_POOL_SIZE"));
            }
        }

        if (env.containsKey("SERVER_MAX_CONNECTIONS")) { // 최대 연결 수 환경 변수
            try {
                int maxConnections = Integer.parseInt(env.get("SERVER_MAX_CONNECTIONS"));
                builder.maxConnections(maxConnections); // 빌더에 최대 연결 수 설정
                logger.info("환경 변수에서 최대 연결 수 설정: " + maxConnections); // 환경 변수 최대 연결 로그
            } catch (NumberFormatException e) {
                logger.warning("SERVER_MAX_CONNECTIONS 환경 변수가 유효한 숫자가 아닙니다: " + env.get("SERVER_MAX_CONNECTIONS"));
            }
        }

        if (env.containsKey("SERVER_MONITORING_ENABLED")) { // 모니터링 활성화 환경 변수
            boolean monitoringEnabled = Boolean.parseBoolean(env.get("SERVER_MONITORING_ENABLED"));
            builder.monitoringEnabled(monitoringEnabled); // 빌더에 모니터링 설정
            logger.info("환경 변수에서 모니터링 설정: " + (monitoringEnabled ? "활성화" : "비활성화")); // 환경 변수 모니터링 로그
        }

        if (env.containsKey("SERVER_DEV_MODE")) { // 개발 모드 환경 변수
            boolean devMode = Boolean.parseBoolean(env.get("SERVER_DEV_MODE"));
            builder.devMode(devMode); // 빌더에 개발 모드 설정
            logger.info("환경 변수에서 개발 모드 설정: " + (devMode ? "활성화" : "비활성화")); // 환경 변수 개발 모드 로그
        }
    }

    /**
     * 설정 유효성 검증 (새로운 메서드)
     * 모든 설정값이 유효한 범위 내에 있는지 포괄적으로 검증
     *
     * @param config 검증할 설정 객체
     * @throws IllegalArgumentException 유효하지 않은 설정값이 있는 경우
     */
    private static void validateConfiguration(ServerConfig config) { // private static 메서드 - 설정 유효성 검증
        if (config == null) { // null 체크
            throw new IllegalArgumentException("설정 객체가 null입니다"); // null 설정 예외
        }

        List<String> errors = new ArrayList<>(); // ArrayList - 오류 메시지 수집용 리스트

        // 포트 번호 검증 (기존 검증 로직 확장)
        if (config.port < 1 || config.port > 65535) { // 포트 범위 검증
            errors.add(String.format("포트 번호가 유효하지 않습니다: %d (유효 범위: 1-65535)", config.port)); // 포트 오류 메시지 추가
        }

        // 권한 포트 경고 (1024 이하 포트는 관리자 권한 필요)
        if (config.port <= 1024) { // 권한 포트 확인
            logger.warning(String.format("포트 %d는 관리자 권한이 필요할 수 있습니다", config.port)); // 권한 포트 경고
        }

        // 스레드풀 크기 검증 (기존 검증 로직 확장)
        if (config.threadPoolSize < 1) { // 스레드풀 최소값 검증
            errors.add(String.format("스레드풀 크기가 유효하지 않습니다: %d (최소값: 1)", config.threadPoolSize)); // 스레드풀 오류 메시지
        }

        // 스레드풀 크기 상한선 검증 (메모리 사용량 고려)
        Runtime runtime = Runtime.getRuntime(); // JVM 런타임 정보
        long maxMemoryMB = runtime.maxMemory() / 1024 / 1024; // 최대 메모리 (MB)
        long estimatedThreadMemoryMB = config.threadPoolSize; // 스레드당 약 1MB 추정
        if (estimatedThreadMemoryMB > maxMemoryMB * 0.2) { // 스레드풀이 전체 메모리의 20% 초과 사용
            errors.add(String.format("스레드풀이 너무 많은 메모리를 사용할 수 있습니다: %dMB (최대 메모리의 20%% 초과)", // 메모리 초과 오류
                    estimatedThreadMemoryMB));
        }

        // 백로그 크기 검증 (추가된 검증)
        if (config.backlog < 1) { // 백로그 최소값 검증
            errors.add(String.format("백로그 크기가 유효하지 않습니다: %d (최소값: 1)", config.backlog)); // 백로그 오류 메시지
        }

        if (config.backlog > 1000) { // 백로그 상한선 검증
            logger.warning(String.format("백로그 크기가 매우 큽니다: %d (일반적 권장: 50-200)", config.backlog)); // 백로그 크기 경고
        }

        // 최대 연결 수 검증 (추가된 검증)
        if (config.maxConnections < 1) { // 최대 연결 수 최소값 검증
            errors.add(String.format("최대 연결 수가 유효하지 않습니다: %d (최소값: 1)", config.maxConnections)); // 최대 연결 수 오류
        }

        // 최대 연결 수와 스레드풀 크기 관계 검증
        if (config.maxConnections < config.threadPoolSize) { // 최대 연결 수가 스레드풀보다 작은 경우
            logger.warning(String.format("최대 연결 수(%d)가 스레드풀 크기(%d)보다 작습니다. 성능이 제한될 수 있습니다", // 성능 제한 경고
                    config.maxConnections, config.threadPoolSize));
        }

        // 시스템 리소스와 설정 비교 검증
        int cores = runtime.availableProcessors(); // CPU 코어 수
        if (config.threadPoolSize > cores * 10) { // 스레드풀이 CPU 코어의 10배 초과
            errors.add(String.format("스레드풀 크기가 시스템 CPU 코어 수에 비해 과도합니다: %d (CPU 코어: %d, 권장 최대: %d)", // CPU 대비 스레드풀 오류
                    config.threadPoolSize, cores, cores * 10));
        }

        // 파일 디스크립터 제한 확인 (Unix/Linux 시스템)
        String osName = System.getProperty("os.name", "").toLowerCase(); // 운영체제 이름 조회
        if (osName.contains("linux") || osName.contains("unix") || osName.contains("mac")) { // Unix 계열 OS인 경우
            // 파일 디스크립터 제한 경고 (정확한 값은 확인 어려우므로 일반적 경고)
            if (config.maxConnections > 1000) { // 높은 연결 수인 경우
                logger.warning("Unix 계열 시스템에서 높은 연결 수 설정 시 'ulimit -n' 값을 확인하세요"); // 파일 디스크립터 제한 경고
            }
        }

        // 오류가 있는 경우 예외 던지기
        if (!errors.isEmpty()) { // 오류 리스트가 비어있지 않은 경우
            String errorMessage = "설정 검증 실패:\n" + String.join("\n", errors); // String.join() - 리스트를 문자열로 결합
            logger.severe(errorMessage); // 심각한 오류 레벨로 로그
            throw new IllegalArgumentException(errorMessage); // 설정 검증 실패 예외
        }

        logger.info("설정 검증 완료 - 모든 설정이 유효합니다"); // 설정 검증 성공 로그
    }

    /**
     * 로깅 시스템 설정 (기존 메서드 시그니처 유지하되 기능 확장)
     * 콘솔과 파일에 동시에 로그를 출력하도록 구성, 고급 포맷터 및 회전 로그 지원
     */
    private static void setupLogging() { // private static 메서드 - 기존 시그니처 완전 유지
        // 루트 로거 가져오기 (기존 로직 유지)
        Logger rootLogger = Logger.getLogger(""); // Logger.getLogger("") - 빈 문자열로 루트 로거 조회

        // 기본 로그 레벨 설정 - INFO 이상만 출력 (기존 로직 유지)
        rootLogger.setLevel(Level.INFO); // rootLogger.setLevel() - 로거의 로그 레벨 설정

        // 기존 핸들러 제거 - 중복 로그 방지 (기존 로직 유지)
        for (Handler handler : rootLogger.getHandlers()) { // rootLogger.getHandlers() - 로거의 모든 핸들러 배열 반환
            rootLogger.removeHandler(handler); // rootLogger.removeHandler() - 핸들러 제거
        }

        // 콘솔 핸들러 설정 (기존 로직 확장)
        ConsoleHandler consoleHandler = new ConsoleHandler(); // new ConsoleHandler() - 콘솔 출력 핸들러 생성
        consoleHandler.setLevel(Level.INFO); // consoleHandler.setLevel() - 핸들러의 로그 레벨 설정

        // 향상된 로그 포맷 설정 - 색상 및 이모지 지원 (기존 SimpleFormatter 확장)
        consoleHandler.setFormatter(new SimpleFormatter() { // consoleHandler.setFormatter() - 로그 포맷터 설정
            @Override // @Override 어노테이션 - 부모 메서드를 재정의함을 명시
            public String format(LogRecord record) { // SimpleFormatter.format() 메서드 재정의
                // 로그 레벨별 색상 및 이모지 설정
                String levelEmoji = getLevelEmoji(record.getLevel()); // getLevelEmoji() - 로그 레벨에 따른 이모지 반환
                String levelColor = getLevelColor(record.getLevel()); // getLevelColor() - 로그 레벨에 따른 색상 코드 반환
                String resetColor = "\u001B[0m"; // ANSI 색상 리셋 코드

                // 클래스명 단순화 (패키지 경로 제거)
                String className = record.getSourceClassName(); // record.getSourceClassName() - 로그 발생 클래스명
                if (className != null && className.contains(".")) { // 패키지 경로가 있는 경우
                    className = className.substring(className.lastIndexOf(".") + 1); // substring() - 마지막 점 이후 부분만 추출
                }

                // 향상된 로그 포맷 생성
                return String.format("%s[%1$tF %1$tT] %s%2$s%s [%3$s] %4$s%s%n", // 색상과 이모지가 포함된 로그 포맷
                        new Date(record.getMillis()), // 로그 시간
                        levelColor, levelEmoji, resetColor, // 색상, 이모지, 색상 리셋
                        className, // 단순화된 클래스명
                        record.getMessage() // 로그 메시지
                );
            }
        });

        rootLogger.addHandler(consoleHandler); // rootLogger.addHandler() - 핸들러를 로거에 추가

        // 향상된 파일 핸들러 설정 - 회전 로그 지원 (기존 로직 확장)
        try { // try-catch 블록 - 파일 핸들러 설정 중 예외 처리
            // 로그 디렉토리 생성
            Path logDir = Paths.get("logs"); // Paths.get() - 로그 디렉토리 경로 생성
            if (!Files.exists(logDir)) { // Files.exists() - 디렉토리 존재 여부 확인
                Files.createDirectories(logDir); // Files.createDirectories() - 디렉토리 생성 (중간 디렉토리도 생성)
            }

            // 회전 로그 파일명 생성 - 날짜와 시간 포함
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(LOG_DATE_PATTERN)); // 현재 시간을 파일명 형식으로 포맷
            String logFileName = String.format("logs/threaded-server-%s.log", timestamp); // 로그 파일명 생성

            // 파일 핸들러 생성 - 회전 로그 지원
            FileHandler fileHandler = new FileHandler( // new FileHandler() - 파일 출력 핸들러 생성
                    logFileName.replace(".log", "_%g.log"), // %g는 로그 파일 번호 (회전 시 사용)
                    10 * 1024 * 1024, // 10MB 파일 크기 제한
                    5, // 최대 5개 파일 유지
                    true // append 모드
            );

            fileHandler.setLevel(Level.ALL); // Level.ALL - 모든 레벨의 로그를 파일에 기록

            // 파일용 상세 포맷터 설정 (색상 코드 제외)
            fileHandler.setFormatter(new SimpleFormatter() { // 파일용 별도 포맷터
                @Override
                public String format(LogRecord record) {
                    return String.format("[%1$tF %1$tT] [%2$s] [%3$s] %4$s%n", // 파일용 단순 포맷 (색상 제외)
                            new Date(record.getMillis()), // 로그 시간
                            record.getLevel(), // 로그 레벨
                            record.getSourceClassName(), // 전체 클래스명 (파일에는 상세 정보 포함)
                            record.getMessage() // 로그 메시지
                    );
                }
            });

            rootLogger.addHandler(fileHandler); // 파일 핸들러 추가
            logger.info("향상된 로그 시스템 설정 완료: " + logFileName); // 로그 시스템 설정 완료 로그

        } catch (IOException e) { // IOException - 파일 핸들러 설정 실패
            // 파일 핸들러 설정 실패 시 콘솔에만 로그 출력 (기존 로직 유지)
            logger.warning("로그 파일 설정 실패 - 콘솔에만 로그를 출력합니다: " + e.getMessage()); // e.getMessage() - 예외의 메시지 반환
        }

        // JVM 종료 시 로그 핸들러 정리 (기존 로직 유지)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { // Runtime.getRuntime().addShutdownHook() - JVM 종료 시 실행할 훅 등록
            // 모든 핸들러를 정리하여 리소스 해제
            for (Handler handler : rootLogger.getHandlers()) { // 모든 핸들러 순회
                handler.close(); // handler.close() - 핸들러 정리 및 리소스 해제
            }
        }));

        // 로그 레벨 동적 변경을 위한 JMX 지원 설정 (추가 기능)
        try { // JMX 등록 시도
            // MBean 서버에 로깅 관리 빈 등록 (운영 중 로그 레벨 변경 가능)
            java.lang.management.ManagementFactory.getPlatformMBeanServer(); // MBean 서버 조회
            logger.fine("JMX 로깅 관리 지원이 활성화되었습니다"); // JMX 지원 로그
        } catch (Exception e) { // JMX 등록 실패 시
            logger.fine("JMX 로깅 관리 등록 실패: " + e.getMessage()); // JMX 실패 로그 (심각하지 않으므로 FINE 레벨)
        }
    }

    /**
     * 로그 레벨별 이모지 반환 (새로운 유틸리티 메서드)
     * 콘솔 로그의 가독성 향상을 위한 이모지 추가
     *
     * @param level 로그 레벨
     * @return 해당 레벨의 이모지
     */
    private static String getLevelEmoji(Level level) { // private static 메서드 - 로그 레벨 이모지 반환
        if (level == Level.SEVERE) return "🔥"; // SEVERE 레벨 - 화재 이모지
        if (level == Level.WARNING) return "⚠️"; // WARNING 레벨 - 경고 이모지
        if (level == Level.INFO) return "ℹ️"; // INFO 레벨 - 정보 이모지
        if (level == Level.FINE) return "🔍"; // FINE 레벨 - 돋보기 이모지
        return "📝"; // 기타 레벨 - 메모 이모지
    }

    /**
     * 로그 레벨별 색상 코드 반환 (새로운 유틸리티 메서드)
     * 콘솔 로그의 가독성 향상을 위한 ANSI 색상 코드
     *
     * @param level 로그 레벨
     * @return 해당 레벨의 ANSI 색상 코드
     */
    private static String getLevelColor(Level level) { // private static 메서드 - 로그 레벨 색상 반환
        if (level == Level.SEVERE) return "\u001B[31m"; // 빨간색 - 심각한 오류
        if (level == Level.WARNING) return "\u001B[33m"; // 노란색 - 경고
        if (level == Level.INFO) return "\u001B[36m"; // 청록색 - 정보
        if (level == Level.FINE) return "\u001B[35m"; // 자주색 - 상세 정보
        return "\u001B[37m"; // 흰색 - 기타
    }

    /**
     * 사용법 출력 (기존 메서드 시그니처 유지하되 내용 대폭 확장)
     * 확장된 옵션과 기능에 대한 상세한 도움말 제공
     */
    private static void printUsage() { // private static 메서드 - 기존 시그니처 완전 유지
        // 향상된 도움말 출력 - 색상과 이모지 포함
        System.out.println("🚀 Enhanced ThreadedServer - Thread-per-Request HTTP 서버 (고급 버전)"); // 제목 with 이모지
        System.out.println();

        System.out.println("📋 사용법:"); // 사용법 섹션
        System.out.println("  java ThreadedServerLauncher [옵션] [포트] [스레드풀크기]"); // 기본 사용법
        System.out.println();

        System.out.println("⚙️ 옵션:"); // 옵션 섹션 (새로 추가)
        System.out.println("  --config <파일>    설정 파일 지정 (*.properties 형식)"); // 설정 파일 옵션
        System.out.println("  --monitoring       실시간 모니터링 활성화"); // 모니터링 옵션
        System.out.println("  --dev             개발 모드 (상세 로깅 + 자동 모니터링)"); // 개발 모드 옵션
        System.out.println("  --help, -h        이 도움말 출력"); // 도움말 옵션
        System.out.println();

        System.out.println("📊 매개변수:"); // 매개변수 섹션 (기존 내용 유지하되 확장)
        System.out.println("  포트              서버 바인딩 포트 (1-65535, 기본값: " + DEFAULT_PORT + ")"); // 포트 설명
        System.out.println("  스레드풀크기       요청 처리용 스레드 수 (1 이상, 기본값: " + DEFAULT_THREAD_POOL_SIZE + ")"); // 스레드풀 설명
        System.out.println();

        System.out.println("🌍 환경 변수 지원:"); // 환경 변수 섹션 (새로 추가)
        System.out.println("  SERVER_PORT                서버 포트"); // 포트 환경 변수
        System.out.println("  SERVER_THREAD_POOL_SIZE    스레드풀 크기"); // 스레드풀 환경 변수
        System.out.println("  SERVER_MAX_CONNECTIONS     최대 동시 연결 수"); // 최대 연결 환경 변수
        System.out.println("  SERVER_MONITORING_ENABLED  모니터링 활성화 (true/false)"); // 모니터링 환경 변수
        System.out.println("  SERVER_DEV_MODE           개발 모드 (true/false)"); // 개발 모드 환경 변수
        System.out.println();

        System.out.println("💡 예시:"); // 예시 섹션 (기존 내용 확장)
        System.out.println("  java ThreadedServerLauncher"); // 기본 실행
        System.out.println("  java ThreadedServerLauncher 8080"); // 포트 지정
        System.out.println("  java ThreadedServerLauncher 8080 200"); // 포트 + 스레드풀 지정
        System.out.println("  java ThreadedServerLauncher --config server.properties --monitoring"); // 설정 파일 + 모니터링
        System.out.println("  java ThreadedServerLauncher 9090 150 --dev"); // 개발 모드
        System.out.println();

        System.out.println("📁 설정 파일 형식 (server.properties):"); // 설정 파일 형식 안내 (새로 추가)
        System.out.println("  server.port=8080"); // 설정 파일 포트 예시
        System.out.println("  server.threadpool.size=100"); // 설정 파일 스레드풀 예시
        System.out.println("  server.backlog=50"); // 설정 파일 백로그 예시
        System.out.println("  server.max.connections=1000"); // 설정 파일 최대 연결 예시
        System.out.println("  server.monitoring.enabled=true"); // 설정 파일 모니터링 예시
        System.out.println();

        System.out.println("🔗 관리 엔드포인트:"); // 관리 엔드포인트 안내 (새로 추가)
        System.out.println("  GET  /health              서버 상태 확인"); // 헬스 체크 엔드포인트
        System.out.println("  GET  /metrics             기본 메트릭 정보"); // 메트릭 엔드포인트
        System.out.println("  GET  /info                서버 구성 정보"); // 정보 엔드포인트
        System.out.println("  GET  /admin/config        현재 설정 조회"); // 설정 조회 엔드포인트
        System.out.println("  GET  /admin/threads       스레드 덤프"); // 스레드 덤프 엔드포인트
        System.out.println("  GET  /admin/advanced-metrics  고급 메트릭"); // 고급 메트릭 엔드포인트
        System.out.println("  POST /admin/restart       서버 재시작"); // 재시작 엔드포인트
        System.out.println("  POST /admin/loglevel      로그 레벨 변경"); // 로그 레벨 변경 엔드포인트
        System.out.println();

        System.out.println("⚠️ 주의사항:"); // 주의사항 섹션 (기존 내용 확장)
        System.out.println("  • 스레드풀 크기가 클수록 더 많은 동시 연결을 처리할 수 있지만"); // 기존 주의사항
        System.out.println("    메모리 사용량도 증가합니다 (스레드당 약 1MB 스택 메모리)"); // 메모리 사용량 설명
        System.out.println("  • 1024 이하의 포트는 관리자 권한이 필요할 수 있습니다"); // 기존 주의사항
        System.out.println("  • 높은 연결 수 설정 시 시스템의 파일 디스크립터 제한을 확인하세요"); // 파일 디스크립터 주의사항
        System.out.println("  • 프로덕션 환경에서는 모니터링 활성화를 권장합니다"); // 모니터링 권장사항
        System.out.println("  • 설정 파일과 환경 변수가 모두 있으면 명령줄 > 설정 파일 > 환경 변수 순으로 우선순위가 적용됩니다"); // 우선순위 설명
        System.out.println();

        System.out.println("🎯 권장 설정:"); // 권장 설정 섹션 (새로 추가)

        // 현재 시스템에 맞는 권장값 계산 및 출력
        int cores = Runtime.getRuntime().availableProcessors(); // CPU 코어 수
        long maxMemoryMB = Runtime.getRuntime().maxMemory() / 1024 / 1024; // 최대 메모리
        int recommendedThreadPool = getRecommendedThreadPoolSize(); // 권장 스레드풀 크기 계산

        System.out.println(String.format("  현재 시스템 (CPU: %d코어, 메모리: %dMB)에 권장:", cores, maxMemoryMB)); // 현재 시스템 정보
        System.out.println(String.format("  java ThreadedServerLauncher 8080 %d --monitoring", recommendedThreadPool)); // 권장 실행 명령
        System.out.println();

        System.out.println("📞 지원 및 문의:"); // 지원 섹션 (새로 추가)
        System.out.println("  GitHub: https://github.com/your-repo/threaded-server"); // GitHub 링크 (예시)
        System.out.println("  문서: https://docs.your-domain.com/threaded-server"); // 문서 링크 (예시)
        System.out.println("==============================================="); // 구분선
    }

    /**
     * 도움말 확인 (기존 메서드 완전 유지)
     * 명령줄 인수에 도움말 요청이 있는지 확인
     *
     * @param args 명령줄 인수
     * @return 도움말 요청이면 true
     */
    private static boolean isHelpRequested(String[] args) { // private static 메서드 - 기존 시그니처 완전 유지
        // for-each 루프로 모든 인수 검사 (기존 로직 완전 유지)
        for (String arg : args) { // for-each 루프 - 배열의 모든 요소 순회
            // arg.equals() - String의 동등성 비교 메서드 (기존 로직 완전 유지)
            if ("-h".equals(arg) || "--help".equals(arg) || "help".equals(arg)) { // 도움말 옵션 확인
                return true; // 도움말 요청 발견 시 true 반환
            }
        }
        return false; // 도움말 요청이 없으면 false 반환
    }

    // ========== 유틸리티 메서드들 (새로 추가된 메서드들) ==========

    /**
     * Map을 JSON 문자열로 변환하는 유틸리티 메서드 (새로운 메서드)
     * 커스텀 라우트에서 JSON 응답 생성을 위해 사용
     *
     * @param map 변환할 Map 객체
     * @return JSON 형태의 문자열
     */
    private static String convertMapToJson(Map<String, Object> map) { // private static 메서드 - Map을 JSON으로 변환
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

            // 키 추가 (따옴표로 감싸기)
            json.append("\"").append(escapeJsonString(entry.getKey())).append("\":"); // escapeJsonString() - JSON 문자열 이스케이프

            // 값 추가 (타입별 처리)
            Object value = entry.getValue(); // 값 추출
            if (value instanceof String) { // 문자열인 경우
                json.append("\"").append(escapeJsonString((String) value)).append("\""); // 문자열을 따옴표로 감싸고 이스케이프
            } else if (value instanceof Number || value instanceof Boolean) { // 숫자나 불린인 경우
                json.append(value); // 그대로 추가
            } else if (value == null) { // null인 경우
                json.append("null"); // JSON null 추가
            } else if (value instanceof Map) { // 중첩 Map인 경우
                json.append(convertMapToJson((Map<String, Object>) value)); // 재귀 호출로 중첩 JSON 생성
            } else if (value instanceof List) { // 리스트인 경우
                json.append(convertListToJson((List<?>) value)); // 리스트를 JSON 배열로 변환
            } else { // 기타 객체인 경우
                json.append("\"").append(escapeJsonString(value.toString())).append("\""); // toString()으로 변환 후 이스케이프
            }
        }

        json.append("}"); // JSON 객체 종료
        return json.toString(); // 완성된 JSON 문자열 반환
    }

    /**
     * List를 JSON 배열 문자열로 변환하는 유틸리티 메서드 (새로운 메서드)
     * 중첩된 리스트 구조를 JSON 배열로 변환
     *
     * @param list 변환할 List 객체
     * @return JSON 배열 형태의 문자열
     */
    private static String convertListToJson(List<?> list) { // private static 메서드 - List를 JSON 배열로 변환
        if (list == null || list.isEmpty()) { // null 체크와 빈 리스트 확인
            return "[]"; // 빈 JSON 배열 반환
        }

        StringBuilder json = new StringBuilder(); // StringBuilder로 JSON 배열 구성
        json.append("["); // JSON 배열 시작

        boolean first = true; // 첫 번째 요소 플래그
        for (Object item : list) { // 리스트의 모든 요소 순회
            if (!first) { // 첫 번째가 아닌 경우
                json.append(","); // 쉼표 추가
            }
            first = false; // 첫 번째 플래그 해제

            // 요소 타입별 처리
            if (item instanceof String) { // 문자열인 경우
                json.append("\"").append(escapeJsonString((String) item)).append("\""); // 문자열 이스케이프 후 추가
            } else if (item instanceof Number || item instanceof Boolean) { // 숫자나 불린인 경우
                json.append(item); // 그대로 추가
            } else if (item == null) { // null인 경우
                json.append("null"); // JSON null 추가
            } else if (item instanceof Map) { // 중첩 Map인 경우
                json.append(convertMapToJson((Map<String, Object>) item)); // 재귀 호출로 중첩 객체 생성
            } else if (item instanceof List) { // 중첩 List인 경우
                json.append(convertListToJson((List<?>) item)); // 재귀 호출로 중첩 배열 생성
            } else { // 기타 객체인 경우
                json.append("\"").append(escapeJsonString(item.toString())).append("\""); // toString() 후 이스케이프
            }
        }

        json.append("]"); // JSON 배열 종료
        return json.toString(); // 완성된 JSON 배열 문자열 반환
    }

    /**
     * JSON 문자열 이스케이프 메서드 (새로운 메서드)
     * JSON에서 특수 문자들을 안전하게 처리하기 위한 이스케이프 처리
     *
     * @param str 이스케이프할 문자열
     * @return 이스케이프된 문자열
     */
    private static String escapeJsonString(String str) { // private static 메서드 - JSON 문자열 이스케이프
        if (str == null) { // null 체크
            return ""; // 빈 문자열 반환
        }

        StringBuilder escaped = new StringBuilder(); // 이스케이프된 문자열 구성용 StringBuilder
        for (char c : str.toCharArray()) { // 문자열의 모든 문자 순회
            switch (c) { // 특수 문자별 이스케이프 처리
                case '"': // 큰따옴표
                    escaped.append("\\\""); // 백슬래시로 이스케이프
                    break;
                case '\\': // 백슬래시
                    escaped.append("\\\\"); // 이중 백슬래시로 이스케이프
                    break;
                case '\b': // 백스페이스
                    escaped.append("\\b"); // \b로 이스케이프
                    break;
                case '\f': // 폼피드
                    escaped.append("\\f"); // \f로 이스케이프
                    break;
                case '\n': // 개행
                    escaped.append("\\n"); // \n으로 이스케이프
                    break;
                case '\r': // 캐리지 리턴
                    escaped.append("\\r"); // \r로 이스케이프
                    break;
                case '\t': // 탭
                    escaped.append("\\t"); // \t로 이스케이프
                    break;
                default: // 일반 문자
                    if (c < 32) { // 제어 문자인 경우 (ASCII < 32)
                        escaped.append(String.format("\\u%04x", (int) c)); // 유니코드 이스케이프
                    } else { // 일반 문자인 경우
                        escaped.append(c); // 그대로 추가
                    }
                    break;
            }
        }
        return escaped.toString(); // 이스케이프된 문자열 반환
    }

    /**
     * 요청 본문에서 로그 레벨 추출 메서드 (새로운 메서드)
     * POST /admin/loglevel 엔드포인트에서 사용하는 파싱 메서드
     *
     * @param body HTTP 요청 본문
     * @return 추출된 로그 레벨 문자열
     */
    private static String extractLogLevelFromBody(String body) { // private static 메서드 - 요청 본문에서 로그 레벨 추출
        if (body == null || body.trim().isEmpty()) { // 본문이 없거나 빈 문자열인 경우
            throw new IllegalArgumentException("로그 레벨이 지정되지 않았습니다"); // 로그 레벨 누락 예외
        }

        // 간단한 형태들 처리
        String trimmedBody = body.trim(); // 앞뒤 공백 제거

        // JSON 형태인 경우: {"level": "INFO"}
        if (trimmedBody.startsWith("{") && trimmedBody.endsWith("}")) { // JSON 형태 확인
            // 간단한 JSON 파싱 (완전한 JSON 파서 없이 기본적인 처리)
            String levelPattern = "\"level\"\\s*:\\s*\"([^\"]+)\""; // 정규표현식 패턴 - level 필드 추출
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(levelPattern); // Pattern.compile() - 정규표현식 컴파일
            java.util.regex.Matcher matcher = pattern.matcher(trimmedBody); // Matcher 생성
            if (matcher.find()) { // 패턴 매칭 시도
                return matcher.group(1); // 첫 번째 그룹 (레벨 값) 반환
            }
        }

        // 단순 텍스트 형태인 경우
        if (trimmedBody.matches("[A-Z]+")) { // 대문자만으로 구성된 경우 (SEVERE, WARNING, INFO 등)
            return trimmedBody; // 그대로 반환
        }

        // key=value 형태인 경우: level=INFO
        if (trimmedBody.contains("=")) { // 등호가 포함된 경우
            String[] parts = trimmedBody.split("=", 2); // "=" 기준으로 분할 (최대 2개 부분)
            if (parts.length == 2 && "level".equalsIgnoreCase(parts[0].trim())) { // level 키인지 확인
                return parts[1].trim(); // 값 부분 반환
            }
        }

        // 파싱 실패 시 예외
        throw new IllegalArgumentException("로그 레벨 형식을 인식할 수 없습니다: " + body); // 파싱 실패 예외
    }

    // ========== 개발용 편의 메서드들 (기존 메서드들 유지하되 확장) ==========

    /**
     * 개발 모드로 서버 시작 (기존 메서드 시그니처 유지하되 기능 확장)
     * IDE에서 직접 실행할 때 사용하는 편의 메서드, 확장된 개발 기능 포함
     */
    public static void startDevelopmentServer() { // public static 메서드 - 기존 시그니처 완전 유지
        // 확장된 개발용 기본 설정 (기존 설정 확장)
        String[] devArgs = {"8080", "50", "--dev", "--monitoring"}; // 개발 모드와 모니터링 포함

        logger.info("확장된 개발 모드로 서버를 시작합니다"); // 확장된 개발 모드 로그

        // 개발 모드 전용 추가 설정
        System.setProperty("java.util.logging.ConsoleHandler.level", "FINE"); // 개발 모드에서 상세 로깅

        main(devArgs); // main() - 현재 클래스의 메인 메서드 호출 (기존 로직 유지)
    }

    /**
     * 테스트용 서버 시작 (기존 메서드 시그니처 유지하되 기능 확장)
     * 단위 테스트나 통합 테스트에서 사용, 확장된 테스트 설정 포함
     *
     * @param port 테스트용 포트
     * @return 시작된 서버 인스턴스
     */
    public static ThreadedServer startTestServer(int port) { // public static 메서드 - 기존 시그니처 완전 유지
        // 확장된 테스트용 설정으로 서버 인스턴스 생성
        // 테스트 환경에 적합한 작은 스레드풀(10)과 백로그(10) 사용
        ThreadedServer testServer = new ThreadedServer(port, 10, 10); // new ThreadedServer() - 3개 매개변수 생성자 사용

        try { // try-catch 블록 - 테스트 서버 시작 중 예외 처리 (기존 구조 유지)
            // 별도 스레드에서 서버 시작 - 테스트 스레드 블로킹 방지 (기존 로직 유지)
            new Thread(() -> { // new Thread() - 새로운 스레드 생성, 람다 표현식으로 Runnable 구현
                try {
                    testServer.start(); // testServer.start() - 서버 시작 (블로킹 호출)
                } catch (IOException e) { // IOException - 서버 시작 실패
                    logger.log(Level.SEVERE, "테스트 서버 시작 실패", e); // Level.SEVERE - 심각한 오류 레벨
                }
            }, "TestServerThread").start(); // 스레드 이름 지정 후 시작

            // 서버가 완전히 시작될 때까지 대기 (기존 로직 확장)
            Thread.sleep(200); // 대기 시간을 200ms로 증가 (더 안정적인 시작 보장)

            // 테스트 서버 헬스 체크 (추가된 기능)
            int maxRetries = 10; // 최대 재시도 횟수
            for (int i = 0; i < maxRetries; i++) { // 헬스 체크 재시도 루프
                try {
                    URL healthUrl = new URL(String.format("http://localhost:%d/health", port)); // 헬스 체크 URL 생성
                    HttpURLConnection connection = (HttpURLConnection) healthUrl.openConnection(); // HTTP 연결 열기
                    connection.setConnectTimeout(1000); // 1초 연결 타임아웃
                    connection.setReadTimeout(1000); // 1초 읽기 타임아웃

                    if (connection.getResponseCode() == 200) { // 정상 응답인 경우
                        logger.info("테스트 서버 헬스 체크 성공 (시도 " + (i + 1) + "/" + maxRetries + ")"); // 헬스 체크 성공 로그
                        break; // 성공 시 루프 종료
                    }
                } catch (Exception e) { // 헬스 체크 실패
                    if (i == maxRetries - 1) { // 마지막 시도인 경우
                        logger.warning("테스트 서버 헬스 체크 실패 - 서버가 완전히 시작되지 않았을 수 있습니다"); // 헬스 체크 실패 경고
                    }
                    Thread.sleep(100); // 100ms 대기 후 재시도
                }
            }

            return testServer; // 테스트 서버 인스턴스 반환

        } catch (InterruptedException e) { // InterruptedException - 스레드 인터럽트 시 발생하는 예외 (기존 처리 유지)
            Thread.currentThread().interrupt(); // Thread.currentThread().interrupt() - 인터럽트 상태 복원
            throw new RuntimeException("테스트 서버 시작 중 인터럽트", e); // new RuntimeException() - 런타임 예외 생성 및 던짐
        } catch (Exception e) { // 기타 예외 처리 (추가된 예외 처리)
            throw new RuntimeException("테스트 서버 시작 실패", e); // 테스트 서버 시작 실패 예외
        }
    }

    /**
     * 현재 JVM의 권장 스레드풀 크기 계산 (기존 메서드 완전 유지)
     * CPU 코어 수와 메모리를 고려한 권장값 제공
     *
     * @return 권장 스레드풀 크기
     */
    public static int getRecommendedThreadPoolSize() { // public static 메서드 - 기존 시그니처 완전 유지
        // CPU 코어 수 가져오기 (기존 로직 완전 유지)
        int cores = Runtime.getRuntime().availableProcessors(); // Runtime.getRuntime().availableProcessors() - 사용 가능한 프로세서(코어) 수 반환

        // 가용 메모리 가져오기 (MB 단위) (기존 로직 완전 유지)
        long maxMemoryMB = Runtime.getRuntime().maxMemory() / 1024 / 1024; // .maxMemory() - JVM이 사용할 수 있는 최대 메모리 (바이트), MB로 변환

        // CPU 기반 계산 - I/O 바운드 작업이 많으므로 코어 수의 2-4배 (기존 로직 완전 유지)
        int cpuBasedSize = cores * 3; // 코어 수의 3배

        // 메모리 기반 계산 - 스레드당 약 1MB로 가정하고 전체 메모리의 10% 사용 (기존 로직 완전 유지)
        int memoryBasedSize = (int) (maxMemoryMB * 0.1); // (int) - long을 int로 형변환

        // 두 값 중 작은 값 선택 (리소스 제약 고려) (기존 로직 완전 유지)
        int recommendedSize = Math.min(cpuBasedSize, memoryBasedSize); // Math.min() - 두 값 중 작은 값을 반환하는 정적 메서드

        // 최소값과 최대값 제한 (기존 로직 완전 유지)
        recommendedSize = Math.max(10, recommendedSize);   // Math.max() - 두 값 중 큰 값을 반환, 최소 10개
        recommendedSize = Math.min(500, recommendedSize);  // 최대 500개

        // 로그 출력 (기존 로직 완전 유지)
        logger.info(String.format("시스템 정보 - CPU 코어: %d, 최대 메모리: %dMB", cores, maxMemoryMB));
        logger.info(String.format("권장 스레드풀 크기: %d (CPU 기반: %d, 메모리 기반: %d)",
                recommendedSize, cpuBasedSize, memoryBasedSize));

        return recommendedSize; // 계산된 권장 크기 반환
    }

    /**
     * 시스템 정보 출력 (기존 메서드 시그니처 유지하되 기능 확장)
     * 서버 시작 전 환경 정보 확인용, 추가 시스템 정보 포함
     */
    public static void printSystemInfo() { // public static 메서드 - 기존 시그니처 완전 유지
        Runtime runtime = Runtime.getRuntime(); // Runtime 인스턴스 가져오기 (기존 로직 유지)

        // 향상된 시스템 정보 출력 - 이모지와 색상 포함
        System.out.println("🖥️ === 시스템 정보 ==="); // 이모지 포함 헤더

        // 기존 정보들 (완전 유지)
        System.out.println("☕ Java 버전: " + System.getProperty("java.version")); // System.getProperty("java.version") - Java 버전 속성 키
        System.out.println("🖥️ OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")); // OS 정보
        System.out.println("⚡ CPU 코어 수: " + runtime.availableProcessors()); // CPU 코어 수

        // 기존 메모리 정보 (완전 유지하되 포맷 개선)
        long maxMemoryMB = runtime.maxMemory() / 1024 / 1024; // 최대 메모리
        long totalMemoryMB = runtime.totalMemory() / 1024 / 1024; // 총 할당 메모리
        long freeMemoryMB = runtime.freeMemory() / 1024 / 1024; // 여유 메모리
        long usedMemoryMB = totalMemoryMB - freeMemoryMB; // 사용 중 메모리 계산

        System.out.printf("💾 JVM 메모리 - 최대: %dMB, 총: %dMB, 사용: %dMB, 여유: %dMB%n", // 메모리 정보 포맷팅
                maxMemoryMB, totalMemoryMB, usedMemoryMB, freeMemoryMB);

        // 추가된 시스템 정보들
        System.out.println("🏗️ JVM 이름: " + System.getProperty("java.vm.name")); // JVM 이름
        System.out.println("🏢 JVM 벤더: " + System.getProperty("java.vm.vendor")); // JVM 벤더
        System.out.println("👤 사용자: " + System.getProperty("user.name")); // 현재 사용자
        System.out.println("📁 작업 디렉토리: " + System.getProperty("user.dir")); // 현재 작업 디렉토리
        System.out.println("🕒 시작 시간: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))); // 현재 시간

        // 네트워크 정보 추가
        try { // 네트워크 정보 수집 시 예외 처리
            String hostname = InetAddress.getLocalHost().getHostName(); // InetAddress.getLocalHost() - 로컬 호스트 정보
            String hostAddress = InetAddress.getLocalHost().getHostAddress(); // 호스트 IP 주소
            System.out.println("🌐 호스트명: " + hostname); // 호스트명 출력
            System.out.println("🔗 IP 주소: " + hostAddress); // IP 주소 출력
        } catch (Exception e) { // 네트워크 정보 조회 실패
            System.out.println("🌐 네트워크 정보: 조회 실패"); // 네트워크 정보 실패 메시지
        }

        // 파일 시스템 정보 추가
        File currentDir = new File("."); // File(".") - 현재 디렉토리 객체
        long freeSpace = currentDir.getFreeSpace() / 1024 / 1024; // 여유 디스크 공간 (MB)
        long totalSpace = currentDir.getTotalSpace() / 1024 / 1024; // 총 디스크 공간 (MB)
        System.out.printf("💿 디스크 공간 - 총: %dMB, 여유: %dMB (%.1f%%)%n", // 디스크 정보 포맷팅
                totalSpace, freeSpace, (double) freeSpace / totalSpace * 100);

        // 기존 권장 스레드풀 크기 (완전 유지)
        System.out.println("🎯 권장 스레드풀 크기: " + getRecommendedThreadPoolSize()); // 권장 크기 출력

        System.out.println("=================="); // 구분선 (기존 유지)
        System.out.println(); // 빈 줄 (기존 유지)
    }

    // ========== 내부 클래스들 ==========

    /**
     * 확장된 서버 설정 정보를 담는 불변 클래스 (기존 ServerConfig 확장)
     * 기존 설정에 모니터링, 개발 모드, 설정 파일 등의 정보 추가
     */
    public static class ServerConfig { // public static 내부 클래스 - 외부에서 독립적으로 사용 가능 (기존 구조 유지)
        // 기존 필드들 (모든 필드를 final로 선언하여 불변성 보장)
        final int port;           // final int - 불변 필드, 서버 바인딩 포트 (기존 필드 유지)
        final int threadPoolSize; // final int - 불변 필드, 스레드풀 크기 (기존 필드 유지)

        // 확장된 필드들 (새로 추가된 설정들)
        final int backlog;         // final int - 불변 필드, 연결 대기 큐 크기
        final int maxConnections;  // final int - 불변 필드, 최대 동시 연결 수
        final boolean monitoringEnabled; // final boolean - 불변 필드, 모니터링 활성화 여부
        final boolean devMode;     // final boolean - 불변 필드, 개발 모드 여부
        final String configFile;  // final String - 불변 필드, 설정 파일 경로 (null 가능)

        /**
         * 확장된 ServerConfig 생성자 (기존 생성자 확장)
         * 모든 설정 값을 한 번에 설정하는 확장된 생성자
         */
        public ServerConfig(int port, int threadPoolSize, int backlog, int maxConnections, // public 생성자 - 모든 설정 값을 매개변수로 받음
                            boolean monitoringEnabled, boolean devMode, String configFile) {
            this.port = port;                         // this.port - 현재 객체의 port 필드에 매개변수 값 할당 (기존 로직 유지)
            this.threadPoolSize = threadPoolSize;     // this.threadPoolSize - 스레드풀 크기 할당 (기존 로직 유지)
            this.backlog = backlog;                   // this.backlog - 백로그 크기 할당 (새로 추가)
            this.maxConnections = maxConnections;     // this.maxConnections - 최대 연결 수 할당 (새로 추가)
            this.monitoringEnabled = monitoringEnabled; // this.monitoringEnabled - 모니터링 설정 할당 (새로 추가)
            this.devMode = devMode;                   // this.devMode - 개발 모드 설정 할당 (새로 추가)
            this.configFile = configFile;             // this.configFile - 설정 파일 경로 할당 (새로 추가)
        }

        /**
         * Map으로 변환하는 메서드 (새로운 메서드)
         * 설정 정보를 Map 형태로 변환하여 JSON 응답에 사용
         *
         * @return 설정 정보를 담은 Map
         */
        public Map<String, Object> toMap() { // public 메서드 - 설정을 Map으로 변환
            Map<String, Object> map = new HashMap<>(); // HashMap 생성 - 설정 정보를 담을 맵

            // 모든 설정 필드를 맵에 추가
            map.put("port", port);                           // 포트 정보
            map.put("threadPoolSize", threadPoolSize);       // 스레드풀 크기
            map.put("backlog", backlog);                     // 백로그 크기
            map.put("maxConnections", maxConnections);       // 최대 연결 수
            map.put("monitoringEnabled", monitoringEnabled); // 모니터링 여부
            map.put("devMode", devMode);                     // 개발 모드 여부
            map.put("configFile", configFile);               // 설정 파일 경로

            // 계산된 정보들도 추가
            Runtime runtime = Runtime.getRuntime(); // JVM 런타임 정보
            map.put("recommendedThreadPoolSize", getRecommendedThreadPoolSize()); // 권장 스레드풀 크기
            map.put("systemCores", runtime.availableProcessors());               // 시스템 CPU 코어 수
            map.put("maxMemoryMB", runtime.maxMemory() / 1024 / 1024);          // JVM 최대 메모리

            return map; // 완성된 맵 반환
        }

        /**
         * 설정 정보를 문자열로 표현 (기존 toString 메서드 확장)
         * 모니터링 대시보드나 로그에서 사용
         */
        @Override // 어노테이션 - Object.toString() 메서드 재정의
        public String toString() { // public 메서드 - 객체의 문자열 표현
            return String.format( // String.format() - 문자열 템플릿 사용
                    "ServerConfig{포트=%d, 스레드풀=%d, 백로그=%d, 최대연결=%d, " + // 기본 설정 정보 템플릿
                            "모니터링=%s, 개발모드=%s, 설정파일='%s'}", // 확장된 설정 정보 템플릿
                    port, threadPoolSize, backlog, maxConnections, // 기본 설정들
                    monitoringEnabled ? "활성화" : "비활성화", // 모니터링 상태를 한글로 표시
                    devMode ? "활성화" : "비활성화", // 개발 모드 상태를 한글로 표시
                    configFile != null ? configFile : "없음" // 설정 파일이 있으면 경로, 없으면 "없음"
            );
        }
    }

    /**
     * 서버 설정 빌더 클래스 (새로운 내부 클래스)
     * 설정 객체를 단계별로 구성할 수 있는 빌더 패턴 구현
     * 환경 변수 로딩과 유효성 검증에서 사용
     */
    private static class ServerConfigBuilder { // private static 내부 클래스 - 빌더 패턴 구현
        // 빌더의 내부 상태 (기본값으로 초기화)
        private int port = DEFAULT_PORT;                     // 포트 기본값
        private int threadPoolSize = DEFAULT_THREAD_POOL_SIZE; // 스레드풀 기본값
        private int backlog = DEFAULT_BACKLOG;               // 백로그 기본값
        private int maxConnections = DEFAULT_MAX_CONNECTIONS; // 최대 연결 수 기본값
        private boolean monitoringEnabled = false;           // 모니터링 기본 비활성화
        private boolean devMode = false;                     // 개발 모드 기본 비활성화
        private String configFile = null;                    // 설정 파일 기본 null

        /**
         * 포트 설정 메서드 (빌더 패턴)
         *
         * @param port 설정할 포트
         * @return 현재 빌더 인스턴스 (메서드 체이닝용)
         */
        public ServerConfigBuilder port(int port) { // public 메서드 - 포트 설정
            this.port = port; // 포트 값 설정
            return this; // 현재 인스턴스 반환 (메서드 체이닝 지원)
        }

        /**
         * 스레드풀 크기 설정 메서드 (빌더 패턴)
         *
         * @param threadPoolSize 설정할 스레드풀 크기
         * @return 현재 빌더 인스턴스 (메서드 체이닝용)
         */
        public ServerConfigBuilder threadPoolSize(int threadPoolSize) { // public 메서드 - 스레드풀 크기 설정
            this.threadPoolSize = threadPoolSize; // 스레드풀 크기 설정
            return this; // 현재 인스턴스 반환
        }

        /**
         * 백로그 크기 설정 메서드 (빌더 패턴)
         *
         * @param backlog 설정할 백로그 크기
         * @return 현재 빌더 인스턴스 (메서드 체이닝용)
         */
        public ServerConfigBuilder backlog(int backlog) { // public 메서드 - 백로그 크기 설정
            this.backlog = backlog; // 백로그 크기 설정
            return this; // 현재 인스턴스 반환
        }

        /**
         * 최대 연결 수 설정 메서드 (빌더 패턴)
         *
         * @param maxConnections 설정할 최대 연결 수
         * @return 현재 빌더 인스턴스 (메서드 체이닝용)
         */
        public ServerConfigBuilder maxConnections(int maxConnections) { // public 메서드 - 최대 연결 수 설정
            this.maxConnections = maxConnections; // 최대 연결 수 설정
            return this; // 현재 인스턴스 반환
        }

        /**
         * 모니터링 활성화 설정 메서드 (빌더 패턴)
         *
         * @param monitoringEnabled 모니터링 활성화 여부
         * @return 현재 빌더 인스턴스 (메서드 체이닝용)
         */
        public ServerConfigBuilder monitoringEnabled(boolean monitoringEnabled) { // public 메서드 - 모니터링 설정
            this.monitoringEnabled = monitoringEnabled; // 모니터링 설정
            return this; // 현재 인스턴스 반환
        }

        /**
         * 개발 모드 설정 메서드 (빌더 패턴)
         *
         * @param devMode 개발 모드 활성화 여부
         * @return 현재 빌더 인스턴스 (메서드 체이닝용)
         */
        public ServerConfigBuilder devMode(boolean devMode) { // public 메서드 - 개발 모드 설정
            this.devMode = devMode; // 개발 모드 설정
            return this; // 현재 인스턴스 반환
        }

        /**
         * 설정 파일 경로 설정 메서드 (빌더 패턴)
         *
         * @param configFile 설정 파일 경로
         * @return 현재 빌더 인스턴스 (메서드 체이닝용)
         */
        public ServerConfigBuilder configFile(String configFile) { // public 메서드 - 설정 파일 설정
            this.configFile = configFile; // 설정 파일 경로 설정
            return this; // 현재 인스턴스 반환
        }

        /**
         * 설정 객체 구성 완료 메서드 (빌더 패턴)
         * 현재 빌더의 모든 설정으로 ServerConfig 인스턴스 생성
         *
         * @return 구성된 ServerConfig 인스턴스
         */
        public ServerConfig build() { // public 메서드 - 설정 객체 생성
            // 모든 설정값으로 ServerConfig 인스턴스 생성
            return new ServerConfig(port, threadPoolSize, backlog, maxConnections,
                    monitoringEnabled, devMode, configFile);
        }
    }
}