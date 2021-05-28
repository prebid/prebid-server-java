package org.prebid.server.bidder.adf;

import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.adf.ExtImpAdf;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;

public class AdfBidder extends OpenrtbBidder<ExtImpAdf> {

    public AdfBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpAdf.class, mapper);
    }

    @Override
    protected Imp modifyImp(Imp imp, ExtImpAdf impExt) {
        return imp.toBuilder()
                .tagid(impExt.getMid())
                .build();
    }

    @Override
    protected BidType getBidType(Bid bid, List<Imp> imps) {
        return BidType.xNative;
    }
}
