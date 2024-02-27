package org.prebid.server.proto.openrtb.ext.request.rtbhouse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder(toBuilder = true)
public class ExtImpRtbhouse {

    @JsonProperty("publisherId")
    String publisherId;

    String region;

    @JsonProperty("bidfloor")
    BigDecimal bidFloor;

    String channel;
}
