package com.serverarch.traditional.routing;

import java.util.*;

/**
 * 라우트 매칭 결과를 담는 클래스
 * 매칭된 라우트와 추출된 경로 파라미터 정보 포함
 */
class RouteMatchResult { // 패키지 전용 클래스
    private final Route route; // 매칭된 라우트
    private final Map<String, String> pathParameters; // 추출된 경로 파라미터
    private final long creationTime; // 결과 생성 시간 (캐시 만료용)

    /**
     * 매칭 결과 생성자
     * @param route 매칭된 라우트
     * @param pathParameters 경로 파라미터 맵
     */
    public RouteMatchResult(Route route, Map<String, String> pathParameters) {
        this.route = route; // 라우트 설정
        this.pathParameters = new HashMap<>(pathParameters); // 방어적 복사로 파라미터 저장
        this.creationTime = System.currentTimeMillis(); // 현재 시간을 생성 시간으로 기록
    }

    // Getter 메서드들
    public Route getRoute() { return route; }
    public Map<String, String> getPathParameters() { return new HashMap<>(pathParameters); } // 방어적 복사 반환
    public long getCreationTime() { return creationTime; }

    /**
     * 캐시 만료 여부 확인
     * @return 만료되었으면 true
     */
    public boolean isExpired() {
        long currentTime = System.currentTimeMillis(); // 현재 시간
        return (currentTime - creationTime) > 300000; // 5분(300000ms) 후 만료
    }
}