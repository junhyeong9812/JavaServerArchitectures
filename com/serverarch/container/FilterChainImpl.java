package com.serverarch.container;

// Jakarta Servlet API 임포트
// jakarta.servlet: Jakarta EE의 서블릿 API 패키지 (Java EE의 후속 버전)
// ServletRequest: HTTP 요청 정보를 담는 인터페이스
// ServletResponse: HTTP 응답 정보를 담는 인터페이스
// ServletException: 서블릿 처리 중 발생하는 예외 클래스
// Filter: 요청/응답을 가로채서 처리하는 필터 인터페이스
// FilterChain: 필터들을 연결하는 체인 인터페이스
import jakarta.servlet.*;

// Java I/O 라이브러리
// IOException: 입출력 작업 중 발생하는 예외 클래스
import java.io.IOException;

// Java 컬렉션 프레임워크
// ArrayList: 동적 배열을 구현한 리스트 클래스
// Collections: 컬렉션 관련 유틸리티 메서드를 제공하는 클래스
// List: 순서가 있는 요소들의 컬렉션을 나타내는 인터페이스
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 서블릿 필터 체인을 구현하는 클래스입니다.
 *
 * FilterChainImpl은 요청 처리 과정에서 여러 필터들을 순차적으로 실행하고,
 * 마지막에 서블릿을 실행하는 체인 패턴을 구현합니다.
 *
 * 필터 체인의 동작:
 * 1. 각 필터가 순서대로 실행됨
 * 2. 필터에서 doFilter() 호출 시 다음 필터로 진행
 * 3. 모든 필터 통과 후 서블릿 실행
 * 4. 응답 시 역순으로 필터 통과
 *
 * 스레드 안전성:
 * - 각 요청마다 새로운 체인 인스턴스 생성
 * - 상태 변경은 인스턴스별로 격리됨
 * - 필터 인스턴스는 공유되지만 체인 상태는 독립적
 */
public class FilterChainImpl implements FilterChain {
    // implements: 자바의 인터페이스 구현 키워드
    // FilterChain: Jakarta Servlet API의 필터 체인 인터페이스

    /**
     * 체인에 포함된 필터들의 정보
     * FilterManager.FilterInfo 타입을 사용하여 필터 메타데이터 포함
     *
     * final: 변수가 한 번 초기화된 후 재할당 불가능하도록 하는 키워드
     * List: Java Collection Framework의 리스트 인터페이스
     * FilterManager.FilterInfo: FilterManager 클래스의 중첩 클래스
     */
    private final List<FilterManager.FilterInfo> filters;

    /**
     * 체인의 마지막에 실행될 서블릿
     *
     * Servlet: Jakarta Servlet API의 서블릿 인터페이스
     */
    private final Servlet servlet;

    /**
     * 현재 실행 중인 필터의 인덱스
     * 0부터 시작하여 필터 수만큼 증가
     *
     * int: 자바의 기본 정수 타입 (32비트)
     */
    private int currentFilterIndex;

    /**
     * 체인 실행 완료 여부
     * 중복 실행 방지용 플래그
     *
     * boolean: 자바의 기본 불린 타입 (true/false)
     */
    private boolean chainCompleted;

    /**
     * FilterChainImpl 생성자
     *
     * @param filters 실행할 필터들의 리스트 (순서대로 실행됨)
     * @param servlet 마지막에 실행할 서블릿 (null 가능)
     */
    public FilterChainImpl(List<FilterManager.FilterInfo> filters, Servlet servlet) {
        // 필터 리스트 복사 (방어적 복사)
        // new ArrayList<>(): ArrayList 클래스의 생성자 호출
        // ArrayList: Java Collection Framework의 동적 배열 구현 클래스
        // filters != null: null 체크 연산자
        // ? : : 삼항 연산자 (조건 ? 참일때값 : 거짓일때값)
        // Collections.emptyList(): Collections 유틸리티 클래스의 빈 리스트 반환 메서드
        this.filters = new ArrayList<>(filters != null ? filters : Collections.emptyList());

        // this: 현재 객체 참조 키워드
        this.servlet = servlet;

        // 필드 초기화 - 0으로 시작
        this.currentFilterIndex = 0;

        // 필드 초기화 - false로 시작
        this.chainCompleted = false;
    }

