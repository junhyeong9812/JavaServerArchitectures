package server.threaded;

import server.core.mini.*;
import server.core.http.*;

/**
 * 헬스체크 서블릿
 *
 * 이 서블릿은 서버의 상태 확인(Health Check)을 위한 엔드포인트를 제공합니다.
 * 로드밸런서, 모니터링 시스템, 운영 도구들이 서버의 생존 여부를 확인하는 데 사용됩니다.
 *
 * 주요 기능:
 * 1. 서버 생존 상태 확인 (Liveness Probe)
 * 2. 서버 준비 상태 확인 (Readiness Probe)
 * 3. 기본적인 서버 정보 제공
 *
 * HTTP 메서드 지원:
 * - GET: 상세한 헬스체크 정보 반환
 * - POST: 일부 헬스체크 도구 호환성을 위한 동일한 응답
 * - HEAD: 헤더만 반환 (바디 없음) - 경량 헬스체크용
 *
 * 헬스체크는 서비스 운영에 필수적인 요소입니다.
 */
public class HealthServlet extends MiniServlet {

    /**
     * HTTP GET 요청 처리 메서드
     *
     * 가장 일반적인 헬스체크 요청을 처리합니다.
     * JSON 형태로 서버 상태 정보를 반환합니다.
     *
     * @param request MiniRequest 객체 - HTTP 요청 정보
     * @param response MiniResponse 객체 - HTTP 응답 생성용
     * @throws Exception 처리 중 발생할 수 있는 모든 예외
     */
    @Override
    protected void doGet(MiniRequest request, MiniResponse response) throws Exception {
        /*
         * 간단한 헬스체크 응답 생성
         *
         * JSON 형태로 서버의 기본 상태 정보를 제공합니다.
         * 이 정보는 모니터링 시스템에서 파싱하여 서버 상태를 판단하는 데 사용됩니다.
         */

        /*
         * String.format()을 사용한 JSON 문자열 생성
         *
         * JSON 구조:
         * {
         *   "status": "healthy",           // 서버 상태 (healthy/unhealthy)
         *   "server": "threaded",          // 서버 타입 식별자
         *   "timestamp": 1640995200000,    // 응답 생성 시각 (Unix timestamp)
         *   "thread": "ThreadPool-1"       // 요청 처리 스레드
         * }
         */
        response.sendJson(String.format(
                "{ \"status\": \"healthy\", " +
                        "\"server\": \"threaded\", " +
                        "\"timestamp\": %d, " +
                        "\"thread\": \"%s\" }",

                /*
                 * System.currentTimeMillis(): 현재 시간을 밀리초로 반환
                 *
                 * Unix Timestamp (Epoch Time):
                 * - 1970년 1월 1일 00:00:00 UTC부터 경과된 밀리초
                 * - 전 세계 공통 시간 표준으로 시간대 문제 해결
                 * - JavaScript Date(), Python datetime 등에서 직접 변환 가능
                 * - 로그 분석과 시간 동기화에 유용
                 *
                 * 용도:
                 * - 헬스체크 응답이 언제 생성되었는지 확인
                 * - 네트워크 지연이나 캐싱 문제 탐지
                 * - 서버 시간 동기화 상태 확인
                 */
                System.currentTimeMillis(),

                /*
                 * Thread.currentThread().getName(): 현재 스레드 이름 반환
                 *
                 * 용도:
                 * - 스레드풀의 작업 분산 모니터링
                 * - 동시 헬스체크 요청 처리 확인
                 * - 디버깅 시 요청 추적
                 * - 성능 분석 (특정 스레드의 부하 확인)
                 */
                Thread.currentThread().getName()
        ));

        /*
         * response.sendJson()의 내부 동작:
         * 1. Content-Type을 "application/json; charset=UTF-8"로 설정
         * 2. HTTP 상태 코드를 200 OK로 설정 (기본값)
         * 3. JSON 문자열을 HTTP 응답 본문에 기록
         * 4. Content-Length 헤더 자동 계산 및 설정
         */
    }

