package com.serverarch.traditional.routing;

import com.serverarch.common.http.HttpStatus;
import com.serverarch.traditional.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 레이트 리미팅 미들웨어
 * 요청 빈도 제한 (간단한 토큰 버킷 알고리즘)
 */
public class RateLimitMiddleware implements Middleware {
    private final Map<String, TokenBucket> clientBuckets; // 클라이언트별 토큰 버킷
    private final int maxRequests; // 최대 요청 수
    private final long timeWindowMs; // 시간 윈도우 (밀리초)

    public RateLimitMiddleware(int maxRequests, long timeWindowMs) {
        this.clientBuckets = new ConcurrentHashMap<>();
        this.maxRequests = maxRequests;
        this.timeWindowMs = timeWindowMs;
    }

    @Override
    public HttpResponse process(HttpRequest request, MiddlewareChain chain) throws Exception {
        String clientId = getClientId(request); // 클라이언트 식별
        TokenBucket bucket = clientBuckets.computeIfAbsent(clientId,
                k -> new TokenBucket(maxRequests, timeWindowMs)); // 버킷 생성 또는 조회

        if (!bucket.tryConsume()) { // 토큰 소비 시도
            // 레이트 리미트 초과
            HttpResponse response = new HttpResponse(HttpStatus.TOO_MANY_REQUESTS); // 429 상태
            response.setHeader("Retry-After", String.valueOf(timeWindowMs / 1000)); // 재시도 시간
            response.setBody("요청 빈도가 너무 높습니다. 잠시 후 다시 시도하세요.");
            return response;
        }

        return chain.processNext(request); // 다음 체인 실행
    }

    private String getClientId(HttpRequest request) {
        // 실제로는 클라이언트 IP나 사용자 ID를 사용해야 함
        // 여기서는 간단히 IP 주소로 대체 (실제 구현에서는 더 정교해야 함)
        return request.getHeader("X-Forwarded-For") != null ?
                request.getHeader("X-Forwarded-For") : "default";
    }

    /**
     * 간단한 토큰 버킷 구현
     */
    private static class TokenBucket {
        private final int capacity; // 버킷 용량
        private final long refillIntervalMs; // 토큰 재충전 간격
        private int tokens; // 현재 토큰 수
        private long lastRefillTime; // 마지막 재충전 시간

        public TokenBucket(int capacity, long refillIntervalMs) {
            this.capacity = capacity;
            this.refillIntervalMs = refillIntervalMs;
            this.tokens = capacity; // 초기에는 모든 토큰 보유
            this.lastRefillTime = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            refillTokens(); // 토큰 재충전

            if (tokens > 0) {
                tokens--; // 토큰 소비
                return true; // 성공
            }
            return false; // 토큰 부족
        }

        private void refillTokens() {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastRefillTime >= refillIntervalMs) {
                tokens = capacity; // 토큰 전체 재충전
                lastRefillTime = currentTime;
            }
        }
    }
}