package org.prebid.server.proto.openrtb.ext.request.amx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAmx {

    @JsonProperty("tagId")
    String tagId;

    @JsonProperty("adUnitId")
    String adUnitId;
}
