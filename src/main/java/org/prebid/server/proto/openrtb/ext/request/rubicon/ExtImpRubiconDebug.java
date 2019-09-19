package org.prebid.server.proto.openrtb.ext.request.rubicon;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.rubicon.debug
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpRubiconDebug {

    /**
     * This should be used only for testing.
     * <p>
     * CPM for bid will be replaced with this value.
     */
    @JsonProperty("cpmOverride")
    Float cpmOverride;
}
