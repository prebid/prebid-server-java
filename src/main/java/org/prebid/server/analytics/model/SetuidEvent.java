package org.prebid.server.analytics.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.analytics.processor.AnalyticsEventProcessor;

/**
 * Represents a transaction at /setuid endpoint.
 */
@Builder
@Value
public class SetuidEvent implements AnalyticsEvent {

    Integer status;

    String bidder;

    String uid;

    Boolean success;

    public static SetuidEvent error(int status) {
        return SetuidEvent.builder().status(status).build();
    }

    @Override
    public <T> T accept(AnalyticsEventProcessor<T> processor) {
        return processor.processSetuidEvent(this);
    }
}
