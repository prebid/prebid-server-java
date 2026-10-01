package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;

public class BidRequestCleaner extends BaseRequestCleaner implements PayloadUpdate<BidderRequestPayload> {

    public static BidRequestCleaner instance() {
        return new BidRequestCleaner();
    }

    @Override
    public BidderRequestPayload apply(BidderRequestPayload payload) {
        return BidderRequestPayloadImpl.of(clearExtUserOptable(payload.bidRequest()));
    }
}
