package org.prebid.server.bidder.openx.proto;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class OpenxBidExt {

    String dspId;
    String buyerId;
    String brandId;
}
