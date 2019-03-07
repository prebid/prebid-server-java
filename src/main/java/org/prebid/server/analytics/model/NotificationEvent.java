package org.prebid.server.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class NotificationEvent {
    String type;

    String bidId;

    String bidder;
}
