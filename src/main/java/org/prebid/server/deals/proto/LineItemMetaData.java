package org.prebid.server.deals.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Defines the contract for lineItems[].
 */
@Builder(toBuilder = true)
@Value
public class LineItemMetaData {

    @JsonProperty("lineItemId")
    String lineItemId;

    @JsonProperty("extLineItemId")
    String extLineItemId;

    @JsonProperty("dealId")
    String dealId;

    List<LineItemSize> sizes;

    @JsonProperty("accountId")
    String accountId;

    String source;

    Price price;

    @JsonProperty("relativePriority")
    Integer relativePriority;

    @JsonProperty("startTimeStamp")
    ZonedDateTime startTimeStamp;

    @JsonProperty("endTimeStamp")
    ZonedDateTime endTimeStamp;

    @JsonProperty("updatedTimeStamp")
    ZonedDateTime updatedTimeStamp;

    String status;

    @JsonProperty("frequencyCaps")
    List<FrequencyCap> frequencyCaps;

    @JsonProperty("deliverySchedules")
    List<DeliverySchedule> deliverySchedules;

    ObjectNode targeting;
}
