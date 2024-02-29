package org.prebid.server.proto.openrtb.ext.response.seatnonbid;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.auction.model.BidRejectionReason;

@Value(staticConstructor = "of")
public class NonBid {

    @JsonProperty("impid")
    String impId;

    @JsonProperty("statuscode")
    BidRejectionReason statusCode;
}
