package com.serverarch.traditional.routing; // 패키지 선언 - 라우팅 시스템 패키지

// import 선언부
import com.serverarch.common.http.HttpStatus;
import com.serverarch.traditional.*; // HttpRequest, HttpResponse 클래스들
import java.util.*; // List, ArrayList 등 컬렉션 클래스들
import java.util.logging.*; // Logger 로깅 클래스들
import java.util.stream.Collectors;

/**
 * 미들웨어 체인 실행 시스템
 *
 * 기능:
 * 1. 글로벌 미들웨어와 라우트별 미들웨어를 순차 실행
 * 2. 체인 패턴으로 각 미들웨어가 다음 미들웨어 또는 핸들러 호출
 * 3. 예외 처리 및 에러 복구
 * 4. 실행 시간 측정 및 로깅
 *
 * 미들웨어 실행 순서:
 * 1. 글로벌 미들웨어들 (등록 순서대로)
 * 2. 라우트별 미들웨어들 (등록 순서대로)
 * 3. 최종 라우트 핸들러
 */
public class MiddlewareChain { // public 클래스 선언

    // 로거 - 미들웨어 실행 추적용
    private static final Logger logger = Logger.getLogger(MiddlewareChain.class.getName()); // static final 로거

    // 미들웨어 리스트 - 실행할 모든 미들웨어들
    private final List<Middleware> middlewares; // final List - 미들웨어 목록
    private final RouteHandler finalHandler; // 최종 라우트 핸들러
    private int currentIndex = 0; // 현재 실행 중인 미들웨어 인덱스

    /**
     * 미들웨어 체인 생성자
     * @param globalMiddlewares 글로벌 미들웨어들
     * @param routeMiddlewares 라우트별 미들웨어들
     * @param finalHandler 최종 핸들러
     */
    public MiddlewareChain(List<Middleware> globalMiddlewares,
                           List<Middleware> routeMiddlewares,
                           RouteHandler finalHandler) {
        // 입력 검증
        if (finalHandler == null) {
            throw new IllegalArgumentException("최종 핸들러는 필수입니다"); // 최종 핸들러 null 체크
        }

        this.finalHandler = finalHandler; // 최종 핸들러 설정
        this.middlewares = new ArrayList<>(); // 미들웨어 리스트 초기화

        // 글로벌 미들웨어들을 먼저 추가
        if (globalMiddlewares != null) {
            // Stream을 사용한 null 필터링 및 추가
            globalMiddlewares.stream() // Stream 생성
                    .filter(Objects::nonNull) // null이 아닌 미들웨어만 필터링, 메서드 참조 사용
                    .forEach(this.middlewares::add); // forEach로 각 미들웨어를 리스트에 추가, 메서드 참조 사용
        }

        // 라우트별 미들웨어들을 나중에 추가
        if (routeMiddlewares != null) {
            routeMiddlewares.stream() // 라우트 미들웨어 스트림 생성
                    .filter(Objects::nonNull) // null 필터링
                    .forEach(this.middlewares::add); // 리스트에 추가
        }
    }

    /**
     * 미들웨어 체인 실행
     * 모든 미들웨어를 순차적으로 실행한 후 최종 핸들러 호출
     * @param request HTTP 요청
     * @return HTTP 응답
     */
    public HttpResponse execute(HttpRequest request) {
        long startTime = System.currentTimeMillis(); // 실행 시작 시간 기록

        try {
            // 체인 실행 시작
            HttpResponse response = processNext(request); // 첫 번째 미들웨어부터 실행

            // 실행 시간 로깅
            long executionTime = System.currentTimeMillis() - startTime; // 실행 시간 계산
            logger.fine(String.format("미들웨어 체인 실행 완료: %dms (미들웨어: %d개)",
                    executionTime, middlewares.size())); // 실행 시간과 미들웨어 개수 로그

            return response; // 최종 응답 반환

        } catch (Exception e) {
            // 예외 발생 시 로깅 및 에러 응답 생성
            logger.log(Level.SEVERE, "미들웨어 체인 실행 중 오류 발생", e); // 심각한 오류 레벨로 로그
            return createErrorResponse(e); // 에러 응답 생성
        }
    }

