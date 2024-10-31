package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class PbRequestCorrectionConfig {

    @JsonProperty("pbsdk-android-instl-remove")
    Boolean interstitialCorrectionEnabled
    @JsonProperty("pbsdk-ua-cleanup")
    Boolean userAgentCorrectionEnabled
    Boolean enabled

    static PbRequestCorrectionConfig getDefaultEnabledConfig(Boolean enabled = true) {
        new PbRequestCorrectionConfig(enabled: enabled)
    }

    static PbRequestCorrectionConfig getDefaultConfigWithInterstitial(Boolean interstitialCorrectionEnabled = true,
                                                                      Boolean enabled = true) {
        new PbRequestCorrectionConfig(enabled: enabled, interstitialCorrectionEnabled: interstitialCorrectionEnabled)
    }

    static PbRequestCorrectionConfig getDefaultConfigWithUserAgentCorrection(Boolean userAgentCorrectionEnabled = true,
                                                                             Boolean enabled = true) {
        new PbRequestCorrectionConfig(enabled: enabled, userAgentCorrectionEnabled: userAgentCorrectionEnabled)
    }
}
