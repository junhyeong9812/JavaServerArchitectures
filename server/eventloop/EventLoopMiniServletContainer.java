package server.eventloop;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;
import server.core.mini.*;
import server.core.http.*;
import server.core.routing.RouteHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 이벤트 기반 서블릿 컨테이너
 * EventLoop 아키텍처와 미니 서블릿 API를 연결
 *
 * 역할:
 * - 서블릿 생명주기 관리 (등록, 초기화, 실행, 종료)
 * - 동기/비동기 서블릿 모두 지원
 * - EventLoop 환경에서 서블릿이 안전하게 실행되도록 보장
 * - JSP 스타일 처리, 필터 체인, 세션 관리 등 웹 컨테이너 기능 제공
 */
public class EventLoopMiniServletContainer {

    // Logger 인스턴스 - 컨테이너 동작 상황 추적
    private static final Logger logger = LoggerFactory.getLogger(EventLoopMiniServletContainer.class);

    // EventLoop 서버 인스턴스 - 실제 HTTP 서버 기능 제공
    private final EventLoopServer server;

    // 서블릿 컨텍스트 - 애플리케이션 전역 정보와 설정 저장
    private final MiniContext context;

    // 등록된 서블릿들을 패턴별로 저장하는 맵
    // ConcurrentHashMap: 스레드 안전한 HashMap 구현
    private final Map<String, MiniServlet> servlets;

    // 이벤트 큐 - 비동기 작업 처리용
    private final EventQueue eventQueue;

    /**
     * 기본 생성자 - 새로운 EventLoopServer 인스턴스 생성
     *
     * @throws Exception 서버 초기화 실패시
     */
    public EventLoopMiniServletContainer() throws Exception {
        // this(): 같은 클래스의 다른 생성자 호출
        // new EventLoopServer(): 새로운 서버 인스턴스 생성
        this(new EventLoopServer());
    }

    /**
     * 매개변수 생성자 - 기존 EventLoopServer 인스턴스 사용
     *
     * @param server 사용할 EventLoop 서버 인스턴스
     * @throws Exception 컨테이너 초기화 실패시
     */
    public EventLoopMiniServletContainer(EventLoopServer server) throws Exception {
        this.server = server;

        // MiniContext: 서블릿 컨텍스트 구현체
        // "/" : 루트 컨텍스트 패스로 설정
        this.context = new MiniContext("/");

        // 서블릿 저장용 동시성 안전 맵 초기화
        this.servlets = new ConcurrentHashMap<>();

        // getProcessor(): EventLoopServer에서 EventLoopProcessor 반환
        // getEventQueue(): EventLoopProcessor에서 EventQueue 반환
        // 메서드 체이닝으로 EventQueue 획득
        this.eventQueue = server.getProcessor().getEventQueue();

        // 컨테이너 초기 설정 수행
        setupContainer();
    }

    /**
     * 컨테이너 초기 설정
     *
     * 컨텍스트에 컨테이너 관련 메타데이터 설정
     * 디버깅과 모니터링에 유용한 정보들
     */
    private void setupContainer() {
        // setAttribute(): 컨텍스트에 속성 설정 (key-value 형태)
        // 컨테이너 타입 정보 설정
        context.setAttribute("container.type", "EventLoop");

        // 컨테이너 아키텍처 정보 설정
        context.setAttribute("container.architecture", "Single Thread + NIO Selector");

        // 비동기 지원 여부 설정
        context.setAttribute("container.async", true);

        logger.info("EventLoop 미니 서블릿 컨테이너 초기화 완료");
    }

