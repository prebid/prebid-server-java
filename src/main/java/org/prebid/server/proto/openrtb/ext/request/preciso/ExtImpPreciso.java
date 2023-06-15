package org.prebid.server.proto.openrtb.ext.request.preciso;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder(toBuilder = true)
public class ExtImpPreciso {
    @JsonProperty("publisherId")
    String publisherId;

    String region;

    @JsonProperty("bidfloor")
    BigDecimal bidFloor;

    String channel;

}
