package server.threaded;

/**
 * 서버 설정 클래스
 *
 * 이 클래스는 ThreadedServer의 전체적인 설정을 관리합니다.
 * 네트워크 설정, 성능 튜닝, 디버깅, 통계 수집 등 서버 운영에 필요한
 * 모든 설정 파라미터를 중앙화하여 관리합니다.
 *
 * 설계 원칙:
 * - Composition Pattern: ThreadPoolConfig, RequestHandlerConfig를 포함
 * - Builder Pattern: Fluent Interface로 설정 체이닝 지원
 * - Factory Method: 환경별 사전 정의된 설정 프로필 제공
 * - Validation: 설정 무결성 검증 기능
 *
 * 설정 범위:
 * 1. 네트워크 설정: 바인딩 주소, 백로그, 버퍼 크기
 * 2. TCP 옵션: NoDelay, KeepAlive 등
 * 3. 애플리케이션 설정: 컨텍스트 경로, 디버그 모드
 * 4. 모니터링: 통계 수집 활성화 및 주기
 * 5. 하위 설정: 스레드풀, 요청 핸들러 설정
 */
public class ServerConfig {

    // === 네트워크 바인딩 설정 ===

    /**
     * 서버 바인딩 주소
     *
     * 서버가 바인딩할 네트워크 인터페이스를 지정합니다.
     *
     * 기본값: "0.0.0.0" (모든 인터페이스에 바인딩)
     *
     * 설정 옵션:
     * - "0.0.0.0": 모든 네트워크 인터페이스 (IPv4)
     * - "127.0.0.1": 로컬호스트만 (로컬 테스트용)
     * - "::": 모든 인터페이스 (IPv6)
     * - 특정 IP: 특정 네트워크 인터페이스만
     *
     * 보안 고려사항:
     * - 프로덕션에서는 특정 IP로 제한 권장
     * - 0.0.0.0은 외부 접근이 가능하므로 방화벽 필수
     */
    private String bindAddress = "0.0.0.0";

    /**
     * 서버 소켓 백로그 크기
     *
     * 운영체제의 연결 대기 큐 크기를 설정합니다.
     * accept() 호출 전까지 대기할 수 있는 연결 수입니다.
     *
     * 기본값: 50
     *
     * 동작 원리:
     * 1. 클라이언트가 connect() 호출
     * 2. TCP 3-way handshake 완료
     * 3. 연결이 백로그 큐에 저장
     * 4. 서버가 accept() 호출하여 연결 처리
     *
     * 설정 가이드:
     * - 너무 작으면: 연결 거부 발생 (Connection refused)
     * - 너무 크면: 메모리 사용량 증가
     * - 일반적으로 동시 연결 수의 10-20% 정도로 설정
     *
     * 운영체제 제한:
     * - Linux: /proc/sys/net/core/somaxconn (기본 128)
     * - Windows: 레지스트리 설정으로 조정 가능
     */
    private int backlogSize = 50;

    // === 네트워크 버퍼 설정 ===

    /**
     * 수신 버퍼 크기 (바이트)
     *
     * 소켓의 수신 버퍼 크기를 설정합니다.
     * 운영체제가 네트워크로부터 받은 데이터를 임시 저장하는 공간입니다.
     *
     * 기본값: 8KB (8,192 bytes)
     *
     * 수신 버퍼의 역할:
     * - 네트워크 패킷을 임시 저장
     * - 애플리케이션이 read() 호출 전까지 데이터 보관
     * - TCP 윈도우 크기 결정에 영향
     *
     * 성능 영향:
     * - 큰 버퍼: 더 많은 데이터 버퍼링, 네트워크 처리량 향상
     * - 작은 버퍼: 메모리 절약, but 빈번한 시스템 콜 필요
     *
     * 최적화 고려사항:
     * - 네트워크 대역폭과 지연시간의 곱 (BDP: Bandwidth-Delay Product)
     * - 동시 연결 수 (메모리 사용량 = 버퍼크기 × 연결수)
     */
    private int receiveBufferSize = 8192;

    /**
     * 송신 버퍼 크기 (바이트)
     *
     * 소켓의 송신 버퍼 크기를 설정합니다.
     * 애플리케이션이 write()한 데이터를 네트워크로 전송하기 전까지 저장하는 공간입니다.
     *
     * 기본값: 8KB (8,192 bytes)
     *
     * 송신 버퍼의 역할:
     * - 애플리케이션 데이터를 임시 저장
     * - 네트워크 상황에 따라 재전송 대비
     * - TCP 혼잡 제어와 연동
     *
     * 설정 영향:
     * - write() 호출의 블로킹 여부 결정
     * - 네트워크 처리량과 직결
     * - 메모리 사용량에 영향
     */
    private int sendBufferSize = 8192;

