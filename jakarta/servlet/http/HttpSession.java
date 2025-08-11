package jakarta.servlet.http;

import jakarta.servlet.ServletContext;

import java.util.Enumeration;

/**
 * HTTP 세션을 나타내는 인터페이스입니다.
 *
 * HttpSession은 여러 HTTP 요청에 걸쳐 사용자별 정보를 저장하고 관리하는 방법을 제공합니다.
 * 웹 애플리케이션에서 로그인 상태, 장바구니, 사용자 설정 등을 유지하는 데 사용됩니다.
 *
 * 세션의 특징:
 * - 서버 측에서 관리되는 사용자별 저장소
 * - 쿠키나 URL 리라이팅을 통해 세션 ID로 식별
 * - 설정된 시간 동안 비활성 상태가 지속되면 자동 만료
 * - 브라우저가 닫히면 일반적으로 세션 쿠키가 삭제됨
 *
 * 사용 예시:
 * HttpSession session = request.getSession();
 * session.setAttribute("user", userObject);
 * User user = (User) session.getAttribute("user");
 */
public interface HttpSession {

    /**
     * 세션의 고유 식별자를 반환합니다.
     *
     * 세션 ID는 서블릿 컨테이너가 생성하는 고유한 문자열입니다.
     * 이 ID는 쿠키나 URL 파라미터를 통해 클라이언트와 주고받습니다.
     *
     * 보안상 이유로 세션 ID는 예측하기 어려운 형태로 생성되며,
     * 충분한 길이와 복잡성을 가집니다.
     *
     * @return 세션의 고유 식별자
     */
    String getId();

    /**
     * 세션이 생성된 시간을 반환합니다.
     *
     * 세션이 처음 생성된 시각을 밀리초 단위로 반환합니다.
     * 1970년 1월 1일 00:00:00 GMT부터의 경과 시간입니다.
     *
     * 세션의 총 지속 시간을 계산하거나 로깅 목적으로 사용할 수 있습니다.
     *
     * @return 세션 생성 시간 (밀리초)
     */
    long getCreationTime();

    /**
     * 클라이언트가 이 세션과 연관된 요청을 마지막으로 보낸 시간을 반환합니다.
     *
     * 클라이언트로부터 마지막 요청을 받은 시각을 밀리초 단위로 반환합니다.
     * 이 시간은 서블릿 컨테이너가 요청을 받을 때마다 자동으로 업데이트됩니다.
     *
     * 세션 타임아웃 계산의 기준이 되는 시간입니다.
     *
     * @return 마지막 접근 시간 (밀리초)
     */
    long getLastAccessedTime();

    /**
     * 이 세션이 속한 ServletContext를 반환합니다.
     *
     * 세션이 생성된 웹 애플리케이션의 ServletContext를 반환합니다.
     * 여러 웹 애플리케이션이 동일한 서버에서 실행되는 경우,
     * 각 세션은 해당 애플리케이션의 컨텍스트에 속합니다.
     *
     * @return 세션이 속한 ServletContext
     */
    ServletContext getServletContext();

    /**
     * 세션의 최대 비활성 간격을 초 단위로 설정합니다.
     *
     * 클라이언트 요청 간의 최대 허용 시간을 설정합니다.
     * 이 시간이 지나면 서블릿 컨테이너가 자동으로 세션을 무효화합니다.
     *
     * 양수 값: 지정된 초 후에 세션 만료
     * 0 또는 음수 값: 세션이 절대 만료되지 않음 (권장하지 않음)
     *
     * 보안과 서버 리소스 관리를 위해 적절한 타임아웃을 설정하는 것이 중요합니다.
     *
     * 사용 예시:
     * session.setMaxInactiveInterval(30 * 60); // 30분
     *
     * @param interval 최대 비활성 간격 (초)
     */
    void setMaxInactiveInterval(int interval);

    /**
     * 세션의 최대 비활성 간격을 초 단위로 반환합니다.
     *
     * setMaxInactiveInterval()로 설정된 값이나
     * web.xml에서 설정된 기본값을 반환합니다.
     *
     * @return 최대 비활성 간격 (초), 만료되지 않으면 음수
     */
    int getMaxInactiveInterval();

