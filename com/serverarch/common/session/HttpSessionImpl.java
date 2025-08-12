package com.serverarch.common.session;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSession;
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
 *
 * 클래스 설계 원칙:
 * - Jakarta Servlet API 완전 구현
 * - 스레드 안전성 보장
 * - 메모리 효율성 고려
 * - 확장성과 유지보수성
 */
public class HttpSessionImpl implements HttpSession {

    // ========== 세션 기본 정보 필드들 ==========

    /**
     * 세션 고유 식별자
     * 보안을 위해 암호화된 랜덤 문자열 사용
     *
     * final로 선언한 이유:
     * - 세션 ID는 생성 후 변경되면 안됨
     * - 보안상 ID 변경 금지
     * - 불변성 보장
     */
    private final String sessionId;

    /**
     * 세션이 생성된 시간 (밀리초)
     * System.currentTimeMillis() 값
     *
     * final로 선언한 이유:
     * - 생성 시간은 변경되지 않는 고정값
     * - 세션 만료 계산의 기준점
     */
    private final long creationTime;

    /**
     * 마지막으로 세션에 접근한 시간 (밀리초)
     * 세션 만료 계산에 사용됨
     *
     * volatile로 선언한 이유:
     * - 멀티스레드 환경에서 즉시 가시성 보장
     * - 접근할 때마다 업데이트되는 값
     * - 동기화 없이도 최신 값 읽기 보장
     */
    private volatile long lastAccessedTime;

    /**
     * 세션의 최대 비활성 간격 (초)
     * -1이면 세션이 만료되지 않음
     * 0이면 즉시 만료
     *
     * volatile로 선언한 이유:
     * - 설정 변경 시 즉시 반영 필요
     * - 여러 스레드에서 읽을 수 있음
     */
    private volatile int maxInactiveInterval;

    /**
     * 세션의 유효성 상태
     * invalidate() 호출 시 false로 변경
     *
     * volatile로 선언한 이유:
     * - 무효화 상태는 즉시 모든 스레드에 반영되어야 함
     * - 세션 사용 전 항상 체크하는 중요한 상태
     */
    private volatile boolean valid;

    /**
     * 새로운 세션인지 여부
     * 클라이언트가 아직 세션을 인식하지 못한 상태
     *
     * volatile로 선언한 이유:
     * - 첫 번째 요청 처리 시 상태 변경
     * - 여러 스레드에서 동시에 체크할 수 있음
     */
    private volatile boolean isNew;

    // ========== 세션 데이터 저장소 ==========

    /**
     * 세션 속성들을 저장하는 맵
     * 스레드 안전한 ConcurrentHashMap 사용
     *
     * ConcurrentHashMap을 사용하는 이유:
     * - 멀티스레드 환경에서 안전한 동시 접근
     * - 높은 동시성 지원 (세그먼트 기반 락킹)
     * - 읽기 작업은 락 없이 수행 가능
     * - HashMap보다 thread-safe하고 Hashtable보다 성능 우수
     *
     * Key: 속성 이름 (String)
     * Value: 속성 값 (Object) - 모든 타입 저장 가능
     */
    private final Map<String, Object> attributes;

    // ========== 외부 의존성 ==========

    /**
     * 서블릿 컨텍스트 참조
     * 웹 애플리케이션 전역 정보 접근용
     *
     * final로 선언한 이유:
     * - 세션이 속한 웹 애플리케이션은 변경되지 않음
     * - 불변 참조로 안정성 보장
     */
    private final ServletContext servletContext;

