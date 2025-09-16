package org.prebid.server.proto.openrtb.ext.request.silvermob;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpSilvermob {

    @JsonProperty("zoneid")
    String zoneId;

    String host;
}
