package jakarta.servlet; // 패키지 선언 - Jakarta EE 서블릿 API 패키지, 필터 설정 관련 인터페이스가 포함

import java.util.Enumeration; // import 선언 - 열거형 인터페이스, 컬렉션 요소들을 순차적으로 접근하기 위한 레거시 인터페이스

/**
 * 필터의 초기화 설정 정보를 제공하는 인터페이스입니다.
 *
 * FilterConfig는 웹 컨테이너가 필터를 초기화할 때 전달하는 설정 객체로,
 * 필터가 실행에 필요한 설정 정보와 컨텍스트에 접근할 수 있게 해줍니다.
 *
 * 주요 제공 정보:
 * - 필터 이름
 * - 초기화 매개변수
 * - 서블릿 컨텍스트 참조
 *
 * 사용 시점:
 * - Filter.init(FilterConfig) 메서드 호출 시 전달됨
 * - 필터 초기화 단계에서만 사용됨
 * - 필터 라이프사이클 동안 참조 유지 가능
 */
public interface FilterConfig { // public interface 선언 - 모든 클래스에서 접근 가능한 필터 설정 인터페이스

    /**
     * 필터의 이름을 반환합니다.
     *
     * 이 이름은 web.xml이나 어노테이션에서 정의된 필터 이름입니다.
     * 디버깅이나 로깅 목적으로 사용할 수 있습니다.
     *
     * 예시:
     * - web.xml: {@code <filter-name>AuthenticationFilter</filter-name>}
     * - 어노테이션: {@code @WebFilter(filterName = "AuthenticationFilter")}
     *
     * @return 필터의 이름, null이 될 수 없음
     */
    String getFilterName(); // abstract 메서드 - String 반환형, 필터 이름 조회, interface의 메서드는 기본적으로 public abstract

    /**
     * 이 필터가 속한 서블릿 컨텍스트를 반환합니다.
     *
     * ServletContext를 통해 다음과 같은 작업이 가능합니다:
     * - 웹 애플리케이션 전역 설정 접근
     * - 다른 서블릿/필터와 데이터 공유
     * - 리소스 파일 접근
     * - 로깅 기능 사용
     * - 임시 디렉터리 접근
     *
     * 사용 예시:
     * <pre>
     * {@code
     * ServletContext context = filterConfig.getServletContext();
     * String appName = context.getServletContextName();
     * context.log("필터 초기화: " + getFilterName());
     * }
     * </pre>
     *
     * @return 서블릿 컨텍스트 객체, null이 될 수 없음
     */
    ServletContext getServletContext(); // abstract 메서드 - ServletContext 반환형, 웹 애플리케이션 컨텍스트 조회

    /**
     * 지정된 이름의 초기화 매개변수 값을 반환합니다.
     *
     * 초기화 매개변수는 필터별 설정 정보로 다음과 같이 정의됩니다:
     * - web.xml: {@code <init-param>} 태그
     * - 어노테이션: {@code @WebFilter(initParams = ...)}
     *
     * 일반적인 사용 예시:
     * - 데이터베이스 연결 정보
     * - 외부 서비스 URL
     * - 로깅 레벨 설정
     * - 인증 설정
     * - 캐시 설정
     *
     * web.xml 예시:
     * <pre>
     * {@code
     * <filter>
     *     <filter-name>LoggingFilter</filter-name>
     *     <filter-class>com.example.LoggingFilter</filter-class>
     *     <init-param>
     *         <param-name>logLevel</param-name>
     *         <param-value>INFO</param-value>
     *     </init-param>
     *     <init-param>
     *         <param-name>logFile</param-name>
     *         <param-value>/var/log/app.log</param-value>
     *     </init-param>
     * </filter>
     * }
     * </pre>
     *
     * 사용 예시:
     * <pre>
     * {@code
     * String logLevel = filterConfig.getInitParameter("logLevel");
     * String logFile = filterConfig.getInitParameter("logFile");
     * if (logLevel == null) {
     *     logLevel = "WARN"; // 기본값 설정
     * }
     * }
     * </pre>
     *
     * @param name 매개변수 이름 (대소문자 구분)
     * @return 매개변수 값, 존재하지 않으면 null
     * @throws IllegalArgumentException name이 null인 경우 (구현에 따라)
     */
    String getInitParameter(String name); // abstract 메서드 - String 매개변수와 String 반환형, 초기화 매개변수 값 조회

    /**
     * 모든 초기화 매개변수의 이름들을 열거형으로 반환합니다.
     *
     * 이 메서드를 통해 필터에 설정된 모든 매개변수를 순회할 수 있습니다.
     * 설정 검증이나 디버깅 목적으로 유용합니다.
     *
     * 사용 예시:
     * <pre>
     * {@code
     * Enumeration<String> paramNames = filterConfig.getInitParameterNames();
     * while (paramNames.hasMoreElements()) {
     *     String paramName = paramNames.nextElement();
     *     String paramValue = filterConfig.getInitParameter(paramName);
     *     System.out.println(paramName + " = " + paramValue);
     * }
     * }
     * </pre>
     *
     * 다른 사용 예시 (모든 설정을 Map으로 변환):
     * <pre>
     * {@code
     * Map<String, String> allParams = new HashMap<>();
     * Enumeration<String> names = filterConfig.getInitParameterNames();
     * while (names.hasMoreElements()) {
     *     String name = names.nextElement();
     *     allParams.put(name, filterConfig.getInitParameter(name));
     * }
     * }
     * </pre>
     *
     * @return 모든 초기화 매개변수 이름의 열거형
     *         매개변수가 없으면 빈 열거형 반환 (null 아님)
     */
    Enumeration<String> getInitParameterNames(); // abstract 메서드 - Enumeration<String> 반환형, 제네릭 타입으로 String을 담는 열거형
}