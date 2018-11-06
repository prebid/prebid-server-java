package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestPrebidCacheBids {

    Integer ttlseconds;

    @JsonProperty("returnCreative")
    Boolean returnCreative;
}
