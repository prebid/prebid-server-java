package org.prebid.server.proto.openrtb.ext.request.telaria;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpOutTelaria {

    @JsonProperty("originalTagid")
    String originalTagid;

    @JsonProperty("originalPublisherid")
    String originalPublisherid;
}
