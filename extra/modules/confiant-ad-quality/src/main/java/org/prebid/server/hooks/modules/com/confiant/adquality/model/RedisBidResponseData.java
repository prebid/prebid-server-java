package org.prebid.server.hooks.modules.com.confiant.adquality.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.response.BidResponse;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class RedisBidResponseData {

    BidResponse bidresponse;

    @JsonProperty("dsp_id")
    String dspId;

    String crid;
}
