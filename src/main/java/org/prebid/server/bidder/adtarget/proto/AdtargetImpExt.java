package org.prebid.server.bidder.adtarget.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AdtargetImpExt {

    @JsonProperty("adtarget")
    ExtImpAdtargetBidRequest extImp;
}
