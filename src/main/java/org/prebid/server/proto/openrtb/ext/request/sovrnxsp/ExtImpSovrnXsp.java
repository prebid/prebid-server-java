package org.prebid.server.proto.openrtb.ext.request.sovrnxsp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value(staticConstructor = "of")
public class ExtImpSovrnXsp {

    @JsonProperty("pub_id")
    String pubId;

    @JsonProperty("med_id")
    String medId;

    @JsonProperty("zone_id")
    String zoneId;

    @JsonProperty("force_bid")
    Boolean forceBid;

}
