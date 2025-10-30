package org.prebid.server.bidder.sspbc;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.BidRequest;
import lombok.Value;

@Value(staticConstructor = "of")
public class SspbcRequest {

    @JsonProperty("bidRequest")
    BidRequest bidRequest;
}
