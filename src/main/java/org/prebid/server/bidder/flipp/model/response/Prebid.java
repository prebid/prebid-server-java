package org.prebid.server.bidder.flipp.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class Prebid {

    BigDecimal cpm;

    String creative;

    @JsonProperty("creativeType")
    String creativeType;

    @JsonProperty("requestId")
    String requestId;
}
