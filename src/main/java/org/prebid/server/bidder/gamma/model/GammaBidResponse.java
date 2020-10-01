package org.prebid.server.bidder.gamma.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class GammaBidResponse {

    String id;

    List<GammaSeatBid> seatbid;

    String bidid;

    String cur;

    String customdata;

    Integer nbr;

    ObjectNode ext;
}
