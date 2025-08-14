package com.serverarch.traditional.routing; // 패키지 선언 - 전통적인 서버의 라우팅 시스템 패키지

// import 선언부 - 필요한 클래스들을 현재 클래스에서 사용할 수 있도록 경로 지정
import com.serverarch.traditional.*; // 전통적인 서버 클래스들 (HttpRequest, HttpResponse) import
import java.util.*; // Collections, Map, List 등 유틸리티 클래스들 import
import java.util.regex.*; // Pattern, Matcher 등 정규식 관련 클래스들 import
import java.util.concurrent.*; // ConcurrentHashMap 등 동시성 컬렉션 클래스들 import
import java.util.function.*; // Function, Predicate 등 함수형 인터페이스들 import
import java.util.stream.*; // Stream API 관련 클래스들 import
import java.util.logging.*; // Logger 등 로깅 관련 클래스들 import

/**
 * 고급 HTTP 라우팅 시스템
 *
 * Phase 2.2에서 도입되는 정교한 라우팅 기능:
 * 1. RESTful API 패턴 매칭 (/users/{id}, /api/v1/books/{isbn})
 * 2. 와일드카드 라우팅 (/static/*, /files/**)
 * 3. HTTP 메서드별 라우트 구분 (GET, POST, PUT, DELETE)
 * 4. 라우트 우선순위 시스템 (구체적인 패턴이 우선)
 * 5. 조건부 라우팅 (헤더, 쿼리 파라미터 기반)
 * 6. 라우트 그룹화 및 네임스페이스 지원
 */
public class Router { // public 클래스 선언 - 라우팅 시스템의 핵심 클래스

    // 로거 - 라우팅 동작 추적용
    private static final Logger logger = Logger.getLogger(Router.class.getName()); // static final - 클래스 레벨 상수, 라우터 전용 로거 생성

    // 라우트 저장소 - HTTP 메서드별로 구분하여 저장
    // ConcurrentHashMap 사용 이유: 멀티스레드 환경에서 안전한 동시 접근 보장
    private final Map<String, List<Route>> routes = new ConcurrentHashMap<>(); // final Map - 참조 불변, 메서드별 라우트 리스트 저장

    // 미들웨어 체인 - 모든 요청에 공통 적용되는 전처리/후처리 로직
    private final List<Middleware> globalMiddlewares = new CopyOnWriteArrayList<>(); // CopyOnWriteArrayList - 읽기가 많고 쓰기가 적은 경우 최적화된 동시성 리스트

    // 라우트 매칭 통계 - 성능 모니터링용
    private final Map<String, Long> routeStats = new ConcurrentHashMap<>(); // 라우트별 호출 횟수 추적

    // 라우트 캐시 - 자주 사용되는 패턴의 매칭 결과를 캐시하여 성능 향상
    private final Map<String, RouteMatchResult> routeCache = new ConcurrentHashMap<>(); // 캐시 키: "METHOD:PATH", 캐시 값: 매칭 결과

    // 캐시 설정
    private static final int MAX_CACHE_SIZE = 1000; // 최대 캐시 항목 수 - 메모리 사용량 제한
    private static final long CACHE_EXPIRE_TIME = 5 * 60 * 1000; // 캐시 만료 시간 5분 - 밀리초 단위

    /**
     * GET 메서드 라우트 등록
     * @param pattern URL 패턴 (예: "/users/{id}", "/static/*")
     * @param handler 요청 처리 핸들러
     * @return 등록된 Route 객체 (메서드 체이닝용)
     */
    public Route get(String pattern, RouteHandler handler) { // public 메서드 - GET 라우트 등록
        // addRoute 메서드에 위임 - 코드 중복 방지
        return addRoute("GET", pattern, handler); // "GET" 문자열 상수로 HTTP 메서드 지정
    }

    /**
     * POST 메서드 라우트 등록
     * @param pattern URL 패턴
     * @param handler 요청 처리 핸들러
     * @return 등록된 Route 객체
     */
    public Route post(String pattern, RouteHandler handler) { // POST 라우트 등록 메서드
        return addRoute("POST", pattern, handler); // POST 메서드로 라우트 등록
    }

