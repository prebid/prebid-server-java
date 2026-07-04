package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.ext.prebid.sdk
 */
@Value
public class ExtRequestPrebidSdk {

    /**
     * Defines the contract for bidrequest.ext.prebid.sdk.renderers
     */
    List<ExtRequestPrebidSdkRenderer> renderers;

    /**
     * Defines the contract for bidrequest.ext.prebid.sdk.usepxratio
     */
    @JsonProperty("usepxratio")
    Boolean usePxRatio;

    public static ExtRequestPrebidSdk of(List<ExtRequestPrebidSdkRenderer> renderers, Boolean usePxRatio) {
        return new ExtRequestPrebidSdk(renderers, usePxRatio);
    }
}