    /**
     * 서블릿 등록
     *
     * 서블릿을 특정 URL 패턴에 매핑하고 라우터에 등록
     *
     * @param pattern URL 패턴 (예: "/api/*", "/servlet/test")
     * @param servlet 등록할 MiniServlet 인스턴스
     * @return 메서드 체이닝을 위한 자기 자신 반환
     */
    public EventLoopMiniServletContainer addServlet(String pattern, MiniServlet servlet) {
        try {
            // servlet.init(): 서블릿 초기화 메서드 호출
            // MiniContext를 매개변수로 전달하여 서블릿이 컨텍스트 정보에 접근할 수 있도록 함
            servlet.init(context);

            // put(): Map에 패턴-서블릿 쌍 저장
            servlets.put(pattern, servlet);

            // 서블릿 타입에 따른 라우트 핸들러 생성
            RouteHandler handler = createServletHandler(servlet);

            // getRouter(): EventLoopServer에서 Router 인스턴스 반환
            // all(): 모든 HTTP 메서드(GET, POST, PUT, DELETE 등)에 대해 라우트 등록
            server.getRouter().all(pattern, handler);

            // getClass().getSimpleName(): 클래스명에서 패키지 제외한 단순 이름
            logger.info("서블릿 등록 완료: {} -> {}", pattern, servlet.getClass().getSimpleName());

        } catch (Exception e) {
            logger.error("서블릿 등록 실패: {}", pattern, e);
            // RuntimeException: 체크되지 않은 예외로 변환하여 메서드 체이닝 중단
            throw new RuntimeException("서블릿 등록 실패", e);
        }

        // 메서드 체이닝을 위해 자기 자신 반환
        // 예: container.addServlet(pattern1, servlet1).addServlet(pattern2, servlet2);
        return this;
    }

    /**
     * 서블릿 핸들러 생성
     *
     * 서블릿 타입(동기/비동기)에 따라 적절한 핸들러 생성
     *
     * @param servlet 핸들러를 생성할 서블릿
     * @return RouteHandler 인스턴스
     */
    private RouteHandler createServletHandler(MiniServlet servlet) {
        // instanceof: 객체가 특정 클래스의 인스턴스인지 확인
        if (servlet instanceof MiniAsyncServlet) {
            // 비동기 서블릿인 경우 비동기 핸들러 생성
            return createAsyncServletHandler((MiniAsyncServlet) servlet);
        } else {
            // 동기 서블릿인 경우 동기 핸들러 생성 (EventLoop에서 비동기로 래핑)
            return createSyncServletHandler(servlet);
        }
    }

    /**
     * 비동기 서블릿 핸들러 생성
     *
     * MiniAsyncServlet을 EventLoop 환경에서 처리하는 핸들러
     *
     * @param asyncServlet 비동기 서블릿 인스턴스
     * @return RouteHandler 구현체
     */
    private RouteHandler createAsyncServletHandler(MiniAsyncServlet asyncServlet) {
        // 람다 표현식으로 RouteHandler 인터페이스 구현
        // request -> { ... } : HttpRequest를 받아서 CompletableFuture<HttpResponse> 반환
        return request -> {
            // EventLoop 스레드에서 실행되므로 직접 처리
            // executeAsync(): EventQueue를 통해 비동기 작업 실행
            return eventQueue.executeAsync(() -> {
                try {
                    // MiniRequest: HttpRequest를 MiniServlet API에 맞게 래핑
                    MiniRequest miniRequest = new MiniRequest(request, context);

                    // MiniResponse: 응답 생성을 위한 헬퍼 클래스
                    MiniResponse miniResponse = new MiniResponse();

                    // serviceAsync(): 비동기 서블릿의 핵심 메서드 호출
                    // CompletableFuture<HttpResponse> 반환
                    return asyncServlet.serviceAsync(miniRequest, miniResponse)
                            // exceptionally(): 예외 발생시 처리
                            .exceptionally(error -> {
                                logger.error("비동기 서블릿 처리 중 오류", error);

                                // internalServerError(): 500 에러 응답 생성
                                return HttpResponse.internalServerError("비동기 서블릿 오류: " + error.getMessage());
                            });

                } catch (Exception e) {
                    logger.error("비동기 서블릿 응답 생성 중 오류", e);

                    // completedFuture(): 이미 완료된 Future 반환
                    return CompletableFuture.completedFuture(
                            HttpResponse.internalServerError("서블릿 초기화 오류"));
                }

                // thenCompose(): CompletableFuture<CompletableFuture<T>>를 CompletableFuture<T>로 평면화
                // 중첩된 Future를 단일 Future로 변환
            }).thenCompose(future -> future);
        };
    }

