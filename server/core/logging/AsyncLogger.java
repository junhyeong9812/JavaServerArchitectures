package server.core.logging;

// 날짜/시간 처리 관련 클래스들
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
// 동시성 처리를 위한 클래스들
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 비동기 Logger 구현체
 * 별도 스레드에서 System.out.println으로 출력
 *
 * 특징:
 * - 메인 스레드를 차단하지 않는 비동기 로깅
 * - BlockingQueue를 사용한 생산자-소비자 패턴
 * - 고성능 애플리케이션에 적합
 * - 메모리 사용량은 SimpleLogger보다 높음
 */
public class AsyncLogger implements Logger {

    // 날짜/시간 포맷 상수 (밀리초까지 표시)
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // Logger 인스턴스별 설정
    private final String name;              // Logger 이름 (불변)
    private volatile LogLevel currentLevel; // 현재 로그 레벨 (변경 가능)

    // 비동기 처리를 위한 구성 요소들
    private final BlockingQueue<LogMessage> messageQueue; // 메시지 큐 (생산자-소비자 패턴)
    private final Thread loggerThread;                    // 로그 출력 전용 스레드
    private final AtomicBoolean running;                  // 스레드 실행 상태 (원자적 연산)

    // 전역 설정 (모든 AsyncLogger 인스턴스가 공유)
    // volatile: 멀티스레드 환경에서 변수 값의 가시성 보장
    private static volatile boolean enableColors = true;    // 색상 출력 여부
    private static volatile boolean enableTimestamp = true; // 타임스탬프 출력 여부
    private static volatile int maxQueueSize = 10000;       // 최대 큐 크기

    // 생성자
    public AsyncLogger(String name, LogLevel level) {
        this.name = name;
        this.currentLevel = level;

        // LinkedBlockingQueue: FIFO 순서를 보장하는 블로킹 큐
        // 용량 제한으로 메모리 사용량 제어
        this.messageQueue = new LinkedBlockingQueue<>(maxQueueSize);

        // AtomicBoolean: 스레드 안전한 boolean 값
        // volatile boolean보다 더 강력한 원자적 연산 제공
        this.running = new AtomicBoolean(true);

        // 로거 스레드 생성 및 시작
        // 생성자에서 바로 스레드를 시작하는 패턴
        this.loggerThread = new Thread(this::processMessages, "AsyncLogger-" + name);

        // setDaemon(true): 데몬 스레드로 설정
        // 메인 프로그램이 종료되면 이 스레드도 함께 종료됨
        this.loggerThread.setDaemon(true);

        // 스레드 시작 (processMessages 메서드를 별도 스레드에서 실행)
        this.loggerThread.start();
    }

    /**
     * 메시지 처리 루프 (별도 스레드)
     * 이 메서드는 로거 전용 스레드에서 계속 실행됨
     */
    private void processMessages() {
        // running이 true이거나 큐에 처리할 메시지가 남아있는 동안 계속 실행
        // ||: 논리합 연산자 (OR)
        while (running.get() || !messageQueue.isEmpty()) {
            try {
                // take(): 큐에서 메시지를 가져옴 (블로킹 연산)
                // 큐가 비어있으면 메시지가 들어올 때까지 대기
                LogMessage message = messageQueue.take();

                // 메시지 포맷팅 및 출력
                formatAndPrint(message);

            } catch (InterruptedException e) {
                // 스레드가 중단됨 (보통 shutdown 시)
                // 현재 스레드의 인터럽트 상태를 복원
                Thread.currentThread().interrupt();
                break; // 루프 종료
            } catch (Exception e) {
                // 로거 자체에서 에러가 발생하면 직접 출력
                // 로깅 시스템이 망가져도 최소한 에러 메시지는 보이도록
                System.err.println("Logger error: " + e.getMessage());
            }
        }
    }

