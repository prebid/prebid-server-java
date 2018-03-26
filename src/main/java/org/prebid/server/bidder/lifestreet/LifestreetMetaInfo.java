package org.prebid.server.bidder.lifestreet;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;
import java.util.Collections;

public class LifestreetMetaInfo implements MetaInfo {

    @Override
    public BidderInfo info() {
        return BidderInfo.create("mobile.tech@lifestreet.com",
                Collections.singletonList("banner"),
                Arrays.asList("banner", "video"), null);
    }
}
