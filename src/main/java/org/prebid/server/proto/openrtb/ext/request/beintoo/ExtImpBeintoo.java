package org.prebid.server.proto.openrtb.ext.request.beintoo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpBeintoo {

    @JsonProperty("tagid")
    String tagId;

    @JsonProperty("bidfloor")
    String bidFloor;
}
