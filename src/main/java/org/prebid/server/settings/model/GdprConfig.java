package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Validated
public class GdprConfig {

    @JsonProperty("host-vendor-id")
    String hostVendorId;

    Boolean enabled;

    @NotBlank
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

