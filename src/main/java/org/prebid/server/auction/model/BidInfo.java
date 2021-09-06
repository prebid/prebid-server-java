package org.prebid.server.auction.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.cache.model.CacheInfo;
import org.prebid.server.proto.openrtb.ext.response.BidType;

@Builder(toBuilder = true)
@Value
public class BidInfo {

    Bid bid;

    Imp correspondingImp;

    String bidCurrency;

    String bidder;

    BidType bidType;

    CacheInfo cacheInfo;

    String lineItemId;

    String lineItemSource;

    TargetingInfo targetingInfo;

    public String getBidId() {
        final ObjectNode extNode = bid != null ? bid.getExt() : null;
        final JsonNode bidIdNode = extNode != null ? extNode.path("prebid").path("bidid") : null;
        final String generatedBidId = bidIdNode != null && bidIdNode.isTextual() ? bidIdNode.textValue() : null;
        final String bidId = bid != null ? bid.getId() : null;

        return generatedBidId != null ? generatedBidId : bidId;
    }
}
