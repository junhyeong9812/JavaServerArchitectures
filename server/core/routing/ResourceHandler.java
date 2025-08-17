package server.core.routing;

/**
 * RESTful 리소스 핸들러
 *
 * 역할:
 * - RESTful API의 표준 CRUD 작업을 위한 핸들러들을 그룹화
 * - 하나의 리소스에 대한 모든 HTTP 메서드 핸들러를 체계적으로 관리
 * - 빌더 패턴을 사용하여 유연한 설정 제공
 * - 라우터와 연동하여 자동으로 RESTful 라우트 생성
 *
 * RESTful 규칙:
 * - GET /resources        -> index()  (목록 조회)
 * - GET /resources/{id}   -> show()   (개별 조회)
 * - POST /resources       -> create() (새 리소스 생성)
 * - PUT /resources/{id}   -> update() (전체 수정)
 * - DELETE /resources/{id} -> delete() (삭제)
 *
 * 사용 예시:
 * ResourceHandler userHandler = new ResourceHandler()
 *     .index(request -> userService.getAllUsersAsync())
 *     .show(request -> userService.getUserByIdAsync(request.getPathParameter("id")))
 *     .create(request -> userService.createUserAsync(request.getBody()))
 *     .update(request -> userService.updateUserAsync(request.getPathParameter("id"), request.getBody()))
 *     .delete(request -> userService.deleteUserAsync(request.getPathParameter("id")));
 *
 * router.resource("/users", userHandler);
 */
public class ResourceHandler {

    // RESTful API의 5가지 기본 작업에 대한 핸들러들
    // 각 핸들러는 선택적으로 설정 가능 (null이면 해당 작업을 지원하지 않음)

    /**
     * GET /resources - 리소스 목록 조회
     *
     * 용도:
     * - 모든 리소스 목록 반환
     * - 페이징, 필터링, 정렬 지원
     * - 검색 기능 제공
     *
     * 예시 응답:
     * [
     *   {"id": 1, "name": "John"},
     *   {"id": 2, "name": "Jane"}
     * ]
     */
    private RouteHandler indexHandler;    // GET /resources

    /**
     * GET /resources/{id} - 개별 리소스 조회
     *
     * 용도:
     * - 특정 ID의 리소스 상세 정보 반환
     * - 존재하지 않으면 404 Not Found
     *
     * 예시 응답:
     * {"id": 1, "name": "John", "email": "john@example.com"}
     */
    private RouteHandler showHandler;     // GET /resources/{id}

    /**
     * POST /resources - 새 리소스 생성
     *
     * 용도:
     * - 새로운 리소스 생성
     * - 요청 본문에서 데이터 읽기
     * - 생성된 리소스 정보 반환 (201 Created)
     *
     * 예시 요청:
     * {"name": "Bob", "email": "bob@example.com"}
     *
     * 예시 응답:
     * {"id": 3, "name": "Bob", "email": "bob@example.com"}
     */
    private RouteHandler createHandler;   // POST /resources

    /**
     * PUT /resources/{id} - 기존 리소스 전체 수정
     *
     * 용도:
     * - 기존 리소스를 완전히 대체
     * - 부분 수정이 아닌 전체 교체
     * - 존재하지 않으면 404 Not Found 또는 새로 생성
     *
     * 예시 요청:
     * {"name": "John Updated", "email": "john.new@example.com"}
     */
    private RouteHandler updateHandler;   // PUT /resources/{id}

    /**
     * DELETE /resources/{id} - 리소스 삭제
     *
     * 용도:
     * - 특정 ID의 리소스 삭제
     * - 성공 시 204 No Content 또는 200 OK
     * - 존재하지 않으면 404 Not Found
     */
    private RouteHandler deleteHandler;   // DELETE /resources/{id}

    /**
     * 기본 생성자
     * 모든 핸들러가 null로 초기화됨
     */
    public ResourceHandler() {
        // 모든 핸들러는 기본적으로 null
        // 필요한 핸들러만 선택적으로 설정 가능
    }

    // === 빌더 패턴 메서드들 ===
    // 각 메서드는 this를 반환하여 메서드 체이닝 지원

    /**
     * 인덱스 핸들러 설정 (GET /resources)
     *
     * @param handler 리소스 목록을 처리할 핸들러
     * @return 체이닝을 위한 this 객체
     *
     * 사용 예시:
     * resourceHandler.index(request -> {
     *     List<User> users = userService.findAll();
     *     return CompletableFuture.completedFuture(
     *         HttpResponse.json(users.toJson()));
     * });
     */
    public ResourceHandler index(RouteHandler handler) {
        this.indexHandler = handler;
        return this;  // 메서드 체이닝을 위해 자기 자신 반환
    }

    /**
     * 조회 핸들러 설정 (GET /resources/{id})
     *
     * @param handler 개별 리소스를 처리할 핸들러
     * @return 체이닝을 위한 this 객체
     *
     * 사용 예시:
     * resourceHandler.show(request -> {
     *     String id = request.getAttribute("path.id", String.class);
     *     return userService.findByIdAsync(id)
     *         .thenApply(user -> user != null
     *             ? HttpResponse.json(user.toJson())
     *             : HttpResponse.notFound());
     * });
     */
    public ResourceHandler show(RouteHandler handler) {
        this.showHandler = handler;
        return this;
    }

