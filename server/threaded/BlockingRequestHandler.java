package server.threaded;

import server.core.http.*;
import server.core.routing.Router;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;

/**
 * 최적화된 블로킹 I/O 요청 핸들러
 *
 * 이 클래스는 Runnable 인터페이스를 구현하여 스레드에서 실행될 수 있도록 합니다.
 * 각 클라이언트 연결을 개별적으로 처리하며, HTTP Keep-Alive를 지원합니다.
 * ServletContainer와 Router 모두를 지원하는 하이브리드 구조입니다.
 *
 * 성능 개선: 단순화된 처리 로직, 디버그 오버헤드 제거
 */
public class BlockingRequestHandler implements Runnable {

    // === 핵심 컴포넌트들 ===
    private final Socket clientSocket;                      // 클라이언트와의 TCP 연결 소켓
    private final Router router;                            // HTTP 요청을 라우팅하는 라우터
    private final ThreadedMiniServletContainer servletContainer;  // 서블릿 컨테이너 (선택적)
    private final RequestHandlerConfig config;             // 요청 처리 설정
    private final long startTime;                          // 핸들러 시작 시간 (성능 측정용)

    // === 성능 최적화를 위한 사전 계산된 값들 ===
    /*
     * 디버그 모드에서 반복적으로 호출되는 값들을 미리 계산하여 저장
     * 매번 메서드 호출하는 오버헤드를 줄이기 위함
     */
    private final boolean debugMode;                       // 디버그 모드 여부
    private final String threadName;                       // 현재 스레드 이름 (디버그용)
    private final String clientAddress;                    // 클라이언트 주소 (디버그용)

    /**
     * 기존 생성자 (하위 호환성 유지)
     * ServletContainer 없이 Router만 사용하는 경우
     *
     * @param clientSocket 클라이언트 소켓
     * @param router HTTP 라우터
     * @param config 요청 처리 설정
     */
    public BlockingRequestHandler(Socket clientSocket, Router router, RequestHandlerConfig config) {
        this(clientSocket, router, null, config);  // ServletContainer는 null로 설정
    }

    /**
     * 새로운 생성자 (ServletContainer 포함)
     * ServletContainer와 Router를 모두 지원하는 완전한 생성자
     *
     * @param clientSocket 클라이언트 소켓 - TCP 연결을 나타냄
     * @param router HTTP 라우터 - URL 패턴에 따라 핸들러 매핑
     * @param servletContainer 서블릿 컨테이너 - 서블릿 기반 요청 처리
     * @param config 요청 처리 설정 - 타임아웃, 디버그 모드 등
     */
    public BlockingRequestHandler(Socket clientSocket, Router router,
                                  ThreadedMiniServletContainer servletContainer, RequestHandlerConfig config) {
        this.clientSocket = clientSocket;
        this.router = router;
        this.servletContainer = servletContainer;
        this.config = config;
        this.startTime = System.currentTimeMillis();  // 현재 시간을 밀리초로 기록

        /*
         * 성능 최적화: 반복 호출되는 값들 미리 계산
         * 디버그 모드가 아닌 경우 불필요한 계산을 피함
         */
        this.debugMode = config.isDebugMode();
        // 삼항 연산자 사용: debugMode가 true면 스레드 이름 계산, false면 null
        this.threadName = debugMode ? Thread.currentThread().getName() : null;
        // Socket.getRemoteSocketAddress(): 원격 클라이언트의 IP주소와 포트 반환
        this.clientAddress = debugMode ? clientSocket.getRemoteSocketAddress().toString() : null;
    }

