package org.prebid.server.proto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class EventNotificationRequest {

    String type;

    @JsonProperty("bidid")
    String bidId;

    String bidder;

    String format;
}
