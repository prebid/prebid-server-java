package org.prebid.server.bidder.msft.proto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MsftBidExt {

    int bidAdType;

    Integer brandId;

    Integer brandCategoryId;

    MsftBidExtCreative creativeInfo;

    int dealPriority;
}
