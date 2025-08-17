package server.threaded;

import server.core.mini.*;
import server.core.http.*;

/**
 * I/O 시뮬레이션 서블릿
 *
 * 이 서블릿은 I/O 집약적인 작업을 시뮬레이션합니다.
 * 실제 파일 읽기, 데이터베이스 조회, 네트워크 호출 등의 블로킹 I/O 작업을
 * Thread.sleep()으로 모방하여 서버의 I/O 처리 성능을 테스트합니다.
 *
 * I/O 집약적 작업의 특징:
 * - CPU 사용률은 낮지만 대기 시간이 긺
 * - 스레드가 블로킹되어 다른 작업을 수행할 수 없음
 * - 동시 처리 능력이 스레드풀 크기에 직접적으로 의존
 * - 스레드 컨텍스트 스위칭 오버헤드 발생
 *
 * 실제 I/O 작업 예시:
 * - 데이터베이스 쿼리 (SELECT, INSERT, UPDATE)
 * - 파일 시스템 읽기/쓰기 (로그 파일, 설정 파일)
 * - 외부 API 호출 (REST API, SOAP 웹서비스)
 * - 네트워크 소켓 통신
 * - 캐시 서버 조회 (Redis, Memcached)
 *
 * 이 서블릿은 스레드 기반 서버의 I/O 처리 한계를 테스트하는 데 사용됩니다.
 */
public class IoSimulationServlet extends MiniServlet {

