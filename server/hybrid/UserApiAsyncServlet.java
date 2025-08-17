package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * User API 비동기 서블릿
 * RESTful API를 비동기로 처리하여 높은 처리량 달성
 */
public class UserApiAsyncServlet extends MiniAsyncServlet {
    // MiniAsyncServlet을 상속받아 사용자 관리 API를 비동기로 구현
    // RESTful 설계 원칙에 따라 HTTP 메서드별로 다른 기능 제공

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        // GET 요청 처리를 위한 비동기 메서드 오버라이드
        // RESTful API에서 GET은 리소스 조회 기능을 담당

        return CompletableFuture.runAsync(() -> {
            // runAsync()로 별도 스레드에서 API 처리
            // 데이터베이스 조회 등의 I/O 작업을 비동기로 처리하여 스레드 효율성 확보

            try {
                // 비동기 DB 조회 시뮬레이션
                Thread.sleep(100);
                // Thread.sleep() 사용 이유:
                // 1. 실제 데이터베이스 쿼리 실행 시간 시뮬레이션 (100ms)
                // 2. 네트워크 지연, 디스크 I/O, 인덱스 검색 등의 지연 모방
                // 3. 비동기 처리의 효과를 명확히 보여주기 위한 의도적 지연
                // 4. 실제 프로덕션 환경의 DB 응답 시간과 유사한 수준

                // 쿼리 파라미터에서 사용자 ID 추출
                String userId = request.getParameter("id");
                // getParameter() 사용으로 URL 쿼리 파라미터 접근
                // 예: /api/users?id=123 형태의 요청에서 "123" 추출

                if (userId != null) {
                    // 특정 사용자 조회 - 개별 리소스 접근
                    String userJson = String.format(
                            "{ \"id\": \"%s\", \"name\": \"User %s\", \"email\": \"user%s@example.com\", " +
                                    "\"thread\": \"%s\", \"server\": \"HybridServer\", \"processing\": \"async\" }",
                            userId, userId, userId, Thread.currentThread().getName()
                    );
                    // JSON 응답 구조:
                    // - id: 요청된 사용자 ID (문자열)
                    // - name: 동적으로 생성된 사용자 이름
                    // - email: ID 기반으로 생성된 이메일 주소
                    // - thread: 처리 스레드 이름 (비동기 처리 확인용)
                    // - server: 서버 타입 (로드 밸런싱 환경에서 구분)
                    // - processing: 처리 방식 (async/sync 성능 비교용)

                    response.sendJson(userJson);
                    // sendJson()으로 Content-Type: application/json 헤더와 함께 전송

                } else {
                    // 전체 사용자 목록 조회 - 컬렉션 리소스 접근
                    String usersJson = String.format(
                            "{ \"users\": [" +
                                    "  { \"id\": \"1\", \"name\": \"Alice\" }," +
                                    "  { \"id\": \"2\", \"name\": \"Bob\" }," +
                                    "  { \"id\": \"3\", \"name\": \"Charlie\" }" +
                                    "], \"thread\": \"%s\", \"server\": \"HybridServer\", \"processing\": \"async\" }",
                            Thread.currentThread().getName()
                    );
                    // 사용자 목록 JSON 구조:
                    // - users: 사용자 객체들의 배열
                    // - 각 사용자는 id와 name 속성 포함
                    // - 메타데이터로 처리 정보 포함
                    // 실제 환경에서는 페이징, 필터링, 정렬 등의 기능 추가 필요

                    response.sendJson(usersJson);
                }

                // RESTful API 설계 원칙 적용:
                // 1. 쿼리 파라미터 유무에 따른 기능 분기
                // 2. 개별 리소스 vs 컬렉션 리소스 구분
                // 3. 일관된 JSON 응답 형식
                // 4. HTTP 상태 코드는 기본값(200 OK) 사용

            } catch (InterruptedException e) {
                // DB 조회 시뮬레이션 중 인터럽트 예외 처리
                Thread.currentThread().interrupt();
                // interrupt() 호출로 인터럽트 상태 복원
                // 비동기 작업 취소시 정상적인 스레드 풀 관리 지원

                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "Async operation interrupted");
                // 비동기 작업 중단시 적절한 에러 응답
                // "Async operation interrupted" 메시지로 중단 원인 명시
            }
        });
    }

    @Override
    protected CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        // POST 요청 처리를 위한 비동기 메서드 오버라이드
        // RESTful API에서 POST는 새로운 리소스 생성 기능을 담당

        return CompletableFuture.runAsync(() -> {
            try {
                // 비동기 사용자 생성 시뮬레이션
                Thread.sleep(200);
                // Thread.sleep() 200ms 사용 이유:
                // 1. 데이터베이스 INSERT 작업은 SELECT보다 일반적으로 더 오래 걸림
                // 2. 트랜잭션 처리, 인덱스 업데이트, 제약조건 검증 등의 추가 시간
                // 3. GET 요청(100ms)보다 긴 처리 시간으로 현실적인 시뮬레이션
                // 4. 유효성 검증, 비즈니스 로직 처리 등의 시간 포함

                // 요청 바디에서 사용자 데이터 추출
                String requestBody = request.getBody();
                // getBody() 사용 이유:
                // 1. POST 요청의 HTTP 바디에서 JSON 데이터 추출
                // 2. 클라이언트가 전송한 새로운 사용자 정보 획득
                // 3. Content-Type: application/json 형태의 요청 처리
                // 실제 환경에서는 JSON 파싱과 유효성 검증 필요

                // 사용자 생성 결과 JSON 응답 생성
                String resultJson = String.format(
                        "{ \"status\": \"created\", \"data\": %s, " +
                                "\"thread\": \"%s\", \"server\": \"HybridServer\", " +
                                "\"processing\": \"async\", \"timestamp\": %d }",
                        requestBody != null ? requestBody : "{}",
                        // 삼항 연산자로 null 체크 및 기본값 설정
                        // 요청 바디가 없으면 빈 JSON 객체 "{}" 사용
                        Thread.currentThread().getName(),
                        System.currentTimeMillis()
                        // 생성 시각을 타임스탬프로 기록
                );
                // POST 응답 JSON 구조:
                // - status: 작업 결과 상태 ("created")
                // - data: 클라이언트가 전송한 원본 데이터 (에코백)
                // - thread: 처리 스레드 정보
                // - server: 서버 식별 정보
                // - processing: 처리 방식
                // - timestamp: 생성 시각 (에포크 시간)

                // HTTP 201 Created 상태 코드 설정
                response.setStatus(HttpStatus.CREATED);
                // HttpStatus.CREATED 사용 이유:
                // 1. RESTful API에서 새로운 리소스 생성시 표준 응답
                // 2. HTTP 201 상태 코드로 성공적인 생성 작업임을 명시
                // 3. 200 OK와 구분하여 더 구체적인 의미 전달
                // 4. 클라이언트에서 생성 완료를 명확히 인식 가능

                // JSON 응답 전송
                response.sendJson(resultJson);

            } catch (InterruptedException e) {
                // 사용자 생성 시뮬레이션 중 인터럽트 예외 처리
                Thread.currentThread().interrupt();
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "User creation failed");
                // 사용자 생성 실패시 구체적인 에러 메시지 제공
                // "User creation failed"로 어떤 작업이 실패했는지 명시
            }
        });
    }

    // PUT, DELETE 등의 메서드를 구현하지 않는 이유:
    // 1. 예제의 간소화를 위해 핵심 기능(조회, 생성)만 구현
    // 2. 실제 환경에서는 다음과 같이 확장 가능:
    //    - PUT: 사용자 정보 전체 업데이트
    //    - PATCH: 사용자 정보 부분 업데이트
    //    - DELETE: 사용자 삭제
    // 3. 부모 클래스의 기본 구현으로 405 Method Not Allowed 응답

    // 이 API 설계의 특징:
    // 1. RESTful 원칙 준수 (HTTP 메서드별 의미 구분)
    // 2. 비동기 처리로 높은 동시성 지원
    // 3. JSON 기반 통신으로 현대적 API 형태
    // 4. 적절한 HTTP 상태 코드 사용
    // 5. 에러 처리와 디버깅 정보 포함
}