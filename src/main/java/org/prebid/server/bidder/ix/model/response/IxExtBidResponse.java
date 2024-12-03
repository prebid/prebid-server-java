package org.prebid.server.bidder.ix.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class IxExtBidResponse {

    @JsonProperty("protectedAudienceAuctionConfigs")
    List<AuctionConfigExtBidResponse> protectedAudienceAuctionConfigs;

}
