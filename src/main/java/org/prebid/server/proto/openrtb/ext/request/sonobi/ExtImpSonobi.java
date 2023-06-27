package org.prebid.server.proto.openrtb.ext.request.sonobi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpSonobi {

    @JsonProperty("TagID")
    String tagId;
}
