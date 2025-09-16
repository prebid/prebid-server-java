package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * ExtDevice defines the contract for bidrequest.device.ext.prebid.interstitial
 */
@Value(staticConstructor = "of")
public class ExtDeviceInt {

    @JsonProperty("minwidthperc")
    Integer minWidthPerc;

    @JsonProperty("minheightperc")
    Integer minHeightPerc;
}
