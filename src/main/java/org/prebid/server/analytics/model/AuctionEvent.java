package org.prebid.server.analytics.model;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Represents a transaction at /openrtb2/auction endpoint.
 */
@Builder
@Value
public class AuctionEvent {

    Integer status;

    List<String> errors;

    Map<String, String> headers;

    Map<String, String> cookies;

    BidRequest bidRequest;

    BidResponse bidResponse;
}
