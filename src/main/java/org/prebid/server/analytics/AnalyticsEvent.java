package org.prebid.server.analytics;

public interface AnalyticsEvent {

    <T> T accept(AnalyticsEventProcessor<T> processor);
}
