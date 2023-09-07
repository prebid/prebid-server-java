package org.prebid.server.proto.openrtb.ext.request.flipp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpFlippOptions {

    @JsonProperty("startCompact")
    Boolean startCompact;

    @JsonProperty("dwellExpand")
    Boolean dwellExpand;

    @JsonProperty("contentCode")
    String contentCode;
}
