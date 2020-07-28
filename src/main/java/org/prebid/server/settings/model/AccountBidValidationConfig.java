package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class AccountBidValidationConfig {

    @JsonProperty("banner-creative-allowed-sizes")
    List<String> bannerCreativeAllowedSizes;
}
