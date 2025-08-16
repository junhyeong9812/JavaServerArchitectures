package server.examples;

import server.core.mini.*;
import server.core.http.*;
import server.core.routing.*;
import java.util.concurrent.CompletableFuture;

/**
 * 사용자 API 서블릿 예시 (RESTful)
 */
public class UserApiServlet extends MiniAsyncServlet {

    @Override
    protected void doInit() throws Exception {
        getContext().log("UserApiServlet initialized");

        // 가상의 사용자 데이터를 컨텍스트에 저장
        getContext().setAttribute("users", new java.util.concurrent.ConcurrentHashMap<String, User>());
    }

    @Override
    protected CompletableFuture<HttpResponse> doGetAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.supplyAsync(() -> {
            String userId = request.getPathParameter("id");

            if (userId == null) {
                // GET /api/users - 모든 사용자 목록
                response.sendJson("{ \"users\": [\"user1\", \"user2\", \"user3\"] }");
            } else {
                // GET /api/users/{id} - 특정 사용자
                User user = findUser(userId);
                if (user != null) {
                    response.sendJson(user.toJson());
                } else {
                    response.sendError(HttpStatus.NOT_FOUND, "User not found");
                }
            }

            return response.build();
        });
    }

    @Override
    protected CompletableFuture<HttpResponse> doPostAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.supplyAsync(() -> {
            // POST /api/users - 새 사용자 생성
            try {
                String body = request.getBody();
                User newUser = User.fromJson(body);

                @SuppressWarnings("unchecked")
                java.util.Map<String, User> users =
                        (java.util.Map<String, User>) getContext().getAttribute("users");
                users.put(newUser.getId(), newUser);

                response.setStatus(HttpStatus.CREATED);
                response.sendJson(newUser.toJson());

            } catch (Exception e) {
                response.sendError(HttpStatus.BAD_REQUEST, "Invalid user data");
            }

            return response.build();
        });
    }

    @Override
    protected CompletableFuture<HttpResponse> doPutAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.supplyAsync(() -> {
            String userId = request.getPathParameter("id");
            if (userId == null) {
                response.sendError(HttpStatus.BAD_REQUEST, "User ID required");
                return response.build();
            }

            try {
                String body = request.getBody();
                User updatedUser = User.fromJson(body);
                updatedUser.setId(userId); // ID는 경로에서 가져옴

                @SuppressWarnings("unchecked")
                java.util.Map<String, User> users =
                        (java.util.Map<String, User>) getContext().getAttribute("users");

                if (users.containsKey(userId)) {
                    users.put(userId, updatedUser);
                    response.sendJson(updatedUser.toJson());
                } else {
                    response.sendError(HttpStatus.NOT_FOUND, "User not found");
                }

            } catch (Exception e) {
                response.sendError(HttpStatus.BAD_REQUEST, "Invalid user data");
            }

            return response.build();
        });
    }

    @Override
    protected CompletableFuture<HttpResponse> doDeleteAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.supplyAsync(() -> {
            String userId = request.getPathParameter("id");
            if (userId == null) {
                response.sendError(HttpStatus.BAD_REQUEST, "User ID required");
                return response.build();
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, User> users =
                    (java.util.Map<String, User>) getContext().getAttribute("users");

            if (users.remove(userId) != null) {
                response.setStatus(HttpStatus.NO_CONTENT);
            } else {
                response.sendError(HttpStatus.NOT_FOUND, "User not found");
            }

            return response.build();
        });
    }

    private User findUser(String userId) {
        @SuppressWarnings("unchecked")
        java.util.Map<String, User> users =
                (java.util.Map<String, User>) getContext().getAttribute("users");
        return users.get(userId);
    }

    /**
     * 간단한 User 모델
     */
    public static class User {
        private String id;
        private String name;
        private String email;

        public User() {}

        public User(String id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }

        public String toJson() {
            return String.format(
                    "{ \"id\": \"%s\", \"name\": \"%s\", \"email\": \"%s\" }",
                    id, name, email
            );
        }

        public static User fromJson(String json) {
            // 간단한 JSON 파싱 (실제로는 Jackson 등 사용)
            User user = new User();
            json = json.replaceAll("[{}\"]", "");
            String[] pairs = json.split(",");

            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();

                    switch (key) {
                        case "id":
                            user.setId(value);
                            break;
                        case "name":
                            user.setName(value);
                            break;
                        case "email":
                            user.setEmail(value);
                            break;
                    }
                }
            }

            return user;
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
}