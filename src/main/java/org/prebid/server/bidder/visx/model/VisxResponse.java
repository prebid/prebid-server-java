package org.prebid.server.bidder.visx.model;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class VisxResponse {

    List<VisxSeatBid> seatbid;
}
