package org.prebid.server.deals.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.deals.proto.FrequencyCap;

import java.time.ZonedDateTime;
import java.util.List;

@Builder(toBuilder = true)
@Value
public class WinEventNotification {

    @JsonProperty("bidderCode")
    String bidderCode;

    @JsonProperty("bidId")
    String bidId;

    @JsonProperty("lineItemId")
    String lineItemId;

    String region;

    @JsonProperty("userIds")
    List<UserId> userIds;

    @JsonProperty("winEventDateTime")
    ZonedDateTime winEventDateTime;

    @JsonProperty("lineUpdatedDateTime")
    ZonedDateTime lineUpdatedDateTime;

    @JsonProperty("frequencyCaps")
    List<FrequencyCap> frequencyCaps;
}
