package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.rubicon.debug
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestRubiconDebug {

    /**
     * This should be used only for testing.
     * <p>
     * CPM for all bids will be replaced with this field value.
     */
    @JsonProperty("cpmOverride")
    Float cpmOverride;
}
