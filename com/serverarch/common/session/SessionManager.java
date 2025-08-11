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
 */
public class SessionManager {

    /**
     * 세션 정보를 담는 내부 클래스입니다.
     */
    public static class SessionInfo {
        private final String sessionId;
        private final long creationTime;
        private volatile long lastAccessedTime;
        private volatile int maxInactiveInterval;
        private volatile boolean valid;
        private volatile boolean newSession;

        public SessionInfo(String sessionId, int defaultMaxInactiveInterval) {
            this.sessionId = sessionId;
            this.creationTime = System.currentTimeMillis();
            this.lastAccessedTime = creationTime;
            this.maxInactiveInterval = defaultMaxInactiveInterval;
            this.valid = true;
            this.newSession = true;
        }

        // Getters
        public String getSessionId() { return sessionId; }
        public long getCreationTime() { return creationTime; }
        public long getLastAccessedTime() { return lastAccessedTime; }
        public int getMaxInactiveInterval() { return maxInactiveInterval; }
        public boolean isValid() { return valid; }
        public boolean isNew() { return newSession; }

        // 상태 업데이트 메서드들
        void updateLastAccessedTime() {
            this.lastAccessedTime = System.currentTimeMillis();
            this.newSession = false;
        }

        void setMaxInactiveInterval(int interval) {
            this.maxInactiveInterval = interval;
        }

        void invalidate() {
            this.valid = false;
        }

        boolean isExpired() {
            if (!valid || maxInactiveInterval <= 0) {
                return !valid;
            }

            long currentTime = System.currentTimeMillis();
            long inactiveTime = (currentTime - lastAccessedTime) / 1000;
            return inactiveTime > maxInactiveInterval;
        }
    }

    /**
     * 세션 ID 생성기
     */
    private final SecureRandom secureRandom;

    /**
     * 세션 저장소 (세션 ID -> HttpSession)
     */
    private final Map<String, HttpSession> sessions;

    /**
     * 세션 메타데이터 저장소 (세션 ID -> SessionInfo)
     */
    private final Map<String, SessionInfo> sessionInfos;

    /**
     * 읽기/쓰기 락 (성능 최적화)
     */
    private final ReentrantReadWriteLock sessionLock;

    /**
     * 세션 만료 정리를 위한 스케줄러
     */
    private final ScheduledExecutorService cleanupScheduler;

    /**
     * 서블릿 컨텍스트 참조
     */
    private final ServletContext servletContext;

    /**
     * 세션 설정
     */
    private volatile int defaultMaxInactiveInterval; // 기본 30분
    private volatile int sessionIdLength; // 세션 ID 길이
    private volatile String cookieName; // 세션 쿠키 이름
    private volatile String cookiePath; // 쿠키 경로
    private volatile String cookieDomain; // 쿠키 도메인
    private volatile boolean cookieSecure; // HTTPS 전용
    private volatile boolean cookieHttpOnly; // JavaScript 접근 차단
    private volatile int cookieMaxAge; // 쿠키 최대 생존시간

    /**
     * 통계 정보
     */
    private volatile long totalSessionsCreated;
    private volatile long totalSessionsExpired;
    private volatile long totalSessionsInvalidated;

