package com.serverarch.traditional.routing;

import java.util.function.Consumer;

/**
 * 라우트 그룹 클래스
 * 공통 접두사를 가진 라우트들을 그룹으로 관리
 */
public class RouteGroup { // public 클래스 - 외부에서 독립적으로 사용 가능
    private final Router router; // 상위 라우터 참조
    private final String prefix; // 그룹 접두사

    RouteGroup(Router router, String prefix) { // 패키지 전용 생성자
        this.router = router; // 라우터 참조 저장
        this.prefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix; // 접두사 정규화 - 마지막 슬래시 제거
    }

    // 그룹 내 라우트 등록 메서드들
    public Route get(String pattern, RouteHandler handler) { // 그룹 내 GET 라우트
        return router.get(prefix + pattern, handler); // 접두사와 패턴 결합
    }

    public Route post(String pattern, RouteHandler handler) { // 그룹 내 POST 라우트
        return router.post(prefix + pattern, handler); // 접두사 자동 추가
    }

    public Route put(String pattern, RouteHandler handler) { // 그룹 내 PUT 라우트
        return router.put(prefix + pattern, handler); // 접두사와 패턴 결합
    }

    public Route delete(String pattern, RouteHandler handler) { // 그룹 내 DELETE 라우트
        return router.delete(prefix + pattern, handler); // 접두사 자동 추가
    }

    // 중첩 그룹 지원
    public void group(String subPrefix, Consumer<RouteGroup> groupConfigurer) { // 중첩 그룹 생성
        router.group(prefix + subPrefix, groupConfigurer); // 상위 라우터에 중첩 그룹 등록
    }
}