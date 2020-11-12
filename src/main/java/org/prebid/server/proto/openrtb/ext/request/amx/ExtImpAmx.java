package org.prebid.server.proto.openrtb.ext.request.amx;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAmx {

    @JsonProperty("tagId")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String tagId;

    @JsonProperty("adUnitId")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String adUnitId;
}
