package org.prebid.server.hooks.modules.pb.request.correction.core.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class Config {

    boolean enabled;

    @JsonProperty("pbsdk-android-instl-remove")
    boolean interstitialCorrectionEnabled;

    @JsonProperty("pbsdk-ua-cleanup")
    boolean userAgentCorrectionEnabled;
}
