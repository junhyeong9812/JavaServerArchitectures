package com.serverarch.container;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Map;

/**
 * 서블릿 인스턴스의 생성, 초기화, 소멸을 관리하는 클래스입니다.
 *
 * ServletInstanceManager는 서블릿 인스턴스의 완전한 생명주기를 관리하며,
 * 메모리 효율성과 스레드 안전성을 보장합니다.
 *
 * 주요 기능:
 * - 서블릿 인스턴스 생성 및 초기화
 * - 인스턴스 캐싱 및 재사용
 * - 스레드 안전한 인스턴스 관리
 * - 리소스 정리 및 메모리 누수 방지
 * - 인스턴스 생성 통계 및 모니터링
 */
public class ServletInstanceManager {

    /**
     * 서블릿 인스턴스 생성 전략을 정의하는 열거형입니다.
     */
    public enum InstanceStrategy {
        /**
         * 싱글톤 전략: 서블릿당 하나의 인스턴스만 생성
         * 대부분의 서블릿에서 사용하는 기본 전략
         */
        SINGLETON,

        /**
         * 프로토타입 전략: 요청마다 새로운 인스턴스 생성
         * 상태를 가지는 서블릿이나 특수한 경우에 사용
         */
        PROTOTYPE,

        /**
         * 풀링 전략: 인스턴스 풀을 유지하여 재사용
         * 인스턴스 생성 비용이 높은 경우에 사용
         */
        POOLED
    }

    /**
     * 서블릿 인스턴스 정보를 담는 클래스입니다.
     */
    public static class InstanceInfo {
        private final Class<? extends Servlet> servletClass;
        private final InstanceStrategy strategy;
        private final long creationTime;
        private final Thread creationThread;
        private volatile long lastAccessTime;
        private volatile int accessCount;
        private volatile boolean destroyed;

        public InstanceInfo(Class<? extends Servlet> servletClass, InstanceStrategy strategy) {
            this.servletClass = servletClass;
            this.strategy = strategy;
            this.creationTime = System.currentTimeMillis();
            this.creationThread = Thread.currentThread();
            this.lastAccessTime = creationTime;
            this.accessCount = 0;
            this.destroyed = false;
        }

        // Getters
        public Class<? extends Servlet> getServletClass() { return servletClass; }
        public InstanceStrategy getStrategy() { return strategy; }
        public long getCreationTime() { return creationTime; }
        public Thread getCreationThread() { return creationThread; }
        public long getLastAccessTime() { return lastAccessTime; }
        public int getAccessCount() { return accessCount; }
        public boolean isDestroyed() { return destroyed; }

        // 상태 업데이트 메서드들
        void updateAccess() {
            this.lastAccessTime = System.currentTimeMillis();
            this.accessCount++;
        }

        void markDestroyed() {
            this.destroyed = true;
        }
    }

    /**
     * 서블릿 클래스별 인스턴스 캐시
     * 클래스 -> 인스턴스 매핑
     */
    private final Map<Class<? extends Servlet>, Servlet> singletonInstances;

    /**
     * 서블릿 인스턴스별 메타데이터
     * 인스턴스 -> 정보 매핑
     */
    private final Map<Servlet, InstanceInfo> instanceInfoMap;

    /**
     * 인스턴스 생성을 위한 락 맵
     * 클래스별로 별도의 락을 사용하여 성능 향상
     */
    private final Map<Class<? extends Servlet>, ReentrantLock> creationLocks;

    /**
     * 전체 매니저 락
     */
    private final ReentrantLock managerLock;

    /**
     * 기본 인스턴스 생성 전략
     */
    private final InstanceStrategy defaultStrategy;

    /**
     * 클래스별 커스텀 전략
     */
    private final Map<Class<? extends Servlet>, InstanceStrategy> customStrategies;

    /**
     * 생성 통계
     */
    private volatile long totalInstancesCreated;
    private volatile long totalInstancesDestroyed;

    /**
     * ServletInstanceManager 생성자
     *
     * @param defaultStrategy 기본 인스턴스 생성 전략
     */
    public ServletInstanceManager(InstanceStrategy defaultStrategy) {
        this.defaultStrategy = defaultStrategy != null ? defaultStrategy : InstanceStrategy.SINGLETON;
        this.singletonInstances = new ConcurrentHashMap<>();
        this.instanceInfoMap = new ConcurrentHashMap<>();
        this.creationLocks = new ConcurrentHashMap<>();
        this.managerLock = new ReentrantLock();
        this.customStrategies = new ConcurrentHashMap<>();
        this.totalInstancesCreated = 0;
        this.totalInstancesDestroyed = 0;
    }

