package org.prebid.server.bidder.openx.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class OpenxRequestExt {

    @JsonProperty("delDomain")
    String delDomain;

    @JsonProperty("platform")
    String platform;

    String bc;
}
