package server.core.mini;

// 컬렉션 관련 클래스들
import java.util.*;
// 멀티스레드 안전한 HashMap
import java.util.concurrent.ConcurrentHashMap;

/**
 * 미니 서블릿 컨텍스트
 * 애플리케이션 설정 및 전역 데이터 관리
 *
 * 역할:
 * - 애플리케이션 수준의 설정과 데이터 저장
 * - 여러 서블릿이 공유하는 정보 관리
 * - 서버 생명주기 정보 제공
 * - 로깅 기능 제공
 */
public class MiniContext {

    // 애플리케이션 속성들을 저장하는 맵
    // ConcurrentHashMap: 멀티스레드 환경에서 안전한 HashMap
    // 여러 서블릿에서 동시에 접근해도 데이터 일관성 보장
    private final Map<String, Object> attributes;

    // 초기화 파라미터들을 저장하는 맵 (web.xml의 context-param과 유사)
    // String-String 매핑으로 설정 정보 저장
    private final Map<String, String> initParameters;

    // 컨텍스트 경로 (예: "/myapp", 루트 컨텍스트는 "")
    // final: 생성 후 변경 불가능
    private final String contextPath;

    // 서버 시작 시간 (밀리초 단위 타임스탬프)
    // 서버 가동 시간 계산에 사용
    private final long startTime;

    // 생성자
    public MiniContext(String contextPath) {
        // 컨텍스트 경로 설정 (null이면 빈 문자열로 처리)
        // 삼항 연산자: 조건 ? 참일때값 : 거짓일때값
        this.contextPath = contextPath != null ? contextPath : "";

        // 스레드 안전한 맵들 초기화
        this.attributes = new ConcurrentHashMap<>();
        this.initParameters = new ConcurrentHashMap<>();

        // 현재 시간을 서버 시작 시간으로 기록
        // System.currentTimeMillis(): 1970년 1월 1일부터의 밀리초
        this.startTime = System.currentTimeMillis();
    }

    // === 속성 관리 ===
    // 애플리케이션 수준에서 공유되는 데이터 관리

    /**
     * 컨텍스트 속성 설정
     * 애플리케이션 전역에서 공유되는 데이터를 저장
     */
    public void setAttribute(String name, Object value) {
        // 속성명이 null인지 검사
        if (name == null) {
            // IllegalArgumentException: 잘못된 매개변수 예외
            throw new IllegalArgumentException("Attribute name cannot be null");
        }

        if (value == null) {
            // 값이 null이면 속성 제거 (remove와 동일한 동작)
            attributes.remove(name);
        } else {
            // 값이 있으면 속성 저장
            // ConcurrentHashMap.put(): 스레드 안전한 키-값 저장
            attributes.put(name, value);
        }
    }

    /**
     * 컨텍스트 속성 가져오기
     */
    public Object getAttribute(String name) {
        // Map.get(): 키에 해당하는 값 반환, 없으면 null
        return attributes.get(name);
    }

    /**
     * 타입 안전한 속성 가져오기
     * 제네릭을 사용하여 형변환 없이 원하는 타입으로 반환
     */
    @SuppressWarnings("unchecked")  // 제네릭 형변환 경고 억제
    public <T> T getAttribute(String name, Class<T> type) {
        Object value = attributes.get(name);

        // 값이 있고 요청한 타입의 인스턴스인지 확인
        // Class.isInstance(): 객체가 해당 타입인지 확인
        if (value != null && type.isInstance(value)) {
            // 안전한 형변환 수행
            return (T) value;
        }
        return null;  // 타입이 맞지 않거나 값이 없으면 null
    }

    /**
     * 속성 제거
     */
    public void removeAttribute(String name) {
        // Map.remove(): 키에 해당하는 항목 제거
        attributes.remove(name);
    }

    /**
     * 모든 속성명 가져오기
     */
    public Set<String> getAttributeNames() {
        // keySet(): 맵의 모든 키들을 Set으로 반환
        // HashSet 생성자: 기존 Collection을 복사하여 새 Set 생성
        // 원본 맵 수정 방지를 위한 복사본 반환
        return new HashSet<>(attributes.keySet());
    }

    // === 초기화 파라미터 관리 ===
    // 애플리케이션 설정 정보 관리 (web.xml의 context-param과 유사)

    /**
     * 초기화 파라미터 설정
     * 애플리케이션 설정값들을 저장 (예: DB 연결 문자열, 환경 설정 등)
     */
    public void setInitParameter(String name, String value) {
        if (name == null) {
            throw new IllegalArgumentException("Parameter name cannot be null");
        }

        if (value == null) {
            // 값이 null이면 파라미터 제거
            initParameters.remove(name);
        } else {
            // 파라미터 저장
            initParameters.put(name, value);
        }
    }

    /**
     * 초기화 파라미터 가져오기
     */
    public String getInitParameter(String name) {
        return initParameters.get(name);
    }

    /**
     * 모든 초기화 파라미터명 가져오기
     */
    public Set<String> getInitParameterNames() {
        return new HashSet<>(initParameters.keySet());
    }

    // === 컨텍스트 정보 ===
    // 서버와 애플리케이션의 기본 정보 제공

    /**
     * 컨텍스트 경로 반환
     * 예: "/myapp", 루트 애플리케이션은 ""
     */
    public String getContextPath() {
        return contextPath;
    }

    /**
     * 서버 시작 시간 반환 (밀리초 타임스탬프)
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * 서버 가동 시간 반환 (밀리초)
     * 현재 시간에서 시작 시간을 뺀 값
     */
    public long getUpTime() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 서버 정보 반환
     * 서버 소프트웨어의 이름과 버전
     */
    public String getServerInfo() {
        return "JavaServerArchitectures/1.0";
    }

    // === 로깅 기능 ===
    // 간단한 로깅 기능 제공 (실제로는 별도 로깅 시스템 사용 권장)

    /**
     * 로그 메시지 출력
     * 현재 시간과 함께 메시지를 표준 출력에 출력
     */
    public void log(String message) {
        // Date: 현재 날짜/시간 객체
        // System.out.println(): 표준 출력 스트림에 한 줄 출력
        System.out.println("[" + new Date() + "] " + message);
    }

    /**
     * 예외와 함께 로그 메시지 출력
     * 에러 메시지는 표준 에러 스트림에 출력
     */
    public void log(String message, Throwable throwable) {
        // System.err: 표준 에러 스트림 (보통 빨간색으로 표시)
        System.err.println("[" + new Date() + "] " + message);

        // printStackTrace(): 예외의 스택 트레이스를 표준 에러에 출력
        // 예외가 발생한 위치와 호출 스택을 보여줌
        throwable.printStackTrace();
    }

    /**
     * 컨텍스트 정보의 문자열 표현
     * 디버깅과 모니터링에 유용
     */
    @Override
    public String toString() {
        // String.format(): printf 스타일의 문자열 포맷팅
        // %s: 문자열, %d: 정수 형식 지정자
        return String.format("MiniContext{contextPath='%s', upTime=%dms, attributes=%d}",
                contextPath,      // 컨텍스트 경로
                getUpTime(),      // 현재 가동 시간
                attributes.size() // 저장된 속성 개수
        );
    }
}