    /**
     * 기본 전략으로 ServletInstanceManager를 생성합니다.
     */
    public ServletInstanceManager() {
        this(InstanceStrategy.SINGLETON);
    }

    /**
     * 서블릿 인스턴스를 생성하거나 기존 인스턴스를 반환합니다.
     *
     * @param servletClass 서블릿 클래스
     * @return 서블릿 인스턴스
     * @throws ServletException 인스턴스 생성 실패 시
     */
    public Servlet getInstance(Class<? extends Servlet> servletClass) throws ServletException {
        if (servletClass == null) {
            throw new IllegalArgumentException("서블릿 클래스가 null입니다");
        }

        InstanceStrategy strategy = getEffectiveStrategy(servletClass);

        switch (strategy) {
            case SINGLETON:
                return getSingletonInstance(servletClass);
            case PROTOTYPE:
                return createNewInstance(servletClass);
            case POOLED:
                // 현재는 싱글톤으로 처리 (향후 풀링 구현 가능)
                return getSingletonInstance(servletClass);
            default:
                throw new ServletException("지원하지 않는 인스턴스 전략: " + strategy);
        }
    }

    /**
     * 싱글톤 인스턴스를 반환합니다.
     * 스레드 안전하게 단일 인스턴스를 보장합니다.
     */
    private Servlet getSingletonInstance(Class<? extends Servlet> servletClass)
            throws ServletException {

        // 먼저 캐시 확인 (빠른 경로)
        Servlet instance = singletonInstances.get(servletClass);
        if (instance != null) {
            updateInstanceAccess(instance);
            return instance;
        }

        // 클래스별 락 획득
        ReentrantLock lock = creationLocks.computeIfAbsent(servletClass, k -> new ReentrantLock());
        lock.lock();
        try {
            // Double-check 패턴
            instance = singletonInstances.get(servletClass);
            if (instance != null) {
                updateInstanceAccess(instance);
                return instance;
            }

            // 새 인스턴스 생성
            instance = createNewInstance(servletClass);
            singletonInstances.put(servletClass, instance);

            return instance;

        } finally {
            lock.unlock();
        }
    }

    /**
     * 새로운 서블릿 인스턴스를 생성합니다.
     */
    private Servlet createNewInstance(Class<? extends Servlet> servletClass)
            throws ServletException {

        try {
            // 기본 생성자 사용
            Constructor<? extends Servlet> constructor = servletClass.getDeclaredConstructor();
            constructor.setAccessible(true); // private 생성자도 접근 가능하게

            Servlet instance = constructor.newInstance();

            // 인스턴스 정보 등록
            InstanceStrategy strategy = getEffectiveStrategy(servletClass);
            InstanceInfo info = new InstanceInfo(servletClass, strategy);
            instanceInfoMap.put(instance, info);

            // 통계 업데이트
            incrementCreatedCount();

            return instance;

        } catch (Exception e) {
            throw new ServletException("서블릿 인스턴스 생성 실패: " + servletClass.getName(), e);
        }
    }

    /**
     * 서블릿 인스턴스를 해제합니다.
     *
     * @param instance 해제할 서블릿 인스턴스
     */
    public void destroyInstance(Servlet instance) {
        if (instance == null) {
            return;
        }

        managerLock.lock();
        try {
            InstanceInfo info = instanceInfoMap.remove(instance);
            if (info != null) {
                info.markDestroyed();

                // 싱글톤 캐시에서 제거
                if (info.getStrategy() == InstanceStrategy.SINGLETON) {
                    singletonInstances.remove(info.getServletClass());
                }

                // 서블릿 정리
                try {
                    instance.destroy();
                } catch (Exception e) {
                    // 로깅만 하고 계속 진행
                    System.err.println("서블릿 인스턴스 정리 중 오류 발생: " + e.getMessage());
                }

                // 통계 업데이트
                incrementDestroyedCount();
            }
        } finally {
            managerLock.unlock();
        }
    }

