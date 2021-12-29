package org.prebid.server.analytics.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.analytics.processor.AnalyticsEventProcessor;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.settings.model.Account;

/**
 * Represents a transaction at /event endpoint.
 */
@Builder
@Value
public class NotificationEvent implements AnalyticsEvent {

    Type type;

    String bidId;

    Account account;

    String lineItemId;

    String bidder;

    Long timestamp;

    String integration;

    HttpRequestContext httpContext;

    @Override
    public <T> T accept(AnalyticsEventProcessor<T> processor) {
        return processor.processNotificationEvent(this);
    }

    public enum Type {
        win, imp
    }
}
