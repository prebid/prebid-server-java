package org.prebid.server.bidder.openx.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class OpenxImpExt {

    @JsonProperty("customParams")
    Map<String, String> customParams;
}
