package org.prebid.server.hooks.modules.pb.richmedia.filter.core;

import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.StringUtils;
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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class BidResponsesMraidFilter {

    private static final String TAG_STATUS = "success-block";
    private static final Map<String, Object> TAG_VALUES = Map.of("richmedia-format", "mraid");

    private final String mraidScriptPattern;

    public BidResponsesMraidFilter(String mraidScriptPattern) {
        this.mraidScriptPattern = Objects.requireNonNull(mraidScriptPattern);
    }

    public MraidFilterResult filter(List<BidderResponse> responses) {
        List<BidderResponse> filteredResponses = new ArrayList<>();
        List<AnalyticsResult> analyticsResults = new ArrayList<>();

        for (BidderResponse bidderResponse : responses) {
            final BidderSeatBid seatBid = bidderResponse.getSeatBid();
            final List<BidderBid> originalBids = seatBid.getBids();
            final Map<Boolean, List<BidderBid>> bidsMap = originalBids.stream()
                    .collect(Collectors.groupingBy(this::hasMraid));

            final List<BidderBid> validBids = Optional.ofNullable(bidsMap.get(Boolean.FALSE))
                    .orElse(Collections.emptyList());
            final List<BidderBid> invalidBids = Optional.ofNullable(bidsMap.get(Boolean.TRUE))
                    .orElse(Collections.emptyList());

            if (validBids.size() == originalBids.size()) {
                filteredResponses.add(bidderResponse.with(seatBid.with(originalBids)));
            } else {
                final List<String> rejectedImps = invalidBids.stream()
                        .map(BidderBid::getBid)
                        .map(Bid::getImpid)
                        .toList();
                final AnalyticsResult analyticsResult = AnalyticsResult.of(
                        TAG_STATUS,
                        TAG_VALUES,
                        bidderResponse.getBidder(),
                        rejectedImps);
                analyticsResults.add(analyticsResult);

                final List<BidderError> errors = new ArrayList<>(seatBid.getErrors());
                errors.add(BidderError.of("Invalid creatives", BidderError.Type.invalid_creative, new HashSet<>(rejectedImps)));
                filteredResponses.add(bidderResponse.with(seatBid.with(validBids, errors)));
            }
        }

        return MraidFilterResult.of(filteredResponses, analyticsResults);
    }

    private boolean hasMraid(BidderBid bid) {
        return StringUtils.contains(bid.getBid().getAdm(), mraidScriptPattern);
    }
}
