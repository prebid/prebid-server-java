package org.prebid.server.bidder.yieldmo;

import com.iab.openrtb.request.Imp;
import io.vertx.core.json.Json;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.bidder.yieldmo.proto.YieldmoImpExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.yieldmo.ExtImpYieldmo;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;

public class YieldmoBidder extends OpenrtbBidder<ExtImpYieldmo> {

    public YieldmoBidder(String endpointUrl) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpYieldmo.class);
    }

    @Override
    protected Imp modifyImp(Imp imp, ExtImpYieldmo impExt) throws PreBidException {
        final Imp.ImpBuilder modifiedImp = imp.toBuilder();

        try {
            modifiedImp.ext(Json.mapper.valueToTree(YieldmoImpExt.of(impExt.getPlacementId())));
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        return modifiedImp.build();
    }

    @Override
    protected BidType getBidType(String impId, List<Imp> imps) {
        return BidType.banner;
    }
}
