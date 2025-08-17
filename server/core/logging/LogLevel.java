package server.core.logging;

/**
 * 로그 레벨 정의
 * 로그 메시지의 중요도와 우선순위를 나타내는 열거형
 */
// enum: 고정된 상수들의 집합을 정의하는 특별한 클래스 타입
// 로그 레벨은 정해진 몇 개의 값만 가질 수 있으므로 enum이 적합
public enum LogLevel {

    // 각 로그 레벨을 우선순위 순서대로 정의 (낮은 숫자 = 높은 우선순위)
    // 괄호 안의 값들은 생성자 매개변수로 전달됨

    // 0: 가장 상세한 디버깅 정보 (개발 중에만 사용)
    DEBUG(0, "DEBUG"),

    // 1: 일반적인 정보성 메시지 (애플리케이션 동작 상태)
    INFO(1, "INFO"),

    // 2: 경고 메시지 (문제가 될 수 있지만 계속 실행 가능)
    WARN(2, "WARN"),

    // 3: 오류 메시지 (심각한 문제, 즉시 조치 필요)
    ERROR(3, "ERROR");

    // 로그 레벨의 구성 요소들
    // final: 한번 초기화되면 변경 불가능한 필드
    private final int level;    // 숫자 레벨 (우선순위 비교용)
    private final String name;  // 문자열 이름 (출력용)

    // enum 생성자 (항상 private)
    // 각 enum 상수가 생성될 때 호출됨
    LogLevel(int level, String name) {
        this.level = level;
        this.name = name;
    }

    // 숫자 레벨 반환하는 getter
    // 레벨 비교 시 사용 (낮은 숫자 = 높은 우선순위)
    public int getLevel() {
        return level;
    }

    // 문자열 이름 반환하는 getter
    // 로그 출력 시 표시되는 이름
    public String getName() {
        return name;
    }

    /**
     * 현재 레벨이 target 레벨보다 높거나 같은지 확인
     * 로그 필터링에 사용됨
     */
    public boolean isEnabled(LogLevel target) {
        // 현재 레벨의 숫자가 target 레벨의 숫자보다 크거나 같은지 확인
        // 예: INFO(1).isEnabled(ERROR(3)) -> false (INFO가 ERROR보다 낮은 레벨)
        // 예: ERROR(3).isEnabled(INFO(1)) -> true (ERROR가 INFO보다 높은 레벨)
        return this.level >= target.level;
    }

    /**
     * 객체의 문자열 표현 반환
     * Object 클래스의 toString() 메서드 오버라이드
     */
    @Override  // 어노테이션: 부모 클래스 메서드를 재정의함을 명시
    public String toString() {
        // 로그 레벨의 이름을 반환 (DEBUG, INFO, WARN, ERROR)
        // 로깅이나 디버깅에서 레벨 정보를 확인할 때 사용
        return name;
    }
}