    // === TCP 옵션 설정 ===

    /**
     * TCP No Delay 옵션
     *
     * Nagle 알고리즘의 비활성화 여부를 설정합니다.
     *
     * 기본값: true (Nagle 알고리즘 비활성화)
     *
     * Nagle 알고리즘:
     * - 작은 패킷들을 모아서 큰 패킷으로 전송
     * - 네트워크 효율성 향상 (패킷 오버헤드 감소)
     * - 하지만 지연시간 증가 (최대 200ms)
     *
     * HTTP 서버에서의 설정:
     * - true 권장: 빠른 응답시간 중요
     * - 작은 HTTP 응답도 즉시 전송
     * - 대화식 애플리케이션에 적합
     *
     * false로 설정하는 경우:
     * - 대용량 파일 전송 서버
     * - 네트워크 대역폭이 제한적인 환경
     * - 패킷 오버헤드를 줄이고 싶은 경우
     */
    private boolean tcpNoDelay = true;

    /**
     * TCP Keep-Alive 옵션
     *
     * 소켓 레벨의 Keep-Alive 기능 활성화 여부입니다.
     * HTTP Keep-Alive와는 다른 TCP 레벨의 기능입니다.
     *
     * 기본값: true
     *
     * TCP Keep-Alive 동작:
     * - 일정 시간 동안 비활성 연결에 대해 probe 패킷 전송
     * - 상대방이 응답하지 않으면 연결 종료
     * - 죽은 연결(dead connection) 감지
     *
     * 설정 매개변수 (OS별로 다름):
     * - keepalive_time: probe 시작 시간 (Linux: 7200초)
     * - keepalive_intvl: probe 간격 (Linux: 75초)
     * - keepalive_probes: probe 횟수 (Linux: 9회)
     *
     * HTTP Keep-Alive와의 차이:
     * - TCP Keep-Alive: 소켓 레벨, 연결 생존 확인
     * - HTTP Keep-Alive: 애플리케이션 레벨, 연결 재사용
     */
    private boolean keepAlive = true;

    // === 애플리케이션 설정 ===

    /**
     * 디버그 모드 활성화 여부
     *
     * 서버 전체의 디버깅 정보 출력을 제어합니다.
     *
     * 기본값: false
     *
     * 활성화시 출력 정보:
     * - 서버 시작/종료 과정
     * - 설정 정보 상세 출력
     * - 통계 정보 주기적 출력
     * - 에러 발생시 상세 스택 트레이스
     *
     * 성능 영향:
     * - 로그 I/O로 인한 성능 저하
     * - 메모리 사용량 증가 (로그 버퍼링)
     * - CPU 사용률 상승 (문자열 포매팅)
     *
     * 운영 환경 권장사항:
     * - 개발/테스트: true
     * - 프로덕션: false (필요시에만 활성화)
     */
    private boolean debugMode = false;

    /**
     * 웹 애플리케이션 컨텍스트 경로
     *
     * 서블릿 컨테이너의 루트 경로를 설정합니다.
     *
     * 기본값: "/" (루트)
     *
     * 컨텍스트 경로의 역할:
     * - 서블릿 URL 매핑의 기준점
     * - 정적 리소스 경로의 접두사
     * - 세션 관리 스코프
     *
     * 설정 예시:
     * - "/": http://localhost:8080/servlet
     * - "/app": http://localhost:8080/app/servlet
     * - "/api/v1": http://localhost:8080/api/v1/servlet
     *
     * 멀티 애플리케이션 환경:
     * - 각 애플리케이션마다 다른 컨텍스트 경로 사용
     * - 서블릿 충돌 방지
     * - 독립적인 세션 관리
     */
    private String contextPath = "/";

    // === 모니터링 및 통계 설정 ===

    /**
     * 통계 수집 활성화 여부
     *
     * 서버 성능 통계 수집 기능의 활성화 여부를 설정합니다.
     *
     * 기본값: true
     *
     * 수집되는 통계:
     * - 총 처리 요청 수
     * - 평균 응답 시간
     * - 활성 연결 수
     * - 스레드풀 사용률
     * - 에러 발생률
     *
     * 통계 활용:
     * - 성능 모니터링
     * - 용량 계획 (Capacity Planning)
     * - 병목 지점 식별
     * - SLA 준수 확인
     *
     * 성능 영향:
     * - 경미한 CPU 오버헤드 (통계 계산)
     * - 메모리 사용량 증가 (통계 데이터 저장)
     * - 프로덕션 환경에서도 권장 (성능 영향 미미)
     */
    private boolean enableStatistics = true;

