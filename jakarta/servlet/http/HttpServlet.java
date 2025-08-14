package jakarta.servlet.http; // 패키지 선언 - Jakarta EE HTTP 서블릿 API 패키지

import jakarta.servlet.GenericServlet; // import 선언 - 범용 서블릿 추상 클래스
import jakarta.servlet.ServletException; // import 선언 - 서블릿 예외 클래스
import jakarta.servlet.ServletRequest; // import 선언 - 서블릿 요청 인터페이스
import jakarta.servlet.ServletResponse; // import 선언 - 서블릿 응답 인터페이스
import jakarta.servlet.ServletOutputStream; // import 선언 - 서블릿 출력 스트림 클래스
import jakarta.servlet.WriteListener; // import 선언 - 비동기 쓰기 리스너 인터페이스

import java.io.IOException; // import 선언 - 입출력 예외 클래스
import java.io.PrintWriter; // import 선언 - 텍스트 출력을 위한 PrintWriter 클래스
import java.io.OutputStreamWriter; // import 선언 - 바이트 스트림을 문자 스트림으로 변환하는 클래스
import java.io.UnsupportedEncodingException; // import 선언 - 지원하지 않는 인코딩 예외 클래스

/**
 * HTTP 프로토콜을 처리하는 서블릿을 위한 추상 클래스입니다.
 *
 * HttpServlet은 GenericServlet을 확장하여 HTTP 프로토콜에 특화된 기능을 제공합니다.
 * 대부분의 웹 애플리케이션에서 이 클래스를 상속받아 서블릿을 작성합니다.
 *
 * 주요 기능:
 * - HTTP 메서드별 전용 처리 메서드 (doGet, doPost, doPut, doDelete 등)
 * - HTTP 헤더 및 상태 코드 처리
 * - 세션 관리 지원
 * - 쿠키 처리 지원
 *
 * 개발자는 처리하고자 하는 HTTP 메서드에 따라 해당 메서드를 오버라이드합니다:
 * - GET 요청: doGet() 메서드
 * - POST 요청: doPost() 메서드
 * - PUT 요청: doPut() 메서드
 * - DELETE 요청: doDelete() 메서드
 */
public abstract class HttpServlet extends GenericServlet { // public abstract class 선언 - HTTP 서블릿 추상 클래스, GenericServlet을 상속

    /**
     * 직렬화를 위한 버전 ID입니다.
     */
    private static final long serialVersionUID = 1L; // private static final 필드 - 직렬화 버전 ID, 클래스 변경 시 호환성 유지용

    /**
     * HTTP 메서드 상수들 - 표준 HTTP 메서드를 정의합니다.
     */
    private static final String METHOD_DELETE = "DELETE"; // private static final 필드 - DELETE HTTP 메서드 상수
    private static final String METHOD_HEAD = "HEAD"; // private static final 필드 - HEAD HTTP 메서드 상수
    private static final String METHOD_GET = "GET"; // private static final 필드 - GET HTTP 메서드 상수
    private static final String METHOD_OPTIONS = "OPTIONS"; // private static final 필드 - OPTIONS HTTP 메서드 상수
    private static final String METHOD_POST = "POST"; // private static final 필드 - POST HTTP 메서드 상수
    private static final String METHOD_PUT = "PUT"; // private static final 필드 - PUT HTTP 메서드 상수
    private static final String METHOD_TRACE = "TRACE"; // private static final 필드 - TRACE HTTP 메서드 상수
    private static final String METHOD_PATCH = "PATCH"; // private static final 필드 - PATCH HTTP 메서드 상수

