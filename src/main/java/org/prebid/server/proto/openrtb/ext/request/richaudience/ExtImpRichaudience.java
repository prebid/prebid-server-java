package org.prebid.server.proto.openrtb.ext.request.richaudience;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpRichaudience {

    String pid;

    String supplyType;

    @JsonProperty(value = "bidfloor")
    Double bidFloor;

    @JsonProperty(value = "bidfloorcur")
    String bidFloorCur;

    Boolean test;
}
