package org.prebid.server.bidder.mabidder.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class MabidderResponse {

    @JsonProperty("Responses")
    List<MabidderBidResponse> bidResponses;

}
