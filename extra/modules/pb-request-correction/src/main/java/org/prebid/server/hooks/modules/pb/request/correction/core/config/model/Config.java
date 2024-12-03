package org.prebid.server.hooks.modules.pb.request.correction.core.config.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Config {

    boolean enabled;

    @JsonAlias("pbsdkAndroidInstlRemove")
    @JsonProperty("pbsdk-android-instl-remove")
    boolean interstitialCorrectionEnabled;

    @JsonAlias("pbsdkUaCleanup")
    @JsonProperty("pbsdk-ua-cleanup")
    boolean userAgentCorrectionEnabled;
}
