package org.prebid.server.interceptor;

import io.vertx.core.Handler;

public interface HandlerInterceptor<E> {

    // can abort handler execution if desired
    boolean preHandle(E event, Handler<E> handler);

    void postHandle(E event, Handler<E> handler);

    void onError(E event, Exception e, Handler<E> handler);

}
