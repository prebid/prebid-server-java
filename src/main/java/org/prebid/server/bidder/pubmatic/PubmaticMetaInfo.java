package org.prebid.server.bidder.pubmatic;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;
import java.util.Collections;

public class PubmaticMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public PubmaticMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "header-bidding@pubmatic.com",
                Collections.singletonList("banner"), Arrays.asList("banner", "video"),
                null, 76, pbsEnforcesGdpr);
    }

    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
