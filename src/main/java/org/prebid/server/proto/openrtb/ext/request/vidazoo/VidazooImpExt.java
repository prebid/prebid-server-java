package org.prebid.server.proto.openrtb.ext.request.vidazoo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class VidazooImpExt {

    @JsonProperty("cId")
    String connectionId;

}
