package org.prebid.server.analytics.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.settings.model.Account;

/**
 * Represents a transaction at /event endpoint.
 */
@Builder
@Value
public class NotificationEvent {

    Type type;

    String bidId;

    Account account;

    String bidder;

    Long timestamp;

    String integration;

    HttpRequestContext httpContext;

    @JsonIgnore
    ActivityInfrastructure activityInfrastructure;

    public enum Type {
        win, imp
    }
}
