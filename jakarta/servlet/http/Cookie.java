package jakarta.servlet.http;

import java.io.Serializable;

/**
 * HTTP 쿠키를 나타내는 클래스입니다.
 *
 * Cookie는 서버가 클라이언트에게 저장하도록 요청하는 작은 데이터 조각입니다.
 * 클라이언트는 이후 요청 시 쿠키를 다시 서버로 전송합니다.
 *
 * 쿠키의 주요 용도:
 * - 세션 관리 (로그인, 장바구니 등)
 * - 개인화 (사용자 설정, 테마 등)
 * - 추적 (사용자 행동 분석)
 *
 * 쿠키 보안 고려사항:
 * - 민감한 정보는 쿠키에 저장하지 않기
 * - HTTPS에서만 전송되도록 Secure 플래그 설정
 * - JavaScript 접근을 방지하려면 HttpOnly 플래그 설정
 * - 적절한 SameSite 정책 설정으로 CSRF 공격 방지
 */
public class Cookie implements Cloneable, Serializable {

    /**
     * 직렬화를 위한 버전 ID입니다.
     */
    private static final long serialVersionUID = 1L;

    /**
     * 쿠키의 이름입니다.
     * 이름은 생성 후 변경할 수 없습니다.
     */
    private final String name;

    /**
     * 쿠키의 값입니다.
     */
    private String value;

    /**
     * 쿠키의 설명 또는 목적을 나타내는 문자열입니다.
     */
    private String comment;

    /**
     * 쿠키의 도메인 속성입니다.
     * 쿠키가 전송될 도메인을 제한합니다.
     */
    private String domain;

    /**
     * 쿠키의 최대 생존 시간(초)입니다.
     * -1은 브라우저 세션이 끝날 때까지 유지됨을 의미합니다.
     */
    private int maxAge = -1;

    /**
     * 쿠키의 경로 속성입니다.
     * 쿠키가 전송될 URL 경로를 제한합니다.
     */
    private String path;

    /**
     * 쿠키의 보안 플래그입니다.
     * true면 HTTPS 연결에서만 쿠키가 전송됩니다.
     */
    private boolean secure;

    /**
     * 쿠키의 버전입니다.
     * RFC 2109에서 정의된 쿠키 버전을 나타냅니다.
     */
    private int version = 0;

    /**
     * HTTP-only 플래그입니다.
     * true면 JavaScript에서 쿠키에 접근할 수 없습니다.
     */
    private boolean httpOnly = false;

