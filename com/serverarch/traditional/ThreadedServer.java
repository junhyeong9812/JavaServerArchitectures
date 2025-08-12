package com.serverarch.traditional; // 패키지 선언 - 전통적인 스레드 기반 서버 아키텍처 패키지

// import 선언부 - 외부 클래스들을 현재 클래스에서 사용할 수 있도록 경로 지정
import com.serverarch.common.http.*; // HTTP 관련 공통 클래스들 전체 import - HttpRequest, HttpResponse, HttpHeaders 등
import java.net.*; // 네트워크 관련 클래스들 전체 import - ServerSocket, Socket, InetSocketAddress 등
import java.io.*; // 입출력 관련 클래스들 전체 import - InputStream, OutputStream, IOException 등
import java.util.concurrent.*; // 동시성 관련 클래스들 전체 import - ExecutorService, ThreadFactory, AtomicLong 등
import java.util.concurrent.atomic.*; // 원자적 연산 클래스들 전체 import - AtomicBoolean, AtomicLong 등
import java.util.logging.*; // 로깅 관련 클래스들 전체 import - Logger, Level 등

/**
 * Thread-per-Request 방식의 HTTP 서버
 *
 * 설계 원칙:
 * 1. 각 클라이언트 연결마다 별도 스레드 할당
 * 2. 블로킹 I/O 사용으로 구현 단순성 확보
 * 3. 스레드풀로 스레드 생성 비용 최적화
 * 4. 동기식 처리로 디버깅 용이성 제공
 *
 * 장점:
 * - 구현이 단순하고 직관적
 * - 디버깅이 쉬움 (스택 추적 명확)
 * - 블로킹 I/O로 기존 라이브러리 활용 용이
 *
 * 단점:
 * - 스레드당 메모리 사용량 높음 (스택 메모리)
 * - 컨텍스트 스위칭 오버헤드
 * - 동시 연결 수 제한 (일반적으로 수백 개)
 */
public class ThreadedServer { // public 클래스 선언 - 다른 패키지에서 접근 가능한 HTTP 서버 클래스

    // 로거 - 서버 동작 상태와 문제 진단을 위한 로깅
    private static final Logger logger = Logger.getLogger(ThreadedServer.class.getName()); // static final - 클래스 레벨 상수, Logger.getLogger() - 클래스 이름 기반 로거 생성

    // 서버 바인딩 포트
    // final로 선언하여 서버 시작 후 변경 불가 - 설정 일관성 보장
    private final int port; // final int - 불변 정수 필드, 서버가 바인딩할 포트 번호

    // 스레드풀 크기 설정
    // final로 선언하여 서버 설정 불변성 보장
    private final int threadPoolSize; // final int - 불변 정수 필드, 동시 처리 가능한 최대 스레드 수

    // 연결 대기 큐 크기 (ServerSocket backlog)
    // 동시에 대기할 수 있는 연결 요청 수 제한
    private final int backlog; // final int - 불변 정수 필드, 연결 대기 큐의 최대 크기

    // 서버 실행 상태
    // AtomicBoolean 사용으로 멀티스레드 환경에서 안전한 상태 관리
    private final AtomicBoolean running = new AtomicBoolean(false); // AtomicBoolean - 원자적 boolean 연산 지원, new AtomicBoolean(false) - 초기값 false로 생성

    // 서버 소켓
    // volatile로 선언하여 다른 스레드에서 즉시 변경사항 확인 가능
    private volatile ServerSocket serverSocket; // volatile - 메모리 가시성 보장, ServerSocket - 서버 소켓 객체

    // 스레드풀 - 요청 처리용 스레드들을 관리
    // 스레드 생성/소멸 비용을 줄이고 동시 처리 수를 제어
    private ExecutorService threadPool; // ExecutorService - 스레드풀 인터페이스, 비동기 작업 실행 관리

    // 요청 처리기 - 실제 HTTP 요청/응답 처리 로직
    // 의존성 주입으로 테스트 용이성과 확장성 확보
    private ThreadedRequestProcessor requestProcessor; // ThreadedRequestProcessor - HTTP 요청 처리 담당 클래스

