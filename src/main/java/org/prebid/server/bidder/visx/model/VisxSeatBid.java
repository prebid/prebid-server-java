package org.prebid.server.bidder.visx.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class VisxSeatBid {

    List<VisxBid> bid;

    String seat;
}
