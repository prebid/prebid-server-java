package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class GdprConfig {

    @JsonProperty("host-vendor-id")
    String hostVendorId;

    Boolean enabled;

    @NotNull
    @JsonProperty("default-value")
    String defaultValue;

    @JsonProperty("consent-string-means-in-scope")
    Boolean consentStringMeansInScope;

    Purposes purposes;

    @JsonProperty("special-features")
    SpecialFeatures specialFeatures;

    @JsonProperty("purpose-one-treatment-interpretation")
    PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation;
}

