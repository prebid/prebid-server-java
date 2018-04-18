package org.prebid.server.analytics;

import io.vertx.core.Vertx;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of the Composite design pattern that dispatches event processing to all enabled reporters.
 */
public class CompositeAnalyticsReporter implements AnalyticsReporter {

    private final List<AnalyticsReporter> delegates;
    private final Vertx vertx;

    public CompositeAnalyticsReporter(List<AnalyticsReporter> delegates, Vertx vertx) {
        this.delegates = Objects.requireNonNull(delegates);
        this.vertx = Objects.requireNonNull(vertx);
    }

    @Override
    public <T> void processEvent(T event) {
        for (final AnalyticsReporter reporter : delegates) {
            vertx.runOnContext(ignored -> reporter.processEvent(event));
        }
    }
}