    /**
     * 동기 서블릿 핸들러 생성 (비동기로 래핑)
     *
     * 기존 동기 서블릿을 EventLoop 환경에서 안전하게 실행
     *
     * @param servlet 동기 서블릿 인스턴스
     * @return RouteHandler 구현체
     */
    private RouteHandler createSyncServletHandler(MiniServlet servlet) {
        return request -> {
            // EventLoop에서 비동기적으로 처리
            return eventQueue.executeAsync(() -> {
                try {
                    MiniRequest miniRequest = new MiniRequest(request, context);
                    MiniResponse miniResponse = new MiniResponse();

                    // service(): 동기 서블릿의 핵심 메서드 호출
                    // HttpResponse를 직접 반환 (CompletableFuture가 아님)
                    HttpResponse response = servlet.service(miniRequest, miniResponse);
                    return response;

                } catch (Exception e) {
                    logger.error("동기 서블릿 처리 중 오류", e);
                    return HttpResponse.internalServerError("서블릿 오류: " + e.getMessage());
                }
            });
        };
    }

    /**
     * JSP 스타일 서블릿 등록 (간단한 구현)
     *
     * JSP와 유사한 문법으로 동적 웹 페이지 생성
     * 실제 JSP 엔진은 아니고 간단한 템플릿 처리
     *
     * @param pattern URL 패턴
     * @param jspContent JSP 스타일의 템플릿 내용
     * @return 메서드 체이닝을 위한 자기 자신
     */
    public EventLoopMiniServletContainer addJsp(String pattern, String jspContent) {
        // 익명 클래스로 MiniServlet 구현
        MiniServlet jspServlet = new MiniServlet() {
            @Override
            protected void doGet(MiniRequest request, MiniResponse response) throws Exception {
                // processJspContent(): JSP 스타일 내용을 실제 HTML로 변환
                String processedContent = processJspContent(jspContent, request);

                // sendHtml(): HTML 응답 전송
                response.sendHtml(processedContent);
            }
        };

        // addServlet(): 생성한 JSP 서블릿을 일반 서블릿으로 등록
        return addServlet(pattern, jspServlet);
    }

    /**
     * 간단한 JSP 컨텐츠 처리
     *
     * JSP의 Expression Language(EL)와 유사한 기능 제공
     * ${param.name}, ${request.uri} 등의 변수 치환
     *
     * @param jspContent 원본 JSP 스타일 내용
     * @param request 요청 객체 (변수 치환에 사용)
     * @return 처리된 HTML 내용
     */
    private String processJspContent(String jspContent, MiniRequest request) {
        // 매우 간단한 JSP 스타일 변수 치환
        String processed = jspContent;

        // ${param.name} 형태의 파라미터 치환
        // java.util.regex.Pattern: 정규표현식 패턴 컴파일
        // \\$\\{param\\.([^}]+)\\}: ${param.변수명} 패턴 매칭
        java.util.regex.Pattern paramPattern = java.util.regex.Pattern.compile("\\$\\{param\\.([^}]+)\\}");

        // matcher(): 문자열에서 패턴 검색을 위한 Matcher 객체 생성
        java.util.regex.Matcher matcher = paramPattern.matcher(processed);

        // StringBuffer: 변경 가능한 문자열 버퍼 (정규표현식 치환에 사용)
        StringBuffer sb = new StringBuffer();

        // find(): 다음 매칭되는 패턴 찾기
        while (matcher.find()) {
            // group(1): 첫 번째 캡처 그룹 (괄호 안의 내용)
            String paramName = matcher.group(1);

            // getParameter(): 요청 파라미터 값 획득
            String value = request.getParameter(paramName);

            // appendReplacement(): 매칭된 부분을 새 값으로 치환
            // value != null ? value : "": null 안전 처리
            matcher.appendReplacement(sb, value != null ? value : "");
        }

        // appendTail(): 남은 부분 추가
        matcher.appendTail(sb);
        processed = sb.toString();

        // ${request.uri} 같은 요청 정보 치환
        // replace(): 문자열 치환 (정규표현식 아님)
        processed = processed.replace("${request.uri}", request.getRequestURI());
        processed = processed.replace("${request.method}", request.getMethod().toString());

        return processed;
    }

