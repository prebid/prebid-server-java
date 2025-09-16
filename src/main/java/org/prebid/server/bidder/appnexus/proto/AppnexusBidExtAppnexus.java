package org.prebid.server.bidder.appnexus.proto;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class AppnexusBidExtAppnexus {

    int bidAdType;

    Integer brandId;

    Integer brandCategoryId;

    AppnexusBidExtCreative creativeInfo;

    int dealPriority;
}
