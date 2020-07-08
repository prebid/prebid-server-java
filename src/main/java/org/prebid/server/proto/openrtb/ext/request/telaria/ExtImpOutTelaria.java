package org.prebid.server.proto.openrtb.ext.request.telaria;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpOutTelaria {

    @JsonProperty("originalTagid")
    String originalTagid;

    @JsonProperty("originalPublisherid")
    String originalPublisherid;
}
