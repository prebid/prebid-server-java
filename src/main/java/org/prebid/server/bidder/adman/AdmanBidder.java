package org.prebid.server.bidder.adman;

import com.iab.openrtb.request.Imp;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.adman.ExtImpAdman;

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
}
