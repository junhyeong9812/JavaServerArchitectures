package jakarta.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

/**
 * 클라이언트의 요청 정보를 제공하는 인터페이스입니다.
 *
 * ServletRequest는 HTTP, FTP, SMTP 등 다양한 프로토콜에서 공통적으로 사용할 수 있는
 * 범용적인 요청 인터페이스입니다. HTTP 특화 기능은 HttpServletRequest에서 제공됩니다.
 *
 * 주요 기능:
 * - 요청 매개변수 접근 (폼 데이터, 쿼리 스트링)
 * - 요청 속성 관리 (요청 스코프 데이터)
 * - 요청 메타데이터 (클라이언트 정보, 프로토콜 정보)
 * - 요청 본문 읽기
 */
public interface ServletRequest {

    /**
     * 지정된 이름의 요청 매개변수 값을 반환합니다.
     *
     * 요청 매개변수는 다음과 같은 소스에서 올 수 있습니다:
     * - URL 쿼리 스트링: http://example.com/servlet?name=value
     * - HTML 폼 데이터: <input name="username" value="john">
     * - POST 요청의 바디 데이터
     *
     * 동일한 이름의 매개변수가 여러 개 있을 경우, 첫 번째 값만 반환됩니다.
     * 여러 값이 필요한 경우 getParameterValues()를 사용하세요.
     *
     * @param name 매개변수 이름
     * @return 매개변수 값, 존재하지 않으면 null
     */
    String getParameter(String name);

    /**
     * 모든 요청 매개변수의 이름을 열거형으로 반환합니다.
     *
     * 어떤 매개변수들이 전송되었는지 확인할 때 사용합니다.
     * 폼 검증이나 디버깅 목적으로 유용합니다.
     *
     * 예시 사용법:
     * Enumeration<String> paramNames = request.getParameterNames();
     * while (paramNames.hasMoreElements()) {
     *     String paramName = paramNames.nextElement();
     *     String paramValue = request.getParameter(paramName);
     *     System.out.println(paramName + " = " + paramValue);
     * }
     *
     * @return 매개변수 이름들의 Enumeration
     */
    Enumeration<String> getParameterNames();

    /**
     * 지정된 이름의 매개변수에 대한 모든 값을 배열로 반환합니다.
     *
     * HTML에서 동일한 name을 가진 여러 input 요소가 있을 때 사용됩니다.
     * 예: 체크박스 그룹, 다중 선택 리스트박스
     *
     * HTML 예시:
     * <input type="checkbox" name="hobby" value="reading">
     * <input type="checkbox" name="hobby" value="swimming">
     * <input type="checkbox" name="hobby" value="coding">
     *
     * @param name 매개변수 이름
     * @return 매개변수 값들의 배열, 존재하지 않으면 null
     */
    String[] getParameterValues(String name);

    /**
     * 모든 요청 매개변수를 Map 형태로 반환합니다.
     *
     * key는 매개변수 이름, value는 해당 매개변수의 모든 값들의 배열입니다.
     * 한 번에 모든 매개변수를 처리하거나 다른 메서드로 전달할 때 편리합니다.
     *
     * 반환되는 Map은 수정할 수 없습니다 (immutable).
     *
     * @return 매개변수 Map (이름 -> 값 배열)
     */
    Map<String, String[]> getParameterMap();

    /**
     * 지정된 이름의 요청 속성을 반환합니다.
     *
     * 요청 속성은 요청 처리 과정에서 서블릿, 필터, 또는 JSP 간에
     * 데이터를 공유하기 위해 사용됩니다. 요청이 끝나면 자동으로 제거됩니다.
     *
     * 사용 예시:
     * - 필터에서 설정한 인증 정보
     * - 서블릿에서 JSP로 전달할 데이터
     * - 요청 처리 중간에 계산된 결과
     *
     * @param name 속성 이름
     * @return 속성 값, 존재하지 않으면 null
     */
    Object getAttribute(String name);

    /**
     * 모든 요청 속성의 이름을 열거형으로 반환합니다.
     *
     * @return 속성 이름들의 Enumeration
     */
    Enumeration<String> getAttributeNames();

    /**
     * 요청 속성을 설정합니다.
     *
     * 요청 처리 과정에서 다른 컴포넌트와 데이터를 공유할 때 사용합니다.
     *
     * @param name 속성 이름
     * @param o 속성 값
     */
    void setAttribute(String name, Object o);

    /**
     * 지정된 요청 속성을 제거합니다.
     *
     * @param name 제거할 속성 이름
     */
    void removeAttribute(String name);

    /**
     * 요청 본문의 문자 인코딩을 반환합니다.
     *
     * 클라이언트가 전송한 데이터의 문자 인코딩입니다.
     * 보통 Content-Type 헤더에서 charset 정보를 가져옵니다.
     *
     * 예: "UTF-8", "ISO-8859-1", "EUC-KR"
     *
     * @return 문자 인코딩, 지정되지 않았으면 null
     */
    String getCharacterEncoding();

