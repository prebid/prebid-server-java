package org.prebid.server.proto.openrtb.ext.request.viant;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpViant {

    @JsonProperty("publisherId")
    String publisherId;
}