    /**
     * 필터 체인 지원 (간단한 구현)
     *
     * 서블릿 실행 전후에 공통 처리 로직 삽입
     * 예: 인증, 로깅, 인코딩 설정 등
     *
     * @param pattern 필터를 적용할 URL 패턴
     * @param filter 필터 구현체
     * @return 메서드 체이닝을 위한 자기 자신
     */
    public EventLoopMiniServletContainer addFilter(String pattern, ServletFilter filter) {
        // 기존 라우트를 필터로 래핑
        // use(): 모든 요청에 대해 실행되는 미들웨어 등록
        server.getRouter().use((request, next) -> {
            // getPath(): 요청 경로 반환
            // matches(): 문자열이 정규표현식과 매칭되는지 확인
            // replace("*", ".*"): 와일드카드를 정규표현식으로 변환
            if (request.getPath().matches(pattern.replace("*", ".*"))) {
                return eventQueue.executeAsync(() -> {
                    try {
                        MiniRequest miniRequest = new MiniRequest(request, context);
                        MiniResponse miniResponse = new MiniResponse();

                        // doFilter(): 필터 실행
                        // boolean 반환: true면 다음 핸들러 실행, false면 차단
                        if (filter.doFilter(miniRequest, miniResponse)) {
                            // 필터가 통과하면 다음 핸들러 실행
                            // next.handle(): 다음 라우트 핸들러로 요청 전달
                            return next.handle(request);
                        } else {
                            // 필터가 차단하면 필터의 응답 반환
                            // build(): MiniResponse를 HttpResponse로 변환
                            return CompletableFuture.completedFuture(miniResponse.build());
                        }
                    } catch (Exception e) {
                        logger.error("필터 오류", e);
                        return CompletableFuture.completedFuture(
                                HttpResponse.internalServerError("필터 오류"));
                    }
                }).thenCompose(future -> future);
            } else {
                // 패턴이 매칭되지 않으면 필터 건너뛰고 다음 핸들러 실행
                return next.handle(request);
            }
        });

        return this;
    }

    /**
     * 서블릿 컨텍스트 리스너 등록
     *
     * 컨텍스트 초기화/소멸 시점에 실행할 리스너 등록
     * 예: 데이터베이스 연결 풀 초기화, 스케줄러 시작 등
     *
     * @param listener 컨텍스트 리스너 구현체
     * @return 메서드 체이닝을 위한 자기 자신
     */
    public EventLoopMiniServletContainer addContextListener(ContextListener listener) {
        try {
            // contextInitialized(): 컨텍스트 초기화 시점에 호출
            listener.contextInitialized(context);
            logger.info("컨텍스트 리스너 등록 완료: {}", listener.getClass().getSimpleName());
        } catch (Exception e) {
            logger.error("컨텍스트 리스너 초기화 오류", e);
        }

        return this;
    }

    /**
     * 세션 관리 활성화 (간단한 메모리 기반)
     *
     * HTTP는 무상태 프로토콜이므로 세션으로 상태 유지
     * 쿠키 기반의 세션 ID로 세션 추적
     *
     * @return 메서드 체이닝을 위한 자기 자신
     */
    public EventLoopMiniServletContainer enableSessions() {
        // 간단한 세션 관리자 구현
        SessionManager sessionManager = new InMemorySessionManager();

        // 컨텍스트에 세션 관리자 설정
        context.setAttribute("session.manager", sessionManager);

        // 세션 쿠키 처리 미들웨어 추가
        server.getRouter().use((request, next) -> {
            return eventQueue.executeAsync(() -> {
                // 세션 처리 로직
                // extractSessionId(): 요청에서 세션 ID 추출
                String sessionId = extractSessionId(request);
                final String finalSessionId;

                if (sessionId == null) {
                    // 세션 ID가 없으면 새 세션 생성
                    finalSessionId = sessionManager.createSession();
                } else {
                    // 기존 세션 ID 사용
                    finalSessionId = sessionId;
                }

                // 요청에 세션 정보 설정
                // setAttribute(): 요청별 속성 설정
                request.setAttribute("session.id", finalSessionId);
                request.setAttribute("session", sessionManager.getSession(finalSessionId));

                // 다음 핸들러 실행 후 응답에 세션 쿠키 추가
                return next.handle(request).thenApply(response -> {
                    // 응답에 세션 쿠키 추가
                    // setHeader(): HTTP 헤더 설정
                    response.setHeader("Set-Cookie", "JSESSIONID=" + finalSessionId + "; Path=/");
                    return response;
                });
            }).thenCompose(future -> future);
        });

        logger.info("세션 관리 활성화됨");
        return this;
    }

