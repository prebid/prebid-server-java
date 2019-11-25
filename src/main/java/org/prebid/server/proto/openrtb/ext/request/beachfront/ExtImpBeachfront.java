package org.prebid.server.proto.openrtb.ext.request.beachfront;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpBeachfront {

    @JsonProperty("appId")
    String appId;

    @JsonProperty("appIds")
    ExtImpBeachfrontAppIds appIds;

    BigDecimal bidfloor;

    @JsonProperty("videoResponseType")
    String videoResponseType;
}
