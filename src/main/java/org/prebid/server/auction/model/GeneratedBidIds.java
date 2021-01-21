package org.prebid.server.auction.model;

import com.iab.openrtb.response.Bid;
import lombok.AllArgsConstructor;
import org.prebid.server.bidder.model.BidderBid;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;


/**
 * Class creates and holds generated bid ids per bidder per imp.
 */
@AllArgsConstructor
public class GeneratedBidIds {

    private static final String BID_IMP_ID_PATTERN = "%s-%s";

    private final Map<String, Map<String, String>> bidderToBidIds;

    /**
     * Creates an object of {@link GeneratedBidIds} with {@link Map} where key is a bidder and value is another map,
     * that have pair of bidId-impId to uniquely identify the bid withing the bidder and as value has bid Id generated
     * by idGenerationStrategy parameter.
     */
    public static GeneratedBidIds of(List<BidderResponse> bidderResponses,
                                     BiFunction<String, Bid, String> idGenerationStrategy) {
        final Map<String, Map<String, String>> generatedBidIds = bidderResponses.stream()
                .collect(Collectors.toMap(BidderResponse::getBidder,
                        bidderResponse -> bidderResponse.getSeatBid()
                                .getBids().stream()
                                .map(BidderBid::getBid)
                                .collect(Collectors.toMap(bid -> makeUniqueBidId(bid.getId(), bid.getImpid()),
                                        bid -> idGenerationStrategy.apply(bidderResponse.getBidder(), bid)))));
        return new GeneratedBidIds(generatedBidIds);
    }

    /**
     * Returns empty {@link GeneratedBidIds}.
     */
    public static GeneratedBidIds empty() {
        return new GeneratedBidIds(Collections.emptyMap());
    }

    /**
     * Creates unique identifier for bid that consist from pair bidId-impId.
     */
    private static String makeUniqueBidId(String bidId, String impId) {
        return String.format(BID_IMP_ID_PATTERN, bidId, impId);
    }

    /**
     * Returns bidder for bidId and impId parameters.
     */
    public Optional<String> getBidderForBid(String bidId, String impId) {
        final String uniqueBidId = makeUniqueBidId(bidId, impId);
        return bidderToBidIds.entrySet().stream()
                .filter(bidIdToGeneratedId -> bidIdToGeneratedId.getValue().containsKey(uniqueBidId))
                .findFirst()
                .map(Map.Entry::getKey);
    }

    /**
     * Returns generated id for bid identified by bidder, bidId and impId parameters.
     */
    public String getGeneratedId(String bidder, String bidId, String impId) {
        if (bidderToBidIds.containsKey(bidder)) {
            return bidderToBidIds.get(bidder).get(makeUniqueBidId(bidId, impId));
        } else {
            return null;
        }
    }

    public Map<String, Map<String, String>> getBidderToBidIds() {
        return bidderToBidIds;
    }
}
