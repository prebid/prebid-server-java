package org.prebid.server.bidder.appnexus;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;

public class AppnexusMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public AppnexusMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "info@prebid.org",
                Arrays.asList("banner", "native"), Arrays.asList("banner", "video"),
                null, 32, pbsEnforcesGdpr);
    }

    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
