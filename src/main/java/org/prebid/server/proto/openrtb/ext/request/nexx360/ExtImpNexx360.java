package org.prebid.server.proto.openrtb.ext.request.nexx360;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpNexx360 {

    @JsonProperty("tagId")
    String tagId;

    String placement;
}