    // 통계 카운터들 - 서버 성능 모니터링을 위한 메트릭
    private final AtomicLong totalRequestsReceived = new AtomicLong(0);    // AtomicLong - 원자적 long 연산 지원, 받은 총 요청 수
    private final AtomicLong totalRequestsProcessed = new AtomicLong(0);   // AtomicLong - 처리 완료된 요청 수
    private final AtomicLong totalRequestsFailed = new AtomicLong(0);      // AtomicLong - 실패한 요청 수
    private final AtomicLong currentActiveConnections = new AtomicLong(0); // AtomicLong - 현재 활성 연결 수

    // 기본 설정 상수들
    private static final int DEFAULT_THREAD_POOL_SIZE = 200;  // static final int - 기본 스레드풀 크기, CPU 코어 수의 배수로 설정
    private static final int DEFAULT_BACKLOG = 50;            // static final int - 기본 백로그 크기, 적당한 대기 큐 크기
    private static final int SOCKET_TIMEOUT = 30000;          // static final int - 소켓 타임아웃 30초, 무한 대기 방지

    /**
     * 기본 설정으로 서버 생성
     *
     * @param port 서버 포트
     */
    public ThreadedServer(int port) { // public 생성자 - 포트만 받는 간단한 생성자
        // 기본 설정값들로 상세 생성자 호출
        this(port, DEFAULT_THREAD_POOL_SIZE, DEFAULT_BACKLOG); // this() - 같은 클래스의 다른 생성자 호출, 기본값들과 함께 호출
    }

    /**
     * 포트와 스레드풀 크기만 지정하는 생성자 (누락되었던 생성자)
     * ThreadedServerLauncher에서 사용하는 생성자
     *
     * @param port 서버 포트 (1-65535)
     * @param threadPoolSize 스레드풀 크기 (최소 1)
     */
    public ThreadedServer(int port, int threadPoolSize) { // public 생성자 - 포트와 스레드풀 크기를 받는 생성자
        // 기본 백로그 크기와 함께 상세 생성자 호출
        this(port, threadPoolSize, DEFAULT_BACKLOG); // this() - 세 개 매개변수를 받는 생성자 호출, 백로그는 기본값 사용
    }

    /**
     * 상세 설정으로 서버 생성
     *
     * @param port 서버 포트 (1-65535)
     * @param threadPoolSize 스레드풀 크기 (최소 1)
     * @param backlog 연결 대기 큐 크기 (최소 1)
     */
    public ThreadedServer(int port, int threadPoolSize, int backlog) { // public 생성자 - 모든 설정을 받는 상세 생성자
        // 포트 번호 유효성 검증 - 시스템 포트와 사용자 포트 범위 확인
        if (port < 1 || port > 65535) { // 조건문 - 포트 번호가 유효 범위(1-65535)를 벗어나는지 확인
            throw new IllegalArgumentException("포트 번호는 1-65535 사이여야 합니다: " + port); // IllegalArgumentException - 잘못된 인수 예외
        }

        // 스레드풀 크기 유효성 검증 - 최소 1개는 있어야 요청 처리 가능
        if (threadPoolSize < 1) { // 조건문 - 스레드풀 크기가 1보다 작은지 확인
            throw new IllegalArgumentException("스레드풀 크기는 1 이상이어야 합니다: " + threadPoolSize); // 스레드풀 크기 오류 예외
        }

        // 백로그 크기 유효성 검증 - 최소 1개는 있어야 연결 대기 가능
        if (backlog < 1) { // 조건문 - 백로그 크기가 1보다 작은지 확인
            throw new IllegalArgumentException("백로그 크기는 1 이상이어야 합니다: " + backlog); // 백로그 크기 오류 예외
        }

        // 인스턴스 변수 초기화 - 검증된 값들로 설정
        this.port = port; // this.port - 현재 객체의 port 필드에 매개변수 값 할당
        this.threadPoolSize = threadPoolSize; // this.threadPoolSize - 현재 객체의 threadPoolSize 필드에 매개변수 값 할당
        this.backlog = backlog; // this.backlog - 현재 객체의 backlog 필드에 매개변수 값 할당

        // 로그로 서버 설정 기록 - 디버깅과 모니터링에 유용
        logger.info(String.format("ThreadedServer 생성됨 - 포트: %d, 스레드풀: %d, 백로그: %d", // Logger.info() - 정보 레벨 로그, String.format() - 문자열 템플릿
                port, threadPoolSize, backlog)); // 생성자 매개변수들을 로그에 기록
    }

