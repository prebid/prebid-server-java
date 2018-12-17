package org.prebid.server.bidder.gumgum;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Collections;

/**
 * Defines GumGum meta info
 */
public class GumgumMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public GumgumMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "pubtech@gumgum.com",
                Collections.emptyList(), Collections.singletonList("banner"),
                null, 61, pbsEnforcesGdpr);
    }

    /**
     * Returns GumGum bidder related meta information: maintainer email address and supported media types.
     */
    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
