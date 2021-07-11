package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpCriteo {

    @JsonProperty("zoneId")
    @JsonAlias({"zoneid"})
    Integer zoneId;

    @JsonProperty("networkId")
    @JsonAlias({"networkid"})
    Integer networkId;
}
