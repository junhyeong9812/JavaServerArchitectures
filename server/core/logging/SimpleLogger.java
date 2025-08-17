package server.core.logging;

// 날짜/시간 처리를 위한 클래스들
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 동기 Simple Logger 구현체
 * 즉시 System.out.println으로 출력 (비동기 없음)
 *
 * 특징:
 * - 메시지를 받는 즉시 동기적으로 콘솔에 출력
 * - 별도 스레드나 큐 사용하지 않음
 * - 메모리 사용량 최소화
 * - 단순하고 안정적인 로깅
 */
public class SimpleLogger implements Logger {

    // 날짜/시간 포맷 상수
    // "yyyy-MM-dd HH:mm:ss.SSS" 형식 (밀리초까지 표시)
    // 예: "2024-01-15 14:30:25.123"
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // Logger 인스턴스별 설정
    private final String name;              // Logger 이름 (불변)
    private volatile LogLevel currentLevel; // 현재 로그 레벨 (변경 가능)

    // 전역 설정 (모든 SimpleLogger 인스턴스가 공유)
    // volatile: 멀티스레드 환경에서 변수 값의 가시성 보장
    private static volatile boolean enableColors = true;      // 색상 출력 여부
    private static volatile boolean enableTimestamp = true;   // 타임스탬프 출력 여부
    private static volatile boolean enableThreadName = false; // 스레드명 출력 여부

    // 생성자
    public SimpleLogger(String name, LogLevel level) {
        this.name = name;
        this.currentLevel = level;
    }

    /**
     * 즉시 동기 출력
     * 메시지를 받는 즉시 포맷팅하여 콘솔에 출력
     */
    private void log(LogLevel level, String message, Throwable throwable) {
        // 현재 설정된 로그 레벨보다 낮은 레벨이면 출력하지 않음
        if (!isLevelEnabled(level)) {
            return;
        }

        // 메시지 포맷팅 (타임스탬프, 레벨, 이름 등 추가)
        String formattedMessage = formatMessage(level, message);

        // 즉시 출력 (동기) - 별도 스레드나 큐 사용 안함
        if (level == LogLevel.ERROR) {
            // ERROR 레벨은 stderr로 출력
            // System.err: 표준 에러 스트림 (보통 빨간색으로 표시)
            System.err.println(formattedMessage);
        } else {
            // 나머지 레벨은 stdout으로 출력
            // System.out: 표준 출력 스트림
            System.out.println(formattedMessage);
        }

        // 예외가 있으면 스택 트레이스 출력
        if (throwable != null) {
            // printStackTrace(): 예외의 전체 스택 트레이스를 stderr에 출력
            throwable.printStackTrace();
        }
    }

    /**
     * 메시지 포맷팅
     * 타임스탬프, 레벨, 스레드명, Logger 이름 등을 추가하여 완전한 로그 메시지 생성
     */
    private String formatMessage(LogLevel level, String message) {
        // StringBuilder: 효율적인 문자열 연결을 위한 클래스
        // String 연결보다 메모리 효율적 (String은 불변이라 연결할 때마다 새 객체 생성)
        StringBuilder sb = new StringBuilder();

        // 1. 색상 코드 추가 (터미널에서 색상 표시용)
        String colorCode = getColorCode(level);
        String resetCode = enableColors ? "\u001B[0m" : "";  // 색상 리셋 코드

        if (enableColors && colorCode != null) {
            sb.append(colorCode);  // 색상 시작 코드 추가
        }

        // 2. 타임스탬프 추가
        if (enableTimestamp) {
            // LocalDateTime.now(): 현재 날짜/시간
            // format(): 지정된 형식으로 날짜/시간을 문자열로 변환
            sb.append("[").append(LocalDateTime.now().format(TIME_FORMAT)).append("] ");
        }

        // 3. 로그 레벨 추가 (고정 길이로 정렬)
        // String.format("%-5s", ...): 왼쪽 정렬, 최소 5자리 (부족하면 공백 추가)
        // 예: "INFO ", "DEBUG", "WARN ", "ERROR"
        sb.append(String.format("%-5s", level.getName())).append(" ");

        // 4. 스레드명 추가 (옵션)
        if (enableThreadName) {
            // Thread.currentThread().getName(): 현재 스레드의 이름
            String threadName = Thread.currentThread().getName();

            // 스레드명이 15자를 넘으면 자르기 (일정한 포맷 유지)
            // substring(): 문자열의 일부분 추출
            sb.append(String.format("[%-15s] ",
                    threadName.length() > 15 ? threadName.substring(0, 15) : threadName));
        }

        // 5. Logger 이름 추가 (간략화)
        String shortName = getShortName(name);
        // 20자리로 고정하여 정렬 맞춤
        sb.append(String.format("%-20s", shortName)).append(" - ");

        // 6. 실제 메시지 추가
        sb.append(message);

        // 7. 색상 리셋 코드 추가
        if (enableColors && colorCode != null) {
            sb.append(resetCode);
        }

        // 완성된 문자열 반환
        return sb.toString();
    }

