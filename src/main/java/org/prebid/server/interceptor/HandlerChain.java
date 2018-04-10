package org.prebid.server.interceptor;

import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class HandlerChain<E> implements Handler<E> {

    private static final Logger logger = LoggerFactory.getLogger(HandlerChain.class);

    protected final Handler<E> handler;
    protected final Collection<HandlerInterceptor<E>> handlerInterceptors;

    public HandlerChain(Handler<E> handler, Collection<HandlerInterceptor<E>> handlerInterceptors) {
        Objects.requireNonNull(handler, "Handler can not be null");
        this.handler = handler;
        this.handlerInterceptors = handlerInterceptors == null ? Collections.EMPTY_LIST : handlerInterceptors;
    }

    @Override
    public void handle(E event) {
        boolean goOn = true;

        try {
            for (HandlerInterceptor<E> hi : handlerInterceptors) {
                if (!hi.preHandle(event, handler)) {
                    if (logger.isInfoEnabled()) {
                        logger.info(String.format("handlerInterceptor %s aborted %s execution for event %s ",
                                hi.getClass().getName(), handler.getClass().getName(), event));
                    }
                    goOn = false;
                    return;
                }
            }

            if (goOn) {
                handleInternal(event);
                handlerInterceptors.stream().forEachOrdered(hi -> hi.postHandle(event, handler));
            }
        } catch (Exception e) {
            handlerInterceptors.stream().forEachOrdered(hi -> hi.onError(event, e, handler));
        }
    }

    protected void handleInternal(E event) {
        handler.handle(event);
    }

}
