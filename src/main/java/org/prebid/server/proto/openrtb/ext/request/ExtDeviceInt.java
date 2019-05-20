package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * ExtDevice defines the contract for bidrequest.device.ext.prebid.interstitial
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtDeviceInt {

    @JsonProperty("minwidtheperc")
    Integer minWidthPerc;

    @JsonProperty("minheightperc")
    Integer minHeightPerc;
}
