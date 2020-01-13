package org.prebid.server.proto.openrtb.ext.request.applogy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpApplogy {

    @JsonProperty("token")
    String token;
}
