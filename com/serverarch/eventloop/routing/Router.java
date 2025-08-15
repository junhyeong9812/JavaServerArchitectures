package com.serverarch.eventloop.routing;

import com.serverarch.eventloop.http.HttpRequest;
import com.serverarch.eventloop.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * EventLoop 서버용 Router 인터페이스
 *
 * 비동기 요청 처리를 위해 CompletableFuture를 반환하는 라우터
 * 기존 Traditional/Hybrid 서버의 동기식 Router와 구분
 */
public interface Router {

    /**
     * HTTP 요청을 비동기로 라우팅하고 처리
     *
     * @param request HTTP 요청
     * @return 비동기 HTTP 응답 Future
     */
    CompletableFuture<HttpResponse> route(HttpRequest request);

    /**
     * GET 요청 핸들러 등록
     * @param path 경로 패턴
     * @param handler 비동기 핸들러
     * @return 현재 Router 인스턴스 (메서드 체이닝용)
     */
    Router get(String path, AsyncRouteHandler handler);

    /**
     * POST 요청 핸들러 등록
     * @param path 경로 패턴
     * @param handler 비동기 핸들러
     * @return 현재 Router 인스턴스 (메서드 체이닝용)
     */
    Router post(String path, AsyncRouteHandler handler);

    /**
     * PUT 요청 핸들러 등록
     * @param path 경로 패턴
     * @param handler 비동기 핸들러
     * @return 현재 Router 인스턴스 (메서드 체이닝용)
     */
    Router put(String path, AsyncRouteHandler handler);

    /**
     * DELETE 요청 핸들러 등록
     * @param path 경로 패턴
     * @param handler 비동기 핸들러
     * @return 현재 Router 인스턴스 (메서드 체이닝용)
     */
    Router delete(String path, AsyncRouteHandler handler);

    /**
     * HEAD 요청 핸들러 등록
     * @param path 경로 패턴
     * @param handler 비동기 핸들러
     * @return 현재 Router 인스턴스 (메서드 체이닝용)
     */
    Router head(String path, AsyncRouteHandler handler);

    /**
     * OPTIONS 요청 핸들러 등록
     * @param path 경로 패턴
     * @param handler 비동기 핸들러
     * @return 현재 Router 인스턴스 (메서드 체이닝용)
     */
    Router options(String path, AsyncRouteHandler handler);

    /**
     * 모든 HTTP 메서드에 대한 핸들러 등록
     * @param path 경로 패턴
     * @param handler 비동기 핸들러
     * @return 현재 Router 인스턴스 (메서드 체이닝용)
     */
    Router all(String path, AsyncRouteHandler handler);

    /**
     * 특정 HTTP 메서드에 대한 핸들러 등록
     * @param method HTTP 메서드
     * @param path 경로 패턴
     * @param handler 비동기 핸들러
     * @return 현재 Router 인스턴스 (메서드 체이닝용)
     */
    Router route(String method, String path, AsyncRouteHandler handler);

    /**
     * 기본 핸들러 설정 (404 처리용)
     * @param handler 기본 핸들러
     * @return 현재 Router 인스턴스 (메서드 체이닝용)
     */
    Router setDefaultHandler(AsyncRouteHandler handler);

    /**
     * 등록된 라우트 수 반환
     * @return 라우트 수
     */
    int getRouteCount();

    /**
     * 라우터 상태 정보 반환
     * @return 상태 정보 문자열
     */
    String getStatus();
}