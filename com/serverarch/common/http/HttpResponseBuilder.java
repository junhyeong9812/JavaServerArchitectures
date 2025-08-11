package com.serverarch.common.http;

import com.serverarch.traditional.HttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpResponseBuilder {
    
    public static byte[] buildResponse(HttpResponse response) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // 상태 라인 작성
            String statusLine = String.format("HTTP/1.1 %d %s\r\n", 
                response.getStatus().getCode(), 
                response.getStatus().getReasonPhrase());
            baos.write(statusLine.getBytes(StandardCharsets.UTF_8));
            
            // 헤더 작성
            for (String name : response.getHeaderNames()) {
                for (String value : response.getHeaders(name)) {
                    String headerLine = name + ": " + value + "\r\n";
                    baos.write(headerLine.getBytes(StandardCharsets.UTF_8));
                }
            }
            
            // 헤더와 바디 구분자
            baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
            
            // 바디 작성
            if (response.getBody().length > 0) {
                baos.write(response.getBody());
            }
            
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to build HTTP response", e);
        }
    }
    
    // 편의 메소드들
    public static HttpResponse ok(String body) {
        HttpResponse response = new HttpResponse(HttpStatus.OK);
        response.setBody(body);
        response.setContentType("text/plain; charset=UTF-8");
        return response;
    }
    
    public static HttpResponse json(String json) {
        HttpResponse response = new HttpResponse(HttpStatus.OK);
        response.setBody(json);
        response.setContentType("application/json; charset=UTF-8");
        return response;
    }
    
    public static HttpResponse notFound(String message) {
        HttpResponse response = new HttpResponse(HttpStatus.NOT_FOUND);
        response.setBody(message != null ? message : "Not Found");
        response.setContentType("text/plain; charset=UTF-8");
        return response;
    }
    
    public static HttpResponse serverError(String message) {
        HttpResponse response = new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR);
        response.setBody(message != null ? message : "Internal Server Error");
        response.setContentType("text/plain; charset=UTF-8");
        return response;
    }
}
