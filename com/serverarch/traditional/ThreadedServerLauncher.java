package com.serverarch.traditional;

// Java I/O 라이브러리
// java.io.IOException: 입출력 작업 중 발생하는 예외 클래스
import java.io.IOException;

// Java 로깅 라이브러리
// java.util.logging.*: 로깅 시스템의 모든 클래스들을 임포트
// Logger: 로그 메시지를 기록하는 클래스
// Level: 로그 레벨을 정의하는 열거형 (SEVERE, WARNING, INFO, FINE 등)
// LogRecord: 로그 기록의 정보를 담는 클래스
// Handler: 로그 출력을 처리하는 추상 클래스
// ConsoleHandler: 콘솔로 로그를 출력하는 핸들러
// FileHandler: 파일로 로그를 출력하는 핸들러
// SimpleFormatter: 간단한 형식으로 로그를 포맷하는 클래스
import java.util.logging.*;

/**
 * ThreadedServer 실행을 위한 메인 런처 클래스
 *
 * 역할:
 * 1. 서버 설정 및 초기화
 * 2. 명령줄 인수 파싱
 * 3. 로깅 설정
 * 4. 서버 시작 및 종료 관리
 * 5. 우아한 종료 처리
 *
 * 사용법:
 * java ThreadedServerLauncher [포트] [스레드풀크기]
 *
 * 예시:
 * java ThreadedServerLauncher 8080 100
 */
public class ThreadedServerLauncher {

    // 로거 - 런처 동작 상태 기록용
    // Logger.getLogger(): Logger 클래스의 정적 메서드로 로거 인스턴스 생성
    // ThreadedServerLauncher.class.getName(): 클래스 이름을 로거 이름으로 사용
    private static final Logger logger = Logger.getLogger(ThreadedServerLauncher.class.getName());

    // 기본 설정값들
    private static final int DEFAULT_PORT = 8080;          // 기본 포트 번호
    private static final int DEFAULT_THREAD_POOL_SIZE = 100; // 기본 스레드풀 크기

    // 서버 인스턴스
    // ThreadedServer: 실제 HTTP 서버 클래스
    private ThreadedServer server;

    /**
     * 메인 진입점
     * 명령줄 인수를 파싱하고 서버를 시작
     *
     * @param args 명령줄 인수 [포트] [스레드풀크기]
     */
    public static void main(String[] args) {
        // 로깅 시스템 초기화 - 서버 동작 상태 추적을 위해
        setupLogging();

        // 런처 인스턴스 생성
        // new ThreadedServerLauncher(): 기본 생성자 호출
        ThreadedServerLauncher launcher = new ThreadedServerLauncher();

        try {
            // 명령줄 인수 파싱 - 포트와 스레드풀 크기 추출
            ServerConfig config = parseCommandLineArgs(args);

            // 서버 설정 및 시작
            launcher.startServer(config);

            // 종료 시그널 대기 - 사용자가 Ctrl+C를 누를 때까지 대기
            launcher.waitForShutdown();

        } catch (Exception e) {
            // 시작 실패 시 오류 로그 출력
            // logger.log(): Logger의 로그 출력 메서드
            // Level.SEVERE: 심각한 오류 레벨
            logger.log(Level.SEVERE, "서버 실행 중 오류 발생", e);

            // 비정상 종료
            // System.exit(): JVM 종료 메서드 (1은 오류 종료 코드)
            System.exit(1);
        } finally {
            // 정리 작업 - 예외 발생 여부와 관계없이 실행
            // finally: 예외 발생 여부와 상관없이 반드시 실행되는 블록
            launcher.cleanup();
        }
    }

