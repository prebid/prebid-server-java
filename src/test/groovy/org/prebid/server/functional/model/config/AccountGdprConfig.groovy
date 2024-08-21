package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.ChannelType

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class AccountGdprConfig {

    Boolean enabled
    String eeaCountries
    Map<ChannelType, Boolean> channelEnabled
    @JsonProperty("channel_enabled")
    Map<ChannelType, Boolean> channelEnabledSnakeCase
    Map<Purpose, PurposeConfig> purposes
    Map<SpecialFeature, SpecialFeatureConfig> specialFeatures
    @JsonProperty("special_features")
    Map<SpecialFeature, SpecialFeatureConfig> specialFeaturesSnakeCase
    PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation
    @JsonProperty("purpose_one_treatment_interpretation")
    PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretationSnakeCase
    List<String> basicEnforcementVendors
    @JsonProperty("basic_enforcement_vendors")
    List<String> basicEnforcementVendorsSnakeCase
}
