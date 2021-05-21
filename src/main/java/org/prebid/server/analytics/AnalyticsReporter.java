package org.prebid.server.analytics;

import io.vertx.core.Future;

/**
 * Type of component that does transactional logging.
 */
public interface AnalyticsReporter {

    /**
     * Callback method invoked for each application event that needs to be reported. Event types are defined in
     * {@link org.prebid.server.analytics.model}.
     * <p>
     * Implementation note: this method is executed on Vert.x event loop thread so it must never use blocking API.
     */
    <T> Future<Void> processEvent(T event);

    /**
     * Method for defining analytics reporter ID for TCF checks.
     */
    int vendorId();

    /**
     * Method for defining name of the related to this analytic adapter.
     */
    String name();
}
