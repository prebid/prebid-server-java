package org.prebid.server.bidder.mgid.model;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.BidType;

@Value(staticConstructor = "of")
public class ExtBidMgid {

    BidType crtype;
}
