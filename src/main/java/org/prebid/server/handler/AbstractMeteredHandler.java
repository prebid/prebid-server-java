package org.prebid.server.handler;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.prebid.AbstractHandlerMetrics;

import java.time.Clock;
import java.util.Objects;

public abstract class AbstractMeteredHandler<M extends AbstractHandlerMetrics> implements Handler<RoutingContext> {

    private static final String ERROR_MESSAGE = "%s has not been executed, and thus RoutingContext does not contain it";

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

    protected Future<Timeout> timeout(long startTime, Long tmax, Long defaultTimeout, RoutingContext context) {
        return timeoutFactory != null
                ? Future.succeededFuture(timeoutFactory.create(startTime, tmax != null && tmax > 0
                ? tmax : defaultTimeout)) : Future.failedFuture(
                        new PreBidException("TimeoutFactory is null, so TimeOut objects can not be built"));
    }

}
