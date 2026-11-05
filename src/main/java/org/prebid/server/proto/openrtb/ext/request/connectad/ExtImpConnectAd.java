package org.prebid.server.proto.openrtb.ext.request.connectad;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpConnectAd {

    @JsonProperty("networkId")
    Integer networkId;

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("bidfloor")
    BigDecimal bidFloor;
}
