package org.prebid.server.proto.openrtb.ext.request.ownadx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpOwnAdx {

    @JsonProperty("sspId")
    String sspId;

    @JsonProperty("seatId")
    String seatId;

    @JsonProperty("tokenId")
    String tokenId;
}
