package com.serverarch.eventloop; // 패키지 선언 - 이벤트 루프 서버 아키텍처 패키지

// === 표준 라이브러리 Import ===
import java.io.*; // IOException 등 I/O 관련 예외 클래스들
import java.util.logging.*; // Logger, Level, Handler 등 로깅 관련 클래스들
import java.util.concurrent.*; // CompletableFuture, ScheduledExecutorService 등 비동기 처리 클래스들
import java.util.*; // Map, List, HashMap 등 컬렉션 클래스들

// === EventLoop 모듈 Import ===
import com.serverarch.eventloop.core.EventLoopServer; // EventLoop 서버 코어 클래스
import com.serverarch.eventloop.routing.Router; // EventLoop용 Router 인터페이스
import com.serverarch.eventloop.http.HttpResponse; // EventLoop용 HttpResponse 인터페이스

/**
 * EventLoopServerLauncher - EventLoop 서버 실행 및 관리 클래스
 *
 * 이 클래스는 EventLoopServer를 실행하고 관리하는 런처입니다.
 *
 * 주요 기능:
 * 1. 명령행 인수 파싱 및 서버 설정
 * 2. 로깅 시스템 초기화 및 설정
 * 3. 서버 생성 및 예시 라우트 등록
 * 4. 통계 모니터링 및 주기적 출력
 * 5. 그레이스풀 셧다운 처리
 * 6. 메인 서버 실행 루프 관리
 *
 * 설계 특징:
 * - 운영 환경에 적합한 로깅 설정
 * - 통계 모니터링으로 실시간 상태 추적
 * - 안전한 서버 종료를 위한 셧다운 훅
 * - 경로 파라미터를 활용한 다양한 예시 엔드포인트
 * - 명령행 인터페이스로 쉬운 서버 설정
 */
public class EventLoopServerLauncher { // public 클래스 선언 - EventLoop 서버 실행기

    // === 로깅 시스템 ===
    // static final Logger: 클래스 레벨 로거로 모든 런처 동작을 추적
    private static final Logger logger = Logger.getLogger(EventLoopServerLauncher.class.getName()); // Logger.getLogger() - 클래스명 기반 로거 생성

    // === 서버 설정 기본값들 ===
    private static final int DEFAULT_PORT = 8082; // 기본 포트 번호 - EventLoop 서버 전용 포트
    private static final int DEFAULT_BACKLOG = 1024; // 기본 백로그 크기 - 연결 대기 큐 크기
    private static final int STATISTICS_INTERVAL_SECONDS = 30; // 통계 출력 간격 (초) - 실시간 모니터링용

    // === 전역 서버 상태 관리 ===
    private static EventLoopServer server; // 실행 중인 서버 인스턴스 (null이면 미실행 상태)
    private static volatile boolean shutdownRequested = false; // volatile - 스레드 간 안전한 종료 플래그 공유
    private static ScheduledExecutorService statisticsExecutor; // 통계 모니터링 전용 스케줄러

    /**
     * 메인 메서드 - 애플리케이션 진입점
     * EventLoop 서버의 전체 생명주기를 관리하는 메인 실행 흐름
     *
     * @param args 명령행 인수 배열 - [포트번호] [백로그크기]
     */
    public static void main(String[] args) { // main 메서드 - JVM이 호출하는 애플리케이션 진입점
        try {
            // 1. 로깅 시스템 초기화
            setupLogging(); // 콘솔과 파일 로깅 설정

            // 2. 명령행 인수 파싱
            ServerConfig config = parseCommandLineArgs(args); // args 배열을 파싱하여 서버 설정 객체 생성

            // 3. 시작 배너 출력
            printStartupBanner(config); // 서버 정보와 설정을 시각적으로 표시

            // 4. 서버 생성 및 라우트 설정
            server = createAndConfigureServer(config); // EventLoopServer 인스턴스 생성 및 라우트 등록

            // 5. 그레이스풀 셧다운 훅 등록
            registerShutdownHook(); // Ctrl+C 시 안전한 서버 종료 처리

            // 6. 통계 모니터링 시작
            startStatisticsMonitoring(); // 주기적 서버 상태 모니터링 시작

            // 7. 서버 시작
            logger.info("EventLoopServer 시작 중..."); // 서버 시작 로그
            server.start(); // EventLoopServer.start() - 서버 시작 (논블로킹)

            // 8. 메인 루프 실행
            runMainLoop(); // 서버 모니터링 및 대기 루프

        } catch (Exception e) { // 모든 예외를 포착하여 안전한 종료 보장
            logger.log(Level.SEVERE, "EventLoopServer 시작 실패", e); // 심각한 오류 로그
            System.exit(1); // System.exit() - JVM 종료 (오류 코드 1)
        }
    }

