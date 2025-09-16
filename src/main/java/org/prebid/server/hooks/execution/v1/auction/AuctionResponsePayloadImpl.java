package org.prebid.server.hooks.execution.v1.auction;

import com.iab.openrtb.response.BidResponse;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class AuctionResponsePayloadImpl implements AuctionResponsePayload {

    BidResponse bidResponse;
}
