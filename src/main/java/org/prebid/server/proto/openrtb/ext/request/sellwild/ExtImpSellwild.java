package org.prebid.server.proto.openrtb.ext.request.sellwild;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpSellwild {

    @JsonProperty("partnerCode")
    String partnerCode;

    String token;
}
