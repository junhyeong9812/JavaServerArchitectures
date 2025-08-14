package com.serverarch.traditional.routing;

import com.serverarch.traditional.*;

/**
 * 미들웨어 인터페이스
 * 요청 전처리, 후처리, 인증, 로깅 등에 사용
 */
@FunctionalInterface // 함수형 인터페이스
public interface Middleware {
    /**
     * 미들웨어 처리 로직
     * @param request HTTP 요청
     * @param chain 다음 미들웨어나 핸들러로 넘어가는 체인
     * @return HTTP 응답
     * @throws Exception 처리 중 예외
     */
    HttpResponse process(HttpRequest request, MiddlewareChain chain) throws Exception; // 체인 패턴 적용
}