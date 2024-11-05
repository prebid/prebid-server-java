package org.prebid.server.proto.openrtb.ext.request.tradplus;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpTradPlus {

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("zoneId")
    String zoneId;
}
