package org.prebid.server.interceptor;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.handler.AbstractMeteredHandler;
import org.prebid.server.metric.MetricName;

import java.time.Clock;

public class MeteredHandlerInterceptor implements RoutingContextHandlerInterceptor {

    public static final String CLOCK_ID = MeteredHandlerInterceptor.class.getName() + ".clock";
    public static final String START_TIME_ID = MeteredHandlerInterceptor.class.getName() + ".startTime";

    @Override
    public boolean preHandle(RoutingContext context, Handler<RoutingContext> handler) {
        if (handler instanceof AbstractMeteredHandler) {
            AbstractMeteredHandler meteredHandler = (AbstractMeteredHandler) handler;
            Clock clock = meteredHandler.getClock();
            long startTime = clock.millis();
            context.put(CLOCK_ID, clock);
            context.put(START_TIME_ID, startTime);

            // we can get concrete handler's metrics time with something like this
            context.response().endHandler(ignoredVoid ->
                    meteredHandler.getHandlerMetrics().getMetrics().updateTimer(
                            MetricName.request_time, meteredHandler.getClock().millis() - startTime));
        }
        return true;
    }

    @Override
    public void postHandle(RoutingContext context, Handler<RoutingContext> handler) {
    }

    @Override
    public void onError(RoutingContext context, Exception e, Handler<RoutingContext> handler) {
    }

}
