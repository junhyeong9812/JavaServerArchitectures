package src.main.java.jakarta.servlet;

// Jakarta Servlet API - ServletRegistration Interface
// 실제 프로젝트에서는 src/main/java/jakarta/servlet/ServletRegistration.java 위치에 배치

import java.util.Collection;
import java.util.Set;

/**
 * 서블릿 등록 정보를 관리하는 인터페이스입니다.
 *
 * ServletRegistration은 웹 애플리케이션에 등록된 서블릿의 설정 정보를
 * 제공하고 동적으로 수정할 수 있게 해줍니다.
 *
 * 주요 기능:
 * - URL 매핑 관리
 * - 초기화 파라미터 설정
 * - 로드 온 스타트업 설정
 * - 멀티파트 설정
 * - 보안 역할 정보
 *
 * @author JavaServerArchitectures Project
 * @version 1.0
 * @since Servlet 3.0
 * @see ServletContext#addServlet(String, String)
 * @see ServletContext#getServletRegistration(String)
 */
public interface ServletRegistration extends Registration {

    /**
     * 서블릿에 URL 패턴 매핑을 추가합니다.
     *
     * 서블릿이 처리할 URL 패턴들을 동적으로 추가할 수 있습니다.
     * 패턴은 서블릿 스펙에 정의된 규칙을 따라야 합니다.
     *
     * URL 패턴 규칙:
     * - 정확한 매치: "/hello", "/api/users"
     * - 경로 매치: "/api/*", "/admin/*"
     * - 확장자 매치: "*.jsp", "*.do"
     * - 기본 서블릿: "/"
     *
     * 예시:
     * <pre>
     * {@code
     * ServletRegistration.Dynamic registration =
     *     context.addServlet("apiServlet", ApiServlet.class);
     *
     * Set<String> mappings = registration.addMapping(
     *     "/api/*",
     *     "/rest/*",
     *     "*.json"
     * );
     *
     * if (mappings.isEmpty()) {
     *     System.out.println("All mappings added successfully");
     * } else {
     *     System.out.println("Conflicting mappings: " + mappings);
     * }
     * }
     * </pre>
     *
     * @param urlPatterns 추가할 URL 패턴들
     * @return 충돌로 인해 추가되지 않은 URL 패턴들의 Set (성공 시 빈 Set)
     * @throws IllegalArgumentException URL 패턴이 유효하지 않은 경우
     * @throws IllegalStateException ServletContext가 초기화 완료된 경우
     */
    Set<String> addMapping(String... urlPatterns);

    /**
     * 서블릿에 매핑된 모든 URL 패턴을 반환합니다.
     *
     * web.xml에서 설정되거나 addMapping()으로 추가된 모든 패턴을 포함합니다.
     *
     * @return URL 패턴들의 Collection (매핑이 없으면 빈 Collection)
     */
    Collection<String> getMappings();

    /**
     * 서블릿의 run-as 역할을 반환합니다.
     *
     * 보안 설정에서 서블릿이 특정 보안 역할로 실행되도록 설정된 경우
     * 그 역할 이름을 반환합니다.
     *
     * @return run-as 역할 이름, 설정되지 않았으면 null
     */
    String getRunAsRole();

    /**
     * 동적 서블릿 등록을 위한 인터페이스입니다.
     *
     * ServletContext.addServlet()이 반환하는 인터페이스로,
     * 서블릿을 동적으로 등록하고 설정할 수 있는 메소드들을 제공합니다.
     *
     * @since Servlet 3.0
     */
    interface Dynamic extends ServletRegistration, Registration.Dynamic {

        /**
         * 서블릿의 로드 온 스타트업 순서를 설정합니다.
         *
         * 양수 값을 설정하면 웹 애플리케이션 시작 시 자동으로 서블릿을 초기화합니다.
         * 작은 값일수록 먼저 초기화됩니다.
         *
         * @param loadOnStartup 로드 순서 (0보다 큰 값), 비활성화하려면 0 이하
         */
        void setLoadOnStartup(int loadOnStartup);

        /**
         * 서블릿이 보안 제약조건을 지원하는지 설정합니다.
         *
         * @param flag 보안 제약조건 지원 여부
         * @return 설정 결과 (성공 시 빈 Set)
         * @throws IllegalStateException ServletContext가 초기화 완료된 경우
         */
        Set<String> setServletSecurity(ServletSecurityElement constraint);

