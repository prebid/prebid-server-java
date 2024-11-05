package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class PbRequestCorrectionConfig {

    @JsonProperty("pbsdkAndroidInstlRemove")
    Boolean interstitialCorrectionEnabled
    @JsonProperty("pbsdkUaCleanup")
    Boolean userAgentCorrectionEnabled
    Boolean enabled

    static PbRequestCorrectionConfig getDefaultConfigWithInterstitial(Boolean interstitialCorrectionEnabled = true,
                                                                      Boolean enabled = true) {
        new PbRequestCorrectionConfig(enabled: enabled, interstitialCorrectionEnabled: interstitialCorrectionEnabled)
    }

    static PbRequestCorrectionConfig getDefaultConfigWithUserAgentCorrection(Boolean userAgentCorrectionEnabled = true,
                                                                             Boolean enabled = true) {
        new PbRequestCorrectionConfig(enabled: enabled, userAgentCorrectionEnabled: userAgentCorrectionEnabled)
    }
}
