package com.serverarch.common.session;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * HTTP 세션의 생성, 관리, 만료를 담당하는 매니저 클래스입니다.
 *
 * SessionManager는 웹 애플리케이션의 모든 세션을 중앙에서 관리하며,
 * 세션 생명주기, 보안, 성능 최적화를 처리합니다.
 *
 * 주요 기능:
 * - 세션 생성 및 ID 생성 (보안)
 * - 세션 저장소 관리
 * - 세션 만료 및 정리
 * - 세션 통계 및 모니터링
 * - 세션 보안 (고정 공격 방지 등)
 *
 * 설계 원칙:
 * - 스레드 안전성 보장
 * - 높은 동시성 지원
 * - 메모리 효율성
 * - 확장성과 유지보수성
 * - 보안 우선 설계
 */
public class SessionManager {

    /**
     * 세션 정보를 담는 내부 클래스입니다.
     *
     * static 내부 클래스로 구현한 이유:
     * - SessionManager와 밀접한 관련이 있는 데이터 구조
     * - 외부 클래스 참조 불필요 (메모리 효율성)
     * - 세션 메타데이터의 캡슐화
     * - 네임스페이스 정리
     */
    public static class SessionInfo {
        // ========== 세션 기본 정보 ==========

        /**
         * 세션 고유 식별자
         * final로 선언하여 불변성 보장
         */
        private final String sessionId;

        /**
         * 세션 생성 시간 (밀리초)
         * final로 선언하여 불변성 보장
         */
        private final long creationTime;

        // ========== 세션 상태 정보 ==========

        /**
         * 마지막 접근 시간 (밀리초)
         * volatile로 선언하여 멀티스레드 환경에서 가시성 보장
         */
        private volatile long lastAccessedTime;

        /**
         * 최대 비활성 간격 (초)
         * volatile로 선언하여 설정 변경 시 즉시 반영
         */
        private volatile int maxInactiveInterval;

        /**
         * 세션 유효성 상태
         * volatile로 선언하여 무효화 상태 즉시 반영
         */
        private volatile boolean valid;

        /**
         * 새로운 세션 여부
         * volatile로 선언하여 상태 변경 즉시 반영
         */
        private volatile boolean newSession;

        /**
         * SessionInfo 생성자
         *
         * @param sessionId 세션 ID
         * @param defaultMaxInactiveInterval 기본 최대 비활성 간격
         */
        public SessionInfo(String sessionId, int defaultMaxInactiveInterval) {
            this.sessionId = sessionId;

            // 현재 시간을 생성 시간과 마지막 접근 시간으로 설정
            // System.currentTimeMillis(): Unix epoch 기준 밀리초
            this.creationTime = System.currentTimeMillis();
            this.lastAccessedTime = creationTime;

            // 매니저에서 설정한 기본값으로 초기화
            this.maxInactiveInterval = defaultMaxInactiveInterval;

            // 새로 생성된 세션은 유효하고 새로운 상태
            this.valid = true;
            this.newSession = true;
        }

        // ========== Getter 메서드들 ==========

        /**
         * 세션 ID 반환
         * @return 세션 고유 식별자
         */
        public String getSessionId() { return sessionId; }

        /**
         * 생성 시간 반환
         * @return 세션 생성 시간 (밀리초)
         */
        public long getCreationTime() { return creationTime; }

        /**
         * 마지막 접근 시간 반환
         * @return 마지막 접근 시간 (밀리초)
         */
        public long getLastAccessedTime() { return lastAccessedTime; }

        /**
         * 최대 비활성 간격 반환
         * @return 최대 비활성 간격 (초)
         */
        public int getMaxInactiveInterval() { return maxInactiveInterval; }

        /**
         * 유효성 상태 반환
         * @return 유효하면 true
         */
        public boolean isValid() { return valid; }

        /**
         * 새로운 세션 여부 반환
         * @return 새로운 세션이면 true
         */
        public boolean isNew() { return newSession; }

