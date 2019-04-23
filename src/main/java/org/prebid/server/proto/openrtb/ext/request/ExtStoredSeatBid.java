package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

@Value
public class ExtStoredSeatBid {

    String bidder;

    String id;
}
