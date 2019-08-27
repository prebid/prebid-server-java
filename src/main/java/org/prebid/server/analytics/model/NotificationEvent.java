package org.prebid.server.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class NotificationEvent {

    Type type;

    String bidId;

    String accountId;

    HttpContext httpContext;

    public enum Type {
        win, imp
    }
}

