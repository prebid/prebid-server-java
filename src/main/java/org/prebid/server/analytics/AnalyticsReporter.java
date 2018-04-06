package org.prebid.server.analytics;

public interface AnalyticsReporter {

    <T> void processEvent(T event);
}
