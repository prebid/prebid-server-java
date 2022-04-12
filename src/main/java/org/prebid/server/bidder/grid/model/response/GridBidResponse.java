package org.prebid.server.bidder.grid.model.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class GridBidResponse {

    String id;

    List<GridSeatBid> seatbid;

    String bidid;

    String cur;

    String customdata;

    Integer nbr;

    ObjectNode ext;
}
