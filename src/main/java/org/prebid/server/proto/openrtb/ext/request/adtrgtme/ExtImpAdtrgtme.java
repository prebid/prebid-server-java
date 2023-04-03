package org.prebid.server.proto.openrtb.ext.request.adtrgtme;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAdtrgtme {

    @JsonProperty("siteId")
    Integer siteId;
}