    /**
     * Runnable 인터페이스의 run() 메서드 구현
     * 스레드풀에서 이 메서드가 실행되어 클라이언트 요청을 처리합니다.
     */
    @Override
    public void run() {
        // === HTTP Keep-Alive 연결 처리 변수들 ===
        boolean keepAlive = true;           // Keep-Alive 연결 유지 여부
        int requestCount = 0;               // 현재 연결에서 처리한 요청 수

        try {
            /*
             * 소켓 타임아웃 설정
             * Socket.setSoTimeout(): 소켓 읽기 작업의 최대 대기 시간 설정
             * 이 시간이 지나면 SocketTimeoutException 발생
             */
            clientSocket.setSoTimeout(config.getSocketTimeout());

            // 디버그 로그 (성능 최적화: 조건문 한 번만 체크)
            logDebug("Handling connection from: " + clientAddress +
                    (servletContainer != null ? " (ServletContainer enabled)" : ""));

            /*
             * Keep-Alive 연결 처리 루프
             * 하나의 TCP 연결로 여러 HTTP 요청을 순차적으로 처리
             *
             * 종료 조건:
             * 1. keepAlive가 false가 되는 경우 (Connection: close)
             * 2. 최대 요청 수에 도달한 경우 (DoS 공격 방지)
             */
            while (keepAlive && requestCount < config.getMaxRequestsPerConnection()) {
                try {
                    /*
                     * HTTP 요청 파싱
                     * InputStream에서 HTTP 요청을 읽어와 HttpRequest 객체로 변환
                     * null 반환시 클라이언트가 연결을 종료한 것으로 판단
                     */
                    HttpRequest request = parseRequest(clientSocket.getInputStream());
                    if (request == null) {
                        break; // 연결 종료
                    }

                    requestCount++;  // 요청 카운터 증가
                    // 디버그 모드일 때만 시간 측정 (성능 최적화)
                    long requestStartTime = debugMode ? System.currentTimeMillis() : 0;

                    /*
                     * 최적화된 요청 처리
                     * ServletContainer 우선, 실패시 Router 사용하는 fallback 로직
                     */
                    HttpResponse response = processRequestOptimized(request);

                    /*
                     * Keep-Alive 확인
                     * HTTP 헤더와 설정을 기반으로 연결을 유지할지 결정
                     * 최대 요청 수 체크도 포함
                     */
                    keepAlive = shouldKeepAlive(request, response) &&
                            requestCount < config.getMaxRequestsPerConnection();

                    /*
                     * HTTP 응답 전송
                     * OutputStream에 HTTP 응답을 기록하고 flush
                     */
                    sendResponse(response, clientSocket.getOutputStream());

                    // 디버그 로그 (성능 최적화: 필요할 때만 시간 계산)
                    if (debugMode) {
                        long requestTime = System.currentTimeMillis() - requestStartTime;
                        logDebug("Request " + requestCount + " processed in " + requestTime +
                                "ms - " + request.getMethod() + " " + request.getPath());
                    }

                    // Keep-Alive가 아니면 연결 종료
                    if (!keepAlive) {
                        break;
                    }

                } catch (SocketTimeoutException e) {
                    /*
                     * 소켓 타임아웃 발생
                     * 클라이언트가 지정된 시간 내에 요청을 보내지 않음
                     */
                    logDebug("Socket timeout - closing connection");
                    break;
                } catch (IOException e) {
                    /*
                     * I/O 오류 발생
                     * 네트워크 문제, 클라이언트 연결 끊김 등
                     */
                    logDebug("I/O error: " + e.getMessage());
                    break;
                }
            }

        } catch (Exception e) {
            /*
             * 예상치 못한 오류 처리
             * 에러 로그는 항상 출력 (성능보다 안정성 중요)
             * System.err: 표준 에러 스트림, 즉시 출력됨
             */
            System.err.println("[" + Thread.currentThread().getName() +
                    "] Error handling connection: " + e.getMessage());

            /*
             * 500 에러 응답 시도
             * 클라이언트에게 서버 오류를 알림
             */
            try {
                HttpResponse errorResponse = HttpResponse.internalServerError();
                sendResponse(errorResponse, clientSocket.getOutputStream());
            } catch (IOException ignored) {
                // 응답 전송 실패는 무시 (이미 연결이 끊어진 상태일 수 있음)
            }

        } finally {
            /*
             * finally 블록: 예외 발생 여부와 관계없이 항상 실행
             * 리소스 정리 작업 수행
             */
            closeConnection();

            // 디버그 로그 (성능 최적화: 필요할 때만 시간 계산)
            if (debugMode) {
                long totalTime = System.currentTimeMillis() - startTime;
                logDebug("Connection closed - total time: " + totalTime +
                        "ms, requests: " + requestCount);
            }
        }
    }

