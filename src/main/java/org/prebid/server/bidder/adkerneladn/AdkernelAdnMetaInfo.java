package org.prebid.server.bidder.adkerneladn;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;
import java.util.Collections;

/**
 * Defines AdkernelAdn meta info
 */
public class AdkernelAdnMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public AdkernelAdnMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "denis@adkernel.com",
                Collections.singletonList("banner"), Arrays.asList("banner", "video"),
                null, 14, pbsEnforcesGdpr);
    }

    /**
     * Returns AdkernelAdn bidder related meta information: maintainer email address and supported media types.
     */
    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
