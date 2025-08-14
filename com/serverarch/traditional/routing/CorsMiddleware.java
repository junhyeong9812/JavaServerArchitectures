package com.serverarch.traditional.routing;

import com.serverarch.traditional.*;

/**
 * CORS 미들웨어
 * Cross-Origin Resource Sharing 헤더 자동 설정
 */
public class CorsMiddleware implements Middleware {
    private final String allowOrigin; // 허용할 오리진
    private final String allowMethods; // 허용할 HTTP 메서드들
    private final String allowHeaders; // 허용할 헤더들

    public CorsMiddleware(String allowOrigin) {
        this(allowOrigin, "GET, POST, PUT, DELETE, OPTIONS", "Content-Type, Authorization");
    }

    public CorsMiddleware(String allowOrigin, String allowMethods, String allowHeaders) {
        this.allowOrigin = allowOrigin != null ? allowOrigin : "*"; // 기본값 "*"
        this.allowMethods = allowMethods;
        this.allowHeaders = allowHeaders;
    }

    @Override
    public HttpResponse process(HttpRequest request, MiddlewareChain chain) throws Exception {
        // OPTIONS 요청 (프리플라이트) 처리
        if ("OPTIONS".equals(request.getMethod())) {
            HttpResponse response = HttpResponse.ok(); // 200 OK 응답 생성
            setCorsHeaders(response); // CORS 헤더 설정
            return response; // OPTIONS 요청은 여기서 종료
        }

        // 일반 요청 처리
        HttpResponse response = chain.processNext(request); // 다음 체인 실행
        setCorsHeaders(response); // 응답에 CORS 헤더 추가

        return response;
    }

    private void setCorsHeaders(HttpResponse response) {
        response.setHeader("Access-Control-Allow-Origin", allowOrigin);
        response.setHeader("Access-Control-Allow-Methods", allowMethods);
        response.setHeader("Access-Control-Allow-Headers", allowHeaders);
        response.setHeader("Access-Control-Max-Age", "3600"); // 1시간 캐시
    }
}