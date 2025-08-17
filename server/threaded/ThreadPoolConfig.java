package server.threaded;

/**
 * 스레드풀 설정 클래스
 *
 * 이 클래스는 ThreadPoolManager의 동작을 제어하는 모든 설정을 관리합니다.
 * Tomcat 스타일의 스레드풀 구현에 필요한 파라미터들을 정의하며,
 * 서버의 동시 처리 능력과 리소스 사용량에 직접적인 영향을 미칩니다.
 *
 * 스레드풀 아키텍처:
 * 1. Core Pool: 항상 유지되는 기본 스레드들
 * 2. Maximum Pool: 부하 급증시 생성 가능한 최대 스레드 수
 * 3. Queue: 스레드가 모두 사용 중일 때 작업 대기 공간
 * 4. Keep-Alive: 유휴 스레드의 생존 시간
 *
 * Tomcat 스타일의 특징:
 * - 큐보다 스레드 생성을 우선 (빠른 응답성)
 * - 부하 급증시 즉시 스레드 확장
 * - 부하 감소시 점진적 스레드 축소
 *
 * 성능 고려사항:
 * - 스레드 수 vs CPU 코어 수
 * - 메모리 사용량 (스레드당 스택 크기)
 * - 컨텍스트 스위칭 비용
 * - I/O 대기시간과 CPU 집약적 작업의 비율
 */
public class ThreadPoolConfig {

    // === 기본 스레드풀 크기 설정 ===

    /**
     * 코어 풀 크기 (Core Pool Size)
     *
     * 항상 유지되는 기본 스레드의 개수입니다.
     * 이 수만큼의 스레드는 작업이 없어도 계속 살아있습니다.
     *
     * 기본값: 10개
     *
     * 코어 풀의 특성:
     * - 서버 시작시 미리 생성 (prestartAllCoreThreads)
     * - Keep-Alive 시간과 무관하게 유지
     * - allowCoreThreadTimeOut이 true인 경우에만 종료 가능
     *
     * 설정 가이드:
     * - CPU 코어 수와 연관: 일반적으로 CPU 코어 수의 1-2배
     * - I/O 집약적 작업: CPU 코어 수의 2-4배
     * - CPU 집약적 작업: CPU 코어 수와 동일하거나 +1
     * - 메모리 제약: 스레드당 1MB 스택 메모리 고려
     *
     * 예시:
     * - 4코어 CPU, 웹 서버: 8-16개
     * - 8코어 CPU, API 서버: 16-32개
     * - 2코어 CPU, 개발환경: 4-8개
     */
    private int corePoolSize = 10;

    /**
     * 최대 풀 크기 (Maximum Pool Size)
     *
     * 부하가 급증했을 때 생성할 수 있는 최대 스레드 개수입니다.
     * 큐가 가득 찼을 때 이 수까지 스레드를 추가로 생성합니다.
     *
     * 기본값: 100개
     *
     * 최대 풀의 역할:
     * - 갑작스러운 트래픽 급증 대응
     * - 시스템 리소스 보호 (무제한 스레드 생성 방지)
     * - 메모리 사용량 상한선 제공
     *
     * Tomcat 스타일에서의 동작:
     * 1. 활성 스레드 < 코어 크기 → 기본 처리
     * 2. 활성 스레드 >= 코어 크기 && 현재 스레드 < 최대 크기 → 즉시 새 스레드 생성
     * 3. 현재 스레드 >= 최대 크기 → 큐에 대기
     * 4. 큐 가득참 → RejectedExecutionHandler 호출
     *
     * 설정 고려사항:
     * - 시스템 메모리 한계 (최대크기 × 1MB)
     * - OS 스레드 한계 확인
     * - 데이터베이스 연결 풀 크기와 조화
     * - 모니터링으로 실제 필요 스레드 수 확인
     */
    private int maxPoolSize = 100;

    // === 큐 설정 ===

    /**
     * 큐 용량 (Queue Capacity)
     *
     * 모든 스레드가 사용 중일 때 작업을 대기시킬 큐의 크기입니다.
     * Tomcat 스타일에서는 스레드 생성을 우선하므로 큐 사용 빈도가 낮습니다.
     *
     * 기본값: 200개
     *
     * 큐의 역할:
     * - 최대 스레드 수 도달 후 추가 작업 저장
     * - 일시적인 부하 스파이크 흡수
     * - 스레드 생성/소멸 오버헤드 감소
     *
     * Tomcat 스타일 큐 동작:
     * - offer() 메서드 오버라이드
     * - 새 스레드 생성 가능하면 큐에 저장하지 않음 (false 반환)
     * - 최대 스레드 도달 후에만 큐 사용
     *
     * 큐 크기 설정 가이드:
     * - 작은 큐 (0-50): 빠른 응답, 많은 스레드 사용
     * - 중간 큐 (100-500): 균형잡힌 설정
     * - 큰 큐 (1000+): 메모리 사용, 느린 응답 가능성
     *
     * 무제한 큐의 위험성:
     * - 메모리 부족 (OutOfMemoryError)
     * - 응답 시간 증가
     * - 시스템 전체 지연
     */
    private int queueCapacity = 200;

