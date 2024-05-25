package org.prebid.server.proto.openrtb.ext.request.theadx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpTheadx {

    @JsonProperty("tagid")
    String tagId;

    @JsonProperty("wid")
    Integer inventorySourceId;

    @JsonProperty("pid")
    Integer memberId;

    @JsonProperty("pname")
    String placementName;

}

