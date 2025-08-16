package server.core.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 동기 Simple Logger 구현체
 * 즉시 System.out.println으로 출력 (비동기 없음)
 */
public class SimpleLogger implements Logger {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final String name;
    private volatile LogLevel currentLevel;

    // 전역 설정
    private static volatile boolean enableColors = true;
    private static volatile boolean enableTimestamp = true;
    private static volatile boolean enableThreadName = false;

    public SimpleLogger(String name, LogLevel level) {
        this.name = name;
        this.currentLevel = level;
    }

    /**
     * 즉시 동기 출력
     */
    private void log(LogLevel level, String message, Throwable throwable) {
        if (!isLevelEnabled(level)) {
            return;
        }

        String formattedMessage = formatMessage(level, message);

        // 즉시 출력 (동기)
        if (level == LogLevel.ERROR) {
            System.err.println(formattedMessage);
        } else {
            System.out.println(formattedMessage);
        }

        // 예외 출력
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

    /**
     * 메시지 포맷팅
     */
    private String formatMessage(LogLevel level, String message) {
        StringBuilder sb = new StringBuilder();

        // 색상 코드
        String colorCode = getColorCode(level);
        String resetCode = enableColors ? "\u001B[0m" : "";

        if (enableColors && colorCode != null) {
            sb.append(colorCode);
        }

        // 타임스탬프
        if (enableTimestamp) {
            sb.append("[").append(LocalDateTime.now().format(TIME_FORMAT)).append("] ");
        }

        // 레벨
        sb.append(String.format("%-5s", level.getName())).append(" ");

        // 스레드명 (옵션)
        if (enableThreadName) {
            String threadName = Thread.currentThread().getName();
            sb.append(String.format("[%-15s] ",
                    threadName.length() > 15 ? threadName.substring(0, 15) : threadName));
        }

        // Logger 이름 (간략화)
        String shortName = getShortName(name);
        sb.append(String.format("%-20s", shortName)).append(" - ");

        // 메시지
        sb.append(message);

        // 색상 리셋
        if (enableColors && colorCode != null) {
            sb.append(resetCode);
        }

        return sb.toString();
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
     * 문자열 포맷팅 (SLF4J 스타일)
     */
    private String formatMessageWithArgs(String format, Object... args) {
        if (args == null || args.length == 0) {
            return format;
        }

        String result = format;
        for (Object arg : args) {
            result = result.replaceFirst("\\{\\}", String.valueOf(arg));
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
            log(LogLevel.DEBUG, formatMessageWithArgs(format, args), null);
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
            log(LogLevel.INFO, formatMessageWithArgs(format, args), null);
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
            log(LogLevel.WARN, formatMessageWithArgs(format, args), null);
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
            log(LogLevel.ERROR, formatMessageWithArgs(format, args), null);
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

    // === 전역 설정 ===

    public static void setEnableColors(boolean enable) {
        enableColors = enable;
    }

    public static void setEnableTimestamp(boolean enable) {
        enableTimestamp = enable;
    }

    public static void setEnableThreadName(boolean enable) {
        enableThreadName = enable;
    }

    public static boolean isEnableColors() {
        return enableColors;
    }

    public static boolean isEnableTimestamp() {
        return enableTimestamp;
    }

    public static boolean isEnableThreadName() {
        return enableThreadName;
    }

    @Override
    public String toString() {
        return String.format("SimpleLogger{name='%s', level=%s}", name, currentLevel);
    }
}