    /**
     * 서버 시작
     * 설정에 따라 ThreadedServer 인스턴스를 생성하고 시작
     *
     * @param config 서버 설정
     * @throws IOException 서버 시작 실패 시
     */
    public void startServer(ServerConfig config) throws IOException {
        // logger.info(): INFO 레벨 로그 출력 메서드
        // String.format(): 형식화된 문자열 생성 정적 메서드
        // %d: 정수 포맷 지정자
        logger.info(String.format("ThreadedServer 시작 준비 - 포트: %d, 스레드풀: %d",
                config.port, config.threadPoolSize));

        // ThreadedServer 인스턴스 생성 (2개 인수 생성자 사용)
        // config.port: ServerConfig의 포트 필드 접근
        // config.threadPoolSize: ServerConfig의 스레드풀 크기 필드 접근
        server = new ThreadedServer(config.port, config.threadPoolSize);

        // 우아한 종료를 위한 셧다운 훅 등록
        // JVM 종료 시 자동으로 서버를 정리하도록 설정
        // Runtime.getRuntime(): 현재 JVM의 Runtime 인스턴스 반환
        // .addShutdownHook(): JVM 종료 시 실행할 스레드 등록
        // new Thread(): 새로운 스레드 생성
        // () -> {}: 람다 표현식으로 Runnable 구현
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("셧다운 훅 실행 - 서버를 정리합니다");
            if (server != null) {
                // server.stop(): ThreadedServer의 정지 메서드 호출
                server.stop();
            }
        }));

        // 서버 시작 - 블로킹 호출
        // server.start(): ThreadedServer의 시작 메서드 호출
        // IOException을 던질 수 있으므로 호출자가 처리해야 함
        server.start();
    }

    /**
     * 종료 시그널 대기
     * 사용자가 Ctrl+C를 누르거나 JVM이 종료 신호를 받을 때까지 대기
     */
    public void waitForShutdown() {
        logger.info("ThreadedServer가 실행 중입니다. 종료하려면 Ctrl+C를 누르세요.");

        try {
            // 메인 스레드를 무한 대기 상태로 만듦
            // 서버는 별도 스레드에서 실행되므로 메인 스레드는 종료 대기만 함
            // Thread.currentThread(): 현재 실행 중인 스레드 반환
            // .join(): 해당 스레드의 종료를 기다리는 메서드 (무한 대기)
            Thread.currentThread().join();

        } catch (InterruptedException e) {
            // 인터럽트 신호 받음 - 정상적인 종료 과정
            // InterruptedException: 스레드가 인터럽트되었을 때 발생하는 예외
            logger.info("서버 종료 신호를 받았습니다");

            // 현재 스레드의 인터럽트 상태 복원
            // Thread.currentThread().interrupt(): 현재 스레드에 인터럽트 플래그 설정
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 정리 작업
     * 서버 종료 및 리소스 해제
     */
    public void cleanup() {
        logger.info("정리 작업을 시작합니다");

        if (server != null) {
            try {
                // 서버가 아직 실행 중이면 중지
                // server.isRunning(): ThreadedServer의 실행 상태 확인 메서드
                if (server.isRunning()) {
                    server.stop();
                }

                // 최종 통계 출력
                printFinalStatistics();

            } catch (Exception e) {
                // Level.WARNING: 경고 레벨 로그
                logger.log(Level.WARNING, "서버 정리 중 오류 발생", e);
            }
        }

        logger.info("정리 작업 완료");
    }

    /**
     * 최종 통계 출력
     * 서버 종료 시 전체 처리 결과 요약
     */
    private void printFinalStatistics() {
        if (server != null) {
            // server.getStatistics(): ThreadedServer의 통계 정보 반환 메서드
            // ThreadedServer.ServerStatistics: ThreadedServer의 내부 클래스
            ThreadedServer.ServerStatistics stats = server.getStatistics();

            logger.info("=== 최종 서버 통계 ===");
            // stats.toString(): ServerStatistics의 문자열 표현 메서드
            logger.info(stats.toString());

            // 콘솔에도 출력 - 사용자가 쉽게 확인할 수 있도록
            // System.out.println(): 표준 출력으로 한 줄 출력
            System.out.println("\n=== ThreadedServer 최종 통계 ===");

            // stats.getTotalRequestsReceived(): 총 받은 요청 수 반환
            System.out.println("총 요청 수: " + stats.getTotalRequestsReceived());

            // stats.getTotalRequestsProcessed(): 처리 완료된 요청 수 반환
            System.out.println("처리 완료: " + stats.getTotalRequestsProcessed());

            // stats.getTotalRequestsFailed(): 실패한 요청 수 반환
            System.out.println("실패 요청: " + stats.getTotalRequestsFailed());

            // System.out.printf(): 형식화된 출력 메서드
            // %.2f: 소수점 둘째 자리까지 표시하는 포맷
            // stats.getSuccessRate(): 성공률 반환 (0.0~1.0)
            System.out.printf("성공률: %.2f%%\n", stats.getSuccessRate() * 100);

            // stats.getFailureRate(): 실패율 반환 (0.0~1.0)
            System.out.printf("실패율: %.2f%%\n", stats.getFailureRate() * 100);
            System.out.println("=============================\n");
        }
    }

    /**
     * 명령줄 인수 파싱
     * 포트 번호와 스레드풀 크기를 추출하고 검증
     *
     * @param args 명령줄 인수 배열
     * @return 파싱된 서버 설정
     */
    private static ServerConfig parseCommandLineArgs(String[] args) {
        int port = DEFAULT_PORT;
        int threadPoolSize = DEFAULT_THREAD_POOL_SIZE;

        // 인수 개수에 따른 처리
        // args.length: 배열의 길이 속성
        if (args.length >= 1) {
            // 첫 번째 인수: 포트 번호
            try {
                // Integer.parseInt(): String을 int로 변환하는 정적 메서드
                // args[0]: 첫 번째 명령줄 인수
                port = Integer.parseInt(args[0]);

                // 포트 번호 유효성 검증
                if (port < 1 || port > 65535) {
                    // logger.warning(): WARNING 레벨 로그 출력
                    logger.warning(String.format("유효하지 않은 포트 번호: %d. 기본값 사용: %d",
                            port, DEFAULT_PORT));
                    port = DEFAULT_PORT;
                }

            } catch (NumberFormatException e) {
                // NumberFormatException: 숫자 변환 실패 시 발생하는 예외
                logger.warning(String.format("포트 번호 파싱 실패: %s. 기본값 사용: %d",
                        args[0], DEFAULT_PORT));
                port = DEFAULT_PORT;
            }
        }

        if (args.length >= 2) {
            // 두 번째 인수: 스레드풀 크기
            try {
                // args[1]: 두 번째 명령줄 인수
                threadPoolSize = Integer.parseInt(args[1]);

                // 스레드풀 크기 유효성 검증
                if (threadPoolSize < 1) {
                    logger.warning(String.format("유효하지 않은 스레드풀 크기: %d. 기본값 사용: %d",
                            threadPoolSize, DEFAULT_THREAD_POOL_SIZE));
                    threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
                }

                // 너무 큰 스레드풀 크기 경고 - 메모리 사용량 증가 위험
                if (threadPoolSize > 1000) {
                    logger.warning(String.format("스레드풀 크기가 매우 큽니다: %d. 메모리 사용량을 주의하세요",
                            threadPoolSize));
                }

            } catch (NumberFormatException e) {
                logger.warning(String.format("스레드풀 크기 파싱 실패: %s. 기본값 사용: %d",
                        args[1], DEFAULT_THREAD_POOL_SIZE));
                threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
            }
        }

        // 사용할 설정 로그 출력
        logger.info(String.format("서버 설정 - 포트: %d, 스레드풀 크기: %d", port, threadPoolSize));

        // new ServerConfig(): 내부 클래스 생성자 호출
        return new ServerConfig(port, threadPoolSize);
    }

    /**
     * 로깅 시스템 설정
     * 콘솔과 파일에 동시에 로그를 출력하도록 구성
     */
    private static void setupLogging() {
        // 루트 로거 가져오기
        // Logger.getLogger(""): 빈 문자열로 루트 로거 조회
        Logger rootLogger = Logger.getLogger("");

        // 기본 로그 레벨 설정 - INFO 이상만 출력
        // rootLogger.setLevel(): 로거의 로그 레벨 설정
        // Level.INFO: INFO 레벨 이상의 로그만 출력
        rootLogger.setLevel(Level.INFO);

        // 기존 핸들러 제거 - 중복 로그 방지
        // rootLogger.getHandlers(): 로거의 모든 핸들러 배열 반환
        for (Handler handler : rootLogger.getHandlers()) {
            // rootLogger.removeHandler(): 핸들러 제거
            rootLogger.removeHandler(handler);
        }

        // 콘솔 핸들러 설정
        // new ConsoleHandler(): 콘솔 출력 핸들러 생성
        ConsoleHandler consoleHandler = new ConsoleHandler();

        // consoleHandler.setLevel(): 핸들러의 로그 레벨 설정
        consoleHandler.setLevel(Level.INFO);

        // 로그 포맷 설정 - 시간, 레벨, 클래스명, 메시지 포함
        // consoleHandler.setFormatter(): 로그 포맷터 설정
        // new SimpleFormatter(): SimpleFormatter를 익명 클래스로 확장
        consoleHandler.setFormatter(new SimpleFormatter() {
            // @Override: 부모 메서드를 재정의함을 명시하는 어노테이션
            @Override
            public String format(LogRecord record) {
                // String.format(): 형식화된 문자열 생성
                // %1$tF: 첫 번째 인수를 날짜 형식으로 포맷 (YYYY-MM-DD)
                // %1$tT: 첫 번째 인수를 시간 형식으로 포맷 (HH:MM:SS)
                // %2$s: 두 번째 인수를 문자열로 포맷
                // %3$s: 세 번째 인수를 문자열로 포맷
                // %4$s: 네 번째 인수를 문자열로 포맷
                // %n: 플랫폼별 줄바꿈 문자
                return String.format("[%1$tF %1$tT] [%2$s] %3$s: %4$s%n",
                        // new java.util.Date(): 새로운 Date 객체 생성
                        // record.getMillis(): LogRecord의 시간 (밀리초) 반환
                        new java.util.Date(record.getMillis()),

                        // record.getLevel(): LogRecord의 로그 레벨 반환
                        record.getLevel(),

                        // record.getSourceClassName(): LogRecord의 소스 클래스명 반환
                        record.getSourceClassName(),

                        // record.getMessage(): LogRecord의 메시지 반환
                        record.getMessage()
                );
            }
        });

        // 루트 로거에 콘솔 핸들러 추가
        // rootLogger.addHandler(): 핸들러를 로거에 추가
        rootLogger.addHandler(consoleHandler);

        // 파일 핸들러 설정 (선택적)
        try {
            // 로그 파일 생성 - 날짜별로 분리
            // String.format(): 형식화된 파일명 생성
            // %1$tY: 첫 번째 인수를 연도로 포맷 (YYYY)
            // %1$tm: 첫 번째 인수를 월로 포맷 (MM)
            // %1$td: 첫 번째 인수를 일로 포맷 (DD)
            String logFileName = String.format("threaded-server-%1$tY%1$tm%1$td.log",
                    new java.util.Date());

            // new FileHandler(): 파일 출력 핸들러 생성
            // logFileName: 로그 파일명
            // true: append 모드 (기존 파일에 추가)
            FileHandler fileHandler = new FileHandler(logFileName, true);

            // Level.ALL: 모든 레벨의 로그를 파일에 기록
            fileHandler.setLevel(Level.ALL);

            // new SimpleFormatter(): 간단한 포맷터 사용
            fileHandler.setFormatter(new SimpleFormatter());

            // 루트 로거에 파일 핸들러 추가
            rootLogger.addHandler(fileHandler);

            logger.info("로그 파일 설정 완료: " + logFileName);

        } catch (IOException e) {
            // 파일 핸들러 설정 실패 시 콘솔에만 로그 출력
            // e.getMessage(): 예외의 메시지 반환
            logger.warning("로그 파일 설정 실패 - 콘솔에만 로그를 출력합니다: " + e.getMessage());
        }

        // JVM 종료 시 로그 핸들러 정리
        // Runtime.getRuntime().addShutdownHook(): JVM 종료 시 실행할 훅 등록
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // 모든 핸들러를 정리하여 리소스 해제
            for (Handler handler : rootLogger.getHandlers()) {
                // handler.close(): 핸들러 정리 및 리소스 해제
                handler.close();
            }
        }));
    }

    /**
     * 사용법 출력
     * 잘못된 인수나 도움말 요청 시 호출
     */
    private static void printUsage() {
        // System.out.println(): 표준 출력으로 도움말 텍스트 출력
        System.out.println("ThreadedServer - Thread-per-Request HTTP 서버");
        System.out.println();
        System.out.println("사용법:");
        System.out.println("  java ThreadedServerLauncher [포트] [스레드풀크기]");
        System.out.println();
        System.out.println("매개변수:");
        System.out.println("  포트          - 서버 바인딩 포트 (1-65535, 기본값: " + DEFAULT_PORT + ")");
        System.out.println("  스레드풀크기   - 요청 처리용 스레드 수 (1 이상, 기본값: " + DEFAULT_THREAD_POOL_SIZE + ")");
        System.out.println();
        System.out.println("예시:");
        System.out.println("  java ThreadedServerLauncher");
        System.out.println("  java ThreadedServerLauncher 8080");
        System.out.println("  java ThreadedServerLauncher 8080 200");
        System.out.println();
        System.out.println("주의사항:");
        System.out.println("  - 스레드풀 크기가 클수록 더 많은 동시 연결을 처리할 수 있지만");
        System.out.println("    메모리 사용량도 증가합니다 (스레드당 약 1MB 스택 메모리)");
        System.out.println("  - 1024 이하의 포트는 관리자 권한이 필요할 수 있습니다");
    }

    /**
     * 도움말 확인
     * 명령줄 인수에 도움말 요청이 있는지 확인
     *
     * @param args 명령줄 인수
     * @return 도움말 요청이면 true
     */
    private static boolean isHelpRequested(String[] args) {
        // for-each 루프로 모든 인수 검사
        for (String arg : args) {
            // arg.equals(): String의 동등성 비교 메서드
            if ("-h".equals(arg) || "--help".equals(arg) || "help".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    // ========== 내부 클래스: ServerConfig ==========

    /**
     * 서버 설정 정보를 담는 불변 클래스
     * 포트 번호와 스레드풀 크기를 함께 관리
     *
     * static: 외부 클래스의 인스턴스 없이도 생성 가능한 정적 중첩 클래스
     */
    private static class ServerConfig {
        // 모든 필드를 final로 선언하여 불변성 보장
        final int port;           // 서버 바인딩 포트
        final int threadPoolSize; // 스레드풀 크기

        /**
         * ServerConfig 생성자
         *
         * @param port 서버 포트
         * @param threadPoolSize 스레드풀 크기
         */
        ServerConfig(int port, int threadPoolSize) {
            // this: 현재 객체 참조 키워드
            this.port = port;
            this.threadPoolSize = threadPoolSize;
        }

        /**
         * 설정 정보를 문자열로 표현
         * 디버깅과 로깅에 사용
         */
        @Override
        public String toString() {
            // String.format(): 형식화된 문자열 생성
            return String.format("ServerConfig{port=%d, threadPoolSize=%d}",
                    port, threadPoolSize);
        }
    }

    // ========== 개발용 편의 메서드들 ==========

    /**
     * 개발 모드로 서버 시작
     * IDE에서 직접 실행할 때 사용하는 편의 메서드
     */
    public static void startDevelopmentServer() {
        // 개발용 기본 설정
        // String[] 배열 초기화 - 개발용 명령줄 인수 시뮬레이션
        String[] devArgs = {"8080", "50"}; // 포트 8080, 스레드풀 50

        logger.info("개발 모드로 서버를 시작합니다");
        // main(): 현재 클래스의 메인 메서드 호출
        main(devArgs);
    }

    /**
     * 테스트용 서버 시작
     * 단위 테스트나 통합 테스트에서 사용
     *
     * @param port 테스트용 포트
     * @return 시작된 서버 인스턴스
     */
    public static ThreadedServer startTestServer(int port) {
        // new ThreadedServer(): 테스트용 서버 인스턴스 생성
        // 작은 스레드풀(10)로 테스트 환경에 적합하게 설정
        ThreadedServer testServer = new ThreadedServer(port, 10);

        try {
            // 별도 스레드에서 서버 시작 - 테스트 스레드 블로킹 방지
            // new Thread(): 새로운 스레드 생성
            // () -> {}: 람다 표현식으로 Runnable 구현
            new Thread(() -> {
                try {
                    // testServer.start(): 서버 시작 (블로킹 호출)
                    testServer.start();
                } catch (IOException e) {
                    // Level.SEVERE: 심각한 오류 레벨
                    logger.log(Level.SEVERE, "테스트 서버 시작 실패", e);
                }
            }).start(); // .start(): 스레드 실행 시작

            // 서버가 완전히 시작될 때까지 잠시 대기
            // Thread.sleep(): 현재 스레드를 지정된 시간만큼 일시정지
            Thread.sleep(100); // 100밀리초 대기

            return testServer;

        } catch (InterruptedException e) {
            // InterruptedException: 스레드 인터럽트 시 발생하는 예외
            // Thread.currentThread().interrupt(): 인터럽트 상태 복원
            Thread.currentThread().interrupt();

            // new RuntimeException(): 런타임 예외 생성 및 던짐
            throw new RuntimeException("테스트 서버 시작 중 인터럽트", e);
        }
    }

    /**
     * 현재 JVM의 권장 스레드풀 크기 계산
     * CPU 코어 수와 메모리를 고려한 권장값 제공
     *
     * @return 권장 스레드풀 크기
     */
    public static int getRecommendedThreadPoolSize() {
        // CPU 코어 수 가져오기
        // Runtime.getRuntime(): 현재 JVM의 Runtime 인스턴스 반환
        // .availableProcessors(): 사용 가능한 프로세서(코어) 수 반환
        int cores = Runtime.getRuntime().availableProcessors();

        // 가용 메모리 가져오기 (MB 단위)
        // .maxMemory(): JVM이 사용할 수 있는 최대 메모리 (바이트)
        // / 1024 / 1024: 바이트를 메가바이트로 변환
        long maxMemoryMB = Runtime.getRuntime().maxMemory() / 1024 / 1024;

        // CPU 기반 계산 - I/O 바운드 작업이 많으므로 코어 수의 2-4배
        int cpuBasedSize = cores * 3;

        // 메모리 기반 계산 - 스레드당 약 1MB로 가정하고 전체 메모리의 10% 사용
        // (int): long을 int로 형변환
        int memoryBasedSize = (int) (maxMemoryMB * 0.1);

        // 두 값 중 작은 값 선택 (리소스 제약 고려)
        // Math.min(): 두 값 중 작은 값을 반환하는 정적 메서드
        int recommendedSize = Math.min(cpuBasedSize, memoryBasedSize);

        // 최소값과 최대값 제한
        // Math.max(): 두 값 중 큰 값을 반환하는 정적 메서드
        recommendedSize = Math.max(10, recommendedSize);   // 최소 10개
        recommendedSize = Math.min(500, recommendedSize);  // 최대 500개

        logger.info(String.format("시스템 정보 - CPU 코어: %d, 최대 메모리: %dMB", cores, maxMemoryMB));
        logger.info(String.format("권장 스레드풀 크기: %d (CPU 기반: %d, 메모리 기반: %d)",
                recommendedSize, cpuBasedSize, memoryBasedSize));

        return recommendedSize;
    }

    /**
     * 시스템 정보 출력
     * 서버 시작 전 환경 정보 확인용
     */
    public static void printSystemInfo() {
        // Runtime 인스턴스 가져오기
        Runtime runtime = Runtime.getRuntime();

        System.out.println("=== 시스템 정보 ===");

        // System.getProperty(): 시스템 속성 값 반환
        // "java.version": Java 버전 속성 키
        System.out.println("Java 버전: " + System.getProperty("java.version"));

        // "os.name": 운영체제 이름 속성 키
        // "os.version": 운영체제 버전 속성 키
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));

        System.out.println("CPU 코어 수: " + runtime.availableProcessors());

        // System.out.printf(): 형식화된 출력 메서드
        // %dMB: 정수를 메가바이트 단위로 표시
        // %n: 플랫폼별 줄바꿈 문자
        // runtime.maxMemory(): JVM 최대 메모리
        // runtime.totalMemory(): JVM 총 할당된 메모리
        // runtime.freeMemory(): JVM 여유 메모리
        System.out.printf("JVM 메모리 - 최대: %dMB, 총: %dMB, 여유: %dMB%n",
                runtime.maxMemory() / 1024 / 1024,
                runtime.totalMemory() / 1024 / 1024,
                runtime.freeMemory() / 1024 / 1024);

        System.out.println("권장 스레드풀 크기: " + getRecommendedThreadPoolSize());
        System.out.println("==================");
        System.out.println();
    }
}