    /**
     * 메시지 포맷팅 및 출력
     * 실제 콘솔 출력은 이 메서드에서만 수행됨
     */
    private void formatAndPrint(LogMessage message) {
        // StringBuilder로 효율적인 문자열 조합
        StringBuilder sb = new StringBuilder();

        // 색상 코드 (터미널 지원시)
        String colorCode = getColorCode(message.level);
        String resetCode = enableColors ? "\u001B[0m" : ""; // ANSI 색상 리셋 코드

        // 1. 색상 시작 코드 추가
        if (enableColors && colorCode != null) {
            sb.append(colorCode);
        }

        // 2. 타임스탬프 추가
        if (enableTimestamp) {
            // message.timestamp: 로그 메시지가 생성된 시점의 시간
            sb.append("[").append(message.timestamp.format(TIME_FORMAT)).append("] ");
        }

        // 3. 로그 레벨 추가 (5자리 고정, 왼쪽 정렬)
        sb.append(String.format("%-5s", message.level.getName())).append(" ");

        // 4. Logger 이름 추가 (간략화)
        String shortName = getShortName(name);
        sb.append(String.format("%-20s", shortName)).append(" - ");

        // 5. 포맷팅된 메시지 추가
        sb.append(message.formattedMessage);

        // 6. 색상 리셋
        if (enableColors && colorCode != null) {
            sb.append(resetCode);
        }

        // 7. 출력 (finally println 사용!)
        if (message.level == LogLevel.ERROR) {
            // ERROR는 stderr로 출력
            System.err.println(sb.toString());
        } else {
            // 나머지는 stdout으로 출력
            System.out.println(sb.toString());
        }

        // 8. 예외 출력 (있는 경우)
        if (message.throwable != null) {
            // printStackTrace(): 스택 트레이스를 stderr에 출력
            message.throwable.printStackTrace();
        }
    }

    /**
     * 로그 레벨별 색상 코드
     * ANSI 이스케이프 시퀀스를 사용한 터미널 색상
     */
    private String getColorCode(LogLevel level) {
        if (!enableColors) return null;

        // ANSI 색상 코드
        switch (level) {
            case DEBUG: return "\u001B[36m"; // Cyan (청록색)
            case INFO:  return "\u001B[32m"; // Green (녹색)
            case WARN:  return "\u001B[33m"; // Yellow (노란색)
            case ERROR: return "\u001B[31m"; // Red (빨간색)
            default:    return null;
        }
    }

    /**
     * Logger 이름 단축
     * 긴 패키지명을 축약하여 가독성 향상
     */
    private String getShortName(String fullName) {
        // 20자 이하면 그대로 사용
        if (fullName.length() <= 20) return fullName;

        // 패키지명을 점(.)으로 분리
        String[] parts = fullName.split("\\.");
        if (parts.length <= 1) {
            // 분리할 수 없으면 뒤에서 20자만 잘라내기
            return fullName.substring(fullName.length() - 20);
        }

        // 패키지명 축약: com.example.service.UserService -> c.e.s.UserService
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            sb.append(parts[i].charAt(0)).append(".");
        }
        sb.append(parts[parts.length - 1]);