    /**
     * 요청에서 세션 ID 추출
     *
     * Cookie 헤더에서 JSESSIONID 값을 찾아 반환
     *
     * @param request HTTP 요청 객체
     * @return 세션 ID 또는 null (세션이 없는 경우)
     */
    private String extractSessionId(HttpRequest request) {
        // getHeader(): HTTP 헤더 값 반환
        String cookieHeader = request.getHeader("Cookie");

        if (cookieHeader != null) {
            // split(";"): 세미콜론으로 쿠키들 분리
            String[] cookies = cookieHeader.split(";");

            // 각 쿠키에서 JSESSIONID 찾기
            for (String cookie : cookies) {
                // trim(): 앞뒤 공백 제거
                // split("=", 2): 등호로 분리, 최대 2개 부분으로 분할
                String[] parts = cookie.trim().split("=", 2);

                // equals(): 문자열 동등성 비교
                if ("JSESSIONID".equals(parts[0]) && parts.length == 2) {
                    return parts[1]; // 세션 ID 값 반환
                }
            }
        }
        return null; // 세션 ID를 찾지 못함
    }

    /**
     * 서버 시작
     *
     * 기본 포트(8082)로 서버 시작
     */
    public void start() {
        start(8082);
    }

    /**
     * 서버 시작 (포트 지정)
     *
     * @param port 서버 포트 번호
     */
    public void start(int port) {
        logger.info("EventLoop 서블릿 컨테이너를 포트 {}에서 시작 중", port);
        logger.info("   서블릿: {}", servlets.size());
        logger.info("   아키텍처: EventLoop + 미니서블릿");

        // EventLoopServer 시작
        server.start(port);

        logger.info("EventLoop 서블릿 컨테이너 시작 완료!");
    }

    /**
     * 서버 종료
     *
     * 모든 서블릿을 안전하게 종료하고 서버 정지
     */
    public void stop() {
        logger.info("EventLoop 서블릿 컨테이너 종료 중...");

        // 모든 서블릿 종료
        // values(): Map의 모든 값들을 Collection으로 반환
        for (MiniServlet servlet : servlets.values()) {
            try {
                // destroy(): 서블릿 종료 메서드 호출
                servlet.destroy();
            } catch (Exception e) {
                logger.error("서블릿 종료 중 오류", e);
            }
        }

        // clear(): Map의 모든 항목 제거
        servlets.clear();

        // EventLoopServer 종료
        server.stop();

        logger.info("EventLoop 서블릿 컨테이너 종료 완료");
    }

    /**
     * 서버 실행 중인지 확인
     *
     * @return 서버가 실행 중이면 true
     */
    public boolean isRunning() {
        return server.isRunning();
    }

    /**
     * 서블릿 정보 반환
     *
     * 등록된 모든 서블릿의 패턴과 클래스명 정보
     *
     * @return 패턴-클래스명 매핑 Map
     */
    public Map<String, String> getServletInfo() {
        Map<String, String> info = new ConcurrentHashMap<>();

        // entrySet(): Map의 모든 키-값 쌍을 Set으로 반환
        for (Map.Entry<String, MiniServlet> entry : servlets.entrySet()) {
            // getKey(): Map.Entry에서 키 반환
            // getValue(): Map.Entry에서 값 반환
            info.put(entry.getKey(), entry.getValue().getClass().getSimpleName());
        }
        return info;
    }

    /**
     * 컨테이너 통계
     *
     * 컨테이너와 서버의 현재 상태 정보
     *
     * @return ContainerStats 객체
     */
    public ContainerStats getStats() {
        return new ContainerStats(
                servlets.size(),           // 등록된 서블릿 수
                server.getStats(),         // 서버 통계
                isRunning()               // 실행 상태
        );
    }

    // === 인터페이스 정의 ===

    /**
     * 서블릿 필터 인터페이스
     *
     * @FunctionalInterface: 함수형 인터페이스 표시
     * 람다 표현식으로 간편하게 구현 가능
     */
    @FunctionalInterface
    public interface ServletFilter {
        /**
         * 필터 실행 메서드
         *
         * @param request 요청 객체
         * @param response 응답 객체
         * @return true면 다음 핸들러 실행, false면 처리 중단
         * @throws Exception 필터 처리 중 예외
         */
        boolean doFilter(MiniRequest request, MiniResponse response) throws Exception;
    }

