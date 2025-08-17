package server.threaded;

/**
 * 요청 핸들러 설정 클래스
 *
 * 이 클래스는 HTTP 요청 처리와 관련된 모든 설정을 관리합니다.
 * BlockingRequestHandler가 요청을 처리할 때 사용하는 파라미터들을 정의하며,
 * 성능, 보안, 안정성에 직접적인 영향을 미칩니다.
 *
 * 설계 패턴:
 * - Builder Pattern: fluent interface로 설정 체이닝 가능
 * - Factory Method Pattern: 미리 정의된 설정 프로필 제공
 * - Validation Pattern: 설정 값의 유효성 검증
 *
 * 주요 설정 영역:
 * 1. 네트워크 설정: 타임아웃, 버퍼 크기
 * 2. 연결 관리: Keep-Alive, 최대 요청 수
 * 3. 디버깅: 로깅, 모니터링 설정
 * 4. 성능 튜닝: 버퍼 크기, 타임아웃 조정
 */
public class RequestHandlerConfig {

    // === 타임아웃 관련 설정 ===

    /**
     * 소켓 타임아웃 (밀리초)
     *
     * Socket.setSoTimeout()에 사용되는 값으로,
     * 클라이언트로부터 데이터를 읽을 때의 최대 대기 시간입니다.
     *
     * 기본값: 30초 (30,000ms)
     *
     * 용도와 영향:
     * - 클라이언트가 요청을 보내지 않고 연결만 유지하는 상황 방지
     * - Slow DoS 공격(Slowloris) 방어
     * - 좀비 연결로 인한 리소스 낭비 방지
     * - 값이 너무 작으면: 느린 네트워크에서 정상 요청도 실패
     * - 값이 너무 크면: 공격자가 연결을 오래 점유 가능
     */
    private int socketTimeout = 30000; // 30초

    /**
     * 연결당 최대 요청 수
     *
     * HTTP Keep-Alive 연결에서 처리할 수 있는 최대 요청 개수입니다.
     * 이 수를 초과하면 연결을 강제로 종료합니다.
     *
     * 기본값: 100개
     *
     * 용도와 영향:
     * - 단일 연결의 독점 방지 (공정한 리소스 분배)
     * - 메모리 누수나 연결 상태 오염 방지
     * - DoS 공격에서 연결당 피해 제한
     * - 값이 너무 작으면: 연결 재설정 오버헤드 증가
     * - 값이 너무 크면: 불량 클라이언트의 장기간 점유 가능
     */
    private int maxRequestsPerConnection = 100;

    // === 디버깅 및 모니터링 설정 ===

    /**
     * 디버그 모드 활성화 여부
     *
     * true로 설정하면 상세한 로그 메시지를 출력합니다.
     * 개발/테스트 환경에서는 활성화하고, 프로덕션에서는 비활성화 권장.
     *
     * 기본값: false
     *
     * 영향:
     * - 성능: 로그 출력으로 인한 오버헤드 발생
     * - 보안: 내부 정보 노출 가능성
     * - 운영: 로그 파일 크기 증가
     * - 디버깅: 문제 진단에 필수적
     */
    private boolean debugMode = false;

    // === 네트워크 버퍼 설정 ===

    /**
     * 읽기 버퍼 크기 (바이트)
     *
     * 클라이언트로부터 데이터를 읽을 때 사용하는 버퍼의 크기입니다.
     * BufferedInputStream의 내부 버퍼 크기로 사용됩니다.
     *
     * 기본값: 8KB (8,192 bytes)
     *
     * 고려사항:
     * - HTTP 요청 헤더는 보통 1-4KB 크기
     * - 큰 버퍼: 시스템 콜 횟수 감소, 메모리 사용량 증가
     * - 작은 버퍼: 메모리 절약, 시스템 콜 횟수 증가
     * - 8KB는 대부분의 HTTP 요청에 적합한 크기
     */
    private int readBufferSize = 8192;

    /**
     * 쓰기 버퍼 크기 (바이트)
     *
     * 클라이언트에게 응답을 보낼 때 사용하는 버퍼의 크기입니다.
     *
     * 기본값: 8KB (8,192 bytes)
     *
     * 최적화 고려사항:
     * - 일반적인 HTTP 응답 크기에 맞춘 설정
     * - TCP 송신 윈도우 크기와 조화
     * - 네트워크 MTU(Maximum Transmission Unit) 고려
     */
    private int writeBufferSize = 8192;

    // === 연결 관리 설정 ===

    /**
     * HTTP Keep-Alive 활성화 여부
     *
     * true면 HTTP/1.1 Keep-Alive 연결을 지원합니다.
     * 단일 TCP 연결로 여러 HTTP 요청/응답을 처리할 수 있습니다.
     *
     * 기본값: true
     *
     * Keep-Alive의 장점:
     * - TCP 연결 설정/해제 오버헤드 감소
     * - 네트워크 지연시간 개선
     * - 서버 리소스 효율성 증대
     *
     * Keep-Alive의 단점:
     * - 연결 상태 관리 복잡성 증가
     * - 메모리 사용량 증가 (연결 유지)
     * - 부적절한 클라이언트 처리 시 리소스 낭비
     */
    private boolean enableKeepAlive = true;