    /**
     * PUT 메서드 라우트 등록
     * @param pattern URL 패턴
     * @param handler 요청 처리 핸들러
     * @return 등록된 Route 객체
     */
    public Route put(String pattern, RouteHandler handler) { // PUT 라우트 등록 메서드
        return addRoute("PUT", pattern, handler); // PUT 메서드로 라우트 등록
    }

    /**
     * DELETE 메서드 라우트 등록
     * @param pattern URL 패턴
     * @param handler 요청 처리 핸들러
     * @return 등록된 Route 객체
     */
    public Route delete(String pattern, RouteHandler handler) { // DELETE 라우트 등록 메서드
        return addRoute("DELETE", pattern, handler); // DELETE 메서드로 라우트 등록
    }

    /**
     * 모든 HTTP 메서드에 대한 라우트 등록
     * @param pattern URL 패턴
     * @param handler 요청 처리 핸들러
     * @return 등록된 Route 객체들의 리스트
     */
    public List<Route> all(String pattern, RouteHandler handler) { // 모든 메서드 라우트 등록
        // 주요 HTTP 메서드들에 대해 동일한 핸들러 등록
        List<Route> allRoutes = new ArrayList<>(); // 등록된 라우트들을 저장할 리스트

        // Stream API를 사용한 함수형 스타일 처리
        Stream.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS") // Stream.of() - 가변 인수를 받아 스트림 생성
                .forEach(method -> allRoutes.add(addRoute(method, pattern, handler))); // forEach() - 각 메서드에 대해 라우트 등록, 람다 표현식 사용

        return allRoutes; // 등록된 모든 라우트 반환
    }

    /**
     * 라우트 그룹 생성 및 등록
     * 공통 접두사를 가진 여러 라우트를 한 번에 등록
     * @param prefix 공통 URL 접두사 (예: "/api/v1")
     * @param groupConfigurer 그룹 설정 함수
     */
    public void group(String prefix, Consumer<RouteGroup> groupConfigurer) { // 라우트 그룹화 메서드
        // Consumer<T> 함수형 인터페이스 - 입력을 받아 처리하지만 반환값이 없는 함수
        RouteGroup group = new RouteGroup(this, prefix); // RouteGroup 생성 - 현재 라우터와 접두사를 전달
        groupConfigurer.accept(group); // Consumer.accept() - 그룹 설정 함수 실행

        logger.info(String.format("라우트 그룹 등록 완료: %s", prefix)); // 그룹 등록 완료 로그
    }

    /**
     * 글로벌 미들웨어 등록
     * 모든 요청에 대해 실행되는 공통 처리 로직
     * @param middleware 미들웨어 인터페이스 구현체
     */
    public void use(Middleware middleware) { // 글로벌 미들웨어 등록 메서드
        if (middleware != null) { // null 체크 - 유효한 미들웨어만 등록
            globalMiddlewares.add(middleware); // List.add() - 미들웨어를 리스트에 추가
            logger.info("글로벌 미들웨어 등록: " + middleware.getClass().getSimpleName()); // 미들웨어 등록 로그
        }
    }

    /**
     * 요청을 적절한 라우트로 매칭하여 처리
     * 핵심 라우팅 로직 - 캐시, 우선순위, 미들웨어 모두 고려
     * @param request HTTP 요청 객체
     * @return HTTP 응답 객체
     */
    public HttpResponse route(HttpRequest request) { // 핵심 라우팅 메서드
        String method = request.getMethod(); // 요청의 HTTP 메서드 추출
        String path = request.getPath(); // 요청의 URL 경로 추출

        // 캐시 키 생성 - "메서드:경로" 형식
        String cacheKey = method + ":" + path; // 문자열 연결로 캐시 키 생성

        // 캐시에서 먼저 확인 - 성능 최적화
        RouteMatchResult cachedResult = routeCache.get(cacheKey); // Map.get() - 캐시에서 결과 조회
        if (cachedResult != null && !cachedResult.isExpired()) { // null 체크와 만료 확인
            return executeRoute(cachedResult, request); // 캐시된 결과로 라우트 실행
        }

        // 해당 HTTP 메서드의 라우트들 조회
        List<Route> methodRoutes = routes.get(method); // Map.get() - 메서드별 라우트 리스트 조회
        if (methodRoutes == null || methodRoutes.isEmpty()) { // null 체크와 빈 리스트 확인
            return handleNotFound(request); // 라우트가 없으면 404 응답
        }

        // 라우트 매칭 시도 - 우선순위 순으로 확인
        for (Route route : methodRoutes) { // for-each 루프 - 모든 라우트를 순차 확인
            RouteMatchResult matchResult = route.match(path, request); // Route.match() - 패턴 매칭 시도
            if (matchResult != null) { // 매칭 성공 시
                // 캐시에 결과 저장 - 다음 동일 요청의 성능 향상
                if (routeCache.size() < MAX_CACHE_SIZE) { // 캐시 크기 제한 확인
                    routeCache.put(cacheKey, matchResult); // 캐시에 매칭 결과 저장
                }

                // 라우트 사용 통계 업데이트
                updateRouteStats(route.getPattern()); // 통계 업데이트 메서드 호출

                return executeRoute(matchResult, request); // 매칭된 라우트 실행
            }
        }

        // 매칭되는 라우트가 없으면 404 응답
        return handleNotFound(request); // 404 Not Found 응답 생성
    }

