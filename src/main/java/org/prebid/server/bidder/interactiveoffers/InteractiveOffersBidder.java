package org.prebid.server.bidder.interactiveoffers;

import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.interactiveoffers.ExtImpInteractiveoffers;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;

public class InteractiveOffersBidder extends OpenrtbBidder<ExtImpInteractiveoffers> {

    public InteractiveOffersBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpInteractiveoffers.class, mapper);
    }

    @Override
    protected Imp modifyImp(Imp imp, ExtImpInteractiveoffers impExt) {
        if (impExt.getPubId() == null) {
            throw new PreBidException("pubid is empty");
        }
        return imp;
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
        throw new PreBidException(String.format("Failed to find impression %s", impId));
    }
}
