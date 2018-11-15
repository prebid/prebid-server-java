package org.prebid.server.bidder.rhythmone;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;

/**
 * Defines RhythmOne meta info
 */
public class RhythmoneMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public RhythmoneMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "support@rhythmone.com",
                Arrays.asList("banner", "video"), Arrays.asList("banner", "video"),
                null, 36, pbsEnforcesGdpr);
    }

    /**
     * Returns RhythmOne bidder related meta information: maintainer email address and supported media types.
     */
    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
