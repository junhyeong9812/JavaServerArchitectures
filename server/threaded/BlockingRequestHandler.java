package server.threaded;

import server.core.http.*;
import server.core.routing.Router;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;

/**
 * 블로킹 I/O 요청 핸들러 (수정된 버전)
 * 스레드당 하나의 연결을 처리하는 전통적인 방식
 * Router + ServletContainer 통합 지원
 */
public class BlockingRequestHandler implements Runnable {

    private final Socket clientSocket;
    private final Router router;
    private final ThreadedMiniServletContainer servletContainer;  // ⭐ 추가
    private final RequestHandlerConfig config;
    private final long startTime;

    // ⭐ 기존 생성자 (하위 호환성 유지)
    public BlockingRequestHandler(Socket clientSocket, Router router, RequestHandlerConfig config) {
        this(clientSocket, router, null, config);
    }

    // ⭐ 새로운 생성자 (ServletContainer 포함)
    public BlockingRequestHandler(Socket clientSocket, Router router,
                                  ThreadedMiniServletContainer servletContainer, RequestHandlerConfig config) {
        this.clientSocket = clientSocket;
        this.router = router;
        this.servletContainer = servletContainer;  // ⭐ ServletContainer 저장
        this.config = config;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        String clientAddress = clientSocket.getRemoteSocketAddress().toString();

        // Keep-Alive 연결 처리
        boolean keepAlive = true;
        int requestCount = 0;

        try {
            // 소켓 타임아웃 설정
            clientSocket.setSoTimeout(config.getSocketTimeout());

            if (config.isDebugMode()) {
                System.out.println("[" + threadName + "] Handling connection from: " + clientAddress);
                if (servletContainer != null) {
                    System.out.println("[" + threadName + "] ServletContainer integration enabled");
                }
            }

            while (keepAlive && requestCount < config.getMaxRequestsPerConnection()) {
                try {
                    // HTTP 요청 파싱
                    HttpRequest request = parseRequest(clientSocket.getInputStream());
                    if (request == null) {
                        break; // 연결 종료
                    }

                    requestCount++;
                    long requestStartTime = System.currentTimeMillis();

                    // ⭐ 통합 요청 처리 (Router + ServletContainer)
                    HttpResponse response = processRequest(request);

                    // Keep-Alive 확인
                    keepAlive = shouldKeepAlive(request, response) &&
                            requestCount < config.getMaxRequestsPerConnection();

                    // 응답 전송
                    sendResponse(response, clientSocket.getOutputStream());

                    long requestTime = System.currentTimeMillis() - requestStartTime;
                    if (config.isDebugMode()) {
                        System.out.println("[" + threadName + "] Request " + requestCount +
                                " processed in " + requestTime + "ms - " +
                                request.getMethod() + " " + request.getPath());
                    }

                    // Keep-Alive가 아니면 연결 종료
                    if (!keepAlive) {
                        break;
                    }

                } catch (SocketTimeoutException e) {
                    if (config.isDebugMode()) {
                        System.out.println("[" + threadName + "] Socket timeout - closing connection");
                    }
                    break;
                } catch (IOException e) {
                    if (config.isDebugMode()) {
                        System.out.println("[" + threadName + "] I/O error: " + e.getMessage());
                    }
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("[" + threadName + "] Error handling connection: " + e.getMessage());

            // 500 에러 응답 시도
            try {
                HttpResponse errorResponse = HttpResponse.internalServerError();
                sendResponse(errorResponse, clientSocket.getOutputStream());
            } catch (IOException ignored) {
                // 응답 전송 실패는 무시
            }

        } finally {
            closeConnection();

            long totalTime = System.currentTimeMillis() - startTime;
            if (config.isDebugMode()) {
                System.out.println("[" + threadName + "] Connection closed - " +
                        "total time: " + totalTime + "ms, requests: " + requestCount);
            }
        }
    }

    /**
     * HTTP 요청 파싱
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
            if (config.isDebugMode()) {
                System.out.println("Request parsing failed: " + e.getMessage());
            }
            throw e;
        }
    }

    /**
     * 통합 요청 처리 (수정된 버전)
     * ServletContainer 우선, 없으면 Router 사용
     */
    private HttpResponse processRequest(HttpRequest request) {
        try {
            // 1. ServletContainer 먼저 시도 (registerHandler로 등록된 핸들러들)
            if (servletContainer != null) {
                try {
                    // CompletableFuture<HttpResponse>를 HttpResponse로 변환
                    CompletableFuture<HttpResponse> servletFuture = servletContainer.handleRequest(request);
                    if (servletFuture != null) {
                        HttpResponse servletResponse = servletFuture.get(); // 동기 변환
                        if (servletResponse != null) {
                            if (config.isDebugMode()) {
                                System.out.println("Request handled by ServletContainer: " + request.getPath());
                            }
                            return servletResponse;
                        }
                    }
                } catch (Exception e) {
                    if (config.isDebugMode()) {
                        System.out.println("ServletContainer failed, fallback to Router: " + e.getMessage());
                    }
                    // ServletContainer 실패시 Router로 fallback
                }
            }

            // ⭐ 2. Router로 처리 (기존 방식)
            HttpResponse routerResponse = router.routeWithMiddlewares(request).get();
            if (config.isDebugMode()) {
                System.out.println("Request handled by Router: " + request.getPath());
            }
            return routerResponse;

        } catch (Exception e) {
            System.err.println("Request processing error: " + e.getMessage());
            return HttpResponse.internalServerError("Request processing failed");
        }
    }

    /**
     * Keep-Alive 여부 결정
     */
    private boolean shouldKeepAlive(HttpRequest request, HttpResponse response) {
        // HTTP/1.1은 기본적으로 Keep-Alive
        boolean requestKeepAlive = !"close".equalsIgnoreCase(request.getHeader("Connection"));
        boolean responseKeepAlive = !"close".equalsIgnoreCase(response.getHeaders().get("Connection"));

        return requestKeepAlive && responseKeepAlive &&
                "HTTP/1.1".equals(request.getVersion());
    }

    /**
     * HTTP 응답 전송
     */
    private void sendResponse(HttpResponse response, OutputStream outputStream) throws IOException {
        try {
            response.writeTo(outputStream);
            outputStream.flush();

        } catch (IOException e) {
            System.err.println("Response sending failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 연결 종료
     */
    private void closeConnection() {
        try {
            if (!clientSocket.isClosed()) {
                // Graceful shutdown
                clientSocket.shutdownOutput();
                clientSocket.close();
            }
        } catch (IOException e) {
            if (config.isDebugMode()) {
                System.out.println("Error closing connection: " + e.getMessage());
            }
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