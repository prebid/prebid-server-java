package org.prebid.server.proto.openrtb.ext.request.kueezrtb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class KueezImpExt {

    @JsonProperty("cId")
    String connectionId;

}
