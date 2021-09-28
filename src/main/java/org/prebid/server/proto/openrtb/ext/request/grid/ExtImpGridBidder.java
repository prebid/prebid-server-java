package org.prebid.server.proto.openrtb.ext.request.grid;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpGridBidder {

    Integer uid;

    Keywords keywords;
}
