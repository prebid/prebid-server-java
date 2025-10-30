package org.prebid.server.proto.openrtb.ext.request.akcelo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAkcelo {

    @JsonProperty("adUnitId")
    Integer adUnitId;

    @JsonProperty("siteId")
    String siteId;

    Integer test;
}
