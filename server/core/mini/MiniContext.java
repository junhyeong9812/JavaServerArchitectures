package server.core.mini;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 미니 서블릿 컨텍스트
 * 애플리케이션 설정 및 전역 데이터 관리
 */
public class MiniContext {

    private final Map<String, Object> attributes;
    private final Map<String, String> initParameters;
    private final String contextPath;
    private final long startTime;

    public MiniContext(String contextPath) {
        this.contextPath = contextPath != null ? contextPath : "";
        this.attributes = new ConcurrentHashMap<>();
        this.initParameters = new ConcurrentHashMap<>();
        this.startTime = System.currentTimeMillis();
    }

    // === 속성 관리 ===

    /**
     * 컨텍스트 속성 설정
     */
    public void setAttribute(String name, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("Attribute name cannot be null");
        }
        if (value == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, value);
        }
    }

    /**
     * 컨텍스트 속성 가져오기
     */
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    /**
     * 타입 안전한 속성 가져오기
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name, Class<T> type) {
        Object value = attributes.get(name);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 속성 제거
     */
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    /**
     * 모든 속성명 가져오기
     */
    public Set<String> getAttributeNames() {
        return new HashSet<>(attributes.keySet());
    }

    // === 초기화 파라미터 관리 ===

    /**
     * 초기화 파라미터 설정
     */
    public void setInitParameter(String name, String value) {
        if (name == null) {
            throw new IllegalArgumentException("Parameter name cannot be null");
        }
        if (value == null) {
            initParameters.remove(name);
        } else {
            initParameters.put(name, value);
        }
    }

    /**
     * 초기화 파라미터 가져오기
     */
    public String getInitParameter(String name) {
        return initParameters.get(name);
    }

    /**
     * 모든 초기화 파라미터명 가져오기
     */
    public Set<String> getInitParameterNames() {
        return new HashSet<>(initParameters.keySet());
    }

    // === 컨텍스트 정보 ===

    /**
     * 컨텍스트 경로 반환
     */
    public String getContextPath() {
        return contextPath;
    }

    /**
     * 서버 시작 시간 반환
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * 서버 가동 시간 반환 (밀리초)
     */
    public long getUpTime() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 서버 정보 반환
     */
    public String getServerInfo() {
        return "JavaServerArchitectures/1.0";
    }

    /**
     * 로그 메시지 출력
     */
    public void log(String message) {
        System.out.println("[" + new Date() + "] " + message);
    }

    /**
     * 예외와 함께 로그 메시지 출력
     */
    public void log(String message, Throwable throwable) {
        System.err.println("[" + new Date() + "] " + message);
        throwable.printStackTrace();
    }

    @Override
    public String toString() {
        return String.format("MiniContext{contextPath='%s', upTime=%dms, attributes=%d}",
                contextPath, getUpTime(), attributes.size());
    }
}