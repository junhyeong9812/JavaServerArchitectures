package server.core.logging;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.ArrayList;
import java.util.List;

/**
 * Logger 팩토리
 * SLF4J 패턴과 유사한 API 제공
 */
public class LoggerFactory {

    // Logger 인스턴스 캐시
    private static final ConcurrentMap<String, Logger> loggerCache = new ConcurrentHashMap<>();

    // 기본 설정
    private static volatile LogLevel defaultLevel = LogLevel.INFO;
    private static volatile boolean initialized = false;
    private static volatile LoggerType defaultLoggerType = LoggerType.ASYNC;

    // 생성된 모든 AsyncLogger 추적 (종료시 정리용)
    private static final List<AsyncLogger> asyncLoggers = new ArrayList<>();

    /**
     * Logger 타입 선택
     */
    public enum LoggerType {
        ASYNC,   // 비동기 Logger (기본값)
        SIMPLE   // 동기 Simple Logger
    }

    static {
        // JVM 종료시 모든 Logger 정리
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown();
        }));
    }

    /**
     * 클래스별 Logger 생성 (기본 타입)
     */
    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    /**
     * 이름별 Logger 생성 (기본 타입)
     */
    public static Logger getLogger(String name) {
        return getLogger(name, defaultLoggerType);
    }

    /**
     * 클래스별 Logger 생성 (타입 지정)
     */
    public static Logger getLogger(Class<?> clazz, LoggerType type) {
        return getLogger(clazz.getName(), type);
    }

    /**
     * 이름별 Logger 생성 (타입 지정)
     */
    public static Logger getLogger(String name, LoggerType type) {
        String cacheKey = name + ":" + type.name();

        return loggerCache.computeIfAbsent(cacheKey, key -> {
            Logger logger;

            switch (type) {
                case SIMPLE:
                    logger = new SimpleLogger(name, defaultLevel);
                    break;
                case ASYNC:
                default:
                    AsyncLogger asyncLogger = new AsyncLogger(name, defaultLevel);
                    // AsyncLogger 추적
                    synchronized (asyncLoggers) {
                        asyncLoggers.add(asyncLogger);
                    }
                    logger = asyncLogger;
                    break;
            }

            return logger;
        });
    }

    /**
     * Root Logger 생성
     */
    public static Logger getRootLogger() {
        return getLogger("ROOT");
    }

    /**
     * Simple Logger 전용 팩토리 메서드
     */
    public static Logger getSimpleLogger(Class<?> clazz) {
        return getLogger(clazz, LoggerType.SIMPLE);
    }

    /**
     * Simple Logger 전용 팩토리 메서드
     */
    public static Logger getSimpleLogger(String name) {
        return getLogger(name, LoggerType.SIMPLE);
    }

    /**
     * Async Logger 전용 팩토리 메서드
     */
    public static Logger getAsyncLogger(Class<?> clazz) {
        return getLogger(clazz, LoggerType.ASYNC);
    }

    /**
     * Async Logger 전용 팩토리 메서드
     */
    public static Logger getAsyncLogger(String name) {
        return getLogger(name, LoggerType.ASYNC);
    }

    /**
     * 기본 Logger 타입 설정
     */
    public static void setDefaultLoggerType(LoggerType type) {
        defaultLoggerType = type;
    }

    /**
     * 기본 Logger 타입 반환
     */
    public static LoggerType getDefaultLoggerType() {
        return defaultLoggerType;
    }

    /**
     * 기본 로그 레벨 설정
     */
    public static void setDefaultLevel(LogLevel level) {
        defaultLevel = level;

        // 기존 생성된 Logger들의 레벨도 업데이트
        for (Logger logger : loggerCache.values()) {
            logger.setLevel(level);
        }
    }

    /**
     * 기본 로그 레벨 반환
     */
    public static LogLevel getDefaultLevel() {
        return defaultLevel;
    }

    /**
     * 특정 패키지/클래스의 로그 레벨 설정
     */
    public static void setLevel(String name, LogLevel level) {
        // 정확한 이름 매칭
        for (String cacheKey : loggerCache.keySet()) {
            if (cacheKey.startsWith(name + ":")) {
                loggerCache.get(cacheKey).setLevel(level);
            }
        }

        // 패키지 패턴 매칭으로 하위 Logger들도 설정
        String pattern = name.endsWith("*") ? name.substring(0, name.length() - 1) : name + ".";
        for (String cacheKey : loggerCache.keySet()) {
            String loggerName = cacheKey.split(":")[0]; // 타입 부분 제거
            if (loggerName.startsWith(pattern)) {
                loggerCache.get(cacheKey).setLevel(level);
            }
        }
    }

    /**
     * 전역 설정 초기화
     */
    public static void configure(LoggerConfig config) {
        if (initialized) {
            throw new IllegalStateException("LoggerFactory already initialized");
        }

        defaultLevel = config.getDefaultLevel();
        defaultLoggerType = config.getDefaultLoggerType();

        // AsyncLogger 설정
        AsyncLogger.setEnableColors(config.isEnableColors());
        AsyncLogger.setEnableTimestamp(config.isEnableTimestamp());
        AsyncLogger.setMaxQueueSize(config.getMaxQueueSize());

        // SimpleLogger 설정
        SimpleLogger.setEnableColors(config.isEnableColors());
        SimpleLogger.setEnableTimestamp(config.isEnableTimestamp());
        SimpleLogger.setEnableThreadName(config.isEnableThreadName());

        initialized = true;
    }

    /**
     * 개발용 설정 (Simple Logger)
     */
    public static void configureForDevelopment() {
        LoggerConfig config = new LoggerConfig()
                .setDefaultLevel(LogLevel.DEBUG)
                .setDefaultLoggerType(LoggerType.SIMPLE)
                .setEnableColors(true)
                .setEnableTimestamp(true)
                .setEnableThreadName(true)
                .setMaxQueueSize(1000);

        configure(config);
    }

    /**
     * 프로덕션용 설정 (Async Logger)
     */
    public static void configureForProduction() {
        LoggerConfig config = new LoggerConfig()
                .setDefaultLevel(LogLevel.INFO)
                .setDefaultLoggerType(LoggerType.ASYNC)
                .setEnableColors(false)
                .setEnableTimestamp(true)
                .setEnableThreadName(false)
                .setMaxQueueSize(10000);

        configure(config);
    }

    /**
     * 테스트용 설정 (Simple Logger + 색상)
     */
    public static void configureForTesting() {
        LoggerConfig config = new LoggerConfig()
                .setDefaultLevel(LogLevel.DEBUG)
                .setDefaultLoggerType(LoggerType.SIMPLE)
                .setEnableColors(true)
                .setEnableTimestamp(false)
                .setEnableThreadName(false);

        configure(config);
    }

    /**
     * 모든 Logger 종료
     */
    public static void shutdown() {
        synchronized (asyncLoggers) {
            for (AsyncLogger logger : asyncLoggers) {
                logger.shutdown();
            }
            asyncLoggers.clear();
        }
        loggerCache.clear();
    }

    /**
     * 통계 정보
     */
    public static LoggerStats getStats() {
        int simpleLoggers = 0;
        int asyncLoggers = 0;

        for (String key : loggerCache.keySet()) {
            if (key.endsWith(":SIMPLE")) {
                simpleLoggers++;
            } else if (key.endsWith(":ASYNC")) {
                asyncLoggers++;
            }
        }

        return new LoggerStats(
                loggerCache.size(),
                asyncLoggers,
                simpleLoggers,
                defaultLevel,
                defaultLoggerType,
                initialized
        );
    }

    /**
     * 모든 Logger 이름 반환
     */
    public static java.util.Set<String> getLoggerNames() {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String key : loggerCache.keySet()) {
            names.add(key.split(":")[0]); // 타입 부분 제거
        }
        return names;
    }

    /**
     * Logger 설정 클래스
     */
    public static class LoggerConfig {
        private LogLevel defaultLevel = LogLevel.INFO;
        private LoggerType defaultLoggerType = LoggerType.ASYNC;
        private boolean enableColors = true;
        private boolean enableTimestamp = true;
        private boolean enableThreadName = false;
        private int maxQueueSize = 10000;

        public LogLevel getDefaultLevel() { return defaultLevel; }
        public LoggerConfig setDefaultLevel(LogLevel defaultLevel) {
            this.defaultLevel = defaultLevel; return this;
        }

        public LoggerType getDefaultLoggerType() { return defaultLoggerType; }
        public LoggerConfig setDefaultLoggerType(LoggerType defaultLoggerType) {
            this.defaultLoggerType = defaultLoggerType; return this;
        }

        public boolean isEnableColors() { return enableColors; }
        public LoggerConfig setEnableColors(boolean enableColors) {
            this.enableColors = enableColors; return this;
        }

        public boolean isEnableTimestamp() { return enableTimestamp; }
        public LoggerConfig setEnableTimestamp(boolean enableTimestamp) {
            this.enableTimestamp = enableTimestamp; return this;
        }

        public boolean isEnableThreadName() { return enableThreadName; }
        public LoggerConfig setEnableThreadName(boolean enableThreadName) {
            this.enableThreadName = enableThreadName; return this;
        }

        public int getMaxQueueSize() { return maxQueueSize; }
        public LoggerConfig setMaxQueueSize(int maxQueueSize) {
            this.maxQueueSize = maxQueueSize; return this;
        }
    }

    /**
     * Logger 통계 정보
     */
    public static class LoggerStats {
        private final int totalLoggers;
        private final int asyncLoggers;
        private final int simpleLoggers;
        private final LogLevel defaultLevel;
        private final LoggerType defaultLoggerType;
        private final boolean initialized;

        public LoggerStats(int totalLoggers, int asyncLoggers, int simpleLoggers,
                           LogLevel defaultLevel, LoggerType defaultLoggerType, boolean initialized) {
            this.totalLoggers = totalLoggers;
            this.asyncLoggers = asyncLoggers;
            this.simpleLoggers = simpleLoggers;
            this.defaultLevel = defaultLevel;
            this.defaultLoggerType = defaultLoggerType;
            this.initialized = initialized;
        }

        public int getTotalLoggers() { return totalLoggers; }
        public int getAsyncLoggers() { return asyncLoggers; }
        public int getSimpleLoggers() { return simpleLoggers; }
        public LogLevel getDefaultLevel() { return defaultLevel; }
        public LoggerType getDefaultLoggerType() { return defaultLoggerType; }
        public boolean isInitialized() { return initialized; }

        @Override
        public String toString() {
            return String.format(
                    "LoggerStats{total=%d, async=%d, simple=%d, level=%s, type=%s, initialized=%s}",
                    totalLoggers, asyncLoggers, simpleLoggers, defaultLevel, defaultLoggerType, initialized
            );
        }
    }
}