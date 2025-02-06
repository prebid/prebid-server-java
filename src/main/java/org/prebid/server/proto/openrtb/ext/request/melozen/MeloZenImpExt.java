package org.prebid.server.proto.openrtb.ext.request.melozen;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class MeloZenImpExt {

    @JsonProperty("pubId")
    String pubId;

}
