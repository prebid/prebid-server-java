package org.prebid.server.bidder.grid.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImp {

    ExtImpPrebid prebid;

    JsonNode bidder;

    @JsonProperty("data")
    ExtImpData extImpData;

    String gpid;
}
