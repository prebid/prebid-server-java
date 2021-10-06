package org.prebid.server.bidder.vrtcal;

import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.vrtcal.ExtImpVrtcal;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;

public class VrtcalBidder extends OpenrtbBidder<ExtImpVrtcal> {

    public VrtcalBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpVrtcal.class, mapper);
    }

    @Override
    protected BidType getBidType(Bid bid, List<Imp> imps) {
        return BidType.banner;
    }
}
