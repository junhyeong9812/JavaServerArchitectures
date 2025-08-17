package server.hybrid;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.http.*;
import server.core.routing.*;
import server.core.mini.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * 하이브리드 미니 서블릿 컨테이너
 *
 * 기능:
 * 1. 하이브리드 방식의 서블릿 생명주기 관리
 * 2. 비동기 서블릿 처리 지원
 * 3. 라우팅 시스템과 서블릿 통합
 * 4. 컨텍스트 스위칭 최적화
 * 5. 서블릿 풀링 및 재사용
 */
public class HybridMiniServletContainer {

    // SLF4J 로거 인스턴스 생성 - 서블릿 컨테이너 관련 로깅용
    private static final Logger logger = LoggerFactory.getLogger(HybridMiniServletContainer.class);
    // static final로 클래스당 하나의 로거 인스턴스 공유하여 메모리 효율성 확보

    // === 핵심 컴포넌트 ===
    private final Router router;                        // HTTP 라우팅 시스템
    // Router 사용 이유:
    // 1. URL 패턴 기반 요청 라우팅 처리
    // 2. RESTful API 지원을 위한 경로 매칭
    // 3. 서블릿과 라우팅 시스템의 통합 인터페이스 제공

    private final HybridProcessor processor;            // 하이브리드 요청 처리기
    // HybridProcessor 사용 이유:
    // 1. 동기/비동기 처리 방식의 통합 관리
    // 2. 컨텍스트 스위칭과 스레드 최적화
    // 3. I/O 집약적 작업의 효율적 처리

    // === 서블릿 관리 ===
    private final ConcurrentMap<String, ServletInfo> servlets;     // 등록된 서블릿들
    // ConcurrentHashMap 사용 이유:
    // 1. 서블릿 등록/해제시 멀티스레드 안전성 보장
    // 2. 서블릿 조회 작업의 락 없는 성능 제공
    // 3. Key는 서블릿 이름, Value는 서블릿 메타데이터

    private final ConcurrentMap<String, String> pathToServlet;     // 경로 -> 서블릿 매핑
    // 경로 기반 서블릿 라우팅을 위한 빠른 매핑 테이블
    // Key는 URL 패턴, Value는 서블릿 이름

    private final AtomicLong servletRequestCounter = new AtomicLong(0);
    // AtomicLong으로 서블릿 요청 수를 스레드 안전하게 추적

    // === 컨텍스트 관리 ===
    private final MiniContext globalContext;           // 글로벌 컨텍스트
    // 모든 서블릿이 공유하는 전역 설정과 리소스 관리

    private final ConcurrentMap<String, MiniContext> contexts;     // 컨텍스트별 설정
    // 컨텍스트 경로별로 독립적인 설정 관리 가능

    // === 서블릿 풀링 (성능 최적화) ===
    private final ConcurrentMap<String, Queue<MiniServlet>> servletPools;
    // 서블릿 인스턴스 풀링으로 객체 생성 비용 절약
    // Key는 서블릿 이름, Value는 재사용 가능한 서블릿 인스턴스 큐

    private final int maxServletPoolSize = 10;         // 풀 최대 크기
    // 메모리 사용량과 성능의 균형을 위한 풀 크기 제한

    // === 통계 및 모니터링 ===
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong asyncRequests = new AtomicLong(0);
    private final AtomicLong completedRequests = new AtomicLong(0);
    private final AtomicLong errorRequests = new AtomicLong(0);
    // AtomicLong 사용으로 멀티스레드 환경에서 정확한 통계 수집
    // 각각 총 요청, 비동기 요청, 완료된 요청, 오류 요청 수를 추적

