package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Gdpr {

    @JsonProperty("host-vendor-id")
    String hostVendorId;

    @JsonProperty(defaultValue = "true")
    Boolean enabled;

    @JsonProperty(value = "default-value")
    String defaultValue;

    Purposes purposes;

    @JsonProperty("special-features")
    SpecialFeatures specialFeatures;

    @JsonProperty("purpose-one-treatment-interpretation")
    Boolean purposeOneTreatmentInterpretation;
}

