package org.prebid.server.proto.openrtb.ext.request.trustx;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtBidBidderTrustx {

    ExtBidTrustx trustx;
}
