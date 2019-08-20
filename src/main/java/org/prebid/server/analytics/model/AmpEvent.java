package org.prebid.server.analytics.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.response.BidResponse;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.model.AuctionContext;

import java.util.List;
import java.util.Map;

/**
 * Represents a transaction at /openrtb2/amp endpoint.
 */
@Builder
@Value
public class AmpEvent {

    Integer status;

    List<String> errors;

    HttpContext httpContext;

    AuctionContext auctionContext;

    BidResponse bidResponse;

    Map<String, JsonNode> targeting;

    String origin;
}