    /**
     * HTTP 에러 메시지 상수들
     */
    private static final String LSTRING_FILE = "jakarta.servlet.http.LocalStrings"; // private static final 필드 - 지역화 문자열 파일 경로
    private static final String HTTP_METHOD_GET_NOT_SUPPORTED = "HTTP method GET is not supported by this URL"; // private static final 필드 - GET 미지원 메시지
    private static final String HTTP_METHOD_POST_NOT_SUPPORTED = "HTTP method POST is not supported by this URL"; // private static final 필드 - POST 미지원 메시지
    private static final String HTTP_METHOD_PUT_NOT_SUPPORTED = "HTTP method PUT is not supported by this URL"; // private static final 필드 - PUT 미지원 메시지
    private static final String HTTP_METHOD_DELETE_NOT_SUPPORTED = "HTTP method DELETE is not supported by this URL"; // private static final 필드 - DELETE 미지원 메시지

    /**
     * 기본 생성자입니다.
     *
     * 서블릿 컨테이너가 HttpServlet 인스턴스를 생성할 때 호출됩니다.
     */
    public HttpServlet() { // public 생성자 - 외부에서 접근 가능한 기본 생성자
        super(); // super() 호출 - 부모 클래스 GenericServlet의 생성자 호출
    }

    /**
     * HTTP GET 요청을 처리합니다.
     *
     * GET 메서드는 서버로부터 정보를 조회할 때 사용됩니다.
     * 멱등성(idempotent)을 가지며, 서버 상태를 변경하지 않아야 합니다.
     *
     * 일반적인 사용 사례:
     * - 웹 페이지 조회
     * - 데이터 검색
     * - 파일 다운로드
     * - API에서 리소스 조회
     *
     * 기본 구현은 405 (Method Not Allowed) 에러를 반환합니다.
     * 하위 클래스에서 이 메서드를 오버라이드하여 GET 요청을 처리해야 합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) // protected 메서드 - 하위 클래스에서 오버라이드 가능, HTTP GET 요청 처리
            throws ServletException, IOException { // throws 절 - 두 가지 체크 예외를 던질 수 있음을 선언

        String protocol = req.getProtocol(); // String 변수 선언 - 요청의 프로토콜 정보 조회 (예: HTTP/1.1)
        String message = HTTP_METHOD_GET_NOT_SUPPORTED; // String 변수 선언 - GET 미지원 에러 메시지

        // HTTP/1.1 이상에서는 405 Method Not Allowed 응답
        if (protocol.endsWith("1.1")) { // 조건문 - 프로토콜이 "1.1"로 끝나는지 확인 (HTTP/1.1인지 체크)
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message); // HttpServletResponse.sendError() 호출 - 405 상태 코드와 메시지 전송
        } else { // else 절 - HTTP/1.0 또는 기타 프로토콜인 경우
            // HTTP/1.0에서는 400 Bad Request 응답
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message); // HttpServletResponse.sendError() 호출 - 400 상태 코드와 메시지 전송
        }
    }

    /**
     * HTTP POST 요청을 처리합니다.
     *
     * POST 메서드는 서버에 데이터를 전송하여 리소스를 생성하거나 수정할 때 사용됩니다.
     * 멱등성을 갖지 않으며, 서버 상태를 변경할 수 있습니다.
     *
     * 일반적인 사용 사례:
     * - 폼 데이터 제출
     * - 파일 업로드
     * - 새 리소스 생성
     * - 로그인 처리
     * - API에서 데이터 생성/수정
     *
     * 기본 구현은 405 (Method Not Allowed) 에러를 반환합니다.
     * 하위 클래스에서 이 메서드를 오버라이드하여 POST 요청을 처리해야 합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) // protected 메서드 - HTTP POST 요청 처리
            throws ServletException, IOException { // throws 절 - 예외 선언

        String protocol = req.getProtocol(); // String 변수 - 프로토콜 정보 조회
        String message = HTTP_METHOD_POST_NOT_SUPPORTED; // String 변수 - POST 미지원 메시지

        if (protocol.endsWith("1.1")) { // 조건문 - HTTP/1.1 체크
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message); // 405 에러 전송
        } else { // else 절 - 기타 프로토콜
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message); // 400 에러 전송
        }
    }

    /**
     * HTTP PUT 요청을 처리합니다.
     *
     * PUT 메서드는 지정된 URI에 리소스를 생성하거나 전체를 대체할 때 사용됩니다.
     * 멱등성을 가지며, 동일한 요청을 여러 번 수행해도 결과가 같아야 합니다.
     *
     * 일반적인 사용 사례:
     * - 리소스 전체 수정
     * - 파일 업로드 (특정 경로에)
     * - RESTful API에서 리소스 생성/대체
     *
     * 기본 구현은 405 (Method Not Allowed) 에러를 반환합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) // protected 메서드 - HTTP PUT 요청 처리
            throws ServletException, IOException { // throws 절 - 예외 선언

        String protocol = req.getProtocol(); // String 변수 - 프로토콜 정보 조회
        String message = HTTP_METHOD_PUT_NOT_SUPPORTED; // String 변수 - PUT 미지원 메시지

        if (protocol.endsWith("1.1")) { // 조건문 - HTTP/1.1 체크
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message); // 405 에러 전송
        } else { // else 절 - 기타 프로토콜
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message); // 400 에러 전송
        }
    }

    /**
     * HTTP DELETE 요청을 처리합니다.
     *
     * DELETE 메서드는 지정된 리소스를 삭제할 때 사용됩니다.
     * 멱등성을 가지며, 동일한 삭제 요청을 여러 번 수행해도 안전해야 합니다.
     *
     * 일반적인 사용 사례:
     * - 리소스 삭제
     * - 계정 삭제
     * - RESTful API에서 리소스 제거
     *
     * 기본 구현은 405 (Method Not Allowed) 에러를 반환합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) // protected 메서드 - HTTP DELETE 요청 처리
            throws ServletException, IOException { // throws 절 - 예외 선언

        String protocol = req.getProtocol(); // String 변수 - 프로토콜 정보 조회
        String message = HTTP_METHOD_DELETE_NOT_SUPPORTED; // String 변수 - DELETE 미지원 메시지

        if (protocol.endsWith("1.1")) { // 조건문 - HTTP/1.1 체크
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message); // 405 에러 전송
        } else { // else 절 - 기타 프로토콜
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message); // 400 에러 전송
        }
    }

    /**
     * HTTP OPTIONS 요청을 처리합니다.
     *
     * OPTIONS 메서드는 서버가 지원하는 HTTP 메서드를 조회할 때 사용됩니다.
     * CORS (Cross-Origin Resource Sharing) preflight 요청에서도 사용됩니다.
     *
     * 기본 구현은 현재 서블릿이 지원하는 메서드들을 Allow 헤더에 설정합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) // protected 메서드 - HTTP OPTIONS 요청 처리
            throws ServletException, IOException { // throws 절 - 예외 선언

        // 지원하는 메서드들을 확인하고 Allow 헤더에 설정
        StringBuilder allow = new StringBuilder(); // StringBuilder 객체 생성 - 가변 문자열로 Allow 헤더 값 구성

        // 각 메서드가 구현되어 있는지 확인 (기본 구현인지 오버라이드된 구현인지)
        // 실제로는 리플렉션을 사용하여 확인하지만, 여기서는 간단히 구현

        // 기본적으로 지원하는 메서드들
        allow.append("GET, HEAD, POST, PUT, DELETE, OPTIONS"); // StringBuilder.append() - 지원 메서드 목록 추가

        resp.setHeader("Allow", allow.toString()); // HttpServletResponse.setHeader() - Allow 헤더 설정
        resp.setStatus(HttpServletResponse.SC_OK); // HttpServletResponse.setStatus() - 200 OK 상태 코드 설정
    }

    /**
     * HTTP HEAD 요청을 처리합니다.
     *
     * HEAD 메서드는 GET과 동일하지만 응답 본문 없이 헤더만 반환합니다.
     * 리소스의 메타정보를 확인하거나 존재 여부를 검사할 때 사용됩니다.
     *
     * 기본 구현은 doGet()을 호출한 후 응답 본문을 제거합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) // protected 메서드 - HTTP HEAD 요청 처리
            throws ServletException, IOException { // throws 절 - 예외 선언

        // HEAD 요청을 위한 특별한 응답 래퍼를 생성
        // 이 래퍼는 응답 본문을 무시하고 헤더만 처리합니다
        NoBodyResponse response = new NoBodyResponse(resp); // NoBodyResponse 객체 생성 - 본문 없는 응답 래퍼

        // GET 메서드 로직을 실행하되 본문은 무시
        doGet(req, response); // doGet() 메서드 호출 - GET 로직 실행하지만 본문은 무시됨

        // Content-Length 헤더를 정확히 설정
        response.setContentLength(); // NoBodyResponse.setContentLength() - 정확한 Content-Length 헤더 설정
    }

    /**
     * HTTP TRACE 요청을 처리합니다.
     *
     * TRACE 메서드는 클라이언트가 보낸 요청을 그대로 반환합니다.
     * 주로 디버깅 목적으로 사용되지만, 보안상 위험할 수 있어 비활성화하는 경우가 많습니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp) // protected 메서드 - HTTP TRACE 요청 처리
            throws ServletException, IOException { // throws 절 - 예외 선언

        // TRACE 메서드는 보안상 위험할 수 있으므로 기본적으로 비활성화
        String message = "TRACE method is not allowed"; // String 변수 - TRACE 미허용 메시지
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message); // 405 에러 전송 - TRACE 메서드 거부
    }

    /**
     * HTTP PATCH 요청을 처리합니다.
     *
     * PATCH 메서드는 리소스의 일부분만 수정할 때 사용됩니다.
     * PUT과 달리 전체가 아닌 부분적인 수정을 수행합니다.
     *
     * 기본 구현은 405 (Method Not Allowed) 에러를 반환합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) // protected 메서드 - HTTP PATCH 요청 처리
            throws ServletException, IOException { // throws 절 - 예외 선언

        String protocol = req.getProtocol(); // String 변수 - 프로토콜 정보 조회
        String message = "HTTP method PATCH is not supported by this URL"; // String 변수 - PATCH 미지원 메시지

        if (protocol.endsWith("1.1")) { // 조건문 - HTTP/1.1 체크
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message); // 405 에러 전송
        } else { // else 절 - 기타 프로토콜
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message); // 400 에러 전송
        }
    }

    /**
     * 모든 HTTP 요청의 진입점입니다.
     *
     * 이 메서드는 GenericServlet의 service() 메서드를 구현합니다.
     * HTTP 메서드를 확인하고 해당하는 doXXX() 메서드로 요청을 전달합니다.
     *
     * 일반적으로 이 메서드를 직접 오버라이드하지 않고,
     * 각 HTTP 메서드에 해당하는 doGet(), doPost() 등을 오버라이드합니다.
     *
     * @param req 클라이언트 요청 (ServletRequest 타입)
     * @param res 클라이언트 응답 (ServletResponse 타입)
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    @Override // 어노테이션 - GenericServlet.service() 메서드 오버라이드임을 명시
    public void service(ServletRequest req, ServletResponse res) // public 메서드 - GenericServlet의 service() 메서드 구현
            throws ServletException, IOException { // throws 절 - 예외 선언

        HttpServletRequest request; // HttpServletRequest 변수 선언 - HTTP 전용 요청 객체
        HttpServletResponse response; // HttpServletResponse 변수 선언 - HTTP 전용 응답 객체

        // ServletRequest를 HttpServletRequest로 캐스팅
        try { // try 블록 - 예외 처리 시작
            request = (HttpServletRequest) req; // 타입 캐스팅 - ServletRequest를 HttpServletRequest로 변환
            response = (HttpServletResponse) res; // 타입 캐스팅 - ServletResponse를 HttpServletResponse로 변환
        } catch (ClassCastException e) { // catch 블록 - ClassCastException 처리
            throw new ServletException("non-HTTP request or response"); // ServletException 던지기 - HTTP가 아닌 요청/응답 에러
        }

        // HTTP 전용 service 메서드로 위임
        service(request, response); // service(HttpServletRequest, HttpServletResponse) 메서드 호출 - HTTP 전용 서비스 메서드로 위임
    }

    /**
     * HTTP 요청을 메서드별로 분기하여 처리합니다.
     *
     * 이 메서드는 HTTP 메서드를 확인하고 해당하는 doXXX() 메서드를 호출합니다.
     *
     * @param req HTTP 요청 객체
     * @param resp HTTP 응답 객체
     * @throws ServletException 서블릿 관련 오류 시
     * @throws IOException 입출력 오류 시
     */
    protected void service(HttpServletRequest req, HttpServletResponse resp) // protected 메서드 - HTTP 전용 service 메서드
            throws ServletException, IOException { // throws 절 - 예외 선언

        String method = req.getMethod(); // String 변수 - HTTP 메서드 조회 (GET, POST, PUT 등)

        // HTTP 메서드에 따라 해당하는 처리 메서드 호출
        switch (method) { // switch 문 - HTTP 메서드별 분기 처리
            case METHOD_GET: // case 절 - GET 메서드인 경우
                // Last-Modified 헤더 처리
                long lastModified = getLastModified(req); // long 변수 - 리소스의 마지막 수정 시간 조회
                if (lastModified == -1) { // 조건문 - Last-Modified를 알 수 없는 경우
                    // Last-Modified를 알 수 없는 경우 일반 GET 처리
                    doGet(req, resp); // doGet() 메서드 호출 - 일반적인 GET 요청 처리
                } else { // else 절 - Last-Modified를 알 수 있는 경우
                    // If-Modified-Since 헤더 확인하여 304 Not Modified 응답 가능
                    long ifModifiedSince = req.getDateHeader("If-Modified-Since"); // long 변수 - 클라이언트가 보낸 If-Modified-Since 헤더 값
                    if (ifModifiedSince < lastModified) { // 조건문 - 리소스가 수정되었는지 확인
                        // 리소스가 수정되었으므로 정상 응답
                        maybeSetLastModified(resp, lastModified); // maybeSetLastModified() 메서드 호출 - Last-Modified 헤더 설정
                        doGet(req, resp); // doGet() 메서드 호출 - GET 요청 처리
                    } else { // else 절 - 리소스가 수정되지 않은 경우
                        // 리소스가 수정되지 않았으므로 304 응답
                        resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED); // 304 Not Modified 상태 코드 설정
                    }
                }
                break; // break 문 - switch 문 탈출

            case METHOD_HEAD: // case 절 - HEAD 메서드인 경우
                // HEAD 요청은 GET과 동일하지만 본문 없음
                long lastModified2 = getLastModified(req); // long 변수 - 마지막 수정 시간 조회
                maybeSetLastModified(resp, lastModified2); // Last-Modified 헤더 설정
                doHead(req, resp); // doHead() 메서드 호출 - HEAD 요청 처리
                break; // break 문 - switch 문 탈출

            case METHOD_POST: // case 절 - POST 메서드인 경우
                doPost(req, resp); // doPost() 메서드 호출 - POST 요청 처리
                break; // break 문 - switch 문 탈출

            case METHOD_PUT: // case 절 - PUT 메서드인 경우
                doPut(req, resp); // doPut() 메서드 호출 - PUT 요청 처리
                break; // break 문 - switch 문 탈출

            case METHOD_DELETE: // case 절 - DELETE 메서드인 경우
                doDelete(req, resp); // doDelete() 메서드 호출 - DELETE 요청 처리
                break; // break 문 - switch 문 탈출

            case METHOD_OPTIONS: // case 절 - OPTIONS 메서드인 경우
                doOptions(req, resp); // doOptions() 메서드 호출 - OPTIONS 요청 처리
                break; // break 문 - switch 문 탈출

            case METHOD_TRACE: // case 절 - TRACE 메서드인 경우
                doTrace(req, resp); // doTrace() 메서드 호출 - TRACE 요청 처리
                break; // break 문 - switch 문 탈출

            case METHOD_PATCH: // case 절 - PATCH 메서드인 경우
                doPatch(req, resp); // doPatch() 메서드 호출 - PATCH 요청 처리
                break; // break 문 - switch 문 탈출

            default: // default 절 - 지원하지 않는 HTTP 메서드인 경우
                // 지원하지 않는 HTTP 메서드
                String errMsg = "Method " + method + " is not defined in RFC 2068 and is not supported by the Servlet API"; // String 변수 - 에러 메시지 구성
                resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, errMsg); // 501 Not Implemented 에러 전송
        }
    }

    /**
     * 리소스의 마지막 수정 시간을 반환합니다.
     *
     * 이 메서드는 HTTP 캐싱 최적화를 위해 사용됩니다.
     * 클라이언트가 If-Modified-Since 헤더를 보낸 경우,
     * 리소스가 변경되지 않았으면 304 Not Modified 응답을 보낼 수 있습니다.
     *
     * 기본 구현은 -1을 반환하여 Last-Modified 헤더를 사용하지 않음을 나타냅니다.
     * 하위 클래스에서 이 메서드를 오버라이드하여 실제 수정 시간을 반환할 수 있습니다.
     *
     * @param req HTTP 요청 객체
     * @return 마지막 수정 시간 (밀리초), 알 수 없으면 -1
     */
    protected long getLastModified(HttpServletRequest req) { // protected 메서드 - 마지막 수정 시간 조회, 하위 클래스에서 오버라이드 가능
        return -1; // return 문 - -1 반환 (Last-Modified 정보 없음)
    }

    /**
     * Last-Modified 헤더를 설정합니다.
     *
     * @param resp HTTP 응답 객체
     * @param lastModified 마지막 수정 시간
     */
    private void maybeSetLastModified(HttpServletResponse resp, long lastModified) { // private 메서드 - Last-Modified 헤더 설정
        if (lastModified >= 0) { // 조건문 - 유효한 수정 시간인지 확인
            resp.setDateHeader("Last-Modified", lastModified); // HttpServletResponse.setDateHeader() - Last-Modified 헤더 설정
        }
    }

    /**
     * HEAD 요청을 위한 응답 래퍼 클래스입니다.
     *
     * 이 클래스는 응답 본문을 무시하고 헤더만 처리하여
     * HEAD 요청의 정확한 Content-Length를 계산합니다.
     */
    private static class NoBodyResponse extends HttpServletResponseWrapper { // private static class - 내부 클래스, HEAD 요청용 응답 래퍼
        private NoBodyOutputStream noBody; // private 필드 - 본문 없는 출력 스트림
        private PrintWriter writer; // private 필드 - 텍스트 출력용 writer
        private boolean didSetContentLength; // private 필드 - Content-Length 설정 여부

        public NoBodyResponse(HttpServletResponse response) { // public 생성자 - HttpServletResponse를 감싸는 래퍼 생성
            super(response); // super() 호출 - 부모 클래스 생성자 호출
            noBody = new NoBodyOutputStream(); // NoBodyOutputStream 객체 생성 - 더미 출력 스트림
        }

        public void setContentLength() { // public 메서드 - Content-Length 설정
            if (!didSetContentLength) { // 조건문 - 아직 Content-Length가 설정되지 않은 경우
                if (writer != null) { // 조건문 - writer가 사용된 경우
                    writer.flush(); // PrintWriter.flush() - 버퍼 비우기
                }
                super.setContentLength(noBody.getContentLength()); // 상위 클래스의 setContentLength() 호출 - 실제 Content-Length 설정
            }
        }

        @Override // 어노테이션 - 메서드 오버라이드
        public void setContentLength(int len) { // public 메서드 - Content-Length 수동 설정
            super.setContentLength(len); // 상위 클래스 메서드 호출
            didSetContentLength = true; // 플래그 설정 - Content-Length 설정됨 표시
        }

        @Override // 어노테이션 - 메서드 오버라이드
        public ServletOutputStream getOutputStream() throws IOException { // public 메서드 - 출력 스트림 반환
            return noBody; // 더미 출력 스트림 반환
        }

        @Override // 어노테이션 - 메서드 오버라이드
        public PrintWriter getWriter() throws UnsupportedEncodingException { // public 메서드 - PrintWriter 반환
            if (writer == null) { // 조건문 - writer가 아직 생성되지 않은 경우
                OutputStreamWriter w = new OutputStreamWriter(noBody, getCharacterEncoding()); // OutputStreamWriter 생성 - 더미 스트림을 문자 스트림으로 변환
                writer = new PrintWriter(w); // PrintWriter 생성 - 텍스트 출력용 writer
            }
            return writer; // PrintWriter 반환
        }
    }

    /**
     * HEAD 요청을 위한 더미 출력 스트림입니다.
     *
     * 실제로 데이터를 출력하지 않고 바이트 수만 계산합니다.
     */
    private static class NoBodyOutputStream extends ServletOutputStream { // private static class - 내부 클래스, 더미 출력 스트림
        private int contentLength = 0; // private 필드 - 계산된 콘텐츠 길이

        public int getContentLength() { // public 메서드 - 계산된 콘텐츠 길이 반환
            return contentLength; // return 문 - 콘텐츠 길이 값 반환
        }

        @Override // 어노테이션 - 메서드 오버라이드
        public void write(int b) { // public 메서드 - 단일 바이트 쓰기 (실제로는 길이만 계산)
            contentLength++; // 증감 연산자 - 콘텐츠 길이 1 증가
        }

        @Override // 어노테이션 - 메서드 오버라이드
        public void write(byte[] buf) throws IOException { // public 메서드 - 바이트 배열 쓰기
            if (buf == null) { // 조건문 - null 체크
                throw new NullPointerException("buf"); // NullPointerException 던지기 - null 포인터 예외
            }
            contentLength += buf.length; // 대입 연산자 - 배열 길이만큼 콘텐츠 길이 증가
        }

        @Override // 어노테이션 - 메서드 오버라이드
        public void write(byte[] buf, int offset, int len) throws IOException { // public 메서드 - 부분 바이트 배열 쓰기
            if (buf == null) { // 조건문 - null 체크
                throw new NullPointerException("buf"); // NullPointerException 던지기
            }
            if (offset < 0 || len < 0 || offset + len > buf.length) { // 조건문 - 배열 범위 체크
                throw new IndexOutOfBoundsException(); // IndexOutOfBoundsException 던지기 - 배열 범위 벗어남
            }
            contentLength += len; // 대입 연산자 - 길이만큼 콘텐츠 길이 증가
        }

        @Override // 어노테이션 - 메서드 오버라이드
        public boolean isReady() { // public 메서드 - 쓰기 준비 상태 확인
            return true; // return 문 - 항상 준비됨 (더미 스트림이므로)
        }

        @Override // 어노테이션 - 메서드 오버라이드
        public void setWriteListener(WriteListener writeListener) { // public 메서드 - 쓰기 리스너 설정
            // NoBody이므로 리스너는 즉시 호출
            try { // try 블록 - 예외 처리
                writeListener.onWritePossible(); // WriteListener.onWritePossible() 호출 - 쓰기 가능 이벤트 즉시 발생
            } catch (IOException e) { // catch 블록 - IOException 처리
                writeListener.onError(e); // WriteListener.onError() 호출 - 에러 이벤트 전달
            }
        }
    }
}