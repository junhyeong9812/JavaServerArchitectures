package server.threaded;

import server.core.http.*;
import server.core.routing.Router;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;

/**
 * 최적화된 블로킹 I/O 요청 핸들러
 * 성능 개선: 단순화된 처리 로직, 디버그 오버헤드 제거
 */
public class BlockingRequestHandler implements Runnable {

    private final Socket clientSocket;
    private final Router router;
    private final ThreadedMiniServletContainer servletContainer;
    private final RequestHandlerConfig config;
    private final long startTime;

    // 디버그 관련 변수들 (성능 최적화를 위해 미리 계산)
    private final boolean debugMode;
    private final String threadName;
    private final String clientAddress;

    // 기존 생성자 (하위 호환성 유지)
    public BlockingRequestHandler(Socket clientSocket, Router router, RequestHandlerConfig config) {
        this(clientSocket, router, null, config);
    }

    // 새로운 생성자 (ServletContainer 포함)
    public BlockingRequestHandler(Socket clientSocket, Router router,
                                  ThreadedMiniServletContainer servletContainer, RequestHandlerConfig config) {
        this.clientSocket = clientSocket;
        this.router = router;
        this.servletContainer = servletContainer;
        this.config = config;
        this.startTime = System.currentTimeMillis();

        // 성능 최적화: 반복 호출되는 값들 미리 계산
        this.debugMode = config.isDebugMode();
        this.threadName = debugMode ? Thread.currentThread().getName() : null;
        this.clientAddress = debugMode ? clientSocket.getRemoteSocketAddress().toString() : null;
    }

    @Override
    public void run() {
        // Keep-Alive 연결 처리
        boolean keepAlive = true;
        int requestCount = 0;

        try {
            // 소켓 타임아웃 설정
            clientSocket.setSoTimeout(config.getSocketTimeout());

            // 디버그 로그 (성능 최적화: 조건문 한 번만)
            logDebug("Handling connection from: " + clientAddress +
                    (servletContainer != null ? " (ServletContainer enabled)" : ""));

            while (keepAlive && requestCount < config.getMaxRequestsPerConnection()) {
                try {
                    // HTTP 요청 파싱
                    HttpRequest request = parseRequest(clientSocket.getInputStream());
                    if (request == null) {
                        break; // 연결 종료
                    }

                    requestCount++;
                    long requestStartTime = debugMode ? System.currentTimeMillis() : 0;

                    // 최적화된 요청 처리
                    HttpResponse response = processRequestOptimized(request);

                    // Keep-Alive 확인
                    keepAlive = shouldKeepAlive(request, response) &&
                            requestCount < config.getMaxRequestsPerConnection();

                    // 응답 전송
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
                    logDebug("Socket timeout - closing connection");
                    break;
                } catch (IOException e) {
                    logDebug("I/O error: " + e.getMessage());
                    break;
                }
            }

        } catch (Exception e) {
            // 에러 로그는 항상 출력 (성능보다 안정성 중요)
            System.err.println("[" + Thread.currentThread().getName() +
                    "] Error handling connection: " + e.getMessage());

            // 500 에러 응답 시도
            try {
                HttpResponse errorResponse = HttpResponse.internalServerError();
                sendResponse(errorResponse, clientSocket.getOutputStream());
            } catch (IOException ignored) {
                // 응답 전송 실패는 무시
            }

        } finally {
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
     * 최적화된 요청 처리
     * ServletContainer 우선, 실패시 Router 사용 (fallback 로직 단순화)
     */
    private HttpResponse processRequestOptimized(HttpRequest request) {
        try {
            // ServletContainer가 있으면 ServletContainer만 사용
            if (servletContainer != null) {
                CompletableFuture<HttpResponse> servletFuture = servletContainer.handleRequest(request);
                if (servletFuture != null) {
                    HttpResponse servletResponse = servletFuture.get();
                    if (servletResponse != null) {
                        logDebug("Request handled by ServletContainer: " + request.getPath());
                        return servletResponse;
                    }
                }
                // ServletContainer에서 null 반환시 Router로 fallback
            }

            // Router로 처리
            HttpResponse routerResponse = router.routeWithMiddlewares(request).get();
            logDebug("Request handled by Router: " + request.getPath());
            return routerResponse;

        } catch (Exception e) {
            // 에러 로그는 성능보다 안정성이 중요하므로 항상 출력
            System.err.println("Request processing error for " + request.getPath() +
                    ": " + e.getMessage());
            return HttpResponse.internalServerError("Request processing failed");
        }
    }

    /**
     * HTTP 요청 파싱 (최적화된 버전)
     */
    private HttpRequest parseRequest(InputStream inputStream) throws IOException {
        try {
            // InputStream을 BufferedInputStream으로 감싸서 mark/reset 지원
            if (!(inputStream instanceof BufferedInputStream)) {
                inputStream = new BufferedInputStream(inputStream, 8192);
            }

            // 요청이 있는지 확인 (peek)
            inputStream.mark(1);
            int firstByte = inputStream.read();
            if (firstByte == -1) {
                return null; // 연결 종료
            }
            inputStream.reset();

            return HttpParser.parseRequest(inputStream);

        } catch (IOException e) {
            logDebug("Request parsing failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Keep-Alive 여부 결정 (최적화된 버전)
     */
    private boolean shouldKeepAlive(HttpRequest request, HttpResponse response) {
        // HTTP/1.1은 기본적으로 Keep-Alive
        String requestConnection = request.getHeader("Connection");
        String responseConnection = response.getHeaders().get("Connection");

        // null 체크와 동시에 비교 (성능 최적화)
        boolean requestKeepAlive = !"close".equalsIgnoreCase(requestConnection);
        boolean responseKeepAlive = !"close".equalsIgnoreCase(responseConnection);

        return requestKeepAlive && responseKeepAlive && "HTTP/1.1".equals(request.getVersion());
    }

    /**
     * HTTP 응답 전송 (최적화된 버전)
     */
    private void sendResponse(HttpResponse response, OutputStream outputStream) throws IOException {
        try {
            response.writeTo(outputStream);
            outputStream.flush();

        } catch (IOException e) {
            // 에러는 항상 로그 (안정성 중요)
            System.err.println("Response sending failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 연결 종료 (최적화된 버전)
     */
    private void closeConnection() {
        try {
            if (!clientSocket.isClosed()) {
                // Graceful shutdown
                clientSocket.shutdownOutput();
                clientSocket.close();
            }
        } catch (IOException e) {
            logDebug("Error closing connection: " + e.getMessage());
        }
    }

    /**
     * 디버그 로그 헬퍼 메서드 (성능 최적화)
     */
    private void logDebug(String message) {
        if (debugMode) {
            System.out.println("[" + threadName + "] " + message);
        }
    }

    /**
     * 핸들러 상태 정보
     */
    public String getStatus() {
        return String.format("BlockingHandler{client=%s, thread=%s, uptime=%dms, servletContainer=%s}",
                clientSocket.getRemoteSocketAddress(),
                Thread.currentThread().getName(),
                System.currentTimeMillis() - startTime,
                servletContainer != null ? "enabled" : "disabled");
    }
}