package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.Reason;
import org.prebid.server.hooks.modules.optable.targeting.model.Status;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;

import java.util.List;
import java.util.Optional;

public class AuctionResponseValidator {

    public EnrichmentStatus checkEnrichmentPossibility(BidResponse bidResponse, List<Audience> targeting) {
        Status status = Status.SUCCESS;
        Reason reason = Reason.NONE;

        if (!hasKeywords(targeting)) {
            status = Status.FAIL;
            reason = Reason.NOKEYWORD;
        } else if (!hasBids(bidResponse)) {
            status = Status.FAIL;
            reason = Reason.NOBID;
        }

        return EnrichmentStatus.builder()
                .status(status)
                .reason(reason)
                .build();
    }

    private boolean hasKeywords(List<Audience> targeting) {
        if (CollectionUtils.isEmpty(targeting)) {
            return false;
        }

        final long idsCounter = targeting.stream()
                .mapToLong(audience -> Optional.ofNullable(audience.getIds()).orElse(List.of()).size())
                .sum();

        return idsCounter > 0;
    }

    private boolean hasBids(BidResponse bidResponse) {
        final List<SeatBid> seatBids = Optional.ofNullable(bidResponse).map(BidResponse::getSeatbid).orElse(null);
        if (CollectionUtils.isEmpty(seatBids)) {
            return false;
        }

        final long bidsCount = seatBids.stream()
                .mapToLong(seatBid -> Optional.ofNullable(seatBid.getBid()).orElse(List.of()).size())
                .sum();

        return bidsCount > 0;
    }
}
