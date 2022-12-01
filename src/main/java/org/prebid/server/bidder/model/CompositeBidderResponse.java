package org.prebid.server.bidder.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.bidder.Bidder;

import java.util.Collections;
import java.util.List;

/**
 * Composite bidder response (bids + other data) returned by a {@link Bidder}.
 */
@AllArgsConstructor(staticName = "of")
@Builder(toBuilder = true)
@Value
public class CompositeBidderResponse {

    List<BidderBid> bids;

    List<BidderError> errors;

    /** FLEDGE interest group bids passback */
    List<JsonNode> fledgeConfigs;

    public static CompositeBidderResponse empty() {
        return new CompositeBidderResponse(Collections.emptyList(), Collections.emptyList(), null);
    }
}
