package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;

public class BidderRequestEnricher extends RequestEnricher implements PayloadUpdate<BidderRequestPayload> {

    private BidderRequestEnricher(TargetingResult targetingResult, OptableTargetingProperties targetingProperties) {
        super(targetingResult, targetingProperties);
    }

    public static BidderRequestEnricher of(TargetingResult targetingResult, OptableTargetingProperties properties) {
        return new BidderRequestEnricher(targetingResult, properties);
    }

    @Override
    public BidderRequestPayload apply(BidderRequestPayload payload) {
        return BidderRequestPayloadImpl.of(enrichBidRequest(payload.bidRequest()));
    }
}
