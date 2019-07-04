package org.prebid.server.bidder.vrtcal;

import com.iab.openrtb.request.Imp;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.proto.openrtb.ext.request.vrtcal.ExtImpVrtcal;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;

public class VrtcalBidder extends OpenrtbBidder<ExtImpVrtcal> {
    public VrtcalBidder(String endpointUrl) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpVrtcal.class);
    }

    @Override
    protected BidType getBidType(String impId, List<Imp> imps) {
        return BidType.banner;
    }
}
