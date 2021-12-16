package org.prebid.server.bidder.appnexus.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value(staticConstructor = "of")
public class AppnexusReqExtAppnexus {

    Boolean includeBrandCategory;

    Boolean brandCategoryUniqueness;

    Integer isAmp;

    @JsonProperty("hb_source")
    Integer headerBiddingSource;

    String adpodId;
}