    /**
     * 최적화된 요청 처리 메서드
     * ServletContainer 우선, 실패시 Router 사용 (fallback 로직 단순화)
     *
     * 처리 순서:
     * 1. ServletContainer가 있으면 ServletContainer 먼저 시도
     * 2. ServletContainer에서 처리 실패시 Router로 fallback
     * 3. 모든 처리 실패시 500 에러 반환
     *
     * @param request HTTP 요청 객체
     * @return HTTP 응답 객체
     */
    private HttpResponse processRequestOptimized(HttpRequest request) {
        try {
            // ServletContainer가 있으면 ServletContainer만 사용
            if (servletContainer != null) {
                /*
                 * CompletableFuture<HttpResponse>: 비동기 작업의 결과를 나타냄
                 * 서블릿 처리가 비동기일 수 있으므로 Future를 반환
                 */
                CompletableFuture<HttpResponse> servletFuture = servletContainer.handleRequest(request);
                if (servletFuture != null) {
                    /*
                     * CompletableFuture.get(): 비동기 작업의 완료를 기다리고 결과 반환
                     * 블로킹 호출이므로 결과가 나올 때까지 대기
                     */
                    HttpResponse servletResponse = servletFuture.get();
                    if (servletResponse != null) {
                        logDebug("Request handled by ServletContainer: " + request.getPath());
                        return servletResponse;
                    }
                }
                // ServletContainer에서 null 반환시 Router로 fallback
            }

            /*
             * Router로 처리
             * Router.routeWithMiddlewares(): 미들웨어 체인을 거쳐 요청 처리
             * 반환값은 CompletableFuture<HttpResponse>이므로 .get()으로 결과 추출
             */
            HttpResponse routerResponse = router.routeWithMiddlewares(request).get();
            logDebug("Request handled by Router: " + request.getPath());
            return routerResponse;

        } catch (Exception e) {
            /*
             * 요청 처리 중 예외 발생
             * 에러 로그는 성능보다 안정성이 중요하므로 항상 출력
             */
            System.err.println("Request processing error for " + request.getPath() +
                    ": " + e.getMessage());
            return HttpResponse.internalServerError("Request processing failed");
        }
    }

    /**
     * HTTP 요청 파싱 (최적화된 버전)
     * InputStream에서 HTTP 요청을 읽어와 HttpRequest 객체로 변환
     *
     * @param inputStream 클라이언트 소켓의 입력 스트림
     * @return 파싱된 HTTP 요청 객체, 연결 종료시 null
     * @throws IOException I/O 오류 발생시
     */
    private HttpRequest parseRequest(InputStream inputStream) throws IOException {
        try {
            /*
             * InputStream을 BufferedInputStream으로 감싸서 mark/reset 지원
             * BufferedInputStream: 내부 버퍼를 사용하여 I/O 성능 향상
             * mark/reset: 스트림의 특정 위치를 표시하고 되돌아갈 수 있는 기능
             */
            if (!(inputStream instanceof BufferedInputStream)) {
                inputStream = new BufferedInputStream(inputStream, 8192);  // 8KB 버퍼
            }

            /*
             * 요청이 있는지 확인 (peek)
             * mark(1): 현재 위치에서 1바이트까지 되돌아갈 수 있도록 표시
             * read(): 다음 바이트를 읽음, 스트림 끝이면 -1 반환
             * reset(): mark로 표시한 위치로 되돌아감
             */
            inputStream.mark(1);
            int firstByte = inputStream.read();
            if (firstByte == -1) {
                return null; // 연결 종료 (EOF)
            }
            inputStream.reset();  // 읽은 바이트를 다시 스트림에 되돌림

            /*
             * HttpParser.parseRequest(): HTTP 요청 문자열을 파싱하여 객체화
             * HTTP 스펙에 따라 Request Line, Headers, Body를 분석
             */
            return HttpParser.parseRequest(inputStream);

        } catch (IOException e) {
            logDebug("Request parsing failed: " + e.getMessage());
            throw e;  // 예외를 다시 던져서 상위에서 처리하도록 함
        }
    }

