package org.prebid.server.proto.openrtb.ext.request.gumgum;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpGumgumVideo {

    @JsonProperty("irisid")
    String irisId;
}
