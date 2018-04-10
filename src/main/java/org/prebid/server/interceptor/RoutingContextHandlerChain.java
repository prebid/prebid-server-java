package org.prebid.server.interceptor;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import java.util.Collection;

public class RoutingContextHandlerChain extends HandlerChain<RoutingContext> {

    public RoutingContextHandlerChain(Handler<RoutingContext> handler,
                                      Collection<HandlerInterceptor<RoutingContext>> handlerInterceptors) {
        super(handler, handlerInterceptors);
    }

    @Override
    protected void handleInternal(RoutingContext context) {
        super.handle(context);
        context.next();
    }

}