        // ========== 상태 업데이트 메서드들 ==========

        /**
         * 마지막 접근 시간을 현재 시간으로 업데이트합니다.
         *
         * package-private 접근 제어자 사용 이유:
         * - SessionManager에서만 호출
         * - 외부에서 임의 조작 방지
         * - 내부 구현 세부사항 은닉
         */
        void updateLastAccessedTime() {
            this.lastAccessedTime = System.currentTimeMillis();
            this.newSession = false; // 접근했으므로 더 이상 새로운 세션 아님
        }

        /**
         * 최대 비활성 간격을 설정합니다.
         *
         * @param interval 새로운 최대 비활성 간격 (초)
         */
        void setMaxInactiveInterval(int interval) {
            this.maxInactiveInterval = interval;
        }

        /**
         * 세션을 무효화합니다.
         */
        void invalidate() {
            this.valid = false;
        }

        /**
         * 세션이 만료되었는지 확인합니다.
         *
         * 만료 조건:
         * 1. 세션이 무효화됨
         * 2. 비활성 시간이 허용 시간 초과
         *
         * @return 만료되었으면 true
         */
        boolean isExpired() {
            // 무효화된 세션은 항상 만료로 간주
            if (!valid || maxInactiveInterval <= 0) {
                return !valid; // maxInactiveInterval <= 0이면 무제한 세션
            }

            // 현재 시간과 마지막 접근 시간의 차이 계산
            long currentTime = System.currentTimeMillis();
            // 비활성 시간을 초 단위로 변환
            long inactiveTime = (currentTime - lastAccessedTime) / 1000;

            // 비활성 시간이 허용 시간을 초과하면 만료
            return inactiveTime > maxInactiveInterval;
        }
    }

    // ========== 핵심 컴포넌트들 ==========

    /**
     * 세션 ID 생성기
     *
     * SecureRandom을 사용하는 이유:
     * - 암호학적으로 안전한 난수 생성
     * - 예측 불가능한 세션 ID로 보안 강화
     * - 세션 고정 공격(Session Fixation) 방지
     * - 무차별 대입 공격(Brute Force) 방지
     */
    private final SecureRandom secureRandom;

    /**
     * 세션 저장소 (세션 ID -> HttpSession)
     *
     * ConcurrentHashMap을 사용하는 이유:
     * - 높은 동시성 지원 (세그먼트 기반 락킹)
     * - 읽기 작업 시 락 없이 수행 가능
     * - 스레드 안전한 세션 저장소
     * - HashMap보다 안전하고 Hashtable보다 성능 우수
     */
    private final Map<String, HttpSession> sessions;

    /**
     * 세션 메타데이터 저장소 (세션 ID -> SessionInfo)
     *
     * 별도 저장소를 사용하는 이유:
     * - 빠른 메타데이터 접근
     * - 세션 객체 없이도 상태 확인 가능
     * - 메모리 효율성 (필요시에만 세션 객체 로드)
     */
    private final Map<String, SessionInfo> sessionInfos;

    /**
     * 읽기/쓰기 락 (성능 최적화)
     *
     * ReentrantReadWriteLock을 사용하는 이유:
     * - 읽기 작업은 동시에 여러 스레드에서 수행 가능
     * - 쓰기 작업만 독점적으로 수행
     * - 읽기가 많은 세션 관리에 최적화
     * - 전체 성능 향상
     */
    private final ReentrantReadWriteLock sessionLock;

    /**
     * 세션 만료 정리를 위한 스케줄러
     *
     * ScheduledExecutorService를 사용하는 이유:
     * - 주기적인 정리 작업 실행
     * - 백그라운드에서 비동기 처리
     * - 메인 애플리케이션 성능에 영향 없음
     * - 자원 관리 자동화
     */
    private final ScheduledExecutorService cleanupScheduler;

    /**
     * 서블릿 컨텍스트 참조
     *
     * 필요한 이유:
     * - HttpSession 생성 시 필요
     * - 웹 애플리케이션 컨텍스트 정보 제공
     * - 리스너 호출 시 사용
     */
    private final ServletContext servletContext;

