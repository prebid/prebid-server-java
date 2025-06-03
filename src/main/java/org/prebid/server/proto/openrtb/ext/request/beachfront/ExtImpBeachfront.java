package org.prebid.server.proto.openrtb.ext.request.beachfront;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpBeachfront {

    @JsonProperty("appId")
    String appId;

    @JsonProperty("appIds")
    ExtImpBeachfrontAppIds appIds;

    BigDecimal bidfloor;

    @JsonProperty("videoResponseType")
    String videoResponseType;
}
