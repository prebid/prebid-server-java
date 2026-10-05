package org.prebid.server.bidder.msft.proto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class ExtRequestMsft {

    Boolean includeBrandCategory;

    Boolean brandCategoryUniqueness;

    Integer isAmp;

    Integer hbSource;
}
