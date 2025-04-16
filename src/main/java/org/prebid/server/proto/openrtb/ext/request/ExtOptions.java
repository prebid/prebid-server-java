package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * ExtRegs defines the contract for ext.prebid.options
 */
@Value(staticConstructor = "of")
public class ExtOptions {

    @JsonProperty("echovideoattrs")
    Boolean echoVideoAttrs;
}
