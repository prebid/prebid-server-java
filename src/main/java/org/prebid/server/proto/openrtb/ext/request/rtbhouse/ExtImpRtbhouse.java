package org.prebid.server.proto.openrtb.ext.request.rtbhouse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
* Defines the contract for bidrequest.imp[i].ext.rtbhouse
*/
@Value
@Builder(toBuilder = true)
public class ExtImpRtbhouse {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("region")
    String region;

    @JsonProperty("bidfloor")
    BigDecimal bidFloor;

    @JsonProperty("channel")
    String channel;
}