    // === 스레드 생명주기 설정 ===

    /**
     * Keep-Alive 시간 (초)
     *
     * 코어 풀 크기를 초과하는 유휴 스레드가 종료되기까지의 대기 시간입니다.
     *
     * 기본값: 60초
     *
     * Keep-Alive의 동작:
     * - 유휴 스레드가 이 시간만큼 작업을 받지 못하면 종료
     * - 코어 풀 크기까지만 스레드를 유지
     * - allowCoreThreadTimeOut이 true면 코어 스레드도 종료 가능
     *
     * 시간 설정 가이드:
     * - 짧은 시간 (10-30초):
     *   장점: 빠른 리소스 회수, 메모리 절약
     *   단점: 스레드 생성/소멸 오버헤드 증가
     *
     * - 긴 시간 (60-300초):
     *   장점: 스레드 재사용률 높음, 오버헤드 감소
     *   단점: 메모리 사용량 유지
     *
     * 트래픽 패턴 고려:
     * - 지속적 트래픽: 긴 Keep-Alive
     * - 간헐적 트래픽: 짧은 Keep-Alive
     * - 예측 불가능한 트래픽: 중간 값
     */
    private long keepAliveTime = 60; // seconds

    // === 모니터링 설정 ===

    /**
     * 모니터 간격 (초)
     *
     * 스레드풀 상태를 모니터링하고 통계를 출력하는 주기입니다.
     *
     * 기본값: 10초
     *
     * 모니터링 정보:
     * - 현재 풀 크기, 활성 스레드 수
     * - 큐 크기, 완료된 작업 수
     * - 거부된 작업 수, 평균 처리 시간
     * - 최대 동시 활성 스레드 수
     *
     * 주기 설정 고려사항:
     * - 짧은 주기 (5-10초): 실시간 모니터링, 디버깅에 유용
     * - 중간 주기 (30-60초): 일반적인 운영 환경
     * - 긴 주기 (300초+): 안정적인 프로덕션 환경
     *
     * 오버헤드:
     * - 통계 수집 CPU 비용
     * - 로그 I/O 비용
     * - 메모리 사용량 (통계 데이터)
     */
    private int monitorInterval = 10; // seconds

    /**
     * 스케일 단계 (Scale Step)
     *
     * 스레드풀 크기 조정시 한 번에 변경할 스레드 개수입니다.
     * (현재 구현에서는 참고용, 향후 자동 스케일링 기능 확장 가능)
     *
     * 기본값: 5개
     *
     * 자동 스케일링 시나리오:
     * - 부하 증가 감지시 scaleStep만큼 스레드 증가
     * - 부하 감소 감지시 scaleStep만큼 스레드 감소
     * - 급격한 변경보다는 점진적 조정 선호
     *
     * 미래 확장 가능성:
     * - 자동 스케일링 알고리즘
     * - 부하 예측 기반 사전 스케일링
     * - 시간대별 스케줄링
     */
    private int scaleStep = 5;

    /**
     * 디버그 모드 활성화 여부
     *
     * 스레드풀의 상세한 디버깅 정보 출력을 제어합니다.
     *
     * 기본값: false
     *
     * 디버그 정보:
     * - 스레드 생성/소멸 로그
     * - 작업 제출/완료 추적
     * - 큐 상태 변화
     * - 거부 처리 상세 로그
     *
     * 성능 영향:
     * - 로그 생성 CPU 오버헤드
     * - 문자열 처리 비용
     * - I/O 대기 시간
     * - 메모리 사용량 증가
     */
    private boolean debugMode = false;

    /**
     * 기본 생성자
     *
     * 모든 설정을 기본값으로 초기화합니다.
     * 기본값들은 일반적인 웹 서버 환경에 적합하도록 선택되었습니다.
     */
    public ThreadPoolConfig() {}

