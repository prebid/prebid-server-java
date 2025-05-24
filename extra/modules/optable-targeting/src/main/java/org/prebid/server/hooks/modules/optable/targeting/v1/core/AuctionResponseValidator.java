package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.Reason;
import org.prebid.server.hooks.modules.optable.targeting.model.Status;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class AuctionResponseValidator {

    private AuctionResponseValidator() {
    }

    public static EnrichmentStatus checkEnrichmentPossibility(BidResponse bidResponse, List<Audience> targeting) {
        if (!hasKeywords(targeting)) {
            return EnrichmentStatus.of(Status.FAIL, Reason.NOKEYWORD);
        } else if (!hasBids(bidResponse)) {
            return EnrichmentStatus.of(Status.FAIL, Reason.NOBID);
        }

        return EnrichmentStatus.of(Status.SUCCESS, Reason.NONE);
    }

    private static boolean hasKeywords(List<Audience> targeting) {
        if (CollectionUtils.isEmpty(targeting)) {
            return false;
        }

        return targeting.stream()
                .filter(Objects::nonNull)
                .anyMatch(audience -> CollectionUtils.isNotEmpty(audience.getIds()));
    }

    private static boolean hasBids(BidResponse bidResponse) {
        final List<SeatBid> seatBids = Optional.ofNullable(bidResponse).map(BidResponse::getSeatbid).orElse(null);
        if (CollectionUtils.isEmpty(seatBids)) {
            return false;
        }

        return seatBids.stream()
                .filter(Objects::nonNull)
                .anyMatch(seatBid -> CollectionUtils.isNotEmpty(seatBid.getBid()));
    }
}
