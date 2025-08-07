package src.main.java.jakarta.servlet;

// Jakarta Servlet API - FilterRegistration Interface
// 실제 프로젝트에서는 src/main/java/jakarta/servlet/FilterRegistration.java 위치에 배치

import java.util.Collection;
import java.util.EnumSet;

/**
 * 필터 등록 정보를 관리하는 인터페이스입니다.
 *
 * FilterRegistration은 웹 애플리케이션에 등록된 필터의 설정 정보를
 * 제공하고 동적으로 수정할 수 있게 해줍니다.
 *
 * 주요 기능:
 * - URL 패턴 매핑 관리
 * - 서블릿 이름 매핑 관리
 * - 디스패처 타입 설정
 * - 초기화 파라미터 설정
 *
 * @author JavaServerArchitectures Project
 * @version 1.0
 * @since Servlet 3.0
 * @see ServletContext#addFilter(String, String)
 * @see ServletContext#getFilterRegistration(String)
 */
public interface FilterRegistration extends Registration {

    /**
     * 필터에 URL 패턴 매핑을 추가합니다.
     *
     * 지정된 URL 패턴들에 대해 필터가 실행되도록 설정합니다.
     * 기본적으로 REQUEST 디스패처 타입에만 적용됩니다.
     *
     * 예시:
     * <pre>
     * {@code
     * FilterRegistration.Dynamic filter =
     *     context.addFilter("authFilter", AuthenticationFilter.class);
     *
     * filter.addMappingForUrlPatterns(
     *     null,           // 디스패처 타입 (null = 기본값 REQUEST)
     *     false,          // 기존 매핑 뒤에 추가
     *     "/admin/*",     // 관리자 페이지
     *     "/api/secure/*" // 보안 API
     * );
     * }
     * </pre>
     *
     * @param dispatcherTypes 적용할 디스패처 타입들 (null이면 REQUEST만)
     * @param isMatchAfter true면 기존 매핑 뒤에, false면 앞에 추가
     * @param urlPatterns 적용할 URL 패턴들
     * @throws IllegalArgumentException URL 패턴이 유효하지 않은 경우
     * @throws IllegalStateException ServletContext가 초기화 완료된 경우
     */
    void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes,
                                  boolean isMatchAfter, String... urlPatterns);

    /**
     * 특정 서블릿들에 대해 필터 매핑을 추가합니다.
     *
     * URL 패턴 대신 서블릿 이름을 사용하여 필터를 적용합니다.
     * 특정 서블릿에만 필터를 적용하고 싶을 때 유용합니다.
     *
     * 예시:
     * <pre>
     * {@code
     * FilterRegistration.Dynamic filter =
     *     context.addFilter("loggingFilter", RequestLoggingFilter.class);
     *
     * filter.addMappingForServletNames(
     *     EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD),
     *     true,                    // 기존 매핑 뒤에 추가
     *     "apiServlet",           // API 서블릿
     *     "uploadServlet"         // 업로드 서블릿
     * );
     * }
     * </pre>
     *
     * @param dispatcherTypes 적용할 디스패처 타입들 (null이면 REQUEST만)
     * @param isMatchAfter true면 기존 매핑 뒤에, false면 앞에 추가
     * @param servletNames 적용할 서블릿 이름들
     * @throws IllegalArgumentException 서블릿 이름이 유효하지 않은 경우
     * @throws IllegalStateException ServletContext가 초기화 완료된 경우
     */
    void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes,
                                   boolean isMatchAfter, String... servletNames);

    /**
     * 필터에 매핑된 모든 URL 패턴을 반환합니다.
     *
     * web.xml에서 설정되거나 addMappingForUrlPatterns()로 추가된
     * 모든 URL 패턴을 포함합니다.
     *
     * @return URL 패턴들의 Collection (매핑이 없으면 빈 Collection)
     */
    Collection<String> getUrlPatternMappings();

    /**
     * 필터에 매핑된 모든 서블릿 이름을 반환합니다.
     *
     * web.xml에서 설정되거나 addMappingForServletNames()로 추가된
     * 모든 서블릿 이름을 포함합니다.
     *
     * @return 서블릿 이름들의 Collection (매핑이 없으면 빈 Collection)
     */
    Collection<String> getServletNameMappings();

    /**
     * 동적 필터 등록을 위한 인터페이스입니다.
     *
     * ServletContext.addFilter()가 반환하는 인터페이스로,
     * 필터를 동적으로 등록하고 설정할 수 있는 메소드들을 제공합니다.
     *
     * @since Servlet 3.0
     */
    interface Dynamic extends FilterRegistration, Registration.Dynamic {
        // 필터 특화 동적 설정 메소드들이 여기에 추가될 수 있습니다.
        // 현재는 기본 Registration.Dynamic 기능만 상속합니다.
    }
}

/**
 * 필터의 기본 인터페이스입니다.
 *
 * 모든 필터가 구현해야 하는 기본 메소드들을 정의합니다.
 */
interface Filter {

    /**
     * 필터를 초기화합니다.
     *
     * @param filterConfig 필터 설정 정보
     * @throws ServletException 초기화 실패 시
     */
    default void init(FilterConfig filterConfig) throws ServletException {}

    /**
     * 필터링 로직을 수행합니다.
     *
     * @param request 요청 객체
     * @param response 응답 객체
     * @param chain 필터 체인
     * @throws java.io.IOException I/O 오류 시
     * @throws ServletException 필터 처리 오류 시
     */
    void doFilter(ServletRequest request, ServletResponse response,
                  FilterChain chain) throws java.io.IOException, ServletException;

    /**
     * 필터를 종료합니다.
     */
    default void destroy() {}
}

/**
 * 필터 설정 정보를 제공하는 인터페이스입니다.
 */
interface FilterConfig {

    /**
     * 필터 이름을 반환합니다.
     *
     * @return 필터 이름
     */
    String getFilterName();

    /**
     * ServletContext를 반환합니다.
     *
     * @return ServletContext 객체
     */
    ServletContext getServletContext();

    /**
     * 초기화 파라미터를 반환합니다.
     *
     * @param name 파라미터 이름
     * @return 파라미터 값
     */
    String getInitParameter(String name);

    /**
     * 모든 초기화 파라미터 이름을 반환합니다.
     *
     * @return 파라미터 이름들의 Enumeration
     */
    java.util.Enumeration<String> getInitParameterNames();
}

/**
 * 필터 체인을 나타내는 인터페이스입니다.
 */
interface FilterChain {

    /**
     * 체인의 다음 필터나 서블릿을 호출합니다.
     *
     * @param request 요청 객체
     * @param response 응답 객체
     * @throws java.io.IOException I/O 오류 시
     * @throws ServletException 처리 오류 시
     */
    void doFilter(ServletRequest request, ServletResponse response)
            throws java.io.IOException, ServletException;
}