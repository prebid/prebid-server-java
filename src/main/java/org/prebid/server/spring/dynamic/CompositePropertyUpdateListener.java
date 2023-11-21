package org.prebid.server.spring.dynamic;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class CompositePropertyUpdateListener<T> implements PropertyUpdateListener<T> {
    private static final Logger logger = LoggerFactory.getLogger(CompositePropertyUpdateListener.class);

    // May leak memory if delegates are spawned and not used afterward
    private final List<PropertyUpdateListener<T>> delegates = new CopyOnWriteArrayList<>();

    public void addListener(PropertyUpdateListener<T> listener) {
        delegates.add(listener);
    }

    @Override
    public void onUpdate(T newValue) {
        delegates.forEach(delegate -> {
            try {
                delegate.onUpdate(newValue);
            } catch (Throwable t) {
                logger.error(t.getMessage());
            }
        });
    }
}
