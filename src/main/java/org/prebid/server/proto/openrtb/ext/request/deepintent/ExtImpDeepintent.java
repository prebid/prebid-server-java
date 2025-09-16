package org.prebid.server.proto.openrtb.ext.request.deepintent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpDeepintent {

    @JsonProperty("tagId")
    String tagId;
}
