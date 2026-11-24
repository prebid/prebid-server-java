package org.prebid.server.bidder.peak226.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Peak226SeatBid {

    List<ObjectNode> bid;

}