    /**
     * 요청 본문의 문자 인코딩을 설정합니다.
     *
     * 이 메서드는 getReader()나 getParameter() 호출 전에 호출해야 합니다.
     * 한번 입력 스트림을 읽기 시작하면 인코딩을 변경할 수 없습니다.
     *
     * @param env 설정할 문자 인코딩
     * @throws UnsupportedEncodingException 지원하지 않는 인코딩인 경우
     */
    void setCharacterEncoding(String env) throws java.io.UnsupportedEncodingException;

    /**
     * 요청 본문의 바이트 길이를 반환합니다.
     *
     * Content-Length 헤더 값과 동일합니다.
     * 파일 업로드나 바이너리 데이터 처리 시 유용합니다.
     *
     * @return 본문 길이(바이트), 알 수 없으면 -1
     */
    int getContentLength();

    /**
     * 요청 본문의 MIME 타입을 반환합니다.
     *
     * Content-Type 헤더의 값입니다.
     * 클라이언트가 전송한 데이터의 형식을 나타냅니다.
     *
     * 예시:
     * - "text/html"
     * - "application/json"
     * - "multipart/form-data"
     * - "application/x-www-form-urlencoded"
     *
     * @return MIME 타입, 지정되지 않았으면 null
     */
    String getContentType();

    /**
     * 요청 본문을 바이너리로 읽기 위한 ServletInputStream을 반환합니다.
     *
     * 파일 업로드, 이미지, 또는 기타 바이너리 데이터를 처리할 때 사용됩니다.
     * getReader()와 getInputStream()은 동시에 사용할 수 없습니다.
     *
     * @return 요청 본문의 ServletInputStream
     * @throws IOException 입출력 오류 시
     */
    ServletInputStream getInputStream() throws IOException;

    /**
     * 요청 본문을 텍스트로 읽기 위한 BufferedReader를 반환합니다.
     *
     * JSON, XML, 또는 기타 텍스트 형태의 데이터를 처리할 때 사용됩니다.
     * 문자 인코딩이 자동으로 처리됩니다.
     *
     * @return 요청 본문의 BufferedReader
     * @throws IOException 입출력 오류 시
     */
    BufferedReader getReader() throws IOException;

    /**
     * 클라이언트 또는 마지막 프록시의 IP 주소를 반환합니다.
     *
     * 보안, 로깅, 통계 수집 등에 사용됩니다.
     * 프록시 서버 뒤에 있는 경우 실제 클라이언트 IP가 아닐 수 있습니다.
     *
     * @return 클라이언트 IP 주소
     */
    String getRemoteAddr();

    /**
     * 클라이언트 또는 마지막 프록시의 호스트명을 반환합니다.
     *
     * DNS 역방향 조회를 수행하므로 성능에 영향을 줄 수 있습니다.
     *
     * @return 클라이언트 호스트명
     */
    String getRemoteHost();

    /**
     * 서버가 요청을 받은 IP 주소를 반환합니다.
     *
     * 멀티홈 서버에서 어떤 네트워크 인터페이스로 요청이 들어왔는지 확인할 때 사용됩니다.
     *
     * @return 서버 IP 주소
     */
    String getLocalAddr();

    /**
     * 서버가 요청을 받은 호스트명을 반환합니다.
     *
     * @return 서버 호스트명
     */
    String getLocalName();

    /**
     * 서버가 요청을 받은 포트 번호를 반환합니다.
     *
     * @return 서버 포트 번호
     */
    int getLocalPort();

    /**
     * 클라이언트가 연결한 포트 번호를 반환합니다.
     *
     * @return 클라이언트 포트 번호
     */
    int getRemotePort();

    /**
     * 요청에서 사용된 프로토콜 이름과 버전을 반환합니다.
     *
     * 예: "HTTP/1.1", "HTTP/2.0"
     *
     * @return 프로토콜 정보
     */
    String getProtocol();

    /**
     * 요청에서 사용된 스키마를 반환합니다.
     *
     * 예: "http", "https", "ftp"
     *
     * @return 스키마 문자열
     */
    String getScheme();

    /**
     * 요청을 받은 서버의 이름을 반환합니다.
     *
     * Host 헤더의 값 또는 서버의 이름입니다.
     *
     * @return 서버 이름
     */
    String getServerName();

    /**
     * 요청을 받은 서버의 포트 번호를 반환합니다.
     *
     * @return 서버 포트 번호
     */
    int getServerPort();

    /**
     * 요청이 보안 채널(HTTPS)을 통해 이루어졌는지 확인합니다.
     *
     * @return HTTPS면 true, 그렇지 않으면 false
     */
    boolean isSecure();

    /**
     * 클라이언트가 선호하는 로케일을 반환합니다.
     *
     * Accept-Language 헤더를 분석하여 가장 선호하는 로케일을 반환합니다.
     * 다국어 애플리케이션에서 사용자의 언어 설정을 파악하는 데 사용됩니다.
     *
     * @return 클라이언트가 선호하는 로케일
     */
    Locale getLocale();

    /**
     * 클라이언트가 선호하는 모든 로케일을 우선순위 순으로 반환합니다.
     *
     * Accept-Language 헤더의 모든 값을 파싱하여 우선순위 순으로 정렬한 결과입니다.
     *
     * @return 클라이언트가 선호하는 로케일들의 Enumeration
     */
    Enumeration<Locale> getLocales();
}