    /**
     * 세션 매니저 참조
     * 세션 생명주기 관리 및 설정 변경 알림용
     *
     * final로 선언한 이유:
     * - 세션 매니저는 세션 생성 시 결정되고 변경되지 않음
     * - 세션과 매니저 간의 강한 결합 관계
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
     * @throws IllegalArgumentException 잘못된 파라미터 전달 시
     */
    public HttpSessionImpl(String sessionId, ServletContext servletContext, SessionManager sessionManager) {
        // ========== 입력 검증 ==========

        // 세션 ID 검증
        // sessionId == null: null 체크
        // sessionId.trim().isEmpty(): 공백만 있는 문자열 체크
        if (sessionId == null || sessionId.trim().isEmpty()) {
            // IllegalArgumentException: 잘못된 인수 전달 시 사용하는 표준 예외
            throw new IllegalArgumentException("세션 ID가 null이거나 비어있습니다");
        }

        // 서블릿 컨텍스트 검증
        if (servletContext == null) {
            throw new IllegalArgumentException("ServletContext가 null입니다");
        }

        // 세션 매니저 검증
        if (sessionManager == null) {
            throw new IllegalArgumentException("SessionManager가 null입니다");
        }

        // ========== 필드 초기화 ==========

        // trim(): 앞뒤 공백 제거하여 정규화
        this.sessionId = sessionId.trim();
        this.servletContext = servletContext;
        this.sessionManager = sessionManager;

        // ========== 시간 정보 초기화 ==========

        // System.currentTimeMillis(): 현재 시간을 밀리초로 반환
        // Unix epoch(1970-01-01 00:00:00 UTC)부터의 경과 시간
        this.creationTime = System.currentTimeMillis();

        // 생성 시점을 마지막 접근 시간으로 초기화
        this.lastAccessedTime = this.creationTime;

        // ========== 세션 설정 초기화 ==========

        // SessionManager에서 기본 설정값 가져오기
        // getDefaultMaxInactiveInterval(): 기본 비활성 간격 (보통 30분)
        this.maxInactiveInterval = sessionManager.getDefaultMaxInactiveInterval();

        // ========== 상태 초기화 ==========

        // 새로 생성된 세션은 유효한 상태로 시작
        this.valid = true;

        // 클라이언트가 아직 세션 ID를 받지 못한 상태
        this.isNew = true;

        // ========== 속성 맵 초기화 ==========

        // ConcurrentHashMap 생성 (스레드 안전)
        // 기본 용량과 로드 팩터 사용
        this.attributes = new ConcurrentHashMap<>();
    }

    // ========== HttpSession 인터페이스 구현 ==========

