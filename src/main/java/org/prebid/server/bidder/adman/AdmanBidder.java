package org.prebid.server.bidder.adman;

import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.adman.ExtImpAdman;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;

/**
 * Adman {@link Bidder} implementation.
 */
public class AdmanBidder extends OpenrtbBidder<ExtImpAdman> {

    public AdmanBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.REQUEST_PER_IMP, ExtImpAdman.class, mapper);
    }

    @Override
    protected Imp modifyImp(Imp imp, ExtImpAdman impExt) {
        return imp.toBuilder()
                .tagid(impExt.getTagId())
                .build();
    }

    @Override
    protected BidType getBidType(Bid bid, List<Imp> imps) {
        final String impId = bid.getImpid();
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() == null && imp.getVideo() != null) {
                    return BidType.video;
                }
                return BidType.banner;
            }
        }
        throw new PreBidException(String.format("Failed to find impression %s", impId));
    }
}
