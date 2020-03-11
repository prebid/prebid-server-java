package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Gdpr {

    @JsonProperty("host-vendor-id")
    String hostVendorId;

    @JsonProperty(defaultValue = "true")
    Boolean enabled;

    @JsonProperty(value = "default-value")
    Boolean defaultValue;

    Purposes purposes;

    @JsonProperty("special-features")
    SpecialFeatures specialFeatures;

    @JsonProperty("purpose-one-treatment-interpretation")
    Boolean purposeOneTreatmentInterpretation;
}

