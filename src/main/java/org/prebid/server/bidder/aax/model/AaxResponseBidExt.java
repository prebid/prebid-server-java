package org.prebid.server.bidder.aax.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AaxResponseBidExt {

    @JsonProperty("adCodeType")
    String adCodeType;
}
