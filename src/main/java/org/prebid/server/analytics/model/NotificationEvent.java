package org.prebid.server.analytics.model;

import lombok.Value;

@Value
public class NotificationEvent {
    String type;

    String bidId;

    String bidder;
}
