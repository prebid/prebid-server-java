package org.prebid.server.bidder.peak226.model;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Peak226Response {

    String cur;

    List<Peak226SeatBid> seatbid;

}
