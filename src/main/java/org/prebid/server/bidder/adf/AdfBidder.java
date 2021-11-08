package org.prebid.server.bidder.adf;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.adf.ExtImpAdf;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;

import java.util.List;

public class AdfBidder extends OpenrtbBidder<ExtImpAdf> {

    private static final String PREBID_EXT = "prebid";

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
        final String impId = bid.getImpid();
        final BidType bidType = getExtBidPrebidType(bid.getExt());
        if (bidType != null) {
            return bidType;
        }

        throw new PreBidException(String.format("Failed to parse bid %s mediatype", impId));
    }

    private BidType getExtBidPrebidType(ObjectNode bidExt) {
        final ExtBidPrebid extBidPrebid = getExtBidPrebid(bidExt);
        return extBidPrebid != null ? extBidPrebid.getType() : null;
    }

    private ExtBidPrebid getExtBidPrebid(ObjectNode bidExt) {
        if (bidExt == null || !bidExt.hasNonNull(PREBID_EXT)) {
            return null;
        }
        try {
            return mapper.mapper().convertValue(bidExt.get(PREBID_EXT), ExtBidPrebid.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