    // === Getter/Setter 메서드들 (Fluent Interface) ===

    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * 코어 풀 크기 설정
     *
     * @param corePoolSize 코어 풀 크기 (1 이상)
     * @return 현재 객체 (메서드 체이닝 지원)
     */
    public ThreadPoolConfig setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
        return this; // Fluent Interface: config.setCorePoolSize(10).setMaxPoolSize(50)
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public ThreadPoolConfig setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
        return this;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public ThreadPoolConfig setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
        return this;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public ThreadPoolConfig setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
        return this;
    }

    public int getMonitorInterval() {
        return monitorInterval;
    }

    public ThreadPoolConfig setMonitorInterval(int monitorInterval) {
        this.monitorInterval = monitorInterval;
        return this;
    }

    public int getScaleStep() {
        return scaleStep;
    }

    public ThreadPoolConfig setScaleStep(int scaleStep) {
        this.scaleStep = scaleStep;
        return this;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public ThreadPoolConfig setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        return this;
    }

    /**
     * 설정 유효성 검증 메서드
     *
     * 모든 설정값이 논리적으로 일관되고 유효한 범위에 있는지 확인합니다.
     * 잘못된 설정으로 인한 런타임 오류나 성능 문제를 사전에 방지합니다.
     *
     * @throws IllegalArgumentException 유효하지 않은 설정값이 있을 때
     */
    public void validate() {
        /*
         * 코어 풀 크기 검증
         *
         * 최소 1개 이상의 스레드가 필요합니다.
         * 0이나 음수는 의미가 없으며 스레드풀이 작동하지 않습니다.
         */
        if (corePoolSize <= 0) {
            throw new IllegalArgumentException("Core pool size must be positive");
        }

        /*
         * 최대 풀 크기 검증
         *
         * 최대 풀 크기는 코어 풀 크기보다 크거나 같아야 합니다.
         * 작으면 논리적으로 모순되며 스레드풀이 제대로 확장되지 않습니다.
         */
        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("Max pool size must be >= core pool size");
        }

        /*
         * 큐 용량 검증
         *
         * 음수는 허용하지 않습니다.
         * 0은 큐를 사용하지 않음을 의미하므로 허용됩니다.
         * (SynchronousQueue 사용과 유사한 효과)
         */
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("Queue capacity must be non-negative");
        }

        /*
         * Keep-Alive 시간 검증
         *
         * 음수는 허용하지 않습니다.
         * 0은 즉시 종료를 의미하므로 허용됩니다.
         */
        if (keepAliveTime < 0) {
            throw new IllegalArgumentException("Keep alive time must be non-negative");
        }

        /*
         * 모니터 간격 검증
         *
         * 양수여야 합니다. 0이나 음수는 모니터링이 불가능합니다.
         */
        if (monitorInterval <= 0) {
            throw new IllegalArgumentException("Monitor interval must be positive");
        }

        /*
         * 스케일 단계 검증
         *
         * 양수여야 합니다. 0이나 음수는 스케일링이 불가능합니다.
         */
        if (scaleStep <= 0) {
            throw new IllegalArgumentException("Scale step must be positive");
        }
    }

    // === Factory 메서드들 ===
    // 다양한 시나리오에 최적화된 설정 프로필 제공

    /**
     * 기본 설정 생성
     *
     * 일반적인 웹 서버 환경에 적합한 균형잡힌 설정입니다.
     *
     * @return 기본 설정이 적용된 ThreadPoolConfig 객체
     */
    public static ThreadPoolConfig defaultConfig() {
        return new ThreadPoolConfig();
    }

    /**
     * 고성능 설정 생성
     *
     * 높은 처리량과 동시성이 필요한 환경을 위한 설정입니다.
     * 메모리 사용량이 증가하는 대신 성능을 최대화합니다.
     *
     * 특징:
     * - 큰 코어/최대 풀 크기 (더 많은 동시 처리)
     * - 큰 큐 용량 (트래픽 스파이크 흡수)
     * - 큰 스케일 단계 (빠른 확장/축소)
     * - 짧은 모니터 간격 (실시간 모니터링)
     *
     * 적용 시나리오:
     * - 대용량 트래픽 웹사이트
     * - API 게이트웨이
     * - 마이크로서비스 환경
     * - 배치 처리 시스템
     *
     * @return 고성능 설정이 적용된 객체
     */
    public static ThreadPoolConfig highPerformanceConfig() {
        return new ThreadPoolConfig()
                .setCorePoolSize(20)         // 기본의 2배 (더 많은 기본 스레드)
                .setMaxPoolSize(200)         // 기본의 2배 (더 높은 확장성)
                .setQueueCapacity(500)       // 기본의 2.5배 (더 많은 버퍼링)
                .setScaleStep(10)            // 기본의 2배 (더 빠른 스케일링)
                .setMonitorInterval(5);      // 기본의 절반 (더 빈번한 모니터링)
    }

    /**
     * 개발용 설정 생성
     *
     * 개발과 디버깅에 최적화된 설정입니다.
     * 리소스 사용을 최소화하면서도 디버깅 정보를 제공합니다.
     *
     * 특징:
     * - 작은 풀 크기 (리소스 절약)
     * - 작은 큐 용량 (빠른 피드백)
     * - 디버그 모드 활성화
     * - 짧은 모니터 간격 (실시간 확인)
     *
     * 적용 시나리오:
     * - 로컬 개발 환경
     * - 단위 테스트
     * - 통합 테스트
     * - 프로토타입 개발
     *
     * @return 개발용 설정이 적용된 객체
     */
    public static ThreadPoolConfig developmentConfig() {
        return new ThreadPoolConfig()
                .setCorePoolSize(2)          // 최소한의 코어 스레드
                .setMaxPoolSize(10)          // 작은 최대 크기
                .setQueueCapacity(50)        // 작은 큐 용량
                .setDebugMode(true)          // 상세한 디버그 로그
                .setMonitorInterval(5);      // 빠른 피드백을 위한 짧은 간격
    }

    /**
     * 설정 정보를 문자열로 반환
     *
     * 주요 설정값들을 간결하게 표현하여 로깅과 디버깅에 사용합니다.
     *
     * @return 설정 요약 문자열
     */
    @Override
    public String toString() {
        return String.format(
                "ThreadPoolConfig{core=%d, max=%d, queue=%d, keepAlive=%ds, " +
                        "monitor=%ds, scaleStep=%d, debug=%s}",
                corePoolSize, maxPoolSize, queueCapacity, keepAliveTime,
                monitorInterval, scaleStep, debugMode
        );
    }

    /**
     * 객체 동등성 비교
     *
     * 두 ThreadPoolConfig 객체가 동일한 설정을 가지는지 확인합니다.
     * 설정 비교, 테스트, 캐싱 등에 사용됩니다.
     *
     * @param obj 비교할 객체
     * @return 모든 설정값이 동일하면 true
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;  // 동일한 객체 참조
        if (!(obj instanceof ThreadPoolConfig)) return false;  // 타입 체크

        ThreadPoolConfig other = (ThreadPoolConfig) obj;

        /*
         * 모든 필드를 개별적으로 비교
         * 기본 타입은 == 연산자 사용
         * long 타입도 == 연산자로 직접 비교 가능
         */
        return corePoolSize == other.corePoolSize &&
                maxPoolSize == other.maxPoolSize &&
                queueCapacity == other.queueCapacity &&
                keepAliveTime == other.keepAliveTime &&
                monitorInterval == other.monitorInterval &&
                scaleStep == other.scaleStep &&
                debugMode == other.debugMode;
    }

    /**
     * 해시코드 생성
     *
     * equals()와 일관성을 유지하는 해시코드를 생성합니다.
     * HashMap, HashSet 등의 해시 기반 컬렉션에서 올바른 동작을 보장합니다.
     *
     * @return 객체의 해시코드
     */
    @Override
    public int hashCode() {
        /*
         * 모든 필드를 조합하여 해시코드 계산
         * 31은 홀수 소수로 해시 충돌을 최소화
         *
         * long 타입 처리:
         * - keepAliveTime은 long 타입이므로 비트 시프트 연산 필요
         * - 상위 32비트와 하위 32비트를 XOR하여 int로 변환
         * - (keepAliveTime ^ (keepAliveTime >>> 32))
         */
        int result = corePoolSize;
        result = 31 * result + maxPoolSize;
        result = 31 * result + queueCapacity;
        result = 31 * result + (int) (keepAliveTime ^ (keepAliveTime >>> 32));  // long -> int 변환
        result = 31 * result + monitorInterval;
        result = 31 * result + scaleStep;
        result = 31 * result + (debugMode ? 1 : 0);  // boolean -> int 변환
        return result;
    }

    /*
     * 스레드풀 설정 최적화 가이드:
     *
     * 1. CPU 집약적 작업:
     *    - 코어 풀 크기 = CPU 코어 수
     *    - 최대 풀 크기 = CPU 코어 수 + 1
     *    - 작은 큐 용량 (빠른 거부로 백프레셔 제공)
     *
     * 2. I/O 집약적 작업:
     *    - 코어 풀 크기 = CPU 코어 수 × 2-4
     *    - 최대 풀 크기 = 더 큰 값 (메모리 허용 범위 내)
     *    - 큰 큐 용량 (I/O 대기 중인 작업 버퍼링)
     *
     * 3. 혼합 작업:
     *    - 작업 타입별 분석 필요
     *    - 프로파일링으로 최적값 탐색
     *    - A/B 테스트로 성능 비교
     *
     * 4. 메모리 고려사항:
     *    - 스레드당 스택 크기: 기본 1MB (JVM 설정으로 조정 가능)
     *    - 최대 메모리 사용량 = 최대 풀 크기 × 스택 크기
     *    - 큐 메모리 = 큐 용량 × 작업 객체 크기
     *
     * 5. 모니터링 지표:
     *    - 활성 스레드 수 vs 최대 스레드 수
     *    - 큐 사용률 (큐 크기 / 큐 용량)
     *    - 작업 거부율
     *    - 평균 작업 대기 시간
     *    - 스레드 생성/소멸 빈도
     */
}