    /**
     * 연결 타임아웃 (밀리초)
     *
     * 연결이 유지될 수 있는 최대 시간입니다.
     * Keep-Alive 연결에서 요청 간 최대 대기 시간을 의미합니다.
     *
     * 기본값: 60초 (60,000ms)
     *
     * socketTimeout과의 차이:
     * - socketTimeout: 단일 요청의 읽기 타임아웃
     * - connectionTimeout: 연결 전체의 생존 시간
     */
    private int connectionTimeout = 60000; // 60초

    /**
     * 요청 로깅 활성화 여부
     *
     * 각 HTTP 요청의 상세 정보를 로그로 기록할지 결정합니다.
     *
     * 기본값: false
     *
     * 로깅 내용:
     * - 요청 메서드, URL, 헤더
     * - 응답 상태, 처리 시간
     * - 클라이언트 IP, User-Agent
     *
     * 프로덕션 환경에서는 성능과 개인정보보호를 고려하여 설정해야 합니다.
     */
    private boolean enableRequestLogging = false;

    /**
     * 기본 생성자
     *
     * 모든 필드를 기본값으로 초기화합니다.
     * 대부분의 일반적인 사용 사례에 적합한 설정값들로 구성되어 있습니다.
     */
    public RequestHandlerConfig() {}

    // === Getter 메서드들 ===
    // 각 설정값을 읽기 위한 접근자 메서드들

    /**
     * 소켓 타임아웃 값 반환
     * @return 소켓 타임아웃 (밀리초)
     */
    public int getSocketTimeout() {
        return socketTimeout;
    }

