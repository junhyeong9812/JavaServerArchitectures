package server.core.logging;

/**
 * 로그 레벨 정의
 */
public enum LogLevel {
    DEBUG(0, "DEBUG"),
    INFO(1, "INFO"),
    WARN(2, "WARN"),
    ERROR(3, "ERROR");

    private final int level;
    private final String name;

    LogLevel(int level, String name) {
        this.level = level;
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    /**
     * 현재 레벨이 target 레벨보다 높거나 같은지 확인
     */
    public boolean isEnabled(LogLevel target) {
        return this.level >= target.level;
    }

    @Override
    public String toString() {
        return name;
    }
}