    /**
     * HybridMiniServletContainer 생성자
     */
    public HybridMiniServletContainer(Router router, HybridProcessor processor) {
        // 의존성 주입을 통한 핵심 컴포넌트 초기화
        this.router = router;
        this.processor = processor;

        // 컬렉션 초기화 - 모든 동시성 컬렉션을 빈 상태로 생성
        this.servlets = new ConcurrentHashMap<>();
        this.pathToServlet = new ConcurrentHashMap<>();
        this.contexts = new ConcurrentHashMap<>();
        this.servletPools = new ConcurrentHashMap<>();

        // 글로벌 컨텍스트 초기화 - 루트 경로로 설정
        this.globalContext = new MiniContext("/");
        // 모든 서블릿이 공유할 기본 컨텍스트 생성

        // 기본 라우트 핸들러 등록
        registerDefaultHandler();
        // 모든 요청을 서블릿 처리기로 라우팅하는 기본 핸들러 설정

        logger.info("하이브리드 미니 서블릿 컨테이너 초기화 완료");
    }

    /**
     * 기본 라우트 핸들러 등록
     */
    private void registerDefaultHandler() {
        // 모든 경로("*")에 대해 서블릿 요청 핸들러 등록
        router.all("*", this::handleServletRequest);
        // router.all() 사용 이유:
        // 1. 모든 HTTP 메서드(GET, POST, PUT, DELETE 등) 지원
        // 2. 와일드카드("*")로 모든 경로 패턴 매칭
        // 3. 메서드 참조(::)로 핸들러 함수 간결하게 전달

        logger.debug("기본 서블릿 핸들러 등록 완료");
    }

    /**
     * 서블릿 등록
     */
    public void registerServlet(String name, MiniServlet servlet, String... patterns) {
        // 가변 인수(String...)로 여러 URL 패턴을 하나의 서블릿에 매핑 가능

        try {
            // 서블릿 정보 생성 - 메타데이터 객체로 서블릿 정보 캡슐화
            ServletInfo servletInfo = new ServletInfo(name, servlet, patterns);

            // 서블릿 초기화 - 서블릿 생명주기의 init 단계 실행
            servlet.init(globalContext);
            // init() 호출로 서블릿이 요청 처리 준비 완료

            // 서블릿 등록 - 이름을 키로 서블릿 정보 저장
            servlets.put(name, servletInfo);

            // 패턴별 매핑 등록 - 각 URL 패턴을 서블릿 이름에 매핑
            for (String pattern : patterns) {
                pathToServlet.put(pattern, name);
                logger.debug("서블릿 패턴 매핑 등록: {} -> {}", pattern, name);
            }
            // 빠른 경로 기반 서블릿 조회를 위한 매핑 테이블 구축

            // 서블릿 풀 초기화 - 성능 최적화를 위한 인스턴스 풀 생성
            initializeServletPool(name, servlet);

            // 등록 완료 로그 - 서블릿 타입과 패턴 정보 포함
            logger.info("서블릿 등록 완료: {} (타입: {}, 패턴: {})",
                    name, servlet.getClass().getSimpleName(), Arrays.toString(patterns));
            // Arrays.toString()으로 패턴 배열을 읽기 쉬운 문자열로 변환

        } catch (Exception e) {
            // 서블릿 등록 실패시 로그 기록 및 예외 전파
            logger.error("서블릿 등록 실패: {}", name, e);
            throw new RuntimeException("서블릿 등록 실패: " + name, e);
            // RuntimeException으로 래핑하여 체크 예외를 언체크 예외로 변환
        }
    }

    /**
     * 서블릿 풀 초기화
     */
    private void initializeServletPool(String servletName, MiniServlet prototype) {
        // ConcurrentLinkedQueue로 스레드 안전한 서블릿 인스턴스 큐 생성
        Queue<MiniServlet> pool = new ConcurrentLinkedQueue<>();
        // ConcurrentLinkedQueue 사용 이유:
        // 1. 락 없는 동시성 큐로 높은 성능 제공
        // 2. offer()/poll() 메서드로 안전한 삽입/제거
        // 3. 큐 크기 제한이 없어 유연한 풀 관리

        try {
            // 프로토타입을 기반으로 추가 인스턴스 생성
            for (int i = 0; i < 3; i++) {
                // 초기 3개 인스턴스로 적당한 풀 크기 설정
                MiniServlet instance = createServletInstance(prototype);
                // 리플렉션을 통한 서블릿 인스턴스 복제
                instance.init(globalContext);
                // 각 인스턴스도 초기화하여 요청 처리 준비
                pool.offer(instance);
                // offer()로 큐에 인스턴스 추가
            }

            // 서블릿 이름을 키로 풀 등록
            servletPools.put(servletName, pool);
            logger.debug("서블릿 풀 초기화 완료: {} (초기 크기: {})", servletName, pool.size());

        } catch (Exception e) {
            // 풀 초기화 실패는 치명적이지 않으므로 경고 로그만 기록
            logger.warn("서블릿 풀 초기화 실패: {}", servletName, e);
            // 풀 없이도 원본 서블릿 인스턴스로 동작 가능
        }
    }

