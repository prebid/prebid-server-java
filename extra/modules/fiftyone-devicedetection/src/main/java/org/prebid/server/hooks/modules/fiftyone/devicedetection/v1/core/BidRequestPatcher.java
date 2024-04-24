package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence;

import java.util.function.BiFunction;

@FunctionalInterface
public interface BidRequestPatcher extends BiFunction<BidRequest, CollectedEvidence, BidRequest> {
    default BidRequest combine(BidRequest bidRequest, CollectedEvidence collectedEvidence) {
        return apply(bidRequest, collectedEvidence);
    }
}
