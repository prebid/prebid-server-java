package org.prebid.server.bidder.resetdigital.response;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ResetDigitalResponse {

    List<ResetDigitalBid> bids;
}
