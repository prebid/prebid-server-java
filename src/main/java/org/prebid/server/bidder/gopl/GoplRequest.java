package org.prebid.server.bidder.gopl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.BidRequest;
import lombok.Value;

@Value(staticConstructor = "of")
public class GoplRequest {

    @JsonProperty("bidRequest")
    BidRequest bidRequest;
}
