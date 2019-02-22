package org.prebid.server.bidder.sonobi;

import com.iab.openrtb.request.Imp;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.proto.openrtb.ext.request.sonobi.ExtImpSonobi;

public class SonobiBidder extends OpenrtbBidder<ExtImpSonobi> {

    public SonobiBidder(String endpointUrl) {
        super(endpointUrl, RequestCreationStrategy.REQUEST_PER_IMP, ExtImpSonobi.class);
    }

    @Override
    protected Imp modifyImp(Imp imp, ExtImpSonobi impExt) {
        return imp.toBuilder()
                .tagid(impExt.getTagId())
                .build();
    }
}
