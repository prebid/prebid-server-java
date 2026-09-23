package org.prebid.server.proto.openrtb.ext.request.eskimi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value(staticConstructor = "of")
public class ExtImpEskimi {

    @JsonProperty("placementId")
    Integer placementId;

    @JsonProperty("bidFloor")
    BigDecimal bidFloor;

    @JsonProperty("bidFloorCur")
    String bidFloorCur;

    List<String> bcat;

    List<String> badv;

    List<String> bapp;

    List<Integer> battr;
}
