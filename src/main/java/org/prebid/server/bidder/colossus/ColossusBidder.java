package org.prebid.server.bidder.colossus;

import com.iab.openrtb.request.Imp;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.colossus.ExtImpColossus;

/**
 * Colossus {@link Bidder} implementation.
 */
public class ColossusBidder extends OpenrtbBidder<ExtImpColossus> {

    public ColossusBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.REQUEST_PER_IMP, ExtImpColossus.class, mapper);
    }

    @Override
    protected Imp modifyImp(Imp imp, ExtImpColossus impExt) {
        return imp.toBuilder()
                .tagid(impExt.getTagId())
                .build();
    }
}