    /**
     * 서블릿 인스턴스 생성 (클론)
     */
    private MiniServlet createServletInstance(MiniServlet prototype) throws Exception {
        // 리플렉션을 사용한 서블릿 인스턴스 복제
        Class<?> servletClass = prototype.getClass();
        // getClass()로 프로토타입의 실제 클래스 타입 획득

        return (MiniServlet) servletClass.getDeclaredConstructor().newInstance();
        // getDeclaredConstructor() 사용 이유:
        // 1. 기본 생성자(매개변수 없는 생성자) 획득
        // 2. newInstance()로 새로운 인스턴스 생성
        // 3. 동일한 클래스의 독립적인 인스턴스 생성
        // 타입 캐스팅으로 MiniServlet 타입 보장
    }

    /**
     * 서블릿 요청 처리 - 하이브리드 컨테이너의 핵심 로직
     */
    private CompletableFuture<HttpResponse> handleServletRequest(HttpRequest request) {
        // 모든 HTTP 요청의 진입점 - 라우터에서 호출되는 핵심 메서드
        totalRequests.incrementAndGet();
        // 총 요청 수 원자적 증가로 통계 수집

        try {
            // 1. 요청 경로에 맞는 서블릿 찾기
            String servletName = findServletForPath(request.getPath());
            // URL 패턴 매칭을 통한 적절한 서블릿 선택

            if (servletName == null) {
                // 매칭되는 서블릿이 없는 경우 404 응답
                logger.debug("서블릿을 찾을 수 없음 - 경로: {}", request.getPath());
                return CompletableFuture.completedFuture(
                        HttpResponse.notFound("No servlet found for path: " + request.getPath())
                );
                // CompletableFuture.completedFuture()로 즉시 완료된 Future 반환
            }

            ServletInfo servletInfo = servlets.get(servletName);
            if (servletInfo == null) {
                // 서블릿 이름은 있지만 실제 서블릿이 등록되지 않은 경우
                logger.warn("등록되지 않은 서블릿: {}", servletName);
                return CompletableFuture.completedFuture(
                        HttpResponse.internalServerError("Servlet not found: " + servletName)
                );
                // 500 에러로 서버 내부 문제임을 표시
            }

            // 2. 서블릿 타입에 따른 처리 분기 (간단한 instanceof 사용)
            MiniServlet servlet = servletInfo.getServlet();

            if (servlet instanceof MiniAsyncServlet) {
                // 비동기 서블릿인 경우 비동기 처리 경로
                return handleAsyncServlet(request, servletInfo);
            } else {
                // 동기 서블릿인 경우 하이브리드 처리 경로
                return handleSyncServlet(request, servletInfo);
            }
            // instanceof 연산자로 런타임 타입 체크하여 적절한 처리 방식 선택

        } catch (Exception e) {
            // 예외 발생시 로그 기록 및 에러 통계 업데이트
            logger.error("서블릿 요청 처리 중 오류", e);
            errorRequests.incrementAndGet();
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Servlet processing error")
            );
            // 모든 예외를 잡아서 500 에러로 변환하여 안정성 확보
        }
    }

    /**
     * Hybrid 비동기 서블릿 처리
     */
    private CompletableFuture<HttpResponse> handleAsyncServlet(HttpRequest request, ServletInfo servletInfo) {
        // 비동기 서블릿 전용 처리 로직
        asyncRequests.incrementAndGet();
        // 비동기 요청 통계 증가

        logger.debug("비동기 서블릿 처리 시작 - 서블릿: {}, URI: {}",
                servletInfo.getName(), request.getPath());

        // 서블릿 풀에서 인스턴스 대여
        MiniAsyncServlet asyncServlet = (MiniAsyncServlet) borrowServletFromPool(servletInfo.getName());
        // borrowServletFromPool()로 재사용 가능한 인스턴스 획득

        if (asyncServlet == null) {
            // 풀에 사용 가능한 인스턴스가 없으면 원본 사용
            asyncServlet = (MiniAsyncServlet) servletInfo.getServlet();
        }

        try {
            // MiniRequest와 MiniResponse 생성
            MiniRequest miniRequest = new MiniRequest(request, globalContext);
            MiniResponse miniResponse = new MiniResponse();
            // 서블릿 API 호환을 위한 래퍼 객체 생성

            // MiniAsyncServlet의 processAsync 메서드 호출
            CompletableFuture<Void> servletFuture = asyncServlet.processAsync(miniRequest, miniResponse);
            // processAsync()로 비동기 서블릿 처리 시작

            final MiniAsyncServlet finalServlet = asyncServlet;
            // final 변수로 람다식에서 사용 가능하도록 함

            return servletFuture
                    .thenApply(unused -> {
                        // 비동기 처리 완료시 응답 생성
                        completedRequests.incrementAndGet();
                        logger.debug("비동기 서블릿 처리 완료 - 서블릿: {}", servletInfo.getName());
                        return miniResponse.build();
                        // MiniResponse.build()로 HttpResponse 객체 생성
                    })
                    .whenComplete((response, throwable) -> {
                        // 완료(성공/실패 무관)시 서블릿 인스턴스 반환
                        returnServletToPool(servletInfo.getName(), finalServlet);
                        // 풀로 반환하여 재사용 가능하도록 함

                        if (throwable != null) {
                            // 예외 발생시 로그 기록 및 에러 통계 업데이트
                            logger.error("비동기 서블릿 처리 실패", throwable);
                            errorRequests.incrementAndGet();
                        }
                    })
                    .exceptionally(throwable -> {
                        // 예외 발생시 에러 응답 생성
                        logger.error("비동기 서블릿 예외", throwable);
                        errorRequests.incrementAndGet();
                        return HttpResponse.internalServerError("Async servlet error");
                    });
            // CompletableFuture 체인으로 비동기 처리 완료 후 후처리 수행

        } catch (Exception e) {
            // 서블릿 호출 실패시 인스턴스 반환 및 에러 응답
            returnServletToPool(servletInfo.getName(), asyncServlet);
            logger.error("비동기 서블릿 호출 실패", e);
            errorRequests.incrementAndGet();
            return CompletableFuture.completedFuture(
                    HttpResponse.internalServerError("Async servlet call failed")
            );
        }
    }

    /**
     * Core 동기 서블릿 처리 (하이브리드 방식)
     */
    private CompletableFuture<HttpResponse> handleSyncServlet(HttpRequest request, ServletInfo servletInfo) {
        // 동기 서블릿을 하이브리드 방식으로 처리
        logger.debug("동기 서블릿 하이브리드 처리 시작 - 서블릿: {}, URI: {}",
                servletInfo.getName(), request.getPath());

        // RouteHandler로 동기 서블릿 로직을 래핑
        RouteHandler servletHandler = (req) -> {
            // 람다식으로 RouteHandler 인터페이스 구현
            MiniServlet servlet = (MiniServlet) borrowServletFromPool(servletInfo.getName());
            // 풀에서 서블릿 인스턴스 대여

            if (servlet == null) {
                // 풀에 인스턴스가 없으면 원본 서블릿 사용
                servlet = servletInfo.getServlet();
            }

            try {
                // 서블릿 API 호환 객체 생성
                MiniRequest miniRequest = new MiniRequest(req, globalContext);
                MiniResponse miniResponse = new MiniResponse();

                // 동기 서블릿의 service 메서드 호출
                HttpResponse response = servlet.service(miniRequest, miniResponse);
                // service()는 동기식으로 즉시 결과 반환

                completedRequests.incrementAndGet();
                logger.debug("동기 서블릿 처리 완료 - 서블릿: {}", servletInfo.getName());

                // 동기 결과를 CompletableFuture로 래핑
                return CompletableFuture.completedFuture(response);

            } catch (Exception e) {
                // 동기 서블릿 처리 실패시 에러 응답
                logger.error("동기 서블릿 처리 실패", e);
                errorRequests.incrementAndGet();
                return CompletableFuture.completedFuture(
                        HttpResponse.internalServerError("Sync servlet error")
                );
            } finally {
                // 반드시 서블릿 인스턴스 반환
                returnServletToPool(servletInfo.getName(), servlet);
                // finally 블록으로 예외 발생 여부와 무관하게 반환 보장
            }
        };

        // HybridProcessor를 통한 하이브리드 처리
        return processor.processRequest(request, servletHandler);
        // 동기 서블릿도 하이브리드 처리기를 통해 최적화된 방식으로 처리
    }

    /**
     * 요청 경로에 맞는 서블릿 찾기
     */
    private String findServletForPath(String path) {
        // 1. 정확한 매칭 우선 - 완전히 일치하는 패턴부터 확인
        String servletName = pathToServlet.get(path);
        if (servletName != null) {
            return servletName;
        }
        // 정확한 경로 매칭이 가장 높은 우선순위

        // 2. 패턴 매칭 - 와일드카드나 확장자 패턴 확인
        for (Map.Entry<String, String> entry : pathToServlet.entrySet()) {
            String pattern = entry.getKey();
            String name = entry.getValue();

            if (matchesPattern(path, pattern)) {
                return name;
            }
        }
        // 등록된 모든 패턴을 순회하며 매칭 확인

        return null; // 매칭되는 서블릿이 없음
    }

    /**
     * URL 패턴 매칭
     */
    private boolean matchesPattern(String path, String pattern) {
        // 다양한 URL 패턴 매칭 지원
        if ("*".equals(pattern)) {
            return true; // 모든 경로 매칭
        }

        if (pattern.equals(path)) {
            return true; // 정확한 매칭
        }

        if (pattern.endsWith("*")) {
            // 접두사 패턴 매칭 (예: /api/*)
            String prefix = pattern.substring(0, pattern.length() - 1);
            return path.startsWith(prefix);
            // startsWith()로 접두사 매칭 확인
        }

        if (pattern.startsWith("*.")) {
            // 확장자 패턴 매칭 (예: *.jsp)
            String extension = pattern.substring(1);
            return path.endsWith(extension);
            // endsWith()로 확장자 매칭 확인
        }

        return false; // 매칭되지 않음
    }

    /**
     * 서블릿 풀에서 인스턴스 가져오기
     */
    private MiniServlet borrowServletFromPool(String servletName) {
        // 서블릿 풀에서 재사용 가능한 인스턴스 획득
        Queue<MiniServlet> pool = servletPools.get(servletName);

        if (pool != null) {
            MiniServlet servlet = pool.poll();
            // poll() 사용 이유:
            // 1. 큐에서 요소를 제거하며 반환
            // 2. 큐가 비어있으면 null 반환 (예외 없음)
            // 3. 스레드 안전한 큐 연산

            if (servlet != null) {
                logger.debug("서블릿 풀에서 인스턴스 가져옴: {} (풀 크기: {})",
                        servletName, pool.size());
                return servlet;
            }
        }

        return null; // 사용 가능한 인스턴스가 없음
    }

    /**
     * 서블릿을 풀로 반환
     */
    private void returnServletToPool(String servletName, MiniServlet servlet) {
        if (servlet == null) return; // null 체크

        Queue<MiniServlet> pool = servletPools.get(servletName);

        if (pool != null && pool.size() < maxServletPoolSize) {
            // 풀 크기가 최대 크기 미만인 경우에만 반환
            pool.offer(servlet);
            // offer()로 큐에 안전하게 추가
            logger.debug("서블릿 풀로 반환: {} (풀 크기: {})", servletName, pool.size());
        } else {
            // 풀이 가득 찬 경우 인스턴스 파괴
            try {
                servlet.destroy();
                // destroy() 호출로 서블릿 생명주기 완료
                logger.debug("서블릿 인스턴스 파괴: {}", servletName);
            } catch (Exception e) {
                logger.warn("서블릿 파괴 중 오류", e);
                // 파괴 실패는 로그만 기록하고 계속 진행
            }
        }
    }

    /**
     * 컨텍스트 등록
     */
    public void registerContext(String contextPath, MiniContext context) {
        // 컨텍스트 경로별 독립적인 설정 관리
        contexts.put(contextPath, context);
        logger.info("컨텍스트 등록: {}", contextPath);
    }

    /**
     * 서블릿 해제
     */
    public void unregisterServlet(String servletName) {
        try {
            // 서블릿 정보 제거
            ServletInfo servletInfo = servlets.remove(servletName);

            if (servletInfo != null) {
                // 등록된 모든 패턴 매핑 제거
                for (String pattern : servletInfo.getPatterns()) {
                    pathToServlet.remove(pattern);
                }

                // 서블릿 생명주기 종료
                servletInfo.getServlet().destroy();
                // 서블릿 풀 파괴
                destroyServletPool(servletName);

                logger.info("서블릿 해제 완료: {}", servletName);
            }

        } catch (Exception e) {
            logger.error("서블릿 해제 실패: {}", servletName, e);
        }
    }

    /**
     * 서블릿 풀 파괴
     */
    private void destroyServletPool(String servletName) {
        // 풀에서 모든 인스턴스 제거 및 파괴
        Queue<MiniServlet> pool = servletPools.remove(servletName);

        if (pool != null) {
            int poolSize = pool.size();

            MiniServlet servlet;
            while ((servlet = pool.poll()) != null) {
                // poll()로 큐에서 하나씩 제거하며 파괴
                try {
                    servlet.destroy();
                } catch (Exception e) {
                    logger.warn("풀 서블릿 파괴 중 오류", e);
                }
            }

            logger.debug("서블릿 풀 파괴 완료: {} (파괴된 인스턴스: {})", servletName, poolSize);
        }
    }

    // === Getters ===

    public Set<String> getServletNames() {
        // 등록된 모든 서블릿 이름의 복사본 반환
        return new HashSet<>(servlets.keySet());
        // 원본 데이터 보호를 위한 새로운 Set 생성
    }

    public ServletInfo getServletInfo(String servletName) {
        // 특정 서블릿의 메타데이터 조회
        return servlets.get(servletName);
    }

    public ContainerStats getStats() {
        // 컨테이너 통계 정보 생성
        return new ContainerStats(
                servlets.size(),
                totalRequests.get(),
                asyncRequests.get(),
                completedRequests.get(),
                errorRequests.get(),
                servletRequestCounter.get(),
                calculateAveragePoolSize()
        );
        // 현재 상태의 스냅샷을 불변 객체로 제공
    }

    private double calculateAveragePoolSize() {
        // 모든 서블릿 풀의 평균 크기 계산
        if (servletPools.isEmpty()) return 0.0;

        int totalSize = servletPools.values().stream()
                .mapToInt(Queue::size)
                .sum();
        // Stream API로 모든 풀 크기의 합계 계산

        return (double) totalSize / servletPools.size();
        // 평균 계산을 위한 double 캐스팅
    }

    /**
     * 컨테이너 종료
     */
    public void shutdown() {
        logger.info("하이브리드 미니 서블릿 컨테이너 종료 시작...");

        try {
            // 등록된 모든 서블릿 해제
            Set<String> servletNames = new HashSet<>(servlets.keySet());
            // keySet()의 복사본 생성으로 동시 수정 문제 방지

            for (String servletName : servletNames) {
                unregisterServlet(servletName);
                // 각 서블릿의 정상적인 해제 과정 수행
            }

            // 모든 컨텍스트 정리
            contexts.clear();
            logger.info("하이브리드 미니 서블릿 컨테이너 종료 완료");

        } catch (Exception e) {
            logger.error("컨테이너 종료 중 오류", e);
        }
    }

    // === 내부 클래스들 ===

    /**
     * 서블릿 메타데이터 클래스
     */
    public static class ServletInfo {
        // 서블릿과 관련된 모든 메타데이터를 보관하는 불변 클래스
        private final String name; // 서블릿 이름
        private final MiniServlet servlet; // 서블릿 인스턴스
        private final String[] patterns; // URL 패턴 배열
        private final long registeredTime; // 등록 시간
        // final 필드로 불변성 보장

        public ServletInfo(String name, MiniServlet servlet, String[] patterns) {
            this.name = name;
            this.servlet = servlet;
            this.patterns = patterns.clone(); // 배열 복사로 외부 수정 방지
            this.registeredTime = System.currentTimeMillis();
        }

        // 접근자 메서드들
        public String getName() { return name; }
        public MiniServlet getServlet() { return servlet; }
        public String[] getPatterns() { return patterns.clone(); } // 반환시에도 복사본 제공
        public long getRegisteredTime() { return registeredTime; }

        @Override
        public String toString() {
            return String.format("ServletInfo{name='%s', patterns=%s, class=%s}",
                    name, Arrays.toString(patterns), servlet.getClass().getSimpleName());
        }
    }

    /**
     * 컨테이너 통계 클래스
     */
    public static class ContainerStats {
        // 컨테이너 성능 통계를 담는 불변 데이터 클래스
        private final int servletCount; // 등록된 서블릿 수
        private final long totalRequests; // 총 요청 수
        private final long asyncRequests; // 비동기 요청 수
        private final long completedRequests; // 완료된 요청 수
        private final long errorRequests; // 오류 요청 수
        private final long servletRequests; // 서블릿 요청 수
        private final double averagePoolSize; // 평균 풀 크기

        public ContainerStats(int servletCount, long totalRequests, long asyncRequests,
                              long completedRequests, long errorRequests, long servletRequests,
                              double averagePoolSize) {
            // 생성자에서 모든 통계 값 초기화
            this.servletCount = servletCount;
            this.totalRequests = totalRequests;
            this.asyncRequests = asyncRequests;
            this.completedRequests = completedRequests;
            this.errorRequests = errorRequests;
            this.servletRequests = servletRequests;
            this.averagePoolSize = averagePoolSize;
        }

        // 접근자 메서드들
        public int getServletCount() { return servletCount; }
        public long getTotalRequests() { return totalRequests; }
        public long getAsyncRequests() { return asyncRequests; }
        public long getCompletedRequests() { return completedRequests; }
        public long getErrorRequests() { return errorRequests; }
        public long getServletRequests() { return servletRequests; }
        public double getAveragePoolSize() { return averagePoolSize; }

        // 계산된 통계 메서드들
        public double getErrorRate() {
            // 오류율 계산 (백분율)
            return totalRequests > 0 ? (double) errorRequests / totalRequests * 100 : 0.0;
        }

        public double getAsyncRate() {
            // 비동기 요청 비율 계산 (백분율)
            return totalRequests > 0 ? (double) asyncRequests / totalRequests * 100 : 0.0;
        }

        @Override
        public String toString() {
            // 모든 통계 정보를 읽기 쉬운 형태로 포맷팅
            return String.format(
                    "ContainerStats{servlets=%d, requests=%d, async=%d (%.1f%%), " +
                            "completed=%d, errors=%d (%.1f%%), avgPoolSize=%.1f}",
                    servletCount, totalRequests, asyncRequests, getAsyncRate(),
                    completedRequests, errorRequests, getErrorRate(), averagePoolSize
            );
        }
    }
}