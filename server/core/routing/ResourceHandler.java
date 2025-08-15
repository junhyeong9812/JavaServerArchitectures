package server.core.routing;

/**
 * RESTful 리소스 핸들러
 */
public class ResourceHandler {
    private RouteHandler indexHandler;    // GET /resources
    private RouteHandler showHandler;     // GET /resources/{id}
    private RouteHandler createHandler;   // POST /resources
    private RouteHandler updateHandler;   // PUT /resources/{id}
    private RouteHandler deleteHandler;   // DELETE /resources/{id}

    public ResourceHandler() {}

    public ResourceHandler index(RouteHandler handler) {
        this.indexHandler = handler;
        return this;
    }

    public ResourceHandler show(RouteHandler handler) {
        this.showHandler = handler;
        return this;
    }

    public ResourceHandler create(RouteHandler handler) {
        this.createHandler = handler;
        return this;
    }

    public ResourceHandler update(RouteHandler handler) {
        this.updateHandler = handler;
        return this;
    }

    public ResourceHandler delete(RouteHandler handler) {
        this.deleteHandler = handler;
        return this;
    }

    // Getters
    public RouteHandler getIndexHandler() { return indexHandler; }
    public RouteHandler getShowHandler() { return showHandler; }
    public RouteHandler getCreateHandler() { return createHandler; }
    public RouteHandler getUpdateHandler() { return updateHandler; }
    public RouteHandler getDeleteHandler() { return deleteHandler; }
}