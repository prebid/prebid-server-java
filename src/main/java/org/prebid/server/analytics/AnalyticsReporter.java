package org.prebid.server.analytics;

/**
 * Type of component that does transactional logging.
 */
@FunctionalInterface
public interface AnalyticsReporter {

    /**
     * Callback method invoked for each application event that needs to be reported. Event types are defined in
     * {@link org.prebid.server.analytics.model}.
     * <p>
     * Implementation note: this method is executed on Vert.x event loop thread so it must never use blocking API.
     */
    <T> void processEvent(T event);
}
