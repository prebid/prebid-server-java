package org.prebid.server.bidder.somoaudience.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class SomoaudienceReqExt {

    @JsonProperty("prebid")
    String bidderConfig;
}