        return sb.toString();
    }

    /**
     * 로그 메시지 큐에 추가
     * 이 메서드가 실제 로깅의 진입점
     */
    private void log(LogLevel level, String message, Throwable throwable) {
        // 레벨 체크 - 비활성화된 레벨이면 즉시 리턴
        if (!isLevelEnabled(level)) {
            return;
        }

        try {
            // LogMessage 객체 생성
            // 현재 시간을 기록하여 정확한 로그 타임스탬프 유지
            LogMessage logMessage = new LogMessage(
                    level,
                    LocalDateTime.now(),  // 메시지 생성 시점의 시간
                    message,
                    throwable
            );

            // 큐에 메시지 추가 시도
            // offer(): 큐에 여유가 있으면 추가, 가득 찬 경우 false 반환
            if (!messageQueue.offer(logMessage)) {
                // 큐가 가득 찬 경우: 가장 오래된 메시지 제거 후 새 메시지 추가
                // poll(): 큐에서 첫 번째 메시지 제거 및 반환
                messageQueue.poll(); // 오래된 메시지 제거
                messageQueue.offer(logMessage); // 새 메시지 추가
            }

        } catch (Exception e) {
            // 큐 오류시 직접 출력 (로깅 시스템이 망가져도 메시지는 보이도록)
            System.err.println("Logger queue error: " + e.getMessage());
            System.out.println(message);
        }
    }

    /**
     * 문자열 포맷팅 (SLF4J 스타일)
     * {} 플레이스홀더를 실제 값으로 치환
     */
    private String formatMessage(String format, Object... args) {
        // 인수가 없으면 원본 포맷 그대로 반환
        if (args == null || args.length == 0) {
            return format;
        }

        String result = format;

        // 각 인수에 대해 순서대로 {} 치환
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{}";
            if (result.contains(placeholder)) {
                // null 안전 처리
                String replacement = args[i] != null ? args[i].toString() : "null";

                // ⭐ 핵심 수정: replaceFirst 대신 indexOf + substring 사용
                // regex를 사용하지 않는 안전한 방법
                // indexOf(): 문자열에서 특정 부분 문자열의 위치 찾기
                int index = result.indexOf(placeholder);
                if (index != -1) {
                    // substring()으로 문자열을 3부분으로 나누어 조합
                    // [시작~플레이스홀더 전] + [치환값] + [플레이스홀더 후~끝]
                    result = result.substring(0, index) +
                            replacement +
                            result.substring(index + placeholder.length());
                }
            }
        }
        return result;
    }

    // === Logger 인터페이스 구현 ===

    @Override
    public void debug(String message) {
        log(LogLevel.DEBUG, message, null);
    }

    @Override
    public void debug(String format, Object... args) {
        // 성능 최적화: 레벨이 활성화된 경우에만 포맷팅
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, formatMessage(format, args), null);
        }
    }

    @Override
    public void debug(String message, Throwable throwable) {
        log(LogLevel.DEBUG, message, throwable);
    }

    @Override
    public void info(String message) {
        log(LogLevel.INFO, message, null);
    }

    @Override
    public void info(String format, Object... args) {
        if (isInfoEnabled()) {
            log(LogLevel.INFO, formatMessage(format, args), null);
        }
    }

    @Override
    public void info(String message, Throwable throwable) {
        log(LogLevel.INFO, message, throwable);
    }

    @Override
    public void warn(String message) {
        log(LogLevel.WARN, message, null);
    }

    @Override
    public void warn(String format, Object... args) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, formatMessage(format, args), null);
        }
    }

    @Override
    public void warn(String message, Throwable throwable) {
        log(LogLevel.WARN, message, throwable);
    }

    @Override
    public void error(String message) {
        log(LogLevel.ERROR, message, null);
    }

    @Override
    public void error(String format, Object... args) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, formatMessage(format, args), null);
        }
    }

    @Override
    public void error(String message, Throwable throwable) {
        log(LogLevel.ERROR, message, throwable);
    }

    // === 레벨 체크 ===

    @Override
    public boolean isDebugEnabled() {
        return isLevelEnabled(LogLevel.DEBUG);
    }

    @Override
    public boolean isInfoEnabled() {
        return isLevelEnabled(LogLevel.INFO);
    }

    @Override
    public boolean isWarnEnabled() {
        return isLevelEnabled(LogLevel.WARN);
    }

    @Override
    public boolean isErrorEnabled() {
        return isLevelEnabled(LogLevel.ERROR);
    }

    // 실제 레벨 체크 로직
    private boolean isLevelEnabled(LogLevel level) {
        // 현재 레벨보다 높거나 같은 레벨만 출력
        return currentLevel.getLevel() <= level.getLevel();
    }

    // === 설정 ===

    @Override
    public void setLevel(LogLevel level) {
        this.currentLevel = level;
    }

    @Override
    public LogLevel getLevel() {
        return currentLevel;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Logger 종료
     * 안전한 스레드 종료를 위한 메서드
     */
    public void shutdown() {
        // 실행 중지 플래그 설정
        running.set(false);

        // 스레드 인터럽트 (블로킹 상태에서 깨우기)
        loggerThread.interrupt();

        try {
            // 스레드가 종료될 때까지 최대 1초 대기
            // join(timeout): 해당 스레드가 끝날 때까지 대기
            loggerThread.join(1000);
        } catch (InterruptedException e) {
            // 현재 스레드가 인터럽트되면 인터럽트 상태 복원
            Thread.currentThread().interrupt();
        }
    }

    // === 전역 설정 ===

    /**
     * 색상 출력 여부 설정
     */
    public static void setEnableColors(boolean enable) {
        enableColors = enable;
    }

    /**
     * 타임스탬프 출력 여부 설정
     */
    public static void setEnableTimestamp(boolean enable) {
        enableTimestamp = enable;
    }

    /**
     * 최대 큐 크기 설정
     */
    public static void setMaxQueueSize(int size) {
        maxQueueSize = size;
    }

    /**
     * 로그 메시지 클래스
     * 큐에 저장될 로그 정보를 담는 불변 객체
     */
    private static class LogMessage {
        final LogLevel level;           // 로그 레벨
        final LocalDateTime timestamp;   // 메시지 생성 시간
        final String formattedMessage;   // 포맷팅된 메시지
        final Throwable throwable;       // 예외 객체 (선택적)

        // 생성자 - 모든 필드를 final로 설정하여 불변성 보장
        LogMessage(LogLevel level, LocalDateTime timestamp, String formattedMessage, Throwable throwable) {
            this.level = level;
            this.timestamp = timestamp;
            this.formattedMessage = formattedMessage;
            this.throwable = throwable;
        }
    }
}