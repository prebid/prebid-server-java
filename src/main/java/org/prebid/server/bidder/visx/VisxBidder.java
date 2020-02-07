package org.prebid.server.bidder.visx;

import com.iab.openrtb.request.Imp;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.visx.ExtImpVisx;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;

public class VisxBidder extends OpenrtbBidder<ExtImpVisx> {
    public VisxBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpVisx.class, mapper);
    }

    @Override
    protected BidType getBidType(String impId, List<Imp> imps) {
        return BidType.banner;
    }
}
