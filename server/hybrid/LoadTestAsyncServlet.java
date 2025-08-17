package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * 로드 테스트 비동기 서블릿
 * CPU 집약적 작업과 I/O 작업을 비동기로 처리하여 성능 측정
 * Threaded 서버와의 성능 비교용
 */
public class LoadTestAsyncServlet extends MiniAsyncServlet {
    // MiniAsyncServlet을 상속받아 로드 테스트용 비동기 서블릿 구현
    // 성능 비교를 위해 CPU 집약적 작업과 I/O 작업을 조합하여 실제적인 부하 생성

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        // GET 요청 처리를 위한 비동기 메서드 오버라이드
        // 표준화된 로드 테스트를 위한 고정된 부하 패턴 제공

        return CompletableFuture.runAsync(() -> {
            // runAsync()로 별도 스레드에서 로드 테스트 실행
            // CPU 집약적 작업이므로 메인 스레드 블로킹 방지 필수

            try {
                // 성능 측정을 위한 시작 시간 기록
                long start = System.currentTimeMillis();
                // 나노초가 아닌 밀리초 사용으로 일반적인 성능 측정에 적합

                // CPU 집약적 작업 시뮬레이션 (비동기로 처리)
                double result = 0;
                for (int i = 0; i < 100000; i++) {
                    // 100,000번 반복으로 적당한 CPU 부하 생성
                    result += Math.sqrt(i) * Math.sin(i);
                    // Math.sqrt() 사용 이유:
                    // 1. 부동소수점 연산으로 CPU 집약적
                    // 2. 제곱근 계산은 비교적 무거운 수학 연산
                    // Math.sin() 사용 이유:
                    // 1. 삼각함수 계산으로 복잡한 수학 연산
                    // 2. CPU 집약적 작업의 대표적인 예시
                    // 두 연산의 조합으로 실제적인 CPU 부하 시뮬레이션
                }

                // 추가적인 비동기 I/O 시뮬레이션
                Thread.sleep(50);
                // Thread.sleep() 사용 이유:
                // 1. I/O 대기 시간 시뮬레이션 (50ms)
                // 2. 실제 데이터베이스나 API 호출 지연 모방
                // 3. CPU 작업과 I/O 작업의 혼합으로 현실적인 부하 패턴

                // 총 처리 시간 계산
                long duration = System.currentTimeMillis() - start;

                // JSON 형태의 로드 테스트 결과 생성
                String resultJson = String.format(
                        "{ \"computation\": %.2f, \"duration\": %d, " +
                                "\"thread\": \"%s\", \"server\": \"HybridServer\", " +
                                "\"processing\": \"async\", \"iterations\": 100000 }",
                        result, duration, Thread.currentThread().getName()
                );
                // 포함 정보:
                // - computation: 계산 결과값 (소수점 2자리)
                // - duration: 처리 시간 (밀리초)
                // - thread: 처리 스레드 이름 (비동기 처리 확인용)
                // - server: 서버 타입 (성능 비교용)
                // - processing: 처리 방식 (async/sync 구분)
                // - iterations: 반복 횟수 (부하 강도 표시)

                // JSON 응답 전송
                response.sendJson(resultJson);
                // 클라이언트에서 파싱하기 쉬운 구조화된 데이터 제공

            } catch (InterruptedException e) {
                // Thread.sleep() 인터럽트 예외 처리
                Thread.currentThread().interrupt();
                // interrupt() 호출 이유:
                // 1. 인터럽트 상태 복원으로 상위 호출자에게 상황 전달
                // 2. 비동기 처리 중단시 정상적인 스레드 풀 관리
                // 3. InterruptedException은 인터럽트 상태를 지우므로 명시적 복원

                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "Load test interrupted");
                // 로드 테스트 중단시 적절한 에러 응답 제공
            }
        });
    }

    @Override
    protected CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        // POST 요청 처리를 위한 비동기 메서드 오버라이드
        // 가변적인 부하 강도를 지원하는 동적 로드 테스트

        return CompletableFuture.runAsync(() -> {
            try {
                // 요청 파라미터에서 부하 강도 추출
                String intensity = request.getParameter("intensity");
                // getParameter()로 쿼리 파라미터 또는 폼 데이터에서 값 추출

                int iterations = intensity != null ? Integer.parseInt(intensity) : 50000;
                // 삼항 연산자와 Integer.parseInt() 사용:
                // 1. intensity 파라미터가 있으면 해당 값으로 반복 횟수 설정
                // 2. 없으면 기본값 50,000회 사용 (GET보다 낮은 기본값)
                // 3. 사용자가 로드 테스트 강도를 동적으로 조절 가능

                // 성능 측정 시작
                long start = System.currentTimeMillis();

                // 가변적인 부하 테스트 - 더 복잡한 수학 연산
                double result = 0;
                for (int i = 0; i < iterations; i++) {
                    result += Math.sqrt(i) * Math.cos(i) * Math.tan(i % 100 + 1);
                    // Math.cos() 추가:
                    // 1. 코사인 함수로 추가적인 삼각함수 연산
                    // 2. Math.sin()과 함께 사용하여 더 복잡한 계산
                    // Math.tan() 추가:
                    // 1. 탄젠트 함수로 가장 무거운 삼각함수 연산
                    // 2. (i % 100 + 1) 사용으로 0으로 나눗셈 방지
                    // 3. 주기적 패턴으로 다양한 연산값 생성
                    // 세 함수의 조합으로 GET보다 높은 CPU 부하 생성
                }

                // 비동기 I/O 시뮬레이션 (GET보다 긴 대기 시간)
                Thread.sleep(100);
                // 100ms 대기로 더 무거운 I/O 작업 시뮬레이션
                // POST 요청이 일반적으로 더 복잡한 처리를 수반하므로 더 긴 대기 시간

                // 총 처리 시간 계산
                long duration = System.currentTimeMillis() - start;

                // 상세한 JSON 응답 생성 (POST는 더 많은 정보 포함)
                String resultJson = String.format(
                        "{ \"computation\": %.2f, \"duration\": %d, " +
                                "\"thread\": \"%s\", \"server\": \"HybridServer\", " +
                                "\"processing\": \"async\", \"iterations\": %d, \"intensity\": \"%s\" }",
                        result, duration, Thread.currentThread().getName(), iterations, intensity
                );
                // POST 특화 정보 추가:
                // - iterations: 실제 실행된 반복 횟수
                // - intensity: 요청된 강도 파라미터 (null 가능)
                // 클라이언트에서 요청한 강도와 실제 실행 결과 비교 가능

                // JSON 응답 전송
                response.sendJson(resultJson);

            } catch (InterruptedException e) {
                // I/O 시뮬레이션 중 인터럽트 처리
                Thread.currentThread().interrupt();
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "Load test interrupted");

            } catch (NumberFormatException e) {
                // intensity 파라미터 파싱 실패 처리
                response.sendError(HttpStatus.BAD_REQUEST, "Invalid intensity parameter");
                // NumberFormatException 처리 이유:
                // 1. Integer.parseInt() 실패시 발생
                // 2. 잘못된 숫자 형식의 intensity 파라미터 전달시
                // 3. HTTP 400 Bad Request로 클라이언트 오류임을 명시
                // 4. 사용자에게 올바른 파라미터 형식 요청 유도
            }
        });
    }
}