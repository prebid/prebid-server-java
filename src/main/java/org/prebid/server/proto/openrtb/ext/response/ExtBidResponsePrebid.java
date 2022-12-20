package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Defines the contract for bidresponse.ext.prebid
 */
@Value(staticConstructor = "of")
@Builder(toBuilder = true)
public class ExtBidResponsePrebid {

    /**
     * Defines the contract for bidresponse.ext.prebid.auctiontimstamp
     */
    Long auctiontimestamp;

    /**
     * Defines the contract for bidresponse.ext.prebid.modules
     */
    ExtModules modules;

    JsonNode passthrough;

    /**
     * Additional targeting key/values for the bid response (only used for AMP)
     * Set targeting options here that will occur in the bidResponse no matter if
     * a bid won the auction or not.
     */
    Map<String, JsonNode> targeting;

    public static ExtBidResponsePrebid of(Long auctiontimestamp, ExtModules modules, JsonNode passthrough, Map<String, JsonNode> targeting) {
        return ExtBidResponsePrebid.builder()
                .auctiontimestamp(auctiontimestamp)
                .modules(modules)
                .passthrough(passthrough)
                .targeting(targeting)
                .build();
    }

    @JsonProperty("seatnonbid")
    List<SeatNonBid> seatNonBid;

    @Value(staticConstructor = "of")
    public static class SeatNonBid {
        String seat;

        @JsonProperty("nonbid")
        List<NonBid> nonBid;
    }

    @Value(staticConstructor = "of")
    public static class NonBid {
        String impId;

        int reason;
    }
}
