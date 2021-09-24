package org.prebid.server.deals.proto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Builder(toBuilder = true)
@Value
public class DeliveryProgressReport {

    @JsonProperty("reportId")
    String reportId;

    @JsonProperty("reportTimeStamp")
    String reportTimeStamp;

    @JsonProperty("dataWindowStartTimeStamp")
    String dataWindowStartTimeStamp;

    @JsonProperty("dataWindowEndTimeStamp")
    String dataWindowEndTimeStamp;

    @JsonProperty("instanceId")
    String instanceId;

    String vendor;

    String region;

    @JsonProperty("clientAuctions")
    Long clientAuctions;

    @JsonProperty("lineItemStatus")
    Set<LineItemStatus> lineItemStatus;
}
