package org.prebid.server.proto.openrtb.ext.request.vrtcal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.Vrtcal
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpVrtcal {

    @JsonProperty("Just_an_unused_vrtcal_param")
    String justAnUnusedVrtcalParam;
}
