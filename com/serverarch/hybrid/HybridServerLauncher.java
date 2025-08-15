package com.serverarch.hybrid;

// === 기본 Java 라이브러리 Import ===
// java.io.*: 입출력 스트림과 파일 처리를 위한 클래스들
import java.io.*;
// IOException: 입출력 작업 중 발생하는 예외 클래스 - 파일 읽기/쓰기 실패 시 처리용
// FileInputStream: 파일에서 바이트를 읽기 위한 입력 스트림 - 설정 파일 로드용
// InputStream: 바이트 입력 스트림의 추상 클래스 - 다양한 입력 소스 추상화용

// java.util.*: 컬렉션 프레임워크와 유틸리티 클래스들
import java.util.*;
// Properties: 키-값 쌍의 설정 정보를 관리하는 클래스 - .properties 파일 파싱에 최적화
// Map: 키-값 매핑을 나타내는 인터페이스 - 설정 데이터와 JSON 변환을 위한 구조
// HashMap: Map 인터페이스의 해시 테이블 기반 구현체 - O(1) 평균 조회 성능으로 빠른 설정 접근
// Scanner: 텍스트 스캐닝을 위한 클래스 - 대화형 모드에서 사용자 입력 파싱용

// java.util.logging.*: Java 내장 로깅 API
import java.util.logging.*;
// Logger: 로그 메시지를 기록하는 클래스 - 외부 의존성 없이 내장 로깅 제공
// Level: 로그 레벨을 정의하는 클래스 - SEVERE, WARNING, INFO 등 로그 중요도 분류
// ConsoleHandler: 콘솔로 로그를 출력하는 핸들러 - 개발/디버깅 시 실시간 로그 확인용
// SimpleFormatter: 간단한 형식으로 로그를 포맷하는 포매터 - 읽기 쉬운 로그 형식 제공

// java.time.*: 날짜와 시간 처리를 위한 클래스들
import java.time.*;
// Instant: 타임라인의 순간을 나타내는 클래스 - UTC 기반 정확한 시간 표현
// Duration: 시간 기간을 나타내는 클래스 - 업타임 계산 등 시간 간격 측정용
// LocalDateTime: 로컬 날짜와 시간을 나타내는 클래스 - 지역 시간 기반 로깅용

// === 공통 모듈 Import ===
// Traditional 서버와 동일한 라우팅 시스템 사용
import com.serverarch.traditional.HttpRequest;
import com.serverarch.traditional.HttpResponse;
import com.serverarch.traditional.routing.*;
// Router: URL 패턴과 핸들러를 매핑하는 라우팅 클래스 - RESTful API 구조 제공
// RouteHandler: 요청을 처리하는 핸들러 인터페이스 - 함수형 인터페이스로 람다 지원
// LoggingMiddleware: 요청/응답 로깅을 위한 미들웨어 - AOP 방식으로 횡단 관심사 처리
// CorsMiddleware: CORS 처리를 위한 미들웨어 - 브라우저 보안 정책 대응

// HTTP 처리를 위한 공통 클래스들
import com.serverarch.common.http.*;
// HttpRequest: HTTP 요청 정보를 담는 클래스 - 헤더, 바디, 파라미터 등 캡슐화
// HttpResponse: HTTP 응답 정보를 담는 클래스 - 상태 코드, 헤더, 바디 등 캡슐화
// HttpMethod: HTTP 메서드를 나타내는 열거형 - GET, POST 등 타입 안전성 제공

/**
 * HybridServer 실행기 및 데모 애플리케이션
 *
 * 이 클래스는 다음과 같은 기능을 제공합니다:
 *
 * 주요 기능:
 * 1. 설정 파일 기반 서버 구성
 * 2. 데모 라우트 및 미들웨어 설정
 * 3. 대화형 서버 관리 인터페이스
 * 4. 성능 모니터링 및 통계 출력
 * 5. 그레이스풀 셧다운 처리
 *
 * 설정 파일 지원:
 * - hybrid-server.properties: 서버 기본 설정
 * - 환경 변수 오버라이드 지원
 * - 커맨드라인 인수 처리
 *
 * 데모 기능:
 * - RESTful API 예시 (사용자 관리)
 * - 비동기 처리 데모
 * - 파일 업로드/다운로드
 * - 웹소켓 시뮬레이션 (Long Polling)
 * - 성능 테스트 엔드포인트
 *
 * 운영 기능:
 * - 실시간 메트릭 모니터링
 * - 서버 상태 확인
 * - 동적 라우트 추가/제거
 * - 로그 레벨 동적 변경
 */
public class HybridServerLauncher { // public 클래스 선언 - 메인 메서드를 포함하는 실행 가능한 클래스

    // === 로깅 시스템 ===
    // static final: 클래스 레벨 상수로 모든 인스턴스가 공유
    // Logger.getLogger() 사용 이유: 클래스별 독립적인 로거로 로그 출처 추적 가능
    private static final Logger logger = Logger.getLogger(HybridServerLauncher.class.getName());

    // === 기본 설정 상수 ===
    // static final: 컴파일 타임 상수로 메모리에 한 번만 로드
    private static final int DEFAULT_PORT = 8081; // 기본 포트 - Traditional(8080), EventLoop(8082)와 구분
    private static final int DEFAULT_MAX_CONNECTIONS = 2000; // 기본 최대 연결 수 - Hybrid 서버의 높은 동시성 반영
    private static final int DEFAULT_BACKLOG = 100; // 기본 백로그 크기 - TCP 연결 대기 큐 크기
    private static final String CONFIG_FILE = "config/hybrid-server.properties"; // 설정 파일 경로

    // === 서버 인스턴스 ===
    // static: 클래스 레벨 변수로 main 메서드에서 접근 가능
    // volatile 사용 이유: 멀티스레드 환경에서 변수의 메모리 가시성 보장 (CPU 캐시 무효화)
    private static volatile HybridServer server; // 서버 인스턴스 - 여러 스레드에서 접근 가능

    // === 설정 정보 ===
    // static: 클래스 레벨 변수로 설정 정보 저장
    private static Properties config; // 설정 파일에서 로드한 속성들
    private static boolean interactiveMode = false; // 대화형 모드 여부 - 콘솔에서 명령 입력 가능

    /**
     * 메인 메서드 - 애플리케이션 진입점
     *
     * 실행 과정:
     * 1. 로깅 시스템 초기화
     * 2. 설정 파일 로드
     * 3. 서버 생성 및 라우트 설정
     * 4. 서버 시작
     * 5. 셧다운 훅 등록
     * 6. 대화형 모드 실행 (옵션)
     *
     * @param args 명령행 인수 배열
     */
    public static void main(String[] args) { // public static: JVM이 호출하는 메인 메서드
        try { // try-catch 블록 사용 이유: 메인 실행 중 발생하는 예외를 한 곳에서 처리
            // 1. 로깅 시스템 초기화
            setupLogging(); // 로그 출력 형식과 레벨 설정

            // 2. 명령행 인수 처리
            parseCommandLineArguments(args); // 사용자가 전달한 명령행 옵션 파싱

            // 3. 설정 파일 로드
            loadConfiguration(); // 설정 파일에서 서버 구성 정보 읽기

            // 4. 서버 생성
            createServer(); // 설정 정보를 바탕으로 HybridServer 인스턴스 생성

            // 5. 라우트 및 미들웨어 설정
            setupRoutes(); // 데모 애플리케이션용 라우트들 등록
            setupMiddleware(); // 로깅, CORS 등의 미들웨어 설정

            // 6. 셧다운 훅 등록
            registerShutdownHook(); // JVM 종료 시 서버를 안전하게 종료하는 훅 등록

            // 7. 서버 시작
            startServer(); // 실제 서버 시작 및 클라이언트 요청 수락 시작

            // 8. 대화형 모드 실행 (선택적)
            if (interactiveMode) { // 대화형 모드가 활성화된 경우
                runInteractiveMode(); // 콘솔에서 서버 관리 명령 처리
            } else {
                // 비대화형 모드 - 서버가 종료될 때까지 대기
                waitForShutdown(); // 서버 종료 시그널을 기다림
            }

        } catch (Exception e) { // 메인 실행 중 발생한 모든 예외
            // 심각한 오류 로깅 및 애플리케이션 종료
            logger.log(Level.SEVERE, "HybridServer 실행 중 오류 발생", e); // 심각한 오류 레벨로 로그
            System.exit(1); // 비정상 종료 코드로 JVM 종료 - 외부 프로세스 매니저에게 오류 신호
        }
    }

