package org.prebid.server.bidder.rubicon.proto.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class RubiconSeatBid {

    List<RubiconBid> bid;

    String seat;

    int group;

    String buyer;

    ObjectNode ext;
}
