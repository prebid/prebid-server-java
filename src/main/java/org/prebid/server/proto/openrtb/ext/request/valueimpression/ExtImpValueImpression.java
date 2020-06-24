package org.prebid.server.proto.openrtb.ext.request.valueimpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpValueImpression {

    @JsonProperty("siteId")
    String siteId;
}
