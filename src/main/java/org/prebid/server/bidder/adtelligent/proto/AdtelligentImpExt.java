package org.prebid.server.bidder.adtelligent.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AdtelligentImpExt {

    @JsonProperty("adtelligent")
    ExtImpAdtelligentBidRequest extImp;
}
