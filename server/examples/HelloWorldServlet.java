package server.examples;

import server.core.mini.*;
import server.core.http.*;
import server.core.routing.*;
import java.util.concurrent.CompletableFuture;

/**
 * Hello World 서블릿 예시
 */
public class HelloWorldServlet extends MiniServlet {

    @Override
    protected void doInit() throws Exception {
        getContext().log("HelloWorldServlet initialized");
    }

    @Override
    protected void doGet(MiniRequest request, MiniResponse response) throws Exception {
        String name = request.getParameter("name");
        if (name == null || name.trim().isEmpty()) {
            name = "World";
        }

        response.sendHtml(
                "<html>" +
                        "<head><title>Hello " + name + "</title></head>" +
                        "<body>" +
                        "<h1>Hello, " + name + "!</h1>" +
                        "<p>This is a response from HelloWorldServlet</p>" +
                        "<p>Request URI: " + request.getRequestURI() + "</p>" +
                        "<p>Method: " + request.getMethod() + "</p>" +
                        "<p>Query String: " + request.getQueryString() + "</p>" +
                        "</body>" +
                        "</html>"
        );
    }

    @Override
    protected void doPost(MiniRequest request, MiniResponse response) throws Exception {
        String message = request.getParameter("message");
        if (message == null) {
            message = "No message provided";
        }

        response.sendJson(
                "{ \"status\": \"success\", \"message\": \"" + message + "\", " +
                        "\"timestamp\": " + System.currentTimeMillis() + " }"
        );
    }
}