package org.prebid.server.bidder.grid;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Collections;

/**
 * Defines TheMediaGrid meta info
 */
public class GridMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public GridMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "grid-tech@themediagrid.com",
                Collections.emptyList(), Collections.singletonList("banner"),
                null, 0, pbsEnforcesGdpr);
    }

    /**
     * Returns TheMediaGrid bidder related meta information: maintainer email address and supported media types.
     */
    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
