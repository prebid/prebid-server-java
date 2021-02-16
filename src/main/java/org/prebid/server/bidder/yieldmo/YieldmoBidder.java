package org.prebid.server.bidder.yieldmo;

import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.bidder.yieldmo.proto.YieldmoImpExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.yieldmo.ExtImpYieldmo;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;

/**
 * Yieldmo {@link Bidder} implementation.
 */
public class YieldmoBidder extends OpenrtbBidder<ExtImpYieldmo> {

    public YieldmoBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpYieldmo.class, mapper);
    }

    @Override
    protected Imp modifyImp(Imp imp, ExtImpYieldmo impExt) throws PreBidException {
        final Imp.ImpBuilder modifiedImp = imp.toBuilder();

        try {
            modifiedImp.ext(mapper.mapper().valueToTree(YieldmoImpExt.of(impExt.getPlacementId())));
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        return modifiedImp.build();
    }

    @Override
    protected BidType getBidType(Bid bid, List<Imp> imps) {
        final String impId = bid.getImpid();
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
            }
        }
        return BidType.video;
    }
}
