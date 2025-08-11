package com.serverarch.traditional;

import com.serverarch.common.http.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

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
public class ThreadedServer {

    // 로거 - 서버 동작 상태와 문제 진단을 위한 로깅
    private static final Logger logger = Logger.getLogger(ThreadedServer.class.getName());

    // 서버 바인딩 포트
    // final로 선언하여 서버 시작 후 변경 불가 - 설정 일관성 보장
    private final int port;

    // 스레드풀 크기 설정
    // final로 선언하여 서버 설정 불변성 보장
    private final int threadPoolSize;

    // 연결 대기 큐 크기 (ServerSocket backlog)
    // 동시에 대기할 수 있는 연결 요청 수 제한
    private final int backlog;

    // 서버 실행 상태
    // AtomicBoolean 사용으로 멀티스레드 환경에서 안전한 상태 관리
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 서버 소켓
    // volatile로 선언하여 다른 스레드에서 즉시 변경사항 확인 가능
    private volatile ServerSocket serverSocket;

    // 스레드풀 - 요청 처리용 스레드들을 관리
    // 스레드 생성/소멸 비용을 줄이고 동시 처리 수를 제어
    private ExecutorService threadPool;

    // 요청 처리기 - 실제 HTTP 요청/응답 처리 로직
    // 의존성 주입으로 테스트 용이성과 확장성 확보
    private ThreadedRequestProcessor requestProcessor;

    // 통계 카운터들 - 서버 성능 모니터링을 위한 메트릭
    private final AtomicLong totalRequestsReceived = new AtomicLong(0);    // 받은 총 요청 수
    private final AtomicLong totalRequestsProcessed = new AtomicLong(0);   // 처리 완료된 요청 수
    private final AtomicLong totalRequestsFailed = new AtomicLong(0);      // 실패한 요청 수
    private final AtomicLong currentActiveConnections = new AtomicLong(0); // 현재 활성 연결 수

    // 기본 설정 상수들
    private static final int DEFAULT_THREAD_POOL_SIZE = 200;  // 기본 스레드풀 크기 - CPU 코어 수의 배수로 설정
    private static final int DEFAULT_BACKLOG = 50;            // 기본 백로그 크기 - 적당한 대기 큐 크기
    private static final int SOCKET_TIMEOUT = 30000;          // 소켓 타임아웃 30초 - 무한 대기 방지

    /**
     * 기본 설정으로 서버 생성
     *
     * @param port 서버 포트
     */
    public ThreadedServer(int port) {
        // 기본 설정값들로 상세 생성자 호출
        this(port, DEFAULT_THREAD_POOL_SIZE, DEFAULT_BACKLOG);
    }

    /**
     * 포트와 스레드풀 크기만 지정하는 생성자 (누락되었던 생성자)
     * ThreadedServerLauncher에서 사용하는 생성자
     *
     * @param port 서버 포트 (1-65535)
     * @param threadPoolSize 스레드풀 크기 (최소 1)
     */
    public ThreadedServer(int port, int threadPoolSize) {
        // 기본 백로그 크기와 함께 상세 생성자 호출
        this(port, threadPoolSize, DEFAULT_BACKLOG);
    }

    /**
     * 상세 설정으로 서버 생성
     *
     * @param port 서버 포트 (1-65535)
     * @param threadPoolSize 스레드풀 크기 (최소 1)
     * @param backlog 연결 대기 큐 크기 (최소 1)
     */
    public ThreadedServer(int port, int threadPoolSize, int backlog) {
        // 포트 번호 유효성 검증 - 시스템 포트와 사용자 포트 범위 확인
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("포트 번호는 1-65535 사이여야 합니다: " + port);
        }

        // 스레드풀 크기 유효성 검증 - 최소 1개는 있어야 요청 처리 가능
        if (threadPoolSize < 1) {
            throw new IllegalArgumentException("스레드풀 크기는 1 이상이어야 합니다: " + threadPoolSize);
        }

        // 백로그 크기 유효성 검증 - 최소 1개는 있어야 연결 대기 가능
        if (backlog < 1) {
            throw new IllegalArgumentException("백로그 크기는 1 이상이어야 합니다: " + backlog);
        }

        // 인스턴스 변수 초기화 - 검증된 값들로 설정
        this.port = port;
        this.threadPoolSize = threadPoolSize;
        this.backlog = backlog;

        // 로그로 서버 설정 기록 - 디버깅과 모니터링에 유용
        logger.info(String.format("ThreadedServer 생성됨 - 포트: %d, 스레드풀: %d, 백로그: %d",
                port, threadPoolSize, backlog));
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
    public void start() throws IOException {
        // 중복 시작 방지 - AtomicBoolean의 compareAndSet으로 원자적 상태 변경
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("서버가 이미 실행 중입니다");
        }

