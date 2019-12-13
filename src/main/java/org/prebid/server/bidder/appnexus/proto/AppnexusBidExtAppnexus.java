package org.prebid.server.bidder.appnexus.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class AppnexusBidExtAppnexus {

    Integer bidAdType;

    Integer brandId;

    Integer brandCategoryId;

    AppnexusBidExtCreative creativeInfo;
}
