package src.main.java.com.serverarch.common.session;

import src.main.java.jakarta.servlet.ServletContext;
import src.main.java.jakarta.servlet.http.HttpSession;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HttpSession 인터페이스의 구현 클래스입니다.
 *
 * HttpSessionImpl은 실제 세션 데이터와 동작을 구현하며,
 * SessionManager와 연동하여 세션 생명주기를 관리합니다.
 *
 * 주요 기능:
 * - 세션 속성 저장 및 관리
 * - 세션 메타데이터 관리
 * - 스레드 안전한 속성 접근
 * - 세션 무효화 처리
 * - 세션 리스너 지원 (향후 구현)
 *
 * 스레드 안전성:
 * - 속성 맵은 ConcurrentHashMap 사용
 * - 세션 상태는 volatile 필드로 관리
 * - 무효화는 동기화 블록으로 보호
 */
public class HttpSessionImpl implements HttpSession {

    /**
     * 세션 고유 식별자
     * 보안을 위해 암호화된 랜덤 문자열 사용
     */
    private final String sessionId;

    /**
     * 세션이 생성된 시간 (밀리초)
     * System.currentTimeMillis() 값
     */
    private final long creationTime;

    /**
     * 마지막으로 세션에 접근한 시간 (밀리초)
     * 세션 만료 계산에 사용됨
     */
    private volatile long lastAccessedTime;

    /**
     * 세션의 최대 비활성 간격 (초)
     * -1이면 세션이 만료되지 않음
     * 0이면 즉시 만료
     */
    private volatile int maxInactiveInterval;

    /**
     * 세션의 유효성 상태
     * invalidate() 호출 시 false로 변경
     */
    private volatile boolean valid;

    /**
     * 새로운 세션인지 여부
     * 클라이언트가 아직 세션을 인식하지 못한 상태
     */
    private volatile boolean isNew;

    /**
     * 세션 속성들을 저장하는 맵
     * 스레드 안전한 ConcurrentHashMap 사용
     */
    private final Map<String, Object> attributes;

    /**
     * 서블릿 컨텍스트 참조
     * 웹 애플리케이션 전역 정보 접근용
     */
    private final ServletContext servletContext;

    /**
     * 세션 매니저 참조
     * 세션 생명주기 관리 및 설정 변경 알림용
     */
    private final SessionManager sessionManager;

    /**
     * HttpSessionImpl 생성자
     * <p>
     * 새로운 세션을 생성하고 초기 상태로 설정합니다.
     * 생성 시점에 새로운 세션으로 마킹되며, 첫 번째 접근 시 해제됩니다.
     *
     * @param sessionId      세션 고유 식별자
     * @param servletContext 서블릿 컨텍스트
     * @param sessionManager 세션 매니저
     */
    public HttpSessionImpl(String sessionId, ServletContext servletContext, SessionManager sessionManager) {
        // 입력 검증
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("세션 ID가 null이거나 비어있습니다");
        }
        if (servletContext == null) {
            throw new IllegalArgumentException("ServletContext가 null입니다");
        }
        if (sessionManager == null) {
            throw new IllegalArgumentException("SessionManager가 null입니다");
        }

        // 필드 초기화
        this.sessionId = sessionId.trim();
        this.servletContext = servletContext;
        this.sessionManager = sessionManager;

        // 시간 정보 초기화
        this.creationTime = System.currentTimeMillis();
        this.lastAccessedTime = this.creationTime;

        // 기본 설정값으로 초기화
        this.maxInactiveInterval = sessionManager.getDefaultMaxInactiveInterval();

        // 상태 초기화
        this.valid = true;
        this.isNew = true;