    /**
     * 로그 레벨별 색상 코드
     * ANSI 색상 코드를 사용하여 터미널에서 색상 표시
     */
    private String getColorCode(LogLevel level) {
        // 색상이 비활성화되어 있으면 null 반환
        if (!enableColors) return null;

        // 각 로그 레벨에 따른 ANSI 색상 코드 반환
        switch (level) {
            case DEBUG: return "\u001B[36m"; // Cyan (청록색) - 디버그 정보
            case INFO:  return "\u001B[32m"; // Green (녹색) - 정상 정보
            case WARN:  return "\u001B[33m"; // Yellow (노란색) - 경고
            case ERROR: return "\u001B[31m"; // Red (빨간색) - 오류
            default:    return null;         // 기본색 (변경 없음)
        }
    }

    /**
     * Logger 이름 단축
     * 긴 패키지명을 줄여서 읽기 쉽게 만듦
     */
    private String getShortName(String fullName) {
        // 이름이 20자 이하면 그대로 반환
        if (fullName.length() <= 20) return fullName;

        // 패키지명을 '.'으로 분리
        // split("\\."): 정규표현식으로 문자열 분리 (\\.는 리터럴 점)
        String[] parts = fullName.split("\\.");

        // 분리된 부분이 1개 이하면 뒤에서 20자만 잘라내기
        if (parts.length <= 1) {
            return fullName.substring(fullName.length() - 20);
        }

        // 패키지명 축약: com.example.service.UserService -> c.e.s.UserService
        StringBuilder sb = new StringBuilder();

        // 마지막 부분(클래스명) 제외하고 첫 글자만 사용
        for (int i = 0; i < parts.length - 1; i++) {
            // charAt(0): 문자열의 첫 번째 문자
            sb.append(parts[i].charAt(0)).append(".");
        }

        // 마지막 부분(클래스명)은 전체 사용
        sb.append(parts[parts.length - 1]);

        return sb.toString();
    }

    /**
     * 문자열 포맷팅 (SLF4J 스타일)
     * {} 플레이스홀더를 실제 값으로 치환
     */
    private String formatMessageWithArgs(String format, Object... args) {
        // 인수가 없으면 원본 포맷 문자열 그대로 반환
        if (args == null || args.length == 0) {
            return format;
        }

        String result = format;

        // 각 인수에 대해 순서대로 {} 플레이스홀더를 치환
        for (Object arg : args) {
            // replaceFirst("\\{\\}", ...): 첫 번째 {} 패턴을 치환
            // \\{\\}: 정규표현식에서 리터럴 {와 }를 의미
            // String.valueOf(): 객체를 문자열로 변환 (null이면 "null" 반환)
            result = result.replaceFirst("\\{\\}", String.valueOf(arg));
        }

        return result;
    }

    // === Logger 인터페이스 구현 ===
    // Logger 인터페이스에서 정의된 모든 메서드들을 구현

    @Override
    public void debug(String message) {
        // 단순 디버그 메시지 출력
        log(LogLevel.DEBUG, message, null);
    }

    @Override
    public void debug(String format, Object... args) {
        // 성능 최적화: 레벨이 활성화된 경우에만 포맷팅 수행
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, formatMessageWithArgs(format, args), null);
        }
    }

    @Override
    public void debug(String message, Throwable throwable) {
        // 예외와 함께 디버그 메시지 출력
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
    // 로그 출력 전에 레벨을 확인하여 불필요한 처리 방지

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

    // 실제 레벨 확인 로직
    private boolean isLevelEnabled(LogLevel level) {
        // 현재 설정된 레벨보다 높거나 같은 레벨만 출력
        // 예: 현재 레벨이 INFO(1)이면 DEBUG(0)는 출력 안됨, WARN(2)는 출력됨
        return currentLevel.getLevel() <= level.getLevel();
    }

    // === 설정 ===

    @Override
    public void setLevel(LogLevel level) {
        // volatile 필드에 새 레벨 설정
        // volatile: 변경사항이 즉시 다른 스레드에게 보이도록 함
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
    // 모든 SimpleLogger 인스턴스에 영향을 주는 설정들

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
     * 스레드명 출력 여부 설정
     */
    public static void setEnableThreadName(boolean enable) {
        enableThreadName = enable;
    }

    // Getter 메서드들 (현재 설정 확인용)
    public static boolean isEnableColors() {
        return enableColors;
    }

    public static boolean isEnableTimestamp() {
        return enableTimestamp;
    }

    public static boolean isEnableThreadName() {
        return enableThreadName;
    }

    /**
     * 객체의 문자열 표현
     * 디버깅과 로깅에 유용
     */
    @Override
    public String toString() {
        // "SimpleLogger{name='com.example.Service', level=INFO}" 형태로 반환
        return String.format("SimpleLogger{name='%s', level=%s}", name, currentLevel);
    }
}