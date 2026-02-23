package org.prebid.server.proto.openrtb.ext.request.beintoo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBeintoo {

    @JsonProperty("tagid")
    String tagId;

    @JsonProperty("bidfloor")
    String bidFloor;
}
