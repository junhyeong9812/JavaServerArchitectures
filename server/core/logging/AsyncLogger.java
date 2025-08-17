package server.core.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 비동기 Logger 구현체
 * 별도 스레드에서 System.out.println으로 출력
 */
public class AsyncLogger implements Logger {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final String name;
    private volatile LogLevel currentLevel;
    private final BlockingQueue<LogMessage> messageQueue;
    private final Thread loggerThread;
    private final AtomicBoolean running;

    // 전역 설정
    private static volatile boolean enableColors = true;
    private static volatile boolean enableTimestamp = true;
    private static volatile int maxQueueSize = 10000;

    public AsyncLogger(String name, LogLevel level) {
        this.name = name;
        this.currentLevel = level;
        this.messageQueue = new LinkedBlockingQueue<>(maxQueueSize);
        this.running = new AtomicBoolean(true);

        // 로거 스레드 시작
        this.loggerThread = new Thread(this::processMessages, "AsyncLogger-" + name);
        this.loggerThread.setDaemon(true);
        this.loggerThread.start();
    }

    /**
     * 메시지 처리 루프 (별도 스레드)
     */
    private void processMessages() {
        while (running.get() || !messageQueue.isEmpty()) {
            try {
                LogMessage message = messageQueue.take();
                formatAndPrint(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // 로거 자체에서 에러가 발생하면 직접 출력
                System.err.println("Logger error: " + e.getMessage());
            }
        }
    }

    /**
     * 메시지 포맷팅 및 출력
     */
    private void formatAndPrint(LogMessage message) {
        StringBuilder sb = new StringBuilder();

        // 색상 코드 (터미널 지원시)
        String colorCode = getColorCode(message.level);
        String resetCode = enableColors ? "\u001B[0m" : "";

        if (enableColors && colorCode != null) {
            sb.append(colorCode);
        }

        // 타임스탬프
        if (enableTimestamp) {
            sb.append("[").append(message.timestamp.format(TIME_FORMAT)).append("] ");
        }

        // 레벨
        sb.append(String.format("%-5s", message.level.getName())).append(" ");

        // Logger 이름 (간략화)
        String shortName = getShortName(name);
        sb.append(String.format("%-20s", shortName)).append(" - ");

        // 메시지
        sb.append(message.formattedMessage);

        // 색상 리셋
        if (enableColors && colorCode != null) {
            sb.append(resetCode);
        }

        // 출력 (finally println 사용!)
        if (message.level == LogLevel.ERROR) {
            System.err.println(sb.toString());
        } else {
            System.out.println(sb.toString());
        }

        // 예외 출력
        if (message.throwable != null) {
            message.throwable.printStackTrace();
        }
    }

    /**
     * 로그 레벨별 색상 코드
     */
    private String getColorCode(LogLevel level) {
        if (!enableColors) return null;

        switch (level) {
            case DEBUG: return "\u001B[36m"; // Cyan
            case INFO:  return "\u001B[32m"; // Green
            case WARN:  return "\u001B[33m"; // Yellow
            case ERROR: return "\u001B[31m"; // Red
            default:    return null;
        }
    }

    /**
     * Logger 이름 단축
     */
    private String getShortName(String fullName) {
        if (fullName.length() <= 20) return fullName;

        String[] parts = fullName.split("\\.");
        if (parts.length <= 1) {
            return fullName.substring(fullName.length() - 20);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            sb.append(parts[i].charAt(0)).append(".");
        }
        sb.append(parts[parts.length - 1]);

        return sb.toString();
    }

    /**
     * 로그 메시지 큐에 추가
     */
    private void log(LogLevel level, String message, Throwable throwable) {
        if (!isLevelEnabled(level)) {
            return;
        }

        try {
            LogMessage logMessage = new LogMessage(
                    level,
                    LocalDateTime.now(),
                    message,
                    throwable
            );

            // 큐가 가득찬 경우 가장 오래된 메시지 제거
            if (!messageQueue.offer(logMessage)) {
                messageQueue.poll(); // 오래된 메시지 제거
                messageQueue.offer(logMessage); // 새 메시지 추가
            }

        } catch (Exception e) {
            // 큐 오류시 직접 출력
            System.err.println("Logger queue error: " + e.getMessage());
            System.out.println(message);
        }
    }

    /**
     * 문자열 포맷팅 (SLF4J 스타일)
     */
    private String formatMessage(String format, Object... args) {
        if (args == null || args.length == 0) {
            return format;
        }

        String result = format;
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{}";
            if (result.contains(placeholder)) {
                String replacement = args[i] != null ? args[i].toString() : "null";

                // ⭐ 핵심 수정: replaceFirst 대신 indexOf + substring 사용
                // regex를 사용하지 않는 안전한 방법
                int index = result.indexOf(placeholder);
                if (index != -1) {
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

    private boolean isLevelEnabled(LogLevel level) {
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
     */
    public void shutdown() {
        running.set(false);
        loggerThread.interrupt();

        try {
            loggerThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // === 전역 설정 ===

    public static void setEnableColors(boolean enable) {
        enableColors = enable;
    }

    public static void setEnableTimestamp(boolean enable) {
        enableTimestamp = enable;
    }

    public static void setMaxQueueSize(int size) {
        maxQueueSize = size;
    }

    /**
     * 로그 메시지 클래스
     */
    private static class LogMessage {
        final LogLevel level;
        final LocalDateTime timestamp;
        final String formattedMessage;
        final Throwable throwable;

        LogMessage(LogLevel level, LocalDateTime timestamp, String formattedMessage, Throwable throwable) {
            this.level = level;
            this.timestamp = timestamp;
            this.formattedMessage = formattedMessage;
            this.throwable = throwable;
        }
    }
}