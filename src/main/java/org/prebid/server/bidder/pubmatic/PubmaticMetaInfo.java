package org.prebid.server.bidder.pubmatic;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;
import java.util.Collections;

public class PubmaticMetaInfo implements MetaInfo {

    @Override
    public BidderInfo info() {
        return BidderInfo.create("header-bidding@pubmatic.com",
                Collections.singletonList("banner"),
                Arrays.asList("banner", "video"));
    }
}
