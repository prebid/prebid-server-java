package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class AccountGdprConfig {

    @JsonProperty("enabled")
    Boolean enabled;

    @JsonProperty("channel-enabled")
    @JsonAlias("integration-enabled") // TODO: remove integration after transition period
    EnabledForRequestType enabledForRequestType;

    Purposes purposes;

    @JsonProperty("special-features")
    SpecialFeatures specialFeatures;

    @JsonProperty("purpose-one-treatment-interpretation")
    PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation;

    @JsonProperty("basic-enforcement-vendors")
    List<String> basicEnforcementVendors;
}
