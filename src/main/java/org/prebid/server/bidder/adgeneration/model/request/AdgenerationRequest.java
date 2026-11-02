package org.prebid.server.bidder.adgeneration.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.BidRequest;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class AdgenerationRequest {

    String currency;
    @JsonProperty("pbver")
    String pbVersion;
    @JsonProperty("sdkname")
    String sdkName;
    @JsonProperty("adapterver")
    String adapterVersion;
    BidRequest ortb;
    @JsonProperty("imark")
    Integer iMark;
}
