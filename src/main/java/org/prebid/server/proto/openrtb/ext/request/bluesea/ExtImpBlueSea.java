package org.prebid.server.proto.openrtb.ext.request.bluesea;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBlueSea {

    @JsonProperty("pubid")
    String pubId;

    String token;
}
