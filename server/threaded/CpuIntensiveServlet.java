package server.threaded;

import server.core.mini.*;
import server.core.http.*;

/**
 * CPU 집약적 작업 서블릿
 *
 * 이 서블릿은 CPU 집약적인 연산을 시뮬레이션하여 서버의 부하 테스트를 위해 사용됩니다.
 * 수학적 연산(제곱근, 삼각함수)을 반복 수행하여 CPU 사용률을 높입니다.
 *
 * 사용 목적:
 * 1. 스레드풀의 CPU 집약적 작업 처리 성능 테스트
 * 2. 멀티스레드 환경에서의 CPU 부하 분산 확인
 * 3. 서버의 최대 처리 능력 벤치마킹
 *
 * MiniServlet을 상속받아 서블릿 라이프사이클을 따릅니다.
 */
public class CpuIntensiveServlet extends MiniServlet {

    /**
     * HTTP GET 요청 처리 메서드
     *
     * MiniServlet의 추상 메서드를 구현합니다.
     * doGet() 메서드는 HTTP GET 요청이 들어올 때 자동으로 호출됩니다.
     *
     * @param request MiniRequest 객체 - HTTP 요청 정보를 캡슐화
     * @param response MiniResponse 객체 - HTTP 응답을 생성하기 위한 객체
     * @throws Exception 처리 중 발생할 수 있는 모든 예외
     */
    @Override
    protected void doGet(MiniRequest request, MiniResponse response) throws Exception {
        /*
         * CPU 집약적 작업 시뮬레이션
         *
         * 복잡한 수학 연산을 100,000회 반복하여 CPU 사용률을 높입니다.
         * 실제 서비스에서는 다음과 같은 작업들이 CPU 집약적입니다:
         * - 암호화/복호화 작업
         * - 이미지/비디오 처리
         * - 복잡한 데이터 분석
         * - 머신러닝 연산
         */
        double result = 0;  // 연산 결과를 저장할 변수

        /*
         * for 루프: 100,000회 반복
         * 이 반복 횟수는 적절한 CPU 부하를 만들기 위해 조정된 값입니다.
         * 너무 적으면 부하가 미미하고, 너무 많으면 응답시간이 과도하게 길어집니다.
         */
        for (int i = 0; i < 100000; i++) {
            /*
             * 복합 수학 연산 수행
             *
             * Math.sqrt(i): i의 제곱근 계산
             * - sqrt는 Square Root의 줄임말
             * - 부동소수점 연산으로 CPU 사용률을 높임
             * - 반환 타입: double
             *
             * Math.sin(i): i의 사인 값 계산 (라디안 단위)
             * - 삼각함수 연산은 CPU 집약적인 작업
             * - 내부적으로 테일러 급수나 CORDIC 알고리즘 사용
             * - 반환 타입: double
             *
             * 곱셈(*) 연산: 두 부동소수점 수의 곱셈
             * 덧셈(+=) 연산: result에 계산 결과를 누적
             */
            result += Math.sqrt(i) * Math.sin(i);
        }

        /*
         * JSON 응답 생성 및 전송
         *
         * String.format(): C 스타일의 포맷 문자열 사용
         * - %.2f: 소수점 둘째자리까지 표시하는 부동소수점 포맷
         * - %s: 문자열 포맷
         *
         * Thread.currentThread(): 현재 실행 중인 스레드 객체 반환
         * getName(): 스레드의 이름을 반환
         * - 멀티스레드 환경에서 어떤 스레드가 요청을 처리했는지 확인
         * - 디버깅과 모니터링에 유용
         *
         * response.sendJson(): JSON 형태의 응답을 클라이언트에게 전송
         * - Content-Type을 "application/json"으로 자동 설정
         * - JSON 문자열을 HTTP 응답 본문에 작성
         */
        response.sendJson(String.format(
                "{ \"server\": \"threaded\", \"result\": %.2f, \"thread\": \"%s\" }",
                result,                              // 계산 결과 (소수점 2자리)
                Thread.currentThread().getName()    // 현재 스레드 이름
        ));

        /*
         * 응답 JSON 구조 설명:
         * {
         *   "server": "threaded",        // 서버 타입 식별
         *   "result": 12345.67,          // CPU 집약적 연산의 결과값
         *   "thread": "Thread-Pool-1"    // 요청을 처리한 스레드 이름
         * }
         *
         * 이 정보를 통해 클라이언트는:
         * 1. 연산이 정상적으로 완료되었는지 확인
         * 2. 어떤 스레드가 처리했는지 모니터링
         * 3. 부하 테스트 시 응답 패턴 분석 가능
         */
    }

    /*
     * 참고사항:
     *
     * 1. CPU 집약적 작업의 특징:
     *    - I/O 대기 시간이 거의 없음
     *    - CPU 코어를 100% 활용
     *    - 스레드가 블로킹되지 않고 계속 실행
     *
     * 2. 이 서블릿의 성능 특성:
     *    - 처리 시간이 CPU 성능에 비례
     *    - 동시 요청 수가 많을수록 CPU 경합 발생
     *    - 스레드풀 크기가 성능에 직접적 영향
     *
     * 3. 벤치마킹 시 고려사항:
     *    - CPU 코어 수와 스레드풀 크기의 관계
     *    - 컨텍스트 스위칭 오버헤드
     *    - GC(Garbage Collection) 압박
     */
}