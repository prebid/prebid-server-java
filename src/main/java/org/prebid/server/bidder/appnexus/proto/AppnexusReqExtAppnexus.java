package org.prebid.server.bidder.appnexus.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class AppnexusReqExtAppnexus {

    Boolean includeBrandCategory;

    Boolean brandCategoryUniqueness;
}
