package org.prebid.server.bidder.pulsepoint;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Collections;

public class PulsepointMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public PulsepointMetaInfo(boolean enabled) {
        bidderInfo = BidderInfo.create(enabled, "info@prebid.org",
                Collections.singletonList("banner"), Collections.singletonList("banner"), null, 81, true);
    }

    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