    /**
     * 통계 출력 주기 (초)
     *
     * 수집된 통계를 콘솔에 출력하는 주기를 설정합니다.
     *
     * 기본값: 30초
     *
     * 주기 설정 가이드:
     * - 짧은 주기 (5-10초): 개발/디버깅 환경
     * - 중간 주기 (30-60초): 일반 운영 환경
     * - 긴 주기 (300초+): 안정적인 프로덕션 환경
     *
     * 고려사항:
     * - 너무 짧으면: 로그 노이즈 증가
     * - 너무 길면: 실시간 모니터링 어려움
     * - 외부 모니터링 시스템과 중복 방지
     */
    private int statisticsInterval = 30; // seconds

    // === 하위 설정 객체들 ===

    /**
     * 스레드풀 설정
     *
     * ThreadPoolManager의 동작을 제어하는 설정입니다.
     * Composition Pattern을 사용하여 관련 설정을 그룹화합니다.
     *
     * 포함 설정:
     * - 코어/최대 스레드 수
     * - 큐 용량
     * - Keep-Alive 시간
     * - 모니터링 주기
     */
    private ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig();

    /**
     * 요청 핸들러 설정
     *
     * BlockingRequestHandler의 동작을 제어하는 설정입니다.
     *
     * 포함 설정:
     * - 소켓 타임아웃
     * - 연결당 최대 요청 수
     * - 버퍼 크기
     * - Keep-Alive 설정
     */
    private RequestHandlerConfig requestHandlerConfig = new RequestHandlerConfig();

    /**
     * 기본 생성자
     *
     * 모든 설정을 기본값으로 초기화합니다.
     * 기본값들은 일반적인 웹 서버 환경에 적합하도록 선택되었습니다.
     */
    public ServerConfig() {}

    // === Getter/Setter 메서드들 (Fluent Interface) ===

    /**
     * 바인딩 주소 반환
     * @return 서버가 바인딩할 IP 주소
     */
    public String getBindAddress() {
        return bindAddress;
    }

