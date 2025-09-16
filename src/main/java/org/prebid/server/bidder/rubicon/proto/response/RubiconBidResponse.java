package org.prebid.server.bidder.rubicon.proto.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class RubiconBidResponse {

    String id;

    List<RubiconSeatBid> seatbid;

    String bidid;

    String cur;

    String customdata;

    Integer nbr;

    ObjectNode ext;
}
