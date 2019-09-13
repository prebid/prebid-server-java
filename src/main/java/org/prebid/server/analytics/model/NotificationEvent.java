package org.prebid.server.analytics.model;

import lombok.Builder;
import lombok.Value;
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

    HttpContext httpContext;

    public enum Type {
        win, imp
    }
}
