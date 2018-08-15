package org.prebid.server.bidder.conversant;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;
import java.util.Collections;

public class ConversantMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public ConversantMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "mediapsr@conversantmedia.com",
                Collections.emptyList(), Arrays.asList("banner", "video"),
                null, 24, pbsEnforcesGdpr);
    }

    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
