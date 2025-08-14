package com.serverarch.traditional.routing;

import com.serverarch.common.http.HttpStatus;
import com.serverarch.traditional.*;
import java.util.*;

/**
 * 인증 미들웨어
 * 간단한 토큰 기반 인증
 */
public class AuthMiddleware implements Middleware {
    private final Set<String> validTokens; // 유효한 토큰들
    private final Set<String> publicPaths; // 인증이 필요 없는 공개 경로들

    public AuthMiddleware() {
        this.validTokens = new HashSet<>();
        this.publicPaths = new HashSet<>();

        // 기본 공개 경로 설정
        publicPaths.add("/");
        publicPaths.add("/login");
        publicPaths.add("/register");
        publicPaths.add("/health");
    }

    /**
     * 유효한 토큰 추가
     * @param token 토큰 문자열
     */
    public void addValidToken(String token) {
        if (token != null && !token.trim().isEmpty()) {
            validTokens.add(token);
        }
    }

    /**
     * 공개 경로 추가 (인증 불필요)
     * @param path 공개 경로
     */
    public void addPublicPath(String path) {
        if (path != null) {
            publicPaths.add(path);
        }
    }

    @Override
    public HttpResponse process(HttpRequest request, MiddlewareChain chain) throws Exception {
        String path = request.getPath();

        // 공개 경로는 인증 스킵
        if (isPublicPath(path)) {
            return chain.processNext(request); // 다음 체인으로 바로 진행
        }

        // Authorization 헤더에서 토큰 추출
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return createUnauthorizedResponse("인증 토큰이 필요합니다");
        }

        String token = authHeader.substring(7); // "Bearer " 제거
        if (!validTokens.contains(token)) {
            return createUnauthorizedResponse("유효하지 않은 토큰입니다");
        }

        // 인증 성공 - 사용자 정보를 요청에 설정
        request.setAttribute("authenticated", true);
        request.setAttribute("token", token);

        return chain.processNext(request); // 다음 체인 실행
    }

    private boolean isPublicPath(String path) {
        // 정확한 매칭 또는 와일드카드 매칭 확인
        return publicPaths.contains(path) ||
                publicPaths.stream().anyMatch(publicPath ->
                        publicPath.endsWith("*") && path.startsWith(publicPath.substring(0, publicPath.length() - 1))
                );
    }

    private HttpResponse createUnauthorizedResponse(String message) {
        HttpResponse response = new HttpResponse(HttpStatus.UNAUTHORIZED); // 401 상태
        response.setHeader("WWW-Authenticate", "Bearer realm=\"API\""); // 인증 방식 명시
        response.setBody(message);
        return response;
    }
}