    /**
     * {@inheritDoc}
     *
     * @inheritDoc: Javadoc 주석으로 부모 인터페이스의 문서를 상속받음
     *
     * 필터 체인의 다음 단계를 실행합니다.
     * 모든 필터를 거친 후에는 서블릿을 실행합니다.
     *
     * 실행 순서:
     * 1. 체인 완료 상태 확인
     * 2. 다음 필터 존재 여부 확인
     * 3. 필터 초기화 (필요시)
     * 4. 필터 실행 또는 서블릿 실행
     * 5. 예외 처리 및 상태 업데이트
     */
    @Override
    // @Override: 어노테이션으로 부모 메서드를 재정의함을 명시
    public void doFilter(ServletRequest request, ServletResponse response)
            throws IOException, ServletException {
        // throws: 메서드에서 발생할 수 있는 예외를 선언하는 키워드

        // 체인이 이미 완료된 경우 중복 실행 방지
        if (chainCompleted) {
            // if: 조건문 키워드
            // new IllegalStateException(): 잘못된 상태 예외 생성자 호출
            // IllegalStateException: Java의 런타임 예외 클래스
            // throw: 예외를 발생시키는 키워드
            throw new IllegalStateException("필터 체인이 이미 완료되었습니다. 중복 호출은 허용되지 않습니다.");
        }

        // 요청/응답 객체 null 검사
        if (request == null) {
            // IllegalArgumentException: 잘못된 인수 예외 클래스
            throw new IllegalArgumentException("ServletRequest가 null입니다");
        }
        if (response == null) {
            throw new IllegalArgumentException("ServletResponse가 null입니다");
        }

        // 다음 필터가 있는지 확인
        // filters.size(): List 인터페이스의 크기 반환 메서드
        if (currentFilterIndex < filters.size()) {
            // 다음 필터 실행
            // filters.get(): List 인터페이스의 인덱스 접근 메서드
            FilterManager.FilterInfo filterInfo = filters.get(currentFilterIndex);

            // 인덱스 증가 (후위 증가 연산자)
            currentFilterIndex++;

            try {
                // try: 예외 처리 블록의 시작

                // 필터 초기화 (필요한 경우)
                ensureFilterInitialized(filterInfo);

                // 필터 실행 (이 필터에서 다시 doFilter를 호출하면 다음 필터로 진행)
                // filterInfo.getFilter(): FilterInfo의 필터 인스턴스 반환 메서드
                // .doFilter(): Filter 인터페이스의 필터 실행 메서드
                // this: 현재 FilterChain 인스턴스를 필터에 전달
                filterInfo.getFilter().doFilter(request, response, this);

            } catch (ServletException e) {
                // catch: 예외 처리 블록
                // ServletException: 서블릿 관련 예외 클래스

                // ServletException은 그대로 전파
                throw e;
            } catch (IOException e) {
                // IOException: 입출력 예외 클래스

                // IOException도 그대로 전파
                throw e;
            } catch (RuntimeException e) {
                // RuntimeException: 런타임 예외의 최상위 클래스

                // RuntimeException을 ServletException으로 래핑
                // filterInfo.getFilterName(): FilterInfo의 필터 이름 반환 메서드
                throw new ServletException("필터 실행 중 런타임 오류 발생: " + filterInfo.getFilterName(), e);
            } catch (Exception e) {
                // Exception: 모든 예외의 최상위 클래스

                // 기타 예외들을 ServletException으로 래핑
                throw new ServletException("필터 실행 중 예상치 못한 오류 발생: " + filterInfo.getFilterName(), e);
            }
        } else {
            // 모든 필터를 통과했으므로 서블릿 실행
            executeServlet(request, response);
        }
    }

    /**
     * 서블릿을 실행합니다.
     *
     * 모든 필터를 통과한 후 최종적으로 서블릿을 실행합니다.
     * 서블릿이 null인 경우에도 정상 처리되도록 구현되었습니다.
     *
     * @param request 서블릿 요청 객체
     * @param response 서블릿 응답 객체
     * @throws IOException 입출력 오류 시
     * @throws ServletException 서블릿 실행 오류 시
     */
    private void executeServlet(ServletRequest request, ServletResponse response)
            throws IOException, ServletException {
        // private: 클래스 내부에서만 접근 가능한 접근 제어자

        try {
            // 체인 완료 마킹 (서블릿 실행 전에 마킹하여 중복 호출 방지)
            chainCompleted = true;

            // 서블릿 실행
            if (servlet != null) {
                // servlet.service(): Servlet 인터페이스의 서비스 메서드
                // HTTP 요청을 처리하는 핵심 메서드
                servlet.service(request, response);
            } else {
                // 서블릿이 null인 경우 404 응답 처리
                // 실제 구현에서는 컨테이너가 404 처리를 담당할 수 있음
                handleNoServlet(response);
            }

        } catch (ServletException e) {
            // ServletException은 그대로 전파
            throw e;
        } catch (IOException e) {
            // IOException도 그대로 전파
            throw e;
        } catch (RuntimeException e) {
            // RuntimeException을 ServletException으로 래핑
            throw new ServletException("서블릿 실행 중 런타임 오류 발생", e);
        } catch (Exception e) {
            // 기타 예외들을 ServletException으로 래핑
            throw new ServletException("서블릿 실행 중 예상치 못한 오류 발생", e);
        }
    }

    /**
     * 서블릿이 없는 경우를 처리합니다.
     *
     * @param response 응답 객체
     * @throws IOException 응답 처리 중 오류 시
     */
    private void handleNoServlet(ServletResponse response) throws IOException {
        // 기본적으로 아무것도 하지 않음
        // 실제 구현에서는 컨테이너가 404 응답을 처리
        // 필요시 ServletResponse를 HttpServletResponse로 캐스팅하여 처리 가능
    }

