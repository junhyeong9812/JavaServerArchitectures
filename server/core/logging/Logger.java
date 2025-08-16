package server.core.logging;

/**
 * Logger 인터페이스
 * SLF4J와 유사한 API 제공
 */
public interface Logger {

    /**
     * DEBUG 레벨 로그
     */
    void debug(String message);
    void debug(String format, Object... args);
    void debug(String message, Throwable throwable);

    /**
     * INFO 레벨 로그
     */
    void info(String message);
    void info(String format, Object... args);
    void info(String message, Throwable throwable);

    /**
     * WARN 레벨 로그
     */
    void warn(String message);
    void warn(String format, Object... args);
    void warn(String message, Throwable throwable);

    /**
     * ERROR 레벨 로그
     */
    void error(String message);
    void error(String format, Object... args);
    void error(String message, Throwable throwable);

    /**
     * 레벨 체크 메서드
     */
    boolean isDebugEnabled();
    boolean isInfoEnabled();
    boolean isWarnEnabled();
    boolean isErrorEnabled();

    /**
     * 로그 레벨 설정
     */
    void setLevel(LogLevel level);
    LogLevel getLevel();

    /**
     * Logger 이름 반환
     */
    String getName();
}