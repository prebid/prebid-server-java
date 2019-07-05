package org.prebid.server.bidder.mgid.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.BidType;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtBidMgid {

    BidType crtype;
}