    /**
     * 소켓 타임아웃 설정 (Fluent Interface)
     *
     * @param socketTimeout 소켓 타임아웃 (밀리초)
     * @return 설정이 적용된 현재 객체 (메서드 체이닝용)
     */
    public RequestHandlerConfig setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
        return this; // Fluent Interface 패턴: 메서드 체이닝 지원
    }

    public int getMaxRequestsPerConnection() {
        return maxRequestsPerConnection;
    }

    /**
     * 연결당 최대 요청 수 설정
     *
     * @param maxRequestsPerConnection 연결당 최대 요청 수
     * @return 현재 객체 (메서드 체이닝용)
     */
    public RequestHandlerConfig setMaxRequestsPerConnection(int maxRequestsPerConnection) {
        this.maxRequestsPerConnection = maxRequestsPerConnection;
        return this;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    /**
     * 디버그 모드 설정
     *
     * @param debugMode 디버그 모드 활성화 여부
     * @return 현재 객체
     */
    public RequestHandlerConfig setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        return this;
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public RequestHandlerConfig setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
        return this;
    }

    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    public RequestHandlerConfig setWriteBufferSize(int writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
        return this;
    }

    public boolean isEnableKeepAlive() {
        return enableKeepAlive;
    }

    public RequestHandlerConfig setEnableKeepAlive(boolean enableKeepAlive) {
        this.enableKeepAlive = enableKeepAlive;
        return this;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public RequestHandlerConfig setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    public boolean isEnableRequestLogging() {
        return enableRequestLogging;
    }

    public RequestHandlerConfig setEnableRequestLogging(boolean enableRequestLogging) {
        this.enableRequestLogging = enableRequestLogging;
        return this;
    }

    /**
     * 설정 유효성 검증 메서드
     *
     * 모든 설정값이 유효한 범위에 있는지 확인합니다.
     * 잘못된 설정으로 인한 런타임 오류를 사전에 방지합니다.
     *
     * @throws IllegalArgumentException 유효하지 않은 설정값이 있을 때
     */
    public void validate() {
        /*
         * 소켓 타임아웃 검증
         * 음수 값은 의미가 없으므로 예외 발생
         * 0은 무한 대기를 의미하므로 허용 (특수한 경우)
         */
        if (socketTimeout < 0) {
            throw new IllegalArgumentException("Socket timeout must be non-negative");
        }

        /*
         * 최대 요청 수 검증
         * 최소 1개 이상의 요청은 처리할 수 있어야 함
         */
        if (maxRequestsPerConnection <= 0) {
            throw new IllegalArgumentException("Max requests per connection must be positive");
        }

        /*
         * 버퍼 크기 검증
         * 버퍼는 최소 1바이트 이상이어야 함
         * 너무 작은 버퍼는 성능 문제를 일으킬 수 있음
         */
        if (readBufferSize <= 0) {
            throw new IllegalArgumentException("Read buffer size must be positive");
        }
        if (writeBufferSize <= 0) {
            throw new IllegalArgumentException("Write buffer size must be positive");
        }

        /*
         * 연결 타임아웃 검증
         * 음수는 허용하지 않음, 0은 무한 대기 의미로 허용
         */
        if (connectionTimeout < 0) {
            throw new IllegalArgumentException("Connection timeout must be non-negative");
        }
    }

    // === Factory 메서드들 ===
    // 미리 정의된 설정 프로필을 제공하는 정적 메서드들

    /**
     * 기본 설정 생성
     *
     * 일반적인 웹 서버 환경에 적합한 균형잡힌 설정입니다.
     *
     * @return 기본 설정이 적용된 RequestHandlerConfig 객체
     */
    public static RequestHandlerConfig defaultConfig() {
        return new RequestHandlerConfig();
    }

    /**
     * 고성능 설정 생성
     *
     * 높은 처리량이 필요한 환경을 위한 설정입니다.
     * 메모리를 더 사용하는 대신 성능을 최적화합니다.
     *
     * @return 고성능 설정이 적용된 객체
     */
    public static RequestHandlerConfig highPerformanceConfig() {
        return new RequestHandlerConfig()
                .setSocketTimeout(15000)        // 더 짧은 타임아웃으로 빠른 처리
                .setMaxRequestsPerConnection(200)  // 더 많은 요청 허용
                .setReadBufferSize(16384)       // 더 큰 읽기 버퍼 (16KB)
                .setWriteBufferSize(16384)      // 더 큰 쓰기 버퍼 (16KB)
                .setConnectionTimeout(30000);   // 연결 타임아웃 단축
    }

    /**
     * 개발용 설정 생성
     *
     * 개발과 디버깅을 위한 설정입니다.
     * 상세한 로그와 긴 타임아웃으로 개발 편의성을 제공합니다.
     *
     * @return 개발용 설정이 적용된 객체
     */
    public static RequestHandlerConfig developmentConfig() {
        return new RequestHandlerConfig()
                .setDebugMode(true)              // 디버그 로그 활성화
                .setEnableRequestLogging(true)   // 요청 로깅 활성화
                .setSocketTimeout(60000)         // 긴 타임아웃 (디버깅 시간 확보)
                .setMaxRequestsPerConnection(50); // 적은 요청 수로 연결 상태 관리 단순화
    }

    /**
     * 보안 강화 설정 생성
     *
     * 보안을 중시하는 환경을 위한 설정입니다.
     * 공격에 대한 노출을 최소화하고 리소스를 보호합니다.
     *
     * @return 보안 강화 설정이 적용된 객체
     */
    public static RequestHandlerConfig secureConfig() {
        return new RequestHandlerConfig()
                .setSocketTimeout(10000)         // 짧은 타임아웃으로 공격 시간 제한
                .setMaxRequestsPerConnection(50) // 적은 요청으로 연결 독점 방지
                .setConnectionTimeout(20000)     // 짧은 연결 수명
                .setEnableKeepAlive(false);      // Keep-Alive 비활성화로 공격 표면 축소
    }

    /**
     * 객체의 문자열 표현 반환
     *
     * 디버깅과 로깅 목적으로 주요 설정값들을 문자열로 표현합니다.
     *
     * @return 설정 정보가 포함된 문자열
     */
    @Override
    public String toString() {
        return String.format(
                "RequestHandlerConfig{timeout=%dms, maxReqs=%d, debug=%s, " +
                        "readBuf=%d, writeBuf=%d, keepAlive=%s, connTimeout=%dms}",
                socketTimeout, maxRequestsPerConnection, debugMode,
                readBufferSize, writeBufferSize, enableKeepAlive, connectionTimeout
        );
    }

    /**
     * 객체 동등성 비교
     *
     * 두 RequestHandlerConfig 객체가 같은 설정값을 가지는지 확인합니다.
     * 설정 비교, 테스트, 캐싱 등에 사용됩니다.
     *
     * @param obj 비교할 객체
     * @return 모든 설정값이 동일하면 true
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;  // 같은 객체 참조
        if (!(obj instanceof RequestHandlerConfig)) return false;  // 타입 체크

        RequestHandlerConfig other = (RequestHandlerConfig) obj;
        return socketTimeout == other.socketTimeout &&
                maxRequestsPerConnection == other.maxRequestsPerConnection &&
                debugMode == other.debugMode &&
                readBufferSize == other.readBufferSize &&
                writeBufferSize == other.writeBufferSize &&
                enableKeepAlive == other.enableKeepAlive &&
                connectionTimeout == other.connectionTimeout &&
                enableRequestLogging == other.enableRequestLogging;
    }

    /**
     * 해시코드 생성
     *
     * equals()와 일관성을 유지하기 위해 모든 필드를 기반으로 해시코드를 계산합니다.
     * HashMap, HashSet 등의 해시 기반 컬렉션에서 올바른 동작을 보장합니다.
     *
     * @return 객체의 해시코드
     */
    @Override
    public int hashCode() {
        int result = socketTimeout;
        result = 31 * result + maxRequestsPerConnection;
        result = 31 * result + (debugMode ? 1 : 0);
        result = 31 * result + readBufferSize;
        result = 31 * result + writeBufferSize;
        result = 31 * result + (enableKeepAlive ? 1 : 0);
        result = 31 * result + connectionTimeout;
        result = 31 * result + (enableRequestLogging ? 1 : 0);
        return result;
    }
}