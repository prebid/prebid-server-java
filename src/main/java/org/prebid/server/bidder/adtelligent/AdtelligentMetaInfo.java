package org.prebid.server.bidder.adtelligent;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;
import java.util.Collections;

/**
 * Defines Adtelligent meta info
 */
public class AdtelligentMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public AdtelligentMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "hb@adtelligent.com",
                Collections.singletonList("banner"), Arrays.asList("banner", "video"),
                null, 0, pbsEnforcesGdpr);
    }

    /**
     * Returns Adtelligent bidder related meta information: maintainer email address and supported media types.
     */
    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
