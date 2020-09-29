package org.prebid.server.proto.openrtb.ext.request.telaria;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpTelaria {

    @JsonProperty("adCode")
    String adCode;

    @JsonProperty("seatCode")
    String seatCode;

    ObjectNode extra;
}
