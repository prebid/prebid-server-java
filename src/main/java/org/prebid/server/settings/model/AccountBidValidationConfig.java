package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AccountBidValidationConfig {

    @JsonProperty("banner-creative-max-size")
    BidValidationEnforcement bannerMaxSizeEnforcement;
}
