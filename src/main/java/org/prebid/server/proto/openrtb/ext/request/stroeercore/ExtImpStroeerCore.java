package org.prebid.server.proto.openrtb.ext.request.stroeercore;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpStroeerCore {

    @JsonProperty("sid")
    String slotId;
}

