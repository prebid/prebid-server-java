package org.prebid.server.hooks.modules.pb.richmedia.filter.core;

import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.hooks.modules.pb.richmedia.filter.model.AnalyticsResult;
import org.prebid.server.hooks.modules.pb.richmedia.filter.model.MraidFilterResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BidResponsesMraidFilter {

    private static final String TAG_STATUS = "success-block";
    private static final Map<String, Object> TAG_VALUES = Map.of("richmedia-format", "mraid");

    public MraidFilterResult filterByPattern(String mraidScriptPattern,
                                             List<BidderResponse> responses) {

        final List<BidderResponse> filteredResponses = new ArrayList<>();
        final List<AnalyticsResult> analyticsResults = new ArrayList<>();

        for (BidderResponse bidderResponse : responses) {
            final BidderSeatBid seatBid = bidderResponse.getSeatBid();
            final List<BidderBid> originalBids = seatBid.getBids();
            final Map<Boolean, List<BidderBid>> bidsMap = originalBids.stream().collect(
                    Collectors.groupingBy(bid -> StringUtils.contains(bid.getBid().getAdm(), mraidScriptPattern)));

            final List<BidderBid> validBids = bidsMap.getOrDefault(false, Collections.emptyList());
            final List<BidderBid> invalidBids = bidsMap.getOrDefault(true, Collections.emptyList());

            if (validBids.size() == originalBids.size()) {
                filteredResponses.add(bidderResponse.with(seatBid.with(originalBids)));
            } else {
                final List<String> rejectedImps = invalidBids.stream()
                        .map(BidderBid::getBid)
                        .map(Bid::getImpid)
                        .toList();

                final String bidder = bidderResponse.getBidder();

                final AnalyticsResult analyticsResult = AnalyticsResult.of(
                        TAG_STATUS,
                        TAG_VALUES,
                        bidder,
                        rejectedImps,
                        invalidBids,
                        BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
                analyticsResults.add(analyticsResult);

                final List<BidderError> errors = new ArrayList<>(seatBid.getErrors());
                errors.add(BidderError.of("Invalid bid", BidderError.Type.invalid_bid, new HashSet<>(rejectedImps)));
                filteredResponses.add(bidderResponse.with(seatBid.with(validBids, errors)));
            }
        }

        return MraidFilterResult.of(filteredResponses, analyticsResults);
    }

}
