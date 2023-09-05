package org.prebid.server.bidder.huaweiads.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class HuaweiAdsResponse {

    @JsonProperty("retcode")
    Integer retcode;

    @JsonProperty("reason")
    String reason;

    @JsonProperty("multiad")
    List<Ad30> multiad;
}