    /**
     * 내부 라우트 등록 메서드
     * 모든 public 라우트 등록 메서드가 이 메서드를 통해 처리됨
     * @param method HTTP 메서드
     * @param pattern URL 패턴
     * @param handler 요청 처리 핸들러
     * @return 등록된 Route 객체
     */
    private Route addRoute(String method, String pattern, RouteHandler handler) { // private 메서드 - 내부 라우트 등록 로직
        // 입력 검증 - 필수 매개변수들의 유효성 확인
        if (method == null || pattern == null || handler == null) { // null 체크
            throw new IllegalArgumentException("메서드, 패턴, 핸들러는 모두 필수입니다"); // IllegalArgumentException - 잘못된 인수 예외
        }

        // Route 객체 생성 - 패턴 컴파일과 우선순위 계산 포함
        Route route = new Route(method, pattern, handler); // Route 생성자 호출

        // 해당 메서드의 라우트 리스트 조회 또는 생성
        List<Route> methodRoutes = routes.computeIfAbsent(method, k -> new ArrayList<>()); // computeIfAbsent() - 키가 없으면 새 값 생성

        // 라우트를 우선순위 순으로 삽입 - 구체적인 패턴이 앞에 오도록
        insertRouteByPriority(methodRoutes, route); // 우선순위 기반 삽입 메서드 호출

        logger.info(String.format("라우트 등록: %s %s", method, pattern)); // 라우트 등록 로그
        return route; // 등록된 라우트 반환
    }

    /**
     * 라우트를 우선순위에 따라 정렬된 위치에 삽입
     * 우선순위가 높은 (구체적인) 라우트가 앞에 오도록 배치
     * @param routes 라우트 리스트
     * @param newRoute 새로 추가할 라우트
     */
    private void insertRouteByPriority(List<Route> routes, Route newRoute) { // 우선순위 기반 삽입 메서드
        int insertIndex = 0; // 삽입 위치 인덱스

        // 기존 라우트들과 우선순위 비교하여 적절한 위치 찾기
        for (int i = 0; i < routes.size(); i++) { // for 루프 - 인덱스 기반 순회
            if (newRoute.getPriority() > routes.get(i).getPriority()) { // 우선순위 비교
                insertIndex = i; // 삽입 위치 설정
                break; // 적절한 위치를 찾으면 루프 중단
            }
            insertIndex = i + 1; // 끝까지 가면 마지막에 삽입
        }

        routes.add(insertIndex, newRoute); // List.add(index, element) - 지정된 위치에 삽입
    }

