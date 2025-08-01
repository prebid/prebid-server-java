package org.prebid.server.proto.openrtb.ext.request.smilewanted;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpSmilewanted {

    @JsonProperty("zoneId")
    String zoneId;
}
