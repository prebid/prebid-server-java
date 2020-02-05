package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * ExtRegs defines the contract for ext.prebid.options
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtOptions {

    @JsonProperty("echovideoattrs")
    Boolean echoVideoAttrs;
}
