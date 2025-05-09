package org.prebid.server.bidder.openx.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class OpenxRequestExt {

    @JsonProperty("delDomain")
    String delDomain;

    @JsonProperty("platform")
    String platform;

    String bc;
}