        /**
         * 서블릿의 멀티파트 설정을 지정합니다.
         *
         * 파일 업로드를 처리하는 서블릿의 멀티파트 설정을 구성합니다.
         *
         * 예시:
         * <pre>
         * {@code
         * ServletRegistration.Dynamic registration =
         *     context.addServlet("uploadServlet", FileUploadServlet.class);
         *
         * MultipartConfigElement multipartConfig = new MultipartConfigElement(
         *     "/tmp",           // 임시 파일 저장 위치
         *     5 * 1024 * 1024,  // 파일당 최대 크기 (5MB)
         *     10 * 1024 * 1024, // 요청당 최대 크기 (10MB)
         *     1024 * 1024       // 메모리 임계값 (1MB)
         * );
         *
         * registration.setMultipartConfig(multipartConfig);
         * }
         * </pre>
         *
         * @param multipartConfig 멀티파트 설정
         * @throws IllegalStateException ServletContext가 초기화 완료된 경우
         */
        void setMultipartConfig(MultipartConfigElement multipartConfig);

        /**
         * 서블릿의 run-as 역할을 설정합니다.
         *
         * 보안 컨텍스트에서 서블릿이 특정 역할로 실행되도록 설정합니다.
         *
         * @param roleName 실행 역할 이름
         * @throws IllegalStateException ServletContext가 초기화 완료된 경우
         */
        void setRunAsRole(String roleName);
    }
}

/**
 * 기본 등록 정보를 관리하는 인터페이스입니다.
 *
 * 서블릿과 필터 등록에서 공통으로 사용되는 기본 기능들을 정의합니다.
 */
interface Registration {

    /**
     * 등록된 컴포넌트의 이름을 반환합니다.
     *
     * @return 컴포넌트 이름
     */
    String getName();

    /**
     * 등록된 컴포넌트의 클래스 이름을 반환합니다.
     *
     * @return 클래스 이름
     */
    String getClassName();

    /**
     * 초기화 파라미터를 설정합니다.
     *
     * @param name 파라미터 이름
     * @param value 파라미터 값
     * @return 기존에 같은 이름의 파라미터가 있었으면 false, 없었으면 true
     * @throws IllegalStateException ServletContext가 초기화 완료된 경우
     */
    boolean setInitParameter(String name, String value);

    /**
     * 초기화 파라미터 값을 반환합니다.
     *
     * @param name 파라미터 이름
     * @return 파라미터 값, 없으면 null
     */
    String getInitParameter(String name);

    /**
     * 여러 초기화 파라미터를 한번에 설정합니다.
     *
     * @param initParameters 설정할 파라미터 맵
     * @return 기존에 존재했던 파라미터 이름들의 Set
     * @throws IllegalStateException ServletContext가 초기화 완료된 경우
     */
    Set<String> setInitParameters(java.util.Map<String, String> initParameters);

    /**
     * 모든 초기화 파라미터의 이름과 값을 반환합니다.
     *
     * @return 파라미터 맵
     */
    java.util.Map<String, String> getInitParameters();

    /**
     * 동적 등록을 위한 인터페이스입니다.
     */
    interface Dynamic extends Registration {

        /**
         * 비동기 처리 지원 여부를 설정합니다.
         *
         * @param isAsyncSupported 비동기 지원 여부
         */
        void setAsyncSupported(boolean isAsyncSupported);
    }
}

/**
 * 서블릿 보안 설정을 나타내는 클래스입니다.
 */
class ServletSecurityElement {
    // 구현은 나중에...
}

/**
 * 멀티파트 설정을 나타내는 클래스입니다.
 */
class MultipartConfigElement {
    private String location;
    private long maxFileSize;
    private long maxRequestSize;
    private int fileSizeThreshold;

    public MultipartConfigElement(String location, long maxFileSize,
                                  long maxRequestSize, int fileSizeThreshold) {
        this.location = location;
        this.maxFileSize = maxFileSize;
        this.maxRequestSize = maxRequestSize;
        this.fileSizeThreshold = fileSizeThreshold;
    }

    // getter 메소드들...
    public String getLocation() { return location; }
    public long getMaxFileSize() { return maxFileSize; }
    public long getMaxRequestSize() { return maxRequestSize; }
    public int getFileSizeThreshold() { return fileSizeThreshold; }
}