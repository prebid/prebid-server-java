package org.prebid.server.bidder.appnexus.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class AppnexusReqExtAppnexus {

    Boolean includeBrandCategory;

    Boolean brandCategoryUniqueness;

    Integer isAmp;

    @JsonProperty("hb_source")
    Integer headerBiddingSource;

    @JsonProperty("adpod_id")
    String adPodId;
}
