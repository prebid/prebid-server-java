package org.prebid.server.bidder.peak226.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Peak226SeatBid {

    @JsonProperty("bid")
    List<ObjectNode> bid;

}
