package org.prebid.server.proto.openrtb.ext.request.vidoomy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpVidoomy {

    @JsonProperty("zoneId")
    String zoneId;

}
