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

    /**
     * 기본 생성자 - 모든 오리진 허용하는 기본 CORS 설정
     *
     * 기본 생성자를 제공하는 이유:
     * - 간편한 사용성 제공 - 복잡한 설정 없이 바로 사용 가능
     * - 개발 환경에서 빠른 테스트 가능 - 모든 오리진 허용으로 CORS 문제 해결
     * - 일반적인 사용 패턴 지원 - 대부분의 경우 기본 설정으로 충분
     */
    public CorsMiddleware() {
        // this() 사용 이유: 기존 생성자 로직 재사용으로 코드 중복 방지
        // "*" 사용 이유: 모든 오리진 허용으로 개발 시 편의성 제공 (운영에서는 특정 도메인 권장)
        // 기본 HTTP 메서드들: 일반적인 RESTful API에서 사용하는 표준 메서드들
        // 기본 헤더들: 일반적인 웹 애플리케이션에서 필요한 헤더들
        this("*", "GET, POST, PUT, DELETE, OPTIONS", "Content-Type, Authorization");
    }

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