    /**
     * 로깅 시스템 설정
     * 콘솔과 파일 로깅을 동시에 지원하는 로깅 환경 구축
     *
     * 설정 내용:
     * - 콘솔 출력: INFO 레벨, 간결한 포맷
     * - 파일 출력: FINE 레벨, 상세한 로그 기록
     * - 커스텀 포매터: 읽기 쉬운 로그 형식
     */
    private static void setupLogging() { // private static 메서드 - 내부에서만 사용하는 로깅 설정
        try {
            // 기존 로그 핸들러 제거 (깔끔한 로깅 환경 구축)
            Logger rootLogger = Logger.getLogger(""); // Logger.getLogger("") - 루트 로거 조회
            Handler[] handlers = rootLogger.getHandlers(); // Logger.getHandlers() - 현재 등록된 핸들러 배열 조회
            for (Handler handler : handlers) { // for-each 반복문 - 모든 핸들러 순회
                rootLogger.removeHandler(handler); // Logger.removeHandler() - 기존 핸들러 제거
            }

            // 콘솔 로그 핸들러 설정
            ConsoleHandler consoleHandler = new ConsoleHandler(); // new ConsoleHandler() - 콘솔 출력 핸들러 생성
            consoleHandler.setLevel(Level.INFO); // Handler.setLevel() - 콘솔은 INFO 레벨 이상만 출력
            consoleHandler.setFormatter(new SimpleFormatter() { // 익명 클래스로 커스텀 포매터 생성
                @Override
                public String format(LogRecord record) { // SimpleFormatter.format() 메서드 오버라이드
                    return String.format("[%s] %s - %s%n", // String.format() - 형식화된 문자열 생성
                            record.getLevel(), // LogRecord.getLevel() - 로그 레벨 (INFO, WARNING 등)
                            record.getLoggerName().substring(record.getLoggerName().lastIndexOf('.') + 1), // 클래스명만 추출 (패키지명 제외)
                            record.getMessage() // LogRecord.getMessage() - 실제 로그 메시지
                    );
                }
            });

            // 루트 로거에 콘솔 핸들러 등록
            rootLogger.addHandler(consoleHandler); // Logger.addHandler() - 핸들러를 로거에 추가
            rootLogger.setLevel(Level.INFO); // 루트 로거 레벨을 INFO로 설정

            // 파일 로그 핸들러 설정 시도
            try {
                FileHandler fileHandler = new FileHandler("logs/eventloop-server.log", true); // new FileHandler() - 파일 로그 핸들러 생성 (append 모드)
                fileHandler.setLevel(Level.FINE); // 파일은 FINE 레벨까지 상세 로그 기록
                fileHandler.setFormatter(new SimpleFormatter()); // 표준 포매터 사용
                rootLogger.addHandler(fileHandler); // 파일 핸들러도 루트 로거에 추가
            } catch (IOException e) { // 파일 핸들러 생성 실패 시 (디렉토리 없음 등)
                logger.warning("파일 로그 핸들러 생성 실패: " + e.getMessage()); // 경고 로그 출력
            }

            logger.info("로깅 시스템 초기화 완료"); // 로깅 설정 완료 로그

        } catch (Exception e) { // 로깅 설정 중 예외 발생
            System.err.println("로깅 설정 실패: " + e.getMessage()); // System.err - 표준 에러 스트림으로 출력
        }
    }

    /**
     * 명령행 인수 파싱
     * 사용자가 제공한 명령행 인수를 파싱하여 서버 설정 생성
     *
     * 지원 형식:
     * - java Launcher
     * - java Launcher [포트]
     * - java Launcher [포트] [백로그]
     *
     * @param args 명령행 인수 배열
     * @return ServerConfig 파싱된 서버 설정 객체
     */
    private static ServerConfig parseCommandLineArgs(String[] args) { // private static 메서드 - 명령행 인수 파싱
        int port = DEFAULT_PORT; // 기본 포트로 초기화
        int backlog = DEFAULT_BACKLOG; // 기본 백로그로 초기화

        try {
            // 첫 번째 인수: 포트 번호
            if (args.length > 0) { // 배열 길이 확인 - 첫 번째 인수가 있는지
                port = Integer.parseInt(args[0]); // Integer.parseInt() - 문자열을 정수로 변환
                if (port < 1 || port > 65535) { // 유효한 포트 범위 확인 (1-65535)
                    throw new IllegalArgumentException("포트 번호는 1-65535 사이여야 합니다: " + port);
                }
            }

            // 두 번째 인수: 백로그 크기
            if (args.length > 1) { // 두 번째 인수가 있는지 확인
                backlog = Integer.parseInt(args[1]); // 백로그 크기 파싱
                if (backlog < 1) { // 백로그는 최소 1 이상이어야 함
                    throw new IllegalArgumentException("백로그 크기는 1 이상이어야 합니다: " + backlog);
                }
            }

            // 추가 인수가 있으면 경고 (무시됨)
            if (args.length > 2) { // 2개를 초과하는 인수가 있는 경우
                logger.warning(String.format("추가 인수 %d개가 무시됩니다", args.length - 2)); // 무시되는 인수 개수 경고
            }

        } catch (NumberFormatException e) { // 숫자 파싱 실패
            logger.severe("잘못된 숫자 형식의 인수: " + e.getMessage()); // 심각한 오류 로그
            printUsage(); // 사용법 출력
            System.exit(1); // 프로그램 종료
        } catch (IllegalArgumentException e) { // 유효하지 않은 값
            logger.severe(e.getMessage()); // 오류 메시지 로그
            printUsage(); // 사용법 출력
            System.exit(1); // 프로그램 종료
        }

        return new ServerConfig(port, backlog); // 파싱된 설정으로 ServerConfig 객체 생성
    }

