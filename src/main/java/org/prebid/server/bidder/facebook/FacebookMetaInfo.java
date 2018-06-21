package org.prebid.server.bidder.facebook;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;
import java.util.Collections;

public class FacebookMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public FacebookMetaInfo(boolean enabled) {
        bidderInfo = BidderInfo.create(enabled, "info@prebid.org",
                Collections.singletonList("banner"), Arrays.asList("banner", "video"), null, 0, true);
    }

    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
