package org.prebid.server.bidder.conversant;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;
import java.util.Collections;

public class ConversantMetaInfo implements MetaInfo {

    @Override
    public BidderInfo info() {
        return BidderInfo.create("mediapsr@conversantmedia.com",
                Collections.singletonList("banner"),
                Arrays.asList("banner", "video"));
    }
}
