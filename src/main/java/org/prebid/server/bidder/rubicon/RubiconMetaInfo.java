package org.prebid.server.bidder.rubicon;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;
import java.util.Collections;

public class RubiconMetaInfo implements MetaInfo {

    @Override
    public BidderInfo info() {
        return BidderInfo.create("header-bidding@rubiconproject.com",
                Collections.singletonList("banner"),
                Arrays.asList("banner", "video"));
    }
}
