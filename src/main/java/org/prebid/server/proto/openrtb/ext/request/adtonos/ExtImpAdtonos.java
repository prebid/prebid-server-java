package org.prebid.server.proto.openrtb.ext.request.adtonos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAdtonos {

    @JsonProperty("supplierId")
    String supplierId;
}
