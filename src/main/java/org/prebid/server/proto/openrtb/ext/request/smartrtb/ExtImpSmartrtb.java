package org.prebid.server.proto.openrtb.ext.request.smartrtb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpSmartrtb {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("propertyId")
    String propertyId;

    @JsonProperty("zoneId")
    String zoneId;

    Boolean forceBid;
}
