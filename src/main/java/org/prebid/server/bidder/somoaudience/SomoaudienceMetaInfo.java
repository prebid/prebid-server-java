package org.prebid.server.bidder.somoaudience;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;
import java.util.Collections;

/**
 * Defines Somoaudience meta info
 */
public class SomoaudienceMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public SomoaudienceMetaInfo(boolean enabled) {
        bidderInfo = BidderInfo.create(enabled, "publishers@somoaudience.com",
                Collections.singletonList("banner, native"), Arrays.asList("banner", "native", "video"), null, 341,
                true);
    }

    /**
     * Returns Somoaudience bidder related meta information: maintainer email address and supported media types.
     */
    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