    // ========== 세션 설정 필드들 ==========

    /**
     * 기본 최대 비활성 간격 (초)
     * volatile로 선언하여 런타임 설정 변경 지원
     */
    private volatile int defaultMaxInactiveInterval; // 기본 30분

    /**
     * 세션 ID 길이 (바이트)
     * 길수록 보안성 향상, 메모리 사용량 증가
     */
    private volatile int sessionIdLength;

    /**
     * 세션 쿠키 이름
     * 기본값: "JSESSIONID" (Servlet 표준)
     */
    private volatile String cookieName;

    /**
     * 쿠키 경로
     * 쿠키가 유효한 URL 경로 지정
     */
    private volatile String cookiePath;

    /**
     * 쿠키 도메인
     * 쿠키가 유효한 도메인 지정
     */
    private volatile String cookieDomain;

    /**
     * HTTPS 전용 쿠키 여부
     * true이면 HTTPS 연결에서만 쿠키 전송
     */
    private volatile boolean cookieSecure;

    /**
     * JavaScript 접근 차단 여부
     * true이면 document.cookie로 접근 불가 (XSS 방지)
     */
    private volatile boolean cookieHttpOnly;

    /**
     * 쿠키 최대 생존시간 (초)
     * -1이면 브라우저 세션 쿠키 (브라우저 종료 시 삭제)
     */
    private volatile int cookieMaxAge;

    // ========== 통계 정보 필드들 ==========

    /**
     * 총 생성된 세션 수
     * volatile로 선언하여 정확한 통계 보장
     */
    private volatile long totalSessionsCreated;

    /**
     * 총 만료된 세션 수
     */
    private volatile long totalSessionsExpired;

    /**
     * 총 무효화된 세션 수
     */
    private volatile long totalSessionsInvalidated;