    /**
     * 서버 시작
     *
     * 1. ServerSocket 생성 및 포트 바인딩
     * 2. 스레드풀 초기화
     * 3. 요청 처리기 생성
     * 4. 메인 루프 시작 (클라이언트 연결 수락)
     *
     * @throws IOException 서버 소켓 생성 실패 또는 포트 바인딩 실패
     * @throws IllegalStateException 서버가 이미 실행 중인 경우
     */
    public void start() throws IOException { // public 메서드 - 서버 시작, throws IOException - 체크 예외 선언
        // 중복 시작 방지 - AtomicBoolean의 compareAndSet으로 원자적 상태 변경
        if (!running.compareAndSet(false, true)) { // AtomicBoolean.compareAndSet() - 예상값과 같으면 새 값으로 변경, 원자적 연산
            throw new IllegalStateException("서버가 이미 실행 중입니다"); // IllegalStateException - 잘못된 상태 예외
        }

        try { // try-catch 블록 - 서버 시작 중 예외 처리
            // 1. ServerSocket 생성 및 설정
            serverSocket = new ServerSocket(); // new ServerSocket() - 기본 생성자로 서버 소켓 생성

            // 주소 재사용 허용 - 서버 재시작 시 "Address already in use" 오류 방지
            serverSocket.setReuseAddress(true); // ServerSocket.setReuseAddress() - 주소 재사용 옵션 활성화

            // 포트 바인딩 - 지정된 포트에서 클라이언트 연결 대기
            serverSocket.bind(new InetSocketAddress(port), backlog); // ServerSocket.bind() - 소켓을 주소에 바인딩, InetSocketAddress - IP 주소와 포트 조합

            // 2. 스레드풀 초기화 - 고정 크기 스레드풀로 리소스 제어
            threadPool = Executors.newFixedThreadPool(threadPoolSize, new ThreadFactory() { // Executors.newFixedThreadPool() - 고정 크기 스레드풀 생성, ThreadFactory - 스레드 생성 팩토리
                // 스레드 번호 카운터 - 스레드 이름 생성용
                private final AtomicLong threadNumber = new AtomicLong(1); // AtomicLong - 원자적 long 연산, 1부터 시작

                @Override // 어노테이션 - ThreadFactory 인터페이스의 메서드 재정의
                public Thread newThread(Runnable r) { // ThreadFactory.newThread() - 새 스레드 생성 메서드
                    // 의미있는 스레드 이름 설정 - 디버깅과 모니터링에 유용
                    Thread thread = new Thread(r, "ThreadedServer-Worker-" + threadNumber.getAndIncrement()); // new Thread() - 스레드 생성, getAndIncrement() - 현재 값 반환 후 1 증가

                    // 데몬 스레드로 설정하지 않음 - 요청 처리 완료까지 JVM 종료 방지
                    thread.setDaemon(false); // Thread.setDaemon() - 데몬 스레드 여부 설정, false - 일반 스레드

                    return thread; // 설정된 스레드 반환
                }
            });

            // 3. 요청 처리기 생성 - 실제 HTTP 요청/응답 처리 담당
            this.requestProcessor = new ThreadedRequestProcessor(); // new ThreadedRequestProcessor() - 요청 처리기 인스턴스 생성

            // 서버 시작 로그
            logger.info(String.format("ThreadedServer가 포트 %d에서 시작되었습니다 (스레드풀: %d)", // 서버 시작 정보 로그
                    port, threadPoolSize)); // 포트와 스레드풀 크기 정보

            // 4. 메인 서버 루프 시작 - 클라이언트 연결 수락 및 처리
            runServerLoop(); // runServerLoop() - 메인 서버 루프 실행

        } catch (Exception e) { // Exception - 모든 예외 처리
            // 시작 실패 시 상태 복원 - 다음 시작 시도를 위해
            running.set(false); // AtomicBoolean.set() - 값을 false로 설정

            // 리소스 정리 - 메모리 누수 방지
            cleanup(); // cleanup() - 리소스 정리 메서드 호출

            // 원본 예외를 포장하여 재던짐 - 호출자에게 구체적인 실패 원인 전달
            throw new IOException("서버 시작 실패", e); // IOException - 원본 예외를 원인으로 포함
        }
    }

