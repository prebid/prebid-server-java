package org.prebid.server.proto.openrtb.ext.request.jixie;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpJixie {

    String unit;

    @JsonProperty("accountid")
    String accountId;

    @JsonProperty("jxprop1")
    String jxProp1;

    @JsonProperty("jxprop2")
    String jxProp2;
}
