package org.prebid.server.proto.openrtb.ext.request.unruly;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpUnruly {

    @JsonProperty("siteId")
    @JsonAlias({"siteid"})
    Integer siteId;
}
