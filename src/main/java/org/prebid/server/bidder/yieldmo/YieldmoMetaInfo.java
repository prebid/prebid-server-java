package org.prebid.server.bidder.yieldmo;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Collections;

/**
 * Defines Yieldmo meta info
 */
public class YieldmoMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public YieldmoMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "progsupport@yieldmo.com",
                Collections.emptyList(), Collections.singletonList("banner"),
                null, 173, pbsEnforcesGdpr);
    }

    /**
     * Returns Yieldmo bidder related meta information: maintainer email address and supported media types.
     */
    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
