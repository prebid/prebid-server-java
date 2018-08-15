package org.prebid.server.bidder.rubicon;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.ViewabilityVendors;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RubiconMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public RubiconMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "header-bidding@rubiconproject.com",
                Collections.singletonList("banner"), Arrays.asList("banner", "video"), Stream.of(
                        ViewabilityVendors.activeview,
                        ViewabilityVendors.adform,
                        ViewabilityVendors.comscore,
                        ViewabilityVendors.doubleverify,
                        ViewabilityVendors.integralads,
                        ViewabilityVendors.moat,
                        ViewabilityVendors.sizemek,
                        ViewabilityVendors.whiteops)
                        .map(ViewabilityVendors::name).collect(Collectors.toList()), 52, pbsEnforcesGdpr);
    }

    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
