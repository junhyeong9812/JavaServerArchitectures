package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * Hello World 비동기 서블릿
 * Threaded의 HelloWorldServlet과 동일한 기능을 비동기로 처리
 */
public class HelloWorldAsyncServlet extends MiniAsyncServlet {
    // MiniAsyncServlet을 상속받아 비동기 Hello World 기능 구현
    // 기존 동기 버전과 동일한 기능을 비동기 방식으로 제공하여 성능 비교 가능

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        // GET 요청 처리를 위한 비동기 메서드 오버라이드
        // 가장 기본적인 HTTP GET 요청에 대한 Hello World 응답 제공

        return CompletableFuture.runAsync(() -> {
            // runAsync()로 별도 스레드에서 Hello World 처리
            // 간단한 작업이지만 일관된 비동기 패턴 적용

            // 쿼리 파라미터에서 name 값 추출
            String name = request.getParameter("name");
            // request.getParameter() 사용 이유:
            // 1. HTTP GET 요청의 쿼리 파라미터 추출
            // 2. URL에서 ?name=value 형태의 파라미터 접근
            // 3. 사용자 정의 이름으로 개인화된 인사 제공

            if (name == null) name = "World";
            // null 체크 및 기본값 설정
            // name 파라미터가 없으면 "World"를 기본값으로 사용
            // 안전한 기본 동작 보장

            // HTML 응답 생성 및 전송
            response.sendHtml(
                    "<h1>Hello, " + name + "!</h1>" +
                            // 동적 이름 삽입으로 개인화된 인사말 생성
                            // HTML h1 태그로 제목 스타일 적용
                            "<p>Handled by HybridServer AsyncServlet</p>" +
                            // 서버 타입과 처리 방식 명시
                            // 클라이언트에서 어떤 서버가 응답했는지 확인 가능
                            "<p>Thread: " + Thread.currentThread().getName() + "</p>" +
                            // Thread.currentThread().getName() 사용 이유:
                            // 1. 현재 처리 스레드 식별로 비동기 처리 확인
                            // 2. 요청마다 다른 스레드에서 처리되는지 검증
                            // 3. 디버깅과 성능 분석에서 스레드 분산 상태 모니터링
                            "<p>Timestamp: " + System.currentTimeMillis() + "</p>" +
                            // System.currentTimeMillis() 사용 이유:
                            // 1. 응답 생성 시각 기록으로 처리 시점 확인
                            // 2. 에포크 시간으로 정확한 타임스탬프 제공
                            // 3. 클라이언트에서 응답 지연시간 계산 가능
                            "<p>Type: Non-blocking Async Processing</p>"
                    // 처리 방식 명시로 비동기 처리임을 강조
                    // 동기 버전과 구분하여 성능 특성 차이 표시
            );
            // response.sendHtml() 사용 이유:
            // 1. Content-Type을 text/html로 자동 설정
            // 2. 웹 브라우저에서 HTML로 렌더링되어 보기 좋은 형태
            // 3. HTML 응답에 적절한 HTTP 헤더 자동 추가

            // HTML 문자열 연결 방식 사용 이유:
            // 1. 간단한 템플릿이므로 복잡한 템플릿 엔진 불필요
            // 2. 동적 값(name, thread, timestamp) 삽입이 쉬움
            // 3. 성능상 오버헤드가 적고 직관적
        });
    }

    // doPostAsync 메서드를 오버라이드하지 않음:
    // 1. Hello World는 일반적으로 GET 요청으로 충분
    // 2. 필요시 상속받는 클래스에서 추가 구현 가능
    // 3. 기본 MiniAsyncServlet의 POST 처리 로직 사용
}