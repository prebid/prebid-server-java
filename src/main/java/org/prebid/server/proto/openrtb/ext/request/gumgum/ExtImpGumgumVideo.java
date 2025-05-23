package org.prebid.server.proto.openrtb.ext.request.gumgum;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpGumgumVideo {

    @JsonProperty("irisid")
    String irisId;
}