        try {
            // 1. ServerSocket 생성 및 설정
            serverSocket = new ServerSocket();

            // 주소 재사용 허용 - 서버 재시작 시 "Address already in use" 오류 방지
            serverSocket.setReuseAddress(true);

            // 포트 바인딩 - 지정된 포트에서 클라이언트 연결 대기
            serverSocket.bind(new InetSocketAddress(port), backlog);

            // 2. 스레드풀 초기화 - 고정 크기 스레드풀로 리소스 제어
            threadPool = Executors.newFixedThreadPool(threadPoolSize, new ThreadFactory() {
                // 스레드 번호 카운터 - 스레드 이름 생성용
                private final AtomicLong threadNumber = new AtomicLong(1);

                @Override
                public Thread newThread(Runnable r) {
                    // 의미있는 스레드 이름 설정 - 디버깅과 모니터링에 유용
                    Thread thread = new Thread(r, "ThreadedServer-Worker-" + threadNumber.getAndIncrement());

                    // 데몬 스레드로 설정하지 않음 - 요청 처리 완료까지 JVM 종료 방지
                    thread.setDaemon(false);

                    return thread;
                }
            });

            // 3. 요청 처리기 생성 - 실제 HTTP 요청/응답 처리 담당
            this.requestProcessor = new ThreadedRequestProcessor();

            // 서버 시작 로그
            logger.info(String.format("ThreadedServer가 포트 %d에서 시작되었습니다 (스레드풀: %d)",
                    port, threadPoolSize));

            // 4. 메인 서버 루프 시작 - 클라이언트 연결 수락 및 처리
            runServerLoop();

        } catch (Exception e) {
            // 시작 실패 시 상태 복원 - 다음 시작 시도를 위해
            running.set(false);

            // 리소스 정리 - 메모리 누수 방지
            cleanup();

            // 원본 예외를 포장하여 재던짐 - 호출자에게 구체적인 실패 원인 전달
            throw new IOException("서버 시작 실패", e);
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
    public void stop() {
        // 이미 중지된 경우 무시 - 중복 호출 방지
        if (!running.compareAndSet(true, false)) {
            logger.info("서버가 이미 중지되었거나 실행 중이 아닙니다");
            return;
        }

        logger.info("서버 중지를 시작합니다...");

        try {
            // 1. ServerSocket 닫기 - 새로운 연결 수락 중지
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            // 2. 스레드풀 우아한 종료
            if (threadPool != null) {
                // 새로운 작업 수락 중지
                threadPool.shutdown();

                try {
                    // 기존 작업 완료 대기 (최대 30초)
                    if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                        logger.warning("일부 작업이 시간 내에 완료되지 않아 강제 종료합니다");

                        // 강제 종료 - 응답하지 않는 작업들을 중단
                        threadPool.shutdownNow();

                        // 강제 종료 후 추가 대기 (최대 10초)
                        if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                            logger.severe("스레드풀을 완전히 종료할 수 없습니다");
                        }
                    }
                } catch (InterruptedException e) {
                    // 인터럽트 발생 시 즉시 강제 종료
                    threadPool.shutdownNow();

                    // 현재 스레드의 인터럽트 상태 복원 - 호출자가 인터럽트를 처리할 수 있도록
                    Thread.currentThread().interrupt();
                }
            }

            // 3. 최종 통계 로그 출력
            logFinalStatistics();

            logger.info("ThreadedServer가 성공적으로 중지되었습니다");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "서버 종료 중 오류 발생", e);
        } finally {
            // 4. 최종 리소스 정리 - 예외 발생 여부와 관계없이 실행
            cleanup();
        }
    }

    /**
     * 메인 서버 루프
     * 클라이언트 연결을 수락하고 각 연결을 별도 스레드에서 처리
     */
    private void runServerLoop() {
        // 서버가 실행 중인 동안 계속 반복
        while (running.get()) {
            try {
                // 클라이언트 연결 수락 - 블로킹 호출
                // 클라이언트가 연결할 때까지 이 지점에서 대기
                Socket clientSocket = serverSocket.accept();

                // 연결 통계 업데이트 - 모니터링을 위한 메트릭
                totalRequestsReceived.incrementAndGet();
                currentActiveConnections.incrementAndGet();

                // 클라이언트 소켓 설정 - 타임아웃과 성능 최적화
                configureClientSocket(clientSocket);

                // 요청 처리를 스레드풀에 제출 - Thread-per-Request 모델의 핵심
                threadPool.submit(new RequestHandler(clientSocket));

            } catch (SocketException e) {
                // ServerSocket이 닫힌 경우 (정상적인 종료)
                if (running.get()) {
                    // 서버가 실행 중인데 SocketException이 발생하면 비정상 상황
                    logger.log(Level.WARNING, "서버 소켓 예외 발생", e);
                }
                break; // 루프 종료

            } catch (IOException e) {
                // 연결 수락 실패 - 일시적인 문제일 수 있으므로 계속 진행
                if (running.get()) {
                    logger.log(Level.WARNING, "클라이언트 연결 수락 실패", e);
                }

            } catch (RejectedExecutionException e) {
                // 스레드풀이 포화 상태 - 백프레셔 처리
                logger.log(Level.WARNING, "스레드풀 포화로 요청 거부", e);
                totalRequestsFailed.incrementAndGet();

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
    private void configureClientSocket(Socket clientSocket) throws SocketException {
        // 소켓 타임아웃 설정 - 무한 대기 방지
        clientSocket.setSoTimeout(SOCKET_TIMEOUT);

        // TCP_NODELAY 활성화 - Nagle 알고리즘 비활성화로 응답성 향상
        clientSocket.setTcpNoDelay(true);

        // Keep-Alive 활성화 - 연결 유지로 성능 향상
        clientSocket.setKeepAlive(true);
    }

    /**
     * 최종 통계를 로그에 출력
     * 서버 종료 시 전체 처리 통계 요약
     */
    private void logFinalStatistics() {
        logger.info(String.format(
                "서버 통계 - 총 요청: %d, 처리 완료: %d, 실패: %d, 활성 연결: %d",
                totalRequestsReceived.get(),
                totalRequestsProcessed.get(),
                totalRequestsFailed.get(),
                currentActiveConnections.get()
        ));
    }

    /**
     * 리소스 정리
     * 메모리 누수 방지를 위한 최종 정리 작업
     */
    private void cleanup() {
        // ServerSocket 정리
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "서버 소켓 닫기 실패", e);
            }
        }

        // 스레드풀 강제 종료 (정상 종료가 실패한 경우)
        if (threadPool != null && !threadPool.isTerminated()) {
            threadPool.shutdownNow();
        }

        // 요청 처리기 정리
        if (requestProcessor != null) {
            // 현재는 정리할 리소스가 없지만 확장성을 위해 준비
            requestProcessor = null;
        }
    }

    // ========== 상태 조회 메서드들 ==========

    /**
     * 서버 실행 상태 확인
     *
     * @return 서버가 실행 중이면 true
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 현재 서버 통계 반환
     * 모니터링과 운영에 사용
     *
     * @return 서버 통계 객체
     */
    public ServerStatistics getStatistics() {
        return new ServerStatistics(
                totalRequestsReceived.get(),
                totalRequestsProcessed.get(),
                totalRequestsFailed.get(),
                currentActiveConnections.get(),
                port,
                threadPoolSize
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
    private class RequestHandler implements Runnable {

        // 처리할 클라이언트 소켓
        // final로 선언하여 핸들러 생성 후 변경 불가
        private final Socket clientSocket;

        /**
         * RequestHandler 생성자
         *
         * @param clientSocket 처리할 클라이언트 소켓
         */
        public RequestHandler(Socket clientSocket) {
            // null 체크는 호출자가 보장한다고 가정 - 내부 클래스이므로 신뢰
            this.clientSocket = clientSocket;
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
        @Override
        public void run() {
            // 요청 처리 시작 시간 기록 - 성능 측정용
            long startTime = System.currentTimeMillis();

            // try-with-resources로 자동 리소스 관리
            // 예외 발생 여부와 관계없이 소켓이 확실히 닫힘
            try (Socket socket = clientSocket;
                 InputStream inputStream = socket.getInputStream();
                 OutputStream outputStream = socket.getOutputStream()) {

                // 1. HTTP 요청 파싱 - 블로킹 I/O
                HttpRequest request = parseHttpRequest(inputStream);

                // 2. 요청 처리 - 비즈니스 로직 실행
                HttpResponse response = processRequest(request);

                // 3. HTTP 응답 전송 - 블로킹 I/O
                sendHttpResponse(outputStream, response);

                // 성공 통계 업데이트
                totalRequestsProcessed.incrementAndGet();

                // 처리 시간 로그 (디버그 레벨)
                long processingTime = System.currentTimeMillis() - startTime;
                logger.fine(String.format("요청 처리 완료: %s %s (%dms)",
                        request.getMethod(), request.getPath(), processingTime));

            } catch (IOException e) {
                // I/O 오류 처리 - 네트워크 문제나 클라이언트 연결 끊김
                logger.log(Level.WARNING, "요청 처리 중 I/O 오류", e);
                totalRequestsFailed.incrementAndGet();

            } catch (Exception e) {
                // 기타 예외 처리 - 파싱 오류나 애플리케이션 오류
                logger.log(Level.SEVERE, "요청 처리 중 예상치 못한 오류", e);
                totalRequestsFailed.incrementAndGet();

                // 500 에러 응답 시도 - 클라이언트에게 에러 상황 알림
                try (OutputStream outputStream = clientSocket.getOutputStream()) {
                    HttpResponse errorResponse = HttpResponse.serverError("내부 서버 오류가 발생했습니다");
                    sendHttpResponse(outputStream, errorResponse);
                } catch (IOException ioException) {
                    // 에러 응답 전송도 실패한 경우 - 로그만 남기고 포기
                    logger.log(Level.SEVERE, "에러 응답 전송 실패", ioException);
                }

            } finally {
                // 활성 연결 수 감소 - 예외 발생 여부와 관계없이 실행
                currentActiveConnections.decrementAndGet();
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
        private HttpRequest parseHttpRequest(InputStream inputStream) throws IOException {
            // requestProcessor에 위임 - 파싱 로직 중앙화
            return requestProcessor.parseRequest(inputStream);
        }

        /**
         * 요청 처리
         * 실제 비즈니스 로직을 실행하여 응답 생성
         *
         * @param request HTTP 요청
         * @return HTTP 응답
         */
        private HttpResponse processRequest(HttpRequest request) {
            // requestProcessor에 위임 - 비즈니스 로직 중앙화
            return requestProcessor.processRequest(request);
        }

        /**
         * HTTP 응답 전송
         * HttpResponse 객체를 HTTP 프로토콜 형식으로 변환하여 전송
         *
         * @param outputStream 클라이언트로의 출력 스트림
         * @param response 전송할 HTTP 응답
         * @throws IOException 전송 실패 시
         */
        private void sendHttpResponse(OutputStream outputStream, HttpResponse response) throws IOException {
            // requestProcessor에 위임 - 응답 직렬화 로직 중앙화
            requestProcessor.sendResponse(outputStream, response);
        }
    }

    // ========== 내부 클래스: ServerStatistics ==========

    /**
     * 서버 통계 정보를 담는 불변 클래스
     * 모니터링과 운영에 사용되는 메트릭들
     */
    public static class ServerStatistics {
        // 모든 필드를 final로 선언하여 불변성 보장
        private final long totalRequestsReceived;    // 받은 총 요청 수
        private final long totalRequestsProcessed;   // 처리 완료된 요청 수
        private final long totalRequestsFailed;      // 실패한 요청 수
        private final long currentActiveConnections; // 현재 활성 연결 수
        private final int port;                      // 서버 포트
        private final int threadPoolSize;            // 스레드풀 크기

        /**
         * ServerStatistics 생성자
         * 모든 통계 값을 한 번에 설정
         */
        public ServerStatistics(long totalRequestsReceived, long totalRequestsProcessed,
                                long totalRequestsFailed, long currentActiveConnections,
                                int port, int threadPoolSize) {
            this.totalRequestsReceived = totalRequestsReceived;
            this.totalRequestsProcessed = totalRequestsProcessed;
            this.totalRequestsFailed = totalRequestsFailed;
            this.currentActiveConnections = currentActiveConnections;
            this.port = port;
            this.threadPoolSize = threadPoolSize;
        }

        // Getter 메서드들 - 불변 객체이므로 setter는 없음
        public long getTotalRequestsReceived() { return totalRequestsReceived; }
        public long getTotalRequestsProcessed() { return totalRequestsProcessed; }
        public long getTotalRequestsFailed() { return totalRequestsFailed; }
        public long getCurrentActiveConnections() { return currentActiveConnections; }
        public int getPort() { return port; }
        public int getThreadPoolSize() { return threadPoolSize; }

        /**
         * 성공률 계산
         *
         * @return 성공률 (0.0 ~ 1.0)
         */
        public double getSuccessRate() {
            return totalRequestsReceived > 0 ?
                    (double) totalRequestsProcessed / totalRequestsReceived : 0.0;
        }

        /**
         * 실패율 계산
         *
         * @return 실패율 (0.0 ~ 1.0)
         */
        public double getFailureRate() {
            return totalRequestsReceived > 0 ?
                    (double) totalRequestsFailed / totalRequestsReceived : 0.0;
        }

        /**
         * 통계 정보를 문자열로 표현
         * 모니터링 대시보드나 로그에서 사용
         */
        @Override
        public String toString() {
            return String.format(
                    "ServerStatistics{받은요청=%d, 처리완료=%d, 실패=%d, 활성연결=%d, " +
                            "포트=%d, 스레드풀=%d, 성공률=%.2f%%, 실패율=%.2f%%}",
                    totalRequestsReceived, totalRequestsProcessed, totalRequestsFailed,
                    currentActiveConnections, port, threadPoolSize,
                    getSuccessRate() * 100, getFailureRate() * 100
            );
        }
    }
}