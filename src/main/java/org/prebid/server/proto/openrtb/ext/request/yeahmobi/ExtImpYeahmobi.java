package org.prebid.server.proto.openrtb.ext.request.yeahmobi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpYeahmobi {

    @JsonProperty("pubId")
    String pubId;

    @JsonProperty("zoneId")
    String zoneId;
}