    /**
     * 사용법 출력
     * 명령행 인수 오류 시 올바른 사용법을 사용자에게 안내
     */
    private static void printUsage() { // private static 메서드 - 사용법 안내
        System.out.println("사용법: java " + EventLoopServerLauncher.class.getName() + " [포트] [백로그]"); // 기본 사용법
        System.out.println("  포트: 서버 포트 번호 (1-65535, 기본값: " + DEFAULT_PORT + ")"); // 포트 설명
        System.out.println("  백로그: 연결 대기 큐 크기 (1 이상, 기본값: " + DEFAULT_BACKLOG + ")"); // 백로그 설명
        System.out.println(); // 빈 줄
        System.out.println("예시:"); // 예시 섹션
        System.out.println("  java " + EventLoopServerLauncher.class.getName()); // 기본 실행
        System.out.println("  java " + EventLoopServerLauncher.class.getName() + " 8080"); // 포트만 지정
        System.out.println("  java " + EventLoopServerLauncher.class.getName() + " 8080 2048"); // 포트와 백로그 지정
    }

    /**
     * 시작 배너 출력
     * 서버 시작 시 시각적으로 구분되는 배너와 설정 정보 출력
     *
     * @param config 출력할 서버 설정 정보
     */
    private static void printStartupBanner(ServerConfig config) { // private static 메서드 - 시작 배너 출력
        System.out.println(); // 빈 줄
        System.out.println("========================================"); // 구분선
        System.out.println("🚀 EventLoop HTTP Server Starting..."); // 시작 메시지 (이모지 포함)
        System.out.println("========================================"); // 구분선
        System.out.println("Architecture: Single-Threaded Event Loop"); // 아키텍처 정보
        System.out.println("Port: " + config.getPort()); // ServerConfig.getPort() - 설정된 포트 출력
        System.out.println("Backlog: " + config.getBacklog()); // ServerConfig.getBacklog() - 설정된 백로그 출력
        System.out.println("Java Version: " + System.getProperty("java.version")); // System.getProperty() - JVM 버전 정보
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")); // 운영체제 정보
        System.out.println("Available Processors: " + Runtime.getRuntime().availableProcessors()); // Runtime.getRuntime().availableProcessors() - CPU 코어 수
        System.out.println("Max Memory: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + "MB"); // 최대 메모리 크기 (MB 단위)
        System.out.println("========================================"); // 구분선
        System.out.println(); // 빈 줄

        // 기본 엔드포인트 안내
        logger.info("기본 엔드포인트:"); // 기본 엔드포인트 섹션 시작
        logger.info("  - 헬스 체크: http://localhost:" + config.getPort() + "/health"); // 헬스 체크 URL
        logger.info("  - 메트릭: http://localhost:" + config.getPort() + "/metrics"); // 메트릭 URL
        logger.info("  - 서버 정보: http://localhost:" + config.getPort() + "/info"); // 서버 정보 URL
        logger.info("  - Hello World: http://localhost:" + config.getPort() + "/hello"); // 테스트 URL
    }

    /**
     * 서버 생성 및 설정
     * EventLoopServer 인스턴스를 생성하고 예시 라우트를 등록
     *
     * @param config 서버 설정 정보
     * @return EventLoopServer 설정이 완료된 서버 인스턴스
     */
    private static EventLoopServer createAndConfigureServer(ServerConfig config) { // private static 메서드 - 서버 생성 및 설정
        EventLoopServer server = new EventLoopServer(config.getPort(), config.getBacklog()); // new EventLoopServer() - 설정된 포트와 백로그로 서버 생성
        Router router = server.getRouter(); // EventLoopServer.getRouter() - 서버의 라우터 인스턴스 조회
        registerExampleRoutes(router); // 예시 라우트들을 라우터에 등록

        logger.info("서버 생성 및 설정 완료"); // 서버 설정 완료 로그
        return server; // 설정이 완료된 서버 인스턴스 반환
    }

    /**
     * 예시 라우트 등록
     * EventLoop 서버의 기능을 시연하는 다양한 예시 엔드포인트 등록
     *
     * 등록되는 라우트:
     * 1. POST /echo - 요청 정보 에코 (HTTP 요청 분석용, 헤더/바디/파라미터 포함)
     * 2. GET /delay/{seconds} - 지연 응답 (비동기 처리 테스트용, 경로 파라미터 활용)
     * 3. GET /cpu/{iterations} - CPU 집약적 작업 (스레드풀 활용 테스트용, 경로 파라미터 활용)
     * 4. GET /status - 서버 상태 정보 (시스템 모니터링용, JVM 메모리 정보 포함)
     *
     * @param router EventLoop용 라우터 인스턴스
     */
    private static void registerExampleRoutes(Router router) { // private static 메서드 - 예시 라우트 등록
        logger.info("예시 라우트 등록 중..."); // 라우트 등록 시작 로그

        // 1. Echo 서비스 - POST 요청의 모든 정보를 JSON으로 응답
        router.post("/echo", request -> { // Router.post() - POST 요청 라우트 등록, 람다로 AsyncRouteHandler 구현
            Map<String, Object> echo = new HashMap<>(); // 에코 정보를 담을 맵 생성
            echo.put("method", request.getMethod()); // HttpRequest.getMethod() - HTTP 메서드 추가
            echo.put("path", request.getPath()); // HttpRequest.getPath() - 요청 경로 추가
            echo.put("body", request.getBodyAsString()); // HttpRequest.getBodyAsString() - 요청 바디를 문자열로 추가
            echo.put("timestamp", System.currentTimeMillis()); // System.currentTimeMillis() - 현재 타임스탬프 추가

            // 헤더 정보 추가
            Map<String, Object> headers = new HashMap<>(); // 헤더를 담을 맵 생성
            for (String headerName : request.getHeaders().getNames()) { // HttpHeaders.getNames() - 모든 헤더 이름 순회
                headers.put(headerName, request.getHeaders().get(headerName)); // HttpHeaders.get() - 헤더 값들을 맵에 추가
            }
            echo.put("headers", headers); // 헤더 맵을 에코 응답에 추가

            // 경로 파라미터와 쿼리 파라미터 추가 (디버깅 및 테스트 목적)
            echo.put("pathParameters", request.getPathParameters()); // HttpRequest.getPathParameters() - 경로 파라미터 맵 추가
            echo.put("queryParameters", request.getQueryParameters()); // HttpRequest.getQueryParameters() - 쿼리 파라미터 맵 추가

            // JSON 응답 생성 및 비동기 반환
            String jsonResponse = convertToJson(echo); // 맵을 JSON 문자열로 변환
            HttpResponse response = HttpResponse.ok(jsonResponse); // HttpResponse.ok() - 200 OK 응답 생성
            response.getHeaders().set("Content-Type", "application/json; charset=UTF-8"); // Content-Type 헤더를 JSON으로 설정

            return CompletableFuture.completedFuture(response); // CompletableFuture.completedFuture() - 즉시 완료된 Future 반환
        });

        // 2. 지연 응답 테스트 - 비동기 처리 능력 시연 (경로 파라미터 활용)
        router.get("/delay/{seconds}", request -> { // Router.get() - GET 요청 라우트 등록, 경로 파라미터 포함
            String secondsStr = request.getPathParameter("seconds"); // 경로 파라미터에서 지연 시간 추출 (라우터가 자동으로 파싱하여 설정)
            int delaySeconds = 1; // 기본 지연 시간 1초

            try {
                if (secondsStr != null) { // null 체크 - 경로 파라미터가 존재하는 경우
                    delaySeconds = Integer.parseInt(secondsStr); // Integer.parseInt() - 문자열을 정수로 변환
                    delaySeconds = Math.max(1, Math.min(delaySeconds, 10)); // Math.max/min - 1-10초 범위로 제한 (안전성 보장)
                }
            } catch (NumberFormatException e) { // 숫자 파싱 실패 시 기본값 사용
                logger.warning("잘못된 지연 시간 파라미터: " + secondsStr + ", 기본값 1초 사용"); // 경고 로그
                delaySeconds = 1; // 안전한 기본값으로 복원
            }

            final int finalDelaySeconds = delaySeconds; // final 변수 - 람다에서 사용하기 위해
            return CompletableFuture
                    .supplyAsync(() -> { // CompletableFuture.supplyAsync() - 별도 스레드에서 비동기 실행
                        try {
                            Thread.sleep(finalDelaySeconds * 1000); // Thread.sleep() - 지정된 시간만큼 대기 (밀리초)
                        } catch (InterruptedException e) { // 인터럽트 예외 처리
                            Thread.currentThread().interrupt(); // 현재 스레드의 인터럽트 상태 복원
                        }
                        return String.format("지연 응답 완료: %d초 대기 (경로 파라미터: %s)", finalDelaySeconds, secondsStr); // 지연 완료 메시지
                    })
                    .thenApply(message -> { // CompletableFuture.thenApply() - 비동기 결과를 HttpResponse로 변환
                        HttpResponse response = HttpResponse.ok(message); // 성공 응답 생성
                        response.getHeaders().set("Content-Type", "text/plain; charset=UTF-8"); // 텍스트 Content-Type 설정
                        return response; // 완성된 응답 반환
                    });
        });

        // 3. CPU 집약적 작업 - 스레드풀 활용 시연 (경로 파라미터 활용)
        router.get("/cpu/{iterations}", request -> { // CPU 부하 테스트 라우트
            String iterStr = request.getPathParameter("iterations"); // 반복 횟수 파라미터 추출 (라우터가 자동으로 파싱하여 설정)
            int iterations = 1000000; // 기본 반복 횟수 100만 회

            try {
                if (iterStr != null) { // null 체크 - 경로 파라미터가 존재하는 경우
                    iterations = Integer.parseInt(iterStr); // Integer.parseInt() - 문자열을 정수로 변환
                    iterations = Math.max(1000, Math.min(iterations, 10000000)); // 1천-1천만 범위로 제한 (시스템 안정성 보장)
                }
            } catch (NumberFormatException e) { // 파싱 실패 처리
                logger.warning("잘못된 반복 횟수 파라미터: " + iterStr + ", 기본값 1,000,000회 사용"); // 경고 로그
                iterations = 1000000; // 안전한 기본값으로 복원
            }

            final int finalIterations = iterations; // final 변수 - 람다에서 사용
            return CompletableFuture.supplyAsync(() -> { // 별도 스레드에서 CPU 집약적 작업 실행
                long startTime = System.currentTimeMillis(); // 시작 시간 기록

                // CPU 집약적 계산 수행 (수학 연산으로 실제 CPU 부하 생성)
                double result = 0.0; // 계산 결과 저장 변수
                for (int i = 0; i < finalIterations; i++) { // 지정된 횟수만큼 반복
                    result += Math.sqrt(i) * Math.sin(i); // Math.sqrt(), Math.sin() - 복잡한 수학 연산으로 CPU 부하 생성
                }

                long endTime = System.currentTimeMillis(); // 종료 시간 기록

                // 응답 데이터 구성 (처리 결과와 성능 메트릭 포함)
                Map<String, Object> response = new HashMap<>(); // 응답 정보를 담을 맵
                response.put("iterations", finalIterations); // 실행된 반복 횟수
                response.put("result", result); // 계산 결과 (실제 연산이 수행되었음을 증명)
                response.put("processingTimeMs", endTime - startTime); // 처리 시간 (밀리초)
                response.put("thread", Thread.currentThread().getName()); // 실행된 스레드 이름 (스레드풀 활용 확인)
                response.put("throughput", finalIterations / Math.max(1, (endTime - startTime) / 1000.0)); // 초당 처리량 계산

                String jsonResponse = convertToJson(response); // JSON 문자열 변환
                HttpResponse httpResponse = HttpResponse.ok(jsonResponse); // 성공 응답 생성
                httpResponse.getHeaders().set("Content-Type", "application/json; charset=UTF-8"); // JSON Content-Type 설정
                return httpResponse; // 완성된 응답 반환
            });
        });

        // 4. 상태 정보 - 시스템 리소스 및 JVM 정보 (운영 모니터링용)
        router.get("/status", request -> { // 시스템 상태 조회 라우트
            Runtime runtime = Runtime.getRuntime(); // Runtime.getRuntime() - JVM 런타임 정보 조회

            Map<String, Object> status = new HashMap<>(); // 상태 정보를 담을 맵
            status.put("server", "EventLoopServer"); // 서버 타입
            status.put("version", "1.0"); // 서버 버전
            status.put("uptime", System.currentTimeMillis()); // 현재 타임스탬프 (업타임 계산용)
            status.put("jvmVersion", System.getProperty("java.version")); // JVM 버전
            status.put("osName", System.getProperty("os.name")); // 운영체제 이름
            status.put("osVersion", System.getProperty("os.version")); // 운영체제 버전
            status.put("processors", runtime.availableProcessors()); // 사용 가능한 프로세서 수

            // 메모리 정보 (바이트 단위를 MB로 변환하여 가독성 향상)
            long totalMemory = runtime.totalMemory(); // Runtime.totalMemory() - 총 할당된 메모리
            long freeMemory = runtime.freeMemory(); // Runtime.freeMemory() - 사용 가능한 메모리
            long maxMemory = runtime.maxMemory(); // Runtime.maxMemory() - 최대 사용 가능한 메모리
            long usedMemory = totalMemory - freeMemory; // 사용 중인 메모리 계산

            status.put("totalMemoryMB", totalMemory / (1024 * 1024)); // MB 단위로 변환
            status.put("freeMemoryMB", freeMemory / (1024 * 1024)); // MB 단위로 변환
            status.put("maxMemoryMB", maxMemory / (1024 * 1024)); // MB 단위로 변환
            status.put("usedMemoryMB", usedMemory / (1024 * 1024)); // MB 단위로 변환
            status.put("memoryUsagePercent", String.format("%.2f%%", (double) usedMemory / totalMemory * 100)); // 메모리 사용률 (백분율)

            String jsonResponse = convertToJson(status); // JSON 변환
            HttpResponse response = HttpResponse.ok(jsonResponse); // 성공 응답 생성
            response.getHeaders().set("Content-Type", "application/json; charset=UTF-8"); // JSON Content-Type 설정

            return CompletableFuture.completedFuture(response); // 즉시 완료된 Future 반환
        });

        // 라우트 등록 완료 로그 (개선된 정보 포함)
        logger.info("예시 라우트 등록 완료 - 총 4개 엔드포인트"); // 등록된 라우트 수 로그
        logger.info("등록된 엔드포인트:"); // 등록된 엔드포인트 안내 시작
        logger.info("  - POST /echo - 요청 정보 에코 (헤더, 바디, 파라미터 포함)"); // Echo 서비스 설명
        logger.info("  - GET /delay/{seconds} - 지연 응답 테스트 (1-10초, 비동기 처리)"); // 지연 응답 설명
        logger.info("  - GET /cpu/{iterations} - CPU 집약적 작업 (1K-10M회, 스레드풀 활용)"); // CPU 테스트 설명
        logger.info("  - GET /status - 서버 상태 정보 (JVM 메모리, 시스템 정보)"); // 상태 정보 설명

        // 사용 예시 추가 (실제 테스트 가능한 URL 제공)
        logger.info("테스트 예시 URL:"); // 테스트 예시 섹션
        logger.info("  - curl -X POST http://localhost:8082/echo -d 'Hello EventLoop'"); // Echo 테스트
        logger.info("  - curl http://localhost:8082/delay/3 (3초 지연)"); // 지연 테스트
        logger.info("  - curl http://localhost:8082/cpu/500000 (50만회 연산)"); // CPU 테스트
        logger.info("  - curl http://localhost:8082/status (시스템 상태)"); // 상태 조회
    }

    /**
     * 그레이스풀 셧다운 훅 등록
     * JVM 종료 신호 수신 시 서버를 안전하게 종료하는 훅 등록
     * Ctrl+C, SIGTERM 등의 신호에 대응하여 데이터 손실 없는 종료 보장
     */
    private static void registerShutdownHook() { // private static 메서드 - 셧다운 훅 등록
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { // Runtime.addShutdownHook() - JVM 종료 시 실행할 스레드 등록
            logger.info("셧다운 신호 수신 - 서버 정리 시작..."); // 셧다운 시작 로그
            shutdownRequested = true; // volatile 변수 - 다른 스레드에서 종료 요청 감지 가능

            try {
                // 통계 모니터링 스레드 정리
                if (statisticsExecutor != null && !statisticsExecutor.isShutdown()) { // 실행 중인 스케줄러가 있는지 확인
                    statisticsExecutor.shutdown(); // ScheduledExecutorService.shutdown() - 새 작업 수락 중단
                    if (!statisticsExecutor.awaitTermination(5, TimeUnit.SECONDS)) { // 5초 대기 후 강제 종료
                        statisticsExecutor.shutdownNow(); // ScheduledExecutorService.shutdownNow() - 실행 중인 작업 강제 중단
                    }
                }

                // 서버 정리
                if (server != null) { // 서버 인스턴스가 존재하는 경우
                    server.stop(); // EventLoopServer.stop() - 서버 안전 종료
                }

                // 최종 통계 출력
                printFinalStatistics(); // 종료 시 최종 서버 통계 출력

                logger.info("서버가 깔끔하게 종료되었습니다. 안녕히 가세요! 👋"); // 종료 완료 메시지 (이모지 포함)

            } catch (Exception e) { // 셧다운 과정 중 예외 처리
                logger.log(Level.SEVERE, "서버 종료 중 오류 발생", e); // 심각한 오류 로그
            }
        }, "EventLoopServer-ShutdownHook")); // 셧다운 훅 스레드 이름 지정