    /**
     * HTTP GET 요청 처리 메서드
     *
     * I/O 대기 시간을 시뮬레이션하고 완료 상태를 반환합니다.
     *
     * @param request MiniRequest 객체 - HTTP 요청 정보
     * @param response MiniResponse 객체 - HTTP 응답 생성용
     * @throws Exception 처리 중 발생할 수 있는 모든 예외
     */
    @Override
    protected void doGet(MiniRequest request, MiniResponse response) throws Exception {
        try {
            /*
             * I/O 작업 시뮬레이션 (파일 읽기, DB 조회 등)
             *
             * Thread.sleep(100): 현재 스레드를 100밀리초 동안 일시정지
             *
             * Thread.sleep() 메서드의 동작:
             * - 현재 스레드를 지정된 시간만큼 TIMED_WAITING 상태로 변경
             * - CPU를 다른 스레드에게 양보 (스케줄러가 다른 스레드 실행)
             * - 지정된 시간이 지나면 RUNNABLE 상태로 복귀
             * - 인터럽트가 발생하면 InterruptedException 던짐
             *
             * 100ms 선택 이유:
             * - 일반적인 데이터베이스 쿼리 응답 시간 (50-200ms)
             * - 파일 시스템 I/O 평균 지연시간
             * - 네트워크 API 호출의 최소 지연시간
             * - 테스트하기에 적당한 시간 (너무 길지도 짧지도 않음)
             *
             * 실제 I/O 작업들의 대기 시간:
             * - SSD 랜덤 읽기: 0.1-0.2ms
             * - HDD 랜덤 읽기: 5-10ms
             * - 로컬 네트워크 DB 쿼리: 1-10ms
             * - 원격 API 호출: 50-500ms
             * - 클라우드 DB 쿼리: 10-100ms
             */
            Thread.sleep(100);

        } catch (InterruptedException e) {
            /*
             * InterruptedException 처리
             *
             * InterruptedException 발생 상황:
             * - 다른 스레드가 현재 스레드에 interrupt() 호출
             * - 서버 종료 시 스레드풀이 작업 중인 스레드들을 정리
             * - 타임아웃이나 취소 요청으로 인한 강제 중단
             *
             * Thread.currentThread().interrupt():
             * - 현재 스레드의 인터럽트 상태를 다시 설정
             * - InterruptedException을 catch하면 인터럽트 상태가 클리어되므로
             * - 상위 호출자나 스레드풀이 인터럽트 상태를 알 수 있도록 복원
             * - 이는 자바의 인터럽트 처리 베스트 프랙티스
             *
             * 인터럽트 전파의 중요성:
             * - 스레드풀이 정상적으로 종료될 수 있도록 함
             * - 취소 가능한 작업의 협력적 중단 메커니즘
             * - 데드락이나 무한 대기 상황 방지
             */
            Thread.currentThread().interrupt();

            /*
             * InterruptedException 발생시 처리 전략:
             * 1. 인터럽트 상태 복원 (위에서 수행)
             * 2. 작업을 빠르게 종료
             * 3. 적절한 응답 반환 (선택적)
             * 4. 리소스 정리 (finally 블록에서)
             *
             * 여기서는 단순히 sleep이 중단된 것으로 처리하고
             * 정상적인 응답을 반환합니다.
             */
        }

        /*
         * I/O 작업 완료 후 결과 응답
         *
         * JSON 응답 구조:
         * {
         *   "server": "threaded",        // 서버 타입 식별
         *   "io": "completed",           // I/O 작업 완료 상태
         *   "thread": "ThreadPool-3"     // 처리한 스레드 이름
         * }
         */
        response.sendJson(String.format(
                "{ \"server\": \"threaded\", \"io\": \"completed\", \"thread\": \"%s\" }",

                /*
                 * Thread.currentThread().getName(): 현재 스레드 이름
                 *
                 * I/O 집약적 작업에서 스레드 정보의 중요성:
                 * 1. 스레드풀 활용도 모니터링
                 *    - 동시 I/O 작업 수 확인
                 *    - 스레드 재사용 패턴 분석
                 *
                 * 2. 성능 분석
                 *    - 특정 스레드의 부하 분산 상태
                 *    - 스레드 컨텍스트 스위칭 빈도
                 *
                 * 3. 디버깅 지원
                 *    - 요청 처리 경로 추적
                 *    - 병목 지점 식별
                 *
                 * 4. 용량 계획
                 *    - 적정 스레드풀 크기 결정
                 *    - I/O 대기시간 vs 스레드 수 관계 분석
                 */
                Thread.currentThread().getName()
        ));

        /*
         * 이 서블릿으로 테스트할 수 있는 시나리오:
         *
         * 1. 동시 I/O 요청 처리 능력:
         *    - 여러 클라이언트가 동시에 요청
         *    - 스레드풀이 모든 요청을 처리할 수 있는지 확인
         *    - 스레드 부족시 큐잉 동작 확인
         *
         * 2. 스레드풀 크기 최적화:
         *    - I/O 대기시간 vs 스레드 수의 관계
         *    - CPU 코어 수 대비 최적 스레드 수 찾기
         *    - 메모리 사용량과 처리량의 트레이드오프
         *
         * 3. 응답 시간 분석:
         *    - 평균 응답 시간 (I/O 시간 + 큐잉 시간)
         *    - 99th percentile 응답 시간
         *    - 최대 동시 처리 가능 요청 수
         *
         * 4. 리소스 사용량:
         *    - 메모리 사용량 (스레드당 스택 메모리)
         *    - 스레드 생성/소멸 오버헤드
         *    - 컨텍스트 스위칭 비용
         */
    }

    /*
     * I/O 집약적 작업의 성능 특성:
     *
     * 1. 스레드 모델의 한계:
     *    - 스레드당 메모리 오버헤드 (기본 1MB 스택)
     *    - 컨텍스트 스위칭 비용
     *    - OS 스레드 수 제한
     *
     * 2. 스케일링 특성:
     *    - 동시 연결 수가 스레드 수에 제한됨
     *    - I/O 대기시간이 길수록 더 많은 스레드 필요
     *    - C10K 문제 (10,000개 동시 연결 처리의 어려움)
     *
     * 3. 대안 아키텍처:
     *    - 비동기 I/O (NIO, Netty)
     *    - 이벤트 루프 (Node.js 스타일)
     *    - 리액티브 프로그래밍 (WebFlux)
     *    - 가상 스레드 (Project Loom, Java 19+)
     *
     * 4. 최적화 전략:
     *    - 커넥션 풀링 (DB, HTTP 클라이언트)
     *    - 캐싱 레이어 (Redis, Memcached)
     *    - 비동기 처리 (CompletableFuture, 메시지 큐)
     *    - 적절한 타임아웃 설정
     */
}