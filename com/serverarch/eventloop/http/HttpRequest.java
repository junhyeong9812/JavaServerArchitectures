package com.serverarch.eventloop.http;

import com.serverarch.common.http.HttpHeaders;
import java.util.Map;
import java.util.Collections;

/**
 * EventLoop 서버용 HttpRequest 인터페이스
 *
 * 기존 Traditional 서버의 HttpRequest 클래스와 구분하여
 * EventLoop 서버의 요구사항에 맞게 설계된 인터페이스
 */
public interface HttpRequest {

    /**
     * HTTP 메서드 반환
     * @return HTTP 메서드 (GET, POST, PUT, DELETE 등)
     */
    String getMethod();

    /**
     * 요청 경로 반환
     * @return 요청 경로 (예: "/users/123")
     */
    String getPath();

    /**
     * HTTP 헤더들 반환
     * @return HTTP 헤더 객체
     */
    HttpHeaders getHeaders();

    /**
     * 요청 바디를 바이트 배열로 반환
     * EventLoop에서는 바이너리 데이터 처리가 중요하므로 byte[] 사용
     * @return 요청 바디 바이트 배열
     */
    byte[] getBody();

    /**
     * 경로 파라미터들을 Map으로 반환
     * 예: "/users/{id}/posts/{postId}" -> {"id": "123", "postId": "456"}
     * @return 경로 파라미터 맵 (key: 파라미터 이름, value: 파라미터 값)
     */
    Map<String, String> getPathParameters();

    /**
     * 쿼리 파라미터들을 Map으로 반환
     * 예: "?name=john&age=25" -> {"name": "john", "age": "25"}
     * @return 쿼리 파라미터 맵
     */
    Map<String, String> getQueryParameters();

    /**
     * 특정 경로 파라미터 값 반환
     * @param name 파라미터 이름
     * @return 파라미터 값 또는 null
     */
    default String getPathParameter(String name) {
        Map<String, String> pathParams = getPathParameters();
        return pathParams != null ? pathParams.get(name) : null;
    }

    /**
     * 특정 쿼리 파라미터 값 반환
     * @param name 파라미터 이름
     * @return 파라미터 값 또는 null
     */
    default String getQueryParameter(String name) {
        Map<String, String> queryParams = getQueryParameters();
        return queryParams != null ? queryParams.get(name) : null;
    }

    /**
     * 요청 바디를 문자열로 반환 (편의 메서드)
     * @return 요청 바디를 UTF-8로 디코딩한 문자열
     */
    default String getBodyAsString() {
        byte[] body = getBody();
        return body != null ? new String(body, java.nio.charset.StandardCharsets.UTF_8) : "";
    }

    /**
     * 특정 헤더 값 반환
     * @param name 헤더 이름
     * @return 헤더 값 또는 null
     */
    default String getHeader(String name) {
        HttpHeaders headers = getHeaders();
        return headers != null ? headers.getFirst(name) : null;
    }

    /**
     * Content-Type 헤더 반환
     * @return Content-Type 값
     */
    default String getContentType() {
        HttpHeaders headers = getHeaders();
        return headers != null ? headers.getContentType() : null;
    }

    /**
     * Host 헤더 반환
     * @return Host 값
     */
    default String getHost() {
        HttpHeaders headers = getHeaders();
        return headers != null ? headers.getHost() : null;
    }

    /**
     * 경로 파라미터가 있는지 확인
     * @return 경로 파라미터가 하나 이상 있으면 true
     */
    default boolean hasPathParameters() {
        Map<String, String> pathParams = getPathParameters();
        return pathParams != null && !pathParams.isEmpty();
    }

    /**
     * 쿼리 파라미터가 있는지 확인
     * @return 쿼리 파라미터가 하나 이상 있으면 true
     */
    default boolean hasQueryParameters() {
        Map<String, String> queryParams = getQueryParameters();
        return queryParams != null && !queryParams.isEmpty();
    }

    /**
     * 특정 경로 파라미터가 존재하는지 확인
     * @param name 파라미터 이름
     * @return 해당 파라미터가 존재하면 true
     */
    default boolean hasPathParameter(String name) {
        return getPathParameter(name) != null;
    }

    /**
     * 특정 쿼리 파라미터가 존재하는지 확인
     * @param name 파라미터 이름
     * @return 해당 파라미터가 존재하면 true
     */
    default boolean hasQueryParameter(String name) {
        return getQueryParameter(name) != null;
    }
}