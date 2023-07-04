package org.prebid.server.bidder.flipp.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class Prebid {

    @JsonProperty("cpm")
    BigDecimal cpm;

    @JsonProperty("creative")
    String creative;

    @JsonProperty("creativeType")
    String creativeType;

    @JsonProperty("requestId")
    String requestId;
}
