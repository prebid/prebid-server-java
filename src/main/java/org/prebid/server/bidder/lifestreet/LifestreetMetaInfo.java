package org.prebid.server.bidder.lifestreet;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;
import java.util.Collections;

public class LifestreetMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public LifestreetMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "mobile.tech@lifestreet.com",
                Collections.singletonList("banner"), Arrays.asList("banner", "video"),
                null, 67, pbsEnforcesGdpr);
    }

    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