    /**
     * SessionManager 생성자
     *
     * @param servletContext 서블릿 컨텍스트
     * @throws IllegalArgumentException servletContext가 null인 경우
     */
    public SessionManager(ServletContext servletContext) {
        // ========== 입력 검증 ==========
        if (servletContext == null) {
            throw new IllegalArgumentException("ServletContext가 null입니다");
        }

        this.servletContext = servletContext;

        // ========== 핵심 컴포넌트 초기화 ==========

        // SecureRandom(): 암호학적으로 안전한 난수 생성기
        // 시드는 시스템에서 자동으로 설정 (entropy pool 사용)
        this.secureRandom = new SecureRandom();

        // ConcurrentHashMap 생성 (기본 용량과 로드 팩터 사용)
        this.sessions = new ConcurrentHashMap<>();
        this.sessionInfos = new ConcurrentHashMap<>();

        // ReentrantReadWriteLock(): 공정성 없는 락 (성능 우선)
        // 공정성 있는 락은 성능이 떨어지지만 스레드 기아 방지
        this.sessionLock = new ReentrantReadWriteLock();

        // ========== 기본 설정 초기화 ==========

        // 30분 = 30 * 60초 = 1800초
        this.defaultMaxInactiveInterval = 30 * 60;

        // 32바이트 = 256비트 (충분한 보안성)
        this.sessionIdLength = 32;

        // Servlet API 표준 쿠키 이름
        this.cookieName = "JSESSIONID";

        // 루트 경로 (전체 애플리케이션에서 유효)
        this.cookiePath = "/";

        // null이면 현재 도메인에서만 유효
        this.cookieDomain = null;

        // 개발 환경에서는 false, 운영 환경에서는 true 권장
        this.cookieSecure = false;

        // XSS 공격 방지를 위해 기본적으로 true
        this.cookieHttpOnly = true;

        // -1이면 브라우저 세션 쿠키 (브라우저 종료 시 삭제)
        this.cookieMaxAge = -1;

        // ========== 통계 초기화 ==========
        this.totalSessionsCreated = 0;
        this.totalSessionsExpired = 0;
        this.totalSessionsInvalidated = 0;

        // ========== 정리 스케줄러 초기화 ==========

        // newSingleThreadScheduledExecutor(): 단일 스레드 스케줄러 생성
        // 람다 표현식으로 스레드 팩토리 커스터마이징
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            // Thread 생성 시 이름과 데몬 여부 설정
            Thread t = new Thread(r, "SessionCleanupThread");
            // setDaemon(true): 데몬 스레드로 설정
            // 메인 스레드 종료 시 함께 종료됨 (좀비 프로세스 방지)
            t.setDaemon(true);
            return t;
        });

        // 스케줄러 시작
        startCleanupScheduler();
    }

    // ========== 세션 생명주기 관리 메서드들 ==========

    /**
     * 새로운 세션을 생성합니다.
     *
     * 세션 생성 과정:
     * 1. 고유한 세션 ID 생성
     * 2. 중복 ID 검사 (매우 낮은 확률)
     * 3. 세션 정보 및 객체 생성
     * 4. 저장소에 등록
     * 5. 통계 업데이트
     *
     * @return 생성된 HttpSession 객체
     */
    public HttpSession createSession() {
        // ========== 1. 세션 ID 생성 ==========
        String sessionId = generateSessionId();

        // ========== 2. 쓰기 락 획득 ==========
        // 세션 생성은 저장소를 수정하므로 쓰기 락 필요
        sessionLock.writeLock().lock();
        try {
            // ========== 3. 중복 ID 확인 ==========
            // SecureRandom으로 생성하므로 중복 확률은 매우 낮음
            // 하지만 보안상 확인 필요
            while (sessions.containsKey(sessionId)) {
                sessionId = generateSessionId(); // 중복이면 새로 생성
            }

            // ========== 4. 세션 정보 생성 ==========
            SessionInfo sessionInfo = new SessionInfo(sessionId, defaultMaxInactiveInterval);
            // put(): ConcurrentHashMap에 세션 정보 저장
            sessionInfos.put(sessionId, sessionInfo);

            // ========== 5. HttpSession 구현체 생성 ==========
            HttpSession session = new HttpSessionImpl(sessionId, servletContext, this);
            // put(): ConcurrentHashMap에 세션 객체 저장
            sessions.put(sessionId, session);

            // ========== 6. 통계 업데이트 ==========
            // volatile 필드 증가 (원자적 연산 아님, 정확성보다 성능 우선)
            totalSessionsCreated++;

            return session;

        } finally {
            // ========== 7. 락 해제 ==========
            // finally 블록으로 예외 발생 시에도 락 해제 보장
            sessionLock.writeLock().unlock();
        }
    }

    /**
     * 세션 ID로 세션을 조회합니다.
     *
     * 조회 과정:
     * 1. 입력 검증
     * 2. 세션 정보 확인
     * 3. 만료 여부 확인
     * 4. 접근 시간 업데이트
     * 5. 세션 객체 반환
     *
     * @param sessionId 세션 ID
     * @return HttpSession 객체, 없거나 만료되면 null
     */
    public HttpSession getSession(String sessionId) {
        // ========== 1. 입력 검증 ==========
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }

        // ========== 2. 읽기 락 획득 ==========
        // 세션 조회는 읽기 작업이므로 읽기 락 사용
        // 여러 스레드가 동시에 읽기 가능
        sessionLock.readLock().lock();
        try {
            // ========== 3. 세션 정보 조회 ==========
            // get(): ConcurrentHashMap에서 세션 정보 조회
            SessionInfo sessionInfo = sessionInfos.get(sessionId);

            // 세션이 없거나 무효하거나 만료된 경우
            if (sessionInfo == null || !sessionInfo.isValid() || sessionInfo.isExpired()) {
                // 만료된 세션 정리 (성능 최적화)
                if (sessionInfo != null && sessionInfo.isExpired()) {
                    removeExpiredSession(sessionId);
                }
                return null;
            }

            // ========== 4. 접근 시간 업데이트 ==========
            // 유효한 세션에 접근했으므로 시간 업데이트
            sessionInfo.updateLastAccessedTime();

            // ========== 5. 세션 객체 반환 ==========
            return sessions.get(sessionId);

        } finally {
            // ========== 6. 락 해제 ==========
            sessionLock.readLock().unlock();
        }
    }

    /**
     * 세션을 무효화합니다.
     *
     * 무효화 과정:
     * 1. 입력 검증
     * 2. 세션 정보 확인
     * 3. 세션 무효화
     * 4. 저장소에서 제거
     * 5. 통계 업데이트
     *
     * @param sessionId 무효화할 세션 ID
     * @return 무효화 성공 여부
     */
    public boolean invalidateSession(String sessionId) {
        if (sessionId == null) {
            return false;
        }

        // ========== 쓰기 락 획득 ==========
        // 세션 제거는 저장소를 수정하므로 쓰기 락 필요
        sessionLock.writeLock().lock();
        try {
            // ========== 세션 정보 확인 ==========
            SessionInfo sessionInfo = sessionInfos.get(sessionId);
            if (sessionInfo == null) {
                return false; // 이미 없는 세션
            }

            // ========== 세션 무효화 ==========
            sessionInfo.invalidate();

            // ========== 저장소에서 제거 ==========
            // remove(): ConcurrentHashMap에서 엔트리 제거
            sessions.remove(sessionId);
            sessionInfos.remove(sessionId);

            // ========== 통계 업데이트 ==========
            totalSessionsInvalidated++;

            return true;

        } finally {
            sessionLock.writeLock().unlock();
        }
    }

    /**
     * 세션 ID가 유효한지 확인합니다.
     *
     * 빠른 유효성 검사 메서드:
     * - 세션 객체를 로드하지 않고 메타데이터만 확인
     * - 성능이 중요한 곳에서 사용
     *
     * @param sessionId 확인할 세션 ID
     * @return 유효한 세션 ID이면 true
     */
    public boolean isValidSessionId(String sessionId) {
        if (sessionId == null) {
            return false;
        }

        // 읽기 락으로 가벼운 확인
        sessionLock.readLock().lock();
        try {
            SessionInfo sessionInfo = sessionInfos.get(sessionId);
            // 세션 정보가 있고, 유효하고, 만료되지 않았으면 유효
            return sessionInfo != null && sessionInfo.isValid() && !sessionInfo.isExpired();
        } finally {
            sessionLock.readLock().unlock();
        }
    }

    /**
     * 세션 정보를 반환합니다.
     *
     * 메타데이터만 필요한 경우 사용:
     * - 세션 객체 로드 없이 빠른 정보 조회
     * - 모니터링이나 통계 수집 시 활용
     *
     * @param sessionId 조회할 세션 ID
     * @return 세션 정보 객체, 없으면 null
     */
    public SessionInfo getSessionInfo(String sessionId) {
        if (sessionId == null) {
            return null;
        }

        sessionLock.readLock().lock();
        try {
            return sessionInfos.get(sessionId);
        } finally {
            sessionLock.readLock().unlock();
        }
    }

    /**
     * 세션의 최대 비활성 간격을 설정합니다.
     *
     * 런타임 설정 변경 지원:
     * - 개별 세션의 만료 시간 조정
     * - 특별한 세션 관리 정책 적용
     *
     * @param sessionId 설정할 세션 ID
     * @param interval 새로운 최대 비활성 간격 (초)
     */
    public void setSessionMaxInactiveInterval(String sessionId, int interval) {
        if (sessionId == null) {
            return;
        }

        // 읽기 락으로 가벼운 업데이트
        // SessionInfo의 메서드가 이미 동기화되어 있음
        sessionLock.readLock().lock();
        try {
            SessionInfo sessionInfo = sessionInfos.get(sessionId);
            if (sessionInfo != null) {
                sessionInfo.setMaxInactiveInterval(interval);
            }
        } finally {
            sessionLock.readLock().unlock();
        }
    }

    // ========== 세션 정리 및 유지보수 메서드들 ==========

    /**
     * 만료된 세션들을 정리합니다.
     *
     * 2단계 정리 과정:
     * 1. 읽기 락으로 만료된 세션 ID 수집
     * 2. 쓰기 락으로 실제 제거 작업
     *
     * 이유: 락 보유 시간을 최소화하여 성능 향상
     */
    public void cleanupExpiredSessions() {
        // ========== 1단계: 만료된 세션 ID 수집 ==========
        List<String> expiredSessionIds = new ArrayList<>();

        // 읽기 락으로 스냅샷 생성
        sessionLock.readLock().lock();
        try {
            // entrySet(): 맵의 모든 키-값 쌍을 Set으로 반환
            for (Map.Entry<String, SessionInfo> entry : sessionInfos.entrySet()) {
                SessionInfo sessionInfo = entry.getValue();
                // 무효하거나 만료된 세션 ID 수집
                if (!sessionInfo.isValid() || sessionInfo.isExpired()) {
                    // add(): 리스트에 요소 추가
                    expiredSessionIds.add(entry.getKey());
                }
            }
        } finally {
            sessionLock.readLock().unlock();
        }

        // ========== 2단계: 만료된 세션 제거 ==========
        if (!expiredSessionIds.isEmpty()) {
            // 쓰기 락으로 실제 제거
            sessionLock.writeLock().lock();
            try {
                // for-each 루프로 각 세션 제거
                for (String sessionId : expiredSessionIds) {
                    removeExpiredSession(sessionId);
                }
            } finally {
                sessionLock.writeLock().unlock();
            }
        }
    }

    /**
     * 현재 활성 세션 수를 반환합니다.
     *
     * 모니터링 및 통계 목적:
     * - 서버 부하 모니터링
     * - 메모리 사용량 추정
     * - 스케일링 결정 지원
     *
     * @return 현재 활성 세션 수
     */
    public int getActiveSessionCount() {
        sessionLock.readLock().lock();
        try {
            // size(): ConcurrentHashMap의 현재 엔트리 수
            return sessions.size();
        } finally {
            sessionLock.readLock().unlock();
        }
    }

    /**
     * 모든 세션을 무효화합니다.
     *
     * 사용 시나리오:
     * - 서버 종료 시 정리
     * - 보안 사고 시 전체 세션 무효화
     * - 시스템 유지보수 시 세션 정리
     */
    public void invalidateAllSessions() {
        sessionLock.writeLock().lock();
        try {
            // ========== 모든 세션 무효화 ==========
            // values(): 맵의 모든 값을 Collection으로 반환
            for (SessionInfo sessionInfo : sessionInfos.values()) {
                sessionInfo.invalidate();
                totalSessionsInvalidated++;
            }

            // ========== 저장소 정리 ==========
            // clear(): 맵의 모든 엔트리 제거
            sessions.clear();
            sessionInfos.clear();

        } finally {
            sessionLock.writeLock().unlock();
        }
    }

    /**
     * SessionManager를 종료합니다.
     *
     * 종료 과정:
     * 1. 정리 스케줄러 종료
     * 2. 모든 세션 무효화
     * 3. 자원 정리
     *
     * Graceful Shutdown 지원:
     * - 진행 중인 작업 완료 대기
     * - 강제 종료 전 유예 시간 제공
     */
    public void shutdown() {
        // ========== 정리 스케줄러 종료 ==========

        // shutdown(): 새로운 작업 받지 않고 기존 작업 완료 대기
        cleanupScheduler.shutdown();
        try {
            // awaitTermination(): 지정된 시간 동안 종료 대기
            // 5초 동안 graceful shutdown 시도
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                // shutdownNow(): 진행 중인 작업 강제 중단
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            // 인터럽트 발생 시 즉시 강제 종료
            cleanupScheduler.shutdownNow();
            // 현재 스레드의 인터럽트 상태 복원
            Thread.currentThread().interrupt();
        }

        // ========== 모든 세션 무효화 ==========
        invalidateAllSessions();
    }

    /**
     * 통계 정보를 반환합니다.
     *
     * 제공하는 통계:
     * - 현재 활성 세션 수
     * - 총 생성된 세션 수
     * - 총 만료된 세션 수
     * - 총 무효화된 세션 수
     * - 기본 설정 정보
     *
     * @return 포맷된 통계 정보 문자열
     */
    public String getStatistics() {
        sessionLock.readLock().lock();
        try {
            // String.format(): 형식화된 문자열 생성
            // 여러 줄에 걸쳐 통계 정보를 보기 좋게 포맷팅
            return String.format(
                    "Session Statistics:\n" +
                            "  Active Sessions: %d\n" +
                            "  Total Created: %d\n" +
                            "  Total Expired: %d\n" +
                            "  Total Invalidated: %d\n" +
                            "  Default Max Inactive Interval: %d seconds",
                    sessions.size(),                    // 현재 활성 세션 수
                    totalSessionsCreated,              // 총 생성 세션 수
                    totalSessionsExpired,              // 총 만료 세션 수
                    totalSessionsInvalidated,          // 총 무효화 세션 수
                    defaultMaxInactiveInterval         // 기본 최대 비활성 간격
            );
        } finally {
            sessionLock.readLock().unlock();
        }
    }

    // ========== 설정 메서드들 ==========
    // volatile 필드들에 대한 getter/setter 메서드들
    // 런타임 설정 변경을 지원하여 재시작 없이 조정 가능

    /**
     * 기본 최대 비활성 간격 설정
     * @param interval 간격 (초)
     */
    public void setDefaultMaxInactiveInterval(int interval) {
        this.defaultMaxInactiveInterval = interval;
    }

    /**
     * 기본 최대 비활성 간격 반환
     * @return 간격 (초)
     */
    public int getDefaultMaxInactiveInterval() {
        return defaultMaxInactiveInterval;
    }

    /**
     * 쿠키 이름 설정
     * @param cookieName 새로운 쿠키 이름
     */
    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    /**
     * 쿠키 이름 반환
     * @return 현재 쿠키 이름
     */
    public String getCookieName() {
        return cookieName;
    }

    /**
     * 쿠키 경로 설정
     * @param cookiePath 새로운 쿠키 경로
     */
    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
    }

    /**
     * 쿠키 경로 반환
     * @return 현재 쿠키 경로
     */
    public String getCookiePath() {
        return cookiePath;
    }

    /**
     * 쿠키 보안 설정
     * @param cookieSecure HTTPS 전용 여부
     */
    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    /**
     * 쿠키 보안 설정 반환
     * @return HTTPS 전용 여부
     */
    public boolean isCookieSecure() {
        return cookieSecure;
    }

    /**
     * 쿠키 HttpOnly 설정
     * @param cookieHttpOnly JavaScript 접근 차단 여부
     */
    public void setCookieHttpOnly(boolean cookieHttpOnly) {
        this.cookieHttpOnly = cookieHttpOnly;
    }

    /**
     * 쿠키 HttpOnly 설정 반환
     * @return JavaScript 접근 차단 여부
     */
    public boolean isCookieHttpOnly() {
        return cookieHttpOnly;
    }

    // ========== 내부 메서드들 ==========

    /**
     * 보안 세션 ID를 생성합니다.
     *
     * 보안 요구사항:
     * - 예측 불가능한 랜덤 값
     * - 충분한 엔트로피 (256비트)
     * - 브루트 포스 공격 방지
     * - 세션 고정 공격 방지
     *
     * @return 생성된 세션 ID (16진수 문자열)
     */
    private String generateSessionId() {
        // ========== 랜덤 바이트 생성 ==========
        // sessionIdLength 길이의 바이트 배열 생성
        byte[] randomBytes = new byte[sessionIdLength];

        // nextBytes(): SecureRandom으로 바이트 배열을 랜덤 값으로 채움
        // 암호학적으로 안전한 난수 생성
        secureRandom.nextBytes(randomBytes);

        // ========== 16진수 문자열 변환 ==========
        StringBuilder sessionId = new StringBuilder();

        // for-each 루프로 각 바이트를 16진수로 변환
        for (byte b : randomBytes) {
            // String.format("%02X", b & 0xFF): 바이트를 2자리 대문자 16진수로 변환
            // %02X: 2자리 16진수, 부족하면 0으로 패딩, 대문자
            // b & 0xFF: byte를 unsigned로 처리 (음수 방지)
            sessionId.append(String.format("%02X", b & 0xFF));
        }

        // StringBuilder를 String으로 변환하여 반환
        return sessionId.toString();
    }

    /**
     * 만료된 세션을 제거합니다.
     *
     * private 메서드로 구현한 이유:
     * - 내부적으로만 사용되는 정리 로직
     * - 통계 업데이트와 함께 처리
     * - 코드 중복 방지
     *
     * @param sessionId 제거할 세션 ID
     */
    private void removeExpiredSession(String sessionId) {
        // ========== 저장소에서 제거 ==========
        // remove(): ConcurrentHashMap에서 엔트리 제거
        sessions.remove(sessionId);
        sessionInfos.remove(sessionId);

        // ========== 통계 업데이트 ==========
        totalSessionsExpired++;
    }

    /**
     * 세션 정리 스케줄러를 시작합니다.
     *
     * 스케줄링 정책:
     * - 5분 초기 지연: 서버 시작 직후 부하 방지
     * - 5분마다 실행: 적절한 정리 주기
     * - 고정 지연: 이전 작업 완료 후 다음 작업 시작
     *
     * private 메서드로 구현한 이유:
     * - 생성자에서만 호출
     * - 내부 초기화 로직
     * - 외부에서 직접 호출 불필요
     */
    private void startCleanupScheduler() {
        // scheduleWithFixedDelay(): 고정 지연 간격으로 반복 실행
        // 매개변수 설명:
        // - command: 실행할 작업 (람다 표현식)
        // - initialDelay: 5 (첫 실행까지 지연 시간)
        // - delay: 5 (각 실행 간 지연 시간)
        // - unit: TimeUnit.MINUTES (시간 단위)
        cleanupScheduler.scheduleWithFixedDelay(() -> {
                    try {
                        // ========== 세션 정리 작업 실행 ==========
                        // cleanupExpiredSessions(): 만료된 세션들을 찾아서 제거
                        cleanupExpiredSessions();
                    } catch (Exception e) {
                        // ========== 예외 처리 ==========
                        // 정리 작업 중 예외가 발생해도 스케줄러는 계속 실행되어야 함
                        // 로깅만 하고 스케줄러는 중단하지 않음
                        // System.err.println(): 표준 에러 출력 (로깅 시스템으로 교체 권장)
                        // e.getMessage(): 예외 메시지 추출
                        System.err.println("세션 정리 중 오류 발생: " + e.getMessage());

                        // 운영 환경에서는 로깅 프레임워크 사용 권장:
                        // logger.error("세션 정리 중 오류 발생", e);
                    }
                },
                5,              // initialDelay: 5분 후 첫 실행
                5,              // delay: 이후 5분마다 실행
                TimeUnit.MINUTES); // 시간 단위: 분

        // scheduleWithFixedDelay vs scheduleAtFixedRate 차이점:
        // - scheduleWithFixedDelay: 이전 작업 완료 후 지연 시간 후 다음 작업 시작
        // - scheduleAtFixedRate: 고정된 시간 간격으로 작업 시작 (겹칠 수 있음)
        //
        // 세션 정리는 시간이 오래 걸릴 수 있으므로 scheduleWithFixedDelay 사용
    }
}