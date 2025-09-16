package org.prebid.server.proto.openrtb.ext.request.amx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAmx {

    @JsonProperty("tagId")
    String tagId;

    @JsonProperty("adUnitId")
    String adUnitId;
}
