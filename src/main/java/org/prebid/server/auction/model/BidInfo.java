package org.prebid.server.auction.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.cache.model.CacheInfo;

@Builder(toBuilder = true)
@Value
public class BidInfo {

    BidderBid bidderBid;

    Imp correspondingImp;

    String bidder;

    CacheInfo cacheInfo;

    String lineItemId;

    String lineItemSource;

    TargetingInfo targetingInfo;

    String category;

    Boolean satisfiedPriority;

    public String getBidId() {
        final Bid bid = bidderBid != null ? bidderBid.getBid() : null;
        final ObjectNode extNode = bid != null ? bid.getExt() : null;
        final JsonNode bidIdNode = extNode != null ? extNode.path("prebid").path("bidid") : null;
        final String generatedBidId = bidIdNode != null && bidIdNode.isTextual() ? bidIdNode.textValue() : null;
        final String bidId = bid != null ? bid.getId() : null;

        return generatedBidId != null ? generatedBidId : bidId;
    }
}