    /**
     * 필터가 초기화되었는지 확인하고 필요시 초기화합니다.
     *
     * 지연 초기화 패턴을 사용하여 실제 사용 시점에 초기화합니다.
     * 스레드 안전하게 중복 초기화를 방지합니다.
     *
     * @param filterInfo 초기화할 필터 정보
     * @throws ServletException 초기화 실패 시
     */
    private void ensureFilterInitialized(FilterManager.FilterInfo filterInfo) throws ServletException {
        // 이미 초기화되었거나 파괴된 경우 검사
        // filterInfo.isInitialized(): FilterInfo의 초기화 상태 확인 메서드
        if (filterInfo.isInitialized()) {
            return; // 이미 초기화됨 - return: 메서드 즉시 종료
        }

        // filterInfo.isDestroyed(): FilterInfo의 파괴 상태 확인 메서드
        if (filterInfo.isDestroyed()) {
            // filterInfo.getFilterName(): FilterInfo의 필터 이름 반환 메서드
            throw new ServletException("파괴된 필터는 사용할 수 없습니다: " + filterInfo.getFilterName());
        }

        // 동기화 블록으로 중복 초기화 방지
        // synchronized: 스레드 동기화 키워드 (한 번에 하나의 스레드만 접근 가능)
        synchronized (filterInfo) {
            // Double-check 패턴 - 동기화 블록 내에서 다시 한 번 확인
            if (filterInfo.isInitialized()) {
                return;
            }

            if (filterInfo.isDestroyed()) {
                throw new ServletException("파괴된 필터는 사용할 수 없습니다: " + filterInfo.getFilterName());
            }

            try {
                // 필터 초기화 실행
                // filterInfo.getFilter(): FilterInfo의 필터 인스턴스 반환 메서드
                // .init(): Filter 인터페이스의 초기화 메서드
                // filterInfo.getFilterConfig(): FilterInfo의 필터 설정 반환 메서드
                filterInfo.getFilter().init(filterInfo.getFilterConfig());

                // 초기화 상태 업데이트 (FilterInfo의 패키지 프라이빗 메서드 사용)
                // filterInfo.setInitialized(): FilterInfo의 초기화 상태 설정 메서드
                filterInfo.setInitialized(true);

            } catch (ServletException e) {
                // ServletException은 그대로 전파
                throw e;
            } catch (Exception e) {
                // 기타 예외를 ServletException으로 래핑
                throw new ServletException("필터 초기화 실패: " + filterInfo.getFilterName(), e);
            }
        }
    }

    // ========== 디버깅 및 모니터링 메서드들 ==========

    /**
     * 체인에 포함된 필터들의 정보를 반환합니다.
     *
     * @return 읽기 전용 필터 정보 리스트
     */
    public List<FilterManager.FilterInfo> getFilters() {
        // Collections.unmodifiableList(): Collections 유틸리티 클래스의 읽기 전용 리스트 반환 메서드
        // 반환된 리스트는 수정할 수 없어 안전함
        return Collections.unmodifiableList(filters);
    }

    /**
     * 실행할 서블릿을 반환합니다.
     *
     * @return 서블릿 인스턴스 (null 가능)
     */
    public Servlet getServlet() {
        return servlet;
    }

    /**
     * 체인 실행 완료 여부를 반환합니다.
     *
     * @return 완료되면 true, 실행 중이거나 실행 전이면 false
     */
    public boolean isChainCompleted() {
        return chainCompleted;
    }

    /**
     * 현재 필터 인덱스를 반환합니다.
     *
     * @return 현재 실행 중인 필터의 인덱스
     */
    public int getCurrentFilterIndex() {
        return currentFilterIndex;
    }

    /**
     * 남은 필터 개수를 반환합니다.
     *
     * @return 아직 실행되지 않은 필터의 개수
     */
    public int getRemainingFilterCount() {
        // Math.max(): Math 클래스의 최댓값 반환 정적 메서드
        // 0과 (전체 필터 수 - 현재 인덱스) 중 큰 값 반환 (음수 방지)
        return Math.max(0, filters.size() - currentFilterIndex);
    }

    /**
     * 체인 상태 정보를 문자열로 반환합니다.
     *
     * @return 체인 상태 정보
     */
    @Override
    public String toString() {
        // String.format(): String 클래스의 형식화된 문자열 생성 정적 메서드
        // %d: 정수 포맷 지정자
        // %s: 문자열 포맷 지정자
        // servlet != null: null 체크
        // servlet.getClass(): Object 클래스의 클래스 정보 반환 메서드
        // .getSimpleName(): Class 클래스의 단순 클래스명 반환 메서드
        return String.format(
                "FilterChain[filters=%d, currentIndex=%d, completed=%s, servlet=%s]",
                filters.size(),
                currentFilterIndex,
                chainCompleted,
                servlet != null ? servlet.getClass().getSimpleName() : "null"
        );
    }
}