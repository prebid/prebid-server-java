package org.prebid.server.metric.prebid;

import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.metric.Metrics;
import org.prebid.server.util.HttpUtil;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

public abstract class AbstractHandlerMetrics {

    public static final String IS_SAFARI = AbstractHandlerMetrics.class.getName() + ".isSafari";

    private final Metrics metrics;
    private final Clock clock;

    protected AbstractHandlerMetrics(Metrics metrics, Clock clock) {
        this.metrics = Objects.requireNonNull(metrics, "Metrics can not be null");
        this.clock = Objects.requireNonNull(clock, "Clock can not be null");
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public Clock getClock() {
        return clock;
    }

    public boolean isSafari(RoutingContext context) {
        return Optional.ofNullable((Boolean) context.get(IS_SAFARI)).orElseGet(() -> {
            boolean isSafari = isSafariInternal(context);
            context.put(IS_SAFARI, isSafari);
            return isSafari;
        });
    }

    private boolean isSafariInternal(RoutingContext context) {
        return HttpUtil.isSafari(context.request().headers().get(HttpHeaders.USER_AGENT));
    }
}
