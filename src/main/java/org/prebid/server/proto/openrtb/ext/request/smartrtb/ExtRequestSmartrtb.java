package org.prebid.server.proto.openrtb.ext.request.smartrtb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestSmartrtb {

    @JsonProperty("pub_id")
    String pubId;

    @JsonProperty("zone_id")
    String zoneId;

    @JsonProperty("force_bid")
    Boolean forceBid;
}