    /**
     * 바인딩 주소 설정
     *
     * @param bindAddress 바인딩할 IP 주소 ("0.0.0.0", "127.0.0.1" 등)
     * @return 현재 객체 (메서드 체이닝 지원)
     */
    public ServerConfig setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
        return this; // Fluent Interface: config.setBindAddress("127.0.0.1").setPort(8080)
    }

    public int getBacklogSize() {
        return backlogSize;
    }

    public ServerConfig setBacklogSize(int backlogSize) {
        this.backlogSize = backlogSize;
        return this;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public ServerConfig setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
        return this;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public ServerConfig setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
        return this;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public ServerConfig setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        return this;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public ServerConfig setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
        return this;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public ServerConfig setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        return this;
    }

    public String getContextPath() {
        return contextPath;
    }

    public ServerConfig setContextPath(String contextPath) {
        this.contextPath = contextPath;
        return this;
    }

    public boolean isEnableStatistics() {
        return enableStatistics;
    }

    public ServerConfig setEnableStatistics(boolean enableStatistics) {
        this.enableStatistics = enableStatistics;
        return this;
    }

    public int getStatisticsInterval() {
        return statisticsInterval;
    }

    public ServerConfig setStatisticsInterval(int statisticsInterval) {
        this.statisticsInterval = statisticsInterval;
        return this;
    }

    public ThreadPoolConfig getThreadPoolConfig() {
        return threadPoolConfig;
    }

    public ServerConfig setThreadPoolConfig(ThreadPoolConfig threadPoolConfig) {
        this.threadPoolConfig = threadPoolConfig;
        return this;
    }

    public RequestHandlerConfig getRequestHandlerConfig() {
        return requestHandlerConfig;
    }

    public ServerConfig setRequestHandlerConfig(RequestHandlerConfig requestHandlerConfig) {
        this.requestHandlerConfig = requestHandlerConfig;
        return this;
    }

    /**
     * 설정 유효성 검증
     *
     * 모든 설정값이 유효한 범위에 있는지 확인합니다.
     * 서버 시작 전에 호출되어 잘못된 설정으로 인한 런타임 오류를 방지합니다.
     *
     * @throws IllegalArgumentException 유효하지 않은 설정값 발견시
     */
    public void validate() {
        /*
         * 바인딩 주소 검증
         * null이나 빈 문자열은 허용하지 않음
         * trim()으로 공백만 있는 문자열도 체크
         */
        if (bindAddress == null || bindAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Bind address cannot be null or empty");
        }

        /*
         * 백로그 크기 검증
         * 음수는 의미가 없으므로 오류
         * 0은 운영체제 기본값 사용을 의미하므로 허용
         */
        if (backlogSize < 0) {
            throw new IllegalArgumentException("Backlog size must be non-negative");
        }

        /*
         * 버퍼 크기 검증
         * 버퍼는 최소 1바이트 이상이어야 함
         * 0이나 음수는 의미 없음
         */
        if (receiveBufferSize <= 0) {
            throw new IllegalArgumentException("Receive buffer size must be positive");
        }
        if (sendBufferSize <= 0) {
            throw new IllegalArgumentException("Send buffer size must be positive");
        }

        /*
         * 컨텍스트 경로 검증
         * null은 허용하지 않음 (빈 문자열은 허용)
         */
        if (contextPath == null) {
            throw new IllegalArgumentException("Context path cannot be null");
        }

        /*
         * 통계 주기 검증
         * 양수여야 함 (0이나 음수는 의미 없음)
         */
        if (statisticsInterval <= 0) {
            throw new IllegalArgumentException("Statistics interval must be positive");
        }

        /*
         * 하위 설정 검증
         * 컴포지션 패턴: 포함된 객체들의 검증도 위임
         * null 체크 후 각자의 validate() 메서드 호출
         */
        if (threadPoolConfig != null) {
            threadPoolConfig.validate();
        }
        if (requestHandlerConfig != null) {
            requestHandlerConfig.validate();
        }
    }

    // === Factory 메서드들 ===
    // 다양한 환경에 최적화된 설정 프로필을 제공

    /**
     * 기본 설정 생성
     *
     * 일반적인 웹 서버 환경에 적합한 균형잡힌 설정입니다.
     *
     * @return 기본 설정이 적용된 ServerConfig 객체
     */
    public static ServerConfig defaultConfig() {
        return new ServerConfig();
    }

    /**
     * 고성능 설정 생성
     *
     * 높은 처리량과 동시 연결을 위한 설정입니다.
     * 메모리 사용량이 증가하는 대신 성능을 최대화합니다.
     *
     * 특징:
     * - 큰 백로그와 버퍼 크기
     * - 고성능 스레드풀 설정
     * - 긴 통계 주기 (오버헤드 최소화)
     *
     * @return 고성능 설정이 적용된 객체
     */
    public static ServerConfig highPerformanceConfig() {
        return new ServerConfig()
                .setBacklogSize(100)                    // 더 많은 대기 연결 허용
                .setReceiveBufferSize(16384)             // 16KB 수신 버퍼
                .setSendBufferSize(16384)                // 16KB 송신 버퍼
                .setStatisticsInterval(60)               // 1분 주기 (오버헤드 감소)
                .setThreadPoolConfig(ThreadPoolConfig.highPerformanceConfig())
                .setRequestHandlerConfig(RequestHandlerConfig.highPerformanceConfig());
    }

    /**
     * 개발용 설정 생성
     *
     * 개발과 디버깅에 최적화된 설정입니다.
     * 성능보다는 디버깅 편의성과 상세한 로그를 중시합니다.
     *
     * 특징:
     * - 디버그 모드 활성화
     * - 작은 백로그 (간단한 테스트용)
     * - 짧은 통계 주기 (실시간 모니터링)
     * - 개발 친화적 스레드풀/핸들러 설정
     *
     * @return 개발용 설정이 적용된 객체
     */
    public static ServerConfig developmentConfig() {
        return new ServerConfig()
                .setDebugMode(true)                      // 상세한 디버그 로그
                .setBacklogSize(10)                      // 작은 백로그 (테스트용)
                .setStatisticsInterval(10)               // 10초 주기 (빠른 피드백)
                .setThreadPoolConfig(ThreadPoolConfig.developmentConfig())
                .setRequestHandlerConfig(RequestHandlerConfig.developmentConfig());
    }

    /**
     * 프로덕션 설정 생성
     *
     * 실제 운영 환경에 최적화된 안정적이고 안전한 설정입니다.
     * 보안과 안정성을 최우선으로 합니다.
     *
     * 특징:
     * - 디버그 모드 비활성화 (성능/보안)
     * - 큰 백로그와 버퍼 (고부하 처리)
     * - 긴 통계 주기 (시스템 부하 최소화)
     * - 보안 강화된 핸들러 설정
     *
     * @return 프로덕션 설정이 적용된 객체
     */
    public static ServerConfig productionConfig() {
        return new ServerConfig()
                .setDebugMode(false)                     // 디버그 로그 비활성화
                .setBacklogSize(200)                     // 큰 백로그 (고부하 대응)
                .setReceiveBufferSize(32768)             // 32KB 수신 버퍼
                .setSendBufferSize(32768)                // 32KB 송신 버퍼
                .setStatisticsInterval(300)              // 5분 주기 (부하 최소화)
                .setThreadPoolConfig(ThreadPoolConfig.highPerformanceConfig())
                .setRequestHandlerConfig(RequestHandlerConfig.secureConfig());
    }

    /**
     * 로컬 테스트용 설정 생성
     *
     * 로컬 개발 환경에서의 테스트에 최적화된 설정입니다.
     * 리소스 사용을 최소화하면서도 디버깅 정보를 제공합니다.
     *
     * 특징:
     * - 로컬호스트만 바인딩 (보안)
     * - 최소한의 리소스 사용
     * - 빠른 피드백을 위한 짧은 주기
     *
     * @return 로컬 테스트용 설정이 적용된 객체
     */
    public static ServerConfig localTestConfig() {
        return new ServerConfig()
                .setBindAddress("127.0.0.1")            // 로컬호스트만 허용
                .setDebugMode(true)                      // 디버깅 활성화
                .setBacklogSize(5)                       // 최소 백로그
                .setStatisticsInterval(5)                // 5초 주기 (빠른 피드백)
                .setThreadPoolConfig(ThreadPoolConfig.developmentConfig())
                .setRequestHandlerConfig(RequestHandlerConfig.developmentConfig());
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
                "ServerConfig{bind=%s, backlog=%d, rcvBuf=%d, sndBuf=%d, " +
                        "tcpNoDelay=%s, keepAlive=%s, debug=%s, context='%s', stats=%s}",
                bindAddress, backlogSize, receiveBufferSize, sendBufferSize,
                tcpNoDelay, keepAlive, debugMode, contextPath, enableStatistics
        );
    }

    /**
     * 객체 동등성 비교
     *
     * 두 ServerConfig 객체가 동일한 설정을 가지는지 확인합니다.
     * 설정 비교, 테스트, 캐싱 등에 사용됩니다.
     *
     * @param obj 비교할 객체
     * @return 모든 설정이 동일하면 true
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;  // 동일한 객체 참조
        if (!(obj instanceof ServerConfig)) return false;  // 타입 검사

        ServerConfig other = (ServerConfig) obj;

        /*
         * 모든 필드를 하나씩 비교
         * 기본 타입은 == 연산자로 비교
         * 객체 타입은 equals() 메서드로 비교
         */
        return backlogSize == other.backlogSize &&
                receiveBufferSize == other.receiveBufferSize &&
                sendBufferSize == other.sendBufferSize &&
                tcpNoDelay == other.tcpNoDelay &&
                keepAlive == other.keepAlive &&
                debugMode == other.debugMode &&
                enableStatistics == other.enableStatistics &&
                statisticsInterval == other.statisticsInterval &&
                bindAddress.equals(other.bindAddress) &&         // String 비교
                contextPath.equals(other.contextPath) &&         // String 비교
                threadPoolConfig.equals(other.threadPoolConfig) && // 객체 비교
                requestHandlerConfig.equals(other.requestHandlerConfig); // 객체 비교
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
         * 31은 홀수 소수로 해시 분산을 향상시킴
         *
         * 해시코드 계산 공식:
         * result = 31 * result + field_hash
         */
        int result = bindAddress.hashCode();
        result = 31 * result + backlogSize;
        result = 31 * result + receiveBufferSize;
        result = 31 * result + sendBufferSize;
        result = 31 * result + (tcpNoDelay ? 1 : 0);      // boolean을 int로 변환
        result = 31 * result + (keepAlive ? 1 : 0);
        result = 31 * result + (debugMode ? 1 : 0);
        result = 31 * result + contextPath.hashCode();
        result = 31 * result + (enableStatistics ? 1 : 0);
        result = 31 * result + statisticsInterval;
        result = 31 * result + threadPoolConfig.hashCode();     // 포함 객체의 해시코드
        result = 31 * result + requestHandlerConfig.hashCode(); // 포함 객체의 해시코드
        return result;
    }
}