        // 속성 맵 초기화 (스레드 안전)
        this.attributes = new ConcurrentHashMap<>();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션의 고유 식별자를 반환합니다.
     * 이 ID는 세션 생성 시 한 번 할당되며 변경되지 않습니다.
     */
    @Override
    public String getId() {
        return sessionId;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션이 생성된 시간을 반환합니다.
     * 이 시간은 세션 생성 시 고정되며 변경되지 않습니다.
     */
    @Override
    public long getCreationTime() {
        checkValid(); // 세션 유효성 검사
        return creationTime;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션에 마지막으로 접근한 시간을 반환합니다.
     * 이 시간은 세션에 접근할 때마다 자동으로 업데이트됩니다.
     */
    @Override
    public long getLastAccessedTime() {
        checkValid(); // 세션 유효성 검사
        return lastAccessedTime;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 이 세션이 속한 서블릿 컨텍스트를 반환합니다.
     */
    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션의 최대 비활성 간격을 설정합니다.
     * 이 값이 변경되면 SessionManager에도 알림을 보냅니다.
     */
    @Override
    public void setMaxInactiveInterval(int interval) {
        checkValid(); // 세션 유효성 검사
        this.maxInactiveInterval = interval;

        // SessionManager에도 업데이트 알림
        sessionManager.setSessionMaxInactiveInterval(sessionId, interval);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션의 최대 비활성 간격을 반환합니다.
     */
    @Override
    public int getMaxInactiveInterval() {
        checkValid(); // 세션 유효성 검사
        return maxInactiveInterval;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 지정된 이름의 속성 값을 반환합니다.
     * 속성에 접근할 때마다 세션 접근 시간이 업데이트됩니다.
     */
    @Override
    public Object getAttribute(String name) {
        checkValid(); // 세션 유효성 검사

        if (name == null) {
            throw new IllegalArgumentException("속성 이름이 null입니다");
        }

        updateLastAccessedTime(); // 접근 시간 업데이트
        return attributes.get(name);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션에 저장된 모든 속성 이름을 반환합니다.
     * 접근할 때마다 세션 접근 시간이 업데이트됩니다.
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        checkValid(); // 세션 유효성 검사
        updateLastAccessedTime(); // 접근 시간 업데이트

        // 현재 시점의 속성 이름들을 복사하여 반환 (동시성 안전)
        return Collections.enumeration(new HashSet<>(attributes.keySet()));
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션에 속성을 설정합니다.
     * null 값을 설정하면 속성이 제거됩니다.
     * 속성 변경 시 세션 접근 시간이 업데이트됩니다.
     */
    @Override
    public void setAttribute(String name, Object value) {
        checkValid(); // 세션 유효성 검사

        if (name == null) {
            throw new IllegalArgumentException("속성 이름이 null입니다");
        }

        updateLastAccessedTime(); // 접근 시간 업데이트

        if (value == null) {
            // null 값 설정은 속성 제거와 동일
            removeAttribute(name);
        } else {
            // 기존 값 확인 (리스너 호출용)
            Object oldValue = attributes.put(name, value);

            // HttpSessionAttributeListener 호출 (향후 구현 가능)
            // if (oldValue == null) {
            //     // attributeAdded 이벤트
            // } else {
            //     // attributeReplaced 이벤트
            // }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 지정된 이름의 속성을 제거합니다.
     * 속성 제거 시 세션 접근 시간이 업데이트됩니다.
     */
    @Override
    public void removeAttribute(String name) {
        checkValid(); // 세션 유효성 검사

        if (name == null) {
            return; // null 이름은 무시
        }

        updateLastAccessedTime(); // 접근 시간 업데이트

        Object removedValue = attributes.remove(name);

        // HttpSessionAttributeListener 호출 (향후 구현 가능)
        // if (removedValue != null) {
        //     // attributeRemoved 이벤트
        // }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션을 무효화합니다.
     * 무효화된 세션은 더 이상 사용할 수 없으며 모든 속성이 제거됩니다.
     * 이 메서드는 동기화되어 있어 스레드 안전합니다.
     */
    @Override
    public synchronized void invalidate() {
        checkValid(); // 세션 유효성 검사

        // 모든 속성 제거 (리스너 호출 포함)
        Set<String> attributeNames = new HashSet<>(attributes.keySet());
        for (String name : attributeNames) {
            removeAttribute(name);
        }

        // 세션 무효화
        this.valid = false;

        // SessionManager에서 제거
        sessionManager.invalidateSession(sessionId);

        // HttpSessionListener 호출 (향후 구현 가능)
        // sessionDestroyed 이벤트
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션이 새로운 세션인지 확인합니다.
     * 클라이언트가 아직 세션 ID를 받지 못했거나 인식하지 못한 상태입니다.
     */
    @Override
    public boolean isNew() {
        checkValid(); // 세션 유효성 검사
        return isNew;
    }

    // ========== 내부 유틸리티 메서드들 ==========

    /**
     * 세션이 유효한지 확인합니다.
     *
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    private void checkValid() {
        if (!valid) {
            throw new IllegalStateException("세션이 이미 무효화되었습니다: " + sessionId);
        }
    }

    /**
     * 마지막 접근 시간을 업데이트합니다.
     * <p>
     * 세션에 접근할 때마다 호출되어 세션 만료 시간을 연장합니다.
     * 또한 새로운 세션 플래그를 해제합니다.
     */
    private void updateLastAccessedTime() {
        this.lastAccessedTime = System.currentTimeMillis();
        this.isNew = false; // 더 이상 새로운 세션이 아님
    }

    // ========== 추가 유틸리티 메서드들 ==========

    /**
     * 세션의 유효성을 반환합니다.
     * <p>
     * SessionManager가 세션 상태를 확인할 때 사용합니다.
     *
     * @return 유효하면 true, 무효화되었으면 false
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * 세션에 저장된 속성 개수를 반환합니다.
     * <p>
     * 모니터링이나 디버깅 목적으로 사용할 수 있습니다.
     *
     * @return 속성 개수
     */
    public int getAttributeCount() {
        checkValid();
        return attributes.size();
    }

    /**
     * 세션이 비어있는지 확인합니다.
     *
     * @return 속성이 없으면 true, 있으면 false
     */
    public boolean isEmpty() {
        checkValid();
        return attributes.isEmpty();
    }

    /**
     * 세션의 모든 속성을 Map으로 반환합니다.
     * <p>
     * 디버깅이나 세션 내용 덤프 시 사용할 수 있습니다.
     * 반환되는 Map은 읽기 전용입니다.
     *
     * @return 읽기 전용 속성 맵
     */
    public Map<String, Object> getAllAttributes() {
        checkValid();
        updateLastAccessedTime();
        return Collections.unmodifiableMap(new HashMap<>(attributes));
    }

    /**
     * 세션의 생존 시간을 반환합니다 (밀리초).
     *
     * @return 세션 생성부터 현재까지의 시간
     */
    public long getAge() {
        checkValid();
        return System.currentTimeMillis() - creationTime;
    }

    /**
     * 세션의 비활성 시간을 반환합니다 (밀리초).
     *
     * @return 마지막 접근부터 현재까지의 시간
     */
    public long getInactiveTime() {
        checkValid();
        return System.currentTimeMillis() - lastAccessedTime;
    }

    /**
     * 세션이 만료되었는지 확인합니다.
     * <p>
     * maxInactiveInterval 설정과 비교하여 만료 여부를 결정합니다.
     *
     * @return 만료되었으면 true, 아니면 false
     */
    public boolean isExpired() {
        if (!valid) {
            return true; // 무효화된 세션은 만료로 간주
        }

        if (maxInactiveInterval <= 0) {
            return false; // 무제한 세션
        }

        long inactiveSeconds = getInactiveTime() / 1000;
        return inactiveSeconds > maxInactiveInterval;
    }

    // ========== Object 메서드 오버라이드 ==========

    /**
     * 세션 정보를 문자열로 반환합니다.
     * <p>
     * 디버깅과 로깅 목적으로 사용됩니다.
     */
    @Override
    public String toString() {
        return String.format(
                "HttpSession[id=%s, creationTime=%d, lastAccessedTime=%d, " +
                        "maxInactiveInterval=%d, attributeCount=%d, valid=%s, new=%s]",
                sessionId,
                creationTime,
                lastAccessedTime,
                maxInactiveInterval,
                attributes.size(),
                valid,
                isNew
        );
    }

    /**
     * 두 세션이 같은지 비교합니다.
     * <p>
     * 세션 ID가 같으면 동일한 세션으로 간주합니다.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        HttpSessionImpl that = (HttpSessionImpl) obj;
        return Objects.equals(sessionId, that.sessionId);
    }

    /**
     * 세션의 해시 코드를 반환합니다.
     * <p>
     * 세션 ID의 해시 코드를 사용합니다.
     */
    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }

    // ========== 패키지 프라이빗 메서드들 (SessionManager 전용) ==========

    /**
     * SessionManager가 세션 접근 시간을 강제로 업데이트할 때 사용합니다.
     * <p>
     * 이 메서드는 패키지 프라이빗으로 SessionManager만 접근 가능합니다.
     */
    void forceUpdateLastAccessedTime() {
        this.lastAccessedTime = System.currentTimeMillis();
        this.isNew = false;
    }

    /**
     * SessionManager가 세션을 강제로 무효화할 때 사용합니다.
     * <p>
     * 이 메서드는 패키지 프라이빗으로 SessionManager만 접근 가능합니다.
     */
    void forceInvalidate() {
        this.valid = false;
        this.attributes.clear();
    }

    /**
     * SessionManager가 새로운 세션 상태를 변경할 때 사용합니다.
     *
     * @param isNew 새로운 세션 여부
     */
    void setNew(boolean isNew) {
        this.isNew = isNew;
    }
}