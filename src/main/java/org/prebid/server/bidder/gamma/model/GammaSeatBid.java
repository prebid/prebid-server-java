package org.prebid.server.bidder.gamma.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class GammaSeatBid {

    List<GammaBid> bid;

    String seat;

    int group;

    ObjectNode ext;
}
