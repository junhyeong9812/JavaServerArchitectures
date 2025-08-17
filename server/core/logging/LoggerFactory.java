package server.core.logging;

// 동시성 처리를 위한 클래스들
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
// 컬렉션 클래스들
import java.util.ArrayList;
import java.util.List;

/**
 * Logger 팩토리
 * SLF4J 패턴과 유사한 API 제공
 *
 * 역할:
 * - Logger 인스턴스 생성 및 캐싱
 * - 전역 설정 관리
 * - 다양한 Logger 타입 지원 (Async, Simple)
 * - 애플리케이션 생명주기 관리
 */
public class LoggerFactory {

    // Logger 인스턴스 캐시
    // ConcurrentHashMap: 멀티스레드 환경에서 안전한 HashMap
    // Key: "LoggerName:LoggerType" 형태의 고유 식별자
    // Value: 생성된 Logger 인스턴스
    private static final ConcurrentMap<String, Logger> loggerCache = new ConcurrentHashMap<>();

    // 기본 설정들
    // volatile: 멀티스레드 환경에서 변수 값의 가시성 보장
    private static volatile LogLevel defaultLevel = LogLevel.INFO;              // 기본 로그 레벨
    private static volatile boolean initialized = false;                       // 초기화 완료 여부
    private static volatile LoggerType defaultLoggerType = LoggerType.ASYNC;    // 기본 Logger 타입

    // 생성된 모든 AsyncLogger 추적 (종료시 정리용)
    // ArrayList: 순서가 있는 리스트, 크기 제한 없음
    private static final List<AsyncLogger> asyncLoggers = new ArrayList<>();

    /**
     * Logger 타입 선택
     * 각 타입별로 다른 특성을 가짐
     */
    public enum LoggerType {
        ASYNC,   // 비동기 Logger (고성능, 별도 스레드 사용)
        SIMPLE   // 동기 Simple Logger (단순, 즉시 출력)
    }

