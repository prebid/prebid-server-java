package org.prebid.server.analytics.model;

import org.prebid.server.analytics.processor.AnalyticsEventProcessor;

public interface AnalyticsEvent {

    <T> T accept(AnalyticsEventProcessor<T> processor);
}
