package org.prebid.server.bidder.contxtful.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.BidRequest;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value(staticConstructor = "of")
public class ContxtfulCompositeRequest {

    @JsonProperty("ortb2")
    BidRequest ortb2Request;

    @JsonProperty("bidRequests")
    List<ContxtfulBidRequest> bidRequests;

    @JsonProperty("bidderRequest")
    ContxtfulBidderRequest bidderRequest;

    ContxtfulConfig config;
}