    /**
     * 서버 중지
     *
     * 1. 새로운 연결 수락 중지
     * 2. 기존 연결들의 처리 완료 대기
     * 3. 스레드풀 종료
     * 4. 리소스 정리
     */
    public void stop() { // public 메서드 - 서버 중지
        // 이미 중지된 경우 무시 - 중복 호출 방지
        if (!running.compareAndSet(true, false)) { // AtomicBoolean.compareAndSet() - true에서 false로 원자적 변경 시도
            logger.info("서버가 이미 중지되었거나 실행 중이 아닙니다"); // 이미 중지된 상태 로그
            return; // early return - 메서드 즉시 종료
        }

        logger.info("서버 중지를 시작합니다..."); // 서버 중지 시작 로그

        try { // try-catch 블록 - 중지 과정 중 예외 처리
            // 1. ServerSocket 닫기 - 새로운 연결 수락 중지
            if (serverSocket != null && !serverSocket.isClosed()) { // null 체크와 닫힘 상태 확인
                serverSocket.close(); // ServerSocket.close() - 서버 소켓 닫기
            }

            // 2. 스레드풀 우아한 종료
            if (threadPool != null) { // null 체크 - 스레드풀이 초기화된 경우에만
                // 새로운 작업 수락 중지
                threadPool.shutdown(); // ExecutorService.shutdown() - 새 작업 수락 중지, 기존 작업은 완료까지 대기

                try { // 내부 try-catch - 종료 대기 중 인터럽트 처리
                    // 기존 작업 완료 대기 (최대 30초)
                    if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) { // awaitTermination() - 지정된 시간 동안 종료 대기
                        logger.warning("일부 작업이 시간 내에 완료되지 않아 강제 종료합니다"); // 타임아웃 경고 로그

                        // 강제 종료 - 응답하지 않는 작업들을 중단
                        threadPool.shutdownNow(); // ExecutorService.shutdownNow() - 실행 중인 작업 강제 중단

                        // 강제 종료 후 추가 대기 (최대 10초)
                        if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) { // 강제 종료 후 추가 대기
                            logger.severe("스레드풀을 완전히 종료할 수 없습니다"); // 종료 실패 심각 로그
                        }
                    }
                } catch (InterruptedException e) { // InterruptedException - 대기 중 인터럽트 발생
                    // 인터럽트 발생 시 즉시 강제 종료
                    threadPool.shutdownNow(); // 즉시 강제 종료

                    // 현재 스레드의 인터럽트 상태 복원 - 호출자가 인터럽트를 처리할 수 있도록
                    Thread.currentThread().interrupt(); // Thread.interrupt() - 현재 스레드에 인터럽트 플래그 설정
                }
            }

            // 3. 최종 통계 로그 출력
            logFinalStatistics(); // logFinalStatistics() - 최종 서버 통계 로그 출력

