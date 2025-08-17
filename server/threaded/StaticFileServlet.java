package server.threaded;

import server.core.mini.*;

/**
 * 정적 파일 서빙 서블릿
 *
 * 이 서블릿은 CSS, JavaScript, 이미지 등의 정적 파일을 클라이언트에게 제공합니다.
 * 실제 파일 시스템에서 파일을 읽지 않고, 파일 확장자에 따라 적절한 내용을 시뮬레이션합니다.
 *
 * 주요 기능:
 * 1. 파일 확장자 기반 MIME 타입 결정
 * 2. 확장자별 적절한 콘텐츠 생성
 * 3. 정적 파일 서빙 패턴 구현
 *
 * 지원하는 파일 타입:
 * - .css: 스타일시트 파일
 * - .js: JavaScript 파일
 * - 기타: 일반 텍스트 파일
 *
 * 실제 정적 파일 서버의 구현 요소들:
 * - 파일 시스템 접근
 * - 캐싱 (ETag, Last-Modified 헤더)
 * - Range 요청 지원 (부분 다운로드)
 * - 보안 (Path Traversal 공격 방지)
 * - 압축 (gzip, brotli)
 *
 * 이 구현은 교육 목적의 단순화된 버전입니다.
 */
public class StaticFileServlet extends MiniServlet {

