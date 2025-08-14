package com.serverarch.traditional.routing;

import com.serverarch.traditional.*;
import java.util.logging.*;

/**
 * 로깅 미들웨어
 * 모든 요청과 응답을 로깅
 */
public class LoggingMiddleware implements Middleware {
    private static final Logger logger = Logger.getLogger(LoggingMiddleware.class.getName());

    @Override
    public HttpResponse process(HttpRequest request, MiddlewareChain chain) throws Exception {
        long startTime = System.currentTimeMillis(); // 요청 시작 시간

        // 요청 로깅
        logger.info(String.format("요청 시작: %s %s", request.getMethod(), request.getPath()));

        // 다음 미들웨어/핸들러 실행
        HttpResponse response = chain.processNext(request);

        // 응답 로깅
        long processingTime = System.currentTimeMillis() - startTime;
        logger.info(String.format("요청 완료: %s %s -> %s (%dms)",
                request.getMethod(), request.getPath(), response.getStatus(), processingTime));

        return response; // 응답 반환
    }
}