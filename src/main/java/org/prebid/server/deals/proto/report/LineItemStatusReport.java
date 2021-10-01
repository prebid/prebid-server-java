package org.prebid.server.deals.proto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;

@Builder
@Value
public class LineItemStatusReport {

    @JsonProperty("lineItemId")
    String lineItemId;

    @JsonProperty("deliverySchedule")
    DeliverySchedule deliverySchedule;

    @JsonProperty("spentTokens")
    Long spentTokens;

    @JsonProperty("readyToServeTimestamp")
    ZonedDateTime readyToServeTimestamp;

    @JsonProperty("pacingFrequency")
    Long pacingFrequency;
}
