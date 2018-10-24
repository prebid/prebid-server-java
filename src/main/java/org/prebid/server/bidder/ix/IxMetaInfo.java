package org.prebid.server.bidder.ix;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Collections;

public class IxMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public IxMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "info@prebid.org",
                Collections.emptyList(), Collections.singletonList("banner"),
                null, 10, pbsEnforcesGdpr);
    }

    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