    /**
     * Keep-Alive 여부 결정 (최적화된 버전)
     * HTTP 헤더와 프로토콜 버전을 분석하여 연결 유지 여부 결정
     *
     * @param request HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @return Keep-Alive 연결을 유지할지 여부
     */
    private boolean shouldKeepAlive(HttpRequest request, HttpResponse response) {
        /*
         * HTTP Connection 헤더 확인
         * "Connection: close" -> 연결 종료
         * "Connection: keep-alive" -> 연결 유지 (HTTP/1.0)
         * HTTP/1.1에서는 기본적으로 Keep-Alive
         */
        String requestConnection = request.getHeader("Connection");
        String responseConnection = response.getHeaders().get("Connection");

        /*
         * null 체크와 동시에 비교 (성능 최적화)
         * String.equalsIgnoreCase(): 대소문자 구분 없이 문자열 비교
         * "close"가 아니면 Keep-Alive로 간주
         */
        boolean requestKeepAlive = !"close".equalsIgnoreCase(requestConnection);
        boolean responseKeepAlive = !"close".equalsIgnoreCase(responseConnection);

        /*
         * HTTP/1.1에서만 Keep-Alive 지원
         * 요청과 응답 모두 Keep-Alive를 허용해야 함
         */
        return requestKeepAlive && responseKeepAlive && "HTTP/1.1".equals(request.getVersion());
    }

    /**
     * HTTP 응답 전송 (최적화된 버전)
     * HttpResponse 객체를 HTTP 프로토콜 형식으로 변환하여 클라이언트에게 전송
     *
     * @param response HTTP 응답 객체
     * @param outputStream 클라이언트 소켓의 출력 스트림
     * @throws IOException I/O 오류 발생시
     */
    private void sendResponse(HttpResponse response, OutputStream outputStream) throws IOException {
        try {
            /*
             * HttpResponse.writeTo(): HTTP 응답을 스트림에 기록
             * Status Line, Headers, Body를 HTTP 프로토콜 형식으로 출력
             */
            response.writeTo(outputStream);

            /*
             * OutputStream.flush(): 버퍼에 있는 데이터를 즉시 전송
             * 버퍼링된 데이터가 실제로 네트워크로 전송되도록 보장
             */
            outputStream.flush();

        } catch (IOException e) {
            // 에러는 항상 로그 (안정성 중요)
            System.err.println("Response sending failed: " + e.getMessage());
            throw e;  // 예외를 다시 던져서 상위에서 처리
        }
    }

    /**
     * 연결 종료 (최적화된 버전)
     * TCP 연결을 정상적으로 종료하는 Graceful shutdown 수행
     */
    private void closeConnection() {
        try {
            /*
             * Socket.isClosed(): 소켓이 이미 닫혔는지 확인
             * 중복 close() 호출 방지
             */
            if (!clientSocket.isClosed()) {
                /*
                 * Graceful shutdown 수행
                 * Socket.shutdownOutput(): 출력 스트림만 먼저 종료
                 * 클라이언트에게 더 이상 데이터를 보내지 않겠다는 신호
                 */
                clientSocket.shutdownOutput();

                /*
                 * Socket.close(): 소켓 완전 종료
                 * 모든 I/O 스트림과 네트워크 연결 해제
                 */
                clientSocket.close();
            }
        } catch (IOException e) {
            /*
             * 연결 종료 중 오류 발생
             * 이미 끊어진 연결일 수 있으므로 디버그 로그만 출력
             */
            logDebug("Error closing connection: " + e.getMessage());
        }
    }

    /**
     * 디버그 로그 헬퍼 메서드 (성능 최적화)
     * 디버그 모드일 때만 로그를 출력하여 성능 오버헤드 최소화
     *
     * @param message 출력할 로그 메시지
     */
    private void logDebug(String message) {
        if (debugMode) {
            /*
             * System.out.println(): 표준 출력 스트림에 메시지 출력
             * 스레드 이름을 포함하여 어떤 스레드에서 실행되는지 식별 가능
             */
            System.out.println("[" + threadName + "] " + message);
        }
    }

    /**
     * 핸들러 상태 정보 반환
     * 현재 핸들러의 실행 상태를 문자열로 반환 (모니터링 용도)
     *
     * @return 핸들러 상태 정보 문자열
     */
    public String getStatus() {
        /*
         * String.format(): C 스타일의 포맷 문자열 사용
         * %s: 문자열, %d: 정수, %b: 불리언
         * 현재 시간에서 시작 시간을 빼서 실행 시간(uptime) 계산
         */
        return String.format("BlockingHandler{client=%s, thread=%s, uptime=%dms, servletContainer=%s}",
                clientSocket.getRemoteSocketAddress(),          // 클라이언트 주소
                Thread.currentThread().getName(),               // 현재 스레드 이름
                System.currentTimeMillis() - startTime,         // 실행 시간
                servletContainer != null ? "enabled" : "disabled");  // ServletContainer 사용 여부
    }
}