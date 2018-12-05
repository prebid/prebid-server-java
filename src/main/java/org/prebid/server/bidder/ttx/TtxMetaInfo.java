package org.prebid.server.bidder.ttx;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Collections;

/**
 * Defines 33Across meta info
 */
public class TtxMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public TtxMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "dev@33across.com",
                Collections.singletonList("banner"), Collections.singletonList("banner"),
                null, 58, pbsEnforcesGdpr);
    }

    /**
     * Returns 33Across bidder related meta information: maintainer email address and supported media types.
     */
    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