    /**
     * 특정 서블릿 클래스의 모든 인스턴스를 해제합니다.
     */
    public void destroyInstancesOfClass(Class<? extends Servlet> servletClass) {
        if (servletClass == null) {
            return;
        }

        managerLock.lock();
        try {
            // 해당 클래스의 모든 인스턴스 찾기
            instanceInfoMap.entrySet().removeIf(entry -> {
                Servlet instance = entry.getKey();
                InstanceInfo info = entry.getValue();

                if (servletClass.equals(info.getServletClass())) {
                    info.markDestroyed();

                    try {
                        instance.destroy();
                    } catch (Exception e) {
                        System.err.println("서블릿 인스턴스 정리 중 오류 발생: " + e.getMessage());
                    }

                    incrementDestroyedCount();
                    return true; // 맵에서 제거
                }

                return false;
            });

            // 싱글톤 캐시에서도 제거
            singletonInstances.remove(servletClass);

        } finally {
            managerLock.unlock();
        }
    }

    /**
     * 모든 서블릿 인스턴스를 해제합니다.
     */
    public void destroyAllInstances() {
        managerLock.lock();
        try {
            // 모든 인스턴스 정리
            for (Map.Entry<Servlet, InstanceInfo> entry : instanceInfoMap.entrySet()) {
                Servlet instance = entry.getKey();
                InstanceInfo info = entry.getValue();

                info.markDestroyed();

                try {
                    instance.destroy();
                } catch (Exception e) {
                    System.err.println("서블릿 인스턴스 정리 중 오류 발생: " + e.getMessage());
                }

                incrementDestroyedCount();
            }

            // 모든 캐시 정리
            instanceInfoMap.clear();
            singletonInstances.clear();
            creationLocks.clear();

        } finally {
            managerLock.unlock();
        }
    }

    /**
     * 특정 서블릿 클래스에 대한 인스턴스 생성 전략을 설정합니다.
     */
    public void setInstanceStrategy(Class<? extends Servlet> servletClass,
                                    InstanceStrategy strategy) {
        if (servletClass == null || strategy == null) {
            throw new IllegalArgumentException("서블릿 클래스와 전략은 null일 수 없습니다");
        }

        customStrategies.put(servletClass, strategy);
    }

    /**
     * 서블릿 인스턴스의 정보를 반환합니다.
     */
    public InstanceInfo getInstanceInfo(Servlet instance) {
        return instance != null ? instanceInfoMap.get(instance) : null;
    }

    /**
     * 현재 관리 중인 인스턴스 수를 반환합니다.
     */
    public int getActiveInstanceCount() {
        return instanceInfoMap.size();
    }

    /**
     * 특정 클래스의 활성 인스턴스 수를 반환합니다.
     */
    public int getActiveInstanceCount(Class<? extends Servlet> servletClass) {
        if (servletClass == null) {
            return 0;
        }

        return (int) instanceInfoMap.values().stream()
                .filter(info -> servletClass.equals(info.getServletClass()))
                .count();
    }

    /**
     * 생성된 총 인스턴스 수를 반환합니다.
     */
    public long getTotalInstancesCreated() {
        return totalInstancesCreated;
    }

    /**
     * 해제된 총 인스턴스 수를 반환합니다.
     */
    public long getTotalInstancesDestroyed() {
        return totalInstancesDestroyed;
    }

    /**
     * 매니저의 상태 정보를 반환합니다.
     */
    public String getStatusInfo() {
        return String.format(
                "ServletInstanceManager Status:\n" +
                        "  Default Strategy: %s\n" +
                        "  Active Instances: %d\n" +
                        "  Total Created: %d\n" +
                        "  Total Destroyed: %d\n" +
                        "  Singleton Cache Size: %d\n" +
                        "  Custom Strategies: %d",
                defaultStrategy,
                getActiveInstanceCount(),
                totalInstancesCreated,
                totalInstancesDestroyed,
                singletonInstances.size(),
                customStrategies.size()
        );
    }

    /**
     * 서블릿 클래스에 대한 효과적인 전략을 반환합니다.
     */
    private InstanceStrategy getEffectiveStrategy(Class<? extends Servlet> servletClass) {
        return customStrategies.getOrDefault(servletClass, defaultStrategy);
    }

    /**
     * 인스턴스 접근 정보를 업데이트합니다.
     */
    private void updateInstanceAccess(Servlet instance) {
        InstanceInfo info = instanceInfoMap.get(instance);
        if (info != null) {
            info.updateAccess();
        }
    }

    /**
     * 생성된 인스턴스 수를 증가시킵니다.
     */
    private void incrementCreatedCount() {
        totalInstancesCreated++;
    }

    /**
     * 해제된 인스턴스 수를 증가시킵니다.
     */
    private void incrementDestroyedCount() {
        totalInstancesDestroyed++;
    }
}