    /**
     * {@inheritDoc}
     * <p>
     * 세션의 고유 식별자를 반환합니다.
     * 이 ID는 세션 생성 시 한 번 할당되며 변경되지 않습니다.
     *
     * @return 세션 ID 문자열
     */
    @Override
    public String getId() {
        // 단순히 저장된 세션 ID 반환
        // final 필드이므로 동기화 불필요
        return sessionId;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션이 생성된 시간을 반환합니다.
     * 이 시간은 세션 생성 시 고정되며 변경되지 않습니다.
     *
     * @return 생성 시간 (밀리초, Unix epoch 기준)
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    @Override
    public long getCreationTime() {
        // 세션 유효성 검사 먼저 수행
        checkValid();

        // final 필드이므로 동기화 없이 안전하게 반환
        return creationTime;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션에 마지막으로 접근한 시간을 반환합니다.
     * 이 시간은 세션에 접근할 때마다 자동으로 업데이트됩니다.
     *
     * @return 마지막 접근 시간 (밀리초, Unix epoch 기준)
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    @Override
    public long getLastAccessedTime() {
        checkValid(); // 세션 유효성 검사

        // volatile 필드이므로 최신 값을 읽을 수 있음
        return lastAccessedTime;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 이 세션이 속한 서블릿 컨텍스트를 반환합니다.
     *
     * @return ServletContext 객체
     */
    @Override
    public ServletContext getServletContext() {
        // final 필드이므로 안전하게 반환
        // 무효화된 세션에서도 컨텍스트 정보는 필요할 수 있음
        return servletContext;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션의 최대 비활성 간격을 설정합니다.
     * 이 값이 변경되면 SessionManager에도 알림을 보냅니다.
     *
     * @param interval 최대 비활성 간격 (초)
     *                 0: 즉시 만료
     *                 음수: 만료되지 않음
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    @Override
    public void setMaxInactiveInterval(int interval) {
        checkValid(); // 세션 유효성 검사

        // volatile 필드에 값 설정 (즉시 가시성 보장)
        this.maxInactiveInterval = interval;

        // SessionManager에도 업데이트 알림
        // 매니저에서 전역 세션 정리 스케줄링에 활용
        sessionManager.setSessionMaxInactiveInterval(sessionId, interval);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션의 최대 비활성 간격을 반환합니다.
     *
     * @return 최대 비활성 간격 (초)
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    @Override
    public int getMaxInactiveInterval() {
        checkValid(); // 세션 유효성 검사

        // volatile 필드에서 최신 값 읽기
        return maxInactiveInterval;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 지정된 이름의 속성 값을 반환합니다.
     * 속성에 접근할 때마다 세션 접근 시간이 업데이트됩니다.
     *
     * @param name 속성 이름
     * @return 속성 값, 없으면 null
     * @throws IllegalArgumentException 속성 이름이 null인 경우
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    @Override
    public Object getAttribute(String name) {
        checkValid(); // 세션 유효성 검사

        // 속성 이름 null 체크
        if (name == null) {
            throw new IllegalArgumentException("속성 이름이 null입니다");
        }

        // 세션 접근 시간 업데이트
        updateLastAccessedTime();

        // ConcurrentHashMap에서 안전하게 값 조회
        // get(): 맵에서 키에 해당하는 값 반환, 없으면 null
        return attributes.get(name);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션에 저장된 모든 속성 이름을 반환합니다.
     * 접근할 때마다 세션 접근 시간이 업데이트됩니다.
     *
     * @return 속성 이름들의 Enumeration
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        checkValid(); // 세션 유효성 검사
        updateLastAccessedTime(); // 접근 시간 업데이트

        // 현재 시점의 속성 이름들을 복사하여 반환 (동시성 안전)
        // new HashSet<>(attributes.keySet()): 키 집합을 새로운 HashSet으로 복사
        // Collections.enumeration(): Set을 Enumeration으로 변환
        // 복사본을 사용하는 이유: 원본 맵이 변경되어도 Enumeration은 영향받지 않음
        return Collections.enumeration(new HashSet<>(attributes.keySet()));
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션에 속성을 설정합니다.
     * null 값을 설정하면 속성이 제거됩니다.
     * 속성 변경 시 세션 접근 시간이 업데이트됩니다.
     *
     * @param name 속성 이름
     * @param value 속성 값 (null이면 속성 제거)
     * @throws IllegalArgumentException 속성 이름이 null인 경우
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    @Override
    public void setAttribute(String name, Object value) {
        checkValid(); // 세션 유효성 검사

        // 속성 이름 null 체크
        if (name == null) {
            throw new IllegalArgumentException("속성 이름이 null입니다");
        }

        updateLastAccessedTime(); // 접근 시간 업데이트

        if (value == null) {
            // null 값 설정은 속성 제거와 동일
            // removeAttribute() 호출로 일관된 처리
            removeAttribute(name);
        } else {
            // 기존 값 확인 (리스너 호출용)
            // put(): ConcurrentHashMap에 키-값 쌍 저장
            // 기존 값이 있으면 반환, 없으면 null 반환
            Object oldValue = attributes.put(name, value);

            // HttpSessionAttributeListener 호출 (향후 구현 가능)
            // Servlet API 표준에 따른 이벤트 처리
            // if (oldValue == null) {
            //     // attributeAdded 이벤트 - 새 속성 추가
            //     fireAttributeAdded(name, value);
            // } else {
            //     // attributeReplaced 이벤트 - 기존 속성 변경
            //     fireAttributeReplaced(name, oldValue, value);
            // }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 지정된 이름의 속성을 제거합니다.
     * 속성 제거 시 세션 접근 시간이 업데이트됩니다.
     *
     * @param name 제거할 속성 이름
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    @Override
    public void removeAttribute(String name) {
        checkValid(); // 세션 유효성 검사

        if (name == null) {
            return; // null 이름은 조용히 무시 (Servlet API 명세에 따름)
        }

        updateLastAccessedTime(); // 접근 시간 업데이트

        // remove(): ConcurrentHashMap에서 키에 해당하는 엔트리 제거
        // 제거된 값을 반환, 없었으면 null 반환
        Object removedValue = attributes.remove(name);

        // HttpSessionAttributeListener 호출 (향후 구현 가능)
        // if (removedValue != null) {
        //     // attributeRemoved 이벤트 - 속성 제거됨
        //     fireAttributeRemoved(name, removedValue);
        // }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션을 무효화합니다.
     * 무효화된 세션은 더 이상 사용할 수 없으며 모든 속성이 제거됩니다.
     * 이 메서드는 동기화되어 있어 스레드 안전합니다.
     *
     * 무효화 과정:
     * 1. 유효성 검사
     * 2. 모든 속성 제거 (리스너 호출 포함)
     * 3. 세션 상태를 무효로 변경
     * 4. SessionManager에서 제거
     * 5. 세션 리스너 호출
     *
     * @throws IllegalStateException 이미 무효화된 세션인 경우
     */
    @Override
    public synchronized void invalidate() {
        checkValid(); // 세션 유효성 검사

        // ========== 1. 모든 속성 제거 (리스너 호출 포함) ==========

        // keySet()을 복사하여 동시 수정 문제 방지
        // ConcurrentHashMap이지만 반복 중 수정은 안전하지 않을 수 있음
        // new HashSet<>(): 키 집합의 스냅샷 생성
        Set<String> attributeNames = new HashSet<>(attributes.keySet());

        // for-each 루프로 모든 속성 제거
        for (String name : attributeNames) {
            // removeAttribute() 호출로 리스너 이벤트도 함께 처리
            removeAttribute(name);
        }

        // ========== 2. 세션 무효화 ==========

        // volatile 필드 설정으로 즉시 가시성 보장
        this.valid = false;

        // ========== 3. SessionManager에서 제거 ==========

        // 중앙 세션 저장소에서 이 세션 제거
        // invalidateSession(): 매니저의 세션 무효화 메서드
        sessionManager.invalidateSession(sessionId);

        // HttpSessionListener 호출 (향후 구현 가능)
        // sessionDestroyed 이벤트 - 세션이 소멸됨
        // fireSessionDestroyed();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 세션이 새로운 세션인지 확인합니다.
     * 클라이언트가 아직 세션 ID를 받지 못했거나 인식하지 못한 상태입니다.
     *
     * 새로운 세션의 의미:
     * - 이번 요청에서 처음 생성된 세션
     * - 클라이언트가 아직 세션 쿠키를 받지 못함
     * - 첫 번째 응답에서 Set-Cookie 헤더 필요
     *
     * @return 새로운 세션이면 true, 기존 세션이면 false
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    @Override
    public boolean isNew() {
        checkValid(); // 세션 유효성 검사

        // volatile 필드에서 최신 상태 읽기
        return isNew;
    }

    // ========== 내부 유틸리티 메서드들 ==========

    /**
     * 세션이 유효한지 확인합니다.
     *
     * private 메서드로 구현한 이유:
     * - 모든 public 메서드에서 공통으로 사용
     * - 일관된 유효성 검사 로직
     * - 코드 중복 방지
     *
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    private void checkValid() {
        // volatile 필드 읽기로 최신 상태 확인
        if (!valid) {
            // IllegalStateException: 객체 상태가 메서드 호출에 적합하지 않을 때
            // Servlet API 명세에서 정의한 표준 예외
            throw new IllegalStateException("세션이 이미 무효화되었습니다: " + sessionId);
        }
    }

    /**
     * 마지막 접근 시간을 업데이트합니다.
     * <p>
     * 세션에 접근할 때마다 호출되어 세션 만료 시간을 연장합니다.
     * 또한 새로운 세션 플래그를 해제합니다.
     *
     * private 메서드로 구현한 이유:
     * - 세션 접근을 표시하는 내부 로직
     * - 여러 메서드에서 공통 사용
     * - 캡슐화 원칙 준수
     */
    private void updateLastAccessedTime() {
        // System.currentTimeMillis(): 현재 시간을 밀리초로 반환
        // volatile 필드 설정으로 즉시 가시성 보장
        this.lastAccessedTime = System.currentTimeMillis();

        // 세션에 접근했으므로 더 이상 새로운 세션이 아님
        // 클라이언트가 세션을 인식했다고 간주
        this.isNew = false;
    }

    // ========== 추가 유틸리티 메서드들 ==========

    /**
     * 세션의 유효성을 반환합니다.
     * <p>
     * SessionManager가 세션 상태를 확인할 때 사용합니다.
     *
     * package-private 접근 제어자 사용 이유:
     * - SessionManager에서만 접근 필요
     * - 외부에서는 isNew() 등으로 간접 확인
     * - 내부 구현 세부사항 은닉
     *
     * @return 유효하면 true, 무효화되었으면 false
     */
    public boolean isValid() {
        // volatile 필드에서 최신 상태 반환
        // checkValid() 호출하지 않음 (무효한 세션도 상태 확인 허용)
        return valid;
    }

    /**
     * 세션에 저장된 속성 개수를 반환합니다.
     * <p>
     * 모니터링이나 디버깅 목적으로 사용할 수 있습니다.
     *
     * 활용 예시:
     * - 메모리 사용량 추정
     * - 세션 크기 모니터링
     * - 디버깅 정보 제공
     *
     * @return 속성 개수
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    public int getAttributeCount() {
        checkValid(); // 세션 유효성 검사

        // size(): ConcurrentHashMap의 현재 엔트리 수 반환
        // 스레드 안전하게 크기 반환
        return attributes.size();
    }

    /**
     * 세션이 비어있는지 확인합니다.
     *
     * 비어있다는 의미:
     * - 저장된 속성이 하나도 없음
     * - 새로 생성되었거나 모든 속성이 제거됨
     *
     * @return 속성이 없으면 true, 있으면 false
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    public boolean isEmpty() {
        checkValid(); // 세션 유효성 검사

        // isEmpty(): 맵이 비어있는지 확인
        // size() == 0과 동일하지만 의미가 더 명확
        return attributes.isEmpty();
    }

    /**
     * 세션의 모든 속성을 Map으로 반환합니다.
     * <p>
     * 디버깅이나 세션 내용 덤프 시 사용할 수 있습니다.
     * 반환되는 Map은 읽기 전용입니다.
     *
     * 방어적 복사를 사용하는 이유:
     * - 원본 맵의 내용 보호
     * - 외부에서 수정해도 세션에 영향 없음
     * - 스냅샷 제공으로 일관된 뷰 보장
     *
     * @return 읽기 전용 속성 맵
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    public Map<String, Object> getAllAttributes() {
        checkValid(); // 세션 유효성 검사
        updateLastAccessedTime(); // 접근 시간 업데이트

        // 방어적 복사 + 읽기 전용 래핑
        // new HashMap<>(attributes): 현재 속성들의 스냅샷 생성
        // Collections.unmodifiableMap(): 수정 불가능한 맵으로 래핑
        return Collections.unmodifiableMap(new HashMap<>(attributes));
    }

    /**
     * 세션의 생존 시간을 반환합니다 (밀리초).
     *
     * 생존 시간 계산:
     * 현재 시간 - 생성 시간
     *
     * 활용 예시:
     * - 세션 수명 통계
     * - 성능 모니터링
     * - 로그 분석
     *
     * @return 세션 생성부터 현재까지의 시간 (밀리초)
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    public long getAge() {
        checkValid(); // 세션 유효성 검사

        // 현재 시간에서 생성 시간을 빼서 경과 시간 계산
        return System.currentTimeMillis() - creationTime;
    }

    /**
     * 세션의 비활성 시간을 반환합니다 (밀리초).
     *
     * 비활성 시간 계산:
     * 현재 시간 - 마지막 접근 시간
     *
     * 활용 예시:
     * - 세션 만료 예측
     * - 정리 스케줄링
     * - 사용자 활동 분석
     *
     * @return 마지막 접근부터 현재까지의 시간 (밀리초)
     * @throws IllegalStateException 세션이 무효화된 경우
     */
    public long getInactiveTime() {
        checkValid(); // 세션 유효성 검사

        // volatile 필드에서 최신 접근 시간 읽기
        return System.currentTimeMillis() - lastAccessedTime;
    }

    /**
     * 세션이 만료되었는지 확인합니다.
     * <p>
     * maxInactiveInterval 설정과 비교하여 만료 여부를 결정합니다.
     *
     * 만료 조건:
     * 1. 세션이 무효화됨
     * 2. 비활성 시간이 최대 허용 시간 초과
     *
     * 예외 조건:
     * - maxInactiveInterval <= 0: 무제한 세션 (만료되지 않음)
     *
     * @return 만료되었으면 true, 아니면 false
     */
    public boolean isExpired() {
        // 무효화된 세션은 만료로 간주
        if (!valid) {
            return true;
        }

        // maxInactiveInterval 체크
        if (maxInactiveInterval <= 0) {
            return false; // 무제한 세션 (0 이하면 만료되지 않음)
        }

        // 비활성 시간을 초 단위로 변환하여 비교
        // getInactiveTime() / 1000: 밀리초를 초로 변환
        long inactiveSeconds = getInactiveTime() / 1000;

        // 비활성 시간이 최대 허용 시간을 초과하면 만료
        return inactiveSeconds > maxInactiveInterval;
    }

    // ========== Object 메서드 오버라이드 ==========

    /**
     * 세션 정보를 문자열로 반환합니다.
     * <p>
     * 디버깅과 로깅 목적으로 사용됩니다.
     *
     * toString() 오버라이드 이유:
     * - 로그에서 세션 정보 쉽게 확인
     * - 디버깅 시 세션 상태 파악
     * - 모니터링 도구에서 활용
     *
     * @return 포맷된 세션 정보 문자열
     */
    @Override
    public String toString() {
        // String.format(): 형식화된 문자열 생성
        // 여러 줄에 걸쳐 정보를 보기 좋게 포맷팅
        return String.format(
                "HttpSession[id=%s, creationTime=%d, lastAccessedTime=%d, " +
                        "maxInactiveInterval=%d, attributeCount=%d, valid=%s, new=%s]",
                sessionId,                    // 세션 ID
                creationTime,                 // 생성 시간
                lastAccessedTime,             // 마지막 접근 시간
                maxInactiveInterval,          // 최대 비활성 간격
                attributes.size(),            // 속성 개수
                valid,                        // 유효성 상태
                isNew                         // 새로운 세션 여부
        );
    }

    /**
     * 두 세션이 같은지 비교합니다.
     * <p>
     * 세션 ID가 같으면 동일한 세션으로 간주합니다.
     *
     * equals() 오버라이드 규칙:
     * 1. 반사성: x.equals(x) == true
     * 2. 대칭성: x.equals(y) == y.equals(x)
     * 3. 이행성: x.equals(y) && y.equals(z) → x.equals(z)
     * 4. 일관성: 여러 번 호출해도 같은 결과
     * 5. null 처리: x.equals(null) == false
     *
     * @param obj 비교할 객체
     * @return 같은 세션이면 true, 다르면 false
     */
    @Override
    public boolean equals(Object obj) {
        // 1. 동일 객체 참조 확인 (성능 최적화)
        if (this == obj) return true;

        // 2. null 체크와 클래스 타입 확인
        // getClass() != obj.getClass(): 정확한 타입 비교 (상속 고려)
        if (obj == null || getClass() != obj.getClass()) return false;

        // 3. 타입 캐스팅
        HttpSessionImpl that = (HttpSessionImpl) obj;

        // 4. 세션 ID로 동등성 비교
        // Objects.equals(): null-safe 문자열 비교
        return Objects.equals(sessionId, that.sessionId);
    }

    /**
     * 세션의 해시 코드를 반환합니다.
     * <p>
     * 세션 ID의 해시 코드를 사용합니다.
     *
     * hashCode() 오버라이드 규칙:
     * - equals()를 오버라이드했으면 반드시 hashCode()도 오버라이드
     * - equals()가 true인 객체들은 같은 해시 코드 반환
     * - 해시 기반 컬렉션(HashMap, HashSet)에서 올바른 동작 보장
     *
     * @return 세션 ID 기반 해시 코드
     */
    @Override
    public int hashCode() {
        // Objects.hash(): null-safe 해시 코드 생성
        // 세션 ID만으로 해시 코드 생성 (equals와 일관성 유지)
        return Objects.hash(sessionId);
    }

    // ========== 패키지 프라이빗 메서드들 (SessionManager 전용) ==========

    /**
     * SessionManager가 세션 접근 시간을 강제로 업데이트할 때 사용합니다.
     * <p>
     * 이 메서드는 패키지 프라이빗으로 SessionManager만 접근 가능합니다.
     *
     * 사용 시나리오:
     * - 매니저에서 세션 정리 시 접근 시간 동기화
     * - 외부 시스템과의 세션 상태 동기화
     * - 테스트 코드에서의 시간 조작
     */
    void forceUpdateLastAccessedTime() {
        // 현재 시간으로 강제 업데이트
        this.lastAccessedTime = System.currentTimeMillis();

        // 새로운 세션 플래그도 해제
        this.isNew = false;
    }

    /**
     * SessionManager가 세션을 강제로 무효화할 때 사용합니다.
     * <p>
     * 이 메서드는 패키지 프라이빗으로 SessionManager만 접근 가능합니다.
     *
     * 일반 invalidate()와의 차이점:
     * - 유효성 검사 없이 강제 무효화
     * - 리스너 호출 없이 간단한 정리
     * - 매니저에서 대량 정리 시 사용
     */
    void forceInvalidate() {
        // 세션 상태를 무효로 변경
        this.valid = false;

        // 모든 속성 제거 (리스너 호출 없음)
        // clear(): ConcurrentHashMap의 모든 엔트리 제거
        this.attributes.clear();
    }

    /**
     * SessionManager가 새로운 세션 상태를 변경할 때 사용합니다.
     *
     * 사용 시나리오:
     * - 세션 복원 시 상태 설정
     * - 테스트에서 상태 조작
     * - 특수한 세션 관리 로직
     *
     * @param isNew 새로운 세션 여부
     */
    void setNew(boolean isNew) {
        // volatile 필드에 직접 설정
        this.isNew = isNew;
    }
}