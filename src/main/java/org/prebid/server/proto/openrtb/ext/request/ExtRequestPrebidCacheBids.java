package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtRequestPrebidCacheBids {

    Integer ttlseconds;

    @JsonProperty("returnCreative")
    Boolean returnCreative;
}
