package org.prebid.server.bidder.magnite.proto.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class MagniteBidResponse {

    String id;

    List<MagniteSeatBid> seatbid;

    String bidid;

    String cur;

    String customdata;

    Integer nbr;

    ObjectNode ext;
}