    /**
     * 다음 미들웨어 또는 핸들러를 실행
     * 미들웨어에서 chain.processNext()를 호출할 때 실행됨
     * @param request HTTP 요청
     * @return HTTP 응답
     * @throws Exception 미들웨어나 핸들러 실행 중 예외
     */
    public HttpResponse processNext(HttpRequest request) throws Exception {
        // 모든 미들웨어를 거쳤으면 최종 핸들러 실행
        if (currentIndex >= middlewares.size()) {
            logger.fine("모든 미들웨어 실행 완료, 최종 핸들러 호출"); // 최종 핸들러 호출 로그
            return finalHandler.handle(request); // 최종 핸들러 실행
        }

        // 현재 미들웨어 가져오기
        Middleware currentMiddleware = middlewares.get(currentIndex); // 현재 인덱스의 미들웨어 조회
        currentIndex++; // 인덱스 증가 (다음 미들웨어 준비)

        // 현재 미들웨어 실행
        logger.fine(String.format("미들웨어 실행: %s (%d/%d)",
                currentMiddleware.getClass().getSimpleName(), currentIndex, middlewares.size())); // 미들웨어 실행 로그

        return currentMiddleware.process(request, this); // 미들웨어 실행 (자기 자신을 체인으로 전달)
    }

    /**
     * 예외 발생 시 에러 응답 생성
     * @param exception 발생한 예외
     * @return 에러 응답
     */
    private HttpResponse createErrorResponse(Exception exception) {
        // 예외 타입에 따른 적절한 에러 응답 생성
        if (exception instanceof IllegalArgumentException) {
            // 잘못된 인수 예외 -> 400 Bad Request
            return HttpResponse.badRequest("잘못된 요청: " + exception.getMessage()); // 400 에러 응답
        } else if (exception instanceof SecurityException) {
            // 보안 예외 -> 403 Forbidden
            HttpResponse response = new HttpResponse(HttpStatus.FORBIDDEN); // 403 상태 코드
            response.setBody("접근이 거부되었습니다: " + exception.getMessage()); // 거부 메시지
            return response;
        } else {
            // 기타 예외 -> 500 Internal Server Error
            return HttpResponse.serverError("서버 내부 오류: " + exception.getMessage()); // 500 에러 응답
        }
    }

    // ========== 체인 정보 조회 메서드들 ==========

    /**
     * 등록된 미들웨어 개수 반환
     * @return 미들웨어 개수
     */
    public int getMiddlewareCount() {
        return middlewares.size(); // 리스트 크기 반환
    }

    /**
     * 현재 실행 중인 미들웨어 인덱스 반환
     * @return 현재 인덱스
     */
    public int getCurrentIndex() {
        return currentIndex; // 현재 인덱스 반환
    }

    /**
     * 남은 미들웨어 개수 반환
     * @return 남은 미들웨어 개수
     */
    public int getRemainingCount() {
        return Math.max(0, middlewares.size() - currentIndex); // 남은 개수 계산 (음수 방지)
    }

    /**
     * 체인 실행 상태 확인
     * @return 완료 여부
     */
    public boolean isCompleted() {
        return currentIndex > middlewares.size(); // 모든 미들웨어와 핸들러 실행 완료 여부
    }

    /**
     * 등록된 모든 미들웨어 클래스 이름 반환
     * 디버깅용
     * @return 미들웨어 클래스 이름 리스트
     */
    public List<String> getMiddlewareNames() {
        return middlewares.stream() // 미들웨어 스트림 생성
                .map(middleware -> middleware.getClass().getSimpleName()) // 각 미들웨어의 클래스 이름 추출
                .collect(Collectors.toList()); // 리스트로 수집
    }
}