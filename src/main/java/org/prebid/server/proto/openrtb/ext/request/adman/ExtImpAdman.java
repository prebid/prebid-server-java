package org.prebid.server.proto.openrtb.ext.request.adman;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAdman {

    @JsonProperty("TagID")
    String tagId;
}
