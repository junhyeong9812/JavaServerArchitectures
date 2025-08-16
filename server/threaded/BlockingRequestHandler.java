package server.threaded;

import server.core.http.*;
import server.core.routing.Router;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * 블로킹 I/O 요청 핸들러
 * 스레드당 하나의 연결을 처리하는 전통적인 방식
 */
public class BlockingRequestHandler implements Runnable {

    private final Socket clientSocket;
    private final Router router;
    private final RequestHandlerConfig config;
    private final long startTime;

    public BlockingRequestHandler(Socket clientSocket, Router router, RequestHandlerConfig config) {
        this.clientSocket = clientSocket;
        this.router = router;
        this.config = config;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        String clientAddress = clientSocket.getRemoteSocketAddress().toString();

        try {
            // 소켓 타임아웃 설정
            clientSocket.setSoTimeout(config.getSocketTimeout());

            if (config.isDebugMode()) {
                System.out.println("[" + threadName + "] Handling connection from: " + clientAddress);
            }

            // Keep-Alive 연결 처리
            boolean keepAlive = true;
            int requestCount = 0;

            while (keepAlive && requestCount < config.getMaxRequestsPerConnection()) {
                try {
                    // HTTP 요청 파싱
                    HttpRequest request = parseRequest(clientSocket.getInputStream());
                    if (request == null) {
                        break; // 연결 종료
                    }

                    requestCount++;
                    long requestStartTime = System.currentTimeMillis();

                    // 라우터를 통한 요청 처리
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
     * 요청 처리
     */
    private HttpResponse processRequest(HttpRequest request) {
        try {
            // 라우터를 통한 비동기 처리를 동기로 변환
            return router.routeWithMiddlewares(request).get();

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
        return String.format("BlockingHandler{client=%s, thread=%s, uptime=%dms}",
                clientSocket.getRemoteSocketAddress(),
                Thread.currentThread().getName(),
                System.currentTimeMillis() - startTime);
    }
}
