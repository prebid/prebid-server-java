package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.support.SimpleThreadScope;

/**
 * Custom Spring scope to use when there should exist only one instance of bean per Vertx context.
 */
public class VertxContextScope extends SimpleThreadScope {

    private static final Logger logger = LoggerFactory.getLogger(VertxContextScope.class);

    public static final String NAME = "vertx-context";

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        if (Vertx.currentContext() == null) {
            logger.warn("Attempt to create vertx context scoped bean on non-vertx thread!");
        }

        return super.get(name, objectFactory);
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        // suppress warning written by SimpleThreadScope as it is of no value for application
    }
}
