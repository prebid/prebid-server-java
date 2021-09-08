package org.prebid.server.proto.openrtb.ext.request.connectad;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpConnectAd {

    @JsonProperty("networkId")
    Integer networkId;

    @JsonProperty("siteId")
    Integer siteId;

    BigDecimal bidfloor;
}
