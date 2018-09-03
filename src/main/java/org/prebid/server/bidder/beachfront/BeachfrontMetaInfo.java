package org.prebid.server.bidder.beachfront;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;

/**
 * Defines Beachfront meta info
 */
public class BeachfrontMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public BeachfrontMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "jim@beachfront.com",
                Arrays.asList("banner", "video"), Arrays.asList("banner", "video"), null, 335, pbsEnforcesGdpr);
    }

    /**
     * Returns Beachfront bidder related meta information: maintainer email address and supported media types.
     */
    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
