package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

public class AuctionRequestCleaner extends BaseRequestCleaner implements PayloadUpdate<AuctionRequestPayload> {

    public static AuctionRequestCleaner instance() {
        return new AuctionRequestCleaner();
    }

    @Override
    public AuctionRequestPayload apply(AuctionRequestPayload payload) {
        return AuctionRequestPayloadImpl.of(clearExtUserOptable(payload.bidRequest()));
    }
}
