package org.prebid.server.proto.openrtb.ext.request.metax;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpMetax {

    @JsonProperty("publisherId")
    Integer publisherId;

    @JsonProperty("adunit")
    Integer adUnit;
}
