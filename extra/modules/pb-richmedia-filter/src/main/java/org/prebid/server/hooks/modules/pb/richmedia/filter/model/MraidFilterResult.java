package org.prebid.server.hooks.modules.pb.richmedia.filter.model;

import lombok.Value;
import org.prebid.server.auction.model.BidderResponse;

import java.util.List;

@Value(staticConstructor = "of")
public class MraidFilterResult {

    List<BidderResponse> filterResult;

    List<AnalyticsResult> analyticsResult;

    public boolean hasRejectedBids() {
        return !analyticsResult.isEmpty();
    }
}
