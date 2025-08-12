package com.serverarch.container;

// Jakarta Servlet API 임포트
// jakarta.servlet.Servlet: HTTP 요청을 처리하는 서블릿 인터페이스
// jakarta.servlet.ServletException: 서블릿 처리 중 발생하는 예외 클래스
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;

// Java 리플렉션 API
// java.lang.reflect.Constructor: 클래스의 생성자 정보를 나타내는 클래스
import java.lang.reflect.Constructor;

// Java 동시성 라이브러리
// java.util.concurrent.ConcurrentHashMap: 스레드 안전한 해시맵 구현 클래스
// java.util.concurrent.locks.ReentrantLock: 재진입 가능한 락 구현 클래스
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

// Java 컬렉션 프레임워크
// java.util.Map: 키-값 쌍을 저장하는 컬렉션 인터페이스
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
     *
     * enum: 자바의 열거형 타입 (상수들의 집합을 정의)
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
     *
     * static: 외부 클래스의 인스턴스 없이도 생성 가능한 정적 중첩 클래스
     */
    public static class InstanceInfo {
        // 서블릿 클래스 정보를 저장하는 필드
        // Class<? extends Servlet>: Servlet을 상속받는 클래스 타입의 제네릭
        // ? extends Servlet: 와일드카드로 Servlet의 하위 타입만 허용
        private final Class<? extends Servlet> servletClass;

        // 인스턴스 생성 전략을 저장하는 필드
        private final InstanceStrategy strategy;

        // 생성 시간을 저장하는 필드 (밀리초)
        // long: 64비트 정수 기본 타입
        private final long creationTime;

        // 생성한 스레드 정보를 저장하는 필드
        // Thread: 자바의 스레드 클래스
        private final Thread creationThread;

        // 마지막 접근 시간을 저장하는 필드
        // volatile: 멀티스레드 환경에서 변수 가시성 보장
        private volatile long lastAccessTime;

        // 접근 횟수를 저장하는 필드
        // int: 32비트 정수 기본 타입
        private volatile int accessCount;

        // 파괴 상태를 저장하는 필드
        // boolean: 불린 기본 타입
        private volatile boolean destroyed;

        public InstanceInfo(Class<? extends Servlet> servletClass, InstanceStrategy strategy) {
            this.servletClass = servletClass;
            this.strategy = strategy;

            // System.currentTimeMillis(): 현재 시간을 밀리초로 반환하는 정적 메서드
            this.creationTime = System.currentTimeMillis();

            // Thread.currentThread(): 현재 실행 중인 스레드를 반환하는 정적 메서드
            this.creationThread = Thread.currentThread();
            this.lastAccessTime = creationTime;
            this.accessCount = 0;
            this.destroyed = false;
        }

        // Getters - 필드 값을 반환하는 메서드들
        public Class<? extends Servlet> getServletClass() {
            return servletClass;
        }

        public InstanceStrategy getStrategy() {
            return strategy;
        }

        public long getCreationTime() {
            return creationTime;
        }

        public Thread getCreationThread() {
            return creationThread;
        }

        public long getLastAccessTime() {
            return lastAccessTime;
        }

        public int getAccessCount() {
            return accessCount;
        }

        public boolean isDestroyed() {
            return destroyed;
        }

        // 상태 업데이트 메서드들
        // 패키지 프라이빗 접근 제어 (default)
        void updateAccess() {
            this.lastAccessTime = System.currentTimeMillis();

            // 전위 증가 연산자 (++): 값을 1 증가시킨 후 반환
            this.accessCount++;
        }

        void markDestroyed() {
            this.destroyed = true;
        }
    }

    /**
     * 서블릿 클래스별 인스턴스 캐시
     * 클래스 -> 인스턴스 매핑
     *
     * Map<Class<? extends Servlet>, Servlet>: 키는 서블릿 클래스, 값은 서블릿 인스턴스
     */
    private final Map<Class<? extends Servlet>, Servlet> singletonInstances;

    /**
     * 서블릿 인스턴스별 메타데이터
     * 인스턴스 -> 정보 매핑
     *
     * Map<Servlet, InstanceInfo>: 키는 서블릿 인스턴스, 값은 인스턴스 정보
     */
    private final Map<Servlet, InstanceInfo> instanceInfoMap;

    /**
     * 인스턴스 생성을 위한 락 맵
     * 클래스별로 별도의 락을 사용하여 성능 향상
     *
     * Map<Class<? extends Servlet>, ReentrantLock>: 키는 서블릿 클래스, 값은 락
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
     * volatile: 멀티스레드 환경에서 가시성 보장
     */
    private volatile long totalInstancesCreated;
    private volatile long totalInstancesDestroyed;

    /**
     * ServletInstanceManager 생성자
     *
     * @param defaultStrategy 기본 인스턴스 생성 전략
     */
    public ServletInstanceManager(InstanceStrategy defaultStrategy) {
        // 삼항 연산자로 null 체크 후 기본값 설정
        this.defaultStrategy = defaultStrategy != null ? defaultStrategy : InstanceStrategy.SINGLETON;

        // new ConcurrentHashMap<>(): 스레드 안전한 해시맵 생성
        this.singletonInstances = new ConcurrentHashMap<>();
        this.instanceInfoMap = new ConcurrentHashMap<>();
        this.creationLocks = new ConcurrentHashMap<>();

        // new ReentrantLock(): 재진입 가능한 락 생성
        this.managerLock = new ReentrantLock();
        this.customStrategies = new ConcurrentHashMap<>();
        this.totalInstancesCreated = 0;
        this.totalInstancesDestroyed = 0;
    }

    /**
     * 기본 전략으로 ServletInstanceManager를 생성합니다.
     */
    public ServletInstanceManager() {
        // this(): 같은 클래스의 다른 생성자 호출
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

        // switch 문: 값에 따른 분기 처리
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
        // singletonInstances.get(): Map의 값 조회 메서드
        Servlet instance = singletonInstances.get(servletClass);
        if (instance != null) {
            updateInstanceAccess(instance);
            return instance;
        }

        // 클래스별 락 획득
        // creationLocks.computeIfAbsent(): 키가 없으면 새 값 계산해서 추가
        // k -> new ReentrantLock(): 람다 표현식으로 새 락 생성
        ReentrantLock lock = creationLocks.computeIfAbsent(servletClass, k -> new ReentrantLock());

        // lock.lock(): 락 획득 (다른 스레드는 대기)
        lock.lock();
        try {
            // Double-check 패턴 - 락 획득 후 다시 확인
            instance = singletonInstances.get(servletClass);
            if (instance != null) {
                updateInstanceAccess(instance);
                return instance;
            }

            // 새 인스턴스 생성
            instance = createNewInstance(servletClass);

            // singletonInstances.put(): Map에 키-값 저장
            singletonInstances.put(servletClass, instance);

            return instance;

        } finally {
            // finally: 예외 발생 여부와 상관없이 실행
            // lock.unlock(): 락 해제
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
            // servletClass.getDeclaredConstructor(): 클래스의 선언된 생성자 반환
            // getDeclaredConstructor(): Class 클래스의 기본 생성자 조회 메서드
            Constructor<? extends Servlet> constructor = servletClass.getDeclaredConstructor();

            // constructor.setAccessible(true): 접근 제어 무시 (private 생성자도 접근 가능)
            constructor.setAccessible(true); // private 생성자도 접근 가능하게

            // constructor.newInstance(): 생성자를 통해 새 인스턴스 생성
            Servlet instance = constructor.newInstance();

            // 인스턴스 정보 등록
            InstanceStrategy strategy = getEffectiveStrategy(servletClass);
            InstanceInfo info = new InstanceInfo(servletClass, strategy);

            // instanceInfoMap.put(): Map에 인스턴스와 정보 저장
            instanceInfoMap.put(instance, info);

            // 통계 업데이트
            incrementCreatedCount();

            return instance;

        } catch (Exception e) {
            // servletClass.getName(): Class의 이름 반환 메서드
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

        // managerLock.lock(): 전체 매니저 락 획득
        managerLock.lock();
        try {
            // instanceInfoMap.remove(): Map에서 키-값 제거
            InstanceInfo info = instanceInfoMap.remove(instance);
            if (info != null) {
                info.markDestroyed();

                // 싱글톤 캐시에서 제거
                // info.getStrategy(): InstanceInfo의 전략 반환 메서드
                if (info.getStrategy() == InstanceStrategy.SINGLETON) {
                    // singletonInstances.remove(): Map에서 키-값 제거
                    singletonInstances.remove(info.getServletClass());
                }

                // 서블릿 정리
                try {
                    // instance.destroy(): Servlet 인터페이스의 정리 메서드
                    instance.destroy();
                } catch (Exception e) {
                    // 로깅만 하고 계속 진행
                    // System.err: 표준 에러 출력 스트림
                    // e.getMessage(): Exception의 메시지 반환 메서드
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
            // instanceInfoMap.entrySet(): Map의 모든 키-값 쌍을 Set<Entry>로 반환
            // .removeIf(): 조건에 맞는 요소들을 제거하는 메서드
            instanceInfoMap.entrySet().removeIf(entry -> {
                // entry.getKey(): Map.Entry의 키 반환
                Servlet instance = entry.getKey();
                // entry.getValue(): Map.Entry의 값 반환
                InstanceInfo info = entry.getValue();

                // servletClass.equals(): Object의 동등성 비교 메서드
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
            // instanceInfoMap.entrySet(): Map의 모든 키-값 쌍 반환
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
            // .clear(): 컬렉션의 모든 요소 제거
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

        // customStrategies.put(): Map에 커스텀 전략 저장
        customStrategies.put(servletClass, strategy);
    }

    /**
     * 서블릿 인스턴스의 정보를 반환합니다.
     */
    public InstanceInfo getInstanceInfo(Servlet instance) {
        // instanceInfoMap.get(): Map의 값 조회
        return instance != null ? instanceInfoMap.get(instance) : null;
    }

    /**
     * 현재 관리 중인 인스턴스 수를 반환합니다.
     */
    public int getActiveInstanceCount() {
        // instanceInfoMap.size(): Map의 크기 반환
        return instanceInfoMap.size();
    }

    /**
     * 특정 클래스의 활성 인스턴스 수를 반환합니다.
     */
    public int getActiveInstanceCount(Class<? extends Servlet> servletClass) {
        if (servletClass == null) {
            return 0;
        }

        // instanceInfoMap.values(): Map의 모든 값을 Collection으로 반환
        // .stream(): Collection을 Stream으로 변환
        // .filter(): 조건에 맞는 요소만 필터링
        // .count(): Stream의 요소 개수 반환
        // (int): long을 int로 형변환
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
        // String.format(): 형식화된 문자열 생성 정적 메서드
        // %s: 문자열 포맷 지정자
        // %d: 정수 포맷 지정자
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
        // customStrategies.getOrDefault(): Map에서 값 조회, 없으면 기본값 반환
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
        // 후위 증가 연산자 (++): 현재 값 사용 후 1 증가
        totalInstancesCreated++;
    }

    /**
     * 해제된 인스턴스 수를 증가시킵니다.
     */
    private void incrementDestroyedCount() {
        totalInstancesDestroyed++;
    }
}