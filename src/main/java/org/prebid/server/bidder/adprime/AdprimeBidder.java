package org.prebid.server.bidder.adprime;

import com.iab.openrtb.request.Imp;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.adprime.ExtImpAdprime;

/**
 * Adprime {@link Bidder} implementation.
 */
public class AdprimeBidder extends OpenrtbBidder<ExtImpAdprime> {

    public AdprimeBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.REQUEST_PER_IMP, ExtImpAdprime.class, mapper);
    }

    @Override
    protected Imp modifyImp(Imp imp, ExtImpAdprime impExt) {
        return imp.toBuilder()
                .tagid(impExt.getTagId())
                .build();
    }
}
