package org.prebid.server.bidder.eplanning;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Collections;

/**
 * Defines Eplanning meta info
 */
public class EplanningMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public EplanningMetaInfo(boolean enabled) {
        bidderInfo = BidderInfo.create(enabled, "mmartinho@e-planning.net",
                Collections.singletonList("banner"), Collections.singletonList("banner"), null, 0, true);
    }

    /**
     * Returns Eplanning bidder related meta information: maintainer email address and supported media types.
     */
    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
