package org.prebid.server.proto.openrtb.ext.request.adagio;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAdagio {

    @JsonProperty("organizationId")
    String organizationId;

    String placement;

    String pagetype;

    String category;
}