    /**
     * HTTP POST 요청 처리 메서드
     *
     * 일부 헬스체크 도구나 로드밸런서는 POST 메서드를 사용할 수 있습니다.
     * GET과 동일한 응답을 제공하여 호환성을 보장합니다.
     *
     * @param request MiniRequest 객체 - HTTP 요청 정보
     * @param response MiniResponse 객체 - HTTP 응답 생성용
     * @throws Exception 처리 중 발생할 수 있는 모든 예외
     */
    @Override
    protected void doPost(MiniRequest request, MiniResponse response) throws Exception {
        /*
         * POST도 같은 응답 (일부 헬스체크 도구가 POST 사용)
         *
         * 메서드 위임(Method Delegation) 패턴:
         * - 중복 코드를 방지하기 위해 doGet() 메서드를 재사용
         * - 동일한 로직을 여러 HTTP 메서드에서 공유
         * - 유지보수성 향상 (로직 변경시 한 곳만 수정)
         *
         * 헬스체크에서 POST를 지원하는 이유:
         * - 일부 구형 로드밸런서나 모니터링 도구의 호환성
         * - 프록시나 방화벽에서 GET 요청을 캐싱하는 문제 회피
         * - RESTful하지 않지만 운영상 필요한 경우가 있음
         */
        doGet(request, response);
    }

    /**
     * HTTP HEAD 요청 처리 메서드
     *
     * HEAD 요청은 응답 헤더만 반환하고 본문(body)은 반환하지 않습니다.
     * 경량 헬스체크나 네트워크 대역폭을 절약하고 싶을 때 사용됩니다.
     *
     * @param request MiniRequest 객체 - HTTP 요청 정보
     * @param response MiniResponse 객체 - HTTP 응답 생성용
     * @throws Exception 처리 중 발생할 수 있는 모든 예외
     */
    @Override
    protected void doHead(MiniRequest request, MiniResponse response) throws Exception {
        /*
         * HEAD 요청 - 헤더만 반환 (바디 없음)
         *
         * HEAD 메서드의 특징:
         * - GET과 동일한 헤더를 반환하지만 응답 본문은 없음
         * - HTTP 스펙(RFC 7231)에 정의된 표준 메서드
         * - 서버가 살아있는지만 확인하고 싶을 때 사용
         * - 네트워크 트래픽 최소화 (헤더만 전송)
         */

        /*
         * HttpStatus.OK: HTTP 200 상태 코드
         *
         * HTTP 상태 코드 의미:
         * - 200 OK: 요청이 성공적으로 처리됨
         * - 헬스체크의 경우 서버가 정상 동작 중임을 의미
         * - 2xx 계열은 모두 성공을 나타냄
         *
         * response.setStatus(): HTTP 응답 상태 코드 설정
         * - 상태 라인에 포함됨 (예: "HTTP/1.1 200 OK")
         * - 클라이언트가 요청 처리 결과를 판단하는 기준
         */
        response.setStatus(HttpStatus.OK);

        /*
         * Content-Type 헤더 설정
         *
         * "application/json": MIME 타입
         * - 응답 본문이 JSON 형식임을 명시 (실제로는 본문 없음)
         * - HEAD 요청이지만 GET과 동일한 헤더를 제공해야 HTTP 스펙 준수
         * - 클라이언트가 실제 GET 요청시 받을 응답 타입을 미리 알 수 있음
         *
         * response.setContentType(): Content-Type 헤더 설정
         * - HTTP 헤더에 "Content-Type: application/json" 추가
         * - 브라우저나 클라이언트가 응답 처리 방식 결정
         */
        response.setContentType("application/json");

        /*
         * HEAD 응답의 특징:
         * 1. 본문(body) 없음 - response.writeBody() 호출 안 함
         * 2. Content-Length: 0 (자동 설정)
         * 3. GET과 동일한 헤더 제공
         * 4. 빠른 응답 시간 (본문 생성/전송 시간 절약)
         *
         * 사용 사례:
         * - 로드밸런서의 빠른 헬스체크
         * - 대용량 트래픽에서 리소스 절약
         * - 네트워크 지연이 중요한 환경
         * - 헬스체크 빈도가 높은 시스템
         */
    }

    /*
     * 헬스체크 서블릿의 중요성과 활용:
     *
     * 1. 운영 환경에서의 역할:
     *    - 로드밸런서: 정상 인스턴스에만 트래픽 전달
     *    - Kubernetes: Liveness/Readiness Probe
     *    - AWS ELB: Health Check Target
     *    - 모니터링 시스템: 서비스 가용성 확인
     *
     * 2. 응답 시간의 중요성:
     *    - 빠른 응답 필요 (일반적으로 1초 이내)
     *    - CPU/메모리 부하 최소화
     *    - 외부 의존성 최소화 (DB 연결 등 체크 안 함)
     *
     * 3. 확장 가능한 헬스체크:
     *    - 심화 체크: /health/deep (DB, 외부 API 연결 확인)
     *    - 준비성 체크: /health/ready (초기화 완료 여부)
     *    - 메트릭: /health/metrics (성능 지표 포함)
     *
     * 4. 보안 고려사항:
     *    - 인증 없이 접근 가능해야 함
     *    - 민감한 정보 노출 금지
     *    - DDoS 공격에 대한 고려
     */
}