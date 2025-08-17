package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * CPU 집약적 작업 비동기 서블릿
 * 수학 연산을 통한 CPU 부하 테스트용
 * Threaded 서버의 CpuIntensiveServlet과 동일한 기능을 비동기로 처리
 */
public class CpuIntensiveAsyncServlet extends MiniAsyncServlet {
    // MiniAsyncServlet을 상속받아 비동기 서블릿 기능 확장
    // 상속을 통해 기본 서블릿 생명주기와 비동기 처리 인프라 활용

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        // GET 요청 처리를 위한 비동기 메서드 오버라이드
        // CompletableFuture<Void> 반환으로 비동기 작업 완료 시점 제어

        return CompletableFuture.runAsync(() -> {
            // runAsync()로 별도 스레드에서 CPU 집약적 작업 실행
            // 람다식 내부에서 실제 계산 로직 수행

            // 작업 시작 시간 기록 - 성능 측정용
            long startTime = System.currentTimeMillis();

            // CPU 집약적 작업 시뮬레이션을 위한 변수들
            double result = 0; // 계산 결과 누적 변수
            int iterations = 100000; // 기본 반복 횟수 10만회
            // 적절한 CPU 부하를 위한 기본값 설정

            // 쿼리 파라미터로 반복 횟수 조정 가능
            String iterParam = request.getParameter("iterations");
            // HTTP 요청의 쿼리 파라미터에서 사용자 정의 반복 횟수 추출
            if (iterParam != null) {
                try {
                    // 문자열을 정수로 파싱하여 반복 횟수 설정
                    iterations = Integer.parseInt(iterParam);
                    // 최대 100만번으로 제한하여 시스템 보호
                    iterations = Math.min(iterations, 1000000);
                    // Math.min()으로 상한선 적용
                } catch (NumberFormatException e) {
                    // 잘못된 숫자 형식인 경우 기본값 사용
                    // 예외 처리로 시스템 안정성 확보
                }
            }

            // 복잡한 수학 연산 수행 - 실제 CPU 집약적 작업 시뮬레이션
            for (int i = 0; i < iterations; i++) {
                // 다양한 수학 함수 조합으로 CPU 사용률 증가
                result += Math.sqrt(i) * Math.sin(i) * Math.cos(i % 100);
                // Math.sqrt() - 제곱근 계산 (부동소수점 연산)
                // Math.sin(), Math.cos() - 삼각함수 계산 (복잡한 수학 연산)
                // 모듈로 연산(%)으로 주기적 패턴 생성

                // 추가적인 연산으로 더 높은 CPU 부하 생성
                if (i % 1000 == 0) {
                    // 1000번마다 한 번씩 거듭제곱 연산 추가
                    result += Math.pow(i, 0.1);
                    // Math.pow()로 지수 연산 수행 (CPU 집약적)
                }
            }

            // 작업 완료 시간 계산
            long duration = System.currentTimeMillis() - startTime;
            // 시작 시간부터 현재까지의 경과 시간 계산

            // JSON 형태의 응답 데이터 생성
            String resultJson = String.format(
                    "{ \"computation\": %.4f, \"duration\": %d, " +
                            "\"iterations\": %d, \"thread\": \"%s\", \"server\": \"HybridServer\", " +
                            "\"processing\": \"async\", \"timestamp\": %d }",
                    result, duration, iterations, Thread.currentThread().getName(),
                    System.currentTimeMillis()
            );
            // String.format()으로 구조화된 JSON 응답 생성
            // %.4f - 소수점 4자리까지 표시하는 부동소수점 포맷
            // %d - 정수 포맷
            // %s - 문자열 포맷
            // Thread.currentThread().getName() - 현재 실행 스레드 이름 포함
            // 서버 타입과 처리 방식을 명시하여 클라이언트에서 구분 가능

            // JSON 응답 전송
            response.sendJson(resultJson);
            // MiniResponse의 sendJson() 메서드로 JSON 응답 전송
            // Content-Type과 적절한 HTTP 헤더 자동 설정
        });
    }

    @Override
    protected CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        // POST 요청 처리를 위한 비동기 메서드 오버라이드
        // GET보다 더 복잡한 설정과 높은 CPU 부하 지원

        return CompletableFuture.runAsync(() -> {
            try {
                // 작업 시작 시간 기록
                long startTime = System.currentTimeMillis();

                // POST 바디에서 설정 읽기를 위한 변수 초기화
                String body = request.getBody(); // HTTP 요청 바디 내용 추출
                int iterations = 50000; // POST 기본값 (GET보다 낮음)
                String complexity = "normal"; // 기본 복잡도 설정

                // 요청 바디가 존재하는 경우 파싱 수행
                if (body != null && !body.trim().isEmpty()) {
                    // null 체크와 빈 문자열 체크로 안전한 파싱

                    // 간단한 JSON 파싱 (실제로는 JSON 라이브러리 사용 권장)
                    if (body.contains("iterations")) {
                        // 반복 횟수 설정이 포함된 경우
                        try {
                            // 문자열 분할과 정규식으로 숫자 추출
                            String[] parts = body.split("iterations");
                            if (parts.length > 1) {
                                // 숫자가 아닌 문자 제거하여 순수 숫자만 추출
                                String num = parts[1].replaceAll("[^0-9]", "");
                                // 정규식 [^0-9]로 숫자가 아닌 모든 문자 제거
                                if (!num.isEmpty()) {
                                    // 최대 100만번으로 제한하여 시스템 보호
                                    iterations = Math.min(Integer.parseInt(num), 1000000);
                                }
                            }
                        } catch (Exception e) {
                            // 파싱 실패시 기본값 사용 (예외 무시)
                        }
                    }

                    // 복잡도 설정 확인
                    if (body.contains("high")) {
                        complexity = "high"; // 고강도 모드 설정
                        iterations *= 2; // 고강도시 반복 횟수 2배 증가
                    }
                }

                // 설정된 복잡도에 따른 CPU 작업 수행
                double result = 0; // 계산 결과 누적 변수
                for (int i = 0; i < iterations; i++) {
                    if ("high".equals(complexity)) {
                        // 고강도 연산 - 더 많은 수학 함수 사용
                        result += Math.sqrt(i) * Math.sin(i) * Math.cos(i) * Math.tan(i % 50 + 1);
                        // Math.tan() 추가로 더 복잡한 삼각함수 연산
                        // (i % 50 + 1)로 0으로 나눗셈 방지
                        result += Math.log(i + 1) * Math.exp(i % 10 * 0.01);
                        // Math.log() - 자연로그 계산
                        // Math.exp() - 지수함수 계산
                        // 매우 CPU 집약적인 연산 조합
                    } else {
                        // 일반 연산 - 기본적인 수학 함수만 사용
                        result += Math.sqrt(i) * Math.sin(i);
                        // GET과 유사하지만 더 단순한 연산
                    }
                }

                // 작업 완료 시간 계산
                long duration = System.currentTimeMillis() - startTime;

                // POST용 JSON 응답 생성 - 더 상세한 정보 포함
                String resultJson = String.format(
                        "{ \"computation\": %.4f, \"duration\": %d, " +
                                "\"iterations\": %d, \"complexity\": \"%s\", " +
                                "\"thread\": \"%s\", \"server\": \"HybridServer\", " +
                                "\"processing\": \"async\", \"method\": \"POST\" }",
                        result, duration, iterations, complexity,
                        Thread.currentThread().getName()
                );
                // POST 특화 정보 추가:
                // - complexity: 사용된 복잡도 레벨
                // - method: HTTP 메서드 명시

                // JSON 응답 전송
                response.sendJson(resultJson);

            } catch (Exception e) {
                // POST 처리 중 예외 발생시 에러 응답 전송
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR,
                        "CPU intensive processing failed: " + e.getMessage());
                // HttpStatus.INTERNAL_SERVER_ERROR - HTTP 500 상태 코드
                // 에러 메시지와 함께 클라이언트에게 실패 원인 전달
            }
        });
    }
}