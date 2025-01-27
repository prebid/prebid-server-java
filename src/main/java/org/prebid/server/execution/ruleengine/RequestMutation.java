package org.prebid.server.execution.ruleengine;

import com.iab.openrtb.request.BidRequest;

public interface RequestMutation {

    public BidRequest mutate(BidRequest request);
}
