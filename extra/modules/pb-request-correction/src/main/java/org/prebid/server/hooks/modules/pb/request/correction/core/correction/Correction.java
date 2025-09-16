package org.prebid.server.hooks.modules.pb.request.correction.core.correction;

import com.iab.openrtb.request.BidRequest;

public interface Correction {

    BidRequest apply(BidRequest bidRequest);
}
