package org.prebid.server.bidder.openx.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class OpenxBidExt {

    @JsonProperty("dsp_id")
    String dspId;
    @JsonProperty("buyer_id")
    String buyerId;
    @JsonProperty("brand_id")
    String brandId;
}