    /**
     * 지정된 이름과 값으로 쿠키를 생성합니다.
     *
     * 쿠키 이름은 생성 후 변경할 수 없으므로 신중히 선택해야 합니다.
     * RFC 2616에 따라 쿠키 이름과 값에는 특정 문자들이 허용되지 않습니다.
     *
     * 허용되지 않는 문자들:
     * - 공백, 탭, 개행문자
     * - 제어 문자 (ASCII 0-31, 127)
     * - 특수 문자: ( ) < > @ , ; : \ " / [ ] ? = { }
     *
     * @param name 쿠키 이름 (null이나 빈 문자열 불가)
     * @param value 쿠키 값
     * @throws IllegalArgumentException 이름이 null이거나 유효하지 않은 경우
     */
    public Cookie(String name, String value) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("쿠키 이름은 null이거나 빈 문자열일 수 없습니다");
        }

        // RFC 2616에 따른 토큰 문자 검증
        if (!isValidName(name)) {
            throw new IllegalArgumentException("유효하지 않은 쿠키 이름: " + name);
        }

        this.name = name;
        this.value = value;
    }

    /**
     * 쿠키 이름이 RFC 2616 토큰 규칙에 맞는지 검증합니다.
     *
     * @param name 검증할 이름
     * @return 유효하면 true, 그렇지 않으면 false
     */
    private boolean isValidName(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            // 제어 문자나 특수 문자는 허용되지 않음
            if (c <= 0x20 || c >= 0x7f || "()<>@,;:\\\"/[]?={}".indexOf(c) != -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * 쿠키의 설명을 설정합니다.
     *
     * 설명은 쿠키의 목적이나 용도를 나타내며, 일부 브라우저에서
     * 사용자에게 표시될 수 있습니다. (현재는 대부분 사용되지 않음)
     *
     * @param purpose 쿠키의 목적이나 설명
     */
    public void setComment(String purpose) {
        this.comment = purpose;
    }

    /**
     * 쿠키의 설명을 반환합니다.
     *
     * @return 쿠키의 설명, 설정되지 않았으면 null
     */
    public String getComment() {
        return comment;
    }

    /**
     * 쿠키의 도메인 속성을 설정합니다.
     *
     * 도메인은 쿠키가 전송될 서버를 제한합니다.
     * 도메인을 설정하지 않으면 쿠키를 설정한 서버에서만 사용됩니다.
     *
     * 도메인 설정 규칙:
     * - ".example.com"로 설정하면 example.com과 모든 서브도메인에서 사용 가능
     * - "www.example.com"으로 설정하면 해당 호스트에서만 사용 가능
     * - 보안상 이유로 다른 도메인의 쿠키는 설정할 수 없음
     *
     * @param domain 쿠키가 유효한 도메인
     */
    public void setDomain(String domain) {
        this.domain = domain != null ? domain.toLowerCase() : null;
    }

    /**
     * 쿠키의 도메인 속성을 반환합니다.
     *
     * @return 쿠키의 도메인, 설정되지 않았으면 null
     */
    public String getDomain() {
        return domain;
    }

    /**
     * 쿠키의 최대 생존 시간을 초 단위로 설정합니다.
     *
     * 쿠키의 생명주기를 제어합니다:
     * - 양수: 지정된 초 후에 쿠키 만료
     * - 0: 즉시 쿠키 삭제
     * - 음수: 브라우저 세션이 끝날 때까지 유지 (세션 쿠키)
     *
     * 사용 예시:
     * cookie.setMaxAge(60 * 60 * 24 * 7); // 7일
     * cookie.setMaxAge(0); // 즉시 삭제
     * cookie.setMaxAge(-1); // 세션 쿠키
     *
     * @param expiry 최대 생존 시간 (초)
     */
    public void setMaxAge(int expiry) {
        this.maxAge = expiry;
    }

    /**
     * 쿠키의 최대 생존 시간을 반환합니다.
     *
     * @return 최대 생존 시간 (초), 세션 쿠키면 -1
     */
    public int getMaxAge() {
        return maxAge;
    }

    /**
     * 쿠키의 경로 속성을 설정합니다.
     *
     * 경로는 쿠키가 전송될 URL 경로를 제한합니다.
     * 설정된 경로와 그 하위 경로에서만 쿠키가 전송됩니다.
     *
     * 경로 설정 예시:
     * - "/" : 모든 경로에서 쿠키 전송
     * - "/admin" : /admin과 /admin/* 경로에서만 전송
     * - "/shop/cart" : /shop/cart와 /shop/cart/* 경로에서만 전송
     *
     * 경로를 설정하지 않으면 쿠키를 설정한 페이지의 경로가 기본값이 됩니다.
     *
     * @param uri 쿠키가 유효한 경로
     */
    public void setPath(String uri) {
        this.path = uri;
    }

    /**
     * 쿠키의 경로 속성을 반환합니다.
     *
     * @return 쿠키의 경로, 설정되지 않았으면 null
     */
    public String getPath() {
        return path;
    }

    /**
     * 쿠키의 보안 플래그를 설정합니다.
     *
     * 보안 플래그가 true로 설정되면 쿠키는 HTTPS 연결에서만 전송됩니다.
     * 민감한 정보를 포함한 쿠키는 반드시 보안 플래그를 설정해야 합니다.
     *
     * 보안 고려사항:
     * - 로그인 세션 쿠키는 항상 보안 플래그 설정
     * - 개발 환경에서는 HTTP를 사용할 수 있지만, 프로덕션에서는 HTTPS 필수
     * - Mixed Content 문제를 피하기 위해 전체 사이트를 HTTPS로 운영 권장
     *
     * @param flag true면 HTTPS에서만 전송, false면 HTTP/HTTPS 모두에서 전송
     */
    public void setSecure(boolean flag) {
        this.secure = flag;
    }

    /**
     * 쿠키의 보안 플래그를 반환합니다.
     *
     * @return 보안 플래그가 설정되어 있으면 true, 그렇지 않으면 false
     */
    public boolean getSecure() {
        return secure;
    }

    /**
     * 쿠키의 이름을 반환합니다.
     *
     * 쿠키 이름은 생성 시에 설정되며 이후 변경할 수 없습니다.
     *
     * @return 쿠키의 이름
     */
    public String getName() {
        return name;
    }

    /**
     * 쿠키의 값을 설정합니다.
     *
     * 쿠키 값에는 특수 문자가 포함될 수 있지만, 세미콜론, 쉼표, 공백 등은
     * 쿠키 구문 분석에 문제를 일으킬 수 있으므로 주의해야 합니다.
     *
     * 복잡한 데이터를 저장할 때는 URL 인코딩이나 Base64 인코딩을 고려하세요.
     *
     * @param newValue 새로운 쿠키 값
     */
    public void setValue(String newValue) {
        this.value = newValue;
    }

    /**
     * 쿠키의 값을 반환합니다.
     *
     * @return 쿠키의 값
     */
    public String getValue() {
        return value;
    }

    /**
     * 쿠키의 버전을 반환합니다.
     *
     * RFC 2109에서 정의된 쿠키 버전입니다.
     * - 버전 0: 원래 Netscape 쿠키 사양 (기본값)
     * - 버전 1: RFC 2109 사양
     *
     * @return 쿠키 버전
     */
    public int getVersion() {
        return version;
    }

    /**
     * 쿠키의 버전을 설정합니다.
     *
     * 일반적으로 기본값인 0을 사용하며, 특별한 이유가 없다면 변경하지 않습니다.
     *
     * @param v 쿠키 버전 (0 또는 1)
     * @throws IllegalArgumentException 버전이 0 또는 1이 아닌 경우
     */
    public void setVersion(int v) {
        if (v != 0 && v != 1) {
            throw new IllegalArgumentException("쿠키 버전은 0 또는 1이어야 합니다");
        }
        this.version = v;
    }

    /**
     * HTTP-only 플래그를 설정합니다.
     *
     * HTTP-only 플래그가 true로 설정되면 JavaScript에서 쿠키에 접근할 수 없습니다.
     * 이는 XSS(Cross-Site Scripting) 공격을 방지하는 중요한 보안 기능입니다.
     *
     * 세션 쿠키나 인증 관련 쿠키는 반드시 HTTP-only로 설정해야 합니다.
     *
     * 사용 예시:
     * Cookie sessionCookie = new Cookie("JSESSIONID", sessionId);
     * sessionCookie.setHttpOnly(true); // XSS 공격 방지
     *
     * @param httpOnly true면 JavaScript에서 접근 불가, false면 접근 가능
     */
    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
    }

    /**
     * HTTP-only 플래그를 반환합니다.
     *
     * @return HTTP-only가 설정되어 있으면 true, 그렇지 않으면 false
     */
    public boolean isHttpOnly() {
        return httpOnly;
    }

    /**
     * 쿠키를 복제합니다.
     *
     * 현재 쿠키와 동일한 속성을 가진 새로운 Cookie 객체를 생성합니다.
     *
     * @return 복제된 Cookie 객체
     */
    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            // Cookie는 Cloneable을 구현하므로 이 예외는 발생하지 않아야 함
            throw new RuntimeException("쿠키 복제 중 오류 발생", e);
        }
    }

    /**
     * 쿠키의 문자열 표현을 반환합니다.
     *
     * 디버깅이나 로깅 목적으로 사용됩니다.
     * 보안상 민감한 값은 마스킹될 수 있습니다.
     *
     * @return 쿠키의 문자열 표현
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Cookie[name=").append(name);
        sb.append(", value=").append(value != null ? "***" : "null"); // 값은 보안상 마스킹
        if (domain != null) {
            sb.append(", domain=").append(domain);
        }
        if (path != null) {
            sb.append(", path=").append(path);
        }
        sb.append(", maxAge=").append(maxAge);
        sb.append(", secure=").append(secure);
        sb.append(", httpOnly=").append(httpOnly);
        sb.append(", version=").append(version);
        sb.append("]");
        return sb.toString();
    }
}
