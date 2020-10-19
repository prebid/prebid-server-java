package org.prebid.server.analytics;

import org.prebid.server.privacy.gdpr.model.TcfContext;

/**
 * Type of component that does transactional logging.
 */
public interface AnalyticsReporter {

    /**
     * Callback method invoked for each application event that needs to be reported. Event types are defined in
     * {@link org.prebid.server.analytics.model}.
     * <p>
     * Receives {@link TcfContext} for TCF validation of each analytic reporter by their vendor id.
     * <p>
     * Implementation note: this method is executed on Vert.x event loop thread so it must never use blocking API.
     */
    <T> void processEvent(T event, TcfContext tcfContext);

    /**
     * Callback method invoked for each application event that needs to be reported. Event types are defined in
     * {@link org.prebid.server.analytics.model}.
     * <p>
     * Should be used only when TCF check is not required.
     * <p>
     * Implementation note: this method is executed on Vert.x event loop thread so it must never use blocking API.
     */
    <T> void processEvent(T event);

    default int reporterVendorId() {
        return 0;
    }
}
