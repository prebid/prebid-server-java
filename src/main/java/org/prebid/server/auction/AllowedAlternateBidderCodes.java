package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestAlternateBidderCodes;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestAlternateBidderCodesBidder;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.util.ObjectUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper class for applying the allow-alternate-bidder-codes configuration
 */
public class AllowedAlternateBidderCodes {

    private static final String WILDCARD = "*";

    private AllowedAlternateBidderCodes() { }

    public static Set<String> allowedCodesForBidder(String bidder, BidRequest bidRequest) {
        final ExtRequestAlternateBidderCodes alternateBidderCodes = ObjectUtil.getIfNotNull(
                bidRequest.getExt().getPrebid(), ExtRequestPrebid::getAlternatebiddercodes);

        if (alternateBidderCodes == null) {
            return null;
        }

        if (!alternateBidderCodes.getEnabled()) {
            return null;
        }

        final Map<String, ExtRequestAlternateBidderCodesBidder> alternateBidderCodesBidderMap =
                alternateBidderCodes.getBidders();

        if (alternateBidderCodesBidderMap == null) {
            return null;
        }

        final ExtRequestAlternateBidderCodesBidder alternateBidderCodesBidder =
                alternateBidderCodesBidderMap.get(bidder);

        if (alternateBidderCodesBidder == null || !alternateBidderCodesBidder.getEnabled()) {
            return null;
        }

        final List<String> allowedAlternates = alternateBidderCodesBidder.getAllowedBidderCodes();
        return allowedAlternates != null ? new HashSet<>(allowedAlternates) : null;
    }

    public static String applySeatForBid(Set<String> allowedAlternateBidderCodes, String bidder, String wantedSeat) {
        if (allowedAlternateBidderCodes == null || wantedSeat == null) {
            return bidder;
        }

        if (allowedAlternateBidderCodes.contains(wantedSeat) || allowedAlternateBidderCodes.contains(WILDCARD)) {
            return wantedSeat;
        }

        return bidder;
    }
}
