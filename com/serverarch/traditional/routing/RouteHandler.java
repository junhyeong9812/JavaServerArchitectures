package com.serverarch.traditional.routing;

import com.serverarch.traditional.*;

/**
 * 라우트 핸들러 함수형 인터페이스
 * 실제 요청 처리 로직을 구현하는 함수
 */
@FunctionalInterface // 함수형 인터페이스 어노테이션 - 람다 표현식 사용 가능
public interface RouteHandler { // 인터페이스 선언
    /**
     * 요청을 처리하여 응답 생성
     * @param request HTTP 요청
     * @return HTTP 응답
     * @throws Exception 처리 중 발생할 수 있는 모든 예외
     */
    HttpResponse handle(HttpRequest request) throws Exception; // 추상 메서드 - 구현체에서 정의
}