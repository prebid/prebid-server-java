package org.prebid.server.proto.openrtb.ext.request.teal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpTeal {

    @JsonProperty("account")
    String account;

    @JsonProperty("placement")
    String placement;
}
