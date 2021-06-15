package org.prebid.server.bidder.criteo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class CriteoGdprConsent {

    @JsonProperty("gdprapplies")
    Boolean gdprApplies;

    @JsonProperty("consentdata")
    String consentData;
}
