package org.prebid.server.proto.openrtb.ext.request.yeahmobi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpYeahmobi {

    @JsonProperty("pubId")
    String pubId;

    @JsonProperty("zoneId")
    String zoneId;

}