            logger.info("ThreadedServer가 성공적으로 중지되었습니다"); // 서버 중지 완료 로그

        } catch (Exception e) { // Exception - 중지 과정 중 예외 처리
            logger.log(Level.SEVERE, "서버 종료 중 오류 발생", e); // 심각한 오류 레벨로 로그
        } finally { // finally 블록 - 예외 발생 여부와 관계없이 실행
            // 4. 최종 리소스 정리 - 예외 발생 여부와 관계없이 실행
            cleanup(); // cleanup() - 최종 리소스 정리
        }
    }

    /**
     * 메인 서버 루프
     * 클라이언트 연결을 수락하고 각 연결을 별도 스레드에서 처리
     */
    private void runServerLoop() { // private 메서드 - 메인 서버 루프
        // 서버가 실행 중인 동안 계속 반복
        while (running.get()) { // while 루프 - running.get()이 true인 동안 계속 실행
            try { // try-catch 블록 - 연결 수락 중 예외 처리
                // 클라이언트 연결 수락 - 블로킹 호출
                // 클라이언트가 연결할 때까지 이 지점에서 대기
                Socket clientSocket = serverSocket.accept(); // ServerSocket.accept() - 클라이언트 연결 대기 및 수락, 블로킹 메서드

                // 연결 통계 업데이트 - 모니터링을 위한 메트릭
                totalRequestsReceived.incrementAndGet(); // AtomicLong.incrementAndGet() - 원자적으로 1 증가 후 값 반환
                currentActiveConnections.incrementAndGet(); // 현재 활성 연결 수 증가

                // 클라이언트 소켓 설정 - 타임아웃과 성능 최적화
                configureClientSocket(clientSocket); // configureClientSocket() - 클라이언트 소켓 옵션 설정

                // 요청 처리를 스레드풀에 제출 - Thread-per-Request 모델의 핵심
                threadPool.submit(new RequestHandler(clientSocket)); // ExecutorService.submit() - 작업을 스레드풀에 제출

            } catch (SocketException e) { // SocketException - 소켓 관련 예외
                // ServerSocket이 닫힌 경우 (정상적인 종료)
                if (running.get()) { // running.get() - 현재 실행 상태 확인
                    // 서버가 실행 중인데 SocketException이 발생하면 비정상 상황
                    logger.log(Level.WARNING, "서버 소켓 예외 발생", e); // WARNING 레벨로 소켓 예외 로그
                }
                break; // break 문 - while 루프 종료

            } catch (IOException e) { // IOException - 입출력 예외
                // 연결 수락 실패 - 일시적인 문제일 수 있으므로 계속 진행
                if (running.get()) { // 서버가 실행 중인 경우에만 로그
                    logger.log(Level.WARNING, "클라이언트 연결 수락 실패", e); // 연결 수락 실패 로그
                }

            } catch (RejectedExecutionException e) { // RejectedExecutionException - 스레드풀 작업 거부 예외
                // 스레드풀이 포화 상태 - 백프레셔 처리
                logger.log(Level.WARNING, "스레드풀 포화로 요청 거부", e); // 스레드풀 포화 경고 로그
                totalRequestsFailed.incrementAndGet(); // AtomicLong.incrementAndGet() - 실패 요청 수 증가

                // 클라이언트 소켓을 즉시 닫아 리소스 해제
                // (accept는 성공했지만 처리할 수 없는 상황)
            }
        }
    }

    /**
     * 클라이언트 소켓 설정
     * 성능 최적화와 안정성을 위한 소켓 옵션 설정
     *
     * @param clientSocket 설정할 클라이언트 소켓
     * @throws SocketException 소켓 설정 실패 시
     */
    private void configureClientSocket(Socket clientSocket) throws SocketException { // private 메서드 - 클라이언트 소켓 설정
        // 소켓 타임아웃 설정 - 무한 대기 방지
        clientSocket.setSoTimeout(SOCKET_TIMEOUT); // Socket.setSoTimeout() - 읽기 작업 타임아웃 설정 (밀리초)

        // TCP_NODELAY 활성화 - Nagle 알고리즘 비활성화로 응답성 향상
        clientSocket.setTcpNoDelay(true); // Socket.setTcpNoDelay() - TCP_NODELAY 옵션 설정, 작은 패킷 즉시 전송

        // Keep-Alive 활성화 - 연결 유지로 성능 향상
        clientSocket.setKeepAlive(true); // Socket.setKeepAlive() - TCP Keep-Alive 옵션 활성화, 연결 상태 주기적 확인
    }

    /**
     * 최종 통계를 로그에 출력
     * 서버 종료 시 전체 처리 통계 요약
     */
    private void logFinalStatistics() { // private 메서드 - 최종 통계 출력
        logger.info(String.format( // Logger.info() - 정보 레벨 로그, String.format() - 문자열 템플릿
                "서버 통계 - 총 요청: %d, 처리 완료: %d, 실패: %d, 활성 연결: %d", // 통계 정보 템플릿
                totalRequestsReceived.get(), // AtomicLong.get() - 현재 값 조회, 총 요청 수
                totalRequestsProcessed.get(), // 처리 완료된 요청 수
                totalRequestsFailed.get(), // 실패한 요청 수
                currentActiveConnections.get() // 현재 활성 연결 수
        ));
    }

    /**
     * 리소스 정리
     * 메모리 누수 방지를 위한 최종 정리 작업
     */
    private void cleanup() { // private 메서드 - 리소스 정리
        // ServerSocket 정리
        if (serverSocket != null && !serverSocket.isClosed()) { // null 체크와 닫힘 상태 확인
            try { // try-catch 블록 - 소켓 닫기 중 예외 처리
                serverSocket.close(); // ServerSocket.close() - 서버 소켓 닫기
            } catch (IOException e) { // IOException - 소켓 닫기 실패
                logger.log(Level.WARNING, "서버 소켓 닫기 실패", e); // 소켓 닫기 실패 로그
            }
        }

        // 스레드풀 강제 종료 (정상 종료가 실패한 경우)
        if (threadPool != null && !threadPool.isTerminated()) { // null 체크와 종료 상태 확인
            threadPool.shutdownNow(); // ExecutorService.shutdownNow() - 강제 종료
        }

        // 요청 처리기 정리
        if (requestProcessor != null) { // null 체크
            // 현재는 정리할 리소스가 없지만 확장성을 위해 준비
            requestProcessor = null; // 참조 해제 - 가비지 컬렉션 대상으로 만들기
        }
    }

    // ========== 상태 조회 메서드들 ==========

    /**
     * 서버 실행 상태 확인
     *
     * @return 서버가 실행 중이면 true
     */
    public boolean isRunning() { // public getter 메서드 - 서버 실행 상태 조회
        return running.get(); // AtomicBoolean.get() - 현재 boolean 값 반환
    }

    /**
     * 현재 서버 통계 반환
     * 모니터링과 운영에 사용
     *
     * @return 서버 통계 객체
     */
    public ServerStatistics getStatistics() { // public getter 메서드 - 서버 통계 조회
        return new ServerStatistics( // ServerStatistics 생성자 호출 - 현재 통계 값들로 객체 생성
                totalRequestsReceived.get(), // 총 요청 수
                totalRequestsProcessed.get(), // 처리 완료 수
                totalRequestsFailed.get(), // 실패 수
                currentActiveConnections.get(), // 활성 연결 수
                port, // 서버 포트
                threadPoolSize // 스레드풀 크기
        );
    }

    // ========== 내부 클래스: RequestHandler ==========

    /**
     * 개별 요청을 처리하는 핸들러
     * Thread-per-Request 모델의 핵심 구현
     *
     * 각 클라이언트 연결마다 이 클래스의 인스턴스가
     * 별도의 스레드에서 실행됩니다.
     */
    private class RequestHandler implements Runnable { // private 내부 클래스 - Runnable 인터페이스 구현

        // 처리할 클라이언트 소켓
        // final로 선언하여 핸들러 생성 후 변경 불가
        private final Socket clientSocket; // final Socket - 불변 소켓 참조

        /**
         * RequestHandler 생성자
         *
         * @param clientSocket 처리할 클라이언트 소켓
         */
        public RequestHandler(Socket clientSocket) { // public 생성자 - 클라이언트 소켓을 받는 생성자
            // null 체크는 호출자가 보장한다고 가정 - 내부 클래스이므로 신뢰
            this.clientSocket = clientSocket; // this.clientSocket - 현재 객체의 clientSocket 필드에 매개변수 값 할당
        }

        /**
         * 요청 처리 메인 로직
         * 스레드풀의 워커 스레드에서 실행됨
         *
         * 처리 과정:
         * 1. HTTP 요청 파싱
         * 2. 요청 처리 (비즈니스 로직)
         * 3. HTTP 응답 전송
         * 4. 연결 종료 및 통계 업데이트
         */
        @Override // 어노테이션 - Runnable.run() 메서드 재정의
        public void run() { // public 메서드 - Runnable 인터페이스의 run() 메서드 구현
            // 요청 처리 시작 시간 기록 - 성능 측정용
            long startTime = System.currentTimeMillis(); // System.currentTimeMillis() - 현재 시간(밀리초) 조회

            // try-with-resources로 자동 리소스 관리
            // 예외 발생 여부와 관계없이 소켓이 확실히 닫힘
            try (Socket socket = clientSocket; // try-with-resources - 자동 리소스 관리, Socket은 AutoCloseable 구현
                 InputStream inputStream = socket.getInputStream(); // Socket.getInputStream() - 소켓의 입력 스트림 조회
                 OutputStream outputStream = socket.getOutputStream()) { // Socket.getOutputStream() - 소켓의 출력 스트림 조회

                // 1. HTTP 요청 파싱 - 블로킹 I/O
                HttpRequest request = parseHttpRequest(inputStream); // parseHttpRequest() - 입력 스트림에서 HTTP 요청 파싱

                // 2. 요청 처리 - 비즈니스 로직 실행
                HttpResponse response = processRequest(request); // processRequest() - 요청 처리하여 응답 생성

                // 3. HTTP 응답 전송 - 블로킹 I/O
                sendHttpResponse(outputStream, response); // sendHttpResponse() - 출력 스트림으로 응답 전송

                // 성공 통계 업데이트
                totalRequestsProcessed.incrementAndGet(); // AtomicLong.incrementAndGet() - 처리 완료 수 증가

                // 처리 시간 로그 (디버그 레벨)
                long processingTime = System.currentTimeMillis() - startTime; // 처리 시간 계산 - 종료 시간 - 시작 시간
                logger.fine(String.format("요청 처리 완료: %s %s (%dms)", // Logger.fine() - 상세 로그 레벨
                        request.getMethod(), request.getPath(), processingTime)); // 메서드, 경로, 처리 시간 로그

            } catch (IOException e) { // IOException - 입출력 예외 처리
                // I/O 오류 처리 - 네트워크 문제나 클라이언트 연결 끊김
                logger.log(Level.WARNING, "요청 처리 중 I/O 오류", e); // WARNING 레벨로 I/O 오류 로그
                totalRequestsFailed.incrementAndGet(); // 실패 요청 수 증가

            } catch (Exception e) { // Exception - 모든 예외 처리
                // 기타 예외 처리 - 파싱 오류나 애플리케이션 오류
                logger.log(Level.SEVERE, "요청 처리 중 예상치 못한 오류", e); // SEVERE 레벨로 심각한 오류 로그
                totalRequestsFailed.incrementAndGet(); // 실패 요청 수 증가

                // 500 에러 응답 시도 - 클라이언트에게 에러 상황 알림
                try (OutputStream outputStream = clientSocket.getOutputStream()) { // try-with-resources - 출력 스트림 자동 관리
                    HttpResponse errorResponse = HttpResponse.serverError("내부 서버 오류가 발생했습니다"); // HttpResponse.serverError() - 500 에러 응답 생성
                    sendHttpResponse(outputStream, errorResponse); // 에러 응답 전송 시도
                } catch (IOException ioException) { // IOException - 에러 응답 전송 실패
                    // 에러 응답 전송도 실패한 경우 - 로그만 남기고 포기
                    logger.log(Level.SEVERE, "에러 응답 전송 실패", ioException); // 에러 응답 전송 실패 로그
                }

            } finally { // finally 블록 - 예외 발생 여부와 관계없이 실행
                // 활성 연결 수 감소 - 예외 발생 여부와 관계없이 실행
                currentActiveConnections.decrementAndGet(); // AtomicLong.decrementAndGet() - 원자적으로 1 감소 후 값 반환
            }
        }

        /**
         * HTTP 요청 파싱
         * InputStream에서 HTTP 요청을 읽어서 HttpRequest 객체로 변환
         *
         * @param inputStream 클라이언트로부터의 입력 스트림
         * @return 파싱된 HTTP 요청 객체
         * @throws IOException 파싱 실패 시
         */
        private HttpRequest parseHttpRequest(InputStream inputStream) throws IOException { // private 메서드 - HTTP 요청 파싱
            // requestProcessor에 위임 - 파싱 로직 중앙화
            return requestProcessor.parseRequest(inputStream); // ThreadedRequestProcessor.parseRequest() - 요청 파싱 로직 위임
        }

        /**
         * 요청 처리
         * 실제 비즈니스 로직을 실행하여 응답 생성
         *
         * @param request HTTP 요청
         * @return HTTP 응답
         */
        private HttpResponse processRequest(HttpRequest request) { // private 메서드 - 요청 처리
            // requestProcessor에 위임 - 비즈니스 로직 중앙화
            return requestProcessor.processRequest(request); // ThreadedRequestProcessor.processRequest() - 요청 처리 로직 위임
        }

        /**
         * HTTP 응답 전송
         * HttpResponse 객체를 HTTP 프로토콜 형식으로 변환하여 전송
         *
         * @param outputStream 클라이언트로의 출력 스트림
         * @param response 전송할 HTTP 응답
         * @throws IOException 전송 실패 시
         */
        private void sendHttpResponse(OutputStream outputStream, HttpResponse response) throws IOException { // private 메서드 - HTTP 응답 전송
            // requestProcessor에 위임 - 응답 직렬화 로직 중앙화
            requestProcessor.sendResponse(outputStream, response); // ThreadedRequestProcessor.sendResponse() - 응답 전송 로직 위임
        }
    }

    // ========== 내부 클래스: ServerStatistics ==========

    /**
     * 서버 통계 정보를 담는 불변 클래스
     * 모니터링과 운영에 사용되는 메트릭들
     */
    public static class ServerStatistics { // public static 내부 클래스 - 외부에서 독립적으로 사용 가능
        // 모든 필드를 final로 선언하여 불변성 보장
        private final long totalRequestsReceived;    // final long - 불변 필드, 받은 총 요청 수
        private final long totalRequestsProcessed;   // final long - 불변 필드, 처리 완료된 요청 수
        private final long totalRequestsFailed;      // final long - 불변 필드, 실패한 요청 수
        private final long currentActiveConnections; // final long - 불변 필드, 현재 활성 연결 수
        private final int port;                      // final int - 불변 필드, 서버 포트
        private final int threadPoolSize;            // final int - 불변 필드, 스레드풀 크기

        /**
         * ServerStatistics 생성자
         * 모든 통계 값을 한 번에 설정
         */
        public ServerStatistics(long totalRequestsReceived, long totalRequestsProcessed, // public 생성자 - 모든 통계 값을 매개변수로 받음
                                long totalRequestsFailed, long currentActiveConnections,
                                int port, int threadPoolSize) {
            this.totalRequestsReceived = totalRequestsReceived; // this.totalRequestsReceived - 현재 객체의 필드에 매개변수 값 할당
            this.totalRequestsProcessed = totalRequestsProcessed; // this.totalRequestsProcessed - 처리 완료 수 할당
            this.totalRequestsFailed = totalRequestsFailed; // this.totalRequestsFailed - 실패 수 할당
            this.currentActiveConnections = currentActiveConnections; // this.currentActiveConnections - 활성 연결 수 할당
            this.port = port; // this.port - 포트 번호 할당
            this.threadPoolSize = threadPoolSize; // this.threadPoolSize - 스레드풀 크기 할당
        }

        // Getter 메서드들 - 불변 객체이므로 setter는 없음
        public long getTotalRequestsReceived() { return totalRequestsReceived; } // public getter - 총 요청 수 반환
        public long getTotalRequestsProcessed() { return totalRequestsProcessed; } // public getter - 처리 완료 수 반환
        public long getTotalRequestsFailed() { return totalRequestsFailed; } // public getter - 실패 수 반환
        public long getCurrentActiveConnections() { return currentActiveConnections; } // public getter - 활성 연결 수 반환
        public int getPort() { return port; } // public getter - 포트 번호 반환
        public int getThreadPoolSize() { return threadPoolSize; } // public getter - 스레드풀 크기 반환

        /**
         * 성공률 계산
         *
         * @return 성공률 (0.0 ~ 1.0)
         */
        public double getSuccessRate() { // public 메서드 - 성공률 계산
            return totalRequestsReceived > 0 ? // 삼항 연산자 - 총 요청이 0보다 큰 경우
                    (double) totalRequestsProcessed / totalRequestsReceived : 0.0; // 형변환 - long을 double로 변환하여 나눗셈, 성공률 계산
        }

        /**
         * 실패율 계산
         *
         * @return 실패율 (0.0 ~ 1.0)
         */
        public double getFailureRate() { // public 메서드 - 실패율 계산
            return totalRequestsReceived > 0 ? // 삼항 연산자 - 총 요청이 0보다 큰 경우
                    (double) totalRequestsFailed / totalRequestsReceived : 0.0; // 형변환 - long을 double로 변환하여 나눗셈, 실패율 계산
        }

        /**
         * 통계 정보를 문자열로 표현
         * 모니터링 대시보드나 로그에서 사용
         */
        @Override // 어노테이션 - Object.toString() 메서드 재정의
        public String toString() { // public 메서드 - 객체의 문자열 표현
            return String.format( // String.format() - 문자열 템플릿 사용
                    "ServerStatistics{받은요청=%d, 처리완료=%d, 실패=%d, 활성연결=%d, " + // 통계 정보 템플릿
                            "포트=%d, 스레드풀=%d, 성공률=%.2f%%, 실패율=%.2f%%}", // 포트, 스레드풀, 성공률, 실패율 템플릿
                    totalRequestsReceived, totalRequestsProcessed, totalRequestsFailed, // 요청 관련 통계들
                    currentActiveConnections, port, threadPoolSize, // 연결, 포트, 스레드풀 정보
                    getSuccessRate() * 100, getFailureRate() * 100 // getSuccessRate() * 100 - 성공률을 백분율로 변환
            );
        }
    }
}