    /**
     * 로깅 시스템 초기화 - 콘솔 출력 형식과 레벨 설정
     */
    private static void setupLogging() { // private static: 클래스 내부에서만 사용하는 초기화 메서드
        try { // try-catch 블록: 로깅 설정 중 예외 처리
            // 루트 로거 설정 - 모든 로거의 기본 설정
            // Logger.getLogger("") 사용 이유: 빈 문자열로 루트 로거 반환
            Logger rootLogger = Logger.getLogger("");
            // Level.INFO 설정 이유: 일반적인 정보 수준의 로그만 출력 (DEBUG는 제외)
            rootLogger.setLevel(Level.INFO);

            // 기존 핸들러 제거 - 중복 출력 방지
            // getHandlers() 사용 이유: 현재 등록된 모든 핸들러를 배열로 반환
            Handler[] handlers = rootLogger.getHandlers();
            for (Handler handler : handlers) { // enhanced for loop: 배열의 모든 요소 순회
                // removeHandler() 사용 이유: 기본 콘솔 핸들러와 중복 출력 방지
                rootLogger.removeHandler(handler);
            }

            // 콘솔 핸들러 생성 및 설정
            // ConsoleHandler 사용 이유: System.out으로 로그를 실시간 출력
            ConsoleHandler consoleHandler = new ConsoleHandler();
            // Level.ALL 설정 이유: 핸들러는 모든 레벨을 받고, 로거 레벨에서 필터링
            consoleHandler.setLevel(Level.ALL);

            // 간단한 포매터 설정 - 읽기 쉬운 로그 형식
            // SimpleFormatter 상속 이유: 기본 포맷터를 커스터마이징하여 가독성 향상
            consoleHandler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) { // format() 오버라이드로 커스텀 형식 적용
                    // String.format() 사용 이유: 일관된 형식의 문자열 생성 (위치 지정자 활용)
                    return String.format("[%1$tF %1$tT] [%2$s] [%3$s] %4$s%n",
                            new Date(record.getMillis()), // Date 객체로 변환하여 포맷 적용
                            record.getLevel().getLocalizedName(), // 현지화된 레벨 이름
                            record.getSourceClassName(), // 로그 출처 클래스 추적
                            record.getMessage() // 실제 로그 메시지
                    );
                }
            });

            // 핸들러를 루트 로거에 등록
            // addHandler() 사용 이유: 새로운 콘솔 핸들러를 로깅 시스템에 연결
            rootLogger.addHandler(consoleHandler);

            logger.info("로깅 시스템이 초기화되었습니다"); // 로깅 시스템 초기화 완료 로그

        } catch (Exception e) { // 로깅 설정 중 예외 발생
            // System.err 사용 이유: 로거가 설정되지 않았을 수 있으므로 표준 에러로 직접 출력
            System.err.println("로깅 시스템 초기화 실패: " + e.getMessage());
            // printStackTrace() 사용 이유: 디버깅을 위한 상세한 스택 트레이스 정보 제공
            e.printStackTrace();
        }
    }

    /**
     * 명령행 인수 파싱 - 사용자가 전달한 옵션들을 처리
     *
     * 지원하는 옵션:
     * -p, --port: 서버 포트 지정
     * -i, --interactive: 대화형 모드 활성화
     * -c, --config: 설정 파일 경로 지정
     * -h, --help: 도움말 출력
     *
     * @param args 명령행 인수 배열
     */
    private static void parseCommandLineArguments(String[] args) { // private static: 클래스 내부 초기화 메서드
        // for 루프 사용 이유: 인덱스 기반 접근으로 다음 인수까지 처리 가능 (옵션 값 처리)
        for (int i = 0; i < args.length; i++) {
            String arg = args[i]; // 현재 처리 중인 인수

            // equals() 사용 이유: null 안전하고 정확한 문자열 비교
            if (arg.equals("-i") || arg.equals("--interactive")) { // 대화형 모드 옵션
                interactiveMode = true; // boolean 플래그로 간단한 상태 관리
                logger.info("대화형 모드가 활성화되었습니다");

            } else if (arg.equals("-h") || arg.equals("--help")) { // 도움말 옵션
                printUsage(); // 사용법 출력 메서드 호출
                // System.exit(0) 사용 이유: 도움말 출력 후 정상 종료
                System.exit(0);

            } else if (arg.equals("-p") || arg.equals("--port")) { // 포트 지정 옵션
                if (i + 1 < args.length) { // 배열 경계 검사로 안전한 접근
                    try {
                        // Integer.parseInt() 사용 이유: 문자열을 정수로 안전하게 변환
                        int port = Integer.parseInt(args[i + 1]);
                        // System.setProperty() 사용 이유: 시스템 속성으로 설정하여 나중에 오버라이드
                        System.setProperty("server.port", String.valueOf(port));
                        i++; // 인덱스 증가로 다음 인수(포트 값) 건너뛰기
                        logger.info("포트가 명령행에서 " + port + "로 설정되었습니다");
                    } catch (NumberFormatException e) { // 숫자 형식 오류 처리
                        logger.warning("잘못된 포트 번호: " + args[i + 1]);
                    }
                } else {
                    logger.warning("포트 옵션에 값이 지정되지 않았습니다");
                }

            } else if (arg.equals("-c") || arg.equals("--config")) { // 설정 파일 경로 옵션
                if (i + 1 < args.length) {
                    System.setProperty("config.file", args[i + 1]);
                    i++; // 다음 인수 건너뛰기
                    logger.info("설정 파일 경로가 " + args[i] + "로 설정되었습니다");
                } else {
                    logger.warning("설정 파일 옵션에 경로가 지정되지 않았습니다");
                }

            } else if (arg.startsWith("-")) { // startsWith() 사용 이유: 알 수 없는 옵션 감지
                logger.warning("알 수 없는 옵션: " + arg);
                printUsage(); // 사용법 출력으로 사용자 가이드
            }
        }
    }

    /**
     * 사용법 출력 - 명령행 옵션 도움말
     */
    private static void printUsage() { // private static: 클래스 내부 유틸리티 메서드
        // System.out 사용 이유: 표준 출력으로 도움말 정보 제공
        System.out.println("HybridServer 사용법:");
        System.out.println("  java com.serverarch.hybrid.HybridServerLauncher [옵션]");
        System.out.println(); // 빈 줄로 가독성 향상
        System.out.println("옵션:");
        System.out.println("  -p, --port <포트>      서버 포트 지정 (기본값: 8081)");
        System.out.println("  -i, --interactive      대화형 모드 활성화");
        System.out.println("  -c, --config <파일>    설정 파일 경로 지정");
        System.out.println("  -h, --help             이 도움말 출력");
        System.out.println();
        System.out.println("예시:");
        System.out.println("  java com.serverarch.hybrid.HybridServerLauncher -p 8081 -i");
        System.out.println("  java com.serverarch.hybrid.HybridServerLauncher --config custom.properties");
    }

    /**
     * 설정 파일 로드 - 서버 구성 정보 읽기
     *
     * 로드 순서:
     * 1. 기본 설정값 적용
     * 2. 설정 파일에서 속성 로드
     * 3. 시스템 속성으로 오버라이드
     * 4. 환경 변수로 최종 오버라이드
     */
    private static void loadConfiguration() { // private static: 클래스 내부 초기화 메서드
        // new Properties() 사용 이유: 키-값 쌍의 설정 정보를 효율적으로 관리
        config = new Properties();

        // 1. 기본 설정값 적용
        setDefaultProperties(); // 하드코딩된 기본값으로 시작

        // 2. 설정 파일에서 속성 로드
        // System.getProperty() 사용 이유: 명령행에서 지정된 설정 파일 경로 우선 사용
        String configFile = System.getProperty("config.file", CONFIG_FILE);
        loadPropertiesFromFile(configFile);

        // 3. 시스템 속성으로 오버라이드
        overrideWithSystemProperties(); // JVM -D 옵션으로 전달된 속성들

        // 4. 환경 변수로 최종 오버라이드
        overrideWithEnvironmentVariables(); // 운영 환경에서 동적 설정 변경

        // 로드된 설정 정보 로그 출력
        logLoadedConfiguration(); // 최종 설정 정보를 로그에 기록
    }

    /**
     * 기본 설정값 설정 - 하드코딩된 기본 설정들
     */
    private static void setDefaultProperties() { // private static: 클래스 내부 초기화 메서드
        // setProperty() 사용 이유: Properties 객체에 키-값 쌍을 안전하게 저장
        config.setProperty("server.port", String.valueOf(DEFAULT_PORT));
        config.setProperty("server.maxConnections", String.valueOf(DEFAULT_MAX_CONNECTIONS));
        config.setProperty("server.backlog", String.valueOf(DEFAULT_BACKLOG));
        config.setProperty("server.requestTimeout", "30"); // 30초 타임아웃
        config.setProperty("logging.level", "INFO"); // 기본 로그 레벨
        config.setProperty("demo.enabled", "true"); // 데모 기능 활성화
        config.setProperty("metrics.enabled", "true"); // 메트릭 수집 활성화
        config.setProperty("cors.enabled", "true"); // CORS 지원 활성화

        logger.fine("기본 설정값이 적용되었습니다");
    }

    /**
     * 설정 파일에서 속성 로드 - 파일 기반 설정 읽기
     *
     * @param configFile 설정 파일 경로
     */
    private static void loadPropertiesFromFile(String configFile) { // private static: 클래스 내부 초기화 메서드
        // File 객체 사용 이유: 파일 존재 여부와 읽기 권한을 미리 확인
        File file = new File(configFile);
        if (!file.exists()) { // exists() 사용 이유: 파일 접근 전 존재 여부 검사
            logger.info("설정 파일을 찾을 수 없습니다: " + configFile + " (기본 설정 사용)");
            return; // early return으로 불필요한 처리 방지
        }

        // try-with-resources 사용 이유: 자동 리소스 관리로 메모리 누수 방지
        try (InputStream input = new FileInputStream(file)) {
            Properties fileProps = new Properties(); // 파일 전용 Properties 객체
            // load() 사용 이유: .properties 파일 형식을 자동으로 파싱
            fileProps.load(input);

            // putAll() 사용 이유: 파일의 모든 속성을 한 번에 메인 설정으로 병합
            config.putAll(fileProps);

            logger.info("설정 파일에서 " + fileProps.size() + "개 속성을 로드했습니다: " + configFile);

        } catch (IOException e) { // IOException 처리로 파일 읽기 오류 대응
            logger.log(Level.WARNING, "설정 파일 로드 실패: " + configFile, e);
        }
    }

    /**
     * 시스템 속성으로 설정 오버라이드 - JVM 시작 시 -D 옵션으로 전달된 속성들
     */
    private static void overrideWithSystemProperties() { // private static: 클래스 내부 초기화 메서드
        // 배열 사용 이유: 오버라이드할 시스템 속성 키들을 명시적으로 관리
        String[] systemPropertyKeys = {
                "server.port", "server.maxConnections", "server.backlog",
                "server.requestTimeout", "logging.level",
                "demo.enabled", "metrics.enabled", "cors.enabled"
        };

        int overrideCount = 0; // 카운터로 오버라이드된 속성 수 추적
        for (String key : systemPropertyKeys) { // enhanced for loop: 코드 간결성과 가독성
            // System.getProperty() 사용 이유: JVM -D 옵션으로 설정된 시스템 속성 조회
            String systemValue = System.getProperty(key);
            if (systemValue != null) { // null 체크로 설정된 속성만 처리
                config.setProperty(key, systemValue); // 기존 설정을 오버라이드
                overrideCount++;
                logger.fine("시스템 속성으로 오버라이드: " + key + " = " + systemValue);
            }
        }

        if (overrideCount > 0) {
            logger.info("시스템 속성으로 " + overrideCount + "개 설정을 오버라이드했습니다");
        }
    }

    /**
     * 환경 변수로 설정 오버라이드 - 운영 환경에서 동적 설정 변경
     */
    private static void overrideWithEnvironmentVariables() { // private static: 클래스 내부 초기화 메서드
        // HashMap 사용 이유: 환경 변수명과 설정 키를 효율적으로 매핑
        Map<String, String> envMappings = new HashMap<>();
        // 환경 변수 규칙: 대문자와 언더스코어 사용 (Unix/Linux 표준)
        envMappings.put("HYBRID_SERVER_PORT", "server.port");
        envMappings.put("HYBRID_SERVER_MAX_CONNECTIONS", "server.maxConnections");
        envMappings.put("HYBRID_SERVER_BACKLOG", "server.backlog");
        envMappings.put("HYBRID_SERVER_REQUEST_TIMEOUT", "server.requestTimeout");
        envMappings.put("HYBRID_LOGGING_LEVEL", "logging.level");
        envMappings.put("HYBRID_DEMO_ENABLED", "demo.enabled");
        envMappings.put("HYBRID_METRICS_ENABLED", "metrics.enabled");
        envMappings.put("HYBRID_CORS_ENABLED", "cors.enabled");

        int envOverrideCount = 0;
        // entrySet() 사용 이유: 키와 값을 동시에 효율적으로 순회
        for (Map.Entry<String, String> entry : envMappings.entrySet()) {
            String envKey = entry.getKey(); // 환경 변수명
            String configKey = entry.getValue(); // 설정 키명
            // System.getenv() 사용 이유: 운영체제 환경 변수 조회
            String envValue = System.getenv(envKey);

            // 환경 변수 값 검증: null이 아니고 공백이 아닌 경우만 처리
            if (envValue != null && !envValue.trim().isEmpty()) {
                config.setProperty(configKey, envValue.trim()); // trim()으로 공백 제거
                envOverrideCount++;
                logger.fine("환경 변수로 오버라이드: " + configKey + " = " + envValue);
            }
        }

        if (envOverrideCount > 0) {
            logger.info("환경 변수로 " + envOverrideCount + "개 설정을 오버라이드했습니다");
        }
    }

    /**
     * 로드된 설정 정보 로그 출력 - 최종 설정값들을 확인용으로 출력
     */
    private static void logLoadedConfiguration() { // private static: 클래스 내부 로깅 메서드
        logger.info("=== HybridServer 설정 정보 ===");
        // getProperty() 사용 이유: Properties에서 안전하게 값 조회
        logger.info("서버 포트: " + config.getProperty("server.port"));
        logger.info("최대 연결 수: " + config.getProperty("server.maxConnections"));
        logger.info("백로그 크기: " + config.getProperty("server.backlog"));
        logger.info("요청 타임아웃: " + config.getProperty("server.requestTimeout") + "초");
        logger.info("로그 레벨: " + config.getProperty("logging.level"));
        logger.info("데모 활성화: " + config.getProperty("demo.enabled"));
        logger.info("메트릭 활성화: " + config.getProperty("metrics.enabled"));
        logger.info("CORS 활성화: " + config.getProperty("cors.enabled"));
        logger.info("=== 설정 정보 끝 ===");
    }

    /**
     * 서버 인스턴스 생성 - 설정 정보를 바탕으로 HybridServer 객체 생성
     */
    private static void createServer() { // private static: 클래스 내부 초기화 메서드
        try {
            // Integer.parseInt() 사용 이유: Properties의 문자열 값을 정수로 안전하게 변환
            int port = Integer.parseInt(config.getProperty("server.port"));
            int maxConnections = Integer.parseInt(config.getProperty("server.maxConnections"));
            int backlog = Integer.parseInt(config.getProperty("server.backlog"));

            // HybridServer 생성자 호출 - 설정된 파라미터로 서버 인스턴스 생성
            server = new HybridServer(port, maxConnections, backlog);

            logger.info("HybridServer 인스턴스가 생성되었습니다");

        } catch (NumberFormatException e) { // 숫자 형식 오류 처리
            logger.log(Level.SEVERE, "설정값의 숫자 형식이 잘못되었습니다", e);
            // RuntimeException 사용 이유: 치명적 오류로 애플리케이션 중단 필요
            throw new RuntimeException("서버 생성 실패: 잘못된 설정값", e);

        } catch (Exception e) { // 기타 서버 생성 오류
            logger.log(Level.SEVERE, "서버 생성 중 오류 발생", e);
            throw new RuntimeException("서버 생성 실패", e);
        }
    }

    /**
     * 라우트 설정 - 데모 애플리케이션용 API 엔드포인트들 등록
     */
    private static void setupRoutes() { // private static: 클래스 내부 초기화 메서드
        // getRouter() 사용 이유: HybridServer에서 라우터 인스턴스를 안전하게 조회
        Router router = server.getRouter();

        // Boolean.parseBoolean() 사용 이유: 문자열 "true"/"false"를 boolean으로 변환
        boolean demoEnabled = Boolean.parseBoolean(config.getProperty("demo.enabled"));
        if (demoEnabled) {
            setupDemoRoutes(router); // 데모 라우트 등록
            logger.info("데모 라우트가 설정되었습니다");
        }

        boolean metricsEnabled = Boolean.parseBoolean(config.getProperty("metrics.enabled"));
        if (metricsEnabled) {
            setupAdvancedMetricsRoutes(router); // 고급 메트릭 라우트 등록
            logger.info("고급 메트릭 라우트가 설정되었습니다");
        }
    }

    /**
     * 데모 라우트 설정 - 실제 사용 가능한 예시 API들
     *
     * @param router 라우트를 등록할 Router 인스턴스
     */
    private static void setupDemoRoutes(Router router) { // private static: 클래스 내부 초기화 메서드
        // === 사용자 관리 API ===
        // HashMap 사용 이유: 인메모리 저장소로 빠른 CRUD 연산 제공
        Map<String, Map<String, Object>> users = new HashMap<>();

        // GET /api/users - 모든 사용자 조회
        // router.get() 사용 이유: HTTP GET 메서드에 대한 라우트 등록
        router.get("/api/users", new RouteHandler() {
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                // StringBuilder 사용 이유: 문자열 연결 성능 최적화
                StringBuilder json = new StringBuilder();
                json.append("["); // JSON 배열 시작

                boolean first = true; // 첫 번째 요소 플래그
                // values() 사용 이유: 맵의 모든 값(사용자 정보)만 필요
                for (Map<String, Object> user : users.values()) {
                    if (!first) json.append(","); // 쉼표로 JSON 요소 분리
                    json.append(mapToJson(user)); // 커스텀 JSON 변환 함수 사용
                    first = false;
                }

                json.append("]"); // JSON 배열 종료
                // HttpResponse.json() 사용 이유: Content-Type 헤더 자동 설정
                return HttpResponse.json(json.toString());
            }
        });

        // POST /api/users - 새 사용자 생성
        router.post("/api/users", new RouteHandler() {
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                try {
                    // getBodyAsString() 사용 이유: 요청 바디를 문자열로 변환
                    String body = request.getBodyAsString();
                    // parseSimpleJson() 사용 이유: 커스텀 JSON 파서로 의존성 최소화
                    Map<String, Object> userData = parseSimpleJson(body);

                    // 필수 필드 검증
                    // containsKey() 사용 이유: 키 존재 여부만 확인 (값은 별도 검증)
                    if (!userData.containsKey("name") || !userData.containsKey("email")) {
                        // badRequest() 사용 이유: 400 상태 코드와 에러 메시지 자동 설정
                        return HttpResponse.badRequest("name과 email 필드가 필요합니다");
                    }

                    // 새 사용자 ID 생성
                    // System.currentTimeMillis() 사용 이유: 간단한 고유 ID 생성
                    String userId = "user_" + System.currentTimeMillis();
                    userData.put("id", userId);
                    // Instant.now() 사용 이유: ISO 8601 형식의 정확한 타임스탬프
                    userData.put("createdAt", Instant.now().toString());

                    // 사용자 저장
                    users.put(userId, userData);

                    // 201 Created 응답 생성
                    // HttpResponse.created() 사용 이유: 리소스 생성 성공을 명시적으로 표현
                    HttpResponse response = HttpResponse.created(mapToJson(userData));
                    // Location 헤더 설정으로 생성된 리소스 위치 알림
                    response.getHeaders().set("Location", "/api/users/" + userId);
                    return response;

                } catch (Exception e) {
                    logger.log(Level.WARNING, "사용자 생성 중 오류", e);
                    return HttpResponse.badRequest("잘못된 요청 형식입니다");
                }
            }
        });

        // GET /api/users/{id} - 특정 사용자 조회
        // 경로 매개변수 {id} 사용 이유: RESTful URL 패턴 구현
        router.get("/api/users/{id}", new RouteHandler() {
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                // getPathParameter() 사용 이유: URL 경로에서 동적 매개변수 추출
                String userId = request.getPathParameter("id");
                Map<String, Object> user = users.get(userId);

                if (user == null) {
                    // notFound() 사용 이유: 404 상태 코드로 리소스 부재 명시
                    return HttpResponse.notFound("사용자를 찾을 수 없습니다: " + userId);
                }

                return HttpResponse.json(mapToJson(user));
            }
        });

        // DELETE /api/users/{id} - 사용자 삭제
        router.delete("/api/users/{id}", new RouteHandler() {
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                String userId = request.getPathParameter("id");
                // remove() 사용 이유: 삭제와 동시에 삭제된 값 반환
                Map<String, Object> removedUser = users.remove(userId);

                if (removedUser == null) {
                    return HttpResponse.notFound("사용자를 찾을 수 없습니다: " + userId);
                }

                // noContent() 사용 이유: 204 상태 코드로 성공적인 삭제 표현
                return HttpResponse.noContent();
            }
        });

        // === 비동기 처리 데모 ===
        // GET /api/async-demo - 비동기 처리 시뮬레이션
        router.get("/api/async-demo", new RouteHandler() {
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                // getQueryParameters() 사용 이유: URL 쿼리 스트링에서 매개변수 추출
                String delayParam = request.getQueryParameters().get("delay");
                int delay = 1000; // 기본 지연 시간

                if (delayParam != null) {
                    try {
                        delay = Integer.parseInt(delayParam);
                        // Math.max/min 사용 이유: 입력값을 안전한 범위로 제한
                        delay = Math.max(0, Math.min(delay, 10000)); // 0~10초 제한
                    } catch (NumberFormatException e) {
                        // 잘못된 형식이면 기본값 사용
                    }
                }

                try {
                    // Thread.sleep() 사용 이유: I/O 작업 시뮬레이션 (실제로는 비동기 처리)
                    Thread.sleep(delay);

                    Map<String, Object> result = new HashMap<>();
                    result.put("message", "비동기 처리 완료");
                    result.put("delay", delay);
                    result.put("timestamp", Instant.now().toString());
                    // Thread.currentThread().getName() 사용 이유: 처리 스레드 추적
                    result.put("threadName", Thread.currentThread().getName());

                    return HttpResponse.json(mapToJson(result));

                } catch (InterruptedException e) {
                    // Thread.currentThread().interrupt() 사용 이유: 인터럽트 상태 복원
                    Thread.currentThread().interrupt();
                    // serverError() 사용 이유: 500 상태 코드로 서버 오류 표현
                    return HttpResponse.serverError("처리가 중단되었습니다");
                }
            }
        });

        // === 파일 업로드 시뮬레이션 ===
        router.post("/api/upload", new RouteHandler() {
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                try {
                    // getContentType() 사용 이유: 요청의 미디어 타입 확인
                    String contentType = request.getHeaders().getContentType();

                    // startsWith() 사용 이유: multipart 형식 여부 확인
                    if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                        // getBody() 사용 이유: 바이너리 데이터를 바이트 배열로 처리
                        byte[] data = request.getBody();

                        Map<String, Object> result = new HashMap<>();
                        result.put("message", "파일 업로드 완료");
                        result.put("size", data.length); // 업로드된 데이터 크기
                        result.put("contentType", contentType != null ? contentType : "application/octet-stream");
                        result.put("uploadId", "upload_" + System.currentTimeMillis());
                        result.put("timestamp", Instant.now().toString());

                        return HttpResponse.json(mapToJson(result));
                    } else {
                        // multipart 파싱은 복잡하므로 간단 처리
                        return HttpResponse.ok("multipart 업로드가 수신되었습니다");
                    }

                } catch (Exception e) {
                    logger.log(Level.WARNING, "파일 업로드 처리 중 오류", e);
                    return HttpResponse.serverError("업로드 처리 중 오류가 발생했습니다");
                }
            }
        });

        // === Long Polling 시뮬레이션 ===
        router.get("/api/long-poll", new RouteHandler() {
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                String timeoutParam = request.getQueryParameters().get("timeout");
                int timeout = 30; // 기본 타임아웃

                if (timeoutParam != null) {
                    try {
                        timeout = Integer.parseInt(timeoutParam);
                        timeout = Math.max(1, Math.min(timeout, 60)); // 1~60초 제한
                    } catch (NumberFormatException e) {
                        // 기본값 사용
                    }
                }

                try {
                    // Math.random() 사용 이유: 이벤트 발생 시뮬레이션을 위한 랜덤 지연
                    int randomDelay = (int) (Math.random() * timeout * 1000);
                    Thread.sleep(randomDelay);

                    Map<String, Object> event = new HashMap<>();
                    event.put("type", "notification");
                    event.put("message", "새로운 알림이 있습니다");
                    event.put("timestamp", Instant.now().toString());
                    event.put("eventId", "event_" + System.currentTimeMillis());
                    event.put("waitTime", randomDelay);

                    return HttpResponse.json(mapToJson(event));

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();

                    Map<String, Object> timeout_result = new HashMap<>();
                    timeout_result.put("type", "timeout");
                    timeout_result.put("message", "타임아웃이 발생했습니다");
                    timeout_result.put("timestamp", Instant.now().toString());

                    return HttpResponse.json(mapToJson(timeout_result));
                }
            }
        });

        logger.fine("데모 라우트 설정 완료: 사용자 API, 비동기 처리, 파일 업로드, Long Polling");
    }

    /**
     * 고급 메트릭 라우트 설정 - 서버 성능 모니터링용 상세 엔드포인트들
     *
     * @param router 라우트를 등록할 Router 인스턴스
     */
    private static void setupAdvancedMetricsRoutes(Router router) { // private static: 클래스 내부 초기화 메서드
        // === 상세 서버 상태 엔드포인트 ===
        router.get("/admin/status", new RouteHandler() {
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                Map<String, Object> status = new HashMap<>();

                // 서버 기본 정보
                status.put("serverType", "HybridServer");
                status.put("version", "1.0");
                // getMetrics() 사용 이유: 서버의 성능 메트릭에 접근
                status.put("startTime", server.getMetrics().getStartTime());
                status.put("uptime", server.getMetrics().getCurrentUptime());

                // 서버 통계 정보
                // getStatistics() 사용 이유: 서버의 운영 통계에 접근
                HybridServer.HybridServerStatistics stats = server.getStatistics();
                status.put("totalRequests", stats.getTotalRequestsReceived());
                status.put("processedRequests", stats.getTotalRequestsProcessed());
                status.put("failedRequests", stats.getTotalRequestsFailed());
                status.put("activeConnections", stats.getCurrentActiveConnections());
                // String.format() 사용 이유: 백분율을 소수점 2자리로 포맷
                status.put("successRate", String.format("%.2f%%", stats.getSuccessRate() * 100));
                status.put("connectionUsage", String.format("%.2f%%", stats.getConnectionUsageRate() * 100));

                // AsyncContext 관리자 상태
                HybridServer.AsyncContextManager asyncManager = server.getAsyncContextManager();
                if (asyncManager != null) { // null 체크로 안전한 접근
                    status.put("asyncContext", asyncManager.getDetailedStatus());
                }

                // JVM 메모리 정보
                // Runtime.getRuntime() 사용 이유: 현재 JVM의 런타임 정보에 접근
                Runtime runtime = Runtime.getRuntime();
                Map<String, Object> memory = new HashMap<>();
                memory.put("totalMemory", runtime.totalMemory());
                memory.put("freeMemory", runtime.freeMemory());
                memory.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
                memory.put("maxMemory", runtime.maxMemory());
                status.put("memory", memory);

                // 시스템 정보
                Map<String, Object> system = new HashMap<>();
                system.put("availableProcessors", runtime.availableProcessors());
                // System.getProperty() 사용 이유: JVM 시스템 속성에서 OS 정보 조회
                system.put("operatingSystem", System.getProperty("os.name"));
                system.put("javaVersion", System.getProperty("java.version"));
                system.put("javaVendor", System.getProperty("java.vendor"));
                status.put("system", system);

                return HttpResponse.json(mapToJson(status));
            }
        });

        // === 성능 메트릭 엔드포인트 ===
        router.get("/admin/performance", new RouteHandler() {
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                Map<String, Object> performance = new HashMap<>();

                // 서버 메트릭
                HybridServer.HybridServerMetrics metrics = server.getMetrics();
                performance.put("averageResponseTime", String.format("%.2f ms", metrics.getAverageResponseTime()));
                performance.put("errorRate", String.format("%.2f%%", metrics.getErrorRate()));
                // formatDuration() 사용 이유: 밀리초를 사람이 읽기 쉬운 형식으로 변환
                performance.put("currentUptime", formatDuration(metrics.getCurrentUptime()));

                // AsyncContext 성능 메트릭
                HybridServer.AsyncContextManager asyncManager = server.getAsyncContextManager();
                if (asyncManager != null) {
                    Map<String, Object> asyncMetrics = asyncManager.getMetrics();
                    performance.put("contextSwitches", asyncMetrics.get("contextSwitches"));
                    performance.put("ioTasks", asyncMetrics.get("ioTasks"));
                    performance.put("cpuTasks", asyncMetrics.get("cpuTasks"));
                    performance.put("timeouts", asyncMetrics.get("timeouts"));

                    // 작업 비율 계산
                    if (asyncMetrics.containsKey("ioTaskRatio")) {
                        performance.put("ioTaskRatio", String.format("%.1f%%", asyncMetrics.get("ioTaskRatio")));
                        performance.put("cpuTaskRatio", String.format("%.1f%%", asyncMetrics.get("cpuTaskRatio")));
                        performance.put("timeoutRatio", String.format("%.1f%%", asyncMetrics.get("timeoutRatio")));
                    }
                }

                // 스레드 정보
                Map<String, Object> threads = new HashMap<>();
                // Thread.currentThread().getThreadGroup() 사용 이유: 현재 스레드 그룹 정보 접근
                ThreadGroup currentGroup = Thread.currentThread().getThreadGroup();
                threads.put("activeThreadCount", currentGroup.activeCount());
                threads.put("threadGroupName", currentGroup.getName());
                performance.put("threads", threads);

                return HttpResponse.json(mapToJson(performance));
            }
        });

        // === 실시간 메트릭 엔드포인트 ===
        router.get("/admin/metrics/realtime", new RouteHandler() {
            @Override
            public HttpResponse handle(HttpRequest request) throws Exception {
                // 쿼리 파라미터에서 샘플링 간격 추출
                String intervalParam = request.getQueryParameters().get("interval");
                int interval = 5; // 기본 5초 간격

                if (intervalParam != null) {
                    try {
                        interval = Integer.parseInt(intervalParam);
                        interval = Math.max(1, Math.min(interval, 60)); // 1~60초로 제한
                    } catch (NumberFormatException e) {
                        // 잘못된 형식이면 기본값 사용
                    }
                }

                // 실시간 메트릭 데이터 수집
                Map<String, Object> realtime = new HashMap<>();
                realtime.put("timestamp", Instant.now().toString());
                realtime.put("interval", interval);

                // 현재 서버 상태
                HybridServer.HybridServerStatistics stats = server.getStatistics();
                realtime.put("activeConnections", stats.getCurrentActiveConnections());
                realtime.put("totalRequests", stats.getTotalRequestsReceived());
                realtime.put("successRate", stats.getSuccessRate());

                // 메모리 사용량 (MB 단위)
                Runtime runtime = Runtime.getRuntime();
                realtime.put("memoryUsage", (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0));

                // AsyncContext 현재 상태
                HybridServer.AsyncContextManager asyncManager = server.getAsyncContextManager();
                if (asyncManager != null) {
                    Map<String, Object> asyncStatus = asyncManager.getStatus();
                    realtime.put("contextSwitches", asyncStatus.get("totalContextSwitches"));
                    realtime.put("ioTasks", asyncStatus.get("totalIOTasks"));
                    realtime.put("cpuTasks", asyncStatus.get("totalCPUTasks"));
                }

                return HttpResponse.json(mapToJson(realtime));
            }
        });

        logger.fine("고급 메트릭 라우트 설정 완료: 상태, 성능, 실시간 메트릭");
    }

    /**
     * 미들웨어 설정 - 횡단 관심사 처리를 위한 미들웨어들 등록
     */
    private static void setupMiddleware() { // private static: 클래스 내부 초기화 메서드
        Router router = server.getRouter();

        // 로깅 미들웨어 추가
        // use() 사용 이유: AOP 방식으로 모든 요청에 로깅 적용
        router.use(new LoggingMiddleware());

        // CORS 미들웨어 추가 (활성화된 경우)
        boolean corsEnabled = Boolean.parseBoolean(config.getProperty("cors.enabled"));
        if (corsEnabled) {
            router.use(new CorsMiddleware());
            logger.info("CORS 미들웨어가 활성화되었습니다");
        }

        logger.fine("미들웨어 설정이 완료되었습니다");
    }

    /**
     * 서버 시작 - 실제 서버 시작 및 시작 완료 로그
     */
    private static void startServer() { // private static: 클래스 내부 서버 시작 메서드
        try {
            logger.info("HybridServer를 시작합니다...");
            // start() 사용 이유: 서버 소켓 바인딩 및 클라이언트 연결 수락 시작
            server.start();

            // 서버 시작 완료 정보 출력
            int port = Integer.parseInt(config.getProperty("server.port"));
            logger.info("=== HybridServer 시작 완료 ===");
            logger.info("서버 URL: http://localhost:" + port);
            logger.info("헬스 체크: http://localhost:" + port + "/health");
            logger.info("메트릭: http://localhost:" + port + "/metrics");
            logger.info("서버 정보: http://localhost:" + port + "/info");
            logger.info("비동기 상태: http://localhost:" + port + "/async-status");

            // 데모가 활성화된 경우 데모 URL들 출력
            boolean demoEnabled = Boolean.parseBoolean(config.getProperty("demo.enabled"));
            if (demoEnabled) {
                logger.info("=== 데모 API 엔드포인트 ===");
                logger.info("사용자 API: http://localhost:" + port + "/api/users");
                logger.info("비동기 데모: http://localhost:" + port + "/api/async-demo?delay=2000");
                logger.info("파일 업로드: http://localhost:" + port + "/api/upload");
                logger.info("Long Polling: http://localhost:" + port + "/api/long-poll");
            }

            // 메트릭이 활성화된 경우 관리자 URL들 출력
            boolean metricsEnabled = Boolean.parseBoolean(config.getProperty("metrics.enabled"));
            if (metricsEnabled) {
                logger.info("=== 관리자 엔드포인트 ===");
                logger.info("서버 상태: http://localhost:" + port + "/admin/status");
                logger.info("성능 메트릭: http://localhost:" + port + "/admin/performance");
                logger.info("실시간 메트릭: http://localhost:" + port + "/admin/metrics/realtime");
            }

            logger.info("=== 시작 완료 ===");

        } catch (IOException e) {
            logger.log(Level.SEVERE, "서버 시작 실패", e);
            // RuntimeException 사용 이유: 서버 시작 실패는 치명적 오류
            throw new RuntimeException("서버 시작 실패", e);
        }
    }

    /**
     * 셧다운 훅 등록 - JVM 종료 시 서버를 안전하게 종료
     */
    private static void registerShutdownHook() { // private static: 클래스 내부 초기화 메서드
        // Runtime.getRuntime().addShutdownHook() 사용 이유: JVM 종료 시 자동 실행
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("셧다운 신호를 받았습니다. 서버를 안전하게 종료합니다...");

            // server.isRunning() 사용 이유: 서버 상태 확인 후 안전하게 종료
            if (server != null && server.isRunning()) {
                try {
                    server.stop(); // 서버 안전 종료
                    logger.info("서버가 안전하게 종료되었습니다");
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "서버 종료 중 오류 발생", e);
                }
            }
        }, "ShutdownHook-HybridServer")); // 스레드 이름 지정으로 디버깅 편의성

        logger.fine("셧다운 훅이 등록되었습니다");
    }

    /**
     * 대화형 모드 실행 - 콘솔에서 서버 관리 명령 처리
     */
    private static void runInteractiveMode() { // private static: 클래스 내부 대화형 모드 메서드
        logger.info("대화형 모드를 시작합니다. 'help'를 입력하여 사용 가능한 명령을 확인하세요.");

        // try-with-resources 사용 이유: Scanner 자동 해제로 리소스 누수 방지
        try (Scanner scanner = new Scanner(System.in)) {
            while (server.isRunning()) { // 서버 실행 중에만 명령 받기
                System.out.print("hybrid-server> ");
                // System.out.flush() 사용 이유: 출력 버퍼를 강제로 플러시하여 즉시 출력
                System.out.flush();

                if (!scanner.hasNextLine()) { // EOF 체크
                    break; // 입력 스트림 종료 시 루프 종료
                }

                // nextLine().trim() 사용 이유: 사용자 입력에서 앞뒤 공백 제거
                String command = scanner.nextLine().trim();

                if (command.isEmpty()) { // 빈 명령 체크
                    continue; // 다음 반복으로 건너뜀
                }

                processInteractiveCommand(command); // 입력된 명령 처리
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "대화형 모드 중 오류 발생", e);
        }
    }

    /**
     * 대화형 명령 처리 - 사용자가 입력한 명령을 파싱하고 실행
     *
     * @param command 사용자가 입력한 명령
     */
    private static void processInteractiveCommand(String command) { // private static: 클래스 내부 명령 처리 메서드
        // split("\\s+") 사용 이유: 정규식으로 하나 이상의 공백 문자를 구분자로 사용
        String[] parts = command.split("\\s+");
        // toLowerCase() 사용 이유: 대소문자 구분 없는 명령 처리
        String cmd = parts[0].toLowerCase();

        try {
            switch (cmd) { // switch 문: 명령에 따른 분기 처리
                case "help": // 도움말 명령
                case "h":
                    printInteractiveHelp(); // 대화형 모드 도움말 출력
                    break;

                case "status": // 서버 상태 확인 명령
                case "stat":
                    printServerStatus(); // 서버 상태 정보 출력
                    break;

                case "metrics": // 메트릭 정보 확인 명령
                case "metric":
                    printServerMetrics(); // 서버 메트릭 정보 출력
                    break;

                case "config": // 설정 정보 확인 명령
                case "conf":
                    printServerConfig(); // 서버 설정 정보 출력
                    break;

                case "async": // 비동기 상태 확인 명령
                    printAsyncStatus(); // 비동기 컨텍스트 상태 출력
                    break;

                case "memory": // 메모리 정보 확인 명령
                case "mem":
                    printMemoryStatus(); // JVM 메모리 상태 출력
                    break;

                case "gc": // 가비지 컬렉션 실행 명령
                    // System.gc() 사용 이유: JVM에게 가비지 컬렉션 실행 요청
                    System.gc();
                    System.out.println("가비지 컬렉션을 요청했습니다.");
                    break;

                case "loglevel": // 로그 레벨 변경 명령
                    if (parts.length > 1) { // 로그 레벨 인수 확인
                        changeLogLevel(parts[1]); // 로그 레벨 변경
                    } else {
                        // Logger.getLogger("").getLevel() 사용 이유: 루트 로거의 현재 레벨 조회
                        System.out.println("현재 로그 레벨: " + Logger.getLogger("").getLevel());
                        System.out.println("사용법: loglevel <레벨> (SEVERE, WARNING, INFO, FINE, ALL)");
                    }
                    break;

                case "stop": // 서버 중지 명령
                case "quit":
                case "exit":
                    System.out.println("서버를 중지합니다...");
                    server.stop(); // 서버 중지 실행
                    return; // 메서드 종료

                default: // 알 수 없는 명령
                    System.out.println("알 수 없는 명령: " + cmd);
                    System.out.println("'help'를 입력하여 사용 가능한 명령을 확인하세요.");
                    break;
            }
        } catch (Exception e) {
            // getMessage() 사용 이유: 예외의 간단한 메시지만 사용자에게 표시
            System.out.println("명령 처리 중 오류 발생: " + e.getMessage());
            logger.log(Level.WARNING, "대화형 명령 처리 오류: " + command, e);
        }
    }

    /**
     * 대화형 모드 도움말 출력 - 사용 가능한 명령들과 설명
     */
    private static void printInteractiveHelp() { // private static: 클래스 내부 도움말 메서드
        System.out.println("=== HybridServer 대화형 명령 ===");
        System.out.println("help, h          - 이 도움말 출력");
        System.out.println("status, stat     - 서버 상태 정보 출력");
        System.out.println("metrics, metric  - 서버 메트릭 정보 출력");
        System.out.println("config, conf     - 서버 설정 정보 출력");
        System.out.println("async            - 비동기 컨텍스트 상태 출력");
        System.out.println("memory, mem      - JVM 메모리 상태 출력");
        System.out.println("gc               - 가비지 컬렉션 실행");
        System.out.println("loglevel [레벨]  - 로그 레벨 확인/변경");
        System.out.println("stop, quit, exit - 서버 중지");
        System.out.println("=== 명령 끝 ===");
    }

    /**
     * 서버 상태 정보 출력 - 현재 서버의 주요 상태 지표들
     */
    private static void printServerStatus() { // private static: 클래스 내부 상태 출력 메서드
        HybridServer.HybridServerStatistics stats = server.getStatistics();

        System.out.println("=== 서버 상태 ===");
        // server.isRunning() 사용 이유: 서버의 현재 실행 상태 확인
        System.out.println("서버 실행 상태: " + (server.isRunning() ? "실행 중" : "중지됨"));
        System.out.println("포트: " + stats.getPort());
        System.out.println("최대 연결 수: " + stats.getMaxConnections());
        System.out.println("현재 활성 연결: " + stats.getCurrentActiveConnections());
        // String.format("%.2f%%") 사용 이유: 백분율을 소수점 2자리로 포맷
        System.out.println("연결 사용률: " + String.format("%.2f%%", stats.getConnectionUsageRate() * 100));
        System.out.println("총 요청 수: " + stats.getTotalRequestsReceived());
        System.out.println("처리된 요청: " + stats.getTotalRequestsProcessed());
        System.out.println("실패한 요청: " + stats.getTotalRequestsFailed());
        System.out.println("성공률: " + String.format("%.2f%%", stats.getSuccessRate() * 100));
        System.out.println("실패율: " + String.format("%.2f%%", stats.getFailureRate() * 100));
        // formatDuration() 사용 이유: 밀리초를 사람이 읽기 쉬운 형식으로 변환
        System.out.println("업타임: " + formatDuration(server.getMetrics().getCurrentUptime()));
        System.out.println("=== 상태 끝 ===");
    }

    /**
     * 서버 메트릭 정보 출력 - 성능 관련 상세 지표들
     */
    private static void printServerMetrics() { // private static: 클래스 내부 메트릭 출력 메서드
        HybridServer.HybridServerMetrics metrics = server.getMetrics();

        System.out.println("=== 서버 메트릭 ===");
        System.out.println("평균 응답 시간: " + String.format("%.2f ms", metrics.getAverageResponseTime()));
        System.out.println("에러율: " + String.format("%.2f%%", metrics.getErrorRate()));
        // new Date() 사용 이유: 타임스탬프를 사람이 읽기 쉬운 날짜 형식으로 변환
        System.out.println("시작 시간: " + new Date(metrics.getStartTime()));
        System.out.println("현재 업타임: " + formatDuration(metrics.getCurrentUptime()));

        // 고급 메트릭 정보
        Map<String, Object> allMetrics = metrics.getAllMetrics();
        System.out.println("총 처리 시간: " + allMetrics.get("totalProcessingTime") + " ms");
        System.out.println("총 연결 수: " + allMetrics.get("totalConnections"));
        System.out.println("총 에러 수: " + allMetrics.get("totalErrors"));
        System.out.println("=== 메트릭 끝 ===");
    }

    /**
     * 서버 설정 정보 출력 - 현재 적용된 설정값들
     */
    private static void printServerConfig() { // private static: 클래스 내부 설정 출력 메서드
        System.out.println("=== 서버 설정 ===");

        // entrySet() 사용 이유: Properties의 모든 키-값 쌍을 효율적으로 순회
        for (Map.Entry<Object, Object> entry : config.entrySet()) {
            System.out.println(entry.getKey() + " = " + entry.getValue());
        }

        System.out.println("=== 설정 끝 ===");
    }

    /**
     * 비동기 컨텍스트 상태 출력 - AsyncContextManager의 상세 상태
     */
    private static void printAsyncStatus() { // private static: 클래스 내부 비동기 상태 출력 메서드
        HybridServer.AsyncContextManager asyncManager = server.getAsyncContextManager();

        if (asyncManager == null) { // null 체크로 안전한 접근
            System.out.println("AsyncContextManager가 초기화되지 않았습니다.");
            return; // early return으로 불필요한 처리 방지
        }

        System.out.println("=== 비동기 컨텍스트 상태 ===");

        Map<String, Object> status = asyncManager.getDetailedStatus();
        for (Map.Entry<String, Object> entry : status.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }

        System.out.println("=== 비동기 상태 끝 ===");
    }

    /**
     * JVM 메모리 상태 출력 - 힙 메모리 사용량과 가비지 컬렉션 정보
     */
    private static void printMemoryStatus() { // private static: 클래스 내부 메모리 상태 출력 메서드
        Runtime runtime = Runtime.getRuntime();

        // 메모리 크기들을 변수로 저장하여 계산 최적화
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        System.out.println("=== JVM 메모리 상태 ===");
        // formatBytes() 사용 이유: 바이트를 KB/MB/GB 단위로 사람이 읽기 쉽게 변환
        System.out.println("사용 중인 메모리: " + formatBytes(usedMemory));
        System.out.println("여유 메모리: " + formatBytes(freeMemory));
        System.out.println("총 할당된 메모리: " + formatBytes(totalMemory));
        System.out.println("최대 메모리: " + formatBytes(maxMemory));
        // (double) 캐스팅 사용 이유: 정수 나눗셈을 실수 나눗셈으로 변환하여 정확한 백분율 계산
        System.out.println("메모리 사용률: " + String.format("%.2f%%", (double) usedMemory / maxMemory * 100));
        System.out.println("사용 가능한 프로세서: " + runtime.availableProcessors());
        System.out.println("=== 메모리 상태 끝 ===");
    }

    /**
     * 로그 레벨 변경 - 실행 중에 동적으로 로그 레벨 조정
     *
     * @param levelStr 새로운 로그 레벨 문자열
     */
    private static void changeLogLevel(String levelStr) { // private static: 클래스 내부 로그 레벨 변경 메서드
        try {
            // Level.parse() 사용 이유: 문자열을 Level 열거형으로 안전하게 변환
            Level newLevel = Level.parse(levelStr.toUpperCase());
            // Logger.getLogger("").setLevel() 사용 이유: 루트 로거의 레벨을 동적으로 변경
            Logger.getLogger("").setLevel(newLevel);

            System.out.println("로그 레벨이 " + newLevel + "로 변경되었습니다.");
            logger.info("로그 레벨이 동적으로 " + newLevel + "로 변경되었습니다");

        } catch (IllegalArgumentException e) { // 잘못된 로그 레벨 문자열 처리
            System.out.println("잘못된 로그 레벨: " + levelStr);
            System.out.println("사용 가능한 레벨: SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL, OFF");
        }
    }

    /**
     * 비대화형 모드에서 서버 종료 대기
     */
    private static void waitForShutdown() { // private static: 클래스 내부 종료 대기 메서드
        try {
            logger.info("서버가 실행 중입니다. 종료하려면 Ctrl+C를 누르세요.");

            // 서버가 실행 중인 동안 계속 대기
            while (server.isRunning()) {
                // Thread.sleep() 사용 이유: CPU 사용률을 낮추면서 주기적으로 상태 확인
                Thread.sleep(1000); // 1초마다 상태 확인
            }

        } catch (InterruptedException e) {
            // Thread.currentThread().interrupt() 사용 이유: 인터럽트 상태를 복원하여 다른 스레드에게 신호 전달
            Thread.currentThread().interrupt();
            logger.info("서버 대기가 중단되었습니다");
        }
    }

    // ========== 유틸리티 메서드들 ==========

    /**
     * Map을 간단한 JSON 문자열로 변환 - 데모 API에서 JSON 응답 생성용
     *
     * @param map 변환할 Map 객체
     * @return JSON 형태의 문자열
     */
    private static String mapToJson(Map<String, Object> map) { // private static: 클래스 내부 JSON 변환 유틸리티
        if (map == null || map.isEmpty()) { // null과 empty 체크로 안전한 처리
            return "{}"; // 빈 JSON 객체 반환
        }

        // StringBuilder 사용 이유: 문자열 연결 성능 최적화 (String + 연산보다 효율적)
        StringBuilder json = new StringBuilder();
        json.append("{"); // JSON 객체 시작

        boolean first = true; // 첫 번째 요소 플래그
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(","); // 쉼표로 JSON 요소 분리
            }
            first = false;

            // 키 추가 (항상 문자열로 처리)
            json.append("\"").append(escapeJsonString(entry.getKey())).append("\":");

            // 값 추가 (타입별 처리)
            Object value = entry.getValue();
            if (value == null) {
                json.append("null"); // JSON null 값
            } else if (value instanceof String) {
                // instanceof 사용 이유: 런타임에 객체의 실제 타입 확인
                json.append("\"").append(escapeJsonString((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value); // 숫자와 불린은 따옴표 없이 직접 추가
            } else {
                // toString() 사용 이유: 다른 객체들을 문자열로 변환
                json.append("\"").append(escapeJsonString(value.toString())).append("\"");
            }
        }

        json.append("}"); // JSON 객체 종료
        return json.toString();
    }

    /**
     * JSON 문자열 이스케이프 처리 - 특수 문자들을 JSON 형식에 맞게 변환
     *
     * @param str 이스케이프할 문자열
     * @return 이스케이프 처리된 문자열
     */
    private static String escapeJsonString(String str) { // private static: 클래스 내부 JSON 이스케이프 유틸리티
        if (str == null) {
            return ""; // null-safe 처리
        }

        StringBuilder escaped = new StringBuilder();

        // toCharArray() 사용 이유: 문자열의 각 문자에 효율적으로 접근
        for (char c : str.toCharArray()) {
            switch (c) { // switch 문으로 특수 문자별 이스케이프 처리
                case '"': // 따옴표
                    escaped.append("\\\"");
                    break;
                case '\\': // 백슬래시
                    escaped.append("\\\\");
                    break;
                case '\b': // 백스페이스
                    escaped.append("\\b");
                    break;
                case '\f': // 폼 피드
                    escaped.append("\\f");
                    break;
                case '\n': // 개행 문자
                    escaped.append("\\n");
                    break;
                case '\r': // 캐리지 리턴
                    escaped.append("\\r");
                    break;
                case '\t': // 탭 문자
                    escaped.append("\\t");
                    break;
                default:
                    if (c < 32) { // 제어 문자인 경우 (ASCII 0-31)
                        // String.format() 사용 이유: 유니코드 이스케이프 형식으로 변환
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c); // 일반 문자는 그대로 추가
                    }
                    break;
            }
        }

        return escaped.toString();
    }

    /**
     * 간단한 JSON 파싱 - 기본적인 키-값 쌍만 처리하는 단순한 파서
     * 실제 프로덕션에서는 Jackson이나 Gson 같은 라이브러리 사용 권장
     *
     * @param json 파싱할 JSON 문자열
     * @return 파싱된 키-값 쌍을 담은 Map
     */
    private static Map<String, Object> parseSimpleJson(String json) { // private static: 클래스 내부 JSON 파싱 유틸리티
        Map<String, Object> result = new HashMap<>();

        if (json == null || json.trim().isEmpty()) {
            return result; // 빈 맵 반환
        }

        // 간단한 JSON 객체 파싱 (중괄호 제거)
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            // substring() 사용 이유: 첫 번째와 마지막 문자(중괄호) 제거
            json = json.substring(1, json.length() - 1);
        }

        // split(",") 사용 이유: 쉼표로 키-값 쌍들을 분리
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            // split(":", 2) 사용 이유: 콜론을 기준으로 최대 2개 부분으로 분할 (키와 값)
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                // replaceAll() 사용 이유: 정규식으로 앞뒤 따옴표 제거
                String key = keyValue[0].trim().replaceAll("^\"|\"$", "");
                String value = keyValue[1].trim().replaceAll("^\"|\"$", "");
                result.put(key, value);
            }
        }

        return result;
    }

    /**
     * 바이트 크기를 사람이 읽기 쉬운 형태로 포맷 - KB, MB, GB 단위로 변환
     *
     * @param bytes 바이트 크기
     * @return 포맷된 크기 문자열
     */
    private static String formatBytes(long bytes) { // private static: 클래스 내부 바이트 포맷 유틸리티
        if (bytes < 1024) {
            return bytes + " B"; // 1KB 미만은 바이트 단위
        }

        // 1024.0 사용 이유: 정수를 실수로 나누어 정확한 소수점 계산
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.2f KB", kb);
        }

        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.2f MB", mb);
        }

        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    /**
     * 시간을 사람이 읽기 쉬운 형태로 포맷 - 일, 시간, 분, 초 단위로 변환
     *
     * @param millis 밀리초 단위 시간
     * @return 포맷된 시간 문자열
     */
    private static String formatDuration(long millis) { // private static: 클래스 내부 시간 포맷 유틸리티
        if (millis < 0) {
            return "0초"; // 음수 입력에 대한 안전 처리
        }

        // 시간 단위별 변환
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        // 나머지 연산(%) 사용 이유: 상위 단위로 변환 후 남은 하위 단위 계산
        seconds %= 60;
        minutes %= 60;
        hours %= 24;

        StringBuilder duration = new StringBuilder();

        // 각 단위별로 0이 아닌 경우만 문자열에 추가
        if (days > 0) {
            duration.append(days).append("일 ");
        }
        if (hours > 0) {
            duration.append(hours).append("시간 ");
        }
        if (minutes > 0) {
            duration.append(minutes).append("분 ");
        }
        // duration.length() == 0 체크 이유: 모든 단위가 0인 경우 최소 "0초" 표시
        if (seconds > 0 || duration.length() == 0) {
            duration.append(seconds).append("초");
        }

        // trim() 사용 이유: 마지막에 남을 수 있는 공백 제거
        return duration.toString().trim();
    }
}