package org.prebid.server.bidder.grid.model.responce;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class GridSeatBid {

    List<GridBid> bid;

    String seat;

    int group;

    ObjectNode ext;
}
