package org.prebid.server.analytics;

import java.util.List;
import java.util.Objects;

public class CompositeAnalyticsReporter implements AnalyticsReporter {

    private final List<AnalyticsReporter> delegates;

    public CompositeAnalyticsReporter(List<AnalyticsReporter> delegates) {
        this.delegates = Objects.requireNonNull(delegates);
    }

    @Override
    public <T> void processEvent(T event) {
        for (final AnalyticsReporter reporter : delegates) {
            reporter.processEvent(event);
        }
    }
}
