package org.prebid.server.bidder.stroeercore.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class StroeerCoreBidResponse {

    @JsonProperty(required = true)
    List<StroeerCoreBid> bids;
}