    /**
     * 지정된 이름의 세션 속성을 반환합니다.
     *
     * 세션에 저장된 객체를 이름으로 조회합니다.
     * 세션 속성은 사용자별로 격리되어 저장됩니다.
     *
     * 일반적인 사용 사례:
     * - 로그인한 사용자 정보
     * - 쇼핑몰의 장바구니 내용
     * - 사용자 설정 정보
     * - 마법사 형태의 폼에서 단계별 데이터
     *
     * 사용 예시:
     * User user = (User) session.getAttribute("currentUser");
     * List<Item> cart = (List<Item>) session.getAttribute("shoppingCart");
     *
     * @param name 속성 이름
     * @return 속성 값, 존재하지 않으면 null
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    Object getAttribute(String name);

    /**
     * 모든 세션 속성의 이름을 열거형으로 반환합니다.
     *
     * 현재 세션에 저장된 모든 속성의 이름을 반환합니다.
     * 디버깅이나 세션 내용을 확인할 때 유용합니다.
     *
     * 사용 예시:
     * Enumeration<String> attrNames = session.getAttributeNames();
     * while (attrNames.hasMoreElements()) {
     *     String attrName = attrNames.nextElement();
     *     Object attrValue = session.getAttribute(attrName);
     *     System.out.println(attrName + " = " + attrValue);
     * }
     *
     * @return 속성 이름들의 Enumeration
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    Enumeration<String> getAttributeNames();

    /**
     * 세션에 속성을 설정합니다.
     *
     * 지정된 이름으로 객체를 세션에 저장합니다.
     * 동일한 이름의 속성이 이미 존재하는 경우 새 값으로 대체됩니다.
     *
     * null 값을 전달하면 removeAttribute(name)과 동일한 효과를 갖습니다.
     *
     * 속성으로 저장되는 객체는 직렬화 가능해야 세션 복제나
     * 영속화가 필요한 환경에서 정상 동작합니다.
     *
     * 사용 예시:
     * session.setAttribute("currentUser", user);
     * session.setAttribute("loginTime", new Date());
     * session.setAttribute("preferences", userPreferences);
     *
     * @param name 속성 이름
     * @param value 속성 값
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    void setAttribute(String name, Object value);

    /**
     * 지정된 이름의 세션 속성을 제거합니다.
     *
     * 세션에서 해당 속성을 완전히 제거합니다.
     * 존재하지 않는 속성을 제거하려고 해도 오류가 발생하지 않습니다.
     *
     * 사용 예시:
     * session.removeAttribute("currentUser"); // 로그아웃 시
     * session.removeAttribute("tempData");    // 임시 데이터 정리
     *
     * @param name 제거할 속성 이름
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    void removeAttribute(String name);

    /**
     * 세션을 무효화하고 모든 속성을 제거합니다.
     *
     * 현재 세션을 즉시 무효화하고 모든 세션 속성을 제거합니다.
     * 무효화된 세션은 더 이상 사용할 수 없으며, 이후의 메서드 호출은
     * IllegalStateException을 발생시킵니다.
     *
     * 로그아웃 처리나 보안상 이유로 세션을 강제 종료할 때 사용합니다.
     *
     * 사용 예시:
     * // 로그아웃 처리
     * session.invalidate();
     * response.sendRedirect("/login.jsp");
     *
     * @throws IllegalStateException 세션이 이미 무효화된 경우
     */
    void invalidate();

    /**
     * 세션이 새로 생성되었는지 확인합니다.
     *
     * 이 세션이 현재 요청에서 새로 생성되었는지,
     * 아니면 이전 요청에서 생성된 기존 세션인지를 확인합니다.
     *
     * 새 세션인 경우 true를 반환하며, 이는 다음 상황에서 발생합니다:
     * - 클라이언트가 처음 방문한 경우
     * - 클라이언트가 쿠키를 비활성화한 경우
     * - 이전 세션이 만료된 후 새 요청이 온 경우
     *
     * 이 정보는 초기 설정이나 웰컴 메시지 표시에 활용할 수 있습니다.
     *
     * @return 새로 생성된 세션이면 true, 기존 세션이면 false
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    boolean isNew();
}
