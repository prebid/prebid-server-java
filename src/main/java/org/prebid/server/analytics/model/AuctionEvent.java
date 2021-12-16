package org.prebid.server.analytics.model;

import com.iab.openrtb.response.BidResponse;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.model.HttpRequestContext;

import java.util.List;

/**
 * Represents a transaction at /openrtb2/auction endpoint.
 */
@Builder(toBuilder = true)
@Value
public class AuctionEvent {

    Integer status;

    List<String> errors;

    HttpRequestContext httpContext;

    AuctionContext auctionContext;

    BidResponse bidResponse;
}
