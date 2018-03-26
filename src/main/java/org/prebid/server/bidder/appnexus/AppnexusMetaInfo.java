package org.prebid.server.bidder.appnexus;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;

public class AppnexusMetaInfo implements MetaInfo {

    @Override
    public BidderInfo info() {
        return BidderInfo.create("info@prebid.org",
                Arrays.asList("banner", "native"),
                Arrays.asList("banner", "video"), null);
    }
}