    /**
     * 생성 핸들러 설정 (POST /resources)
     *
     * @param handler 새 리소스 생성을 처리할 핸들러
     * @return 체이닝을 위한 this 객체
     *
     * 사용 예시:
     * resourceHandler.create(request -> {
     *     if (!request.isJsonRequest()) {
     *         return CompletableFuture.completedFuture(
     *             HttpResponse.badRequest("JSON required"));
     *     }
     *
     *     User user = User.fromJson(request.getBodyAsString());
     *     return userService.createAsync(user)
     *         .thenApply(savedUser -> {
     *             HttpResponse response = HttpResponse.json(savedUser.toJson());
     *             response.setStatus(HttpStatus.CREATED);
     *             return response;
     *         });
     * });
     */
    public ResourceHandler create(RouteHandler handler) {
        this.createHandler = handler;
        return this;
    }

    /**
     * 수정 핸들러 설정 (PUT /resources/{id})
     *
     * @param handler 리소스 수정을 처리할 핸들러
     * @return 체이닝을 위한 this 객체
     *
     * 사용 예시:
     * resourceHandler.update(request -> {
     *     String id = request.getAttribute("path.id", String.class);
     *     User updateData = User.fromJson(request.getBodyAsString());
     *
     *     return userService.updateAsync(id, updateData)
     *         .thenApply(updatedUser -> updatedUser != null
     *             ? HttpResponse.json(updatedUser.toJson())
     *             : HttpResponse.notFound());
     * });
     */
    public ResourceHandler update(RouteHandler handler) {
        this.updateHandler = handler;
        return this;
    }

    /**
     * 삭제 핸들러 설정 (DELETE /resources/{id})
     *
     * @param handler 리소스 삭제를 처리할 핸들러
     * @return 체이닝을 위한 this 객체
     *
     * 사용 예시:
     * resourceHandler.delete(request -> {
     *     String id = request.getAttribute("path.id", String.class);
     *
     *     return userService.deleteAsync(id)
     *         .thenApply(deleted -> deleted
     *             ? HttpResponse.noContent()
     *             : HttpResponse.notFound());
     * });
     */
    public ResourceHandler delete(RouteHandler handler) {
        this.deleteHandler = handler;
        return this;
    }

    // === Getter 메서드들 ===
    // 라우터에서 핸들러들을 가져와 라우트에 등록할 때 사용

    /**
     * 인덱스 핸들러 반환
     * @return GET /resources 핸들러 (없으면 null)
     */
    public RouteHandler getIndexHandler() {
        return indexHandler;
    }

    /**
     * 조회 핸들러 반환
     * @return GET /resources/{id} 핸들러 (없으면 null)
     */
    public RouteHandler getShowHandler() {
        return showHandler;
    }

    /**
     * 생성 핸들러 반환
     * @return POST /resources 핸들러 (없으면 null)
     */
    public RouteHandler getCreateHandler() {
        return createHandler;
    }

    /**
     * 수정 핸들러 반환
     * @return PUT /resources/{id} 핸들러 (없으면 null)
     */
    public RouteHandler getUpdateHandler() {
        return updateHandler;
    }

    /**
     * 삭제 핸들러 반환
     * @return DELETE /resources/{id} 핸들러 (없으면 null)
     */
    public RouteHandler getDeleteHandler() {
        return deleteHandler;
    }
}

/*
 * 실제 사용 예시:
 *
 * 1. 완전한 CRUD API:
 * ResourceHandler userHandler = new ResourceHandler()
 *     .index(request -> userService.getAllUsersAsync()
 *         .thenApply(users -> HttpResponse.json(users.toJson())))
 *     .show(request -> {
 *         String id = request.getAttribute("path.id", String.class);
 *         return userService.findByIdAsync(id)
 *             .thenApply(user -> user != null
 *                 ? HttpResponse.json(user.toJson())
 *                 : HttpResponse.notFound());
 *     })
 *     .create(request -> {
 *         User user = User.fromJson(request.getBodyAsString());
 *         return userService.createAsync(user)
 *             .thenApply(saved -> HttpResponse.builder(HttpStatus.CREATED)
 *                 .body(saved.toJson())
 *                 .contentType("application/json")
 *                 .build());
 *     })
 *     .update(request -> {
 *         String id = request.getAttribute("path.id", String.class);
 *         User updateData = User.fromJson(request.getBodyAsString());
 *         return userService.updateAsync(id, updateData)
 *             .thenApply(updated -> HttpResponse.json(updated.toJson()));
 *     })
 *     .delete(request -> {
 *         String id = request.getAttribute("path.id", String.class);
 *         return userService.deleteAsync(id)
 *             .thenApply(success -> success
 *                 ? HttpResponse.noContent()
 *                 : HttpResponse.notFound());
 *     });
 *
 * router.resource("/users", userHandler);
 *
 * 2. 읽기 전용 API:
 * ResourceHandler readOnlyHandler = new ResourceHandler()
 *     .index(request -> dataService.getAllDataAsync())
 *     .show(request -> {
 *         String id = request.getAttribute("path.id", String.class);
 *         return dataService.getDataAsync(id);
 *     });
 *     // create, update, delete는 설정하지 않음
 *
 * router.resource("/data", readOnlyHandler);
 *
 * 3. 부분 API (생성과 조회만):
 * ResourceHandler logHandler = new ResourceHandler()
 *     .index(request -> logService.getRecentLogsAsync())
 *     .create(request -> {
 *         LogEntry entry = LogEntry.fromJson(request.getBodyAsString());
 *         return logService.addLogAsync(entry);
 *     });
 *     // show, update, delete는 필요 없음
 *
 * router.resource("/logs", logHandler);
 *
 * 생성되는 라우트들:
 * GET    /users      -> userHandler.index
 * GET    /users/{id} -> userHandler.show
 * POST   /users      -> userHandler.create
 * PUT    /users/{id} -> userHandler.update
 * DELETE /users/{id} -> userHandler.delete
 */