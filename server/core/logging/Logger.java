package server.core.logging;

/**
 * Logger 인터페이스
 * SLF4J와 유사한 API 제공
 *
 * 이 인터페이스는 다양한 Logger 구현체들이 공통으로 제공해야 할
 * 메서드들을 정의합니다. (AsyncLogger, SimpleLogger 등)
 */
// interface: 클래스가 구현해야 할 메서드들의 명세를 정의
// 구현 클래스는 이 인터페이스의 모든 메서드를 반드시 구현해야 함
public interface Logger {

    // === DEBUG 레벨 로그 메서드들 ===

    /**
     * 단순 문자열 디버그 메시지 출력
     * 가장 상세한 정보 (개발/디버깅 시에만 사용)
     */
    void debug(String message);

    /**
     * 포맷팅된 디버그 메시지 출력
     * SLF4J 스타일의 {} 플레이스홀더 지원
     * 예: debug("User {} logged in at {}", username, timestamp)
     */
    void debug(String format, Object... args);

    /**
     * 예외와 함께 디버그 메시지 출력
     * 스택 트레이스도 함께 출력됨
     */
    void debug(String message, Throwable throwable);

    // === INFO 레벨 로그 메서드들 ===

    /**
     * 단순 문자열 정보 메시지 출력
     * 일반적인 애플리케이션 상태 정보
     */
    void info(String message);

    /**
     * 포맷팅된 정보 메시지 출력
     * 예: info("Server started on port {}", port)
     */
    void info(String format, Object... args);

    /**
     * 예외와 함께 정보 메시지 출력
     */
    void info(String message, Throwable throwable);

    // === WARN 레벨 로그 메서드들 ===

    /**
     * 단순 문자열 경고 메시지 출력
     * 문제가 될 수 있지만 계속 실행 가능한 상황
     */
    void warn(String message);

    /**
     * 포맷팅된 경고 메시지 출력
     * 예: warn("Connection timeout after {} seconds", timeout)
     */
    void warn(String format, Object... args);

    /**
     * 예외와 함께 경고 메시지 출력
     */
    void warn(String message, Throwable throwable);

    // === ERROR 레벨 로그 메서드들 ===

    /**
     * 단순 문자열 오류 메시지 출력
     * 심각한 문제, 즉시 조치가 필요한 상황
     */
    void error(String message);

    /**
     * 포맷팅된 오류 메시지 출력
     * 예: error("Failed to process request {}", requestId)
     */
    void error(String format, Object... args);

    /**
     * 예외와 함께 오류 메시지 출력
     * 가장 일반적인 에러 로깅 패턴
     */
    void error(String message, Throwable throwable);

    // === 레벨 체크 메서드들 ===
    // 성능 최적화를 위해 로그 출력 전에 레벨을 미리 확인

    /**
     * DEBUG 레벨이 활성화되어 있는지 확인
     * 사용 예: if (logger.isDebugEnabled()) { logger.debug(expensiveOperation()); }
     */
    boolean isDebugEnabled();

    /**
     * INFO 레벨이 활성화되어 있는지 확인
     */
    boolean isInfoEnabled();

    /**
     * WARN 레벨이 활성화되어 있는지 확인
     */
    boolean isWarnEnabled();

    /**
     * ERROR 레벨이 활성화되어 있는지 확인
     */
    boolean isErrorEnabled();

    // === 로그 레벨 관리 메서드들 ===

    /**
     * 현재 Logger의 최소 로그 레벨 설정
     * 설정한 레벨보다 낮은 레벨의 로그는 출력되지 않음
     * 예: setLevel(LogLevel.INFO) -> DEBUG 메시지는 출력 안됨
     */
    void setLevel(LogLevel level);

    /**
     * 현재 Logger의 로그 레벨 반환
     */
    LogLevel getLevel();

    // === Logger 정보 메서드 ===

    /**
     * Logger의 이름 반환
     * 보통 클래스명이나 패키지명을 사용
     * 예: "com.example.UserService", "ROOT"
     */
    String getName();
}