    /**
     * 매칭된 라우트를 실제로 실행
     * 미들웨어 체인을 거쳐 최종 핸들러까지 실행
     * @param matchResult 라우트 매칭 결과
     * @param request HTTP 요청
     * @return HTTP 응답
     */
    private HttpResponse executeRoute(RouteMatchResult matchResult, HttpRequest request) { // 라우트 실행 메서드
        try { // try-catch 블록 - 예외 처리
            // 경로 파라미터를 요청 객체에 설정
            for (Map.Entry<String, String> param : matchResult.getPathParameters().entrySet()) { // for-each 루프 - 모든 경로 파라미터 순회
                request.setPathParameter(param.getKey(), param.getValue()); // 경로 파라미터 설정
            }

            // 미들웨어 체인 생성 및 실행
            MiddlewareChain chain = new MiddlewareChain(globalMiddlewares, matchResult.getRoute().getMiddlewares(), matchResult.getRoute().getHandler()); // 미들웨어 체인 생성
            return chain.execute(request); // 체인 실행으로 최종 응답 생성

        } catch (Exception e) { // Exception - 모든 예외 처리
            logger.log(Level.SEVERE, "라우트 실행 중 오류 발생", e); // 심각한 오류 레벨로 로그
            return HttpResponse.serverError("요청 처리 중 오류가 발생했습니다"); // 500 에러 응답
        }
    }

    /**
     * 404 Not Found 응답 처리
     * 매칭되는 라우트가 없을 때 호출
     * @param request HTTP 요청
     * @return 404 응답
     */
    private HttpResponse handleNotFound(HttpRequest request) { // 404 처리 메서드
        logger.warning(String.format("라우트를 찾을 수 없음: %s %s", request.getMethod(), request.getPath())); // 경고 로그
        return HttpResponse.notFound(String.format("요청한 경로를 찾을 수 없습니다: %s %s", request.getMethod(), request.getPath())); // 404 응답 생성
    }

    /**
     * 라우트 사용 통계 업데이트
     * 각 라우트별 호출 횟수를 추적하여 성능 분석에 활용
     * @param pattern 라우트 패턴
     */
    private void updateRouteStats(String pattern) { // 통계 업데이트 메서드
        // merge() 메서드를 사용한 원자적 카운터 증가
        routeStats.merge(pattern, 1L, Long::sum); // merge() - 키가 있으면 함수 적용, 없으면 기본값 설정, Long::sum - 메서드 참조로 덧셈 함수
    }

    /**
     * 라우트 통계 정보 반환
     * 모니터링 대시보드나 관리 도구에서 사용
     * @return 라우트별 호출 횟수 맵
     */
    public Map<String, Long> getRouteStats() { // 통계 조회 메서드
        return new HashMap<>(routeStats); // 방어적 복사로 반환 - 원본 보호
    }

    /**
     * 등록된 모든 라우트 정보 반환
     * 디버깅이나 관리 목적으로 사용
     * @return 메서드별 라우트 리스트 맵
     */
    public Map<String, List<Route>> getAllRoutes() { // 모든 라우트 조회 메서드
        Map<String, List<Route>> result = new HashMap<>(); // 결과 맵 생성

        // 각 메서드별 라우트 리스트를 복사하여 반환
        routes.forEach((method, routeList) -> // forEach() - 람다로 각 엔트리 처리
                result.put(method, new ArrayList<>(routeList)) // 방어적 복사
        );

        return result; // 복사된 라우트 정보 반환
    }

    /**
     * 캐시 정리
     * 메모리 관리나 설정 변경 시 사용
     */
    public void clearCache() { // 캐시 정리 메서드
        routeCache.clear(); // Map.clear() - 모든 캐시 항목 제거
        logger.info("라우트 캐시가 정리되었습니다"); // 캐시 정리 로그
    }

    /**
     * 특정 패턴의 라우트 제거
     * 동적 라우트 관리에 사용
     * @param method HTTP 메서드
     * @param pattern URL 패턴
     * @return 제거 성공 여부
     */
    public boolean removeRoute(String method, String pattern) { // 라우트 제거 메서드
        List<Route> methodRoutes = routes.get(method); // 해당 메서드의 라우트 리스트 조회
        if (methodRoutes == null) return false; // 메서드 자체가 없으면 false

        // removeIf()를 사용한 조건부 제거
        boolean removed = methodRoutes.removeIf(route -> route.getPattern().equals(pattern)); // removeIf() - 조건에 맞는 요소 제거, 람다로 조건 지정

        if (removed) { // 제거 성공 시
            clearCache(); // 캐시 무효화 - 제거된 라우트의 캐시 정리
            logger.info(String.format("라우트 제거됨: %s %s", method, pattern)); // 제거 로그
        }

        return removed; // 제거 결과 반환
    }
}