package org.prebid.server.bidder.index;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;
import java.util.Collections;

public class IndexMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public IndexMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "info@prebid.org",
                Collections.singletonList("banner"), Arrays.asList("banner", "video"),
                null, 10, pbsEnforcesGdpr);
    }

    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
