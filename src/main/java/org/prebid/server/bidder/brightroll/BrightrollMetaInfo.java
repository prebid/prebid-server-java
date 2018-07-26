package org.prebid.server.bidder.brightroll;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;
import java.util.Collections;

/**
 * Defines Brightroll meta info
 */
public class BrightrollMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public BrightrollMetaInfo(boolean enabled) {
        bidderInfo = BidderInfo.create(enabled, "smithaa@oath.com",
                Collections.singletonList("banner, video"), Arrays.asList("banner", "video"), null, 25, true);
    }

    /**
     * Returns Brightroll bidder related meta information: maintainer email address and supported media types.
     */
    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
