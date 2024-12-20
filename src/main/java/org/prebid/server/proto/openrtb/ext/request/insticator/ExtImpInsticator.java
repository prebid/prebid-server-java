package org.prebid.server.proto.openrtb.ext.request.insticator;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpInsticator {

    @JsonProperty("adUnitId")
    String adUnitId;

    @JsonProperty("publisherId")
    String publisherId;
}
