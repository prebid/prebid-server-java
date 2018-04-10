package org.prebid.server.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.prebid.AbstractHandlerMetrics;

import java.time.Clock;
import java.util.Objects;

public abstract class AbstractMeteredHandler<M extends AbstractHandlerMetrics> implements Handler<RoutingContext> {

    private final M handlerMetrics;
    private final Clock clock;
    private final TimeoutFactory timeoutFactory;

    protected AbstractMeteredHandler(M handlerMetrics, Clock clock) {
        this(handlerMetrics, clock, null);
    }

    protected AbstractMeteredHandler(M handlerMetrics, Clock clock, TimeoutFactory timeoutFactory) {
        this.handlerMetrics = Objects.requireNonNull(handlerMetrics);
        this.clock = Objects.requireNonNull(clock);
        this.timeoutFactory = timeoutFactory;
    }

    public M getHandlerMetrics() {
        return this.handlerMetrics;
    }

    public Clock getClock() {
        return this.clock;
    }

    protected Timeout timeout(long startTime, Long tmax, Long defaultTimeout) {
        Objects.requireNonNull(timeoutFactory, "TimeoutFactory is null, so TimeOut objects can not be built!");
        return timeoutFactory.create(startTime, (tmax != null && tmax > 0) ? tmax : defaultTimeout);
    }

}
