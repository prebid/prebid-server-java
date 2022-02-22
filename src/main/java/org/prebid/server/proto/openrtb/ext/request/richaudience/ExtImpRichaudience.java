package org.prebid.server.proto.openrtb.ext.request.richaudience;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder
@Value(staticConstructor = "of")
public class ExtImpRichaudience {

    String pid;

    @JsonProperty("supplyType")
    String supplyType;

    @JsonProperty("bidfloor")
    BigDecimal bidFloor;

    @JsonProperty("bidfloorcur")
    String bidFloorCur;

    Boolean test;
}