    /**
     * HTTP GET 요청 처리 메서드
     *
     * 클라이언트가 요청한 정적 파일의 경로를 분석하고,
     * 파일 확장자에 따라 적절한 콘텐츠와 MIME 타입으로 응답합니다.
     *
     * @param request MiniRequest 객체 - HTTP 요청 정보
     * @param response MiniResponse 객체 - HTTP 응답 생성용
     * @throws Exception 처리 중 발생할 수 있는 모든 예외
     */
    @Override
    protected void doGet(MiniRequest request, MiniResponse response) throws Exception {
        /*
         * 요청된 파일 경로 추출
         *
         * request.getPathInfo(): 서블릿 매핑 패스 이후의 경로 반환
         *
         * 예시:
         * - 서블릿 매핑: /static/*
         * - 요청 URL: /static/css/main.css
         * - getPathInfo() 반환: /css/main.css
         *
         * 실제 구현에서는 보안 검증 필요:
         * - "../" 경로 순회 공격 방지
         * - 절대 경로 사용 금지
         * - 허용된 디렉토리 외부 접근 차단
         */
        String path = request.getPathInfo();

        /*
         * 파일 확장자 기반 콘텐츠 타입 결정 및 응답 생성
         *
         * String.endsWith(): 문자열 끝이 특정 패턴과 일치하는지 확인
         * - 대소문자를 구분하므로 실제 구현에서는 toLowerCase() 사용 권장
         * - 확장자가 없는 파일이나 숨김 파일(.gitignore 등) 처리 고려
         */

        // CSS 파일 처리
        if (path.endsWith(".css")) {
            /*
             * CSS (Cascading Style Sheets) 파일 처리
             *
             * response.setContentType("text/css"):
             * - MIME 타입을 text/css로 설정
             * - 브라우저가 CSS로 해석하도록 지시
             * - 브라우저의 CSS 파서가 활성화됨
             *
             * CSS MIME 타입의 중요성:
             * - 잘못된 MIME 타입시 브라우저가 스타일을 적용하지 않음
             * - FOUC (Flash of Unstyled Content) 현상 발생 가능
             * - 일부 브라우저는 MIME 타입이 정확하지 않으면 CSS를 무시
             */
            response.setContentType("text/css");

            /*
             * 시뮬레이션된 CSS 콘텐츠 생성
             *
             * response.writeBody(): HTTP 응답 본문에 콘텐츠 작성
             * - 실제로는 파일 시스템에서 파일을 읽어옴
             * - 캐싱, 압축, 최적화 등의 처리가 필요
             *
             * CSS 주석과 간단한 스타일 규칙:
             */
             // /* */ : CSS 주석 문법
            //      * - body { color: blue; } : 기본적인 CSS 규칙
            //      * - 경로별로 다른 콘텐츠를 제공하여 실제 파일처럼 동작

            response.writeBody("/* CSS content for " + path + " */\n" +
                    "body { color: blue; }");

        } else if (path.endsWith(".js")) {
            /*
             * JavaScript 파일 처리
             *
             * response.setContentType("application/javascript"):
             * - MIME 타입을 application/javascript로 설정
             * - RFC 4329에서 정의된 표준 MIME 타입
             * - 구형 브라우저 호환을 위해 text/javascript도 사용 가능
             *
             * JavaScript MIME 타입의 역할:
             * - 브라우저의 JavaScript 엔진 활성화
             * - 보안 정책 (CORS, CSP) 적용
             * - 캐싱 동작 결정
             */
            response.setContentType("application/javascript");

            /*
             * 시뮬레이션된 JavaScript 콘텐츠 생성
             *
             * JavaScript 주석과 간단한 코드:
             * - // : 한 줄 주석 문법
             * - console.log() : 브라우저 콘솔에 메시지 출력
             * - 템플릿 리터럴 패턴으로 동적 콘텐츠 생성
             *
             * 실제 구현에서 고려사항:
             * - 모듈 시스템 (ES6 modules, CommonJS)
             * - 트랜스파일링 (ES6+ -> ES5)
             * - 압축/난독화 (uglify, terser)
             * - 소스맵 지원
             */
            response.writeBody("// JavaScript content for " + path + "\n" +
                    "console.log('Hello from " + path + "');");

        } else {
            /*
             * 기타 파일 타입 처리 (기본 동작)
             *
             * 확장자가 .css나 .js가 아닌 모든 파일에 대한 기본 처리입니다.
             * 이미지, 폰트, 문서 파일 등이 여기에 해당합니다.
             *
             * response.setContentType("text/plain"):
             * - MIME 타입을 text/plain으로 설정
             * - 브라우저가 일반 텍스트로 표시
             * - 파일 다운로드가 아닌 브라우저 내 표시
             *
             * 실제 구현에서는 확장자별 적절한 MIME 타입 매핑 필요:
             * - .png, .jpg: image/png, image/jpeg
             * - .pdf: application/pdf
             * - .woff, .ttf: font/woff, font/ttf
             * - .json: application/json
             * - .xml: application/xml
             */
            response.setContentType("text/plain");

            /*
             * 기본 콘텐츠 생성
             *
             * 파일 경로와 처리 스레드 정보를 포함한 간단한 응답:
             * - 정적 파일임을 명시
             * - 요청된 경로 표시 (디버깅용)
             * - 처리 스레드 정보 (성능 분석용)
             *
             * Thread.currentThread().getName():
             * - 현재 요청을 처리하는 스레드의 이름
             * - 스레드풀 동작 확인
             * - 로드 밸런싱 분석
             * - 성능 병목 지점 식별
             */
            response.writeBody("Static file: " + path + "\n" +
                    "Served by: " + Thread.currentThread().getName());
        }

        /*
         * 정적 파일 서빙의 실제 구현 고려사항:
         *
         * 1. 성능 최적화:
         *    - 파일 캐싱 (메모리, 디스크)
         *    - 압축 전송 (gzip, brotli)
         *    - ETag, Last-Modified 헤더로 브라우저 캐싱
         *    - Range 요청 지원 (부분 다운로드)
         *
         * 2. 보안:
         *    - 경로 순회 공격 방지 (../, ..\\ 필터링)
         *    - 심볼릭 링크 처리
         *    - 권한 검사 (읽기 권한 확인)
         *    - 숨김 파일 접근 차단
         *
         * 3. 확장성:
         *    - CDN 연동
         *    - 클러스터 환경에서의 파일 동기화
         *    - 버전 관리 (캐시 무효화)
         *    - 동적 리사이징 (이미지 파일)
         *
         * 4. 모니터링:
         *    - 액세스 로그
         *    - 404 에러 추적
         *    - 대역폭 사용량
         *    - 인기 파일 통계
         *
         * 5. 표준 준수:
         *    - HTTP 캐싱 스펙 (RFC 7234)
         *    - MIME 타입 표준 (RFC 2046)
         *    - Range 요청 스펙 (RFC 7233)
         *    - 조건부 요청 (If-None-Match, If-Modified-Since)
         */
    }

    /*
     * 추가 HTTP 메서드 지원 고려사항:
     *
     * HEAD 메서드:
     * - GET과 동일한 헤더만 반환 (본문 없음)
     * - 파일 존재 여부 확인
     * - 캐시 검증
     *
     * OPTIONS 메서드:
     * - CORS 프리플라이트 요청 처리
     * - 허용되는 메서드 목록 반환
     *
     * 조건부 요청:
     * - If-None-Match: ETag 기반 캐시 검증
     * - If-Modified-Since: 수정 시간 기반 캐시 검증
     * - 304 Not Modified 응답
     *
     * 예시 확장:
     *
     * @Override
     * protected void doHead(MiniRequest request, MiniResponse response) throws Exception {
     *     // 헤더만 설정하고 본문은 전송하지 않음
     *     String path = request.getPathInfo();
     *     if (path.endsWith(".css")) {
     *         response.setContentType("text/css");
     *     } else if (path.endsWith(".js")) {
     *         response.setContentType("application/javascript");
     *     } else {
     *         response.setContentType("text/plain");
     *     }
     *     // Content-Length, Last-Modified, ETag 등의 헤더 설정
     * }
     */
}