    // static 블록: 클래스가 로드될 때 한번만 실행되는 초기화 코드
    static {
        // JVM 종료시 모든 Logger 정리를 위한 shutdown hook 등록
        // Runtime.getRuntime(): 현재 JVM 런타임 인스턴스
        // addShutdownHook(): JVM 종료 시 실행될 스레드 등록
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown();  // 모든 AsyncLogger 안전하게 종료
        }));
    }

    // === 기본 Logger 생성 메서드들 ===

    /**
     * 클래스별 Logger 생성 (기본 타입)
     * 가장 일반적인 사용 패턴
     */
    public static Logger getLogger(Class<?> clazz) {
        // Class.getName(): 클래스의 완전한 이름 반환
        // 예: com.example.service.UserService
        return getLogger(clazz.getName());
    }

    /**
     * 이름별 Logger 생성 (기본 타입)
     */
    public static Logger getLogger(String name) {
        // 기본 Logger 타입으로 생성
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
     * 실제 Logger 생성 로직이 있는 핵심 메서드
     */
    public static Logger getLogger(String name, LoggerType type) {
        // 캐시 키 생성: "LoggerName:LoggerType" 형태
        // 같은 이름이라도 타입이 다르면 별도 인스턴스 생성
        String cacheKey = name + ":" + type.name();

        // computeIfAbsent(): 키가 없으면 새 값을 계산하여 저장, 있으면 기존 값 반환
        // 스레드 안전한 캐싱 패턴
        return loggerCache.computeIfAbsent(cacheKey, key -> {
            Logger logger;

            // Logger 타입에 따른 인스턴스 생성
            switch (type) {
                case SIMPLE:
                    // 동기 Simple Logger 생성
                    logger = new SimpleLogger(name, defaultLevel);
                    break;
                case ASYNC:
                default:
                    // 비동기 Async Logger 생성
                    AsyncLogger asyncLogger = new AsyncLogger(name, defaultLevel);

                    // AsyncLogger 추적 리스트에 추가 (종료시 정리용)
                    // synchronized: 멀티스레드 환경에서 안전한 리스트 접근
                    synchronized (asyncLoggers) {
                        asyncLoggers.add(asyncLogger);
                    }
                    logger = asyncLogger;
                    break;
            }

            return logger;
        });
    }

    // === 편의 메서드들 ===

    /**
     * Root Logger 생성
     * 최상위 Logger, 보통 애플리케이션 전체 로깅용
     */
    public static Logger getRootLogger() {
        return getLogger("ROOT");
    }

    /**
     * Simple Logger 전용 팩토리 메서드 (클래스)
     */
    public static Logger getSimpleLogger(Class<?> clazz) {
        return getLogger(clazz, LoggerType.SIMPLE);
    }

    /**
     * Simple Logger 전용 팩토리 메서드 (이름)
     */
    public static Logger getSimpleLogger(String name) {
        return getLogger(name, LoggerType.SIMPLE);
    }

    /**
     * Async Logger 전용 팩토리 메서드 (클래스)
     */
    public static Logger getAsyncLogger(Class<?> clazz) {
        return getLogger(clazz, LoggerType.ASYNC);
    }

    /**
     * Async Logger 전용 팩토리 메서드 (이름)
     */
    public static Logger getAsyncLogger(String name) {
        return getLogger(name, LoggerType.ASYNC);
    }

    // === 전역 설정 메서드들 ===

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
     * 기존에 생성된 Logger들의 레벨도 함께 업데이트
     */
    public static void setDefaultLevel(LogLevel level) {
        defaultLevel = level;

        // 기존 생성된 Logger들의 레벨도 업데이트
        // values(): Map의 모든 값들을 Collection으로 반환
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
     * 패턴 매칭으로 여러 Logger에 한번에 적용 가능
     */
    public static void setLevel(String name, LogLevel level) {
        // 1. 정확한 이름 매칭
        // keySet(): Map의 모든 키들을 Set으로 반환
        for (String cacheKey : loggerCache.keySet()) {
            // startsWith(): 문자열이 특정 접두사로 시작하는지 확인
            if (cacheKey.startsWith(name + ":")) {
                loggerCache.get(cacheKey).setLevel(level);
            }
        }

        // 2. 패키지 패턴 매칭으로 하위 Logger들도 설정
        // 예: "com.example" -> "com.example."로 변환하여 하위 패키지 포함
        // endsWith(): 문자열이 특정 접미사로 끝나는지 확인
        String pattern = name.endsWith("*") ? name.substring(0, name.length() - 1) : name + ".";

        for (String cacheKey : loggerCache.keySet()) {
            // split(":"): 캐시 키에서 Logger 이름 부분만 추출
            String loggerName = cacheKey.split(":")[0]; // 타입 부분 제거

            if (loggerName.startsWith(pattern)) {
                loggerCache.get(cacheKey).setLevel(level);
            }
        }
    }

    // === 설정 초기화 메서드들 ===

    /**
     * 전역 설정 초기화
     * 애플리케이션 시작 시 한번만 호출되어야 함
     */
    public static void configure(LoggerConfig config) {
        // 중복 초기화 방지
        if (initialized) {
            throw new IllegalStateException("LoggerFactory already initialized");
        }

        // 기본 설정 적용
        defaultLevel = config.getDefaultLevel();
        defaultLoggerType = config.getDefaultLoggerType();

        // AsyncLogger 전역 설정 적용
        AsyncLogger.setEnableColors(config.isEnableColors());
        AsyncLogger.setEnableTimestamp(config.isEnableTimestamp());
        AsyncLogger.setMaxQueueSize(config.getMaxQueueSize());

        // SimpleLogger 전역 설정 적용
        SimpleLogger.setEnableColors(config.isEnableColors());
        SimpleLogger.setEnableTimestamp(config.isEnableTimestamp());
        SimpleLogger.setEnableThreadName(config.isEnableThreadName());

        // 초기화 완료 표시
        initialized = true;
    }

    /**
     * 개발용 설정 (Simple Logger)
     * 개발 환경에 최적화된 설정
     */
    public static void configureForDevelopment() {
        // 메서드 체이닝으로 설정 구성
        LoggerConfig config = new LoggerConfig()
                .setDefaultLevel(LogLevel.DEBUG)        // 모든 로그 출력
                .setDefaultLoggerType(LoggerType.SIMPLE) // 즉시 출력
                .setEnableColors(true)                   // 색상 활성화
                .setEnableTimestamp(true)                // 시간 표시
                .setEnableThreadName(true)               // 스레드명 표시
                .setMaxQueueSize(1000);                  // 작은 큐 크기

        configure(config);
    }

    /**
     * 프로덕션용 설정 (Async Logger)
     * 운영 환경에 최적화된 설정
     */
    public static void configureForProduction() {
        LoggerConfig config = new LoggerConfig()
                .setDefaultLevel(LogLevel.INFO)          // INFO 이상만 출력
                .setDefaultLoggerType(LoggerType.ASYNC)  // 비동기 처리
                .setEnableColors(false)                  // 색상 비활성화 (파일 출력용)
                .setEnableTimestamp(true)                // 시간 표시
                .setEnableThreadName(false)              // 스레드명 숨김
                .setMaxQueueSize(10000);                 // 큰 큐 크기

        configure(config);
    }

    /**
     * 테스트용 설정 (Simple Logger + 색상)
     * 테스트 환경에 최적화된 설정
     */
    public static void configureForTesting() {
        LoggerConfig config = new LoggerConfig()
                .setDefaultLevel(LogLevel.DEBUG)         // 모든 로그 출력
                .setDefaultLoggerType(LoggerType.SIMPLE) // 즉시 출력
                .setEnableColors(true)                   // 색상 활성화
                .setEnableTimestamp(false)               // 시간 숨김 (테스트 안정성)
                .setEnableThreadName(false);             // 스레드명 숨김

        configure(config);
    }

    // === 생명주기 관리 ===

    /**
     * 모든 Logger 종료
     * 애플리케이션 종료 시 안전한 정리
     */
    public static void shutdown() {
        // AsyncLogger들 안전하게 종료
        synchronized (asyncLoggers) {
            for (AsyncLogger logger : asyncLoggers) {
                logger.shutdown();  // 각 AsyncLogger의 스레드 종료
            }
            asyncLoggers.clear();   // 리스트 정리
        }

        // 캐시 정리
        loggerCache.clear();
    }

    // === 정보 조회 메서드들 ===

    /**
     * 통계 정보 반환
     */
    public static LoggerStats getStats() {
        int simpleLoggers = 0;
        int asyncLoggers = 0;

        // 각 Logger 타입별 개수 집계
        for (String key : loggerCache.keySet()) {
            if (key.endsWith(":SIMPLE")) {
                simpleLoggers++;
            } else if (key.endsWith(":ASYNC")) {
                asyncLoggers++;
            }
        }

        // 통계 객체 생성
        return new LoggerStats(
                loggerCache.size(),  // 전체 Logger 수
                asyncLoggers,        // Async Logger 수
                simpleLoggers,       // Simple Logger 수
                defaultLevel,        // 기본 레벨
                defaultLoggerType,   // 기본 타입
                initialized          // 초기화 여부
        );
    }

    /**
     * 모든 Logger 이름 반환
     */
    public static java.util.Set<String> getLoggerNames() {
        // HashSet: 중복을 허용하지 않는 집합
        java.util.Set<String> names = new java.util.HashSet<>();

        for (String key : loggerCache.keySet()) {
            // "LoggerName:LoggerType"에서 LoggerName 부분만 추출
            names.add(key.split(":")[0]); // 타입 부분 제거
        }

        return names;
    }

    // === 내부 클래스들 ===

    /**
     * Logger 설정 클래스
     * 빌더 패턴으로 구현하여 유연한 설정 가능
     */
    public static class LoggerConfig {
        // 기본값들
        private LogLevel defaultLevel = LogLevel.INFO;              // 기본 로그 레벨
        private LoggerType defaultLoggerType = LoggerType.ASYNC;    // 기본 Logger 타입
        private boolean enableColors = true;                       // 색상 출력 여부
        private boolean enableTimestamp = true;                    // 타임스탬프 출력 여부
        private boolean enableThreadName = false;                  // 스레드명 출력 여부
        private int maxQueueSize = 10000;                          // 최대 큐 크기

        // === Getter 메서드들 ===

        public LogLevel getDefaultLevel() {
            return defaultLevel;
        }

        public LoggerType getDefaultLoggerType() {
            return defaultLoggerType;
        }

        public boolean isEnableColors() {
            return enableColors;
        }

        public boolean isEnableTimestamp() {
            return enableTimestamp;
        }

        public boolean isEnableThreadName() {
            return enableThreadName;
        }

        public int getMaxQueueSize() {
            return maxQueueSize;
        }

        // === Setter 메서드들 (빌더 패턴) ===
        // 모든 setter는 this를 반환하여 메서드 체이닝 지원

        /**
         * 기본 로그 레벨 설정
         */
        public LoggerConfig setDefaultLevel(LogLevel defaultLevel) {
            this.defaultLevel = defaultLevel;
            return this;  // 메서드 체이닝을 위해 자기 자신 반환
        }

        /**
         * 기본 Logger 타입 설정
         */
        public LoggerConfig setDefaultLoggerType(LoggerType defaultLoggerType) {
            this.defaultLoggerType = defaultLoggerType;
            return this;
        }

        /**
         * 색상 출력 여부 설정
         */
        public LoggerConfig setEnableColors(boolean enableColors) {
            this.enableColors = enableColors;
            return this;
        }

        /**
         * 타임스탬프 출력 여부 설정
         */
        public LoggerConfig setEnableTimestamp(boolean enableTimestamp) {
            this.enableTimestamp = enableTimestamp;
            return this;
        }

        /**
         * 스레드명 출력 여부 설정
         */
        public LoggerConfig setEnableThreadName(boolean enableThreadName) {
            this.enableThreadName = enableThreadName;
            return this;
        }

        /**
         * 최대 큐 크기 설정 (AsyncLogger용)
         */
        public LoggerConfig setMaxQueueSize(int maxQueueSize) {
            this.maxQueueSize = maxQueueSize;
            return this;
        }
    }

    /**
     * Logger 통계 정보 클래스
     * 현재 Logger 시스템의 상태를 나타내는 불변 객체
     */
    public static class LoggerStats {
        // final: 생성 후 변경 불가능한 필드들
        private final int totalLoggers;           // 전체 Logger 수
        private final int asyncLoggers;           // Async Logger 수
        private final int simpleLoggers;          // Simple Logger 수
        private final LogLevel defaultLevel;      // 기본 로그 레벨
        private final LoggerType defaultLoggerType; // 기본 Logger 타입
        private final boolean initialized;        // 초기화 완료 여부

        // 생성자 - 모든 필드를 한번에 초기화
        public LoggerStats(int totalLoggers, int asyncLoggers, int simpleLoggers,
                           LogLevel defaultLevel, LoggerType defaultLoggerType, boolean initialized) {
            this.totalLoggers = totalLoggers;
            this.asyncLoggers = asyncLoggers;
            this.simpleLoggers = simpleLoggers;
            this.defaultLevel = defaultLevel;
            this.defaultLoggerType = defaultLoggerType;
            this.initialized = initialized;
        }

        // === Getter 메서드들 ===
        // 모든 필드에 대한 읽기 전용 접근자

        /**
         * 전체 Logger 수 반환
         */
        public int getTotalLoggers() {
            return totalLoggers;
        }

        /**
         * Async Logger 수 반환
         */
        public int getAsyncLoggers() {
            return asyncLoggers;
        }

        /**
         * Simple Logger 수 반환
         */
        public int getSimpleLoggers() {
            return simpleLoggers;
        }

        /**
         * 기본 로그 레벨 반환
         */
        public LogLevel getDefaultLevel() {
            return defaultLevel;
        }

        /**
         * 기본 Logger 타입 반환
         */
        public LoggerType getDefaultLoggerType() {
            return defaultLoggerType;
        }

        /**
         * 초기화 완료 여부 반환
         */
        public boolean isInitialized() {
            return initialized;
        }

        /**
         * 통계 정보의 문자열 표현
         * 디버깅과 모니터링에 유용
         */
        @Override
        public String toString() {
            // String.format(): printf 스타일의 문자열 포맷팅
            // 각 필드의 값을 보기 좋게 정리하여 반환
            return String.format(
                    "LoggerStats{total=%d, async=%d, simple=%d, level=%s, type=%s, initialized=%s}",
                    totalLoggers,      // 전체 Logger 수
                    asyncLoggers,      // Async Logger 수
                    simpleLoggers,     // Simple Logger 수
                    defaultLevel,      // 기본 레벨
                    defaultLoggerType, // 기본 타입
                    initialized        // 초기화 여부
            );
        }
    }
}