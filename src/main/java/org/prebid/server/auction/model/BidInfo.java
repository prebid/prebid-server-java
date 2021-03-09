package org.prebid.server.auction.model;

import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.BidType;

@Builder(toBuilder = true)
@Value
public class BidInfo {

    String generatedBidId;

    Bid bid;

    Imp correspondingImp;

    String bidder;

    BidType bidType;

    String category;

    Boolean satisfiedPriority;

    public String getBidId() {
        return generatedBidId != null ? generatedBidId : bid.getId();
    }
}