    /**
     * 컨텍스트 리스너 인터페이스
     *
     * 컨텍스트 생명주기 이벤트 처리
     */
    @FunctionalInterface
    public interface ContextListener {
        /**
         * 컨텍스트 초기화 시점 호출
         *
         * @param context 초기화된 컨텍스트
         * @throws Exception 초기화 중 예외
         */
        void contextInitialized(MiniContext context) throws Exception;
    }

    /**
     * 간단한 세션 관리자 인터페이스
     *
     * 세션 생성, 조회, 삭제 기능 제공
     */
    public interface SessionManager {
        /**
         * 새 세션 생성
         *
         * @return 생성된 세션 ID
         */
        String createSession();

        /**
         * 세션 데이터 조회
         *
         * @param sessionId 세션 ID
         * @return 세션 데이터 Map
         */
        Map<String, Object> getSession(String sessionId);

        /**
         * 세션 삭제
         *
         * @param sessionId 삭제할 세션 ID
         */
        void destroySession(String sessionId);
    }

    /**
     * 메모리 기반 세션 관리자
     *
     * 간단한 인메모리 세션 저장소 구현
     * 실제 운영환경에서는 Redis 등 외부 저장소 사용 권장
     */
    private static class InMemorySessionManager implements SessionManager {
        // 세션 데이터 저장용 맵 (세션ID -> 세션 데이터)
        private final Map<String, Map<String, Object>> sessions = new ConcurrentHashMap<>();

        // 세션 ID 생성용 카운터
        // AtomicLong: 스레드 안전한 long 값
        private final java.util.concurrent.atomic.AtomicLong sessionIdGenerator =
                new java.util.concurrent.atomic.AtomicLong(0);

        @Override
        public String createSession() {
            // incrementAndGet(): 원자적으로 1 증가하고 새 값 반환
            // System.currentTimeMillis(): 현재 시간 (밀리초)
            String sessionId = "SESSION_" + sessionIdGenerator.incrementAndGet() + "_" + System.currentTimeMillis();

            // put(): 새 세션 데이터 저장 (빈 Map으로 초기화)
            sessions.put(sessionId, new ConcurrentHashMap<>());
            return sessionId;
        }

        @Override
        public Map<String, Object> getSession(String sessionId) {
            // computeIfAbsent(): 키가 없으면 새 값을 계산하여 저장, 있으면 기존 값 반환
            // k -> new ConcurrentHashMap<>(): 람다 표현식으로 새 Map 생성
            return sessions.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
        }

        @Override
        public void destroySession(String sessionId) {
            // remove(): Map에서 키와 연결된 값 제거
            sessions.remove(sessionId);
        }
    }

    /**
     * 컨테이너 통계 정보
     *
     * 컨테이너의 현재 상태를 나타내는 불변 객체
     */
    public static class ContainerStats {
        // final: 생성 후 변경 불가능한 필드들
        private final int servletCount;                                    // 등록된 서블릿 수
        private final EventLoopProcessor.ProcessorStats processorStats;    // 프로세서 통계
        private final boolean running;                                     // 실행 상태

        /**
         * 생성자 - 모든 필드를 한번에 초기화
         *
         * @param servletCount 서블릿 수
         * @param processorStats 프로세서 통계
         * @param running 실행 상태
         */
        public ContainerStats(int servletCount, EventLoopProcessor.ProcessorStats processorStats, boolean running) {
            this.servletCount = servletCount;
            this.processorStats = processorStats;
            this.running = running;
        }

        // === Getter 메서드들 ===

        /**
         * 등록된 서블릿 수 반환
         */
        public int getServletCount() {
            return servletCount;
        }

        /**
         * 프로세서 통계 반환
         */
        public EventLoopProcessor.ProcessorStats getProcessorStats() {
            return processorStats;
        }

        /**
         * 실행 상태 반환
         */
        public boolean isRunning() {
            return running;
        }

        /**
         * 통계 정보의 문자열 표현
         *
         * @return 포맷된 통계 정보 문자열
         */
        @Override
        public String toString() {
            // String.format(): printf 스타일의 문자열 포맷팅
            return String.format("ContainerStats{servlets=%d, running=%s, %s}",
                    servletCount, running, processorStats);
        }
    }
}