    /**
     * SessionManager 생성자
     *
     * @param servletContext 서블릿 컨텍스트
     */
    public SessionManager(ServletContext servletContext) {
        this.servletContext = servletContext;
        this.secureRandom = new SecureRandom();
        this.sessions = new ConcurrentHashMap<>();
        this.sessionInfos = new ConcurrentHashMap<>();
        this.sessionLock = new ReentrantReadWriteLock();

        // 기본 설정
        this.defaultMaxInactiveInterval = 30 * 60; // 30분
        this.sessionIdLength = 32;
        this.cookieName = "JSESSIONID";
        this.cookiePath = "/";
        this.cookieDomain = null;
        this.cookieSecure = false;
        this.cookieHttpOnly = true;
        this.cookieMaxAge = -1; // 브라우저 세션 쿠키

        // 통계 초기화
        this.totalSessionsCreated = 0;
        this.totalSessionsExpired = 0;
        this.totalSessionsInvalidated = 0;

        // 세션 정리 스케줄러 시작 (5분마다 실행)
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SessionCleanupThread");
            t.setDaemon(true);
            return t;
        });

        startCleanupScheduler();
    }

    /**
     * 새로운 세션을 생성합니다.
     *
     * @return 생성된 HttpSession 객체
     */
    public HttpSession createSession() {
        String sessionId = generateSessionId();

        sessionLock.writeLock().lock();
        try {
            // 중복 ID 확인 (매우 낮은 확률이지만)
            while (sessions.containsKey(sessionId)) {
                sessionId = generateSessionId();
            }

            // 세션 정보 생성
            SessionInfo sessionInfo = new SessionInfo(sessionId, defaultMaxInactiveInterval);
            sessionInfos.put(sessionId, sessionInfo);

            // HttpSession 구현체 생성
            HttpSession session = new HttpSessionImpl(sessionId, servletContext, this);
            sessions.put(sessionId, session);

            // 통계 업데이트
            totalSessionsCreated++;

            return session;

        } finally {
            sessionLock.writeLock().unlock();
        }
    }

    /**
     * 세션 ID로 세션을 조회합니다.
     *
     * @param sessionId 세션 ID
     * @return HttpSession 객체, 없거나 만료되면 null
     */
    public HttpSession getSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }

        sessionLock.readLock().lock();
        try {
            SessionInfo sessionInfo = sessionInfos.get(sessionId);
            if (sessionInfo == null || !sessionInfo.isValid() || sessionInfo.isExpired()) {
                // 만료된 세션 정리
                if (sessionInfo != null && sessionInfo.isExpired()) {
                    removeExpiredSession(sessionId);
                }
                return null;
            }

            // 접근 시간 업데이트
            sessionInfo.updateLastAccessedTime();

            return sessions.get(sessionId);

        } finally {
            sessionLock.readLock().unlock();
        }
    }

    /**
     * 세션을 무효화합니다.
     *
     * @param sessionId 무효화할 세션 ID
     * @return 무효화 성공 여부
     */
    public boolean invalidateSession(String sessionId) {
        if (sessionId == null) {
            return false;
        }

        sessionLock.writeLock().lock();
        try {
            SessionInfo sessionInfo = sessionInfos.get(sessionId);
            if (sessionInfo == null) {
                return false;
            }

            // 세션 무효화
            sessionInfo.invalidate();

            // 저장소에서 제거
            sessions.remove(sessionId);
            sessionInfos.remove(sessionId);

            // 통계 업데이트
            totalSessionsInvalidated++;

            return true;

        } finally {
            sessionLock.writeLock().unlock();
        }
    }

    /**
     * 세션 ID가 유효한지 확인합니다.
     */
    public boolean isValidSessionId(String sessionId) {
        if (sessionId == null) {
            return false;
        }

        sessionLock.readLock().lock();
        try {
            SessionInfo sessionInfo = sessionInfos.get(sessionId);
            return sessionInfo != null && sessionInfo.isValid() && !sessionInfo.isExpired();
        } finally {
            sessionLock.readLock().unlock();
        }
    }

    /**
     * 세션 정보를 반환합니다.
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
     */
    public void setSessionMaxInactiveInterval(String sessionId, int interval) {
        if (sessionId == null) {
            return;
        }

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

    /**
     * 만료된 세션들을 정리합니다.
     */
    public void cleanupExpiredSessions() {
        List<String> expiredSessionIds = new ArrayList<>();

        // 읽기 락으로 만료된 세션 ID 수집
        sessionLock.readLock().lock();
        try {
            for (Map.Entry<String, SessionInfo> entry : sessionInfos.entrySet()) {
                SessionInfo sessionInfo = entry.getValue();
                if (!sessionInfo.isValid() || sessionInfo.isExpired()) {
                    expiredSessionIds.add(entry.getKey());
                }
            }
        } finally {
            sessionLock.readLock().unlock();
        }

        // 쓰기 락으로 만료된 세션 제거
        if (!expiredSessionIds.isEmpty()) {
            sessionLock.writeLock().lock();
            try {
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
     */
    public int getActiveSessionCount() {
        sessionLock.readLock().lock();
        try {
            return sessions.size();
        } finally {
            sessionLock.readLock().unlock();
        }
    }

    /**
     * 모든 세션을 무효화합니다.
     */
    public void invalidateAllSessions() {
        sessionLock.writeLock().lock();
        try {
            // 모든 세션 무효화
            for (SessionInfo sessionInfo : sessionInfos.values()) {
                sessionInfo.invalidate();
                totalSessionsInvalidated++;
            }

            // 저장소 정리
            sessions.clear();
            sessionInfos.clear();

        } finally {
            sessionLock.writeLock().unlock();
        }
    }

    /**
     * SessionManager를 종료합니다.
     */
    public void shutdown() {
        // 정리 스케줄러 종료
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 모든 세션 무효화
        invalidateAllSessions();
    }

    /**
     * 통계 정보를 반환합니다.
     */
    public String getStatistics() {
        sessionLock.readLock().lock();
        try {
            return String.format(
                    "Session Statistics:\n" +
                            "  Active Sessions: %d\n" +
                            "  Total Created: %d\n" +
                            "  Total Expired: %d\n" +
                            "  Total Invalidated: %d\n" +
                            "  Default Max Inactive Interval: %d seconds",
                    sessions.size(),
                    totalSessionsCreated,
                    totalSessionsExpired,
                    totalSessionsInvalidated,
                    defaultMaxInactiveInterval
            );
        } finally {
            sessionLock.readLock().unlock();
        }
    }

    // ========== 설정 메서드들 ==========

    public void setDefaultMaxInactiveInterval(int interval) {
        this.defaultMaxInactiveInterval = interval;
    }

    public int getDefaultMaxInactiveInterval() {
        return defaultMaxInactiveInterval;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
    }

    public String getCookiePath() {
        return cookiePath;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieHttpOnly(boolean cookieHttpOnly) {
        this.cookieHttpOnly = cookieHttpOnly;
    }

    public boolean isCookieHttpOnly() {
        return cookieHttpOnly;
    }

    // ========== 내부 메서드들 ==========

    /**
     * 보안 세션 ID를 생성합니다.
     */
    private String generateSessionId() {
        byte[] randomBytes = new byte[sessionIdLength];
        secureRandom.nextBytes(randomBytes);

        StringBuilder sessionId = new StringBuilder();
        for (byte b : randomBytes) {
            sessionId.append(String.format("%02X", b & 0xFF));
        }

        return sessionId.toString();
    }

    /**
     * 만료된 세션을 제거합니다.
     */
    private void removeExpiredSession(String sessionId) {
        sessions.remove(sessionId);
        sessionInfos.remove(sessionId);
        totalSessionsExpired++;
    }

    /**
     * 세션 정리 스케줄러를 시작합니다.
     */
    private void startCleanupScheduler() {
        cleanupScheduler.scheduleWithFixedDelay(() -> {
            try {
                cleanupExpiredSessions();
            } catch (Exception e) {
                // 로깅만 하고 스케줄러는 계속 실행
                System.err.println("세션 정리 중 오류 발생: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.MINUTES); // 5분 초기 지연, 5분마다 실행
    }
}
