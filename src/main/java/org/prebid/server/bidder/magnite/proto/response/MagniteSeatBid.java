package org.prebid.server.bidder.magnite.proto.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class MagniteSeatBid {

    List<MagniteBid> bid;

    String seat;

    int group;

    String buyer;

    ObjectNode ext;
}
