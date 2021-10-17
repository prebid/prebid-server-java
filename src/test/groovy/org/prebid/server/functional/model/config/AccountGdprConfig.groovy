package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategy.KebabCaseStrategy.class)
class AccountGdprConfig {

    Boolean enabled
    Map<ChannelType, Boolean> enabledForRequestType
    Map<Purpose, PurposeConfig> purposes
    Map<SpecialFeature, SpecialFeatureConfig> specialFeatures
    PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation
    List<String> basicEnforcementVendors
}
