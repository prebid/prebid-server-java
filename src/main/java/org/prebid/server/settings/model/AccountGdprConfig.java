package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class AccountGdprConfig {

    Boolean enabled;

    @JsonProperty("channel_enabled")
    @JsonAlias("channel-enabled")
    EnabledForRequestType enabledForRequestType;

    Purposes purposes;

    @JsonAlias("special-features")
    SpecialFeatures specialFeatures;

    @JsonAlias("purpose-one-treatment-interpretation")
    PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation;

    @JsonAlias("basic-enforcement-vendors")
    List<String> basicEnforcementVendors;
}
