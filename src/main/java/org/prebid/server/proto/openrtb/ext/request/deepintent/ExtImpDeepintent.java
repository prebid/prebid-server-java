package org.prebid.server.proto.openrtb.ext.request.deepintent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpDeepintent {

    @JsonProperty("tagId")
    String tagId;
}