        logger.info("셧다운 훅 등록됨 - Ctrl+C로 깔끔한 종료 가능"); // 셧다운 훅 등록 완료 로그
    }

    /**
     * 통계 모니터링 시작
     * 주기적으로 서버 상태를 모니터링하고 로그로 출력하는 스케줄러 시작
     * 데몬 스레드로 실행하여 메인 프로그램 종료에 영향 없음
     */
    private static void startStatisticsMonitoring() { // private static 메서드 - 통계 모니터링 시작
        statisticsExecutor = Executors.newSingleThreadScheduledExecutor(r -> { // Executors.newSingleThreadScheduledExecutor() - 단일 스레드 스케줄러 생성
            Thread t = new Thread(r, "EventLoopServer-Statistics"); // new Thread() - 통계 전용 스레드 생성
            t.setDaemon(true); // Thread.setDaemon() - 데몬 스레드로 설정 (메인 프로그램 종료 시 자동 종료)
            return t; // 설정된 스레드 반환
        });

        // 주기적 통계 출력 스케줄링
        statisticsExecutor.scheduleAtFixedRate( // ScheduledExecutorService.scheduleAtFixedRate() - 고정 간격으로 작업 반복 실행
                EventLoopServerLauncher::printCurrentStatistics, // 메서드 참조 - 실행할 작업 (통계 출력)
                STATISTICS_INTERVAL_SECONDS, // 최초 지연 시간 (초)
                STATISTICS_INTERVAL_SECONDS, // 반복 간격 (초)
                TimeUnit.SECONDS // TimeUnit.SECONDS - 시간 단위 지정
        );

        logger.info("통계 모니터링 시작됨 - " + STATISTICS_INTERVAL_SECONDS + "초마다 출력"); // 모니터링 시작 로그
    }

    /**
     * 현재 통계 출력
     * 서버의 현재 상태를 주기적으로 로그에 출력
     * 실시간 모니터링과 운영 상태 파악에 활용
     */
    private static void printCurrentStatistics() { // private static 메서드 - 현재 통계 출력
        if (server == null) { // 서버가 초기화되지 않은 경우
            return; // early return - 출력할 통계가 없음
        }

        try {
            // 서버 통계 조회 및 출력
            Map<String, Object> stats = server.getStatistics(); // EventLoopServer.getStatistics() - 서버 통계 조회

            logger.info("=== EventLoop 서버 통계 ==="); // 통계 섹션 시작
            logger.info(String.format("활성 연결: %s, 총 연결: %s", // String.format() - 형식화된 통계 출력
                    stats.get("activeConnections"), stats.get("totalConnections"))); // Map.get() - 통계 값 조회
            logger.info(String.format("총 요청: %s, 총 응답: %s, 총 에러: %s",
                    stats.get("totalRequests"), stats.get("totalResponses"), stats.get("totalErrors")));

            // 성공률 및 에러율 출력
            if (stats.containsKey("successRate")) { // Map.containsKey() - 키 존재 여부 확인
                logger.info(String.format("성공률: %.2f%%, 에러율: %.2f%%",
                        stats.get("successRate"), stats.get("errorRate"))); // 성공률과 에러율 출력
            }

            logger.info("========================="); // 통계 섹션 종료

        } catch (Exception e) { // 통계 출력 중 예외 처리
            logger.log(Level.WARNING, "통계 출력 중 오류", e); // 경고 레벨 로그
        }
    }

    /**
     * 최종 통계 출력
     * 서버 종료 시 전체 운영 기간 동안의 통계를 요약하여 출력
     * 운영 성과 분석과 문제 진단에 활용
     */
    private static void printFinalStatistics() { // private static 메서드 - 최종 통계 출력
        if (server == null) { // 서버가 초기화되지 않은 경우
            return; // early return - 출력할 통계가 없음
        }

        try {
            System.out.println(); // 빈 줄
            System.out.println("==========================================="); // 구분선
            System.out.println("🏁 EventLoop 서버 최종 통계"); // 최종 통계 제목 (이모지 포함)
            System.out.println("==========================================="); // 구분선

            // 서버 통계 조회 및 출력
            Map<String, Object> stats = server.getStatistics(); // 최종 통계 조회
            System.out.println("총 연결 수: " + stats.get("totalConnections")); // 총 연결 수
            System.out.println("총 요청 수: " + stats.get("totalRequests")); // 총 요청 수
            System.out.println("총 응답 수: " + stats.get("totalResponses")); // 총 응답 수
            System.out.println("총 에러 수: " + stats.get("totalErrors")); // 총 에러 수

            // 성공률 및 에러율 출력
            if (stats.containsKey("successRate")) { // 성공률 정보가 있는 경우
                System.out.printf("성공률: %.2f%%, 에러율: %.2f%%%n", // System.out.printf() - 형식화된 출력
                        stats.get("successRate"), stats.get("errorRate"));
            }

            System.out.println("서버가 성공적으로 종료되었습니다."); // 종료 성공 메시지
            System.out.println("==========================================="); // 구분선
            System.out.println(); // 빈 줄

        } catch (Exception e) { // 최종 통계 출력 중 예외 처리
            logger.log(Level.WARNING, "최종 통계 출력 중 오류", e); // 경고 로그
        }
    }

    /**
     * 메인 실행 루프
     * 서버가 실행되는 동안 지속적으로 실행되는 모니터링 루프
     * shutdownRequested 플래그를 확인하여 안전한 종료 대기
     */
    private static void runMainLoop() { // private static 메서드 - 메인 실행 루프
        logger.info("메인 루프 시작 - 서버 모니터링 중..."); // 메인 루프 시작 로그
        logger.info("종료하려면 Ctrl+C를 누르세요"); // 사용자 안내 메시지

        while (!shutdownRequested) { // while 반복문 - 종료 요청이 없는 동안 계속 실행
            try {
                Thread.sleep(1000); // Thread.sleep() - 1초 대기 (CPU 사용률 절약)
            } catch (InterruptedException e) { // 인터럽트 예외 처리
                Thread.currentThread().interrupt(); // 현재 스레드의 인터럽트 상태 복원
                logger.info("메인 루프 인터럽트됨 - 종료 중..."); // 인터럽트 로그
                break; // 루프 탈출
            }
        }

        logger.info("메인 루프 종료됨"); // 메인 루프 종료 로그
    }

    /**
     * Map을 JSON 문자열로 변환
     * 기본적인 JSON 직렬화 기능 제공 (외부 라이브러리 없이)
     *
     * 지원 타입:
     * - String, Number, Boolean: 직접 변환
     * - null: JSON null로 변환
     * - List: 배열로 재귀 변환
     * - Map: 객체로 재귀 변환
     * - 기타: toString()으로 문자열 변환
     *
     * @param map 변환할 Map 객체
     * @return JSON 형태의 문자열
     */
    private static String convertToJson(Map<String, Object> map) { // private static 메서드 - JSON 변환 유틸리티
        if (map == null || map.isEmpty()) { // null 또는 빈 맵 확인
            return "{}"; // 빈 JSON 객체 반환
        }

        StringBuilder json = new StringBuilder(); // StringBuilder - 효율적인 문자열 조합
        json.append("{"); // JSON 객체 시작

        boolean first = true; // 첫 번째 요소 플래그
        for (Map.Entry<String, Object> entry : map.entrySet()) { // Map.entrySet() - 모든 키-값 쌍 순회
            if (!first) { // 첫 번째가 아닌 경우
                json.append(","); // 쉼표 구분자 추가
            }
            first = false; // 첫 번째 플래그 해제

            // 키 추가 (항상 문자열로 감싸기)
            json.append("\"").append(escapeJsonString(entry.getKey())).append("\":"); // Map.Entry.getKey() - 키 추가 (JSON 이스케이프 처리)

            Object value = entry.getValue(); // Map.Entry.getValue() - 값 추출
            if (value instanceof String) { // instanceof - 문자열 타입 확인
                json.append("\"").append(escapeJsonString((String) value)).append("\""); // 문자열은 따옴표로 감싸기 (이스케이프 처리)
            } else if (value instanceof Number || value instanceof Boolean) { // 숫자나 불린 타입
                json.append(value); // 그대로 추가 (따옴표 없음)
            } else if (value == null) { // null 값
                json.append("null"); // JSON null
            } else if (value instanceof List) { // List 타입
                json.append(convertListToJson((List<?>) value)); // 재귀 호출로 배열 변환
            } else if (value instanceof Map) { // Map 타입
                @SuppressWarnings("unchecked") // 타입 캐스팅 경고 억제
                Map<String, Object> nestedMap = (Map<String, Object>) value; // 중첩 맵으로 캐스팅
                json.append(convertToJson(nestedMap)); // 재귀 호출로 객체 변환
            } else { // 기타 타입
                json.append("\"").append(escapeJsonString(value.toString())).append("\""); // Object.toString() - 문자열로 변환 후 이스케이프 처리
            }
        }

        json.append("}"); // JSON 객체 종료
        return json.toString(); // StringBuilder.toString() - 완성된 JSON 문자열 반환
    }

    /**
     * List를 JSON 배열 문자열로 변환
     * Map과 유사한 방식으로 List를 JSON 배열로 직렬화
     *
     * @param list 변환할 List 객체
     * @return JSON 배열 형태의 문자열
     */
    private static String convertListToJson(List<?> list) { // private static 메서드 - List를 JSON 배열로 변환
        if (list == null || list.isEmpty()) { // null 또는 빈 리스트 확인
            return "[]"; // 빈 JSON 배열 반환
        }

        StringBuilder json = new StringBuilder(); // StringBuilder 생성
        json.append("["); // JSON 배열 시작

        boolean first = true; // 첫 번째 요소 플래그
        for (Object item : list) { // for-each 반복문 - 모든 리스트 요소 순회
            if (!first) { // 첫 번째가 아닌 경우
                json.append(","); // 쉼표 구분자 추가
            }
            first = false; // 첫 번째 플래그 해제

            // 요소 타입별 처리 (Map 변환과 동일한 로직)
            if (item instanceof String) { // 문자열 타입
                json.append("\"").append(escapeJsonString((String) item)).append("\""); // 따옴표로 감싸기 (이스케이프 처리)
            } else if (item instanceof Number || item instanceof Boolean) { // 숫자나 불린 타입
                json.append(item); // 그대로 추가
            } else if (item == null) { // null 값
                json.append("null"); // JSON null
            } else if (item instanceof Map) { // Map 타입 (중첩 객체)
                @SuppressWarnings("unchecked") // 타입 캐스팅 경고 억제
                Map<String, Object> mapItem = (Map<String, Object>) item; // Map으로 캐스팅
                json.append(convertToJson(mapItem)); // 재귀 호출로 객체 변환
            } else { // 기타 타입
                json.append("\"").append(escapeJsonString(item.toString())).append("\""); // toString()으로 문자열 변환 후 이스케이프 처리
            }
        }

        json.append("]"); // JSON 배열 종료
        return json.toString(); // 완성된 JSON 배열 문자열 반환
    }

    /**
     * JSON 문자열 이스케이프 처리
     * 특수 문자를 JSON 규격에 맞게 이스케이프하여 안전한 JSON 생성
     *
     * @param str 이스케이프할 문자열
     * @return 이스케이프 처리된 문자열
     */
    private static String escapeJsonString(String str) { // private static 메서드 - JSON 이스케이프 처리
        if (str == null) { // null 체크
            return ""; // 빈 문자열 반환
        }

        StringBuilder escaped = new StringBuilder(); // 이스케이프된 문자열 구성용
        for (int i = 0; i < str.length(); i++) { // 문자열의 모든 문자 순회
            char c = str.charAt(i); // String.charAt() - i번째 문자 조회
            switch (c) { // switch 문 - 특수 문자별 처리
                case '"': escaped.append("\\\""); break; // 쌍따옴표 이스케이프
                case '\\': escaped.append("\\\\"); break; // 백슬래시 이스케이프
                case '\b': escaped.append("\\b"); break; // 백스페이스 이스케이프
                case '\f': escaped.append("\\f"); break; // 폼피드 이스케이프
                case '\n': escaped.append("\\n"); break; // 줄바꿈 이스케이프
                case '\r': escaped.append("\\r"); break; // 캐리지 리턴 이스케이프
                case '\t': escaped.append("\\t"); break; // 탭 이스케이프
                default:
                    if (c < 0x20) { // 제어 문자 (ASCII 32 미만)
                        escaped.append(String.format("\\u%04x", (int) c)); // 유니코드 이스케이프
                    } else {
                        escaped.append(c); // 일반 문자는 그대로 추가
                    }
                    break;
            }
        }
        return escaped.toString(); // 이스케이프 처리된 문자열 반환
    }

    /**
     * 서버 설정 정보를 담는 내부 클래스
     * 명령행 인수로부터 파싱된 서버 설정을 캡슐화
     *
     * 불변 객체로 설계하여 설정 정보의 안전성 보장
     */
    private static class ServerConfig { // private static 내부 클래스 - 서버 설정 정보
        private final int port; // final int - 서버 포트 (불변)
        private final int backlog; // final int - 백로그 크기 (불변)

        /**
         * ServerConfig 생성자
         *
         * @param port 서버 포트 번호
         * @param backlog 연결 대기 큐 크기
         */
        public ServerConfig(int port, int backlog) { // public 생성자
            this.port = port; // 포트 설정
            this.backlog = backlog; // 백로그 설정
        }

        /**
         * 포트 번호 반환
         * @return 서버 포트 번호
         */
        public int getPort() { return port; } // getter 메서드 - 포트 반환

        /**
         * 백로그 크기 반환
         * @return 연결 대기 큐 크기
         */
        public int getBacklog() { return backlog; } // getter 메서드 - 백로그 반환

        /**
         * 설정 정보를 문자열로 표현
         * 디버깅과 로깅에 활용
         *
         * @return 설정 정보 문자열
         */
        @Override
        public String toString() { // Object.toString() 메서드 오버라이드
            return String.format("ServerConfig{port=%d, backlog=%d}", port, backlog); // String.format() - 형식화된 문자열 생성
        }
    }
}