package org.prebid.server.hooks.modules.greenbids.real.time.data.model.result;

import com.iab.openrtb.request.BidRequest;
import lombok.Value;
import org.prebid.server.hooks.v1.InvocationAction;

@Value(staticConstructor = "of")
public class GreenbidsInvocationResult {

    BidRequest updatedBidRequest;

    InvocationAction invocationAction;

    AnalyticsResult analyticsResult;
}
