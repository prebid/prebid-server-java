package org.prebid.server.proto.openrtb.ext.request.adverxo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpAdverxo {

    @JsonProperty("adUnitId")
    Integer adUnitId;
    String auth;
}
