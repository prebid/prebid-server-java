package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

public class BidRequestEnricher extends RequestEnricher implements PayloadUpdate<AuctionRequestPayload> {

    private BidRequestEnricher(TargetingResult targetingResult, OptableTargetingProperties targetingProperties) {
        super(targetingResult, targetingProperties);
    }

    public static BidRequestEnricher of(TargetingResult targetingResult, OptableTargetingProperties properties) {
        return new BidRequestEnricher(targetingResult, properties);
    }

    @Override
    public AuctionRequestPayload apply(AuctionRequestPayload payload) {
        return AuctionRequestPayloadImpl.of(enrichBidRequest(payload.bidRequest()));
    }
}
