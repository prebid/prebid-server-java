package org.prebid.server.proto.openrtb.ext.request.tappx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.Tappx
 */
@AllArgsConstructor(
        staticName = "of"
)
@Value
public class ExtImpTappx {
    @JsonProperty("host")
    String host;

    @JsonProperty("tappxkey")
    String tappxkey;

    @JsonProperty("endpoint")
    String endpoint;
}

