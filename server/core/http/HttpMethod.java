package server.core.http;

/**
 * HTTP 메서드 열거형
 * RESTful API의 모든 표준 메서드를 지원
 */
// enum: 상수들의 집합을 정의하는 특별한 클래스 타입
// HTTP 메서드는 정해진 몇 개의 값만 가질 수 있으므로 enum이 적합
public enum HttpMethod {
    // 각 HTTP 메서드를 enum 상수로 정의
    // 괄호 안의 문자열은 생성자 매개변수로 전달됨

    // GET: 리소스 조회 (읽기 전용)
    GET("GET"),

    // POST: 새로운 리소스 생성, 데이터 처리
    POST("POST"),

    // PUT: 리소스 전체 수정 또는 생성
    PUT("PUT"),

    // DELETE: 리소스 삭제
    DELETE("DELETE"),

    // HEAD: GET과 동일하지만 헤더만 반환 (본문 없음)
    HEAD("HEAD"),

    // OPTIONS: 서버가 지원하는 메서드 확인 (CORS에서 주로 사용)
    OPTIONS("OPTIONS"),

    // PATCH: 리소스 부분 수정
    PATCH("PATCH"),

    // TRACE: 요청 경로 추적 (디버깅용, 보안상 잘 사용 안함)
    TRACE("TRACE"),

    // CONNECT: 프록시 서버와의 터널링 연결 설정
    CONNECT("CONNECT");

    // 실제 HTTP 메서드 문자열을 저장하는 필드
    // final: 한번 초기화되면 변경 불가능
    // private: 외부에서 직접 접근 불가능
    private final String method;

    // enum의 생성자
    // enum 생성자는 항상 private (명시하지 않아도 private)
    // 외부에서 new HttpMethod() 형태로 생성할 수 없음
    HttpMethod(String method) {
        // 전달받은 문자열을 method 필드에 저장
        this.method = method;
    }

    // method 필드의 getter 메서드
    // HTTP 메서드의 문자열 값을 반환
    public String getMethod() {
        return method;
    }

    /**
     * 문자열로부터 HttpMethod를 파싱
     */
    public static HttpMethod fromString(String method) {
        // method가 null이거나 공백으로만 이루어져 있는지 확인
        // trim(): 문자열 앞뒤 공백 제거
        // isEmpty(): 빈 문자열인지 확인
        if (method == null || method.trim().isEmpty()) {
            // IllegalArgumentException: 잘못된 매개변수가 전달되었을 때 발생시키는 예외
            throw new IllegalArgumentException("HTTP method cannot be null or empty");
        }

        // 입력받은 메서드 문자열을 대문자로 변환
        // HTTP 메서드는 대소문자를 구분하지만, 일반적으로 대문자로 사용
        // toUpperCase(): 문자열을 모두 대문자로 변환
        String upperMethod = method.trim().toUpperCase();

        // 모든 enum 상수들을 순회하며 일치하는 것을 찾기
        // values(): enum의 모든 상수들을 배열로 반환하는 정적 메서드 (자동 생성)
        for (HttpMethod httpMethod : values()) {
            // 현재 enum 상수의 method 값과 입력값이 일치하는지 확인
            // equals(): 문자열 내용 비교 (==는 참조 비교이므로 사용하면 안됨)
            if (httpMethod.method.equals(upperMethod)) {
                // 일치하는 enum 상수 반환
                return httpMethod;
            }
        }

        // 일치하는 HTTP 메서드가 없으면 예외 발생
        throw new IllegalArgumentException("Unsupported HTTP method: " + method);
    }

    /**
     * 안전한 메서드인지 확인 (RFC 7231)
     * 안전한 메서드는 서버 상태를 변경하지 않음
     */
    public boolean isSafe() {
        // this: 현재 enum 인스턴스 (현재 HTTP 메서드)
        // ==: enum에서는 참조 비교로 동등성 확인 (같은 enum 상수인지 확인)
        // ||: 논리합 연산자 (OR) - 하나라도 true면 전체가 true

        // GET, HEAD, OPTIONS, TRACE는 서버 상태를 변경하지 않는 안전한 메서드
        return this == GET || this == HEAD || this == OPTIONS || this == TRACE;
    }

    /**
     * 멱등성을 가진 메서드인지 확인 (RFC 7231)
     * 멱등성 메서드는 여러 번 실행해도 같은 결과
     */
    public boolean isIdempotent() {
        // 멱등성: 동일한 요청을 여러 번 수행해도 결과가 동일함
        // GET: 조회는 몇 번 해도 같은 결과
        // HEAD: GET과 동일하므로 멱등성
        // PUT: 전체 리소스 교체는 몇 번 해도 같은 상태
        // DELETE: 삭제는 이미 삭제된 것을 다시 삭제해도 결과 동일
        // OPTIONS: 메서드 확인은 몇 번 해도 같은 결과
        // TRACE: 추적은 몇 번 해도 같은 결과
        return this == GET || this == HEAD || this == PUT || this == DELETE ||
                this == OPTIONS || this == TRACE;
    }

    /**
     * 요청 본문을 가질 수 있는 메서드인지 확인
     */
    public boolean canHaveBody() {
        // POST: 새 데이터 생성/처리를 위해 본문 필요
        // PUT: 리소스 전체 교체를 위해 본문 필요
        // PATCH: 부분 수정을 위해 본문 필요
        // GET, DELETE 등은 일반적으로 본문을 사용하지 않음
        return this == POST || this == PUT || this == PATCH;
    }

    /**
     * enum의 문자열 표현을 반환
     * Object 클래스의 toString() 메서드를 오버라이드
     */
    @Override  // 어노테이션: 부모 클래스의 메서드를 재정의함을 명시
    public String toString() {
        // HTTP 메서드 문자열 반환 (예: "GET", "POST")
        return method;
    }
}