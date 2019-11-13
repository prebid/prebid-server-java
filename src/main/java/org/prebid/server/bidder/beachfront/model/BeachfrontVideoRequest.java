package org.prebid.server.bidder.beachfront.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.BidRequest;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class BeachfrontVideoRequest {

    @JsonProperty("isPrebid")
    Boolean isPrebid;

    @JsonProperty("appId")
    String appId;

    @JsonProperty("videoResponseType")
    String videoResponseType;

    BidRequest request;
}
