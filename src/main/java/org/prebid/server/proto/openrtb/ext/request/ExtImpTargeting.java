package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class ExtImpTargeting {

    @JsonProperty("preferdeals")
    Boolean preferDeals;
}
