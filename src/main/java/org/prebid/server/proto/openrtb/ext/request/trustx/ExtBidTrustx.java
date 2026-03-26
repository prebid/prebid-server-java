package org.prebid.server.proto.openrtb.ext.request.trustx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtBidTrustx {

    @JsonProperty("networkName")
    String networkName;
}
